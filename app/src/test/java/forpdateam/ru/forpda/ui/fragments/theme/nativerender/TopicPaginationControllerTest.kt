package forpdateam.ru.forpda.ui.fragments.theme.nativerender

import forpdateam.ru.forpda.entity.remote.others.pagination.Pagination
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure tests for downward pagination (native-topic-renderer.md, Фаза 1 infinite scroll):
 * next-page URL geometry (0-based `st`), has-next, dedup across overlapping server pages.
 */
class TopicPaginationControllerTest {

    private fun pagination(current: Int, all: Int, perPage: Int = 20) = Pagination().apply {
        this.current = current
        this.all = all
        this.perPage = perPage
    }

    private fun items(vararg ids: Int) = ids.map { id ->
        NativePostItem(
            postId = id, number = 0, userId = 0, nick = null, avatarUrl = null, group = null,
            groupColor = null, date = null, reputation = null, postRating = null, isCurator = false,
            isOnline = false, blocks = emptyList(), canEdit = false, canDelete = false,
            canQuote = false, canReport = false, canPlusRep = false, canMinusRep = false,
            canPlusPostRating = false, canMinusPostRating = false,
        )
    }

    @Test
    fun freshFirstPage_hasNext_whenMorePagesExist() {
        val c = TopicPaginationController()
        c.reset(topicId = 42, pagination = pagination(current = 1, all = 3), initialItems = items(1, 2))
        assertTrue(c.hasNextPage())
        // Page 2 → st = (2-1)*20 = 20 → loadedPage(1) * perPage(20).
        assertEquals("https://4pda.to/forum/index.php?showtopic=42&st=20", c.nextPageUrl())
    }

    @Test
    fun lastPage_hasNoNext() {
        val c = TopicPaginationController()
        c.reset(topicId = 42, pagination = pagination(current = 3, all = 3), initialItems = items(1))
        assertFalse(c.hasNextPage())
        assertNull(c.nextPageUrl())
    }

    @Test
    fun singlePageTopic_hasNoNext() {
        val c = TopicPaginationController()
        c.reset(topicId = 7, pagination = pagination(current = 1, all = 1), initialItems = items(1, 2, 3))
        assertFalse(c.hasNextPage())
    }

    @Test
    fun uninitialised_hasNoNext() {
        assertFalse(TopicPaginationController().hasNextPage())
        assertNull(TopicPaginationController().nextPageUrl())
    }

    @Test
    fun nextPageUrl_advancesAfterAppend() {
        val c = TopicPaginationController()
        c.reset(topicId = 42, pagination = pagination(current = 1, all = 4), initialItems = items(1, 2))
        assertEquals("https://4pda.to/forum/index.php?showtopic=42&st=20", c.nextPageUrl())
        c.onPageAppended(pageNumber = 2, pagination = pagination(current = 2, all = 4))
        // Now page 3 → st = 2*20 = 40.
        assertEquals("https://4pda.to/forum/index.php?showtopic=42&st=40", c.nextPageUrl())
    }

    @Test
    fun dedup_dropsPostsAlreadySeen_onOverlappingPage() {
        val c = TopicPaginationController()
        c.reset(topicId = 42, pagination = pagination(current = 1, all = 3), initialItems = items(1, 2))
        // Next page overlaps by post 2 and brings 3, 4.
        val newOnly = c.registerAndFilterNew(items(2, 3, 4))
        assertEquals(listOf(3, 4), newOnly.map { it.postId })
        // And 3/4 are now themselves deduped if they reappear.
        assertEquals(emptyList<Int>(), c.registerAndFilterNew(items(3, 4)).map { it.postId })
    }

    @Test
    fun openedAtPage1_hasNoPrevPage() {
        val c = TopicPaginationController()
        c.reset(topicId = 42, pagination = pagination(current = 1, all = 5), initialItems = items(1))
        assertFalse(c.hasPrevPage())
        assertNull(c.prevPageUrl())
    }

    @Test
    fun openedMidTopic_hasPrevPage_withCorrectSt() {
        val c = TopicPaginationController()
        // Landed on page 3 (e.g. from unread): page above is 2 → st = (2-1)*20 = 20.
        c.reset(topicId = 42, pagination = pagination(current = 3, all = 5), initialItems = items(41, 42))
        assertTrue(c.hasPrevPage())
        assertEquals("https://4pda.to/forum/index.php?showtopic=42&st=20", c.prevPageUrl())
    }

    @Test
    fun prevPageUrl_advancesUpwardAfterPrepend() {
        val c = TopicPaginationController()
        c.reset(topicId = 42, pagination = pagination(current = 3, all = 5), initialItems = items(41))
        assertEquals("https://4pda.to/forum/index.php?showtopic=42&st=20", c.prevPageUrl())
        c.onPagePrepended(pageNumber = 2)
        // Now the top is page 2 → page above is 1 → st = 0.
        assertEquals("https://4pda.to/forum/index.php?showtopic=42&st=0", c.prevPageUrl())
        c.onPagePrepended(pageNumber = 1)
        assertFalse(c.hasPrevPage())
        assertNull(c.prevPageUrl())
    }

    @Test
    fun upwardAndDownwardEdges_areIndependent() {
        val c = TopicPaginationController()
        c.reset(topicId = 42, pagination = pagination(current = 3, all = 6), initialItems = items(41, 42))
        // Down goes to page 4 (st=60), up goes to page 2 (st=20) — from the same middle start.
        assertEquals("https://4pda.to/forum/index.php?showtopic=42&st=60", c.nextPageUrl())
        assertEquals("https://4pda.to/forum/index.php?showtopic=42&st=20", c.prevPageUrl())
    }

    @Test
    fun totalPages_canGrowWhileReading() {
        val c = TopicPaginationController()
        c.reset(topicId = 42, pagination = pagination(current = 1, all = 2), initialItems = items(1))
        c.onPageAppended(pageNumber = 2, pagination = pagination(current = 2, all = 5))
        // A new page appeared since first load → still has next.
        assertTrue(c.hasNextPage())
        assertEquals(5, c.totalPages)
    }
}
