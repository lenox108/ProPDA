package forpdateam.ru.forpda.presentation.theme

import forpdateam.ru.forpda.entity.remote.theme.ThemePage

/**
 * Pure resolver-side helper that turns the *open* state of a topic
 * (a freshly-loaded [ThemePage] + whether the user opened it via a findpost
 * deep link) into the explicit inputs the [HighlightResolver] needs to stamp
 * a [HighlightTarget] onto the page.
 *
 * The three inputs are:
 *  - `firstUnreadPostId` / `unreadPage` / `unreadUrl` — present when the
 *    parser marked the page as `hasUnreadTarget` (e.g. opened from a
 *    getnewpost URL or a list-unread row);
 *  - `explicitPostId` — present only when the user opened the topic via a
 *    `view=findpost` / `act=findpost` deep link to a specific post.
 *
 * Pulled out of [ThemeViewModel] so the extraction is unit-testable without
 * the full Hilt graph. The VM only forwards a boolean ([openedViaFindPost])
 * and the page itself.
 */
object HighlightOpenInputsPolicy {

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
     */
    fun resolveOpenInputs(
            page: ThemePage,
            openedViaFindPost: Boolean,
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
        return OpenHighlightInputs(
                firstUnreadPostId = firstUnreadPostId,
                unreadPage = unreadPage,
                unreadUrl = unreadUrl,
                explicitPostId = explicitPostId,
        )
    }
}

/**
 * Snapshot of the four explicit inputs [TopicHighlightApply.applyToPage] can
 * take. All fields are nullable — `null` means "no input" for the resolver.
 */
data class OpenHighlightInputs(
        val firstUnreadPostId: Long?,
        val unreadPage: Int?,
        val unreadUrl: String?,
        val explicitPostId: Long?,
)
