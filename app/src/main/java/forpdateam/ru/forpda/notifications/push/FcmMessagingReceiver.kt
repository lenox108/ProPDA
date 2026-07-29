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
import java.util.ArrayDeque

/**
 * Приёмник FCM data-сообщений от сервера 4PDA (легаси c2dm-путь, как в офиц. клиенте). Сам не
 * ходит в сеть (у BroadcastReceiver жёсткий бюджет) — только будит [EventsCheckWorker]
 * expedited-работой, которая переиспользует весь зрелый пайплайн ProPDA (inspector-дозагрузка,
 * дедуп, [forpdateam.ru.forpda.notifications.NotificationPublisher]).
 *
 * Это и есть выигрыш push над опросом/сокетом: сообщение будит процесс силами GmsCore даже в
 * Doze (`google.delivered_priority=high`), сокет держать не нужно.
 *
 * Payload не парсим в событие напрямую (ключи `t/i/e1/an/…` из Unread2), а используем как сигнал
 * «что-то изменилось» и берём авторитетное состояние из inspector — так не дублируем логику и не
 * рискуем на нестабильном формате.
 */
class FcmMessagingReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_RECEIVE) return

        // GmsCore может прислать служебную команду вместо события: токен инвалидирован и его
        // надо перевыпустить. Без этой ветки push тихо умер бы до перезапуска приложения.
        if (intent.getStringExtra("from") == IID_SENDER) {
            val cmd = intent.getStringExtra("CMD")
            if (cmd == "RST" || cmd == "RST_FULL" || cmd == "SYNC") {
                Timber.i("FCM IID command %s -> refreshing token", cmd)
                runCatching {
                    WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
                            PushTokenRefreshWorker.WORK_NAME,
                            ExistingWorkPolicy.REPLACE,
                            OneTimeWorkRequestBuilder<PushTokenRefreshWorker>().build())
                }.onFailure { Timber.e(it, "FCM: token refresh enqueue failed") }
            }
            return
        }

        // Третья, независимая точка проверки Pro (первые две — экран настроек и PushRegistrar).
        // Обход только настроек не даёт работающих уведомлений: входящий push здесь отбрасывается.
        // Спрашиваем ДРУГУЮ реализацию проверки (LicenseGuard), не ту, что в PushRegistrar:
        // патч одного «разрешающего» метода не должен открывать всю цепочку.
        if (!forpdateam.ru.forpda.pro.LicenseGuard.allowed(context.applicationContext)) {
            Timber.d("FCM push ignored: pro license missing")
            return
        }

        // Дедуп по google.message_id: GmsCore иногда доставляет дубли (окно как в офиц. клиенте).
        val messageId = intent.getStringExtra("google.message_id")
        if (messageId != null) {
            synchronized(recentIds) {
                if (recentIds.contains(messageId)) {
                    Timber.d("FCM duplicate %s, ignore", messageId)
                    return
                }
                if (recentIds.size >= DEDUP_WINDOW) recentIds.remove()
                recentIds.add(messageId)
            }
        }

        Timber.i("FCM push received, waking events check")
        // Журнал пишем вне главного потока: onReceive идёт на Main, а NotifDiagLog делает
        // файловый I/O (StrictMode ругался в полевом логе). Диагностика не должна тормозить
        // приём пуша — enqueue ниже важнее.
        val appContext = context.applicationContext
        val diagId = messageId?.take(12)
        Thread { runCatching { NotifDiagLog.log(appContext, "fcm: push received id=$diagId") } }
                .apply { isDaemon = true }
                .start()

        val work = OneTimeWorkRequestBuilder<EventsCheckWorker>()
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .setInputData(Data.Builder().putBoolean(EventsCheckWorker.KEY_FCM_TRIGGER, true).build())
                .build()
        runCatching {
            WorkManager.getInstance(context.applicationContext)
                    .enqueueUniqueWork(FCM_WORK_NAME, ExistingWorkPolicy.REPLACE, work)
        }.onFailure {
            Timber.e(it, "FCM: enqueue check failed")
            NotifDiagLog.log(context, "fcm: enqueue failed ${it.javaClass.simpleName}")
        }
    }

    companion object {
        private const val ACTION_RECEIVE = "com.google.android.c2dm.intent.RECEIVE"
        /** Отправитель служебных команд Instance ID (сброс/синхронизация токена). */
        private const val IID_SENDER = "google.com/iid"
        const val FCM_WORK_NAME = "events_check_fcm"
        private const val DEDUP_WINDOW = 16
        private val recentIds = ArrayDeque<String>(DEDUP_WINDOW)
    }
}
