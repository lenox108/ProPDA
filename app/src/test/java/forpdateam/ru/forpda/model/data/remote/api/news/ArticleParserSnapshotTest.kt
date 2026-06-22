package forpdateam.ru.forpda.model.data.remote.api.news

import android.util.SparseArray
import forpdateam.ru.forpda.entity.remote.news.Comment
import forpdateam.ru.forpda.model.data.remote.ParserPatterns
import forpdateam.ru.forpda.model.data.storage.IPatternProvider
import forpdateam.ru.forpda.presentation.articles.detail.ArticleTemplate
import forpdateam.ru.forpda.ui.TemplateManager
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.regex.Pattern

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ArticleParserSnapshotTest {

    @Test
    fun articleFixture_documentsBodyPollAndComments() {
        val html = resource("parser/news/article_with_comments.html")
        val parser = ArticleParser(ArticlesPatternProviderStub())
        val article = parser.parseArticle(html)
        val comments = parser.parseComments(SparseArray<Comment.Karma>(), html)

        assertEquals("Snapshot article", article.title)
        assertTrue(article.html.orEmpty().contains("Article body"))
        assertTrue(article.html.orEmpty().contains("poll-ajax-frame-news"))
        assertEquals(1, comments.children.size)
        assertEquals("Commenter", comments.children.single().userNick)
        assertEquals("First comment", comments.children.single().content.orEmpty().trim())
    }

    @Test
    fun pollFixture_parserBuildsVoteModelAndTemplateKeepsPollAndVideo() {
        val html = resource("parser/news/article_with_poll.html")
        val parser = ArticleParser(ArticlesPatternProviderStub())
        val article = parser.parseArticle(html)

        // Parser produces the normalized poll model (question + all real options + vote form).
        val parsedBody = article.html.orEmpty()
        assertEquals(457102, article.id)
        assertTrue(parsedBody.contains("news-poll-normalized"))
        assertTrue(parsedBody.contains("name=\"answer[]\""))
        assertTrue(parsedBody.contains("value=\"7512\""))
        assertTrue(parsedBody.contains("value=\"7517\""))
        assertTrue(parsedBody.contains("До 10 000 руб."))
        assertTrue(parsedBody.contains("Не планирую покупать в этом году"))
        assertTrue(parsedBody.contains("poll_id=1335"))
        // Parser converts the inline YouTube iframe into a tappable inline video card.
        assertTrue(parsedBody.contains("news-video-card"))
        assertTrue(parsedBody.contains("data-video-play=\"true\""))

        // Security sanitizer (run inside the template) must NOT strip the trusted poll/video markup.
        val renderedHtml = articleTemplate().mapString(article)
        assertTrue(renderedHtml.contains("<form"))
        assertTrue(renderedHtml.contains("name=\"answer[]\""))
        assertTrue(renderedHtml.contains("value=\"7512\""))
        assertTrue(renderedHtml.contains("Проголосовать"))
        assertTrue(renderedHtml.contains("news-video-card"))
        assertTrue(renderedHtml.contains("data-video-play=\"true\""))
        assertTrue(renderedHtml.contains("youtube-nocookie.com/embed/dQw4w9WgXcQ"))
    }

    @Ignore("Pending ArticleParser data-site-poll rendering integration. " +
            "ArticleDataSitePollParser parses the JSON and NewsPollRenderer builds the " +
            "news-poll-normalized block, but the parser does not yet wire the two together " +
            "(see ArticleParser.kt:2125 — 'Lazy data-site-poll placeholders are not renderable').")
    @Test
    fun dataSitePollFixture_parsesLazyPollPayloadAndRendersBothQuestions() {
        // Reproduces p=457102 desktop save: poll-ajax-frame carries only data-site-poll JSON
        // (no rendered form / poll-list); site JS hydrates it in the browser.
        val html = resource("parser/news/article_with_data_site_poll.html")
        val parser = ArticleParser(ArticlesPatternProviderStub())
        val article = parser.parseArticle(html)
        val parsedBody = article.html.orEmpty()

        assertEquals(457102, article.id)
        assertTrue(parsedBody.contains("news-poll-normalized"))
        assertTrue(parsedBody.contains("name=\"answer[]\""))
        assertTrue(parsedBody.contains("Купил смартфон не более года назад"))
        assertTrue(parsedBody.contains("Планирую купить смартфон в этом году"))
        assertTrue(parsedBody.contains("value=\"7511\""))
        assertTrue(parsedBody.contains("value=\"7517\""))
        assertFalse(parsedBody.contains("data-site-poll"))
        assertTrue(parsedBody.contains("poll-ajax-frame-1334"))
        assertTrue(parsedBody.contains("poll-ajax-frame-1335"))
        assertEquals(2, parsedBody.split("data-normalized-poll", ignoreCase = true).size - 1)

        val renderedHtml = articleTemplate().mapString(article)
        assertTrue(renderedHtml.contains("<form"))
        assertTrue(renderedHtml.contains("Проголосовать"))
        assertTrue(renderedHtml.contains("Планирую купить смартфон в этом году"))
    }

    @Test
    fun pollFrameFixture_recognizesButtonDesignAndDoesNotLeakBoldHeading() {
        // Reproduces the on-device p=457102 failure: the "poll based on publication" (poll-frame)
        // design has no answer[] radios / ul.poll-list, so the classic detector missed it, the
        // <h3 class="poll-frame-title"> leaked as a bare bold heading and the <form> was stripped.
        val html = resource("parser/news/article_with_poll_frame.html")
        val parser = ArticleParser(ArticlesPatternProviderStub())
        val article = parser.parseArticle(html)
        val parsedBody = article.html.orEmpty()

        assertEquals(457102, article.id)
        // Poll is recognized and normalized (question + every option + vote control).
        assertTrue(parsedBody.contains("news-poll-normalized"))
        assertTrue(parsedBody.contains("Купил смартфон не более года назад"))
        assertTrue(parsedBody.contains("name=\"answer[]\""))
        assertTrue(parsedBody.contains("value=\"7512\""))
        assertTrue(parsedBody.contains("value=\"7515\""))
        assertTrue(parsedBody.contains("До 10 000 руб."))
        assertTrue(parsedBody.contains("Более 40 000 руб."))
        assertTrue(parsedBody.contains("poll_id=1335"))
        // The raw server-rendered poll-frame markup must be removed from the body (no leak / dupes).
        assertFalse(parsedBody.contains("poll-frame-title"))
        assertFalse(parsedBody.contains("poll-frame-option"))
        assertFalse(parsedBody.contains("class=\"poll-frame\""))

        // Through the template (incl. security sanitizer) the interactive poll survives.
        val renderedHtml = articleTemplate().mapString(article)
        assertTrue(renderedHtml.contains("<form"))
        assertTrue(renderedHtml.contains("name=\"answer[]\""))
        assertTrue(renderedHtml.contains("value=\"7512\""))
        assertTrue(renderedHtml.contains("Проголосовать"))
        assertFalse(renderedHtml.contains("poll-frame-title"))
    }

    @Test
    fun haierFixture_parsesArticleBodyAndRendersWithoutDuplicateHero() {
        val html = resource("parser/news/haier_w3_article.html")
        val article = ArticleParser(ArticlesPatternProviderStub()).parseArticle(html)
        val renderedHtml = articleTemplate().mapString(article)

        assertEquals(456862, article.id)
        assertEquals("Представлен роботизированный экзоскелет Haier W3 весом менее 2 кг и со встроенным ИИ", article.title)
        assertEquals(5, article.commentsCount)
        assertNull(article.imgUrl)
        assertTrue(article.html.orEmpty().contains("Haier выпустила новое носимое"))
        assertTrue(renderedHtml.contains("Haier выпустила новое носимое"))
    }

    @Test
    fun commentActionsFixture_documentsModerationLinkCorpus() {
        val html = resource("parser/news/article_comment_actions.html")
        assertTrue(html.contains("comment-edit"))
        assertTrue(html.contains("comment-delete"))
        assertTrue(html.contains("action=editcomment&amp;c=42"))
        assertTrue(html.contains("action=deletecomment&amp;c=42"))
        assertTrue(html.contains("Moderated comment body"))
    }

    @Test
    fun haierFixture_parsesInlineCommentsAndRealActions() {
        val html = resource("parser/news/haier_w3_article.html")
        val comments = ArticleParser(ArticlesPatternProviderStub())
                .parseComments(SparseArray<Comment.Karma>(), html)
        val first = comments.children.first()

        assertEquals(5, comments.children.size)
        assertEquals(10597307, first.id)
        assertEquals(2221326, first.userId)
        assertEquals("sche91", first.userNick)
        assertTrue(first.content.orEmpty().contains("-Алло? Это кто?"))
        assertEquals(Comment.Action.Type.REPLY, first.actions.reply?.type)
        assertEquals("10597307", first.actions.reply?.fields?.get("comment_reply_ID"))
        assertEquals("https://4pda.to/pages/karma?p=456862&c=10597307&v=1", first.likeAction?.url)
        assertEquals("https://4pda.to/pages/karma?p=456862&c=10597307&v=0", first.unlikeAction?.url)
        assertEquals(Comment.Action.Type.COMMENT_LIKE, first.likeAction?.type)
        assertEquals(Comment.Action.Type.COMMENT_UNLIKE, first.unlikeAction?.type)
        assertEquals(Comment.Action.Type.HIDE, first.actions.hide?.type)
        assertEquals("https://4pda.to/forum/index.php?act=rep&view=win_add&mid=2221326&c=10597307", first.actions.reputationPlus?.url)
        assertEquals("add", first.actions.reputationPlus?.fields?.get("type"))
        assertEquals("2221326", first.actions.reputationPlus?.fields?.get("mid"))
        assertEquals(Comment.Action.Type.REPUTATION_MINUS, first.actions.reputationMinus?.type)
        assertEquals("minus", first.actions.reputationMinus?.fields?.get("type"))
        assertEquals(Comment.Action.Type.REPORT, first.actions.report?.type)
        assertNull(first.actions.edit)
        assertNull(first.actions.delete)
        assertNull(first.actions.karmaPlus)
    }

    @Test
    fun articleV3_prefersCurrentArticleCommentsCountOverSidebarLinks() {
        val html = """
            <html><head>
              <meta property="article:id" content="457253">
              <meta property="og:title" content="Messenger removed from App Store">
              <script type="application/ld+json">{"@type":"NewsArticle","commentCount":110}</script>
            </head><body>
              <aside class="popular">
                <a href="/index.php?p=111#comments">3</a>
              </aside>
              <article class="article">
                <div class="article-header">
                  <h1>Messenger removed from App Store</h1>
                  <a class="comment-count" href="#comments">110</a>
                </div>
                <div class="entry-content"><p>Article body</p></div>
              </article>
              <div id="comments"><ul class="comment-list"></ul></div>
            </body></html>
        """.trimIndent()
        val article = ArticleParser(ArticlesPatternProviderStub()).parseArticle(html, ArticleParsePhase.FIRST_RENDER)

        assertEquals(457253, article.id)
        assertEquals(110, article.commentsCount)
    }

    @Test
    fun articleCountExtractor_prefersLargestCurrentCountOverFirstCommentsAnchor() {
        val html = """
            <html><head>
              <meta property="article:id" content="457253">
              <meta property="og:title" content="Messenger removed from App Store">
              <meta property="article:comments_count" content="110">
            </head><body>
              <aside class="popular"><a href="/index.php?p=111#comments">3</a></aside>
              <div class="article">
                <header class="article-header">
                  <h1>Messenger removed from App Store</h1>
                  <a href="#comments">3</a>
                </header>
                <div class="article-meta-comment"><a href="#comments">110</a></div>
                <div class="entry-content"><p>Article body</p></div>
              </div>
            </body></html>
        """.trimIndent()
        val article = ArticleParser(ArticlesPatternProviderStub()).parseArticle(html, ArticleParsePhase.FIRST_RENDER)

        assertEquals(457253, article.id)
        assertEquals(110, article.commentsCount)
    }

    @Test
    fun articleCountExtractor_prefersScopedAnchorWhenJsonLdInflated() {
        val html = """
            <html><head>
              <meta property="article:id" content="457253">
              <meta property="og:title" content="Messenger removed from App Store">
              <script type="application/ld+json">{"@type":"NewsArticle","commentCount":245}</script>
            </head><body>
              <div class="article">
                <header class="article-header">
                  <h1>Messenger removed from App Store</h1>
                  <a class="comment-count" href="#comments">165</a>
                </header>
                <div class="entry-content"><p>Article body</p></div>
              </div>
            </body></html>
        """.trimIndent()
        val article = ArticleParser(ArticlesPatternProviderStub()).parseArticle(html, ArticleParsePhase.FIRST_RENDER)

        assertEquals(457253, article.id)
        assertEquals(165, article.commentsCount)
    }

    @Test
    fun articleCountExtractor_prefersScopedAnchorWhenJsonLdAndVCountInflated() {
        val html = """
            <html><head>
              <meta property="article:id" content="457900">
              <meta property="og:title" content="MacBook Neo">
              <script type="application/ld+json">{"@type":"NewsArticle","commentCount":57}</script>
            </head><body>
              <div class="article">
                <header class="article-header">
                  <h1>MacBook Neo</h1>
                  <a href="#comments">27</a>
                </header>
                <div class="entry-content"><p>Article body</p></div>
              </div>
              <a class="v-count">57</a>
            </body></html>
        """.trimIndent()
        val parser = ArticleParser(ArticlesPatternProviderStub())
        val article = parser.parseArticle(html, ArticleParsePhase.FIRST_RENDER)

        assertEquals(457900, article.id)
        assertEquals(27, article.commentsCount)
        assertEquals(27, parser.resolveListItemCommentsCount(html))
    }

    @Test
    fun parseArticlesFallback_prefersScopedAnchorOverJsonLdAndVCount() {
        val html = """
            <article class="post" itemid="457900">
                <a href="https://4pda.to/2026/06/04/457900/macbook-neo/" title="MacBook Neo"></a>
                <script type="application/ld+json">{"@type":"NewsArticle","commentCount":57}</script>
                <header class="article-header"><a href="#comments">27</a></header>
                <img itemprop="image" src="https://4pda.to/wp-content/uploads/test.jpg" />
                <a class="v-count">57</a>
                <em class="date">04.06.2026</em>
                <a href="https://4pda.to/forum/index.php?showuser=1">News</a>
                <div itemprop="description"><p>Description</p></div>
            </article>
        """.trimIndent()

        val news = ArticleParser(ArticlesPatternProviderStub()).parseArticles(html)

        assertEquals(457900, news.single().id)
        assertEquals(27, news.single().commentsCount)
    }

    private fun resource(path: String): String {
        return javaClass.classLoader?.getResource(path)?.readText()
                ?: error("Missing test resource $path")
    }

    private class DetailV2PatternProviderStub : IPatternProvider {
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
                ParserPatterns.Articles.detail_detector -> Pattern.compile(
                        "(<[^>]*>[^<]*?<[^>]*?>[^<]*?<div[^>]*?data-ztm=\"\\d+:\\d+[^\"]*?\"[^>]*?>[^<]*?<meta[^>]*?content=\"[^\"]*?\"[^>]*?><link[^>]*?><div class=\"[^\"]*?article[^\"]*?\"[^>]*?>)",
                        Pattern.CASE_INSENSITIVE or Pattern.DOTALL
                )
                ParserPatterns.Articles.detail_v2 -> Pattern.compile(
                        "<[^>]*>[^<]*?<[^>]*?>[^<]*?<div[^>]*?data-ztm=\"\\d+:(\\d+)[^\"]*?\"[^>]*?>[^<]*?<meta[^>]*?content=\"([^\"]*?)\"[^>]*?>[\\s\\S]*?<div class=\"[^\"]*?article[^\"]*?\"[^>]*?><div class=\"[^\"]*?article-header[^\"]*?\"[^>]*?>(?:<h1>)?([^<]*?)(?:<\\/h1>)[\\s\\S]*?<time[^>]*?>([^<]*?)<\\/time>[\\s\\S]*?<a[^>]*?href=\"#comments\"[^>]*?>(\\d+)<\\/a>[\\s\\S]*?(<meta property=\"og:description\"[\\s\\S]*?)<div class=\"article-footer[^\"]*?\"[^>]*?>[\\s\\S]*?(?:<div class=\"article-footer-tags[^\"]*?\"[^>]*?>([\\s\\S]*?)<\\/div>)?<\\/div>[^<]*?<\\/div>[^<]*?<\\/div>[^<]*?<\\/\\w+>[\\s\\S]*?(?:[^<]*?<div class=\"materials-box\"[^>]*?>(?:[\\s\\S]*?<ul class=\"materials-slider\"[^>]*?>([\\s\\S]*?)<\\/ul>)?[^<]*?<\\/div>)?([\\s\\S]*?)(?:[\\s\\S]*?<div class=\"comment-box[^\"]*?\" id=\"comments\"[^>]*?>[\\s\\S]*?(<ul class=\"comment-list[\\s\\S]*?<\\/ul>)(?:<form|<\\/div><\\/div><article))",
                        Pattern.CASE_INSENSITIVE
                )
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

    private fun articleTemplate(): ArticleTemplate {
        val templateManager = mockk<TemplateManager>()
        every { templateManager.getTemplate(TemplateManager.TEMPLATE_NEWS) } returns
                biz.source_code.miniTemplator.MiniTemplator.Builder().build(
                        (File("src/main/assets/template_news.html").takeIf { it.isFile }
                                ?: File("app/src/main/assets/template_news.html")).inputStream(),
                        Charsets.UTF_8
                )
        every { templateManager.fillStaticStrings(any()) } answers { firstArg() }
        every { templateManager.getThemeType() } returns "light"
        every { templateManager.getThemeOverridesCss() } returns ""
        every { templateManager.getStaticString("res_s_comments") } returns "Комментарии"
        every { templateManager.getStaticString("news_inline_comments_description") } returns "Комментарии откроются прямо под статьей"
        every { templateManager.getStaticString("news_inline_comments_show") } returns "Показать"
        every { templateManager.getStaticString("news_inline_comments_hide") } returns "Скрыть"
        every { templateManager.getStaticString("news_show_comments") } returns "Показать комментарии"
        every { templateManager.getStaticString("retry") } returns "Повторить"
        return ArticleTemplate(templateManager)
    }

    @Test
    fun commentsFastExtractor_doesNotTruncateOnNestedLists() {
        val html = """
            <html><head>
              <meta property="og:title" content="Article">
              <meta property="article:id" content="123">
            </head><body>
            <div class="entry-content"><p>Body</p></div>
            <ul class="comment-list">
              <li><div id="comment-1" class="comment">
                <a class="comment-avatar" href="https://4pda.to/forum/index.php?showuser=11">x</a>
                <a class="nickname">User1</a>
                <a class="date">today</a>
                <div class="content">
                  First with list:
                  <ul><li>inner</li></ul>
                </div>
              </div></li>
              <li><div id="comment-2" class="comment">
                <a class="comment-avatar" href="https://4pda.to/forum/index.php?showuser=22">x</a>
                <a class="nickname">User2</a>
                <a class="date">today</a>
                <p class="content">Second</p>
              </div></li>
            </ul>
            </body></html>
        """.trimIndent()
        val parser = ArticleParser(ArticlesPatternProviderStub())
        val article = parser.parseArticle(html, ArticleParsePhase.FULL)
        val comments = parser.parseComments(SparseArray<Comment.Karma>(), article.commentsSource)

        assertEquals(2, comments.children.size)
        assertEquals(1, comments.children[0].id)
        assertTrue(comments.children[0].content.orEmpty().contains("inner"))
        assertEquals(2, comments.children[1].id)
        assertTrue(comments.children[1].content.orEmpty().contains("Second"))
    }

    @Test
    fun firstRender_skipsEmbeddedCommentListForLazyLoad() {
        val html = resource("parser/news/article_with_comments.html")
        val parser = ArticleParser(ArticlesPatternProviderStub())
        val firstRender = parser.parseArticle(html, ArticleParsePhase.FIRST_RENDER)
        val full = parser.parseArticle(html, ArticleParsePhase.FULL)

        assertTrue(full.commentsSource.orEmpty().contains("comment-list"))
        assertTrue(firstRender.commentsSource.isNullOrBlank())
    }

    @Test
    fun firstRender_prefersDetailRegexBodyWithoutFullDomWalk() {
        val body = "<p>Текст статьи для быстрого first-render без DOM.</p>"
        val padding = "x".repeat(300_000)
        val html = """
            <html><head>
              <meta property="article:id" content="457355">
              <meta property="og:title" content="Alien Isolation 2">
            </head><body>
              $padding
              <div data-ztm="1:457355">
                <meta content="https://4pda.to/img.jpg">
                <div class="article">
                  <div class="article-header">
                    <h1>Alien Isolation 2</h1>
                    <time>06.06.2026</time>
                    <a href="#comments">9</a>
                  </div>
                  <meta property="og:description" content="Lead">
                  <div class="entry-content">$body</div>
                  <div class="article-footer"></div>
                </div>
              </div>
            </body></html>
        """.trimIndent()
        val parser = ArticleParser(DetailV2PatternProviderStub())
        val startedAt = android.os.SystemClock.elapsedRealtime()
        val article = parser.parseArticle(html, ArticleParsePhase.FIRST_RENDER)
        val elapsedMs = android.os.SystemClock.elapsedRealtime() - startedAt

        assertEquals(457355, article.id)
        assertTrue(article.html.orEmpty().contains("Alien Isolation 2") || article.html.orEmpty().contains(body))
        assertTrue("FIRST_RENDER must stay well under DOM-parse budget, was ${elapsedMs}ms", elapsedMs < 800)
    }

    @Test
    fun firstRender_normalizesPollFromMobileResponse() {
        val html = resource("parser/news/article_with_poll.html")
        val parser = ArticleParser(ArticlesPatternProviderStub())
        val article = parser.parseArticle(html, ArticleParsePhase.FIRST_RENDER)
        val body = article.html.orEmpty()

        assertTrue(body.contains("news-poll-normalized"))
        assertTrue(body.contains("name=\"answer[]\""))
    }
}
