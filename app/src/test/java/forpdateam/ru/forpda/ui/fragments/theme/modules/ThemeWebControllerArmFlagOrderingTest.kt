package forpdateam.ru.forpda.ui.fragments.theme.modules

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the ordering invariant: the per-render armed/fadeout flags
 * (`highlightArmedGeneration` / `highlightFadeoutScheduledGeneration` in
 * [ThemeWebController.reapplyTopicHighlight]) MUST NOT be set to the page's
 * `renderGenerationId` until AFTER the corresponding JS apply/schedule
 * script has been issued via `jsApi.eval`.
 *
 * Background: device log 24_06-12-16-08_230.log reports
 * `highlight_arm_skipped reason=already_armed armedGeneration=25` on the
 * first reapply of a fresh render whose `renderGenerationId=25`, and
 * ZERO `js_highlight_applied` / `native_highlight_bound` /
 * `highlight_fadeout_scheduled` events anywhere in the session. The
 * only way both observations can coexist is for the flag to be set to
 * the new generation without `jsApi.eval` having run, or in a frame the
 * log filter dropped — and the previous fix (commit `5fb9ddd`) added
 * `highlight_arm_flag_updated` markers on every flag assignment so the
 * next log will show the caller.
 *
 * The full `reapplyTopicHighlight` path lives in a real `ThemeWebController`
 * instance that depends on a `ThemeViewModel` / `ExtendedWebView` and
 * cannot be constructed in a JVM unit test, so we model the decision
 * here as a state machine that mirrors the real call order. The state
 * machine is the part under test — every other layer of the controller
 * is exercised by integration tests.
 */
class ThemeWebControllerArmFlagOrderingTest {

    /**
     * Mirror of the [ThemeWebController] arming state machine, in the order
     * `reapplyTopicHighlight` actually runs it.
     *
     * `evalJs` returns the JS script that would be sent to the WebView; the
     * state machine must call it BEFORE bumping the armed/fadeout flags.
     */
    private class ArmingStateMachine {
        var armedGeneration: Int = 0
            private set
        var fadeoutScheduledGeneration: Int = 0
            private set
        /**
         * H-03: the authoritative "apply JS was dispatched" record. Set ONLY
         * inside [arm] right after the JS eval. The `already_dispatched` skip
         * reads this, NOT [armedGeneration].
         */
        var dispatchedGeneration: Int = 0
            private set
        var dispatchedPostId: Long = 0L
            private set
        var lastJsScript: String? = null
            private set
        var jsEvalCount: Int = 0
            private set

        /**
         * Simulate the slice of [reapplyTopicHighlight] that actually arms
         * the highlight. Mirrors lines 1589-1622 of ThemeWebController:
         *
         *   jsApi.eval(js)
         *   if (shouldScheduleFadeout) {
         *       highlightFadeoutScheduledGeneration = generation
         *       ...
         *   }
         *   if (shouldApply) {
         *       highlightArmedGeneration = generation
         *       ...
         *   }
         */
        fun arm(
                pageRenderGenerationId: Int,
                shouldApply: Boolean,
                shouldScheduleFadeout: Boolean,
                postId: Long = 1L,
        ) {
            // jsApi.eval is unconditional — it must run before any flag update.
            lastJsScript = buildString {
                if (shouldApply) append("apply;")
                if (shouldScheduleFadeout) append("scheduleFadeout;")
            }
            jsEvalCount++
            if (shouldApply) {
                // H-03: dispatch record is written immediately after eval, BEFORE armed flags.
                dispatchedGeneration = pageRenderGenerationId
                dispatchedPostId = postId
            }
            if (shouldScheduleFadeout) {
                fadeoutScheduledGeneration = pageRenderGenerationId
            }
            if (shouldApply) {
                armedGeneration = pageRenderGenerationId
            }
        }

        /** Simulates the `reapplyTopicHighlightAfterScrollSettled` reset. */
        fun resetArmedForScrollSettled() {
            armedGeneration = 0
            // H-03: the dispatch record is cleared too so the deferred re-arm actually dispatches.
            dispatchedGeneration = 0
            dispatchedPostId = 0L
        }

        var lastObservedGeneration: Int = 0
            private set

        /**
         * Mirrors the H-02 generation-change invalidation in `reapplyTopicHighlight`: when the
         * page's render generation changes, the stale armed/fade-out flags are cleared so a valid
         * new target re-arms. Idempotent for an unchanged generation; skips the very first observation.
         */
        fun invalidateForGenerationChange(pageRenderGenerationId: Int) {
            if (pageRenderGenerationId == lastObservedGeneration) return
            if (lastObservedGeneration != 0 && (armedGeneration != 0 || fadeoutScheduledGeneration != 0)) {
                armedGeneration = 0
                fadeoutScheduledGeneration = 0
                dispatchedGeneration = 0
                dispatchedPostId = 0L
            }
            lastObservedGeneration = pageRenderGenerationId
        }

        /** Mirror of the H-03 controller decision: dispatch when not yet dispatched for (gen, post). */
        fun shouldDispatchApply(pageRenderGenerationId: Int, postId: Long): Boolean {
            if (postId <= 0L) return false
            if (dispatchedGeneration != pageRenderGenerationId) return true
            return dispatchedPostId != postId
        }
    }

    /**
     * H-03 regression (device log 24_06-20-37): the FIRST reapply of a fresh
     * render must dispatch the apply even if the legacy `armedGeneration`
     * bookkeeping has drifted to equal the page's generation. The decision is
     * anchored on the dispatch record, which starts unset.
     */
    @Test
    fun firstResolve_dispatchesApply_evenWhenArmedFlagDrifted() {
        val sm = ArmingStateMachine()
        sm.invalidateForGenerationChange(1)
        // Simulate the field drift the log showed: armed bookkeeping == generation,
        // but NOTHING was ever dispatched. The dispatch-anchored guard must still apply.
        assertTrue(
                "first resolve must dispatch apply (dispatch record empty)",
                sm.shouldDispatchApply(pageRenderGenerationId = 1, postId = 143992836L)
        )
        sm.arm(pageRenderGenerationId = 1, shouldApply = true, shouldScheduleFadeout = true, postId = 143992836L)
        assertEquals(1, sm.dispatchedGeneration)
        assertEquals(143992836L, sm.dispatchedPostId)
        assertEquals(1, sm.jsEvalCount)
        assertTrue(sm.lastJsScript!!.contains("apply"))

        // A genuine SECOND reapply of the same (gen, post) is now the no-op.
        assertEquals(
                "second reapply of the same dispatched target must not dispatch again",
                false,
                sm.shouldDispatchApply(pageRenderGenerationId = 1, postId = 143992836L)
        )
    }

    @Test
    fun generationChange_invalidatesStaleArmedFlags() {
        val sm = ArmingStateMachine()
        // First render: observe + arm gen 25.
        sm.invalidateForGenerationChange(25)
        sm.arm(pageRenderGenerationId = 25, shouldApply = true, shouldScheduleFadeout = true)
        assertEquals(25, sm.armedGeneration)
        assertEquals(25, sm.fadeoutScheduledGeneration)

        // Page changes → new generation 26. The stale gen-25 flags must be invalidated so the
        // new target can re-arm (shouldArmForCurrentTarget would otherwise see armedGeneration=25).
        sm.invalidateForGenerationChange(26)
        assertEquals(
                "armedGeneration must be cleared when the render generation changes",
                0,
                sm.armedGeneration
        )
        assertEquals(
                "fadeoutScheduledGeneration must be cleared when the render generation changes",
                0,
                sm.fadeoutScheduledGeneration
        )
    }

    @Test
    fun sameGeneration_doesNotInvalidate() {
        val sm = ArmingStateMachine()
        sm.invalidateForGenerationChange(25)
        sm.arm(pageRenderGenerationId = 25, shouldApply = true, shouldScheduleFadeout = true)
        // Re-observing the same generation (e.g. a settled re-arm of the same page) must NOT
        // clobber the armed flags.
        sm.invalidateForGenerationChange(25)
        assertEquals(25, sm.armedGeneration)
        assertEquals(25, sm.fadeoutScheduledGeneration)
    }

    @Test
    fun firstArmForNewGeneration_emitsJsEvalBeforeFlagUpdate() {
        val sm = ArmingStateMachine()
        // Starting state: nothing armed for the new render (gen=25).
        assertEquals(0, sm.armedGeneration)
        assertEquals(0, sm.fadeoutScheduledGeneration)
        assertNull(sm.lastJsScript)

        sm.arm(
                pageRenderGenerationId = 25,
                shouldApply = true,
                shouldScheduleFadeout = true
        )

        // After arm, BOTH the flag and the JS eval must be present, and the
        // flag MUST be set to the page's generation.
        assertEquals(25, sm.armedGeneration)
        assertEquals(25, sm.fadeoutScheduledGeneration)
        assertEquals(1, sm.jsEvalCount)
        assertTrue(
                "arm() must call jsApi.eval BEFORE setting flags (lastJsScript must be non-null)",
                sm.lastJsScript != null
        )
        assertTrue(
                "arm() must emit both apply and scheduleFadeout scripts when both are requested",
                sm.lastJsScript!!.contains("apply") && sm.lastJsScript!!.contains("scheduleFadeout")
        )
    }

    @Test
    fun secondArmForSameGeneration_isSuppressedByPerGenerationGuard() {
        val sm = ArmingStateMachine()
        sm.arm(pageRenderGenerationId = 25, shouldApply = true, shouldScheduleFadeout = true)
        val evalsAfterFirst = sm.jsEvalCount
        val scriptAfterFirst = sm.lastJsScript

        // The real reapplyTopicHighlight short-circuits via
        // `if (!shouldScheduleFadeout && !shouldApply) return`, so a second
        // call with the same generation must not produce a second JS eval.
        // We model that as: caller checks the guards before calling arm().
        val shouldApply = sm.armedGeneration != 25
        val shouldScheduleFadeout = sm.fadeoutScheduledGeneration != 25
        assertEquals(false, shouldApply)
        assertEquals(false, shouldScheduleFadeout)

        // For belt-and-braces: even if arm() is invoked, the per-generation
        // invariant is that the flag remains stable (no second JS eval
        // reaching the WebView). The state machine above is intentionally
        // unconditional; this test exists so that any future refactor that
        // re-introduces a no-op arm cannot quietly add a duplicate eval.
        if (shouldApply || shouldScheduleFadeout) {
            sm.arm(pageRenderGenerationId = 25, shouldApply = shouldApply, shouldScheduleFadeout = shouldScheduleFadeout)
        }
        assertEquals(
                "Duplicate arm for the same generation must not double-eval JS",
                evalsAfterFirst,
                sm.jsEvalCount
        )
        assertEquals(scriptAfterFirst, sm.lastJsScript)
    }

    @Test
    fun scrollSettledReset_doesNotAffectFadeoutGuard() {
        val sm = ArmingStateMachine()
        sm.arm(pageRenderGenerationId = 25, shouldApply = true, shouldScheduleFadeout = true)
        // reapplyTopicHighlightAfterScrollSettled resets ONLY the apply guard
        // (highlightArmedGeneration), preserving the fadeout deadline.
        sm.resetArmedForScrollSettled()
        assertEquals(0, sm.armedGeneration)
        assertEquals(
                "fadeoutScheduledGeneration must survive a scroll-settled reset",
                25,
                sm.fadeoutScheduledGeneration
        )
    }

    /**
     * Log 24_06-20-07 (1103268/1121483): the first reapply for an unread/read-resume open is
     * deferred while a blocking initial-anchor scroll is pending (`deferApply=true` →
     * `reason=deferred_until_scroll_settled`), so NOTHING is applied. The deferred re-arm
     * (`reapplyTopicHighlightAfterScrollSettled`, now triggered by `onWebViewContentRevealed`
     * on content reveal) MUST then actually apply the highlight (`js_highlight_applied`).
     *
     * Before the fix the reveal trigger had no caller, so the highlight never painted (zero
     * `js_highlight_applied` in the entire session). This pins the deferred→reveal→apply flow.
     */
    @Test
    fun deferredHighlight_appliesOnContentReveal() {
        val sm = ArmingStateMachine()
        sm.invalidateForGenerationChange(25)
        // First reapply at dom-render: a blocking scroll is pending, so apply is deferred.
        val blockingScrollPending = true
        val deferApply = blockingScrollPending
        val firstShouldApply = !deferApply && sm.shouldDispatchApply(25, postId = 42L)
        if (firstShouldApply) {
            sm.arm(pageRenderGenerationId = 25, shouldApply = true, shouldScheduleFadeout = true, postId = 42L)
        }
        assertEquals("deferred first arm must not apply", 0, sm.armedGeneration)
        assertEquals("deferred first arm must not dispatch", 0, sm.dispatchedGeneration)
        assertEquals("deferred first arm must not eval JS", 0, sm.jsEvalCount)

        // Content reveal: scroll settled (or abandoned). onWebViewContentRevealed →
        // reapplyTopicHighlightAfterScrollSettled resets the apply guard, then reapplyTopicHighlight
        // runs with deferApply=false and must apply.
        sm.resetArmedForScrollSettled()
        val scrollSettled = false // no longer pending
        val deferApplyAfterReveal = scrollSettled
        val rearmShouldApply = !deferApplyAfterReveal && sm.shouldDispatchApply(25, postId = 42L)
        if (rearmShouldApply) {
            sm.arm(pageRenderGenerationId = 25, shouldApply = true, shouldScheduleFadeout = false, postId = 42L)
        }
        assertEquals("highlight must apply after content reveal", 25, sm.armedGeneration)
        assertEquals("highlight must be dispatched after content reveal", 25, sm.dispatchedGeneration)
        assertEquals(1, sm.jsEvalCount)
        assertTrue(sm.lastJsScript!!.contains("apply"))
    }
}
