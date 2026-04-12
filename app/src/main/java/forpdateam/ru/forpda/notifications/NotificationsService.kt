package forpdateam.ru.forpda.notifications

import android.annotation.SuppressLint
import android.app.AlarmManager
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
import android.widget.Toast
import androidx.annotation.DrawableRes
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import forpdateam.ru.forpda.App
import forpdateam.ru.forpda.R
import forpdateam.ru.forpda.common.BitmapUtils
import forpdateam.ru.forpda.common.ForPdaCoil
import forpdateam.ru.forpda.entity.remote.events.NotificationEvent
import forpdateam.ru.forpda.model.data.remote.api.ApiUtils
import forpdateam.ru.forpda.model.preferences.NotificationPreferencesHolder
import forpdateam.ru.forpda.model.repository.avatar.AvatarRepository
import forpdateam.ru.forpda.model.repository.events.EventsRepository
import forpdateam.ru.forpda.ui.activities.MainActivity
import io.reactivex.SingleSource
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.disposables.Disposable
import io.reactivex.functions.Function
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/**
 * Created by radiationx on 31.07.17.
 */
class NotificationsService : Service() {

    private val myMessenger = Messenger(IncomingHandler())
    private var mNotificationManager: NotificationManagerCompat? = null
    private var lastHardCheckTime = 0L

    private val avatarRepository: AvatarRepository = App.get().Di().avatarRepository
    private val eventsRepository: EventsRepository = App.get().Di().eventsRepository
    private val notificationPreferencesHolder: NotificationPreferencesHolder =
            App.get().Di().notificationPreferencesHolder

    private val rxDisposables = CompositeDisposable()
    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(serviceJob + Dispatchers.Main.immediate)

    private fun addToDisposable(disposable: Disposable) {
        rxDisposables.add(disposable)
    }

    override fun onBind(intent: Intent?): IBinder {
        Log.v(LOG_TAG, "onBind")
        return myMessenger.binder
    }

    override fun onRebind(intent: Intent?) {
        Log.v(LOG_TAG, "onRebind")
        super.onRebind(intent)
    }

    override fun onUnbind(intent: Intent?): Boolean {
        Log.v(LOG_TAG, "onUnbind")
        return true
    }

    override fun onCreate() {
        Log.i(LOG_TAG, "onCreate")
        addToDisposable(
                notificationPreferencesHolder.observeFavEnabled()
                        .subscribe { enabled ->
                            if (enabled) {
                                eventsRepository.updateEvents(NotificationEvent.Source.THEME)
                            }
                        }
        )
        addToDisposable(
                notificationPreferencesHolder.observeQmsEnabled()
                        .subscribe { enabled ->
                            if (enabled) {
                                eventsRepository.updateEvents(NotificationEvent.Source.QMS)
                            }
                        }
        )
        addToDisposable(
                notificationPreferencesHolder.observeMainLimit()
                        .subscribe { limit ->
                            Log.d(LOG_TAG, "NEW timer period $limit")
                            eventsRepository.setTimerPeriod(limit)
                        }
        )

        serviceScope.launch {
            launch {
                eventsRepository.observeEvents().collect { sendNotification(it) }
            }
            launch {
                eventsRepository.observeEventsStack().collect { sendNotifications(it) }
            }
            launch {
                eventsRepository.observeCancel().collect { cancelNotification(it) }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i(LOG_TAG, "onStartCommand this$this : ${App.get()}")
        Log.i(LOG_TAG, "onStartCommand args$flags : $startId : $intent")
        var checkEvents = intent?.action != null && intent.action == CHECK_LAST_EVENTS
        val time = System.currentTimeMillis()

        Log.d(LOG_TAG, "Handle check last events: $time : $lastHardCheckTime : ${time - lastHardCheckTime}")

        checkEvents = if (checkEvents && time - lastHardCheckTime >= 1000 * 60) {
            lastHardCheckTime = time
            true
        } else {
            false
        }
        eventsRepository.externalStart(checkEvents)
        return START_STICKY
    }

    override fun onDestroy() {
        serviceJob.cancel()
        if (!rxDisposables.isDisposed) {
            rxDisposables.dispose()
        }
        super.onDestroy()
        Log.i(LOG_TAG, "onDestroy")
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        Log.i(LOG_TAG, "onTaskRemoved")
        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.KITKAT) {
            val restartIntent = Intent(this, javaClass)
            val am = getSystemService(ALARM_SERVICE) as? AlarmManager
            if (am != null) {
                val pi = PendingIntent.getService(
                        this, 1, restartIntent,
                        activityPendingIntentFlags(PendingIntent.FLAG_ONE_SHOT)
                )
                restartIntent.putExtra("RESTART", "RESTART_CHEBUREK")
                am.setExact(AlarmManager.RTC, System.currentTimeMillis() + 3000, pi)
            }
        }
    }

    private fun getNotificationManager(): NotificationManagerCompat {
        if (mNotificationManager == null) {
            mNotificationManager = NotificationManagerCompat.from(this)
        }
        return mNotificationManager!!
    }

    private fun cancelNotification(event: NotificationEvent) {
        getNotificationManager().cancel(event.notifyId())
    }

    fun sendNotification(event: NotificationEvent) {
        if (notificationPreferencesHolder.getMainAvatarsEnabled()) {
            val schedulers = App.get().Di().schedulers
            val res: Resources = App.getContext().resources
            val height = res.getDimension(android.R.dimen.notification_large_icon_height).toInt()
            val width = res.getDimension(android.R.dimen.notification_large_icon_width).toInt()
            val disposable = avatarRepository
                    .getAvatar(event.userId, event.userNick)
                    .flatMap(Function<String, SingleSource<Bitmap>> { s ->
                        ForPdaCoil.loadBitmapForNotificationSingle(App.getContext(), s, width, height)
                                .subscribeOn(schedulers.io())
                    })
                    .map { bitmap ->
                        val isCircle = Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP
                        var b = bitmap
                        b = BitmapUtils.centerCrop(b, width, height, 1.0f)
                        BitmapUtils.createAvatar(b, width, height, isCircle)
                    }
                    .subscribe({ avatar -> sendNotification(event, avatar) }, Throwable::printStackTrace)
            addToDisposable(disposable)
        } else {
            sendNotification(event, null)
        }
    }

    fun sendNotification(event: NotificationEvent, avatar: Bitmap?) {
        val title = createTitle(event)
        val text = createContent(event)
        val summaryText = createSummary(event)

        val bigTextStyle = NotificationCompat.BigTextStyle()
        bigTextStyle.setBigContentTitle(title)
        bigTextStyle.bigText(text)
        bigTextStyle.setSummaryText(summaryText)

        val channelId = getChannelId(event)
        val channelName = getChannelName(event)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_DEFAULT)
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }

        val builder = NotificationCompat.Builder(this, channelId)

        if (avatar != null && !event.fromSite()) {
            builder.setLargeIcon(avatar)
        }
        builder.setSmallIcon(createSmallIcon(event))

        builder.setContentTitle(title)
        builder.setContentText(text)
        builder.setStyle(bigTextStyle)
        builder.setChannelId(channelId)

        val notifyIntent = Intent(this, MainActivity::class.java)
        notifyIntent.data = Uri.parse(createIntentUrl(event))
        notifyIntent.action = Intent.ACTION_VIEW
        val notifyPendingIntent = PendingIntent.getActivity(
                this, 0, notifyIntent,
                activityPendingIntentFlags(0)
        )
        builder.setContentIntent(notifyPendingIntent)

        configureNotification(builder)

        getNotificationManager().cancel(event.notifyId())
        getNotificationManager().notify(event.notifyId(), builder.build())
    }

    fun sendNotifications(events: List<NotificationEvent>) {
        val title = createStackedTitle(events)
        val text = createStackedContent(events)
        val summaryText = createStackedSummary(events)

        val bigTextStyle = NotificationCompat.BigTextStyle()
        bigTextStyle.setBigContentTitle(title)
        bigTextStyle.bigText(text)
        bigTextStyle.setSummaryText(summaryText)

        val channelId = getChannelId(events[0])
        val channelName = getChannelName(events[0])

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_DEFAULT)
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }

        val builder = NotificationCompat.Builder(this, channelId)

        builder.setSmallIcon(createStackedSmallIcon(events))

        builder.setContentTitle(title)
        builder.setContentText(text)
        builder.setStyle(bigTextStyle)
        builder.setChannelId(channelId)

        val notifyIntent = Intent(this, MainActivity::class.java)
        notifyIntent.data = Uri.parse(createStackedIntentUrl(events))
        notifyIntent.action = Intent.ACTION_VIEW
        val notifyPendingIntent = PendingIntent.getActivity(
                this, 0, notifyIntent,
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
        getNotificationManager().notify(id, builder.build())
    }

    private fun configureNotification(builder: NotificationCompat.Builder) {
        builder.setAutoCancel(true)
        builder.setPriority(NotificationCompat.PRIORITY_DEFAULT)
        builder.setCategory(NotificationCompat.CATEGORY_SOCIAL)
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
        builder.setVibrate(longArrayOf(0L))
    }

    private fun getChannelId(event: NotificationEvent): String {
        if (event.isMention) return CHANNEL_MENTION_ID
        if (event.fromQms()) return CHANNEL_QMS_ID
        if (event.fromTheme()) return CHANNEL_FAV_ID
        if (event.fromSite()) return CHANNEL_SITE_ID
        return CHANNEL_DEFAULT_ID
    }

    private fun getChannelName(event: NotificationEvent): String {
        if (event.isMention) return getString(R.string.notification_summary_mention)
        if (event.fromQms()) return getString(R.string.notification_summary_qms)
        if (event.fromTheme()) return getString(R.string.notification_summary_fav)
        if (event.fromSite()) return getString(R.string.notification_summary_comment)
        return CHANNEL_DEFAULT_NAME
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
            if (nick.isEmpty()) return "Сообщения 4PDA"
        }
        if (event.fromSite()) return "ForPDA"
        return event.userNick
    }

    fun createContent(event: NotificationEvent): String {
        if (event.fromQms()) {
            return String.format(
                    getString(R.string.notification_content_qms_Nick_Count),
                    event.sourceTitle,
                    event.msgCount
            )
        }
        if (event.fromTheme()) {
            if (event.isMention) {
                return String.format(
                        getString(R.string.notification_content_mention_Title),
                        event.sourceTitle
                )
            }
            return String.format(
                    getString(R.string.notification_content_theme_Title),
                    event.sourceTitle
            )
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
                if (nick.isEmpty()) nick = "Сообщения 4PDA"
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
        return ApiUtils.spannedFromHtml(content.toString())
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

    @SuppressLint("HandlerLeak")
    internal inner class IncomingHandler : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message) {
            Toast.makeText(applicationContext, "" + msg.data, Toast.LENGTH_SHORT).show()
        }
    }

    companion object {
        private val LOG_TAG = NotificationsService::class.java.simpleName
        private const val CHANNEL_DEFAULT_ID = "forpda_channel_default"
        private const val CHANNEL_DEFAULT_NAME = "forpda_channel_default"
        private const val CHANNEL_FAV_ID = "forpda_channel_fav"
        private const val CHANNEL_QMS_ID = "forpda_channel_qms"
        private const val CHANNEL_MENTION_ID = "forpda_channel_mention"
        private const val CHANNEL_SITE_ID = "forpda_channel_site"

        const val CHECK_LAST_EVENTS = "CHECK_LAST_EVENTS"
        private const val NOTIFY_STACKED_QMS_ID = -123
        private const val NOTIFY_STACKED_FAV_ID = -234
        private const val STACKED_MAX = 4

        /** API 31+: обязателен FLAG_IMMUTABLE или FLAG_MUTABLE; для открытия активности по тапу достаточно IMMUTABLE. */
        @JvmStatic
        fun activityPendingIntentFlags(base: Int): Int {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                base or PendingIntent.FLAG_IMMUTABLE
            } else {
                base
            }
        }

        /**
         * Исторически этот метод делал start+bind. На современных Android/OEM bind из "внезапных" мест
         * (ресиверы/wake-up) может приводить к ANR. Оставляем безопасный вариант по умолчанию.
         */
        @JvmStatic
        fun startAndCheck() {
            startAndCheckNoBind()
        }

        /** Вариант для BroadcastReceiver: без bind (может затянуться и привести к ANR). */
        @JvmStatic
        fun startAndCheckNoBind() {
            try {
                val intent = Intent(App.getContext(), NotificationsService::class.java)
                        .setAction(CHECK_LAST_EVENTS)
                App.getContext().startService(intent)
            } catch (ignore: Exception) {
            }
        }
    }
}
