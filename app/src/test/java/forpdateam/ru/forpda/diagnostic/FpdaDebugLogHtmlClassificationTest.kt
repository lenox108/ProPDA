package forpdateam.ru.forpda.diagnostic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FpdaDebugLogHtmlClassificationTest {

    @Test
    fun `classifyHtml empty`() {
        val fields = FpdaDebugLog.classifyHtml("")
        assertEquals(0, fields["htmlLen"])
        assertEquals("empty", fields["htmlHash"])
    }

    @Test
    fun `classifyHtml does not include raw markup`() {
        val html = "<html><body><form action='/login'><div class='comment-list'>secret</div></body></html>"
        val fields = FpdaDebugLog.classifyHtml(html)
        assertEquals(html.length, fields["htmlLen"])
        assertFalse(fields.values.any { it is String && it.contains("secret") })
        assertTrue(fields["hasForm"] as Boolean)
        assertTrue(fields["hasCommentList"] as Boolean)
    }

    @Test
    fun `classifyHtml hash changes with content`() {
        val a = FpdaDebugLog.classifyHtml("<p>one</p>")["htmlHash"]
        val b = FpdaDebugLog.classifyHtml("<p>two</p>")["htmlHash"]
        assertNotEquals(a, b)
    }
}
