package forpdateam.ru.forpda.presentation.theme

import forpdateam.ru.forpda.entity.remote.theme.ThemePage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeToolbarTitlePolicyTest {

    @Test
    fun `mergeTitleFromSession keeps parsed title`() {
        val page = ThemePage().apply {
            id = 1103268
            title = "Обсуждение OnePlus 15"
        }

        ThemeToolbarTitlePolicy.mergeTitleFromSession(page, previousPage = null, loadedPages = emptyList())

        assertEquals("Обсуждение OnePlus 15", page.title)
    }

    @Test
    fun `mergeTitleFromSession copies title from previous page in same topic`() {
        val previous = ThemePage().apply {
            id = 1103268
            title = "Обсуждение OnePlus 15"
        }
        val page = ThemePage().apply {
            id = 1103268
            pagination.current = 1213
        }

        ThemeToolbarTitlePolicy.mergeTitleFromSession(page, previousPage = previous, loadedPages = emptyList())

        assertEquals("Обсуждение OnePlus 15", page.title)
    }

    @Test
    fun `mergeTitleFromSession copies title from loaded hybrid pages`() {
        val cached = ThemePage().apply {
            id = 1103268
            title = "Обсуждение OnePlus 15"
            pagination.current = 1
        }
        val page = ThemePage().apply {
            id = 1103268
            pagination.current = 1213
        }

        ThemeToolbarTitlePolicy.mergeTitleFromSession(
                page = page,
                previousPage = null,
                loadedPages = listOf(cached),
        )

        assertEquals("Обсуждение OnePlus 15", page.title)
    }

    @Test
    fun mergeTitleFromFirstPage_copiesHatMetadataTitle() {
        val firstPage = ThemePage().apply {
            id = 1121483
            title = "ForPDA — обсуждение"
            pagination.current = 1
        }
        val page = ThemePage().apply {
            id = 1121483
            pagination.current = 58
        }

        ThemeToolbarTitlePolicy.mergeTitleFromFirstPage(page, firstPage)

        assertEquals("ForPDA — обсуждение", page.title)
    }

    @Test
    fun resolveForToolbar_preservesCurrentTitleWhenPageTitleMissing() {
        val page = ThemePage().apply {
            id = 1080563
            pagination.current = 713
        }

        val resolved = ThemeToolbarTitlePolicy.resolveForToolbar(
                page = page,
                sessionTitle = null,
                argTitle = null,
                currentTitle = "Обсуждение темы",
        )

        assertEquals("Обсуждение темы", resolved)
        assertEquals("Обсуждение темы", page.title)
    }

    @Test
    fun resolveForToolbar_prefersSessionTitleOverClearedCurrentTitle() {
        val page = ThemePage().apply {
            id = 1080563
            pagination.current = 713
        }

        val resolved = ThemeToolbarTitlePolicy.resolveForToolbar(
                page = page,
                sessionTitle = "Заголовок из сессии",
                argTitle = null,
                currentTitle = "",
        )

        assertEquals("Заголовок из сессии", resolved)
        assertEquals("Заголовок из сессии", page.title)
    }

    @Test
    fun mergeTitleFromNavigation_fillsMissingDeepPageTitle() {
        val page = ThemePage().apply {
            id = 1103268
            pagination.current = 1227
        }

        ThemeToolbarTitlePolicy.mergeTitleFromNavigation(page, "Обсуждение OnePlus 15")

        assertEquals("Обсуждение OnePlus 15", page.title)
    }

    @Test
    fun needsTitleFromFirstPage_trueOnlyForDeepPagesWithoutTitle() {
        val deep = ThemePage().apply {
            id = 1103268
            pagination.current = 1227
        }
        val first = ThemePage().apply {
            id = 1103268
            title = "Обсуждение OnePlus 15"
            pagination.current = 1
        }

        assertTrue(ThemeToolbarTitlePolicy.needsTitleFromFirstPage(deep))
        assertFalse(ThemeToolbarTitlePolicy.needsTitleFromFirstPage(first))
        assertFalse(
                ThemeToolbarTitlePolicy.needsTitleFromFirstPage(
                        ThemePage().apply {
                            id = 1103268
                            title = "Already set"
                            pagination.current = 1227
                        }
                )
        )
    }

    @Test
    fun `mergeTitleFromSession ignores title from different topic`() {
        val previous = ThemePage().apply {
            id = 999
            title = "Other topic"
        }
        val page = ThemePage().apply {
            id = 1103268
            pagination.current = 1213
        }

        ThemeToolbarTitlePolicy.mergeTitleFromSession(page, previousPage = previous, loadedPages = emptyList())

        assertEquals(null, page.title)
    }
}
