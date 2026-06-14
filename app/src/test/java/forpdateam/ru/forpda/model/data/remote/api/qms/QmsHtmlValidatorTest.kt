package forpdateam.ru.forpda.model.data.remote.api.qms

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QmsHtmlValidatorTest {

    @Test
    fun `valid QMS thread is classified as QMS_THREAD`() {
        val html = """
            <html><body act="qms">
            <div class="mess_list"></div>
            <div class="list-group-item" data-message-id="1" data-unread-status="0">
            <b>user</b> 12:00 <img src="/a.png"/>
            <div class="msg-content">Hello world</div></div>
            <input name="mid" value="5"/><input name="t" value="9"/>
            </body></html>
        """.trimIndent()
        assertEquals(QmsHtmlValidator.PageKind.QMS_THREAD, QmsHtmlValidator.classify(200, html))
    }

    @Test
    fun `login page is detected and rejected`() {
        val html = """<form class="loginform"><input name="password" type="password"/></form>"""
        assertEquals(QmsHtmlValidator.PageKind.LOGIN, QmsHtmlValidator.classify(200, html))
        assertTrue(QmsHtmlValidator.looksLikeLoginPage(html))
    }

    @Test
    fun `captcha page is detected before login heuristics`() {
        val html = """
            <form class="loginform">
            <div class="captcha-image"></div>
            <input name="captcha-time" value="123"/>
            captcha challenge
            </form>
        """.trimIndent()
        assertEquals(QmsHtmlValidator.PageKind.CAPTCHA, QmsHtmlValidator.classify(200, html))
        assertTrue(QmsHtmlValidator.looksLikeCaptcha(html))
    }

    @Test
    fun `QMS thread with act auth nav link is not login`() {
        val html = """
            <a href="index.php?act=auth">Вход</a>
            <div class="mess_list"></div>
            <div class="list-group-item" data-message-id="1">
            <div class="msg-content">Hi</div></div>
            <input name="mid" value="1"/><input name="t" value="2"/>
        """.trimIndent()
        assertEquals(QmsHtmlValidator.PageKind.QMS_THREAD, QmsHtmlValidator.classify(200, html))
        assertFalse(QmsHtmlValidator.looksLikeLoginPage(html))
    }

    @Test
    fun `error page is detected`() {
        val html = """<div class="errors-list">страница не найдена</div>"""
        assertEquals(QmsHtmlValidator.PageKind.ERROR, QmsHtmlValidator.classify(200, html))
    }

    @Test
    fun `HTTP 200 invalid HTML is UNKNOWN not QMS thread`() {
        val html = "<html><body>nothing here</body></html>"
        assertEquals(QmsHtmlValidator.PageKind.UNKNOWN, QmsHtmlValidator.classify(200, html))
        assertFalse(QmsHtmlValidator.responseLooksLikeQms(html))
    }

    @Test
    fun `empty thread has root but no message markers`() {
        val html = """
            <div class="mess_list"></div>
            <input name="mid" value="1"/><input name="t" value="2"/>
            act=qms thread-inside-bottom
        """.trimIndent()
        assertEquals(QmsHtmlValidator.PageKind.QMS_EMPTY_THREAD, QmsHtmlValidator.classify(200, html))
        val signals = QmsHtmlValidator.measureThread(html)
        assertTrue(signals.parserRootFound)
        assertEquals(0, signals.messageMarkers)
    }

    @Test
    fun `system notification thread with msg-content but no markers is not empty`() {
        val html = """
            <div class="nav"><b><a>Сообщения 4PDA</a>:</b><span>Оповещения</span></div>
            <div class="mess_list">
            <div class="list-group-item"><b></b> 09:00
            <div class="msg-content">Тема перемещена в раздел Android</div></div>
            </div>
            <textarea name="message"></textarea>
            <input name="mid" value="0"/><input name="t" value="734"/>
            act=qms thread-inside-bottom
        """.trimIndent()
        assertEquals(QmsHtmlValidator.PageKind.QMS_THREAD, QmsHtmlValidator.classify(200, html))
        assertFalse(QmsHtmlValidator.looksLikeEmptyThread(html))
        val signals = QmsHtmlValidator.measureThread(html)
        assertEquals(0, signals.messageMarkers)
        assertEquals(0, signals.containerMessageMarkers)
        assertEquals(1, signals.contentMarkers)
    }

    @Test
    fun `bare thread root without editor is not classified empty`() {
        val html = """
            <div class="mess_list"></div>
            <input name="mid" value="1"/><input name="t" value="2"/>
            act=qms
        """.trimIndent()
        assertEquals(QmsHtmlValidator.PageKind.QMS_THREAD, QmsHtmlValidator.classify(200, html))
        assertFalse(QmsHtmlValidator.looksLikeEmptyThread(html))
    }

    @Test
    fun `mess_container thread is QMS_THREAD`() {
        val html = """
            <div class="mess_list">
            <div class="mess_container our" data-mess-id="42">
            <div class="mess"><div class="content">Hi</div></div>
            <div class="time"><span>12:00</span></div></div>
            </div>
            <input name="mid" value="1"/><input name="t" value="2"/>
        """.trimIndent()
        assertEquals(QmsHtmlValidator.PageKind.QMS_THREAD, QmsHtmlValidator.classify(200, html))
        assertEquals(1, QmsHtmlValidator.measureThread(html).containerMessageMarkers)
    }

    @Test
    fun `parser selector missing logs diagnostic via measureThread`() {
        val html = "<html>act=qms only</html>"
        val signals = QmsHtmlValidator.measureThread(html)
        assertFalse(signals.parserRootFound)
        assertEquals(0, signals.messageMarkers)
    }
}
