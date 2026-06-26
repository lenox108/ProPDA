package forpdateam.ru.forpda.presentation.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Step 9 / T1-T5: deterministic resolver behaviour for the topic post highlight.
 *
 * The resolver is the *only* place that decides which post gets the
 * `post-highlight-*` class on a freshly-rendered page. Tests cover the four
 * priority rules from the spec, missing-input fall-through, and the
 * determinism requirement.
 */
class HighlightResolverTest {

    private val pagePostIds = listOf(100L, 200L, 300L, 400L, 500L)

    @Test
    fun lastRead_withReadPosition_returnsLastRead() {
        val r = HighlightResolver.resolve(
                topicId = 1L,
                unread = null,
                readPosition = ReadPosition(
                        topicId = 1L,
                        lastViewedPostId = 300L,
                        lastViewedPage = 2,
                ),
                explicitPostId = null,
                pagePostIds = pagePostIds,
        )
        assertEquals(HighlightType.LastRead, r.target.type)
        assertEquals(300L, r.target.postId)
        assertEquals("last_read", r.reason)
        assertTrue(r.lastViewedInput)
        assertFalse(r.hasUnreadInput)
        assertFalse(r.explicitInput)
    }

    @Test
    fun firstUnread_withUnreadTarget_returnsFirstUnread() {
        val unread = UnreadTarget(
                topicId = 1L,
                firstUnreadPostId = 200L,
                unreadPage = 1,
                unreadUrl = null,
        )
        val r = HighlightResolver.resolve(
                topicId = 1L,
                unread = unread,
                readPosition = ReadPosition(
                        topicId = 1L,
                        lastViewedPostId = 100L,
                        lastViewedPage = 1,
                ),
                explicitPostId = 500L,
                pagePostIds = pagePostIds,
        )
        assertEquals(HighlightType.FirstUnread, r.target.type)
        assertEquals(200L, r.target.postId)
        assertEquals("first_unread", r.reason)
        assertTrue(r.hasUnreadInput)
    }

    @Test
    fun explicitPost_doesNotOverrideFirstUnread() {
        val unread = UnreadTarget(
                topicId = 1L,
                firstUnreadPostId = 200L,
                unreadPage = null,
                unreadUrl = null,
        )
        val r = HighlightResolver.resolve(
                topicId = 1L,
                unread = unread,
                readPosition = null,
                explicitPostId = 500L,
                pagePostIds = pagePostIds,
        )
        assertEquals(HighlightType.FirstUnread, r.target.type)
        assertEquals(200L, r.target.postId)
    }

    @Test
    fun missingPostId_returnsNoneWithMissingReason() {
        val r = HighlightResolver.resolve(
                topicId = 1L,
                unread = UnreadTarget(
                        topicId = 1L,
                        firstUnreadPostId = 999L,
                        unreadPage = 3,
                        unreadUrl = null,
                ),
                readPosition = ReadPosition(
                        topicId = 1L,
                        lastViewedPostId = 888L,
                        lastViewedPage = 5,
                ),
                explicitPostId = 777L,
                pagePostIds = pagePostIds,
        )
        assertSame(HighlightTarget.None, r.target)
        assertFalse(r.isRenderable)
        // Resolver reports the *first* available input kind that was on the
        // page is missing. Unread wins over last read.
        assertEquals("unread_off_page", r.reason)
    }

    @Test
    fun noInputs_fallsBackToLastPostOnPage() {
        val r = HighlightResolver.resolve(
                topicId = 1L,
                unread = null,
                readPosition = null,
                explicitPostId = null,
                pagePostIds = pagePostIds,
        )
        assertEquals(HighlightType.LastRead, r.target.type)
        assertEquals(500L, r.target.postId)
        assertEquals("last_post_on_page_fallback", r.reason)
    }

    @Test
    fun resolver_isDeterministic() {
        val unread = UnreadTarget(
                topicId = 1L,
                firstUnreadPostId = 300L,
                unreadPage = 1,
                unreadUrl = null,
        )
        val read = ReadPosition(
                topicId = 1L,
                lastViewedPostId = 200L,
                lastViewedPage = 1,
        )
        val a = HighlightResolver.resolve(1L, unread, read, 500L, pagePostIds)
        val b = HighlightResolver.resolve(1L, unread, read, 500L, pagePostIds)
        assertEquals(a, b)
    }

    @Test
    fun resolver_sameInputs_samePostIdAcrossCalls() {
        val read = ReadPosition(
                topicId = 1103268L,
                lastViewedPostId = 143903696L,
                lastViewedPage = 1270,
        )
        val ids = listOf(143903679L, 143903696L, 143904035L)
        val first = HighlightResolver.resolve(1103268L, null, read, null, ids)
        val second = HighlightResolver.resolve(1103268L, null, read, null, ids)
        assertEquals(first.target.postId, second.target.postId)
        assertEquals(143903696L, first.target.postId)
    }

    @Test
    fun refresh_sameInputs_sameTarget() {
        // Simulates a refresh: resolver re-invoked with the same inputs.
        val unread = UnreadTarget(
                topicId = 7L,
                firstUnreadPostId = 400L,
                unreadPage = 1,
                unreadUrl = null,
        )
        val r1 = HighlightResolver.resolve(7L, unread, null, null, pagePostIds)
        val r2 = HighlightResolver.resolve(7L, unread, null, null, pagePostIds)
        assertEquals(HighlightType.FirstUnread, r1.target.type)
        assertEquals(400L, r1.target.postId)
        assertEquals(r1, r2)
    }

    @Test
    fun explicitPost_fallsBackToLastReadWhenExplicitMissing() {
        val read = ReadPosition(
                topicId = 1L,
                lastViewedPostId = 300L,
                lastViewedPage = 1,
        )
        val r = HighlightResolver.resolve(
                topicId = 1L,
                unread = null,
                readPosition = read,
                explicitPostId = 9999L, // off page
                pagePostIds = pagePostIds,
        )
        assertEquals(HighlightType.LastRead, r.target.type)
        assertEquals(300L, r.target.postId)
    }

    @Test
    fun lastRead_missing_thenExplicitWins() {
        val r = HighlightResolver.resolve(
                topicId = 1L,
                unread = null,
                readPosition = ReadPosition(1L, 9999L, 5),
                explicitPostId = 400L,
                pagePostIds = pagePostIds,
        )
        assertEquals(HighlightType.Explicit, r.target.type)
        assertEquals(400L, r.target.postId)
        assertEquals("explicit_post", r.reason)
    }

    @Test
    fun savedReadPosition_highlightsExactPost_noUpgradeToLast() {
        // The saved `lastViewedPostId` must be highlighted exactly as stored.
        // The previous resolver silently "upgraded" a penultimate saved post to
        // the last post on the page, which made the highlight land on the bottom
        // post and let the saved read position drift forward across opens. The
        // corrected contract highlights the saved post verbatim.
        val ids = listOf(143903679L, 143903696L, 143904035L)
        val r = HighlightResolver.resolve(
                topicId = 1103268L,
                unread = null,
                readPosition = ReadPosition(
                        topicId = 1103268L,
                        lastViewedPostId = 143903696L,
                        lastViewedPage = 1270,
                ),
                explicitPostId = null,
                pagePostIds = ids,
        )
        assertEquals(HighlightType.LastRead, r.target.type)
        assertEquals(143903696L, r.target.postId)
        assertEquals("last_read", r.reason)
    }

    @Test
    fun midPageReadPosition_isNotUpgraded() {
        val r = HighlightResolver.resolve(
                topicId = 1L,
                unread = null,
                readPosition = ReadPosition(1L, 200L, 1),
                explicitPostId = null,
                pagePostIds = pagePostIds,
        )
        assertEquals(200L, r.target.postId)
        assertEquals("last_read", r.reason)
    }

    @Test
    fun zeroOrNegativePostIdIsTreatedAsAbsent() {
        // AUDIT-M14 / refactor: the resolver now derives `unreadId` /
        // `readId` / `explicitId` as `Long?` via `takeIf { it > 0L }`, so a
        // present-but-zero value must not bypass the null-check and crash
        // downstream with `!!`. Each input shape is exercised independently.
        val r1 = HighlightResolver.resolve(
                topicId = 1L,
                unread = UnreadTarget(1L, firstUnreadPostId = 0L, unreadPage = 1, unreadUrl = null),
                readPosition = null,
                explicitPostId = null,
                pagePostIds = pagePostIds,
        )
        assertFalse(r1.hasUnreadInput)

        val r2 = HighlightResolver.resolve(
                topicId = 1L,
                unread = null,
                readPosition = ReadPosition(1L, lastViewedPostId = 0L, lastViewedPage = 1),
                explicitPostId = null,
                pagePostIds = pagePostIds,
        )
        assertFalse(r2.lastViewedInput)

        val r3 = HighlightResolver.resolve(
                topicId = 1L,
                unread = null,
                readPosition = null,
                explicitPostId = 0L,
                pagePostIds = pagePostIds,
        )
        assertFalse(r3.explicitInput)
    }
}
