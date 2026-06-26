package forpdateam.ru.forpda.ui.fragments.theme.modules

import forpdateam.ru.forpda.ui.views.ExtendedWebView
import io.mockk.mockk
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression test for [ThemeJsApi.scheduleHighlightFadeout].
 *
 * The native call into
 * `window.PPDA_scheduleHighlightFadeout(generationId, delayMs)` is the runtime
 * bridge that arms the JS-side 2-second visible window for the topic-post
 * highlight. Without this call, the static `post-highlight-*` class would
 * stay on the post forever (the source of truth for theme/layout) — but the
 * user wants a brief ~2-second flash, not a permanent state.
 *
 * The full JS timer lives in the WebView and cannot run as a JVM unit test
 * (see `HighlightJsGuardTest` for the guard contract). Here we only assert
 * the *Kotlin* side:
 *  - the generated JS string targets the documented function name with the
 *    documented signature;
 *  - both `generationId` and `delayMs` are interpolated as plain JS numbers
 *    (not strings) so the JS `typeof generationId === "number"` guard and
 *    the `setTimeout(_, delayMs)` call work;
 *  - a `typeof`-guard wraps the call so a missing/removed JS function on
 *    the page must not throw.
 *
 * Mirrors the structure of `ThemeJsApiApplyHighlightTest` to keep the two
 * JS bridges in lock-step.
 */
class ThemeJsApiScheduleHighlightFadeoutTest {

    private val jsApi = ThemeJsApi(mockk<ExtendedWebView>(relaxed = true))

    @Test
    fun scheduleHighlightFadeout_emitsExpectedCallSignature() {
        val script = jsApi.scheduleHighlightFadeout(generationId = 7, delayMs = 2000)
        // Function name + argument order match template_theme.html's contract.
        assertTrue(
                "Expected call to window.PPDA_scheduleHighlightFadeout(generationId,delayMs) but was: $script",
                script.contains("window.PPDA_scheduleHighlightFadeout(7,2000)")
        )
    }

    @Test
    fun scheduleHighlightFadeout_emitsTypeGuard() {
        val script = jsApi.scheduleHighlightFadeout(generationId = 1, delayMs = 2000)
        // Defensive: a missing/removed function on the page must not throw.
        assertTrue(
                "Expected typeof-guard around window.PPDA_scheduleHighlightFadeout: $script",
                script.startsWith("if(typeof window.PPDA_scheduleHighlightFadeout==='function'){")
        )
    }

    @Test
    fun scheduleHighlightFadeout_interpolatesGenerationIdAsNumber() {
        val script = jsApi.scheduleHighlightFadeout(generationId = 12, delayMs = 2000)
        // generationId must be a bare integer literal — the JS guard
        // checks `typeof generationId === "number"` to reject bad input.
        assertTrue("generationId must be a bare integer: $script", script.contains("(12,"))
        assertFalse("generationId must not be stringified: $script", script.contains("\"12\""))
    }

    @Test
    fun scheduleHighlightFadeout_interpolatesDelayMsAsNumber() {
        val script = jsApi.scheduleHighlightFadeout(generationId = 1, delayMs = 2000)
        // delayMs must be a bare integer literal so `setTimeout(_, delayMs)`
        // gets a real number, not a string that would coerce to 0.
        assertTrue("delayMs must be a bare integer: $script", script.contains(",2000)"))
        assertFalse("delayMs must not be stringified: $script", script.contains("\"2000\""))
    }

    @Test
    fun scheduleHighlightFadeout_defaultDelayIs2000() {
        // The visible window is fixed at 2 seconds per the spec. This test
        // pins the constant value so a future refactor that drops the
        // `2000` literal must consciously re-confirm the user-facing flash
        // duration.
        val script = jsApi.scheduleHighlightFadeout(generationId = 1, delayMs = 2000)
        assertTrue(
                "Expected 2-second visible window: $script",
                script.contains(",2000)")
        )
    }
}
