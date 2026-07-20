package forpdateam.ru.forpda.client

import android.content.Context
import androidx.preference.PreferenceManager
import forpdateam.ru.forpda.common.PrivateHeaders
import forpdateam.ru.forpda.common.SecureCookiesPreferences
import forpdateam.ru.forpda.common.di.AppScope
import forpdateam.ru.forpda.entity.common.AuthData
import forpdateam.ru.forpda.entity.common.AuthState
import forpdateam.ru.forpda.model.AuthHolder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import timber.log.Timber
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Менеджер для управления cookies аутентификации.
 */
class CookieManager(
    private val context: Context,
    private val authHolder: AuthHolder,
    @AppScope private val appScope: CoroutineScope,
) {
    private val clientCookies = ConcurrentHashMap<String, Cookie>()
    private val mobileCookie: Cookie? = safeParseMobileCookie()

    /**
     * Cache of the last cookie value already written to EncryptedSharedPreferences
     * (key = "cookie_<name>"). Used to skip the AES-256-GCM re-encryption on every
     * response when the server keeps resending the same cookie value.
     */
    private val lastSavedCookieValues = ConcurrentHashMap<String, String>()

    private val lastStaleCheck = java.util.concurrent.atomic.AtomicLong(0L)

    /**
     * Set to true once the auth cookies from EncryptedSharedPreferences have been
     * hydrated into [clientCookies]. Hydration runs off the main thread on
     * construction; the first [CookieJar.loadForRequest] call will block briefly
     * (with a short timeout) to wait for the in-flight hydration to complete so
     * that poll pages do not go out as guest. See AUDIT-M15.
     */
    private val cookiesHydrated = AtomicBoolean(false)

    companion object {
        private const val MOBILE_COOKIE_NAME = "ngx_mb"
        private const val MOBILE_COOKIE_VALUE = "1"
        private const val STALE_CHECK_INTERVAL_MS = 60_000L  // раз в минуту
        private const val COOKIE_HYDRATION_WAIT_MS = 500L
    }

    init {
        putCookieIfValid(mobileCookie)
        // Auth cookies must be available before the first topic request; otherwise
        // poll pages can be fetched as guest and lose the voting form.
        // Hydrate off the main thread so the app's critical-path work (DI graph,
        // first activity creation) is not blocked by the EncryptedSharedPreferences
        // roundtrip (which can take ~50-200 ms on a cold start).
        appScope.launch(Dispatchers.IO) {
            runCatching { initializeCookies() }
                .onFailure { Timber.w(it, "CookieManager.initializeCookies failed") }
            cookiesHydrated.set(true)
        }
    }

    /**
     * Дождаться, пока auth-куки из EncryptedSharedPreferences доедут в память.
     * Для фонового воркера 500 мс потолка [loadForRequest] мало: на холодном старте
     * медленных устройств Keystore+AES занимают больше, и опрос inspector'а уходил
     * «гостем» — пустые события и затёртый снапшот. @return true, если гидрация успела.
     */
    suspend fun awaitHydration(timeoutMs: Long): Boolean {
        if (cookiesHydrated.get()) return true
        return withTimeoutOrNull(timeoutMs) {
            while (!cookiesHydrated.get()) {
                kotlinx.coroutines.delay(20L)
            }
            true
        } ?: false
    }

    private fun initializeCookies() {
        val authData = authHolder.get()
        val securePrefs = SecureCookiesPreferences.getInstance(context)
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)

        val memberId = securePrefs.getString("cookie_member_id", null)
        val passHash = securePrefs.getString("cookie_pass_hash", null)
        val sessionId = securePrefs.getString("cookie_session_id", null)
        val anonymous = securePrefs.getString("cookie_anonymous", null)
        val clearance = securePrefs.getString("cookie_cf_clearance", null)

        clearance?.let { putCookieIfValid(parseCookie(it)) }

        val updatedAuthData = if (memberId != null && passHash != null) {
            val userId = preferences.getString("member_id", "0")?.toIntOrNull() ?: 0
            authData.copy(
                state = AuthState.AUTH,
                userId = userId
            )
        } else {
            // Диагностика фоновой авторизации: почему воркер видит «not authorized». Если
            // secureFallback=true — зашифрованное хранилище не открылось (KeyStore), и auth-куки
            // «пропали» в холодном фоне, хотя пользователь залогинен (полевой лог).
            forpdateam.ru.forpda.notifications.NotifDiagLog.log(
                context,
                "auth: SKIP secureFallback=${SecureCookiesPreferences.getInstance(context).isUsingFallback}" +
                    " memberId=${memberId != null} passHash=${passHash != null}"
            )
            authData.copy(
                state = AuthState.SKIP,
                userId = 0
            )
        }
        putCookieIfValid(parseCookie(memberId))
        putCookieIfValid(parseCookie(passHash))
        sessionId?.let { putCookieIfValid(parseCookie(it)) }
        anonymous?.let { putCookieIfValid(parseCookie(it)) }
        authHolder.set(updatedAuthData)
        removeStaleNullCookies()
    }

    val cookieJar: CookieJar = object : CookieJar {
            override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
                val securePrefs = SecureCookiesPreferences.getInstance(context)
                val editor = PreferenceManager.getDefaultSharedPreferences(context).edit()

                for (cookie in cookies) {
                    val key = "cookie_${cookie.name}"
                    if (cookie.value == "deleted" || cookie.expiresAt <= System.currentTimeMillis()) {
                        if (lastSavedCookieValues.remove(cookie.name) != null) {
                            securePrefs.remove(key)
                        }
                        clientCookies.remove(cookie.name)
                    } else {
                        val encoded = cookieToPref(url.toString(), cookie)
                        // Skip the AES roundtrip if we already wrote the same value last time.
                        if (lastSavedCookieValues[cookie.name] != encoded) {
                            securePrefs.putString(key, encoded)
                            lastSavedCookieValues[cookie.name] = encoded
                        }

                        if (cookie.name == "member_id") {
                            editor.putString("member_id", cookie.value)
                            val userId = cookie.value.toIntOrNull() ?: AuthData.NO_ID
                            val authData = authHolder.get()
                            val updatedAuthData = authData.copy(
                                userId = userId,
                                state = if (userId == AuthData.NO_ID) AuthState.NO_AUTH else AuthState.AUTH
                            )
                            authHolder.set(updatedAuthData)
                        }
                        clientCookies[cookie.name] = cookie
                    }
                }
                editor.apply()
            }

            override fun loadForRequest(url: HttpUrl): List<Cookie> {
                val external = !url.host.contains("4pda", ignoreCase = true)

                val now = System.currentTimeMillis()
                val lastCheck = lastStaleCheck.get()
                if (now - lastCheck > STALE_CHECK_INTERVAL_MS && lastStaleCheck.compareAndSet(lastCheck, now)) {
                    removeStaleNullCookies()
                }

                // Block briefly on the first request so the off-main hydration
                // can finish before the first outgoing call. After hydration
                // completes this is a single volatile read.
                if (!cookiesHydrated.get()) {
                    runBlocking(Dispatchers.IO) {
                        withTimeoutOrNull(COOKIE_HYDRATION_WAIT_MS) {
                            while (!cookiesHydrated.get()) {
                                kotlinx.coroutines.yield()
                            }
                        }
                    }
                }

                val cookies = clientCookies.values
                    .filter { it.matches(url) }
                    .toMutableList()
                
                if (external) {
                    // Удаляем приватные куки для внешних запросов
                    val iterator = cookies.iterator()
                    while (iterator.hasNext()) {
                        val cookie = iterator.next()
                        if (PrivateHeaders.LIST.any { cookie.name.contains(it, ignoreCase = true) }) {
                            iterator.remove()
                        }
                    }
                }
                
                return cookies
            }
        }

    fun getCookies(): Map<String, Cookie> = Collections.unmodifiableMap(clientCookies)

    fun putCookie(cookie: Cookie) {
        putCookieIfValid(cookie)
    }

    fun clearCookies() {
        clientCookies.clear()
        lastSavedCookieValues.clear()
        putCookieIfValid(mobileCookie)
        // Удаляем auth cookies из SecurePrefs
        val securePrefs = SecureCookiesPreferences.getInstance(context)
        listOf("cookie_member_id", "cookie_pass_hash", "cookie_session_id",
               "cookie_anonymous", "cookie_cf_clearance").forEach { securePrefs.remove(it) }
        // Очищаем userId из обычных prefs
        PreferenceManager.getDefaultSharedPreferences(context).edit().remove("member_id").apply()
        // Сбрасываем authHolder
        val authData = authHolder.get()
        authHolder.set(authData.copy(state = AuthState.NO_AUTH, userId = AuthData.NO_ID))
    }

    private fun putCookieIfValid(cookie: Cookie?) {
        if (cookie != null && cookie.value.isNotEmpty() && cookie.value != "null") {
            clientCookies[cookie.name] = cookie
        }
    }

    private fun removeStaleNullCookies() {
        val now = System.currentTimeMillis()
        val securePrefs = SecureCookiesPreferences.getInstance(context)
        clientCookies.entries.removeIf { (name, cookie) ->
            val isStale = cookie.name != MOBILE_COOKIE_NAME &&
                    (cookie.expiresAt < now || cookie.value.isEmpty() || cookie.value == "null")
            if (isStale) {
                securePrefs.remove("cookie_$name")
                lastSavedCookieValues.remove(name)
            }
            isStale
        }
    }

    private fun safeParseMobileCookie(): Cookie? {
        return try {
            Cookie.Builder()
                .name(MOBILE_COOKIE_NAME)
                .value(MOBILE_COOKIE_VALUE)
                .domain("4pda.to")
                .path("/")
                .build()
        } catch (e: Exception) {
            null
        }
    }

    private fun parseCookie(cookieString: String?): Cookie? {
        if (cookieString.isNullOrEmpty()) {
            return null
        }
        
        return try {
            // Хранение: Url|:|Cookie
            val fields = cookieString.split("|:|", limit = 2)
            if (fields.size < 2 || fields[0].isEmpty() || fields[1].isEmpty()) {
                return null
            }
            
            val url = fields[0].toHttpUrlOrNull() ?: return null
            Cookie.parse(url, fields[1])
        } catch (e: Exception) {
            Timber.w("parseCookie failed", e)
            null
        }
    }

    private fun cookieToPref(url: String, cookie: Cookie): String {
        return "$url|:|$cookie"
    }
}
