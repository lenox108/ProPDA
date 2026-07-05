package forpdateam.ru.forpda.ui.fragments.theme.nativerender

import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode

/**
 * Splits the raw HTML of a post body (`.post_body`, i.e. [forpdateam.ru.forpda.entity.remote
 * .theme.ThemePost.body]) into a flat [List] of [BodyBlock]s for the native topic renderer.
 *
 * Roadmap `native-topic-renderer.md`, Фаза 1. This is deliberately a PURE, Android-free,
 * JVM-unit-testable pass (Jsoup only): its whole job is STRUCTURAL SEGMENTATION —
 * decide which slices of the body render natively as text and which fall back to an
 * inline WebView. It does NOT convert HTML to spans (that happens in the view layer at
 * render time) and it does NOT touch network/parsing (`ThemeParser` already produced the
 * body verbatim).
 *
 * ## Segmentation rule (Фаза 1)
 * The renderer walks only the TOP-LEVEL nodes of the body. Each top-level node is either:
 *  - **pure-inline** (its subtree contains no complex block) → appended to the current
 *    inline buffer, so an entire paragraph with bold/links/smiles/lists stays ONE
 *    [BodyBlock.Text] (one Spannable, not fragmented); or
 *  - **complex** (it IS, or CONTAINS, a quote/spoiler/code/attachment/table/poll) → the
 *    inline buffer is flushed as a [BodyBlock.Text] and the whole node is emitted as a
 *    [BodyBlock.WebFallback] with its verbatim outer HTML.
 *
 * Emitting the WHOLE top-level node (not recursing into wrapper `<div align=center>` etc.)
 * is intentional for Фаза 1: it preserves the block's layout/wrapper and never fragments a
 * complex block across the native/fallback boundary. The only cost is that inline text
 * sharing a top-level wrapper with a complex block also goes to fallback — rare, and the
 * exact "complex → fallback" tradeoff §5 Фаза 1 endorses. Nested complex blocks (a spoiler
 * containing code, see the topic-hat fixture) travel together inside their top-level
 * ancestor as a single fallback, which is correct.
 */
class PostBodyRenderer {

    fun render(bodyHtml: String?): List<BodyBlock> {
        if (bodyHtml.isNullOrBlank()) return emptyList()

        // parseBodyFragment keeps the input as a body fragment (no <html>/<head> synthesis
        // beyond the wrapping <body>) and tolerates malformed markup gracefully.
        val body = Jsoup.parseBodyFragment(bodyHtml).body()
        return renderNodes(body.childNodes())
    }

    /**
     * Segments a run of sibling nodes into blocks. Reusable so complex blocks that render natively
     * (quotes) can recursively render their inner content the same way.
     */
    private fun renderNodes(nodes: List<Node>): List<BodyBlock> {
        val blocks = ArrayList<BodyBlock>()
        val inlineBuffer = StringBuilder()

        fun flushInline() {
            val html = inlineBuffer.toString()
            inlineBuffer.setLength(0)
            // Skip runs that are only whitespace / stray newlines between blocks.
            if (html.isNotBlank()) {
                blocks.add(BodyBlock.Text(html))
            }
        }

        for (node in nodes) {
            val complexKind = complexKindOf(node)
            if (complexKind != null) {
                flushInline()
                blocks.addAll(nativeOrFallback(node as Element, complexKind))
            } else {
                inlineBuffer.append(node.outerHtml())
            }
        }
        flushInline()

        return blocks
    }

    /** Maps a complex [element] to its native block(s) where implemented, else the WebView fallback. */
    private fun nativeOrFallback(element: Element, kind: BodyBlock.WebFallback.Kind): List<BodyBlock> {
        // A top-level node classified complex because of a DESCENDANT (e.g. a wrapper div holding an
        // attach table or quote) is not itself the block — only peel when the element IS the block.
        val native: List<BodyBlock>? = when (kind) {
            BodyBlock.WebFallback.Kind.ATTACHMENT -> {
                // A gallery attaches MANY pictures → one Image block each; else a single file link.
                extractAttachmentImages(element).ifEmpty { extractFileAttachment(element)?.let { listOf(it) } }
            }
            BodyBlock.WebFallback.Kind.QUOTE ->
                if (element.hasClass("quote")) listOf(extractQuote(element)) else null
            BodyBlock.WebFallback.Kind.SPOILER ->
                if (element.hasClass("spoil")) listOf(extractSpoiler(element)) else null
            BodyBlock.WebFallback.Kind.CODE ->
                if (element.hasClass("code")) listOf(extractCode(element)) else null
            else -> null
        }
        return native?.takeIf { it.isNotEmpty() }
                ?: listOf(BodyBlock.WebFallback(element.outerHtml(), kind))
    }

    /**
     * Builds a native [BodyBlock.Code] from a `.post-block.code`: `.block-title` label (often empty)
     * and `.block-body` text with `<br>` turned into newlines and HTML entities decoded, so the
     * monospace view shows the code verbatim.
     */
    private fun extractCode(element: Element): BodyBlock.Code {
        val title = element.selectFirst("> .block-title")?.text()?.trim()?.ifBlank { null }
        val bodyEl = element.selectFirst("> .block-body")?.clone()
        val text = if (bodyEl != null) {
            bodyEl.select("br").forEach { it.replaceWith(TextNode("\n")) }
            bodyEl.wholeText().trim('\n')
        } else {
            ""
        }
        return BodyBlock.Code(title = title, text = text)
    }

    /**
     * Builds a native [BodyBlock.Spoiler] from a `.post-block.spoil`: `.block-title` text as the
     * toggle label, `open`/`close` class as the initial state, and the recursively-rendered
     * `.block-body` as [BodyBlock.Spoiler.inner].
     */
    private fun extractSpoiler(element: Element): BodyBlock.Spoiler {
        val title = element.selectFirst("> .block-title")?.text()?.trim()?.ifBlank { null }
        val body = element.selectFirst("> .block-body")
        val inner = if (body != null) renderNodes(body.childNodes()) else emptyList()
        return BodyBlock.Spoiler(
            title = title,
            initiallyOpen = element.hasClass("open"),
            inner = inner,
        )
    }

    /**
     * Builds a native [BodyBlock.Quote] from a `.post-block.quote`: author/date from `.block-title`
     * text ("author @ date"), the snapback href as the source link, and the recursively-rendered
     * `.block-body` as [BodyBlock.Quote.inner] (so nested quotes render natively too).
     */
    private fun extractQuote(element: Element): BodyBlock.Quote {
        val title = element.selectFirst("> .block-title")
        val titleText = title?.ownText()?.trim().orEmpty()
        val author: String?
        val date: String?
        val atIdx = titleText.indexOf(" @ ")
        if (atIdx >= 0) {
            author = titleText.substring(0, atIdx).trim().ifBlank { null }
            date = titleText.substring(atIdx + 3).trim().ifBlank { null }
        } else {
            author = titleText.ifBlank { null }
            date = null
        }
        val sourceUrl = title?.selectFirst("a[href]")?.attr("href")?.takeIf { it.isNotBlank() }
        val body = element.selectFirst("> .block-body")
        val inner = if (body != null) renderNodes(body.childNodes()) else emptyList()
        return BodyBlock.Quote(author = author, date = date, sourceUrl = sourceUrl, inner = inner)
    }

    /**
     * Extracts EVERY attachment picture in [element] as a native [BodyBlock.Image] (a gallery attach
     * has many). Empty if there are no images (e.g. a file link). Reads display dimensions from the
     * img width/height so the view reserves space and a late bitmap never slides the scroll anchor.
     */
    private fun extractAttachmentImages(element: Element): List<BodyBlock.Image> {
        val imgs = element.select("img.attach, img.linked-image")
        return imgs.mapNotNull { img ->
            val src = firstNonBlank(
                img.attr("src"),
                img.attr("data-src"),
                img.attr("data-preview"),
            ) ?: return@mapNotNull null
            // The full-size / download link is the enclosing <a> nearest to this image.
            val linkUrl = (img.closest("a[href]") ?: element.selectFirst("a[href]"))
                    ?.attr("href")?.takeIf { it.isNotBlank() }
            BodyBlock.Image(
                imageUrl = src,
                linkUrl = linkUrl,
                displayWidthPx = img.attr("width").toIntOrZero(),
                displayHeightPx = img.attr("height").toIntOrZero(),
            )
        }
    }

    /**
     * If [element] is (or wraps) a downloadable file link `a.ipb-attach.attach-file`, returns a
     * native [BodyBlock.FileAttachment]; `null` otherwise. Filename = link text, url = href.
     */
    private fun extractFileAttachment(element: Element): BodyBlock.FileAttachment? {
        val link = if (element.normalName() == "a" && element.hasClass("ipb-attach")) {
            element
        } else {
            element.selectFirst("a.ipb-attach")
        } ?: return null
        val url = link.attr("href").takeIf { it.isNotBlank() } ?: return null
        val name = link.text().trim().ifBlank { "Файл" }
        return BodyBlock.FileAttachment(name = name, url = url)
    }

    private fun String.toIntOrZero(): Int = trim().toIntOrNull() ?: 0

    private fun firstNonBlank(vararg values: String?): String? =
        values.firstOrNull { !it.isNullOrBlank() }?.trim()

    /**
     * Returns the [BodyBlock.WebFallback.Kind] if [node] is, or contains, a complex block;
     * `null` if the node's whole subtree is pure-inline (native-renderable).
     */
    private fun complexKindOf(node: Node): BodyBlock.WebFallback.Kind? {
        if (node !is Element) return null
        // The node itself?
        selfKind(node)?.let { return it }
        // A complex descendant? Classify by the first one found (document order).
        val descendant = node.selectFirst(COMPLEX_SELECTOR) ?: return null
        return selfKind(descendant) ?: BodyBlock.WebFallback.Kind.UNKNOWN
    }

    /** Classifies [el] itself (not its descendants); `null` if [el] is not a complex block. */
    private fun selfKind(el: Element): BodyBlock.WebFallback.Kind? {
        val tag = el.normalName()
        // Attachment file link, e.g. <a class="ipb-attach attach-file"> — check before
        // generic table so the attach-picture table below is caught by its id, not "table".
        if (el.hasClass("ipb-attach")) return BodyBlock.WebFallback.Kind.ATTACHMENT
        if (el.hasClass("post-block")) {
            return when {
                el.hasClass("quote") -> BodyBlock.WebFallback.Kind.QUOTE
                el.hasClass("spoil") -> BodyBlock.WebFallback.Kind.SPOILER
                el.hasClass("code") -> BodyBlock.WebFallback.Kind.CODE
                else -> BodyBlock.WebFallback.Kind.UNKNOWN
            }
        }
        // A raw (blocks.js-untransformed) quote can arrive as a bare <blockquote>.
        if (tag == "blockquote") return BodyBlock.WebFallback.Kind.QUOTE
        if (tag == "form" && el.hasClass("topic_poll")) return BodyBlock.WebFallback.Kind.POLL
        if (tag == "table") {
            // 4pda attach-image tables carry id="ipb-attach-table-…"; everything else is a
            // generic table (both fall back in Фаза 1, but the classification differs).
            return if (el.id().startsWith("ipb-attach")) {
                BodyBlock.WebFallback.Kind.ATTACHMENT
            } else {
                BodyBlock.WebFallback.Kind.TABLE
            }
        }
        return null
    }

    private companion object {
        /**
         * Jsoup selector matching any complex block that must go to the WebView fallback in
         * Фаза 1. Kept in sync with [selfKind]. `blockquote` is included because a raw
         * (untransformed by blocks.js) quote can arrive as a bare `<blockquote>` as well as
         * the `.post-block.quote` shape.
         */
        const val COMPLEX_SELECTOR =
            "div.post-block, form.topic_poll, table, .ipb-attach, blockquote"
    }
}
