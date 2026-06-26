package forpdateam.ru.forpda.ui.fragments.theme.modules

import forpdateam.ru.forpda.ui.views.ExtendedWebView
import io.mockk.mockk
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * S-01 / R-03 (audit Finding S-01 / R-03): Kotlin announces ownership of the
 * initial-anchor scroll so the JS DOM-anchor path becomes fallback-only.
 *
 * The runtime handshake (`setThemeInitialAnchorExpected` in `theme.js`) is
 * exercised inside the WebView; here we pin only the Kotlin side — the generated
 * JS snippet must call the documented function with the documented argument,
 * guarded so a missing function never throws.
 */
class ThemeJsApiInitialAnchorExpectTest {

    private val jsApi = ThemeJsApi(mockk<ExtendedWebView>(relaxed = true))

    @Test
    fun setThemeInitialAnchorExpected_armsWithWindowMs() {
        val script = jsApi.setThemeInitialAnchorExpected(700)
        assertTrue(
                "Expected guarded call to setThemeInitialAnchorExpected(700): $script",
                script.contains("setThemeInitialAnchorExpected(700)")
        )
        assertTrue(
                "Expected typeof-guard so a missing function cannot throw: $script",
                script.startsWith("if(typeof setThemeInitialAnchorExpected==='function'){")
        )
    }

    @Test
    fun setThemeInitialAnchorExpected_disarmsWithZero() {
        val script = jsApi.setThemeInitialAnchorExpected(0)
        assertTrue(
                "Disarm must pass a non-positive window: $script",
                script.contains("setThemeInitialAnchorExpected(0)")
        )
    }
}
