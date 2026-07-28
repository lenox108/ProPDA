package forpdateam.ru.forpda.ui.fragments.theme.nativerender

/**
 * The ONE traversal that defines what «найти на странице» sees inside a post body, shared by the
 * host that builds the match index ([forpdateam.ru.forpda.ui.fragments.theme.nativerender.NativeTopicFragment])
 * and the renderer that paints the highlights ([BodyBlockViewFactory]).
 *
 * Why it must be shared: navigation is occurrence-based, and an occurrence is identified by its
 * ORDINAL within the post — the host says «подсветь 3-е вхождение в посте 12345», the renderer counts
 * occurrences as it lays blocks out and paints the third one as active. That only works while both
 * sides walk the body in the SAME order and consider the SAME text searchable, so both call
 * [forEachUnit]: the host to count, the renderer (implicitly, by rendering in document order) to paint.
 *
 * A «unit» is one run of text that the renderer highlights in a single TextView. Blocks that render no
 * highlightable text (images, the spoiler/quote headers, the system edit note) contribute nothing and
 * are skipped by BOTH sides — counting text the renderer never paints would shift every later ordinal
 * and send ↑/↓ to the wrong place.
 */
object TopicSearchScan {

    /** Number of case-insensitive, non-overlapping [query] occurrences in [text]. */
    fun countIn(text: CharSequence, query: String): Int {
        if (query.isEmpty()) return 0
        val hay = text.toString()
        var count = 0
        var i = hay.indexOf(query, ignoreCase = true)
        while (i >= 0) {
            count++
            i = hay.indexOf(query, i + query.length, ignoreCase = true)
        }
        return count
    }

    /**
     * Visits every searchable text unit of [blocks] in render order (document order, recursing into
     * quotes / spoilers / hidden blocks exactly where the renderer does).
     *
     * [html] converts a markup run to its rendered characters — the caller passes the renderer's own
     * parse so the two sides agree character-for-character (see [BodyBlockViewFactory.plainForSearch]).
     */
    fun forEachUnit(blocks: List<BodyBlock>, html: (String) -> CharSequence, visit: (CharSequence) -> Unit) {
        for (block in blocks) when (block) {
            is BodyBlock.Text -> visit(html(block.html))
            is BodyBlock.WebFallback -> visit(html(block.html))
            is BodyBlock.Code -> visit(block.text)
            is BodyBlock.FileAttachment -> visit(block.name)
            is BodyBlock.Table -> block.rows.forEach { row -> row.forEach { visit(html(it)) } }
            is BodyBlock.Quote -> forEachUnit(block.inner, html, visit)
            is BodyBlock.Spoiler -> forEachUnit(block.inner, html, visit)
            is BodyBlock.Hidden -> forEachUnit(block.inner, html, visit)
            // Rendered without a highlight pass: a picture has no text, and the edit note is system meta
            // (the WebView never found it either).
            is BodyBlock.Image, is BodyBlock.EditNote -> Unit
        }
    }

    /** Total occurrences of [query] across [blocks] — the post's contribution to the «k/N» counter. */
    fun countInBlocks(blocks: List<BodyBlock>, query: String, html: (String) -> CharSequence): Int {
        if (query.isEmpty()) return 0
        var total = 0
        forEachUnit(blocks, html) { total += countIn(it, query) }
        return total
    }
}
