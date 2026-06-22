package forpdateam.ru.forpda.notifications

import android.content.Context
import android.os.Build
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import forpdateam.ru.forpda.App
import forpdateam.ru.forpda.common.BatteryDebugLogger
import timber.log.Timber
import java.util.concurrent.TimeUnit

/**
 * Планирование фонового [EventsCheckWorker]: обычная цепочка и expedited при росте счётчиков.
 */
object EventsCheckScheduler {

    const val UNIQUE_PERIODIC = "events_check_periodic"
    const val UNIQUE_EXPEDITED = "events_check_expedited"

    private val networkConstraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

    fun schedulePeriodic(context: Context, delayMs: Long) {
        val request = OneTimeWorkRequestBuilder<EventsCheckWorker>()
                .setInitialDelay(delayMs.coerceAtLeast(MIN_DELAY_MS), TimeUnit.MILLISECONDS)
                .setConstraints(networkConstraints)
                .setBackoffCriteria(
                        androidx.work.BackoffPolicy.EXPONENTIAL,
                        30,
                        TimeUnit.SECONDS
                )
                .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
                UNIQUE_PERIODIC,
                ExistingWorkPolicy.REPLACE,
                request
        )
        if (Timber.treeCount > 0) {
            Timber.d("EventsCheckScheduler: periodic in ${delayMs / 1000}s")
        }
        BatteryDebugLogger.logState("EventsCheckScheduler", "periodic", "delayMs=$delayMs")
    }

    /**
     * Expedited one-shot (API 31+) или немедленный tick (API < 31).
     * Не заменяет periodic-цепочку — отдельное unique-имя.
     */
    fun scheduleExpedited(context: Context) {
        val builder = OneTimeWorkRequestBuilder<EventsCheckWorker>()
                .setConstraints(networkConstraints)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
        } else {
            builder.setInitialDelay(0, TimeUnit.MILLISECONDS)
        }
        WorkManager.getInstance(context).enqueueUniqueWork(
                UNIQUE_EXPEDITED,
                ExistingWorkPolicy.REPLACE,
                builder.build()
        )
        Timber.d("EventsCheckScheduler: expedited enqueued")
        BatteryDebugLogger.logState("EventsCheckScheduler", "expedited", "enqueued")
    }

    fun cancelAll(context: Context) {
        val wm = WorkManager.getInstance(context)
        wm.cancelUniqueWork(UNIQUE_PERIODIC)
        wm.cancelUniqueWork(UNIQUE_EXPEDITED)
    }

    // TODO restore on next pass: requires App.canScheduleBackgroundEventsCheck(),
    //  App.isAppInForeground and NotificationsService.computeAdaptiveIntervalMs() to land
    //  in the tracked code. Not called from the current main sources.
    @Suppress("unused")
    fun schedulePeriodicFromAppContext(context: Context, delayMs: Long? = null) {
        val delay = delayMs ?: DEFAULT_PERIODIC_DELAY_MS
        schedulePeriodic(context, delay)
    }

    private const val DEFAULT_PERIODIC_DELAY_MS = 15L * 60L * 1000L

    private const val MIN_DELAY_MS = 5_000L
}
