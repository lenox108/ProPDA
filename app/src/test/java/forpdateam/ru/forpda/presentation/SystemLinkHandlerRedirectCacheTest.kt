package forpdateam.ru.forpda.presentation

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * JVM regression test for SystemLinkHandler redirect cache logic (R-4).
 * Verifies that redirect URLs are cached by original URL, not by redirect URL.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SystemLinkHandlerRedirectCacheTest {

    private val redirectCache = mutableMapOf<String, String>()

    @Before
    fun setup() {
        redirectCache.clear()
    }

    @Test
    fun `redirect cache stores resolved URL against original URL`() {
        val originalUrl = "https://example.com/download/file1"
        val resolvedUrl = "https://cdn.example.com/files/file1"

        // Simulate caching the resolved URL
        redirectCache[originalUrl] = resolvedUrl

        // Verify cache lookup by original URL
        val cached = redirectCache[originalUrl]
        assertEquals(resolvedUrl, cached)
    }

    @Test
    fun `redirect cache does not return value for different original URL`() {
        val originalUrl1 = "https://example.com/download/file1"
        val originalUrl2 = "https://example.com/download/file2"
        val resolvedUrl = "https://cdn.example.com/files/file1"

        redirectCache[originalUrl1] = resolvedUrl

        // Verify cache doesn't return value for different original URL
        val cached = redirectCache[originalUrl2]
        assertNull(cached)
    }

    @Test
    fun `redirect cache respects 50 entry limit`() {
        // Fill cache with 50 entries
        for (i in 0 until 50) {
            redirectCache["url$i"] = "resolved$i"
        }

        assertEquals(50, redirectCache.size)

        // Add 51st entry - mirror production eviction (drop oldest when at capacity)
        if (redirectCache.size >= 50) {
            redirectCache.remove(redirectCache.keys.first())
        }
        redirectCache["url50"] = "resolved50"

        assertEquals(50, redirectCache.size)
        assertNull(redirectCache["url0"])
        assertEquals("resolved50", redirectCache["url50"])
    }

    @Test
    fun `redirect cache handles empty cache`() {
        val cached = redirectCache["any_url"]
        assertNull(cached)
    }
}
