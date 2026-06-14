package forpdateam.ru.forpda.common

import androidx.core.text.HtmlCompat

/**
 * Содержимое `<textarea name="Post">` — это BBCode, не HTML-документ.
 * Нельзя прогонять через [HtmlCompat.fromHtml]: теги [b], [url] и т.д. теряются.
 * Декодируем только HTML-сущности и нормализуем переводы строк.
 */
fun decodeForumPostTextareaContent(raw: String): String {
    var s = raw
        .replace("\r\n", "\n")
        .replace("\r", "\n")
    // Сначала &amp;… → &…, иначе &amp;quot; не совпадёт с &quot; и в тексте остаются кавычки/мусор вместо BBCode
    while (s.contains("&amp;")) {
        s = s.replace("&amp;", "&")
    }
    s = Regex("&#(\\d+);").replace(s) { m ->
        val v = m.groupValues[1].toIntOrNull() ?: return@replace m.value
        unicodeScalarEntityToString(v) ?: m.value
    }
    s = Regex("&#x([0-9a-fA-F]+);").replace(s) { m ->
        val v = m.groupValues[1].toIntOrNull(16) ?: return@replace m.value
        unicodeScalarEntityToString(v) ?: m.value
    }
    s = s.replace("&nbsp;", "\u00a0")
    s = s.replace("&lt;", "<")
    s = s.replace("&gt;", ">")
    s = s.replace("&quot;", "\"")
    s = s.replace("&apos;", "'")
    s = s.replace("&#039;", "'")
    s = s.replace("&#39;", "'")
    // Именованные сущности скобок (числовые &#91; обрабатываются выше)
    s = s.replace("&lbrack;", "[")
    s = s.replace("&rbrack;", "]")
    return s
}

/** Декодирование &#...; / &#x...; в UTF-16 без java.lang.Character (Kotlin-стиль). */
private fun unicodeScalarEntityToString(v: Int): String? {
    if (v !in 0..0x10FFFF) return null
    if (v in 0xD800..0xDFFF) return null
    return if (v < 0x10000) {
        v.toChar().toString()
    } else {
        val c = v - 0x10000
        val high = ((c shr 10) + 0xD800).toChar()
        val low = ((c and 0x3FF) + 0xDC00).toChar()
        "$high$low"
    }
}

/**
 * Убирает служебные хвосты IPB (кто отредактировал, причина), артефакты JSON/экранирования.
 * Вызывать для текста из DOM и после парсера формы.
 */
private val bbcodeLineBreakTag = Regex("""(?i)\[br\s*/?\]""")

/**
 * 4PDA/IPB в textarea отдаёт переводы строк как `[br]`; в редакторе показываем обычный `\n`.
 */
fun decodeBbcodeLineBreaksForEditor(text: String): String =
        bbcodeLineBreakTag.replace(text) { "\n" }

/**
 * Тело поля `Post` при submit — как в браузерной textarea: сырые `\n`, не `[br]`.
 * IPB в textarea отдаёт `[br]`, но при сохранении принимает переводы строк; литеральные
 * `[br]` в POST попадают в HTML поста как видимый текст.
 */
fun encodeEditPostBodyForSubmit(text: String): String =
        text.replace("\r\n", "\n").replace('\r', '\n')

fun normalizeEditPostBodyForEditor(text: String): String {
    var s = decodeBbcodeLineBreaksForEditor(text)
    // Снять экранирование кавычек/переносов (двойное тоже — пока есть подстрока)
    var prev: String
    do {
        prev = s
        s = s.replace("\\\"", "\"")
    } while (s != prev)
    s = s
        .replace("\\n", "\n")
        .replace("\\r\\n", "\n")
        .replace("\\r", "\n")
    s = stripIpbEditFooterPlain(s)
    s = s.trim()
    // Остатки UI спойлера из WebView (если HTML не разобрался до [spoiler])
    s = s.replace(Regex("(?m)^\\s*Закрыть спойлер\\s*$"), "")
    s = s.replace(Regex("(?m)^\\s*Спойлер\\s*$"), "")
    // Заголовок блока вложений из DOM (attach_transformer / спойлер «Прикреплённые файлы»)
    s = s.replace(Regex("(?m)^\\s*Прикреплённые файлы\\s*$"), "")
    s = s.replace(Regex("(?m)^\\s*Прикрепленные файлы\\s*$"), "")
    s = s.replace(Regex("(?im)^\\s*Attached files\\s*$"), "")
    s = fixBbcodeBracketQuoteTypos(s)
    // 4PDA иногда отдаёт цитату в textarea как литерал "автор, …текст…" или "…"1 без [quote]
    s = convertOuterDoubleQuotedLiteralToBbcodeIfNeeded(s)
    // Иначе после &quot; и \" остаётся обёртка "…[quote]…" — в редакторе видны кавычки вместо нормального BBCode
    return stripOuterQuotesIfBbcodeContent(s)
}

private fun stripIpbEditFooterPlain(s: String): String {
    var t = s
    // Служебный хвост IPB/4PDA часто без class=edit в DOM; бывает сразу после [/img] без перевода строки.
    t = t.replace(
            Regex(
                    "(?is)(\\[/[a-zA-Z][a-zA-Z0-9]*\\])" +
                            "\\s*(?:Сообщение[\\s\\u00A0]+отредактировал|Message[\\s\\u00A0]+edited[\\s\\u00A0]+by)\\b[^\\n]*" +
                            "(?:\\R\\s*(?:Причина[\\s\\u00A0]+редактирования\\s*:|Reason[\\s\\u00A0]+for[\\s\\u00A0]+edit\\s*:)[^\\n]*)?"
            )
    ) { m -> m.groupValues[1] }
    t = t.replace(Regex("(?im)^\\s*Сообщение[\\s\\u00A0]+отредактировал.*$"), "")
    t = t.replace(Regex("(?im)^\\s*Причина[\\s\\u00A0]+редактирования\\s*:.*$"), "")
    t = t.replace(Regex("(?im)^\\s*Message[\\s\\u00A0]+edited[\\s\\u00A0]+by.*$"), "")
    t = t.replace(Regex("(?im)^\\s*Reason[\\s\\u00A0]+for[\\s\\u00A0]+edit\\s*:.*$"), "")
    t = t.replace(Regex("(?im)^\\s*default_edit_reason\\s*$"), "")
    return t.trim()
}

/**
 * После &apos;/&#39; в BBCode иногда вместо "[" оказывается апостроф (код 39 вместо 91) — видно 'quote вместо [quote].
 * Плюс именованные сущности скобок из HTML формы.
 */
private fun fixBbcodeBracketQuoteTypos(s: String): String {
    var t = s
    t = t.replace(Regex("(?i)&lsqb;"), "[")
    t = t.replace(Regex("(?i)&rsqb;"), "]")
    t = t.replace(Regex("(?i)&lbrack;"), "[")
    t = t.replace(Regex("(?i)&rbrack;"), "]")
    t = t.replace(Regex("(?m)(^|[\n\r])([\u2018\u2019'])quote\\b")) { m ->
        m.groupValues[1] + "[quote"
    }
    t = t.replace(Regex("(?i)[\u2018\u2019'](/quote\\])"), "[/quote]")
    return t
}

/**
 * IPB иногда отдаёт литерал цитаты как `"текст…"123` (хвост — только цифры) без `[quote]`.
 * Нельзя оборачивать любой текст в кавычках: фразы вроде "Привет" превращались в ложную цитату.
 * Срабатываем только при хвосте-цифрах или явной «шапке» автора (запятая + перенос строки внутри).
 */
private fun convertOuterDoubleQuotedLiteralToBbcodeIfNeeded(s: String): String {
    val t = s.trim().trimStart('\uFEFF')
    if (t.contains('[')) return s
    if (!t.startsWith('"')) return s
    var body = t
    var guard = 0
    while (body.startsWith('"') && guard++ < 32) {
        body = body.substring(1)
    }
    if (body.isEmpty()) return s
    val lastQuote = body.lastIndexOf('"')
    if (lastQuote < 0) return s
    val inner = body.substring(0, lastQuote)
    val after = body.substring(lastQuote + 1).trim()
    val trailingDigitArtifact = after.isNotEmpty() && after.all { it.isDigit() }
    val looksLikeForumQuoteHeader = Regex(",\\s*\\R").containsMatchIn(inner)
    if (!trailingDigitArtifact && !looksLikeForumQuoteHeader) {
        return s
    }
    return if (after.isEmpty() || after.all { it.isDigit() }) {
        "[quote]$inner[/quote]"
    } else {
        "[quote]$inner[/quote]\n\n$after"
    }
}

/**
 * После &quot; и снятия [\"] весь текст иногда остаётся в кавычках "[quote]…[/quote]" —
 * в редакторе видны кавычки вместо нормального BBCode.
 * Снимаем парные кавычки по краям, пока внутри есть признаки разметки ( [… ).
 */
fun stripOuterQuotesIfBbcodeContent(text: String): String {
    var t = text.trim().trimStart('\uFEFF')
    if (!t.contains('[')) return t
    fun isOpen(c: Char) = c == '"' || c == '\u201C' || c == '\u201E' || c == '\u00AB'
    fun isClose(c: Char) = c == '"' || c == '\u201D' || c == '\u00BB'
    var guard = 0
    while (guard++ < 16 && t.isNotEmpty() && t.contains('[')) {
        val open = isOpen(t.first())
        val close = isClose(t.last())
        when {
            t.length >= 2 && open && close ->
                t = t.substring(1, t.length - 1).trim()
            open && !close ->
                t = t.substring(1).trimStart()
            !open && close ->
                t = t.substring(0, t.length - 1).trimEnd()
            else -> break
        }
    }
    return t
}

/**
 * HTML из WebView: вырезать блоки «редактирование», затем в plain (BBCode в теме уже превращён в разметку).
 */
fun normalizeEditPostBodyFromDomHtml(html: String): String {
    var h = html.replace("\\\"", "\"")
    h = stripEmbeddedAttachmentsUiFromPostHtml(h)
    h = stripAttachmentSpoilerBlocksFromPostHtml(h)
    h = stripSpoilerUiControlsFromPostHtml(h)
    h = preprocessHtmlMentionsAndBoldForBbcodeEdit(h)
    h = preprocessHtmlQuoteBlocksForBbcodeEdit(h)
    h = preprocessHtmlSpoilerBlocksForBbcodeEdit(h)
    h = stripIpbEditFooterHtml(h)
    h = preprocessHtmlForPlainExtraction(h)
    val plain = HtmlCompat.fromHtml(h, HtmlCompat.FROM_HTML_MODE_LEGACY)
        .toString()
        .replace('\u00a0', ' ')
        .replace("\uFFFC", "")
    return normalizeEditPostBodyForEditor(plain)
}

private fun stripIpbEditFooterHtml(html: String): String {
    var h = html
    h = h.replace(Regex("(?is)<p[^>]*class=\"[^\"]*edit[^\"]*\"[^>]*>[\\s\\S]*?</p>"), "")
    h = h.replace(Regex("(?is)<div[^>]*class=\"[^\"]*edit[^\"]*\"[^>]*>[\\s\\S]*?</div>"), "")
    h = h.replace(Regex("(?is)<span[^>]*class=\"[^\"]*edit[^\"]*\"[^>]*>[\\s\\S]*?</span>"), "")
    // IPS/IP.B без class=edit: обычно <p> или <div class="ipsType_light|…"> — не матчим голый <div> (риск съесть весь post_body).
    val ipsish = """[^"]*(?:ipsType_light|ipsFaded|ipsType_reset|post-edit-reason)[^"]*"""
    val sp = "(?:\\s|&nbsp;|&#160;|\u00A0)+"
    // Не использовать [\s\S]* внутри <p> — иначе матч «выползает» через </p> и съедает предыдущие абзацы.
    val inOneP = """(?:(?!</p>).)*"""
    h = h.replace(Regex("(?is)<p\\b[^>]*>$inOneP?Сообщение${sp}отредактировал$inOneP</p>"), "")
    h = h.replace(Regex("(?is)<div\\b[^>]*class=\"$ipsish\"[^>]*>[\\s\\S]*?Сообщение${sp}отредактировал[\\s\\S]*?</div>"), "")
    h = h.replace(Regex("(?is)<span\\b[^>]*class=\"$ipsish\"[^>]*>[\\s\\S]*?Сообщение${sp}отредактировал[\\s\\S]*?</span>"), "")
    h = h.replace(Regex("(?is)<p\\b[^>]*>$inOneP?Message${sp}edited${sp}by\\b$inOneP</p>"), "")
    h = h.replace(Regex("(?is)<div\\b[^>]*class=\"$ipsish\"[^>]*>[\\s\\S]*?Message${sp}edited${sp}by\\b[\\s\\S]*?</div>"), "")
    h = h.replace(Regex("(?is)<span\\b[^>]*class=\"$ipsish\"[^>]*>[\\s\\S]*?Message${sp}edited${sp}by\\b[\\s\\S]*?</span>"), "")
    h = h.replace(Regex("(?is)<p\\b[^>]*>$inOneP?Причина${sp}редактирования\\s*:$inOneP</p>"), "")
    h = h.replace(Regex("(?is)<div\\b[^>]*class=\"$ipsish\"[^>]*>[\\s\\S]*?Причина${sp}редактирования\\s*:[\\s\\S]*?</div>"), "")
    h = h.replace(Regex("(?is)<span\\b[^>]*class=\"$ipsish\"[^>]*>[\\s\\S]*?Причина${sp}редактирования\\s*:[\\s\\S]*?</span>"), "")
    h = h.replace(Regex("(?is)<p\\b[^>]*>$inOneP?Reason${sp}for${sp}edit\\s*:$inOneP</p>"), "")
    h = h.replace(Regex("(?is)<div\\b[^>]*class=\"$ipsish\"[^>]*>[\\s\\S]*?Reason${sp}for${sp}edit\\s*:[\\s\\S]*?</div>"), "")
    h = h.replace(Regex("(?is)<span\\b[^>]*class=\"$ipsish\"[^>]*>[\\s\\S]*?Reason${sp}for${sp}edit\\s*:[\\s\\S]*?</span>"), "")
    return h
}

/**
 * Выбор лучшего варианта тела поста: приоритет у строки с `[quote` (как на форуме),
 * кроме случаев, когда более длинный кандидат содержит merged/appended-секцию поста.
 */
fun selectBestEditBodyCandidate(candidates: List<String>): String {
    val nonBlank = candidates.filter { it.isNotBlank() }
    if (nonBlank.isEmpty()) return ""
    val quoteRe = Regex("""(?i)\[quote\b""")
    val withMergedSection = nonBlank.filter { containsMergedPostSection(it) }
    if (withMergedSection.isNotEmpty()) {
        return withMergedSection.maxByOrNull { it.length } ?: withMergedSection.first()
    }
    val withQuote = nonBlank.filter { quoteRe.containsMatchIn(it) }
    if (withQuote.isNotEmpty()) {
        return withQuote.maxByOrNull { it.length } ?: withQuote.first()
    }
    val withBracket = nonBlank.filter { it.contains('[') }
    if (withBracket.isNotEmpty()) {
        return withBracket.maxByOrNull { it.length } ?: withBracket.first()
    }
    return nonBlank.maxByOrNull { it.length } ?: nonBlank.first()
}

/** Упоминание в стиле форума: `[b]Ник,[/b]` (запятая перед закрытием тега). */
private val mentionBoldBbcode = Regex("""\[(?i)b\][^\[]+?,\s*\[\s*/\s*b\]""")
private val snapbackBbcode = Regex("""(?is)\[snapback\]\d+\[/snapback\]""")

/**
 * Текст из `<textarea name="Post">` — основной источник BBCode.
 * Сервер иногда отдаёт упоминания без `[b]…[/b]`, тогда как [normalizeEditPostBodyFromDomHtml] уже восстановила их из DOM — не затирать.
 */
private val outerSingleBbcodeQuote = Regex("""(?is)^\[quote[^\]]*\]([\s\S]*)\[/quote\]\s*$""")

private fun roughPlainForMerge(s: String): String {
    var t = s
    var prev: String
    do {
        prev = t
        t = t.replace(snapbackBbcode, "")
        t = t.replace(Regex("""(?is)\[[^\]]*\]"""), "")
    } while (t != prev)
    return t.replace(Regex("\\s+"), " ").trim()
}

private fun singleOuterQuoteInner(text: String): String? {
    val m = outerSingleBbcodeQuote.find(text.trim()) ?: return null
    return m.groupValues[1].trim()
}

private fun containsMergedPostSection(text: String): Boolean =
        Regex("""(?i)\[(?:no)?mergetime\b""").containsMatchIn(text) ||
                text.contains("Добавлено", ignoreCase = true)

fun mergeEditPostMessage(serverMessage: String, prefilledFromDom: String): String {
    val server = normalizeEditPostBodyForEditor(serverMessage)
    val dom = normalizeEditPostBodyForEditor(prefilledFromDom)
    if (server.isBlank()) return dom
    if (dom.isBlank()) return server
    // Для редактирования серверная textarea — единственный полный источник merged-постов
    // (`Добавлено ...`, [mergetime]); DOM-подстановка может содержать только первую часть.
    if (containsMergedPostSection(server) && !containsMergedPostSection(dom)) {
        return server
    }
    // Если сервер внезапно вернул только первую часть, но предзаполнение из WebView уже содержит
    // видимый merged-блок, лучше сохранить полный текст, чем молча отрезать добавленную часть.
    if (!containsMergedPostSection(server) && containsMergedPostSection(dom) && dom.length > server.length) {
        return dom
    }
    val serverHasBracket = server.contains('[')
    val domLooksLikeBbcode = dom.contains('[') && dom.contains(']')
    val serverHasQuote = server.contains("[quote", ignoreCase = true)
    val domHasQuote = dom.contains("[quote", ignoreCase = true)
    val serverHasSnapback = snapbackBbcode.containsMatchIn(server)
    val domHasSnapback = snapbackBbcode.containsMatchIn(dom)
    // Страница редактирования иногда отдаёт «голый» текст без [, а из WebView уже собран нормальный BBCode.
    if (!serverHasBracket && domLooksLikeBbcode) {
        // DOM часто даёт ложные [quote] из blockquote/обёрток 4PDA — для правки поста приоритет у textarea с сервера.
        if (domHasQuote && !serverHasQuote && server.isNotBlank()) {
            return server
        }
        return dom
    }
    // DOM: одна ложная обёртка [quote]… вокруг того же текста, что и в BBCode с сервера (есть [b] и т.д., но нет цитаты).
    if (serverHasBracket && !serverHasQuote && domHasQuote) {
        singleOuterQuoteInner(dom)?.let { inner ->
            val innerN = normalizeEditPostBodyForEditor(inner)
            if (innerN.isNotBlank() && roughPlainForMerge(server) == roughPlainForMerge(innerN)) {
                return server
            }
        }
    }
    if (!serverHasSnapback && domHasSnapback && roughPlainForMerge(server) == roughPlainForMerge(dom)) {
        return dom
    }
    if (mentionBoldBbcode.containsMatchIn(dom) && !mentionBoldBbcode.containsMatchIn(server)) {
        // Не терять цитаты с сервера, если из DOM цитата не попала в нормализацию.
        if (server.contains("[quote", ignoreCase = true) && !dom.contains("[quote", ignoreCase = true)) {
            return server
        }
        return dom
    }
    return server
}
