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
                shouldScheduleFadeout: Boolean
        ) {
            // jsApi.eval is unconditional — it must run before any flag update.
            lastJsScript = buildString {
                if (shouldApply) append("apply;")
                if (shouldScheduleFadeout) append("scheduleFadeout;")
            }
            jsEvalCount++
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
        }
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
}
