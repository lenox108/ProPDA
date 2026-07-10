package forpdateam.ru.forpda.model.repository.events

import android.app.Application
import android.content.Context
import android.os.SystemClock
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
import java.util.concurrent.TimeUnit

class EventsRepository(
        _context: Context,
        private val application: Application,
        private val webClient: IWebClient,
        private val eventsApi: NotificationEventsApi,
        private val networkStateProvider: NetworkStateProvider,
        private val authHolder: AuthHolder,
        private val countersHolder: CountersHolder,
        private val notificationPreferencesHolder: NotificationPreferencesHolder,
        private val mentionsRepository: MentionsRepository,
        private val hatVersionWatcher: forpdateam.ru.forpda.notifications.hatwatch.HatVersionWatcher
) {
    companion object {
        private const val NOTIFICATIONS_LOG_TAG = "Notifications"

        /**
         * Окно склейки websocket-событий перед походом в act=inspector за заголовком и ником.
         * Событие WS несёт только sourceId/messageId/type, поэтому запрос неизбежен, а окно
         * нужно, чтобы всплеск сообщений в одном диалоге стоил одного запроса, а не пяти.
         * Всплеск укладывается в пару секунд; минута здесь означала бы, что каждое уведомление
         * приходит в шторку на минуту позже события.
         */
        private const val NOTIFICATION_AGGREGATION_PERIOD_MS = 2_500L

        /** Не чаще одного «жёсткого» опроса inspector'а на источник за этот интервал. */
        private const val HARD_CHECK_MIN_INTERVAL_MS = 60_000L

        /**
         * Сколько WS живёт после ухода приложения в фон. Короткое переключение в другое
         * приложение не должно стоить полного TLS-хендшейка на возврате, а уведомления
         * продолжают приходить ещё минуту — ровно когда пользователь ждёт ответ.
         */
        private const val BACKGROUND_GRACE_MS = 45_000L

        /** Верхняя граница истории событий: нужна только для отмены ещё висящих уведомлений. */
        private const val EVENTS_HISTORY_MAX = 200

        /**
         * Максимальное количество событий для группового (stacked) уведомления.
         * Если событий больше — отправляется summary-уведомление с количеством.
         * Значение 4 подобрано эмпирически для оптимального UX на мобильных устройствах.
         */
        private const val STACKED_MAX = 4

        /**
         * Idle-таймаут foreground-WebSocket: если приложение открыто, но пользователь не
         * взаимодействует дольше этого срока, WS отключается (пинги каждые 60с зря будят
         * радио). Реконнект — по первому касанию ([notifyUserActive]). Экраны, которым
         * нужен живой WS (открытый QMS-чат), удерживают его через refcount
         * ([requestRealtimeForScreen]/[isRealtimeScreenActive]) и idle-паузу не получают.
         */
        private const val FOREGROUND_IDLE_TIMEOUT_MS = 5 * 60_000L
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

    // Idle-disconnect state (foreground WS). idleDisconnected=true означает, что WS сознательно
    // закрыт из-за бездействия, хотя foregroundRealtimeEnabled ещё true — реконнект делает только
    // notifyUserActive(). lastInteractionAt — монотонная метка последнего касания.
    @Volatile
    private var idleDisconnected = false
    @Volatile
    private var lastInteractionAt = SystemClock.elapsedRealtime()
    private var idleJob: Job? = null
    private var backgroundGraceJob: Job? = null

    private val hardCheckLock = Any()
    private var lastHardCheckAtMs = 0L

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

    // synchronizedMap: put/remove атомарны; итерации (checkOldEvents/onTopicRead/
    // onTopicPostsRead) берут снапшот под synchronized(eventsHistory) — иначе
    // ConcurrentModificationException.
    //
    // Записи удаляются только при прочтении события, поэтому без ограничения карта росла бы
    // весь сеанс. Держим окно последних событий: более старые уведомления пользователь всё
    // равно уже смахнул, и отменять их нечего.
    private val eventsHistory: MutableMap<Int, NotificationEvent> =
            java.util.Collections.synchronizedMap(
                    object : LinkedHashMap<Int, NotificationEvent>(64, 0.75f, false) {
                        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, NotificationEvent>): Boolean =
                                size > EVENTS_HISTORY_MAX
                    }
            )


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

    /**
     * Колбэки OkHttp приходят на его читающем потоке, а [pendingEvents],
     * [pendingAggregationJob] и [reconnectAttempts] читаются и пишутся с главного.
     * Все они — обычные не-потокобезопасные поля, поэтому обработку целиком заводим
     * в [repoScope] (Main.immediate): состояние остаётся однопоточным, порядок событий
     * сохраняется. Разбор текста оставляем на потоке OkHttp — он ничего не мутирует.
     */
    private val controllerListener: WebSocketController.Listener = object : WebSocketController.Listener() {
        override fun onConnected() {
            if (BuildConfig.DEBUG) Timber.d("WSContr onConnected ${webSocketController.getCurrentId()},  ${webSocketController.isConnected()}")
            // onConnected приходит с потока OkHttp; reconnectAttempts/idleJob живут на Main —
            // сериализуем на repoScope.
            repoScope.launch {
                // Без сброса бэкофф рос через весь сеанс: после пары обрывов даже первый сбой
                // на здоровой сети ждал бы пять минут до переподключения.
                reconnectAttempts = 0
                webSocketController.send("""[${webSocketController.getCurrentId()}, "sv"]""")
                webSocketController.send("""[0, "ea", "u${authHolder.get().userId}"]""")
                // Соединение живо — запускаем/перезапускаем idle-таймер относительно него.
                armIdleTimer()
            }
        }

        override fun onMessage(text: String?) {
            if (BuildConfig.DEBUG) Timber.d("WSContr onMessage ${webSocketController.getCurrentId()}, ${webSocketController.isConnected()}, hasPayload=${!text.isNullOrEmpty()}")
            val event = try {
                eventsApi.parseWebSocketEvent(text.orEmpty())
            } catch (ex: Exception) {
                Timber.e(ex, "Events parse error")
                null
            } ?: return
            if (event.type == NotificationEvent.Type.HAT_EDITED) {
                // Сервер сообщил, что в теме тронули шапку. Точечно пере-сканируем именно её
                // на предмет нового apk (фича «Следить за новыми версиями»). Раньше это
                // событие молча выбрасывалось.
                onHatEditedEvent(event)
                return
            }
            repoScope.launch { handleWebSocketEvent(event) }
        }

        override fun onDisconnected(throwable: Throwable, response: Response?) {
            if (BuildConfig.DEBUG) Timber.d("WSContr onDisconnected ${webSocketController.getCurrentId()}, ${webSocketController.isConnected()}, code=${response?.code}")
            Timber.e(throwable, "Events check error")

            if (response?.code == 403) {
                // Проблема не в соединении, а в авторизации: переподключение будет
                // отбито тем же 403 и превратится в шторм.
                (application as forpdateam.ru.forpda.App).notifyForbidden(true)
                return
            }
            // Переподключаемся на ЛЮБОМ обрыве, а не только по таймауту: штатный close-фрейм
            // сервера и обычные для мобильной сети IOException раньше молча убивали realtime
            // до конца сеанса. Бэкофф и гейт по foreground не дают этому стать штормом.
            repoScope.launch {
                if (foregroundRealtimeEnabled) {
                    if (BuildConfig.DEBUG) Timber.d("start onDisconnected")
                    start(checkEvents = false, force = false)
                }
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

    /**
     * Приложение вернулось на передний план. Отменяем отложенный разрыв WS: если пользователь
     * просто переключился в другое приложение на пару секунд, соединение переживает это без
     * повторного TLS-хендшейка.
     */
    fun onAppForegrounded() {
        backgroundGraceJob?.cancel()
        backgroundGraceJob = null
        setForegroundRealtimeEnabled(true, "process_start")
    }

    /**
     * Приложение ушло в фон. WS не рвём сразу: держим [BACKGROUND_GRACE_MS], потому что
     * ответ обычно приходит в первые секунды после того, как пользователь свернул чат.
     */
    fun onAppBackgrounded() {
        if (!foregroundRealtimeEnabled) return
        backgroundGraceJob?.cancel()
        backgroundGraceJob = repoScope.launch {
            delay(BACKGROUND_GRACE_MS)
            backgroundGraceJob = null
            // Reason обязан содержать process_stop: по нему NotificationsService снимает FGS.
            setForegroundRealtimeEnabled(false, "process_stop")
        }
    }

    /**
     * Жёсткий опрос inspector'а стоит двух сетевых запросов, а на возврат в приложение его
     * просят сразу три независимых места (ProcessLifecycle, onCreate сервиса, onStartCommand).
     */
    private fun consumeHardCheckSlot(): Boolean = synchronized(hardCheckLock) {
        val now = System.currentTimeMillis()
        if (now - lastHardCheckAtMs < HARD_CHECK_MIN_INTERVAL_MS) {
            BatteryDebugLogger.logState("EventsRepository", "skipHardCheck", "throttled")
            return false
        }
        lastHardCheckAtMs = now
        true
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
            cancelIdle()
            stop("notifications_disabled")
            return
        }
        foregroundRealtimeEnabled = enabled
        BatteryDebugLogger.logState("EventsRepository", if (enabled) "foreground" else "background", "reason=$reason")
        foregroundRealtimeFlow.tryEmit(ForegroundRealtimeChange(enabled, reason))
        if (enabled) {
            // Свежий foreground-сеанс: снимаем idle-паузу и стартуем полный отсчёт бездействия.
            idleDisconnected = false
            lastInteractionAt = SystemClock.elapsedRealtime()
            start(checkEvents = true, force = true)
            armIdleTimer()
        } else {
            cancelIdle()
            stop("background:$reason")
        }
    }

    /**
     * Сигнал о пользовательском взаимодействии (касание/скролл). Вызывается из
     * [forpdateam.ru.forpda.ui.activities.MainActivity.onUserInteraction]. Дёшево: обычно лишь
     * обновляет метку времени. Если WS был закрыт по idle — мгновенно переподнимает его и
     * досинхронизирует пропущенные события.
     */
    fun notifyUserActive() {
        lastInteractionAt = SystemClock.elapsedRealtime()
        if (!foregroundRealtimeEnabled) return
        if (!idleDisconnected) return
        idleDisconnected = false
        BatteryDebugLogger.logState("EventsRepository", "idleReconnect", "user_active")
        start(checkEvents = true, force = true)
        armIdleTimer()
    }

    /**
     * Запускает (перезапускает) сторож бездействия. По достижении [FOREGROUND_IDLE_TIMEOUT_MS]
     * без взаимодействия закрывает WS, ЕСЛИ ни один экран не удерживает realtime
     * ([isRealtimeScreenActive]); иначе откладывает паузу.
     */
    private fun armIdleTimer() {
        idleJob?.cancel()
        if (!foregroundRealtimeEnabled) return
        idleJob = repoScope.launch {
            while (foregroundRealtimeEnabled && !idleDisconnected) {
                val remaining = FOREGROUND_IDLE_TIMEOUT_MS - (SystemClock.elapsedRealtime() - lastInteractionAt)
                if (remaining > 0) {
                    delay(remaining)
                    continue
                }
                if (!webSocketController.isConnected()) {
                    // Активного WS нет (сеть отвалилась / идёт реконнект) — отключать нечего, и
                    // ставить idleDisconnected нельзя, иначе заблокируем авто-реконнект при
                    // восстановлении сети. Откладываем проверку на следующий цикл.
                    lastInteractionAt = SystemClock.elapsedRealtime()
                    continue
                }
                if (isRealtimeScreenActive()) {
                    // Открытый QMS-чат (или иной realtime-экран) требует живого WS — откладываем паузу.
                    lastInteractionAt = SystemClock.elapsedRealtime()
                    continue
                }
                idleDisconnected = true
                reconnectAttempts = 0
                BatteryDebugLogger.logState("EventsRepository", "idleDisconnect", "timeoutMs=$FOREGROUND_IDLE_TIMEOUT_MS")
                cancelTimer()
                webSocketController.disconnectAll()
                break
            }
        }
    }

    private fun cancelIdle() {
        idleJob?.cancel()
        idleJob = null
        idleDisconnected = false
    }

    /**
     * Экран, которому нужен realtime-WebSocket, запрашивает разрешение через refcount.
     * Пока хотя бы один экран держит счётчик > 0, [armIdleTimer] НЕ закрывает WS по
     * бездействию (см. [isRealtimeScreenActive] в idle-петле) — даже если пользователь
     * долго не касается экрана. Это нужно, например, открытому QMS-чату: при живом WS он
     * получает сообщения мгновенно, а без него уходит в поллинг (дороже по батарее).
     * Сам по себе вызов НЕ открывает WS — для открытия по-прежнему требуется
     * foreground + network + auth. Парный вызов — [releaseRealtimeForScreen].
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
        val snapshot = synchronized(eventsHistory) { eventsHistory.entries.map { it.key to it.value } }
        for ((key, event) in snapshot) {
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
        val snapshot = synchronized(eventsHistory) { eventsHistory.entries.map { it.key to it.value } }
        for ((key, event) in snapshot) {
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

    /**
     * Регистрируем непрочитанное упоминание (topic+post) из realtime-уведомления в [mentionsRepository],
     * чтобы строка в списке «Ответы» оставалась жирной, даже если act=mentions отдаёт её прочитанной.
     * Бейдж уже горит из шапки форума (index_header); здесь мы лишь синхронизируем с ним список.
     */
    private fun feedMentionUnreadFromEvent(event: NotificationEvent) {
        if (!event.fromTheme() || !event.isMention || event.isRead) return
        val topicId = event.sourceId
        val postId = event.messageId
        if (topicId <= 0 || postId <= 0) return
        repoScope.launch(ioDispatcher) {
            runCatching { mentionsRepository.markMentionUnreadFromNotification(topicId, postId) }
                    .onFailure { Timber.e(it, "feedMentionUnreadFromEvent failed") }
        }
    }

    /**
     * READ-событие по упоминанию (например, прочитано на другом устройстве) — снимаем override
     * «жирной» строки, чтобы список «Ответы» не держал упоминание непрочитанным после факта прочтения.
     */
    private fun clearMentionUnreadOverride(event: NotificationEvent) {
        if (!event.fromTheme() || !event.isMention) return
        val topicId = event.sourceId
        val postId = event.messageId
        if (topicId <= 0 || postId <= 0) return
        repoScope.launch(ioDispatcher) {
            runCatching { mentionsRepository.removeUnreadFromEvent(topicId, postId) }
                    .onFailure { Timber.e(it, "clearMentionUnreadOverride failed") }
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
        // Единственная точка, через которую открывается WS и уходят inspector-запросы, поэтому
        // проверку «а нужны ли вообще push» держим здесь. Иначе подписки на сеть и авторизацию
        // в init воскрешали соединение после того, как пользователь всё выключил.
        if (!notificationPreferencesHolder.wantsPushNotifications()) {
            BatteryDebugLogger.logState("EventsRepository", "skipStart", "notifications disabled")
            stop("notifications_disabled")
            return
        }
        // Пока действует idle-пауза, автоматические триггеры (сеть/авторизация/сервис) WS не
        // поднимают — реконнект делает только пользовательское взаимодействие (notifyUserActive
        // снимает флаг перед вызовом start()).
        if (idleDisconnected) {
            BatteryDebugLogger.logState("EventsRepository", "skipStart", "idle_disconnected check=$checkEvents")
            return
        }
        if (networkStateProvider.getState() && authHolder.get().isAuth()) {
            // Whether to hold a socket open is now decided in ONE place (see [RealtimeConnectPolicy]).
            // This branch used to check only network+auth, so a network or auth change re-opened the
            // socket after NotificationsService had stopped it because the user disabled notifications:
            // `stop()` never clears `foregroundRealtimeEnabled`, so the guard in
            // `setForegroundRealtimeEnabled()` was bypassed and the 45-second pings came back.
            val wantsRealtime = RealtimeConnectPolicy.shouldOpenWebSocket(
                    foregroundRealtime = foregroundRealtimeEnabled,
                    networkAvailable = networkStateProvider.getState(),
                    authorized = authHolder.get().isAuth(),
                    wantsPushNotifications = notificationPreferencesHolder.wantsPushNotifications(),
            )
            if (!wantsRealtime) {
                BatteryDebugLogger.logState("EventsRepository", "skipWebSocket", "notifications disabled")
                if (webSocketController.isConnected()) {
                    stop("notifications_disabled")
                }
            } else if (!webSocketController.isConnected()) {
                if (!force && reconnectAttempts > 0) {
                    val delayMs = reconnectBackoffMs()
                    BatteryDebugLogger.logState("EventsRepository", "reconnectBackoff", "attempt=$reconnectAttempts delayMs=$delayMs")
                    repoScope.launch {
                        delay(delayMs)
                        if (!webSocketController.isConnected()) {
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

            if (checkEvents && consumeHardCheckSlot()) {
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
        feedMentionUnreadFromEvent(event)
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

    /**
     * Пришло READ-событие: снимаем уведомления, которые оно закрывает.
     *
     * Раньше из истории удалялся только NEW-ключ, а MENTION оставался навсегда: карта росла,
     * а каждое следующее READ повторно слало отмену уже снятого уведомления. Теперь ключ
     * удаляется тот же, по которому уведомление нашли.
     */
    private fun checkOldEvent(event: NotificationEvent) {
        if (BuildConfig.DEBUG) {
            Timber.d("checkOldEvent new=$event")
        }

        if (event.fromTheme()) {
            // Уведомление избранного закрывается только сообщением не старше показанного.
            cancelAndForget(event.notifyId(NotificationEvent.Type.NEW)) { old ->
                event.messageId >= old.messageId
            }
            cancelAndForget(event.notifyId(NotificationEvent.Type.MENTION))
        } else if (event.fromQms()) {
            cancelAndForget(event.notifyId(NotificationEvent.Type.NEW))
        }

        // Вкладки обновляем всегда: даже если снимать было нечего, счётчик мог измениться
        // на другом устройстве.
        notifyTabs(TabNotification(
                event.source,
                event.type,
                event,
                true
        ))
    }

    private inline fun cancelAndForget(notifyId: Int, shouldCancel: (NotificationEvent) -> Boolean = { true }) {
        val old = eventsHistory[notifyId] ?: return
        if (!shouldCancel(old)) return
        cancelEventFlow.tryEmit(old)
        eventsHistory.remove(notifyId)
    }

    private fun checkOldEvents(loadedEvents: List<NotificationEvent>, source: NotificationEvent.Source) {
        val oldEvents = synchronized(eventsHistory) {
            eventsHistory.values.filter { it.source == source }
        }

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

    /**
     * Realtime-событие «в теме изменена шапка». Если пользователь следит за версиями этой темы —
     * запускаем детектор нового apk в фоне (сеть — на ioDispatcher).
     */
    private fun onHatEditedEvent(event: NotificationEvent) {
        if (!event.fromTheme()) return
        val topicId = event.sourceId
        if (topicId <= 0) return
        if (!notificationPreferencesHolder.getMainEnabled()) return
        if (!notificationPreferencesHolder.getHatEnabled()) return
        if (!notificationPreferencesHolder.isHatWatched(topicId)) return
        repoScope.launch(ioDispatcher) {
            runCatching { hatVersionWatcher.check(topicId) }
                    .onFailure { Timber.e(it, "HAT_EDITED hat check failed for topic $topicId") }
        }
    }

    /**
     * An event that already arrived is processed regardless of the notification preference: muting
     * notifications must not freeze the app itself. [notifyTabs] is the in-app bus — the QMS badge
     * ([forpdateam.ru.forpda.model.repository.qms.QmsRepository.handleEvent]), the favourites badge and
     * an open QMS chat all hang off it — and it shows nothing to the user.
     *
     * Only the notification-producing tail stays gated. It is a two-layer gate already:
     * [sendNotification] re-checks the master toggle and [checkNotify] re-checks each event family, so
     * nothing leaks through. [handleEvent] is skipped explicitly because its pending-event aggregation
     * ends in [hardHandleEvent], which goes to the NETWORK for the sole purpose of building
     * notifications — pointless traffic when the user asked for none.
     */
    private fun handleWebSocketEvent(event: NotificationEvent) {
        if (event.isRead) {
            clearMentionUnreadOverride(event)
            checkOldEvent(event)
            return
        }
        feedMentionUnreadFromEvent(event)
        eventsHistory[event.notifyId()] = event
        notifyTabs(TabNotification(
                event.source,
                event.type,
                event,
                true
        ))
        if (!notificationPreferencesHolder.getMainEnabled()) {
            if (BuildConfig.DEBUG) Log.i(NOTIFICATIONS_LOG_TAG, "Skip websocket event notification: app preference disabled")
            return
        }
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

            // События, о которых уже сообщил WebSocket, показываем поимённо (у них есть тип и
            // messageId), остальные новые уходят в общий стек.
            //
            // Здесь стоял `else if (event.isMention && !favEnabled) stackedNewEvents.remove(newEvent)`,
            // который выбрасывал из стека события, НЕ совпавшие по sourceId, — то есть чужие.
            // Он был и лишним: стек всё равно проходит через checkNotify(), где выключенное
            // избранное отсекается целиком.
            for (event in events) {
                for (newEvent in newEvents) {
                    if (newEvent.sourceId != event.sourceId) continue
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