package forpdateam.ru.forpda.model.repository.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Cross-tab return-position store contract (multi-back anchor loss, log 24_06-23-12-50). The store
 * remembers the real per-topic viewed position so a TAB re-entry can restore it instead of
 * re-opening fresh via getlastpost.
 */
class TopicReturnPositionStoreTest {

    @Test
    fun save_thenPeek_returnsNormalizedPosition() {
        val store = TopicReturnPositionStore()
        store.save(topicId = 1121483, pageSt = 1180, postId = "entry143876380", scrollY = 10099)
        val got = store.peek(1121483)
        assertEquals(1121483, got!!.topicId)
        assertEquals(1180, got.pageSt)
        // entry prefix is stripped so the restore URL builder can re-add #entry.
        assertEquals("143876380", got.postId)
        assertEquals(10099, got.scrollY)
    }

    @Test
    fun save_withoutUsablePostId_isIgnored_soGoodPositionIsNotDowngraded() {
        val store = TopicReturnPositionStore()
        store.save(topicId = 1121483, pageSt = 1180, postId = "143876380", scrollY = 10099)
        // A later page-top settle with no anchor must not erase the good saved position.
        store.save(topicId = 1121483, pageSt = 1200, postId = null, scrollY = 0)
        store.save(topicId = 1121483, pageSt = 1200, postId = "  ", scrollY = 0)
        assertEquals("143876380", store.peek(1121483)!!.postId)
        assertEquals(1180, store.peek(1121483)!!.pageSt)
    }

    @Test
    fun save_doesNotClobberUnrelatedTopic() {
        val store = TopicReturnPositionStore()
        store.save(1121483, 1180, "143876380", 10099)
        store.save(239158, 0, "100000001", 0)
        assertEquals("143876380", store.peek(1121483)!!.postId)
        assertEquals("100000001", store.peek(239158)!!.postId)
    }

    @Test
    fun latestSaveWins_soReentryRestoresTheMostRecentlyViewedPosition() {
        val store = TopicReturnPositionStore()
        store.save(1121483, 1160, "143870000", 5000)
        store.save(1121483, 1180, "143876380", 10099)
        assertEquals(1180, store.peek(1121483)!!.pageSt)
        assertEquals("143876380", store.peek(1121483)!!.postId)
    }

    @Test
    fun clear_removesOnlyTargetedTopic() {
        val store = TopicReturnPositionStore()
        store.save(1121483, 1180, "143876380", 10099)
        store.save(239158, 0, "100000001", 0)
        store.clear(1121483)
        assertNull(store.peek(1121483))
        assertEquals("100000001", store.peek(239158)!!.postId)
    }

    @Test
    fun nonPositiveTopicId_isRejected() {
        val store = TopicReturnPositionStore()
        store.save(0, 0, "1", 0)
        store.save(-5, 0, "1", 0)
        assertNull(store.peek(0))
        assertNull(store.peek(-5))
    }

    /**
     * Read-seal contract (log 25_06-10-52-43, "stuck on stale already-read post 143999521"):
     * once a topic is marked READ, the store must drop its saved position and ignore further saves
     * so a fresh re-open defers to the server getlastpost bookmark instead of a drifted-up snapshot.
     */
    @Test
    fun markRead_clearsSavedPosition_andSuppressesFurtherSaves() {
        val store = TopicReturnPositionStore()
        store.save(1103268, 26340, "143999828", 4212)
        assertEquals("143999828", store.peek(1103268)!!.postId)

        store.markRead(1103268)
        assertNull("mark-read must drop the saved position", store.peek(1103268))

        // The "scrolled up to re-read" snapshot must NOT repopulate the store while sealed.
        store.save(1103268, 26340, "143999521", 20794)
        assertNull("a sealed (already-read) topic must not record a hijacking snapshot", store.peek(1103268))
        assertEquals(true, store.isReadSealed(1103268))
    }

    @Test
    fun repeatedSavesAfterMarkRead_neverStick_soEveryReopenDefersToServerBookmark() {
        val store = TopicReturnPositionStore()
        store.markRead(1103268)
        // Simulate three consecutive opens where the user drifts to the top post each time.
        store.save(1103268, 26340, "143999521", 20794)
        store.save(1103268, 26340, "143999521", 18000)
        store.save(1103268, 26340, "143999521", 16000)
        assertNull("no save may stick on a read topic -> getlastpost wins every time", store.peek(1103268))
    }

    @Test
    fun clearReadSeal_reenablesSaving_whenTopicIsUnreadAgain() {
        val store = TopicReturnPositionStore()
        store.markRead(1103268)
        store.save(1103268, 26340, "143999521", 20794)
        assertNull(store.peek(1103268))

        // Topic becomes unread again (server unread target) -> seal lifted, in-progress save resumes.
        store.clearReadSeal(1103268)
        assertEquals(false, store.isReadSealed(1103268))
        store.save(1103268, 26360, "144000000", 1200)
        assertEquals("144000000", store.peek(1103268)!!.postId)
    }

    @Test
    fun markRead_isScopedToTopic_doesNotSealOthers() {
        val store = TopicReturnPositionStore()
        store.save(1121483, 1180, "143876380", 10099)
        store.markRead(1103268)
        // The documented multi-back topic (not marked read) keeps its restorable position.
        assertEquals("143876380", store.peek(1121483)!!.postId)
        store.save(1121483, 1200, "143880000", 12000)
        assertEquals("143880000", store.peek(1121483)!!.postId)
    }
}
