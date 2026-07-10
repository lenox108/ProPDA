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
import forpdateam.ru.forpda.R
import forpdateam.ru.forpda.entity.remote.events.NotificationEvent
import forpdateam.ru.forpda.model.preferences.NotificationPreferencesHolder
import forpdateam.ru.forpda.model.data.remote.api.ApiUtils
import forpdateam.ru.forpda.ui.activities.MainActivity
import timber.log.Timber

/**
 * Единая сборка системных уведомлений для foreground-сервиса и фонового воркера.
 * До этого у каждого была своя копия билдера, и они успели разойтись: воркер не умел
 * ни аватарки, ни stacked-уведомления, ни события сайта.
 */
object NotificationPublisher {

    private const val NOTIFICATIONS_LOG_TAG = "Notifications"
    const val NOTIFY_STACKED_QMS_ID = -123
    const val NOTIFY_STACKED_FAV_ID = -234
    private const val STACKED_MAX = 4
    // Switch to InboxStyle at 4+ events so each line stays scannable. Below
    // the threshold the joined BigText looks more natural than a list.
    private const val INBOX_STYLE_THRESHOLD = 4
    private const val INBOX_STYLE_MAX_LINES = 6

    /** @return ID опубликованного уведомления либо null, если публикация не состоялась. */
    @SuppressLint("MissingPermission")
    fun publish(
            context: Context,
            prefs: NotificationPreferencesHolder,
            event: NotificationEvent,
            intentUrlOverride: String? = null,
            largeIcon: android.graphics.Bitmap? = null,
    ): Int? {
        if (!prefs.getMainEnabled()) return null

        val channelId = channelIdFor(event)
        NotificationsService.createEventChannels(context)
        ensureChannel(context, channelId, channelNameFor(context, event))

        val title = titleFor(context, event)
        val text = textFor(context, event)
        val summary = summaryFor(context, event)
        val intentUrl = intentUrlOverride ?: intentUrlFor(event)

        val notifyIntent = Intent(Intent.ACTION_VIEW, Uri.parse(intentUrl))
                .setClass(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val pi = PendingIntent.getActivity(
                context,
                event.notifyId(),
                notifyIntent,
                NotificationsService.activityPendingIntentFlags(0)
        )

        val builder = NotificationCompat.Builder(context, channelId)
                .setSmallIcon(smallIconFor(event))
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(
                        NotificationCompat.BigTextStyle()
                                .setBigContentTitle(title)
                                .bigText(text)
                                .setSummaryText(summary)
                )
                .setContentIntent(pi)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setCategory(NotificationCompat.CATEGORY_SOCIAL)

        if (largeIcon != null && !event.fromSite()) {
            builder.setLargeIcon(largeIcon)
        }
        applyLegacyAlerts(prefs, builder)
        NotificationActions.apply(context, builder, event)

        if (!canNotify(context, event.notificationLogCategory())) return null
        val manager = NotificationManagerCompat.from(context)
        val notifyId = event.notifyId()
        manager.notify(notifyId, builder.build())
        Log.i(NOTIFICATIONS_LOG_TAG, "Published ${event.notificationLogCategory()} notification")
        return notifyId
    }

    /** @return ID опубликованного stacked-уведомления либо null, если публикация не состоялась. */
    @SuppressLint("MissingPermission")
    fun publishStacked(
            context: Context,
            prefs: NotificationPreferencesHolder,
            events: List<NotificationEvent>,
    ): Int? {
        if (events.isEmpty() || !prefs.getMainEnabled()) return null

        val first = events.first()
        val channelId = channelIdFor(first)
        NotificationsService.createEventChannels(context)
        ensureChannel(context, channelId, channelNameFor(context, first))

        val title = summaryFor(context, first)
        val text = stackedContentFor(context, events)
        val summary = summaryFor(context, first)
        val intentUrl = stackedIntentUrlFor(first)

        val notifyIntent = Intent(Intent.ACTION_VIEW, Uri.parse(intentUrl))
                .setClass(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val notifyId = stackedNotifyId(first)
        val pi = PendingIntent.getActivity(
                context,
                notifyId,
                notifyIntent,
                NotificationsService.activityPendingIntentFlags(0)
        )

        val builder = NotificationCompat.Builder(context, channelId)
                .setSmallIcon(smallIconFor(first))
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(stackedStyle(context, events, title, summary))
                .setNumber(events.size.coerceAtMost(99))
                .setContentIntent(pi)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setCategory(NotificationCompat.CATEGORY_SOCIAL)

        applyLegacyAlerts(prefs, builder)

        if (!canNotify(context, "stacked")) return null
        val manager = NotificationManagerCompat.from(context)
        manager.notify(notifyId, builder.build())
        Log.i(NOTIFICATIONS_LOG_TAG, "Published stacked ${first.notificationLogCategory()} notification, count=${events.size}")
        return notifyId
    }

    /**
     * На Android 8+ звук, вибрация и индикатор — свойства канала, приложение их переопределить
     * не может (и не должно: пользователь настраивает канал в системных настройках). До Oreo
     * каналов нет, и единственный способ уважить наши переключатели — выставить defaults здесь.
     */
    private fun applyLegacyAlerts(prefs: NotificationPreferencesHolder, builder: NotificationCompat.Builder) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) return
        var defaults = 0
        if (prefs.getMainSoundEnabled()) defaults = defaults or android.app.Notification.DEFAULT_SOUND
        if (prefs.getMainVibrationEnabled()) defaults = defaults or android.app.Notification.DEFAULT_VIBRATE
        if (prefs.getMainIndicatorEnabled()) defaults = defaults or android.app.Notification.DEFAULT_LIGHTS
        builder.setDefaults(defaults)
    }

    private fun canNotify(context: Context, category: String): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            Log.w(NOTIFICATIONS_LOG_TAG, "Skip $category notification: POST_NOTIFICATIONS denied")
            return false
        }
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            Log.w(NOTIFICATIONS_LOG_TAG, "Skip $category notification: disabled by system")
            return false
        }
        return true
    }

    fun cancel(context: Context, event: NotificationEvent) {
        NotificationManagerCompat.from(context).cancel(event.notifyId())
    }

    fun channelIdFor(e: NotificationEvent): String = when {
        e.isMention -> NotificationsService.CHANNEL_MENTION_ID
        e.fromQms() -> NotificationsService.CHANNEL_QMS_ID
        e.fromTheme() -> NotificationsService.CHANNEL_FAV_ID
        else -> NotificationsService.CHANNEL_SITE_ID
    }

    fun channelNameFor(context: Context, e: NotificationEvent): String = when {
        e.isMention -> context.getString(R.string.notification_summary_mention)
        e.fromQms() -> context.getString(R.string.notification_summary_qms)
        e.fromTheme() -> context.getString(R.string.notification_summary_fav)
        else -> context.getString(R.string.notification_summary_comment)
    }

    fun smallIconFor(e: NotificationEvent): Int = when {
        e.fromQms() -> R.drawable.ic_notify_qms
        e.fromTheme() && e.isMention -> R.drawable.ic_notify_mention
        e.fromTheme() -> R.drawable.ic_notify_favorites
        e.fromSite() -> R.drawable.ic_notify_site
        else -> R.drawable.ic_notify_qms
    }

    fun titleFor(context: Context, e: NotificationEvent): String = when {
        e.fromQms() -> if (e.userNick.isEmpty()) {
            context.getString(R.string.notification_title_qms_fallback)
        } else {
            context.getString(R.string.notification_title_qms_from_Nick, e.userNick)
        }
        e.fromTheme() && e.isMention -> if (e.userNick.isEmpty()) {
            context.getString(R.string.notification_title_mention_fallback)
        } else {
            context.getString(R.string.notification_title_mention_Nick, e.userNick)
        }
        e.fromSite() -> "ForPDA"
        e.fromTheme() -> context.getString(R.string.notification_title_favorite)
        else -> e.userNick
    }

    fun textFor(context: Context, e: NotificationEvent): String = when {
        e.fromQms() -> e.sourceTitle.ifBlank {
            context.resources.getQuantityString(
                    R.plurals.notification_content_qms_count,
                    e.msgCount,
                    e.msgCount
            )
        }
        e.fromTheme() && e.isMention -> e.sourceTitle.ifBlank {
            context.getString(R.string.notification_content_mention_fallback)
        }
        e.fromSite() -> e.sourceTitle.ifBlank {
            context.getString(R.string.notification_content_news)
        }
        e.fromTheme() -> e.sourceTitle.ifBlank {
            context.getString(R.string.notification_content_theme_fallback)
        }
        else -> ""
    }

    fun summaryFor(context: Context, e: NotificationEvent): String = when {
        e.isMention -> context.getString(R.string.notification_summary_mention)
        e.fromQms() -> context.getString(R.string.notification_summary_qms)
        e.fromTheme() -> context.getString(R.string.notification_summary_fav)
        e.fromSite() -> context.getString(R.string.notification_summary_comment)
        else -> ""
    }

    fun intentUrlFor(e: NotificationEvent): String = when {
        e.isMention && e.fromTheme() ->
            "https://4pda.to/forum/index.php?showtopic=${e.sourceId}&view=findpost&p=${e.messageId}"
        e.isMention && e.fromSite() && e.sourceId > 0 && e.messageId > 0 ->
            "https://4pda.to/index.php?p=${e.sourceId}/#comment${e.messageId}"
        e.fromQms() -> "https://4pda.to/forum/index.php?act=qms&mid=${e.userId}&t=${e.sourceId}"
        e.fromTheme() -> "https://4pda.to/forum/index.php?showtopic=${e.sourceId}&view=getnewpost"
        else -> "https://4pda.to/forum/index.php?act=mentions"
    }

    fun stackedNotifyId(first: NotificationEvent): Int = when {
        first.fromQms() -> NOTIFY_STACKED_QMS_ID
        first.fromTheme() -> NOTIFY_STACKED_FAV_ID
        else -> first.notifyId()
    }

    fun stackedIntentUrlFor(first: NotificationEvent): String = when {
        first.fromQms() -> "https://4pda.to/forum/index.php?act=qms"
        first.fromTheme() -> "https://4pda.to/forum/index.php?act=fav"
        else -> "https://4pda.to/forum/index.php?act=mentions"
    }

    /**
     * Builds the [NotificationCompat.Style] for [publishStacked]. Uses
     * [NotificationCompat.InboxStyle] for 4+ events so the user can read each
     * line individually; falls back to [NotificationCompat.BigTextStyle] for
     * small stacks where the joined text is the more natural form. See AUDIT-L12.
     */
    internal fun stackedStyle(
            context: Context,
            events: List<NotificationEvent>,
            title: CharSequence,
            summary: CharSequence,
    ): NotificationCompat.Style {
        if (events.size >= INBOX_STYLE_THRESHOLD) {
            val inbox = NotificationCompat.InboxStyle()
                    .setBigContentTitle(title)
                    .setSummaryText(summary)
            val show = minOf(events.size, INBOX_STYLE_MAX_LINES)
            for (i in 0 until show) {
                val event = events[i]
                inbox.addLine(stackedLineFor(context, event))
            }
            if (events.size > show) {
                inbox.addLine(context.getString(R.string.notification_stacked_more, events.size - show))
            }
            return inbox
        }
        return NotificationCompat.BigTextStyle()
                .setBigContentTitle(title)
                .bigText(stackedContentFor(context, events))
                .setSummaryText(summary)
    }

    private fun stackedLineFor(context: Context, e: NotificationEvent): CharSequence {
        if (e.fromQms()) {
            val nick = e.userNick.ifBlank { context.getString(R.string.notification_title_qms_fallback) }
            return "$nick: ${e.sourceTitle}"
        }
        return e.sourceTitle.ifBlank { context.getString(R.string.notification_content_theme_fallback) }
    }

    fun stackedContentFor(context: Context, events: List<NotificationEvent>): CharSequence {
        val content = StringBuilder()
        val size = minOf(events.size, STACKED_MAX)
        for (i in 0 until size) {
            val event = events[i]
            if (event.fromQms()) {
                var nick = event.userNick
                if (nick.isEmpty()) nick = context.getString(R.string.notification_title_qms_fallback)
                content.append("<b>").append(nick).append("</b>")
                content.append(": ").append(event.sourceTitle)
            } else if (event.fromTheme()) {
                content.append(event.sourceTitle)
            }
            if (i < size - 1) {
                content.append("<br>")
            }
        }
        if (events.size > size) {
            content.append("<br>")
            content.append(context.getString(R.string.notification_stacked_more, events.size - size))
        }
        return ApiUtils.spannedFromHtml(content.toString()) ?: content
    }

    /** Каналов до Android 8 нет, а сам класс NotificationChannel появился только в API 26. */
    private fun ensureChannel(context: Context, channelId: String, channelName: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val ch = NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_DEFAULT)
        context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(ch)
    }
}

private fun NotificationEvent.notificationLogCategory(): String = when {
    isMention && fromSite() -> "site"
    isMention -> "mention"
    fromQms() -> "qms"
    fromTheme() -> "favorite"
    else -> "unknown"
}
