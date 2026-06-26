package forpdateam.ru.forpda.ui.fragments.theme.modules

import forpdateam.ru.forpda.ui.views.ExtendedWebView
import io.mockk.mockk
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeJsApiTest {

    private val jsApi = ThemeJsApi(mockk<ExtendedWebView>(relaxed = true))

    @Test
    fun nativeScrollToAnchorPost_doesNotScheduleJsAnchorRetries() {
        val script = jsApi.nativeScrollToAnchorPost("entry143825965")

        assertFalse(script.contains("scrollToElementWithRetries"))
        assertFalse(script.contains("scrollToElement("))
        assertTrue(script.contains("window.scrollTo(0,y)"))
    }

    @Test
    fun nativeScrollToAnchorPost_alignsPostTopUsingTopChromePadding_notMagic45() {
        // S2 (top-clip): a fresh first-unread / explicit open has no saved viewport offset
        // (window.loadAnchorOffsetTop === null). The anchor-y must align the post TOP to the
        // visible top by subtracting the real sticky-header height (topChromePadding), the same
        // offset the JS doScroll/end-anchor paths use — NOT the old hardcoded 45 which clipped the
        // post above the viewport top.
        val script = jsApi.nativeScrollToAnchorPost("entry143825965")

        assertTrue(
                "must fall back to topChromePadding when no saved offset",
                script.contains("topChromePadding")
        )
        assertTrue(script.contains("var y=Math.max(0,Math.round(top-offset))"))
        // The magic 45 fallback must be gone.
        assertFalse("must not hardcode the 45px offset", script.contains(":45"))
    }

    @Test
    fun nativeScrollToAnchorPost_stillHonorsGenuineSavedOffset() {
        // A genuine back/restore (loadAnchorOffsetTop is a finite number) must keep resuming the
        // exact saved viewport offset; only the null (fresh-open) case changes.
        val script = jsApi.nativeScrollToAnchorPost("entry143825965")

        assertTrue(
                script.contains("typeof window.loadAnchorOffsetTop==='number'&&isFinite(window.loadAnchorOffsetTop)")
        )
    }
}
