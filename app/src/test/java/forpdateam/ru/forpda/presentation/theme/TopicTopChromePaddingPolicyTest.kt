package forpdateam.ru.forpda.presentation.theme

import org.junit.Assert.assertEquals
import org.junit.Test

class TopicTopChromePaddingPolicyTest {

    @Test
    fun usesPaginationBottomWhenHigherThanAppBar() {
        assertEquals(
                84,
                TopicTopChromePaddingPolicy.paddingPxFromWindowGeometry(
                        webViewWindowY = 100,
                        appBarWindowY = 100,
                        appBarHeight = 48,
                        paginationWindowY = 148,
                        paginationHeight = 36,
                        paginationVisible = true,
                )
        )
    }

    @Test
    fun ignoresHiddenPagination() {
        assertEquals(
                48,
                TopicTopChromePaddingPolicy.paddingPxFromWindowGeometry(
                        webViewWindowY = 100,
                        appBarWindowY = 100,
                        appBarHeight = 48,
                        paginationWindowY = 148,
                        paginationHeight = 36,
                        paginationVisible = false,
                )
        )
    }

    @Test
    fun returnsZeroWhenWebViewStartsAtChromeBottom() {
        assertEquals(
                0,
                TopicTopChromePaddingPolicy.paddingPxFromChromeBottom(
                        webViewWindowY = 156,
                        chromeBottomWindowY = 156,
                )
        )
    }

    @Test
    fun neverReturnsNegativePadding() {
        assertEquals(
                0,
                TopicTopChromePaddingPolicy.paddingPxFromWindowGeometry(
                        webViewWindowY = 200,
                        appBarWindowY = 100,
                        appBarHeight = 48,
                        paginationWindowY = null,
                        paginationHeight = 0,
                        paginationVisible = false,
                )
        )
    }

    @Test
    fun usesAppBarBottomWhenPaginationHidden() {
        assertEquals(
                32,
                TopicTopChromePaddingPolicy.paddingPxFromWindowGeometry(
                        webViewWindowY = 100,
                        appBarWindowY = 80,
                        appBarHeight = 52,
                        paginationWindowY = 132,
                        paginationHeight = 36,
                        paginationVisible = false,
                )
        )
    }

    @Test
    fun expandedChromeBottomRestoresHiddenToolbarOffset() {
        assertEquals(
                200,
                TopicTopChromePaddingPolicy.expandedChromeBottomWindowY(
                        chromeWindowY = 88,
                        chromeHeight = 56,
                        translationY = -56f,
                )
        )
    }
}
