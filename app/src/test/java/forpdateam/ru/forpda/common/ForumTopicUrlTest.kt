package forpdateam.ru.forpda.common

import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ForumTopicUrlTest {

    @Test
    fun plainShowtopic_addsGetnewpost() {
        val u = Uri.parse("https://4pda.to/forum/index.php?showtopic=123")
        assertTrue(topicUrlWithUnreadIfPlainOpen(u).contains("view=getnewpost"))
    }

    @Test
    fun showtopicWithStZero_stillAddsGetnewpost() {
        val u = Uri.parse("https://4pda.to/forum/index.php?showtopic=123&st=0")
        assertTrue(topicUrlWithUnreadIfPlainOpen(u).contains("view=getnewpost"))
    }

    @Test
    fun showtopicWithStNonZero_doesNotAddGetnewpost() {
        val u = Uri.parse("https://4pda.to/forum/index.php?showtopic=123&st=40")
        val out = topicUrlWithUnreadIfPlainOpen(u)
        assertEquals("https://4pda.to/forum/index.php?showtopic=123&st=40", out)
    }

    @Test
    fun showtopicWithP_addsFindpost() {
        val u = Uri.parse("https://4pda.to/forum/index.php?showtopic=123&p=999")
        val out = topicUrlWithUnreadIfPlainOpen(u)
        assertTrue(out.contains("view=findpost"))
        assertTrue(out.contains("p=999"))
    }

    @Test
    fun existingViewPreserved() {
        val u = Uri.parse("https://4pda.to/forum/index.php?showtopic=123&view=getlastpost")
        assertEquals(u.toString(), topicUrlWithUnreadIfPlainOpen(u))
    }

    @Test
    fun topicUrlStZero_notTreatedAsPaginationOffset() {
        assertFalse(topicUrlHasNonZeroStParameter("https://4pda.to/forum/index.php?showtopic=1&st=0"))
    }

    @Test
    fun topicUrlStNonZero_detected() {
        assertTrue(topicUrlHasNonZeroStParameter("https://4pda.to/forum/index.php?showtopic=1&st=40"))
    }
}
