package forpdateam.ru.forpda.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class EditPostBodyNormalizeTest {

    @Test
    fun mergeEditPostMessage_serverWinsWhenDomHasSpuriousQuote() {
        val server = "Обычный текст поста."
        val dom = "[quote]Обычный текст поста.[/quote]"
        assertEquals(server, mergeEditPostMessage(server, dom))
    }

    @Test
    fun mergeEditPostMessage_serverWinsWhenDomWrapsServerBbcodeInSpuriousQuote() {
        val server = "[b]Привет[/b] мир"
        val dom = "[quote][b]Привет[/b] мир[/quote]"
        assertEquals(server, mergeEditPostMessage(server, dom))
    }

    @Test
    fun plainTextInAsciiQuotes_notWrappedAsBbcodeQuote() {
        assertEquals("\"Просто фраза в кавычках\"", normalizeEditPostBodyForEditor("\"Просто фраза в кавычках\""))
    }

    @Test
    fun ipbLiteralWithTrailingDigits_becomesBbcodeQuote() {
        val out = normalizeEditPostBodyForEditor("\"nick, \nтекст ответа\"42")
        assertTrue(out.startsWith("[quote]"))
        assertTrue(out.contains("текст ответа"))
        assertTrue(out.contains("[/quote]"))
    }

    @Test
    fun commaNewlineInsideQuotedLiteral_becomesBbcodeQuote() {
        val out = normalizeEditPostBodyForEditor("\"SomeUser, \nЦитата без цифр в конце\"")
        assertTrue(out.startsWith("[quote]"))
    }

    @Test
    fun normalizeEditor_stripsIpbEditFooterAfterClosingBbcode() {
        val raw = "[img]https://4pda.to/s/x.jpg[/img]Сообщение отредактировал toxa246 - Сегодня, 11:29\nПричина редактирования: дополнение"
        val out = normalizeEditPostBodyForEditor(raw)
        assertFalse(out.contains("отредактировал", ignoreCase = true))
        assertFalse(out.contains("Причина", ignoreCase = true))
        assertTrue(out.contains("[img]https://4pda.to/s/x.jpg[/img]"))
    }

    @Test
    fun normalizeDomHtml_stripsEditNoticeParagraphs() {
        val html = "<p>Привет</p><p class=\"ipsType_light\">Сообщение отредактировал @nick - Сегодня</p><p>Причина редактирования: дополнение</p>"
        val out = normalizeEditPostBodyFromDomHtml(html)
        assertFalse(out.contains("отредактировал", ignoreCase = true))
        assertTrue(out.contains("Привет"))
    }

    @Test
    fun normalizeDomHtml_stripsEditNoticeInIpsSpan() {
        val html = "<p>Текст</p><span class=\"ipsType_light\">Сообщение отредактировал nick</span>"
        val out = normalizeEditPostBodyFromDomHtml(html)
        assertFalse(out.contains("отредактировал", ignoreCase = true))
        assertTrue(out.contains("Текст"))
    }

    @Test
    fun normalizeEditor_stripsEnglishEditFooterLines() {
        val raw = "Body text\nMessage edited by user - today\nReason for edit: typo"
        val out = normalizeEditPostBodyForEditor(raw)
        assertFalse(out.contains("edited", ignoreCase = true))
        assertFalse(out.contains("Reason", ignoreCase = true))
        assertTrue(out.contains("Body text"))
    }

    @Test
    fun normalizeEditor_stripsEditFooterWhenBbcodeOnlyNoHtmlTags() {
        // Как фрагмент без <p>/<div> в onQuotePostClick — должен обрабатываться через normalizeEditPostBodyForEditor.
        val raw = "Ответ пользователю.\nСообщение отредактировал nick - сегодня\nПричина редактирования: тест"
        val out = normalizeEditPostBodyForEditor(raw)
        assertFalse(out.contains("отредактировал", ignoreCase = true))
        assertTrue(out.contains("Ответ пользователю"))
    }

    @Test
    fun normalizeDomHtml_spoilerWithImg_becomesSpoilerBbcodeWithImg() {
        val html = """
            <div class="post-block spoil open">
            <div class="block-title">Спойлер</div>
            <div class="block-body"><img src="https://4pda.to/s/test.gif"/>Lenox30,</div>
            </div>
        """.trimIndent()
        val out = normalizeEditPostBodyFromDomHtml(html)
        assertTrue(out.contains("[spoiler]", ignoreCase = true))
        assertTrue(out.contains("[/spoiler]", ignoreCase = true))
        assertTrue(out.contains("[img]https://4pda.to/s/test.gif[/img]", ignoreCase = true))
        assertTrue(out.contains("Lenox30"))
    }
}
