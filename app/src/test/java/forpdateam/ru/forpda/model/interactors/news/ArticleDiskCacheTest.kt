package forpdateam.ru.forpda.model.interactors.news

import forpdateam.ru.forpda.entity.remote.news.DetailsPage
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ArticleDiskCacheTest {

    private fun cache(): ArticleDiskCache {
        val context = RuntimeEnvironment.getApplication()
        File(context.filesDir, "article_disk_cache.json").delete()
        File(context.filesDir, "article_disk_cache.json.tmp").delete()
        File(context.filesDir, "article_disk_cache.json.corrupt").delete()
        return ArticleDiskCache(context, maxEntries = 4, maxAgeMs = 10_000L)
    }

    private fun page(id: Int): DetailsPage =
            DetailsPage().apply {
                this.id = id
                title = "Title $id"
                html = "<article>${"Body $id ".repeat(24)}</article>"
                url = "https://4pda.to/index.php?p=$id"
            }

    @Test
    fun `flush writes cache file through atomic temp replacement`() = runTest {
        val cache = cache()
        cache.put(page(42), nowMs = 1_000L)

        cache.flushForTest()

        val target = cache.cacheFileForTest!!
        assertTrue(target.exists())
        assertFalse(File(target.parentFile, "${target.name}.tmp").exists())
        val lookup = cache.get(42, nowMs = 1_500L)
        assertTrue(lookup.valid)
        assertEquals("Title 42", lookup.entry?.page?.title)
    }

    @Test
    fun `corrupt cache file is quarantined and ignored`() = runTest {
        val cache = cache()
        val target = cache.cacheFileForTest!!
        target.writeText("{not-json", Charsets.UTF_8)

        val lookup = cache.get(42, nowMs = 1_500L)

        assertFalse(lookup.valid)
        assertFalse(target.exists())
        assertTrue(File(target.parentFile, "${target.name}.corrupt").exists())
    }

    @Test
    fun `deferred extras pending flag roundtrips and clears after enrichment`() = runTest {
        val cache = cache()
        val pending = page(42).apply {
            commentsSource = "https://4pda.to/index.php?p=42#comments"
            commentsCount = 5
        }
        cache.put(pending, nowMs = 1_000L)
        cache.flushForTest()

        val pendingLookup = cache.get(42, nowMs = 1_500L)
        assertTrue(pendingLookup.valid)
        assertEquals("deferred_extras_pending", pendingLookup.reason)

        val enriched = page(42).apply {
            commentsSource = "https://4pda.to/index.php?p=42#comments"
            desktopCommentsSource = "https://4pda.to/index.php?p=42&desktop=1"
            commentsCount = 5
        }
        cache.put(enriched, nowMs = 2_000L)
        cache.flushForTest()

        val enrichedLookup = cache.get(42, nowMs = 2_500L)
        assertTrue(enrichedLookup.valid)
        assertEquals(null, enrichedLookup.reason)
        assertFalse(enrichedLookup.entry?.deferredExtrasPending ?: true)
    }
}
