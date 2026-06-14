package forpdateam.ru.forpda.model.interactors.news

import forpdateam.ru.forpda.entity.remote.news.DetailsPage
import forpdateam.ru.forpda.model.data.remote.api.NetworkResponse
import forpdateam.ru.forpda.model.data.remote.api.news.ArticleFetchResult
import forpdateam.ru.forpda.model.data.remote.api.news.ArticleHtmlValidator
import forpdateam.ru.forpda.model.data.remote.api.news.ArticleParsePhase
import forpdateam.ru.forpda.model.data.remote.api.news.NewsApi
import forpdateam.ru.forpda.model.data.cache.forumuser.ForumUsersCacheRoom
import forpdateam.ru.forpda.model.repository.news.NewsRepository
import forpdateam.ru.forpda.presentation.articles.detail.ArticleTemplate
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ArticleOpenPipelineTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun renderableHtml(): String = """
        <html><body>
        <div class="content material_item news-detail-header">
        Article body with enough length for render checks and stable article reload behavior.
        </div></body></html>
    """.trimIndent()

    private fun article(id: Int = 42, html: String = renderableHtml()) = DetailsPage().apply {
        this.id = id
        title = "News title"
        this.html = html
        commentsCount = 3
    }

    private fun fetchResult(page: DetailsPage, body: String = page.html.orEmpty()) = ArticleFetchResult(
            page = page,
            rawBody = body,
            response = NetworkResponse(code = 200, body = body),
            originalUrl = "https://4pda.to/index.php?p=${page.id}",
            probeUrl = "https://4pda.to/index.php?p=${page.id}"
    )

    private fun interactor(api: NewsApi, template: ArticleTemplate = mockk(relaxed = true)): ArticleInteractor {
        every { template.mapEntity(any()) } answers { firstArg() }
        return ArticleInteractor(
                ArticleInteractor.InitData(newsId = 42),
                NewsRepository(api, mockk<ForumUsersCacheRoom>(relaxed = true)),
                template
        )
    }

    @Test
    fun `valid article has non-empty body after parse`() {
        val page = article(html = "<div class=\"entry-content\">" + "x".repeat(120) + "</div>")
        assertTrue(ArticleHtmlValidator.hasNonEmptyParsedBody(page))
    }

    @Test
    fun `empty HTML maps to error not success`() = runTest(dispatcher) {
        val empty = article(html = "")
        val api = mockk<NewsApi> {
            every { fetchArticleDetails("https://4pda.to/index.php?p=42", ArticleParsePhase.FIRST_RENDER, bypassCache = false) } returns fetchResult(empty, "")
        }
        val interactor = interactor(api)
        interactor.resetForNewOpen()
        val result = runCatching { interactor.loadArticle() }
        advanceUntilIdle()
        assertTrue(result.isFailure)
    }

    @Test
    fun `login page rejected at network layer`() = runTest(dispatcher) {
        val api = mockk<NewsApi> {
            every { fetchArticleDetails("https://4pda.to/index.php?p=42", ArticleParsePhase.FIRST_RENDER, bypassCache = false) } throws IllegalStateException("login_page")
        }
        val interactor = interactor(api)
        interactor.resetForNewOpen()
        assertTrue(runCatching { interactor.loadArticle() }.isFailure)
    }

    @Test
    fun `empty cache entry is not stored`() = runTest(dispatcher) {
        val cache = ArticleMemoryCache()
        val page = DetailsPage().apply {
            id = 9
            title = "T"
            html = ""
        }
        assertFalse(cache.put(page))
        val lookup = cache.get(9)
        assertFalse(lookup.valid)
    }

    @Test
    fun `second load uses memory cache without network`() = runTest(dispatcher) {
        val page = article()
        val api = mockk<NewsApi> {
            every { fetchArticleDetails("https://4pda.to/index.php?p=42", ArticleParsePhase.FIRST_RENDER, bypassCache = false) } returns fetchResult(page)
        }
        val interactor = interactor(api)
        interactor.resetForNewOpen()
        interactor.loadArticle()
        advanceUntilIdle()
        interactor.loadArticle()
        advanceUntilIdle()
        verify(exactly = 1) { api.fetchArticleDetails("https://4pda.to/index.php?p=42", ArticleParsePhase.FIRST_RENDER, bypassCache = false) }
    }

    @Test
    fun `refresh bypasses cache and hits network again`() = runTest(dispatcher) {
        val page = article()
        val api = mockk<NewsApi> {
            every { fetchArticleDetails("https://4pda.to/index.php?p=42", ArticleParsePhase.FIRST_RENDER, bypassCache = false) } returns fetchResult(page)
            every { fetchArticleDetails("https://4pda.to/index.php?p=42", ArticleParsePhase.FIRST_RENDER, bypassCache = true) } returns fetchResult(page)
        }
        val interactor = interactor(api)
        interactor.resetForNewOpen()
        interactor.loadArticle()
        advanceUntilIdle()
        interactor.loadArticle(bypassCache = true)
        advanceUntilIdle()
        verify(exactly = 1) { api.fetchArticleDetails("https://4pda.to/index.php?p=42", ArticleParsePhase.FIRST_RENDER, bypassCache = false) }
        verify(exactly = 1) { api.fetchArticleDetails("https://4pda.to/index.php?p=42", ArticleParsePhase.FIRST_RENDER, bypassCache = true) }
    }

    @Test
    fun `reset cancels in-flight generation before next load`() = runTest(dispatcher) {
        val page = article()
        val api = mockk<NewsApi> {
            every { fetchArticleDetails("https://4pda.to/index.php?p=42", ArticleParsePhase.FIRST_RENDER, bypassCache = false) } returns fetchResult(page)
        }
        val interactor = interactor(api)
        interactor.resetForNewOpen()
        val generationBefore = interactor.currentArticleGeneration()
        interactor.loadArticle()
        advanceUntilIdle()
        val generationAfterLoad = interactor.currentArticleGeneration()
        interactor.resetForNewOpen()
        val generationAfterReset = interactor.currentArticleGeneration()
        assertTrue(generationAfterLoad > generationBefore)
        assertTrue(generationAfterReset > generationAfterLoad)
    }

    @Test
    fun `loadArticle does not parse comment tree on critical path`() = runTest(dispatcher) {
        val page = article().apply { commentsCount = 0 }
        val api = mockk<NewsApi>(relaxed = true) {
            every { fetchArticleDetails("https://4pda.to/index.php?p=42", ArticleParsePhase.FIRST_RENDER, bypassCache = false) } returns fetchResult(page)
        }
        val interactor = interactor(api)
        interactor.resetForNewOpen()
        interactor.loadArticle(loadComments = false)
        advanceUntilIdle()
        verify(exactly = 0) { api.parseComments(any<DetailsPage>()) }
    }

    @Test
    fun `open session is created for trace`() = runTest(dispatcher) {
        val page = article()
        val api = mockk<NewsApi> {
            every { fetchArticleDetails("https://4pda.to/index.php?p=42", ArticleParsePhase.FIRST_RENDER, bypassCache = false) } returns fetchResult(page)
        }
        val interactor = interactor(api)
        interactor.resetForNewOpen()
        interactor.loadArticle()
        advanceUntilIdle()
        assertNotNull(interactor.openSession)
        assertEquals(42, interactor.openSession?.articleId)
    }

    @Test
    fun `rapid reset increments generation and ignores stale completion`() = runTest(dispatcher) {
        val page = article()
        val api = mockk<NewsApi> {
            coEvery {
                fetchArticleDetails("https://4pda.to/index.php?p=42", ArticleParsePhase.FIRST_RENDER, bypassCache = false)
            } coAnswers {
                delay(50)
                fetchResult(page)
            }
        }
        val interactor = interactor(api)
        interactor.resetForNewOpen()
        val firstGeneration = interactor.currentArticleGeneration()
        val firstLoad = async { runCatching { interactor.loadArticle() } }
        delay(10)
        interactor.resetForNewOpen()
        val secondGeneration = interactor.currentArticleGeneration()
        assertTrue(secondGeneration > firstGeneration)
        val firstResult = firstLoad.await()
        advanceUntilIdle()
        assertTrue(firstResult.isFailure)
    }

    @Test
    fun `memory cache applies list comment count hint`() = runTest(dispatcher) {
        val page = article(id = 457203, html = renderableHtml()).apply {
            commentsCount = 0
        }
        val template = mockk<ArticleTemplate> {
            every { mapEntity(any()) } answers { firstArg() }
            every { remapWithCurrentTheme(any()) } answers {
                val mapped = firstArg<DetailsPage>()
                mapped.apply { commentsCount = mapped.commentsCount }
            }
        }
        val api = mockk<NewsApi> {
            every { fetchArticleDetails("https://4pda.to/index.php?p=457203", ArticleParsePhase.FIRST_RENDER, bypassCache = false) } returns fetchResult(page)
        }
        val interactor = ArticleInteractor(
                ArticleInteractor.InitData(newsId = 457203, hintCommentsCount = 10),
                NewsRepository(api, mockk<ForumUsersCacheRoom>(relaxed = true)),
                template
        )
        interactor.resetForNewOpen()
        interactor.loadArticle()
        advanceUntilIdle()
        interactor.loadArticle()
        advanceUntilIdle()
        verify(exactly = 1) { api.fetchArticleDetails("https://4pda.to/index.php?p=457203", ArticleParsePhase.FIRST_RENDER, bypassCache = false) }
        assertEquals(10, interactor.observeData().first { it?.id == 457203 }?.commentsCount)
    }

    @Test
    fun `consecutive loads bump generation guards`() = runTest(dispatcher) {
        val page = article()
        val api = mockk<NewsApi> {
            every { fetchArticleDetails("https://4pda.to/index.php?p=42", ArticleParsePhase.FIRST_RENDER, bypassCache = false) } returns fetchResult(page)
        }
        val interactor = interactor(api)
        interactor.resetForNewOpen()
        val g0 = interactor.currentArticleGeneration()
        interactor.loadArticle()
        advanceUntilIdle()
        val g1 = interactor.currentArticleGeneration()
        interactor.loadArticle()
        advanceUntilIdle()
        val g2 = interactor.currentArticleGeneration()
        assertTrue(g1 > g0)
        assertTrue(g2 > g1)
    }
}
