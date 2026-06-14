package forpdateam.ru.forpda.presentation.theme

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TopicToolbarNavigationPolicyTest {

    @Test
    fun `single tab hides topic toolbar back`() {
        assertFalse(shouldShowTopicToolbarBack(tabCount = 1, isMenuTab = false))
    }

    @Test
    fun `menu tab hides topic toolbar back`() {
        assertFalse(shouldShowTopicToolbarBack(tabCount = 3, isMenuTab = true))
    }

    @Test
    fun `multi tab stack shows topic toolbar back`() {
        assertTrue(shouldShowTopicToolbarBack(tabCount = 2, isMenuTab = false))
    }

    @Test
    fun `single topic with parent shows topic toolbar back`() {
        assertTrue(shouldShowTopicToolbarBack(tabCount = 1, isMenuTab = false, hasParent = true))
    }

    @Test
    fun `single topic in theme chain shows topic toolbar back`() {
        assertTrue(shouldShowTopicToolbarBack(tabCount = 1, isMenuTab = false, canCloseThemeChain = true))
    }
}
