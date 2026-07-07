package forpdateam.ru.forpda.ui.fragments.theme.nativerender

/**
 * The immutable per-post view model consumed by the native topic adapter (roadmap
 * `native-topic-renderer.md`, Фаза 1). Produced by [NativePostMapper] from the parsed
 * [forpdateam.ru.forpda.entity.remote.theme.ThemePost] — parsing/network are untouched.
 *
 * [postId] is the STABLE id: it is both the RecyclerView `getItemId` (so adapter diffs and
 * animations are stable) and the anchor key used by [NativeAnchorResolver] (anchoring is by
 * post id, never by pixel offset — §2/§6). Header/footer fields are carried verbatim; the
 * body is pre-segmented into [blocks] by [PostBodyRenderer]. Permission flags travel now so
 * the Фаза-3 footer (like/quote/edit/delete) needs no second pass over the raw post.
 */
data class NativePostItem(
    val postId: Int,
    val topicId: Int,
    val number: Int,
    val userId: Int,
    val nick: String?,
    val avatarUrl: String?,
    val group: String?,
    val groupColor: String?,
    val date: String?,
    val reputation: String?,
    /** Author's total forum post count (from the page / merged profile fetch) — the 💬 header badge. */
    val userPostCount: Int?,
    val postRating: String?,
    val isCurator: Boolean,
    val isOnline: Boolean,
    val blocks: List<BodyBlock>,
    /** Raw post body HTML (as parsed) — source for the full-post «Цитировать» body (mirrors the WebView
     *  quote launcher, which normalizes the post HTML into editor BBCode). Not used for rendering. */
    val rawBodyHtml: String?,
    val canEdit: Boolean,
    val canDelete: Boolean,
    val canQuote: Boolean,
    val canReport: Boolean,
    val canPlusRep: Boolean,
    val canMinusRep: Boolean,
    val canPlusPostRating: Boolean,
    val canMinusPostRating: Boolean,
    /** 1-based topic page this post was loaded from — drives the «Страница N» dividers between pages. */
    val pageNumber: Int = 0,
    /** Non-null → render a «Страница N» divider above this post (computed at list assembly so DiffUtil
     *  rebinds the boundary post when a prepended page shifts it — see the adapter). */
    val pageDividerLabel: String? = null,
) {
    /** Stable RecyclerView item id. Post ids are positive; pseudo-items (hat/spacer) reserve negatives. */
    val stableId: Long get() = postId.toLong()
}
