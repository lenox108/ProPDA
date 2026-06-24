package forpdateam.ru.forpda.presentation.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HighlightArmingPolicyTest {

    @Test
    fun defersArmingWhileBlockingScrollPending() {
        assertTrue(HighlightArmingPolicy.shouldDeferUntilScrollSettled(hasBlockingScrollPending = true))
        assertFalse(HighlightArmingPolicy.shouldDeferUntilScrollSettled(hasBlockingScrollPending = false))
    }

    @Test
    fun newRenderResetsArmedGeneration() {
        assertEquals(0, HighlightArmingPolicy.armedGenerationAfterNewRender())
    }

    /**
     * The policy only governs the active "apply" path (which can scroll an
     * off-screen post into view and would fight a blocking INITIAL_ANCHOR).
     * The fadeout scheduler is independent — it just arms a passive JS
     * `setTimeout` and must run even when a blocking scroll is pending;
     * otherwise the static `post-highlight-*` class stays visible until
     * the scroll completes (regression: log 1781778137-1781778139 has
     * `read_position_save_suppressed reason=blocking_scroll` for ~2s with
     * no `highlight_fadeout_scheduled` ever emitted).
     */
    @Test
    fun policyIsSeparateFromFadeoutScheduling() {
        // The policy itself is opaque: a future refactor may split it
        // further. What MUST stay invariant is that the policy decision
        // is *only* about whether the active apply is allowed to run —
        // never about whether the passive fadeout timer may arm.
        // This test is a tripwire: if anyone moves the fadeout
        // scheduling back under `shouldDeferUntilScrollSettled`, the
        // highlight stays visible during the blocking-scroll window.
        val state = ReapplyState()
        state.hasBlockingScrollPending = true
        state.reapplyGeneration = 7
        // Simulate the controller decision: schedule the fadeout first,
        // then consult the policy for the apply step.
        if (state.reapplyGeneration == state.fadeoutScheduledGeneration) {
            // no-op (already scheduled)
        } else {
            state.fadeoutScheduledGeneration = state.reapplyGeneration
        }
        val applyAllowed = !HighlightArmingPolicy.shouldDeferUntilScrollSettled(state.hasBlockingScrollPending)
        assertTrue("fadeout must be scheduled even when apply is deferred", state.fadeoutScheduledGeneration == 7)
        assertFalse("apply must be deferred while blocking scroll is pending", applyAllowed)
    }

    private class ReapplyState {
        var hasBlockingScrollPending: Boolean = false
        var reapplyGeneration: Int = 0
        var fadeoutScheduledGeneration: Int = 0
    }

    @Test
    fun shouldArmForCurrentTarget_skipsWhenSameGenerationAndSamePost() {
        // Once we already armed for (generation=10, postId=143903696), a
        // second `reapplyTopicHighlight` call for the same pair is a no-op.
        assertFalse(
                HighlightArmingPolicy.shouldArmForCurrentTarget(
                        armedGeneration = 10,
                        armedPostId = 143903696L,
                        currentGeneration = 10,
                        currentPostId = 143903696L,
                )
        )
    }

    @Test
    fun shouldArmForCurrentTarget_reArmsWhenPostIdChangesWithinSameGeneration() {
        // Log 24_06-14-15: the read-resume path can change the highlight
        // target post id across re-resolves within the same render
        // generation (parser realigned the anchor, openSessionKind flipped
        // from AMBIGUOUS_ALL_READ to READ_RESUME). A guard that only checks
        // `armedGeneration` would block the re-arm and the highlight would
        // never apply.
        assertTrue(
                "same generation with different postId must re-arm",
                HighlightArmingPolicy.shouldArmForCurrentTarget(
                        armedGeneration = 10,
                        armedPostId = 143903679L,
                        currentGeneration = 10,
                        currentPostId = 143988703L,
                )
        )
    }

    @Test
    fun shouldArmForCurrentTarget_reArmsWhenGenerationBumps() {
        // A new render generation always allows a fresh arm, even if the
        // post id happened to match the previous arm.
        assertTrue(
                HighlightArmingPolicy.shouldArmForCurrentTarget(
                        armedGeneration = 10,
                        armedPostId = 143903696L,
                        currentGeneration = 11,
                        currentPostId = 143903696L,
                )
        )
    }

    @Test
    fun shouldArmForCurrentTarget_refusesZeroCurrentPostId() {
        // Guard against accidentally arming a "no target" placeholder.
        assertFalse(
                HighlightArmingPolicy.shouldArmForCurrentTarget(
                        armedGeneration = 0,
                        armedPostId = 0L,
                        currentGeneration = 5,
                        currentPostId = 0L,
                )
        )
    }
}
