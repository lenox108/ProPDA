package forpdateam.ru.forpda.presentation.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeScrollCommandTest {

    // Bug A regression guard: "В конец темы" must emit a BOTTOM scroll command that
    // executeThemeScrollCommand() in theme.js dispatches to scrollToThemeBottomWithRetries().
    @Test
    fun bottom_hasExpectedKind() {
        val command = ThemeScrollCommand.bottom()
        assertEquals(ThemeScrollCommand.Kind.BOTTOM, command.kind)
        assertTrue(command.commandId.isNotBlank())
    }

    // Bug B regression guard: bottom-mode refresh restore must carry mode so the JS restore
    // targets the bottom instead of falling back to page top.
    @Test
    fun refreshRestore_bottomMode_carriesMode() {
        val command = ThemeScrollCommand.refreshRestore("restore-bottom", "BOTTOM")
        assertEquals(ThemeScrollCommand.Kind.REFRESH_RESTORE, command.kind)
        assertEquals("restore-bottom", command.restoreId)
        assertEquals("BOTTOM", command.restoreMode)
    }

    @Test
    fun refreshRestore_hasExpectedFields() {
        val command = ThemeScrollCommand.refreshRestore("restore1", "ANCHOR")
        assertEquals(ThemeScrollCommand.Kind.REFRESH_RESTORE, command.kind)
        assertEquals("restore1", command.restoreId)
        assertEquals("ANCHOR", command.restoreMode)
        assertTrue(command.commandId.isNotBlank())
    }

    @Test
    fun anchor_hasExpectedFields() {
        val command = ThemeScrollCommand.anchor("12345")
        assertEquals(ThemeScrollCommand.Kind.ANCHOR, command.kind)
        assertEquals("12345", command.anchorPostId)
    }

    @Test
    fun initialAnchor_hasExpectedKind() {
        val command = ThemeScrollCommand.initialAnchor()
        assertEquals(ThemeScrollCommand.Kind.INITIAL_ANCHOR, command.kind)
        assertTrue(command.commandId.isNotBlank())
    }

    // P0-1: deferred smart-end scroll must stay BOTTOM so flushPendingScrollCommand can replay it
    // after the same render cycle; ThemeViewModel.clearPendingScrollCommand() clears it on loadData.
    @Test
    fun bottomCommand_isReplayablePendingKind() {
        val first = ThemeScrollCommand.bottom()
        val second = ThemeScrollCommand.bottom()
        assertEquals(ThemeScrollCommand.Kind.BOTTOM, first.kind)
        assertEquals(ThemeScrollCommand.Kind.BOTTOM, second.kind)
        assertTrue(first.commandId != second.commandId)
    }

    @Test
    fun endAnchorOrBottom_carriesResolvedLastPostId() {
        val command = ThemeScrollCommand.endAnchorOrBottom("143765001")

        assertEquals(ThemeScrollCommand.Kind.END_ANCHOR_OR_BOTTOM, command.kind)
        assertEquals("143765001", command.anchorPostId)
        assertTrue(command.commandId.isNotBlank())
    }
}
