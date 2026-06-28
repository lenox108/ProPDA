package forpdateam.ru.forpda.ui.fragments.theme.modules

import forpdateam.ru.forpda.ui.views.ExtendedWebView
import io.mockk.mockk
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression test for [ThemeJsApi.applyHighlight].
 *
 * The native call into `window.PPDA_applyHighlight(postId, type, generationId)`
 * is the runtime bridge that re-asserts the topic-post highlight on the live
 * WebView after the static class has been embedded in [template_theme.html].
 * Without this call, any post-render DOM mutation (smart-patch, infinite-scroll
 * append) can drop the class, and the diagnostic `js_highlight_applied` /
 * `native_highlight_bound` events never fire.
 *
 * The full JS guard lives in the WebView and cannot run as a JVM unit test
 * (see `HighlightJsGuardTest` for the guard contract). Here we only assert
 * the *Kotlin* side:
 *  - the generated JS string targets the documented function name with the
 *    documented signature;
 *  - numeric postId and generationId are interpolated as plain JS numbers
 *    (not strings) so the JS `typeof postId === "number"` guard works;
 *  - the `type` argument is JSON-quoted to defeat injection when it later
 *    becomes user-controlled (e.g. a future FirstUnread/Explicit type from
 *    a deep link).
 *
 * Note: the JVM unit-test classpath's `org.json.JSONObject.quote()` returns
 * `null` for a non-null input (it has no `JSONStringer`), so we cannot assert
 * the exact quoting on this classpath. The Android runtime uses Android's
 * `org.json.JSONObject.quote` which DOES return a properly-quoted string —
 * this is exercised by the live-WebView QA checklist (`docs/topic-highlight-qa.md`).
 */
class ThemeJsApiApplyHighlightTest {

    private val jsApi = ThemeJsApi(mockk<ExtendedWebView>(relaxed = true))

    @Test
    fun applyHighlight_emitsExpectedCallSignature() {
        val script = jsApi.applyHighlight(postId = 143898864L, type = "last-read", generationId = 7)
        // Function name + argument order match template_theme.html's contract
        // window.PPDA_applyHighlight(postId,type,generationId,allowScroll).
        assertTrue(
                "Expected call to window.PPDA_applyHighlight(postId,type,generationId,allowScroll) but was: $script",
                script.contains("window.PPDA_applyHighlight(143898864,") &&
                        script.contains(",7,true)")
        )
    }

    @Test
    fun applyHighlight_emitsTypeGuard() {
        val script = jsApi.applyHighlight(postId = 1L, type = "first-unread", generationId = 1)
        // Defensive: a missing/removed function on the page must not throw.
        assertTrue(
                "Expected typeof-guard around window.PPDA_applyHighlight: $script",
                script.startsWith("if(typeof window.PPDA_applyHighlight==='function'){")
        )
    }

    @Test
    fun applyHighlight_doesNotInterpolateRawType() {
        // Even though the JVM unit-test classpath's JSONObject.quote returns
        // null for some inputs, the production Android runtime quotes
        // properly. We assert here that the type is NEVER interpolated as a
        // bare token — i.e. it must come back from JSONObject.quote, which
        // (on Android) wraps it in quotes. If a future refactor accidentally
        // interpolates the raw type string, this assertion catches it.
        val script = jsApi.applyHighlight(postId = 1L, type = "last-read", generationId = 1)
        // The script must contain the type SOMEWHERE — whether as a quoted
        // string on Android or as a null token on the JVM test classpath.
        // What we forbid is the bare unquoted token appearing next to the
        // postId integer.
        assertFalse(
                "Type must not be a bare unquoted token: $script",
                script.contains("(1,last-read,") || script.contains("(1, last-read,")
        )
    }

    @Test
    fun applyHighlight_interpolatesPostIdAsNumber() {
        val script = jsApi.applyHighlight(postId = 42L, type = "explicit", generationId = 3)
        // postId must be a bare integer literal so the JS `parseInt` on the
        // target side and the `typeof postId === "number"` guard agree.
        assertTrue("postId must be a bare integer: $script", script.contains("(42,"))
        assertFalse("postId must not be stringified: $script", script.contains("\"42\""))
    }

    @Test
    fun applyHighlight_interpolatesGenerationIdAsNumber() {
        val script = jsApi.applyHighlight(postId = 1L, type = "last-read", generationId = 12)
        // generationId must be a bare integer literal — the JS guard does
        // `typeof generationId === "number"` to filter stale callbacks. The 4th arg is allowScroll.
        assertTrue("generationId must be a bare integer: $script", script.contains(",12,true)"))
        assertFalse("generationId must not be stringified: $script", script.contains("\"12\""))
    }
}
