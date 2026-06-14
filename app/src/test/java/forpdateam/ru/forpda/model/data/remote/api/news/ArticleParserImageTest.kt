package forpdateam.ru.forpda.model.data.remote.api.news

import forpdateam.ru.forpda.model.data.remote.ParserPatterns
import forpdateam.ru.forpda.model.data.remote.IWebClient
import forpdateam.ru.forpda.model.data.remote.api.NetworkRequest
import forpdateam.ru.forpda.model.data.remote.api.NetworkResponse
import forpdateam.ru.forpda.model.data.storage.IPatternProvider
import forpdateam.ru.forpda.presentation.articles.detail.ArticleTemplate
import forpdateam.ru.forpda.ui.TemplateManager
import forpdateam.ru.forpda.entity.remote.news.DetailsPage
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.regex.Pattern
import kotlinx.coroutines.runBlocking

class ArticleParserImageTest {

    private open class ArticlesPatternProviderStub : IPatternProvider {
        override fun getCurrentVersion(): Int = 29
        override fun update(jsonString: String) {}
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
                ParserPatterns.Articles.karmaSource -> Pattern.compile("a^")
                ParserPatterns.Articles.tags -> Pattern.compile("<a[^>]*?href=\"/tag/([^\"/]*?)/\"[^>]*?>([^<]*?)</a>")
                else -> throw IllegalArgumentException(key)
            }
        }
    }

    private class RuntimeArticlesPatternProviderStub : IPatternProvider {
        override fun getCurrentVersion(): Int = 29
        override fun update(jsonString: String) {}
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
                        "(<[^>]*>[^<]*?<[^>]*?>[^<]*?<div[^>]*?data-ztm=\"\\d+:\\d+[^\"]*?\"[^>]*?>[^<]*?<meta[^>]*?content=\"[^\"]*?\"[^>]*?><div[^>]*><div[^>]*?class=\"photo\"[^>]*?>)|" +
                                "(<[^>]*>[^<]*?<[^>]*?>[^<]*?<div[^>]*?data-ztm=\"\\d+:\\d+[^\"]*?\"[^>]*?>[^<]*?<meta[^>]*?content=\"[^\"]*?\"[^>]*?><link[^>]*?><div class=\"[^\"]*?article[^\"]*?\"[^>]*?>)|" +
                                "(<meta[^>]*?property=\"og:description\"[^>]*>[^<]*<meta[^>]*?property=\"og:site_name\"[^>]*>.*?<div[^>]*?class=\"[^\"]*?(?:article|content|post|entry)[^\"]*?\"[^>]*>)|" +
                                "(<div[^>]*?class=\"[^\"]*?(?:articleBody|article-body|article-content|entry-content|post-content|content-body)[^\"]*?\"[^>]*>)",
                        Pattern.CASE_INSENSITIVE or Pattern.DOTALL
                )
                ParserPatterns.Articles.detail_v2 -> Pattern.compile(
                        "<[^>]*>[^<]*?<[^>]*?>[^<]*?<div[^>]*?data-ztm=\"\\d+:(\\d+)[^\"]*?\"[^>]*?>[^<]*?<meta[^>]*?content=\"([^\"]*?)\"[^>]*?>[\\s\\S]*?<div class=\"[^\"]*?article[^\"]*?\"[^>]*?><div class=\"[^\"]*?article-header[^\"]*?\"[^>]*?>(?:<h1>)?([^<]*?)(?:<\\/h1>)[\\s\\S]*?<time[^>]*?>([^<]*?)<\\/time>[\\s\\S]*?<a[^>]*?href=\"#comments\"[^>]*?>(\\d+)<\\/a>[\\s\\S]*?(<meta property=\"og:description\"[\\s\\S]*?)<div class=\"article-footer[^\"]*?\"[^>]*?>[\\s\\S]*?(?:<div class=\"article-footer-tags[^\"]*?\"[^>]*?>([\\s\\S]*?)<\\/div>)?<\\/div>[^<]*?<\\/div>[^<]*?<\\/div>[^<]*?<\\/\\w+>[\\s\\S]*?(?:[^<]*?<div class=\"materials-box\"[^>]*?>(?:[\\s\\S]*?<ul class=\"materials-slider\"[^>]*?>([\\s\\S]*?)<\\/ul>)?[^<]*?<\\/div>)?([\\s\\S]*?)(?:[\\s\\S]*?<div class=\"comment-box[^\"]*?\" id=\"comments\"[^>]*?>[\\s\\S]*?(<ul class=\"comment-list[\\s\\S]*?<\\/ul>)(?:<form|<\\/div><\\/div><article))",
                        Pattern.CASE_INSENSITIVE
                )
                ParserPatterns.Articles.list -> Pattern.compile("a^")
                ParserPatterns.Articles.exclude_form_comment -> Pattern.compile("<form[\\s\\S]*", Pattern.CASE_INSENSITIVE)
                ParserPatterns.Articles.karmaSource -> Pattern.compile("a^")
                ParserPatterns.Articles.tags -> Pattern.compile("<a[^>]*?href=\"/tag/([^\"/]*?)/\"[^>]*?>([^<]*?)</a>")
                ParserPatterns.Articles.materials -> Pattern.compile("a^")
                else -> throw IllegalArgumentException(key)
            }
        }
    }

    private class PollFallbackWebClient(
            private val mobileBody: String,
            private val desktopBody: String
    ) : IWebClient {
        val requestedUrls = mutableListOf<String>()
        val desktopRequestHeaders = mutableListOf<Map<String, String>>()
        var desktopRequests = 0

        override fun get(url: String): NetworkResponse {
            requestedUrls += url
            return NetworkResponse(url = url, body = mobileBody)
        }

        override fun request(request: NetworkRequest): NetworkResponse {
            requestedUrls += request.url
            return NetworkResponse(url = request.url, body = mobileBody)
        }

        override fun request(request: NetworkRequest, progressListener: IWebClient.ProgressListener?): NetworkResponse =
                request(request)

        override fun requestWithoutMobileCookie(request: NetworkRequest): NetworkResponse {
            desktopRequests++
            requestedUrls += request.url
            desktopRequestHeaders += request.headers.orEmpty()
            return NetworkResponse(url = request.url, body = desktopBody)
        }

        override fun getAuthKey(): String = "0"
        override fun getClientCookies(): Map<String, okhttp3.Cookie> = emptyMap()
        override fun clearCookies() = Unit
        override fun createWebSocketConnection(webSocketListener: okhttp3.WebSocketListener): okhttp3.WebSocket {
            throw UnsupportedOperationException()
        }
    }

    private fun assertFalse(condition: Boolean) {
        assertTrue(!condition)
    }

    @Test
    fun newsApi_doesNotFetchDesktopPollForRegularArticle() {
        val mobileHtml = """
            <html><head>
                <meta property="og:title" content="Regular news">
                <meta property="article:id" content="456700">
            </head><body>
                <div class="article">
                    <div class="article-header"><h1>Regular news</h1><time>19.05.2026</time><a href="#comments">0</a></div>
                    <div class="entry-content"><p>Regular article body without poll.</p></div>
                    <div class="article-footer"></div>
                </div>
            </body></html>
        """.trimIndent()
        val webClient = PollFallbackWebClient(mobileHtml, "<html></html>")

        val article = NewsApi(webClient, ArticleParser(ArticlesPatternProviderStub())).getDetails(456700)

        assertEquals(listOf("https://4pda.to/index.php?p=456700"), webClient.requestedUrls)
        assertEquals(0, webClient.desktopRequests)
        assertTrue(article.html.orEmpty().contains("Regular article body without poll."))
    }

    @Test
    fun parseArticlesFallback_prefersMediumSrcsetImageOverSmallSrc() {
        val html = """
            <article class="post" itemid="42">
                <a href="https://4pda.to/2026/05/18/42/test-news/" title="Test news"></a>
                <img itemprop="image"
                     src="https://4pda.to/wp-content/uploads/test-300x168.jpg"
                     srcset="https://4pda.to/wp-content/uploads/test-300x168.jpg 300w,
                             https://4pda.to/wp-content/uploads/test-768x430.jpg 768w,
                             https://4pda.to/wp-content/uploads/test-1536x860.jpg 1536w" />
                <a class="v-count">3</a>
                <em class="date">18.05.2026</em>
                <a href="https://4pda.to/forum/index.php?showuser=1">News</a>
                <div itemprop="description"><p>Description</p></div>
            </article>
        """.trimIndent()

        val news = ArticleParser(ArticlesPatternProviderStub()).parseArticles(html)

        assertEquals("https://4pda.to/wp-content/uploads/test-768x430.jpg", news.single().imgUrl)
    }

    @Test
    fun parseArticlesFallback_usesLazyImageWhenSrcsetMissing() {
        val html = """
            <article class="post" itemid="43">
                <a href="https://4pda.to/2026/05/18/43/test-news/" title="Test news"></a>
                <img itemprop="image"
                     src="https://4pda.to/wp-content/uploads/placeholder.jpg"
                     data-original="https://4pda.to/wp-content/uploads/test-768x430.jpg" />
                <a class="v-count">3</a>
                <em class="date">18.05.2026</em>
                <a href="https://4pda.to/forum/index.php?showuser=1">News</a>
                <div itemprop="description"><p>Description</p></div>
            </article>
        """.trimIndent()

        val news = ArticleParser(ArticlesPatternProviderStub()).parseArticles(html)

        assertEquals("https://4pda.to/wp-content/uploads/test-768x430.jpg", news.single().imgUrl)
    }

    @Test
    fun parseArticleV3_appendsPollFrameOutsideArticleBody() {
        val html = """
            <html><head>
                <meta property="og:title" content="Poll news">
                <meta property="og:image" content="https://4pda.to/image.jpg">
            </head><body>
                <div class="article">
                    <div class="article-header"><h1>Poll news</h1><time>19.05.2026</time><a href="#comments">0</a></div>
                    <div class="entry-content"><p>Article body</p></div>
                    <div class="article-footer"></div>
                </div>
                <div id="poll-ajax-frame-77">
                    <h2><strong>Опрос</strong></h2>
                    <form action="https://4pda.to/pages/poll/?act=vote&amp;poll_id=77" method="post">
                        <input type="hidden" name="from" value="/index.php?p=123">
                        <ul class="poll-list">
                            <li><label class="text"><input type="radio" name="answer[]" value="1"> Да</label></li>
                            <li><label class="text"><input type="radio" name="answer[]" value="2"> Нет</label></li>
                        </ul>
                        <button type="submit" class="btn">Голосовать</button>
                    </form>
                </div>
            </body></html>
        """.trimIndent()

        val article = ArticleParser(RuntimeArticlesPatternProviderStub()).parseArticle(html)

        assertTrue(article.html.orEmpty().contains("Article body"))
        assertTrue(article.html.orEmpty().contains("poll-ajax-frame-77"))
        assertTrue(article.html.orEmpty().contains("answer[]"))
    }

    @Test
    fun parseArticleV3_preservesLeadBeforeFirstImage() {
        val html = """
            <html><head>
                <meta property="og:title" content="Lead news">
                <meta property="og:image" content="https://4pda.to/hero.jpg">
                <meta property="article:id" content="456701">
            </head><body>
                <div class="article">
                    <div class="article-header"><h1>Lead news</h1><time>20.05.2026</time><a href="#comments">0</a></div>
                    <div class="article__lead">
                        <p>Lead paragraph that must stay before the first image.</p>
                    </div>
                    <figure class="article__image">
                        <img src="https://4pda.to/hero.jpg" alt="Hero">
                        <figcaption>Hero caption</figcaption>
                    </figure>
                    <div class="entry-content">
                        <p>Body paragraph after image.</p>
                    </div>
                    <div class="article-footer"></div>
                </div>
            </body></html>
        """.trimIndent()

        val article = ArticleParser(ArticlesPatternProviderStub()).parseArticle(html)
        val parsedHtml = article.html.orEmpty()

        val leadIndex = parsedHtml.indexOf("Lead paragraph that must stay before the first image.")
        val imageIndex = parsedHtml.indexOf("https://4pda.to/hero.jpg")
        val bodyIndex = parsedHtml.indexOf("Body paragraph after image.")
        assertTrue(leadIndex >= 0)
        assertTrue(imageIndex > leadIndex)
        assertTrue(bodyIndex > imageIndex)
        assertEquals(leadIndex, parsedHtml.lastIndexOf("Lead paragraph that must stay before the first image."))
        assertEquals(null, article.imgUrl)
    }

    @Test
    fun parseArticleV3_usesOgImageAsHeroWhenBodyStartsWithText() {
        val html = """
            <html><head>
                <meta property="og:title" content="Hero news">
                <meta property="og:image" content="https://4pda.to/hero.jpg">
            </head><body>
                <div class="article">
                    <div class="article-header"><h1>Hero news</h1><time>20.05.2026</time><a href="#comments">0</a></div>
                    <div class="entry-content">
                        <p>Lead body before any image.</p>
                        <p>More text.</p>
                    </div>
                    <div class="article-footer"></div>
                </div>
            </body></html>
        """.trimIndent()

        val article = ArticleParser(ArticlesPatternProviderStub()).parseArticle(html)
        val renderedHtml = articleTemplate().mapString(article)

        assertEquals("https://4pda.to/hero.jpg", article.imgUrl)
        assertFalse(article.html.orEmpty().contains("https://4pda.to/hero.jpg"))
        assertTrue(renderedHtml.contains("class=\"news-detail-header-image app-stable-media\""))
        assertTrue(renderedHtml.contains("src=\"https://4pda.to/hero.jpg\""))
        assertTrue(renderedHtml.indexOf("https://4pda.to/hero.jpg") < renderedHtml.indexOf("Lead body before any image."))
    }

    @Test
    fun parseArticleV3_doesNotDuplicateHeroAlreadyInFirstBodyImage() {
        val html = """
            <html><head>
                <meta property="og:title" content="Hero news">
                <meta property="og:image" content="https://4pda.to/wp-content/uploads/hero.jpg">
            </head><body>
                <div class="article">
                    <div class="article-header"><h1>Hero news</h1><time>20.05.2026</time><a href="#comments">0</a></div>
                    <div class="entry-content">
                        <figure><img src="https://4pda.to/wp-content/uploads/hero-768x430.jpg" alt="Hero"></figure>
                        <p>Body.</p>
                    </div>
                    <div class="article-footer"></div>
                </div>
            </body></html>
        """.trimIndent()

        val article = ArticleParser(ArticlesPatternProviderStub()).parseArticle(html)
        val renderedHtml = articleTemplate().mapString(article)

        assertEquals(null, article.imgUrl)
        assertTrue(article.html.orEmpty().contains("hero-768x430.jpg"))
        assertEquals(renderedHtml.indexOf("hero-768x430.jpg"), renderedHtml.lastIndexOf("hero-768x430.jpg"))
        assertFalse(renderedHtml.contains("news-detail-header-image"))
    }

    @Test
    fun parseArticleRuntime_rendersHeroWhenBodyStartsWithYoutubeCard() {
        val html = """
            <html><head>
                <meta property="og:title" content="Video first news">
                <meta property="og:image" content="https://4pda.to/wp-content/uploads/video-hero.jpg">
                <meta property="og:url" content="https://4pda.to/index.php?p=456777">
            </head><body>
                <article class="article article-single" itemid="456777">
                    <header class="article-header">
                        <h1>Video first news</h1>
                        <div class="article-anons"><p>Lead paragraph before video.</p></div>
                    </header>
                    <div class="articleBody" itemprop="articleBody">
                        <p style="text-align:center">
                            <a href="https://www.youtube.com/watch?v=dQw4w9WgXcQ" class="yt-p-overlay">Видео</a>
                        </p>
                        <p>Text after video card.</p>
                    </div>
                    <footer class="article-footer"></footer>
                </article>
            </body></html>
        """.trimIndent()

        val article = ArticleParser(ArticlesPatternProviderStub()).parseArticle(html)
        val renderedHtml = articleTemplate().mapString(article)

        assertEquals("https://4pda.to/wp-content/uploads/video-hero.jpg", article.imgUrl)
        assertTrue(renderedHtml.contains("src=\"https://4pda.to/wp-content/uploads/video-hero.jpg\""))
        assertTrue(renderedHtml.contains("news-video-card"))
        assertTrue(renderedHtml.indexOf("video-hero.jpg") < renderedHtml.indexOf("Lead paragraph before video."))
        assertTrue(renderedHtml.indexOf("Lead paragraph before video.") < renderedHtml.indexOf("news-video-card"))
        assertTrue(renderedHtml.indexOf("news-video-card") < renderedHtml.indexOf("Text after video card."))
    }

    @Test
    fun parseArticleRuntime_doesNotSuppressHeroForDifferentFirstBodyImage() {
        val html = """
            <html><head>
                <meta property="og:title" content="Different first image">
                <meta property="og:image" content="https://4pda.to/wp-content/uploads/hero.jpg">
            </head><body>
                <article class="article article-single" itemid="456778">
                    <header class="article-header"><h1>Different first image</h1></header>
                    <div class="articleBody" itemprop="articleBody">
                        <p>Intro text.</p>
                        <figure><img src="https://4pda.to/wp-content/uploads/body.jpg" alt="Body"></figure>
                        <p>Text after body image.</p>
                    </div>
                    <footer class="article-footer"></footer>
                </article>
            </body></html>
        """.trimIndent()

        val article = ArticleParser(ArticlesPatternProviderStub()).parseArticle(html)
        val renderedHtml = articleTemplate().mapString(article)

        assertEquals("https://4pda.to/wp-content/uploads/hero.jpg", article.imgUrl)
        assertTrue(renderedHtml.contains("src=\"https://4pda.to/wp-content/uploads/hero.jpg\""))
        assertTrue(renderedHtml.contains("https://4pda.to/wp-content/uploads/body.jpg"))
        assertTrue(renderedHtml.indexOf("hero.jpg") < renderedHtml.indexOf("Intro text."))
        assertTrue(renderedHtml.indexOf("body.jpg") > renderedHtml.indexOf("Intro text."))
    }

    @Test
    fun parseArticleRuntime_usesTwitterImageWhenOgImagePatternMisses() {
        val html = """
            <html><head>
                <meta name="twitter:image" content="https://4pda.to/wp-content/uploads/twitter-hero.jpg">
                <meta property="og:title" content="Twitter hero news">
            </head><body>
                <article class="article article-single" itemid="456779">
                    <header class="article-header"><h1>Twitter hero news</h1></header>
                    <div class="articleBody" itemprop="articleBody">
                        <p>Article starts with plain text.</p>
                    </div>
                    <footer class="article-footer"></footer>
                </article>
            </body></html>
        """.trimIndent()

        val article = ArticleParser(ArticlesPatternProviderStub()).parseArticle(html)
        val renderedHtml = articleTemplate().mapString(article)

        assertEquals("https://4pda.to/wp-content/uploads/twitter-hero.jpg", article.imgUrl)
        assertTrue(renderedHtml.contains("src=\"https://4pda.to/wp-content/uploads/twitter-hero.jpg\""))
        assertTrue(renderedHtml.indexOf("twitter-hero.jpg") < renderedHtml.indexOf("Article starts with plain text."))
    }

    @Test
    fun parseArticleRuntime_preservesArticleAnonsBeforeFirstImageInRenderedHtml() {
        val leadText = "Lead paragraph from article-anons before the first image."
        val bodyText = "Body paragraph after image."
        val html = """
            <html><head>
                <meta property="og:description" content="Lead news">
                <meta property="og:site_name" content="4PDA">
                <meta property="og:title" content="Lead news">
                <meta property="og:image" content="https://4pda.to/hero.jpg">
            </head><body>
                <div class="container" data-ztm="1:456701:" itemid="456701">
                    <meta itemprop="datePublished" content="2026-05-20T06:15:00+00:00"/>
                    <link rel="stylesheet" href="https://4pda.to/style.css"/>
                    <div class="article">
                        <div class="article-header">
                            <h1>Lead news</h1>
                            <div class="article-anons">
                                <div class="article-meta">
                                    <time class="article-meta-time">20.05.26</time>
                                    <div class="article-meta-comment"><a href="#comments">0</a></div>
                                </div>
                                <p>$leadText</p>
                            </div>
                        </div>
                        <meta property="og:description" content="$leadText"/>
                        <!--more--></p>
                        <figure>
                            <a data-lightbox="post-456701" href="https://4pda.to/hero-full.jpg">
                                <img src="https://4pda.to/hero.jpg" alt="Hero">
                            </a>
                        </figure>
                        <p>$bodyText</p>
                        <div class="article-footer">
                            <div class="article-footer-tags"><a href="/tag/test/" rel="tag">Test</a></div>
                        </div>
                    </div>
                </div>
                <div class="comment-box" id="comments"><ul class="comment-list"></ul><form></form></div>
            </body></html>
        """.trimIndent()

        val article = ArticleParser(RuntimeArticlesPatternProviderStub()).parseArticle(html)
        val renderedHtml = articleTemplate().mapString(article)

        val leadIndex = renderedHtml.indexOf(leadText)
        val imageIndex = renderedHtml.indexOf("https://4pda.to/hero.jpg")
        val bodyIndex = renderedHtml.indexOf(bodyText)
        assertTrue(leadIndex >= 0)
        assertTrue(imageIndex > leadIndex)
        assertTrue(bodyIndex > imageIndex)
        assertEquals(leadIndex, renderedHtml.lastIndexOf(leadText))
        assertFalse(renderedHtml.contains("article-meta-comment"))
    }

    @Test
    fun parseArticleRuntime_doesNotDuplicateHeaderMetadataInsideRenderedBody() {
        val title = "Article metadata news"
        val leadText = "Lead paragraph that must remain in the article body."
        val bodyText = "Main article text that must remain after the image."
        val html = """
            <html><head>
                <meta property="og:description" content="$leadText">
                <meta property="og:site_name" content="4PDA">
                <meta property="og:title" content="$title">
                <meta property="og:image" content="https://4pda.to/hero.jpg">
            </head><body>
                <div class="container" data-ztm="1:456702:" itemid="456702">
                    <meta itemprop="datePublished" content="2026-05-24T06:15:00+00:00"/>
                    <link rel="stylesheet" href="https://4pda.to/style.css"/>
                    <div class="article">
                        <div class="article-header">
                            <h1>$title</h1>
                            <div class="article-meta">
                                <time class="article-meta-time">24.05.26</time>
                                <div class="article-meta-comment"><a href="#comments">10</a></div>
                            </div>
                        </div>
                        <div class="articleBody" itemprop="articleBody">
                            <h1>$title</h1>
                            <div class="article-meta">
                                <time class="article-meta-time">24.05.26</time>
                                <div class="article-meta-comment"><a href="#comments">10</a></div>
                            </div>
                            <div class="article-anons"><p>$leadText</p></div>
                            <figure>
                                <a data-lightbox="post-456702" href="https://4pda.to/hero-full.jpg">
                                    <img src="https://4pda.to/hero.jpg" alt="Hero">
                                </a>
                            </figure>
                            <p>$bodyText</p>
                        </div>
                        <div class="article-footer">
                            <div class="article-footer-tags"><a href="/tag/test/" rel="tag">Test</a></div>
                        </div>
                    </div>
                </div>
                <div class="comment-box" id="comments"><ul class="comment-list"></ul><form></form></div>
            </body></html>
        """.trimIndent()

        val parsedHtml = ArticleParser(ArticlesPatternProviderStub()).parseArticle(html).html
        val page = DetailsPage().apply {
            this.title = title
            author = "News"
            date = "24.05.26"
            commentsCount = 10
            this.html = parsedHtml
        }
        val renderedHtml = articleTemplate().mapString(page)

        val leadIndex = renderedHtml.indexOf(leadText)
        val imageIndex = renderedHtml.indexOf("https://4pda.to/hero.jpg")
        val bodyIndex = renderedHtml.indexOf(bodyText)
        assertTrue(renderedHtml.contains("news-detail-header-meta"))
        assertTrue(leadIndex >= 0)
        assertTrue(imageIndex > leadIndex)
        assertTrue(bodyIndex > imageIndex)
        assertFalse(parsedHtml.orEmpty().contains("article-meta-comment"))
        assertFalse(parsedHtml.orEmpty().contains("<time class=\"article-meta-time\">24.05.26</time>"))
        assertFalse(renderedHtml.contains("article-meta-comment"))
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
    fun parseArticle_normalizesStyledSponsoredCardLinkToInlineAnchor() {
        val html = """
            <html><head>
                <meta property="og:title" content="Hobot news">
                <meta property="article:id" content="455346">
            </head><body>
                <div class="article">
                    <div class="entry-content">
                        <p>В продаже модель появится в скором времени.</p>
                        <p style="text-align: center;">
                            <link rel="stylesheet" href="https://4pda.to/s/ad.css" media="all"/>
                            <div class="z1mul39e8JokuPlDDDBHGRP" style="background-color:#00394f;">
                                <span class="ad-marker"></span>
                                <a id="z0Rv0Dj9n1nU3"
                                   class="fQguz2hhXCxr"
                                   href="https://hobot.ru/roboty_moyshchiki_okon/robot_moyshchik_okon_hobot_sp10/?erid=2SDnjdAKiBc"
                                   target="_blank"
                                   style="background-color:#a4d8a5;background-image:url(https://4pda.to/s/ad.jpg);justify-content:left;">
                                    <span class="Y1tSftpVcwMlNGDD" style="background-color:#ffffff;color:#00394f;font-size:20px;">
                                        Предзаказать робот-мойщик окон Hobot SP10
                                    </span>
                                </a>
                            </div>
                        </p>
                    </div>
                    <div class="article-footer"></div>
                </div>
            </body></html>
        """.trimIndent()

        val parsedHtml = ArticleParser(ArticlesPatternProviderStub()).parseArticle(html).html.orEmpty()

        assertTrue(parsedHtml.contains(">Предзаказать робот-мойщик окон Hobot SP10</a>"))
        assertTrue(parsedHtml.contains("<a href=\"https://hobot.ru/roboty_moyshchiki_okon/robot_moyshchik_okon_hobot_sp10/?erid=2SDnjdAKiBc\""))
        assertFalse(parsedHtml.contains("background-image"))
        assertFalse(parsedHtml.contains("background-color:#00394f"))
        assertFalse(parsedHtml.contains("ad.css"))
    }

    @Test
    fun parseArticleV3_preservesRegularNewsPollFormOutsideArticleBody() {
        val html = """
            <html><head>
                <meta property="og:title" content="Poll news">
                <meta property="og:image" content="https://4pda.to/image.jpg">
            </head><body>
                <div class="article">
                    <div class="article-header"><h1>Poll news</h1><time>19.05.2026</time><a href="#comments">0</a></div>
                    <div class="entry-content"><p>Article body</p></div>
                    <div class="article-footer"></div>
                </div>
                <section class="article-poll vote">
                    <form action="https://4pda.to/pages/poll/?act=vote&amp;poll_id=456521" method="post">
                        <input type="hidden" name="from" value="/index.php?p=456521">
                        <h3>Как вы относитесь к яркому дизайну десктопных ПК?</h3>
                        <ul>
                            <li><label><input type="radio" name="answer[]" value="7477"> У меня компьютер светится, как новогодняя ёлка</label></li>
                            <li><label><input type="radio" name="answer[]" value="7478"> Допускаю умеренную однотонную подсветку</label></li>
                            <li><label><input type="radio" name="answer[]" value="7479"> Прозрачный корпус — да, подсветка — нет</label></li>
                            <li><label><input type="radio" name="answer[]" value="7480"> Я за минималистичный дизайн без окон и подсветки</label></li>
                            <li><label><input type="radio" name="answer[]" value="7481"> Другое</label></li>
                        </ul>
                        <button type="submit">Проголосовать</button>
                        <p>Чтобы увидеть результаты, необходимо проголосовать.</p>
                    </form>
                </section>
            </body></html>
        """.trimIndent()

        val article = ArticleParser(ArticlesPatternProviderStub()).parseArticle(html)
        val parsedHtml = article.html.orEmpty()

        assertTrue(parsedHtml.contains("Article body"))
        assertTrue(parsedHtml.contains("poll-ajax-frame-news"))
        assertTrue(parsedHtml.contains("Как вы относитесь к яркому дизайну десктопных ПК?"))
        assertTrue(parsedHtml.contains("У меня компьютер светится, как новогодняя ёлка"))
        assertTrue(parsedHtml.contains("Прозрачный корпус — да, подсветка — нет"))
        assertTrue(parsedHtml.contains("name=\"answer[]\""))
        assertTrue(parsedHtml.contains("value=\"7477\""))
        assertTrue(parsedHtml.contains("Проголосовать"))
        assertTrue(parsedHtml.contains("Чтобы увидеть результаты, необходимо проголосовать."))
    }

    @Test
    fun newsApi_fetchesDesktopPollWhenMobileArticleOmitsPoll() {
        val mobileHtml = """
            <html><head>
                <meta property="og:title" content="Яркий дизайн ПК">
            </head><body>
                <div class="article">
                    <div class="article-header"><h1>Яркий дизайн ПК</h1><time>19.05.2026</time><a href="#comments">0</a></div>
                    <div class="entry-content"><p>Расскажите в комментариях, каким должен быть компьютер.</p></div>
                    <div class="article-footer"></div>
                </div>
            </body></html>
        """.trimIndent()
        val desktopHtml = """
            <html><head>
                <meta property="og:title" content="Опрос: яркий дизайн ПК">
            </head><body>
                <div class="article">
                    <div class="article-header"><h1>Опрос: яркий дизайн ПК</h1><time>19.05.2026</time><a href="#comments">0</a></div>
                    <div class="entry-content"><p>Проголосуйте и расскажите в комментариях.</p></div>
                    <div class="article-footer"></div>
                </div>
                <section class="article-poll vote">
                    <form action="https://4pda.to/pages/poll/?act=vote&amp;poll_id=456521" method="post">
                        <input type="hidden" name="from" value="/index.php?p=456521">
                        <h3>Как вы относитесь к яркому дизайну десктопных ПК?</h3>
                        <ul>
                            <li><label><input type="radio" name="answer[]" value="7477"> У меня компьютер светится, как новогодняя елка</label></li>
                            <li><label><input type="radio" name="answer[]" value="7478"> Допускаю умеренную однотонную подсветку</label></li>
                        </ul>
                        <button type="submit">Проголосовать</button>
                    </form>
                </section>
            </body></html>
        """.trimIndent()
        val webClient = PollFallbackWebClient(mobileHtml, desktopHtml)

        val api = NewsApi(webClient, ArticleParser(ArticlesPatternProviderStub()))
        val article = runBlocking {
            api.enrichDesktopExtras(api.fetchArticleDetails("https://4pda.to/index.php?p=456521"))
        }

        assertEquals(listOf("https://4pda.to/index.php?p=456521", "https://4pda.to/index.php?p=456521"), webClient.requestedUrls)
        assertEquals(1, webClient.desktopRequests)
        assertTrue(webClient.desktopRequestHeaders.single().getValue("User-Agent").contains("Macintosh"))
        // Desktop phase-2 fetch uses default cache policy (bypass only on explicit refresh).
        assertTrue(webClient.desktopRequestHeaders.single()["Cache-Control"].isNullOrEmpty())
        val parsedHtml = article.html.orEmpty()
        assertTrue(parsedHtml.contains("Расскажите в комментариях"))
        assertTrue(parsedHtml.contains("poll-ajax-frame-news"))
        assertTrue(parsedHtml.contains("poll_id=456521"))
        assertTrue(parsedHtml.contains("name=\"answer[]\""))
        assertTrue(parsedHtml.contains("Проголосовать"))
    }

    @Test
    fun newsApi_fetchesDesktopPollWhenMobileOnlyHasWeakPollText() {
        val mobileHtml = """
            <html><head>
                <meta property="og:title" content="Опрос: яркий дизайн ПК">
            </head><body>
                <div class="article">
                    <div class="article-header"><h1>Опрос: яркий дизайн ПК</h1><time>19.05.2026</time><a href="#comments">0</a></div>
                    <div class="entry-content">
                        <p>Голосуйте в комментариях за лучший корпус.</p>
                        <p>Опрос читателей появится позднее.</p>
                    </div>
                    <div class="article-footer"></div>
                </div>
            </body></html>
        """.trimIndent()
        val desktopHtml = """
            <html><head>
                <meta property="og:title" content="Опрос: яркий дизайн ПК">
            </head><body>
                <div class="article">
                    <div class="article-header"><h1>Опрос: яркий дизайн ПК</h1><time>19.05.2026</time><a href="#comments">0</a></div>
                    <div class="entry-content"><p>Голосуйте за лучший корпус.</p></div>
                    <div class="article-footer"></div>
                </div>
                <section class="article-poll vote">
                    <form action="https://4pda.to/pages/poll/?act=vote&amp;poll_id=456521" method="post">
                        <input type="hidden" name="from" value="/index.php?p=456521">
                        <input type="hidden" name="poll_id" value="456521">
                        <h3>Как вы относитесь к яркому дизайну десктопных ПК?</h3>
                        <ul>
                            <li><label><input type="radio" name="answer[]" value="7477"> У меня компьютер светится</label></li>
                            <li><label><input type="radio" name="answer[]" value="7478"> Я за минимализм</label></li>
                        </ul>
                        <button type="submit">Проголосовать</button>
                    </form>
                </section>
            </body></html>
        """.trimIndent()
        val parser = ArticleParser(ArticlesPatternProviderStub())
        val webClient = PollFallbackWebClient(mobileHtml, desktopHtml)

        val mobileArticle = parser.parseArticle(mobileHtml)
        val api = NewsApi(webClient, parser)
        val article = runBlocking {
            api.enrichDesktopExtras(api.fetchArticleDetails("https://4pda.to/index.php?p=456521"))
        }

        assertTrue(parser.hasWeakNewsPollMarker(mobileHtml))
        assertFalse(parser.hasRealNewsPollMarkup(mobileHtml))
        assertFalse(parser.hasRealNewsPollMarkup(mobileArticle.html))
        assertEquals(1, webClient.desktopRequests)
        val parsedHtml = article.html.orEmpty()
        assertTrue(parsedHtml.contains("poll-ajax-frame-news"))
        assertTrue(parsedHtml.contains("poll_id=456521"))
        assertTrue(parsedHtml.contains("name=\"answer[]\""))
        assertTrue(parsedHtml.contains("Проголосовать"))
    }

    @Test
    fun parseArticle_rebuildsPollFromVisibleOptionsWhenRawTemplatePresent() {
        val html = """
            <html><head>
                <meta property="og:title" content="Опрос: яркий дизайн ПК">
                <meta property="og:url" content="https://4pda.to/2026/05/18/456521/opros_kak_vy_otnosites_k_yarkomu_dizajnu_desktopnykh_pk/">
            </head><body>
                <div class="article">
                    <div class="article-header"><h1>Опрос: яркий дизайн ПК</h1><time>19.05.2026</time><a href="#comments">0</a></div>
                    <div class="entry-content">
                        <p>Проголосуйте и расскажите в комментариях.</p>
                    </div>
                    <div class="article-footer"></div>
                </div>
                <div id="poll-ajax-frame-456521" class="poll">
                    <script type="text/template">
                        {%if showResult()%}
                        {%each ${'$'}args[3]%}
                        <span>${'$'}{getColor()} ${'$'}{Math.round(100*value)}</span>
                        {%/each%}
                    </script>
                </div>
                <section class="article-poll vote" data-poll-id="456521">
                    <h3>Как вы относитесь к яркому дизайну десктопных ПК?</h3>
                    <ul>
                        <li>У меня компьютер светится, как новогодняя ёлка</li>
                        <li>Допускаю умеренную однотонную подсветку</li>
                        <li>Прозрачный корпус — да, подсветка — нет</li>
                    </ul>
                    <button type="button">Проголосовать</button>
                </section>
            </body></html>
        """.trimIndent()

        val parsedHtml = ArticleParser(ArticlesPatternProviderStub()).parseArticle(html).html.orEmpty()

        assertTrue(parsedHtml.contains("poll-ajax-frame-news"))
        assertTrue(parsedHtml.contains("news-poll-normalized"))
        assertTrue(parsedHtml.contains("У меня компьютер светится, как новогодняя ёлка"))
        assertTrue(parsedHtml.contains("Допускаю умеренную однотонную подсветку"))
        assertTrue(parsedHtml.contains("Опрос доступен на сайте"))
        assertTrue(parsedHtml.contains("Открыть статью в браузере"))
        assertFalse(parsedHtml.contains("{%"))
        assertFalse(parsedHtml.contains("${'$'}args"))
        assertFalse(parsedHtml.contains("showResult()"))
        assertFalse(parsedHtml.contains("getColor()"))
        assertFalse(parsedHtml.contains("Math.round(100*"))
    }

    @Test
    fun parseArticle_rebuildsPollFromDataSitePollPayload() {
        val html = """
            <html><head>
                <meta property="og:title" content="Опрос: как вы относитесь к яркому дизайну десктопных ПК?">
                <meta property="og:url" content="https://4pda.to/2026/05/18/456521/opros_kak_vy_otnosites_k_yarkomu_dizajnu_desktopnykh_pk/">
            </head><body>
                <div class="article">
                    <div class="article-header"><h1>Опрос: как вы относитесь к яркому дизайну десктопных ПК?</h1><time>19.05.2026</time><a href="#comments">0</a></div>
                    <div class="entry-content">
                        <p>Проголосуйте и расскажите в комментариях.</p>
                        <script type="text/template" data-name="site-poll">
                            {%if showResult()%}{%each ${'$'}args[3]%}${'$'}{getColor()}{%/each%}
                        </script>
                    </div>
                    <div class="article-footer"></div>
                </div>
                <div id="poll-ajax-frame-1330" class="EjVZz0KcVu4mHo9HHz2Qz2z2">
                    <div data-site-poll="[1330,&quot;Как вы относитесь к яркому дизайну десктопных ПК?&quot;,0,[[7477,&quot;У меня&nbsp;компьютер светится, как новогодняя ёлка&quot;,386],[7478,&quot;Допускаю умеренную однотонную подсветку&quot;,758],[7479,&quot;Прозрачный корпус — да, подсветка — нет&quot;,472],[7480,&quot;Я за минималистичный дизайн без окон и подсветки&quot;,1185],[7481,&quot;Другое&quot;,131]],2932,[7481],false,0]"></div>
                </div>
                <aside class="most-commented">
                    <h3>Самые комментируемые</h3>
                    <ul class="news-list">
                        <li><a href="/index.php?p=456002">Новая версия Gemini получила важное обновление</a> <span class="v-count">101</span></li>
                    </ul>
                </aside>
            </body></html>
        """.trimIndent()

        val parsedHtml = ArticleParser(ArticlesPatternProviderStub()).parseArticle(html).html.orEmpty()

        assertTrue(parsedHtml.contains("poll-ajax-frame-news"))
        assertTrue(parsedHtml.contains("У меня компьютер светится, как новогодняя ёлка"))
        assertTrue(parsedHtml.contains("Допускаю умеренную однотонную подсветку"))
        assertTrue(parsedHtml.contains("Прозрачный корпус — да, подсветка — нет"))
        assertTrue(parsedHtml.contains("Я за минималистичный дизайн без окон и подсветки"))
        assertTrue(parsedHtml.contains("Другое"))
        assertTrue(parsedHtml.contains("select-option"))
        assertTrue(parsedHtml.contains("13% <span class=\"num_votes\">386</span>"))
        assertTrue(parsedHtml.contains("4% <span class=\"num_votes\">131</span>"))
        assertTrue(parsedHtml.contains("Проголосовало 2932 чел."))
        assertTrue(parsedHtml.contains("Открыть статью в браузере"))
        assertFalse(parsedHtml.contains("<form"))
        assertFalse(parsedHtml.contains("name=\"answer[]\""))
        assertFalse(parsedHtml.contains("Проголосовать"))
        assertFalse(parsedHtml.contains("Новая версия Gemini получила важное обновление"))
        assertFalse(parsedHtml.contains("news-poll-fallback"))
        assertFalse(parsedHtml.contains("{%"))
        assertFalse(parsedHtml.contains("${'$'}args"))
        assertFalse(parsedHtml.contains("showResult()"))
        assertFalse(parsedHtml.contains("getColor()"))
    }

    @Test
    fun parseArticle_rebuildsUnvotedDataSitePollPayloadAsVoteForm() {
        val html = """
            <html><head>
                <meta property="og:title" content="Опрос: тест">
                <meta property="og:url" content="https://4pda.to/index.php?p=456522">
            </head><body>
                <div class="article">
                    <div class="entry-content"><p>Проголосуйте.</p></div>
                    <div class="article-footer"></div>
                </div>
                <div id="poll-ajax-frame-1331">
                    <div data-site-poll="[1331,&quot;Какой вариант выбрать?&quot;,0,[[8001,&quot;Первый вариант&quot;,0],[8002,&quot;Второй вариант&quot;,0]],0,[],false,0]"></div>
                </div>
            </body></html>
        """.trimIndent()

        val parsedHtml = ArticleParser(ArticlesPatternProviderStub()).parseArticle(html).html.orEmpty()

        assertTrue(parsedHtml.contains("poll_id=1331"))
        assertTrue(parsedHtml.contains("data-news-poll-token=\""))
        assertTrue(parsedHtml.contains("name=\"answer[]\""))
        assertTrue(parsedHtml.contains("value=\"8001\""))
        assertTrue(parsedHtml.contains("Проголосовать"))
        assertTrue(parsedHtml.contains("Открыть статью в браузере"))
        assertFalse(parsedHtml.contains("disabled"))
        assertFalse(parsedHtml.contains("Опрос доступен на сайте"))
    }

    @Test
    fun extractNormalizedPollBlock_readsVotedDataSitePollFromVoteResponse() {
        val html = """
            <html><body>
                <div id="poll-ajax-frame-1331">
                    <div data-site-poll="[1331,&quot;Какой вариант выбрать?&quot;,0,[[8001,&quot;Первый вариант&quot;,3],[8002,&quot;Второй вариант&quot;,1]],4,[8001],false,0]"></div>
                </div>
            </body></html>
        """.trimIndent()

        val pollHtml = ArticleParser(ArticlesPatternProviderStub()).extractNormalizedPollBlock(html, "1331").orEmpty()

        assertTrue(pollHtml.contains("select-option"))
        assertTrue(pollHtml.contains("75% <span class=\"num_votes\">3</span>"))
        assertTrue(pollHtml.contains("25% <span class=\"num_votes\">1</span>"))
        assertTrue(pollHtml.contains("Проголосовало 4 чел."))
        assertFalse(pollHtml.contains("<form"))
        assertFalse(pollHtml.contains("name=\"answer[]\""))
        assertFalse(pollHtml.contains("Проголосовать"))
    }

    @Test
    fun parseArticle_dataSitePollWithVoteCookieRendersResultsNotVoteForm() {
        val parser = ArticleParser(ArticlesPatternProviderStub())
        parser.syncPollVoteCookies(
                mapOf(
                        "poll-1335" to okhttp3.Cookie.Builder()
                                .domain("4pda.to")
                                .path("/")
                                .name("poll-1335")
                                .value("7514")
                                .build()
                )
        )
        val html = """
            <html><head>
                <meta property="og:title" content="Опрос: тест">
                <meta property="og:url" content="https://4pda.to/index.php?p=457102">
            </head><body>
                <div class="article">
                    <div class="entry-content"><p>Проголосуйте.</p></div>
                    <div class="article-footer"></div>
                </div>
                <div id="poll-ajax-frame-1335">
                    <div data-site-poll="[1335,&quot;Планирую купить смартфон в этом году&quot;,0,[[7512,&quot;До 10 000 руб.&quot;,88],[7513,&quot;От 10 000 до 23 000 руб.&quot;,259],[7514,&quot;От 23 000 до 40 000 руб.&quot;,427],[7515,&quot;От 40 000 до 70 000 руб.&quot;,497],[7516,&quot;Более 70 000 руб.&quot;,502],[7517,&quot;Не планирую покупать в этом году&quot;,1888]],3661,[],false,0]"></div>
                </div>
            </body></html>
        """.trimIndent()

        val parsedHtml = parser.parseArticle(html).html.orEmpty()

        assertTrue(parsedHtml.contains("select-option"))
        assertTrue(parsedHtml.contains("poll-list"))
        assertTrue(parsedHtml.contains("class=\"slider\""))
        assertTrue(parsedHtml.contains("Проголосовало 3661 чел."))
        assertFalse(parsedHtml.contains("Проголосовать"))
        assertFalse(parsedHtml.contains("name=\"answer[]\""))
    }

    @Test
    fun parseArticle_dataSitePollWithoutVoteCookieStillRendersVoteForm() {
        val parser = ArticleParser(ArticlesPatternProviderStub())
        parser.syncPollVoteCookies(emptyMap())
        val html = """
            <html><head>
                <meta property="og:title" content="Опрос: тест">
                <meta property="og:url" content="https://4pda.to/index.php?p=457102">
            </head><body>
                <div class="article">
                    <div class="entry-content"><p>Проголосуйте.</p></div>
                    <div class="article-footer"></div>
                </div>
                <div id="poll-ajax-frame-1335">
                    <div data-site-poll="[1335,&quot;Планирую купить смартфон в этом году&quot;,0,[[7512,&quot;До 10 000 руб.&quot;,88],[7513,&quot;От 10 000 до 23 000 руб.&quot;,259],[7514,&quot;От 23 000 до 40 000 руб.&quot;,427],[7515,&quot;От 40 000 до 70 000 руб.&quot;,497],[7516,&quot;Более 70 000 руб.&quot;,502],[7517,&quot;Не планирую покупать в этом году&quot;,1888]],3661,[],false,0]"></div>
                </div>
            </body></html>
        """.trimIndent()

        val parsedHtml = parser.parseArticle(html).html.orEmpty()

        assertTrue(parsedHtml.contains("Проголосовать"))
        assertTrue(parsedHtml.contains("name=\"answer[]\""))
        assertFalse(parsedHtml.contains("class=\"slider\""))
    }

    @Test
    fun parseArticle_rawTemplateDoesNotUseNeighboringNewsListAsPollOptions() {
        val html = """
            <html><head>
                <meta property="og:title" content="Опрос: яркий дизайн ПК">
                <meta property="article:id" content="456521">
            </head><body>
                <div class="article">
                    <div class="article-header"><h1>Опрос: яркий дизайн ПК</h1><time>19.05.2026</time><a href="#comments">0</a></div>
                    <div class="entry-content"><p>Проголосуйте и расскажите в комментариях.</p></div>
                    <div class="article-footer"></div>
                </div>
                <div id="poll-ajax-frame-456521" class="poll">
                    {%if showResult()%}
                    {%each ${'$'}args[3]%}
                    <span>${'$'}{getColor()} ${'$'}{Math.round(100*value)}</span>
                    {%/each%}
                </div>
                <aside class="most-commented">
                    <h3>Самые комментируемые</h3>
                    <ul class="news-list">
                        <li><a href="/index.php?p=456001">Опрос: как вы относитесь к яркому дизайну десктопных ПК?</a> <span class="v-count">110</span></li>
                        <li><a href="/index.php?p=456002">Новая версия Gemini получила важное обновление</a> <span class="v-count">101</span></li>
                        <li><a href="/index.php?p=456003">Вышел необычный смартфон с большим экраном</a> <span class="v-count">88</span></li>
                    </ul>
                </aside>
            </body></html>
        """.trimIndent()

        val parsedHtml = ArticleParser(ArticlesPatternProviderStub()).parseArticle(html).html.orEmpty()

        assertTrue(parsedHtml.contains("news-poll-fallback"))
        assertTrue(parsedHtml.contains("Опрос доступен на сайте"))
        assertTrue(parsedHtml.contains("Открыть статью в браузере"))
        assertFalse(parsedHtml.contains("Новая версия Gemini получила важное обновление"))
        assertFalse(parsedHtml.contains("Вышел необычный смартфон с большим экраном"))
        assertFalse(parsedHtml.contains("name=\"answer[]\""))
    }

    @Test
    fun parseArticle_weakRawTemplateStillRendersFallbackInsteadOfRelatedNewsOptions() {
        val html = """
            <html><head>
                <meta property="og:title" content="Опрос: яркий дизайн ПК">
                <meta property="og:url" content="https://4pda.to/index.php?p=456521">
            </head><body>
                <div class="article">
                    <div class="article-header"><h1>Опрос: яркий дизайн ПК</h1><time>19.05.2026</time><a href="#comments">0</a></div>
                    <div class="entry-content">
                        <p>Проголосуйте и расскажите в комментариях.</p>
                        <script type="text/template">
                            {%if showResult()%}
                            {%each ${'$'}args[3]%}
                            <span>${'$'}{getColor()} ${'$'}{Math.round(100*value)}</span>
                            {%/each%}
                        </script>
                    </div>
                    <div class="article-footer"></div>
                </div>
                <aside class="most-commented">
                    <h3>Самые комментируемые</h3>
                    <ul class="news-list">
                        <li><a href="/index.php?p=456001">Опрос: как вы относитесь к яркому дизайну десктопных ПК?</a> <span class="v-count">110</span></li>
                        <li><a href="/index.php?p=456002">Новая версия Gemini получила важное обновление</a> <span class="v-count">101</span></li>
                    </ul>
                </aside>
            </body></html>
        """.trimIndent()

        val parsedHtml = ArticleParser(ArticlesPatternProviderStub()).parseArticle(html).html.orEmpty()

        assertTrue(parsedHtml.contains("news-poll-fallback"))
        assertTrue(parsedHtml.contains("Опрос: яркий дизайн ПК"))
        assertTrue(parsedHtml.contains("Опрос доступен на сайте"))
        assertTrue(parsedHtml.contains("Открыть статью в браузере"))
        assertTrue(parsedHtml.contains("https://4pda.to/index.php?p=456521"))
        assertFalse(parsedHtml.contains("Новая версия Gemini получила важное обновление"))
        assertFalse(parsedHtml.contains("name=\"answer[]\""))
        assertFalse(parsedHtml.contains("{%"))
        assertFalse(parsedHtml.contains("${'$'}args"))
    }

    @Test
    fun parseArticle_realPollFormBeatsNeighboringNewsList() {
        val html = """
            <html><head>
                <meta property="og:title" content="Опрос: яркий дизайн ПК">
                <meta property="article:id" content="456521">
            </head><body>
                <div class="article">
                    <div class="article-header"><h1>Опрос: яркий дизайн ПК</h1><time>19.05.2026</time><a href="#comments">0</a></div>
                    <div class="entry-content"><p>Проголосуйте и расскажите в комментариях.</p></div>
                    <div class="article-footer"></div>
                </div>
                <div id="poll-ajax-frame-456521" class="poll">
                    {%if showResult()%}
                    {%each ${'$'}args[3]%}
                    <span>${'$'}{getColor()} ${'$'}{Math.round(100*value)}</span>
                    {%/each%}
                </div>
                <section class="article-poll vote">
                    <form action="https://4pda.to/pages/poll/?act=vote&amp;poll_id=456521" method="post">
                        <input type="hidden" name="from" value="/index.php?p=456521">
                        <h3>Как вы относитесь к яркому дизайну десктопных ПК?</h3>
                        <ul>
                            <li><input type="radio" name="answer[]" value="7477"> У меня компьютер светится, как новогодняя ёлка</li>
                            <li><input type="radio" name="answer[]" value="7478"> Допускаю умеренную однотонную подсветку</li>
                            <li><input type="radio" name="answer[]" value="7479"> Прозрачный корпус — да, подсветка — нет</li>
                            <li><input type="radio" name="answer[]" value="7480"> Я за минималистичный дизайн без окон и подсветки</li>
                            <li><input type="radio" name="answer[]" value="7481"> Другое</li>
                        </ul>
                        <button type="submit">Проголосовать</button>
                    </form>
                </section>
                <aside class="most-commented">
                    <h3>Самые комментируемые</h3>
                    <ul class="news-list">
                        <li><a href="/index.php?p=456001">Опрос: как вы относитесь к яркому дизайну десктопных ПК?</a> <span class="v-count">110</span></li>
                        <li><a href="/index.php?p=456002">Новая версия Gemini получила важное обновление</a> <span class="v-count">101</span></li>
                    </ul>
                </aside>
            </body></html>
        """.trimIndent()

        val parsedHtml = ArticleParser(ArticlesPatternProviderStub()).parseArticle(html).html.orEmpty()

        assertTrue(parsedHtml.contains("poll-ajax-frame-news"))
        assertTrue(parsedHtml.contains("У меня компьютер светится, как новогодняя ёлка"))
        assertTrue(parsedHtml.contains("Допускаю умеренную однотонную подсветку"))
        assertTrue(parsedHtml.contains("Прозрачный корпус — да, подсветка — нет"))
        assertTrue(parsedHtml.contains("Я за минималистичный дизайн без окон и подсветки"))
        assertTrue(parsedHtml.contains("Другое"))
        assertTrue(parsedHtml.contains("value=\"7477\""))
        assertFalse(parsedHtml.contains("Новая версия Gemini получила важное обновление"))
        assertFalse(parsedHtml.contains("news-poll-fallback"))
    }

    @Test
    fun parseArticle_fallsBackWithoutRawTemplateWhenNoPollOptionsPresent() {
        val html = """
            <html><head>
                <meta property="og:title" content="Опрос: яркий дизайн ПК">
                <meta property="article:id" content="456521">
            </head><body>
                <div class="article">
                    <div class="article-header"><h1>Опрос: яркий дизайн ПК</h1><time>19.05.2026</time><a href="#comments">0</a></div>
                    <div class="entry-content"><p>Проголосуйте и расскажите в комментариях.</p></div>
                    <div class="article-footer"></div>
                </div>
                <div id="poll-ajax-frame-456521" class="poll">
                    {%if showResult()%}
                    {%each ${'$'}args[3]%}
                    <span>${'$'}{getColor()} ${'$'}{Math.round(100*value)}</span>
                    {%/each%}
                </div>
            </body></html>
        """.trimIndent()

        val parsedHtml = ArticleParser(ArticlesPatternProviderStub()).parseArticle(html).html.orEmpty()

        assertTrue(parsedHtml.contains("news-poll-fallback"))
        assertTrue(parsedHtml.contains("Опрос доступен на сайте"))
        assertTrue(parsedHtml.contains("https://4pda.to/index.php?p=456521"))
        assertFalse(parsedHtml.contains("{%"))
        assertFalse(parsedHtml.contains("${'$'}args"))
        assertFalse(parsedHtml.contains("showResult()"))
        assertFalse(parsedHtml.contains("getColor()"))
        assertFalse(parsedHtml.contains("Math.round(100*"))
    }

    @Test
    fun parseArticle_forcesFallbackForPollTitleWithoutPollMarkup() {
        val html = """
            <html><head>
                <meta property="og:title" content="Опрос: яркий дизайн ПК">
                <meta property="og:url" content="https://4pda.to/index.php?p=456521">
            </head><body>
                <div class="article">
                    <div class="article-header"><h1>Опрос: яркий дизайн ПК</h1><time>19.05.2026</time><a href="#comments">0</a></div>
                    <div class="entry-content"><p>Проголосуйте и расскажите в комментариях.</p></div>
                    <div class="article-footer"></div>
                </div>
                <aside class="most-commented">
                    <h3>Самые комментируемые</h3>
                    <ul class="news-list">
                        <li><a href="/index.php?p=456001">Опрос: похожая статья</a> <span class="v-count">110</span></li>
                        <li><a href="/index.php?p=456002">Новая версия Gemini получила важное обновление</a> <span class="v-count">101</span></li>
                    </ul>
                </aside>
            </body></html>
        """.trimIndent()

        val parser = ArticleParser(ArticlesPatternProviderStub())
        val parsedHtml = parser.parseArticle(html).html.orEmpty()

        assertTrue(parsedHtml.contains("news-poll-fallback"))
        assertTrue(parsedHtml.contains("data-forced-fallback-poll=\"true\""))
        assertTrue(parsedHtml.contains("Опрос: яркий дизайн ПК"))
        assertTrue(parsedHtml.contains("Опрос доступен на сайте"))
        assertTrue(parsedHtml.contains("data-open-external-browser=\"true\""))
        assertTrue(parsedHtml.contains("https://4pda.to/index.php?p=456521"))
        assertTrue(parser.hasFallbackNewsPollBlock(parsedHtml))
        assertTrue(parser.hasForcedFallbackNewsPollBlock(parsedHtml))
        assertFalse(parsedHtml.contains("Новая версия Gemini получила важное обновление"))
        assertFalse(parsedHtml.contains("name=\"answer[]\""))
        assertFalse(parsedHtml.contains("{%"))
    }

    @Test
    fun parseArticle_preservesLeadHeadingsVideoAndOrder() {
        val leadText = "Lead text before the image must stay first."
        val bodyText = "Body text after image."
        val html = """
            <html><head>
                <meta property="og:title" content="Full content news">
                <meta property="og:image" content="https://4pda.to/hero.jpg">
                <meta property="article:id" content="456900">
            </head><body>
                <div class="article">
                    <div class="article-header">
                        <h1>Full content news</h1>
                        <div class="article-anons"><p>$leadText</p></div>
                    </div>
                    <div class="articleBody" itemprop="articleBody">
                        <figure><img src="https://4pda.to/hero.jpg" alt="Hero"></figure>
                        <h2>Important section</h2>
                        <p>$bodyText</p>
                        <h3>Video section</h3>
                        <iframe src="https://www.youtube.com/embed/dQw4w9WgXcQ"></iframe>
                        <ul><li>List item</li></ul>
                    </div>
                    <div class="article-footer"></div>
                </div>
            </body></html>
        """.trimIndent()

        val article = ArticleParser(ArticlesPatternProviderStub()).parseArticle(html)
        val parsedHtml = article.html.orEmpty()
        val renderedHtml = articleTemplate().mapString(article)

        val leadIndex = parsedHtml.indexOf(leadText)
        val imageIndex = parsedHtml.indexOf("https://4pda.to/hero.jpg")
        val headingIndex = parsedHtml.indexOf("<h2>Important section</h2>")
        val bodyIndex = parsedHtml.indexOf(bodyText)
        val videoIndex = parsedHtml.indexOf("news-video-card")
        assertTrue(leadIndex >= 0)
        assertTrue(imageIndex > leadIndex)
        assertTrue(headingIndex > imageIndex)
        assertTrue(bodyIndex > headingIndex)
        assertTrue(videoIndex > bodyIndex)
        assertTrue(parsedHtml.contains("<h3>Video section</h3>"))
        assertTrue(parsedHtml.contains("https://www.youtube.com/watch?v=dQw4w9WgXcQ"))
        assertTrue(parsedHtml.contains("data-video-id=\"dQw4w9WgXcQ\""))
        assertTrue(parsedHtml.contains("data-video-embed-url=\"https://www.youtube-nocookie.com/embed/dQw4w9WgXcQ?autoplay=1&amp;rel=0\""))
        assertTrue(parsedHtml.contains("data-video-play=\"true\""))
        assertTrue(parsedHtml.contains("Открыть в YouTube"))
        assertTrue(parsedHtml.contains("https://img.youtube.com/vi/dQw4w9WgXcQ/hqdefault.jpg"))
        assertFalse(parsedHtml.contains("<iframe"))
        assertTrue(renderedHtml.contains("news-video-card"))
        assertEquals(null, article.imgUrl)
    }

    @Test
    fun parseArticle_actualLikeMarkupKeepsYoutubeCategoryLeadHeadingsAndOrder() {
        val html = """
            <html><head>
                <meta property="og:title" content="Actual-like news">
                <meta property="og:url" content="https://4pda.to/index.php?p=456776">
                <meta property="article:section" content="Новости">
            </head><body>
                <article class="article article-single" itemid="456776">
                    <header class="article-header">
                        <h1>Actual-like news</h1>
                        <div class="article-anons"><p>Lead paragraph from site.</p></div>
                    </header>
                    <div class="articleBody" itemprop="articleBody">
                        <p>First body paragraph.</p>
                        <h2>Video heading</h2>
                        <div class="wp-block-embed is-type-video is-provider-youtube">
                            <div class="wp-block-embed__wrapper">
                                <iframe src="https://www.youtube.com/embed/dQw4w9WgXcQ?feature=oembed" allowfullscreen></iframe>
                            </div>
                            <figcaption>Official clip caption.</figcaption>
                        </div>
                        <p>Paragraph after video.</p>
                    </div>
                    <footer class="article-footer">
                        <div class="article-footer-tags">
                            <a href="/news/">Новости</a>
                            <a href="/tag/android/">Android</a>
                            <a href="https://4pda.to/software/tag/good-lock/">Good Lock</a>
                        </div>
                    </footer>
                </article>
            </body></html>
        """.trimIndent()

        val article = ArticleParser(ArticlesPatternProviderStub()).parseArticle(html)
        val parsedHtml = article.html.orEmpty()
        val renderedHtml = articleTemplate().mapString(article)

        assertEquals("Новости", article.category?.title)
        assertEquals("https://4pda.to/news/", article.category?.url)
        assertTrue(article.tags.isEmpty())
        assertTrue(parsedHtml.contains("Lead paragraph from site."))
        assertTrue(parsedHtml.contains("<h2>Video heading</h2>"))
        assertTrue(parsedHtml.contains("news-video-card"))
        assertTrue(parsedHtml.contains("https://www.youtube.com/watch?v=dQw4w9WgXcQ"))
        assertFalse(parsedHtml.contains("<iframe"))
        assertTrue(parsedHtml.indexOf("Lead paragraph from site.") < parsedHtml.indexOf("First body paragraph."))
        assertTrue(parsedHtml.indexOf("First body paragraph.") < parsedHtml.indexOf("<h2>Video heading</h2>"))
        assertTrue(parsedHtml.indexOf("<h2>Video heading</h2>") < parsedHtml.indexOf("news-video-card"))
        assertTrue(parsedHtml.indexOf("Paragraph after video.") > parsedHtml.indexOf("news-video-card"))
        assertTrue(renderedHtml.contains("news-detail-category"))
        assertFalse(renderedHtml.contains("news-detail-tags"))
        assertTrue(renderedHtml.contains("href=\"https://4pda.to/news/\""))
        assertTrue(renderedHtml.contains("data-taxonomy-url=\"https://4pda.to/news/\""))
        assertFalse(renderedHtml.contains("href=\"https://4pda.to/tag/android/\""))
        assertFalse(renderedHtml.contains("href=\"https://4pda.to/software/tag/good-lock/\""))
    }

    @Test
    fun parseArticle_preservesLeadTextAfterInlineLinkBeforeMoreMarker() {
        val html = """
            <html><head>
                <meta property="og:title" content="Hopetown news">
                <meta property="article:id" content="456776">
            </head><body>
                <div class="article">
                    <div class="article-header">
                        <h1>Hopetown news</h1>
                        <div class="article-anons">
                            <div class="article-meta">
                                <time class="article-meta-time">22.05.26</time>
                                <div class="article-meta-comment"><a href="#comments">0</a></div>
                            </div>
                            <p>На этой неделе ZA/UM <a href="//4pda.to/related/">выпустила</a> Zero Parades: For Dead Spies. Некоторые геймеры показательно игнорируют новинку.</p>
                        </div>
                    </div>
                    <meta property="og:description" content="На этой неделе ZA/UM выпустила Zero Parades: For Dead Spies.">
                    <!--more--><p></p>
                    <p style="text-align:center">
                        <a href="https://www.youtube.com/watch?v=eXpxiRIhZXA" class="yt-p-overlay">Видео</a>
                    </p>
                    <p>В сети появился трейлер Hopetown.</p>
                    <p>Главный герой истории — журналист.</p>
                    <ul>
                        <li>корреспондент — профессионал;</li>
                        <li>колумнист — эксперт;</li>
                    </ul>
                    <p>Авторы обещают очень глубокий геймплей.</p>
                    <p>Также у персонажа будет очень развитый внутренний мир.</p>
                    <figure><img src="https://4pda.to/s/hopetown.jpg" alt="Hope"></figure>
                    <p>Помимо геймплейного трейлера, Longdue Games поделилась важной новостью.</p>
                    <div class="article-footer"></div>
                </div>
            </body></html>
        """.trimIndent()

        val article = ArticleParser(ArticlesPatternProviderStub()).parseArticle(html)
        val parsedHtml = article.html.orEmpty()
        val renderedHtml = articleTemplate().mapString(article)

        val releasedIndex = parsedHtml.indexOf("выпустила")
        val tailIndex = parsedHtml.indexOf("Zero Parades: For Dead Spies")
        val trailerIndex = parsedHtml.indexOf("В сети появился трейлер Hopetown.")
        val gameplayIndex = parsedHtml.indexOf("Авторы обещают очень глубокий геймплей.")
        val figureIndex = parsedHtml.indexOf("https://4pda.to/s/hopetown.jpg")
        val afterFigureIndex = parsedHtml.indexOf("Помимо геймплейного трейлера")
        assertTrue(releasedIndex >= 0)
        assertTrue(tailIndex > releasedIndex)
        assertTrue(trailerIndex > tailIndex)
        assertTrue(gameplayIndex > trailerIndex)
        assertTrue(figureIndex > gameplayIndex)
        assertTrue(afterFigureIndex > figureIndex)
        assertTrue(renderedHtml.contains("выпустила</a> Zero Parades: For Dead Spies"))
        assertTrue(renderedHtml.indexOf("Zero Parades: For Dead Spies") < renderedHtml.indexOf("В сети появился трейлер Hopetown."))
    }

    @Test
    fun parseArticle_normalizesYoutubeLinkBlock() {
        val html = """
            <html><head>
                <meta property="og:title" content="Youtube link news">
            </head><body>
                <div class="article">
                    <div class="entry-content">
                        <p>Before video.</p>
                        <p><a href="https://youtu.be/dQw4w9WgXcQ">Watch on YouTube</a></p>
                        <p>After video.</p>
                    </div>
                    <div class="article-footer"></div>
                </div>
            </body></html>
        """.trimIndent()

        val parsedHtml = ArticleParser(ArticlesPatternProviderStub()).parseArticle(html).html.orEmpty()

        assertTrue(parsedHtml.contains("news-video-card"))
        assertTrue(parsedHtml.contains("Watch on YouTube"))
        assertTrue(parsedHtml.contains("https://www.youtube.com/watch?v=dQw4w9WgXcQ"))
        assertTrue(parsedHtml.contains("data-video-id=\"dQw4w9WgXcQ\""))
        assertTrue(parsedHtml.contains("data-video-embed-url=\"https://www.youtube-nocookie.com/embed/dQw4w9WgXcQ?autoplay=1&amp;rel=0\""))
        assertTrue(parsedHtml.contains("Открыть в YouTube"))
        assertTrue(parsedHtml.indexOf("Before video.") < parsedHtml.indexOf("news-video-card"))
        assertTrue(parsedHtml.indexOf("After video.") > parsedHtml.indexOf("news-video-card"))
    }

    @Test
    fun parseArticle_normalizesYoutubeWatchLinkWithQueryAndEmbedTag() {
        val html = """
            <html><head>
                <meta property="og:title" content="Youtube variants">
            </head><body>
                <div class="article">
                    <div class="entry-content">
                        <p><a href="https://www.youtube.com/watch?feature=share&amp;v=dQw4w9WgXcQ">Watch variant</a></p>
                        <embed src="https://www.youtube.com/embed/oHg5SJYRHA0"></embed>
                    </div>
                    <div class="article-footer"></div>
                </div>
            </body></html>
        """.trimIndent()

        val parsedHtml = ArticleParser(ArticlesPatternProviderStub()).parseArticle(html).html.orEmpty()

        assertTrue(parsedHtml.contains("https://www.youtube.com/watch?v=dQw4w9WgXcQ"))
        assertTrue(parsedHtml.contains("https://www.youtube.com/watch?v=oHg5SJYRHA0"))
        assertFalse(parsedHtml.contains("<embed"))
    }

    @Test
    fun parseArticle_extractsCategoryAndIgnoresTags() {
        val html = """
            <html><head>
                <meta property="og:title" content="Tagged news">
                <meta property="article:section" content="Обзоры">
            </head><body>
                <div class="article">
                    <nav class="breadcrumbs">
                        <a href="/">4PDA</a>
                        <a href="/news/">Новости</a>
                        <a href="/reviews/">Обзоры</a>
                    </nav>
                    <div class="entry-content"><p>Article body.</p></div>
                    <footer class="article-footer">
                        <div class="article-footer-tags">
                            <a href="/tag/android/">Android</a>
                            <a href="/tag/google/">Google</a>
                        </div>
                    </footer>
                </div>
            </body></html>
        """.trimIndent()

        val article = ArticleParser(ArticlesPatternProviderStub()).parseArticle(html)
        val renderedHtml = articleTemplate().mapString(article)

        assertEquals("Обзоры", article.category?.title)
        assertTrue(article.tags.isEmpty())
        assertEquals("https://4pda.to/reviews/", article.category?.url)
        assertTrue(renderedHtml.contains("news-detail-category"))
        assertFalse(renderedHtml.contains("news-detail-tags"))
        val categoryHtml = renderedHtml.substringAfter("news-detail-category")
        assertTrue(categoryHtml.contains("href=\"https://4pda.to/reviews/\""))
        assertTrue(categoryHtml.contains("data-taxonomy-url=\"https://4pda.to/reviews/\""))
        assertTrue(categoryHtml.contains("Обзоры"))
        assertFalse(renderedHtml.contains("https://4pda.to/tag/android/"))
        assertFalse(renderedHtml.contains("https://4pda.to/tag/google/"))
    }

    @Test
    fun articleTemplate_doesNotRenderTags() {
        val renderedHtml = articleTemplate().mapString(
                DetailsPage().apply {
                    title = "Tag without url"
                    html = "<p>Article body.</p>"
                    tags.add(forpdateam.ru.forpda.entity.remote.news.Tag(tag = "android", title = "Android"))
                }
        )

        assertFalse(renderedHtml.contains("news-detail-tags"))
        assertFalse(renderedHtml.contains("Android"))
        assertFalse(renderedHtml.contains("href=\"https://4pda.to/tag/android/\""))
        assertFalse(renderedHtml.contains("data-taxonomy-url=\"https://4pda.to/tag/android/\""))
    }

    @Test
    fun parseArticle_rendersMetaSectionAsSeparateDisabledCategoryWhenNoUrlExists() {
        val html = """
            <html><head>
                <meta property="og:title" content="Meta section news">
                <meta property="article:section" content="Техника">
            </head><body>
                <div class="article">
                    <div class="entry-content"><p>Article body.</p></div>
                    <footer class="article-footer">
                        <div class="article-footer-tags">
                            <a href="/tag/xiaomi/">Xiaomi</a>
                        </div>
                    </footer>
                </div>
            </body></html>
        """.trimIndent()

        val article = ArticleParser(ArticlesPatternProviderStub()).parseArticle(html)
        val renderedHtml = articleTemplate().mapString(article)
        val categoryHtml = renderedHtml.substringAfter("news-detail-category")

        assertEquals("Техника", article.category?.title)
        assertTrue(article.tags.isEmpty())
        assertTrue(categoryHtml.contains("Раздел"))
        assertTrue(categoryHtml.contains("Техника"))
        assertTrue(categoryHtml.contains("news-detail-chip-disabled"))
        assertFalse(categoryHtml.contains("data-taxonomy-url"))
        assertFalse(categoryHtml.contains("Xiaomi"))
        assertFalse(renderedHtml.contains("news-detail-tags"))
        assertFalse(renderedHtml.contains("href=\"https://4pda.to/tag/xiaomi/\""))
    }

    @Test
    fun parseArticle_prefersConcreteSectionOverGenericNewsBreadcrumb() {
        val html = """
            <html><head>
                <meta property="og:title" content="Concrete section news">
            </head><body>
                <article class="article">
                    <nav class="breadcrumbs">
                        <a href="https://4pda.to/news/">Новости</a>
                        <a href="/games/">Игры</a>
                    </nav>
                    <div class="entry-content"><p>Article body.</p></div>
                    <footer class="article-footer">
                        <div class="article-footer-tags">
                            <a href="/tag/telegram/">Telegram</a>
                        </div>
                    </footer>
                </article>
            </body></html>
        """.trimIndent()

        val article = ArticleParser(ArticlesPatternProviderStub()).parseArticle(html)
        val renderedHtml = articleTemplate().mapString(article)
        val categoryHtml = renderedHtml.substringAfter("news-detail-category")

        assertEquals("Игры", article.category?.title)
        assertEquals("https://4pda.to/games/", article.category?.url)
        assertTrue(article.tags.isEmpty())
        assertTrue(categoryHtml.contains("href=\"https://4pda.to/games/\""))
        assertFalse(categoryHtml.contains("Telegram"))
        assertFalse(renderedHtml.contains("news-detail-tags"))
        assertFalse(renderedHtml.contains("href=\"https://4pda.to/tag/telegram/\""))
    }

    @Test
    fun parseArticle_withoutVideoKeepsRegularBody() {
        val html = """
            <html><head>
                <meta property="og:title" content="Regular news">
            </head><body>
                <div class="article">
                    <div class="entry-content">
                        <p>Regular paragraph.</p>
                        <h2>Regular heading</h2>
                    </div>
                    <div class="article-footer"></div>
                </div>
            </body></html>
        """.trimIndent()

        val parsedHtml = ArticleParser(ArticlesPatternProviderStub()).parseArticle(html).html.orEmpty()

        assertTrue(parsedHtml.contains("Regular paragraph."))
        assertTrue(parsedHtml.contains("<h2>Regular heading</h2>"))
        assertFalse(parsedHtml.contains("news-video-card"))
    }
}
