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
fun normalizeEditPostBodyForEditor(text: String): String {
    var s = text
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
    s = fixBbcodeBracketQuoteTypos(s)
    // 4PDA иногда отдаёт цитату в textarea как литерал "автор, …текст…" или "…"1 без [quote]
    s = convertOuterDoubleQuotedLiteralToBbcodeIfNeeded(s)
    // Иначе после &quot; и \" остаётся обёртка "…[quote]…" — в редакторе видны кавычки вместо нормального BBCode
    return stripOuterQuotesIfBbcodeContent(s)
}

private fun stripIpbEditFooterPlain(s: String): String {
    var t = s
    t = t.replace(Regex("(?is)Сообщение отредактировал[^\\n]*"), "")
    t = t.replace(Regex("(?is)Причина редактирования\\s*:\\s*[^\\n]*"), "")
    t = t.replace(Regex("(?im)^\\s*default_edit_reason\\s*$"), "")
    t = t.replace(Regex("(?is)\\n\\s*Причина редактирования\\s*:\\s*\\n[^\\n]*"), "")
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
 * Сервер может вернуть целиком обёртку в ASCII-кавычках без BBCode, например
 * `"fox malder, \n…текст…"1` или `""fox malder, …"1` (двойная кавычка в начале в DOM) —
 * превращаем в `[quote]…[/quote]`, хвост из цифр отбрасываем.
 * Если уже есть `[`, обработку оставляем [stripOuterQuotesIfBbcodeContent].
 */
private fun convertOuterDoubleQuotedLiteralToBbcodeIfNeeded(s: String): String {
    val t = s.trim().trimStart('\uFEFF')
    if (t.contains('[')) return s
    if (!t.startsWith('"')) return s
    // Снимаем все ведущие " — иначе при ""… остаётся лишняя " внутри [quote]
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
    return h
}

/**
 * Выбор лучшего варианта тела поста: приоритет у строки с `[quote` (как на форуме),
 * затем у более длинной с BBCode — иначе «обрезок» с `[img]` побеждает полный `[quote]`.
 */
fun selectBestEditBodyCandidate(candidates: List<String>): String {
    val nonBlank = candidates.filter { it.isNotBlank() }
    if (nonBlank.isEmpty()) return ""
    val quoteRe = Regex("""(?i)\[quote\b""")
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

/**
 * Текст из `<textarea name="Post">` — основной источник BBCode.
 * Сервер иногда отдаёт упоминания без `[b]…[/b]`, тогда как [normalizeEditPostBodyFromDomHtml] уже восстановила их из DOM — не затирать.
 */
fun mergeEditPostMessage(serverMessage: String, prefilledFromDom: String): String {
    val server = normalizeEditPostBodyForEditor(serverMessage)
    val dom = normalizeEditPostBodyForEditor(prefilledFromDom)
    if (server.isBlank()) return dom
    if (dom.isBlank()) return server
    val serverHasBracket = server.contains('[')
    val domLooksLikeBbcode = dom.contains('[') && dom.contains(']')
    // Страница редактирования иногда отдаёт «голый» текст без [, а из WebView уже собран нормальный BBCode.
    if (!serverHasBracket && domLooksLikeBbcode) {
        return dom
    }
    if (mentionBoldBbcode.containsMatchIn(dom) && !mentionBoldBbcode.containsMatchIn(server)) {
        // Не терять цитаты с сервера, если из DOM цитата не попала в нормализацию.
        if (server.contains("[quote") && !dom.contains("[quote")) {
            return server
        }
        return dom
    }
    return server
}
