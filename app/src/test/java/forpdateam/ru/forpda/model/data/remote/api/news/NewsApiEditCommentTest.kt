package forpdateam.ru.forpda.model.data.remote.api.news

import forpdateam.ru.forpda.entity.remote.news.Comment
import forpdateam.ru.forpda.model.data.remote.IWebClient
import forpdateam.ru.forpda.model.data.remote.ParserPatterns
import forpdateam.ru.forpda.model.data.remote.api.NetworkRequest
import forpdateam.ru.forpda.model.data.remote.api.NetworkResponse
import forpdateam.ru.forpda.model.data.storage.IPatternProvider
import okhttp3.Cookie
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.regex.Pattern

/**
 * Регресс на редактирование комментариев к новостям.
 *
 * Живой механизм 4pda (подтверждён захватом сети в браузере): правка — это обычный POST на
 * wp-comments-post.php с выставленным comment_ID (тем же, что и публикация нового), НОНС НЕ нужен.
 * Прежний код целил в admin-ajax.php?action=editcomment, которого на сервере нет (404), плюс
 * hasParsedEditForm/buildInlineCommentEditAction требовали нонс — из-за чего правка не работала
 * (удаление при этом работало через свой эндпоинт).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class NewsApiEditCommentTest {

    private class StubPatternProvider : IPatternProvider {
        override fun getCurrentVersion(): Int = 29
        override fun update(jsonString: String) = Unit
        override fun getPattern(scope: String, key: String): Pattern = Pattern.compile("a^")
    }

    private class CapturingWebClient(
            responses: List<NetworkResponse> = emptyList()
    ) : IWebClient {
        private val queue = ArrayDeque(responses)
        val requests = mutableListOf<NetworkRequest>()

        override fun get(url: String): NetworkResponse = NetworkResponse(url = url)
        override fun request(request: NetworkRequest): NetworkResponse {
            requests += request
            return queue.removeFirstOrNull() ?: NetworkResponse(url = request.url, body = "ok")
        }
        override fun request(request: NetworkRequest, progressListener: IWebClient.ProgressListener?): NetworkResponse =
                request(request)
        override fun requestWithoutMobileCookie(request: NetworkRequest): NetworkResponse {
            requests += request
            return queue.removeFirstOrNull() ?: NetworkResponse(url = request.url, body = "ok")
        }
        override fun getAuthKey(): String = "0"
        override fun getClientCookies(): Map<String, Cookie> = emptyMap()
        override fun clearCookies() = Unit
        override fun createWebSocketConnection(webSocketListener: WebSocketListener): WebSocket =
                throw UnsupportedOperationException()
    }

    @Test
    fun editComment_ownCommentFallback_postsToWpCommentsPostWithCommentId() {
        val webClient = CapturingWebClient(listOf(
                NetworkResponse(url = "https://4pda.to/wp-comments-post.php", body = "<html>ok</html>")
        ))
        val parser = ArticleParser(StubPatternProvider())
        val api = NewsApi(webClient, parser)

        // экшен правки, который теперь строится для собственного комментария
        val action = parser.buildFallbackEditCommentAction(commentId = 10650778, articleId = 458291)

        val result = api.editComment(action, "новый текст комментария")

        assertTrue(result)
        // ровно один запрос — значит форма ушла напрямую, без похода в несуществующий admin-ajax
        val request = webClient.requests.single()
        assertEquals("https://4pda.to/wp-comments-post.php", request.url)
        assertFalse("edit must not use admin-ajax editcomment (404)", request.url.contains("admin-ajax"))
        val fields = requireNotNull(request.formHeaders)
        assertEquals("10650778", fields["comment_ID"])
        assertEquals("458291", fields["comment_post_ID"])
        assertTrue("comment text field must be present", fields.containsKey("comment"))
    }

    @Test
    fun buildFallbackEditCommentAction_targetsWpCommentsPost_notAdminAjax() {
        val action = ArticleParser(StubPatternProvider())
                .buildFallbackEditCommentAction(commentId = 42, articleId = 100)

        assertEquals("https://4pda.to/wp-comments-post.php", action.url)
        assertFalse(action.url.orEmpty().contains("admin-ajax"))
        assertEquals("42", action.fields["comment_ID"])
        assertEquals("100", action.fields["comment_post_ID"])
        assertEquals(Comment.Action.Type.EDIT, action.type)
    }
}
