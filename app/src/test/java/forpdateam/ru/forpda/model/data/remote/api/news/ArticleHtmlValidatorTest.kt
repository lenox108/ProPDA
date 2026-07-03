package forpdateam.ru.forpda.model.data.remote.api.news

import forpdateam.ru.forpda.entity.remote.news.DetailsPage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ArticleHtmlValidatorTest {

    @Test
    fun `classifyRawHtml detects login page`() {
        val html = """<html><body><form id="loginform" action="/wp-login.php">login</form></body></html>"""
        assertEquals(ArticleHtmlValidator.PageKind.LOGIN, ArticleHtmlValidator.classifyRawHtml(html))
    }

    @Test
    fun `classifyRawHtml does not misflag article about passwords as login`() {
        // Реальный кейс (458375): новость ПРО проверку PIN/паролей. В шапке сайта есть act=auth,
        // а в теле — слово "password". Раньше это ложно давало LOGIN → «Не удалось загрузить новость».
        val body = "PIN password проверка ".repeat(500)
        val html = """<html><body><div class="article-body"><p>$body</p></div>
            <a href="/forum/index.php?act=auth">Вход</a></body></html>"""
        assertTrue(html.length >= 8_000)
        assertFalse(ArticleHtmlValidator.looksLikeLoginPage(html))
        assertEquals(ArticleHtmlValidator.PageKind.ARTICLE, ArticleHtmlValidator.classifyRawHtml(html))
    }

    @Test
    fun `hasNonEmptyParsedBody rejects empty article`() {
        val page = DetailsPage().apply {
            id = 1
            title = "Title"
            html = ""
        }
        assertFalse(ArticleHtmlValidator.hasNonEmptyParsedBody(page))
    }

    @Test
    fun `isRenderableMappedHtml accepts nested content div`() {
        val html = """
            <html><body id="news">
            <article class="news-detail-header"><h1>Title</h1></article>
            <div class="content">
              <div class="wrapper"></div>
              <p>${"Article text with enough characters for validation. ".repeat(2)}</p>
            </div>
            </body></html>
        """.trimIndent()
        assertTrue(ArticleHtmlValidator.isRenderableMappedHtml(html))
        assertTrue(ArticleHtmlValidator.mappedContentPlainTextLen(html) >= 24)
    }

    @Test
    fun `isRenderableMappedHtml rejects header only shell`() {
        val html = """
            <html><body id="news">
            <article class="news-detail-header"><h1>Only header</h1></article>
            <div class="content"><div class="spacer"></div></div>
            </body></html>
        """.trimIndent()
        assertFalse(ArticleHtmlValidator.isRenderableMappedHtml(html))
    }

    @Test
    fun `isRenderableMappedHtml accepts media heavy article`() {
        val html = """
            <html><body id="news">
            <div class="content">
              <figure><img src="https://4pda.to/a.jpg" alt="shot" width="1200" height="675"/></figure>
            </div>
            </body></html>
        """.trimIndent().padEnd(140, ' ')
        assertTrue(ArticleHtmlValidator.isRenderableMappedHtml(html.trim()))
    }

    @Test
    fun `validateCached rejects parser version mismatch`() {
        val page = DetailsPage().apply {
            id = 1
            title = "T"
            html = "<div class=\"entry-content\">".padEnd(100, 'x')
        }
        val verdict = ArticleHtmlValidator.validateCached(
                page = page,
                parserVersion = ARTICLE_PARSER_VERSION - 1,
                storedAtMs = System.currentTimeMillis(),
                maxAgeMs = 60_000,
                nowMs = System.currentTimeMillis()
        )
        assertFalse(verdict.valid)
        assertEquals("parser_version_mismatch", verdict.reason)
    }
}
