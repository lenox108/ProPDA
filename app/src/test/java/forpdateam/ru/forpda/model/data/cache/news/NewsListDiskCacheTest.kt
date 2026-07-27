package forpdateam.ru.forpda.model.data.cache.news

import forpdateam.ru.forpda.entity.remote.news.NewsItem
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class NewsListDiskCacheTest {

    private fun cache(maxAgeMs: Long = 10_000L): NewsListDiskCache {
        val context = RuntimeEnvironment.getApplication()
        File(context.filesDir, "news_list_cache.json").delete()
        File(context.filesDir, "news_list_cache.json.tmp").delete()
        return NewsListDiskCache(context, maxEntries = 2, maxAgeMs = maxAgeMs)
    }

    private fun items(vararg ids: Int): List<NewsItem> = ids.map { id ->
        NewsItem().apply {
            this.id = id
            title = "Заголовок $id"
            author = "author$id"
            authorId = id * 10
            commentsCount = id
            url = "https://4pda.to/index.php?p=$id"
        }
    }

    @Test
    fun `entry survives flush and keeps validators`() = runTest {
        val cache = cache()
        cache.put("all_news|1", items(1, 2), etag = "W/\"abc\"", lastModified = "Mon, 27 Jul 2026 06:00:00 GMT", nowMs = 1_000L)
        cache.flushForTest()

        val entry = cache.get("all_news|1", nowMs = 2_000L)

        assertTrue(cache.cacheFileForTest!!.exists())
        assertEquals(2, entry?.items?.size)
        assertEquals("Заголовок 1", entry?.items?.first()?.title)
        assertEquals(10, entry?.items?.first()?.authorId)
        assertEquals("W/\"abc\"", entry?.etag)
    }

    @Test
    fun `stale entry is not served`() = runTest {
        val cache = cache(maxAgeMs = 1_000L)
        cache.put("all_news|1", items(1), nowMs = 1_000L)
        cache.flushForTest()

        assertNull(cache.get("all_news|1", nowMs = 5_000L))
    }

    @Test
    fun `refreshValidity extends freshness after 304`() = runTest {
        val cache = cache(maxAgeMs = 2_000L)
        cache.put("all_news|1", items(1), etag = "e1", nowMs = 1_000L)
        cache.flushForTest()
        val stored = cache.get("all_news|1", nowMs = 1_500L)!!

        cache.refreshValidity("all_news|1", stored, etag = "e1", lastModified = null, nowMs = 4_000L)
        cache.flushForTest()

        val refreshed = cache.get("all_news|1", nowMs = 5_000L)
        assertEquals(1, refreshed?.items?.size)
        assertEquals("e1", refreshed?.etag)
    }

    @Test
    fun `oldest category is evicted beyond the limit`() = runTest {
        val cache = cache()
        cache.put("a|1", items(1), nowMs = 1_000L)
        cache.put("b|1", items(2), nowMs = 2_000L)
        cache.put("c|1", items(3), nowMs = 3_000L)
        cache.flushForTest()

        assertNull(cache.get("a|1", nowMs = 3_500L))
        assertEquals(1, cache.get("c|1", nowMs = 3_500L)?.items?.size)
    }

    @Test
    fun `empty list is not stored`() = runTest {
        val cache = cache()
        cache.put("all_news|1", emptyList(), nowMs = 1_000L)
        cache.flushForTest()

        assertNull(cache.get("all_news|1", nowMs = 1_500L))
    }
}
