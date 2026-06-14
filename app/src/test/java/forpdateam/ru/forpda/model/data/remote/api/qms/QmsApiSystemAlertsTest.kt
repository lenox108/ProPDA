package forpdateam.ru.forpda.model.data.remote.api.qms

import forpdateam.ru.forpda.model.data.remote.IWebClient
import forpdateam.ru.forpda.model.data.remote.api.NetworkRequest
import forpdateam.ru.forpda.model.data.remote.api.NetworkResponse
import forpdateam.ru.forpda.model.data.storage.IPatternProvider
import kotlinx.coroutines.runBlocking
import okhttp3.Cookie
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.regex.Pattern

class QmsApiSystemAlertsTest {

    @Test
    fun `system chat with valid themeId is fetched directly without theme re-resolution`() {
        val webClient = RecordingWebClient()
        val api = QmsApi(webClient, QmsParser(NoopPatternProvider()))

        runBlocking { api.fetchChat(userId = 0, themeId = 282644) }

        // The concrete thread must be requested as-is; the themes-list (mid=0 without t) request
        // that can resolve to the virtual id=0 thread must NOT happen.
        assertEquals(1, webClient.urls.size)
        assertTrue(webClient.urls.single().contains("mid=0&t=282644"))
        assertFalse(webClient.urls.any { it.endsWith("act=qms&mid=0") })
    }

    @Test
    fun `system contact themes list is requested from the mid=0 mailbox`() {
        val webClient = RecordingWebClient()
        val api = QmsApi(webClient, QmsParser(NoopPatternProvider()))

        api.getThemesList(0)

        // The system mailbox lives at mid=0; requesting the QMS root (act=qms with no mid) returned
        // the contacts page and no thread rows, which caused the empty "Оповещения" regression.
        assertEquals(1, webClient.urls.size)
        assertTrue(webClient.urls.single().endsWith("act=qms&mid=0"))
    }

    @Test
    fun `empty system mailbox does not synthesise an unusable virtual theme`() {
        val api = QmsApi(RecordingWebClient(), QmsParser(NoopPatternProvider()))

        // NoopPatternProvider never matches, so the mailbox parses to zero rows. We must NOT add a
        // virtual id=0 theme (it would navigate a themeId<=0 that the chat screen rejects).
        val themes = api.getThemesList(0)

        assertTrue(themes.themes.isEmpty())
    }

    @Test
    fun `system chat without valid themeId falls back to theme resolution`() {
        val webClient = RecordingWebClient()
        val api = QmsApi(webClient, QmsParser(NoopPatternProvider()))

        runBlocking { api.fetchChat(userId = 0, themeId = 0) }

        // First the themes list is fetched, then a chat request is made (here resolution yields the
        // fallback id, so t=0). The important point is the themes-list lookup only runs when no
        // valid thread id was supplied.
        assertTrue(webClient.urls.any { it.endsWith("act=qms&mid=0") })
        assertTrue(webClient.urls.any { it.contains("mid=0&t=") })
    }

    private class RecordingWebClient : IWebClient {
        val urls = mutableListOf<String>()

        override fun get(url: String): NetworkResponse {
            urls.add(url)
            return NetworkResponse(url = url, body = "")
        }

        override fun request(request: NetworkRequest): NetworkResponse = get(request.url)

        override fun request(request: NetworkRequest, progressListener: IWebClient.ProgressListener?): NetworkResponse =
                get(request.url)

        override fun getAuthKey(): String = ""

        override fun getClientCookies(): Map<String, Cookie> = emptyMap()

        override fun clearCookies() = Unit

        override fun createWebSocketConnection(webSocketListener: WebSocketListener): WebSocket {
            throw UnsupportedOperationException("Not needed for QmsApi tests")
        }
    }

    private class NoopPatternProvider : IPatternProvider {
        override fun getCurrentVersion(): Int = 0

        // Never matches, so parseThemes returns empty and (for mid=0) synthesises the virtual theme.
        override fun getPattern(scope: String, key: String): Pattern = Pattern.compile("a^")

        override fun update(jsonString: String) = Unit
    }
}
