package forpdateam.ru.forpda.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class YouTubeUrlTest {

    @Test
    fun extractVideoId_supportsWatchUrls() {
        assertEquals("dQw4w9WgXcQ", YouTubeUrl.extractVideoId("https://www.youtube.com/watch?v=dQw4w9WgXcQ"))
        assertEquals("dQw4w9WgXcQ", YouTubeUrl.extractVideoId("https://youtube.com/watch?feature=share&v=dQw4w9WgXcQ&t=1"))
        assertEquals("dQw4w9WgXcQ", YouTubeUrl.extractVideoId("https://m.youtube.com/watch?v=dQw4w9WgXcQ"))
    }

    @Test
    fun extractVideoId_supportsShortAndEmbedUrls() {
        assertEquals("dQw4w9WgXcQ", YouTubeUrl.extractVideoId("https://youtu.be/dQw4w9WgXcQ"))
        assertEquals("dQw4w9WgXcQ", YouTubeUrl.extractVideoId("https://www.youtube.com/embed/dQw4w9WgXcQ"))
        assertEquals("dQw4w9WgXcQ", YouTubeUrl.extractVideoId("https://www.youtube-nocookie.com/embed/dQw4w9WgXcQ"))
    }

    @Test
    fun extractVideoId_supportsShortsAndLiveUrls() {
        assertEquals("dQw4w9WgXcQ", YouTubeUrl.extractVideoId("https://www.youtube.com/shorts/dQw4w9WgXcQ"))
        assertEquals("dQw4w9WgXcQ", YouTubeUrl.extractVideoId("https://youtube.com/shorts/dQw4w9WgXcQ?feature=share"))
        assertEquals("dQw4w9WgXcQ", YouTubeUrl.extractVideoId("https://m.youtube.com/live/dQw4w9WgXcQ"))
        assertEquals("dQw4w9WgXcQ", YouTubeUrl.extractVideoId("https://www.youtube.com/v/dQw4w9WgXcQ"))
    }

    @Test
    fun extractVideoId_rejectsUnsupportedUrls() {
        assertNull(YouTubeUrl.extractVideoId("https://example.com/watch?v=dQw4w9WgXcQ"))
        assertNull(YouTubeUrl.extractVideoId("https://www.youtube.com/watch?v=short"))
        assertNull(YouTubeUrl.extractVideoId("javascript:alert(1)"))
        assertNull(YouTubeUrl.extractVideoId(null))
    }
}
