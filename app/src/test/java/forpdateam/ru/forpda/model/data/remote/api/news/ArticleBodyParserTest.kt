package forpdateam.ru.forpda.model.data.remote.api.news

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Smoke tests for [ArticleBodyParser] covering the most important public methods that were
 * extracted from [ArticleParser] as part of the §1.1 decomposition. These tests are deliberately
 * tiny — they exercise the helpers and the regex-fallback fast path with synthetic HTML so
 * regressions in the extracted class are caught without depending on full article fixtures.
 */
class ArticleBodyParserTest {

    private fun newBodyParser(
            articleContentRegexes: List<Regex> = listOf(
                    Regex("""class="article-body"[^>]*>(.*?)</div>""", RegexOption.DOT_MATCHES_ALL)
            )
    ): ArticleBodyParser = ArticleBodyParser(
            articleFromHtml = { input -> input?.replace(Regex("<[^>]+>"), "")?.trim() },
            cleanPollText = { source -> source.replace(Regex("<[^>]+>"), "") },
            articleContentRegexes = articleContentRegexes,
            leadClassMarkers = listOf("lead", "intro", "announce", "subtitle", "article__lead", "content__lead"),
            articleBodyClassMarkers = listOf("article-body", "article__body", "content-body", "entry-content"),
            articleMediaClassMarkers = listOf("article-media", "article-image", "wp-caption"),
            skippedArticleClassMarkers = listOf("article-share", "article-related", "article-nav", "article-aside"),
            articleBodyMetaClassMarkers = listOf("article-meta", "article-meta-info"),
            contentHeadingTagNames = setOf("h2", "h3", "h4", "h5", "h6"),
            whitespaceRegex = Regex("""\s+"""),
            extractYoutubeVideoId = { url ->
                val match = Regex("""(?:v=|youtu\.be/)([\w-]{11})""").find(url)
                match?.groupValues?.getOrNull(1)
            }
    )

    @Test
    fun resolveArticleBodyContent_prefersRegexFallbackWhenProvided() {
        val parser = newBodyParser()
        val fallback = "<p>fallback body</p>"
        val result = parser.resolveArticleBodyContent(
                pageContext = ArticleParser.ArticlePageContext("<html><body>empty</body></html>"),
                phase = ArticleParsePhase.FULL,
                regexFallback = fallback
        )
        assertEquals(fallback, result.html)
        assertFalse(result.hasInlineHeroMedia)
    }

    @Test
    fun resolveArticleBodyContent_fallsBackToRegexExtractionWhenPresent() {
        val html = """
            <html><body>
              <div class="article-body">
                <p>Real article body paragraph</p>
              </div>
            </body></html>
        """.trimIndent()
        val parser = newBodyParser()
        val result = parser.resolveArticleBodyContent(
                pageContext = ArticleParser.ArticlePageContext(html),
                phase = ArticleParsePhase.FULL
        )
        assertNotNull(result.html)
        assertTrue(result.html!!.contains("Real article body paragraph"))
    }

    @Test
    fun resolveArticleBodyContent_returnsNullHtmlWhenNoMatchAndFirstRender() {
        val parser = newBodyParser()
        val result = parser.resolveArticleBodyContent(
                pageContext = ArticleParser.ArticlePageContext("no article markup at all"),
                phase = ArticleParsePhase.FIRST_RENDER
        )
        assertEquals(null, result.html)
    }

    @Test
    fun normalizeArticleText_stripsWhitespaceAndLowercases() {
        val parser = newBodyParser()
        val out = parser.normalizeArticleText("<p>  Hello&nbsp;World  </p>")
        assertEquals("helloworld", out)
    }

    @Test
    fun extractArticleContent_delegatesToResolveArticleBodyContent() {
        val parser = newBodyParser()
        val regexFallback = "<p>delegated</p>"
        val viaExtract = parser.extractArticleContent(
                pageContext = ArticleParser.ArticlePageContext("irrelevant"),
                phase = ArticleParsePhase.FULL
        )
        val viaResolve = parser.resolveArticleBodyContent(
                pageContext = ArticleParser.ArticlePageContext("irrelevant"),
                phase = ArticleParsePhase.FULL,
                regexFallback = regexFallback
        )
        assertEquals(viaResolve.html, viaExtract.html)
        assertEquals(regexFallback, viaExtract.html)
    }
}
