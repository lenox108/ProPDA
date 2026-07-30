package forpdateam.ru.forpda.notifications.push

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import forpdateam.ru.forpda.notifications.EventsCheckWorker
import forpdateam.ru.forpda.notifications.NotifDiagLog
import timber.log.Timber

/**
 * Приём push от Huawei (HMS) — зеркало [FcmMessagingReceiver] для устройств без Google.
 *
 * Два интента, как у офиц. клиента:
 *  - `…push.intent.REGISTRATION` — выданный токен (байты в extra `device_token`). Сохраняем и
 *    сразу отправляем на сервер 4PDA: сам [PicoHms] токена не возвращает, он приходит только сюда;
 *  - `…push.intent.RECEIVE` — само событие. Тело не парсим (как и в FCM-пути): будим
 *    [EventsCheckWorker], который берёт авторитетное состояние из inspector'а.
 */
class HmsPushReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val appContext = context.applicationContext
        when (intent.action) {
            ACTION_REGISTRATION -> {
                val token = PicoHms.tokenFrom(intent)
                if (token.isNullOrEmpty()) {
                    Timber.w("HMS: пустой токен в REGISTRATION")
                    return
                }
                Timber.i("HMS: получен токен (%d символов)", token.length)
                HmsTokenStore.save(appContext, token)
                // Регистрируем в фоне: приёмник обязан вернуться быстро, а тут сеть.
                val work = OneTimeWorkRequestBuilder<PushTokenRefreshWorker>().build()
                runCatching {
                    WorkManager.getInstance(appContext).enqueueUniqueWork(
                            PushTokenRefreshWorker.WORK_NAME, ExistingWorkPolicy.REPLACE, work)
                }.onFailure { Timber.e(it, "HMS: не удалось запланировать регистрацию токена") }
            }
            ACTION_RECEIVE -> {
                // Гейт Pro — как в FCM-приёмнике: без ключа мгновенной доставки нет.
                if (!forpdateam.ru.forpda.pro.LicenseGuard.allowed(appContext)) {
                    Timber.d("HMS push ignored: pro license missing")
                    return
                }
                Timber.i("HMS push received, waking events check")
                Thread { runCatching { NotifDiagLog.log(appContext, "hms: push received") } }
                        .apply { isDaemon = true }
                        .start()
                val work = OneTimeWorkRequestBuilder<EventsCheckWorker>()
                        .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                        .setInputData(Data.Builder()
                                .putBoolean(EventsCheckWorker.KEY_FCM_TRIGGER, true)
                                .build())
                        .build()
                runCatching {
                    WorkManager.getInstance(appContext).enqueueUniqueWork(
                            FcmMessagingReceiver.FCM_WORK_NAME, ExistingWorkPolicy.REPLACE, work)
                }.onFailure { Timber.e(it, "HMS: enqueue check failed") }
            }
        }
    }

    companion object {
        private const val ACTION_REGISTRATION = "com.huawei.android.push.intent.REGISTRATION"
        private const val ACTION_RECEIVE = "com.huawei.android.push.intent.RECEIVE"
    }
}

/**
 * Последний выданный HMS токен. Отдельно от [PushSessionStore]: тот хранит секрет входа, а это
 * просто идентификатор устройства у Huawei — он приходит асинхронно бродкастом, поэтому
 * регистрации нужно место, где его подождать.
 */
object HmsTokenStore {

    fun save(context: Context, token: String) {
        prefs(context).edit().putString(KEY_TOKEN, token).apply()
    }

    fun get(context: Context): String? =
            prefs(context).getString(KEY_TOKEN, null)?.takeIf { it.isNotBlank() }

    fun clear(context: Context) {
        prefs(context).edit().remove(KEY_TOKEN).apply()
    }

    private fun prefs(context: Context) =
            androidx.preference.PreferenceManager.getDefaultSharedPreferences(context.applicationContext)

    private const val KEY_TOKEN = "notifications.hms_token"
}
