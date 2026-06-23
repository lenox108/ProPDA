package forpdateam.ru.forpda.model.repository.events

import android.app.Application
import android.content.Context
import android.util.Log
import timber.log.Timber
import forpdateam.ru.forpda.BuildConfig
import forpdateam.ru.forpda.client.WebSocketController
import forpdateam.ru.forpda.common.BatteryDebugLogger
import forpdateam.ru.forpda.entity.app.TabNotification
import forpdateam.ru.forpda.entity.remote.events.NotificationEvent
import forpdateam.ru.forpda.model.AuthHolder
import forpdateam.ru.forpda.model.CountersHolder
import forpdateam.ru.forpda.model.NetworkStateProvider
import forpdateam.ru.forpda.model.data.remote.IWebClient
import forpdateam.ru.forpda.model.data.remote.api.events.NotificationEventsApi
import forpdateam.ru.forpda.model.preferences.NotificationPreferencesHolder
import forpdateam.ru.forpda.model.repository.mentions.MentionsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import okhttp3.Response
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

class EventsRepository(
        _context: Context,
        private val application: Application,
        private val webClient: IWebClient,
        private val eventsApi: NotificationEventsApi,
        private val networkStateProvider: NetworkStateProvider,
        private val authHolder: AuthHolder,
        private val countersHolder: CountersHolder,
        private val notificationPreferencesHolder: NotificationPreferencesHolder,
        private val mentionsRepository: MentionsRepository
) {
    companion object {
        private const val NOTIFICATIONS_LOG_TAG = "Notifications"
        // Internal aggregation window for websocket events. The old user preference
        // is no longer exposed; keep batching stable and predictable.
        private const val NOTIFICATION_AGGREGATION_PERIOD_MS = 60_000L

        /**
         * Максимальное количество событий для группового (stacked) уведомления.
         * Если событий больше — отправляется summary-уведомление с количеством.
         * Значение 4 подобрано эмпирически для оптимального UX на мобильных устройствах.
         */
        private const val STACKED_MAX = 4
    }

    private val timerPeriod = NOTIFICATION_AGGREGATION_PERIOD_MS

    private val repoJob = SupervisorJob()
    private val repoScope = CoroutineScope(repoJob + Dispatchers.Main.immediate)
    private val ioDispatcher = Dispatchers.IO
    @Volatile
    private var foregroundRealtimeEnabled = false
    private val realtimeScreenOwners = mutableSetOf<String>()
    @Volatile
    private var realtimeScreenRefCount = 0
    private var reconnectAttempts = 0

    private val pendingEvents = mapOf<NotificationEvent.Source, MutableMap<Int, NotificationEvent>>(
            NotificationEvent.Source.QMS to mutableMapOf(),
            NotificationEvent.Source.THEME to mutableMapOf(),
            NotificationEvent.Source.SITE to mutableMapOf()
    )

    private var pendingAggregationJob: Job? = null
    private val timerRunnable = {
        for (source in pendingEvents.keys) {
            handlePendingEvents(source)
        }
    }

    private val eventsHistory = mutableMapOf<Int, NotificationEvent>()


    private val notifyEventFlow = MutableSharedFlow<NotificationEvent>(
            extraBufferCapacity = 256,
            onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    private val notifyStackFlow = MutableSharedFlow<List<NotificationEvent>>(
            extraBufferCapacity = 64,
            onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    private val cancelEventFlow = MutableSharedFlow<NotificationEvent>(
            extraBufferCapacity = 256,
            onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    private val notifyTabFlow = MutableSharedFlow<TabNotification>(
            extraBufferCapacity = 256,
            onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    data class ForegroundRealtimeChange(val enabled: Boolean, val reason: String)

    private val foregroundRealtimeFlow = MutableSharedFlow<ForegroundRealtimeChange>(
            extraBufferCapacity = 16,
            onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    fun observeForegroundRealtimeChanges(): Flow<ForegroundRealtimeChange> =
            foregroundRealtimeFlow.asSharedFlow()

    private val controllerListener: WebSocketController.Listener = object : WebSocketController.Listener() {
        override fun onConnected() {
            if (BuildConfig.DEBUG) Timber.d("WSContr onConnected ${webSocketController.getCurrentId()},  ${webSocketController.isConnected()}")
            webSocketController.send("""[${webSocketController.getCurrentId()}, "sv"]""")
            webSocketController.send("""[0, "ea", "u${authHolder.get().userId}"]""")
        }

        override fun onMessage(text: String?) {
            if (BuildConfig.DEBUG) Timber.d("WSContr onMessage ${webSocketController.getCurrentId()}, ${webSocketController.isConnected()}, hasPayload=${!text.isNullOrEmpty()}")
            try {
                eventsApi.parseWebSocketEvent(text.orEmpty())?.also {
                    if (it.type != NotificationEvent.Type.HAT_EDITED) {
                        handleWebSocketEvent(it)
                    }
                }
            } catch (ex: Exception) {
                Timber.e(ex, "Events parse error")
            }
        }

        override fun onDisconnected(throwable: Throwable, response: Response?) {
            if (BuildConfig.DEBUG) Timber.d("WSContr onDisconnected ${webSocketController.getCurrentId()}, ${webSocketController.isConnected()}, code=${response?.code}")
            if (response != null) {
                if (BuildConfig.DEBUG) Timber.d("WSContr onDisconnected: code=${response.code}")
                if (response.code == 403) {
                    (application as forpdateam.ru.forpda.App).notifyForbidden(true)
                }
            }

            Timber.e(throwable, "Events check error")
            if (foregroundRealtimeEnabled && (throwable is SocketTimeoutException || throwable is TimeoutException)) {
                if (BuildConfig.DEBUG) Timber.d("start onFailure")
                start(checkEvents = false, force = false)
            }
        }
    }

    private val webSocketController = WebSocketController(webClient, controllerListener)

    init {
        repoScope.launch {
            var lastNet = networkStateProvider.getState()
            networkStateProvider.observeState().collect { s ->
                if (s == lastNet) return@collect
                lastNet = s
                if (s && foregroundRealtimeEnabled) {
                    if (BuildConfig.DEBUG) Timber.d("start networkStateProvider.observeState")
                    start(checkEvents = true, force = true)
                } else if (!s) {
                    stop("network_lost")
                }
            }
        }
        repoScope.launch {
            var lastAuth = authHolder.get().isAuth()
            authHolder.observe().collect { auth ->
                val now = auth.isAuth()
                if (now == lastAuth) return@collect
                if (BuildConfig.DEBUG) {
                    Timber.d("authHolder.observe state=${auth.state}")
                }
                lastAuth = now
                if (now && foregroundRealtimeEnabled) {
                    if (webSocketController.isConnected()) {
                        stop("auth_changed")
                    }
                    if (BuildConfig.DEBUG) Timber.d("start authHolder.observe")
                    start(checkEvents = true, force = true)
                } else {
                    stop("auth_lost_or_background")
                }
            }
        }
    }

    fun observeEvents(): Flow<NotificationEvent> = notifyEventFlow.asSharedFlow()

    fun observeEventsStack(): Flow<List<NotificationEvent>> = notifyStackFlow.asSharedFlow()

    fun observeCancel(): Flow<NotificationEvent> = cancelEventFlow.asSharedFlow()

    fun observeEventsTab(): Flow<TabNotification> = notifyTabFlow.asSharedFlow()

    fun onDestroy() {
        // Репозиторий живет как singleton, поэтому нельзя отменять repoScope:
        // после stop/start он должен продолжать принимать события и публиковать notify-flow.
        stop("destroy")
    }

    fun externalStart(checkEvents: Boolean) {
        if (BuildConfig.DEBUG) Timber.d("start externalStart")
        start(checkEvents = checkEvents, force = true)
    }

    fun isForegroundRealtimeActive(): Boolean = foregroundRealtimeEnabled

    fun isWebSocketConnected(): Boolean = webSocketController.isConnected()

    fun setForegroundRealtimeEnabled(enabled: Boolean, reason: String) {
        if (foregroundRealtimeEnabled == enabled) {
            BatteryDebugLogger.logState("EventsRepository", "foregroundRealtimeUnchanged", "enabled=$enabled reason=$reason")
            return
        }
        // При выключенных уведомлениях realtime-WS не нужен: пинги каждые 30с держат
        // радио в активном состоянии. Никогда не поднимаем WS, если уведомления выключены.
        if (enabled && !notificationPreferencesHolder.wantsPushNotifications()) {
            foregroundRealtimeEnabled = false
            BatteryDebugLogger.logState("EventsRepository", "skipForeground", "notifications disabled reason=$reason")
            stop("notifications_disabled")
            return
        }
        foregroundRealtimeEnabled = enabled
        BatteryDebugLogger.logState("EventsRepository", if (enabled) "foreground" else "background", "reason=$reason")
        foregroundRealtimeFlow.tryEmit(ForegroundRealtimeChange(enabled, reason))
        if (enabled) {
            start(checkEvents = true, force = true)
        } else {
            stop("background:$reason")
        }
    }

    /**
     * Экран, которому нужен realtime-WebSocket, запрашивает разрешение через refcount.
     * Пока хотя бы один экран держит счётчик > 0, фреймворк не отключает WS даже
     * если приложение остаётся в foreground без активных realtime-экранов.
     * Это позволяет экрану, который в фоне (например, QMS-чат на свёрнутой вкладке),
     * удерживать WS открытым. Сам по себе вызов НЕ открывает WS — для открытия
     * по-прежнему требуется foreground + network + auth.
     */
    fun requestRealtimeForScreen(owner: String) {
        synchronized(realtimeScreenOwners) {
            val added = realtimeScreenOwners.add(owner)
            realtimeScreenRefCount = realtimeScreenOwners.size
            if (added) {
                BatteryDebugLogger.logState("EventsRepository", "screenAcquireRealtime", "owner=$owner count=$realtimeScreenRefCount")
            }
        }
    }

    fun releaseRealtimeForScreen(owner: String) {
        synchronized(realtimeScreenOwners) {
            if (realtimeScreenOwners.remove(owner)) {
                realtimeScreenRefCount = realtimeScreenOwners.size
                BatteryDebugLogger.logState("EventsRepository", "screenReleaseRealtime", "owner=$owner count=$realtimeScreenRefCount")
            }
        }
    }

    fun isRealtimeScreenActive(): Boolean = realtimeScreenRefCount > 0

    fun updateEvents(source: NotificationEvent.Source) {
        hardHandleEvent(source)
    }

    fun disableEvents(source: NotificationEvent.Source) {
        val pending = pendingEvents[source]
        pending?.clear()
    }

    /**
     * Вызывается, когда пользователь напрямую из приложения открыл тему (и, значит,
     * прочитал новые сообщения). Отменяем все связанные с этой темой уведомления
     * в шторке, т.к. сервер не всегда присылает READ-событие через WebSocket сразу,
     * и уведомления «повисали» даже после прочтения.
     */
    fun onTopicRead(topicId: Int) {
        if (topicId <= 0) return
        val toRemove = mutableListOf<Int>()
        for ((key, event) in eventsHistory) {
            if (event.fromTheme() && !event.isMention && event.sourceId == topicId) {
                cancelEventFlow.tryEmit(event)
                toRemove.add(key)
                notifyTabs(TabNotification(
                        event.source,
                        NotificationEvent.Type.READ,
                        event,
                        true
                ))
            }
        }
        for (k in toRemove) {
            eventsHistory.remove(k)
        }
        // Также очищаем pending события этой темы, чтобы повторный таймер
        // не восстановил только что отменённые уведомления.
        pendingEvents[NotificationEvent.Source.THEME]?.entries?.removeAll { (_, event) ->
            !event.isMention && event.sourceId == topicId
        }
    }

    /**
     * Если пользователь открыл тему из избранного/форума/ссылки и на странице уже есть пост,
     * где его упомянули, считаем это упоминание прочитанным без обязательного захода в раздел «Ответы».
     */
    fun onTopicPostsRead(topicId: Int, postIds: Collection<Int>) {
        if (topicId <= 0 || postIds.isEmpty()) return
        val visiblePostIds = postIds.asSequence().filter { it > 0 }.toSet()
        if (visiblePostIds.isEmpty()) return

        var readMentions = 0
        val toRemove = mutableListOf<Int>()
        for ((key, event) in eventsHistory) {
            if (event.fromTheme() && event.isMention && event.sourceId == topicId && event.messageId in visiblePostIds) {
                cancelEventFlow.tryEmit(event)
                toRemove.add(key)
                readMentions++
                notifyTabs(TabNotification(
                        event.source,
                        NotificationEvent.Type.READ,
                        event,
                        true
                ))
            }
        }
        for (key in toRemove) {
            eventsHistory.remove(key)
        }
        pendingEvents[NotificationEvent.Source.THEME]?.entries?.removeAll { (_, event) ->
            event.isMention && event.sourceId == topicId && event.messageId in visiblePostIds
        }

        repoScope.launch(ioDispatcher) {
            val (mentionStateChanged, snapshot) = mentionsRepository.markPostsReadAndRecomputeUnreadSnapshot(topicId, visiblePostIds)
            if (readMentions > 0 || mentionStateChanged) {
                countersHolder.setMentions(snapshot.unreadCount, source = "theme_posts_read_mentions")
            }
        }
    }

    private fun start(checkEvents: Boolean, force: Boolean) {
        if (BuildConfig.DEBUG) {
            Timber.d("Start: net=${networkStateProvider.getState()} ws=${webSocketController.isConnected()} check=$checkEvents id=${webSocketController.getCurrentId()}")
        }
        if (!foregroundRealtimeEnabled) {
            BatteryDebugLogger.logState("EventsRepository", "skipStart", "background check=$checkEvents")
            return
        }
        if (networkStateProvider.getState() && authHolder.get().isAuth()) {
            if (!webSocketController.isConnected()) {
                if (!force && reconnectAttempts > 0) {
                    val delayMs = reconnectBackoffMs()
                    BatteryDebugLogger.logState("EventsRepository", "reconnectBackoff", "attempt=$reconnectAttempts delayMs=$delayMs")
                    repoScope.launch {
                        delay(delayMs)
                        if (foregroundRealtimeEnabled && networkStateProvider.getState() && authHolder.get().isAuth() && !webSocketController.isConnected()) {
                            start(checkEvents = false, force = true)
                        }
                    }
                    reconnectAttempts++
                    return
                }
                if (!force) {
                    reconnectAttempts = 1
                }
                BatteryDebugLogger.logState("EventsRepository", "webSocketConnect", "check=$checkEvents")
                webSocketController.connect()
            }

            if (checkEvents) {
                hardHandleEvent(NotificationEvent.Source.THEME)
                hardHandleEvent(NotificationEvent.Source.QMS)
            }
        } else {
            BatteryDebugLogger.logState("EventsRepository", "skipStart", "net=${networkStateProvider.getState()} auth=${authHolder.get().isAuth()}")
        }
    }

    private fun stop(reason: String) {
        if (BuildConfig.DEBUG) Timber.d("stop")
        BatteryDebugLogger.logState("EventsRepository", "stopRealtime", reason)
        if (!reason.startsWith("reconnect")) {
            reconnectAttempts = 0
        }
        cancelTimer()
        webSocketController.disconnectAll()
    }

    private fun reconnectBackoffMs(): Long {
        val seconds = 30L * (1L shl (reconnectAttempts - 1).coerceAtMost(3))
        return TimeUnit.SECONDS.toMillis(seconds.coerceAtMost(5 * 60L))
    }

    private fun schedulePendingAggregationIfNeeded() {
        if (!foregroundRealtimeEnabled) return
        if (pendingEvents.values.none { it.isNotEmpty() }) return
        if (pendingAggregationJob?.isActive == true) return
        pendingAggregationJob = repoScope.launch {
            delay(timerPeriod)
            if (!foregroundRealtimeEnabled) return@launch
            timerRunnable.invoke()
            pendingAggregationJob = null
            schedulePendingAggregationIfNeeded()
        }
    }

    private fun cancelTimer() {
        pendingAggregationJob?.cancel()
        pendingAggregationJob = null
    }

    private fun sendNotification(event: NotificationEvent) {
        if (!notificationPreferencesHolder.getMainEnabled()) {
            if (BuildConfig.DEBUG) Log.i(NOTIFICATIONS_LOG_TAG, "Skip ${event.notificationLogCategory()} event: app preference disabled")
            return
        }
        if (event.userId == authHolder.get().userId) {
            if (BuildConfig.DEBUG) Log.i(NOTIFICATIONS_LOG_TAG, "Skip ${event.notificationLogCategory()} event: own user event")
            return
        }
        // Per-topic mute (только для тем избранного). Mention из темы тоже глушим.
        if (event.fromTheme() && notificationPreferencesHolder.isTopicMuted(event.sourceId)) {
            if (BuildConfig.DEBUG) Log.i(NOTIFICATIONS_LOG_TAG, "Skip ${event.notificationLogCategory()} event: topic muted")
            return
        }
        eventsHistory[event.notifyId()] = event
        if (!checkNotify(event, event.source)) {
            if (BuildConfig.DEBUG) Log.i(NOTIFICATIONS_LOG_TAG, "Skip ${event.notificationLogCategory()} event: category preference disabled")
            return
        }
        if (BuildConfig.DEBUG) Log.i(NOTIFICATIONS_LOG_TAG, "Queue ${event.notificationLogCategory()} notification")
        notifyEventFlow.tryEmit(event)
    }

    private fun sendNotifications(events: List<NotificationEvent>, tSource: NotificationEvent.Source) {
        if (events.isEmpty()) {
            return
        }
        // Применяем per-topic mute для стека (тем избранного).
        val mutedIds = notificationPreferencesHolder.getMutedTopics()
        val filtered = if (mutedIds.isEmpty() || tSource != NotificationEvent.Source.THEME) {
            events
        } else {
            events.filterNot { it.sourceId in mutedIds }
        }
        if (filtered.isEmpty()) {
            if (BuildConfig.DEBUG) Log.i(NOTIFICATIONS_LOG_TAG, "Skip ${tSource.name} stacked events: all topics muted")
            return
        }
        if (filtered.size <= STACKED_MAX) {
            for (event in filtered) {
                sendNotification(event)
            }
            return
        }
        if (!checkNotify(null, tSource)) {
            if (BuildConfig.DEBUG) Log.i(NOTIFICATIONS_LOG_TAG, "Skip ${tSource.name} stacked events: category preference disabled")
            return
        }
        if (BuildConfig.DEBUG) Log.i(NOTIFICATIONS_LOG_TAG, "Queue ${tSource.name} stacked notification, count=${filtered.size}")
        notifyStackFlow.tryEmit(filtered)
    }

    private fun notifyTabs(event: TabNotification) {
        if (BuildConfig.DEBUG) {
            Timber.d("notifyTabs")
        }
        notifyTabFlow.tryEmit(event)
    }

    private fun checkNotify(event: NotificationEvent?, source: NotificationEvent.Source): Boolean {
        if (!notificationPreferencesHolder.getMainEnabled()) {
            return false
        }
        if (NotificationEvent.fromQms(source)) {
            if (!notificationPreferencesHolder.getQmsEnabled()) {
                return false
            }
        } else if (NotificationEvent.fromTheme(source)) {
            if (event != null && event.isMention) {
                if (!notificationPreferencesHolder.getMentionsEnabled()) {
                    return false
                }
            } else {
                if (!notificationPreferencesHolder.getFavEnabled()) {
                    return false
                }
                if (event != null && notificationPreferencesHolder.getFavOnlyImportant() && !event.isImportant) {
                    return false
                }
            }
        } else if (NotificationEvent.fromSite(source)) {
            if (!notificationPreferencesHolder.getMentionsEnabled()) {
                return false
            }
        }
        return true
    }

    private fun checkOldEvent(event: NotificationEvent) {
        var oldEvent = eventsHistory[event.notifyId(NotificationEvent.Type.NEW)]
        var delete = false
        if (BuildConfig.DEBUG) {
            Timber.d("checkOldEvent old=$oldEvent new=$event")
        }

        if (event.fromTheme()) {
            //Убираем уведомления избранного
            if (oldEvent != null && event.messageId >= oldEvent.messageId) {
                cancelEventFlow.tryEmit(oldEvent)
                delete = true
            }

            //Убираем уведомление упоминаний
            oldEvent = eventsHistory[event.notifyId(NotificationEvent.Type.MENTION)]
            if (oldEvent != null) {
                cancelEventFlow.tryEmit(oldEvent)
                delete = true
            }
        } else if (event.fromQms()) {

            //Убираем уведомление кумыса
            if (oldEvent != null) {
                cancelEventFlow.tryEmit(oldEvent)
                delete = true
            }
        }

        if (delete || oldEvent == null) {
            notifyTabs(TabNotification(
                    event.source,
                    event.type,
                    event,
                    true
            ))
        }
        if (delete) {
            eventsHistory.remove(event.notifyId(NotificationEvent.Type.NEW))
        }
    }

    private fun checkOldEvents(loadedEvents: List<NotificationEvent>, source: NotificationEvent.Source) {
        val oldEvents = eventsHistory.filter { it.value.source == source }.map { it.value }

        for (oldEvent in oldEvents) {
            var exist = false
            for (loadedEvent in loadedEvents) {
                if (oldEvent.sourceId == loadedEvent.sourceId) {
                    exist = true
                    break
                }
            }
            if (!exist) {
                cancelEventFlow.tryEmit(oldEvent)
                eventsHistory.remove(oldEvent.notifyId(NotificationEvent.Type.NEW))
                notifyTabs(TabNotification(
                        oldEvent.source,
                        NotificationEvent.Type.READ,
                        oldEvent,
                        true
                ))
            }
        }
    }

    private fun handleWebSocketEvent(event: NotificationEvent) {
        if (!notificationPreferencesHolder.getMainEnabled()) {
            if (BuildConfig.DEBUG) Log.i(NOTIFICATIONS_LOG_TAG, "Skip websocket event: app preference disabled")
            return
        }

        if (event.isRead) {
            checkOldEvent(event)
            return
        }
        eventsHistory[event.notifyId()] = event
        notifyTabs(TabNotification(
                event.source,
                event.type,
                event,
                true
        ))
        handleEvent(listOf(event), event.source)
    }


    private fun handleEvent(events: List<NotificationEvent>, source: NotificationEvent.Source) {
        val pending = pendingEvents[source]
        if (pending != null) {
            for (event in events) {
                pending[event.sourceId] = event
            }
            schedulePendingAggregationIfNeeded()
        }
    }

    private fun hardHandleEvent(source: NotificationEvent.Source) {
        hardHandleEvent(emptyList(), source)
    }

    private fun hardHandleEvent(events: List<NotificationEvent>, source: NotificationEvent.Source) {
        if (!notificationPreferencesHolder.getMainEnabled()) {
            if (BuildConfig.DEBUG) Log.i(NOTIFICATIONS_LOG_TAG, "Skip hard event check: app preference disabled")
            return
        }

        if (NotificationEvent.fromSite(source)) {
            if (notificationPreferencesHolder.getMentionsEnabled()) {
                for (event in events) {
                    sendNotification(event)
                }
            }
            return
        }
        if (!NotificationEvent.fromQms(source) && !NotificationEvent.fromTheme(source)) {
            return
        }

        repoScope.launch(ioDispatcher) {
            val loadedEvents = runCatching<List<NotificationEvent>> {
                if (NotificationEvent.fromQms(source)) {
                    eventsApi.getQmsEvents()
                } else {
                    eventsApi.getFavoritesEvents()
                }
            }.getOrElse { e ->
                Timber.e(e, "Favorites events error")
                emptyList()
            }

            val savedEvents = getSavedEvents(source)
            saveEvents(loadedEvents, source)
            val newEvents = compareEvents(savedEvents, loadedEvents, events, source)
            val stackedNewEvents = newEvents.toMutableList()

            checkOldEvents(loadedEvents, source)

            for (event in events) {
                for (newEvent in newEvents) {
                    if (newEvent.sourceId == event.sourceId) {
                        stackedNewEvents.remove(newEvent)
                        newEvent.type = event.type
                        newEvent.messageId = event.messageId

                        notifyTabs(TabNotification(
                                newEvent.source,
                                newEvent.type,
                                newEvent,
                                false,
                                loadedEvents.toList(),
                                newEvents.toList()
                        ))

                        sendNotification(newEvent)
                    } else if (event.isMention && !notificationPreferencesHolder.getFavEnabled()) {
                        stackedNewEvents.remove(newEvent)
                    }
                }
            }

            sendNotifications(stackedNewEvents, source)
        }
    }


    private fun handlePendingEvents(source: NotificationEvent.Source) {
        val pending = pendingEvents[source]
        if (pending != null && pending.isNotEmpty()) {
            hardHandleEvent(pending.map { it.value }, source)
            pending.clear()
        }
    }


    private fun getSavedEvents(source: NotificationEvent.Source): List<NotificationEvent> {
        val savedEvents: Set<String> = when {
            NotificationEvent.fromQms(source) -> notificationPreferencesHolder.getDataQmsEvents()
            NotificationEvent.fromTheme(source) -> notificationPreferencesHolder.getDataFavoritesEvents()
            else -> return emptyList()
        }

        val responseBuilder = StringBuilder()
        for (saved in savedEvents) {
            responseBuilder.append(saved).append('\n')
        }
        val response = responseBuilder.toString()

        if (NotificationEvent.fromQms(source)) {
            return eventsApi.getQmsEvents(response)
        } else if (NotificationEvent.fromTheme(source)) {
            return eventsApi.getFavoritesEvents(response)
        }
        return emptyList()
    }

    private fun saveEvents(loadedEvents: List<NotificationEvent>, source: NotificationEvent.Source) {
        val savedEvents = androidx.collection.ArraySet<String>()
        for (event in loadedEvents) {
            savedEvents.add(event.sourceEventText.orEmpty())
        }
        if (NotificationEvent.fromQms(source)) {
            notificationPreferencesHolder.setDataQmsEvents(savedEvents)
        } else if (NotificationEvent.fromTheme(source)) {
            notificationPreferencesHolder.setDataFavoritesEvents(savedEvents)
        }
    }

    private fun compareEvents(
            savedEvents: List<NotificationEvent>,
            loadedEvents: List<NotificationEvent>,
            events: List<NotificationEvent>,
            source: NotificationEvent.Source
    ): List<NotificationEvent> {
        val newEvents = mutableListOf<NotificationEvent>()

        for (loaded in loadedEvents) {
            var isNew = true
            for (saved in savedEvents) {
                if (loaded.sourceId == saved.sourceId && loaded.timeStamp <= saved.timeStamp) {
                    isNew = false
                    break
                }
            }

            if (isNew) {
                newEvents.add(loaded)
            }
        }

        if (NotificationEvent.fromTheme(source) && notificationPreferencesHolder.getFavOnlyImportant()) {
            val toRemove = mutableListOf<NotificationEvent>()
            val mentionTopicIds = events
                    .asSequence()
                    .filter { it.isMention }
                    .map { it.sourceId }
                    .toSet()
            for (newEvent in newEvents) {
                if (!newEvent.isImportant && newEvent.sourceId !in mentionTopicIds) {
                    toRemove.add(newEvent)
                }
            }
            for (removeEvent in toRemove) {
                newEvents.remove(removeEvent)
            }
            toRemove.clear()
        }

        return newEvents
    }

    /**
     * Останавливает активную проверку событий без разрушения singleton-репозитория.
     */
    fun cleanup() {
        stop("cleanup")
    }

}

private fun NotificationEvent.notificationLogCategory(): String = when {
    isMention -> "mention"
    fromQms() -> "qms"
    fromTheme() -> "favorite"
    fromSite() -> "site"
    else -> "unknown"
}