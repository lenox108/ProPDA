package forpdateam.ru.forpda.ui.navigation

import forpdateam.ru.forpda.presentation.Screen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TabControllerQmsChatTabTest {

    @Test
    fun findAlone_findsExistingQmsChatTabForAnotherDialog() {
        val controller = TabController()
        val themesTag = "Tab_themes"
        val chatTag = "Tab_qms"
        controller.addNew(themesTag, Screen.QmsThemes().apply { userId = 42 })
        controller.addNew(chatTag, Screen.QmsChat().apply {
            userId = 42
            themeId = 100
        })
        controller.setCurrent(themesTag)

        val found = controller.findAlone(Screen.QmsChat().apply {
            userId = 42
            themeId = 200
        })

        assertNotNull(found)
        assertEquals(chatTag, found?.tag)
    }

    @Test
    fun qmsChatScreen_isAloneForTabReuse() {
        assertTrue(Screen.QmsChat().isAlone)
    }
}
