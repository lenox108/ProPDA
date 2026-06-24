package forpdateam.ru.forpda.presentation.theme

import forpdateam.ru.forpda.entity.remote.theme.ThemePage
import forpdateam.ru.forpda.model.data.remote.api.theme.ThemeApi

/**
 * Pure resolver-side helper that turns the *open* state of a topic
 * (a freshly-loaded [ThemePage] + whether the user opened it via a findpost
 * deep link) into the explicit inputs the [HighlightResolver] needs to stamp
 * a [HighlightTarget] onto the page.
 *
 * The inputs are:
 *  - `firstUnreadPostId` / `unreadPage` / `unreadUrl` — present when the
 *    parser marked the page as `hasUnreadTarget` (e.g. opened from a
 *    getnewpost URL or a list-unread row);
 *  - `explicitPostId` — present only when the user opened the topic via a
 *    `view=findpost` / `act=findpost` deep link to a specific post;
 *  - `readPosition` — optional override for the persisted last-viewed position
 *    when the parser can derive a more accurate post id for the current page
 *    (e.g. the server `getlastpost` redirect hash, or the realigned anchor
 *    from the read-resume bottom redirect). When non-null, takes precedence
 *    over the repository value inside [TopicHighlightApply.applyToPage].
 *
 * Pulled out of [ThemeViewModel] so the extraction is unit-testable without
 * the full Hilt graph. The VM only forwards a boolean ([openedViaFindPost])
 * and the page itself.
 */
object HighlightOpenInputsPolicy {

    /** Origin of the override `readPosition` for the highlight resolver — diagnostic only. */
    enum class LastReadSource {
        NONE,
        PAGE_ANCHOR,
        REDIRECT_URL,
        ANCHORS_LIST,
    }

    /**
     * Build the explicit inputs for [TopicHighlightApply.applyToPage] from a
     * [page] that has just been loaded by the renderer.
     *
     * @param page the freshly-parsed page.
     * @param openedViaFindPost true when the URL the user opened contains
     *        `view=findpost` or `act=findpost`. Scroll anchors from
     *        getnewpost / smart-end do NOT count as an explicit findpost
     *        target — only deep links to a specific post do (see
     *        [HighlightExplicitPostPolicy]).
     * @param forceLastViewedInput when true, always expose a
     *        [OpenHighlightInputs.readPosition] (e.g. for an
     *        [TopicUnreadOpenPolicy.TopicOpenSessionKind.AMBIGUOUS_ALL_READ]
     *        reopen) so the resolver does not fall through to the
     *        bottom-of-page fallback that picked up the wrong post in the
     *        log-24_06-14 regression.
     */
    fun resolveOpenInputs(
            page: ThemePage,
            openedViaFindPost: Boolean,
            forceLastViewedInput: Boolean = false,
    ): OpenHighlightInputs {
        val firstUnreadPostId = if (page.hasUnreadTarget) {
            page.anchorPostId?.toLongOrNull()?.takeIf { it > 0L }
        } else {
            null
        }
        val unreadPage = if (firstUnreadPostId != null) {
            page.pagination.current.takeIf { it > 0 }
        } else {
            null
        }
        val unreadUrl = if (firstUnreadPostId != null) {
            page.url
        } else {
            null
        }
        val explicitPostId = if (openedViaFindPost) {
            page.anchorPostId?.toLongOrNull()?.takeIf { it > 0L }
        } else {
            null
        }
        val (overrideReadPos, source) = resolveReadPositionOverride(page)
        val effectiveReadPos = if (forceLastViewedInput) {
            overrideReadPos
                    ?: fallbackReadPositionFromPage(page)
                    ?.let { it to LastReadSource.PAGE_ANCHOR }
                    ?.let { (rp, _) -> rp }
        } else {
            overrideReadPos
        }
        return OpenHighlightInputs(
                firstUnreadPostId = firstUnreadPostId,
                unreadPage = unreadPage,
                unreadUrl = unreadUrl,
                explicitPostId = explicitPostId,
                readPosition = effectiveReadPos,
                lastReadSource = source,
        )
    }

    /**
     * Best-effort `lastViewedPostId` derived from the freshly-loaded page.
     * Log 24_06-14: read-resume / all-read bottom-redirect opens need this so
     * the highlight falls into priority 2 ("Last read on page") instead of the
     * `last_post_on_page_fallback` path (priority 5) that the regression log
     * showed picking the bottom post without a post-highlight class.
     */
    private fun resolveReadPositionOverride(page: ThemePage): Pair<ReadPosition?, LastReadSource> {
        val topicId = page.id.toLong().takeIf { it > 0L } ?: return null to LastReadSource.NONE
        val pageSt = page.pagination.current
        page.anchorPostId?.trim()?.takeIf { it.isNotEmpty() }
                ?.toLongOrNull()?.takeIf { it > 0L }
                ?.let { postId ->
                    return ReadPosition(
                            topicId = topicId,
                            lastViewedPostId = postId,
                            lastViewedPage = pageSt,
                    ) to LastReadSource.PAGE_ANCHOR
                }
        page.anchors.lastOrNull()?.removePrefix("entry")?.trim()?.toLongOrNull()?.takeIf { it > 0L }
                ?.let { postId ->
                    return ReadPosition(
                            topicId = topicId,
                            lastViewedPostId = postId,
                            lastViewedPage = pageSt,
                    ) to LastReadSource.ANCHORS_LIST
                }
        ThemeApi.extractHashEntryPostIdFromTopicUrl(page.url.orEmpty())?.toLongOrNull()
                ?.takeIf { it > 0L }
                ?.let { postId ->
                    return ReadPosition(
                            topicId = topicId,
                            lastViewedPostId = postId,
                            lastViewedPage = pageSt,
                    ) to LastReadSource.REDIRECT_URL
                }
        return null to LastReadSource.NONE
    }

    private fun fallbackReadPositionFromPage(page: ThemePage): ReadPosition? {
        val topicId = page.id.toLong().takeIf { it > 0L } ?: return null
        val postId = page.posts.lastOrNull { it.id > 0 }?.id?.toLong()?.takeIf { it > 0L } ?: return null
        return ReadPosition(
                topicId = topicId,
                lastViewedPostId = postId,
                lastViewedPage = page.pagination.current,
        )
    }
}

/**
 * Snapshot of the explicit inputs [TopicHighlightApply.applyToPage] can
 * take. All fields are nullable — `null` means "no input" for the resolver.
 */
data class OpenHighlightInputs(
        val firstUnreadPostId: Long?,
        val unreadPage: Int?,
        val unreadUrl: String?,
        val explicitPostId: Long?,
        /**
         * Optional override for the persisted [ReadPosition]. When non-null,
         * [TopicHighlightApply.applyToPage] uses it in place of the value
         * loaded from [forpdateam.ru.forpda.model.repository.theme.ThemeReadPositionRepository].
         */
        val readPosition: ReadPosition? = null,
        /** Origin of [readPosition] — diagnostic only. */
        val lastReadSource: HighlightOpenInputsPolicy.LastReadSource = HighlightOpenInputsPolicy.LastReadSource.NONE,
)

