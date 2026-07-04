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

        for (node in body.childNodes()) {
            val complexKind = complexKindOf(node)
            if (complexKind != null) {
                flushInline()
                val element = node as Element
                // Фаза 2: peel single-image attachments out of the fallback into a native Image
                // block. Anything else (galleries, files, quotes, spoilers, code, tables, polls)
                // still falls back verbatim.
                val image = if (complexKind == BodyBlock.WebFallback.Kind.ATTACHMENT) {
                    extractSingleAttachmentImage(element)
                } else {
                    null
                }
                blocks.add(image ?: BodyBlock.WebFallback(element.outerHtml(), complexKind))
            } else {
                inlineBuffer.append(node.outerHtml())
            }
        }
        flushInline()

        return blocks
    }

    /**
     * If [element] is an attachment block wrapping EXACTLY ONE picture, returns a native
     * [BodyBlock.Image]; otherwise `null` (galleries / file attachments stay as fallback).
     * Reads display dimensions from the img width/height attributes so the view can reserve
     * space and not slide the scroll anchor when the bitmap arrives.
     */
    private fun extractSingleAttachmentImage(element: Element): BodyBlock.Image? {
        val imgs = element.select("img.attach, img.linked-image")
        if (imgs.size != 1) return null
        val img = imgs.first() ?: return null
        val src = firstNonBlank(
            img.attr("src"),
            img.attr("data-src"),
            img.attr("data-preview"),
        ) ?: return null
        // Prefer the enclosing attachment <a href> (full-size / download link) as the tap target.
        val linkUrl = element.selectFirst("a[href]")?.attr("href")?.takeIf { it.isNotBlank() }
        return BodyBlock.Image(
            imageUrl = src,
            linkUrl = linkUrl,
            displayWidthPx = img.attr("width").toIntOrZero(),
            displayHeightPx = img.attr("height").toIntOrZero(),
        )
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
