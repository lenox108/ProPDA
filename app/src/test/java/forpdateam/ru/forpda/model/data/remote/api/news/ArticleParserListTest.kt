package forpdateam.ru.forpda.model.data.remote.api.news

import forpdateam.ru.forpda.model.data.storage.IPatternProvider
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.regex.Pattern

class ArticleParserListTest {

    @Test
    fun parseArticles_parsesRegularListCardWithProductionPattern() {
        val html = """
            <article class="post">
                <div>
                    <a href="https://4pda.to/2026/05/18/456789/test_news/" title="Test &amp; News">
                        <img src="https://4pda.to/wp-content/uploads/test.jpg" />
                    </a>
                    <a class="v-count" href="#comments">12</a>
                    <em class="date">18.05.2026</em>
                    <a href="https://4pda.to/forum/index.php?showuser=204809">News</a>
                    <div itemprop="description">Short &amp; useful description.</div>
                    <div class="meta"><a href="/tag/android/">Android</a></div>
                </div>
            </article>
        """.trimIndent()

        val item = ArticleParser(loadProductionPatterns()).parseArticles(html).single()

        assertEquals(456789, item.id)
        assertEquals("https://4pda.to/2026/05/18/456789/test_news/", item.url)
        assertEquals("Test & News", item.title)
        assertEquals("https://4pda.to/wp-content/uploads/test.jpg", item.imgUrl)
        assertEquals(12, item.commentsCount)
        assertEquals("18.05.2026", item.date)
        assertEquals(204809, item.authorId)
        assertEquals("News", item.author)
        assertEquals("Short & useful description.", item.description)
        assertEquals("Android", item.tags.single().title)
    }

    @Test
    fun parseArticlesFallback_parsesItemidCardWithLazySrcsetImage() {
        val html = """
            <article class='post card' itemid='456790'>
                <a href='https://4pda.to/2026/05/19/456790/lazy_news/'>
                    <img itemprop='image'
                         src='https://4pda.to/wp-content/uploads/placeholder.jpg'
                         data-srcset='https://4pda.to/wp-content/uploads/lazy-300.jpg 300w,
                                      https://4pda.to/wp-content/uploads/lazy-768.jpg 768w,
                                      https://4pda.to/wp-content/uploads/lazy-1536.jpg 1536w' />
                </a>
                <h2><a href='https://4pda.to/2026/05/19/456790/lazy_news/'>Lazy image news</a></h2>
                <a class='v-count' href='#comments'>5</a>
                <time class='date'>19.05.2026</time>
                <a href='https://4pda.to/forum/index.php?showuser=42'>Author</a>
                <div itemprop='description'>Fallback description.</div>
                <div class='meta'><a href="/tag/lazy/">Lazy</a></div>
            </article>
        """.trimIndent()

        val item = ArticleParser(FallbackOnlyPatternProvider()).parseArticles(html).single()

        assertEquals(456790, item.id)
        assertEquals("https://4pda.to/2026/05/19/456790/lazy_news/", item.url)
        assertEquals("Lazy image news", item.title)
        assertEquals("https://4pda.to/wp-content/uploads/lazy-768.jpg", item.imgUrl)
        assertEquals(5, item.commentsCount)
        assertEquals("19.05.2026", item.date)
        assertEquals(42, item.authorId)
        assertEquals("Author", item.author)
        assertEquals("Fallback description.", item.description)
        assertEquals("lazy", item.tags.single().tag)
    }

    @Test
    fun parseArticlesFallback_allowsCardWithoutImage() {
        val html = """
            <article class="post" itemid="456791">
                <h2><a href="https://4pda.to/2026/05/20/456791/no_image/" title="No image news">No image news</a></h2>
                <a class="v-count" href="#comments">0</a>
                <em class="date">20.05.2026</em>
                <a href="https://4pda.to/forum/index.php?showuser=7">Editor</a>
                <div itemprop="description"><p>No image description.</p></div>
            </article>
        """.trimIndent()

        val item = ArticleParser(FallbackOnlyPatternProvider()).parseArticles(html).single()

        assertEquals(456791, item.id)
        assertEquals("No image news", item.title)
        assertEquals(null, item.imgUrl)
        assertEquals(0, item.commentsCount)
        assertEquals("No image description.", item.description)
    }

    @Test
    fun parseArticles_emptyOrUnexpectedHtmlReturnsEmptyList() {
        val parser = ArticleParser(loadProductionPatterns())

        assertTrue(parser.parseArticles("").isEmpty())
        assertTrue(parser.parseArticles("<html><body><p>Not a news list</p></body></html>").isEmpty())
    }

    private class FallbackOnlyPatternProvider : IPatternProvider {
        override fun getCurrentVersion(): Int = -1
        override fun update(jsonString: String) = Unit
        override fun getPattern(scope: String, key: String): Pattern =
                when ("$scope/$key") {
                    "articles/list" -> Pattern.compile("a^")
                    "articles/tags" -> Pattern.compile("""<a[^>]*?href=["']/tag/([^"'/]*?)/["'][^>]*?>([^<]*?)</a>""")
                    else -> Pattern.compile("a^")
                }
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
}
