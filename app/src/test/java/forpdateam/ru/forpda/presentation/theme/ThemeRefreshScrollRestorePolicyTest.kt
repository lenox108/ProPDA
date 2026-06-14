package forpdateam.ru.forpda.presentation.theme

import forpdateam.ru.forpda.common.Preferences as AppPreferences
import forpdateam.ru.forpda.entity.remote.theme.ThemePage
import forpdateam.ru.forpda.entity.remote.theme.ThemePost
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeRefreshScrollRestorePolicyTest {

    @Test
    fun wasNearBottom_upgradesAnchorModeToBottom() {
        val mode = ThemeRefreshScrollRestorePolicy.effectiveRestoreMode(
                requestedMode = "ANCHOR",
                wasNearBottom = true,
                scrollRatio = 0.99,
                page = lastHybridPage(),
                scrollMode = AppPreferences.Main.TopicScrollMode.HYBRID
        )

        assertEquals("BOTTOM", mode)
    }

    @Test
    fun highRatioOnLastHybridPage_upgradesAnchorModeToBottom() {
        val mode = ThemeRefreshScrollRestorePolicy.effectiveRestoreMode(
                requestedMode = "ANCHOR",
                wasNearBottom = false,
                scrollRatio = 0.95,
                page = lastHybridPage(),
                scrollMode = AppPreferences.Main.TopicScrollMode.HYBRID
        )

        assertEquals("BOTTOM", mode)
    }

    @Test
    fun midPageAnchor_keepsAnchorModeInHybrid() {
        val page = lastHybridPage().apply {
            pagination.current = 10
        }
        val mode = ThemeRefreshScrollRestorePolicy.effectiveRestoreMode(
                requestedMode = "ANCHOR",
                wasNearBottom = false,
                scrollRatio = 0.95,
                page = page,
                scrollMode = AppPreferences.Main.TopicScrollMode.HYBRID
        )

        assertEquals("ANCHOR", mode)
    }

    @Test
    fun resolveRefreshAnchor_remapsServerFirstPostToLastParsedOnLastPage() {
        val page = ThemePage().apply {
            addAnchor("entry143734055")
            pagination.current = 149
            pagination.all = 149
            posts.add(ThemePost().apply { id = 143734055 })
            posts.add(ThemePost().apply { id = 143734120 })
        }

        val resolved = ThemeRefreshScrollRestorePolicy.resolveRefreshAnchorPostId(
                page = page,
                anchorPostId = "143734055",
                wasNearBottom = true,
                scrollRatio = 1.0,
                scrollMode = AppPreferences.Main.TopicScrollMode.HYBRID
        )

        assertEquals("143734120", resolved)
    }

    @Test
    fun bottomRestore_prefersBottomModeOverServerFirstAnchor() {
        val page = lastHybridPage()
        val mode = ThemeRefreshScrollRestorePolicy.effectiveRestoreMode(
                requestedMode = "ANCHOR",
                wasNearBottom = true,
                scrollRatio = null,
                page = page,
                scrollMode = AppPreferences.Main.TopicScrollMode.HYBRID
        )

        assertEquals("BOTTOM", mode)
        assertTrue(ThemeRefreshScrollRestorePolicy.shouldPreferBottomRestore(
                wasNearBottom = true,
                scrollRatio = null,
                page = page,
                scrollMode = AppPreferences.Main.TopicScrollMode.HYBRID
        ))
    }

    @Test
    fun sameTopicRefresh_preservesLoadedHybridPages() {
        assertTrue(
                ThemeRefreshScrollRestorePolicy.shouldPreserveLoadedPagesOnRefresh(
                        action = ThemeLoadAction.Refresh,
                        crossTopicLoad = false,
                        freshSameTopicOpen = false,
                        requestedTopicId = 1103268,
                        currentTopicId = 1103268
                )
        )
    }

    @Test
    fun crossTopicRefresh_clearsLoadedHybridPages() {
        assertFalse(
                ThemeRefreshScrollRestorePolicy.shouldPreserveLoadedPagesOnRefresh(
                        action = ThemeLoadAction.Refresh,
                        crossTopicLoad = true,
                        freshSameTopicOpen = false,
                        requestedTopicId = 1103268,
                        currentTopicId = 999
                )
        )
    }

    @Test
    fun normalLoad_doesNotPreserveLoadedPages() {
        assertFalse(
                ThemeRefreshScrollRestorePolicy.shouldPreserveLoadedPagesOnRefresh(
                        action = ThemeLoadAction.Normal,
                        crossTopicLoad = false,
                        freshSameTopicOpen = false,
                        requestedTopicId = 1103268,
                        currentTopicId = 1103268
                )
        )
    }

    @Test
    fun classicMode_doesNotUpgradeByRatioAlone() {
        val mode = ThemeRefreshScrollRestorePolicy.effectiveRestoreMode(
                requestedMode = "ANCHOR",
                wasNearBottom = false,
                scrollRatio = 0.99,
                page = lastHybridPage(),
                scrollMode = AppPreferences.Main.TopicScrollMode.CLASSIC
        )

        assertEquals("ANCHOR", mode)
    }

    private fun lastHybridPage(): ThemePage = ThemePage().apply {
        addAnchor("entry143734055")
        pagination.current = 149
        pagination.all = 149
        posts.add(ThemePost().apply { id = 143734055 })
        posts.add(ThemePost().apply { id = 143734120 })
    }
}
