package forpdateam.ru.forpda.ui.navigation

import forpdateam.ru.forpda.presentation.Screen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Documents the alone-theme tab policy: [Screen.Theme.isAlone] reuses one tab app-wide;
 * a fresh list open must reload URL/hints, not only show the previous topic WebView.
 */
class TabNavigatorThemeSwitchPolicyTest {

    @Test
    fun freshOpenFromList_mustReloadAloneThemeTab() {
        assertTrue(
                TabNavigatorThemeSwitchPolicy.mustReloadAloneThemeOnNavigation(
                        openIntent = forpdateam.ru.forpda.presentation.theme.TopicOpenIntentClassifier.FRESH_FORUM
                )
        )
    }

    @Test
    fun explicitPostOpen_mustReloadAloneThemeTab() {
        assertTrue(TabNavigatorThemeSwitchPolicy.mustReloadAloneThemeOnNavigation(openIntent = "explicit_post"))
    }

    @Test
    fun backRestore_mustNotForceReloadAloneThemeTab() {
        assertFalse(TabNavigatorThemeSwitchPolicy.mustReloadAloneThemeOnNavigation(openIntent = "back_restore"))
    }

    @Test
    fun crossTopicFreshOpen_detectsTopicChange() {
        assertTrue(
                TabNavigatorThemeSwitchPolicy.isCrossTopicFreshOpen(
                        targetTopicId = 456,
                        openTopicId = 123,
                        openIntent = forpdateam.ru.forpda.presentation.theme.TopicOpenIntentClassifier.FRESH_LEGACY
                )
        )
        assertFalse(
                TabNavigatorThemeSwitchPolicy.isCrossTopicFreshOpen(
                        targetTopicId = 123,
                        openTopicId = 123,
                        openIntent = forpdateam.ru.forpda.presentation.theme.TopicOpenIntentClassifier.FRESH_LEGACY
                )
        )
    }

    @Test
    fun themeScreen_isAloneSoListReuseMustReload() {
        val screen = Screen.Theme().apply {
            topicOpenIntent = forpdateam.ru.forpda.presentation.theme.TopicOpenIntentClassifier.FRESH_FORUM
        }
        assertTrue(screen.isAlone)
        assertTrue(TabNavigatorThemeSwitchPolicy.mustReloadAloneThemeOnNavigation(screen.topicOpenIntent))
    }

    @Test
    fun listHintsFromThemeScreen_passesUnreadFieldsForReusePath() {
        val screen = Screen.Theme().apply {
            topicOpenSource = "favorites"
            unreadUrlFromList = "https://4pda.to/forum/index.php?showtopic=1&view=getnewpost"
            unreadPostIdFromList = 555
        }
        val hints = TabNavigatorThemeSwitchPolicy.listHintsFromThemeScreen(screen)
        assertEquals("favorites", screen.topicOpenSource)
        assertEquals(screen.unreadUrlFromList, hints.unreadUrlFromList)
        assertEquals(555, hints.unreadPostIdFromList)
    }

    @Test
    fun listHintsFromThemeScreen_omitsZeroPostId() {
        val screen = Screen.Theme().apply {
            unreadPostIdFromList = 0
        }
        assertNull(TabNavigatorThemeSwitchPolicy.listHintsFromThemeScreen(screen).unreadPostIdFromList)
    }
}
