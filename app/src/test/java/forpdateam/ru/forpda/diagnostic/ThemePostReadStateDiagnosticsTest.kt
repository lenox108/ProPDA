package forpdateam.ru.forpda.diagnostic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemePostReadStateDiagnosticsTest {

    @Test
    fun `parseLastPostFromScrollReason extracts post id`() {
        val reason = "initial_anchor|y=120|max=4800|lastPost=987654"
        assertEquals("987654", ThemePostReadStateDiagnostics.parseLastPostFromScrollReason(reason))
    }

    @Test
    fun `parseLastPostFromScrollReason empty lastPost`() {
        val reason = "initial_anchor|y=0|max=100|lastPost="
        assertNull(ThemePostReadStateDiagnostics.parseLastPostFromScrollReason(reason))
    }

    @Test
    fun `normalizePostId strips entry prefix`() {
        assertEquals("42", ThemePostReadStateDiagnostics.normalizePostId("entry42"))
        assertNull(ThemePostReadStateDiagnostics.normalizePostId("  "))
    }

    @Test
    fun `formatPostIdList sorted distinct`() {
        val formatted = ThemePostReadStateDiagnostics.formatPostIdList(listOf(30, 10, 10, 20))
        assertEquals("10,20,30", formatted)
        assertEquals("none", ThemePostReadStateDiagnostics.formatPostIdList(emptyList()))
    }

    @Test
    fun `anchor match when ids equal after normalization`() {
        val anchor = ThemePostReadStateDiagnostics.normalizePostId("entry42")
        val final = ThemePostReadStateDiagnostics.normalizePostId("42")
        assertTrue(anchor == final)
    }
}
