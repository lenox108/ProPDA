package forpdateam.ru.forpda.presentation.qms.chat

import forpdateam.ru.forpda.entity.app.TabNotification
import forpdateam.ru.forpda.entity.remote.events.NotificationEvent
import forpdateam.ru.forpda.entity.remote.qms.QmsChatModel
import forpdateam.ru.forpda.entity.remote.qms.QmsMessage
import forpdateam.ru.forpda.model.data.remote.api.qms.QmsHtmlValidator
import forpdateam.ru.forpda.model.interactors.qms.QmsChatLoadOutcome
import forpdateam.ru.forpda.model.interactors.qms.QmsChatMemoryCache
import forpdateam.ru.forpda.model.interactors.qms.QmsInteractor
import forpdateam.ru.forpda.model.preferences.MainPreferencesHolder
import forpdateam.ru.forpda.model.repository.avatar.AvatarRepository
import forpdateam.ru.forpda.model.repository.events.EventsRepository
import forpdateam.ru.forpda.presentation.IErrorHandler
import forpdateam.ru.forpda.presentation.ILinkHandler
import forpdateam.ru.forpda.presentation.TabRouter
import forpdateam.ru.forpda.ui.TemplateManager
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import io.mockk.coVerify

@OptIn(ExperimentalCoroutinesApi::class)
class QmsChatViewModelLoadTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        QmsChatMemoryCache.invalidateAll()
        Dispatchers.resetMain()
    }

    private fun chatWithMessages(): QmsChatModel = QmsChatModel().apply {
        userId = 1
        themeId = 2
        nick = "nick"
        title = "title"
        messages.add(QmsMessage().apply {
            id = 1
            content = "hello"
            isMyMessage = false
        })
    }

    private fun viewModel(
            interactor: QmsInteractor,
            events: EventsRepository = mockEventsRepository(),
    ): QmsChatViewModel {
        val prefs = mockk<MainPreferencesHolder>(relaxed = true)
        every { prefs.observeWebViewFontSizeFlow() } returns flowOf(100)
        return QmsChatViewModel(
                interactor,
                mockk(relaxed = true),
                events,
                prefs,
                mockk(relaxed = true),
                mockk(relaxed = true),
                mockk(relaxed = true),
                mockk<IErrorHandler>(relaxed = true)
        ).apply {
            userId = 1
            themeId = 2
        }
    }

    private fun mockEventsRepository(
            webSocketConnected: Boolean = false,
            threadActivity: Flow<Int> = flowOf(),
            msSinceUserInteraction: Long = 0L,
    ): EventsRepository {
        val events = mockk<EventsRepository>(relaxed = true)
        every { events.observeEventsTab() } returns flowOf()
        every { events.observeQmsThreadActivity() } returns threadActivity
        every { events.isWebSocketConnected() } returns webSocketConnected
        every { events.msSinceUserInteraction() } returns msSinceUserInteraction
        return events
    }

    @Test
    fun `start does not restart load already started by navigator`() = runTest {
        val interactor = mockk<QmsInteractor>()
        val success = chatWithMessages()
        coEvery {
            interactor.loadChatThread(any(), any(), any(), any(), any(), any())
        } coAnswers {
            delay(500)
            QmsChatLoadOutcome.Content(success, fromCache = false, pageKind = mockk(relaxed = true))
        }
        val vm = viewModel(interactor)
        vm.onChatIdentityChanged()
        vm.start()
        advanceUntilIdle()
        coVerify(exactly = 1) {
            interactor.loadChatThread(1, 2, any(), any(), any(), any())
        }
        assertTrue(vm.threadState.value is QmsThreadUiState.Content)
    }

    @Test
    fun `retry creates new requestId and ignores stale failure`() = runTest {
        val interactor = mockk<QmsInteractor>()
        val success = chatWithMessages()
        coEvery {
            interactor.loadChatThread(any(), any(), any(), any(), any(), any())
        } coAnswers {
            val requestId = args[3] as Int
            if (requestId == 1) {
                delay(200)
                QmsChatLoadOutcome.Failure(QmsLoadErrorKind.NETWORK, "slow", canRetry = true)
            } else {
                QmsChatLoadOutcome.Content(success, fromCache = false, pageKind = mockk(relaxed = true))
            }
        }
        val vm = viewModel(interactor)
        vm.retryLoadChat()
        val firstRequest = 1
        vm.retryLoadChat()
        advanceUntilIdle()
        val state = vm.threadState.value
        assertTrue(state is QmsThreadUiState.Content)
        assertTrue((state as QmsThreadUiState.Content).requestId > firstRequest)
    }

    @Test
    fun `old failed request cannot overwrite new success`() = runTest {
        val interactor = mockk<QmsInteractor>()
        val success = chatWithMessages()
        coEvery {
            interactor.loadChatThread(any(), any(), any(), any(), any(), any())
        } coAnswers {
            val requestId = args[3] as Int
            when (requestId) {
                1 -> {
                    delay(300)
                    QmsChatLoadOutcome.Failure(QmsLoadErrorKind.NETWORK, "late", canRetry = true)
                }
                else -> QmsChatLoadOutcome.Content(success, fromCache = false, pageKind = mockk(relaxed = true))
            }
        }
        val vm = viewModel(interactor)
        val first = async { vm.retryLoadChat() }
        delay(50)
        vm.retryLoadChat()
        advanceUntilIdle()
        first.await()
        assertTrue(vm.threadState.value is QmsThreadUiState.Content)
    }

    @Test
    fun `invalid themeId shows empty state without network`() = runTest {
        val interactor = mockk<QmsInteractor>(relaxed = true)
        val vm = viewModel(interactor).apply {
            userId = 0
            themeId = 0
        }
        vm.start()
        advanceUntilIdle()
        val state = vm.threadState.value
        assertTrue(state is QmsThreadUiState.Empty)
        assertEquals("invalid_theme_id", (state as QmsThreadUiState.Empty).reason)
        coVerify(exactly = 0) {
            interactor.loadChatThread(any(), any(), any(), any(), any(), any())
        }
    }

    @Test
    fun `session failure surfaces explicit error kind not parser`() = runTest {
        val interactor = mockk<QmsInteractor>()
        coEvery {
            interactor.loadChatThread(any(), any(), any(), any(), any(), any())
        } returns QmsChatLoadOutcome.Failure(
                QmsLoadErrorKind.SESSION,
                "session_expired",
                canRetry = false
        )
        val vm = viewModel(interactor)
        vm.retryLoadChat()
        advanceUntilIdle()
        val state = vm.threadState.value
        assertTrue(state is QmsThreadUiState.Error)
        assertEquals(QmsLoadErrorKind.SESSION, (state as QmsThreadUiState.Error).kind)
        assertEquals(false, state.canRetry)
    }

    @Test
    fun `stale ignored load retries when no cached data`() = runTest {
        val interactor = mockk<QmsInteractor>()
        val success = chatWithMessages()
        coEvery {
            interactor.loadChatThread(any(), any(), any(), any(), any(), any())
        } coAnswers {
            val requestId = args[3] as Int
            when (requestId) {
                1 -> {
                    delay(300)
                    QmsChatLoadOutcome.Content(success, fromCache = false, pageKind = mockk(relaxed = true))
                }
                else -> QmsChatLoadOutcome.Content(success, fromCache = false, pageKind = mockk(relaxed = true))
            }
        }
        val vm = viewModel(interactor)
        vm.retryLoadChat()
        delay(50)
        vm.retryLoadChat()
        advanceUntilIdle()
        assertTrue(vm.threadState.value is QmsThreadUiState.Content)
        coVerify(atLeast = 2) {
            interactor.loadChatThread(1, 2, any(), any(), any(), any())
        }
    }

    /**
     * A connected-but-silent socket must NOT suppress the safety-net poll: `isConnected()` is true
     * right after onOpen and says nothing about delivery. Suppression is earned by an actual WS event
     * (see the freshness tests below) — that is the whole point of the P2 gate.
     */
    @Test
    fun `connected but silent websocket does not suppress the poll`() = runTest {
        val interactor = mockk<QmsInteractor>()
        val initial = chatWithMessages()
        coEvery {
            interactor.loadChatThread(any(), any(), any(), any(), any(), any())
        } returns QmsChatLoadOutcome.Content(initial, fromCache = false, pageKind = mockk(relaxed = true))
        val events = mockEventsRepository(webSocketConnected = true)
        val vm = viewModel(interactor, events)
        vm.start()
        advanceUntilIdle()
        assertFalse(vm.shouldSkipAutoRefreshPoll())
    }

    /**
     * Cadence follows PROVEN delivery, not `isConnected()`: a connected-but-silent socket (measured
     * live — onConnected, subscription acked by the server, zero events for arriving messages) must
     * get the fast tick while the user is in the dialog, because then the poll is the only delivery
     * path left. As soon as the socket proves it delivers, the cadence relaxes.
     */
    @Test
    fun `poll cadence follows proven delivery not mere connectivity`() = runTest {
        val interactor = mockk<QmsInteractor>()
        val initial = chatWithMessages()
        coEvery {
            interactor.loadChatThread(any(), any(), any(), any(), any(), any())
        } returns QmsChatLoadOutcome.Content(initial, fromCache = false, pageKind = mockk(relaxed = true))
        coEvery { interactor.getMessagesAfter(1, 2, 1) } returns emptyList()
        val vm = viewModel(interactor, mockEventsRepository(webSocketConnected = true))
        vm.start()
        advanceUntilIdle()

        assertEquals(
                "подключён, но молчит — опрос единственный путь",
                QmsChatViewModel.AUTO_REFRESH_ACTIVE_MS,
                vm.autoRefreshDelayMs()
        )

        vm.handleEvent(TabNotification(
                source = NotificationEvent.Source.QMS,
                type = NotificationEvent.Type.NEW,
                event = NotificationEvent(
                        type = NotificationEvent.Type.NEW,
                        source = NotificationEvent.Source.QMS,
                        messageId = 2,
                        sourceId = 2,
                        userId = 999
                ),
                isWebSocket = true
        ))
        advanceUntilIdle()

        assertEquals(
                "сокет доказал доставку — можно реже",
                QmsChatViewModel.AUTO_REFRESH_IDLE_MS,
                vm.autoRefreshDelayMs()
        )
        assertTrue(QmsChatViewModel.AUTO_REFRESH_ACTIVE_MS < QmsChatViewModel.AUTO_REFRESH_IDLE_MS)
    }

    /**
     * Диалог, просто оставленный открытым (никто не касается экрана, сообщений нет), не должен
     * опрашиваться в частом темпе: живой замер дал 679 запросов за час на негаснущем экране.
     */
    @Test
    fun `abandoned open dialog falls back to the idle cadence`() = runTest {
        val interactor = mockk<QmsInteractor>()
        val initial = chatWithMessages()
        coEvery {
            interactor.loadChatThread(any(), any(), any(), any(), any(), any())
        } returns QmsChatLoadOutcome.Content(initial, fromCache = false, pageKind = mockk(relaxed = true))
        val idle = mockEventsRepository(msSinceUserInteraction = 10 * 60_000L)
        val vm = viewModel(interactor, idle)
        vm.start()
        advanceUntilIdle()

        assertEquals(QmsChatViewModel.AUTO_REFRESH_IDLE_MS, vm.autoRefreshDelayMs())
    }

    /** Пришло сообщение — переписка живая, следующее ждём в частом темпе даже без касаний. */
    @Test
    fun `incoming message re-arms the fast cadence without touches`() = runTest {
        val interactor = mockk<QmsInteractor>()
        val initial = chatWithMessages()
        val appended = QmsMessage().apply {
            id = 2
            content = "ответ собеседника"
            isMyMessage = false
        }
        coEvery {
            interactor.loadChatThread(any(), any(), any(), any(), any(), any())
        } returns QmsChatLoadOutcome.Content(initial, fromCache = false, pageKind = mockk(relaxed = true))
        coEvery { interactor.getMessagesAfter(1, 2, 1) } returns listOf(appended)
        val activity = MutableSharedFlow<Int>(extraBufferCapacity = 8)
        val vm = viewModel(
                interactor,
                mockEventsRepository(threadActivity = activity, msSinceUserInteraction = 10 * 60_000L)
        )
        vm.start()
        advanceUntilIdle()
        assertEquals(QmsChatViewModel.AUTO_REFRESH_IDLE_MS, vm.autoRefreshDelayMs())

        activity.emit(2)
        advanceUntilIdle()

        assertEquals(QmsChatViewModel.AUTO_REFRESH_ACTIVE_MS, vm.autoRefreshDelayMs())
    }

    /**
     * The gap behind the field report «уведомление в шторке есть, а в диалоге сообщения ещё нет»:
     * the inspector poll and the background worker (the push wake-up path) learn about the message
     * without any WebSocket event. They now signal the open dialog, which must fetch immediately
     * instead of waiting for its next poll tick.
     */
    @Test
    fun `thread activity signal fetches new messages without a websocket event`() = runTest {
        val interactor = mockk<QmsInteractor>()
        val initial = chatWithMessages()
        val appended = QmsMessage().apply {
            id = 2
            content = "доставлено воркером"
            isMyMessage = false
        }
        coEvery {
            interactor.loadChatThread(any(), any(), any(), any(), any(), any())
        } returns QmsChatLoadOutcome.Content(initial, fromCache = false, pageKind = mockk(relaxed = true))
        coEvery { interactor.getMessagesAfter(1, 2, 1) } returns listOf(appended)
        val activity = MutableSharedFlow<Int>(extraBufferCapacity = 8)
        val vm = viewModel(interactor, mockEventsRepository(threadActivity = activity))
        vm.start()
        advanceUntilIdle()

        activity.emit(2)
        advanceUntilIdle()

        coVerify(exactly = 1) { interactor.getMessagesAfter(1, 2, 1) }
        assertEquals(2, vm.visibleMessages.value.messages.size)
    }

    @Test
    fun `thread activity for another dialog is ignored`() = runTest {
        val interactor = mockk<QmsInteractor>()
        val initial = chatWithMessages()
        coEvery {
            interactor.loadChatThread(any(), any(), any(), any(), any(), any())
        } returns QmsChatLoadOutcome.Content(initial, fromCache = false, pageKind = mockk(relaxed = true))
        val activity = MutableSharedFlow<Int>(extraBufferCapacity = 8)
        val vm = viewModel(interactor, mockEventsRepository(threadActivity = activity))
        vm.start()
        advanceUntilIdle()

        activity.emit(4242)
        advanceUntilIdle()

        coVerify(exactly = 0) { interactor.getMessagesAfter(any(), any(), any()) }
    }

    /** Событие темы форума с номером, совпавшим с id диалога, не должно дёргать чат. */
    @Test
    fun `forum theme event with the same source id does not touch the chat`() = runTest {
        val interactor = mockk<QmsInteractor>()
        val initial = chatWithMessages()
        coEvery {
            interactor.loadChatThread(any(), any(), any(), any(), any(), any())
        } returns QmsChatLoadOutcome.Content(initial, fromCache = false, pageKind = mockk(relaxed = true))
        val vm = viewModel(interactor)
        vm.start()
        advanceUntilIdle()

        vm.handleEvent(TabNotification(
                source = NotificationEvent.Source.THEME,
                type = NotificationEvent.Type.NEW,
                event = NotificationEvent(
                        type = NotificationEvent.Type.NEW,
                        source = NotificationEvent.Source.THEME,
                        messageId = 2,
                        sourceId = 2,
                        userId = 999
                ),
                isWebSocket = true
        ))
        advanceUntilIdle()

        coVerify(exactly = 0) { interactor.getMessagesAfter(any(), any(), any()) }
        assertFalse(vm.shouldSkipAutoRefreshPoll())
    }

    /** Пока диалог на экране, уведомление о сообщении именно в нём не публикуется. */
    @Test
    fun `open dialog registers itself as viewed and releases it when hidden`() = runTest {
        val interactor = mockk<QmsInteractor>(relaxed = true)
        val events = mockEventsRepository()
        val vm = viewModel(interactor, events)

        vm.onScreenVisible()
        advanceUntilIdle()
        verify(exactly = 1) { events.setViewedQmsThread(2) }

        vm.onScreenHidden()
        advanceUntilIdle()
        verify(exactly = 1) { events.clearViewedQmsThread(2) }
    }

    @Test
    fun `shouldSkipAutoRefreshPoll after websocket activity`() = runTest {
        val interactor = mockk<QmsInteractor>()
        val initial = chatWithMessages()
        coEvery {
            interactor.loadChatThread(any(), any(), any(), any(), any(), any())
        } returns QmsChatLoadOutcome.Content(initial, fromCache = false, pageKind = mockk(relaxed = true))
        val vm = viewModel(interactor)
        vm.start()
        advanceUntilIdle()
        assertTrue(!vm.shouldSkipAutoRefreshPoll())
        vm.handleEvent(TabNotification(
                source = NotificationEvent.Source.QMS,
                type = NotificationEvent.Type.NEW,
                event = NotificationEvent(
                        type = NotificationEvent.Type.NEW,
                        source = NotificationEvent.Source.QMS,
                        messageId = 2,
                        sourceId = 2,
                        userId = 1
                ),
                isWebSocket = true
        ))
        advanceUntilIdle()
        assertTrue(vm.shouldSkipAutoRefreshPoll())
    }

    @Test
    fun `websocket qms event loads new messages with active dialog user id`() = runTest {
        val interactor = mockk<QmsInteractor>()
        val initial = chatWithMessages()
        val appended = QmsMessage().apply {
            id = 2
            content = "new incoming"
            isMyMessage = false
        }
        coEvery {
            interactor.loadChatThread(any(), any(), any(), any(), any(), any())
        } returns QmsChatLoadOutcome.Content(initial, fromCache = false, pageKind = mockk(relaxed = true))
        coEvery { interactor.getMessagesAfter(1, 2, 1) } returns listOf(appended)
        val vm = viewModel(interactor)
        vm.start()
        advanceUntilIdle()

        vm.handleEvent(TabNotification(
                source = NotificationEvent.Source.QMS,
                type = NotificationEvent.Type.NEW,
                event = NotificationEvent(
                        type = NotificationEvent.Type.NEW,
                        source = NotificationEvent.Source.QMS,
                        messageId = 2,
                        sourceId = 2,
                        userId = 999
                ),
                isWebSocket = true
        ))
        advanceUntilIdle()

        coVerify(exactly = 1) { interactor.getMessagesAfter(1, 2, 1) }
        coVerify(exactly = 0) { interactor.getMessagesFromWs(any(), any(), any()) }
        assertEquals(2, (vm.threadState.value as QmsThreadUiState.Content).chat.messages.size)
    }

    @Test
    fun `shouldSkipAutoRefreshPoll is true shortly after websocket new message`() = runTest {
        val interactor = mockk<QmsInteractor>()
        val initial = chatWithMessages()
        coEvery {
            interactor.loadChatThread(any(), any(), any(), any(), any(), any())
        } returns QmsChatLoadOutcome.Content(initial, fromCache = false, pageKind = mockk(relaxed = true))
        coEvery { interactor.getMessagesAfter(1, 2, 1) } returns emptyList()
        val vm = viewModel(interactor)
        vm.start()
        advanceUntilIdle()
        assertFalse(vm.shouldSkipAutoRefreshPoll())

        vm.handleEvent(TabNotification(
                source = NotificationEvent.Source.QMS,
                type = NotificationEvent.Type.NEW,
                event = NotificationEvent(
                        type = NotificationEvent.Type.NEW,
                        source = NotificationEvent.Source.QMS,
                        messageId = 2,
                        sourceId = 2,
                        userId = 999
                ),
                isWebSocket = true
        ))
        advanceUntilIdle()

        assertTrue(vm.shouldSkipAutoRefreshPoll())
    }

    @Test
    fun `bg refresh failure with cache emits LoadWarning with cache age`() = runTest {
        val interactor = mockk<QmsInteractor>()
        val cached = chatWithMessages()
        QmsChatMemoryCache.put(1, 2, cached, QmsHtmlValidator.PageKind.QMS_THREAD)
        coEvery {
            interactor.loadChatThread(any(), any(), any(), any(), any(), any())
        } coAnswers {
            val bypassCache = args[4] as Boolean
            if (bypassCache) {
                QmsChatLoadOutcome.Failure(QmsLoadErrorKind.NETWORK, "offline", canRetry = true)
            } else {
                QmsChatLoadOutcome.Content(cached, fromCache = true, pageKind = QmsHtmlValidator.PageKind.QMS_THREAD)
            }
        }
        val vm = viewModel(interactor)
        val collected = mutableListOf<QmsChatUiEvent>()
        val collector = backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) {
            vm.uiEvents.collect { collected += it }
        }
        vm.retryLoadChat()
        advanceUntilIdle()

        val warning = collected.filterIsInstance<QmsChatUiEvent.LoadWarning>().single()
        assertEquals(QmsLoadErrorKind.NETWORK, warning.kind)
        assertNotNull(warning.cacheAgeMinutes)
        assertTrue(warning.cacheAgeMinutes!! >= 0)
    }

    private fun chatWith(messageCount: Int): QmsChatModel = QmsChatModel().apply {
        userId = 1
        themeId = 2
        nick = "nick"
        title = "title"
        repeat(messageCount) { index ->
            messages.add(QmsMessage().apply {
                id = index + 1
                content = "message ${index + 1}"
            })
        }
    }

    private fun loadedViewModel(chat: QmsChatModel): QmsChatViewModel {
        val interactor = mockk<QmsInteractor>()
        coEvery {
            interactor.loadChatThread(any(), any(), any(), any(), any(), any())
        } returns QmsChatLoadOutcome.Content(chat, fromCache = false, pageKind = mockk(relaxed = true))
        return viewModel(interactor).also { it.start() }
    }

    @Test
    fun `initial window exposes the newest page and reports more above`() = runTest {
        val vm = loadedViewModel(chatWith(75))
        advanceUntilIdle()

        val window = vm.visibleMessages.value
        assertEquals(30, window.messages.size)
        assertEquals(46, window.messages.first().id)
        assertEquals(75, window.messages.last().id)
        assertTrue(window.hasMoreAbove)
    }

    @Test
    fun `loadMoreMessages grows the window upwards until history is exhausted`() = runTest {
        val vm = loadedViewModel(chatWith(40))
        advanceUntilIdle()

        vm.loadMoreMessages()
        advanceUntilIdle()

        val window = vm.visibleMessages.value
        assertEquals(40, window.messages.size)
        assertEquals(1, window.messages.first().id)
        assertFalse("nothing left above", window.hasMoreAbove)

        // History exhausted: another request must not shift or re-emit a different window.
        vm.loadMoreMessages()
        advanceUntilIdle()
        assertEquals(40, vm.visibleMessages.value.messages.size)
    }

    @Test
    fun `a short thread has no history above`() = runTest {
        val vm = loadedViewModel(chatWith(3))
        advanceUntilIdle()

        assertEquals(3, vm.visibleMessages.value.messages.size)
        assertFalse(vm.visibleMessages.value.hasMoreAbove)
    }

    @Test
    fun `websocket read event clears unread flags`() = runTest {
        val chat = chatWith(2).apply { messages.forEach { it.readStatus = false } }
        val vm = loadedViewModel(chat)
        advanceUntilIdle()
        assertTrue(vm.visibleMessages.value.messages.none { it.readStatus })

        vm.handleEvent(TabNotification(
                source = NotificationEvent.Source.QMS,
                type = NotificationEvent.Type.READ,
                event = NotificationEvent(
                        type = NotificationEvent.Type.READ,
                        source = NotificationEvent.Source.QMS,
                        messageId = 2,
                        sourceId = 2,
                        userId = 1
                ),
                isWebSocket = true
        ))
        advanceUntilIdle()

        assertTrue(vm.visibleMessages.value.messages.all { it.readStatus })
    }
}
