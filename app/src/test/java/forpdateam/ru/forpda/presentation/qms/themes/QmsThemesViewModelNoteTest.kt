package forpdateam.ru.forpda.presentation.qms.themes

import org.junit.Assert.assertEquals
import org.junit.Test

class QmsThemesViewModelNoteTest {

    @Test
    fun themeNoteUrl_usesThemeIdNotUserId() {
        assertEquals(
                "https://4pda.to/forum/index.php?act=qms&mid=42&t=9001",
                QmsThemesViewModel.themeNoteUrl(userId = 42, themeId = 9001)
        )
    }
}
