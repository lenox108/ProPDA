package forpdateam.ru.forpda.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FourPdaImageUrlsTest {

    @Test
    fun `resolveViewerUrl prefers forum dl href over signed preview`() {
        val preview = "https://4pda.to/s/Zy0hJdJhXo4sHCLpS6kl8e2vWUPl8z08mpImL7l5uxWk.jpg"
        val full = "https://4pda.to/forum/dl/post/35620118/Screenshot_2026-06-08-15-11-58-62_b783bf344239542886fee7b48fa4b892.jpg"

        assertEquals(full, FourPdaImageUrls.resolveViewerUrl(preview, full))
    }

    @Test
    fun `resolveViewerUrl strips wordpress size suffix for news images`() {
        val preview = "https://4pda.to/wp-content/uploads/2024/01/image-768x430.jpg"

        assertEquals(
                "https://4pda.to/wp-content/uploads/2024/01/image.jpg",
                FourPdaImageUrls.resolveViewerUrl(preview)
        )
    }

    @Test
    fun `resolveViewerUrl keeps forum dl url when already full quality`() {
        val full = "https://4pda.to/forum/dl/post/1/full-one.png"

        assertEquals(full, FourPdaImageUrls.resolveViewerUrl(full))
    }

    @Test
    fun `isPreviewOrThumbnailUrl detects signed cdn and thumb uploads`() {
        assertTrue(FourPdaImageUrls.isPreviewOrThumbnailUrl("https://4pda.to/s/abc.jpg"))
        assertTrue(
                FourPdaImageUrls.isPreviewOrThumbnailUrl(
                        "https://s.4pda.to/forum/uploads/post-1/thumb-one.png"
                )
        )
        assertFalse(FourPdaImageUrls.isPreviewOrThumbnailUrl("https://4pda.to/forum/dl/post/1/full-one.png"))
    }

    @Test
    fun `isViewableInViewer accepts forum attachments and wp uploads`() {
        assertTrue(
                FourPdaImageUrls.isViewableInViewer(
                        "https://4pda.to/forum/dl/post/123/photo.webp?download=1"
                )
        )
        assertTrue(
                FourPdaImageUrls.isViewableInViewer(
                        "https://4pda.to/wp-content/uploads/hero.jpg"
                )
        )
        assertFalse(FourPdaImageUrls.isViewableInViewer("https://example.com/pic.jpg"))
    }
}
