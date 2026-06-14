package forpdateam.ru.forpda.presentation.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Regression: stale [ThemePaginationState.visiblePage] from a previous topic must not make
 * [ThemePaginationState.activePage] look like the last page of the newly opened topic.
 */
class ThemePaginationVisiblePageTest {
    private val controller = ThemePaginationController()

    @Test
    fun activePage_ignoresVisiblePageNotPresentInLoadedPages() {
        val state = ThemePaginationState(
                currentPage = 2,
                allPages = 8,
                perPage = 20,
                isForum = true,
                visiblePage = 10,
                loadedPages = setOf(2)
        )
        assertEquals(2, state.activePage)
        assertNotNull(controller.lastPage(state))
    }

    @Test
    fun activePage_usesVisibleWhenLoadedPageExists() {
        val state = ThemePaginationState(
                currentPage = 2,
                allPages = 8,
                perPage = 20,
                isForum = true,
                visiblePage = 3,
                loadedPages = setOf(2, 3)
        )
        assertEquals(3, state.activePage)
    }

    @Test
    fun staleVisiblePageWithoutLoadedPages_coercesToCurrentNotLast() {
        val state = ThemePaginationState(
                currentPage = 1,
                allPages = 5,
                perPage = 20,
                isForum = true,
                visiblePage = 10,
                loadedPages = emptySet()
        )
        assertEquals(1, state.activePage)
        assertNotNull(controller.lastPage(state))
    }

    @Test
    fun staleVisiblePageWithPartialHybridLoad_stillAllowsLastPageTransition() {
        val state = ThemePaginationState(
                currentPage = 2,
                allPages = 5,
                perPage = 20,
                isForum = true,
                visiblePage = 10,
                loadedPages = setOf(2)
        )
        assertEquals(2, state.activePage)
        assertNotNull(controller.lastPage(state))
    }

    @Test
    fun lastPage_ignoresStaleVisibleAtEndWhenFinalPageNotLoaded() {
        val state = ThemePaginationState(
                currentPage = 15,
                allPages = 15,
                perPage = 20,
                isForum = true,
                visiblePage = 15,
                loadedPages = setOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14)
        )
        assertEquals(15, state.activePage)
        assertNotNull(controller.lastPage(state))
    }
}
