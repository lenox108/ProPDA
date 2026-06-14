package forpdateam.ru.forpda.model.data.remote.api.editpost

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EditPostSubmitUrlTest {

    @Test
    fun applySubmittedPageSt_addsStWhenRedirectOmitsOffset() {
        val url = "https://4pda.to/forum/index.php?showtopic=123"
        val out = EditPostSubmitUrl.applySubmittedPageSt(url, 20)
        assertTrue(out.contains("st=20"))
    }

    @Test
    fun applySubmittedPageSt_replacesZeroSt() {
        val url = "https://4pda.to/forum/index.php?showtopic=123&st=0"
        val out = EditPostSubmitUrl.applySubmittedPageSt(url, 40)
        assertTrue(out.contains("st=40"))
    }

    @Test
    fun applySubmittedPageSt_preservesExistingNonZeroSt() {
        val url = "https://4pda.to/forum/index.php?showtopic=123&st=40"
        val out = EditPostSubmitUrl.applySubmittedPageSt(url, 20)
        assertEquals(url, out)
    }

    @Test
    fun applySubmittedPageSt_preservesHash() {
        val url = "https://4pda.to/forum/index.php?showtopic=99#entry555"
        val out = EditPostSubmitUrl.applySubmittedPageSt(url, 15)
        assertTrue(out.contains("st=15"))
        assertTrue(out.endsWith("#entry555"))
    }

    @Test
    fun applySubmittedPageSt_noOpForZeroSubmittedSt() {
        val url = "https://4pda.to/forum/index.php?showtopic=123"
        assertEquals(url, EditPostSubmitUrl.applySubmittedPageSt(url, 0))
    }

    @Test
    fun buildPostedFullPageUrl_usesSubmittedStAndEntryAnchor() {
        val out = EditPostSubmitUrl.buildPostedFullPageUrl(topicId = 77, submittedSt = 420, scrollToPostId = 999)
        assertEquals(
                "https://4pda.to/forum/index.php?showtopic=77&st=420#entry999",
                out
        )
    }

    @Test
    fun buildPostedFullPageUrl_omitsStOnFirstPage() {
        val out = EditPostSubmitUrl.buildPostedFullPageUrl(topicId = 5, submittedSt = 0, scrollToPostId = 12)
        assertEquals("https://4pda.to/forum/index.php?showtopic=5#entry12", out)
    }
}
