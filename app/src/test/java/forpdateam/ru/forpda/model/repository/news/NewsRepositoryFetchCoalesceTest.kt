package forpdateam.ru.forpda.model.repository.news

import forpdateam.ru.forpda.entity.remote.news.DetailsPage
import forpdateam.ru.forpda.entity.remote.news.NewsItem
import forpdateam.ru.forpda.model.data.cache.forumuser.ForumUsersCacheRoom
import forpdateam.ru.forpda.model.data.remote.api.NetworkResponse
import forpdateam.ru.forpda.model.data.remote.api.news.ArticleFetchResult
import forpdateam.ru.forpda.model.data.remote.api.news.ArticleParsePhase
import forpdateam.ru.forpda.model.data.remote.api.news.NewsApi
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class NewsRepositoryFetchCoalesceTest {

    @Test
    fun `concurrent fetches coalesce into single network call`() = runTest {
        val page = DetailsPage().apply {
            id = 42
            title = "Title"
            html = "<article>${"x".repeat(120)}</article>"
        }
        val fetchResult = ArticleFetchResult(
                page = page,
                rawBody = page.html.orEmpty(),
                response = NetworkResponse(code = 200, body = page.html.orEmpty()),
                originalUrl = "https://4pda.to/index.php?p=42",
                probeUrl = "https://4pda.to/index.php?p=42"
        )
        val calls = AtomicInteger(0)
        val api = mockk<NewsApi> {
            every {
                fetchArticleDetails(
                        "https://4pda.to/index.php?p=42",
                        ArticleParsePhase.FULL,
                        bypassCache = false
                )
            } answers {
                calls.incrementAndGet()
                Thread.sleep(50)
                fetchResult
            }
        }
        val repository = NewsRepository(api, mockk<ForumUsersCacheRoom>(relaxed = true))

        val first = async { repository.fetchArticleDetails(42, ArticleParsePhase.FULL) }
        delay(5)
        val second = async { repository.fetchArticleDetails(42, ArticleParsePhase.FULL) }

        assertEquals(fetchResult.page.id, first.await().page.id)
        assertEquals(fetchResult.page.id, second.await().page.id)
        assertEquals(1, calls.get())
        verify(exactly = 1) {
            api.fetchArticleDetails(
                    "https://4pda.to/index.php?p=42",
                    ArticleParsePhase.FULL,
                    bypassCache = false
            )
        }
    }

    @Test
    fun `coalesced article fetch returns independent mutable pages`() = runTest {
        val page = DetailsPage().apply {
            id = 42
            title = "Original"
            html = "<article>${"x".repeat(120)}</article>"
        }
        val fetchResult = ArticleFetchResult(
                page = page,
                rawBody = page.html.orEmpty(),
                response = NetworkResponse(code = 200, body = page.html.orEmpty()),
                originalUrl = "https://4pda.to/index.php?p=42",
                probeUrl = "https://4pda.to/index.php?p=42"
        )
        val api = mockk<NewsApi> {
            every {
                fetchArticleDetails(
                        "https://4pda.to/index.php?p=42",
                        ArticleParsePhase.FIRST_RENDER,
                        bypassCache = false
                )
            } answers {
                Thread.sleep(50)
                fetchResult
            }
        }
        val repository = NewsRepository(api, forumUsersCache())

        val first = async { repository.fetchArticleDetails(42, ArticleParsePhase.FIRST_RENDER) }
        delay(5)
        val second = async { repository.fetchArticleDetails(42, ArticleParsePhase.FIRST_RENDER) }
        val firstResult = first.await()
        val secondResult = second.await()

        firstResult.page.title = "Mutated by first opener"
        firstResult.page.html = ""

        assertEquals("Original", secondResult.page.title)
        assertEquals("<article>${"x".repeat(120)}</article>", secondResult.page.html)
        verify(exactly = 1) {
            api.fetchArticleDetails(
                    "https://4pda.to/index.php?p=42",
                    ArticleParsePhase.FIRST_RENDER,
                    bypassCache = false
            )
        }
    }

    @Test
    fun `bypass cache skips coalescing`() = runTest {
        val page = DetailsPage().apply {
            id = 42
            title = "Title"
            html = "<article>${"x".repeat(120)}</article>"
        }
        val fetchResult = ArticleFetchResult(
                page = page,
                rawBody = page.html.orEmpty(),
                response = NetworkResponse(code = 200, body = page.html.orEmpty()),
                originalUrl = "https://4pda.to/index.php?p=42",
                probeUrl = "https://4pda.to/index.php?p=42"
        )
        val api = mockk<NewsApi> {
            every {
                fetchArticleDetails(
                        "https://4pda.to/index.php?p=42",
                        ArticleParsePhase.FIRST_RENDER,
                        bypassCache = true
                )
            } returns fetchResult
        }
        val repository = NewsRepository(api, mockk<ForumUsersCacheRoom>(relaxed = true))

        repository.fetchArticleDetails(42, ArticleParsePhase.FIRST_RENDER, bypassCache = true)
        repository.fetchArticleDetails(42, ArticleParsePhase.FIRST_RENDER, bypassCache = true)

        verify(exactly = 2) {
            api.fetchArticleDetails(
                    "https://4pda.to/index.php?p=42",
                    ArticleParsePhase.FIRST_RENDER,
                    bypassCache = true
            )
        }
    }

    @Test
    fun `concurrent news list requests coalesce into single api call`() = runTest {
        val calls = AtomicInteger(0)
        val api = mockk<NewsApi> {
            every { getNews("all", 1) } answers {
                calls.incrementAndGet()
                Thread.sleep(50)
                listOf(newsItem(1, "First"))
            }
        }
        val repository = NewsRepository(api, forumUsersCache())

        val first = async { repository.getNews("all", 1) }
        delay(5)
        val second = async { repository.getNews("all", 1) }

        assertEquals(listOf(1), first.await().map { it.id })
        assertEquals(listOf(1), second.await().map { it.id })
        assertEquals(1, calls.get())
        verify(exactly = 1) { api.getNews("all", 1) }
    }

    @Test
    fun `news list cache serves fresh repeated request as independent copy`() = runTest {
        val cachedTitle = "Cached"
        val api = mockk<NewsApi> {
            every { getNews("all", 1) } returns listOf(newsItem(7, cachedTitle))
        }
        val repository = NewsRepository(api, forumUsersCache())

        val first = repository.getNews("all", 1)
        first.single().title = "Mutated by ui"
        val second = repository.getNews("all", 1)

        assertEquals(cachedTitle, second.single().title)
        verify(exactly = 1) { api.getNews("all", 1) }
    }

    @Test
    fun `news list bypass cache refreshes api result`() = runTest {
        val api = mockk<NewsApi> {
            every { getNews("all", 1) } returnsMany listOf(
                    listOf(newsItem(1, "First")),
                    listOf(newsItem(2, "Second"))
            )
        }
        val repository = NewsRepository(api, forumUsersCache())

        assertEquals(1, repository.getNews("all", 1).single().id)
        assertEquals(2, repository.getNews("all", 1, bypassCache = true).single().id)

        verify(exactly = 2) { api.getNews("all", 1) }
    }

    private fun forumUsersCache(): ForumUsersCacheRoom =
            mockk {
                coEvery { getUsersByIds(any()) } returns emptyMap()
            }

    private fun newsItem(id: Int, title: String): NewsItem =
            NewsItem().apply {
                this.id = id
                this.title = title
                authorId = id * 10
            }
}
