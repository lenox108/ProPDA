package forpdateam.ru.forpda.appupdates

import android.content.Context
import androidx.work.Constraints
import androidx.work.BackoffPolicy
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import forpdateam.ru.forpda.common.BatteryDebugLogger
import java.util.concurrent.TimeUnit
import javax.inject.Inject

class AppUpdateScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferences: AppUpdatePreferences
) {

    fun reschedule() {
        val workManager = WorkManager.getInstance(context)
        if (!preferences.isCheckEnabled()) {
            workManager.cancelUniqueWork(AppUpdateWorker.UNIQUE_NAME)
            BatteryDebugLogger.logState("AppUpdateWorker", "cancelled")
            return
        }

        val request = PeriodicWorkRequestBuilder<AppUpdateWorker>(24, TimeUnit.HOURS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .setRequiresBatteryNotLow(true)
                    .build()
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES)
            .build()

        workManager.enqueueUniquePeriodicWork(
            AppUpdateWorker.UNIQUE_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
        BatteryDebugLogger.logState("AppUpdateWorker", "scheduled", "intervalHours=24 batteryNotLow=true")
    }
}
