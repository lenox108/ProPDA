package forpdateam.ru.forpda.presentation.theme

import forpdateam.ru.forpda.common.Preferences
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TopicToolbarBehaviorPolicyTest {

    @Test
    fun `pinned toolbar does not auto hide`() {
        assertFalse(isToolbarAutoHideEnabled(Preferences.Main.TopicToolbarBehavior.PINNED))
    }

    @Test
    fun `hide on scroll toolbar auto hides`() {
        assertTrue(isToolbarAutoHideEnabled(Preferences.Main.TopicToolbarBehavior.HIDE_ON_SCROLL))
    }
}
