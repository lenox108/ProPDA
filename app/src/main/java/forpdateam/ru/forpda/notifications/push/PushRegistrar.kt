package forpdateam.ru.forpda.notifications.push

import android.content.Context
import forpdateam.ru.forpda.model.preferences.NotificationPreferencesHolder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import kotlin.coroutines.resume

/**
 * Оркестратор push-регистрации: берёт FCM-токен ([PicoFcm]) и загружает его на сервер 4PDA через
 * [AppProtocolClient] (`ai`), используя сохранённую сессию (`ma` по login_key). Сам логин (`ml` с
 * капчей) делается один раз в настройках — здесь только resume + upload.
 *
 * Битмаск семейств уведомлений собирается из пользовательских настроек ProPDA, чтобы сервер слал
 * ровно те события, что включены (как у офиц. клиента).
 */
class PushRegistrar(
        private val context: Context,
        private val notifPrefs: NotificationPreferencesHolder,
        private val session: PushSessionStore = PushSessionStore(context)
) {

    sealed class Result {
        object Success : Result()
        object NoSession : Result()      // нет login_key — нужен разовый логин в настройках
        object NoGms : Result()          // нет Google Play Services
        object NotPro : Result()         // нет действующего ключа активации Pro
        data class Error(val reason: String) : Result()
    }

    /**
     * Полный цикл: токен → resume → upload. Идемпотентно — если токен и битмаск не изменились,
     * повторный `ai` не шлётся. Блокирующая сеть вынесена на IO.
     */
    suspend fun register(force: Boolean = false): Result = withContext(Dispatchers.IO) {
        // Push — функция Pro. Проверяем здесь, а не только в UI: это единственная точка, где
        // токен реально уходит на сервер, поэтому она и должна быть решающей.
        if (!forpdateam.ru.forpda.pro.ProLicense.isUnlocked(context)) {
            Timber.d("PushRegistrar: pro license missing, skip")
            return@withContext Result.NotPro
        }
        if (!session.hasSession()) return@withContext Result.NoSession
        // Страховка от привязки токена к чужому аккаунту: если в приложении сменился
        // пользователь, а push-сессия осталась от прежнего — гасим её вместо регистрации.
        val appUserId = runCatching {
            androidx.preference.PreferenceManager.getDefaultSharedPreferences(context)
                    .getString("member_id", null)?.toIntOrNull()
        }.getOrNull()
        if (appUserId != null && appUserId != 0 && appUserId != session.memberId) {
            Timber.w("PushRegistrar: account changed (app=%d push=%d), dropping stale session",
                    appUserId, session.memberId)
            session.clear()
            return@withContext Result.NoSession
        }

        val token = obtainToken() ?: return@withContext Result.NoGms
        val bitmask = computeBitmask()

        if (!force && token == session.lastRegisteredToken && bitmask == session.lastRegisteredBitmask) {
            Timber.d("PushRegistrar: token/bitmask unchanged, skip")
            return@withContext Result.Success
        }

        val host = runCatching { AppProtocolClient.resolveWsHost() }.getOrDefault(AppProtocolClient.DEFAULT_WS_HOST)
        runCatching {
            AppProtocolClient(host).use { client ->
                client.connect()
                if (!client.resume(session.memberId, session.loginKey!!)) {
                    // login_key протух — чистим, чтобы настройки предложили перелогин.
                    session.loginKey = null
                    return@withContext Result.NoSession
                }
                if (!client.registerToken(token, bitmask, PROVIDER_GOOGLE)) {
                    return@withContext Result.Error("server rejected token")
                }
            }
        }.getOrElse {
            Timber.e(it, "PushRegistrar: register failed")
            return@withContext Result.Error(it.javaClass.simpleName)
        }

        session.lastRegisteredToken = token
        session.lastRegisteredBitmask = bitmask
        Timber.i("PushRegistrar: token registered (bitmask=%d)", bitmask)
        Result.Success
    }

    /** Снять регистрацию (пустой токен = отписка на стороне сервера). */
    suspend fun unregister(): Result = withContext(Dispatchers.IO) {
        if (!session.hasSession()) return@withContext Result.NoSession
        val host = runCatching { AppProtocolClient.resolveWsHost() }.getOrDefault(AppProtocolClient.DEFAULT_WS_HOST)
        runCatching {
            AppProtocolClient(host).use { client ->
                client.connect()
                if (!client.resume(session.memberId, session.loginKey!!)) return@withContext Result.NoSession
                client.registerToken("", 0, PROVIDER_GOOGLE)
            }
        }.getOrElse { return@withContext Result.Error(it.javaClass.simpleName) }
        session.lastRegisteredToken = null
        session.lastRegisteredBitmask = -1
        Result.Success
    }

    /** Битмаск из настроек: б0 QMS, б1 QMS-системные, б2 избранное, б3 важные темы, б4 упоминания. */
    fun computeBitmask(): Int {
        var mask = 0
        val mainOn = notifPrefs.getMainEnabled()
        if (!mainOn) return 0
        if (notifPrefs.getQmsEnabled()) mask = mask or 0b00001 or 0b00010 // QMS + системные QMS
        if (notifPrefs.getFavEnabled()) {
            mask = if (notifPrefs.getFavOnlyImportant()) mask or 0b01000 // только важные темы
            else mask or 0b00100 or 0b01000                             // все избранные + важные
        }
        if (notifPrefs.getMentionsEnabled()) mask = mask or 0b10000
        return mask
    }

    private suspend fun obtainToken(): String? = withTimeoutOrNull(TOKEN_TIMEOUT_MS) {
        suspendCancellableCoroutine { cont ->
            PicoFcm(context, FOURPDA_GMP_APP_ID).getToken { _, result ->
                val token = result?.getString("registration_id")
                if (cont.isActive) cont.resume(if (token.isNullOrBlank()) null else token)
            }
        }
    }

    companion object {
        /** Firebase-приложение 4PDA (открыто лежит в их APK; используется с разрешения владельца). */
        const val FOURPDA_GMP_APP_ID = "1:1043483203481:android:43c96e036dc3fe54"
        const val PROVIDER_GOOGLE = 0
        private const val TOKEN_TIMEOUT_MS = 20_000L
    }
}
