package forpdateam.ru.forpda.client

import android.content.Context
import androidx.preference.PreferenceManager
import forpdateam.ru.forpda.common.PrivateHeaders
import forpdateam.ru.forpda.common.SecureCookiesPreferences
import forpdateam.ru.forpda.entity.common.AuthData
import forpdateam.ru.forpda.entity.common.AuthState
import forpdateam.ru.forpda.model.AuthHolder
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import timber.log.Timber
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

/**
 * Менеджер для управления cookies аутентификации.
 */
class CookieManager(
    private val context: Context,
    private val authHolder: AuthHolder
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

    companion object {
        private const val MOBILE_COOKIE_NAME = "ngx_mb"
        private const val MOBILE_COOKIE_VALUE = "1"
        private const val STALE_CHECK_INTERVAL_MS = 60_000L  // раз в минуту
    }

    init {
        putCookieIfValid(mobileCookie)
        // Auth cookies must be available before the first topic request; otherwise
        // poll pages can be fetched as guest and lose the voting form.
        initializeCookies()
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
