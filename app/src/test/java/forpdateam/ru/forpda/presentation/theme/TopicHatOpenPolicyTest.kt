package forpdateam.ru.forpda.presentation.theme

import forpdateam.ru.forpda.entity.remote.theme.ThemePage
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TopicHatOpenPolicyTest {

    @Test
    fun `overlay stays closed on ordinary topic open`() {
        val page = ThemePage().apply { isHatOpen = true }
        TopicHatOpenPolicy.prepareOverlayStateForRender(
                page = page,
                userHatOpenOverride = null,
                pendingToolbarOverlayOpen = false,
        )
        assertFalse(page.isHatOpen)
        assertFalse(
                TopicHatOpenPolicy.overlayExpandedForRender(
                        page = page,
                        userHatOpenOverride = null,
                        pendingToolbarOverlayOpen = false,
                )
        )
    }

    @Test
    fun `overlay stays closed in html even after explicit toolbar request`() {
        val page = ThemePage()
        TopicHatOpenPolicy.prepareOverlayStateForRender(
                page = page,
                userHatOpenOverride = true,
                pendingToolbarOverlayOpen = true,
        )
        assertFalse(page.isHatOpen)
        assertTrue(
                TopicHatOpenPolicy.shouldDispatchOverlayOpenAfterRender(
                        userHatOpenOverride = true,
                        pendingToolbarOverlayOpen = true,
                )
        )
    }

    @Test
    fun `pending render without user override does not auto open overlay`() {
        assertFalse(
                TopicHatOpenPolicy.shouldDispatchOverlayOpenAfterRender(
                        userHatOpenOverride = null,
                        pendingToolbarOverlayOpen = true,
                )
        )
        val page = ThemePage().apply { isHatOpen = true }
        TopicHatOpenPolicy.prepareOverlayStateForRender(
                page = page,
                userHatOpenOverride = null,
                pendingToolbarOverlayOpen = true,
        )
        assertFalse(page.isHatOpen)
    }
}
