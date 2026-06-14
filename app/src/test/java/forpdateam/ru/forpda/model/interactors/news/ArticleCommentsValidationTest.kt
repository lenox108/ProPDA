package forpdateam.ru.forpda.model.interactors.news

import forpdateam.ru.forpda.entity.remote.news.Comment
import forpdateam.ru.forpda.entity.remote.news.DetailsPage
import forpdateam.ru.forpda.model.data.remote.api.NetworkResponse
import forpdateam.ru.forpda.model.data.remote.api.news.ArticleFetchResult
import forpdateam.ru.forpda.model.data.remote.api.news.ArticleParsePhase
import forpdateam.ru.forpda.model.data.remote.IWebClient
import forpdateam.ru.forpda.model.data.remote.ParserPatterns
import forpdateam.ru.forpda.model.data.remote.api.NetworkRequest
import forpdateam.ru.forpda.model.data.remote.api.news.ArticleParser
import forpdateam.ru.forpda.model.data.remote.api.news.NewsApi
import forpdateam.ru.forpda.model.data.cache.forumuser.ForumUsersCacheRoom
import forpdateam.ru.forpda.model.data.storage.IPatternProvider
import forpdateam.ru.forpda.model.repository.news.NewsRepository
import forpdateam.ru.forpda.presentation.articles.detail.ArticleTemplate
import java.util.regex.Pattern
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ArticleCommentsValidationTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `empty parse with real comment node and positive count is parse failure`() = runTest(dispatcher) {
        val article = DetailsPage().apply {
            id = 42
            commentsCount = 12
            commentsSource = "<ul class=\"comment-list\"><li id=\"comment-1\">x</li></ul>"
            html = """
                <html><body><div class="content material_item">
                Article body with enough length for render checks and stable article reload behavior.
                </div></body></html>
            """.trimIndent()
            // Pre-set a complete tree so prefetch is skipped (keeps requestId deterministic).
            commentTree = Comment().apply {
                repeat(12) { index -> children.add(Comment().apply { id = index + 1 }) }
            }
        }
        val template = mockk<ArticleTemplate> {
            every { mapEntity(any()) } answers { firstArg() }
        }
        val emptyTree = Comment()
        val api = mockk<NewsApi> {
            every { fetchArticleDetails("https://4pda.to/index.php?p=42", ArticleParsePhase.FIRST_RENDER, bypassCache = false) } returns ArticleFetchResult(
                    page = article,
                    rawBody = article.html.orEmpty(),
                    response = NetworkResponse(body = article.html.orEmpty()),
                    originalUrl = "https://4pda.to/index.php?p=42",
                    probeUrl = "https://4pda.to/index.php?p=42"
            )
            every { rebalanceCommentsSource(any()) } returns false
            every { hasCommentNodeMarkup(any()) } returns true
            every { countCommentNodesInSource(any()) } returns 1
            every { commentsSourceUnderfetchesExpected(any(), any()) } returns true
            every { parseCommentsFromSource(any(), any(), paginated = true) } returns emptyTree
        }
        val interactor = ArticleInteractor(
                ArticleInteractor.InitData(newsId = 42, hintCommentsCount = 12),
                NewsRepository(api, mockk<ForumUsersCacheRoom>(relaxed = true)),
                template
        )
        interactor.loadArticle(loadComments = false)
        advanceUntilIdle()

        val result = interactor.loadComments(forceReload = true)
        advanceUntilIdle()

        assertTrue(result.toString(), result is ArticleInteractor.CommentLoadResult.Error)
        assertEquals(
                "comments_page_fetch_empty",
                (result as ArticleInteractor.CommentLoadResult.Error).throwable.message
        )
    }

    @Test
    fun `empty comment-list shell with positive own count needs fetch not empty`() = runTest(dispatcher) {
        // NEW EVIDENCE: the phase-1 mobile page ships an EMPTY comment-list shell even for articles
        // with hundreds of comments (mobile lazy-loads them). When the article's own count badge is
        // positive but the desktop comment list could not be fetched, the load must surface a
        // RETRYABLE failure (needs-fetch), NOT a false "no comments yet" empty state.
        val article = DetailsPage().apply {
            id = 42
            commentsCount = 289
            commentsSource = "<div class=\"comment-box\" id=\"comments\"><ul class=\"comment-list level-0\"></ul></div>"
            // Desktop source is also a shell (e.g. desktop fetch failed) -> no comment nodes to parse.
            desktopCommentsSource = "<div class=\"comment-box\"><ul class=\"comment-list level-0\"></ul></div>"
            // Pre-set a tree so the IO-dispatched comments prefetch is skipped (deterministic requestId).
            commentTree = Comment().apply { children.add(Comment().apply { id = 1 }) }
            html = """
                <html><body><div class="content material_item">
                Article body with enough length for render checks and stable article reload behavior.
                </div></body></html>
            """.trimIndent()
        }
        val template = mockk<ArticleTemplate> {
            every { mapEntity(any()) } answers { firstArg() }
        }
        val api = mockk<NewsApi>(relaxed = true) {
            every { fetchArticleDetails("https://4pda.to/index.php?p=42", ArticleParsePhase.FIRST_RENDER, bypassCache = false) } returns ArticleFetchResult(
                    page = article,
                    rawBody = article.html.orEmpty(),
                    response = NetworkResponse(body = article.html.orEmpty()),
                    originalUrl = "https://4pda.to/index.php?p=42",
                    probeUrl = "https://4pda.to/index.php?p=42"
            )
            every { rebalanceCommentsSource(any()) } returns false
            every { hasCommentNodeMarkup(any()) } returns false
            every { countCommentNodesInSource(any()) } returns 0
            every { commentsSourceUnderfetchesExpected(any(), 289) } returns true
            every { fetchCommentsPageSource(any(), any()) } returns null
        }
        val interactor = ArticleInteractor(
                ArticleInteractor.InitData(newsId = 42, hintCommentsCount = 289),
                NewsRepository(api, mockk<ForumUsersCacheRoom>(relaxed = true)),
                template
        )
        interactor.loadArticle(loadComments = false)
        advanceUntilIdle()

        val result = interactor.loadComments(forceReload = true)
        advanceUntilIdle()

        assertTrue(result.toString(), result is ArticleInteractor.CommentLoadResult.Error)
        assertEquals(
                "comments_page_fetch_empty",
                (result as ArticleInteractor.CommentLoadResult.Error).throwable.message
        )
    }

    @Test
    fun `empty comment-list shell with own count zero resolves to clean empty`() = runTest(dispatcher) {
        // Own count badge is 0 -> the article genuinely has no comments. An empty comment-list shell
        // here must surface as a clean EMPTY state (no scary error, no needless desktop refetch).
        val article = DetailsPage().apply {
            id = 42
            commentsCount = 0
            commentsSource = "<div class=\"comment-box\" id=\"comments\"><ul class=\"comment-list level-0\"></ul></div>"
            commentTree = Comment().apply { children.add(Comment().apply { id = 1 }) }
            html = """
                <html><body><div class="content material_item">
                Article body with enough length for render checks and stable article reload behavior.
                </div></body></html>
            """.trimIndent()
        }
        val template = mockk<ArticleTemplate> {
            every { mapEntity(any()) } answers { firstArg() }
        }
        val api = mockk<NewsApi>(relaxed = true) {
            every { fetchArticleDetails("https://4pda.to/index.php?p=42", ArticleParsePhase.FIRST_RENDER, bypassCache = false) } returns ArticleFetchResult(
                    page = article,
                    rawBody = article.html.orEmpty(),
                    response = NetworkResponse(body = article.html.orEmpty()),
                    originalUrl = "https://4pda.to/index.php?p=42",
                    probeUrl = "https://4pda.to/index.php?p=42"
            )
            every { rebalanceCommentsSource(any()) } returns false
            every { hasCommentNodeMarkup(any()) } returns false
            every { countCommentNodesInSource(any()) } returns 0
            every { fetchCommentsPageSource(any(), any()) } returns null
        }
        val interactor = ArticleInteractor(
                ArticleInteractor.InitData(newsId = 42, hintCommentsCount = 0),
                NewsRepository(api, mockk<ForumUsersCacheRoom>(relaxed = true)),
                template
        )
        interactor.loadArticle(loadComments = false)
        advanceUntilIdle()

        val result = interactor.loadComments(forceReload = true)
        advanceUntilIdle()

        assertTrue(result.toString(), result is ArticleInteractor.CommentLoadResult.Empty)
    }

    @Test
    fun `empty mobile shell with positive own count fetches desktop comments`() = runTest(dispatcher) {
        // Happy path: mobile ships an empty shell, but the desktop page renders the real comment
        // list inline. The interactor must fetch the desktop source and parse its nodes (Loaded),
        // instead of giving up with Empty/Error.
        val desktopCommentsHtml =
                "<ul class=\"comment-list level-0\"><li id=\"comment-1001\">real comment</li></ul>"
        val article = DetailsPage().apply {
            id = 42
            commentsCount = 289
            commentsSource = null
            commentTree = Comment().apply { children.add(Comment().apply { id = 1 }) }
            html = """
                <html><body><div class="content material_item">
                Article body with enough length for render checks and stable article reload behavior.
                </div></body></html>
            """.trimIndent()
        }
        val template = mockk<ArticleTemplate> {
            every { mapEntity(any()) } answers { firstArg() }
        }
        val parsedDesktopTree = Comment().apply { children.add(Comment().apply { id = 1001 }) }
        val fetchResult = ArticleFetchResult(
                page = article,
                rawBody = article.html.orEmpty(),
                response = NetworkResponse(body = article.html.orEmpty()),
                originalUrl = "https://4pda.to/index.php?p=42",
                probeUrl = "https://4pda.to/index.php?p=42"
        )
        val api = mockk<NewsApi>(relaxed = true) {
            every { fetchArticleDetails("https://4pda.to/index.php?p=42", ArticleParsePhase.FIRST_RENDER, bypassCache = false) } returns fetchResult
            every { fetchArticleDetails("https://4pda.to/index.php?p=42", ArticleParsePhase.FULL, bypassCache = false) } returns fetchResult
            coEvery { enrichDesktopExtras(any()) } answers { firstArg<ArticleFetchResult>().page }
            every { enrichArticleMetadata(any(), any()) } returns Unit
            every { rebalanceCommentsSource(any()) } returns false
            every { hasCommentNodeMarkup(match { it == desktopCommentsHtml }) } returns true
            every { hasCommentNodeMarkup(match { it != desktopCommentsHtml }) } returns false
            every { commentsSourceUnderfetchesExpected(null, 289) } returns true
            every { commentsSourceUnderfetchesExpected(match { it != desktopCommentsHtml }, 289) } returns true
            every { commentsSourceUnderfetchesExpected(desktopCommentsHtml, 289) } returns true
            every { fetchCommentsPageSource("https://4pda.to/index.php?p=42", 1) } returns desktopCommentsHtml
            every { parseCommentsFromSource(any(), desktopCommentsHtml, paginated = true) } returns parsedDesktopTree
        }
        val interactor = ArticleInteractor(
                ArticleInteractor.InitData(newsId = 42, hintCommentsCount = 289),
                NewsRepository(api, mockk<ForumUsersCacheRoom>(relaxed = true)),
                template
        )
        interactor.loadArticle(loadComments = false)
        advanceUntilIdle()

        val result = interactor.loadComments(forceReload = true)
        advanceUntilIdle()

        assertTrue(result.toString(), result is ArticleInteractor.CommentLoadResult.Loaded)
        val loaded = result as ArticleInteractor.CommentLoadResult.Loaded
        assertEquals(1, loaded.tree.children.size)
        assertTrue(loaded.hasMore)
        coVerify(exactly = 1) { api.fetchCommentsPageSource("https://4pda.to/index.php?p=42", 1) }
    }

    @Test
    fun `partial mobile batch loads paginated first page without desktop merge`() = runTest(dispatcher) {
        val partialMobileHtml = buildString {
            append("""<ul class="comment-list level-0">""")
            repeat(10) { index ->
                val id = 2000 + index
                append("""<li><div id="comment-$id"><div class="content">partial $id</div></div></li>""")
            }
            append("</ul>")
        }
        val desktopCommentsHtml = buildString {
            append("""<ul class="comment-list level-0">""")
            repeat(40) { index ->
                val id = 3000 + index
                append("""<li><div id="comment-$id"><div class="content">full $id</div></div></li>""")
            }
            append("</ul>")
        }
        val networkPageHtml = buildString {
            append("""<ul class="comment-list level-0">""")
            repeat(20) { index ->
                val id = 2500 + index
                append("""<li><div id="comment-$id"><div class="content">net $id</div></div></li>""")
            }
            append("</ul>")
        }
        val networkTree = Comment().apply {
            repeat(20) { index -> children.add(Comment().apply { id = 2500 + index }) }
        }
        val article = DetailsPage().apply {
            id = 457102
            commentsCount = 40
            commentsSource = partialMobileHtml
            commentTree = Comment().apply { children.add(Comment().apply { id = 1 }) }
            url = "https://4pda.to/index.php?p=457102"
            html = """
                <html><body><div class="content material_item">
                Article body with enough length for render checks and stable article reload behavior.
                </div></body></html>
            """.trimIndent()
        }
        val template = mockk<ArticleTemplate> {
            every { mapEntity(any()) } answers { firstArg() }
        }
        val api = mockk<NewsApi>(relaxed = true) {
            every { fetchArticleDetails("https://4pda.to/index.php?p=457102", ArticleParsePhase.FIRST_RENDER, bypassCache = false) } returns ArticleFetchResult(
                    page = article,
                    rawBody = article.html.orEmpty(),
                    response = NetworkResponse(body = article.html.orEmpty()),
                    originalUrl = "https://4pda.to/index.php?p=457102",
                    probeUrl = "https://4pda.to/index.php?p=457102"
            )
            every { rebalanceCommentsSource(any()) } returns false
            every { countCommentNodesInSource(partialMobileHtml) } returns 10
            every { countCommentNodesInSource(networkPageHtml) } returns 20
            every { countCommentNodesInSource(desktopCommentsHtml) } returns 40
            every { commentsSourceUnderfetchesExpected(partialMobileHtml, 40) } returns true
            every { commentsSourceUnderfetchesExpected(desktopCommentsHtml, 40) } returns false
            every { commentsSourceUnderfetchesExpected(match { it != partialMobileHtml && it != desktopCommentsHtml && it != networkPageHtml }, any()) } returns false
            every { hasCommentNodeMarkup(partialMobileHtml) } returns true
            every { hasCommentNodeMarkup(networkPageHtml) } returns true
            every { hasCommentNodeMarkup(desktopCommentsHtml) } returns true
            every { hasCommentNodeMarkup(match { it != partialMobileHtml && it != desktopCommentsHtml && it != networkPageHtml }) } returns false
            every { fetchCommentsPageSource("https://4pda.to/index.php?p=457102", 1) } returns networkPageHtml
            every { parseCommentsFromSource(any(), networkPageHtml, paginated = true, commentPage = 1) } returns networkTree
        }
        val interactor = ArticleInteractor(
                ArticleInteractor.InitData(newsId = 457102, hintCommentsCount = 40),
                NewsRepository(api, mockk<ForumUsersCacheRoom>(relaxed = true)),
                template
        )
        interactor.loadArticle(loadComments = false)
        advanceUntilIdle()

        val result = interactor.loadComments(forceReload = true)
        advanceUntilIdle()

        assertTrue(result.toString(), result is ArticleInteractor.CommentLoadResult.Loaded)
        val loaded = result as ArticleInteractor.CommentLoadResult.Loaded
        assertEquals(20, loaded.tree.children.size)
        assertTrue(loaded.hasMore)
        coVerify(exactly = 0) { api.loadDesktopCommentsSource(any()) }
        coVerify(exactly = 1) { api.fetchCommentsPageSource("https://4pda.to/index.php?p=457102", 1) }
        coVerify(exactly = 0) { api.parseCommentsFromSource(any(), partialMobileHtml, paginated = true, commentPage = 1) }
    }

    @Test
    fun `prefetch is skipped and oversized embedded batch slices locally`() = runTest(dispatcher) {
        val fullEmbedded = buildString {
            append("""<ul class="comment-list level-0">""")
            repeat(50) { index ->
                val id = 5000 + index
                append("""<li><div id="comment-$id"><div class="content">body $id</div></div></li>""")
            }
            append("</ul>")
        }
        val slicedTree = Comment().apply {
            repeat(20) { index -> children.add(Comment().apply { id = 5000 + index }) }
        }
        val article = DetailsPage().apply {
            id = 457253
            commentsCount = 353
            commentsSource = fullEmbedded
            url = "https://4pda.to/index.php?p=457253"
            html = """
                <html><body><div class="content material_item">
                Article body with enough length for render checks and stable article reload behavior.
                </div></body></html>
            """.trimIndent()
        }
        val template = mockk<ArticleTemplate> {
            every { mapEntity(any()) } answers { firstArg() }
        }
        val api = mockk<NewsApi>(relaxed = true) {
            every { fetchArticleDetails("https://4pda.to/index.php?p=457253", ArticleParsePhase.FIRST_RENDER, bypassCache = false) } returns ArticleFetchResult(
                    page = article,
                    rawBody = article.html.orEmpty(),
                    response = NetworkResponse(body = article.html.orEmpty()),
                    originalUrl = "https://4pda.to/index.php?p=457253",
                    probeUrl = "https://4pda.to/index.php?p=457253"
            )
            every { rebalanceCommentsSource(any()) } returns false
            every { hasCommentNodeMarkup(fullEmbedded) } returns true
            every { commentsSourceUnderfetchesExpected(fullEmbedded, 353) } returns false
            every { countCommentNodesInSource(fullEmbedded) } returns 50
            every {
                parseCommentsFromSource(any(), fullEmbedded, paginated = true, commentPage = 1)
            } returns slicedTree
        }
        val interactor = ArticleInteractor(
                ArticleInteractor.InitData(newsId = 457253, hintCommentsCount = 353),
                NewsRepository(api, mockk<ForumUsersCacheRoom>(relaxed = true)),
                template
        )
        interactor.loadArticle(loadComments = false)
        advanceUntilIdle()

        interactor.prefetchCommentsIfNeeded("test")
        advanceUntilIdle()
        coVerify(exactly = 0) { api.parseComments(any()) }

        val result = interactor.loadComments()
        advanceUntilIdle()

        assertTrue(result.toString(), result is ArticleInteractor.CommentLoadResult.Loaded)
        val loaded = result as ArticleInteractor.CommentLoadResult.Loaded
        assertEquals(20, loaded.tree.children.size)
        assertEquals(5000, loaded.tree.children.first().id)
        assertTrue(loaded.hasMore)
        coVerify(exactly = 0) { api.fetchCommentsPageSource(any(), any()) }
        coVerify(exactly = 1) {
            api.parseCommentsFromSource(any(), fullEmbedded, paginated = true, commentPage = 1)
        }
    }

    @Test
    fun `partial mobile batch loads first page with hasMore for paginated fetch`() = runTest(dispatcher) {
        val partialHtml = buildString {
            append("""<ul class="comment-list level-0">""")
            repeat(10) { index ->
                val id = 4000 + index
                append("""<li><div id="comment-$id"><div class="content">partial $id</div></div></li>""")
            }
            append("</ul>")
        }
        val networkPageHtml = buildString {
            append("""<ul class="comment-list level-0">""")
            repeat(20) { index ->
                val id = 4100 + index
                append("""<li><div id="comment-$id"><div class="content">net $id</div></div></li>""")
            }
            append("</ul>")
        }
        val networkTree = Comment().apply {
            repeat(20) { index -> children.add(Comment().apply { id = 4100 + index }) }
        }
        val article = DetailsPage().apply {
            id = 457253
            commentsCount = 353
            commentsSource = partialHtml
            url = "https://4pda.to/index.php?p=457253"
            html = """
                <html><body><div class="content material_item">
                Article body with enough length for render checks and stable article reload behavior.
                </div></body></html>
            """.trimIndent()
        }
        val template = mockk<ArticleTemplate> {
            every { mapEntity(any()) } answers { firstArg() }
        }
        val api = mockk<NewsApi>(relaxed = true) {
            every { fetchArticleDetails("https://4pda.to/index.php?p=457253", ArticleParsePhase.FIRST_RENDER, bypassCache = false) } returns ArticleFetchResult(
                    page = article,
                    rawBody = article.html.orEmpty(),
                    response = NetworkResponse(body = article.html.orEmpty()),
                    originalUrl = "https://4pda.to/index.php?p=457253",
                    probeUrl = "https://4pda.to/index.php?p=457253"
            )
            every { rebalanceCommentsSource(any()) } returns false
            every { countCommentNodesInSource(partialHtml) } returns 10
            every { countCommentNodesInSource(networkPageHtml) } returns 20
            every { commentsSourceUnderfetchesExpected(partialHtml, 353) } returns true
            every { hasCommentNodeMarkup(partialHtml) } returns true
            every { hasCommentNodeMarkup(networkPageHtml) } returns true
            every { fetchCommentsPageSource("https://4pda.to/index.php?p=457253", 1) } returns networkPageHtml
            every { parseCommentsFromSource(any(), networkPageHtml, paginated = true, commentPage = 1) } returns networkTree
        }
        val interactor = ArticleInteractor(
                ArticleInteractor.InitData(newsId = 457253, hintCommentsCount = 353),
                NewsRepository(api, mockk<ForumUsersCacheRoom>(relaxed = true)),
                template
        )
        interactor.loadArticle(loadComments = false)
        advanceUntilIdle()

        val result = interactor.loadComments(forceReload = true)
        advanceUntilIdle()

        assertTrue(result.toString(), result is ArticleInteractor.CommentLoadResult.Loaded)
        val loaded = result as ArticleInteractor.CommentLoadResult.Loaded
        assertEquals(20, loaded.tree.children.size)
        assertTrue(loaded.hasMore)
        coVerify(exactly = 0) { api.parseCommentsFromSource(any(), partialHtml, paginated = true, commentPage = 1) }
        coVerify(exactly = 1) { api.fetchCommentsPageSource("https://4pda.to/index.php?p=457253", 1) }
    }

    @Test
    fun `loadComments keeps first paginated batch when stored commentTree underfetches`() = runTest(dispatcher) {
        val partialHtml = buildString {
            append("""<ul class="comment-list level-0">""")
            repeat(10) { index ->
                val id = 5000 + index
                append("""<li><div id="comment-$id"><div class="content">partial $id</div></div></li>""")
            }
            append("</ul>")
        }
        val networkPageHtml = buildString {
            append("""<ul class="comment-list level-0">""")
            repeat(20) { index ->
                val id = 6000 + index
                append("""<li><div id="comment-$id"><div class="content">net $id</div></div></li>""")
            }
            append("</ul>")
        }
        val partialTree = Comment().apply {
            repeat(10) { index -> children.add(Comment().apply { id = 5000 + index }) }
        }
        val networkTree = Comment().apply {
            repeat(20) { index -> children.add(Comment().apply { id = 6000 + index }) }
        }
        val article = DetailsPage().apply {
            id = 457181
            commentsCount = 181
            commentsSource = partialHtml
            commentTree = partialTree
            url = "https://4pda.to/index.php?p=457181"
            html = """
                <html><body><div class="content material_item">
                Article body with enough length for render checks and stable article reload behavior.
                </div></body></html>
            """.trimIndent()
        }
        val template = mockk<ArticleTemplate> {
            every { mapEntity(any()) } answers { firstArg() }
        }
        val api = mockk<NewsApi>(relaxed = true) {
            every { fetchArticleDetails("https://4pda.to/index.php?p=457181", ArticleParsePhase.FIRST_RENDER, bypassCache = false) } returns ArticleFetchResult(
                    page = article,
                    rawBody = article.html.orEmpty(),
                    response = NetworkResponse(body = article.html.orEmpty()),
                    originalUrl = "https://4pda.to/index.php?p=457181",
                    probeUrl = "https://4pda.to/index.php?p=457181"
            )
            every { rebalanceCommentsSource(any()) } returns false
            every { countCommentNodesInSource(partialHtml) } returns 10
            every { countCommentNodesInSource(networkPageHtml) } returns 20
            every { commentsSourceUnderfetchesExpected(partialHtml, 181) } returns true
            every { hasCommentNodeMarkup(partialHtml) } returns true
            every { hasCommentNodeMarkup(networkPageHtml) } returns true
            every { fetchCommentsPageSource("https://4pda.to/index.php?p=457181", 1) } returns networkPageHtml
            every { parseCommentsFromSource(any(), networkPageHtml, paginated = true, commentPage = 1) } returns networkTree
        }
        val interactor = ArticleInteractor(
                ArticleInteractor.InitData(newsId = 457181, hintCommentsCount = 181),
                NewsRepository(api, mockk<ForumUsersCacheRoom>(relaxed = true)),
                template
        )
        interactor.loadArticle(loadComments = false)
        advanceUntilIdle()

        val result = interactor.loadComments(forceReload = false)
        advanceUntilIdle()

        assertTrue(result.toString(), result is ArticleInteractor.CommentLoadResult.Loaded)
        val loaded = result as ArticleInteractor.CommentLoadResult.Loaded
        assertEquals(20, loaded.tree.children.size)
        assertTrue(loaded.hasMore)
        coVerify(exactly = 0) { api.loadDesktopCommentsSource(any()) }
        coVerify(exactly = 1) { api.fetchCommentsPageSource("https://4pda.to/index.php?p=457181", 1) }
    }

    @Test
    fun `loadCommentsNextPage fetches cp page and appends without desktop merge`() = runTest(dispatcher) {
        fun batchHtml(startId: Int, count: Int): String = buildString {
            append("""<ul class="comment-list level-0">""")
            repeat(count) { index ->
                val id = startId + index
                append("""<li><div id="comment-$id"><div class="content">c$id</div></div></li>""")
            }
            append("</ul>")
        }
        fun batchTree(startId: Int, count: Int): Comment = Comment().apply {
            repeat(count) { index -> children.add(Comment().apply { id = startId + index }) }
        }
        val page1Html = batchHtml(1000, 20)
        val page2Html = batchHtml(2000, 20)
        val page1Tree = batchTree(1000, 20)
        val page2Tree = batchTree(2000, 20)
        val article = DetailsPage().apply {
            id = 457253
            commentsCount = 353
            commentsSource = page1Html
            url = "https://4pda.to/index.php?p=457253"
            html = """
                <html><body><div class="content material_item">
                Article body with enough length for render checks and stable article reload behavior.
                </div></body></html>
            """.trimIndent()
        }
        val template = mockk<ArticleTemplate> {
            every { mapEntity(any()) } answers { firstArg() }
        }
        val api = mockk<NewsApi>(relaxed = true) {
            every { fetchArticleDetails("https://4pda.to/index.php?p=457253", ArticleParsePhase.FIRST_RENDER, bypassCache = false) } returns ArticleFetchResult(
                    page = article,
                    rawBody = article.html.orEmpty(),
                    response = NetworkResponse(body = article.html.orEmpty()),
                    originalUrl = "https://4pda.to/index.php?p=457253",
                    probeUrl = "https://4pda.to/index.php?p=457253"
            )
            every { rebalanceCommentsSource(any()) } returns false
            every { countCommentNodesInSource(page1Html) } returns 20
            every { countCommentNodesInSource(page2Html) } returns 20
            every { commentsSourceUnderfetchesExpected(page1Html, 353) } returns true
            every { commentsSourceUnderfetchesExpected(page2Html, 353) } returns true
            every { hasCommentNodeMarkup(any()) } returns true
            every { parseCommentsFromSource(any(), page1Html, paginated = true, commentPage = 1) } returns page1Tree
            every { fetchCommentsPageSource("https://4pda.to/index.php?p=457253", 2) } returns page2Html
            every { parseCommentsFromSource(any(), page2Html, paginated = true, commentPage = 2) } returns page2Tree
        }
        val interactor = ArticleInteractor(
                ArticleInteractor.InitData(newsId = 457253, hintCommentsCount = 353),
                NewsRepository(api, mockk<ForumUsersCacheRoom>(relaxed = true)),
                template
        )
        interactor.loadArticle(loadComments = false)
        advanceUntilIdle()

        val first = interactor.loadComments(forceReload = true)
        advanceUntilIdle()
        assertTrue(first is ArticleInteractor.CommentLoadResult.Loaded)
        val firstLoaded = first as ArticleInteractor.CommentLoadResult.Loaded
        assertEquals(20, firstLoaded.tree.children.size)
        assertTrue(firstLoaded.hasMore)

        val second = interactor.loadCommentsNextPage()
        advanceUntilIdle()
        assertTrue(second is ArticleInteractor.CommentLoadResult.Loaded)
        val secondLoaded = second as ArticleInteractor.CommentLoadResult.Loaded
        assertEquals(40, secondLoaded.tree.children.size)
        assertTrue(secondLoaded.append)
        assertTrue(secondLoaded.hasMore)
        coVerify(exactly = 1) { api.fetchCommentsPageSource("https://4pda.to/index.php?p=457253", 2) }
        coVerify(exactly = 0) { api.loadDesktopCommentsSource(any()) }
    }

    @Test
    fun `loadCommentsNextPage appends when cp page html still contains full desktop list`() = runTest(dispatcher) {
        fun fullHtml(count: Int): String = buildString {
            append("""<ul class="comment-list level-0">""")
            repeat(count) { index ->
                val id = 5000 + index
                append("""<li><div id="comment-$id"><div class="content">c$id</div></div></li>""")
            }
            append("</ul>")
        }
        val fullListHtml = fullHtml(60)
        val article = DetailsPage().apply {
            id = 457253
            commentsCount = 417
            commentsSource = fullListHtml
            url = "https://4pda.to/index.php?p=457253"
            html = """
                <html><body><div class="content material_item">
                Article body with enough length for render checks and stable article reload behavior.
                </div></body></html>
            """.trimIndent()
        }
        val template = mockk<ArticleTemplate> {
            every { mapEntity(any()) } answers { firstArg() }
        }
        val parser = ArticleParser(CommentsValidationPatternProvider())
        val realApi = NewsApi(CommentsValidationWebClient(), parser)
        val api = mockk<NewsApi>(relaxed = true) {
            every {
                fetchArticleDetails(
                        "https://4pda.to/index.php?p=457253",
                        ArticleParsePhase.FIRST_RENDER,
                        bypassCache = false
                )
            } returns ArticleFetchResult(
                    page = article,
                    rawBody = article.html.orEmpty(),
                    response = NetworkResponse(body = article.html.orEmpty()),
                    originalUrl = "https://4pda.to/index.php?p=457253",
                    probeUrl = "https://4pda.to/index.php?p=457253"
            )
            every { rebalanceCommentsSource(any()) } returns false
            every { hasCommentNodeMarkup(any()) } returns true
            every { countCommentNodesInSource(fullListHtml) } returns 60
            every { commentsSourceUnderfetchesExpected(fullListHtml, 417) } returns true
            every { fetchCommentsPageSource("https://4pda.to/index.php?p=457253", 1) } returns fullListHtml
            every { fetchCommentsPageSource("https://4pda.to/index.php?p=457253", 2) } returns fullListHtml
            every {
                parseCommentsFromSource(any(), fullListHtml, paginated = true, commentPage = 1)
            } answers {
                realApi.parseCommentsFromSource(firstArg(), fullListHtml, paginated = true, commentPage = 1)
            }
            every {
                parseCommentsFromSource(any(), fullListHtml, paginated = true, commentPage = 2)
            } answers {
                realApi.parseCommentsFromSource(firstArg(), fullListHtml, paginated = true, commentPage = 2)
            }
        }
        val interactor = ArticleInteractor(
                ArticleInteractor.InitData(newsId = 457253, hintCommentsCount = 417),
                NewsRepository(api, mockk<ForumUsersCacheRoom>(relaxed = true)),
                template
        )
        interactor.loadArticle(loadComments = false)
        advanceUntilIdle()

        val first = interactor.loadComments(forceReload = true)
        advanceUntilIdle()
        assertTrue(first is ArticleInteractor.CommentLoadResult.Loaded)
        val firstLoaded = first as ArticleInteractor.CommentLoadResult.Loaded
        assertEquals(20, firstLoaded.tree.children.size)
        assertEquals(5000, firstLoaded.tree.children.first().id)

        val second = interactor.loadCommentsNextPage()
        advanceUntilIdle()
        assertTrue(second is ArticleInteractor.CommentLoadResult.Loaded)
        val secondLoaded = second as ArticleInteractor.CommentLoadResult.Loaded
        assertEquals(40, secondLoaded.tree.children.size)
        assertEquals(5020, secondLoaded.tree.children[20].id)
        assertTrue(secondLoaded.append)
        assertTrue(secondLoaded.hasMore)
        coVerify(exactly = 0) {
            api.parseCommentsFromSource(any(), fullListHtml, paginated = false, commentPage = any())
        }
    }

    @Test
    fun `loadCommentsNextPage third batch slices full embedded html locally`() = runTest(dispatcher) {
        fun fullHtml(count: Int): String = buildString {
            append("""<ul class="comment-list level-0">""")
            repeat(count) { index ->
                val id = 6000 + index
                append("""<li><div id="comment-$id"><div class="content">c$id</div></div></li>""")
            }
            append("</ul>")
        }
        val fullListHtml = fullHtml(80)
        val article = DetailsPage().apply {
            id = 457253
            commentsCount = 80
            commentsSource = fullListHtml
            url = "https://4pda.to/index.php?p=457253"
            html = """
                <html><body><div class="content material_item">
                Article body with enough length for render checks and stable article reload behavior.
                </div></body></html>
            """.trimIndent()
        }
        val template = mockk<ArticleTemplate> {
            every { mapEntity(any()) } answers { firstArg() }
        }
        val parser = ArticleParser(CommentsValidationPatternProvider())
        val realApi = NewsApi(CommentsValidationWebClient(), parser)
        val api = mockk<NewsApi>(relaxed = true) {
            every {
                fetchArticleDetails(
                        "https://4pda.to/index.php?p=457253",
                        ArticleParsePhase.FIRST_RENDER,
                        bypassCache = false
                )
            } returns ArticleFetchResult(
                    page = article,
                    rawBody = article.html.orEmpty(),
                    response = NetworkResponse(body = article.html.orEmpty()),
                    originalUrl = "https://4pda.to/index.php?p=457253",
                    probeUrl = "https://4pda.to/index.php?p=457253"
            )
            every { rebalanceCommentsSource(any()) } returns false
            every { hasCommentNodeMarkup(any()) } returns true
            every { countCommentNodesInSource(fullListHtml) } returns 80
            every { commentsSourceUnderfetchesExpected(fullListHtml, 80) } returns false
            every {
                parseCommentsFromSource(any(), fullListHtml, paginated = true, commentPage = 1)
            } answers {
                realApi.parseCommentsFromSource(firstArg(), fullListHtml, paginated = true, commentPage = 1)
            }
            every {
                parseCommentsFromSource(any(), fullListHtml, paginated = true, commentPage = 2)
            } answers {
                realApi.parseCommentsFromSource(firstArg(), fullListHtml, paginated = true, commentPage = 2)
            }
            every {
                parseCommentsFromSource(any(), fullListHtml, paginated = true, commentPage = 3)
            } answers {
                realApi.parseCommentsFromSource(firstArg(), fullListHtml, paginated = true, commentPage = 3)
            }
        }
        val interactor = ArticleInteractor(
                ArticleInteractor.InitData(newsId = 457253, hintCommentsCount = 80),
                NewsRepository(api, mockk<ForumUsersCacheRoom>(relaxed = true)),
                template
        )
        interactor.loadArticle(loadComments = false)
        advanceUntilIdle()

        interactor.loadComments(forceReload = true)
        advanceUntilIdle()
        interactor.loadCommentsNextPage()
        advanceUntilIdle()
        val third = interactor.loadCommentsNextPage()
        advanceUntilIdle()

        assertTrue(third is ArticleInteractor.CommentLoadResult.Loaded)
        val thirdLoaded = third as ArticleInteractor.CommentLoadResult.Loaded
        assertEquals(60, thirdLoaded.tree.children.size)
        assertEquals(6040, thirdLoaded.tree.children[40].id)
        coVerify(exactly = 1) {
            api.parseCommentsFromSource(any(), fullListHtml, paginated = true, commentPage = 3)
        }
        coVerify(exactly = 0) {
            api.parseCommentsFromSource(any(), fullListHtml, paginated = false, commentPage = any())
        }
        coVerify(exactly = 0) { api.fetchCommentsPageSource(any(), any()) }
    }

    @Test
    fun `desktop commentsSource after metadata resolve slices first batch locally`() = runTest(dispatcher) {
        val desktopHtml = buildString {
            append("""<ul class="comment-list level-0">""")
            repeat(353) { index ->
                val id = 7000 + index
                append("""<li><div id="comment-$id"><div class="content">d$id</div></div></li>""")
            }
            append("</ul>")
        }
        val page1Html = buildString {
            append("""<ul class="comment-list level-0">""")
            repeat(20) { index ->
                val id = 8000 + index
                append("""<li><div id="comment-$id"><div class="content">p$id</div></div></li>""")
            }
            append("</ul>")
        }
        val page1Tree = Comment().apply {
            repeat(20) { index -> children.add(Comment().apply { id = 8000 + index }) }
        }
        val article = DetailsPage().apply {
            id = 457999
            commentsCount = 353
            commentsSource = desktopHtml
            url = "https://4pda.to/index.php?p=457999"
            html = """
                <html><body><div class="content material_item">
                Article body with enough length for render checks and stable article reload behavior.
                </div></body></html>
            """.trimIndent()
        }
        val template = mockk<ArticleTemplate> {
            every { mapEntity(any()) } answers { firstArg() }
        }
        val api = mockk<NewsApi>(relaxed = true) {
            every { fetchArticleDetails("https://4pda.to/index.php?p=457999", ArticleParsePhase.FIRST_RENDER, bypassCache = false) } returns ArticleFetchResult(
                    page = article,
                    rawBody = article.html.orEmpty(),
                    response = NetworkResponse(body = article.html.orEmpty()),
                    originalUrl = "https://4pda.to/index.php?p=457999",
                    probeUrl = "https://4pda.to/index.php?p=457999"
            )
            every { rebalanceCommentsSource(any()) } returns false
            every { hasCommentNodeMarkup(any()) } returns true
            every { countCommentNodesInSource(desktopHtml) } returns 353
            every { commentsSourceUnderfetchesExpected(desktopHtml, 353) } returns false
            every {
                parseCommentsFromSource(any(), desktopHtml, paginated = true, commentPage = 1)
            } returns page1Tree
        }
        val interactor = ArticleInteractor(
                ArticleInteractor.InitData(newsId = 457999, hintCommentsCount = 353),
                NewsRepository(api, mockk<ForumUsersCacheRoom>(relaxed = true)),
                template
        )
        interactor.loadArticle(loadComments = false)
        advanceUntilIdle()

        val result = interactor.loadComments(forceReload = true)
        advanceUntilIdle()

        assertTrue(result is ArticleInteractor.CommentLoadResult.Loaded)
        val loaded = result as ArticleInteractor.CommentLoadResult.Loaded
        assertEquals(20, loaded.tree.children.size)
        assertTrue(loaded.hasMore)
        coVerify(exactly = 0) { api.parseComments(any()) }
        coVerify(exactly = 0) { api.fetchCommentsPageSource(any(), any()) }
        coVerify(exactly = 1) {
            api.parseCommentsFromSource(any(), desktopHtml, paginated = true, commentPage = 1)
        }
    }

    @Test
    fun `loadComments first batch is 20 when article has 21 comments`() = runTest(dispatcher) {
        fun batchHtml(startId: Int, count: Int): String = buildString {
            append("""<ul class="comment-list level-0">""")
            repeat(count) { index ->
                val id = startId + index
                append("""<li><div id="comment-$id"><div class="content">c$id</div></div></li>""")
            }
            append("</ul>")
        }
        fun batchTree(startId: Int, count: Int): Comment = Comment().apply {
            repeat(count) { index -> children.add(Comment().apply { id = startId + index }) }
        }
        val embeddedHtml = batchHtml(9000, 20)
        val page1Tree = batchTree(9000, 20)
        val article = DetailsPage().apply {
            id = 457021
            commentsCount = 21
            commentsSource = embeddedHtml
            url = "https://4pda.to/index.php?p=457021"
            html = """
                <html><body><div class="content material_item">
                Article body with enough length for render checks and stable article reload behavior.
                </div></body></html>
            """.trimIndent()
        }
        val template = mockk<ArticleTemplate> {
            every { mapEntity(any()) } answers { firstArg() }
        }
        val api = mockk<NewsApi>(relaxed = true) {
            every { fetchArticleDetails("https://4pda.to/index.php?p=457021", ArticleParsePhase.FIRST_RENDER, bypassCache = false) } returns ArticleFetchResult(
                    page = article,
                    rawBody = article.html.orEmpty(),
                    response = NetworkResponse(body = article.html.orEmpty()),
                    originalUrl = "https://4pda.to/index.php?p=457021",
                    probeUrl = "https://4pda.to/index.php?p=457021"
            )
            every { rebalanceCommentsSource(any()) } returns false
            every { hasCommentNodeMarkup(any()) } returns true
            every { countCommentNodesInSource(embeddedHtml) } returns 20
            every { commentsSourceUnderfetchesExpected(embeddedHtml, 21) } returns true
            every {
                parseCommentsFromSource(any(), embeddedHtml, paginated = true, commentPage = 1)
            } returns page1Tree
        }
        val interactor = ArticleInteractor(
                ArticleInteractor.InitData(newsId = 457021, hintCommentsCount = 21),
                NewsRepository(api, mockk<ForumUsersCacheRoom>(relaxed = true)),
                template
        )
        interactor.loadArticle(loadComments = false)
        advanceUntilIdle()

        val result = interactor.loadComments(forceReload = true)
        advanceUntilIdle()

        assertTrue(result is ArticleInteractor.CommentLoadResult.Loaded)
        val loaded = result as ArticleInteractor.CommentLoadResult.Loaded
        assertEquals(20, loaded.tree.children.size)
        assertTrue(loaded.hasMore)
    }

    @Test
    fun `small article with one embedded node fetches cp page for both comments`() = runTest(dispatcher) {
        val embeddedHtml = """
            <ul class="comment-list level-0">
            <li><div id="comment-9001"><div class="content">sliderpro</div></div></li>
            </ul>
        """.trimIndent()
        val networkHtml = """
            <ul class="comment-list level-0">
            <li><div id="comment-9001"><div class="content">sliderpro</div></div></li>
            <li><div id="comment-9002"><div class="content">second</div></div></li>
            </ul>
        """.trimIndent()
        val embeddedTree = Comment().apply { children.add(Comment().apply { id = 9001 }) }
        val networkTree = Comment().apply {
            children.add(Comment().apply { id = 9001 })
            children.add(Comment().apply { id = 9002 })
        }
        val article = DetailsPage().apply {
            id = 457375
            commentsCount = 2
            commentsSource = embeddedHtml
            url = "https://4pda.to/index.php?p=457375"
            html = """
                <html><body><div class="content material_item">
                Article body with enough length for render checks and stable article reload behavior.
                </div></body></html>
            """.trimIndent()
        }
        val template = mockk<ArticleTemplate> {
            every { mapEntity(any()) } answers { firstArg() }
        }
        val api = mockk<NewsApi>(relaxed = true) {
            every { fetchArticleDetails("https://4pda.to/index.php?p=457375", ArticleParsePhase.FIRST_RENDER, bypassCache = false) } returns ArticleFetchResult(
                    page = article,
                    rawBody = article.html.orEmpty(),
                    response = NetworkResponse(body = article.html.orEmpty()),
                    originalUrl = "https://4pda.to/index.php?p=457375",
                    probeUrl = "https://4pda.to/index.php?p=457375"
            )
            every { rebalanceCommentsSource(any()) } returns false
            every { hasCommentNodeMarkup(embeddedHtml) } returns true
            every { hasCommentNodeMarkup(networkHtml) } returns true
            every { countCommentNodesInSource(embeddedHtml) } returns 1
            every { countCommentNodesInSource(networkHtml) } returns 2
            every { commentsSourceUnderfetchesExpected(embeddedHtml, 2) } returns true
            every { fetchCommentsPageSource("https://4pda.to/index.php?p=457375", 1) } returns networkHtml
            every { parseCommentsFromSource(any(), networkHtml, paginated = true, commentPage = 1) } returns networkTree
        }
        val interactor = ArticleInteractor(
                ArticleInteractor.InitData(newsId = 457375, hintCommentsCount = 2),
                NewsRepository(api, mockk<ForumUsersCacheRoom>(relaxed = true)),
                template
        )
        interactor.loadArticle(loadComments = false)
        advanceUntilIdle()

        val result = interactor.loadComments(forceReload = true)
        advanceUntilIdle()

        assertTrue(result.toString(), result is ArticleInteractor.CommentLoadResult.Loaded)
        val loaded = result as ArticleInteractor.CommentLoadResult.Loaded
        assertEquals(2, loaded.tree.children.size)
        assertFalse(loaded.hasMore)
        coVerify(exactly = 0) { api.parseCommentsFromSource(any(), embeddedHtml, paginated = true, commentPage = 1) }
        coVerify(exactly = 1) { api.fetchCommentsPageSource("https://4pda.to/index.php?p=457375", 1) }
    }

    @Test
    fun `loadCommentsNextPage loads 20 then 20 then 15 for 55 total`() = runTest(dispatcher) {
        fun batchHtml(startId: Int, count: Int): String = buildString {
            append("""<ul class="comment-list level-0">""")
            repeat(count) { index ->
                val id = startId + index
                append("""<li><div id="comment-$id"><div class="content">c$id</div></div></li>""")
            }
            append("</ul>")
        }
        fun batchTree(startId: Int, count: Int): Comment = Comment().apply {
            repeat(count) { index -> children.add(Comment().apply { id = startId + index }) }
        }
        val page1Html = batchHtml(100, 20)
        val page2Html = batchHtml(200, 20)
        val page3Html = batchHtml(300, 15)
        val page1Tree = batchTree(100, 20)
        val page2Tree = batchTree(200, 20)
        val page3Tree = batchTree(300, 15)
        val article = DetailsPage().apply {
            id = 457055
            commentsCount = 55
            commentsSource = page1Html
            url = "https://4pda.to/index.php?p=457055"
            html = """
                <html><body><div class="content material_item">
                Article body with enough length for render checks and stable article reload behavior.
                </div></body></html>
            """.trimIndent()
        }
        val template = mockk<ArticleTemplate> {
            every { mapEntity(any()) } answers { firstArg() }
        }
        val api = mockk<NewsApi>(relaxed = true) {
            every { fetchArticleDetails("https://4pda.to/index.php?p=457055", ArticleParsePhase.FIRST_RENDER, bypassCache = false) } returns ArticleFetchResult(
                    page = article,
                    rawBody = article.html.orEmpty(),
                    response = NetworkResponse(body = article.html.orEmpty()),
                    originalUrl = "https://4pda.to/index.php?p=457055",
                    probeUrl = "https://4pda.to/index.php?p=457055"
            )
            every { rebalanceCommentsSource(any()) } returns false
            every { hasCommentNodeMarkup(any()) } returns true
            every { countCommentNodesInSource(page1Html) } returns 20
            every { countCommentNodesInSource(page2Html) } returns 20
            every { countCommentNodesInSource(page3Html) } returns 15
            every { commentsSourceUnderfetchesExpected(page1Html, 55) } returns true
            every { parseCommentsFromSource(any(), page1Html, paginated = true, commentPage = 1) } returns page1Tree
            every { fetchCommentsPageSource("https://4pda.to/index.php?p=457055", 2) } returns page2Html
            every { parseCommentsFromSource(any(), page2Html, paginated = true, commentPage = 2) } returns page2Tree
            every { fetchCommentsPageSource("https://4pda.to/index.php?p=457055", 3) } returns page3Html
            every { parseCommentsFromSource(any(), page3Html, paginated = true, commentPage = 3) } returns page3Tree
        }
        val interactor = ArticleInteractor(
                ArticleInteractor.InitData(newsId = 457055, hintCommentsCount = 55),
                NewsRepository(api, mockk<ForumUsersCacheRoom>(relaxed = true)),
                template
        )
        interactor.loadArticle(loadComments = false)
        advanceUntilIdle()

        val first = interactor.loadComments(forceReload = true)
        advanceUntilIdle()
        assertTrue(first is ArticleInteractor.CommentLoadResult.Loaded)
        val firstLoaded = first as ArticleInteractor.CommentLoadResult.Loaded
        assertEquals(20, firstLoaded.tree.children.size)
        assertTrue(firstLoaded.hasMore)

        val second = interactor.loadCommentsNextPage()
        advanceUntilIdle()
        assertTrue(second is ArticleInteractor.CommentLoadResult.Loaded)
        val secondLoaded = second as ArticleInteractor.CommentLoadResult.Loaded
        assertEquals(40, secondLoaded.tree.children.size)
        assertTrue(secondLoaded.hasMore)

        val third = interactor.loadCommentsNextPage()
        advanceUntilIdle()
        assertTrue(third is ArticleInteractor.CommentLoadResult.Loaded)
        val thirdLoaded = third as ArticleInteractor.CommentLoadResult.Loaded
        assertEquals(55, thirdLoaded.tree.children.size)
        assertFalse(thirdLoaded.hasMore)
    }

    private class CommentsValidationWebClient : IWebClient {
        override fun get(url: String): NetworkResponse = NetworkResponse(url = url)
        override fun request(request: NetworkRequest): NetworkResponse = NetworkResponse(url = request.url)
        override fun request(request: NetworkRequest, progressListener: IWebClient.ProgressListener?): NetworkResponse =
                request(request)
        override fun requestWithoutMobileCookie(request: NetworkRequest): NetworkResponse = request(request)
        override fun getClientCookies(): Map<String, okhttp3.Cookie> = emptyMap()
        override fun getAuthKey(): String = "0"
        override fun clearCookies() = Unit
        override fun createWebSocketConnection(listener: okhttp3.WebSocketListener): okhttp3.WebSocket =
                throw UnsupportedOperationException()
    }

    private class CommentsValidationPatternProvider : IPatternProvider {
        override fun getCurrentVersion(): Int = 29
        override fun update(jsonString: String) = Unit
        override fun getPattern(scope: String, key: String): Pattern {
            require(scope == ParserPatterns.Articles.scope) { scope }
            return when (key) {
                ParserPatterns.Articles.exclude_form_comment ->
                        Pattern.compile("<form[\\s\\S]*", Pattern.CASE_INSENSITIVE)
                ParserPatterns.Articles.comment_id -> Pattern.compile("comment-(\\d+)")
                ParserPatterns.Articles.comment_user_id -> Pattern.compile("showuser=(\\d+)")
                ParserPatterns.Articles.karmaSource -> Pattern.compile("a^")
                ParserPatterns.Articles.karma -> Pattern.compile("a^")
                else -> Pattern.compile("a^")
            }
        }
    }
}
