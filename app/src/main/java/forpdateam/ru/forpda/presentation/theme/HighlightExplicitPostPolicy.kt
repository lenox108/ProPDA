package forpdateam.ru.forpda.presentation.theme

import forpdateam.ru.forpda.entity.remote.theme.ThemePage

/**
 * Scroll anchors (`anchorPostId` from getnewpost / smart-end / read-resume) must
 * NOT be treated as explicit highlight targets. Only findpost / deep-link opens
 * should supply an [HighlightResolver] explicit input.
 */
object HighlightExplicitPostPolicy {

    fun resolveExplicitPostId(
            page: ThemePage,
            openedViaFindPost: Boolean,
            requestUrl: String?,
    ): Long? {
        if (!openedViaFindPost && !isFindPostUrl(requestUrl) && !isFindPostUrl(page.url)) {
            return null
        }
        return page.anchorPostId
                ?.removePrefix("entry")
                ?.trim()
                ?.toLongOrNull()
                ?.takeIf { it > 0L }
    }

    fun isFindPostUrl(url: String?): Boolean {
        val low = url?.lowercase().orEmpty()
        return low.contains("view=findpost") || low.contains("act=findpost")
    }
}
