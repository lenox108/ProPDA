package forpdateam.ru.forpda.presentation.theme

import forpdateam.ru.forpda.entity.remote.theme.ThemePage

/**
 * Deep pagination responses often omit topic title; preserve it from an earlier page in the session.
 */
internal object ThemeToolbarTitlePolicy {

    fun mergeTitleFromSession(
            page: ThemePage,
            previousPage: ThemePage?,
            loadedPages: Collection<ThemePage>,
    ) {
        if (!page.title.isNullOrBlank()) return
        previousPage
                ?.takeIf { it.id == page.id && !it.title.isNullOrBlank() }
                ?.title
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?.let { page.title = it }
        if (!page.title.isNullOrBlank()) return
        loadedPages
                .firstOrNull { it.id == page.id && !it.title.isNullOrBlank() }
                ?.title
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?.let { page.title = it }
    }

    /** Hat-metadata page-1 fetch carries the topic title missing from deep pagination HTML. */
    fun mergeTitleFromFirstPage(page: ThemePage, firstPage: ThemePage) {
        if (!page.title.isNullOrBlank()) return
        if (page.id <= 0 || page.id != firstPage.id) return
        firstPage.title
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?.let { page.title = it }
    }

    /** List/favorites/forward navigation may carry the title before the first network page arrives. */
    fun mergeTitleFromNavigation(page: ThemePage, navigationTitle: String?) {
        if (!page.title.isNullOrBlank()) return
        navigationTitle
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?.let { page.title = it }
    }

    fun needsTitleFromFirstPage(page: ThemePage): Boolean =
            page.id > 0 && page.pagination.current > 1 && page.title.isNullOrBlank()

    /**
     * Resolves toolbar title without clearing an already visible label when pagination HTML
     * omits [ThemePage.title] (deep pages, hat overlay reinjection).
     */
    fun resolveForToolbar(
            page: ThemePage,
            sessionTitle: String?,
            argTitle: String?,
            currentTitle: String?,
    ): String {
        val resolved = page.title
                ?.trim()
                .orEmpty()
                .ifBlank { sessionTitle?.trim().orEmpty() }
                .ifBlank { argTitle?.trim().orEmpty() }
                .ifBlank { currentTitle?.trim().orEmpty() }
        if (page.title.isNullOrBlank() && resolved.isNotEmpty()) {
            page.title = resolved
        }
        return resolved
    }
}
