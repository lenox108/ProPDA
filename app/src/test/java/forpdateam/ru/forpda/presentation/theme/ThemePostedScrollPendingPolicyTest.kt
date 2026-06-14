package forpdateam.ru.forpda.presentation.theme

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemePostedScrollPendingPolicyTest {

    @Test
    fun postedAnchorPrefersExplicitSubmitPostIdOverSmartEnd() {
        assertEquals(
                "200",
                ThemePostedScrollPendingPolicy.resolvePostedScrollAnchor(
                        isEditPost = false,
                        explicitPostId = 200,
                        smartEndPostId = "150",
                        pageAnchorPostId = "150",
                )
        )
    }

    @Test
    fun postedAnchorFallsBackToSmartEndOnlyWhenSubmitPostIdUnknown() {
        assertEquals(
                "200",
                ThemePostedScrollPendingPolicy.resolvePostedScrollAnchor(
                        isEditPost = false,
                        explicitPostId = null,
                        smartEndPostId = "200",
                        pageAnchorPostId = "150",
                )
        )
    }

    @Test
    fun editAnchorDoesNotUseSmartEndFallback() {
        assertNull(
                ThemePostedScrollPendingPolicy.resolvePostedScrollAnchor(
                        isEditPost = true,
                        explicitPostId = null,
                        smartEndPostId = "200",
                        pageAnchorPostId = "150",
                )
        )
    }

    @Test
    fun endNavigationPendingOnlyForSmartEndWithoutPostedAnchor() {
        assertTrue(
                ThemePostedScrollPendingPolicy.shouldMarkEndNavigationPending(
                        ThemeScrollCommand.Kind.END_ANCHOR_OR_BOTTOM,
                        pendingPostedPageScrollAnchor = null
                )
        )
        assertFalse(
                ThemePostedScrollPendingPolicy.shouldMarkEndNavigationPending(
                        ThemeScrollCommand.Kind.END_ANCHOR_OR_BOTTOM,
                        pendingPostedPageScrollAnchor = "143797503"
                )
        )
    }

    @Test
    fun postedScrollPendingWhenAnchorPresent() {
        assertTrue(
                ThemePostedScrollPendingPolicy.shouldMarkPostedPageScrollPending(
                        ThemeScrollCommand.Kind.END_ANCHOR_OR_BOTTOM,
                        pendingPostedPageScrollAnchor = "143797503"
                )
        )
        assertFalse(
                ThemePostedScrollPendingPolicy.shouldMarkPostedPageScrollPending(
                        ThemeScrollCommand.Kind.INITIAL_ANCHOR,
                        pendingPostedPageScrollAnchor = "143797503"
                )
        )
    }

    @Test
    fun deferFlushUntilRenderable_forPostedScroll() {
        assertTrue(
                ThemePostedScrollPendingPolicy.shouldDeferFlushUntilRenderable(
                        hasRenderableContent = false,
                        completedRenderHasPosts = false,
                        endNavigationPending = false,
                        postedPageScrollPending = true,
                )
        )
        assertFalse(
                ThemePostedScrollPendingPolicy.shouldDeferFlushUntilRenderable(
                        hasRenderableContent = true,
                        completedRenderHasPosts = false,
                        endNavigationPending = false,
                        postedPageScrollPending = true,
                )
        )
    }

    @Test
    fun deferFlushUntilRenderable_forSmartEnd() {
        assertTrue(
                ThemePostedScrollPendingPolicy.shouldDeferFlushUntilRenderable(
                        hasRenderableContent = false,
                        completedRenderHasPosts = false,
                        endNavigationPending = true,
                        postedPageScrollPending = false,
                )
        )
        assertFalse(
                ThemePostedScrollPendingPolicy.shouldDeferFlushUntilRenderable(
                        hasRenderableContent = true,
                        completedRenderHasPosts = false,
                        endNavigationPending = true,
                        postedPageScrollPending = false,
                )
        )
    }
}
