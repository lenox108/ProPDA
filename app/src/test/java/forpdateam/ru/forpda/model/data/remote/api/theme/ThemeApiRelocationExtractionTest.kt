package forpdateam.ru.forpda.model.data.remote.api.theme

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class ThemeApiRelocationExtractionTest {

    @Before
    fun setUp() {
        MovedTopicResolver.clearForTests()
    }

    @After
    fun tearDown() {
        MovedTopicResolver.clearForTests()
    }

    @Test
    fun extractsCanonicalTopicUrl() {
        val html = """
            <html><head>
              <link rel="canonical" href="/forum/index.php?showtopic=12345&amp;view=getnewpost">
            </head></html>
        """.trimIndent()
        assertEquals(
            "https://4pda.to/forum/index.php?showtopic=12345&view=getnewpost",
            ThemeApi.extractTopicRelocationUrlFromHtml(html)
        )
    }

    @Test
    fun extractsMetaRefreshTopicUrl() {
        val html = """
            <html><head>
              <meta http-equiv="refresh" content="0; url=/forum/index.php?showtopic=222">
            </head></html>
        """.trimIndent()
        assertEquals(
            "https://4pda.to/forum/index.php?showtopic=222",
            ThemeApi.extractTopicRelocationUrlFromHtml(html)
        )
    }

    @Test
    fun movedRelocationUrlCanBeStoredInResolver() {
        val original = "https://4pda.to/forum/index.php?showtopic=111&view=getnewpost"
        val html = """
            <html><body>
              <div class="errorwrap">
                <p>Тема перенесена в другой раздел.</p>
                <a href="/forum/index.php?showtopic=999&amp;st=40">новый</a>
              </div>
            </body></html>
        """.trimIndent()
        val relocated = ThemeApi.extractTopicRelocationUrlFromHtml(html, originalRequestUrl = original)
        assertEquals("https://4pda.to/forum/index.php?showtopic=999&st=40", relocated)
        val newId = Regex("showtopic=(\\d+)").find(relocated!!)?.groupValues?.get(1)?.toInt()
        MovedTopicResolver.remember(oldTopicId = 111, newTopicId = newId!!)
        assertEquals(999, MovedTopicResolver.resolve(111))
    }

    @Test
    fun extractsMovedTopicLinkPreferringDifferentId() {
        val original = "https://4pda.to/forum/index.php?showtopic=111&view=getnewpost"
        val html = """
            <html><body>
              <div class="errorwrap">
                <p>Тема перенесена в другой раздел.</p>
                <a href="/forum/index.php?showtopic=111">старый</a>
                <a href="/forum/index.php?showtopic=999&amp;st=40">новый</a>
              </div>
            </body></html>
        """.trimIndent()
        assertEquals(
            "https://4pda.to/forum/index.php?showtopic=999&st=40",
            ThemeApi.extractTopicRelocationUrlFromHtml(html, originalRequestUrl = original)
        )
    }

    @Test
    fun extractsJsLocationHrefRedirect() {
        val html = """
            <html><head>
              <script>
                window.location.href='/forum/index.php?showtopic=777&amp;view=getnewpost';
              </script>
            </head></html>
        """.trimIndent()
        assertEquals(
            "https://4pda.to/forum/index.php?showtopic=777&view=getnewpost",
            ThemeApi.extractTopicRelocationUrlFromHtml(html)
        )
    }

    @Test
    fun extractsHrefWithForumPrefixAndEntities() {
        val html = """
            <html><body>
              <div class="errorwrap">
                <p>Тема перенесена в другой раздел.</p>
                <a href="forum/index.php?showtopic=888&#038;st=40">go</a>
              </div>
            </body></html>
        """.trimIndent()
        assertEquals(
            "https://4pda.to/forum/index.php?showtopic=888&st=40",
            ThemeApi.extractTopicRelocationUrlFromHtml(html)
        )
    }

    @Test
    fun returnsNullWhenNoTopicLikeLinks() {
        val html = "<html><body><a href=\"/forum/index.php?showforum=1\">forum</a></body></html>"
        assertNull(ThemeApi.extractTopicRelocationUrlFromHtml(html))
    }

    @Test
    fun returnsNullWhenTopicPageHasPostsAndUnrelatedShowtopicLink() {
        val html = """
            <html><head><title>Обычная тема</title></head><body>
              <div class="post-block">
                <a name="entry123"></a>
                <div class="post_body">
                  Ссылка на другую тему внутри поста:
                  <a href="/forum/index.php?showtopic=766822">рандом</a>
                </div>
              </div>
              <div class="post-block">
                <a name="entry124"></a>
                <div class="post_body">ещё пост</div>
              </div>
            </body></html>
        """.trimIndent()
        assertNull(ThemeApi.extractTopicRelocationUrlFromHtml(html, originalRequestUrl = "https://4pda.to/forum/index.php?showtopic=1121568"))
    }
}

