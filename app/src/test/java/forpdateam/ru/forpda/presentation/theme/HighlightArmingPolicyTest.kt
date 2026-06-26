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

    // --- STEP 2 — sticky pending ScrollIntent for an explicit-anchor open ------------------------

    @Test
    fun `pending explicit anchor is unsettled until blocking scroll reports success`() {
        // STEP 2: a sticky explicit-anchor intent survives generation bumps and is only cleared
        // by the event-based settle signal (blocking INITIAL_ANCHOR success). Until that fires,
        // the caller must bypass the per-render completion latch and re-arm.
        assertTrue(
                "unsettled when pending and not settled",
                HighlightArmingPolicy.isPendingExplicitAnchorUnsettled(
                        pendingPostId = 143994024L,
                        anchorSettled = false,
                )
        )
        assertFalse(
                "no pending intent -> never unsettled",
                HighlightArmingPolicy.isPendingExplicitAnchorUnsettled(
                        pendingPostId = 0L,
                        anchorSettled = false,
                )
        )
        assertFalse(
                "settled -> not unsettled",
                HighlightArmingPolicy.isPendingExplicitAnchorUnsettled(
                        pendingPostId = 143994024L,
                        anchorSettled = true,
                )
        )
    }

    @Test
    fun `pending explicit anchor clears once blocking scroll settles`() {
        // STEP 2 clear criterion: the event-based settle (INITIAL_ANCHOR success) clears the
        // sticky intent so the highlight can latch normally again. Not a timer.
        assertTrue(
                HighlightArmingPolicy.shouldClearPendingExplicitAnchor(
                        pendingPostId = 143994024L,
                        anchorSettled = true,
                )
        )
        assertFalse(
                "must NOT clear before settle (survives generation bumps)",
                HighlightArmingPolicy.shouldClearPendingExplicitAnchor(
                        pendingPostId = 143994024L,
                        anchorSettled = false,
                )
        )
        assertFalse(
                "no pending intent -> nothing to clear",
                HighlightArmingPolicy.shouldClearPendingExplicitAnchor(
                        pendingPostId = 0L,
                        anchorSettled = true,
                )
        )
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

    /**
     * H-03 (device log 24_06-20-37, topics 1121483 / 1103268 / 1115025):
     *
     *   highlight_arm_skipped reason=already_armed renderGenerationId=1 postId=143992836
     *       armedGeneration=1 fadeoutScheduledGeneration=1
     *
     * appeared on the VERY FIRST reapply of a fresh render, with ZERO
     * `js_highlight_applied` / `native_highlight_bound` anywhere in the session
     * — the apply never fired. The dispatch-anchored decision MUST dispatch the
     * apply when nothing has been dispatched yet for this (generation, postId),
     * regardless of any stale `armed*` bookkeeping.
     */
    @Test
    fun shouldDispatchApply_dispatchesOnFirstResolve_whenNothingDispatchedYet() {
        assertTrue(
                "first resolve (no prior dispatch) must dispatch apply",
                HighlightArmingPolicy.shouldDispatchApplyForCurrentTarget(
                        dispatchedGeneration = 0,
                        dispatchedPostId = 0L,
                        currentGeneration = 1,
                        currentPostId = 143992836L,
                )
        )
    }

    @Test
    fun shouldDispatchApply_skipsOnlyAfterRealDispatchOfSameTarget() {
        assertFalse(
                "a genuine second reapply of the already-dispatched (gen, post) is a no-op",
                HighlightArmingPolicy.shouldDispatchApplyForCurrentTarget(
                        dispatchedGeneration = 1,
                        dispatchedPostId = 143992836L,
                        currentGeneration = 1,
                        currentPostId = 143992836L,
                )
        )
    }

    @Test
    fun shouldDispatchApply_redispatchesWhenGenerationOrPostChanges() {
        assertTrue(
                HighlightArmingPolicy.shouldDispatchApplyForCurrentTarget(
                        dispatchedGeneration = 1,
                        dispatchedPostId = 143992836L,
                        currentGeneration = 2,
                        currentPostId = 143992836L,
                )
        )
        assertTrue(
                HighlightArmingPolicy.shouldDispatchApplyForCurrentTarget(
                        dispatchedGeneration = 1,
                        dispatchedPostId = 143992836L,
                        currentGeneration = 1,
                        currentPostId = 143999999L,
                )
        )
    }

    @Test
    fun shouldDispatchApply_refusesZeroCurrentPostId() {
        assertFalse(
                HighlightArmingPolicy.shouldDispatchApplyForCurrentTarget(
                        dispatchedGeneration = 0,
                        dispatchedPostId = 0L,
                        currentGeneration = 5,
                        currentPostId = 0L,
                )
        )
    }

    /**
     * Double-light-up fix (device log 24_06-23-12, post 143876380 gen=3):
     * the ring lit, faded, then RE-LIT a second time because a later
     * reveal/scroll-settled re-arm re-dispatched the SAME (generation, postId)
     * after the dispatch guard had been reset. The completion latch is the
     * authoritative "this pair already ran its single light+fade cycle" record;
     * once set it must suppress ALL later re-arms for that exact pair.
     */
    @Test
    fun isHighlightCycleAlreadyCompleted_suppressesSameGenerationAndPostAfterFadeout() {
        assertTrue(
                "a (generation, postId) that already completed its light+fade cycle must not re-dispatch",
                HighlightArmingPolicy.isHighlightCycleAlreadyCompleted(
                        completedGeneration = 3,
                        completedPostId = 143876380L,
                        currentGeneration = 3,
                        currentPostId = 143876380L,
                )
        )
    }

    @Test
    fun isHighlightCycleAlreadyCompleted_allowsNewGenerationForSamePost() {
        // A genuinely NEW render generation (e.g. page change / refresh) for the
        // same post must still light up once — the latch is keyed on the pair.
        assertFalse(
                HighlightArmingPolicy.isHighlightCycleAlreadyCompleted(
                        completedGeneration = 3,
                        completedPostId = 143876380L,
                        currentGeneration = 4,
                        currentPostId = 143876380L,
                )
        )
    }

    @Test
    fun isHighlightCycleAlreadyCompleted_allowsDifferentPostInSameGeneration() {
        // A different highlight target within the same generation must still
        // light up once and supersede the previous one.
        assertFalse(
                HighlightArmingPolicy.isHighlightCycleAlreadyCompleted(
                        completedGeneration = 3,
                        completedPostId = 143876380L,
                        currentGeneration = 3,
                        currentPostId = 143999999L,
                )
        )
    }

    @Test
    fun isHighlightCycleAlreadyCompleted_inertWhenNothingCompletedYet() {
        // Fresh state: no cycle has completed, so the very first dispatch is
        // never suppressed.
        assertFalse(
                HighlightArmingPolicy.isHighlightCycleAlreadyCompleted(
                        completedGeneration = 0,
                        completedPostId = 0L,
                        currentGeneration = 3,
                        currentPostId = 143876380L,
                )
        )
    }

    @Test
    fun isHighlightCycleAlreadyCompleted_refusesZeroCurrentPostId() {
        assertFalse(
                HighlightArmingPolicy.isHighlightCycleAlreadyCompleted(
                        completedGeneration = 3,
                        completedPostId = 143876380L,
                        currentGeneration = 3,
                        currentPostId = 0L,
                )
        )
    }
}
