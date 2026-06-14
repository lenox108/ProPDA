package forpdateam.ru.forpda.model.interactors.news

import forpdateam.ru.forpda.entity.remote.news.Comment
import forpdateam.ru.forpda.entity.remote.news.DetailsPage
import forpdateam.ru.forpda.model.data.remote.api.NetworkResponse
import forpdateam.ru.forpda.model.data.remote.api.news.ArticleFetchResult
import forpdateam.ru.forpda.model.data.remote.api.news.ArticleParsePhase
import forpdateam.ru.forpda.model.data.remote.api.news.NewsApi
import forpdateam.ru.forpda.model.data.cache.forumuser.ForumUsersCacheRoom
import forpdateam.ru.forpda.model.repository.news.NewsRepository
import forpdateam.ru.forpda.presentation.articles.detail.ArticleTemplate
import io.mockk.every
import io.mockk.mockk
import io.mockk.coEvery
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import java.util.concurrent.atomic.AtomicInteger
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ArticleInteractorTest {

    private val dispatcher = StandardTestDispatcher()

    private fun fetchResult(page: DetailsPage) = ArticleFetchResult(
            page = page,
            rawBody = page.html.orEmpty(),
            response = NetworkResponse(code = 200, body = page.html.orEmpty()),
            originalUrl = "https://4pda.to/index.php?p=${page.id}",
            probeUrl = "https://4pda.to/index.php?p=${page.id}"
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `load comments re-emits existing comment tree without reparsing article`() = runTest(dispatcher) {
        val article = DetailsPage().apply {
            id = 42
            html = """
                <html><body><div class="content material_item">
                Article body with enough length for render checks and stable article reload behavior.
                </div></body></html>
            """.trimIndent()
            commentTree = Comment().apply {
                children.add(Comment().apply {
                    id = 7
                    userNick = "tester"
                })
            }
        }
        val template = mockk<ArticleTemplate> {
            every { mapEntity(any()) } answers { firstArg() }
            every { remapWithCurrentTheme(any()) } answers { firstArg() }
        }
        val api = mockk<NewsApi>(relaxed = true) {
            every { fetchArticleDetails("https://4pda.to/index.php?p=42", ArticleParsePhase.FIRST_RENDER, bypassCache = false) } returns fetchResult(article)
        }
        val interactor = ArticleInteractor(
                ArticleInteractor.InitData(newsId = 42),
                NewsRepository(api, mockk<ForumUsersCacheRoom>(relaxed = true)),
                template
        )

        interactor.loadArticle(loadComments = false)
        val observed = async { interactor.observeComments().first() }

        val loaded = interactor.loadComments(forceReload = false)
        advanceUntilIdle()

        assertTrue(loaded is ArticleInteractor.CommentLoadResult.Loaded)
        val loadedTree = (loaded as ArticleInteractor.CommentLoadResult.Loaded).tree
        assertEquals(7, loadedTree.children.firstOrNull()?.id)
        assertEquals(7, observed.await().children.firstOrNull()?.id)
        verify(exactly = 0) { api.parseComments(any<DetailsPage>()) }
    }

    @Test
    fun `reply comment keeps rendered article html and finds new comment id`() = runTest(dispatcher) {
        val existingHtml = """
            <html><body>
            <div class="content material_item news-detail-header">
            Article body with enough length for render checks and stable article reload behavior.
            </div></body></html>
        """.trimIndent()
        val existing = DetailsPage().apply {
            id = 42
            html = existingHtml
            commentsSource = "<ul></ul>"
        }
        val posted = DetailsPage().apply {
            id = 42
            html = "<html><body><p>Redirect page</p></body></html>"
            commentsSource = "<ul><li id=\"comment-99\"></li></ul>"
            url = "https://4pda.to/index.php?p=42#comment-99"
        }
        val template = mockk<ArticleTemplate> {
            every { remapWithCurrentTheme(any()) } answers { firstArg() }
            every { mapEntity(any()) } answers { firstArg() }
        }
        val parsedTree = Comment().apply {
            children.add(Comment().apply { id = 99; userId = 5 })
        }
        val api = mockk<NewsApi>(relaxed = true) {
            every { fetchArticleDetails("https://4pda.to/index.php?p=42", ArticleParsePhase.FIRST_RENDER, bypassCache = false) } returns fetchResult(existing)
            coEvery { replyComment(42, 0, "hello") } returns posted
            every { parseComments(any<DetailsPage>()) } returns parsedTree
        }
        val interactor = ArticleInteractor(
                ArticleInteractor.InitData(newsId = 42),
                NewsRepository(api, mockk<ForumUsersCacheRoom>(relaxed = true)),
                template
        )
        interactor.loadArticle(loadComments = false)

        val result = interactor.replyComment(0, "hello")
        advanceUntilIdle()

        assertSame(existingHtml, result.html)
        assertEquals(99, interactor.findNewCommentId(parsedTree, setOf(7)))
        assertEquals(99, interactor.takePendingScrollCommentId())
        verify(exactly = 1) { api.parseComments(any<DetailsPage>()) }
    }

    @Test
    fun `reply comment emits refreshed tree after article load`() = runTest(dispatcher) {
        val existingHtml = """
            <html><body>
            <div class="content material_item news-detail-header">
            Article body with enough length for render checks and stable article reload behavior.
            </div></body></html>
        """.trimIndent()
        val existing = DetailsPage().apply {
            id = 42
            html = existingHtml
            commentsSource = "<ul><li id=\"comment-7\"></li></ul>"
        }
        val posted = DetailsPage().apply {
            id = 42
            html = existingHtml
            commentsSource = "<ul><li id=\"comment-7\"></li><li id=\"comment-99\"></li></ul>"
            url = "https://4pda.to/index.php?p=42#comment-99"
        }
        val refreshedTree = Comment().apply {
            children.add(Comment().apply { id = 7; userId = 5 })
            children.add(Comment().apply { id = 99; userId = 6; userNick = "newbie" })
        }
        val template = mockk<ArticleTemplate> {
            every { remapWithCurrentTheme(any()) } answers { firstArg() }
            every { mapEntity(any()) } answers { firstArg() }
        }
        val api = mockk<NewsApi>(relaxed = true) {
            every { fetchArticleDetails("https://4pda.to/index.php?p=42", ArticleParsePhase.FIRST_RENDER, bypassCache = false) } returns fetchResult(existing)
            coEvery { replyComment(42, 0, "hello") } returns posted
            every { parseComments(any<DetailsPage>()) } returns refreshedTree
        }
        val interactor = ArticleInteractor(
                ArticleInteractor.InitData(newsId = 42),
                NewsRepository(api, mockk<ForumUsersCacheRoom>(relaxed = true)),
                template
        )
        interactor.resetForNewOpen()
        interactor.loadArticle(loadComments = false)
        advanceUntilIdle()
        assertTrue(interactor.currentArticleGeneration() != interactor.currentCommentsGeneration())

        val emissions = mutableListOf<Comment>()
        val collectJob = launch { interactor.observeComments().collect { emissions.add(it) } }

        interactor.replyComment(0, "hello")
        advanceUntilIdle()

        assertTrue(emissions.isNotEmpty())
        assertEquals(99, interactor.findNewCommentId(emissions.last(), setOf(7)))
        assertEquals(99, interactor.takePendingScrollCommentId())
        verify(exactly = 1) { api.parseComments(any<DetailsPage>()) }

        collectJob.cancel()
    }

    @Test
    fun `reply comment uses redirect fragment when tree diff is empty`() = runTest(dispatcher) {
        val existingHtml = """
            <html><body>
            <div class="content material_item news-detail-header">
            Article body with enough length for render checks and stable article reload behavior.
            </div></body></html>
        """.trimIndent()
        val existing = DetailsPage().apply {
            id = 42
            html = existingHtml
            commentsSource = "<ul><li id=\"comment-7\"></li></ul>"
            commentTree = Comment().apply {
                children.add(Comment().apply { id = 7; userId = 5 })
            }
        }
        val posted = DetailsPage().apply {
            id = 42
            html = existingHtml
            commentsSource = "<ul><li id=\"comment-7\"></li></ul>"
            url = "https://4pda.to/index.php?p=42#comment-88"
        }
        val template = mockk<ArticleTemplate> {
            every { remapWithCurrentTheme(any()) } answers { firstArg() }
            every { mapEntity(any()) } answers { firstArg() }
        }
        val parsedTree = Comment().apply {
            children.add(Comment().apply { id = 7; userId = 5 })
        }
        val api = mockk<NewsApi>(relaxed = true) {
            every { fetchArticleDetails("https://4pda.to/index.php?p=42", ArticleParsePhase.FIRST_RENDER, bypassCache = false) } returns fetchResult(existing)
            coEvery { replyComment(42, 0, "hello") } returns posted
            every { parseComments(any<DetailsPage>()) } returns parsedTree
        }
        val interactor = ArticleInteractor(
                ArticleInteractor.InitData(newsId = 42),
                NewsRepository(api, mockk<ForumUsersCacheRoom>(relaxed = true)),
                template
        )
        interactor.loadArticle(loadComments = false)

        interactor.replyComment(0, "hello")
        advanceUntilIdle()

        assertEquals(88, interactor.takePendingScrollCommentId())
        verify(exactly = 1) { api.parseComments(any<DetailsPage>()) }
    }

    @Test
    fun `loadArticle uses single generation increment per request`() = runTest(dispatcher) {
        val article = DetailsPage().apply {
            id = 42
            html = """
                <html><body><div class="content material_item">
                Article body with enough length for render checks and stable article reload behavior.
                </div></body></html>
            """.trimIndent()
        }
        val template = mockk<ArticleTemplate> {
            every { mapEntity(any()) } answers { firstArg() }
            every { remapWithCurrentTheme(any()) } answers { firstArg() }
        }
        val api = mockk<NewsApi>(relaxed = true) {
            every { fetchArticleDetails("https://4pda.to/index.php?p=42", ArticleParsePhase.FIRST_RENDER, bypassCache = false) } returns fetchResult(article)
        }
        val interactor = ArticleInteractor(
                ArticleInteractor.InitData(newsId = 42),
                NewsRepository(api, mockk<ForumUsersCacheRoom>(relaxed = true)),
                template
        )
        interactor.resetForNewOpen()
        val generationBeforeLoad = interactor.currentArticleGeneration()

        interactor.loadArticle(loadComments = false)
        advanceUntilIdle()

        assertEquals(generationBeforeLoad + 1, interactor.currentArticleGeneration())
        verify(exactly = 1) { api.fetchArticleDetails("https://4pda.to/index.php?p=42", ArticleParsePhase.FIRST_RENDER, bypassCache = false) }
    }

    @Test
    fun `extractCommentIdFromUrl reads comment anchor`() {
        val interactor = ArticleInteractor(
                ArticleInteractor.InitData(newsId = 1),
                NewsRepository(mockk(relaxed = true), mockk(relaxed = true)),
                mockk(relaxed = true)
        )
        assertEquals(123, interactor.extractCommentIdFromUrl("https://4pda.to/index.php?p=1#comment-123"))
    }

    @Test
    fun `requestId guard prevents older comment load overwriting newer`() = runTest(dispatcher) {
        val article = DetailsPage().apply {
            id = 42
            html = """
                <html><body><div class="content material_item">
                Article body with enough length for render checks and stable article reload behavior.
                </div></body></html>
            """.trimIndent()
            // Keep count at 0 so loadArticle does not start IO comments prefetch (races requestId).
            commentsCount = 0
            commentsSource = "<ul><li id=\"comment-1\"></li></ul>"
            url = "https://4pda.to/index.php?p=42"
        }
        val template = mockk<ArticleTemplate> {
            every { mapEntity(any()) } answers { firstArg() }
            every { remapWithCurrentTheme(any()) } answers { firstArg() }
        }
        val treeOld = Comment().apply { children.add(Comment().apply { id = 1; userNick = "old" }) }
        val treeNew = Comment().apply { children.add(Comment().apply { id = 2; userNick = "new" }) }

        val newsRepository = mockk<NewsRepository>(relaxed = true)
        coEvery { newsRepository.fetchArticleDetails(any<Int>(), ArticleParsePhase.FIRST_RENDER, bypassCache = false) } returns fetchResult(article)
        every { newsRepository.hasCommentNodeMarkup(any()) } returns true
        every { newsRepository.countCommentNodesInSource(any()) } returns 1
        val call = AtomicInteger(0)
        val firstCallEntered = CompletableDeferred<Unit>()
        val secondCallFinished = CompletableDeferred<Unit>()
        coEvery { newsRepository.parseCommentsFromSource(any(), any(), paginated = true) } coAnswers {
            when (call.incrementAndGet()) {
                1 -> {
                    firstCallEntered.complete(Unit)
                    secondCallFinished.await()
                    treeOld
                }
                else -> {
                    val result = treeNew
                    secondCallFinished.complete(Unit)
                    result
                }
            }
        }

        val interactor = ArticleInteractor(
                ArticleInteractor.InitData(newsId = 42),
                newsRepository,
                template
        )
        interactor.resetForNewOpen()
        interactor.loadArticle(loadComments = false)
        advanceUntilIdle()

        val emissions = mutableListOf<Comment>()
        val collectJob = launch { interactor.observeComments().collect { emissions.add(it) } }

        val first = async { interactor.loadComments(forceReload = true) }
        firstCallEntered.await()
        val second = async { interactor.loadComments(forceReload = true) }
        advanceUntilIdle()

        // Newest request must win (old one should become Stale due to requestId guard).
        assertTrue(second.await() is ArticleInteractor.CommentLoadResult.Loaded)
        assertTrue(first.await() is ArticleInteractor.CommentLoadResult.Stale)
        assertTrue(emissions.isNotEmpty())
        assertEquals(2, emissions.last().children.firstOrNull()?.id)

        collectJob.cancel()
    }

    @Test
    fun `cache hit with pending extras schedules single full fetch`() = runTest(dispatcher) {
        val cachedHtml = """
            <html><body>
            <div class="content material_item news-detail-header">
            Article body with enough length for render checks and stable article reload behavior.
            </div></body></html>
        """.trimIndent()
        val cached = DetailsPage().apply {
            id = 42
            title = "Cached"
            html = cachedHtml
            commentsSource = "https://4pda.to/index.php?p=42#comments"
            commentsCount = 4
        }
        val enriched = DetailsPage().apply {
            id = 42
            title = "Cached"
            html = cachedHtml
            commentsSource = "https://4pda.to/index.php?p=42#comments"
            desktopCommentsSource = "https://4pda.to/index.php?p=42&desktop=1"
            commentsCount = 4
        }
        val template = mockk<ArticleTemplate> {
            every { mapEntity(any()) } answers { firstArg() }
            every { remapWithCurrentTheme(any()) } answers { firstArg() }
        }
        val api = mockk<NewsApi>(relaxed = true) {
            every {
                fetchArticleDetails("https://4pda.to/index.php?p=42", ArticleParsePhase.FIRST_RENDER, bypassCache = false)
            } returns fetchResult(cached)
            every {
                fetchArticleDetails("https://4pda.to/index.php?p=42", ArticleParsePhase.FULL, bypassCache = false)
            } returns fetchResult(enriched)
            coEvery { enrichDesktopExtras(any()) } answers { firstArg<ArticleFetchResult>().page }
            every { enrichArticleMetadata(any(), any()) } returns Unit
        }
        val repository = NewsRepository(api, mockk<ForumUsersCacheRoom>(relaxed = true))
        val interactor = ArticleInteractor(
                ArticleInteractor.InitData(newsId = 42),
                repository,
                template
        )
        interactor.resetForNewOpen()
        interactor.loadArticle(loadComments = false)
        advanceUntilIdle()

        interactor.loadArticle(loadComments = false)
        advanceUntilIdle()

        verify(timeout = 5000, exactly = 1) {
            api.fetchArticleDetails("https://4pda.to/index.php?p=42", ArticleParsePhase.FIRST_RENDER, bypassCache = false)
        }
        verify(timeout = 5000, exactly = 1) {
            api.fetchArticleDetails("https://4pda.to/index.php?p=42", ArticleParsePhase.FULL, bypassCache = false)
        }
    }

    @Test
    fun `findNewCommentId delegates localized text matching`() {
        val interactor = ArticleInteractor(
                ArticleInteractor.InitData(newsId = 1),
                NewsRepository(mockk(relaxed = true), mockk(relaxed = true)),
                mockk(relaxed = true)
        )
        val tree = Comment().apply {
            children.add(Comment().apply { id = 7; userId = 5 })
            children.add(Comment().apply { id = 3; userId = 5; content = "Unique localized reply body" })
        }
        assertEquals(
                3,
                interactor.findNewCommentId(tree, setOf(7), "Unique localized reply body")
        )
    }

    @Test
    fun `loadArticle applies list hint when mobile batch count undercounts feed total`() = runTest(dispatcher) {
        val article = DetailsPage().apply {
            id = 457999
            commentsCount = 20
            html = """
                <html><body><div class="content material_item">
                Article body with enough length for render checks and stable article reload behavior.
                </div></body></html>
            """.trimIndent()
        }
        val template = mockk<ArticleTemplate> {
            every { mapEntity(any()) } answers { firstArg() }
            every { remapWithCurrentTheme(any()) } answers { firstArg() }
            every { restampCommentsCountInMappedHtml(any(), 33) } answers {
                secondArg<String>().replace("Комментарии (20)", "Комментарии (33)")
            }
        }
        val api = mockk<NewsApi>(relaxed = true) {
            every {
                fetchArticleDetails("https://4pda.to/index.php?p=457999", ArticleParsePhase.FIRST_RENDER, bypassCache = false)
            } returns fetchResult(article)
        }
        val interactor = ArticleInteractor(
                ArticleInteractor.InitData(newsId = 457999, hintCommentsCount = 33),
                NewsRepository(api, mockk<ForumUsersCacheRoom>(relaxed = true)),
                template
        )
        interactor.resetForNewOpen()
        interactor.loadArticle(loadComments = false)
        advanceUntilIdle()
        assertEquals(33, interactor.expectedCommentsCount())
        assertEquals(33, interactor.observeData().first { it?.id == 457999 }?.commentsCount)
    }

    @Test
    fun `loadArticle applies list hint when mobile badge undercounts`() = runTest(dispatcher) {
        val article = DetailsPage().apply {
            id = 457253
            commentsCount = 27
            html = """
                <html><body><div class="content material_item">
                Article body with enough length for render checks and stable article reload behavior.
                </div></body></html>
            """.trimIndent()
        }
        val template = mockk<ArticleTemplate> {
            every { mapEntity(any()) } answers { firstArg() }
            every { remapWithCurrentTheme(any()) } answers { firstArg() }
        }
        val api = mockk<NewsApi>(relaxed = true) {
            every {
                fetchArticleDetails("https://4pda.to/index.php?p=457253", ArticleParsePhase.FIRST_RENDER, bypassCache = false)
            } returns fetchResult(article)
        }
        val interactor = ArticleInteractor(
                ArticleInteractor.InitData(newsId = 457253, hintCommentsCount = 353),
                NewsRepository(api, mockk<ForumUsersCacheRoom>(relaxed = true)),
                template
        )
        interactor.resetForNewOpen()
        interactor.loadArticle(loadComments = false)
        advanceUntilIdle()
        assertEquals(353, interactor.observeData().first { it?.id == 457253 }?.commentsCount)
        assertEquals(353, interactor.expectedCommentsCount())
    }

    @Test
    fun `reconcileCommentsCountFromParsed keeps badge during paginated partial batch`() = runTest(dispatcher) {
        val article = DetailsPage().apply {
            id = 457102
            commentsCount = 181
            html = """
                <html><body><div class="content material_item">
                Article body with enough length for render checks and stable article reload behavior.
                </div></body></html>
            """.trimIndent()
        }
        val template = mockk<ArticleTemplate> {
            every { mapEntity(any()) } answers { firstArg() }
            every { remapWithCurrentTheme(any()) } answers { firstArg() }
        }
        val api = mockk<NewsApi>(relaxed = true) {
            every {
                fetchArticleDetails("https://4pda.to/index.php?p=457102", ArticleParsePhase.FIRST_RENDER, bypassCache = false)
            } returns fetchResult(article)
        }
        val interactor = ArticleInteractor(
                ArticleInteractor.InitData(newsId = 457102, hintCommentsCount = 181),
                NewsRepository(api, mockk<ForumUsersCacheRoom>(relaxed = true)),
                template
        )
        interactor.resetForNewOpen()
        interactor.loadArticle(loadComments = false)
        advanceUntilIdle()
        interactor.reconcileCommentsCountFromParsed(20)
        assertEquals(181, interactor.observeData().first { it?.id == 457102 }?.commentsCount)
    }

    @Test
    fun `reconcileCommentsCountFromParsed keeps badge when paginated session active and nested replies`() = runTest(dispatcher) {
        val partialHtml = buildString {
            append("""<ul class="comment-list level-0">""")
            repeat(20) { index ->
                val id = 5000 + index
                append("""<li><div id="comment-$id"><div class="content">partial $id</div></div></li>""")
            }
            append("</ul>")
        }
        val partialTree = Comment().apply {
            repeat(20) { index -> children.add(Comment().apply { id = 5000 + index }) }
            // Nested replies inflate flattened count above COMMENTS_PER_PAGE.
            children.first().children.add(Comment().apply { id = 5999 })
            repeat(6) { index -> children[1].children.add(Comment().apply { id = 6100 + index }) }
        }
        val article = DetailsPage().apply {
            id = 457102
            commentsCount = 181
            commentsSource = partialHtml
            html = """
                <html><body><div class="content material_item">
                Article body with enough length for render checks and stable article reload behavior.
                </div></body></html>
            """.trimIndent()
        }
        val template = mockk<ArticleTemplate> {
            every { mapEntity(any()) } answers { firstArg() }
            every { remapWithCurrentTheme(any()) } answers { firstArg() }
        }
        val api = mockk<NewsApi>(relaxed = true) {
            every {
                fetchArticleDetails("https://4pda.to/index.php?p=457102", ArticleParsePhase.FIRST_RENDER, bypassCache = false)
            } returns fetchResult(article)
            every { rebalanceCommentsSource(any()) } returns false
            every { countCommentNodesInSource(partialHtml) } returns 20
            every { commentsSourceUnderfetchesExpected(partialHtml, 181) } returns true
            every { commentsSourceUnderfetchesExpected(match { it != partialHtml }, any()) } returns false
            every { hasCommentNodeMarkup(partialHtml) } returns true
            every { hasCommentNodeMarkup(match { it != partialHtml }) } returns false
            every { parseCommentsFromSource(any(), partialHtml, paginated = true) } returns partialTree
        }
        val interactor = ArticleInteractor(
                ArticleInteractor.InitData(newsId = 457102, hintCommentsCount = 181),
                NewsRepository(api, mockk<ForumUsersCacheRoom>(relaxed = true)),
                template
        )
        interactor.resetForNewOpen()
        interactor.loadArticle(loadComments = false)
        advanceUntilIdle()

        val result = interactor.loadComments(forceReload = false)
        advanceUntilIdle()
        assertTrue(result is ArticleInteractor.CommentLoadResult.Loaded)

        interactor.reconcileCommentsCountFromParsed(27)
        assertEquals(181, interactor.observeData().first { it?.id == 457102 }?.commentsCount)
    }

    @Test
    fun `reconcileCommentsCountFromParsed raises undercounted badge`() = runTest(dispatcher) {
        val article = DetailsPage().apply {
            id = 457501
            commentsCount = 14
            html = """
                <html><body><div class="content material_item">
                Article body with enough length for render checks and stable article reload behavior.
                </div></body></html>
            """.trimIndent()
        }
        val template = mockk<ArticleTemplate> {
            every { mapEntity(any()) } answers { firstArg() }
            every { remapWithCurrentTheme(any()) } answers { firstArg() }
        }
        val api = mockk<NewsApi>(relaxed = true) {
            every {
                fetchArticleDetails("https://4pda.to/index.php?p=457501", ArticleParsePhase.FIRST_RENDER, bypassCache = false)
            } returns fetchResult(article)
        }
        val interactor = ArticleInteractor(
                ArticleInteractor.InitData(newsId = 457501),
                NewsRepository(api, mockk<ForumUsersCacheRoom>(relaxed = true)),
                template
        )
        interactor.resetForNewOpen()
        interactor.loadArticle(loadComments = false)
        advanceUntilIdle()
        interactor.reconcileCommentsCountFromParsed(21)
        assertEquals(21, interactor.observeData().first { it?.id == 457501 }?.commentsCount)
    }

    @Test
    fun `reconcileCommentsCountFromParsed clamps inflated metadata count`() = runTest(dispatcher) {
        val article = DetailsPage().apply {
            id = 457900
            commentsCount = 222
            html = """
                <html><body><div class="content material_item">
                Article body with enough length for render checks and stable article reload behavior.
                </div></body></html>
            """.trimIndent()
        }
        val template = mockk<ArticleTemplate> {
            every { mapEntity(any()) } answers { firstArg() }
            every { remapWithCurrentTheme(any()) } answers { firstArg() }
        }
        val api = mockk<NewsApi>(relaxed = true) {
            every {
                fetchArticleDetails("https://4pda.to/index.php?p=457900", ArticleParsePhase.FIRST_RENDER, bypassCache = false)
            } returns fetchResult(article)
        }
        val interactor = ArticleInteractor(
                ArticleInteractor.InitData(newsId = 457900),
                NewsRepository(api, mockk<ForumUsersCacheRoom>(relaxed = true)),
                template
        )
        interactor.resetForNewOpen()
        interactor.loadArticle(loadComments = false)
        advanceUntilIdle()
        interactor.reconcileCommentsCountFromParsed(27)
        assertEquals(27, interactor.observeData().first { it?.id == 457900 }?.commentsCount)
    }

    @Test
    fun `refresh uses bypass cache headers`() = runTest(dispatcher) {
        val article = DetailsPage().apply {
            id = 42
            html = """
                <html><body><div class="content material_item">
                Article body with enough length for render checks and stable article reload behavior.
                </div></body></html>
            """.trimIndent()
        }
        val template = mockk<ArticleTemplate> {
            every { mapEntity(any()) } answers { firstArg() }
            every { remapWithCurrentTheme(any()) } answers { firstArg() }
        }
        val api = mockk<NewsApi>(relaxed = true) {
            every {
                fetchArticleDetails("https://4pda.to/index.php?p=42", ArticleParsePhase.FIRST_RENDER, bypassCache = false)
            } returns fetchResult(article)
            every {
                fetchArticleDetails("https://4pda.to/index.php?p=42", ArticleParsePhase.FIRST_RENDER, bypassCache = true)
            } returns fetchResult(article)
        }
        val interactor = ArticleInteractor(
                ArticleInteractor.InitData(newsId = 42),
                NewsRepository(api, mockk<ForumUsersCacheRoom>(relaxed = true)),
                template
        )
        interactor.resetForNewOpen()
        interactor.loadArticle(loadComments = false)
        advanceUntilIdle()
        interactor.loadArticle(loadComments = false, bypassCache = true)
        advanceUntilIdle()

        verify(exactly = 1) {
            api.fetchArticleDetails("https://4pda.to/index.php?p=42", ArticleParsePhase.FIRST_RENDER, bypassCache = false)
        }
        verify(exactly = 1) {
            api.fetchArticleDetails("https://4pda.to/index.php?p=42", ArticleParsePhase.FIRST_RENDER, bypassCache = true)
        }
    }
}
