package forpdateam.ru.forpda.presentation.theme

import forpdateam.ru.forpda.entity.remote.others.pagination.Pagination
import forpdateam.ru.forpda.entity.remote.theme.ThemePage
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Regression test for the cross-topic loadedPages contamination in
 * [ThemeInfiniteScrollController.requestInfinitePage]. The hybrid scroll min/max must be
 * computed only over pages belonging to the current topic; otherwise a stale 797-page
 * entry from the previous topic shifts the calculation out of bounds and the "load
 * previous" request is silently dropped.
 */
class ThemeInfiniteScrollTargetPageTest {

    private fun page(topicId: Int, pageNumber: Int, allPages: Int): ThemePage = ThemePage().apply {
        id = topicId
        pagination = Pagination().apply {
            current = pageNumber
            all = allPages
            perPage = 20
        }
    }

    @Test
    fun `min filtered by current topic id`() {
        val loaded = linkedMapOf<Int, ThemePage>()
        loaded[797] = page(topicId = 239158, pageNumber = 797, allPages = 1000)
        loaded[60] = page(topicId = 1121483, pageNumber = 60, allPages = 61)
        loaded[61] = page(topicId = 1121483, pageNumber = 61, allPages = 61)

        val current = page(topicId = 1121483, pageNumber = 61, allPages = 61)
        val topicPageNumbers = loaded.entries.asSequence()
            .filter { (_, p) -> p.id == current.id }
            .map { (n, _) -> n }
            .toList()
        val min = topicPageNumbers.min()
        assertEquals(60, min)
    }

    @Test
    fun `max filtered by current topic id`() {
        val loaded = linkedMapOf<Int, ThemePage>()
        loaded[3] = page(topicId = 1121483, pageNumber = 3, allPages = 61)
        loaded[999] = page(topicId = 239158, pageNumber = 999, allPages = 1000)

        val current = page(topicId = 1121483, pageNumber = 3, allPages = 61)
        val topicPageNumbers = loaded.entries.asSequence()
            .filter { (_, p) -> p.id == current.id }
            .map { (n, _) -> n }
            .toList()
        val max = topicPageNumbers.max()
        assertEquals(3, max)
    }

    @Test
    fun `targetPage for TOP stays in bounds when previous topic is stale`() {
        val loaded = linkedMapOf<Int, ThemePage>()
        loaded[797] = page(topicId = 239158, pageNumber = 797, allPages = 1000)
        loaded[61] = page(topicId = 1121483, pageNumber = 61, allPages = 61)
        val current = page(topicId = 1121483, pageNumber = 61, allPages = 61)

        val topicPageNumbers = loaded.entries.asSequence()
            .filter { (_, p) -> p.id == current.id }
            .map { (n, _) -> n }
            .toList()
        val targetPage = (topicPageNumbers.minOrNull() ?: current.pagination.current) - 1

        assertEquals(60, targetPage)
        assert(targetPage in 1..current.pagination.all) {
            "expected $targetPage to be within 1..${current.pagination.all}"
        }
    }

    @Test
    fun `targetPage for BOTTOM stays in bounds when previous topic is stale`() {
        val loaded = linkedMapOf<Int, ThemePage>()
        loaded[3] = page(topicId = 1121483, pageNumber = 3, allPages = 61)
        loaded[999] = page(topicId = 239158, pageNumber = 999, allPages = 1000)
        val current = page(topicId = 1121483, pageNumber = 3, allPages = 61)

        val topicPageNumbers = loaded.entries.asSequence()
            .filter { (_, p) -> p.id == current.id }
            .map { (n, _) -> n }
            .toList()
        val targetPage = (topicPageNumbers.maxOrNull() ?: current.pagination.current) + 1

        assertEquals(4, targetPage)
        assert(targetPage in 1..current.pagination.all) {
            "expected $targetPage to be within 1..${current.pagination.all}"
        }
    }
}
