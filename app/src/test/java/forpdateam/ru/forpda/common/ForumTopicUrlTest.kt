package forpdateam.ru.forpda.common

import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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

    @Test
    fun absolutizeForumHref_relativeAndQuery() {
        assertEquals(
                "https://4pda.to/forum/index.php?showtopic=99",
                absolutizeFourPdaForumHref("/forum/index.php?showtopic=99")
        )
        assertEquals(
                "https://4pda.to/forum/index.php?showtopic=1&f=2",
                absolutizeFourPdaForumHref("?showtopic=1&f=2")
        )
    }

    @Test
    fun uriFromListing_prefersHrefWhenShowtopicMatches() {
        val u = uriForOpeningTopicFromListing(
                "/forum/index.php?showtopic=123&view=getlastpost",
                123
        )
        assertTrue(u.toString().contains("showtopic=123"))
        assertTrue(u.toString().contains("view=getlastpost"))
    }

    @Test
    fun uriFromListing_ignoresMismatchedHrefShowtopic() {
        val u = uriForOpeningTopicFromListing(
                "/forum/index.php?showtopic=77&view=getlastpost",
                123
        )
        assertEquals("https://4pda.to/forum/index.php?showtopic=123", u.toString())
    }

    @Test
    fun uriFromListing_fallsBackToId() {
        val u = uriForOpeningTopicFromListing(null, 555)
        assertEquals("https://4pda.to/forum/index.php?showtopic=555", u.toString())
    }

    @Test
    fun openingFromListing_relocatedStub_doesNotAddGetnewpost() {
        val out = topicUrlForOpeningFromListing(
                listingHref = "https://4pda.to/forum/index.php?showtopic=1121632",
                topicId = 1121632,
                isRelocated = true
        )
        assertEquals("https://4pda.to/forum/index.php?showtopic=1121632", out)
        assertFalse(out.contains("view=getnewpost"))
    }

    @Test
    fun openingFromListing_regularTopic_keepsPlainHref() {
        val out = topicUrlForOpeningFromListing(
                listingHref = "https://4pda.to/forum/index.php?showtopic=1121568",
                topicId = 1121568,
                isRelocated = false
        )
        assertTrue(out.contains("showtopic=1121568"))
        assertFalse(out.contains("view=getnewpost"))
    }

    @Test
    fun topicOpenListHints_synthesizesGetNewPostWhenTopicMarkedUnread() {
        val hints = topicOpenListHintsFromListing(
                listingHref = "https://4pda.to/forum/index.php?showtopic=555",
                topicId = 555,
                topicMarkedUnread = true
        )
        assertTrue(hints.topicMarkedUnread)
        assertEquals(
                "https://4pda.to/forum/index.php?showtopic=555&view=getnewpost",
                hints.unreadUrlFromList
        )
    }

    @Test
    fun topicOpenListHints_stripResumeStFromGetLastPost() {
        val hints = topicOpenListHintsFromListing(
                listingHref = "https://4pda.to/forum/index.php?showtopic=123&st=40&view=getlastpost",
                topicId = 123
        )
        assertEquals(
                "https://4pda.to/forum/index.php?showtopic=123&view=getnewpost",
                hints.unreadUrlFromList
        )
        assertNull(hints.unreadPostIdFromList)
    }

    @Test
    fun topicOpenListHints_preservesExplicitUnreadEntryAnchor() {
        val hints = topicOpenListHintsFromListing(
                listingHref = "https://4pda.to/forum/index.php?showtopic=123&st=40&view=getnewpost#entry999",
                topicId = 123,
                topicMarkedUnread = true
        )
        assertEquals(
                "https://4pda.to/forum/index.php?showtopic=123&st=40&view=getnewpost#entry999",
                hints.unreadUrlFromList
        )
    }

    @Test
    fun stripTopicListResumeSt_removesNonZeroSt() {
        assertEquals(
                "https://4pda.to/forum/index.php?showtopic=123&view=getnewpost",
                stripTopicListResumeSt("https://4pda.to/forum/index.php?showtopic=123&st=40&view=getnewpost")
        )
    }

    @Test
    fun stripTopicLastReadPostParams_removesPAndPid() {
        assertEquals(
                "https://4pda.to/forum/index.php?showtopic=123&view=getnewpost",
                stripTopicLastReadPostParams("https://4pda.to/forum/index.php?showtopic=123&view=getnewpost&p=999")
        )
        assertEquals(
                "https://4pda.to/forum/index.php?showtopic=123&view=getnewpost&highlight=555",
                stripTopicLastReadPostParams("https://4pda.to/forum/index.php?showtopic=123&view=getnewpost&p=999&highlight=555")
        )
    }

    @Test
    fun openingFromListing_relocatedStub_nullHref_usesId() {
        val out = topicUrlForOpeningFromListing(
                listingHref = null,
                topicId = 999,
                isRelocated = true
        )
        assertEquals("https://4pda.to/forum/index.php?showtopic=999", out)
    }
}
