package forpdateam.ru.forpda.notifications.push

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import forpdateam.ru.forpda.model.preferences.NotificationPreferencesHolder
import forpdateam.ru.forpda.notifications.NotifDiagLog
import timber.log.Timber

/**
 * Перевыпуск и повторная регистрация push-токена.
 *
 * Нужен, потому что GmsCore может в любой момент инвалидировать токен и прислать служебную
 * команду `RST`/`RST_FULL`/`SYNC` (from = `google.com/iid`). Без реакции на неё старый токен
 * на сервере 4PDA протух бы, и push молча перестал бы приходить до следующего запуска
 * приложения. Официальный клиент обрабатывает ровно эти команды.
 */
@HiltWorker
class PushTokenRefreshWorker @AssistedInject constructor(
        @Assisted private val appContext: Context,
        @Assisted params: WorkerParameters,
        private val notifPrefs: NotificationPreferencesHolder
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val registrar = PushRegistrar(appContext, notifPrefs)
        // force = true: токен мог смениться, дедуп по старому значению здесь только помешал бы.
        return when (val r = registrar.register(force = true)) {
            is PushRegistrar.Result.Success -> {
                NotifDiagLog.log(appContext, "fcm: token re-registered")
                Result.success()
            }
            is PushRegistrar.Result.NoSession -> {
                // Нет login_key — молчим: пользователь заново включит push в настройках.
                NotifDiagLog.log(appContext, "fcm: token refresh skipped (no session)")
                Result.success()
            }
            is PushRegistrar.Result.NoGms -> Result.success()
            // Ключ Pro убрали — обновлять токен незачем, это не ошибка.
            is PushRegistrar.Result.NotPro -> Result.success()
            is PushRegistrar.Result.Error -> {
                Timber.w("push token refresh failed: %s", r.reason)
                NotifDiagLog.log(appContext, "fcm: token refresh failed ${r.reason}")
                Result.retry()
            }
        }
    }

    companion object {
        const val WORK_NAME = "push_token_refresh"
    }
}
