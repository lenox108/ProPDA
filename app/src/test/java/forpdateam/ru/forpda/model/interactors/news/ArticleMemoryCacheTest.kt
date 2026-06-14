package forpdateam.ru.forpda.model.interactors.news

import forpdateam.ru.forpda.entity.remote.news.DetailsPage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ArticleMemoryCacheTest {

    private fun page(id: Int): DetailsPage =
            DetailsPage().apply {
                this.id = id
                title = "Title $id"
                html = "<article>${"Body $id ".repeat(24)}</article>"
                url = "https://4pda.to/index.php?p=$id"
            }

    @Test
    fun `deferred extras pending flag is stored and cleared`() {
        val cache = ArticleMemoryCache()
        val pending = page(42).apply {
            commentsSource = "https://4pda.to/index.php?p=42#comments"
            commentsCount = 3
        }
        assertTrue(cache.put(pending, nowMs = 1_000L))

        val pendingLookup = cache.get(42, nowMs = 1_500L)
        assertTrue(pendingLookup.valid)
        assertEquals("deferred_extras_pending", pendingLookup.reason)
        assertTrue(pendingLookup.entry?.deferredExtrasPending == true)

        val enriched = page(42).apply {
            commentsSource = "https://4pda.to/index.php?p=42#comments"
            desktopCommentsSource = "https://4pda.to/index.php?p=42&desktop=1"
            commentsCount = 3
        }
        assertTrue(cache.put(enriched, nowMs = 2_000L))

        val enrichedLookup = cache.get(42, nowMs = 2_500L)
        assertTrue(enrichedLookup.valid)
        assertNull(enrichedLookup.reason)
        assertFalse(enrichedLookup.entry?.deferredExtrasPending ?: true)
    }
}
