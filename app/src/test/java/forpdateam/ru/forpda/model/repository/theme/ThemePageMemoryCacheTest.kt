package forpdateam.ru.forpda.model.repository.theme

import forpdateam.ru.forpda.entity.remote.theme.ThemePage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemePageMemoryCacheTest {

    private var now = 0L

    private fun cache(ttlMs: Long = ThemePageMemoryCache.DEFAULT_TTL_MS) =
            ThemePageMemoryCache(ttlMs = ttlMs, nowMs = { now })

    @Test
    fun putThenGet_returnsCopyAndExpiresAfterTtl() {
        val cache = cache(ttlMs = 1000L)
        val key = ThemePageMemoryCache.Key(topicId = 42, st = 0, hatOpen = false, pollOpen = false)
        val page = ThemePage().apply {
            id = 42
            title = "cached"
        }
        cache.put(key, page)
        val hit = cache.get(key)
        assertNotNull(hit)
        assertEquals(42, hit!!.id)
        assertEquals("cached", hit.title)
        assertTrue(hit !== page)

        now = 1001L
        assertNull(cache.get(key))
    }

    @Test
    fun invalidateTopic_removesAllPagesForTopic() {
        val cache = cache()
        val topicId = 7
        cache.put(ThemePageMemoryCache.Key(topicId, 0, false, false), ThemePage().apply { id = topicId })
        cache.put(ThemePageMemoryCache.Key(topicId, 20, false, false), ThemePage().apply { id = topicId })
        cache.put(ThemePageMemoryCache.Key(8, 0, false, false), ThemePage().apply { id = 8 })

        cache.invalidateTopic(topicId)

        assertNull(cache.get(ThemePageMemoryCache.Key(topicId, 0, false, false)))
        assertNull(cache.get(ThemePageMemoryCache.Key(topicId, 20, false, false)))
        assertNotNull(cache.get(ThemePageMemoryCache.Key(8, 0, false, false)))
    }

    @Test
    fun keyFrom_parsesTopicAndSt() {
        val cache = cache()
        val key = cache.keyFrom("https://4pda.to/forum/index.php?showtopic=99&st=40", hatOpen = true, pollOpen = false)
        assertEquals(ThemePageMemoryCache.Key(99, 40, hatOpen = true, pollOpen = false), key)
    }

    @Test
    fun shouldSkipCache_forUnreadAndFindPostUrls() {
        assertTrue(ThemePageMemoryCache.shouldSkipCache("https://4pda.to/forum/index.php?showtopic=1&view=getnewpost"))
        assertTrue(ThemePageMemoryCache.shouldSkipCache("https://4pda.to/forum/index.php?showtopic=1&view=getlastpost"))
        assertTrue(ThemePageMemoryCache.shouldSkipCache("https://4pda.to/forum/index.php?showtopic=1&view=findpost&p=2"))
        assertFalse(ThemePageMemoryCache.shouldSkipCache("https://4pda.to/forum/index.php?showtopic=1&st=0"))
    }
}
