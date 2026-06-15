package forpdateam.ru.forpda.ui.fragments.offline

import forpdateam.ru.forpda.entity.db.offline.OfflineItemRoom
import forpdateam.ru.forpda.entity.db.offline.OfflineItemStatus
import forpdateam.ru.forpda.entity.db.offline.OfflineItemType
import forpdateam.ru.forpda.presentation.Screen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Routing tests for [OfflineListComposeFragment.openOfflineItem].
 *
 * The fragment holds no instance state worth its own JVM unit test
 * (it delegates navigation to [forpdateam.ru.forpda.presentation.TabRouter]),
 * so we extract the id-parsing logic to a top-level helper and
 * cover the article/theme branching here. The same helper is used
 * by the fragment, so behaviour drift between the test and the
 * production code path is impossible.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class OfflineListOpenItemRoutingTest {

    @Test
    fun articleId_parsesAndBuildsArticleDetail() {
        val row = sampleRow(id = "article:42", type = OfflineItemType.ARTICLE)
        val screen = buildOfflineItemScreen(row)
        assertNotNull(screen)
        val detail = screen as Screen.ArticleDetail
        assertEquals(42, detail.articleId)
        assertEquals(row.sourceUrl, detail.articleUrl)
        assertEquals(row.title, detail.articleTitle)
    }

    @Test
    fun themeId_parsesAndBuildsTheme() {
        val row = sampleRow(id = "theme:777", type = OfflineItemType.THEME)
        val screen = buildOfflineItemScreen(row)
        assertNotNull(screen)
        val theme = screen as Screen.Theme
        assertEquals(row.sourceUrl, theme.themeUrl)
        assertEquals(row.title, theme.screenTitle)
    }

    @Test
    fun themeId_withPageSuffix_stillBuildsTheme() {
        val row = sampleRow(id = "theme:888:3", type = OfflineItemType.THEME)
        val screen = buildOfflineItemScreen(row)
        assertNotNull(screen)
        val theme = screen as Screen.Theme
        assertEquals(row.sourceUrl, theme.themeUrl)
    }

    @Test
    fun unknownType_returnsNull() {
        val row = sampleRow(id = "weird:1", type = "WEIRD")
        val screen = buildOfflineItemScreen(row)
        assertNull(screen)
    }

    @Test
    fun articleId_invalidNumber_returnsNull() {
        val row = sampleRow(id = "article:notanumber", type = OfflineItemType.ARTICLE)
        val screen = buildOfflineItemScreen(row)
        assertNull(screen)
    }

    @Test
    fun themeId_invalidNumber_returnsNull() {
        val row = sampleRow(id = "theme:abc", type = OfflineItemType.THEME)
        val screen = buildOfflineItemScreen(row)
        assertNull(screen)
    }

    private fun sampleRow(id: String, type: String) = OfflineItemRoom(
            id = id,
            type = type,
            sourceUrl = "https://4pda.to/x",
            title = "Title",
            savedAtMs = 0L,
            sizeBytes = 0L,
            status = OfflineItemStatus.COMPLETE,
            htmlPath = "html/index.html",
            modelJson = "{}"
    )
}
