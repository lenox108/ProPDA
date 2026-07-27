package forpdateam.ru.forpda.model.repository.news

import forpdateam.ru.forpda.entity.remote.news.NewsItem
import forpdateam.ru.forpda.model.data.cache.forumuser.ForumUsersCacheRoom
import forpdateam.ru.forpda.model.data.cache.news.NewsListDiskCache
import forpdateam.ru.forpda.model.data.remote.api.news.NewsApi
import forpdateam.ru.forpda.model.data.remote.api.news.NewsListFetch
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class NewsListCachingTest {

    private fun diskCache(): NewsListDiskCache {
        val context = RuntimeEnvironment.getApplication()
        File(context.filesDir, "news_list_cache.json").delete()
        return NewsListDiskCache(context)
    }

    private fun items(vararg ids: Int): List<NewsItem> = ids.map { id ->
        NewsItem().apply {
            this.id = id
            title = "Новость $id"
            author = "author$id"
        }
    }

    private fun usersCache(): ForumUsersCacheRoom = mockk<ForumUsersCacheRoom>(relaxed = true).also {
        coEvery { it.getUsersByIds(any()) } returns emptyMap()
    }

    @Test
    fun `first page is served from disk without touching the network`() = runTest {
        val api = mockk<NewsApi>()
        coEvery { api.fetchNewsList("all_news", 1, null, null) } returns
                NewsListFetch(items = items(1, 2), etag = "e1", lastModified = "lm1")
        val disk = diskCache()
        val repository = NewsRepository(api, usersCache(), disk)

        repository.getNews("all_news", 1, bypassCache = true)
        disk.flushForTest()

        // Новый репозиторий = чистая память: ответ обязан прийти с диска.
        val cached = NewsRepository(api, usersCache(), disk).getCachedNews("all_news", 1)

        assertEquals(listOf(1, 2), cached?.map { it.id })
        coVerify(exactly = 1) { api.fetchNewsList(any(), any(), any(), any()) }
    }

    @Test
    fun `304 response reuses cached items and keeps validators`() = runTest {
        val api = mockk<NewsApi>()
        coEvery { api.fetchNewsList("all_news", 1, null, null) } returns
                NewsListFetch(items = items(1, 2), etag = "e1", lastModified = "lm1")
        val disk = diskCache()
        NewsRepository(api, usersCache(), disk).getNews("all_news", 1, bypassCache = true)
        disk.flushForTest()

        coEvery { api.fetchNewsList("all_news", 1, "e1", "lm1") } returns
                NewsListFetch(items = null, notModified = true, etag = "e1", lastModified = "lm1")
        val fresh = NewsRepository(api, usersCache(), disk).getNews("all_news", 1)

        assertEquals(listOf(1, 2), fresh.map { it.id })
        coVerify(exactly = 1) { api.fetchNewsList("all_news", 1, "e1", "lm1") }
    }

    @Test
    fun `manual refresh does not send validators`() = runTest {
        val api = mockk<NewsApi>()
        coEvery { api.fetchNewsList("all_news", 1, null, null) } returns
                NewsListFetch(items = items(1), etag = "e1")
        val disk = diskCache()
        val repository = NewsRepository(api, usersCache(), disk)
        repository.getNews("all_news", 1, bypassCache = true)
        disk.flushForTest()

        repository.getNews("all_news", 1, bypassCache = true)

        coVerify(exactly = 2) { api.fetchNewsList("all_news", 1, null, null) }
    }

    @Test
    fun `following pages are not cached on disk`() = runTest {
        val api = mockk<NewsApi>()
        coEvery { api.fetchNewsList("all_news", 2, null, null) } returns
                NewsListFetch(items = items(5, 6))
        val disk = diskCache()
        val repository = NewsRepository(api, usersCache(), disk)

        repository.getNews("all_news", 2, bypassCache = true)
        disk.flushForTest()

        assertNull(NewsRepository(api, usersCache(), disk).getCachedNews("all_news", 2))
    }
}
