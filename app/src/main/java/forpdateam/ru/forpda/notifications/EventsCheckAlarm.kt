package forpdateam.ru.forpda.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.await
import dagger.hilt.android.AndroidEntryPoint
import forpdateam.ru.forpda.common.di.AppScope
import forpdateam.ru.forpda.model.preferences.NotificationPreferencesHolder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import timber.log.Timber
import javax.inject.Inject

/**
 * Точный будильник — второй, «пробивной» триггер фоновой проверки уведомлений.
 *
 * WorkManager — «вежливый» планировщик: OEM/Doze свободно откладывают периодическую работу
 * на часы (полевые жалобы «не приходят даже спустя 30 минут»). `setExactAndAllowWhileIdle`
 * — механизм будильников: система доставляет его даже в глубоком Doze (с системным
 * троттлингом ~раз в 15 мин на приложение в idle — ровно наш минимальный интервал).
 *
 * Схема двух контуров:
 *  - будильник взводится на интервал настроек и по срабатыванию ставит одноразовую
 *    expedited-работу [EventsCheckWorker] (expedited запускается и в Doze), затем перевзводится;
 *  - периодический WorkManager остаётся страховкой на случай отзыва разрешения будильников.
 *
 * Расход батареи не растёт: сетевую проверку делает максимум один триггер за полуинтервал —
 * дедуп по отметке последнего сетевого прохода внутри [EventsCheckWorker].
 */
object EventsCheckAlarmScheduler {

    private const val REQUEST_CODE = 5417
    internal const val ENQUEUE_TIMEOUT_MS = 8_000L

    /** Уникальное имя one-time работы от будильника — не пересекается с periodic. */
    const val ALARM_WORK_NAME = "events_check_alarm"

    fun schedule(context: Context, intervalMin: Long) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val pi = pendingIntent(context)
        val triggerAt = System.currentTimeMillis() + intervalMin * 60_000L
        // API 31-32: SCHEDULE_EXACT_ALARM может быть отозван пользователем; API 33+ у нас
        // USE_EXACT_ALARM (выдаётся всегда). При отсутствии права — неточный will-idle
        // будильник: тоже пробивает Doze, но в maintenance-окне.
        val canExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
        try {
            if (canExact) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
            }
            Timber.d("EventsCheckAlarm: armed in $intervalMin min (exact=$canExact)")
        } catch (se: SecurityException) {
            // Гонка отзыва права между canScheduleExactAlarms и set — просто не взводим,
            // страховочный periodic WorkManager продолжает работать.
            Timber.w(se, "EventsCheckAlarm: schedule failed")
        }
    }

    fun cancel(context: Context) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        am.cancel(pendingIntent(context))
        Timber.d("EventsCheckAlarm: cancelled")
    }

    private fun pendingIntent(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            Intent(context, EventsCheckAlarmReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
}

@AndroidEntryPoint
class EventsCheckAlarmReceiver : BroadcastReceiver() {

    @Inject lateinit var prefs: NotificationPreferencesHolder
    @Inject @AppScope lateinit var appScope: CoroutineScope

    override fun onReceive(context: Context, intent: Intent) {
        val enabled = prefs.getMainEnabled() && prefs.getBgCheckEnabled() && prefs.wantsPushNotifications()
        NotifDiagLog.log(context, "alarm: fired (enabled=$enabled)")
        if (!enabled) {
            // Настройки выключили фон — цепочку не перевзводим; включение заново взведёт её
            // через App.rescheduleEventsCheckWorker (реагирует на изменение настроек).
            return
        }
        // Перевзводим цепочку до асинхронной постановки работы: даже если WorkManager сломан или
        // его база временно недоступна, следующий alarm всё равно даст системе новый шанс.
        EventsCheckAlarmScheduler.schedule(context, prefs.getBgCheckIntervalMin())
        enqueueCheck(context.applicationContext)
    }

    private fun enqueueCheck(appContext: Context) {
        // Expedited-работа запускается и в Doze; сам ресивер сетью не занимается —
        // у BroadcastReceiver жёсткий бюджет времени.
        //
        // Alarm-путь намеренно БЕЗ NetworkType.CONNECTED. Полевой журнал показал 138 доставленных
        // alarm подряд без единого старта worker: первая constrained unique-work зависла в ENQUEUED,
        // а KEEP отбрасывал все следующие попытки. Периодик остаётся экономной constrained-
        // страховкой, а alarm обязан быть пробивным: worker сам безопасно обработает IOException,
        // вернёт Result.retry() и не продвинет snapshot/дедуп.
        val request = OneTimeWorkRequestBuilder<EventsCheckWorker>()
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()
        val pendingResult = goAsync()
        val operation = runCatching {
            WorkManager.getInstance(appContext).enqueueUniqueWork(
                EventsCheckAlarmScheduler.ALARM_WORK_NAME,
                // Заменяем только незавершённую alarm-задачу. Проверка обычно занимает
                // секунды, поэтому к следующему 15-минутному alarm RUNNING здесь быть не
                // должно; зато stale ENQUEUED больше не блокирует доставку сутками.
                ExistingWorkPolicy.REPLACE,
                request
            )
        }.getOrElse { error ->
            Timber.e(error, "EventsCheckAlarm: enqueue failed")
            NotifDiagLog.log(appContext, "alarm: enqueue failed ${error.javaClass.simpleName}")
            pendingResult.finish()
            return
        }

        // enqueueUniqueWork пишет в БД и планирует JobScheduler асинхронно. Не отпускаем
        // BroadcastReceiver раньше завершения этой операции, иначе OEM может убить холодный
        // процесс между «alarm: fired» и фактической постановкой worker.
        appScope.launch {
            try {
                withTimeout(EventsCheckAlarmScheduler.ENQUEUE_TIMEOUT_MS) {
                    operation.await()
                }
                NotifDiagLog.log(
                    appContext,
                    "alarm: work enqueued id=${request.id.toString().take(8)} replace=true"
                )
            } catch (error: Throwable) {
                Timber.e(error, "EventsCheckAlarm: enqueue completion failed")
                NotifDiagLog.log(
                    appContext,
                    "alarm: enqueue completion failed ${error.javaClass.simpleName}"
                )
            } finally {
                pendingResult.finish()
            }
        }
    }
}
