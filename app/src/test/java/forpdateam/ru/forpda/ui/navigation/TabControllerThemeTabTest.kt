package forpdateam.ru.forpda.ui.navigation

import forpdateam.ru.forpda.presentation.Screen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TabControllerThemeTabTest {

    @Test
    fun findThemeTab_returnsHiddenThemeTabRegardlessOfIsAloneFlagOnStoredTab() {
        val controller = TabController()
        val favoritesTag = "Tab_fav"
        val themeTag = "Tab_theme"
        controller.addNew(favoritesTag, Screen.Favorites())
        controller.addNew(themeTag, Screen.Theme().apply {
            themeUrl = "https://4pda.to/forum/index.php?showtopic=111"
        })
        controller.setCurrent(favoritesTag)

        val found = controller.findThemeTab()
        assertEquals(themeTag, found?.tag)
    }

    @Test
    fun findThemeTab_returnsNullWhenNoThemeInTree() {
        val controller = TabController()
        controller.addNew("Tab_fav", Screen.Favorites())
        assertNull(controller.findThemeTab())
    }
}
