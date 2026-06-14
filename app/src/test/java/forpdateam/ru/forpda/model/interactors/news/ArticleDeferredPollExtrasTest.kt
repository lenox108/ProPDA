package forpdateam.ru.forpda.model.interactors.news

import forpdateam.ru.forpda.entity.remote.news.DetailsPage
import forpdateam.ru.forpda.model.data.remote.api.NetworkResponse
import forpdateam.ru.forpda.model.data.remote.api.news.ArticleFetchResult
import forpdateam.ru.forpda.model.data.remote.api.news.ArticleParsePhase
import forpdateam.ru.forpda.model.data.remote.api.news.NewsApi
import forpdateam.ru.forpda.model.data.cache.forumuser.ForumUsersCacheRoom
import forpdateam.ru.forpda.model.repository.news.NewsRepository
import forpdateam.ru.forpda.presentation.articles.detail.ArticleTemplate
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ArticleDeferredPollExtrasTest {

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
    fun `deferred extras full reload when desktop poll appended to parser body`() = runTest(dispatcher) {
        val unmappedBody = """
            <div class="entry-content">
            <p>Текст статьи-опроса без интерактивного блока на мобильной версии.</p>
            </div>
        """.trimIndent()
        val mappedHtml = """
            <html><body id="news">
            <div class="content">$unmappedBody</div>
            </body></html>
        """.trimIndent()
        val pollBlock = """
            <div id="poll-ajax-frame-news" class="poll-ajax-frame news-poll news-poll-normalized" data-normalized-poll="true" data-news-poll-token="poll-1335-1">
              <h2>Опрос</h2>
              <form action="https://4pda.to/pages/poll/?act=vote&amp;poll_id=1335" method="post">
                <input type="hidden" name="poll_id" value="1335">
                <ul class="poll-list">
                  <li><label class="text"><input type="radio" name="answer[]" value="1"> <span>Да</span></label></li>
                  <li><label class="text"><input type="radio" name="answer[]" value="2"> <span>Нет</span></label></li>
                </ul>
                <button type="submit" class="btn">Проголосовать</button>
              </form>
            </div>
        """.trimIndent()

        val parsedPage = DetailsPage().apply {
            id = 457102
            title = "Опрос: тест"
            html = unmappedBody
            commentsCount = 0
        }
        val remappedCapture = mutableListOf<DetailsPage>()
        val template = mockk<ArticleTemplate> {
            every { mapEntity(any()) } answers {
                val page = firstArg<DetailsPage>()
                remappedCapture += page
                page.apply { html = mappedHtml.replace(unmappedBody, page.html.orEmpty()) }
            }
            every { remapWithCurrentTheme(any()) } answers { firstArg() }
        }
        val api = mockk<NewsApi>(relaxed = true) {
            every {
                fetchArticleDetails("https://4pda.to/index.php?p=457102", ArticleParsePhase.FIRST_RENDER, bypassCache = false)
            } returns ArticleFetchResult(
                    page = parsedPage,
                    rawBody = "<html>$unmappedBody</html>",
                    response = NetworkResponse(code = 200, body = "<html>$unmappedBody</html>"),
                    originalUrl = "https://4pda.to/index.php?p=457102",
                    probeUrl = "https://4pda.to/index.php?p=457102",
                    parsedBodyHtml = unmappedBody
            )
            coEvery { enrichDesktopExtras(any()) } answers {
                val fetch = firstArg<ArticleFetchResult>()
                fetch.page.apply { html = listOf(fetch.page.html.orEmpty(), pollBlock).joinToString("\n") }
            }
            every { enrichArticleMetadata(any(), any()) } returns Unit
        }
        val interactor = ArticleInteractor(
                ArticleInteractor.InitData(newsId = 457102),
                NewsRepository(api, mockk<ForumUsersCacheRoom>(relaxed = true)),
                template
        )
        interactor.resetForNewOpen()
        interactor.loadArticle(loadComments = false)
        advanceUntilIdle()

        val displayed = interactor.observeData().first { it?.html?.contains("news-poll-normalized") == true }
        assertTrue(displayed!!.html.orEmpty().contains("news-poll-normalized"))
        assertTrue(displayed.html.orEmpty().contains("name=\"answer[]\""))
        assertTrue(remappedCapture.isNotEmpty())
        assertTrue(remappedCapture.last().html.orEmpty().contains("news-poll-normalized"))
    }
}
