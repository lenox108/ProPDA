package forpdateam.ru.forpda.presentation.theme

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeWebViewRevealFromDomPolicyTest {

    @Test
    fun marksRenderVerifiedWhenDomHasPosts() {
        val state = RenderVerificationState(
                activeRenderKey = "trace::1",
                activeRenderExpectedPosts = 13,
                jsReady = true,
        )
        assertTrue(state.markRenderVerifiedFromDom(domPosts = 13))
        assertTrue(state.hasCompletedRender("trace::1"))
    }

    @Test
    fun rejectsRenderVerificationWithoutPosts() {
        val state = RenderVerificationState(
                activeRenderKey = "trace::1",
                activeRenderExpectedPosts = 13,
                jsReady = true,
        )
        assertFalse(state.markRenderVerifiedFromDom(domPosts = 0))
        assertFalse(state.hasCompletedRender("trace::1"))
    }

    @Test
    fun rejectsRenderVerificationWhenJsNotReady() {
        val state = RenderVerificationState(
                activeRenderKey = "trace::1",
                activeRenderExpectedPosts = 13,
                jsReady = false,
        )
        assertFalse(state.markRenderVerifiedFromDom(domPosts = 13))
    }

    private class RenderVerificationState(
            private val activeRenderKey: String?,
            private val activeRenderExpectedPosts: Int,
            private val jsReady: Boolean,
    ) {
        private var completedRenderKey: String? = null
        private var completedRenderHasPosts = false

        fun markRenderVerifiedFromDom(domPosts: Int): Boolean {
            val renderKey = activeRenderKey ?: return false
            if (!jsReady || domPosts <= 0) return false
            completedRenderKey = renderKey
            completedRenderHasPosts = true
            return true
        }

        fun hasCompletedRender(renderKey: String): Boolean =
                ThemeRenderCompletePolicy.hasCompletedRender(
                        renderKey = renderKey,
                        completedRenderKey = completedRenderKey,
                        completedRenderHasPosts = completedRenderHasPosts,
                        jsReady = jsReady,
                        hasParent = true,
                        contentHeight = 0,
                        blankContentThreshold = 4,
                )
    }
}
