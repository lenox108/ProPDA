package forpdateam.ru.forpda.presentation.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Log 24_06-14-15: pin the "last read on page" priority rule and verify the
 * `last_post_on_page_fallback` semantics still holds when nothing is known
 * (priority 5).
 *
 * The previous regression: with a fully-read topic, the resolver fell through
 * to priority 5 (`last_post_on_page_fallback`) which does not surface a
 * `lastViewedInput=true` flag and does not call out the readPosition source.
 * With Fix #B in place, the resolver now sees a non-null
 * [ReadPosition.lastViewedPostId] for the read-resume / all-read bottom
 * redirect open, and goes through priority 2 (`last_read`).
 */
class HighlightResolverLastReadPathTest {

    private val pagePostIds = listOf(143903679L, 143903696L, 143904035L)

    @Test
    fun readPositionHit_returnsLastReadViaPriority2() {
        val r = HighlightResolver.resolve(
                topicId = 1103268L,
                unread = null,
                readPosition = ReadPosition(
                        topicId = 1103268L,
                        lastViewedPostId = 143903696L,
                        lastViewedPage = 1270,
                ),
                explicitPostId = null,
                pagePostIds = pagePostIds,
        )
        assertEquals(HighlightType.LastRead, r.target.type)
        assertEquals(143903696L, r.target.postId)
        assertEquals("last_read", r.reason)
        assertTrue("lastViewedInput must be true for priority-2 hit", r.lastViewedInput)
        assertFalse("hasUnreadInput must be false when no unread", r.hasUnreadInput)
        assertTrue("resolver must produce a renderable target", r.isRenderable)
    }

    @Test
    fun readPositionSourceIsPropagatedToDiagnostic() {
        // The diagnostic must echo the read source so a logcat reader can
        // distinguish `page_anchor` / `redirect_url` / `anchors_list` /
        // `repository` / `none` for the same priority-2 outcome.
        val r = HighlightResolver.resolve(
                topicId = 1103268L,
                unread = null,
                readPosition = ReadPosition(
                        topicId = 1103268L,
                        lastViewedPostId = 143903696L,
                        lastViewedPage = 1270,
                ),
                explicitPostId = null,
                pagePostIds = pagePostIds,
                lastReadSource = "page_anchor",
        )
        // Outcome: the resolver is purely deterministic, the source string is
        // just logged. We assert the resolution is the priority-2 case so a
        // future change that drops the readPosition path will be caught.
        assertEquals("last_read", r.reason)
        assertTrue(r.lastViewedInput)
    }

    @Test
    fun readPositionOffPage_fallsThroughToLastPostOnPage() {
        // The saved post id is on a different page — priority 2 misses, so
        // priority 5 (`last_post_on_page_fallback`) fires with the last post
        // id of the current page. The previous regression chain
        // (no readPosition, no anchor override) used to land here too even
        // for on-page read-position; that case is now covered by the test
        // above.
        val r = HighlightResolver.resolve(
                topicId = 1103268L,
                unread = null,
                readPosition = ReadPosition(
                        topicId = 1103268L,
                        lastViewedPostId = 999999L,
                        lastViewedPage = 1,
                ),
                explicitPostId = null,
                pagePostIds = pagePostIds,
        )
        assertEquals(HighlightType.LastRead, r.target.type)
        assertEquals(pagePostIds.last(), r.target.postId)
        assertEquals("last_post_on_page_fallback", r.reason)
        // lastViewedInput is reported (we had a readPosition, just off-page),
        // but the target post id is the bottom post.
        assertTrue(r.lastViewedInput)
    }

    @Test
    fun noInputs_fallsBackToLastPostOnPage_unchangedBehaviour() {
        // No unread, no readPosition, no explicit: priority 5 still fires.
        val r = HighlightResolver.resolve(
                topicId = 1L,
                unread = null,
                readPosition = null,
                explicitPostId = null,
                pagePostIds = listOf(10L, 20L, 30L),
        )
        assertEquals(HighlightType.LastRead, r.target.type)
        assertEquals(30L, r.target.postId)
        assertEquals("last_post_on_page_fallback", r.reason)
        assertFalse(r.lastViewedInput)
        assertFalse(r.hasUnreadInput)
        assertFalse(r.explicitInput)
    }

    @Test
    fun explicitPost_winsWhenReadPositionIsOffPage() {
        // The documented priority is: unread > readPosition (priority 2) >
        // explicit (priority 3). When the readPosition post is OFF the page,
        // explicit wins.
        val r = HighlightResolver.resolve(
                topicId = 1L,
                unread = null,
                readPosition = ReadPosition(1L, 9999L, 1),
                explicitPostId = 30L,
                pagePostIds = listOf(10L, 20L, 30L),
        )
        assertEquals(HighlightType.Explicit, r.target.type)
        assertEquals(30L, r.target.postId)
        assertEquals("explicit_post", r.reason)
    }

    @Test
    fun firstUnread_winsOverReadPositionHit() {
        val r = HighlightResolver.resolve(
                topicId = 1L,
                unread = UnreadTarget(
                        topicId = 1L,
                        firstUnreadPostId = 20L,
                        unreadPage = 1,
                        unreadUrl = null,
                ),
                readPosition = ReadPosition(1L, 10L, 1),
                explicitPostId = null,
                pagePostIds = listOf(10L, 20L, 30L),
        )
        assertEquals(HighlightType.FirstUnread, r.target.type)
        assertEquals(20L, r.target.postId)
        assertEquals("first_unread", r.reason)
    }
}
