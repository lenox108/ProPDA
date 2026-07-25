package forpdateam.ru.forpda.ui.views

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EditorHistoryTest {

    @Test
    fun `consecutive typing is one undo operation`() {
        val history = EditorHistory(maxOperations = 20, maxChars = 100)
        history.record(0, "", "a")
        history.record(1, "", "b")
        history.record(2, "", "c")

        assertEquals(EditorEdit(0, "", "abc"), history.takeUndo())
        assertFalse(history.canUndo)
        assertTrue(history.canRedo)
    }

    @Test
    fun `consecutive backspace restores text in original order`() {
        val history = EditorHistory(maxOperations = 20, maxChars = 100)
        history.record(2, "c", "")
        history.record(1, "b", "")
        history.record(0, "a", "")

        assertEquals(EditorEdit(0, "abc", ""), history.takeUndo())
    }

    @Test
    fun `new edit invalidates redo`() {
        val history = EditorHistory(maxOperations = 20, maxChars = 100)
        history.record(0, "", "a")
        history.takeUndo()
        history.record(0, "", "b")

        assertFalse(history.canRedo)
    }
}
