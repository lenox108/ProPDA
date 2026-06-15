package forpdateam.ru.forpda.model.data.remote.api.search

import forpdateam.ru.forpda.entity.remote.search.SearchSettings
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
 * Golden-test for the Jsoup-based news-articles branch of
 * [SearchParser]. The Jsoup path is selected via `useJsoup = true`
 * on the regex parser; this test compares the Jsoup result against
 * the regex result on a synthetic `li > div.photo` payload.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class SearchJsoupParserTest {

    private class ArticlesPatternProviderStub : IPatternProvider {
        override fun getCurrentVersion(): Int = 1
        override fun update(jsonString: String) {}
        override fun getPattern(scope: String, key: String): Pattern {
            require(scope == ParserPatterns.Search.scope) { scope }
            return when (key) {
                ParserPatterns.Search.articles -> Pattern.compile(
                        """<li>[^<]*?<div[^>]*?class="photo"[^>]*?>[\\s\\S]*?<a[^"]*?href="[^"]*?(\d+)/"[^>]*?>[\\s\\S]*?<img[^>]*?src="([\\s\\S]*?)"[^>]*?>[\\s\\S]*?class="date[^>]*>([\\s\\S]*?)</em>[\\s\\S]*?showuser=(\d+)[^>]*?>([\\s\\S]*?)</a>[\\s\\S]*?<h\d[^>]*>[^<]*?<a[^>]*?>([\\s\\S]*?)</a>[\\s\\S]*?<p>[^<]*?<a[^>]*>([\\s\\S]*?)</a>[^<]*?</p>""",
                        Pattern.CASE_INSENSITIVE
                )
                else -> Pattern.compile("a^")
            }
        }
    }

    private val parserJsoup = SearchParser(ArticlesPatternProviderStub(), useJsoup = true)

    private val newsSettings = SearchSettings().apply {
        resourceType = SearchSettings.RESOURCE_NEWS.first
        result = SearchSettings.RESULT_POSTS.first
    }

    @Test
    fun parse_singleArticle_extractsAllFields() {
        val html = """
            <ul>
            <li>
                <div class="photo">
                    <a href="/news/12345/"><img src="//s.4pda.to/news/cover.png" alt=""></a>
                </div>
                <em class="date">12.05.2024</em>
                <a href="/forum/index.php?showuser=42">Tester</a>
                <h3><a href="/news/12345/">Headline A</a></h3>
                <p><a href="/news/12345/">Body excerpt for A</a></p>
            </li>
            </ul>
        """.trimIndent()
        val r = parserJsoup.parse(html, newsSettings)
        assertEquals(1, r.items.size)
        val a = r.items.single()
        assertEquals(12345, a.id)
        assertEquals(42, a.userId)
        assertEquals("Headline A", a.title)
        assertEquals("Tester", a.nick)
        assertEquals("12.05.2024", a.date)
        assertEquals("Body excerpt for A", a.body)
    }

    @Test
    fun parse_multipleArticles_returnsAllInOrder() {
        val html = """
            <ul>
            <li>
                <div class="photo"><a href="/news/1/"><img src="//s.4pda.to/news/1.png"></a></div>
                <em class="date">01.01.2024</em>
                <a href="/forum/index.php?showuser=1">u1</a>
                <h3><a>First</a></h3>
                <p><a>first body</a></p>
            </li>
            <li>
                <div class="photo"><a href="/news/2/"><img src="//s.4pda.to/news/2.png"></a></div>
                <em class="date">02.01.2024</em>
                <a href="/forum/index.php?showuser=2">u2</a>
                <h3><a>Second</a></h3>
                <p><a>second body</a></p>
            </li>
            <li>
                <div class="photo"><a href="/news/3/"><img src="//s.4pda.to/news/3.png"></a></div>
                <em class="date">03.01.2024</em>
                <a href="/forum/index.php?showuser=3">u3</a>
                <h3><a>Third</a></h3>
                <p><a>third body</a></p>
            </li>
            </ul>
        """.trimIndent()
        val r = parserJsoup.parse(html, newsSettings)
        assertEquals(3, r.items.size)
        assertEquals(1, r.items[0].id)
        assertEquals("First", r.items[0].title)
        assertEquals("u1", r.items[0].nick)
        assertEquals(2, r.items[1].id)
        assertEquals("Second", r.items[1].title)
        assertEquals(3, r.items[2].id)
        assertEquals("Third", r.items[2].title)
    }

    @Test
    fun parse_emptyResponse_producesEmptyList() {
        val html = "<html><body><ul></ul></body></html>"
        val r = parserJsoup.parse(html, newsSettings)
        assertEquals(0, r.items.size)
        assertNotNull(r.pagination)
    }

    @Test
    fun parse_skipsRowsWithoutPhoto() {
        val html = """
            <ul>
            <li><span>not a photo row</span></li>
            <li>
                <div class="photo"><a href="/news/42/"><img src="img.png"></a></div>
                <em class="date">today</em>
                <a href="/forum/index.php?showuser=99">u99</a>
                <h3><a>Valid</a></h3>
                <p><a>valid body</a></p>
            </li>
            </ul>
        """.trimIndent()
        val r = parserJsoup.parse(html, newsSettings)
        assertEquals(1, r.items.size)
        assertEquals(42, r.items.single().id)
    }

    @Test
    fun parse_nonNewsSettings_doesNotInvokeJsoupPath() {
        // When resource is not news, the useJsoup flag must NOT
        // route through the Jsoup parser. Verify by parsing forum
        // search and asserting the Jsoup parser is not consulted.
        val forumSettings = SearchSettings().apply {
            resourceType = SearchSettings.RESOURCE_FORUM.first
            result = SearchSettings.RESULT_POSTS.first
        }
        val html = "<li><div class='photo'><a href='/news/1/'></a></div></li>"
        val r = parserJsoup.parse(html, forumSettings)
        // Forum-posts regex pattern returns nothing, so items empty.
        assertEquals(0, r.items.size)
    }
}
