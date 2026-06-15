package forpdateam.ru.forpda.model.data.remote.api.mentions

import forpdateam.ru.forpda.entity.remote.mentions.MentionItem
import forpdateam.ru.forpda.model.data.remote.ParserPatterns
import forpdateam.ru.forpda.model.data.storage.IPatternProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.regex.Pattern

/**
 * Golden-test for the Jsoup-based [MentionsJsoupParser] that
 * guards the §2.1 migration. The [MentionsJsoupParser] is
 * exercised on synthetic HTML that mirrors the structure of
 * `div.topic_title_post` blocks, and the resulting model is
 * compared field-by-field against an expected [MentionItem].
 *
 * The synthetic HTML fixtures used here include two anchors
 * inside the post_date div — a date link and a showuser link —
 * matching the actual site shape so that the Jsoup path can
 * distinguish them.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class MentionsJsoupParserTest {

    private class MentionsPatternProviderStub : IPatternProvider {
        override fun getCurrentVersion(): Int = 1
        override fun update(jsonString: String) {}
        override fun getPattern(scope: String, key: String): Pattern {
            require(scope == ParserPatterns.Mentions.scope) { scope }
            return when (key) {
                ParserPatterns.Mentions.main -> Pattern.compile(
                        """<div class="topic_title_post ?([^\"]*?)"[^>]*?>([^:]*?):[^<]*?<a[^>]*?href="([^\"]*?)"[^>]*?>(?:([^<]*?)(?:, ([^<]*?)|))</a>[\s\S]*?post_date[^\"]*?\"[^>]*?>[^<]*?<a[^>]*?>([\s\S]*?)</a>[\s\S]*?showuser[^>]*>([\s\S]*?)<""",
                        Pattern.CASE_INSENSITIVE
                )
                else -> throw IllegalArgumentException(key)
            }
        }
    }

    private val parserJsoup = MentionsJsoupParser()

    private fun assertJsoupMatches(html: String, expected: MentionItem) {
        val data = parserJsoup.parse(html)
        assertEquals(1, data.items.size)
        val actual = data.items.single()
        assertEquals("state", expected.state, actual.state)
        assertEquals("type", expected.type, actual.type)
        assertEquals("title", expected.title, actual.title)
        assertEquals("date", expected.date, actual.date)
        assertEquals("nick", expected.nick, actual.nick)
        assertEquals("link", expected.link, actual.link)
    }

    @Test
    fun parse_topicReadMention_setsReadAndTopic() {
        val html = """
            <html><body>
            <div class="topic_title_post">
                Форум: <a href="index.php?showtopic=123&amp;view=findpost&amp;p=99">Title A</a>
                <div class="post_date"><a href="/forum/index.php?showtopic=123">12.05.2024</a> <a href="/forum/index.php?showuser=42">user1</a></div>
            </div>
            </body></html>
        """.trimIndent()
        // preferMentionPostUrl short-circuits on findpost in primaryHref
        // and returns the href verbatim, so the link stays relative.
        assertJsoupMatches(html, MentionItem().apply {
            state = MentionItem.STATE_READ
            type = MentionItem.TYPE_TOPIC
            title = "Title A"
            date = "12.05.2024"
            nick = "user1"
            link = "index.php?showtopic=123&view=findpost&p=99"
        })
    }

    @Test
    fun parse_unreadMention_setsUnreadState() {
        val html = """
            <html><body>
            <div class="topic_title_post unread">
                Форум: <a href="index.php?showtopic=200&amp;view=findpost&amp;p=55">Topic unread</a>
                <div class="post_date"><a href="/forum/index.php?showtopic=200">2024-01-02</a> <a href="/forum/index.php?showuser=7">bob</a></div>
            </div>
            </body></html>
        """.trimIndent()
        assertJsoupMatches(html, MentionItem().apply {
            state = MentionItem.STATE_UNREAD
            type = MentionItem.TYPE_TOPIC
            title = "Topic unread"
            date = "2024-01-02"
            nick = "bob"
            link = "index.php?showtopic=200&view=findpost&p=55"
        })
    }

    @Test
    fun parse_newsMention_setsNewsType() {
        val html = """
            <html><body>
            <div class="topic_title_post">
                News: <a href="/news/feed/">Some news</a>
                <div class="post_date"><a href="/news/feed/">Yesterday</a> <a href="/forum/index.php?showuser=3">alice</a></div>
            </div>
            </body></html>
        """.trimIndent()
        val data = parserJsoup.parse(html)
        assertEquals(1, data.items.size)
        val actual = data.items.single()
        assertEquals(MentionItem.STATE_READ, actual.state)
        assertEquals(MentionItem.TYPE_NEWS, actual.type)
        assertEquals("Some news", actual.title)
        assertEquals("Yesterday", actual.date)
        assertEquals("alice", actual.nick)
    }

    @Test
    fun parse_topicOnlyLinkWithDataPostId_picksPostLink() {
        val html = """
            <html><body>
            <div class="topic_title_post" data-post-id="77">
                Форум: <a href="index.php?showtopic=3">T</a>
                <div class="post_date"><a href="/forum/index.php?showtopic=3">Today</a> <a href="/forum/index.php?showuser=11">u</a></div>
            </div>
            </body></html>
        """.trimIndent()
        val data = parserJsoup.parse(html)
        assertEquals(1, data.items.size)
        // preferMentionPostUrl + patchMentionLinkIfTopicOnly should
        // resolve to the showtopic & view=findpost form.
        assertEquals(
                "https://4pda.to/forum/index.php?showtopic=3&view=findpost&p=77",
                data.items.single().link
        )
    }

    @Test
    fun parse_emptyResponse_producesEmptyList() {
        val html = "<html><body></body></html>"
        val data = parserJsoup.parse(html)
        assertEquals(0, data.items.size)
    }

    @Test
    fun parse_multipleRows_preservesOrder() {
        val html = """
            <html><body>
            <div class="topic_title_post">
                Форум: <a href="index.php?showtopic=1&amp;view=findpost&amp;p=1">First</a>
                <div class="post_date"><a href="/forum/index.php?showtopic=1">01.01.2024</a> <a href="/forum/index.php?showuser=100">u1</a></div>
            </div>
            <div class="topic_title_post unread">
                News: <a href="/news/x/">Second</a>
                <div class="post_date"><a href="/news/x/">02.01.2024</a> <a href="/forum/index.php?showuser=200">u2</a></div>
            </div>
            </body></html>
        """.trimIndent()
        val data = parserJsoup.parse(html)
        assertEquals(2, data.items.size)
        assertEquals("First", data.items[0].title)
        assertEquals(MentionItem.STATE_READ, data.items[0].state)
        assertEquals(MentionItem.TYPE_TOPIC, data.items[0].type)
        assertEquals("01.01.2024", data.items[0].date)
        assertEquals("u1", data.items[0].nick)
        assertEquals("Second", data.items[1].title)
        assertEquals(MentionItem.STATE_UNREAD, data.items[1].state)
        assertEquals(MentionItem.TYPE_NEWS, data.items[1].type)
        assertEquals("02.01.2024", data.items[1].date)
        assertEquals("u2", data.items[1].nick)
    }

    @Test
    fun parse_returnsNonNullData() {
        val html = """
            <html><body>
            <div class="topic_title_post">
                Форум: <a href="index.php?showtopic=999&amp;view=findpost&amp;p=1">X</a>
                <div class="post_date"><a href="/forum/index.php?showtopic=999">Now</a> <a href="/forum/index.php?showuser=1">u</a></div>
            </div>
            </body></html>
        """.trimIndent()
        val data = parserJsoup.parse(html)
        assertNotNull(data)
        assertEquals(1, data.items.size)
    }

    /**
     * Sanity test: when the date link is the FIRST anchor and
     * the showuser link is the SECOND anchor inside the post_date
     * div, the Jsoup parser picks the right one.
     */
    @Test
    fun parse_postDateWithTwoAnchors_distinguishesThem() {
        val html = """
            <html><body>
            <div class="topic_title_post">
                Форум: <a href="index.php?showtopic=5&amp;view=findpost&amp;p=2">A</a>
                <div class="post_date"><a href="/forum/index.php?showtopic=5">31.12.2023</a> | <a href="/forum/index.php?showuser=99">jdoe</a></div>
            </div>
            </body></html>
        """.trimIndent()
        val data = parserJsoup.parse(html)
        assertEquals(1, data.items.size)
        assertEquals("31.12.2023", data.items.single().date)
        assertEquals("jdoe", data.items.single().nick)
    }
}
