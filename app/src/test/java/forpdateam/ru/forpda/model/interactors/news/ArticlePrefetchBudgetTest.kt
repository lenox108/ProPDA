package forpdateam.ru.forpda.model.interactors.news

import forpdateam.ru.forpda.client.FourPdaRequestGovernor
import forpdateam.ru.forpda.entity.remote.news.DetailsPage
import forpdateam.ru.forpda.model.data.cache.forumuser.ForumUsersCacheRoom
import forpdateam.ru.forpda.model.data.remote.api.NetworkResponse
import forpdateam.ru.forpda.model.data.remote.api.news.ArticleFetchResult
import forpdateam.ru.forpda.model.data.remote.api.news.ArticleParsePhase
import forpdateam.ru.forpda.model.data.remote.api.news.NewsApi
import forpdateam.ru.forpda.model.repository.news.NewsRepository
import forpdateam.ru.forpda.presentation.articles.detail.ArticleTemplate
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * Спекулятивный префетч не должен превращаться в поток запросов к 4pda: он ограничен бюджетом на
 * минуту и полностью замолкает, пока сервер держит нас в 429-паузе. Осознанный тап — исключение.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ArticlePrefetchBudgetTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        FourPdaRequestGovernor.resetForTest()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        FourPdaRequestGovernor.resetForTest()
    }

    private fun html(): String = """
        <html><body><div class="content material_item news-detail-header">
        Article body long enough for the renderable-html validator to accept it.
        </div></body></html>
    """.trimIndent()

    private fun page(id: Int) = DetailsPage().apply {
        this.id = id
        title = "News $id"
        this.html = html()
    }

    private fun fetchResult(page: DetailsPage) = ArticleFetchResult(
            page = page,
            rawBody = page.html.orEmpty(),
            response = NetworkResponse(code = 200, body = page.html.orEmpty()),
            originalUrl = "https://4pda.to/index.php?p=${page.id}",
            probeUrl = "https://4pda.to/index.php?p=${page.id}"
    )

    private fun service(api: NewsApi): ArticlePrefetchService {
        val template = mockk<ArticleTemplate> {
            every { mapEntity(any()) } answers { firstArg() }
        }
        return ArticlePrefetchService(
                NewsRepository(api, mockk<ForumUsersCacheRoom>(relaxed = true)),
                template,
                mockk<ArticleDiskCache>(relaxed = true),
                ArticleMemoryCache(),
                prefetchDebounceMs = 0L
        )
    }

    private fun api(): NewsApi = mockk<NewsApi> {
        every { fetchArticleDetails(any(), any(), any(), any()) } answers {
            val url = firstArg<String>()
            val id = url.substringAfterLast("p=").toInt()
            fetchResult(page(id))
        }
    }

    @Test
    fun `speculative prefetch stops after the per-minute budget is spent`() = runTest(dispatcher) {
        val api = api()
        val prefetch = service(api)

        // Каждый вызов ждём завершения: иначе новый кандидат отменяет предыдущий и до сети не доходит.
        repeat(9) { index ->
            prefetch.prefetchArticle(100 + index)
            runBlocking { delay(120) }
        }

        verify(atMost = 6) { api.fetchArticleDetails(any(), any(), any(), background = true) }
        // Хвост очереди обрезан бюджетом, а не отменой: последние кандидаты до сети не дошли.
        verify(exactly = 0) {
            api.fetchArticleDetails("https://4pda.to/index.php?p=107", any(), any(), any())
        }
        verify(exactly = 0) {
            api.fetchArticleDetails("https://4pda.to/index.php?p=108", any(), any(), any())
        }
    }

    @Test
    fun `deliberate tap ignores budget and cooldown and asks with user priority`() = runTest(dispatcher) {
        val api = api()
        val prefetch = service(api)
        FourPdaRequestGovernor.onResponse(code = 429, retryAfterSeconds = 60L)

        prefetch.prefetchArticle(200)
        runBlocking { delay(60) }
        prefetch.prefetchArticleNow(201)
        runBlocking { delay(60) }

        verify(exactly = 0) { api.fetchArticleDetails(any(), any(), any(), background = true) }
        verify(exactly = 1) {
            api.fetchArticleDetails(
                    "https://4pda.to/index.php?p=201",
                    ArticleParsePhase.FIRST_RENDER,
                    bypassCache = false,
                    background = false
            )
        }
    }
}
