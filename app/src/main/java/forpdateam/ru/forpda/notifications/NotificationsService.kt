package forpdateam.ru.forpda.notifications

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.util.Log
import java.lang.ref.WeakReference
import timber.log.Timber
import android.widget.Toast
import androidx.annotation.DrawableRes
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import forpdateam.ru.forpda.BuildConfig
import forpdateam.ru.forpda.R
import forpdateam.ru.forpda.common.BatteryDebugLogger
import forpdateam.ru.forpda.common.BitmapUtils
import forpdateam.ru.forpda.common.ForPdaCoil
import forpdateam.ru.forpda.entity.remote.events.NotificationEvent
import forpdateam.ru.forpda.model.data.remote.api.ApiUtils
import forpdateam.ru.forpda.model.preferences.NotificationPreferencesHolder
import forpdateam.ru.forpda.model.repository.avatar.AvatarRepository
import forpdateam.ru.forpda.model.repository.events.EventsRepository
import forpdateam.ru.forpda.ui.activities.MainActivity
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/**
 * Created by radiationx on 31.07.17.
 */
@AndroidEntryPoint
class NotificationsService : Service() {

    @Inject lateinit var avatarRepository: AvatarRepository
    @Inject lateinit var eventsRepository: EventsRepository
    @Inject lateinit var notificationPreferencesHolder: NotificationPreferencesHolder

    private val myMessenger = Messenger(IncomingHandler(this))
    private var mNotificationManager: NotificationManagerCompat? = null
    private var lastHardCheckTime = 0L

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(serviceJob + Dispatchers.Main.immediate)

    @SuppressLint("MissingPermission")
    private fun notifySafe(id: Int, notification: android.app.Notification, event: NotificationEvent?, channelId: String) {
        val category = event?.notificationLogCategory() ?: "stack"
        // Последняя линия защиты - проверяем, включены ли уведомления
        if (!notificationPreferencesHolder.getMainEnabled()) {
            if (BuildConfig.DEBUG) Log.i(NOTIFICATIONS_LOG_TAG, "Skip $category notification: app preference disabled")
            if (BuildConfig.DEBUG) Timber.d("Notification publish skipped: main notifications disabled")
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                    != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                Log.w(NOTIFICATIONS_LOG_TAG, "Skip $category notification: POST_NOTIFICATIONS denied")
                Timber.w("Notification publish skipped: POST_NOTIFICATIONS denied")
                return
            }
        }
        if (!getNotificationManager().areNotificationsEnabled()) {
            Log.w(NOTIFICATIONS_LOG_TAG, "Skip $category notification: app notifications disabled by system")
            Timber.w("Notification publish skipped: app notifications disabled by system")
            return
        }
        getNotificationManager().notify(id, notification)
        if (BuildConfig.DEBUG) Log.i(NOTIFICATIONS_LOG_TAG, "Published $category notification")
    }

    override fun onBind(intent: Intent?): IBinder {
        Timber.v("onBind")
        BatteryDebugLogger.logState("NotificationsService", "bind")
        return myMessenger.binder
    }

    override fun onRebind(intent: Intent?) {
        Timber.v("onRebind")
        super.onRebind(intent)
    }

    override fun onUnbind(intent: Intent?): Boolean {
        Timber.v("onUnbind")
        BatteryDebugLogger.logState("NotificationsService", "unbind")
        return true
    }

    override fun onCreate() {
        super.onCreate()
        BatteryDebugLogger.logState("NotificationsService", "create")
        createEventChannels(this)
        serviceScope.launch {
            launch {
                notificationPreferencesHolder.mainEnabledFlow().collect { enabled ->
                    if (!enabled) {
                        // Принудительно останавливаем WebSocket перед остановкой сервиса
                        eventsRepository.onDestroy()
                        stopSelf()
                    }
                }
            }
            launch {
                notificationPreferencesHolder.favEnabledFlow().collect { enabled ->
                    if (enabled) {
                        eventsRepository.updateEvents(NotificationEvent.Source.THEME)
                    } else {
                        eventsRepository.disableEvents(NotificationEvent.Source.THEME)
                    }
                }
            }
            launch {
                notificationPreferencesHolder.qmsEnabledFlow().collect { enabled ->
                    if (enabled) {
                        eventsRepository.updateEvents(NotificationEvent.Source.QMS)
                    } else {
                        eventsRepository.disableEvents(NotificationEvent.Source.QMS)
                    }
                }
            }
            launch {
                eventsRepository.observeEvents().collect { sendNotification(it) }
            }
            launch {
                eventsRepository.observeEventsStack().collect {
                    sendNotifications(it)
                }
            }
            launch {
                eventsRepository.observeCancel().collect { cancelNotification(it) }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Timber.i("onStartCommand this$this : ${this@NotificationsService}")
        Timber.i("onStartCommand args$flags : $startId : $intent")
        BatteryDebugLogger.logState("NotificationsService", "startCommand", "action=${intent?.action}")

        // Проверяем главный переключатель уведомлений перед запуском
        if (!notificationPreferencesHolder.getMainEnabled()) {
            if (BuildConfig.DEBUG) Log.i(NOTIFICATIONS_LOG_TAG, "Skip service start: app preference disabled")
            stopSelf(startId)
            return START_NOT_STICKY
        }

        var checkEvents = intent?.action != null && intent.action == CHECK_LAST_EVENTS
        val time = System.currentTimeMillis()

        Timber.d("Handle check last events: $time : $lastHardCheckTime : ${time - lastHardCheckTime}")

        checkEvents = if (checkEvents && time - lastHardCheckTime >= 1000 * 60) {
            lastHardCheckTime = time
            true
        } else {
            false
        }
        eventsRepository.externalStart(checkEvents)
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        BatteryDebugLogger.logState("NotificationsService", "destroy")
        serviceScope.cancel()  // Отменяем все корутины
        serviceJob.cancel()
        // EventsRepository является singleton'ом. Не отменяем его repoScope из lifecycle
        // сервиса, иначе последующие события обновляют вкладки, но уже не доходят до notify().
        super.onDestroy()
        Timber.i("onDestroy")
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        Timber.i("onTaskRemoved")
    }

    private fun getNotificationManager(): NotificationManagerCompat {
        return mNotificationManager ?: NotificationManagerCompat.from(this).also {
            mNotificationManager = it
        }
    }

    private fun cancelNotification(event: NotificationEvent) {
        getNotificationManager().cancel(event.notifyId())
    }

    fun sendNotification(event: NotificationEvent) {
        // Проверяем главный переключатель уведомлений
        if (!notificationPreferencesHolder.getMainEnabled()) {
            if (BuildConfig.DEBUG) Log.i(NOTIFICATIONS_LOG_TAG, "Skip incoming notification: app preference disabled")
            return
        }
        if (notificationPreferencesHolder.getMainAvatarsEnabled()) {
            val res: Resources = this.resources
            val height = res.getDimension(android.R.dimen.notification_large_icon_height).toInt()
            val width = res.getDimension(android.R.dimen.notification_large_icon_width).toInt()
            serviceScope.launch {
                runCatching {
                    val url = avatarRepository.getAvatar(event.userId, event.userNick)
                    val bitmap = ForPdaCoil.loadBitmapForNotification(this@NotificationsService, url, width, height)
                    var b = bitmap
                    b = BitmapUtils.centerCrop(b, width, height, 1.0f)
                    BitmapUtils.createAvatar(b, width, height, true)
                }.onSuccess { avatar ->
                    sendNotification(event, avatar)
                }.onFailure {
                    Timber.e(it, "Notification avatar load failed")
                    // Аватар не должен блокировать само уведомление: иначе счетчики обновятся,
                    // а событие не попадет в системную шторку.
                    sendNotification(event, null)
                }
            }
        } else {
            sendNotification(event, null)
        }
    }

    fun sendNotification(event: NotificationEvent, avatar: Bitmap?) {
        // Двойная проверка mainEnabled
        if (!notificationPreferencesHolder.getMainEnabled()) {
            if (BuildConfig.DEBUG) Log.i(NOTIFICATIONS_LOG_TAG, "Skip incoming notification: app preference disabled")
            return
        }
        val title = createTitle(event)
        val text = createContent(event)
        val summaryText = createSummary(event)

        val bigTextStyle = NotificationCompat.BigTextStyle()
        bigTextStyle.setBigContentTitle(title)
        bigTextStyle.bigText(text)
        bigTextStyle.setSummaryText(summaryText)

        val channelId = getChannelId(event)
        val channelName = getChannelName(event)

        ensureChannel(channelId, channelName)

        val builder = NotificationCompat.Builder(this, channelId)

        if (avatar != null && !event.fromSite()) {
            builder.setLargeIcon(avatar)
        }
        builder.setSmallIcon(createSmallIcon(event))

        builder.setContentTitle(title)
        builder.setContentText(text)
        builder.setStyle(bigTextStyle)
        builder.setChannelId(channelId)

        val intentUrl = createIntentUrl(event)
        val notifyIntent = Intent(Intent.ACTION_VIEW, Uri.parse(intentUrl))
        notifyIntent.setClass(this, MainActivity::class.java)
        notifyIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val notifyPendingIntent = PendingIntent.getActivity(
                this, event.notifyId(), notifyIntent,
                activityPendingIntentFlags(0)
        )
        builder.setContentIntent(notifyPendingIntent)

        configureNotification(builder)

        getNotificationManager().cancel(event.notifyId())
        notifySafe(event.notifyId(), builder.build(), event, channelId)
    }

    fun sendNotifications(events: List<NotificationEvent>) {
        // Проверяем главный переключатель уведомлений
        if (!notificationPreferencesHolder.getMainEnabled()) {
            if (BuildConfig.DEBUG) Log.i(NOTIFICATIONS_LOG_TAG, "Skip stacked notification: app preference disabled")
            return
        }
        val title = createStackedTitle(events)
        val text = createStackedContent(events)
        val summaryText = createStackedSummary(events)

        val bigTextStyle = NotificationCompat.BigTextStyle()
        bigTextStyle.setBigContentTitle(title)
        bigTextStyle.bigText(text)
        bigTextStyle.setSummaryText(summaryText)

        val channelId = getChannelId(events[0])
        val channelName = getChannelName(events[0])

        ensureChannel(channelId, channelName)

        val builder = NotificationCompat.Builder(this, channelId)

        builder.setSmallIcon(createStackedSmallIcon(events))

        builder.setContentTitle(title)
        builder.setContentText(text)
        builder.setStyle(bigTextStyle)
        builder.setChannelId(channelId)

        val stackedUrl = createStackedIntentUrl(events)
        val notifyIntent = Intent(Intent.ACTION_VIEW, Uri.parse(stackedUrl))
        notifyIntent.setClass(this, MainActivity::class.java)
        notifyIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val notifyPendingIntent = PendingIntent.getActivity(
                this, events[0].notifyId(), notifyIntent,
                activityPendingIntentFlags(0)
        )
        builder.setContentIntent(notifyPendingIntent)

        configureNotification(builder)

        var id = 0
        val event = events[0]
        id = when {
            event.fromQms() -> NOTIFY_STACKED_QMS_ID
            event.fromTheme() -> NOTIFY_STACKED_FAV_ID
            else -> id
        }
        notifySafe(id, builder.build(), event, channelId)
    }

    private fun ensureChannel(channelId: String, channelName: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_DEFAULT)
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
    }

    private fun configureNotification(builder: NotificationCompat.Builder) {
        builder.setAutoCancel(true)
        builder.setPriority(NotificationCompat.PRIORITY_DEFAULT)
        builder.setCategory(NotificationCompat.CATEGORY_SOCIAL)
        applyLegacyAlertPreferences(builder)
    }

    private fun applyLegacyAlertPreferences(builder: NotificationCompat.Builder) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) return

        var defaults = 0
        if (notificationPreferencesHolder.getMainSoundEnabled()) {
            defaults = defaults or NotificationCompat.DEFAULT_SOUND
        }
        if (notificationPreferencesHolder.getMainVibrationEnabled()) {
            defaults = defaults or NotificationCompat.DEFAULT_VIBRATE
        }
        if (notificationPreferencesHolder.getMainIndicatorEnabled()) {
            defaults = defaults or NotificationCompat.DEFAULT_LIGHTS
        }
        builder.setDefaults(defaults)
    }

    private fun getChannelId(event: NotificationEvent): String {
        if (event.isMention) return CHANNEL_MENTION_ID
        if (event.fromQms()) return CHANNEL_QMS_ID
        if (event.fromTheme()) return CHANNEL_FAV_ID
        return CHANNEL_SITE_ID
    }

    private fun getChannelName(event: NotificationEvent): String {
        if (event.isMention) return getString(R.string.notification_summary_mention)
        if (event.fromQms()) return getString(R.string.notification_summary_qms)
        if (event.fromTheme()) return getString(R.string.notification_summary_fav)
        return getString(R.string.notification_summary_comment)
    }

    @DrawableRes
    fun createSmallIcon(event: NotificationEvent): Int {
        if (event.fromQms()) return R.drawable.ic_notify_qms
        if (event.fromTheme()) {
            if (event.isMention) return R.drawable.ic_notify_mention
            return R.drawable.ic_notify_favorites
        }
        if (event.fromSite()) return R.drawable.ic_notify_site
        return R.drawable.ic_notify_qms
    }

    fun createTitle(event: NotificationEvent): String {
        if (event.fromQms()) {
            val nick = event.userNick
            return if (nick.isEmpty()) {
                getString(R.string.notification_title_qms_fallback)
            } else {
                getString(R.string.notification_title_qms_from_Nick, nick)
            }
        }
        if (event.fromSite()) return "ForPDA"
        if (event.fromTheme() && event.isMention) {
            val nick = event.userNick
            return if (nick.isEmpty()) {
                getString(R.string.notification_title_mention_fallback)
            } else {
                getString(R.string.notification_title_mention_Nick, nick)
            }
        }
        if (event.fromTheme()) {
            return getString(R.string.notification_title_favorite)
        }
        return event.userNick
    }

    fun createContent(event: NotificationEvent): String {
        if (event.fromQms()) {
            return event.sourceTitle.ifBlank {
                resources.getQuantityString(
                        R.plurals.notification_content_qms_count,
                        event.msgCount,
                        event.msgCount
                )
            }
        }
        if (event.fromTheme()) {
            if (event.isMention) {
                return event.sourceTitle.ifBlank { getString(R.string.notification_content_mention_fallback) }
            }
            return event.sourceTitle.ifBlank { getString(R.string.notification_content_theme_fallback) }
        }
        if (event.fromSite()) return getString(R.string.notification_content_news)
        return ""
    }

    fun createSummary(event: NotificationEvent): String {
        if (event.isMention) return getString(R.string.notification_summary_mention)
        if (event.fromQms()) return getString(R.string.notification_summary_qms)
        if (event.fromTheme()) return getString(R.string.notification_summary_fav)
        if (event.fromSite()) return getString(R.string.notification_summary_comment)
        return ""
    }

    fun createIntentUrl(event: NotificationEvent): String {
        if (event.isMention) {
            if (event.fromTheme()) {
                return "https://4pda.to/forum/index.php?showtopic=${event.sourceId}&view=findpost&p=${event.messageId}"
            }
            if (event.fromSite()) {
                return "https://4pda.to/index.php?p=${event.sourceId}/#comment${event.messageId}"
            }
        }
        if (event.fromQms()) {
            return "https://4pda.to/forum/index.php?act=qms&mid=${event.userId}&t=${event.sourceId}"
        }
        if (event.fromTheme()) {
            return "https://4pda.to/forum/index.php?showtopic=${event.sourceId}&view=getnewpost"
        }
        return ""
    }

    private fun createStackedTitle(events: List<NotificationEvent>): String =
            createStackedSummary(events)

    private fun createStackedContent(events: List<NotificationEvent>): CharSequence {
        val content = StringBuilder()
        val size = minOf(events.size, STACKED_MAX)
        for (i in 0 until size) {
            val event = events[i]
            if (event.fromQms()) {
                var nick = event.userNick
                if (nick.isEmpty()) nick = getString(R.string.notification_title_qms_fallback)
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
            content.append("...и еще ").append(events.size - size)
        }
        return ApiUtils.spannedFromHtml(content.toString()) ?: content
    }

    private fun createStackedSummary(events: List<NotificationEvent>): String =
            createSummary(events[0])

    @DrawableRes
    fun createStackedSmallIcon(events: List<NotificationEvent>): Int =
            createSmallIcon(events[0])

    private fun createStackedIntentUrl(events: List<NotificationEvent>): String {
        val event = events[0]
        if (event.fromQms()) return "https://4pda.to/forum/index.php?act=qms"
        if (event.fromTheme()) return "https://4pda.to/forum/index.php?act=fav"
        return ""
    }

    private class IncomingHandler(service: NotificationsService) : Handler(Looper.getMainLooper()) {
        private val serviceRef: WeakReference<NotificationsService> = WeakReference(service)

        override fun handleMessage(msg: Message) {
            serviceRef.get()?.let { service ->
                Toast.makeText(service.applicationContext, "" + msg.data, Toast.LENGTH_SHORT).show()
            }
        }
    }

    companion object {
        private val LOG_TAG = NotificationsService::class.java.simpleName
        private const val NOTIFICATIONS_LOG_TAG = "Notifications"
        private const val LEGACY_CHANNEL_DEFAULT_ID = "forpda_channel_default"
        const val CHANNEL_FAV_ID = "forpda_channel_fav"
        const val CHANNEL_QMS_ID = "forpda_channel_qms"
        const val CHANNEL_MENTION_ID = "forpda_channel_mention"
        const val CHANNEL_SITE_ID = "forpda_channel_site"

        const val CHECK_LAST_EVENTS = "CHECK_LAST_EVENTS"
        private const val NOTIFY_STACKED_QMS_ID = -123
        private const val NOTIFY_STACKED_FAV_ID = -234
        private const val STACKED_MAX = 4

        /** API 31+: обязателен FLAG_IMMUTABLE или FLAG_MUTABLE; для открытия активности по тапу достаточно IMMUTABLE. */
        @JvmStatic
        fun activityPendingIntentFlags(base: Int): Int {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                base or PendingIntent.FLAG_IMMUTABLE
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                base or PendingIntent.FLAG_IMMUTABLE
            } else {
                base
            }
        }

        @JvmStatic
        fun createEventChannels(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val manager = context.getSystemService(NotificationManager::class.java) ?: return
            manager.deleteNotificationChannel(LEGACY_CHANNEL_DEFAULT_ID)
            manager.createNotificationChannels(listOf(
                    NotificationChannel(
                            CHANNEL_QMS_ID,
                            context.getString(R.string.notification_summary_qms),
                            NotificationManager.IMPORTANCE_DEFAULT
                    ),
                    NotificationChannel(
                            CHANNEL_MENTION_ID,
                            context.getString(R.string.notification_summary_mention),
                            NotificationManager.IMPORTANCE_DEFAULT
                    ),
                    NotificationChannel(
                            CHANNEL_FAV_ID,
                            context.getString(R.string.notification_summary_fav),
                            NotificationManager.IMPORTANCE_DEFAULT
                    ),
                    NotificationChannel(
                            CHANNEL_SITE_ID,
                            context.getString(R.string.notification_summary_comment),
                            NotificationManager.IMPORTANCE_DEFAULT
                    )
            ))
            if (BuildConfig.DEBUG) Log.i(NOTIFICATIONS_LOG_TAG, "Ensured event notification channels")
        }

        /**
         * Исторически этот метод делал start+bind. На современных Android/OEM bind из "внезапных" мест
         * (ресиверы/wake-up) может приводить к ANR. Оставляем безопасный вариант по умолчанию.
         */
        @JvmStatic
        fun startAndCheck(context: Context) {
            startAndCheckNoBind(context)
        }

        /** Вариант для BroadcastReceiver: без bind (может затянуться и привести к ANR). */
        @JvmStatic
        fun startAndCheckNoBind(context: Context) {
            try {
                BatteryDebugLogger.logState("NotificationsService", "startAndCheckNoBind")
                val intent = Intent(context, NotificationsService::class.java)
                        .setAction(CHECK_LAST_EVENTS)
                context.startService(intent)
            } catch (ignore: Exception) {
            }
        }
    }
}

private fun NotificationEvent.notificationLogCategory(): String = when {
    isMention -> "mention"
    fromQms() -> "qms"
    fromTheme() -> "favorite"
    fromSite() -> "site"
    else -> "unknown"
}
