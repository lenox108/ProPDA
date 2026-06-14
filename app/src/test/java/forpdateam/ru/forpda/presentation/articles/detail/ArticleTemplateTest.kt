package forpdateam.ru.forpda.presentation.articles.detail

import biz.source_code.miniTemplator.MiniTemplator
import forpdateam.ru.forpda.entity.remote.news.DetailsPage
import forpdateam.ru.forpda.ui.TemplateManager
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ArticleTemplateTest {

    @Test
    fun `restampCommentsCountInMappedHtml updates footer and header totals`() {
        val template = articleTemplate()
        val mapped = template.mapString(DetailsPage().apply {
            title = "Article"
            author = "News"
            date = "01.01.2026"
            html = "<p>Body</p>"
            commentsCount = 20
        })
        val restamped = template.restampCommentsCountInMappedHtml(mapped, 33).orEmpty()
        assertTrue(restamped.contains("data-comments-count=\"33\""))
        assertTrue(restamped.contains("Комментарии (33)"))
        assertTrue(restamped.contains("News · 33 · 01.01.2026"))
        assertFalse(restamped.contains("Комментарии (20)"))
        assertFalse(restamped.contains("data-comments-count=\"20\""))
    }

    @Test
    fun `news comments footer uses reconciled count in header meta`() {
        val html = articleTemplate().mapString(DetailsPage().apply {
            title = "Article"
            author = "News"
            date = "01.01.2026"
            this.html = "<p>Body</p>"
            commentsCount = 27
        })

        assertTrue(html.contains("news-detail-header-meta"))
        assertTrue(html.contains("News · 27 · 01.01.2026") || html.contains(">27<"))
    }

    @Test
    fun `news comments footer renders manual toggle section`() {
        val html = articleTemplate().mapString(DetailsPage().apply {
            title = "Article"
            this.html = "<p>Body</p>"
            commentsCount = 8
        })

        assertEquals(1, "id=\"news-comments-section\"".toRegex().findAll(html).count())
        assertTrue(html.contains("class=\"news-comments-section\""))
        assertFalse(html.contains("data-native-toggle"))
        assertTrue(html.contains("data-comments-count=\"8\""))
        assertTrue(html.contains("id=\"news-comments-toggle\""))
        assertTrue(html.contains("class=\"news-comments-toggle-title\""))
        assertTrue(html.contains("class=\"news-comments-toggle-action\""))
        assertTrue(html.contains("INews.onCommentsSectionTapReceived('toggle_expand')"))
        assertTrue(html.contains("newsInlineCommentsHandleToggleFromNativeButton"))
        assertTrue(html.contains("id=\"news-inline-comments-status\""))
        assertTrue(html.contains("id=\"news-inline-comments-retry\""))
        assertTrue(html.contains("id=\"news-inline-comments-list\""))
        assertTrue(html.contains("id=\"news-comments-body\""))
        assertTrue(html.contains("hidden"))
        assertFalse(html.contains("onclick=\"INews.toComments()\""))
    }

    @Test
    fun `news comments footer uses fallback labels when missing`() {
        val html = articleTemplate(
                commentsLabel = "",
                showCommentsLabel = ""
        ).mapString(DetailsPage().apply {
            title = "Article"
            this.html = "<p>Body</p>"
            commentsCount = 8
        })

        assertTrue(html.contains("class=\"news-comments-section\""))
        assertTrue(html.contains("data-comments-count=\"8\""))
        assertTrue(html.contains("data-label-show=\"Показать\""))
        assertTrue(html.contains("data-label-hide=\"Скрыть\""))
    }

    @Test
    fun `news comments footer renders when count is zero without source`() {
        val html = articleTemplate().mapString(DetailsPage().apply {
            title = "Article"
            this.html = "<p>Body</p>"
            commentsCount = 0
        })

        assertEquals(1, "id=\"news-comments-section\"".toRegex().findAll(html).count())
        assertTrue(html.contains("class=\"news-comments-toggle-action\""))
        assertFalse(html.contains("Комментарии 0"))
    }

    @Test
    fun `news comments footer renders when count unknown`() {
        val html = articleTemplate().mapString(DetailsPage().apply {
            title = "Article"
            this.html = "<p>Body</p>"
            commentsCount = -1
        })

        assertEquals(1, "id=\"news-comments-section\"".toRegex().findAll(html).count())
        assertFalse(html.contains("data-native-toggle"))
        assertTrue(html.contains("data-collapsed=\"true\""))
        assertTrue(html.contains("id=\"news-comments-toggle\""))
        assertTrue(html.contains("Комментарии (?)"))
    }

    @Test
    fun `news comments footer renders when comments source present with zero count`() {
        val html = articleTemplate().mapString(DetailsPage().apply {
            title = "Article"
            this.html = "<p>Body</p>"
            commentsCount = 0
            commentsSource = "<ul class=\"comment-list\"></ul>"
        })

        assertEquals(1, "id=\"news-comments-section\"".toRegex().findAll(html).count())
    }

    @Test
    fun `remapWithCurrentTheme skips rebuild when theme cache key matches`() {
        val template = articleTemplate()
        val mapped = template.mapString(DetailsPage().apply {
            title = "Article"
            html = "<p>Body with enough text for article open pipeline checks.</p>"
            commentsCount = 0
        })
        assertTrue(mapped.contains("data-fpda-theme-key="))

        val remapped = template.remapWithCurrentTheme(DetailsPage().apply {
            id = 1
            title = "Article"
            html = mapped
            commentsCount = 0
        })

        assertEquals(mapped, remapped.html)
    }

    @Test
    fun `remapWithCurrentTheme rebuilds stylesheet links for light palette`() {
        val darkMapped = articleTemplate(isNight = true).mapString(DetailsPage().apply {
            title = "Article"
            html = """<p style="background-color:#212121">Body</p>"""
            commentsCount = 0
        })

        val remapped = articleTemplate(isNight = false).remapWithCurrentTheme(DetailsPage().apply {
            id = 457156
            title = "Article"
            html = darkMapped
            commentsCount = 0
        })

        assertTrue(remapped.html.orEmpty().contains("/styles/light/light_"))
        assertFalse(remapped.html.orEmpty().contains("/styles/dark/dark_"))
        assertFalse(remapped.html.orEmpty().contains("#212121"))
    }

    @Test
    fun `news template loads news scripts synchronously in head`() {
        val templateFile = sequenceOf(
                File("src/main/assets/template_news.html"),
                File("app/src/main/assets/template_news.html")
        ).firstOrNull { it.exists() }
                ?: error("template_news.html not found")
        val template = templateFile.readText()

        assertTrue(template.contains("type=\"text/javascript\" src=\"file:///android_asset/forpda/scripts/main.js\""))
        assertTrue(template.contains("type=\"text/javascript\" src=\"file:///android_asset/forpda/scripts/modules/news.js\""))
        assertFalse(template.contains("defer type=\"text/javascript\" src=\"file:///android_asset/forpda/scripts/modules/news.js\""))
        assertTrue(template.contains("defer type=\"text/javascript\" src=\"file:///android_asset/forpda/scripts/z_emoticons.js\""))
    }

    private fun articleTemplate(
            commentsLabel: String = "Комментарии",
            showCommentsLabel: String = "Показать комментарии",
            commentsDescription: String = "Комментарии откроются прямо под статьей",
            isNight: Boolean = false
    ): ArticleTemplate {
        val templateManager = mockk<TemplateManager>()
        every { templateManager.getTemplate(TemplateManager.TEMPLATE_NEWS) } returns
                MiniTemplator.Builder()
                        .setSkipUndefinedVars(true)
                        .build(newsTemplateFile().inputStream(), Charsets.UTF_8)
        every { templateManager.fillStaticStrings(any()) } answers { firstArg() }
        every { templateManager.getThemeType() } returns if (isNight) "dark" else "light"
        every { templateManager.getThemeOverridesCss() } returns ""
        every { templateManager.getStaticString("res_s_comments") } returns commentsLabel
        every { templateManager.getStaticString("news_inline_comments_description") } returns commentsDescription
        every { templateManager.getStaticString("news_inline_comments_show") } returns showCommentsLabel
        every { templateManager.getStaticString("news_inline_comments_hide") } returns "Скрыть"
        every { templateManager.getStaticString("retry") } returns "Повторить"
        return ArticleTemplate(templateManager)
    }

    private fun newsTemplateFile(): File =
            File("src/main/assets/template_news.html").takeIf { it.isFile }
                    ?: File("app/src/main/assets/template_news.html")
}
