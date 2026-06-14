package forpdateam.ru.forpda.model.data.remote.api.reputation

import forpdateam.ru.forpda.common.Cp1251Codec
import forpdateam.ru.forpda.model.data.remote.IWebClient
import forpdateam.ru.forpda.model.data.remote.ParserPatterns
import forpdateam.ru.forpda.model.data.remote.api.NetworkRequest
import forpdateam.ru.forpda.model.data.remote.api.NetworkResponse
import forpdateam.ru.forpda.model.data.storage.IPatternProvider
import okhttp3.Cookie
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.regex.Pattern

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ReputationApiReportTest {

    private class ReputationPatternProviderStub : IPatternProvider {
        override fun getCurrentVersion(): Int = -1
        override fun update(jsonString: String) = Unit
        override fun getPattern(scope: String, key: String): Pattern {
            if (scope != ParserPatterns.Reputation.scope) return Pattern.compile("a^")
            return when (key) {
                ParserPatterns.Reputation.info -> Pattern.compile(
                        """showuser=(\d+)[^>]*>([^<]+)[^\[]*\[\+\s*(\d+)\/-(\d+)\]""",
                        Pattern.CASE_INSENSITIVE,
                )
                ParserPatterns.Reputation.main -> Pattern.compile("a^")
                else -> Pattern.compile("a^")
            }
        }
    }

    private class CapturingWebClient(
            private val responses: ArrayDeque<NetworkResponse> = ArrayDeque(),
    ) : IWebClient {
        val requests = mutableListOf<NetworkRequest>()

        override fun get(url: String): NetworkResponse {
            requests += NetworkRequest.Builder().url(url).build()
            return responses.removeFirstOrNull() ?: NetworkResponse(url = url, body = "")
        }

        override fun request(request: NetworkRequest): NetworkResponse {
            requests += request
            return responses.removeFirstOrNull() ?: NetworkResponse(url = request.url, body = "ok")
        }

        override fun request(request: NetworkRequest, progressListener: IWebClient.ProgressListener?): NetworkResponse =
                request(request)

        override fun getAuthKey(): String = "0"
        override fun getClientCookies(): Map<String, Cookie> = emptyMap()
        override fun clearCookies() = Unit
        override fun createWebSocketConnection(webSocketListener: WebSocketListener): WebSocket =
                throw UnsupportedOperationException()
    }

    @Test
    fun submitReport_usesParsedFormActionAndToken() {
        val reportUrl = "https://4pda.to/forum/index.php?act=report&reputation=13268602&st=0"
        val reportForm = """
            <form action="/forum/index.php" method="post">
                <input type="hidden" name="act" value="report">
                <input type="hidden" name="reputation" value="13268602">
                <input type="hidden" name="auth_key" value="token123">
                <input type="hidden" name="st" value="0">
                <textarea name="message"></textarea>
            </form>
        """.trimIndent()
        val webClient = CapturingWebClient(ArrayDeque(listOf(
                NetworkResponse(url = reportUrl, code = 200, body = reportForm),
                NetworkResponse(url = "https://4pda.to/forum/index.php?send=1", code = 200, body = "ok"),
        )))
        val api = ReputationApi(webClient, ReputationParser(ReputationPatternProviderStub()))

        val form = api.loadReportForm(userId = 42, reputationId = 13268602, reportUrl = reportUrl)
        api.submitReport(userId = 42, form = form, message = "Appeal text")

        assertEquals(reportUrl, webClient.requests[0].url)
        val submit = webClient.requests[1]
        assertTrue(submit.url.contains("send=1"))
        assertEquals("https://4pda.to/forum/index.php", submit.url.substringBefore("?"))
        assertEquals("report", submit.formHeaders?.get("act"))
        assertEquals("13268602", submit.formHeaders?.get("reputation"))
        assertEquals("token123", submit.formHeaders?.get("auth_key"))
        assertEquals(Cp1251Codec.encode("Appeal text"), submit.formHeaders?.get("message"))
    }

    @Test
    fun loadReportForm_rejectsLoginPage() {
        val webClient = CapturingWebClient(ArrayDeque(listOf(
                NetworkResponse(
                        url = "https://4pda.to/forum/index.php?act=report&reputation=1&st=0",
                        code = 200,
                        body = """<form id="loginform"><input type="password" name="PassWord"></form>""",
                )
        )))
        val api = ReputationApi(webClient, ReputationParser(ReputationPatternProviderStub()))

        assertThrows(IllegalStateException::class.java) {
            api.loadReportForm(userId = 1, reputationId = 1, reportUrl = "https://4pda.to/forum/index.php?act=report&reputation=1&st=0")
        }
    }
}
