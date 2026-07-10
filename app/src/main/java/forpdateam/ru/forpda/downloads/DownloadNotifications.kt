package forpdateam.ru.forpda.downloads

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import forpdateam.ru.forpda.R

internal object DownloadNotifications {
    // Единый канал для всех уведомлений о загрузках
    const val CHANNEL_ID = "downloads_all_v1"
    
    // Старые ID каналов для удаления
    private const val OLD_CHANNEL_ID = "downloads"
    private const val OLD_CHANNEL_ID_COMPLETED = "downloads_completed"
    private const val OLD_CHANNEL_ID_V2 = "downloads_v2"
    private const val OLD_CHANNEL_ID_COMPLETED_V2 = "downloads_completed_v2"

    /** Каналов до Android 8 нет, а класс NotificationChannel появился только в API 26. */
    fun ensureChannel(context: Context) {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.O) return
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
            .setSmallIcon(R.drawable.ic_notify_download)
            .setLargeIcon(appLauncherBitmap(context))
            .setColor(ContextCompat.getColor(context, R.color.light_link_color))
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
    }
    
    fun completedBuilder(context: Context): NotificationCompat.Builder {
        ensureChannel(context)
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notify_download)
            .setLargeIcon(appLauncherBitmap(context))
            .setColor(ContextCompat.getColor(context, R.color.light_link_color))
            .setOnlyAlertOnce(false)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        return builder
    }

    /**
     * Большая круглая иконка уведомления — лого приложения. Нужна, т.к. на ряде OEM (MIUI/ColorOS/
     * OneUI) адаптивная launcher‑иконка в круглом слоте уведомления рендерится как пустой цветной
     * круг без содержимого (прозрачный background + foreground вне safe zone маски). Рендерим сами
     * из mipmap: получаем Drawable через ResourcesCompat (корректно разворачивает adaptive‑icon),
     * кладём на Bitmap фиксированного размера.
     */
    private fun appLauncherBitmap(context: Context): Bitmap? {
        val d = try {
            ResourcesCompat.getDrawable(context.resources, R.mipmap.ic_launcher, context.theme)
        } catch (_: Throwable) {
            null
        } ?: return null
        if (d is BitmapDrawable && d.bitmap != null) return d.bitmap
        val size = context.resources.getDimensionPixelSize(android.R.dimen.notification_large_icon_width)
            .coerceAtLeast(96)
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        d.setBounds(0, 0, size, size)
        d.draw(canvas)
        return bmp
    }
}

