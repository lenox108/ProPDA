package forpdateam.ru.forpda.common.bbcode

import org.junit.Assert.assertEquals
import org.junit.Test

class BbcodeWrapTest {

    @Test
    fun wrap_nonEmptySelection_keepsSelectionInsideTags() {
        val text = "hello world"
        val res = BbcodeWrap.wrap(text, 0, 5, "[b]", "[/b]")
        assertEquals("[b]hello[/b] world", res.text)
        assertEquals(3, res.selectionStart)
        assertEquals(8, res.selectionEnd)
    }

    @Test
    fun wrap_reversedSelection_isHandled() {
        val text = "hello"
        val res = BbcodeWrap.wrap(text, 5, 0, "[i]", "[/i]")
        assertEquals("[i]hello[/i]", res.text)
        assertEquals(3, res.selectionStart)
        assertEquals(8, res.selectionEnd)
    }

    @Test
    fun wrap_emptySelection_placesCursorInsideByDefault() {
        val text = "abc"
        val res = BbcodeWrap.wrap(text, 1, 1, "[u]", "[/u]")
        assertEquals("a[u][/u]bc", res.text)
        assertEquals(4, res.selectionStart)
        assertEquals(4, res.selectionEnd)
    }

    @Test
    fun wrap_emptySelection_cursorAfterClose_whenRequested() {
        val text = "abc"
        val res = BbcodeWrap.wrap(text, 1, 1, "[code]", "[/code]", placeCursorInsideIfEmpty = false)
        assertEquals("a[code][/code]bc", res.text)
        assertEquals("a[code]".length + "[/code]".length, res.selectionStart)
        assertEquals(res.selectionStart, res.selectionEnd)
    }
}

