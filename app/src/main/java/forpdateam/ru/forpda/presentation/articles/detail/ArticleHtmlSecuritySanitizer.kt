package forpdateam.ru.forpda.presentation.articles.detail

/**
 * Strict sanitizer for network-derived article fragments rendered in a JavaScript-enabled WebView.
 *
 * Network HTML is hardened aggressively: scripts, styles, head injection, plugin objects, unsafe
 * iframes, inline event handlers and dangerous URL schemes are stripped, and arbitrary forms /
 * inputs / buttons (phishing surface) are removed.
 *
 * The article body, however, may already contain trusted app-generated UI baked in by the parser
 * (the normalized poll vote form and the inline YouTube video card with its play button). Those
 * blocks MUST survive sanitization, otherwise the inline player degrades to a bare "open in YouTube"
 * link and the poll renders only its heading. We therefore extract these trusted blocks first,
 * sanitize them with a relaxed rule-set (still removing scripts / handlers / dangerous URLs while
 * keeping the app's own form/input/button markup), run the strict pass on the remaining network
 * content, and finally splice the trusted blocks back in.
 */
object ArticleHtmlSecuritySanitizer {

    fun sanitize(html: String?): String? {
        if (html.isNullOrBlank()) return html
        val trustedBlocks = ArrayList<String>()
        var result = extractTrustedBlocks(html, trustedBlocks)
        result = dangerousBlockRegex.replace(result, "")
        result = objectEmbedRegex.replace(result, "")
        result = unsafeIframeRegex.replace(result, "")
        result = eventHandlerAttributeRegex.replace(result, "")
        result = scrubUrlAttributes(result)
        return restoreTrustedBlocks(result, trustedBlocks)
    }

    private fun scrubUrlAttributes(html: String): String {
        var result = dangerousUrlAttributeRegex.replace(html) { match ->
            val name = match.groupValues[1]
            val value = match.groupValues[3]
            if (isSafeUrlAttribute(name, value)) match.value else ""
        }
        result = unquotedDangerousUrlAttributeRegex.replace(result) { match ->
            val name = match.groupValues[1]
            val value = match.groupValues[2]
            if (isSafeUrlAttribute(name, value)) match.value else ""
        }
        return result
    }

    /**
     * Pulls every trusted app block (normalized poll form, inline video card) out of [html],
     * replacing it with an opaque placeholder token so the strict network pass cannot touch it.
     * Each extracted block is itself lightly sanitized (handlers / scripts / dangerous URLs removed)
     * before being stored for restoration.
     */
    private fun extractTrustedBlocks(html: String, store: MutableList<String>): String {
        if (!trustedBlockMarkerRegex.containsMatchIn(html)) return html
        val builder = StringBuilder(html.length)
        var index = 0
        while (index < html.length) {
            val match = trustedBlockOpenRegex.find(html, index) ?: break
            val end = findBalancedDivEnd(html, match.range.last + 1)
            if (end < 0) {
                // Unbalanced markup: keep scanning past the opening tag, leave content to strict pass.
                builder.append(html, index, match.range.last + 1)
                index = match.range.last + 1
                continue
            }
            builder.append(html, index, match.range.first)
            val block = html.substring(match.range.first, end)
            store.add(sanitizeTrustedBlock(block))
            builder.append(placeholderToken(store.size - 1))
            index = end
        }
        if (index < html.length) builder.append(html, index, html.length)
        return builder.toString()
    }

    private fun restoreTrustedBlocks(html: String, store: List<String>): String {
        if (store.isEmpty()) return html
        var result = html
        store.forEachIndexed { i, block ->
            result = result.replace(placeholderToken(i), block)
        }
        return result
    }

    /** Relaxed pass: keep the app's own form/input/button markup, drop everything executable. */
    private fun sanitizeTrustedBlock(block: String): String {
        var result = block
        result = trustedDangerousBlockRegex.replace(result, "")
        result = objectEmbedRegex.replace(result, "")
        result = unsafeIframeRegex.replace(result, "")
        result = eventHandlerAttributeRegex.replace(result, "")
        result = scrubUrlAttributes(result)
        return result
    }

    private fun findBalancedDivEnd(html: String, fromIndex: Int): Int {
        var depth = 1
        var index = fromIndex
        while (index < html.length && depth > 0) {
            val nextOpen = divOpenTagRegex.find(html, index)
            val nextClose = divCloseTagRegex.find(html, index) ?: return -1
            if (nextOpen != null && nextOpen.range.first < nextClose.range.first) {
                depth++
                index = nextOpen.range.last + 1
            } else {
                depth--
                if (depth == 0) return nextClose.range.last + 1
                index = nextClose.range.last + 1
            }
        }
        return -1
    }

    private fun placeholderToken(index: Int): String = "\u0001FPDA_TRUSTED_BLOCK_${index}\u0001"

    private fun isSafeUrlAttribute(name: String, rawValue: String): Boolean {
        val value = rawValue.trim()
        if (value.isBlank()) return true
        val decoded = value
                .replace("&colon;", ":", ignoreCase = true)
                .replace("&#58;", ":")
                .replace("&#x3a;", ":", ignoreCase = true)
                .trimStart()
        val lower = decoded.lowercase()
        if (lower.startsWith("javascript:") ||
                lower.startsWith("vbscript:") ||
                lower.startsWith("file:") ||
                lower.startsWith("content:")) {
            return false
        }
        if (!lower.startsWith("data:")) return true
        return name.equals("src", ignoreCase = true) && safeImageDataUrlRegex.matches(lower)
    }

    private val dangerousBlockRegex = Regex(
            """(?is)<\s*(script|style|link|meta|base|form|input|button|textarea|select|option|noscript)\b[^>]*>[\s\S]*?<\s*/\s*\1\s*>|<\s*(script|style|link|meta|base|input)\b[^>]*?/?>"""
    )
    /** Trusted blocks keep form/input/button; only executable / head-injection tags are removed. */
    private val trustedDangerousBlockRegex = Regex(
            """(?is)<\s*(script|style|link|meta|base|noscript)\b[^>]*>[\s\S]*?<\s*/\s*\1\s*>|<\s*(script|style|link|meta|base)\b[^>]*?/?>"""
    )
    private val objectEmbedRegex = Regex("""(?is)<\s*(object|embed|applet)\b[^>]*>[\s\S]*?<\s*/\s*\1\s*>|<\s*(object|embed|applet)\b[^>]*?/?>""")
    private val unsafeIframeRegex = Regex("""(?is)<iframe\b[^>]*\bsrc\s*=\s*(["']?)\s*(?:javascript:|vbscript:|data:|file:|content:)[\s\S]*?</iframe>|<iframe\b(?![^>]*\bsrc\s*=\s*["']?https?://)[^>]*>[\s\S]*?</iframe>""")
    private val eventHandlerAttributeRegex = Regex("""(?is)\s+on[a-z0-9_-]+\s*=\s*(?:"[^"]*"|'[^']*'|[^\s>]+)""")
    private val dangerousUrlAttributeRegex = Regex("""(?is)\s+(href|src|poster|data|action|formaction|xlink:href)\s*=\s*(["'])(.*?)\2""")
    private val unquotedDangerousUrlAttributeRegex = Regex("""(?is)\s+(href|src|poster|data|action|formaction|xlink:href)\s*=\s*([^\s"'=<>`]+)""")
    private val safeImageDataUrlRegex = Regex("""data:image/(?:png|jpe?g|gif|webp);base64,[a-z0-9+/=\s]+""")

    private val divOpenTagRegex = Regex("""(?is)<div\b""")
    private val divCloseTagRegex = Regex("""(?is)</div\s*>""")
    private val trustedBlockMarkerRegex = Regex("""(?is)\b(?:news-video-card|news-poll|poll-ajax-frame|data-poll-fallback|data-normalized-poll)\b""")
    private val trustedBlockOpenRegex = Regex(
            """(?is)<div\b[^>]*?(?:\bclass\s*=\s*["'][^"']*\b(?:news-video-card|news-poll)\b[^"']*["']|\bid\s*=\s*["'][^"']*\bpoll-ajax-frame[^"']*["']|\bdata-(?:poll-fallback|normalized-poll)\s*=)[^>]*>"""
    )
}
