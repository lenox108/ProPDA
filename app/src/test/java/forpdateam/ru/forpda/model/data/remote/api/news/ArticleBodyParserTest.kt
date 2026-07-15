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
        val out = parser.normalizeArticleText("<p>  Hello World  </p>")
        assertEquals("hello world", out)
    }

    @Test
    fun normalizeArticleText_replacesNonBreakingSpaceWithSpace() {
        val parser = newBodyParser()
        val nbsp = '\u00A0'
        val out = parser.normalizeArticleText("<p>Hello${nbsp}World</p>")
        assertEquals("hello world", out)
    }

    @Test
    fun extractArticleContent_delegatesToResolveArticleBodyContent() {
        val parser = newBodyParser()
        val html = """
            <html><body>
              <div class="article-body">
                <p>Delegated body</p>
              </div>
            </body></html>
        """.trimIndent()
        val viaExtract = parser.extractArticleContent(
                pageContext = ArticleParser.ArticlePageContext(html),
                phase = ArticleParsePhase.FULL
        )
        val viaResolve = parser.resolveArticleBodyContent(
                pageContext = ArticleParser.ArticlePageContext(html),
                phase = ArticleParsePhase.FULL
        )
        assertEquals(viaResolve.html, viaExtract.html)
        assertNotNull(viaExtract.html)
        assertTrue(viaExtract.html!!.contains("Delegated body"))
    }

    // 4pda nests the lead paragraph in `article-header > article-anons`, but the detail_v2 body
    // group starts AFTER the header — so the fallback body arrives without the lead (the reader saw
    // the article jump from the title straight to the hero image). The lead must be recovered.
    @Test
    fun resolveArticleBodyContent_prependsArticleAnonsLeadMissingFromV2Body() {
        val parser = newBodyParser()
        val page = """
            <html><body>
              <div class="article">
                <div class="article-header">
                  <h1>Title</h1>
                  <div class="article-anons">
                    <div class="article-meta"><time>14.07.26</time><a href="#comments">240</a></div>
                    <p>Lead sentence with <a href="https://x">a link</a>. More lead.</p>
                  </div>
                </div>
                <meta property="og:description" content="Lead sentence with a link. More lead."/><!--more-->
                <figure>img</figure><h2>Section</h2><p>Body paragraph.</p>
              </div>
            </body></html>
        """.trimIndent()
        val v2Fallback = """<!--more--><figure>img</figure><h2>Section</h2><p>Body paragraph.</p>"""
        val result = parser.resolveArticleBodyContent(
                pageContext = ArticleParser.ArticlePageContext(page),
                phase = ArticleParsePhase.FULL,
                regexFallback = v2Fallback
        )

        assertNotNull(result.html)
        assertTrue(result.html!!.contains("Lead sentence with"))
        // The lead's inline link survives.
        assertTrue(result.html!!.contains("""href="https://x""""))
        // Lead is prepended above the body, not appended after it.
        assertTrue(result.html!!.indexOf("Lead sentence") < result.html!!.indexOf("Body paragraph"))
    }

    @Test
    fun resolveArticleBodyContent_doesNotDuplicateLeadAlreadyInBody() {
        val parser = newBodyParser()
        val page = """<div class="article"><div class="article-header"><div class="article-anons"><p>Lead text here.</p></div></div></div>"""
        val fallback = """<p>Lead text here.</p><p>Body.</p>"""
        val result = parser.resolveArticleBodyContent(
                pageContext = ArticleParser.ArticlePageContext(page),
                phase = ArticleParsePhase.FULL,
                regexFallback = fallback
        )

        assertNotNull(result.html)
        assertEquals(1, Regex("Lead text here").findAll(result.html!!).count())
    }
}
