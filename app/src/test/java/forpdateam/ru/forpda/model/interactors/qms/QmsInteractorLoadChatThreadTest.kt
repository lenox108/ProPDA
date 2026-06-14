package forpdateam.ru.forpda.model.interactors.qms

import forpdateam.ru.forpda.model.data.remote.api.NetworkResponse
import forpdateam.ru.forpda.model.data.remote.api.qms.QmsApi
import forpdateam.ru.forpda.model.data.remote.api.qms.QmsParser
import forpdateam.ru.forpda.model.repository.events.EventsRepository
import forpdateam.ru.forpda.model.repository.qms.QmsRepository
import forpdateam.ru.forpda.presentation.qms.chat.QmsLoadErrorKind
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.regex.Pattern

@OptIn(ExperimentalCoroutinesApi::class)
class QmsInteractorLoadChatThreadTest {

    private val dispatcher = StandardTestDispatcher()
    private val webClient = mockk<forpdateam.ru.forpda.model.data.remote.IWebClient>()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        QmsChatMemoryCache.invalidateAll()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `loadChatThread runs fetch off main when called from main scope`() = runTest(dispatcher) {
        val callerThreadId = Thread.currentThread().id
        var fetchThreadId: Long? = null
        every { webClient.request(any()) } answers {
            fetchThreadId = Thread.currentThread().id
            NetworkResponse(
                    code = 200,
                    body = """
                        <html act=qms>
                        <div class="mess_list"></div>
                        <input name="mid" value="1"/><input name="t" value="2"/>
                        </html>
                    """.trimIndent()
            )
        }
        val api = QmsApi(webClient, QmsParser(QmsChatOpenPipelineTestPatterns.provider()))
        val interactor = QmsInteractor(
                mockk<QmsRepository>(relaxed = true),
                mockk<EventsRepository>(relaxed = true),
                api
        )

        withContext(Dispatchers.Main.immediate) {
            interactor.loadChatThread(
                    userId = 1,
                    themeId = 2,
                    traceId = "trace-io",
                    requestId = 1,
                    bypassCache = true
            )
        }
        advanceUntilIdle()

        assertNotEquals("fetch must not run on Main/test caller thread", callerThreadId, fetchThreadId)
    }
}

/** Minimal patterns for [QmsInteractorLoadChatThreadTest] (empty thread body). */
private object QmsChatOpenPipelineTestPatterns {
    fun provider(): forpdateam.ru.forpda.model.data.storage.IPatternProvider =
            object : forpdateam.ru.forpda.model.data.storage.IPatternProvider {
                override fun getCurrentVersion(): Int = -1
                override fun getPattern(scope: String, key: String): Pattern =
                        Pattern.compile(".*")
                override fun update(jsonString: String) = Unit
            }
}
