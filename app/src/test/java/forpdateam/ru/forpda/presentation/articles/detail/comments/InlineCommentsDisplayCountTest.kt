package forpdateam.ru.forpda.presentation.articles.detail.comments

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InlineCommentsDisplayCountTest {

    @Test
    fun `resolveExpectedCount prefers list hint over mobile undercount`() {
        assertEquals(353, InlineCommentsDisplayCount.resolveExpectedCount(27, 353))
        assertEquals(27, InlineCommentsDisplayCount.resolveExpectedCount(27, 0))
        assertEquals(12, InlineCommentsDisplayCount.resolveExpectedCount(0, 12))
    }

    @Test
    fun `mergeMetadataCount blocks inflation above dom count`() {
        assertEquals(27, InlineCommentsDisplayCount.mergeMetadataCount(27, 222, 27))
    }

    @Test
    fun `mergeMetadataCount keeps list total over first batch dom ceiling`() {
        assertEquals(33, InlineCommentsDisplayCount.mergeMetadataCount(20, 33, 20))
    }

    @Test
    fun `mergeMetadataCount allows increase when below dom ceiling`() {
        assertEquals(12, InlineCommentsDisplayCount.mergeMetadataCount(5, 12, 27))
    }

    @Test
    fun `mergeMetadataCount uses metadata when dom unknown`() {
        assertEquals(188, InlineCommentsDisplayCount.mergeMetadataCount(27, 188, null))
    }

    @Test
    fun `shouldPatchWebViewAfterPrefetch when counts already reconciled`() {
        assertTrue(InlineCommentsDisplayCount.shouldPatchWebViewAfterPrefetch(27, 27))
    }
}
