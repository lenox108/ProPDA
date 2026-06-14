package forpdateam.ru.forpda.presentation.theme

import forpdateam.ru.forpda.common.Preferences as AppPreferences
import forpdateam.ru.forpda.entity.remote.theme.ThemePage

/**
 * Refresh scroll restore: preserve bottom/end intent across hybrid re-render.
 *
 * Server #entry on the last page often points at the first post on that page; ANCHOR restore
 * with that id scrolls to the page top instead of the last message the user was reading.
 */
object ThemeRefreshScrollRestorePolicy {

    private const val NEAR_BOTTOM_RATIO = 0.92

    fun shouldPreferBottomRestore(
            wasNearBottom: Boolean,
            scrollRatio: Double?,
            page: ThemePage?,
            scrollMode: AppPreferences.Main.TopicScrollMode
    ): Boolean {
        if (wasNearBottom) return true
        if (scrollMode != AppPreferences.Main.TopicScrollMode.HYBRID) return false
        if (page == null || !isOnLastTopicPage(page)) return false
        val ratio = scrollRatio ?: return false
        return ratio >= NEAR_BOTTOM_RATIO
    }

    fun effectiveRestoreMode(
            requestedMode: String?,
            wasNearBottom: Boolean,
            scrollRatio: Double?,
            page: ThemePage?,
            scrollMode: AppPreferences.Main.TopicScrollMode
    ): String = when {
        requestedMode.equals("BOTTOM", ignoreCase = true) -> "BOTTOM"
        shouldPreferBottomRestore(wasNearBottom, scrollRatio, page, scrollMode) -> "BOTTOM"
        else -> requestedMode?.takeIf { it.isNotBlank() } ?: "ANCHOR"
    }

    /**
     * Remap server first-post anchor to the last parsed post when refresh intent is bottom/end.
     */
    fun resolveRefreshAnchorPostId(
            page: ThemePage?,
            anchorPostId: String?,
            wasNearBottom: Boolean,
            scrollRatio: Double?,
            scrollMode: AppPreferences.Main.TopicScrollMode
    ): String? {
        if (page == null) return anchorPostId
        if (!shouldPreferBottomRestore(wasNearBottom, scrollRatio, page, scrollMode)) {
            return anchorPostId
        }
        val normalizedAnchor = anchorPostId?.removePrefix("entry")?.takeIf { it.isNotBlank() }
        val resolvedLast = ThemeSmartEndNavigation.resolveEndScrollAnchorPostId(page)
                ?.removePrefix("entry")
                ?.takeIf { it.isNotBlank() }
        if (resolvedLast == null) return anchorPostId
        if (normalizedAnchor.isNullOrBlank()) return resolvedLast
        val serverFirst = ThemeSmartEndNavigation.serverAnchorPostId(page)
                ?.removePrefix("entry")
                ?.takeIf { it.isNotBlank() }
        if (normalizedAnchor == serverFirst && resolvedLast != normalizedAnchor) {
            return resolvedLast
        }
        return anchorPostId
    }

    fun isOnLastTopicPage(page: ThemePage): Boolean {
        val current = page.pagination.current
        val all = page.pagination.all
        return current > 0 && all > 0 && current >= all
    }

    /**
     * Keep hybrid [loadedPages] across same-topic refresh so [mapHybridPages] can rebuild the
     * stacked document the user was reading instead of collapsing to a single server page at Y=0.
     */
    fun shouldPreserveLoadedPagesOnRefresh(
            action: ThemeLoadAction,
            crossTopicLoad: Boolean,
            freshSameTopicOpen: Boolean,
            requestedTopicId: Int?,
            currentTopicId: Int?
    ): Boolean = action == ThemeLoadAction.Refresh &&
            !crossTopicLoad &&
            !freshSameTopicOpen &&
            requestedTopicId != null &&
            requestedTopicId > 0 &&
            requestedTopicId == currentTopicId
}
