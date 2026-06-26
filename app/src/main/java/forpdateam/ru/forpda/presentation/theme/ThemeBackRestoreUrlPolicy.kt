package forpdateam.ru.forpda.presentation.theme

/**
 * Builds a clean back-restore URL: topic st offset plus optional #entry post anchor.
 * Avoids findpost/getnewpost/p= params that make the server redirect away from saved position.
 */
object ThemeBackRestoreUrlPolicy {

    /**
     * HYBRID infinite-scroll reanchor fix (device log 25_06-23-04-44, cross-topic BACK to the source
     * post 143876380 landed on the neighbor 143860995).
     *
     * A back snapshot carries a post id + page scrollY but no trustworthy intra-post offsetTop — the
     * only offsetTop on the restored page is the stale CLICK-TIME value (e.g. 278.44, captured when
     * the user tapped the link with 143876380 scrolled low in the viewport, ratio≈0.759). The JS
     * STEP-3 restore subtracts that as an intra-post offset and places the source post LOW on screen;
     * the post ABOVE it then becomes the topmost-visible post, so the STEP-4 HYBRID top-prepend pins
     * to that neighbor and the scroll drifts onto the wrong post.
     *
     * A snapshot-based back restore that has a real anchor POST must TOP-ALIGN that post (offset
     * cleared → JS uses offset 0, exactly like a findpost open and like the already-working
     * `offset=null` back path), so the source post is the topmost-visible post and the prepend pins
     * it deterministically.
     *
     * @return true when the stale [anchorOffsetTop] must be dropped before a back snapshot restore.
     * Requires a usable anchor post id; with no anchor there is nothing to top-align and the pixel/
     * ratio fallback path is left untouched.
     */
    fun shouldTopAlignBackRestoreAnchor(snapshotVisiblePostId: String?): Boolean =
            !snapshotVisiblePostId?.trim().isNullOrEmpty()

    fun buildRestoreUrl(topicId: Int, st: Int, anchorPostId: String?, pageUrl: String?): String =
            buildRestoreUrl(topicId, st, anchorPostId, pageUrl, nativeSnapshotPostId = null)

    /**
     * B-02: the back-restore URL must keep `#entry<postId>` even when the JS source-anchor
     * has expired (slow network / long read of the target topic). The persisted native back
     * snapshot ([TopicBackSnapshot.visiblePostId]) survives independently of the 15s JS TTL,
     * so it is consulted right after the page anchor and before the (lossy) url-hash fallback.
     *
     * Priority: page anchorPostId → native snapshot post id → url-hash. This preserves the
     * existing ThemeBackRestoreUrlPolicyTest contract (anchor preferred over url-hash, findpost
     * params stripped) — the new snapshot source only fills the gap where anchorPostId is null.
     */
    fun buildRestoreUrl(
            topicId: Int,
            st: Int,
            anchorPostId: String?,
            pageUrl: String?,
            nativeSnapshotPostId: String?
    ): String {
        val cleanUrl = buildCleanThemeUrl(topicId, st)
        val postId = sanitizePostId(anchorPostId)
                ?: sanitizePostId(nativeSnapshotPostId)
                ?: extractEntryPostIdFromUrl(pageUrl)
                ?: return cleanUrl
        return "$cleanUrl#entry$postId"
    }

    private fun sanitizePostId(value: String?): String? =
            value?.removePrefix("entry")?.takeIf { it.isNotEmpty() && it.all(Char::isDigit) }

    fun buildCleanThemeUrl(topicId: Int, st: Int): String {
        val safeSt = if (st < 0) 0 else st
        return if (safeSt > 0) {
            "https://4pda.to/forum/index.php?showtopic=$topicId&st=$safeSt"
        } else {
            "https://4pda.to/forum/index.php?showtopic=$topicId"
        }
    }

    fun extractEntryPostIdFromUrl(url: String?): String? {
        val hash = url?.substringAfter('#', "")?.takeIf { it.isNotBlank() } ?: return null
        return hash.removePrefix("entry").takeIf { it.isNotEmpty() && it.all(Char::isDigit) }
    }

    /**
     * Multi-hop BACK wrong-post fix (log 239158: cached back-remap replayed `prev.url` with the
     * server-landing `#entry126307973` while the authoritative `anchorPostId` had been updated to
     * the scrolled-to source post 126306622). Rewrites the `#entry<postId>` fragment of an existing
     * page url to the authoritative source post so url, anchor and anchorPostId stay self-consistent.
     * Returns the url unchanged when [postId] is blank/non-numeric (never corrupts the url).
     */
    fun replaceEntryHash(url: String?, postId: String?): String? {
        if (url.isNullOrBlank()) return url
        val sanitized = sanitizePostId(postId) ?: return url
        val base = url.substringBefore('#')
        return "$base#entry$sanitized"
    }
}
