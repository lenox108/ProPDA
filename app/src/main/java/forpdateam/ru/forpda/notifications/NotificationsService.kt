package forpdateam.ru.forpda.notifications

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.graphics.Bitmap
import android.os.Build
import android.os.IBinder
import android.util.Log
import timber.log.Timber
import androidx.annotation.DrawableRes
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import forpdateam.ru.forpda.BuildConfig
import forpdateam.ru.forpda.R
import forpdateam.ru.forpda.common.BatteryDebugLogger
import forpdateam.ru.forpda.common.BitmapUtils
import forpdateam.ru.forpda.common.ForPdaCoil
import forpdateam.ru.forpda.entity.remote.events.NotificationEvent
import forpdateam.ru.forpda.model.AuthHolder
import forpdateam.ru.forpda.model.preferences.NotificationPreferencesHolder
import forpdateam.ru.forpda.model.repository.avatar.AvatarRepository
import forpdateam.ru.forpda.model.repository.events.EventsRepository
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Created by radiationx on 31.07.17.
 */
@AndroidEntryPoint
class NotificationsService : Service() {

    @Inject lateinit var avatarRepository: AvatarRepository
    @Inject lateinit var eventsRepository: EventsRepository
    @Inject lateinit var notificationPreferencesHolder: NotificationPreferencesHolder
    @Inject lateinit var authHolder: AuthHolder

    private var mNotificationManager: NotificationManagerCompat? = null
    private var lastHardCheckTime = 0L

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(serviceJob + Dispatchers.Main.immediate)

    internal val avatarBitmapCache = object : android.util.LruCache<String, Bitmap>(64) {}

    /** ID уведомлений, опубликованных этим сервисом, — чтобы снимать ровно их, а не всё подряд. */
    private val postedEventNotifyIds = java.util.Collections.synchronizedSet(mutableSetOf<Int>())

    override fun onBind(intent: Intent?): IBinder? {
        Timber.v("onBind")
        BatteryDebugLogger.logState("NotificationsService", "bind")
        return null
    }

    override fun onCreate() {
        super.onCreate()
        BatteryDebugLogger.logState("NotificationsService", "create")
        createEventChannels(this)
        serviceScope.launch {
            launch {
                // Глушим сервис не только при выключении общего тумблера, но и когда
                // выключены ВСЕ семейства push (темы/QMS/упоминания): иначе foreground-сервис
                // продолжает жить, а его «служебное» уведомление залипает в шторке, хотя
                // показывать пользователю уже нечего.
                notificationPreferencesHolder.wantsPushNotificationsFlow().collect { wants ->
                    if (!wants) {
                        // Гасим realtime через флаг, а не только сокет: onDestroy() рвал соединение,
                        // но оставлял foregroundRealtimeEnabled=true, и первое же изменение сети или
                        // авторизации поднимало WebSocket заново уже при выключенных уведомлениях.
                        eventsRepository.setForegroundRealtimeEnabled(false, "notifications_disabled")
                        cancelAllNotifications()
                        stopSelf()
                    }
                }
            }
            launch {
                // Если пользователь вышел из аккаунта (или стёр куки), шторка не должна
                // висеть с уведомлением. Сразу глушим сервис и снимаем уведомления.
                authHolder.observe().collect { auth ->
                    if (!auth.isAuth()) {
                        Timber.i("NotificationsService: auth lost, stopping service")
                        BatteryDebugLogger.logState("NotificationsService", "auth_lost", "stop")
                        eventsRepository.onDestroy()
                        cancelAllNotifications()
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
            launch {
                // При process_stop WS закрывается в EventsRepository, но FGS-уведомление
                // продолжает висеть в шторке. Детачим foreground (без REMOVE), чтобы
                // снять ограничения background-launch'ей и не держать CPU, оставляя
                // сервис живым до возврата приложения в foreground.
                eventsRepository.observeForegroundRealtimeChanges().collect { change ->
                    // На переднем плане (change.enabled) FGS НЕ поднимаем: приложение само
                    // удерживает процесс в foreground-приоритете, WS жив, а «служебное»
                    // уведомление в шторке не нужно. При уходе в фон (process_stop) снимаем
                    // foreground, если он почему-то был поднят (фолбэк-старт из фона).
                    if (!change.enabled && change.reason.contains("process_stop")) {
                        detachForegroundIfPromoted(change.reason)
                    } else if (change.reason == "ws_connected") {
                        // Сокет ожил после кулдауна — если персистентный фон, снова держим FGS
                        // (мы отпускали его на время кулдауна). Только в фоне и при живой авторизации.
                        val bg = !androidx.lifecycle.ProcessLifecycleOwner.get().lifecycle.currentState
                                .isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)
                        if (notificationPreferencesHolder.getBgPersistentWs() && bg && authHolder.get().isAuth()) {
                            runCatching { promoteToForegroundIfNeeded() }
                                    .onFailure { Timber.w(it, "re-promote FGS after ws_connected failed") }
                        }
                    }
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Timber.i("onStartCommand this$this : ${this@NotificationsService}")
        Timber.i("onStartCommand args$flags : $startId : $intent")
        BatteryDebugLogger.logState("NotificationsService", "startCommand", "action=${intent?.action}")

        // wantsPushNotifications, а не getMainEnabled: при пустых push-семействах
        // и включённых загрузках FGS поднимать не нужно.
        if (!notificationPreferencesHolder.wantsPushNotifications()) {
            if (BuildConfig.DEBUG) Log.i(NOTIFICATIONS_LOG_TAG, "Skip service start: no push families enabled")
            stopSelf(startId)
            return START_NOT_STICKY
        }
        // Без авторизации уведомления о событиях форума нам слать неоткуда.
        if (!authHolder.get().isAuth()) {
            if (BuildConfig.DEBUG) Log.i(NOTIFICATIONS_LOG_TAG, "Skip service start: user not authenticated")
            stopSelf(startId)
            return START_NOT_STICKY
        }

        // FGS-уведомление («Служебный канал») поднимаем ТОЛЬКО если сервис стартовали
        // из фона (через startForegroundService — тогда Android требует startForeground
        // в течение 5 секунд, иначе краш). В штатном случае приложение на переднем плане
        // стартует нас обычным startService(): процесс и так имеет foreground-приоритет,
        // WS живёт без FGS, и уведомление в шторке не показывается. Скрыть уведомление
        // запущенного FGS на API 26+ нельзя — поэтому единственный способ его не показывать
        // в обычном режиме — не быть foreground-сервисом, пока приложение видимо.
        if (intent?.getBooleanExtra(EXTRA_STARTED_AS_FGS, false) == true) {
            promoteToForegroundIfNeeded()
        }

        // Возврат приложения на передний план: FGS больше не нужен (процесс держит UI),
        // снимаем «служебное» уведомление. При следующем уходе в фон App поднимет его снова
        // (режим «Постоянное соединение») — симметрично и без залипших уведомлений.
        val uiForeground = androidx.lifecycle.ProcessLifecycleOwner.get().lifecycle.currentState
                .isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)
        if (uiForeground && intent?.getBooleanExtra(EXTRA_STARTED_AS_FGS, false) != true) {
            detachForegroundIfPromoted("app_visible")
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

        // Режим «Постоянное соединение» в фоне: держим FGS и живой realtime. Ветка покрывает
        // и воскрешение по START_STICKY после убийства процесса (intent=null, extra потерян,
        // а в свежем процессе foregroundRealtime по умолчанию выключен — включаем явно).
        // НО: если WS в кулдауне circuit breaker'а и не подключён — держать FGS ради мёртвого
        // сокета бессмысленно (audit: до 60 мин foreground впустую). Доставку берёт воркер;
        // при успешном реконнекте (onConnected → «ws_connected») FGS поднимется снова.
        val wsUsable = eventsRepository.isWebSocketConnected() || !eventsRepository.isWsCoolingDown()
        if (notificationPreferencesHolder.getBgPersistentWs() && !uiForeground && authHolder.get().isAuth()) {
            if (wsUsable) {
                promoteToForegroundIfNeeded()
                if (!eventsRepository.isForegroundRealtimeActive()) {
                    BatteryDebugLogger.logState("NotificationsService", "persistentRestart")
                    NotifDiagLog.log(this, "service: persistent realtime (re)start")
                    eventsRepository.setForegroundRealtimeEnabled(true, "persistent_restart")
                }
            } else {
                NotifDiagLog.log(this, "service: persistent FGS dropped (ws cooldown), worker covers")
            }
        }

        // externalStart в фоне не делает ничего: WebSocket поднимается только при
        // foregroundRealtime. Если мы при этом успели подняться как FGS (фолбэк-старт),
        // то держали бы уведомление в шторке и foreground-приоритет процесса впустую —
        // и снять его было бы уже некому, т.к. process_stop давно прошёл. В кулдауне персистента
        // тоже снимаем — не жжём батарею на неподключающийся сокет.
        if (!eventsRepository.isForegroundRealtimeActive() ||
                (notificationPreferencesHolder.getBgPersistentWs() && !uiForeground && !wsUsable)) {
            detachForegroundIfPromoted("no_realtime_work")
        }
        // «Постоянное соединение»: сервис переживает смерть UI-задачи (START_STICKY), но ТОЛЬКО
        // пока сокет полезен. В кулдауне — START_NOT_STICKY: убьют → не воскрешаем ради мёртвого
        // сокета (иначе churn: рестарт → кулдаун → детач → смерть → рестарт…). Воркер держит доставку.
        return if (notificationPreferencesHolder.getBgPersistentWs() && wsUsable) START_STICKY else START_NOT_STICKY
    }

    internal var foregroundPromoted = false

    private fun detachForegroundIfPromoted(reason: String) {
        if (!foregroundPromoted) return
        BatteryDebugLogger.logState("NotificationsService", "foregroundDetach", "reason=$reason")
        // REMOVE, а не DETACH: при DETACH «служебное» уведомление остаётся висеть в шторке
        // уже без сервиса — то самое залипшее уведомление, ради которого FGS и прячут.
        stopForeground(STOP_FOREGROUND_REMOVE)
        foregroundPromoted = false
    }

    private fun promoteToForegroundIfNeeded() {
        if (foregroundPromoted) return
        val channel = NotificationChannel(
                FOREGROUND_CHANNEL_ID,
                getString(R.string.notification_foreground_channel_name),
                NotificationManager.IMPORTANCE_MIN
        ).apply {
            setShowBadge(false)
            enableLights(false)
            enableVibration(false)
            // Канал FGS максимально скрытный: ни шторка, ни lock-screen не показывают.
            setLockscreenVisibility(Notification.VISIBILITY_SECRET)
        }
        getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        val notification = NotificationCompat.Builder(this, FOREGROUND_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notify_favorites)
                .setContentTitle(getString(R.string.app_name))
                .setContentText(getString(R.string.notification_foreground_channel_name))
                // Без ongoing/без ongoing-summary: пользователь может смахнуть, и на
                // многих оболочках уведомление вообще не появится в шторке.
                .setOngoing(false)
                .setPriority(NotificationCompat.PRIORITY_MIN)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setVisibility(NotificationCompat.VISIBILITY_SECRET)
                .setShowWhen(false)
                .setOnlyAlertOnce(true)
                .setSilent(true)
                .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // «Постоянное соединение» живёт под specialUse: у dataSync на Android 15 суммарный
            // лимит ~6 ч/сутки (onTimeout), а постоянный сокет должен жить сутками.
            val fgsType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE
                    && notificationPreferencesHolder.getBgPersistentWs()) {
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            } else {
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            }
            startForeground(FOREGROUND_NOTIFICATION_ID, notification, fgsType)
        } else {
            startForeground(FOREGROUND_NOTIFICATION_ID, notification)
        }
        foregroundPromoted = true
    }

    override fun onDestroy() {
        BatteryDebugLogger.logState("NotificationsService", "destroy")
        avatarBitmapCache.evictAll()
        serviceScope.cancel()  // Отменяем все корутины
        serviceJob.cancel()
        // Снимаем только «служебное» FGS-уведомление: уведомления о событиях форума живут
        // сами по себе, и остановка сервиса не повод их гасить. Там, где их действительно
        // надо убрать (логаут, выключение push), это делается явно.
        removeForegroundNotification()
        // EventsRepository является singleton'ом. Не отменяем его repoScope из lifecycle
        // сервиса, иначе последующие события обновляют вкладки, но уже не доходят до notify().
        super.onDestroy()
        Timber.i("onDestroy")
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        Timber.i("onTaskRemoved")
        // Режим «Постоянное соединение»: свайп из Recents НЕ должен убивать пуши — в этом
        // весь смысл режима (как у Telegram). Сервис и сокет продолжают жить.
        if (notificationPreferencesHolder.getBgPersistentWs()
                && notificationPreferencesHolder.wantsPushNotifications()
                && authHolder.get().isAuth()) {
            BatteryDebugLogger.logState("NotificationsService", "taskRemoved", "persistent: keep running")
            return
        }
        // Пользователь смахнул приложение из Recents. Без явной остановки foreground-сервис
        // продолжает жить вместе со своим обязательным уведомлением в шторке.
        //
        // Уведомления о новых сообщениях при этом НЕ трогаем: свайп из Recents не значит
        // «я всё прочитал». Раньше здесь снималось всё подряд, включая уведомление о
        // неотправленном быстром ответе — вместе с текстом, который пользователь набрал.
        removeForegroundNotification()
        stopSelf()
    }

    /**
     * Android 15+ (API 35): у foreground-сервиса типа `dataSync` есть суммарный лимит
     * времени (~6 ч за 24 ч). Когда он исчерпан, система вызывает onTimeout и ТРЕБУЕТ
     * немедленно снять foreground. Если этого не сделать — прилетает
     * ForegroundServiceDidNotStopInTimeException и процесс убивают (полевой краш v3.1.3).
     *
     * Снимаем FGS и останавливаемся. Realtime-сокет при этом умолкает, но уведомления
     * продолжает приносить периодическая проверка событий (WorkManager), а уже показанные
     * уведомления о форумных событиях не трогаем — как и в onTaskRemoved.
     */
    override fun onTimeout(startId: Int, fgsType: Int) {
        Timber.w("FGS dataSync timeout (startId=$startId type=$fgsType) — stopping service")
        BatteryDebugLogger.logState("NotificationsService", "fgs_timeout")
        removeForegroundNotification()
        stopSelf()
    }

    /** Убирает «служебное» FGS-уведомление, не трогая уведомления о событиях. */
    private fun removeForegroundNotification() {
        if (foregroundPromoted) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            foregroundPromoted = false
        }
        runCatching { getNotificationManager().cancel(FOREGROUND_NOTIFICATION_ID) }
                .onFailure { Timber.e(it, "cancel foreground notification failed") }
    }

    /**
     * Снимает ТОЛЬКО уведомления о событиях форума. Раньше здесь стоял `cancelAll()`, который
     * заодно стирал прогресс загрузки файла и уведомление о доступном обновлении — они живут
     * в своих каналах и к этому сервису отношения не имеют.
     *
     * Вызывается там, где показывать события больше нечего и незачем: логаут и выключение push.
     */
    private fun cancelAllNotifications() {
        removeForegroundNotification()
        val manager = getNotificationManager()
        runCatching {
            manager.cancel(NotificationPublisher.NOTIFY_STACKED_QMS_ID)
            manager.cancel(NotificationPublisher.NOTIFY_STACKED_FAV_ID)
            postedEventNotifyIds.toList().forEach { manager.cancel(it) }
            postedEventNotifyIds.clear()
            // Подметаем и то, что опубликовал фоновый EventsCheckWorker: он публикует
            // сам, поэтому в postedEventNotifyIds его ID не попадают.
            cancelActiveEventChannelNotifications(manager)
        }.onFailure { Timber.e(it, "cancel event notifications failed") }
    }

    private fun cancelActiveEventChannelNotifications(manager: NotificationManagerCompat) {
        val systemManager = getSystemService(NotificationManager::class.java) ?: return
        val active = runCatching { systemManager.activeNotifications }.getOrNull() ?: return
        for (sbn in active) {
            if (sbn.notification?.channelId in EVENT_CHANNEL_IDS) {
                manager.cancel(sbn.tag, sbn.id)
            }
        }
    }

    private fun getNotificationManager(): NotificationManagerCompat {
        return mNotificationManager ?: NotificationManagerCompat.from(this).also {
            mNotificationManager = it
        }
    }

    private fun cancelNotification(event: NotificationEvent) {
        val id = event.notifyId()
        getNotificationManager().cancel(id)
        postedEventNotifyIds.remove(id)
        Log.i(NOTIFICATIONS_LOG_TAG, "Cancelled notification id=$id source=${event.source} sourceId=${event.sourceId}")
        // Сводка («N сообщений», [NotificationPublisher.publishStacked]) висит под собственным ID и
        // сама не исчезает вместе с последним снятым событием — в шторке оставалась группа про уже
        // прочитанные сообщения. Снимаем её, когда в канале не осталось ни одного события.
        when {
            event.fromQms() ->
                cancelStackedIfEmpty(CHANNEL_QMS_ID, NotificationPublisher.NOTIFY_STACKED_QMS_ID, id)
            event.fromTheme() && !event.isMention ->
                cancelStackedIfEmpty(CHANNEL_FAV_ID, NotificationPublisher.NOTIFY_STACKED_FAV_ID, id)
        }
    }

    /**
     * Снимает сводное уведомление канала, если в шторке не осталось ни одного его события.
     * [justCancelledId] исключается явно: `activeNotifications` успевает вернуть уведомление,
     * которое мы отменили строкой выше.
     */
    private fun cancelStackedIfEmpty(channelId: String, stackedId: Int, justCancelledId: Int) {
        val systemManager = getSystemService(NotificationManager::class.java) ?: return
        val active = runCatching { systemManager.activeNotifications }.getOrNull() ?: return
        val hasEvents = active.any { sbn ->
            sbn.notification?.channelId == channelId && sbn.id != stackedId && sbn.id != justCancelledId
        }
        if (!hasEvents) {
            getNotificationManager().cancel(stackedId)
        }
    }

    fun sendNotification(event: NotificationEvent) {
        // Проверяем главный переключатель уведомлений
        if (!notificationPreferencesHolder.getMainEnabled()) {
            if (BuildConfig.DEBUG) Log.i(NOTIFICATIONS_LOG_TAG, "Skip incoming notification: app preference disabled")
            return
        }
        if (notificationPreferencesHolder.getMainAvatarsEnabled()) {
            val cacheKey = "user_${event.userId}"
            avatarBitmapCache.get(cacheKey)?.let { cached ->
                sendNotification(event, cached)
                return
            }
            val res: Resources = this.resources
            val height = res.getDimension(android.R.dimen.notification_large_icon_height).toInt()
            val width = res.getDimension(android.R.dimen.notification_large_icon_width).toInt()
            serviceScope.launch {
                runCatching {
                    // serviceScope живёт на Main.immediate: загрузка аватара и обрезка битмапа
                    // на нём подвешивали кадры ровно в момент прихода уведомления.
                    withContext(Dispatchers.IO) {
                        val url = avatarRepository.getAvatar(event.userId, event.userNick)
                        val bitmap = ForPdaCoil.loadBitmapForNotification(this@NotificationsService, url, width, height)
                        val cropped = BitmapUtils.centerCrop(bitmap, width, height, 1.0f)
                        BitmapUtils.createAvatar(cropped, width, height, true)
                    }
                }.onSuccess { avatar ->
                    avatarBitmapCache.put(cacheKey, avatar)
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
        NotificationPublisher.publish(this, notificationPreferencesHolder, event, largeIcon = avatar)
                ?.let { postedEventNotifyIds.add(it) }
    }

    fun sendNotifications(events: List<NotificationEvent>) {
        NotificationPublisher.publishStacked(this, notificationPreferencesHolder, events)
                ?.let { postedEventNotifyIds.add(it) }
    }

    companion object {
        private val LOG_TAG = NotificationsService::class.java.simpleName
        private const val NOTIFICATIONS_LOG_TAG = "Notifications"
        private const val LEGACY_CHANNEL_DEFAULT_ID = "forpda_channel_default"
        const val CHANNEL_FAV_ID = "forpda_channel_fav"
        const val CHANNEL_QMS_ID = "forpda_channel_qms"
        const val CHANNEL_MENTION_ID = "forpda_channel_mention"
        const val CHANNEL_SITE_ID = "forpda_channel_site"
        const val CHANNEL_HAT_ID = "forpda_channel_hat"

        /** Каналы событий форума. Каналы загрузок и обновлений сюда НЕ входят намеренно. */
        @JvmStatic
        val EVENT_CHANNEL_IDS: Set<String> = setOf(
                CHANNEL_FAV_ID,
                CHANNEL_QMS_ID,
                CHANNEL_MENTION_ID,
                CHANNEL_SITE_ID,
                FOREGROUND_CHANNEL_ID
        )

        const val CHECK_LAST_EVENTS = "CHECK_LAST_EVENTS"
        /** Помечает старт через startForegroundService(): onStartCommand обязан поднять FGS в 5 сек. */
        private const val EXTRA_STARTED_AS_FGS = "started_as_fgs"
        private const val FOREGROUND_CHANNEL_ID = "forpda_foreground_service"
        private const val FOREGROUND_NOTIFICATION_ID = -345

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
                    ),
                    NotificationChannel(
                            CHANNEL_HAT_ID,
                            context.getString(R.string.notification_hat_channel_name),
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

        /**
         * Режим «Постоянное соединение»: поднять сервис как FGS при уходе приложения в фон,
         * чтобы процесс и WebSocket пережили потерю foreground-приоритета. Вызывается из
         * App.onStop — в окне после ухода с переднего плана старт FGS ещё разрешён.
         */
        @JvmStatic
        fun startPersistentFgs(context: Context) {
            try {
                BatteryDebugLogger.logState("NotificationsService", "startPersistentFgs")
                val intent = Intent(context, NotificationsService::class.java)
                        .putExtra(EXTRA_STARTED_AS_FGS, true)
                context.startForegroundService(intent)
            } catch (e: Exception) {
                Timber.w(e, "startPersistentFgs failed")
            }
        }

        /** Вариант для BroadcastReceiver: без bind (может затянуться и привести к ANR). */
        @JvmStatic
        fun startAndCheckNoBind(context: Context) {
            try {
                BatteryDebugLogger.logState("NotificationsService", "startAndCheckNoBind")
                val intent = Intent(context, NotificationsService::class.java)
                        .setAction(CHECK_LAST_EVENTS)
                // Все вызовы идут из MainActivity, т.е. приложение на переднем плане:
                // обычный startService() разрешён и НЕ обязывает показывать FGS-уведомление
                // «Служебный канал». Если же мы внезапно оказались в фоне (гонка/edge-case),
                // startService кинет IllegalStateException — тогда поднимаемся как FGS
                // (помечая интент, чтобы onStartCommand вызвал startForeground в срок).
                try {
                    context.startService(intent)
                } catch (notAllowed: IllegalStateException) {
                    intent.putExtra(EXTRA_STARTED_AS_FGS, true)
                    context.startForegroundService(intent)
                }
            } catch (e: Exception) {
                // Финальный отказ старта (в т.ч. ForegroundServiceStartNotAllowedException /
                // FGS-timeout из FGS-ветки) раньше глотался молча и не попадал в диагностику.
                Timber.w(e, "startAndCheckNoBind failed to start service")
                BatteryDebugLogger.logState("NotificationsService", "startAndCheckNoBind failed: ${e.javaClass.simpleName}")
            }
        }

        @JvmStatic
        fun shouldStartService(wantsPushNotifications: Boolean, isAuth: Boolean): Boolean =
                wantsPushNotifications && isAuth
    }
}
