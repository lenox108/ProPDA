package forpdateam.ru.forpda.presentation.theme

/**
 * Post-publish [ThemeScrollCommand.Kind.END_ANCHOR_OR_BOTTOM] must not reuse smart-end
 * [ThemeViewModel.isEndNavigationPending] (log: scroll_replay_deferred + onPageComplete end branch).
 */
internal object ThemePostedScrollPendingPolicy {

    fun resolvePostedScrollAnchor(
            isEditPost: Boolean,
            explicitPostId: Int?,
            smartEndPostId: String?,
            pageAnchorPostId: String?,
    ): String? {
        val explicitAnchor = explicitPostId
                ?.takeIf { it > 0 }
                ?.toString()
        return when {
            explicitAnchor != null -> explicitAnchor
            isEditPost -> null
            !smartEndPostId.isNullOrBlank() -> smartEndPostId
            !pageAnchorPostId.isNullOrBlank() -> pageAnchorPostId
            else -> null
        }
    }

    fun shouldMarkEndNavigationPending(
            kind: ThemeScrollCommand.Kind,
            pendingPostedPageScrollAnchor: String?,
    ): Boolean =
            kind == ThemeScrollCommand.Kind.END_ANCHOR_OR_BOTTOM &&
                    pendingPostedPageScrollAnchor.isNullOrBlank()

    fun shouldMarkPostedPageScrollPending(
            kind: ThemeScrollCommand.Kind,
            pendingPostedPageScrollAnchor: String?,
    ): Boolean =
            kind == ThemeScrollCommand.Kind.END_ANCHOR_OR_BOTTOM &&
                    !pendingPostedPageScrollAnchor.isNullOrBlank()

    fun shouldDeferFlushUntilRenderable(
            hasRenderableContent: Boolean,
            completedRenderHasPosts: Boolean,
            endNavigationPending: Boolean,
            postedPageScrollPending: Boolean,
    ): Boolean {
        if (postedPageScrollPending) {
            return !hasRenderableContent && !completedRenderHasPosts
        }
        return !hasRenderableContent || (!completedRenderHasPosts && !endNavigationPending)
    }
}
