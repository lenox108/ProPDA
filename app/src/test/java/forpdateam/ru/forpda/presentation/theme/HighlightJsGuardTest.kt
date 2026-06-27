package forpdateam.ru.forpda.presentation.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path

/**
 * Step 6 + Step 9 / T6: stale-callback suppression logic.
 *
 * The runtime JS function `window.PPDA_applyHighlight(postId, type, generationId)`
 * is exercised end-to-end inside the WebView; we cannot run that in a JVM unit
 * test. Instead we model the contract here and assert the *decision*:
 *
 *   - a callback whose generationId is strictly less than the current
 *     `PageInfo.ppdaHighlight.generationId` is dropped (returns false, no
 *     highlight applied);
 *   - a callback with a generationId equal to or greater than the current is
 *     applied and updates the current generationId;
 *   - the post must be on the rendered page for the highlight to actually land.
 *
 * This mirrors the implementation in `app/src/main/assets/template_theme.html`
 * and `TopicHighlightApply`. If the JS contract changes, the runtime path will
 * break; this test acts as a tripwire so the contract stays in sync.
 */
class HighlightJsGuardTest {

    private data class JsHighlight(
            var postId: Long = 0L,
            var type: String = "none",
            var generationId: Int = 0,
    )

    /**
     * Mirror of the JS function's decision logic. We intentionally keep this
     * short and obvious — the goal is to spell the contract.
     */
    private fun applyHighlight(
            current: JsHighlight,
            postId: Long,
            type: String,
            callbackGenerationId: Int,
            pagePostIds: Set<Long>,
    ): Boolean {
        if (callbackGenerationId < current.generationId) {
            return false
        }
        current.postId = postId
        current.type = type
        current.generationId = callbackGenerationId
        if (postId == 0L || type == "none" || postId !in pagePostIds) return false
        return true
    }

    @Test
    fun staleCallback_isDropped() {
        val current = JsHighlight(postId = 0L, type = "none", generationId = 5)
        val pagePostIds = setOf(100L, 200L, 300L)
        val applied = applyHighlight(
                current = current,
                postId = 200L,
                type = "last-read",
                callbackGenerationId = 4,
                pagePostIds = pagePostIds,
        )
        assertTrue(!applied)
        // Stale callback must not update state.
        assertEquals(0L, current.postId)
        assertEquals("none", current.type)
        assertEquals(5, current.generationId)
    }

    @Test
    fun freshCallback_isApplied() {
        val current = JsHighlight(postId = 0L, type = "none", generationId = 5)
        val pagePostIds = setOf(100L, 200L, 300L)
        val applied = applyHighlight(
                current = current,
                postId = 200L,
                type = "last-read",
                callbackGenerationId = 6,
                pagePostIds = pagePostIds,
        )
        assertTrue(applied)
        assertEquals(200L, current.postId)
        assertEquals("last-read", current.type)
        assertEquals(6, current.generationId)
    }

    @Test
    fun equalGenerationId_isApplied() {
        // Equal generation: still applies (the new page is the *same* logical render).
        val current = JsHighlight(postId = 0L, type = "none", generationId = 5)
        val pagePostIds = setOf(100L, 200L)
        val applied = applyHighlight(
                current = current,
                postId = 100L,
                type = "first-unread",
                callbackGenerationId = 5,
                pagePostIds = pagePostIds,
        )
        assertTrue(applied)
        assertEquals(100L, current.postId)
        assertEquals(5, current.generationId)
    }

    @Test
    fun postNotOnPage_fails() {
        val current = JsHighlight(postId = 0L, type = "none", generationId = 5)
        val pagePostIds = setOf(100L, 200L)
        val applied = applyHighlight(
                current = current,
                postId = 9999L,
                type = "last-read",
                callbackGenerationId = 6,
                pagePostIds = pagePostIds,
        )
        assertTrue(!applied)
    }

    @Test
    fun applyHighlightScrollsOffscreenTargetIntoView() {
        val templatePath = listOf(
                Path.of("src/main/assets/template_theme.html"),
                Path.of("app/src/main/assets/template_theme.html"),
        ).first { Files.exists(it) }
        val template = Files.newInputStream(templatePath).bufferedReader().readText()
        // It must still bring an off-screen highlighted post into view, but INSTANTLY: this WebView
        // animates scrollIntoView (even behavior:"auto"), so the smooth scrollInto({block:"center"})
        // was the visible "forced scroll" on open/link/back. The highlight now centers the post via a
        // direct scrollTop assignment (instant). Assert it still gates on viewport AND positions.
        assertTrue(
                "PPDA_applyHighlight must still bring an off-screen target into view (ppdaIsInViewport gate)",
                template.contains("if (!ppdaIsInViewport(target))"),
        )
        assertTrue(
                "PPDA_applyHighlight must center an off-screen target INSTANTLY via scrollTop (no animated scrollIntoView)",
                template.contains("ppdaSe.scrollTop = ppdaY"),
        )
        assertTrue(
                "PPDA_applyHighlight off-screen positioning must not use an animated scrollIntoView",
                !template.contains("scrollIntoView({ block: \"center\""),
        )
    }

    // -----------------------------------------------------------------------
    // Fadeout contract.
    //
    // The visible state of the highlight is timed: a new render arms a
    // 2-second setTimeout; the class is then removed on `transitionend`
    // and JS reports back via `IThemePresenter.highlightFadeoutCompleted`.
    // Scrolling within the same page does NOT restart the timer; a page
    // change (fresh generation) DOES re-arm. A stale re-apply on a
    // generation that has already faded must NOT bring the highlight back
    // (the CSS transition is in flight, re-adding the class would race).
    //
    // The full timer/transitionend lives inside the WebView and cannot run
    // as a JVM unit test. We model the *decision* here.
    // -----------------------------------------------------------------------

    private data class JsFadeout(
            var scheduledGeneration: Int = 0,
            var fadedGeneration: Int = 0,
            var deadlineAtMs: Long = 0L,
            var lastResult: String = "none",
    )

    /**
     * Mirror of the JS contract: a single (gen, delay) call arms a fresh
     * timer; an equal/older generation is a no-op; a new generation on
     * top of an old one cancels the old timer and arms a new one.
     */
    private fun scheduleFadeout(
            state: JsFadeout,
            generationId: Int,
            delayMs: Int,
            now: Long = 0L,
    ): String {
        if (generationId <= 0) return "rejected"
        if (state.scheduledGeneration == generationId) {
            state.lastResult = "noop_same_generation"
            return "noop_same_generation"
        }
        state.scheduledGeneration = generationId
        state.deadlineAtMs = now + delayMs
        state.lastResult = "armed"
        return "armed"
    }

    /**
     * Mirror of the JS contract: when the timer fires (or the
     * `transitionend` handler strips the class), the generation is
     * marked as "faded". A later `applyHighlight` on a faded generation
     * must NOT bring the class back.
     */
    private fun applyHighlightAfterFadeout(
            current: JsHighlight,
            state: JsFadeout,
            callbackGenerationId: Int,
    ): Boolean {
        if (callbackGenerationId < current.generationId) return false
        // The new contract: re-applying on a generation that has already
        // been faded (or is mid-fade) is rejected. The CSS transition
        // is in flight; re-asserting the class would clobber it.
        if (state.fadedGeneration == current.generationId) return false
        current.generationId = callbackGenerationId
        return true
    }

    @Test
    fun fadeout_freshGeneration_armsTimer() {
        val state = JsFadeout()
        val result = scheduleFadeout(state, generationId = 1, delayMs = 2000, now = 0L)
        assertEquals("armed", result)
        assertEquals(1, state.scheduledGeneration)
        assertEquals(2000L, state.deadlineAtMs)
    }

    @Test
    fun fadeout_equalGeneration_isNoOp() {
        // Re-arming for the same generation (e.g. a smart-patch that
        // re-fires `applyHighlight` followed by `scheduleHighlightFadeout`)
        // MUST NOT extend the deadline. The original timer wins.
        val state = JsFadeout()
        scheduleFadeout(state, generationId = 1, delayMs = 2000, now = 0L)
        val result = scheduleFadeout(state, generationId = 1, delayMs = 2000, now = 1000L)
        assertEquals("noop_same_generation", result)
        // Deadline preserved at 0 + 2000 = 2000 (NOT 1000 + 2000 = 3000).
        assertEquals(2000L, state.deadlineAtMs)
    }

    @Test
    fun fadeout_newGeneration_cancelsOldArmsNew() {
        // A page change bumps the render generation. The old pending
        // timer is cancelled and a fresh 2-second deadline is armed.
        val state = JsFadeout()
        scheduleFadeout(state, generationId = 1, delayMs = 2000, now = 0L)
        val result = scheduleFadeout(state, generationId = 2, delayMs = 2000, now = 1000L)
        assertEquals("armed", result)
        assertEquals(2, state.scheduledGeneration)
        assertEquals(3000L, state.deadlineAtMs)
    }

    @Test
    fun fadeout_invalidGeneration_isRejected() {
        val state = JsFadeout()
        val result = scheduleFadeout(state, generationId = 0, delayMs = 2000)
        assertEquals("rejected", result)
        assertEquals(0, state.scheduledGeneration)
    }

    @Test
    fun fadeout_completionThenReapply_isRejected() {
        // The timer fires, the class is removed, JS reports back via
        // `highlightFadeoutCompleted`. A subsequent `applyHighlight` call
        // for the same generation (e.g. a stray smart-patch re-assert)
        // must NOT bring the highlight back: the CSS transition is gone,
        // re-adding the class would race the cleanup.
        val state = JsFadeout()
        val current = JsHighlight(postId = 100L, type = "last-read", generationId = 1)
        scheduleFadeout(state, generationId = 1, delayMs = 2000, now = 0L)
        // Simulate the timer firing + transitionend cleanup.
        state.fadedGeneration = 1
        val reapplied = applyHighlightAfterFadeout(
                current = current,
                state = state,
                callbackGenerationId = 1,
        )
        assertTrue(!reapplied)
    }

    // -----------------------------------------------------------------------
    // Fade-removal "remove once" race.
    //
    // The visible highlight is an `::after` overlay FRAME whose opacity fades
    // over ~250ms. Because pseudo-element `transitionend` is unreliable on some
    // WebView builds, the JS arms BOTH a `transitionend` listener and a 350ms
    // setTimeout fallback; whichever fires first removes the highlight classes,
    // guarded by a per-node `done` flag so cleanup runs exactly once. We model
    // that guard here: the classes must be cleared exactly once regardless of
    // which path (or both) fires.
    // -----------------------------------------------------------------------

    private class FadeNode {
        var hasHighlightClass = true
        var hasFadingClass = true
        var removeCount = 0
        private var done = false

        /** Mirror of the JS `removeClasses()` once-guard. */
        fun removeClasses() {
            if (done) return
            done = true
            removeCount += 1
            hasFadingClass = false
            hasHighlightClass = false
        }
    }

    @Test
    fun fadeRemoval_transitionendThenFallback_removesExactlyOnce() {
        val node = FadeNode()
        // transitionend fires first...
        node.removeClasses()
        // ...then the 350ms fallback fires too.
        node.removeClasses()
        assertEquals(1, node.removeCount)
        assertTrue(!node.hasHighlightClass)
        assertTrue(!node.hasFadingClass)
    }

    @Test
    fun fadeRemoval_fallbackOnly_removesClasses() {
        // Simulate the case where `transitionend` never fires for the overlay
        // opacity and only the 350ms fallback runs.
        val node = FadeNode()
        node.removeClasses()
        assertEquals(1, node.removeCount)
        assertTrue(!node.hasHighlightClass)
    }

    @Test
    fun scrollWithinSamePage_doesNotReapplyHighlight() {
        // After fadeout completes for a generation, scrolling does NOT bump the
        // generation, so a re-apply on the same (now-faded) generation is
        // rejected — the outline must not flash again while scrolling.
        val state = JsFadeout()
        val current = JsHighlight(postId = 100L, type = "first-unread", generationId = 3)
        scheduleFadeout(state, generationId = 3, delayMs = 2000, now = 0L)
        state.fadedGeneration = 3
        // Scroll events do not change the generation; a stray re-apply on gen 3
        // must be rejected.
        val reapplied = applyHighlightAfterFadeout(
                current = current,
                state = state,
                callbackGenerationId = 3,
        )
        assertTrue(!reapplied)
    }

    // -----------------------------------------------------------------------
    // Post-fade completion latch (`ppdaDone`).
    //
    // Double-light-up fix (device log 24_06-23-12, post 143876380 gen=3): the
    // `noop_already_applied` guard only covered the STILL-LIT window. Once the
    // first fade had started/completed, a later re-apply for the SAME
    // (postId, generation) RE-LIT the ring (a second visible flash). The JS
    // now carries a durable `ppdaDone` latch keyed on (postId, generation):
    // after a full light+fade cycle, any re-apply for that exact pair is a
    // NO-OP and logs `noop_generation_done`. A new generation / different post
    // does not match the latch and still lights up once. This mirrors the
    // native completion latch (HighlightArmingPolicy.isHighlightCycleAlreadyCompleted).
    // -----------------------------------------------------------------------

    private data class JsDone(var generation: Int = 0, var postId: Long = 0L)

    /** Mirror of the JS `ppdaDone` short-circuit at the top of PPDA_applyHighlight. */
    private fun applyHighlightWithDoneLatch(
            done: JsDone,
            callbackGenerationId: Int,
            postId: Long,
    ): String {
        if (callbackGenerationId == done.generation && postId == done.postId) {
            return "noop_generation_done"
        }
        return "applied"
    }

    @Test
    fun doneLatch_reapplySamePairAfterFade_isNoOp() {
        // Post 143876380 gen=3 completed its single light+fade cycle.
        val done = JsDone(generation = 3, postId = 143876380L)
        val result = applyHighlightWithDoneLatch(
                done = done,
                callbackGenerationId = 3,
                postId = 143876380L,
        )
        assertEquals(
                "a re-apply for an already-faded (postId, generation) must be a no-op (no re-light)",
                "noop_generation_done",
                result,
        )
    }

    @Test
    fun doneLatch_newGenerationForSamePost_stillLights() {
        val done = JsDone(generation = 3, postId = 143876380L)
        assertEquals(
                "a new generation for the same post must still light up once",
                "applied",
                applyHighlightWithDoneLatch(done, callbackGenerationId = 4, postId = 143876380L),
        )
    }

    @Test
    fun doneLatch_differentPostInSameGeneration_stillLights() {
        val done = JsDone(generation = 3, postId = 143876380L)
        assertEquals(
                "a different post in the same generation must still light up once",
                "applied",
                applyHighlightWithDoneLatch(done, callbackGenerationId = 3, postId = 143999999L),
        )
    }

    @Test
    fun template_carriesPostFadeDoneLatch() {
        // Tripwire: the runtime JS must keep the `ppdaDone` post-fade latch and
        // emit `noop_generation_done` so the next on-device log proves a later
        // re-apply for an already-faded (postId, generation) does not re-light.
        val templatePath = listOf(
                Path.of("src/main/assets/template_theme.html"),
                Path.of("app/src/main/assets/template_theme.html"),
        ).first { Files.exists(it) }
        val template = Files.newInputStream(templatePath).bufferedReader().readText()
        assertTrue(
                "PPDA_applyHighlight must carry the durable post-fade completion latch (ppdaDone)",
                template.contains("ppdaDone"),
        )
        assertTrue(
                "the post-fade no-op must be logged distinctly as noop_generation_done",
                template.contains("noop_generation_done"),
        )
    }

    @Test
    fun fadeout_newRenderAfterCompletion_reappliesHighlight() {
        // Once a render has been faded out, a *new* render (greater
        // generation) is a fresh visible window: the timer is armed, the
        // class is re-applied, and a new fadeout will be scheduled.
        val state = JsFadeout()
        val current = JsHighlight(postId = 100L, type = "last-read", generationId = 1)
        scheduleFadeout(state, generationId = 1, delayMs = 2000, now = 0L)
        state.fadedGeneration = 1
        // Bump the generation: a new render.
        current.generationId = 2
        scheduleFadeout(state, generationId = 2, delayMs = 2000, now = 3000L)
        assertEquals(2, state.scheduledGeneration)
        assertEquals(5000L, state.deadlineAtMs)
        // The "faded" mark from the previous render is now stale and
        // must NOT block the new render's re-apply.
        val reapplied = applyHighlightAfterFadeout(
                current = current,
                state = state,
                callbackGenerationId = 2,
        )
        assertTrue(reapplied)
    }
}
