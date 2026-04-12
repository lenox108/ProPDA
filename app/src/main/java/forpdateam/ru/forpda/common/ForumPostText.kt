package forpdateam.ru.forpda.common

import androidx.core.text.HtmlCompat

/**
 * Убирает из HTML тела поста блоки цитат (чтобы в цитату попадал только текст автора).
 */
fun stripHtmlQuoteBlocks(html: String): String {
    var s = html
    val blockquote = Regex("(?is)<blockquote[^>]*>[\\s\\S]*?</blockquote>")
    while (blockquote.containsMatchIn(s)) {
        s = s.replace(blockquote, "")
    }
    val postBlockquote = Regex(
        "(?is)<div[^>]*class=\"[^\"]*(?:post_blockquote|blockquote)[^\"]*\"[^>]*>[\\s\\S]*?</div>"
    )
    while (postBlockquote.containsMatchIn(s)) {
        s = s.replace(postBlockquote, "")
    }
    val ipbQuoteMarkers = Regex("(?is)<!--\\s*QuoteBegin[^>]*-->[\\s\\S]*?<!--\\s*QuoteEnd[^>]*-->")
    while (ipbQuoteMarkers.containsMatchIn(s)) {
        s = s.replace(ipbQuoteMarkers, "")
    }
    // Разметка темы в WebView: .post-block.quote (вложенные div — через баланс, как у спойлеров)
    val postBlockQuoteOpen = Regex(
        "(?is)<div[^>]*class=\"[^\"]*(?:post-block\\s+quote|quote\\s+post-block)[^\"]*\"[^>]*>"
    )
    var guard = 0
    while (postBlockQuoteOpen.containsMatchIn(s) && guard++ < 256) {
        val m = postBlockQuoteOpen.find(s) ?: break
        val gt = s.indexOf('>', m.range.first)
        if (gt < 0) break
        val endPair = extractDivInnerUntilMatchingClose(s, gt + 1) ?: break
        s = s.removeRange(m.range.first, endPair.second)
    }
    return s
}

/**
 * Удаляет вложенные [quote]…[/quote] из BBCode (с конца — как внутренние вложения).
 */
fun stripBbcodeQuotes(text: String): String {
    var s = text
    while (true) {
        val start = s.lastIndexOf("[quote")
        if (start < 0) break
        val end = s.indexOf("[/quote]", start)
        if (end < 0) break
        s = s.removeRange(start, end + "[/quote]".length)
    }
    return s.trim()
}

/**
 * Тег &lt;q&gt; в HTML5 — браузер и [HtmlCompat.fromHtml] добавляют символы кавычек вокруг текста;
 * для упоминаний вида &lt;q&gt;@nick&lt;/q&gt; в BBCode вместо @ оказываются «ёлочки».
 */
private fun unwrapHtmlQTags(html: String): String {
    var s = html
    val q = Regex("(?is)<q\\b[^>]*>([\\s\\S]*?)</q>")
    var i = 0
    while (q.containsMatchIn(s) && i++ < 64) {
        s = q.replace(s) { it.groupValues[1] }
    }
    return s
}

/**
 * Кнопка «Закрыть спойлер» и контейнер из [blocks.js] — не часть BBCode, ломают предпросмотр редактора.
 */
fun stripSpoilerUiControlsFromPostHtml(html: String): String {
    var s = html
    s = Regex("(?is)<div[^>]*class=\"[^\"]*\\bbtns_container\\b[^\"]*\"[^>]*>[\\s\\S]*?</div>").replace(s, "")
    s = Regex("(?is)<div[^>]*class=\"[^\"]*\\bspoil_close\\b[^\"]*\"[^>]*>[\\s\\S]*?</div>").replace(s, "")
    return s
}

/** Текст из фрагмента HTML для упоминания (без полного конвейера img — только разметка ника). */
private fun htmlInnerToPlainForMention(inner: String): String {
    var t = inner.replace(Regex("(?is)<br\\s*/?>"), "\n")
    return HtmlCompat.fromHtml(t, HtmlCompat.FROM_HTML_MODE_LEGACY)
            .toString()
            .replace('\u00a0', ' ')
            .replace("\uFFFC", "")
            .trim()
}

/**
 * Упоминания в DOM: ссылка на профиль (showuser), часто с `<b>Ник,</b>` внутри — в BBCode как `[b]Ник,[/b]`.
 * Оставшиеся простые `<b>`/`</b>` тоже переводим, иначе [HtmlCompat.fromHtml] съедает жирный текст.
 */
fun preprocessHtmlMentionsAndBoldForBbcodeEdit(html: String): String {
    var s = html
    // Ссылка на профиль (showuser) — в т.ч. после blocks.js (class snapback user).
    val showUserLink = Regex("(?is)<a\\b[^>]*href=[^>]*showuser[^>]*>([\\s\\S]*?)</a>")
    var guard = 0
    while (showUserLink.containsMatchIn(s) && guard++ < 64) {
        s = showUserLink.replace(s) { m ->
            val plain = htmlInnerToPlainForMention(m.groupValues[1])
            if (plain.isBlank()) m.value else "[b]$plain[/b]"
        }
    }
    // Упоминание без showuser в href (редко): только class snapback + user, как в blocks.js.
    val snapbackUserClass = Regex(
        "(?is)<a\\b[^>]*class=\"[^\"]*(?:snapback[^\"]*\\buser\\b|user[^\"]*snapback)[^\"]*\"[^>]*>([\\s\\S]*?)</a>"
    )
    guard = 0
    while (snapbackUserClass.containsMatchIn(s) && guard++ < 32) {
        s = snapbackUserClass.replace(s) { m ->
            val plain = htmlInnerToPlainForMention(m.groupValues[1])
            if (plain.isBlank()) m.value else "[b]$plain[/b]"
        }
    }
    // Вложенные <b><span>Nick</span>,</b> — старый regex с [^<]* их не ловил.
    val boldTag = Regex("""(?is)<(b|strong)\b[^>]*>([\s\S]*?)</\1>""")
    guard = 0
    while (boldTag.containsMatchIn(s) && guard++ < 256) {
        s = boldTag.replace(s) { m ->
            val innerHtml = m.groupValues[2]
            val plain = htmlInnerToPlainForMention(innerHtml)
            val trimmed = plain.trim()
            if (trimmed.isEmpty()) m.value else "[b]$trimmed[/b]"
        }
    }
    return s
}

/** Внутренность div по балансу &lt;div&gt; / &lt;/div&gt; (после символа `>` открывающего тега). */
private fun extractDivInnerUntilMatchingClose(s: String, afterOpenTagGt: Int): Pair<String, Int>? {
    var depth = 1
    var i = afterOpenTagGt
    while (i < s.length && depth > 0) {
        val nextOpen = s.indexOf("<div", i, ignoreCase = true)
        val nextClose = s.indexOf("</div>", i, ignoreCase = true)
        if (nextClose < 0) return null
        if (nextOpen >= 0 && nextOpen < nextClose) {
            depth++
            i = nextOpen + 4
        } else {
            depth--
            if (depth == 0) {
                return Pair(s.substring(afterOpenTagGt, nextClose), nextClose + 6)
            }
            i = nextClose + 6
        }
    }
    return null
}

private fun htmlFragmentToPlainForSpoilerEdit(fragment: String): String {
    val p = preprocessHtmlForPlainExtraction(fragment)
    return HtmlCompat.fromHtml(p, HtmlCompat.FROM_HTML_MODE_LEGACY)
            .toString()
            .replace('\u00a0', ' ')
            .replace("\uFFFC", "")
            .trim()
}

/**
 * Блоки `.post-block.spoil` в теме (WebView) → `[spoiler]…[/spoiler]`, иначе в plain попадают «Спойлер»/«Закрыть спойлер».
 */
fun preprocessHtmlSpoilerBlocksForBbcodeEdit(html: String): String {
    var s = html
    var searchStart = 0
    val blockStart = Regex("(?is)<div[^>]*class=\"[^\"]*(?:post-block\\s+spoil|spoil\\s+post-block)[^\"]*\"[^>]*>")
    val bodyOpen = Regex("(?is)<div[^>]*class=\"[^\"]*block-body[^\"]*\"[^>]*>")
    while (true) {
        val m = blockStart.find(s, searchStart) ?: break
        val body = bodyOpen.find(s, m.range.first) ?: break
        val innerStart = body.range.last + 1
        val innerPair = extractDivInnerUntilMatchingClose(s, innerStart) ?: break
        var innerHtml = innerPair.first
        innerHtml = preprocessHtmlQuoteBlocksForBbcodeEdit(innerHtml)
        innerHtml = preprocessHtmlSpoilerBlocksForBbcodeEdit(innerHtml)
        innerHtml = stripSpoilerUiControlsFromPostHtml(innerHtml)
        val plain = htmlFragmentToPlainForSpoilerEdit(innerHtml)
        var endPos = innerPair.second
        while (endPos < s.length && s[endPos].isWhitespace()) endPos++
        if (endPos + 6 <= s.length && s.regionMatches(endPos, "</div>", 0, 6, ignoreCase = true)) {
            endPos += 6
        } else {
            break
        }
        val replacement = "[spoiler]$plain[/spoiler]"
        s = s.replaceRange(m.range.first, endPos, replacement)
        searchStart = m.range.first + replacement.length
    }
    return s
}

/**
 * Перед [HtmlCompat.fromHtml]: цитаты в HTML (IPB, blockquote) в плоский текст с [quote]…[/quote],
 * иначе остаётся «"ник," …» без тегов BBCode.
 */
fun preprocessHtmlQuoteBlocksForBbcodeEdit(html: String): String {
    var s = html
    val ipb = Regex("(?is)<!--\\s*QuoteBegin\\s+([^>]+?)\\s*-->([\\s\\S]*?)<!--\\s*QuoteEnd[^>]*-->")
    while (ipb.containsMatchIn(s)) {
        s = ipb.replace(s) { m ->
            val attrs = m.groupValues[1].trim()
            val body = m.groupValues[2]
            "[quote $attrs]$body[/quote]"
        }
    }
    // Разметка темы в приложении: .post-block.quote > .block-body
    val postBlockBody = Regex(
        "(?is)<div[^>]*class=\"[^\"]*(?:post-block\\s+quote|quote\\s+post-block)[^\"]*\"[^>]*>" +
                "[\\s\\S]*?<div[^>]*class=\"[^\"]*block-body[^\"]*\"[^>]*>([\\s\\S]*?)</div>"
    )
    while (postBlockBody.containsMatchIn(s)) {
        s = postBlockBody.replace(s) { "[quote]${it.groupValues[1]}[/quote]" }
    }
    val bq = Regex("(?is)<blockquote[^>]*>([\\s\\S]*?)</blockquote>")
    while (bq.containsMatchIn(s)) {
        s = bq.replace(s) { "[quote]${it.groupValues[1]}[/quote]" }
    }
    val postBlockquote = Regex(
        "(?is)<div[^>]*class=\"[^\"]*(?:post_blockquote|blockquote)[^\"]*\"[^>]*>([\\s\\S]*?)</div>"
    )
    while (postBlockquote.containsMatchIn(s)) {
        s = postBlockquote.replace(s) { "[quote]${it.groupValues[1]}[/quote]" }
    }
    return s
}

/**
 * Перед [HtmlCompat.fromHtml]: теги &lt;img&gt; превращаются в ImageSpan с U+FFFC (квадратик «OBJ»).
 * Заменяем на [img]url[/img] или на alt, если картинка без URL (иконка @ и т.п.).
 */
fun preprocessHtmlForPlainExtraction(html: String): String {
    var s = unwrapHtmlQTags(html)
    // Иконка @ в упоминании: иначе первая же ветка с src превратит её в [img]…[/img] и пропадёт @
    s = Regex("(?is)<img[^>]*\\bmention\\b[^>]*>").replace(s, "@")
    s = Regex("""(?is)<img[^>]+(?:alt|title)\s*=\s*["']@[^"']*["'][^>]*>""").replace(s, "@")
    s = Regex("""(?is)<img[^>]+?src\s*=\s*["']([^"']+)["'][^>]*>""").replace(s) { m ->
        "[img]${m.groupValues[1]}[/img]"
    }
    s = Regex("""(?is)<img[^>]+?data-src\s*=\s*["']([^"']+)["'][^>]*>""").replace(s) { m ->
        "[img]${m.groupValues[1]}[/img]"
    }
    s = Regex("""(?is)<img[^>]+?data-original\s*=\s*["']([^"']+)["'][^>]*>""").replace(s) { m ->
        "[img]${m.groupValues[1]}[/img]"
    }
    s = Regex("(?is)<img[^>]+>").replace(s) { m ->
        val tag = m.value
        val alt = Regex("""(?i)alt\s*=\s*["']([^"']*)["']""").find(tag)?.groupValues?.getOrNull(1).orEmpty()
        alt
    }
    return s
}

/**
 * Блок вложений ForPDA ([attach_transformer.js]) и превью файлов оказываются внутри `.post_body` —
 * без удаления в редактор попадает «Прикрепленные файлы» и разметка вместо [attachment=…].
 */
fun stripEmbeddedAttachmentsUiFromPostHtml(html: String): String {
    var s = html
    s = stripOuterDivByClassName(s, "attachments")
    s = stripOuterDivByClassName(s, "btns_container")
    return s
}

/**
 * Спойлер с заголовком «Прикреплённые файлы» (оригинальная разметка форума), если трансформер не вычистил.
 */
fun stripAttachmentSpoilerBlocksFromPostHtml(html: String): String {
    val blockStart = Regex(
        "(?is)<div\\b[^>]*class=\"[^\"]*(?:post-block\\s+spoil|spoil\\s+post-block)[^\"]*\"[^>]*>"
    )
    val titleHasAttach = Regex("(?is)Прикреплен|Attached\\s+files")
    var s = html
    var searchStart = 0
    var guard = 0
    while (guard++ < 64) {
        val m = blockStart.find(s, searchStart) ?: break
        val gt = s.indexOf('>', m.range.first)
        if (gt < 0) break
        val blockSnippet = s.substring(m.range.first, minOf(m.range.first + 800, s.length))
        if (!titleHasAttach.containsMatchIn(blockSnippet)) {
            searchStart = m.range.first + 1
            continue
        }
        val endPair = extractDivInnerUntilMatchingClose(s, gt + 1)
        if (endPair == null) {
            searchStart = m.range.first + 1
            continue
        }
        s = s.removeRange(m.range.first, endPair.second)
        searchStart = m.range.first
    }
    return s
}

private fun stripOuterDivByClassName(html: String, classToken: String): String {
    var s = html
    val open = Regex(
        "(?is)<div\\b[^>]*\\bclass\\s*=\\s*[\"'][^\"']*\\b${Regex.escape(classToken)}\\b[^\"']*[\"'][^>]*>"
    )
    var guard = 0
    while (guard++ < 64) {
        val m = open.find(s) ?: break
        val gt = s.indexOf('>', m.range.first)
        if (gt < 0) break
        val endPair = extractDivInnerUntilMatchingClose(s, gt + 1) ?: break
        s = s.removeRange(m.range.first, endPair.second)
    }
    return s
}

fun forumPostHtmlToPlainText(html: String): String {
    val plain = HtmlCompat.fromHtml(
            preprocessHtmlForPlainExtraction(html),
            HtmlCompat.FROM_HTML_MODE_LEGACY
    )
            .toString()
            .replace('\u00a0', ' ')
            .replace("\uFFFC", "")
    return stripOuterQuotesIfBbcodeContent(plain)
}
