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
        history.record(2, "c", "", beforeSelectionStart = 3, afterSelectionStart = 2)
        history.record(1, "b", "", beforeSelectionStart = 2, afterSelectionStart = 1)
        history.record(0, "a", "", beforeSelectionStart = 1, afterSelectionStart = 0)

        assertEquals(
            EditorEdit(
                start = 0,
                before = "abc",
                after = "",
                beforeSelectionStart = 3,
                beforeSelectionEnd = 3,
                afterSelectionStart = 0,
                afterSelectionEnd = 0,
            ),
            history.takeUndo(),
        )
    }

    @Test
    fun `new edit invalidates redo`() {
        val history = EditorHistory(maxOperations = 20, maxChars = 100)
        history.record(0, "", "a")
        history.takeUndo()
        history.record(0, "", "b")

        assertFalse(history.canRedo)
    }

    @Test
    fun `typing after grouping timeout creates another undo operation`() {
        val history = EditorHistory(maxOperations = 20, maxChars = 100)
        history.record(0, "", "a", recordedAtMillis = 1_000)
        history.record(1, "", "b", recordedAtMillis = 2_001)

        assertEquals(EditorEdit(1, "", "b"), history.takeUndo())
        assertEquals(EditorEdit(0, "", "a"), history.takeUndo())
    }

    @Test
    fun `atomic replacement keeps selections for undo and redo`() {
        val history = EditorHistory(maxOperations = 20, maxChars = 100)
        history.record(
            start = 2,
            before = "text",
            after = "[b]text[/b]",
            beforeSelectionStart = 2,
            beforeSelectionEnd = 6,
            afterSelectionStart = 5,
            afterSelectionEnd = 9,
        )

        val operation = history.takeUndo()
        assertEquals(2, operation?.beforeSelectionStart)
        assertEquals(6, operation?.beforeSelectionEnd)
        assertEquals(5, history.takeRedo()?.afterSelectionStart)
    }
}
