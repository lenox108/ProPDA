package forpdateam.ru.forpda.common.receivers
import timber.log.Timber

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.WorkManager
import forpdateam.ru.forpda.common.BatteryDebugLogger
import forpdateam.ru.forpda.di.AppEntryPoint
import forpdateam.ru.forpda.notifications.EventsCheckWorker

/**
 * Created by isanechek on 7/11/17.
 * Оптимизированная версия с проверкой настроек перед запуском сервиса.
 */

class WakeUpReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val pr = goAsync()
        val action = intent?.action
        try {
            Timber.d("RECEIVER ACTION $action")
            if (Intent.ACTION_BOOT_COMPLETED == action) {
                // Проверяем, включены ли уведомления перед запуском сервиса
                // Это экономит заряд батареи - не запускаем сервис, если уведомления выключены
                val notificationPrefs = dagger.hilt.android.EntryPointAccessors
                    .fromApplication(context, AppEntryPoint::class.java)
                    .notificationPreferencesHolder()
                val mainEnabled = notificationPrefs.getMainEnabled()

                if (mainEnabled) {
                    Timber.d("Notifications enabled, WorkManager will handle background checks")
                    BatteryDebugLogger.logState("WakeUpReceiver", "bootCompleted", "background worker already scheduled")
                } else {
                    Timber.d("Notifications disabled, skipping service start to save battery")
                    WorkManager.getInstance(context).cancelUniqueWork(EventsCheckWorker.UNIQUE_NAME)
                    BatteryDebugLogger.logState("WakeUpReceiver", "bootCompleted", "notifications disabled")
                }
            }
        } catch (t: Throwable) {
            // Не даём ресиверу упасть/повиснуть
            Timber.e(t, "Failed to handle action=$action")
        } finally {
            pr.finish()
        }
    }
}
