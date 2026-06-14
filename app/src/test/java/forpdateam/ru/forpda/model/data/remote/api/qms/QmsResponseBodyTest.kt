package forpdateam.ru.forpda.model.data.remote.api.qms

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QmsResponseBodyTest {

    @Test
    fun `unwraps JSON html field`() {
        val html = """
            <html act=qms>
            <div class="mess_list">
            <div class="mess_container our" data-mess-id="1">
            <div class="mess"><div class="content">Hi</div></div>
            <div class="time"><span>12:00</span></div></div>
            </div>
            <input name="mid" value="1"/><input name="t" value="2"/>
            </html>
        """.trimIndent()
        val json = buildJsonObject {
            put("status", "ok")
            put("html", html)
        }.toString()
        val normalized = QmsResponseBody.normalize(json)
        assertFalse(QmsResponseBody.looksLikeJsonEnvelope(normalized))
        assertTrue(normalized.contains("mess_container"))
        assertEquals(QmsHtmlValidator.PageKind.QMS_THREAD, QmsHtmlValidator.classify(200, normalized))
    }

    @Test
    fun `unwraps nested payload body field`() {
        val html = """
            <div class="mess_list">
            <div class="mess_container our" data-mess-id="9">
            <div class="mess"><div class="content">Nested</div></div>
            </div>
            <input name="mid" value="1"/><input name="t" value="2"/>
            </div>
        """.trimIndent()
        val json = buildJsonObject {
            put("status", "ok")
            put("payload", buildJsonObject { put("body", html) })
        }.toString()
        val normalized = QmsResponseBody.normalize(json)
        assertTrue(normalized.contains("mess_container"))
        assertEquals(QmsHtmlValidator.PageKind.QMS_THREAD, QmsHtmlValidator.classify(200, normalized))
    }

    @Test
    fun `raw HTML passes through unchanged`() {
        val html = "<div class=\"mess_list\"></div><input name=\"mid\" value=\"1\"/>"
        assertEquals(html, QmsResponseBody.normalize(html))
    }
}
