package forpdateam.ru.forpda.ui.fragments.news.details

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class NewsArticleUrlsTest {

    @Test
    fun normalizeExternal_passesThroughHttpsUrl() {
        assertEquals("https://example.com/x", NewsArticleUrls.normalizeExternal("https://example.com/x"))
    }

    @Test
    fun normalizeExternal_blocksDangerousScheme() {
        assertNull(NewsArticleUrls.normalizeExternal("javascript:alert(1)"))
    }

    @Test
    fun normalizeTaxonomy_acceptsCategoryPath() {
        val out = NewsArticleUrls.normalizeTaxonomy("https://4pda.to/category/games/")
        assertEquals("https://4pda.to/category/games/", out)
    }

    @Test
    fun normalizeTaxonomy_acceptsSingleSegmentSection() {
        assertTrue(NewsArticleUrls.normalizeTaxonomy("/news/")!!.startsWith("https://4pda.to/news"))
    }

    @Test
    fun normalizeTaxonomy_forcesHttpsAndAbsolutizesProtocolRelative() {
        val out = NewsArticleUrls.normalizeTaxonomy("//4pda.to/category/soft/")
        assertTrue(out!!.startsWith("https://4pda.to/category/soft"))
    }

    @Test
    fun normalizeTaxonomy_rejectsForeignHost() {
        assertNull(NewsArticleUrls.normalizeTaxonomy("https://evil.com/category/games/"))
    }

    @Test
    fun normalizeTaxonomy_rejectsDeepArticlePath() {
        // Многосегментный путь (конкретная статья) — не таксономия.
        assertNull(NewsArticleUrls.normalizeTaxonomy("https://4pda.to/2026/07/15/123456/"))
    }

    @Test
    fun normalizeTaxonomy_rejectsBlank() {
        assertNull(NewsArticleUrls.normalizeTaxonomy("   "))
    }
}
