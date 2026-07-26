package forpdateam.ru.forpda.debuglab

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.graphics.drawable.IconCompat
import forpdateam.ru.forpda.R
import forpdateam.ru.forpda.common.appicon.applySelectedNotificationIcon

/**
 * Debug-лаборатория для проверки, чем можно подменить кружок слева в шторке
 * Android 16. Публикует уведомление «как Избранное» в запрошенном стиле:
 *   -e mode msg   — MessagingStyle, аватар персоны = цветная иконка приложения;
 *   -e mode big   — обычный BigText (контроль: кружок = значок из манифеста).
 */
class NotifIconLabReceiver : BroadcastReceiver() {

    @SuppressLint("MissingPermission")
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.getStringExtra("mode") == "alias") {
            // Переключение launcher-псевдонима: -e enable <FQCN> -e disable <FQCN>
            val pm = context.packageManager
            intent.getStringExtra("enable")?.let {
                pm.setComponentEnabledSetting(
                        android.content.ComponentName(context, it),
                        android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                        android.content.pm.PackageManager.DONT_KILL_APP,
                )
            }
            intent.getStringExtra("disable")?.let {
                pm.setComponentEnabledSetting(
                        android.content.ComponentName(context, it),
                        android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                        android.content.pm.PackageManager.DONT_KILL_APP,
                )
            }
            return
        }
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Notif icon lab", NotificationManager.IMPORTANCE_DEFAULT)
        )

        val title = "Новые посты в избранной теме"
        val text = "Выбор и Сравнение смартфонов и телефонов - Общая тема"
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
                .applySelectedNotificationIcon(context, R.drawable.ic_notify_favorites)
                .setContentTitle(title)
                .setContentText(text)
                .setAutoCancel(true)

        when (intent.getStringExtra("mode") ?: "msg") {
            "msg" -> {
                val avatar = iconBitmap(context)
                val sender = Person.Builder()
                        .setName("Избранное")
                        .setIcon(IconCompat.createWithBitmap(avatar))
                        .build()
                builder.setStyle(
                        NotificationCompat.MessagingStyle(sender)
                                .setConversationTitle(title)
                                .addMessage(text, System.currentTimeMillis(), sender)
                )
            }
            "conv" -> {
                val avatarIcon = IconCompat.createWithBitmap(iconBitmap(context))
                val sender = Person.Builder()
                        .setName("Избранное")
                        .setIcon(avatarIcon)
                        .build()
                val shortcut = androidx.core.content.pm.ShortcutInfoCompat.Builder(context, SHORTCUT_ID)
                        .setShortLabel("Избранное")
                        .setIcon(avatarIcon)
                        .setPerson(sender)
                        .setLongLived(true)
                        .setIntent(
                                Intent(Intent.ACTION_VIEW)
                                        .setClass(context, forpdateam.ru.forpda.ui.activities.MainActivity::class.java)
                        )
                        .build()
                androidx.core.content.pm.ShortcutManagerCompat.pushDynamicShortcut(context, shortcut)
                builder.setShortcutId(SHORTCUT_ID)
                builder.setStyle(
                        NotificationCompat.MessagingStyle(sender)
                                .setConversationTitle(title)
                                .addMessage(text, System.currentTimeMillis(), sender)
                )
            }
            else -> builder.setStyle(NotificationCompat.BigTextStyle().bigText(text))
        }

        NotificationManagerCompat.from(context).notify(NOTIFY_ID, builder.build())
    }

    /** Цветная иконка приложения (дефолтный вариант), отрисованная в bitmap. */
    private fun iconBitmap(context: Context): Bitmap {
        val drawable = AppCompatResources.getDrawable(context, R.mipmap.ic_launcher)!!
        val bitmap = Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bitmap
    }

    companion object {
        private const val CHANNEL_ID = "debug_notif_icon_lab"
        private const val SHORTCUT_ID = "debug_notif_icon_lab_fav"
        private const val NOTIFY_ID = 991_991
    }
}
