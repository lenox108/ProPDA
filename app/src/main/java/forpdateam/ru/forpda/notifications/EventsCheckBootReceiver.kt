package forpdateam.ru.forpda.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import forpdateam.ru.forpda.model.preferences.NotificationPreferencesHolder
import timber.log.Timber
import javax.inject.Inject

/**
 * Перевзвод точного будильника фоновой проверки после перезагрузки устройства и обновления
 * приложения. ОС чистит все AlarmManager-будильники на ребуте, а перевзвод у нас живёт в
 * ресивере будильника и в конце прохода воркера — значит без этого receiver'а alarm-цепь
 * молчала бы до первого срабатывания СТРАХОВОЧНОГО периодика (а он теперь на 2× интервале,
 * т.е. до ~30–480 мин тишины после ребута). WorkManager свою периодику восстанавливает сам;
 * здесь достаточно вернуть точный будильник.
 *
 * После force-stop система не доставит BOOT_COMPLETED (приложение в состоянии stopped) — этот
 * кейс не лечится ничем, кроме ручного запуска, и это ожидаемо.
 */
@AndroidEntryPoint
class EventsCheckBootReceiver : BroadcastReceiver() {

    @Inject lateinit var prefs: NotificationPreferencesHolder

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            "android.intent.action.QUICKBOOT_POWERON" -> {
                val enabled = prefs.getMainEnabled() && prefs.getBgCheckEnabled() &&
                        prefs.wantsPushNotifications()
                NotifDiagLog.log(context, "boot: ${intent.action?.substringAfterLast('.')} enabled=$enabled")
                if (!enabled) return
                runCatching {
                    EventsCheckAlarmScheduler.schedule(context, prefs.getBgCheckIntervalMin())
                }.onFailure { Timber.w(it, "boot: alarm reschedule failed") }
            }
        }
    }
}
