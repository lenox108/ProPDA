package forpdateam.ru.forpda.presentation.theme

import forpdateam.ru.forpda.entity.remote.others.pagination.Pagination
import forpdateam.ru.forpda.entity.remote.theme.ThemePage
import forpdateam.ru.forpda.entity.remote.theme.ThemePost
import forpdateam.ru.forpda.model.repository.theme.ThemeReadPositionRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression coverage for the controller-driven highlight re-apply path.
 *
 * The recent refactor extracted the topic-post highlight policy into
 * dedicated classes ([HighlightResolver], [HighlightArmingPolicy],
 * [HighlightExplicitPostPolicy], [TopicHighlightApply], and the new
 * [HighlightOpenInputsPolicy]) and made [ThemeWebController.reapplyTopicHighlight]
 * a stub (TODO restore on next pass). The regression this guards against:
 *
 *  - in an already-read topic, the [HighlightType.LastRead] highlight was lost;
 *  - in a partially-read topic, the [HighlightType.FirstUnread] highlight was lost;
 *  - the [HighlightType.LastRead] fallback for a fully-read topic with no
 *    saved read position was lost.
 *
 * All three regressions funnel through [TopicHighlightApply.applyToPage] via
 * [HighlightOpenInputsPolicy.resolveOpenInputs], so a unit test on the policy
 * plus a smoke test on [TopicHighlightApply.applyToPage] is sufficient to
 * catch the bug. The test is also a tripwire: if a future refactor drops
 * the `applyHighlightForCurrentPage` wiring in the controller, the unit-test
 * suite for the resolver still guarantees the page-stamping half of the
 * contract, so a manual QA run catches the controller half.
 */
class HighlightOpenInputsPolicyTest {

    private fun makePage(
            topicId: Int = 1,
            vararg postIds: Long,
            hasUnreadTarget: Boolean = false,
            anchorPostId: String? = null,
            url: String? = null,
            pageNumber: Int = 1,
    ): ThemePage = ThemePage().apply {
        id = topicId
        pagination = Pagination().apply {
            current = pageNumber
            all = 1
            perPage = postIds.size.coerceAtLeast(1)
        }
        this.hasUnreadTarget = hasUnreadTarget
        this.anchorPostId = anchorPostId
        this.url = url
        posts.addAll(postIds.map { pid ->
            ThemePost().apply {
                this.id = pid.toInt()
                this.number = pid.toInt()
            }
        })
    }

    @Test
    fun unreadTarget_propagatesFirstUnreadInputs() {
        val page = makePage(
                postIds = longArrayOf(100L, 200L, 300L),
                hasUnreadTarget = true,
                anchorPostId = "200",
                url = "https://4pda.to/forum/index.php?showtopic=1&view=getnewpost",
                pageNumber = 1,
        )
        val inputs = HighlightOpenInputsPolicy.resolveOpenInputs(
                page = page,
                openedViaFindPost = false,
        )
        assertEquals(200L, inputs.firstUnreadPostId)
        assertEquals(1, inputs.unreadPage)
        assertEquals(
                "https://4pda.to/forum/index.php?showtopic=1&view=getnewpost",
                inputs.unreadUrl
        )
        assertNull("Scroll anchors from getnewpost do NOT count as explicit findpost", inputs.explicitPostId)
    }

    @Test
    fun findPost_open_suppliesExplicitPostId() {
        val page = makePage(
                postIds = longArrayOf(100L, 200L, 300L, 400L),
                hasUnreadTarget = false,
                anchorPostId = "400",
                url = "https://4pda.to/forum/index.php?showtopic=1&view=findpost&p=400",
        )
        val inputs = HighlightOpenInputsPolicy.resolveOpenInputs(
                page = page,
                openedViaFindPost = true,
        )
        assertNull("Findpost with no unread does not produce a firstUnreadPostId", inputs.firstUnreadPostId)
        assertEquals(400L, inputs.explicitPostId)
    }

    @Test
    fun normalOpenWithoutUnreadOrFindpost_producesNoOpenInputs() {
        val page = makePage(
                postIds = longArrayOf(100L, 200L, 300L),
                hasUnreadTarget = false,
                anchorPostId = null,
                url = "https://4pda.to/forum/index.php?showtopic=1",
        )
        val inputs = HighlightOpenInputsPolicy.resolveOpenInputs(
                page = page,
                openedViaFindPost = false,
        )
        assertNull(inputs.firstUnreadPostId)
        assertNull(inputs.unreadPage)
        assertNull(inputs.unreadUrl)
        assertNull(inputs.explicitPostId)
    }

    @Test
    fun unreadAnchorMissingOrZero_isTreatedAsAbsent() {
        // AUDIT-M14: a present-but-zero / non-numeric anchor must NOT bypass
        // the null-check (would otherwise crash the resolver downstream with `!!`).
        val zeroPage = makePage(
                postIds = longArrayOf(100L, 200L),
                hasUnreadTarget = true,
                anchorPostId = "0",
        )
        val zeroInputs = HighlightOpenInputsPolicy.resolveOpenInputs(zeroPage, false)
        assertNull(zeroInputs.firstUnreadPostId)

        val negativePage = makePage(
                postIds = longArrayOf(100L, 200L),
                hasUnreadTarget = true,
                anchorPostId = "-1",
        )
        val negativeInputs = HighlightOpenInputsPolicy.resolveOpenInputs(negativePage, false)
        assertNull(negativeInputs.firstUnreadPostId)

        val nonNumericPage = makePage(
                postIds = longArrayOf(100L, 200L),
                hasUnreadTarget = true,
                anchorPostId = "garbage",
        )
        val nonNumericInputs = HighlightOpenInputsPolicy.resolveOpenInputs(nonNumericPage, false)
        assertNull(nonNumericInputs.firstUnreadPostId)
    }

    @Test
    fun hasUnreadFalse_suppressesUnreadInputs() {
        val page = makePage(
                postIds = longArrayOf(100L, 200L, 300L),
                hasUnreadTarget = false,
                anchorPostId = "200", // anchor is present but hasUnreadTarget is false
                url = "https://4pda.to/forum/index.php?showtopic=1",
        )
        val inputs = HighlightOpenInputsPolicy.resolveOpenInputs(page, false)
        assertNull(inputs.firstUnreadPostId)
        assertNull(inputs.unreadPage)
        assertNull(inputs.unreadUrl)
    }

    /**
     * End-to-end smoke test: the inputs the policy produces, when fed to
     * [TopicHighlightApply.applyToPage], must stamp a [HighlightTarget] onto
     * the page. This is the regression this test exists to catch — the
     * controller stub left `page.highlightTarget` null, so the JS side
     * never received a `window.PPDA_applyHighlight(...)` call.
     */
    @Test
    fun applyToPage_withFirstUnreadInputs_stampsFirstUnreadTarget() {
        val page = makePage(
                postIds = longArrayOf(100L, 200L, 300L),
                hasUnreadTarget = true,
                anchorPostId = "200",
                url = "https://4pda.to/forum/index.php?showtopic=1&view=getnewpost",
        )
        val repo = ThemeReadPositionRepository()
        val inputs = HighlightOpenInputsPolicy.resolveOpenInputs(page, openedViaFindPost = false)
        val resolution = TopicHighlightApply.applyToPage(
                page = page,
                readPositionRepository = repo,
                firstUnreadPostId = inputs.firstUnreadPostId,
                unreadPage = inputs.unreadPage,
                unreadUrl = inputs.unreadUrl,
        )
        assertNotNull("page.highlightTarget must be stamped by the controller path", page.highlightTarget)
        assertEquals(HighlightType.FirstUnread, page.highlightTarget!!.type)
        assertEquals(200L, page.highlightTarget!!.postId)
        assertEquals("first_unread", resolution.reason)
        assertTrue(page.renderGenerationId > 0)
    }

    @Test
    fun applyToPage_withLastReadInRepo_stampsLastReadTarget() {
        val page = makePage(
                topicId = 1,
                postIds = longArrayOf(100L, 200L, 300L, 400L),
                hasUnreadTarget = false,
                anchorPostId = null,
                url = "https://4pda.to/forum/index.php?showtopic=1",
        )
        val repo = ThemeReadPositionRepository()
        repo.save(ReadPosition(topicId = 1L, lastViewedPostId = 300L, lastViewedPage = 1))
        val inputs = HighlightOpenInputsPolicy.resolveOpenInputs(page, openedViaFindPost = false)
        val resolution = TopicHighlightApply.applyToPage(
                page = page,
                readPositionRepository = repo,
                firstUnreadPostId = inputs.firstUnreadPostId,
                unreadPage = inputs.unreadPage,
                unreadUrl = inputs.unreadUrl,
                explicitPostId = inputs.explicitPostId,
        )
        assertNotNull(page.highlightTarget)
        assertEquals(HighlightType.LastRead, page.highlightTarget!!.type)
        assertEquals(300L, page.highlightTarget!!.postId)
        assertEquals("last_read", resolution.reason)
        assertTrue(page.renderGenerationId > 0)
    }

    @Test
    fun applyToPage_withNoInputs_stampsLastPostOnPageFallback() {
        // The fallback for a fully-read topic with no saved read position:
        // highlight the last post on the page (the user read to the bottom).
        val page = makePage(
                topicId = 1,
                postIds = longArrayOf(100L, 200L, 300L),
                hasUnreadTarget = false,
                anchorPostId = null,
                url = "https://4pda.to/forum/index.php?showtopic=1",
        )
        val repo = ThemeReadPositionRepository()
        val inputs = HighlightOpenInputsPolicy.resolveOpenInputs(page, openedViaFindPost = false)
        val resolution = TopicHighlightApply.applyToPage(
                page = page,
                readPositionRepository = repo,
                firstUnreadPostId = inputs.firstUnreadPostId,
                unreadPage = inputs.unreadPage,
                unreadUrl = inputs.unreadUrl,
                explicitPostId = inputs.explicitPostId,
        )
        assertNotNull(page.highlightTarget)
        assertEquals(HighlightType.LastRead, page.highlightTarget!!.type)
        assertEquals(300L, page.highlightTarget!!.postId)
        assertEquals("last_post_on_page_fallback", resolution.reason)
        assertTrue(page.renderGenerationId > 0)
    }

    /**
     * Device log 24_06-13-00-28_912 (topic 1103268): the topic was opened via
     * `view=getnewpost` BUT the parser resolved it as
     * `anchorSource=list_read_use_getnewpost` with `hasUnreadTarget=false`
     * (i.e. the user has read all posts; getnewpost is just the last-read
     * redirect on the favorites row). The page also has a non-null
     * `anchorPostId` (= the redirect entry id, e.g. 143987753).
     *
     * The highlight must still be stamped. As of Fix #B, the policy now
     * forwards `page.anchorPostId` as a `ReadPosition` override, so the
     * resolver hits priority 2 ("Last read on page") and stamps the actual
     * last-read post (= 143987753) instead of the previous regression where
     * the highlight silently slipped through to `last_post_on_page_fallback`
     * (= 143987870, the bottom of the last page) without a `lastViewedInput`
     * flag.
     */
    @Test
    fun applyToPage_allReadTopicFromGetnewpost_stampsLastReadAnchor() {
        val page = makePage(
                topicId = 1103268,
                postIds = longArrayOf(143987740L, 143987753L, 143987760L, 143987781L,
                        143987795L, 143987820L, 143987840L, 143987870L),
                hasUnreadTarget = false,
                anchorPostId = "143987753",
                url = "https://4pda.to/forum/index.php?showtopic=1103268&st=26280#entry143987753",
                pageNumber = 1315,
        )
        val repo = ThemeReadPositionRepository()
        val inputs = HighlightOpenInputsPolicy.resolveOpenInputs(page, openedViaFindPost = false)
        // hasUnreadTarget=false → policy must NOT surface firstUnreadPostId even
        // though the page URL is getnewpost-shaped and the parser set an
        // anchorPostId. This is the tripwire for the regression.
        assertNull(
                "hasUnreadTarget=false must strip firstUnreadPostId " +
                        "even when the page URL is getnewpost-shaped",
                inputs.firstUnreadPostId
        )
        assertNull(inputs.unreadPage)
        assertNull(inputs.unreadUrl)
        assertNull(
                "openedViaFindPost=false must strip explicitPostId " +
                        "even when anchorPostId is present",
                inputs.explicitPostId
        )
        // Fix #B: anchorPostId is forwarded as a read-position override so the
        // resolver hits priority 2 ("last_read") rather than the previous
        // priority 5 fallback.
        assertNotNull(
                "Fix #B: anchorPostId must produce a ReadPosition override",
                inputs.readPosition
        )
        assertEquals(143987753L, inputs.readPosition!!.lastViewedPostId)
        assertEquals(
                HighlightOpenInputsPolicy.LastReadSource.PAGE_ANCHOR,
                inputs.lastReadSource
        )
        val resolution = TopicHighlightApply.applyToPage(
                page = page,
                readPositionRepository = repo,
                firstUnreadPostId = inputs.firstUnreadPostId,
                unreadPage = inputs.unreadPage,
                unreadUrl = inputs.unreadUrl,
                explicitPostId = inputs.explicitPostId,
                readPositionOverride = inputs.readPosition,
        )
        assertNotNull(
                "Highlight must still be stamped even with all three open-inputs null " +
                        "(this is the user-reported regression on topic 1103268).",
                page.highlightTarget
        )
        assertEquals(HighlightType.LastRead, page.highlightTarget!!.type)
        assertEquals(
                "Fix #B: highlight must be the realigned last-read post, not the page bottom",
                143987753L,
                page.highlightTarget!!.postId
        )
        assertEquals("last_read", resolution.reason)
        assertTrue(page.renderGenerationId > 0)
    }

    @Test
    fun resolver_isDeterministic_viaPolicy() {
        // The whole point of the policy is determinism: same inputs -> same
        // highlight on the page across multiple resolves.
        val page = makePage(
                topicId = 1,
                postIds = longArrayOf(100L, 200L, 300L),
                hasUnreadTarget = true,
                anchorPostId = "200",
                url = "https://4pda.to/forum/index.php?showtopic=1&view=getnewpost",
        )
        val repo = ThemeReadPositionRepository()
        val inputs = HighlightOpenInputsPolicy.resolveOpenInputs(page, openedViaFindPost = false)
        val r1 = TopicHighlightApply.applyToPage(
                page = page,
                readPositionRepository = repo,
                firstUnreadPostId = inputs.firstUnreadPostId,
                unreadPage = inputs.unreadPage,
                unreadUrl = inputs.unreadUrl,
        )
        val gen1 = page.renderGenerationId
        val r2 = TopicHighlightApply.applyToPage(
                page = page,
                readPositionRepository = repo,
                firstUnreadPostId = inputs.firstUnreadPostId,
                unreadPage = inputs.unreadPage,
                unreadUrl = inputs.unreadUrl,
        )
        assertEquals(r1.reason, r2.reason)
        assertEquals(r1.target, r2.target)
        assertEquals("Refresh of the same page must keep the same renderGenerationId", gen1, page.renderGenerationId)
    }
}
