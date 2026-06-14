package forpdateam.ru.forpda.presentation.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ThemePaginationControllerTest {
    private val controller = ThemePaginationController()

    @Test
    fun firstAndPrevPage_rejectPageOne() {
        val state = state(currentPage = 1)

        assertNull(controller.firstPage(state))
        assertNull(controller.prevPage(state))
    }

    @Test
    fun nextAndLastPage_rejectLastPage() {
        val state = state(currentPage = 5, allPages = 5, loadedPages = setOf(5))

        assertNull(controller.nextPage(state))
        assertNull(controller.lastPage(state))
    }

    @Test
    fun lastPage_loadsFinalPageWhenFurthestLoadedIsBehindPaginationEnd() {
        val state = state(
                currentPage = 15,
                allPages = 15,
                loadedPages = setOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14),
                visiblePage = 15
        )

        assertEquals(ThemePageTransition.LoadSt(280), controller.lastPage(state))
    }

    @Test
    fun lastPage_ignoresVisiblePageFromPreviousTopicWhenItIsNotLoaded() {
        val state = state(
                currentPage = 1,
                allPages = 15,
                loadedPages = setOf(1),
                visiblePage = 15
        )

        assertEquals(ThemePageTransition.LoadSt(280), controller.lastPage(state))
    }

    @Test
    fun selectPage_rejectsInvalidAndOutOfRangeOffsets() {
        val state = state(currentPage = 2, allPages = 3, perPage = 20)

        assertNull(controller.selectPage(-1, state))
        assertNull(controller.selectPage(60, state))
    }

    @Test
    fun selectPage_usesLoadedPageWhenPresent() {
        val state = state(currentPage = 1, allPages = 3, perPage = 20, loadedPages = setOf(2))

        assertEquals(ThemePageTransition.ShowLoadedPage(2), controller.selectPage(20, state))
    }

    @Test
    fun searchPage_convertsForumStToPage() {
        val state = state(currentPage = 1, allPages = 4, perPage = 20)

        assertEquals(ThemePageTransition.LoadSt(40), controller.searchPage(40, state))
    }

    @Test
    fun searchPage_rejectsOutOfRangeSt() {
        val state = state(currentPage = 1, allPages = 2, perPage = 20)

        assertNull(controller.searchPage(40, state))
    }

    @Test
    fun visiblePageChanged_clampsPageToPaginationBounds() {
        val state = state(currentPage = 2, allPages = 5, perPage = 20)

        assertEquals(
                ThemeVisiblePageChange(pageNumber = 1, allPages = 5, perPage = 20, isForum = true),
                controller.visiblePageChanged(0, state)
        )
        assertEquals(
                ThemeVisiblePageChange(pageNumber = 5, allPages = 5, perPage = 20, isForum = true),
                controller.visiblePageChanged(8, state)
        )
    }

    @Test
    fun infiniteScroll_decidesDirectionAndTargetPage() {
        val state = state(currentPage = 3, allPages = 5, perPage = 20, loadedPages = setOf(2, 3))

        assertEquals(
                ThemeInfinitePageDecision.LoadPage(
                        direction = ThemePaginationDirection.TOP,
                        pageNumber = 1,
                        st = 0
                ),
                controller.infiniteScroll("top", state)
        )
        assertEquals(
                ThemeInfinitePageDecision.LoadPage(
                        direction = ThemePaginationDirection.BOTTOM,
                        pageNumber = 4,
                        st = 60
                ),
                controller.infiniteScroll("bottom", state)
        )
    }

    @Test
    fun infiniteScroll_rejectsInvalidLoadedAndOutOfBoundsDirections() {
        val state = state(currentPage = 1, allPages = 1, loadedPages = setOf(1))

        assertNull(controller.infiniteScroll("side", state))
        assertEquals(
                ThemeInfinitePageDecision.OutOfBounds(ThemePaginationDirection.TOP),
                controller.infiniteScroll("top", state)
        )
        assertEquals(
                ThemeInfinitePageDecision.OutOfBounds(ThemePaginationDirection.BOTTOM),
                controller.infiniteScroll("bottom", state)
        )
    }

    @Test
    fun retry_returnsDirectionOnlyWhenRetryIsAllowed() {
        assertEquals(ThemePaginationDirection.TOP, controller.retry("top", canRetry = true))
        assertNull(controller.retry("top", canRetry = false))
        assertNull(controller.retry("side", canRetry = true))
    }

    @Test
    fun lastPage_loadsFinalPageWhenMaxLoadedIsBelowEndButNotPresent() {
        val state = state(
                currentPage = 3,
                allPages = 5,
                perPage = 20,
                loadedPages = setOf(1, 2, 3, 4)
        )

        assertEquals(ThemePageTransition.LoadSt(80), controller.lastPage(state))
    }

    @Test
    fun lastPage_scrollOnlyWhenFinalPageNumberIsLoaded() {
        val state = state(currentPage = 1, allPages = 15, loadedPages = setOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15))

        assertNull(controller.lastPage(state))
    }

    private fun state(
            currentPage: Int,
            allPages: Int = 5,
            perPage: Int = 20,
            isForum: Boolean = true,
            visiblePage: Int? = null,
            loadedPages: Set<Int> = emptySet()
    ) = ThemePaginationState(
            currentPage = currentPage,
            allPages = allPages,
            perPage = perPage,
            isForum = isForum,
            visiblePage = visiblePage,
            loadedPages = loadedPages
    )
}
