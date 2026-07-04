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
    ) : BodyBlock

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
