package forpdateam.ru.forpda.presentation.theme

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeHatToolbarClickPolicyTest {

    @Test
    fun `preserves hat overlay on explicit user open render retry`() {
        assertTrue(
                ThemeHatToolbarClickPolicy.shouldPreserveHatOnRenderRetry(
                        userHatOpenOverride = true,
                        reason = "initialLoad",
                        listPostsUnderRendered = false,
                )
        )
        assertFalse(
                ThemeHatToolbarClickPolicy.shouldPreserveHatOnRenderRetry(
                        userHatOpenOverride = true,
                        reason = "hatOverlayEnsure",
                        listPostsUnderRendered = true,
                )
        )
        assertFalse(
                ThemeHatToolbarClickPolicy.shouldPreserveHatOnRenderRetry(
                        userHatOpenOverride = null,
                        reason = "hatOverlayEnsure",
                        listPostsUnderRendered = false,
                )
        )
        assertFalse(
                ThemeHatToolbarClickPolicy.shouldPreserveHatOnRenderRetry(
                        userHatOpenOverride = null,
                        reason = "initialLoad",
                        listPostsUnderRendered = false,
                )
        )
    }

    @Test
    fun `detects overlay host in rendered html`() {
        assertTrue(
                ThemeHatToolbarClickPolicy.overlayHostPresentInHtml(
                        """<div class="topic_hat_fixed post_container top_hat_overlay_host close"><div class="hat_content close"><div class="post_body">Hat</div></div></div>"""
                )
        )
        assertFalse(ThemeHatToolbarClickPolicy.overlayHostPresentInHtml("<div class=\"posts_list\"></div>"))
    }

    @Test
    fun `shouldToggleOverlayViaJs requires hat body in overlay host slice`() {
        assertTrue(
                ThemeHatToolbarClickPolicy.shouldToggleOverlayViaJs(
                        """<div class="topic_hat_fixed post_container top_hat_overlay_host close"><div class="hat_content close"><div class="post_body">Hat</div></div></div>"""
                )
        )
        assertFalse(
                ThemeHatToolbarClickPolicy.shouldToggleOverlayViaJs(
                        """<div class="topic_hat_fixed post_container top_hat_overlay_host close"></div>"""
                )
        )
        assertTrue(
                ThemeHatToolbarClickPolicy.shouldToggleOverlayViaJs(
                        """<div class="topic_hat_fixed post_container top_hat_overlay_host close" data-post-id="135617646"><div class="hat_content close"></div></div>"""
                )
        )
    }
}
