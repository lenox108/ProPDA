package forpdateam.ru.forpda.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import dagger.hilt.android.AndroidEntryPoint
import forpdateam.ru.forpda.model.preferences.NotificationPreferencesHolder
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

    override fun onReceive(context: Context, intent: Intent) {
        val enabled = prefs.getMainEnabled() && prefs.getBgCheckEnabled() && prefs.wantsPushNotifications()
        NotifDiagLog.log(context, "alarm: fired (enabled=$enabled)")
        if (!enabled) {
            // Настройки выключили фон — цепочку не перевзводим; включение заново взведёт её
            // через App.rescheduleEventsCheckWorker (реагирует на изменение настроек).
            return
        }
        // Expedited-работа запускается и в Doze; сам ресивер сетью не занимается —
        // у BroadcastReceiver жёсткий бюджет времени.
        // NetworkType.CONNECTED — как у периодика (App.rescheduleEventsCheckWorker): без него
        // будильник в Doze-без-сети запускал воркер вхолостую, тот ловил IOException и жёг
        // Result.retry()-попытки/батарею. С констрейнтом WorkManager дождётся сети (и комментарий
        // в EventsCheckWorker «сеть гарантирована констрейнтом» становится верен и для alarm-пути).
        val request = OneTimeWorkRequestBuilder<EventsCheckWorker>()
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .setConstraints(
                        Constraints.Builder()
                                .setRequiredNetworkType(NetworkType.CONNECTED)
                                .build()
                )
                .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
                EventsCheckAlarmScheduler.ALARM_WORK_NAME,
                ExistingWorkPolicy.KEEP,
                request
        )
        // Перевзводим цепочку на следующий интервал.
        EventsCheckAlarmScheduler.schedule(context, prefs.getBgCheckIntervalMin())
    }
}
