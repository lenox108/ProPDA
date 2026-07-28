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
    fun mergeEditPostMessage_domWinsWhenItOnlyRestoresMissingSnapback() {
        val server = "[b]Nick,[/b] текст ответа"
        val dom = "[snapback]143448230[/snapback] [b]Nick,[/b] текст ответа"

        assertEquals(dom, mergeEditPostMessage(server, dom))
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
    fun normalizeDomHtml_preservesFindpostSnapbackBeforeMention() {
        val html = """
            <a class="snapback post" title="Перейти к сообщению" href="index.php?showtopic=1&amp;view=findpost&amp;p=143448230">
                <b>Nick,</b>
            </a>
            текст ответа
        """.trimIndent()

        val out = normalizeEditPostBodyFromDomHtml(html)

        assertTrue(out.contains("[snapback]143448230[/snapback]", ignoreCase = true))
        assertTrue(out.contains("[b]Nick,[/b]", ignoreCase = true))
        assertTrue(out.contains("текст ответа"))
    }

    @Test
    fun normalizeDomHtml_preservesEntrySnapbackBeforeMention() {
        val html = """
            <a class="snapback post" href="#entry143448230"><span class="icon"></span><b>Nick,</b></a>
            text
        """.trimIndent()

        val out = normalizeEditPostBodyFromDomHtml(html)

        assertTrue(out.contains("[snapback]143448230[/snapback]", ignoreCase = true))
        assertTrue(out.contains("[b]Nick,[/b]", ignoreCase = true))
        assertTrue(out.contains("text"))
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

    @Test
    fun normalizeDomHtml_namedSpoilerKeepsItsTitle() {
        val html = """<div class="post-block spoil close"><div class="block-title" title="Открыть этот спойлер">скрины</div><div class="block-body">содержимое</div></div>"""
        assertTrue(normalizeEditPostBodyFromDomHtml(html).contains("[spoiler=скрины]содержимое[/spoiler]"))
    }

    @Test
    fun normalizeDomHtml_bracketsInSpoilerTitleDoNotBreakBbcode() {
        val html = """<div class="post-block spoil close"><div class="block-title">скрины [1]</div><div class="block-body">t</div></div>"""
        assertTrue(normalizeEditPostBodyFromDomHtml(html).contains("[spoiler=скрины (1)]"))
    }

    @Test
    fun normalizeDomHtml_stripsSmartQuoteToggleFromCollapsedQuote() {
        val html = """
            <div class="post-block quote smart-quote-collapsible smart-quote-collapsed">
                <div class="block-title">User @ 23.05.26, 10:00</div>
                <div class="block-body">Цитируемый текст</div>
                <div class="smart-quote-toggle" role="button">Развернуть цитату</div>
            </div>
            Ответ автора
        """.trimIndent()
        val out = normalizeEditPostBodyFromDomHtml(html)

        assertTrue(out.contains("""[quote name="User" date="23.05.26, 10:00"]Цитируемый текст[/quote]"""))
        assertTrue(out.contains("Ответ автора"))
        assertFalse(out.contains("Развернуть цитату"))
    }

    /** Ник/дата/номер поста из шапки цитаты не должны теряться: иначе при вставке скопированного
     *  BB-кода непонятно, кто это сказал. */
    @Test
    fun normalizeDomHtml_keepsQuoteAuthorDateAndPostId() {
        val html = """
            <div class="post-block quote">
                <div class="block-title">Sanitar1000 @ 27.07.26, 21:35
                    <a href="index.php?act=findpost&amp;pid=144410289"><img src="post_snapback.gif"></a>
                </div>
                <div class="block-body">поставил начисто</div>
            </div>
            тогда, ой!
        """.trimIndent()
        val out = normalizeEditPostBodyFromDomHtml(html)

        assertTrue(
                out,
                out.contains(
                        """[quote name="Sanitar1000" date="27.07.26, 21:35" post=144410289]поставил начисто[/quote]"""),
        )
    }

    /** Разметка живой темы 4pda (снята с поста 144307457): «@» между ником и датой приходит
     *  сущностью `&#064;`, а не символом — без её раскрытия весь заголовок уезжал в `name`.
     *  Свой `<br />` после блока уже есть, поэтому лишнюю пустую строку не добавляем. */
    @Test
    fun normalizeDomHtml_realForumQuoteMarkup() {
        val html = """<div class="post-block quote"><div class="block-title">OLEGA2007 &#064; 18.07.26,""" +
                """ 20:32 <a href="/forum/index.php?act=findpost&amp;pid=144307457" target="_blank"""" +
                """ title="Перейти к сообщению"><img src="https://4pda.to/s/x.gif" alt="*" border="0" /></a>""" +
                """</div><div class="block-body">Что можно ещё посоветовать ?<br /></div></div>""" +
                """<br />Можно ещё посмотреть OnePlus Nord CE5"""
        val out = normalizeEditPostBodyFromDomHtml(html)

        assertEquals(
                """[quote name="OLEGA2007" date="18.07.26, 20:32" post=144307457]""" +
                        "Что можно ещё посоветовать ?\n[/quote]\nМожно ещё посмотреть OnePlus Nord CE5",
                out,
        )
    }

    /** Текст после цитаты не должен слипаться с `[/quote]`: блочный `<div>` съедается заменой, и
     *  перевод строки надо восстановить явно. */
    @Test
    fun normalizeDomHtml_keepsLineBreakAfterQuoteBlock() {
        val html = """<div class="post-block quote"><div class="block-title">User</div>""" +
                """<div class="block-body">цитата</div></div>ответ"""
        val out = normalizeEditPostBodyFromDomHtml(html)

        assertTrue(out, out.contains("[/quote]\nответ"))
    }

    /** Безымянная цитата: «Цитата» — подпись блока, а не ник. */
    @Test
    fun normalizeDomHtml_defaultQuoteLabelIsNotTreatedAsAuthor() {
        val html = """<div class="post-block quote"><div class="block-title">Цитата</div>""" +
                """<div class="block-body">текст</div></div>"""
        val out = normalizeEditPostBodyFromDomHtml(html)

        assertTrue(out, out.contains("[quote]текст[/quote]"))
    }

    @Test
    fun normalizeDomHtml_stripsExpandedSmartQuoteToggleFromNestedQuotes() {
        val html = """
            <div class="post-block quote smart-quote-collapsible smart-quote-expanded">
                <div class="block-title">Outer</div>
                <div class="block-body">
                    Внешняя цитата
                    <div class="post-block quote smart-quote-collapsible smart-quote-collapsed">
                        <div class="block-title">Inner</div>
                        <div class="block-body">Вложенная цитата</div>
                        <div class="smart-quote-toggle" role="button">Развернуть цитату</div>
                    </div>
                </div>
                <div class="smart-quote-toggle" role="button">Свернуть цитату</div>
            </div>
        """.trimIndent()
        val out = normalizeEditPostBodyFromDomHtml(html)

        assertTrue(out, out.contains("""[quote name="Outer"]"""))
        assertTrue(out, out.contains("""[quote name="Inner"]"""))
        assertTrue(out.contains("Внешняя цитата"))
        assertTrue(out.contains("Вложенная цитата"))
        assertFalse(out.contains("Развернуть цитату"))
        assertFalse(out.contains("Свернуть цитату"))
    }

    @Test
    fun normalizeDomHtml_preservesUserWrittenSmartQuoteTextOutsideUi() {
        val html = """
            <p>Пользователь написал: Развернуть цитату</p>
            <div class="post-block quote smart-quote-collapsible smart-quote-collapsed">
                <div class="block-title">User</div>
                <div class="block-body">Цитата</div>
                <div class="smart-quote-toggle" role="button">Развернуть цитату</div>
            </div>
        """.trimIndent()
        val out = normalizeEditPostBodyFromDomHtml(html)

        assertTrue(out.contains("Пользователь написал: Развернуть цитату"))
        assertEquals(1, Regex("Развернуть цитату").findAll(out).count())
    }

    @Test
    fun decodeForumPostTextareaContent_decodesHtmlEntities() {
        val raw = "&lt;b&gt;привет&lt;/b&gt;"
        val out = decodeForumPostTextareaContent(raw)
        assertEquals("<b>привет</b>", out)
    }

    @Test
    fun decodeForumPostTextareaContent_decodesNumericEntities() {
        val raw = "Привет&#44; мир"
        val out = decodeForumPostTextareaContent(raw)
        assertEquals("Привет, мир", out)
    }

    @Test
    fun decodeForumPostTextareaContent_decodesHexEntities() {
        val raw = "Привет&#x2C; мир"
        val out = decodeForumPostTextareaContent(raw)
        assertEquals("Привет, мир", out)
    }

    @Test
    fun decodeForumPostTextareaContent_normalizesLineBreaks() {
        val raw = "строка1\r\nстрока2\rстрока3"
        val out = decodeForumPostTextareaContent(raw)
        assertEquals("строка1\nстрока2\nстрока3", out)
    }

    @Test
    fun decodeForumPostTextareaContent_decodesNamedEntities() {
        val raw = "&quot;текст&quot; &apos;кавычки&apos;"
        val out = decodeForumPostTextareaContent(raw)
        assertEquals("\"текст\" 'кавычки'", out)
    }

    @Test
    fun decodeForumPostTextareaContent_handlesAmpersandSequence() {
        val raw = "&amp;lt;"
        val out = decodeForumPostTextareaContent(raw)
        assertEquals("<", out)
    }

    @Test
    fun selectBestEditBodyCandidate_prefersQuote() {
        val candidates = listOf("обычный текст", "[quote]цитата[/quote]", "короткий")
        val out = selectBestEditBodyCandidate(candidates)
        assertEquals("[quote]цитата[/quote]", out)
    }

    @Test
    fun selectBestEditBodyCandidate_prefersMergedSectionOverShortQuote() {
        val full = "Первая часть\n[mergetime]12.05.2026, 05:43[/mergetime]\nДобавлено: вторая часть"
        val candidates = listOf("[quote]короткая цитата[/quote]", full)
        val out = selectBestEditBodyCandidate(candidates)
        assertEquals(full, out)
    }

    @Test
    fun selectBestEditBodyCandidate_prefersAddedSectionOverShortVisiblePostBody() {
        val visibleFirstPart = "[b]Тип:[/b] Другое\n[b]Версия:[/b] 2.7.6\n[b]Краткое описание:[/b] Исправленная"
        val full = """
            $visibleFirstPart

            Добавлено 13.05.2026, 23:06:
            [quote name="Wildhist" date="13.05.2026, 21:38" post=688]Если включить классическую навигацию[/quote]

            Это сделано, чтобы номера страниц не дублировались.
        """.trimIndent()
        val out = selectBestEditBodyCandidate(listOf(visibleFirstPart, full))
        assertEquals(full, out)
    }

    @Test
    fun mergeEditPostMessage_serverMergedSectionWinsOverDomPrefill() {
        val server = """
            [quote name="Ananas8" date="12.05.2026, 05:43" post=143349328]Первая часть[/quote]
            [mergetime]12.05.2026, 05:43[/mergetime]
            Добавлено: вторая часть
        """.trimIndent()
        val dom = "[quote name=\"Ananas8\"]Первая часть[/quote]"
        assertEquals(server, mergeEditPostMessage(server, dom))
    }

    @Test
    fun mergeEditPostMessage_domMergedSectionWinsWhenServerLooksTruncated() {
        val server = "[quote name=\"Ananas8\"]Первая часть[/quote]"
        val dom = "$server\nДобавлено: вторая часть"
        assertEquals(dom, mergeEditPostMessage(server, dom))
    }

    @Test
    fun selectBestEditBodyCandidate_prefersLongerWithBrackets() {
        val candidates = listOf("текст", "[b]жирный[/b] текст", "короткий")
        val out = selectBestEditBodyCandidate(candidates)
        assertEquals("[b]жирный[/b] текст", out)
    }

    @Test
    fun selectBestEditBodyCandidate_returnsLongestWhenNoBrackets() {
        val candidates = listOf("короткий", "длинный текст", "средний")
        val out = selectBestEditBodyCandidate(candidates)
        assertEquals("длинный текст", out)
    }

    @Test
    fun selectBestEditBodyCandidate_emptyList_returnsEmpty() {
        val candidates = emptyList<String>()
        val out = selectBestEditBodyCandidate(candidates)
        assertEquals("", out)
    }

    @Test
    fun stripOuterQuotesIfBbcodeContent_removesOuterQuotes() {
        val text = "\"[quote]цитата[/quote]\""
        val out = stripOuterQuotesIfBbcodeContent(text)
        assertEquals("[quote]цитата[/quote]", out)
    }

    @Test
    fun stripOuterQuotesIfBbcodeContent_noBrackets_noChange() {
        val text = "\"просто текст в кавычках\""
        val out = stripOuterQuotesIfBbcodeContent(text)
        assertEquals("\"просто текст в кавычках\"", out)
    }

    @Test
    fun stripOuterQuotesIfBbcodeContent_curlyQuotes_removed() {
        val text = "«[b]текст[/b]»"
        val out = stripOuterQuotesIfBbcodeContent(text)
        assertEquals("[b]текст[/b]", out)
    }

    @Test
    fun encodeEditPostBodyForSubmit_preservesRawNewlines() {
        val input = "Первая строка.\n\nВторая строка."
        val out = encodeEditPostBodyForSubmit(input)
        assertEquals(input, out)
    }

    @Test
    fun encodeEditPostBodyForSubmit_normalizesLineEndings() {
        val input = "строка1\r\nстрока2\rстрока3"
        val out = encodeEditPostBodyForSubmit(input)
        assertEquals("строка1\nстрока2\nстрока3", out)
    }

    @Test
    fun encodeEditPostBodyForSubmit_doesNotInjectBrTags() {
        val input = "текст\n[code]line1\nline2[/code]\nхвост"
        val out = encodeEditPostBodyForSubmit(input)
        assertEquals(input, out)
        assertFalse(out.contains("[br]"))
    }

    @Test
    fun decodeBbcodeLineBreaksForEditor_restoresNewlines() {
        val raw = "строка1[br]строка2[BR /]строка3"
        assertEquals("строка1\nстрока2\nстрока3", decodeBbcodeLineBreaksForEditor(raw))
    }

    @Test
    fun normalizeEditor_decodesBbcodeLineBreaks() {
        val raw = "абзац1[br][br]абзац2"
        val out = normalizeEditPostBodyForEditor(raw)
        assertEquals("абзац1\n\nабзац2", out)
    }
}
