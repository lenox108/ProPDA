package forpdateam.ru.forpda.presentation.articles.detail

/**
 * Removes site-embedded dark theme markup from article bodies when the app uses a light palette.
 * Some 4PDA articles ship inline styles, dark utility classes, or embedded CSS that override
 * ForPDA WebView styles and make the body look like night mode while the native shell stays light.
 */
object ArticleBodyThemeNormalizer {

    private val embeddedStyleBlockRegex = Regex("""(?is)<style\b[^>]*>[\s\S]*?</style>""")
    private val embeddedStylesheetLinkRegex = Regex(
            """(?is)<link\b(?=[^>]*\brel\s*=\s*["']?stylesheet["']?)[^>]*>"""
    )
    private val colorSchemeAttrRegex = Regex("""(?is)\bcolor-scheme\s*:\s*[^;"]+""")
    private val darkClassTokenRegex = Regex(
            """(?i)\b(?:dark|theme-dark|dark-theme|night-mode|color-scheme-dark|is-dark|mode-dark)\b"""
    )
    private val darkHexColorRegex = Regex(
            """(?i)#(?:141414|1[0-4a-f]{5}|212121|252525|2[0-4a-f]{5}|333333|1e1e1e|1a1a1a|0f0f0f|000000|000)\b"""
    )
    private val darkRgbColorRegex = Regex(
            """(?i)\brgba?\(\s*(?:1?\d{1,2}|2[0-4]\d|25[0-5])\s*,\s*(?:1?\d{1,2}|2[0-4]\d|25[0-5])\s*,\s*(?:1?\d{1,2}|2[0-4]\d|25[0-5])"""
    )
    private val styleAttrRegex = Regex("""(?is)\bstyle\s*=\s*(["'])(.*?)\1""")
    private val classAttrRegex = Regex("""(?is)\bclass\s*=\s*(["'])(.*?)\1""")

    fun sanitizeForAppTheme(html: String?, isNight: Boolean): String? {
        if (html.isNullOrBlank()) return html
        return if (isNight) {
            stripEmbeddedStylesheetsForDark(html)
        } else {
            sanitizeForLightMode(html)
        }
    }

    private fun sanitizeForLightMode(html: String): String {
        var result = html
        result = embeddedStyleBlockRegex.replace(result, "")
        result = embeddedStylesheetLinkRegex.replace(result, "")
        result = styleAttrRegex.replace(result) { match ->
            val quote = match.groupValues[1]
            val cleaned = sanitizeInlineStyle(match.groupValues[2], forLightMode = true)
            if (cleaned.isBlank()) "" else """style=$quote$cleaned$quote"""
        }
        result = classAttrRegex.replace(result) { match ->
            val quote = match.groupValues[1]
            val cleaned = stripDarkClassTokens(match.groupValues[2])
            if (cleaned.isBlank()) "" else """class=$quote$cleaned$quote"""
        }
        return result
    }

    private fun stripEmbeddedStylesheetsForDark(html: String): String =
            embeddedStylesheetLinkRegex.replace(html, "")

    private fun sanitizeInlineStyle(style: String, forLightMode: Boolean): String {
        if (style.isBlank()) return ""
        val parts = style.split(';')
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .filterNot { declaration ->
                    if (!forLightMode) return@filterNot false
                    val name = declaration.substringBefore(':').trim().lowercase()
                    when (name) {
                        "color-scheme" -> true
                        "background", "background-color" -> containsDarkColor(declaration)
                        "color" -> containsDarkTextOnDarkBg(declaration)
                        else -> false
                    }
                }
        return parts.joinToString("; ").trim()
    }

    private fun containsDarkColor(declaration: String): Boolean {
        val value = declaration.substringAfter(':').lowercase()
        if (value.contains("color-scheme") && value.contains("dark")) return true
        return darkHexColorRegex.containsMatchIn(value) ||
                (darkRgbColorRegex.containsMatchIn(value) && looksLikeDarkRgb(value))
    }

    private fun containsDarkTextOnDarkBg(declaration: String): Boolean {
        val value = declaration.substringAfter(':').trim().lowercase()
        if (value == "#fff" || value == "#ffffff" || value == "white") return false
        return darkHexColorRegex.containsMatchIn(value)
    }

    private fun looksLikeDarkRgb(value: String): Boolean {
        val numbers = Regex("""\d+""").findAll(value).map { it.value.toIntOrNull() ?: 255 }.take(3).toList()
        if (numbers.size < 3) return false
        val (r, g, b) = numbers
        return r < 80 && g < 80 && b < 90
    }

    private fun stripDarkClassTokens(classes: String): String =
            classes.split(Regex("\\s+"))
                    .filter { token ->
                        token.isNotBlank() && !darkClassTokenRegex.matches(token)
                    }
                    .joinToString(" ")
}
