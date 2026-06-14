package forpdateam.ru.forpda.presentation.articles.detail

import biz.source_code.miniTemplator.MiniTemplator
import forpdateam.ru.forpda.entity.remote.news.DetailsPage
import forpdateam.ru.forpda.model.data.remote.ParserPatterns
import forpdateam.ru.forpda.model.data.remote.api.news.ArticleBlock
import forpdateam.ru.forpda.model.data.remote.api.news.ArticleParser
import forpdateam.ru.forpda.model.data.storage.IPatternProvider
import forpdateam.ru.forpda.ui.TemplateManager
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.regex.Pattern

/**
 * Audit IDs 10/12: typed [ArticleBlock.Poll] must survive sanitize + template map (render pipeline).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ArticlePollRenderPipelineTest {

    @Test
    fun sanitizerInvariant_preservesNormalizedVotePoll() {
        val poll = normalizedPollFixture()
        val sanitized = ArticleHtmlSecuritySanitizer.sanitize(poll).orEmpty()

        assertTrue(ArticleBlock.pollSurvivedSanitize(poll, sanitized))
        assertNotNull(ArticleBlock.findPollBlock(sanitized))
        assertTrue(sanitized.contains("<form"))
        assertTrue(sanitized.contains("name=\"answer[]\""))
        assertTrue(sanitized.contains("Проголосовать"))
        assertFalse(sanitized.contains("onclick", ignoreCase = true))
    }

    @Test
    fun sanitizerInvariant_preservesPollThroughArticleTemplateMap() {
        val page = DetailsPage().apply {
            id = 457102
            title = "Опрос: тест"
            html = "<p>Текст статьи.</p>\n${normalizedPollFixture()}"
            commentsCount = 0
        }

        val mapped = articleTemplate().mapString(page)
        val typedPoll = ArticleBlock.findPollBlock(mapped)

        assertTrue(ArticleBlock.pollSurvivedSanitize(page.html, mapped))
        assertNotNull(typedPoll)
        assertEquals("1335", typedPoll!!.pollId)
        assertTrue(mapped.contains("<form"))
        assertTrue(mapped.contains("name=\"answer[]\""))
        assertTrue(mapped.contains("value=\"7512\""))
        assertTrue(mapped.contains("Проголосовать"))
    }

    @Test
    fun parserPollFrameFixture_survivesFullRenderPipeline() {
        val html = resource("parser/news/article_with_poll_frame.html")
        val article = ArticleParser(ArticlesPatternProviderStub()).parseArticle(html)
        val mapped = articleTemplate().mapString(article)

        val typedBefore = ArticleBlock.findPollBlock(article.html)
        val typedAfter = ArticleBlock.findPollBlock(mapped)

        assertNotNull(typedBefore)
        assertNotNull(typedAfter)
        assertTrue(ArticleBlock.pollSurvivedSanitize(article.html, mapped))
        assertTrue(mapped.contains("name=\"answer[]\""))
        assertTrue(mapped.contains("value=\"7512\""))
        assertFalse(mapped.contains("poll-frame-option"))
        assertFalse(mapped.contains("poll-frame-title"))
    }

    @Test
    fun bindNewsPolls_targetsNormalizedFormNotRawPollFrameButtons() {
        val newsJs = newsJsFile().readText()

        assertTrue(newsJs.contains("function transformPoll()"))
        assertTrue(newsJs.contains("form[action*=\"/pages/poll/\"]"))
        assertTrue(newsJs.contains("input[name=\"answer[]\"]"))
        assertFalse(newsJs.contains("poll-frame-option"))
    }

    private fun normalizedPollFixture(): String = """
        <div id="poll-ajax-frame-news" class="poll-ajax-frame news-poll news-poll-normalized" data-normalized-poll="true" data-news-poll-token="poll-1335-100">
          <h2 onclick="alert(1)">Планирую купить смартфон в этом году</h2>
          <form action="https://4pda.to/pages/poll/?act=vote&amp;poll_id=1335" method="post">
            <input type="hidden" name="poll_id" value="1335">
            <ul class="poll-list">
              <li><label class="text"><input type="radio" name="answer[]" value="7512"> <span>До 10 000 руб.</span></label></li>
              <li><label class="text"><input type="radio" name="answer[]" value="7513"> <span>От 10 000 до 23 000 руб.</span></label></li>
            </ul>
            <button type="submit" class="btn">Проголосовать</button>
          </form>
        </div>
    """.trimIndent()

    private fun articleTemplate(): ArticleTemplate {
        val templateManager = mockk<TemplateManager>()
        every { templateManager.getTemplate(TemplateManager.TEMPLATE_NEWS) } returns
                MiniTemplator.Builder()
                        .setSkipUndefinedVars(true)
                        .build(newsTemplateFile().inputStream(), Charsets.UTF_8)
        every { templateManager.fillStaticStrings(any()) } answers { firstArg() }
        every { templateManager.getThemeType() } returns "light"
        every { templateManager.getThemeOverridesCss() } returns ""
        every { templateManager.getStaticString("res_s_comments") } returns "Комментарии"
        every { templateManager.getStaticString("news_inline_comments_description") } returns ""
        every { templateManager.getStaticString("news_inline_comments_show") } returns "Показать"
        every { templateManager.getStaticString("news_inline_comments_hide") } returns "Скрыть"
        every { templateManager.getStaticString("retry") } returns "Повторить"
        return ArticleTemplate(templateManager)
    }

    private fun newsTemplateFile(): File =
            File("src/main/assets/template_news.html").takeIf { it.isFile }
                    ?: File("app/src/main/assets/template_news.html")

    private fun newsJsFile(): File =
            File("src/main/assets/forpda/scripts/modules/news.js").takeIf { it.isFile }
                    ?: File("app/src/main/assets/forpda/scripts/modules/news.js")

    private fun resource(path: String): String =
            javaClass.classLoader?.getResource(path)?.readText()
                    ?: error("Missing test resource $path")

    private class ArticlesPatternProviderStub : IPatternProvider {
        override fun getCurrentVersion(): Int = 29
        override fun update(jsonString: String) = Unit
        override fun getPattern(scope: String, key: String): Pattern {
            if (scope == ParserPatterns.Global.scope) {
                return when (key) {
                    ParserPatterns.Global.meta_tags ->
                        Pattern.compile("<meta[^>]*?property=\"([^:]*?):([^\"]*?)\"[^>]*?content=\"([^>]*?)\"[^>]*?>")
                    else -> throw IllegalArgumentException(key)
                }
            }
            require(scope == ParserPatterns.Articles.scope) { scope }
            return when (key) {
                ParserPatterns.Articles.detail_detector -> Pattern.compile("a^")
                ParserPatterns.Articles.list -> Pattern.compile("a^")
                ParserPatterns.Articles.exclude_form_comment -> Pattern.compile("<form[\\s\\S]*", Pattern.CASE_INSENSITIVE)
                ParserPatterns.Articles.comment_id -> Pattern.compile("comment-(\\d+)")
                ParserPatterns.Articles.comment_user_id -> Pattern.compile("showuser=(\\d+)")
                ParserPatterns.Articles.karmaSource -> Pattern.compile("a^")
                ParserPatterns.Articles.karma -> Pattern.compile("a^")
                ParserPatterns.Articles.tags -> Pattern.compile("<a[^>]*?href=\"/tag/([^\"/]*?)/\"[^>]*?>([^<]*?)</a>")
                else -> throw IllegalArgumentException(key)
            }
        }
    }
}
