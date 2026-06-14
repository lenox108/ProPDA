package forpdateam.ru.forpda.presentation.theme

class ThemePaginationController {

    fun firstPage(state: ThemePaginationState): ThemePageTransition? {
        if (state.activePage <= 1) return null
        return ThemePageTransition.LoadSt(pageToSt(1, state))
    }

    fun prevPage(state: ThemePaginationState): ThemePageTransition? {
        val targetPage = state.activePage - 1
        if (targetPage < 1) return null
        return transitionToPage(targetPage, state)
    }

    fun nextPage(state: ThemePaginationState): ThemePageTransition? {
        val targetPage = state.activePage + 1
        if (targetPage > state.safeAll) return null
        return transitionToPage(targetPage, state)
    }

    fun lastPage(state: ThemePaginationState): ThemePageTransition? {
        val lastPageNum = state.safeAll
        // Scroll-only when the final page is actually present in [loadedPages] for the current topic.
        // Do not infer from max(loadedPages) alone — stale page numbers from a previous topic can
        // coerce to [safeAll] and skip the getlastpost load (hybrid «В конец темы» no-op).
        if (state.loadedPages.contains(lastPageNum)) return null
        return transitionToPage(lastPageNum, state)
    }

    fun selectPage(st: Int, state: ThemePaginationState): ThemePageTransition? {
        if (st < 0) return null
        val targetPage = stToPage(st, state)
        if (targetPage !in 1..state.safeAll) return null
        return transitionToPage(targetPage, state, requestedSt = st)
    }

    fun searchPage(st: Int, state: ThemePaginationState): ThemePageTransition? {
        return selectPage(st, state)
    }

    fun visiblePageChanged(page: Int, state: ThemePaginationState): ThemeVisiblePageChange? {
        if (state.safeAll <= 0) return null
        return ThemeVisiblePageChange(
                pageNumber = page.coerceIn(1, state.safeAll),
                allPages = state.safeAll,
                perPage = state.safePerPage,
                isForum = state.isForum
        )
    }

    fun infiniteScroll(direction: String): ThemePaginationDirection? {
        return ThemePaginationDirection.from(direction)
    }

    fun infiniteScroll(direction: String, state: ThemePaginationState): ThemeInfinitePageDecision? {
        val dir = infiniteScroll(direction) ?: return null
        val targetPage = when (dir) {
            ThemePaginationDirection.TOP -> (state.loadedPages.minOrNull() ?: state.activePage) - 1
            ThemePaginationDirection.BOTTOM -> (state.loadedPages.maxOrNull() ?: state.activePage) + 1
        }
        if (targetPage !in 1..state.safeAll) {
            return ThemeInfinitePageDecision.OutOfBounds(dir)
        }
        if (state.loadedPages.contains(targetPage)) {
            return ThemeInfinitePageDecision.AlreadyLoaded(dir, targetPage)
        }
        return ThemeInfinitePageDecision.LoadPage(
                direction = dir,
                pageNumber = targetPage,
                st = pageToSt(targetPage, state)
        )
    }

    fun retry(direction: String): ThemePaginationDirection? {
        return ThemePaginationDirection.from(direction)
    }

    fun retry(direction: String, canRetry: Boolean): ThemePaginationDirection? {
        if (!canRetry) return null
        return retry(direction)
    }

    private fun transitionToPage(
            pageNumber: Int,
            state: ThemePaginationState,
            requestedSt: Int = pageToSt(pageNumber, state)
    ): ThemePageTransition {
        return if (state.loadedPages.contains(pageNumber)) {
            ThemePageTransition.ShowLoadedPage(pageNumber)
        } else {
            ThemePageTransition.LoadSt(requestedSt)
        }
    }

    private fun pageToSt(pageNumber: Int, state: ThemePaginationState): Int {
        return if (state.isForum) {
            (pageNumber - 1).coerceAtLeast(0) * state.safePerPage
        } else {
            pageNumber
        }
    }

    private fun stToPage(st: Int, state: ThemePaginationState): Int {
        return if (state.isForum) {
            (st / state.safePerPage) + 1
        } else {
            st
        }
    }
}

data class ThemePaginationState(
        val currentPage: Int,
        val allPages: Int,
        val perPage: Int,
        val isForum: Boolean,
        val visiblePage: Int? = null,
        val loadedPages: Set<Int> = emptySet()
) {
    val safeAll: Int = allPages.coerceAtLeast(1)
    val safePerPage: Int = perPage.coerceAtLeast(1)
    /** Visible page from hybrid scroll only when that page is actually loaded for the current topic. */
    val activePage: Int = (resolvedVisiblePage() ?: currentPage).coerceIn(1, safeAll)

    private fun resolvedVisiblePage(): Int? {
        val visible = visiblePage ?: return null
        if (loadedPages.isEmpty()) return null
        return visible.takeIf { loadedPages.contains(it) }
    }
}

sealed class ThemePageTransition {
    data class LoadSt(val st: Int) : ThemePageTransition()
    data class ShowLoadedPage(val pageNumber: Int) : ThemePageTransition()
}

data class ThemeVisiblePageChange(
        val pageNumber: Int,
        val allPages: Int,
        val perPage: Int,
        val isForum: Boolean
)

enum class ThemePaginationDirection(val jsName: String) {
    TOP("top"),
    BOTTOM("bottom");

    companion object {
        fun from(value: String): ThemePaginationDirection? = values().firstOrNull {
            it.jsName.equals(value, ignoreCase = true)
        }
    }
}

sealed class ThemeInfinitePageDecision {
    data class LoadPage(
            val direction: ThemePaginationDirection,
            val pageNumber: Int,
            val st: Int
    ) : ThemeInfinitePageDecision()

    data class AlreadyLoaded(
            val direction: ThemePaginationDirection,
            val pageNumber: Int
    ) : ThemeInfinitePageDecision()

    data class OutOfBounds(val direction: ThemePaginationDirection) : ThemeInfinitePageDecision()
}
