package forpdateam.ru.forpda.ui.views.messagepanel

import org.junit.Assert.assertEquals
import org.junit.Test

class EditorSessionStoreTest {

    @Test
    fun `compact and fullscreen state use one normalized snapshot`() {
        val store = EditorSessionStore()

        val full = store.replace("message", selectionStart = 99, selectionEnd = -1, mode = "full")
        val compact = store.updateSelection(start = 6, end = 2, mode = "compact")

        assertEquals(0, full.selectionStart)
        assertEquals(7, full.selectionEnd)
        assertEquals("message", compact.message)
        assertEquals(2, compact.selectionStart)
        assertEquals(6, compact.selectionEnd)
        assertEquals("compact", compact.mode)
        assertEquals(2L, compact.revision)
    }
}
