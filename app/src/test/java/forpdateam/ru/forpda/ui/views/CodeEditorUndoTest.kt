package forpdateam.ru.forpda.ui.views

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class CodeEditorUndoTest {

    @Test
    fun `typing undo and redo restore caret`() {
        val editor = CodeEditor(ApplicationProvider.getApplicationContext())
        editor.setText("abc")
        editor.setSelection(3)
        editor.clearUndoHistory()

        editor.text!!.insert(3, "d")
        editor.undo()

        assertEquals("abc", editor.text.toString())
        assertEquals(3, editor.selectionStart)

        editor.redo()

        assertEquals("abcd", editor.text.toString())
        assertEquals(4, editor.selectionStart)
    }

    @Test
    fun `bbcode replacement is one undo operation`() {
        val editor = CodeEditor(ApplicationProvider.getApplicationContext())
        editor.setText("a text z")
        editor.setSelection(2, 6)
        editor.clearUndoHistory()

        editor.replaceRangeAtomically(
            start = 2,
            end = 6,
            replacement = "[b]text[/b]",
            targetSelectionStart = 5,
            targetSelectionEnd = 9,
        )
        editor.undo()

        assertEquals("a text z", editor.text.toString())
        assertEquals(2, editor.selectionStart)
        assertEquals(6, editor.selectionEnd)
        editor.redo()
        assertEquals("a [b]text[/b] z", editor.text.toString())
        assertEquals(5, editor.selectionStart)
        assertEquals(9, editor.selectionEnd)
    }
}
