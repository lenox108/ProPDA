package forpdateam.ru.forpda.presentation.search

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchWebRenderPolicyTest {

    @Test
    fun defersLoadWhileWebViewHasZeroSize() {
        assertTrue(SearchWebRenderPolicy.shouldDeferHtmlLoad(0, 1080))
        assertTrue(SearchWebRenderPolicy.shouldDeferHtmlLoad(1080, 0))
        assertFalse(SearchWebRenderPolicy.shouldDeferHtmlLoad(1080, 1920))
    }

    @Test
    fun detectsDuplicateQueuedHtml() {
        assertTrue(SearchWebRenderPolicy.isDuplicateQueuedHtml(42, 42, renderGeneration = 3))
        assertFalse(SearchWebRenderPolicy.isDuplicateQueuedHtml(42, 99, renderGeneration = 3))
        assertFalse(SearchWebRenderPolicy.isDuplicateQueuedHtml(42, 42, renderGeneration = 0))
    }

    @Test
    fun skipsInflightDuplicateAfterLoadDispatched() {
        assertFalse(
                SearchWebRenderPolicy.shouldSkipInflightDuplicate(
                        force = false,
                        renderGeneration = 3,
                        htmlHash = 42,
                        loadDispatched = false,
                        domConfirmedGeneration = 0,
                        pendingHtmlHash = 42,
                )
        )
        assertTrue(
                SearchWebRenderPolicy.shouldSkipInflightDuplicate(
                        force = false,
                        renderGeneration = 3,
                        htmlHash = 42,
                        loadDispatched = true,
                        domConfirmedGeneration = 0,
                        pendingHtmlHash = 42,
                )
        )
        assertFalse(
                SearchWebRenderPolicy.shouldSkipInflightDuplicate(
                        force = true,
                        renderGeneration = 3,
                        htmlHash = 42,
                        loadDispatched = true,
                        domConfirmedGeneration = 0,
                        pendingHtmlHash = 42,
                )
        )
    }

    @Test
    fun bodyVisibleWhenContentHeightOrPostsPresent() {
        assertTrue(SearchWebRenderPolicy.isBodyVisible(contentHeight = 120, domPostCount = 0))
        assertTrue(SearchWebRenderPolicy.isBodyVisible(contentHeight = 0, domPostCount = 5))
        assertFalse(SearchWebRenderPolicy.isBodyVisible(contentHeight = 0, domPostCount = 0))
    }

    @Test
    fun blankRecoveryEscalatesLikeArticlePolicy() {
        assertTrue(
                SearchWebRenderPolicy.blankRecoveryDecision(retryCount = 1) ==
                        SearchWebRenderPolicy.BlankRecovery.RERENDER_CACHED
        )
        assertTrue(
                SearchWebRenderPolicy.blankRecoveryDecision(retryCount = 2) ==
                        SearchWebRenderPolicy.BlankRecovery.REFETCH
        )
    }
}
