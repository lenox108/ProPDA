package forpdateam.ru.forpda.model.data.remote.api.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ThemePageMetadataParserTest {
    private val parser = ThemePageMetadataParser()

    @Test
    fun parseTopicIds_prefersDataAttributes() {
        val html = """<div data-topic="42" data-forum="7"></div>"""

        assertEquals(42 to 7, parser.parseTopicIds(html))
    }

    @Test
    fun parseTopicIds_acceptsReorderedDataAttributes() {
        val html = """<div data-forum="7" data-topic="42"></div>"""

        assertEquals(42 to 7, parser.parseTopicIds(html))
    }

    @Test
    fun parseTopicIds_fallsBackToTopicAndForumUrls() {
        val html = """<a href="index.php?showtopic=42"></a><a href="index.php?showforum=7"></a>"""

        assertEquals(42 to 7, parser.parseTopicIds(html))
    }

    @Test
    fun parseTopicIds_returnsNullWithoutTopic() {
        assertNull(parser.parseTopicIds("<html></html>"))
    }

    @Test
    fun parseTopicIds_returnsNullForMalformedAttributes() {
        assertNull(parser.parseTopicIds("""<div data-topic="broken" data-forum="7"></div>"""))
        assertNull(parser.parseTopicIds("""<div data-topic="42" data-forum="broken"></div>"""))
    }

    @Test
    fun parseTitle_readsH1ThenOgTitle() {
        assertEquals("Title & test", parser.parseTitle("<h1>Title &amp; test</h1>"))
        assertEquals(
                "OG title",
                parser.parseTitle("""<meta property="og:title" content="OG title">""")
        )
    }

    @Test
    fun parseTitle_stripsNestedMarkupWithoutCrashing() {
        assertEquals("Nested title", parser.parseTitle("<h1><span>Nested title</span></h1>"))
    }
}
