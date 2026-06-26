package forpdateam.ru.forpda.presentation.theme

import forpdateam.ru.forpda.common.DayNightHelper
import forpdateam.ru.forpda.model.preferences.MainPreferencesHolder
import forpdateam.ru.forpda.ui.TemplateCssComposer
import forpdateam.ru.forpda.ui.TemplatePaletteResolver
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Test

class ThemeRenderSessionTest {

    @Test
    fun create_mirrorsPageAndControllerFields() {
        val page = mockk<forpdateam.ru.forpda.entity.remote.theme.ThemePage>(relaxed = true)
        every { page.id } returns 42
        every { page.pagination.current } returns 3
        every { page.renderGenerationId } returns 7
        every { page.renderSignature } returns "sig-1"

        val session = ThemeRenderSession.create(
                page = page,
                bridgeToken = "token-abc",
        )
        assertEquals(42, session.topicId)
        assertEquals(3, session.page)
        assertEquals(7, session.renderGenerationId)
        assertEquals("token-abc", session.bridgeToken)
        assertEquals("sig-1", session.themeSignature)
    }
}
