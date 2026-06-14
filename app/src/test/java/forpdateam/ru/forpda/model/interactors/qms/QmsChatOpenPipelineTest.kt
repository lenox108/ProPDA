package forpdateam.ru.forpda.model.interactors.qms

import forpdateam.ru.forpda.model.data.remote.api.NetworkResponse
import forpdateam.ru.forpda.model.data.remote.api.qms.QmsApi
import forpdateam.ru.forpda.model.data.remote.api.qms.QmsHtmlValidator
import forpdateam.ru.forpda.model.data.remote.api.qms.QmsParser
import forpdateam.ru.forpda.model.data.storage.IPatternProvider
import forpdateam.ru.forpda.presentation.qms.chat.QmsLoadErrorKind
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import forpdateam.ru.forpda.BuildConfig
import forpdateam.ru.forpda.client.OkHttpResponseException
import android.os.NetworkOnMainThreadException
import java.io.File
import java.util.concurrent.TimeoutException
import java.util.regex.Pattern
import io.mockk.slot

class QmsChatOpenPipelineTest {

    private val webClient = mockk<forpdateam.ru.forpda.model.data.remote.IWebClient>()
    private lateinit var api: QmsApi
    private lateinit var pipeline: QmsChatOpenPipeline

    @Before
    fun setUp() {
        QmsChatMemoryCache.invalidateAll()
        api = QmsApi(webClient, QmsParser(loadProductionPatterns()))
        pipeline = QmsChatOpenPipeline(api)
    }

    private fun loadQmsFixture(name: String): String {
        val file = listOf(
                File("src/test/resources/parser/qms/$name"),
                File("app/src/test/resources/parser/qms/$name")
        ).first { it.exists() }
        return file.readText()
    }

    private fun loadProductionPatterns(): IPatternProvider {
        val patternsFile = listOf(
                File("src/main/assets/patterns.json"),
                File("app/src/main/assets/patterns.json")
        ).first { it.exists() }
        val root = Json.parseToJsonElement(patternsFile.readText()).jsonObject
        val patternsByScope = mutableMapOf<String, MutableMap<String, Pattern>>()
        root.getValue("scopes").jsonArray.forEach { scopeElement ->
            val scope = scopeElement.jsonObject
            val name = scope.getValue("scope").jsonPrimitive.content
            val map = mutableMapOf<String, Pattern>()
            scope.getValue("patterns").jsonArray.forEach { patternElement ->
                val p = patternElement.jsonObject
                map[p.getValue("key").jsonPrimitive.content] =
                        Pattern.compile(p.getValue("value").jsonPrimitive.content)
            }
            patternsByScope[name] = map
        }
        return object : IPatternProvider {
            override fun getCurrentVersion(): Int = -1
            override fun getPattern(scope: String, key: String): Pattern =
                    patternsByScope[scope]?.get(key)
                            ?: error("No pattern $scope/$key")
            override fun update(jsonString: String) = Unit
        }
    }

    private fun qmsThreadBody(): String = """
        <html act=qms>
        <div class="mess_list"></div>
        <div class="list-group-item" data-message-id="10" data-unread-status="0">
        <b></b> 10:00 <img src="/a.png"/>
        <div class="msg-content">Hi</div></div>
        <input name="mid" value="1"/><input name="t" value="2"/>
        </html>
    """.trimIndent()

    @Test
    fun `login page detected and rejected`() = runTest {
        every { webClient.request(any()) } returns NetworkResponse(
                code = 200,
                body = """<form act="auth" class="loginform"><input name="password"/></form>"""
        )
        val outcome = pipeline.loadChat(1, 2, "trace1", 1, bypassCache = true)
        assertTrue(outcome is QmsChatLoadOutcome.Failure)
        outcome as QmsChatLoadOutcome.Failure
        assertEquals(QmsLoadErrorKind.SESSION, outcome.kind)
        assertEquals(false, outcome.canRetry)
    }

    @Test
    fun `captcha page maps to captcha error not parser`() = runTest {
        every { webClient.request(any()) } returns NetworkResponse(
                code = 200,
                body = """
                    <html><form class="captcha-form">
                    <div class="captcha-image"></div>
                    <input name="captcha-time" value="1"/>
                    captcha required
                    </form></html>
                """.trimIndent()
        )
        val outcome = pipeline.loadChat(1, 2, "trace-captcha", 12, bypassCache = true)
        assertTrue(outcome is QmsChatLoadOutcome.Failure)
        outcome as QmsChatLoadOutcome.Failure
        assertEquals(QmsLoadErrorKind.CAPTCHA, outcome.kind)
        assertEquals(false, outcome.canRetry)
    }

    @Test
    fun `NetworkOnMainThreadException maps to network error`() = runTest {
        every { webClient.request(any()) } throws NetworkOnMainThreadException()
        val outcome = pipeline.loadChat(1, 2, "trace-main", 8, bypassCache = true)
        assertTrue(outcome is QmsChatLoadOutcome.Failure)
        outcome as QmsChatLoadOutcome.Failure
        assertEquals(QmsLoadErrorKind.NETWORK, outcome.kind)
        assertTrue(outcome.canRetry)
        if (BuildConfig.DEBUG) {
            assertTrue(outcome.message.contains("network_on_main_thread"))
        }
    }

    @Test
    fun `network timeout maps to retryable error`() = runTest {
        every { webClient.request(any()) } throws TimeoutException("timeout")
        val outcome = pipeline.loadChat(1, 2, "trace2", 2, bypassCache = true)
        assertTrue(outcome is QmsChatLoadOutcome.Failure)
        outcome as QmsChatLoadOutcome.Failure
        assertEquals(QmsLoadErrorKind.NETWORK, outcome.kind)
        assertTrue(outcome.canRetry)
    }

    @Test
    fun `HTTP error from client is server not network`() = runTest {
        every { webClient.request(any()) } throws OkHttpResponseException(503, "Service Unavailable", "https://4pda.to/")
        val outcome = pipeline.loadChat(1, 2, "trace4", 4, bypassCache = true)
        assertTrue(outcome is QmsChatLoadOutcome.Failure)
        outcome as QmsChatLoadOutcome.Failure
        assertEquals(QmsLoadErrorKind.SERVER, outcome.kind)
        assertTrue(outcome.canRetry)
    }

    @Test
    fun `HTTP 503 response is retried once and can recover`() = runTest {
        val recoveredBody = """
            <html act=qms>
            <div class="mess_list">
            <div class="mess_container our" data-mess-id="55">
            <div class="mess"><div class="content">Works</div></div>
            <div class="time"><span>11:00</span></div></div>
            </div>
            <input name="mid" value="1"/><input name="t" value="2"/>
            </html>
        """.trimIndent()
        every { webClient.request(any()) } returnsMany listOf(
                NetworkResponse(code = 503, body = "busy"),
                NetworkResponse(code = 200, body = recoveredBody)
        )
        val outcome = pipeline.loadChat(1, 2, "trace-http-retry", 9, bypassCache = true)
        assertTrue(outcome is QmsChatLoadOutcome.Content)
        assertEquals(1, (outcome as QmsChatLoadOutcome.Content).chat.messages.size)
    }

    @Test
    fun `HTTP 403 response is not retried`() = runTest {
        every { webClient.request(any()) } returns NetworkResponse(code = 403, body = "forbidden")
        val outcome = pipeline.loadChat(1, 2, "trace-http-403", 10, bypassCache = true)
        assertTrue(outcome is QmsChatLoadOutcome.Failure)
        outcome as QmsChatLoadOutcome.Failure
        assertEquals(QmsLoadErrorKind.SERVER, outcome.kind)
        assertEquals(false, outcome.canRetry)
    }

    @Test
    fun `empty cache is rejected on read`() = runTest {
        val emptyChat = forpdateam.ru.forpda.entity.remote.qms.QmsChatModel().apply {
            userId = 1
            themeId = 2
        }
        QmsChatMemoryCache.put(1, 2, emptyChat, QmsHtmlValidator.PageKind.UNKNOWN)
        val cached = QmsChatMemoryCache.get(1, 2)
        assertEquals(null, cached)
    }

    @Test
    fun `mess_container id-first fixture loads content`() = runTest {
        val body = loadQmsFixture("chat_mess_container_id_first.html")
        every { webClient.request(any()) } returns NetworkResponse(code = 200, body = body)
        val outcome = pipeline.loadChat(12_345, 67_890, "trace6", 6, bypassCache = true)
        assertTrue(outcome is QmsChatLoadOutcome.Content)
        assertEquals(2, (outcome as QmsChatLoadOutcome.Content).chat.messages.size)
    }

    @Test
    fun `mess_container thread loads content`() = runTest {
        val body = """
            <html act=qms>
            <div class="mess_list">
            <div class="mess_container our" data-mess-id="55">
            <div class="mess"><div class="content">Works</div></div>
            <div class="time"><span>11:00</span></div></div>
            </div>
            <input name="mid" value="1"/><input name="t" value="2"/>
            </html>
        """.trimIndent()
        every { webClient.request(any()) } returns NetworkResponse(code = 200, body = body)
        val outcome = pipeline.loadChat(1, 2, "trace5", 5, bypassCache = true)
        assertTrue(outcome is QmsChatLoadOutcome.Content)
        assertEquals(1, (outcome as QmsChatLoadOutcome.Content).chat.messages.size)
    }

    @Test
    fun `chat fetch uses xhr get request without form body`() = runTest {
        val requestSlot = slot<forpdateam.ru.forpda.model.data.remote.api.NetworkRequest>()
        val body = """
            <html act=qms>
            <div class="mess_list">
            <div class="mess_container our" data-mess-id="55">
            <div class="mess"><div class="content">Works</div></div>
            <div class="time"><span>11:00</span></div></div>
            </div>
            <input name="mid" value="1"/><input name="t" value="2"/>
            </html>
        """.trimIndent()
        every { webClient.request(capture(requestSlot)) } returns NetworkResponse(code = 200, body = body)

        val outcome = pipeline.loadChat(1, 2, "trace-xhr-get", 11, bypassCache = true)

        assertTrue(outcome is QmsChatLoadOutcome.Content)
        assertEquals("https://4pda.to/forum/index.php?act=qms&mid=1&t=2", requestSlot.captured.url)
        assertEquals(null, requestSlot.captured.formHeaders)
        assertEquals("XMLHttpRequest", requestSlot.captured.headers?.get("X-Requested-With"))
    }

    @Test
    fun `JSON envelope with html field loads content`() = runTest {
        val html = """
            <html act=qms>
            <div class="mess_list">
            <div class="mess_container our" data-mess-id="77">
            <div class="mess"><div class="content">From JSON</div></div>
            <div class="time"><span>10:00</span></div></div>
            </div>
            <input name="mid" value="1"/><input name="t" value="2"/>
            </html>
        """.trimIndent()
        val body = buildJsonObject { put("html", html) }.toString()
        every { webClient.request(any()) } returns NetworkResponse(code = 200, body = body)
        val outcome = pipeline.loadChat(1, 2, "trace7", 7, bypassCache = true)
        assertTrue(outcome is QmsChatLoadOutcome.Content)
        assertEquals(1, (outcome as QmsChatLoadOutcome.Content).chat.messages.size)
    }

    @Test
    fun `selector mismatch container fixture maps to parser failure`() = runTest {
        val body = loadQmsFixture("chat_selector_mismatch_container.html")
        every { webClient.request(any()) } returns NetworkResponse(code = 200, body = body)
        val outcome = pipeline.loadChat(111, 222, "trace-mismatch", 13, bypassCache = true)
        assertTrue(outcome is QmsChatLoadOutcome.Failure)
        outcome as QmsChatLoadOutcome.Failure
        assertEquals(QmsLoadErrorKind.PARSER, outcome.kind)
        assertTrue(outcome.canRetry)
        if (BuildConfig.DEBUG) {
            assertTrue(
                    "expected selector_mismatch_container in failure detail, got: ${outcome.message}",
                    outcome.message.contains("selector_mismatch_container")
            )
        }
    }

    @Test
    fun `cached valid thread displayed before refresh`() = runTest {
        val chat = forpdateam.ru.forpda.entity.remote.qms.QmsChatModel().apply {
            userId = 1
            themeId = 2
            messages.add(
                    forpdateam.ru.forpda.entity.remote.qms.QmsMessage().apply {
                        id = 7
                        content = "cached"
                    }
            )
        }
        QmsChatMemoryCache.put(1, 2, chat, QmsHtmlValidator.PageKind.QMS_THREAD)
        val outcome = pipeline.loadChat(1, 2, "trace3", 3, bypassCache = false)
        assertTrue(outcome is QmsChatLoadOutcome.Content)
        assertTrue((outcome as QmsChatLoadOutcome.Content).fromCache)
    }
}
