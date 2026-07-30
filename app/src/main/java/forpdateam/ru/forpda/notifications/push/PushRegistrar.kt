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
        object NoHms : Result()          // нет сервисов Huawei / токен ещё не выдан
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

        // Провайдер выбирает пользователь способом доставки: Google (FCM) или Huawei (HCM).
        // Huawei нужен там, где нет сервисов Google, — их сервер принимает оба, поле `provider`
        // в опкоде `ai` для того и есть.
        val huawei = isHuaweiMode()
        val token = if (huawei) huaweiToken() else obtainToken()
        if (token == null) return@withContext if (huawei) Result.NoHms else Result.NoGms
        val bitmask = computeBitmask()

        if (!force && token == session.lastRegisteredToken && bitmask == session.lastRegisteredBitmask) {
            Timber.d("PushRegistrar: token/bitmask unchanged, skip")
            return@withContext Result.Success
        }

        // connectAny сам переберёт TLS и прямой сокет — см. комментарий там.
        runCatching {
            AppProtocolClient.connectAny().use { client ->
                if (!client.resume(session.memberId, session.loginKey!!)) {
                    // login_key протух — чистим, чтобы настройки предложили перелогин.
                    session.loginKey = null
                    return@withContext Result.NoSession
                }
                if (!client.registerToken(token, bitmask, if (huawei) PROVIDER_HUAWEI else PROVIDER_GOOGLE)) {
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
        // connectAny сам переберёт TLS и прямой сокет — см. комментарий там.
        runCatching {
            AppProtocolClient.connectAny().use { client ->
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
        if (notifPrefs.getQmsEnabled()) mask = mask or 0b00001              // переписка QMS
        // Системные события QMS — отдельный бит и отдельная настройка: у офиц. клиента они тоже
        // отключаются независимо (репутация, состояние аккаунта приходят в «Сообщения 4PDA»).
        if (notifPrefs.getQmsSystemEnabled()) mask = mask or 0b00010
        if (notifPrefs.getFavEnabled()) {
            mask = if (notifPrefs.getFavOnlyImportant()) mask or 0b01000 // только важные темы
            else mask or 0b00100 or 0b01000                             // все избранные + важные
        }
        if (notifPrefs.getMentionsEnabled()) mask = mask or 0b10000
        return mask
    }

    /** Выбран ли способ доставки «Push (Huawei)». */
    private fun isHuaweiMode(): Boolean = runCatching {
        androidx.preference.PreferenceManager.getDefaultSharedPreferences(context)
                .getString(KEY_DELIVERY_METHOD, null) == DELIVERY_PUSH_HMS
    }.getOrDefault(false)

    /**
     * Токен Huawei приходит бродкастом, а не в ответе на запрос ([PicoHms]). Поэтому: есть
     * сохранённый — берём; нет — просим выдать и выходим, регистрацию доделает
     * [PushTokenRefreshWorker], которого разбудит [HmsPushReceiver].
     */
    private fun huaweiToken(): String? {
        HmsTokenStore.get(context)?.let { return it }
        if (!PicoHms.isAvailable(context)) return null
        Timber.i("PushRegistrar: запрашиваем токен HMS")
        PicoHms(context).requestToken()
        return null
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
        /** Провайдер Huawei в опкоде `ai` (их же нумерация: 0 = Google, 1 = Huawei). */
        const val PROVIDER_HUAWEI = 1
        const val KEY_DELIVERY_METHOD = "notifications.delivery_method"
        const val DELIVERY_PUSH_HMS = "push_hms"
        private const val TOKEN_TIMEOUT_MS = 20_000L
    }
}
