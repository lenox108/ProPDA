package forpdateam.ru.forpda.presentation.theme

import forpdateam.ru.forpda.entity.remote.theme.ThemePage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Cross-topic / in-tab BACK geometry-consistency regression (device log 25_06-22-18-38_133.log,
 * topic 1121483 st=1180; user opened bookmark at post 143876380, scrolled down to post 143860995,
 * tapped a link to topic 239158, then pressed BACK and landed VISUALLY on 143860995 instead of the
 * source post 143876380).
 *
 * ## What the log proves (quoted line numbers from 25_06-22-18-38_133.log)
 *  - L2316: JS pointerdown remembers the click-time visible post `143860995` at `pageY=11810 ratio=0.6364`.
 *  - L2318: `sourceAnchor kept authoritative authoritativePostId=143876380 ignoredSourcePost=143860995`
 *    — the anchor POST id is correctly kept at the authoritative open post.
 *  - L2319/L2320: **but a back snapshot is STILL captured** as `post=143876380 y=11810 ratio=0.6364`
 *    — the RIGHT post id paired with the WRONG (visible post's) pixel geometry.
 *  - L2668: `back cross_topic_return_in_place ... anchor=143876380 y=11810` — BACK restores the
 *    in-tab top entry IN PLACE using `top.scrollY=11810`, so although the anchor is 143876380 the
 *    page scrolls to y=11810 (143860995's screen location). The user sees the neighbor.
 *
 * The authoritative post 143876380's own self-consistent geometry is `y=3807 ratio=0.5626`
 * (L420/L691: `finalScrolledPostId=entry143876380 ... savedScrollY=...`, and the clean back restores
 * at L2008/L7924: `back_restore_applied anchorPostId=143876380 scrollY=3807`). The fix must keep
 * THAT geometry on the snapshot/history entry, never the visible post's y=11810.
 *
 * ## Fail-before / pass-after
 *  - BEFORE the fix: [ThemeAuthoritativeAnchorPolicy.shouldRejectAuthoritativeMismatchedBackSnapshot]
 *    did not exist and the capture sites unconditionally wrote the visible geometry, so a snapshot
 *    with post=143876380 + y=11810 reached the back restore — the wrong-post symptom. The
 *    [geometryRejectGuard_keepsAuthoritativePostGeometry] assertions below pin the corrected decision
 *    (reject the mismatched-geometry capture) and would fail without the new policy method.
 *  - AFTER the fix: the guard rejects the mismatched geometry, so the durable snapshot / history top
 *    keeps the authoritative post's own y=3807 and BACK lands on 143876380.
 */
class CrossTopicBackGeometryConsistencyTest {

    private val topicId = 1121483
    private val st = 1180
    private val authoritativePost = "143876380"
    private val visiblePost = "143860995"
    private val authoritativeY = 3807
    private val authoritativeRatio = 0.5626
    private val visibleY = 11810
    private val visibleRatio = 0.6364

    /**
     * The policy-level decision the capture sites consult. The visible post the user tapped the link
     * from (143860995) DIFFERS from the page's authoritative anchor (143876380), so the geometry the
     * page currently holds (y=11810) describes 143860995, not 143876380. The guard must reject it.
     */
    @Test
    fun geometryRejectGuard_keepsAuthoritativePostGeometry() {
        val reject = ThemeAuthoritativeAnchorPolicy.shouldRejectAuthoritativeMismatchedBackSnapshot(
                authoritativeAnchorPostId = authoritativePost,
                candidateVisiblePostId = visiblePost,
        )
        assertTrue(
                "a back snapshot for an authoritative-anchored page must reject the VISIBLE post's " +
                        "geometry when the visible post differs from the authoritative anchor",
                reject,
        )
    }

    /**
     * End-to-end mirror of the device-log sequence using [ThemeHistoryController] — the consumer of
     * the cross-topic restore-in-place BACK. Demonstrates that when the capture site honors the guard
     * (skips the mismatched-geometry overwrite), the durable back snapshot keeps the authoritative
     * post's own y=3807, so the BACK restore lands on 143876380, not 143860995.
     */
    @Test
    fun crossTopicBack_restoresAuthoritativePostGeometry_notVisiblePostLocation() {
        val controller = ThemeHistoryController()

        val page = ThemePage().apply {
            id = topicId
            pagination.current = 60 // st = (60 - 1) * 20 = 1180
            pagination.all = 64
            url = "https://4pda.to/forum/index.php?showtopic=$topicId&st=$st#entry$authoritativePost"
            anchorPostId = authoritativePost
            authoritativeAnchorPostId = authoritativePost
            scrollY = authoritativeY
            scrollRatio = authoritativeRatio
        }
        controller.saveToHistory(page)

        // crossTopicOpen captured the durable back snapshot at the authoritative post's own geometry
        // (the render that was actually AT 143876380): post=143876380 y=3807.
        controller.captureBackSnapshot(
                TopicBackSnapshot.fromPage(
                        topicId = topicId,
                        pageSt = st,
                        visiblePostId = authoritativePost,
                        scrollOffset = authoritativeY,
                        scrollRatio = authoritativeRatio,
                        wasNearBottom = false,
                        status = TopicBackSnapshotStatus.CAPTURED,
                ),
        )

        // The user tapped the 239158 link while VISUALLY at 143860995 (y=11810). The capture site
        // consults the guard: visible post 143860995 != authoritative 143876380 → REJECT the
        // mismatched geometry. The buggy code would have called captureBackSnapshot(post=143876380,
        // y=11810) here; the fix skips it.
        val reject = ThemeAuthoritativeAnchorPolicy.shouldRejectAuthoritativeMismatchedBackSnapshot(
                authoritativeAnchorPostId = page.authoritativeAnchorPostId,
                candidateVisiblePostId = visiblePost,
        )
        assertTrue("mismatched-geometry back snapshot must be rejected at the cross-topic open", reject)
        if (!reject) {
            // (Unreachable with the fix; pins the buggy overwrite so a regression is caught.)
            controller.captureBackSnapshot(
                    TopicBackSnapshot.fromPage(
                            topicId = topicId,
                            pageSt = st,
                            visiblePostId = authoritativePost,
                            scrollOffset = visibleY,
                            scrollRatio = visibleRatio,
                            wasNearBottom = false,
                            status = TopicBackSnapshotStatus.CAPTURED,
                    ),
            )
        }

        // BACK restore reads the durable snapshot: it must still carry the authoritative post's own
        // geometry (y=3807), NOT the visible post's y=11810.
        val snapshot = controller.peekBackSnapshot(topicId, st)
        assertNotNull(snapshot)
        assertEquals(
                "BACK must restore the authoritative source post 143876380, not the visible neighbor",
                authoritativePost,
                snapshot!!.visiblePostId,
        )
        assertEquals(
                "BACK must scroll to the authoritative post's own y=3807, not the visible post's y=11810",
                authoritativeY,
                snapshot.scrollOffset,
        )
    }

    /**
     * Negative control: the NORMAL cross-topic case where the user tapped the link from the SAME post
     * the page was opened at (143876380 == 143876380). The guard must NOT reject — the geometry is
     * genuine and the snapshot is captured unchanged, preserving the working restore-in-place behavior.
     */
    @Test
    fun normalCrossTopic_sameSourcePost_capturesGeometryUnchanged() {
        val reject = ThemeAuthoritativeAnchorPolicy.shouldRejectAuthoritativeMismatchedBackSnapshot(
                authoritativeAnchorPostId = authoritativePost,
                candidateVisiblePostId = authoritativePost,
        )
        assertEquals(
                "tapping the cross-topic link from the same authoritative post must NOT reject geometry",
                false,
                reject,
        )
    }
}
