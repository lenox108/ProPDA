package forpdateam.ru.forpda.model.data.remote.api.search

import forpdateam.ru.forpda.entity.remote.search.SearchSettings
import forpdateam.ru.forpda.model.data.remote.IWebClient
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
import java.util.regex.Pattern

class SearchApiTest {

    @Test
    fun getSearch_userIdSearchDoesNotSendEmojiUsernameParameter() {
        val webClient = CapturingWebClient()
        val api = SearchApi(webClient, SearchParser(EmptyPatternProvider()))

        api.getSearch(SearchSettings().apply {
            addForum("10")
            addTopic("20")
            source = SearchSettings.SOURCE_CONTENT.first
            nick = "⚡ elektrik ⚡"
            userId = 598
            result = SearchSettings.RESULT_POSTS.first
            subforums = SearchSettings.SUB_FORUMS_FALSE
        })

        val requestedUrl = webClient.lastUrl
        assertTrue(requestedUrl.contains("username-id=598"))
        assertFalse(requestedUrl.contains("username="))
        assertFalse(requestedUrl.contains("%3F"))
        assertTrue(requestedUrl.contains("forums=10"))
        assertTrue(requestedUrl.contains("topics=20"))
    }

    @Test
    fun parse_forumPostPrefersResultEntryIdOverBodyPostLink() {
        val parser = SearchParser(SinglePatternProvider(
                Pattern.compile(
                        "<a name=\"entry(\\d+)\"></a>" +
                                "(?:missing-topic-and-post(\\d+)(\\d+))?" +
                                "<a href=\"/forum/index.php\\?showtopic=(?:222)\">([^<]*)</a>" +
                                "\\|date=([^|]*)\\|num=([^|]*)\\|online=([^|]*)\\|avatar=([^|]*)" +
                                "\\|nick=([^|]*)\\|user=(\\d+)\\|curator=([^|]*)\\|color=([^|]*)" +
                                "\\|group=([^|]*)\\|minus=([^|]*)\\|rep=([^|]*)\\|plus=([^|]*)" +
                                "\\|report=([^|]*)\\|edit=([^|]*)\\|delete=([^|]*)\\|quote=([^|]*)" +
                                "\\|body=([\\s\\S]*)"
                )
        ))

        val result = parser.parse(
                "<a name=\"entry111\"></a>" +
                        "<a href=\"/forum/index.php?showtopic=222\">Topic</a>" +
                        "|date=today|num=1|online=green|avatar=avatar.png|nick=Tester|user=598" +
                        "|curator=|color=#000|group=User|minus=|rep=0|plus=|report=|edit=|delete=|quote=" +
                        "|body=<a href=\"/forum/index.php?showtopic=222&p=333\">quoted</a>",
                SearchSettings().apply { result = SearchSettings.RESULT_POSTS.first }
        )

        assertEquals(1, result.items.size)
        assertEquals(222, result.items[0].topicId)
        assertEquals(111, result.items[0].id)
    }

    @Test
    fun forumPostsFixture_documentsSearchSpoilerCorpus() {
        val html = javaClass.classLoader
                ?.getResource("parser/search/forum_posts_spoiler_hit.html")
                ?.readText()
                ?: error("Missing parser/search/forum_posts_spoiler_hit.html")
        assertTrue(html.contains("name=\"entry55501\""))
        assertTrue(html.contains("post-block spoil"))
        assertTrue(html.contains("showtopic=222"))
        assertTrue(html.contains("full tail"))
    }

    @Test
    fun parse_forumPostKeepsCompletePostBodyHtml() {
        val parser = SearchParser(SinglePatternProvider(
                Pattern.compile(
                        "<a name=\"entry(\\d+)\"></a>" +
                                "(?:missing-topic-and-post(\\d+)(\\d+))?" +
                                "<a href=\"/forum/index.php\\?showtopic=(?:222)\">([^<]*)</a>" +
                                "\\|date=([^|]*)\\|num=([^|]*)\\|online=([^|]*)\\|avatar=([^|]*)" +
                                "\\|nick=([^|]*)\\|user=(\\d+)\\|curator=([^|]*)\\|color=([^|]*)" +
                                "\\|group=([^|]*)\\|minus=([^|]*)\\|rep=([^|]*)\\|plus=([^|]*)" +
                                "\\|report=([^|]*)\\|edit=([^|]*)\\|delete=([^|]*)\\|quote=([^|]*)" +
                                "\\|body=([\\s\\S]*)"
                )
        ))

        val bodyHtml = "<p>short match</p><div class=\"post-block spoil\"><div class=\"block-title\">spoiler</div>" +
                "<div class=\"block-body\"><a href=\"https://4pda.to/forum/index.php?showtopic=222&p=111\">full link</a>" +
                "<img src=\"image.png\"></div></div><p>full tail</p>"
        val result = parser.parse(
                "<a name=\"entry111\"></a>" +
                        "<a href=\"/forum/index.php?showtopic=222\">Topic</a>" +
                        "|date=today|num=1|online=green|avatar=avatar.png|nick=Tester|user=598" +
                        "|curator=|color=#000|group=User|minus=|rep=0|plus=|report=|edit=|delete=|quote=" +
                        "|body=$bodyHtml",
                SearchSettings().apply { result = SearchSettings.RESULT_POSTS.first }
        )

        assertEquals(bodyHtml, result.items[0].body)
    }

    private class CapturingWebClient : IWebClient {
        lateinit var lastUrl: String

        override fun get(url: String): NetworkResponse {
            lastUrl = url
            return NetworkResponse(url = url, body = "")
        }

        override fun request(request: NetworkRequest): NetworkResponse = get(request.url)

        override fun request(request: NetworkRequest, progressListener: IWebClient.ProgressListener?): NetworkResponse =
                get(request.url)

        override fun getAuthKey(): String = ""

        override fun getClientCookies(): Map<String, Cookie> = emptyMap()

        override fun clearCookies() = Unit

        override fun createWebSocketConnection(webSocketListener: WebSocketListener): WebSocket {
            throw UnsupportedOperationException("Not needed for SearchApi tests")
        }
    }

    private class EmptyPatternProvider : IPatternProvider {
        override fun getCurrentVersion(): Int = 0

        override fun getPattern(scope: String, key: String): Pattern = Pattern.compile("a^")

        override fun update(jsonString: String) = Unit
    }

    private class SinglePatternProvider(private val forumPostsPattern: Pattern) : IPatternProvider {
        override fun getCurrentVersion(): Int = 0

        override fun getPattern(scope: String, key: String): Pattern =
                if (scope == "search" && key == "forum_posts") forumPostsPattern else Pattern.compile("a^")

        override fun update(jsonString: String) = Unit
    }
}
