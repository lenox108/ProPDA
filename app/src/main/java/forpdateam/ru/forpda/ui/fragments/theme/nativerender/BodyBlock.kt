package forpdateam.ru.forpda.ui.fragments.theme.nativerender

/**
 * One renderable segment of a post body (`.post_body`), produced by [PostBodyRenderer].
 *
 * Roadmap `native-topic-renderer.md`, Фаза 1: the native topic renderer walks the raw
 * server HTML of a post body and splits it into a flat list of blocks. In Фаза 1 there
 * are exactly two concrete kinds:
 *  - [Text] — a run of inline/simple HTML rendered natively as a `Spannable` TextView.
 *  - [WebFallback] — a complex block (quote/spoiler/code/attachment/table/poll) rendered
 *    in a small inline WebView until a native view for it lands in later phases.
 *
 * Later phases peel block kinds out of [WebFallback] one at a time (§5 Фаза 2–4); the
 * [WebFallback.kind] classification is what lets them do that incrementally.
 */
sealed interface BodyBlock {

    /**
     * A contiguous run of inline / simple-structural HTML (text, `<b>`, `<i>`, `<a>`,
     * `<img>` smiles, `<ul>/<li>`, `<br>`, `<span style=color>` …). Carries the RAW
     * HTML substring; the HTML→Spannable conversion happens at render time in the view
     * layer (see [forpdateam.ru.forpda.common.HtmlToSpannedConverter]), not here — this
     * class stays free of Android dependencies so it is unit-testable on the JVM.
     */
    data class Text(val html: String) : BodyBlock

    /**
     * The server-appended edit note (`<span class="edit">Сообщение отредактировал … — …</span>` and its
     * optional `.post-edit-reason`) — a SYSTEM meta line, not the user's text. Peeled out so the view can
     * render it smaller and muted (WebView `.edit`: font-size 0.875em, colour #757575) instead of blending
     * into the post body. Carries the raw span HTML (nick link preserved).
     */
    data class EditNote(val html: String) : BodyBlock

    /**
     * A native inline image (attachment picture), peeled out of the fallback in Фаза 2.
     * [displayWidthPx]/[displayHeightPx] come from the server markup (img width/height attrs),
     * so the view can reserve the correct aspect ratio BEFORE the bitmap loads — this is the
     * anti-"async height slides the anchor" measure the roadmap calls for (§2/§6). A value ≤0
     * means unknown (the view falls back to a default aspect).
     * [linkUrl] is the attachment's full-size / download link (tap target).
     */
    data class Image(
        val imageUrl: String,
        val linkUrl: String?,
        val displayWidthPx: Int,
        val displayHeightPx: Int,
        /**
         * true = a CONTENT image inlined in the post body (banner/preview/animated GIF), peeled out of
         * the text flow — rendered at natural width (capped to the column). false = an ATTACHMENT gallery
         * picture, rendered as a compact 150dp thumbnail. Governs sizing in the view layer.
         */
        val inline: Boolean = false,
        /**
         * true = the `<img>` that lived INSIDE an `a.ipb-attach.attach-file` download link (emitted by
         * [PostBodyRenderer.extractDownloadButtons] alongside its [FileAttachment] chip). On 4pda this inner
         * image is EITHER a wide animated «СКАЧАТЬ»/«UPDATE» banner (worth showing) OR a tiny square file-type
         * mime glyph (pure decoration — the legacy WebView hid it with `.ipb-attach.attach-file img{display:none}`,
         * and the chip already names the file). The two are indistinguishable until the bitmap loads, so the
         * view layer decides at load time: keep/enlarge wide banners, hide tiny square glyphs.
         */
        val attachmentButton: Boolean = false,
    ) : BodyBlock

    /**
     * A native quote block (`.post-block.quote`), peeled out of the fallback in Фаза 2.
     * Server structure (confirmed live via logged-in capture): `.block-title` holds
     * "author @ date" text plus a snapback `<a href=findpost>` to the source post; `.block-body`
     * holds the quoted content, which itself may contain nested quotes / images / etc. — hence
     * [inner] is a recursively-rendered block list.
     */
    data class Quote(
        val author: String?,
        val date: String?,
        val sourceUrl: String?,
        val inner: List<BodyBlock>,
    ) : BodyBlock

    /**
     * A native collapsible spoiler (`.post-block.spoil`), peeled out of the fallback in Фаза 2.
     * Server structure (confirmed live): `.block-title` is the toggle header, `.block-body` the
     * collapsible content (may contain nested code/spoilers → [inner] is recursive). The class is
     * `spoil close` (collapsed) or `spoil open` (expanded) → [initiallyOpen].
     */
    data class Spoiler(
        val title: String?,
        val initiallyOpen: Boolean,
        val inner: List<BodyBlock>,
    ) : BodyBlock

    /**
     * A native file attachment (`a.ipb-attach.attach-file`) — a downloadable file link (apk/zip/…).
     * [name] is the filename, [url] the dl/post download link. 4pda emits the file's size «( N МБ )»
     * and an optional «скачиваний: N» download count as SIBLING nodes right after the link; those are
     * folded here into [size] / [desc] (and the siblings removed) so the view can draw one compact
     * Telegram-style row — icon · name / size — instead of the name, size and count spilling onto
     * three stacked lines (user report). Both are null when the markup omitted them. Фаза 2.
     */
    data class FileAttachment(
        val name: String,
        val url: String,
        val size: String? = null,
        val desc: String? = null,
    ) : BodyBlock

    /**
     * A native code block (`.post-block.code`), peeled out of the fallback in Фаза 2. [text] is the
     * decoded code (server `&#91;`→`[`, `<br>`→newline), preserving line breaks for a monospace view.
     */
    data class Code(val title: String?, val text: String) : BodyBlock

    /**
     * A native table (`<table>`), peeled out of the fallback in Фаза 6. [rows] is a row-major grid;
     * each cell holds RAW inline HTML (rendered to a Spannable at view time, like [Text]). Rows may
     * be ragged (different cell counts); the view left-aligns and horizontally scrolls. Merged cells
     * (colspan/rowspan) are NOT modelled — rare, and the cell text is still shown in its origin
     * position, so no content is lost.
     */
    data class Table(val rows: List<List<String>>) : BodyBlock

    /**
     * A complex block rendered via the Фаза-1 inline-WebView fallback. [html] is the
     * block's outer HTML (verbatim server markup); [kind] classifies it so later phases
     * can route specific kinds to native views.
     */
    data class WebFallback(val html: String, val kind: Kind) : BodyBlock {
        enum class Kind {
            QUOTE,
            SPOILER,
            CODE,
            ATTACHMENT,
            POLL,
            TABLE,
            /** Contains a complex descendant but not one of the recognised kinds. */
            UNKNOWN,
        }
    }
}
