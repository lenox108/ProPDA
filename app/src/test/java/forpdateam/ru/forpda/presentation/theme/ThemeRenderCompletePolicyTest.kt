package forpdateam.ru.forpda.presentation.theme

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeRenderCompletePolicyTest {

    @Test
    fun trustsDomVerifiedPostsWhenNativeContentHeightIsZero() {
        assertTrue(
                ThemeRenderCompletePolicy.hasCompletedRender(
                        renderKey = "trace::1",
                        completedRenderKey = "trace::1",
                        completedRenderHasPosts = true,
                        jsReady = true,
                        hasParent = true,
                        contentHeight = 0,
                        blankContentThreshold = 4,
                )
        )
    }

    @Test
    fun requiresDomVerificationBeforeIgnoringBlankContent() {
        assertFalse(
                ThemeRenderCompletePolicy.hasCompletedRender(
                        renderKey = "trace::1",
                        completedRenderKey = "trace::1",
                        completedRenderHasPosts = false,
                        jsReady = true,
                        hasParent = true,
                        contentHeight = 0,
                        blankContentThreshold = 4,
                )
        )
    }

    @Test
    fun rejectsMismatchedRenderKey() {
        assertFalse(
                ThemeRenderCompletePolicy.hasCompletedRender(
                        renderKey = "trace::1",
                        completedRenderKey = "trace::2",
                        completedRenderHasPosts = true,
                        jsReady = true,
                        hasParent = true,
                        contentHeight = 500,
                        blankContentThreshold = 4,
                )
        )
    }
}
