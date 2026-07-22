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

    /**
     * Srcs of the service glyphs (`alt="*"`) found in the CURRENT body. 4pda's quote «snapback» arrow is
     * emitted TWICE: once as `<a act=findpost><img alt="*" src=…snapback.gif></a>` (a real service icon,
     * caught by [isServiceIcon]) and once as a bare `<img alt="Изображение" src=…snapback.gif>` in a plain
     * div — the generic alt slips past the icon filter and the duplicate balloons into a big blurry arrow
     * (user report). We can't blanket-drop `alt="Изображение"` (real screenshots use it too), so we treat
     * any image whose src ALSO appears as an `alt="*"` icon as the same service sprite and strip it.
     */
    private var serviceIconSrcs: Set<String> = emptySet()

    fun render(bodyHtml: String?): List<BodyBlock> {
        if (bodyHtml.isNullOrBlank()) return emptyList()

        // parseBodyFragment keeps the input as a body fragment (no <html>/<head> synthesis
        // beyond the wrapping <body>) and tolerates malformed markup gracefully.
        val body = Jsoup.parseBodyFragment(bodyHtml).body()
        // 4pda ships inline <script> next to a linked image (e.g. `fix_linked_img_thumb("35798713-bb",
        // 1080,2376,…)`, which resizes the thumbnail in a real browser/WebView). The native renderer has
        // no JS engine — Html.fromHtml drops the <script> TAG but keeps its text content, so the raw JS
        // call leaks as a visible caption under the image (user report, QMS chat). Neither <script> nor
        // <style> carries body content, so strip both up-front before segmentation.
        body.select("script, style").forEach { it.remove() }
        normalizeOfftopFonts(body)
        foldAttachmentMeta(body)
        serviceIconSrcs = body.select("img").asSequence()
                .filter { it.attr("alt").trim() == "*" }
                .map { it.attr("src").trim() }
                .filter { it.isNotEmpty() }
                .toSet()
        linkReplyToNicks(body)
        return renderNodes(body.childNodes())
    }

    /**
     * `[offtop]` on 4pda is an INLINE `<font style="font-size:9px;color:gray;">…</font>` — a small, muted
     * "by the way" aside (confirmed on the BB-code reference and live posts; it is NOT a spoiler/block). The
     * native text path renders via [android.text.Html.fromHtml], which honours inline `color` (USE_CSS_COLORS)
     * but IGNORES inline `font-size` — so the aside came out at full body size, indistinguishable from normal
     * text (user report «тег оффтоп не применяется»). Rewrite the offtop font to a `<small>` — which
     * Html.fromHtml DOES render at 0.8× — keeping the muted grey, so the aside reads small + de-emphasised
     * wherever it sits: bare in the flow, or nested inside a quote/spoiler (this runs on the whole body before
     * segmentation, so every path — Text, native quote/spoiler inner, WebView fallback — gets the transform).
     */
    private fun normalizeOfftopFonts(root: Element) {
        for (el in root.select("[style]")) {
            val style = el.attr("style").replace(" ", "").lowercase()
            // 9px is 4pda's offtop size marker (size=1 is 8pt, a different span); pair it with the grey to be
            // sure this is the offtop font and not some other inline sizing.
            if (!(style.contains("font-size:9px") && style.contains("color:gray"))) continue
            // Wrap as <small><span style="color:#hex">…</span></small>:
            //  • <small> → Html.fromHtml renders 0.8× (it IGNORES the CSS font-size, so the aside came out
            //    full-size — the reported «тег оффтоп не применяется»).
            //  • inner <span style> → the muted grey. Html.fromHtml applies a style `color` on <span>/block
            //    tags but NOT on <small>, and its USE_CSS_COLORS does NOT parse the NAMED colour `gray` (only
            //    #hex) — hence the explicit #808080 (keeps >2.5 contrast on light AND dark, so the
            //    low-contrast filter never strips it).
            el.tagName("span")
            el.attr("style", "color:#808080")
            val small = org.jsoup.nodes.Element("small")
            el.replaceWith(small)
            small.appendChild(el)
        }
    }

    /**
     * 4pda renders a download link as `…<a class="ipb-attach attach-file">name.zip</a>&nbsp;( 12,3 МБ )
     * <span class="desc">скачиваний: 421</span><br>`. The size and download-count live in SIBLING
     * nodes, so natively they land in a separate [BodyBlock.Text] BELOW the chip — the name, size and
     * count spread across three stacked lines («не аккуратно и размашисто», user report). Fold that
     * metadata INTO the anchor as data-attrs the chip reads, and REMOVE the consumed siblings so the
     * view draws one compact Telegram-style row. Runs on the whole body BEFORE segmentation, so the
     * child-list mutation never races the [renderNodes] iteration.
     *
     * Conservative on both edges: only a size at the very START of the following text is consumed (any
     * prose before it means the "(…)" is not this file's size and is left alone); if prose FOLLOWS the
     * size in the same text node, only the size substring is stripped and the prose survives.
     */
    private fun foldAttachmentMeta(root: Element) {
        for (link in root.select("a.ipb-attach.attach-file, a.ipb-attach:not(.attach-img):not(.attach-image)")) {
            var size: String? = null
            var desc: String? = null
            var sib = link.nextSibling()
            while (sib != null) {
                val next = sib.nextSibling()
                when {
                    sib is TextNode -> {
                        if (sib.isBlank) { sib.remove(); sib = next; continue }
                        val raw = sib.wholeText
                        val m = ATTACH_SIZE.find(raw)
                        if (size == null && m != null && raw.substring(0, m.range.first).isBlank()) {
                            size = m.groupValues[1].replace(Regex("\\s+"), " ").trim()
                            val rest = raw.substring(m.range.last + 1)
                            if (rest.isBlank()) { sib.remove() } else { sib.text(rest); break }
                            sib = next
                            continue
                        }
                        break // real prose — stop, leave it as body text
                    }
                    sib is Element && sib.hasClass("desc") -> {
                        desc = sib.text().trim().ifBlank { null }
                        sib.remove()
                        sib = next
                        continue
                    }
                    else -> break // <br>, another block, etc.
                }
            }
            if (size != null) link.attr(ATTACH_SIZE_ATTR, size)
            if (desc != null) link.attr(ATTACH_DESC_ATTR, desc)
        }
    }

    /**
     * Segments a run of sibling nodes into blocks. Reusable so complex blocks that render natively
     * (quotes) can recursively render their inner content the same way.
     */
    private fun renderNodes(nodes: List<Node>): List<BodyBlock> {
        val blocks = ArrayList<BodyBlock>()
        val inlineBuffer = StringBuilder()

        fun flushInline() {
            // Content <img> were already peeled into Image blocks, so any <img> left in the inline flow is a
            // decorative service/smiley glyph. Html.fromHtml has no ImageGetter → it would render an ugly
            // placeholder box (the reported little green square). Drop them.
            var html = SERVICE_IMG.replace(inlineBuffer.toString(), "")
            inlineBuffer.setLength(0)
            // Authors are inconsistent about putting a <br>/newline right before or after a quote/spoiler/code
            // block. That stray break lands at the START or END of this inline run and renders as a phantom
            // blank line ON TOP OF the block's own 6dp margin, so the very same two blocks show a bigger gap in
            // one post than in another (user report: «после блоков спойлер/цитирование иногда лишний отступ»).
            // Trim breaks at the run edges so every block boundary spaces uniformly; a <br> WITHIN the prose is
            // a deliberate line break and is left untouched. A run that was ONLY edge-breaks collapses to blank
            // and is dropped by the isNotBlank guard below (same as the pure-whitespace case it already handled).
            html = EDGE_BREAKS.replace(html, "")
            // Skip runs that are only whitespace / stray newlines between blocks.
            if (html.isNotBlank()) {
                blocks.add(BodyBlock.Text(html))
            }
        }

        for (node in nodes) {
            // 4pda sometimes serves hat / spoiler content with its OWN <a>/<img>/<br> markup HTML-ESCAPED
            // (it arrives as a text node whose text literally reads `<a title="Ссылка" href="…">…</a>` and
            // shows raw). A normal link is an Element, never a TextNode — so a TextNode that contains an
            // anchor/image tag can only be escaped source markup. Re-parse it as HTML so it renders like the
            // sibling entries that arrived as real elements (links + inline images).
            if (node is TextNode && looksLikeEscapedMarkup(node.wholeText)) {
                flushInline()
                blocks.addAll(renderNodes(Jsoup.parseBodyFragment(node.wholeText).body().childNodes()))
                continue
            }
            // The server edit note (`.edit` / `.post-edit-reason`) is a SYSTEM meta line — peel it into
            // its own block so the view can render it muted/smaller rather than as body text.
            if (node is Element && (node.hasClass("edit") || node.hasClass("post-edit-reason"))) {
                flushInline()
                blocks.add(BodyBlock.EditNote(node.outerHtml()))
                continue
            }
            val complexKind = complexKindOf(node)
            if (complexKind != null) {
                flushInline()
                val element = node as Element
                if (wrapsProseAroundComplexBlock(element)) {
                    // The node is not itself the complex block — it merely WRAPS one, together with prose
                    // (4pda puts an attach table / `a.ipb-attach` right inside the div that also holds the
                    // typed text, most visibly in QMS messages). Peeling the whole node natively keeps only
                    // the attachment and silently drops that prose — the reported «текст обрезается, если к
                    // сообщению прикреплена картинка». Segment the wrapper's children instead: the text
                    // survives as its own block and the complex descendant is peeled at its own level.
                    blocks.addAll(renderNodes(element.childNodes()))
                } else {
                    blocks.addAll(nativeOrFallback(element, complexKind))
                }
            } else if (containsContentImage(node)) {
                // The inline flow carries a CONTENT <img> (banner / preview / animated «UPDATE» gif). Html.fromHtml
                // has no ImageGetter, so an inline image would be silently DROPPED — peel each one into its own
                // Image block, splitting the surrounding text around it. (Smilies stay inline; see isSmiley.)
                explodeInlineImages(node, blocks, inlineBuffer, ::flushInline)
            } else {
                inlineBuffer.append(node.outerHtml())
            }
        }
        flushInline()

        return blocks
    }

    /**
     * «Ответ без цитаты» приходит как крошечная snapback-стрелка `<a href=…findpost…><img alt="*"></a>`,
     * за которой идёт жирный ник `<b>Ник,</b>`. Сама стрелка в нативе вырезается как service-иконка
     * ([isServiceIcon]/[flushInline]), и от якоря остаётся ПУСТОЙ `<a>` — тапать нечего, а ник — просто
     * `<b>`-текст без ссылки. В шапке цитаты имя кликабельно и якорит к посту ([BodyBlockViewFactory]
     * quoteView), а тут — нет (репорт пользователя). Переносим ник ВНУТРЬ snapback-якоря, чтобы тап по
     * имени вёл к исходному посту тем же путём (findpost → [LinkHandler] → Theme explicit_post).
     */
    private fun linkReplyToNicks(body: Element) {
        for (anchor in body.select("a[href]")) {
            // Шапка цитаты и прочие сложные блоки обрабатываются отдельно (extractQuote) — не трогаем.
            if (anchor.closest(COMPLEX_SELECTOR) != null) continue
            // Snapback-якорь = обёртка вокруг служебной стрелки, без собственного видимого текста.
            val wrapsArrow = anchor.select("img").any { it.isServiceIcon() }
            if (!wrapsArrow || anchor.text().isNotBlank()) continue
            // Ник — ближайший следующий элемент-сосед, обычно `<b>Ник,</b>`.
            val nick = anchor.nextElementSibling() ?: continue
            if (nick.normalName() != "b" && nick.normalName() != "strong") continue
            // Между стрелкой и ником допустимы только пробельные текст-ноды.
            var between = anchor.nextSibling()
            var onlyWhitespace = true
            while (between != null && between !== nick) {
                if (between !is TextNode || between.wholeText.isNotBlank()) { onlyWhitespace = false; break }
                between = between.nextSibling()
            }
            if (!onlyWhitespace) continue
            anchor.appendChild(nick) // move the nick into the snapback anchor → the name becomes tappable
        }
    }

    /** True if [node]'s subtree holds a real content image (banner / preview / attach thumbnail), not a smiley. */
    private fun containsContentImage(node: Node): Boolean {
        if (node !is Element) return false
        val imgs = if (node.normalName() == "img") listOf(node) else node.select("img")
        return imgs.any { it.isContentImage() }
    }

    // Any non-smiley <img> that reaches the inline flow is peeled into an Image block. This INCLUDES the
    // attach thumbnails 4pda drops loose into post text — <img class="linked-image"> / <img class="attach">
    // (e.g. the icon-pack screenshots and the animated «СКАЧАТЬ» gif wrapped in a source-post link). Those
    // are NOT inside an ipb-attach table/`.ipb-attach` wrapper (those are peeled as an ATTACHMENT block
    // BEFORE this runs), so without peeling them here Html.fromHtml — which has no ImageGetter — would
    // silently drop them and the post would show only its title + spoilers («в приложении картинок нет»).
    private fun Element.isContentImage(): Boolean =
        normalName() == "img" && !isSmiley() && !isServiceIcon()

    /**
     * 4pda decorates posts with tiny service glyphs: the quote / «reply-to» snapback arrow
     * (`<img alt="*" title="Перейти к сообщению">`) and the «Прикрепленный файл» indicator gif shown inline
     * before an attach block. They are decorations, not content — peeling one into a block Image blew a
     * ~13px icon up into a big pixelated picture on every search-result post (user report). Match them by their
     * distinctive `alt`; the inline flush then strips them so they don't leave an Html.fromHtml placeholder box
     * (the little green square) either. NB: this is `alt="Прикрепленный файл"` (the indicator), NOT the real
     * attach thumbnail `alt="Прикрепленное изображение"` (a `linked-image` on the same /s/ path) — that stays
     * content, so we key on the exact alt, not the src path.
     */
    private fun Element.isServiceIcon(): Boolean {
        val alt = attr("alt").trim()
        // The `alt="Изображение"` twin of the snapback arrow shares its src with the `alt="*"` icon → strip it.
        val src = attr("src").trim()
        if (src.isNotEmpty() && src in serviceIconSrcs) return true
        return alt == "*" || alt.equals("Прикрепленный файл", ignoreCase = true)
    }

    /**
     * 4pda forum bodies deliver emoticons as text shortcodes (rendered by SmileProvider), but be defensive
     * against `<img>`-form smilies (tiny, or a smiles/emoticon src) so they never balloon into block images.
     */
    private fun Element.isSmiley(): Boolean {
        val src = (attr("src") + " " + attr("data-src")).lowercase()
        if (src.contains("smile") || src.contains("emoticon") || src.contains("style_emoticons") || src.contains("/sm/")) return true
        val w = attr("width").toIntOrNull(); val h = attr("height").toIntOrNull()
        return w != null && h != null && w in 1..40 && h in 1..40
    }

    /**
     * Walks [node] emitting a [BodyBlock.Image] for each content `<img>` and flushing the inline text buffer
     * around it. Wrapper formatting on split text is intentionally dropped (same tradeoff as the complex→
     * fallback boundary) — the priority is that the image renders. The enclosing `<a href>` becomes the tap link.
     */
    private fun explodeInlineImages(node: Node, blocks: MutableList<BodyBlock>, inline: StringBuilder, flush: () -> Unit) {
        if (node is Element && node.normalName() == "img") {
            val image = node.toContentImageOrNull()
            if (image != null) {
                flush()
                blocks.add(image)
            } else {
                inline.append(node.outerHtml()) // smiley / srcless → leave inline (handled/ignored as before)
            }
            return
        }
        if (node is Element && node.selectFirst("img") != null) {
            for (child in node.childNodes()) explodeInlineImages(child, blocks, inline, flush)
            return
        }
        inline.append(node.outerHtml())
    }

    private fun Element.toContentImageOrNull(): BodyBlock.Image? {
        if (!isContentImage()) return null
        val src = firstNonBlank(attr("src"), attr("data-src"), attr("data-preview")) ?: return null
        val link = closest("a[href]")?.attr("href")?.takeIf { it.isNotBlank() }
        return BodyBlock.Image(
            imageUrl = src,
            linkUrl = link,
            displayWidthPx = attr("width").toIntOrZero(),
            displayHeightPx = attr("height").toIntOrZero(),
            inline = true,
        )
    }

    /** Maps a complex [element] to its native block(s) where implemented, else the WebView fallback. */
    private fun nativeOrFallback(element: Element, kind: BodyBlock.WebFallback.Kind): List<BodyBlock> {
        // A top-level node classified complex because of a DESCENDANT (e.g. a wrapper div holding an
        // attach table or quote) is not itself the block — only peel when the element IS the block.
        val native: List<BodyBlock>? = when (kind) {
            BodyBlock.WebFallback.Kind.ATTACHMENT -> {
                // A gallery attaches MANY pictures → one Image block each; plus EVERY downloadable file
                // link (a post can list several — e.g. a spoiler `<ol>` of «Themes.rar / …zip» download
                // buttons, each an <a class="ipb-attach attach-file"> wrapping an animated gif). Collect
                // them all, not just the first, so none silently vanish.
                (extractAttachmentImages(element) + extractDownloadButtons(element)).takeIf { it.isNotEmpty() }
            }
            BodyBlock.WebFallback.Kind.QUOTE ->
                if (element.hasClass("quote")) listOf(extractQuote(element)) else null
            BodyBlock.WebFallback.Kind.SPOILER ->
                if (element.hasClass("spoil")) listOf(extractSpoiler(element)) else null
            BodyBlock.WebFallback.Kind.HIDDEN ->
                if (element.hasClass("hidden")) listOf(extractHidden(element)) else null
            BodyBlock.WebFallback.Kind.CODE ->
                if (element.hasClass("code")) listOf(extractCode(element)) else null
            BodyBlock.WebFallback.Kind.TABLE -> {
                val tableEl = if (element.tagName() == "table") element else element.selectFirst("table")
                tableEl?.let { extractTable(it) }?.let { listOf(it) }
            }
            else -> null
        }
        return native?.takeIf { it.isNotEmpty() }
                ?: listOf(BodyBlock.WebFallback(element.outerHtml(), kind))
    }

    /**
     * Builds a native [BodyBlock.Table] from a `<table>`: each `<tr>` becomes a row, each `<td>`/`<th>`
     * a cell holding its inner HTML (rendered as a Spannable in the view). Returns null for a table
     * with no cells (so it falls back rather than showing an empty grid).
     */
    private fun extractTable(table: Element): BodyBlock.Table? {
        val rows = table.select("tr").map { tr ->
            tr.select("> td, > th").map { it.html().trim() }
        }.filter { it.isNotEmpty() }
        return if (rows.isEmpty()) null else BodyBlock.Table(rows)
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
     * Builds a native [BodyBlock.Hidden] from a `.post-block.hidden` («Скрытый текст»): its `.block-body`
     * is rendered RECURSIVELY (same pass as spoiler/quote) so an attachment image / table nested inside it
     * becomes a native [BodyBlock.Image]/[BodyBlock.Table] — instead of the whole block falling to the
     * WebView text fallback, whose Html.fromHtml drops the image and leaves an empty box (user report).
     */
    private fun extractHidden(element: Element): BodyBlock.Hidden {
        val body = element.selectFirst("> .block-body")
        val inner = if (body != null) renderNodes(body.childNodes()) else emptyList()
        return BodyBlock.Hidden(inner = inner)
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
        // A QMS attachment IS the <img class="ipb-attach attach-img"> itself — no wrapping table or
        // anchor like a forum post has (verified against a live dialog). `select` only walks
        // DESCENDANTS and only knew the post-side classes, so such a picture matched nothing here and
        // fell through to the WebView fallback, which renders as an empty grey strip in the bubble.
        val imgs = if (element.normalName() == "img") {
            listOf(element)
        } else {
            element.select("img.attach, img.linked-image, img.attach-img, img.ipb-attach")
        }
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
     * EVERY downloadable file link `a.ipb-attach.attach-file` in [element] (or [element] itself when it IS
     * such a link) as native blocks. On 4pda each of these is a DOWNLOAD BUTTON: an animated «UPDATE /
     * СКАЧАТЬ» gif wrapped in the file link, followed by the filename text. We render BOTH so it matches the
     * site — the gif as a tappable [BodyBlock.Image] (its [BodyBlock.Image.linkUrl] is the download href;
     * since that href is a file, not a viewable image, the view layer routes the tap to the download
     * handler) PLUS a filename [BodyBlock.FileAttachment] chip. A post can list several (e.g. a spoiler
     * `<ol>` of «Themes.rar / …zip»), so we return ALL, not just the first (previously the rest, and the
     * gif itself, silently vanished — «в приложении гифкнопки с ссылкой на загрузку нет»).
     */
    private fun extractDownloadButtons(element: Element): List<BodyBlock> {
        val links = if (element.normalName() == "a" && element.hasClass("ipb-attach")) {
            listOf(element)
        } else {
            element.select("a.ipb-attach.attach-file, a.ipb-attach:not(.attach-img):not(.attach-image)")
        }
        return links.flatMap { link ->
            val url = link.attr("href").takeIf { it.isNotBlank() } ?: return@flatMap emptyList<BodyBlock>()
            val out = ArrayList<BodyBlock>(2)
            val img = link.selectFirst("img")
            val gif = img?.let { firstNonBlank(it.attr("data-src"), it.attr("src"), it.attr("data-preview")) }
            if (gif != null) {
                out.add(
                    BodyBlock.Image(
                        imageUrl = gif,
                        linkUrl = url,
                        displayWidthPx = img.attr("width").toIntOrZero(),
                        displayHeightPx = img.attr("height").toIntOrZero(),
                        inline = true,
                        // A wide «СКАЧАТЬ» banner stays; a tiny square file-type mime glyph is hidden at
                        // load time (the chip below already names the file) — see BodyBlock.Image.attachmentButton.
                        attachmentButton = true,
                    )
                )
            }
            out.add(
                BodyBlock.FileAttachment(
                    name = link.text().trim().ifBlank { "Файл" },
                    url = url,
                    // Folded from the trailing «( N МБ )» / «.desc» siblings by foldAttachmentMeta.
                    size = link.attr(ATTACH_SIZE_ATTR).takeIf { it.isNotBlank() },
                    desc = link.attr(ATTACH_DESC_ATTR).takeIf { it.isNotBlank() },
                )
            )
            out
        }
    }

    private fun String.toIntOrZero(): Int = trim().toIntOrNull() ?: 0

    private fun firstNonBlank(vararg values: String?): String? =
        values.firstOrNull { !it.isNullOrBlank() }?.trim()

    /**
     * True if [text] (an already-decoded text node) literally reads as an HTML anchor/image tag — the
     * signature of 4pda markup delivered HTML-escaped. Requires a STRUCTURED tag with href/src, so a stray
     * "<br>" or "<3" in ordinary prose is never re-parsed. Safe because real links/images are Elements —
     * only escaped source markup ever lands inside a text node.
     */
    private fun looksLikeEscapedMarkup(text: String): Boolean =
        (text.contains("<a ") && text.contains("href=")) || (text.contains("<img ") && text.contains("src="))

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

    /**
     * True when [el] is NOT itself a complex block but wraps one together with visible prose, so peeling it
     * as a single block would throw that prose away. Whitespace-only wrappers (`<div class="attach">` holding
     * nothing but the attach anchor) stay on the peel path, keeping the existing single-block behaviour.
     */
    private fun wrapsProseAroundComplexBlock(el: Element): Boolean {
        if (selfKind(el) != null) return false
        val stripped = el.clone()
        stripped.select(COMPLEX_SELECTOR).forEach { it.remove() }
        return stripped.text().isNotBlank()
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
                el.hasClass("hidden") -> BodyBlock.WebFallback.Kind.HIDDEN
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

        /** Any `<img …>` tag — used to strip decorative service/smiley glyphs from inline text (they can't
         *  render via Html.fromHtml and would leave a placeholder box). Content images are peeled out first. */
        val SERVICE_IMG = Regex("<img\\b[^>]*>", RegexOption.IGNORE_CASE)

        /**
         * Leading OR trailing run of whitespace and `<br>` tags at the edge of an inline text run. Stripped in
         * [flushInline] so a stray line break an author put right before/after a quote/spoiler block does not
         * add a phantom blank line on top of the block's margin (uniform block spacing). Both alternatives are
         * edge-anchored (`^…` / `…$`), so a single [Regex.replace] trims both ends and never touches a `<br>`
         * that sits between words (a deliberate in-paragraph line break).
         */
        val EDGE_BREAKS = Regex("^(?:\\s|<br\\s*/?>)+|(?:\\s|<br\\s*/?>)+$", RegexOption.IGNORE_CASE)

        /**
         * A parenthesised file size right after a download link: «( 12,3 МБ )», «(188.33 МБ)», «(500 Б)».
         * Group 1 is the size WITHOUT the parens. Units are the Cyrillic Б/КБ/МБ/ГБ/ТБ 4pda emits (also the
         * Latin B/KB/… as a courtesy). Anchored by [foldAttachmentMeta] to the START of the sibling text so a
         * stray "(…)" mid-sentence is never mistaken for a size. See [foldAttachmentMeta].
         */
        val ATTACH_SIZE = Regex(
            "\\(\\s*([0-9][0-9.,]*\\s*(?:[ТГМК]?Б|[TGMK]?i?B|байт))\\s*\\)",
            RegexOption.IGNORE_CASE,
        )

        /** data-attr keys foldAttachmentMeta stows the folded size / download-count on, read by extractDownloadButtons. */
        const val ATTACH_SIZE_ATTR = "data-native-size"
        const val ATTACH_DESC_ATTR = "data-native-desc"
    }
}
