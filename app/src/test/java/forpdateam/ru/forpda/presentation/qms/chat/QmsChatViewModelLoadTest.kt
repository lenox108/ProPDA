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
        val templateManager = mockk<TemplateManager>(relaxed = true)
        every { templateManager.observeThemeTypeFlow() } returns flowOf("light")
        val prefs = mockk<MainPreferencesHolder>(relaxed = true)
        every { prefs.observeWebViewFontSizeFlow() } returns flowOf(100)
        every { prefs.observeAppFontModeFlow() } returns flowOf(forpdateam.ru.forpda.ui.AppFontMode.SYSTEM)
        return QmsChatViewModel(
                interactor,
                mockk(relaxed = true),
                mockk(relaxed = true),
                events,
                prefs,
                templateManager,
                mockk(relaxed = true),
                mockk(relaxed = true),
                mockk<IErrorHandler>(relaxed = true)
        ).apply {
            userId = 1
            themeId = 2
        }
    }

    private fun mockEventsRepository(webSocketConnected: Boolean = false): EventsRepository {
        val events = mockk<EventsRepository>(relaxed = true)
        every { events.observeEventsTab() } returns flowOf()
        every { events.isWebSocketConnected() } returns webSocketConnected
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

    @Test
    fun `shouldSkipAutoRefreshPoll when websocket connected`() = runTest {
        val interactor = mockk<QmsInteractor>()
        val initial = chatWithMessages()
        coEvery {
            interactor.loadChatThread(any(), any(), any(), any(), any(), any())
        } returns QmsChatLoadOutcome.Content(initial, fromCache = false, pageKind = mockk(relaxed = true))
        val events = mockEventsRepository(webSocketConnected = true)
        val vm = viewModel(interactor, events)
        vm.start()
        advanceUntilIdle()
        assertTrue(vm.shouldSkipAutoRefreshPoll())
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
}
