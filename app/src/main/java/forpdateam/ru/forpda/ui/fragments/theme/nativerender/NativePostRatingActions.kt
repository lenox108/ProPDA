package forpdateam.ru.forpda.ui.fragments.theme.nativerender

import forpdateam.ru.forpda.entity.common.AuthData

/**
 * Decides whether a post footer shows the 👍 / 👎 post-rating controls — a faithful port of the
 * WebView's `ThemeTemplate.resolvePostRatingUi` (normal, non-hat footer).
 *
 * The reason a raw `post.canPlusPostRating` is not enough: 4pda's MOBILE topic HTML usually omits the
 * `ka_p` / post-action metadata until a desktop merge, so the parsed flags are false on most posts.
 * The WebView compensates with `allowQuoteFallback` — if a post is quotable and not your own, it
 * still renders +/- so the footer isn't empty on only some posts. Native must do the same or the
 * thumbs would almost never appear (they didn't, before this).
 */
object NativePostRatingActions {

    /** @return (canPlus, canMinus) — whether to render 👍 and 👎 for this post. */
    fun resolve(
            canQuote: Boolean,
            postRating: String?,
            parsedCanPlus: Boolean,
            parsedCanMinus: Boolean,
            postUserId: Int,
            authorized: Boolean,
            memberId: Int,
    ): Pair<Boolean, Boolean> {
        val hasServerPostRating = !postRating.isNullOrBlank()
        val isOwnPost = authorized &&
                memberId != AuthData.NO_ID &&
                postUserId != AuthData.NO_ID &&
                postUserId == memberId
        val hasParsed = parsedCanPlus || parsedCanMinus
        val metadataMissing = !hasServerPostRating && !hasParsed
        val allowQuoteFallback = canQuote && metadataMissing && !isOwnPost
        val canRatePost = authorized && !isOwnPost &&
                (hasServerPostRating || hasParsed || allowQuoteFallback)
        val fallback = !hasParsed && (hasServerPostRating || allowQuoteFallback)
        return Pair(
                canRatePost && (parsedCanPlus || fallback),
                canRatePost && (parsedCanMinus || fallback),
        )
    }
}
