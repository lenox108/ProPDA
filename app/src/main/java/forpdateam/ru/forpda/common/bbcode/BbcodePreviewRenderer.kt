package forpdateam.ru.forpda.common.bbcode

import forpdateam.ru.forpda.common.Html

object BbcodePreviewRenderer {
    private val supportedTags = setOf(
        "b",
        "i",
        "u",
        "s",
        "strike",
        "url",
        "quote",
        "spoiler",
        "offtop",
        "hide",
        "code",
        "snapback",
        "mergetime",
        "br",
        "size",
        "color",
        "background",
        "font",
        "left",
        "center",
        "right",
        "sub",
        "sup",
        "cur",
        "list",
        "*"
    )

    fun renderToHtml(source: String): String {
        if (source.isBlank()) {
            return ""
        }

        return Parser(normalizeLineBreaks(source)).parseUntil(null)
    }

    private fun normalizeLineBreaks(source: String): String =
        source.replace("\r\n", "\n").replace('\r', '\n')

    private class Parser(private val source: String) {
        private var position = 0

        fun parseUntil(closingTag: String?): String {
            val out = StringBuilder()
            while (position < source.length) {
                if (closingTag != null && source.startsWithClosingTag(closingTag, position)) {
                    position = source.indexOf(']', position) + 1
                    break
                }

                if (source[position] == '[') {
                    val tag = readOpeningTag()
                    if (tag != null) {
                        out.append(renderTag(tag))
                        continue
                    }
                }

                out.append(escapeText(source[position].toString()))
                position++
            }
            return out.toString()
        }

        private fun readOpeningTag(): Tag? {
            val closeBracket = source.indexOf(']', position)
            if (closeBracket == -1) {
                return null
            }

            val raw = source.substring(position + 1, closeBracket)
            if (raw.isBlank() || raw.startsWith("/")) {
                return null
            }

            val nameEnd = raw.indexOfFirst { it == '=' || it.isWhitespace() }
                .takeIf { it != -1 }
                ?: raw.length
            val name = raw.substring(0, nameEnd).lowercase()
            if (name !in supportedTags) {
                return null
            }

            position = closeBracket + 1
            return Tag(
                name = name,
                argument = raw.drop(nameEnd).trim().removePrefix("=").trim()
            )
        }

        private fun renderTag(tag: Tag): String =
            when (tag.name) {
                "b", "i", "u" -> wrap(tag.name, parseUntil(tag.name))
                "s", "strike" -> wrap("s", parseUntil(tag.name))
                "url" -> renderUrl(tag)
                "quote" -> renderQuote(tag)
                "spoiler" -> renderSpoiler(tag)
                "offtop" -> renderOfftop()
                "hide" -> renderHide()
                "code" -> renderCode()
                "snapback", "mergetime" -> hideContent(tag.name)
                "br" -> "<br>"
                "size" -> renderSize(tag)
                "color" -> renderColor(tag)
                "background" -> parseUntil(tag.name)
                "font" -> parseUntil(tag.name)
                "left", "center", "right" -> renderAligned(tag)
                "sub", "sup" -> wrap(tag.name, parseUntil(tag.name))
                "cur" -> parseUntil(tag.name)
                "list" -> parseUntil("list")
                "*" -> "<br>&bull; "
                else -> ""
            }

        private fun wrap(htmlTag: String, content: String): String =
            "<$htmlTag>$content</$htmlTag>"

        private fun renderUrl(tag: Tag): String {
            val label = parseUntil("url")
            val href = (tag.argument.ifBlank { htmlToPlainText(label) }).trim()
            if (href.isBlank()) {
                return label
            }
            return """<a href="${escapeAttribute(href)}">$label</a>"""
        }

        private fun renderQuote(tag: Tag): String {
            val content = parseUntil("quote")
            val title = quoteTitle(tag.argument)
            return buildString {
                append("<blockquote>")
                if (title.isNotBlank()) {
                    append("<b>")
                    append(escapeText(title))
                    append("</b><br>")
                }
                append(content)
                append("</blockquote>")
            }
        }

        private fun renderSpoiler(tag: Tag): String {
            val content = parseUntil("spoiler")
            val title = tag.argument.ifBlank { "Spoiler" }
            return buildString {
                append("<b>")
                append(escapeText(title))
                append("</b><br><blockquote>")
                append(content)
                append("</blockquote>")
            }
        }

        /**
         * `[offtop]` on 4pda renders as de-emphasised small grey text (always visible, no
         * header/box). Reproduce that look so the preview matches the posted result instead of
         * leaking the raw brackets.
         */
        private fun renderOfftop(): String {
            val content = parseUntil("offtop")
            return """<small><font color="#888888">$content</font></small>"""
        }

        /**
         * `[hide]` gates its body behind a reply on the live forum; there is nothing to gate in a
         * local preview, so we surface the content in a labelled block (mirrors [renderSpoiler]).
         */
        private fun renderHide(): String {
            val content = parseUntil("hide")
            return "<b>Hide</b><br><blockquote>$content</blockquote>"
        }

        private fun renderCode(): String {
            val start = position
            val closeStart = source.indexOfClosingTag("code", start)
            val codeText = if (closeStart == -1) {
                source.substring(start).also { position = source.length }
            } else {
                source.substring(start, closeStart).also {
                    position = source.indexOf(']', closeStart) + 1
                }
            }
            return "<tt>${escapeText(codeText)}</tt>"
        }

        private fun hideContent(tagName: String): String {
            parseUntil(tagName)
            return ""
        }

        private fun renderSize(tag: Tag): String {
            val content = parseUntil("size")
            val size = tag.argumentValue().toIntOrNull()
            return when {
                size != null && size <= 2 -> "<small>$content</small>"
                size != null && size >= 5 -> "<big>$content</big>"
                else -> content
            }
        }

        private fun renderColor(tag: Tag): String {
            val content = parseUntil("color")
            val color = tag.argumentValue()
            if (color.isBlank()) {
                return content
            }
            return """<font color="${escapeAttribute(color)}">$content</font>"""
        }

        private fun renderAligned(tag: Tag): String {
            val content = parseUntil(tag.name)
            return """<div align="${tag.name}">$content</div>"""
        }

        private fun quoteTitle(argument: String): String {
            if (argument.isBlank()) {
                return ""
            }
            findAttribute(argument, "name")?.let { return it }
            return argument
                .removePrefix("=")
                .trim()
                .trim('"', '\'')
        }

        private fun findAttribute(argument: String, name: String): String? {
            val pattern = Regex("""(?i)(?:^|\s)$name\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s]+))""")
            val match = pattern.find(argument) ?: return null
            return match.groupValues.drop(1).firstOrNull { it.isNotEmpty() }?.trim()
        }

        private fun htmlToPlainText(html: String): String =
            html.replace(Regex("<[^>]+>"), "")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&amp;", "&")
                .replace("&quot;", "\"")

        private fun escapeText(text: CharSequence): String =
            Html.escapeHtml(text).replace("\n", "<br>")

        private fun escapeAttribute(text: String): String =
            Html.escapeHtml(text).replace("\"", "&quot;")
    }

    private data class Tag(val name: String, val argument: String)

    private fun Tag.argumentValue(): String =
        argument.trim().trim('"', '\'')

    private fun String.startsWithClosingTag(tag: String, index: Int): Boolean {
        if (index >= length || this[index] != '[' || index + 2 >= length || this[index + 1] != '/') {
            return false
        }
        val closeBracket = indexOf(']', index)
        if (closeBracket == -1) {
            return false
        }
        return substring(index + 2, closeBracket).trim().equals(tag, ignoreCase = true)
    }

    private fun String.indexOfClosingTag(tag: String, startIndex: Int): Int {
        var searchFrom = startIndex
        while (searchFrom < length) {
            val index = indexOf("[/", searchFrom)
            if (index == -1) {
                return -1
            }
            if (startsWithClosingTag(tag, index)) {
                return index
            }
            searchFrom = index + 2
        }
        return -1
    }
}
