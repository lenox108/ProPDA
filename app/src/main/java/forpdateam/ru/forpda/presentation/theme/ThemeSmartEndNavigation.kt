package forpdateam.ru.forpda.presentation.theme

import forpdateam.ru.forpda.entity.remote.theme.ThemePage
import forpdateam.ru.forpda.common.Preferences as AppPreferences

internal object ThemeSmartEndNavigation {
    const val PAGE_NOT_IN_DOM = "page_not_in_dom"

    fun resolveEndScrollEvent(
            transition: ThemePageTransition?,
            page: ThemePage,
            safeAll: Int,
            scrollMode: AppPreferences.Main.TopicScrollMode
    ): ThemeUiEvent = when (transition) {
        is ThemePageTransition.ShowLoadedPage ->
                endAnchorScrollEvent(page)
        is ThemePageTransition.LoadSt -> endAnchorScrollEvent(page)
        null -> if (scrollMode == AppPreferences.Main.TopicScrollMode.HYBRID) {
            endAnchorScrollEvent(page)
        } else {
            ThemeUiEvent.ScrollToBottom
        }
    }

    /** Scroll to the last parsed post on the loaded page, not the page separator (top of page). */
    fun endAnchorScrollEvent(page: ThemePage): ThemeUiEvent =
            ThemeUiEvent.ScrollToEndAnchorOrBottom(resolveEndScrollAnchorPostId(page))

    /**
     * Hybrid keeps each loaded page in [loadedPagesByNumber]; [fallback] is often the first opened
     * page, not the final one. Prefer the explicit target page, then the highest loaded page.
     */
    fun pageForEndScrollAnchor(
            loadedPagesByNumber: Map<Int, ThemePage>,
            fallback: ThemePage,
            targetPageNumber: Int
    ): ThemePage {
        val topicId = fallback.id
        val targetPage = targetPageNumber.coerceAtLeast(1)
        loadedPagesByNumber[targetPage]?.takeIf { it.id == topicId }?.let { return it }
        return loadedPagesByNumber.entries
                .asSequence()
                .filter { (pageNumber, page) -> page.id == topicId && pageNumber <= targetPage }
                .maxByOrNull { it.key }
                ?.value
                ?: fallback
    }

    fun shouldFallbackToLastPageLoad(
            reason: String,
            scrollMode: AppPreferences.Main.TopicScrollMode
    ): Boolean = reason == PAGE_NOT_IN_DOM &&
            scrollMode == AppPreferences.Main.TopicScrollMode.HYBRID

    fun shouldDeferFallbackWhileLoadInFlight(loadInFlight: Boolean): Boolean = loadInFlight

    /**
     * Last post id on the loaded page for End navigation.
     * Parsed posts list wins over server #entry: getlastpost often redirects to the first post
     * on the final page while the true last post is further down the same page.
     */
    fun resolveEndScrollAnchorPostId(page: ThemePage): String? {
        val parsedPosts = page.posts.filter { it.id > 0 }
        if (parsedPosts.isEmpty()) {
            return serverAnchorPostId(page)
        }
        // Parsed post order is not guaranteed to match DOM order; forum post ids increase monotonically.
        val lastParsedId = parsedPosts.maxByOrNull { it.id }!!.id
        val serverAnchorId = serverAnchorPostId(page)?.toIntOrNull()
        val firstParsedId = parsedPosts.minByOrNull { it.id }!!.id
        if (parsedPosts.size > 1 &&
                serverAnchorId != null &&
                serverAnchorId == firstParsedId &&
                lastParsedId != serverAnchorId
        ) {
            return lastParsedId.toString()
        }
        return lastParsedId.toString()
    }

    fun serverAnchorPostId(page: ThemePage): String? =
            page.anchorPostId?.takeIf { it.isNotBlank() }
                    ?: page.anchor
                            ?.removePrefix("entry")
                            ?.takeIf { it.isNotBlank() }

    fun applyLoadedEndScrollTarget(page: ThemePage) {
        page.anchorPostId = resolveEndScrollAnchorPostId(page)
        page.wasNearBottom = true
    }

    fun endScrollCommand(page: ThemePage): ThemeScrollCommand =
            endScrollCommand(resolveEndScrollAnchorPostId(page))

    fun endScrollCommand(anchorPostId: String?): ThemeScrollCommand =
            anchorPostId
                    ?.takeIf { it.isNotBlank() }
                    ?.let { ThemeScrollCommand.endAnchorOrBottom(it) }
                    ?: ThemeScrollCommand.bottom()
}
