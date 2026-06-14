package forpdateam.ru.forpda.notifications

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import forpdateam.ru.forpda.BuildConfig
import forpdateam.ru.forpda.R
import forpdateam.ru.forpda.entity.remote.events.NotificationEvent
import forpdateam.ru.forpda.model.data.remote.api.events.NotificationEventsApi
import forpdateam.ru.forpda.model.preferences.NotificationPreferencesHolder
import forpdateam.ru.forpda.model.repository.events.EventsRepository
import forpdateam.ru.forpda.ui.activities.MainActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Периодический фоновый воркер для опроса уведомлений, когда NotificationsService недоступен
 * (приложение убито системой / закрыто пользователем / DOZE).
 *
 * Запускается через PeriodicWorkRequest каждые ~15 минут. Дёргает getQmsEvents/getFavoritesEvents,
 * сравнивает с сохранёнными в SharedPreferences, и публикует уведомления о новых событиях напрямую
 * через NotificationManagerCompat (без поднятия Service — отсюда и без foreground-иконки).
 *
 * Фильтрация:
 *  - notifications.main.enabled — главный выключатель.
 *  - notifications.fav.enabled / qms.enabled / mentions.enabled — по каналам.
 *  - per-topic mute (notifications.muted_topic_ids) — конкретные темы избранного.
 */
@HiltWorker
class EventsCheckWorker @AssistedInject constructor(
        @Assisted private val appContext: Context,
        @Assisted params: WorkerParameters,
        private val eventsApi: NotificationEventsApi,
        private val prefs: NotificationPreferencesHolder,
        private val eventsRepository: EventsRepository
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        if (eventsRepository.isForegroundRealtimeActive()) {
            if (BuildConfig.DEBUG) {
                Log.i(NOTIFICATIONS_LOG_TAG, "Skip background check: foreground realtime active")
            }
            Timber.d("EventsCheckWorker: foreground realtime active, skip")
            return@withContext Result.success()
        }
        if (!prefs.getMainEnabled()) {
            if (BuildConfig.DEBUG) Log.i(NOTIFICATIONS_LOG_TAG, "Skip background check: app preference disabled")
            Timber.d("EventsCheckWorker: main disabled, skip")
            return@withContext Result.success()
        }
        if (!prefs.getBgCheckEnabled()) {
            if (BuildConfig.DEBUG) Log.i(NOTIFICATIONS_LOG_TAG, "Skip background check: background preference disabled")
            Timber.d("EventsCheckWorker: bgCheck disabled, skip")
            return@withContext Result.success()
        }

        runCatching { checkSource(NotificationEvent.Source.QMS) }
                .onFailure { Timber.e(it, "EventsCheckWorker QMS failed") }
        runCatching { checkSource(NotificationEvent.Source.THEME) }
                .onFailure { Timber.e(it, "EventsCheckWorker THEME failed") }

        Result.success()
    }

    private fun checkSource(source: NotificationEvent.Source) {
        // THEME-источник несёт и обычные события избранного, и упоминания в темах.
        // Включать опрос нужно, если ВКЛЮЧЁН хотя бы один из соответствующих переключателей.
        val channelEnabled = when (source) {
            NotificationEvent.Source.QMS -> prefs.getQmsEnabled()
            NotificationEvent.Source.THEME -> prefs.getFavEnabled() || prefs.getMentionsEnabled()
            else -> false
        }
        if (!channelEnabled) {
            if (BuildConfig.DEBUG) Log.i(NOTIFICATIONS_LOG_TAG, "Skip ${source.name} background check: category preference disabled")
            return
        }

        val saved = when (source) {
            NotificationEvent.Source.QMS -> prefs.getDataQmsEvents()
            NotificationEvent.Source.THEME -> prefs.getDataFavoritesEvents()
            else -> emptySet()
        }
        val savedResponse = saved.joinToString("\n")
        val savedEvents = when (source) {
            NotificationEvent.Source.QMS -> eventsApi.getQmsEvents(savedResponse)
            NotificationEvent.Source.THEME -> eventsApi.getFavoritesEvents(savedResponse)
            else -> return
        }

        val current = when (source) {
            NotificationEvent.Source.QMS -> eventsApi.getQmsEvents()
            NotificationEvent.Source.THEME -> eventsApi.getFavoritesEvents()
            else -> return
        }

        // Сохранить новый снимок в prefs (источник истины общий с EventsRepository)
        val savedSet = current.map { it.sourceEventText.orEmpty() }.toSet()
        when (source) {
            NotificationEvent.Source.QMS -> prefs.setDataQmsEvents(savedSet)
            NotificationEvent.Source.THEME -> prefs.setDataFavoritesEvents(savedSet)
            else -> Unit
        }

        // Найти новые события по timeStamp
        val newEvents = current.filter { loaded ->
            val sameSaved = savedEvents.firstOrNull { it.sourceId == loaded.sourceId }
            sameSaved == null || loaded.timeStamp > sameSaved.timeStamp
        }
        if (newEvents.isEmpty()) return

        // Применить per-topic mute (только для тем избранного — заглушаем и упоминания тоже)
        val mutedIds: Set<Int> = if (source == NotificationEvent.Source.THEME) prefs.getMutedTopics() else emptySet()
        val afterMute = if (mutedIds.isEmpty()) newEvents else newEvents.filterNot { it.sourceId in mutedIds }
        if (afterMute.isEmpty()) {
            if (BuildConfig.DEBUG) Log.i(NOTIFICATIONS_LOG_TAG, "Skip ${source.name} background notifications: all events muted")
            return
        }

        // Per-type фильтрация для THEME: упоминания и обычные события управляются разными переключателями.
        val finalEvents: List<NotificationEvent> = if (source == NotificationEvent.Source.THEME) {
            val favOn = prefs.getFavEnabled()
            val mentionsOn = prefs.getMentionsEnabled()
            val onlyImportant = prefs.getFavOnlyImportant()
            afterMute.filter { e ->
                if (e.isMention) {
                    mentionsOn
                } else {
                    favOn && (!onlyImportant || e.isImportant)
                }
            }
        } else {
            afterMute
        }
        if (finalEvents.isEmpty()) {
            if (BuildConfig.DEBUG) Log.i(NOTIFICATIONS_LOG_TAG, "Skip ${source.name} background notifications: category filter removed all events")
            return
        }

        for (e in finalEvents) {
            publish(e)
        }
    }

    @SuppressLint("MissingPermission")
    private fun publish(event: NotificationEvent) {
        val channelId = channelIdFor(event)
        val channelName = channelNameFor(event)

        NotificationsService.createEventChannels(appContext)
        ensureChannel(channelId, channelName)

        val title = titleFor(event)
        val text = textFor(event)
        val summary = summaryFor(event)

        val intentUrl = intentUrlFor(event)
        val notifyIntent = Intent(Intent.ACTION_VIEW, Uri.parse(intentUrl))
                .setClass(appContext, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val pi = PendingIntent.getActivity(
                appContext, event.notifyId(), notifyIntent,
                NotificationsService.activityPendingIntentFlags(0)
        )

        val builder = NotificationCompat.Builder(appContext, channelId)
                .setSmallIcon(smallIconFor(event))
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().setBigContentTitle(title).bigText(text).setSummaryText(summary))
                .setContentIntent(pi)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setCategory(NotificationCompat.CATEGORY_SOCIAL)

        applyLegacyAlertPreferences(builder)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (appContext.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                    != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                Log.w(NOTIFICATIONS_LOG_TAG, "Skip ${event.notificationLogCategory()} background notification: POST_NOTIFICATIONS denied")
                Timber.w("EventsCheckWorker publish skipped: POST_NOTIFICATIONS denied")
                return
            }
        }
        val notificationManager = NotificationManagerCompat.from(appContext)
        if (!notificationManager.areNotificationsEnabled()) {
            Log.w(NOTIFICATIONS_LOG_TAG, "Skip ${event.notificationLogCategory()} background notification: app notifications disabled by system")
            Timber.w("EventsCheckWorker publish skipped: app notifications disabled by system")
            return
        }
        notificationManager.notify(event.notifyId(), builder.build())
        if (BuildConfig.DEBUG) Log.i(NOTIFICATIONS_LOG_TAG, "Published ${event.notificationLogCategory()} background notification")
    }

    private fun applyLegacyAlertPreferences(builder: NotificationCompat.Builder) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) return

        var defaults = 0
        if (prefs.getMainSoundEnabled()) defaults = defaults or NotificationCompat.DEFAULT_SOUND
        if (prefs.getMainVibrationEnabled()) defaults = defaults or NotificationCompat.DEFAULT_VIBRATE
        if (prefs.getMainIndicatorEnabled()) defaults = defaults or NotificationCompat.DEFAULT_LIGHTS
        builder.setDefaults(defaults)
    }

    private fun channelIdFor(e: NotificationEvent): String = when {
        e.isMention -> NotificationsService.CHANNEL_MENTION_ID
        e.fromQms() -> NotificationsService.CHANNEL_QMS_ID
        e.fromTheme() -> NotificationsService.CHANNEL_FAV_ID
        else -> NotificationsService.CHANNEL_SITE_ID
    }

    private fun channelNameFor(e: NotificationEvent): String = when {
        e.isMention -> appContext.getString(R.string.notification_summary_mention)
        e.fromQms() -> appContext.getString(R.string.notification_summary_qms)
        e.fromTheme() -> appContext.getString(R.string.notification_summary_fav)
        else -> appContext.getString(R.string.notification_summary_comment)
    }

    private fun smallIconFor(e: NotificationEvent): Int = when {
        e.fromQms() -> R.drawable.ic_notify_qms
        e.fromTheme() && e.isMention -> R.drawable.ic_notify_mention
        e.fromTheme() -> R.drawable.ic_notify_favorites
        else -> R.drawable.ic_notify_qms
    }

    private fun titleFor(e: NotificationEvent): String = when {
        e.fromQms() -> if (e.userNick.isEmpty()) {
            appContext.getString(R.string.notification_title_qms_fallback)
        } else {
            appContext.getString(R.string.notification_title_qms_from_Nick, e.userNick)
        }
        e.fromTheme() && e.isMention -> if (e.userNick.isEmpty()) {
            appContext.getString(R.string.notification_title_mention_fallback)
        } else {
            appContext.getString(R.string.notification_title_mention_Nick, e.userNick)
        }
        e.fromTheme() -> appContext.getString(R.string.notification_title_favorite)
        else -> e.userNick
    }

    private fun textFor(e: NotificationEvent): String = when {
        e.fromQms() -> e.sourceTitle.ifBlank {
            appContext.resources.getQuantityString(
                    R.plurals.notification_content_qms_count,
                    e.msgCount,
                    e.msgCount
            )
        }
        e.fromTheme() && e.isMention -> e.sourceTitle.ifBlank {
            appContext.getString(R.string.notification_content_mention_fallback)
        }
        e.fromTheme() -> e.sourceTitle.ifBlank {
            appContext.getString(R.string.notification_content_theme_fallback)
        }
        else -> ""
    }

    private fun summaryFor(e: NotificationEvent): String = when {
        e.isMention -> appContext.getString(R.string.notification_summary_mention)
        e.fromQms() -> appContext.getString(R.string.notification_summary_qms)
        e.fromTheme() -> appContext.getString(R.string.notification_summary_fav)
        else -> ""
    }

    private fun intentUrlFor(e: NotificationEvent): String = when {
        e.fromQms() -> "https://4pda.to/forum/index.php?act=qms&mid=${e.userId}&t=${e.sourceId}"
        e.fromTheme() -> "https://4pda.to/forum/index.php?showtopic=${e.sourceId}&view=getnewpost"
        else -> ""
    }

    private fun ensureChannel(channelId: String, channelName: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_DEFAULT)
            appContext.getSystemService(NotificationManager::class.java)?.createNotificationChannel(ch)
        }
    }

    companion object {
        private const val NOTIFICATIONS_LOG_TAG = "Notifications"
        const val UNIQUE_NAME = "events_check_periodic"
    }
}

private fun NotificationEvent.notificationLogCategory(): String = when {
    isMention -> "mention"
    fromQms() -> "qms"
    fromTheme() -> "favorite"
    else -> "unknown"
}
