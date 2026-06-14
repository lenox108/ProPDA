package forpdateam.ru.forpda.presentation.qms.chat

import forpdateam.ru.forpda.presentation.BaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

import forpdateam.ru.forpda.entity.app.TabNotification
import forpdateam.ru.forpda.entity.remote.editpost.AttachmentItem
import forpdateam.ru.forpda.entity.remote.events.NotificationEvent
import forpdateam.ru.forpda.entity.remote.qms.QmsChatModel
import forpdateam.ru.forpda.entity.remote.qms.QmsMessage
import forpdateam.ru.forpda.model.data.remote.api.RequestFile
import forpdateam.ru.forpda.model.interactors.qms.QmsChatLoadOutcome
import forpdateam.ru.forpda.model.interactors.qms.QmsChatMemoryCache
import forpdateam.ru.forpda.model.interactors.qms.QmsInteractor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import forpdateam.ru.forpda.model.repository.avatar.AvatarRepository
import forpdateam.ru.forpda.model.repository.events.EventsRepository
import forpdateam.ru.forpda.model.preferences.MainPreferencesHolder
import forpdateam.ru.forpda.presentation.IErrorHandler
import forpdateam.ru.forpda.presentation.ILinkHandler
import forpdateam.ru.forpda.presentation.Screen
import forpdateam.ru.forpda.presentation.TabRouter
import forpdateam.ru.forpda.ui.TemplateManager
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import forpdateam.ru.forpda.diagnostic.FpdaDebugLog
import forpdateam.ru.forpda.diagnostic.QmsOpenLog
import forpdateam.ru.forpda.diagnostic.StateRaceTrace
import forpdateam.ru.forpda.BuildConfig
import kotlinx.coroutines.launch
import timber.log.Timber

@HiltViewModel
class QmsChatViewModel @Inject constructor(
        private val qmsInteractor: QmsInteractor,
        private val qmsChatTemplate: QmsChatTemplate,
        private val avatarRepository: AvatarRepository,
        private val eventsRepository: EventsRepository,
        private val mainPreferencesHolder: MainPreferencesHolder,
        private val templateManager: TemplateManager,
        private val router: TabRouter,
        private val linkHandler: ILinkHandler,
        private val errorHandler: IErrorHandler
) : BaseViewModel(), QmsChatWebCallbacks {

    companion object {
        const val MODE_CHAT = "chat"
        const val MODE_CREATING = "creating"
        private const val WS_POLL_SUPPRESS_MS = 120_000L
        /**
         * If the in-memory QMS chat cache is fresher than this, a second open of the same dialog
         * within the [QmsChatMemoryCache.MAX_AGE_MS] window skips the background network refresh
         * and renders from cache only. Set to 60s — short enough to stay current with active
         * sessions, long enough to avoid redundant fetches on tab re-entries.
         */
        private const val QMS_BACKGROUND_REFRESH_SKIP_MS = 60_000L
    }

    private var subscriptionsStarted = false

    var themeId = QmsChatModel.NOT_CREATED
    var userId = QmsChatModel.NOT_CREATED
    var title: String? = null
    var nick: String? = null
    var avatarUrl: String? = null

    private var currentMode = MODE_CHAT

    private val _chatMode = MutableStateFlow(MODE_CHAT)
    val chatMode: StateFlow<String> = _chatMode.asStateFlow()

    private var currentData: QmsChatModel? = null

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    private val _messageRefreshing = MutableStateFlow(false)
    val messageRefreshing: StateFlow<Boolean> = _messageRefreshing.asStateFlow()

    private val _newMessagesRefreshing = MutableStateFlow(false)
    val newMessagesRefreshing: StateFlow<Boolean> = _newMessagesRefreshing.asStateFlow()

    /** Buffers chat UI events until [QmsChatFragment] collector is STARTED (no replay). */
    private val _uiEvents = MutableSharedFlow<QmsChatUiEvent>(extraBufferCapacity = 32)
    val uiEvents: SharedFlow<QmsChatUiEvent> = _uiEvents.asSharedFlow()

    private var loadedChatKey: String? = null
    /** Dialog key for an in-flight [loadChat]; [loadedChatKey] is set only after success. */
    private var inFlightLoadKey: String? = null
    private var loadRequestId = 0
    private var loadJob: Job? = null
    private var openTraceId: String = FpdaDebugLog.newTraceId()
    private var lastRealtimeMessageAtMs = 0L

    private val _threadState = MutableStateFlow<QmsThreadUiState>(QmsThreadUiState.Idle)
    val threadState: StateFlow<QmsThreadUiState> = _threadState.asStateFlow()

    fun start() {
        val key = chatKey(userId, themeId)
        if (subscriptionsStarted) {
            if (key == loadedChatKey && currentData != null) {
                syncLoadedChatToUi()
                return
            }
            if (isLoadInFlightFor(key)) {
                return
            }
            onChatIdentityChanged()
            return
        }
        subscriptionsStarted = true

        scope.launch {
            mainPreferencesHolder.observeWebViewFontSizeFlow().collect {
                scope.launch { _uiEvents.emit(QmsChatUiEvent.SetFontSize(it)) }
            }
        }

        scope.launch {
            mainPreferencesHolder.observeAppFontModeFlow().collect {
                scope.launch { _uiEvents.emit(QmsChatUiEvent.SetAppFontMode(it)) }
            }
        }

        scope.launch {
            templateManager.observeThemeTypeFlow().collect {
                scope.launch { _uiEvents.emit(QmsChatUiEvent.SetStyleType(it)) }
            }
        }
        scope.launch {
            eventsRepository.observeEventsTab()
                    .collect { handleEvent(it) }
        }
        nick?.let { n -> title?.let { t -> scope.launch { _uiEvents.emit(QmsChatUiEvent.SetTitles(t, n)) } } }

        updateMode()
        if (currentMode == MODE_CHAT) {
            tryShowAvatar()
            if (!isLoadInFlightFor(key) && !(key == loadedChatKey && currentData != null)) {
                loadChat()
            }
        }
    }

    private fun isLoadInFlightFor(key: String): Boolean =
            key == inFlightLoadKey && loadJob?.isActive == true

    override fun onCleared() {
        super.onCleared()
    }

    private fun updateMode() {
        currentMode = if (themeId == QmsChatModel.NOT_CREATED || userId == QmsChatModel.NOT_CREATED) {
            MODE_CREATING
        } else {
            MODE_CHAT
        }
        _chatMode.value = currentMode
        scope.launch { _uiEvents.emit(QmsChatUiEvent.SetChatMode(currentMode)) }
    }

    private fun updateCurrentData(newData: QmsChatModel) {
        currentData = newData
        themeId = newData.themeId
        userId = newData.userId
        title = newData.title
        nick = newData.nick
        avatarUrl = newData.avatarUrl
        loadedChatKey = chatKey(userId, themeId)
        updateMode()
    }

    fun findUser(nick: String) {
        scope.launch {
            runCatching { qmsInteractor.findUser(nick) }
                    .onSuccess { scope.launch { _uiEvents.emit(QmsChatUiEvent.OnShowSearchRes(it)) } }
                    .onFailure { errorHandler.handle(it) }
        }
    }

    /**
     * Pushes already-loaded messages into the WebView after view recreation or when the UI
     * collector was not yet active when [loadChat] finished.
     */
    fun syncLoadedChatToUi(clearExisting: Boolean = true) {
        val data = currentData ?: return
        scope.launch {
            _uiEvents.emit(QmsChatUiEvent.ShowChat(data))
            val end = data.messages.size
            val start = maxOf(end - 30, 0)
            val visible = data.messages.subList(start, end).toList()
            if (visible.isNotEmpty()) {
                _uiEvents.emit(QmsChatUiEvent.ResetAndShowMessages(visible, clearExisting))
            }
        }
    }

    /** Called when the alone QMS tab is reused for another dialog or on fragment re-entry. */
    fun onChatIdentityChanged() {
        val key = chatKey(userId, themeId)
        logChat("identity_check", mapOf("requestKey" to key))
        if (key == loadedChatKey && currentData != null) {
            syncLoadedChatToUi()
            return
        }
        loadJob?.cancel()
        loadRequestId++
        loadJob = null
        inFlightLoadKey = null
        currentData = null
        loadedChatKey = null
        QmsOpenTiming.clear(openTraceId)
        openTraceId = FpdaDebugLog.newTraceId()
        transitionThreadState(QmsThreadUiState.Idle, "identity_reset")
        updateMode()
        if (currentMode == MODE_CHAT) {
            tryShowAvatar()
            loadChat()
        }
    }

    fun retryLoadChat() {
        loadChat(forceRefresh = true)
    }

    /** Exposed for WebView render diagnostics (release logcat / snackbar detail). */
    fun traceIdForDiagnostics(): String = openTraceId

    private fun chatKey(userId: Int, themeId: Int) = "$userId:$themeId"

    private fun logChat(event: String, extra: Map<String, Any?> = emptyMap()) {
        if (!BuildConfig.DEBUG) return
        FpdaDebugLog.logQms(
                FpdaDebugLog.QmsArea.CHAT,
                event,
                buildMap {
                    put("traceId", openTraceId)
                    put("chatId", themeId)
                    put("userId", userId)
                    put("loadedKey", loadedChatKey)
                    put("requestId", loadRequestId)
                    putAll(extra)
                }
        )
    }

    private fun loadChat(
            bypassCache: Boolean = false,
            forceRefresh: Boolean = false
    ) {
        if (userId == QmsChatModel.NOT_CREATED || themeId == QmsChatModel.NOT_CREATED) {
            return
        }
        if (themeId <= 0) {
            logChat("load_skipped_invalid_theme", mapOf("userId" to userId, "themeId" to themeId))
            // This is the on-device symptom for system alerts ("Сообщения 4PDA" → "Оповещения"):
            // the themes list synthesised a virtual theme with id=0 (see QmsParser.parseThemes /
            // QmsApi.getThemesList), so navigation arrives here with themeId=0 and we never even
            // fetch the thread. Surface it on the FPDA_QMS_OPEN stream so it is captured in logcat.
            QmsOpenTrace.logOpen(
                    traceId = openTraceId,
                    dialogId = themeId,
                    userId = userId,
                    requestId = loadRequestId,
                    phase = "open_invalid_theme_rejected",
                    requestedUrl = "https://4pda.to/forum/index.php?act=qms&mid=$userId&t=$themeId",
                    sourceScreen = if (userId == 0) "qms_chat_system_alerts" else "qms_chat",
                    messagesCount = 0,
                    parserErrorMessage = "invalid_theme_id",
                    finalUiState = "Empty(invalid_theme_id)",
                    extra = mapOf("mid" to userId)
            )
            transitionThreadState(QmsThreadUiState.Empty(loadRequestId, "invalid_theme_id"), "invalid_theme_id")
            return
        }
        loadJob?.cancel()
        val requestId = ++loadRequestId
        val requestKey = chatKey(userId, themeId)
        val traceId = openTraceId
        inFlightLoadKey = requestKey
        QmsOpenTiming.markOpenStart(traceId)
        val instantCache = if (!bypassCache) QmsChatMemoryCache.toLoadOutcome(userId, themeId) else null
        val cacheAgeMs = if (instantCache != null) {
            QmsChatMemoryCache.cacheAgeMs(userId, themeId) ?: 0L
        } else {
            null
        }
        // Fresh-cache fast path: if we have a valid instant cache and the caller didn't ask for
        // an explicit refresh AND the cache is younger than [QMS_BACKGROUND_REFRESH_SKIP_MS],
        // skip the background network fetch and render from cache only. WebSocket-driven updates
        // and pull-to-refresh both go through [forceRefresh] = true.
        val skipBackgroundRefresh = instantCache != null &&
                !forceRefresh &&
                (cacheAgeMs ?: Long.MAX_VALUE) < QMS_BACKGROUND_REFRESH_SKIP_MS
        logChat(
                "load_start",
                mapOf(
                        "requestKey" to requestKey,
                        "requestId" to requestId,
                        "bypassCache" to bypassCache,
                        "forceRefresh" to forceRefresh,
                        "instantCache" to (instantCache != null),
                        "cacheAgeMs" to (cacheAgeMs ?: -1L),
                        "skipBgRefresh" to skipBackgroundRefresh
                )
        )
        if (skipBackgroundRefresh) {
            QmsOpenTrace.logOpen(
                    traceId = traceId,
                    dialogId = themeId,
                    userId = userId,
                    requestId = requestId,
                    phase = "cache_fresh_skip_bg_refresh",
                    cacheHit = true,
                    cacheValid = true,
                    sourceScreen = "qms_chat_cache_fresh",
                    extra = mapOf(
                            "cacheAgeMs" to (cacheAgeMs ?: 0L),
                            "skipThresholdMs" to QMS_BACKGROUND_REFRESH_SKIP_MS
                    )
            )
            scope.launch { applyInstantCacheOutcome(instantCache!!, requestId, traceId) }
            if (inFlightLoadKey == requestKey) {
                inFlightLoadKey = null
            }
            _refreshing.value = false
            return
        }
        if (instantCache == null) {
            transitionThreadState(QmsThreadUiState.Loading(requestId), "load_start")
        }
        _refreshing.value = true
        loadJob = scope.launch {
            try {
                if (instantCache != null) {
                    if (requestId == loadRequestId && requestKey == chatKey(userId, themeId)) {
                        applyInstantCacheOutcome(instantCache, requestId, traceId)
                    }
                }
                val outcome = qmsInteractor.loadChatThread(
                        userId = userId,
                        themeId = themeId,
                        traceId = traceId,
                        requestId = requestId,
                        bypassCache = bypassCache || instantCache != null,
                        sourceScreen = if (instantCache != null) "qms_chat_bg_refresh" else "qms_chat"
                )
                if (requestId != loadRequestId || requestKey != chatKey(userId, themeId)) {
                    QmsOpenTrace.logStateTransition(
                            traceId = traceId,
                            dialogId = themeId,
                            userId = userId,
                            requestId = requestId,
                            previousState = "Loading",
                            nextState = "ignored",
                            reason = "stale_request",
                            staleResultIgnored = true
                    )
                    logChat("load_stale", mapOf("requestKey" to requestKey, "requestId" to requestId))
                    val newerLoadActive = loadJob?.isActive == true && requestId < loadRequestId
                    if (currentData == null && !newerLoadActive) {
                        StateRaceTrace.log(
                                domain = "qms_load",
                                event = "stale_ignored_retry",
                                requestId = requestId,
                                reason = "stale_request_no_data",
                                extra = mapOf(
                                        "currentRequestId" to loadRequestId,
                                        "requestKey" to requestKey,
                                        "userId" to userId,
                                        "themeId" to themeId
                                )
                        )
                        retryLoadChat()
                    }
                    return@launch
                }
                if (instantCache != null) {
                    applyNetworkOutcomeAfterCache(outcome, requestId, traceId)
                } else {
                    when (outcome) {
                        is QmsChatLoadOutcome.Content ->
                                applyLoadedChat(outcome.chat, requestId, traceId, "content")
                        is QmsChatLoadOutcome.Empty ->
                                applyLoadedChat(outcome.chat, requestId, traceId, "empty")
                        is QmsChatLoadOutcome.Failure ->
                                applyLoadFailure(outcome, requestId, traceId)
                    }
                    logNetworkDoneIfApplicable(outcome, traceId, requestId)
                }
            } catch (e: Throwable) {
                if (e is CancellationException) throw e
                if (requestId == loadRequestId) {
                    val errorClass = FpdaDebugLog.errorClass(e).orEmpty()
                    val detail = listOfNotNull(
                            errorClass.takeIf { it.isNotEmpty() },
                            e.message?.trim()?.take(48)?.takeIf { it.isNotEmpty() }
                    ).joinToString(separator = " · ")
                    logChat("load_error", mapOf("error" to errorClass))
                    QmsOpenLog.openError(
                            traceId = traceId,
                            dialogId = themeId,
                            userId = userId,
                            requestId = requestId,
                            phase = "load_uncaught",
                            errorReason = QmsOpenErrorReason.Unknown.name,
                            kind = QmsLoadErrorKind.UNKNOWN.name,
                            detail = detail.ifBlank { "uncaught" }
                    )
                    applyLoadFailure(
                            QmsChatLoadOutcome.Failure(
                                    QmsLoadErrorKind.UNKNOWN,
                                    if (BuildConfig.DEBUG && detail.isNotBlank()) {
                                        "Unknown · $detail"
                                    } else {
                                        ""
                                    },
                                    canRetry = true
                            ),
                            requestId,
                            traceId
                    )
                }
            } finally {
                if (requestId == loadRequestId) {
                    if (inFlightLoadKey == requestKey) {
                        inFlightLoadKey = null
                    }
                    _refreshing.value = false
                }
            }
        }
    }

    private suspend fun applyInstantCacheOutcome(
            outcome: QmsChatLoadOutcome,
            requestId: Int,
            traceId: String
    ) {
        when (outcome) {
            is QmsChatLoadOutcome.Content -> {
                applyLoadedChat(outcome.chat, requestId, traceId, "cache_instant")
                QmsOpenTiming.logCacheShown(
                        traceId,
                        themeId,
                        userId,
                        requestId,
                        outcome.chat.messages.size
                )
            }
            is QmsChatLoadOutcome.Empty -> {
                applyLoadedChat(outcome.chat, requestId, traceId, "cache_instant_empty")
                QmsOpenTiming.logCacheShown(traceId, themeId, userId, requestId, 0)
            }
            is QmsChatLoadOutcome.Failure -> Unit
        }
        if (requestId == loadRequestId) {
            _refreshing.value = false
        }
    }

    private suspend fun applyNetworkOutcomeAfterCache(
            outcome: QmsChatLoadOutcome,
            requestId: Int,
            traceId: String
    ) {
        logNetworkDoneIfApplicable(outcome, traceId, requestId)
        when (outcome) {
            is QmsChatLoadOutcome.Content -> {
                if (chatKey(outcome.chat.userId, outcome.chat.themeId) != chatKey(userId, themeId)) return
                if (outcome.chat.messages.size >= (currentData?.messages?.size ?: 0)) {
                    val previous = currentData
                    val visibleChanged = qmsVisibleMessagesChanged(previous, outcome.chat)
                    updateCurrentData(outcome.chat)
                    transitionThreadState(QmsThreadUiState.Content(loadRequestId, outcome.chat), "network_after_cache")
                    if (visibleChanged) {
                        _uiEvents.emit(QmsChatUiEvent.ShowChat(outcome.chat))
                        emitInitialMessages(outcome.chat)
                    }
                }
            }
            is QmsChatLoadOutcome.Empty -> Unit
            is QmsChatLoadOutcome.Failure -> {
                QmsOpenTrace.logOpen(
                        traceId = traceId,
                        dialogId = themeId,
                        userId = userId,
                        requestId = requestId,
                        phase = "bg_refresh_failed",
                        parserErrorMessage = outcome.message
                )
                if (currentData != null) {
                    _uiEvents.emit(
                            QmsChatUiEvent.LoadWarning(
                                    kind = outcome.kind,
                                    cacheAgeMinutes = QmsChatMemoryCache.cacheAgeMinutes(userId, themeId)
                            )
                    )
                } else {
                    applyLoadFailure(outcome, requestId, traceId)
                }
            }
        }
    }

    private fun logNetworkDoneIfApplicable(
            outcome: QmsChatLoadOutcome,
            traceId: String,
            requestId: Int
    ) {
        val count = when (outcome) {
            is QmsChatLoadOutcome.Content -> outcome.chat.messages.size
            is QmsChatLoadOutcome.Empty -> 0
            is QmsChatLoadOutcome.Failure -> null
        }
        if (outcome !is QmsChatLoadOutcome.Failure) {
            QmsOpenTiming.logNetworkDone(traceId, themeId, userId, requestId, count)
        }
    }

    private suspend fun applyLoadedChat(
            chat: QmsChatModel,
            requestId: Int,
            traceId: String,
            reason: String
    ) {
        updateCurrentData(chat)
        val nextState = if (chat.messages.isEmpty()) {
            QmsThreadUiState.Empty(requestId, reason)
        } else {
            QmsThreadUiState.Content(requestId, chat)
        }
        transitionThreadState(nextState, reason, traceId, requestId)
        logChat(
                "load_success",
                mapOf(
                        "messageCount" to chat.messages.size,
                        "visibleCount" to expectedVisibleMessageCount(),
                        "requestId" to requestId
                )
        )
        _uiEvents.emit(QmsChatUiEvent.ShowChat(chat))
        emitInitialMessages(chat)
        tryShowAvatar()
    }

    private suspend fun applyLoadFailure(
            failure: QmsChatLoadOutcome.Failure,
            requestId: Int,
            traceId: String
    ) {
        val cached = QmsChatMemoryCache.get(userId, themeId)
        if (cached != null && cached.messageCount > 0) {
            updateCurrentData(cached.chat)
            transitionThreadState(
                    QmsThreadUiState.Content(requestId, cached.chat),
                    "cache_fallback_after_error",
                    traceId,
                    requestId
            )
            _uiEvents.emit(QmsChatUiEvent.ShowChat(cached.chat))
            emitInitialMessages(cached.chat)
            _uiEvents.emit(
                    QmsChatUiEvent.LoadWarning(
                            kind = failure.kind,
                            cacheAgeMinutes = QmsChatMemoryCache.cacheAgeMinutes(userId, themeId)
                    )
            )
            return
        }
        transitionThreadState(
                QmsThreadUiState.Error(requestId, failure.kind, failure.message, failure.canRetry),
                "load_failure",
                traceId,
                requestId
        )
        logChat("load_failure", mapOf("kind" to failure.kind, "message" to failure.message))
        _uiEvents.emit(QmsChatUiEvent.LoadFailed(failure.kind, failure.message, failure.canRetry))
    }

    private fun transitionThreadState(
            next: QmsThreadUiState,
            reason: String,
            traceId: String = openTraceId,
            requestId: Int = loadRequestId
    ) {
        val previous = _threadState.value
        _threadState.value = next
        QmsOpenTrace.logStateTransition(
                traceId = traceId,
                dialogId = themeId,
                userId = userId,
                requestId = requestId,
                previousState = previous::class.java.simpleName,
                nextState = next::class.java.simpleName,
                reason = reason
        )
    }

    fun sendNewTheme(nick: String, title: String, message: String, files: List<AttachmentItem>) {
        scope.launch {
            try {
                _refreshing.value = true
                val chat = qmsInteractor.sendNewTheme(nick, title, message, files)
                updateCurrentData(chat)
                _uiEvents.emit(QmsChatUiEvent.ShowChat(chat))
                _uiEvents.emit(QmsChatUiEvent.OnNewThemeCreate(chat))
                emitInitialMessages(chat)
                tryShowAvatar()
            } catch (e: Throwable) {
                errorHandler.handle(e)
            } finally {
                _refreshing.value = false
            }
        }
    }

    fun sendMessage(message: String, files: List<AttachmentItem>) {
        if (userId == QmsChatModel.NOT_CREATED || themeId == QmsChatModel.NOT_CREATED) {
            return
        }
        scope.launch {
            try {
                _messageRefreshing.value = true
                val messages = qmsInteractor.sendMessage(userId, themeId, message, files)
                // Сервер не всегда присылает WS-событие для собственного сообщения сразу,
                // из-за этого в чате оно не появляется до пересоздания/обновления.
                // Добавляем пришедшие сообщения в текущие данные и показываем их сразу.
                onNewMessages(messages)
                _uiEvents.emit(QmsChatUiEvent.OnSentMessage(messages))
            } catch (e: Throwable) {
                errorHandler.handle(e)
            } finally {
                _messageRefreshing.value = false
            }
        }
    }

    fun blockUser() {
        currentData?.nick?.let { n ->
            scope.launch {
                runCatching { qmsInteractor.blockUser(n) }
                        .onSuccess { list ->
                            scope.launch { _uiEvents.emit(QmsChatUiEvent.OnBlockUser(list.firstOrNull { it.nick == n } != null)) }
                        }
                        .onFailure { errorHandler.handle(it) }
            }
        }
    }

    private fun tryShowAvatar() {
        val result = avatarUrl?.let { it } ?: currentData?.avatarUrl?.let { it }
        if (result != null) {
            scope.launch { _uiEvents.emit(QmsChatUiEvent.ShowAvatar(result)) }
        } else {
            currentData?.let {
                scope.launch {
                    runCatching { avatarRepository.getAvatar(it.nick.orEmpty()) }
                            .onSuccess { url -> scope.launch { _uiEvents.emit(QmsChatUiEvent.ShowAvatar(url)) } }
                            .onFailure { e -> errorHandler.handle(e) }
                }
            }
        }
    }

    fun uploadFiles(files: List<RequestFile>, pending: List<AttachmentItem>) {
        scope.launch {
            runCatching { qmsInteractor.uploadFiles(files, pending) }
                    .onSuccess { scope.launch { _uiEvents.emit(QmsChatUiEvent.OnUploadFiles(it)) } }
                    .onFailure { errorHandler.handle(it) }
        }
    }

    fun shouldSkipAutoRefreshPoll(): Boolean =
            eventsRepository.isWebSocketConnected() ||
                    System.currentTimeMillis() - lastRealtimeMessageAtMs < WS_POLL_SUPPRESS_MS

    private fun markRealtimeMessageActivity() {
        lastRealtimeMessageAtMs = System.currentTimeMillis()
    }

    fun handleEvent(event: TabNotification) {
        val tid = event.event.sourceId
        currentData?.let {
            if (tid == it.themeId) {
                when (event.type) {
                    NotificationEvent.Type.NEW -> {
                        markRealtimeMessageActivity()
                        onNewWsMessage(tid)
                    }
                    NotificationEvent.Type.READ -> {
                        scope.launch { _uiEvents.emit(QmsChatUiEvent.MakeAllRead) }
                    }
                    NotificationEvent.Type.MENTION -> {
                    }
                    NotificationEvent.Type.HAT_EDITED -> {
                    }
                    null -> {
                    }
                }
            }
        }
    }

    private fun onNewWsMessage(themeId: Int) {
        currentData?.let {
            val lastMessId = it.messages.lastOrNull()?.id ?: 0
            scope.launch {
                runCatching { qmsInteractor.getMessagesAfter(userId, themeId, lastMessId) }
                        .onSuccess { onNewMessages(it) }
                        .onFailure { errorHandler.handle(it) }
            }
        }
    }

    fun checkNewMessages() {
        checkNewMessages(silent = false)
    }

    fun checkNewMessagesSilently() {
        checkNewMessages(silent = true)
    }

    private fun checkNewMessages(silent: Boolean) {
        if (_refreshing.value || _messageRefreshing.value || _newMessagesRefreshing.value) return
        currentData?.let {
            val lastMessId = it.messages.lastOrNull()?.id ?: 0
            scope.launch {
                try {
                    _newMessagesRefreshing.value = true
                    val messages = qmsInteractor.getMessagesAfter(userId, themeId, lastMessId)
                    onNewMessages(messages, forceScroll = !silent)
                } catch (e: Throwable) {
                    if (silent) {
                        Timber.d(e, "QMS auto-refresh failed")
                    } else {
                        errorHandler.handle(e)
                    }
                } finally {
                    _newMessagesRefreshing.value = false
                }
            }
        }
    }

    private suspend fun emitInitialMessages(data: QmsChatModel) {
        val end = data.messages.size
        val start = maxOf(end - 30, 0)
        data.showedMessIndex = start
        val newMessages = data.messages.subList(start, end).toList()
        if (newMessages.isNotEmpty()) {
            logChat("emit_initial_messages", mapOf("count" to newMessages.size))
            _uiEvents.emit(QmsChatUiEvent.ShowInitialMessages(newMessages))
        }
    }

    private fun qmsVisibleMessagesChanged(previous: QmsChatModel?, next: QmsChatModel): Boolean {
        if (previous == null) return true
        val prevEnd = previous.messages.size
        val prevStart = maxOf(prevEnd - 30, 0)
        val nextEnd = next.messages.size
        val nextStart = maxOf(nextEnd - 30, 0)
        if (prevStart != nextStart || prevEnd != nextEnd) return true
        val prevVisible = previous.messages.subList(prevStart, prevEnd)
        val nextVisible = next.messages.subList(nextStart, nextEnd)
        if (prevVisible.size != nextVisible.size) return true
        return prevVisible.asSequence()
                .zip(nextVisible.asSequence())
                .any { (left, right) -> left.id != right.id || left.content != right.content }
    }

    fun hasLoadedMessages(): Boolean = !currentData?.messages.isNullOrEmpty()

    fun expectedVisibleMessageCount(): Int {
        val data = currentData ?: return 0
        val end = data.messages.size
        val start = maxOf(end - 30, 0)
        return end - start
    }

    /** Non-date rows in the visible window; DOM only has `.mess_container` for these. */
    fun expectedVisibleMessContainerCount(): Int {
        val data = currentData ?: return 0
        val end = data.messages.size
        val start = maxOf(end - 30, 0)
        return data.messages.subList(start, end).count { !it.isDate }
    }

    /** Re-injects the latest message batch when WebView rendered blank but data is already loaded. */
    fun resendVisibleMessagesToWeb(clearExisting: Boolean = false) {
        val data = currentData ?: return
        val end = data.messages.size
        val start = maxOf(end - 30, 0)
        data.showedMessIndex = start
        val visibleMessages = data.messages.subList(start, end).toList()
        scope.launch {
            _uiEvents.emit(QmsChatUiEvent.ResetAndShowMessages(visibleMessages, clearExisting))
        }
    }

    private fun onNewMessages(items: List<QmsMessage>, forceScroll: Boolean = true) {
        currentData?.let { data ->
            val result = items.filter { new ->
                data.messages.none { it.id == new.id }
            }
            data.messages.addAll(result)
            scope.launch { _uiEvents.emit(QmsChatUiEvent.OnNewMessages(result, forceScroll)) }
        }
    }

    fun createThemeNote() {
        currentData?.let {
            val url = "https://4pda.to/forum/index.php?act=qms&mid=${it.userId}&t=${it.themeId}"
            scope.launch { _uiEvents.emit(QmsChatUiEvent.ShowCreateNote(it.title.orEmpty(), it.nick.orEmpty(), url)) }
        }
    }

    fun openProfile() {
        currentData?.let {
            linkHandler.handle("https://4pda.to/forum/index.php?showuser=${it.userId}", router)
        }
    }

    fun openDialogs() {
        currentData?.let {
            router.navigateTo(Screen.QmsThemes().apply {
                screenTitle = it.nick
                userId = it.userId
                avatarUrl = it.avatarUrl
            })
        }
    }

    fun onSendClick() {
        if (themeId == QmsChatModel.NOT_CREATED) {
            scope.launch { _uiEvents.emit(QmsChatUiEvent.TempSendNewTheme) }
        } else {
            scope.launch { _uiEvents.emit(QmsChatUiEvent.TempSendMessage) }
        }
    }

    override fun loadMoreMessages() {
        currentData?.let {
            val endIndex = it.showedMessIndex
            val startIndex = Math.max(endIndex - 30, 0)
            it.showedMessIndex = startIndex
            scope.launch { _uiEvents.emit(QmsChatUiEvent.ShowMoreMessages(it.messages, startIndex, endIndex)) }
        }
    }

    override fun openLink(url: String) {
        val resolved = QmsChatLinkNavigation.resolveInAppUrl(url) ?: url
        linkHandler.handle(resolved, router)
    }
}

sealed class QmsChatUiEvent {
    data class SetFontSize(val fontSize: Int) : QmsChatUiEvent()
    data class SetAppFontMode(val mode: forpdateam.ru.forpda.ui.AppFontMode) : QmsChatUiEvent()
    data class SetStyleType(val styleType: String) : QmsChatUiEvent()
    data class SetTitles(val title: String, val nick: String) : QmsChatUiEvent()
    data class SetChatMode(val mode: String) : QmsChatUiEvent()
    data class OnShowSearchRes(val result: List<forpdateam.ru.forpda.entity.remote.others.user.ForumUser>) : QmsChatUiEvent()
    data class ShowChat(val chat: QmsChatModel) : QmsChatUiEvent()
    data class OnNewThemeCreate(val chat: QmsChatModel) : QmsChatUiEvent()
    data class OnSentMessage(val messages: List<QmsMessage>) : QmsChatUiEvent()
    data class OnBlockUser(val isBlocked: Boolean) : QmsChatUiEvent()
    data class ShowAvatar(val url: String) : QmsChatUiEvent()
    data class OnUploadFiles(val files: List<AttachmentItem>) : QmsChatUiEvent()
    object MakeAllRead : QmsChatUiEvent()
    data class OnNewMessages(val messages: List<QmsMessage>, val forceScroll: Boolean) : QmsChatUiEvent()
    data class ShowInitialMessages(val messages: List<QmsMessage>) : QmsChatUiEvent()
    data class ResetAndShowMessages(val messages: List<QmsMessage>, val clearExisting: Boolean) : QmsChatUiEvent()
    data class ShowCreateNote(val title: String, val nick: String, val url: String) : QmsChatUiEvent()
    object TempSendNewTheme : QmsChatUiEvent()
    object TempSendMessage : QmsChatUiEvent()
    data class ShowMoreMessages(val messages: List<QmsMessage>, val startIndex: Int, val endIndex: Int) : QmsChatUiEvent()
    data class LoadFailed(
            val kind: QmsLoadErrorKind,
            val message: String,
            val canRetry: Boolean
    ) : QmsChatUiEvent()
    data class LoadWarning(
            val kind: QmsLoadErrorKind,
            val cacheAgeMinutes: Int? = null
    ) : QmsChatUiEvent()
}
