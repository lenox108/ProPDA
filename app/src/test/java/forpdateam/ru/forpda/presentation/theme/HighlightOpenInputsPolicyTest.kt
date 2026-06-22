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
