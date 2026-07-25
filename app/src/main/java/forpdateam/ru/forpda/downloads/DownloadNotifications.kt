package forpdateam.ru.forpda.downloads

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import forpdateam.ru.forpda.R
import forpdateam.ru.forpda.common.appicon.applySelectedNotificationIcon

internal object DownloadNotifications {
    // Единый канал для всех уведомлений о загрузках
    const val CHANNEL_ID = "downloads_all_v1"
    
    // Старые ID каналов для удаления
    private const val OLD_CHANNEL_ID = "downloads"
    private const val OLD_CHANNEL_ID_COMPLETED = "downloads_completed"
    private const val OLD_CHANNEL_ID_V2 = "downloads_v2"
    private const val OLD_CHANNEL_ID_COMPLETED_V2 = "downloads_completed_v2"

    fun ensureChannel(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        
        // Удаляем все старые каналы
        nm.deleteNotificationChannel(OLD_CHANNEL_ID)
        nm.deleteNotificationChannel(OLD_CHANNEL_ID_COMPLETED)
        nm.deleteNotificationChannel(OLD_CHANNEL_ID_V2)
        nm.deleteNotificationChannel(OLD_CHANNEL_ID_COMPLETED_V2)
        
        // Создаём единый канал для всех уведомлений о загрузках
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.downloads),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = context.getString(R.string.downloads)
                setSound(null, null)
                enableVibration(true) // Вибрация для завершения
            }
        )
    }

    fun baseBuilder(context: Context): NotificationCompat.Builder {
        ensureChannel(context)
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .applySelectedNotificationIcon(context, R.drawable.ic_notify_download)
            .setColor(ContextCompat.getColor(context, R.color.light_link_color))
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
    }
    
    fun completedBuilder(context: Context): NotificationCompat.Builder {
        ensureChannel(context)
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .applySelectedNotificationIcon(context, R.drawable.ic_notify_download)
            .setColor(ContextCompat.getColor(context, R.color.light_link_color))
            .setOnlyAlertOnce(false)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        return builder
    }
}
