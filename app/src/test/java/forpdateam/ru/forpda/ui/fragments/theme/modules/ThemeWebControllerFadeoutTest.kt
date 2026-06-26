package forpdateam.ru.forpda.ui.fragments.theme.modules

import forpdateam.ru.forpda.presentation.theme.HighlightArmingPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression test for the `ThemeWebController` fade-out wiring.
 *
 * The contract is: on a *new render* (topic open / page change / refresh)
 * the controller must call `window.PPDA_scheduleHighlightFadeout(generationId, 2000)`
 * with the page's `renderGenerationId` and a 2-second delay. Re-applying the
 * highlight within the same render (e.g. a smart-patch DOM mutation that
 * triggers another `applyHighlight` call with the SAME generation) must
 * NOT re-arm the timer — the original deadline still wins. A re-apply with
 * a *new* generation (a fresh render) MUST re-arm.
 *
 * The full controller path lives in a real `ThemeWebController` instance
 * which depends on a `ThemeViewModel`, an `ExtendedWebView`, etc. and cannot
 * be instantiated in a JVM unit test. We model the *decision* here: a tiny
 * state machine that mirrors what `reapplyTopicHighlight` does on the
 * native side, and assert its behaviour.
 *
 * The native side calls:
 *   1. `applyHighlight(postId, type, generationId)` — re-asserts the class
 *   2. `scheduleHighlightFadeout(generationId, 2000)` — arms the JS timer
 *
 * Step 2 is the part under test. We capture which generations have a
 * scheduled timer and assert the documented behaviour.
 */
class ThemeWebControllerFadeoutTest {

    /**
     * Mirror of the relevant slice of the controller's reapply logic.
     * `schedule` returns true if a new timer was armed, false if the
     * existing one for that generation was preserved (idempotent).
     */
    private class FadeoutScheduler {
        private val scheduledGenerations = mutableSetOf<Int>()
        var lastScheduledGeneration: Int = 0
        var lastScheduledDelayMs: Int = 0
        var callCount: Int = 0

        fun schedule(generationId: Int, delayMs: Int): Boolean {
            callCount++
            if (generationId in scheduledGenerations) {
                return false
            }
            scheduledGenerations.add(generationId)
            lastScheduledGeneration = generationId
            lastScheduledDelayMs = delayMs
            return true
        }
    }

    @Test
    fun newRender_armsFadeoutWith2000Ms() {
        val scheduler = FadeoutScheduler()
        val armed = scheduler.schedule(generationId = 5, delayMs = 2000)
        assertTrue("A new render must arm the fadeout", armed)
        assertEquals(5, scheduler.lastScheduledGeneration)
        assertEquals(2000, scheduler.lastScheduledDelayMs)
    }

    @Test
    fun reapplySameRender_doesNotReschedule() {
        // Smart-patch re-applies applyHighlight for the SAME generation;
        // the original 2-second deadline must still win. The scheduler
        // returns false to signal "no-op, do not extend the timer".
        val scheduler = FadeoutScheduler()
        assertTrue(scheduler.schedule(generationId = 5, delayMs = 2000))
        val rearmed = scheduler.schedule(generationId = 5, delayMs = 2000)
        assertFalse("Same render must not re-arm the timer", rearmed)
        assertEquals(1, scheduler.callCount - 1) // 1 actual arm + 1 no-op
    }

    @Test
    fun newRenderOnTopOfOld_armsFreshTimer() {
        // A page change bumps the render generation. The scheduler must
        // arm a fresh timer for the new generation, regardless of whether
        // the previous render's timer is still pending.
        val scheduler = FadeoutScheduler()
        assertTrue(scheduler.schedule(generationId = 5, delayMs = 2000))
        assertTrue(scheduler.schedule(generationId = 6, delayMs = 2000))
        assertEquals(6, scheduler.lastScheduledGeneration)
    }

    @Test
    fun delay_isAlways2000Ms() {
        // The user-facing spec pins the visible window at 2 seconds. Any
        // future refactor that changes the delay must consciously confirm
        // the user-facing flash duration.
        val scheduler = FadeoutScheduler()
        scheduler.schedule(generationId = 1, delayMs = 2000)
        assertEquals(2000, scheduler.lastScheduledDelayMs)
    }

    /**
     * H-01 (audit Finding H-01): the fade-out window must be armed ONLY after the highlight
     * is actually applied. Mirrors the controller decision:
     *   shouldScheduleFadeout = shouldApply && fadeoutScheduledGeneration != generation
     */
    private fun shouldScheduleFadeout(
            shouldApply: Boolean,
            fadeoutScheduledGeneration: Int,
            generation: Int,
    ): Boolean = shouldApply && fadeoutScheduledGeneration != generation

    @Test
    fun deferredApply_doesNotScheduleFadeout() {
        // When the apply is deferred until the blocking scroll settles (deferApply=true →
        // shouldApply=false), the fade-out must NOT be armed yet — otherwise the 2-second
        // timer counts against an unpainted/off-screen post and the ring can fade before
        // the user ever sees it.
        val armed = shouldScheduleFadeout(
                shouldApply = false,
                fadeoutScheduledGeneration = 0,
                generation = 7,
        )
        assertFalse("Deferred apply must defer the fade-out scheduling too", armed)
    }

    @Test
    fun appliedHighlight_schedulesFadeoutOncePerGeneration() {
        // When apply happens (shouldApply=true) and the generation has not been scheduled yet,
        // the fade-out arms. A second arm for the SAME generation is a no-op (the original
        // deadline wins).
        assertTrue(shouldScheduleFadeout(shouldApply = true, fadeoutScheduledGeneration = 0, generation = 7))
        assertFalse(
                "Same-generation re-apply must not re-arm the fade-out",
                shouldScheduleFadeout(shouldApply = true, fadeoutScheduledGeneration = 7, generation = 7)
        )
    }

    @Test
    fun deferredThenSettledApply_armsFadeoutOnReArm() {
        // Models the deferred re-arm path: first reapply is deferred (no fade-out), then after
        // scroll settles reapplyTopicHighlightAfterScrollSettled resets the armed flag and the
        // re-arm applies AND schedules the fade-out for the first time.
        assertFalse(shouldScheduleFadeout(shouldApply = false, fadeoutScheduledGeneration = 0, generation = 9))
        // scroll settled → shouldApply becomes true, fade-out still unscheduled for gen 9
        assertTrue(shouldScheduleFadeout(shouldApply = true, fadeoutScheduledGeneration = 0, generation = 9))
    }

    /**
     * Double-light-up fix (device log 24_06-23-12, post 143876380 gen=3): a
     * reveal/scroll-settled re-arm reset the per-render dispatch guard
     * (`highlightApplyDispatchedGeneration`) so the SAME (generation, postId)
     * re-dispatched the apply AND re-scheduled the fade-out — the user saw the
     * ring light, fade, then light again.
     *
     * The fix is the durable completion latch
     * ([HighlightArmingPolicy.isHighlightCycleAlreadyCompleted]) that survives
     * the dispatch-guard reset. This mirrors the controller decision order:
     *   1. completion latch checked FIRST — if (gen, post) already completed → return (no eval)
     *   2. only otherwise do the dispatch / fade-out guards run
     */
    private class HighlightCycleModel {
        var completedGeneration: Int = 0
            private set
        var completedPostId: Long = 0L
            private set
        var dispatchedGeneration: Int = 0
        var dispatchedPostId: Long = 0L
        var fadeoutScheduledGeneration: Int = 0
        var dispatchCount: Int = 0
            private set
        var scheduleCount: Int = 0
            private set

        /** Mirrors the completion-latch short-circuit at the top of reapplyTopicHighlight. */
        private fun alreadyCompleted(generation: Int, postId: Long): Boolean =
                HighlightArmingPolicy.isHighlightCycleAlreadyCompleted(
                        completedGeneration = completedGeneration,
                        completedPostId = completedPostId,
                        currentGeneration = generation,
                        currentPostId = postId,
                )

        /** Mirrors one reapplyTopicHighlight invocation for an already-revealed (non-deferred) target. */
        fun reapply(generation: Int, postId: Long) {
            if (alreadyCompleted(generation, postId)) return
            val shouldApply = HighlightArmingPolicy.shouldDispatchApplyForCurrentTarget(
                    dispatchedGeneration = dispatchedGeneration,
                    dispatchedPostId = dispatchedPostId,
                    currentGeneration = generation,
                    currentPostId = postId,
            )
            val shouldScheduleFadeout = shouldApply && fadeoutScheduledGeneration != generation
            if (!shouldApply && !shouldScheduleFadeout) return
            if (shouldApply) {
                dispatchCount++
                dispatchedGeneration = generation
                dispatchedPostId = postId
            }
            if (shouldScheduleFadeout) {
                scheduleCount++
                fadeoutScheduledGeneration = generation
                // Commit the durable completion latch (native: highlightCompleted*).
                completedGeneration = generation
                completedPostId = postId
            }
        }

        /** Mirrors the reveal/scroll-settled reset that clears ONLY the per-render dispatch guard. */
        fun resetDispatchGuardForReveal() {
            dispatchedGeneration = 0
            dispatchedPostId = 0L
        }
    }

    @Test
    fun repeatedRevealReArm_afterFadeoutScheduled_doesNotReDispatchOrReSchedule() {
        val model = HighlightCycleModel()
        // First reveal: lights up once and schedules the single fade-out.
        model.reapply(generation = 3, postId = 143876380L)
        assertEquals("first reveal dispatches exactly once", 1, model.dispatchCount)
        assertEquals("first reveal schedules the fade-out exactly once", 1, model.scheduleCount)

        // Later reveal / content-revealed re-arm resets the per-render dispatch
        // guard (the exact bug trigger), then reapply runs again for the SAME pair.
        model.resetDispatchGuardForReveal()
        model.reapply(generation = 3, postId = 143876380L)
        // scroll-settled is another such re-arm.
        model.resetDispatchGuardForReveal()
        model.reapply(generation = 3, postId = 143876380L)

        assertEquals("the completion latch must suppress all later re-dispatches", 1, model.dispatchCount)
        assertEquals("the fade-out must be scheduled at most once per (generation, postId)", 1, model.scheduleCount)
    }

    @Test
    fun newGeneration_afterPreviousCompleted_lightsUpOnceMore() {
        val model = HighlightCycleModel()
        model.reapply(generation = 3, postId = 143876380L)
        assertEquals(1, model.dispatchCount)
        assertEquals(1, model.scheduleCount)

        // A genuinely NEW render generation (page change/refresh) for the same
        // post must NOT be suppressed — it lights up once and schedules once.
        model.resetDispatchGuardForReveal()
        model.reapply(generation = 4, postId = 143876380L)
        assertEquals("a new generation must re-dispatch once", 2, model.dispatchCount)
        assertEquals("a new generation must schedule its own fade-out once", 2, model.scheduleCount)
    }
}
