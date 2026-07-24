package forpdateam.ru.forpda.ui.fragments.theme.nativerender

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LinkActionsMenuTest {

    @Test
    fun `relative quote source is resolved before browser action`() {
        assertEquals(
                "https://4pda.to/forum/index.php?act=findpost&pid=456",
                LinkActionsMenu.resolveForActions("/forum/index.php?act=findpost&pid=456"),
        )
    }

    @Test
    fun `absolute link is preserved`() {
        val url = "https://4pda.to/forum/index.php?showtopic=123"
        assertEquals(url, LinkActionsMenu.resolveForActions(url))
    }
}
