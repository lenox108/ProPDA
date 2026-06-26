package forpdateam.ru.forpda.presentation.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression for the Pack-B consolidation in the back-navigation audit: the captured source
 * anchor must live long enough to cover cross-topic link navigation flows
 * ([ThemeViewModel.consumeLinkSourceAnchorFor]) AND the same-topic scroll restoration
 * ([ThemeViewModel.sourceAnchorAppliesTo]). The two paths now share a single field
 * ([ThemeViewModel.pendingHistorySourceAnchor]) and a single TTL constant.
 */
class SourceAnchorTtlTest {

    @Test
    fun sourceAnchorTtl_isAtLeast15Seconds() {
        // 15s covers: typical user think-time between tap and the WebViewClient's
        // handleUri consuming the anchor for cross-topic navigation, plus the
        // same-topic history restore that fires on the new page's loadData.
        assertTrue(
            "SOURCE_ANCHOR_TTL_MS=$SOURCE_ANCHOR_TTL_MS must be >= 15s",
            SOURCE_ANCHOR_TTL_MS >= 15_000L
        )
    }

    @Test
    fun sourceAnchorTtl_isExactly15Seconds() {
        // Pinned to 15s by the audit (Pack B, lastLinkSourceAnchor removal). The previous
        // 8s TTL was too short to cover cross-topic navigation; reverting would re-introduce
        // the bug where consumeLinkSourceAnchorFor returns null for a user who paused between
        // tapping a cross-topic link and the WebViewClient dispatching handleUri.
        assertEquals(15_000L, SOURCE_ANCHOR_TTL_MS)
    }
}
