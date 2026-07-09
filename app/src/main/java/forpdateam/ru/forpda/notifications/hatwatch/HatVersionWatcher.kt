package forpdateam.ru.forpda.notifications.hatwatch

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import forpdateam.ru.forpda.R
import forpdateam.ru.forpda.model.data.remote.api.theme.ThemeApi
import forpdateam.ru.forpda.model.preferences.NotificationPreferencesHolder
import forpdateam.ru.forpda.notifications.NotificationsService
import forpdateam.ru.forpda.ui.activities.MainActivity
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Детектор «в шапку темы добавили новый apk».
 *
 * Единственный источник истины для фичи «Следить за новыми версиями»: грузит первую страницу темы,
 * берёт пост-шапку (пост №1), извлекает множество attach-id apk-файлов ([HatApkExtractor]) и сравнивает
 * с сохранённым снимком. Появился новый id → публикует уведомление «Новая версия».
 *
 * Вызывается из ДВУХ планировщиков (оба — не с главного потока):
 *  - realtime WebSocket по событию HAT_EDITED (пока приложение открыто) — точечно по одной теме;
 *  - периодический [forpdateam.ru.forpda.notifications.EventsCheckWorker] (приложение закрыто) — обход watch-list.
 *
 * Публикует напрямую через [NotificationManagerCompat] (как EventsCheckWorker), чтобы работать
 * одинаково и в foreground, и в фоне без поднятия Service.
 */
@Singleton
class HatVersionWatcher @Inject constructor(
        @ApplicationContext private val context: Context,
        private val themeApi: ThemeApi,
        private val prefs: NotificationPreferencesHolder,
) {

    /**
     * Проверяет одну тему. Блокирующий сетевой вызов — вызывать только с IO/фонового потока.
     * Возвращает `true`, если было опубликовано уведомление о новой версии.
     */
    fun check(topicId: Int): Boolean {
        if (topicId <= 0) return false
        if (!prefs.getMainEnabled()) return false
        if (!prefs.getHatEnabled()) return false
        if (!prefs.isHatWatched(topicId)) return false

        val page = runCatching {
            themeApi.getTheme(
                    "https://4pda.to/forum/index.php?showtopic=$topicId&st=0",
                    /* hatOpen = */ true,
                    /* pollOpen = */ false
            )
        }.getOrElse {
            Timber.e(it, "HatVersionWatcher: load topic $topicId failed")
            return false
        }

        // Строго «только в шапке»: пост №1 первой страницы (fallback — первый пост).
        val hatPost = page.posts.firstOrNull { it.number == 1 } ?: page.posts.firstOrNull() ?: return false
        val currentApks = HatApkExtractor.extract(hatPost.body)
        val currentIds = currentApks.map { it.id }.toSet()

        val hadSnapshot = prefs.hasHatApkSnapshot(topicId)
        val saved = prefs.getHatApkSnapshot(topicId)
        // Всегда обновляем снимок актуальным состоянием.
        prefs.setHatApkSnapshot(topicId, currentIds)

        // Первый заход = только эталон, без пуша.
        if (!hadSnapshot) return false

        val newIds = currentIds - saved
        if (newIds.isEmpty()) return false

        val newNames = currentApks.filter { it.id in newIds }.map { it.name }
        publish(topicId, page.title, newNames)
        return true
    }

    @SuppressLint("MissingPermission")
    private fun publish(topicId: Int, topicTitle: String?, newApkNames: List<String>) {
        if (!prefs.getMainEnabled() || !prefs.getHatEnabled()) return

        ensureChannel()

        val title = context.getString(R.string.notification_hat_title)
        val fileLine = newApkNames.firstOrNull().orEmpty()
        val topic = topicTitle?.takeIf { it.isNotBlank() }
        val text = when {
            topic != null && fileLine.isNotBlank() -> "$topic — $fileLine"
            topic != null -> topic
            fileLine.isNotBlank() -> fileLine
            else -> context.getString(R.string.notification_hat_fallback)
        }

        val intentUrl = "https://4pda.to/forum/index.php?showtopic=$topicId&st=0"
        val notifyIntent = Intent(Intent.ACTION_VIEW, Uri.parse(intentUrl))
                .setClass(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val notifyId = ("hat_$topicId").hashCode()
        val pi = PendingIntent.getActivity(
                context, notifyId, notifyIntent,
                NotificationsService.activityPendingIntentFlags(0)
        )

        val builder = NotificationCompat.Builder(context, NotificationsService.CHANNEL_HAT_ID)
                .setSmallIcon(R.drawable.ic_notify_favorites)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().setBigContentTitle(title).bigText(text))
                .setContentIntent(pi)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setCategory(NotificationCompat.CATEGORY_SOCIAL)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                Timber.w("HatVersionWatcher: POST_NOTIFICATIONS denied, skip")
                return
            }
        }
        val nm = NotificationManagerCompat.from(context)
        if (!nm.areNotificationsEnabled()) {
            Timber.w("HatVersionWatcher: notifications disabled by system, skip")
            return
        }
        nm.notify(notifyId, builder.build())
        Timber.i("HatVersionWatcher: published new-version notification for topic $topicId")
    }

    private fun ensureChannel() {
        NotificationsService.createEventChannels(context)
        val ch = NotificationChannel(
                NotificationsService.CHANNEL_HAT_ID,
                context.getString(R.string.notification_hat_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT
        )
        context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(ch)
    }
}
