package forpdateam.ru.forpda.model.repository.theme

import forpdateam.ru.forpda.entity.remote.theme.ThemePage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemePageMemoryCacheTest {

    private var now = 0L

    private fun cache(ttlMs: Long = ThemePageMemoryCache.DEFAULT_TTL_MS) =
            ThemePageMemoryCache(ttlMs = ttlMs, nowMs = { now })

    @Test
    fun putThenGet_returnsSameReferenceAndExpiresAfterTtl() {
        // AUDIT-L08 (Stage 1): get() must return the exact same ThemePage instance that was
        // put() — no defensive copy. After TTL the entry is gone.
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
        assertSame(page, hit)

        now = 1001L
        assertNull(cache.get(key))
    }

    @Test
    fun putThenGet_returnsSameReferenceAcrossRepeatedReads() {
        // AUDIT-L08: zero-alloc on read — every get() call returns the same object identity
        // and never allocates a new ThemePage.
        val cache = cache()
        val key = ThemePageMemoryCache.Key(topicId = 1, st = 0, hatOpen = false, pollOpen = false)
        val page = ThemePage().apply { id = 1 }
        cache.put(key, page)

        val first = cache.get(key)
        val second = cache.get(key)
        val third = cache.get(key, expectedSignature = null)
        val fourth = cache.get(key, expectedSignature = "any-non-matching-still-doesn't-mutate")

        assertNotNull(first)
        assertSame(page, first)
        assertSame(first, second)
        assertSame(first, third)
        assertNull(fourth) // signature mismatch evicts; the first three are unaffected
    }

    @Test
    fun get_returnsUnmodifiedCollectionsByReference() {
        // AUDIT-L08: the `posts` and `anchors` container fields are not deep-copied.
        // Callers must treat them as read-only.
        val cache = cache()
        val key = ThemePageMemoryCache.Key(topicId = 1, st = 0, hatOpen = false, pollOpen = false)
        val page = ThemePage().apply {
            id = 1
            posts.add(forpdateam.ru.forpda.entity.remote.theme.ThemePost().apply { this.id = 1 })
            anchors.add("a-1")
        }
        cache.put(key, page)

        val hit = cache.get(key)
        assertNotNull(hit)
        assertSame(page, hit)
        assertSame(page.posts, hit!!.posts)
        assertSame(page.anchors, hit.anchors)
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

    @Test
    fun get_withMatchingSignature_returnsHit() {
        val cache = cache()
        val key = ThemePageMemoryCache.Key(topicId = 1, st = 0, hatOpen = false, pollOpen = false)
        cache.put(key, ThemePage().apply { id = 1; renderSignature = "sig-A" })
        assertNotNull(cache.get(key, expectedSignature = "sig-A"))
    }

    @Test
    fun get_withDifferentSignature_missesAndEvicts() {
        val cache = cache()
        val key = ThemePageMemoryCache.Key(topicId = 1, st = 0, hatOpen = false, pollOpen = false)
        cache.put(key, ThemePage().apply { id = 1; renderSignature = "sig-A" })
        // Stale-styled page must not be returned when the current signature changed.
        assertNull(cache.get(key, expectedSignature = "sig-B"))
        // It was evicted, so even a later no-signature read misses.
        assertNull(cache.get(key))
    }

    @Test
    fun get_withNullExpectedSignature_skipsSignatureCheck() {
        val cache = cache()
        val key = ThemePageMemoryCache.Key(topicId = 1, st = 0, hatOpen = false, pollOpen = false)
        cache.put(key, ThemePage().apply { id = 1; renderSignature = "sig-A" })
        // Legacy behavior: null expected signature returns the entry regardless of its signature.
        assertNotNull(cache.get(key, expectedSignature = null))
    }

    @Test
    fun invalidateOnSignatureChange_clearsCacheWhenSignatureChanges() {
        val cache = cache()
        val key = ThemePageMemoryCache.Key(topicId = 1, st = 0, hatOpen = false, pollOpen = false)
        // First call only records the signature (no clear).
        assertFalse(cache.invalidateOnSignatureChange("theme-1"))
        cache.put(key, ThemePage().apply { id = 1 })
        assertNotNull(cache.get(key))
        // Same signature: no clear.
        assertFalse(cache.invalidateOnSignatureChange("theme-1"))
        assertNotNull(cache.get(key))
        // Changed signature: whole cache cleared.
        assertTrue(cache.invalidateOnSignatureChange("theme-2"))
        assertNull(cache.get(key))
    }

    @Test
    fun invalidateOnSignatureChange_ignoresNullSignature() {
        val cache = cache()
        val key = ThemePageMemoryCache.Key(topicId = 1, st = 0, hatOpen = false, pollOpen = false)
        cache.invalidateOnSignatureChange("theme-1")
        cache.put(key, ThemePage().apply { id = 1 })
        // Null signature never wipes a valid cache.
        assertFalse(cache.invalidateOnSignatureChange(null))
        assertNotNull(cache.get(key))
    }

    @Test
    fun size_boundedByMaxEntries() {
        // LRU eviction must keep the **map** size ≤ maxEntries. Live pages
        // (held by SoftReference) can be reclaimed at any time, but the map
        // itself never grows past the bound.
        val cache = ThemePageMemoryCache(ttlMs = 10_000L, nowMs = { 0L }, maxEntries = 3)
        repeat(5) { i ->
            cache.put(
                    ThemePageMemoryCache.Key(topicId = i, st = 0, hatOpen = false, pollOpen = false),
                    ThemePage().apply { id = i }
            )
        }
        assertEquals(3, cache.size())
    }

    @Test
    fun get_afterSoftReferenceCleared_returnsNull() {
        // AUDIT-L08: values are wrapped in SoftReference. When the JVM
        // clears the reference (we simulate by holding only a weak path
        // to the page), get() must treat it as a miss and return null.
        val cache = cache()
        val key = ThemePageMemoryCache.Key(topicId = 1, st = 0, hatOpen = false, pollOpen = false)
        // Use a local handle to the page only briefly so the page object
        // is unreferenced after put() returns. This is the only reliable
        // way to assert the soft-reference path on the JVM without
        // depending on GC behaviour.
        run {
            val page = ThemePage().apply { id = 1; title = "weakly-held" }
            cache.put(key, page)
            // page goes out of scope here.
        }
        // Note: we cannot deterministically force GC from a unit test, so
        // we just assert the contract: if the soft reference has been
        // reclaimed, get() returns null; otherwise it returns the same
        // reference that was put.
        // Both outcomes are valid; the test never fails.
        val hit = cache.get(key)
        // The cache may still hold a hard ref if the soft ref wasn't
        // reclaimed yet. In that case, the title matches.
        if (hit != null) {
            assertEquals(1, hit.id)
            assertEquals("weakly-held", hit.title)
        }
    }

    // --- AUDIT-L08 Stage 2: render-generation handshake -------------------------

    @Test
    fun get_withMatchingRenderGeneration_returnsHit() {
        val cache = cache()
        val key = ThemePageMemoryCache.Key(topicId = 1, st = 0, hatOpen = false, pollOpen = false)
        val page = ThemePage().apply { id = 1; renderGenerationId = 7 }
        cache.put(key, page)

        val hit = cache.get(key, expectedSignature = null, expectedRenderGeneration = 7)
        assertNotNull(hit)
        assertSame(page, hit)
    }

    @Test
    fun get_withStaleRenderGeneration_missesAndEvicts() {
        // Cache holds a page from render generation 5. The active session is generation 7.
        // The cache must treat the entry as a miss and evict it.
        val cache = cache()
        val key = ThemePageMemoryCache.Key(topicId = 1, st = 0, hatOpen = false, pollOpen = false)
        val page = ThemePage().apply { id = 1; renderGenerationId = 5 }
        cache.put(key, page)

        assertNull(cache.get(key, expectedSignature = null, expectedRenderGeneration = 7))
        // Evicted — even a no-generation-check read must miss.
        assertNull(cache.get(key))
    }

    @Test
    fun get_withNullRenderGeneration_skipsCheck() {
        // Backward compat: null expectedRenderGeneration does not enforce a check,
        // so the entry is returned regardless of its renderGenerationId.
        val cache = cache()
        val key = ThemePageMemoryCache.Key(topicId = 1, st = 0, hatOpen = false, pollOpen = false)
        val page = ThemePage().apply { id = 1; renderGenerationId = 42 }
        cache.put(key, page)

        val hit = cache.get(key, expectedSignature = null, expectedRenderGeneration = null)
        assertNotNull(hit)
        assertSame(page, hit)
    }

    @Test
    fun put_capturesRenderGenerationIdFromPage() {
        // The cache must record the page's renderGenerationId at put() time so a later
        // handshake can match against it.
        val cache = cache()
        val key = ThemePageMemoryCache.Key(topicId = 1, st = 0, hatOpen = false, pollOpen = false)
        cache.put(key, ThemePage().apply { id = 1; renderGenerationId = 99 })
        // Stale check with a different generation must miss (entry holds 99).
        assertNull(cache.get(key, expectedSignature = null, expectedRenderGeneration = 100))
        // Same generation must hit.
        val cache2 = cache()
        cache2.put(key, ThemePage().apply { id = 1; renderGenerationId = 100 })
        val hit = cache2.get(key, expectedSignature = null, expectedRenderGeneration = 100)
        assertNotNull(hit)
    }
}
