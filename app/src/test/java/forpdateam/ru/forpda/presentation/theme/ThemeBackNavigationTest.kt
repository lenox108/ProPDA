package forpdateam.ru.forpda.presentation.theme

import forpdateam.ru.forpda.common.Preferences as AppPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeLoadActionTest {

    @Test
    fun toString_matchesThemeJsConstants() {
        assertEquals("NORMAL", ThemeLoadAction.Normal.toString())
        assertEquals("REFRESH", ThemeLoadAction.Refresh.toString())
        assertEquals("BACK", ThemeLoadAction.Back.toString())
        assertEquals("END", ThemeLoadAction.End.toString())
    }

    @Test
    fun fromString_roundTripsJsValues() {
        assertEquals(ThemeLoadAction.Back, ThemeLoadAction.fromString("BACK"))
        assertEquals(ThemeLoadAction.Refresh, ThemeLoadAction.fromString("REFRESH"))
        assertEquals(ThemeLoadAction.End, ThemeLoadAction.fromString("END"))
        assertEquals(ThemeLoadAction.Normal, ThemeLoadAction.fromString("NORMAL"))
    }
}

class ThemeBackRestoreUrlPolicyTest {

    @Test
    fun buildRestoreUrl_prefersAnchorPostId_overUrlHash() {
        val url = ThemeBackRestoreUrlPolicy.buildRestoreUrl(
                topicId = 1121483,
                st = 1180,
                anchorPostId = "143876380",
                pageUrl = "https://4pda.to/forum/index.php?showtopic=1121483&st=1180#entry143860995",
        )
        assertEquals(
                "https://4pda.to/forum/index.php?showtopic=1121483&st=1180#entry143876380",
                url,
        )
    }

    @Test
    fun buildRestoreUrl_fallsBackToUrlHashWhenAnchorMissing() {
        val url = ThemeBackRestoreUrlPolicy.buildRestoreUrl(
                topicId = 1121483,
                st = 1180,
                anchorPostId = null,
                pageUrl = "https://4pda.to/forum/index.php?showtopic=1121483&st=1180#entry143876380",
        )
        assertEquals(
                "https://4pda.to/forum/index.php?showtopic=1121483&st=1180#entry143876380",
                url,
        )
    }

    @Test
    fun buildRestoreUrl_stripsFindpostParams() {
        val url = ThemeBackRestoreUrlPolicy.buildRestoreUrl(
                topicId = 1121483,
                st = 1180,
                anchorPostId = "entry143876380",
                pageUrl = "https://4pda.to/forum/index.php?s=&showtopic=1121483&view=findpost&p=143876380",
        )
        assertEquals(
                "https://4pda.to/forum/index.php?showtopic=1121483&st=1180#entry143876380",
                url,
        )
        assertFalse(url.contains("findpost"))
        assertFalse(url.contains("p=143860995"))
    }
}

class ThemeBackScrollRestorePolicyTest {

    @Test
    fun backAction_neverSuppressesScrollRestore_evenWithStaleUnreadFlags() {
        assertFalse(
                TopicOpenScrollRestorePolicy.shouldSuppressScrollRestoreOnRender(
                        suppressScrollRestoreForOpen = true,
                        pendingUnreadOpenSuppressScroll = true,
                        loadAction = ThemeLoadAction.Back,
                        hasActiveRefreshRestore = false,
                        themeUrl = "https://4pda.to/forum/index.php?showtopic=239158&view=getnewpost",
                        topicOpenTarget = AppPreferences.Main.TopicOpenTarget.LAST_UNREAD,
                        navigationTarget = TopicOpenTarget.Unread(
                                fetchUrl = "https://4pda.to/forum/index.php?showtopic=239158&view=getnewpost",
                                topicId = 239158,
                        ),
                )
        )
    }

    @Test
    fun backAction_allowsSavedScrollRestore() {
        val target = TopicOpenTarget.BackRestore(
                fetchUrl = "https://4pda.to/forum/index.php?showtopic=1121483&st=1180#entry143876380",
                topicId = 1121483,
                pageSt = 1180,
                snapshot = TopicBackSnapshot(
                        topicId = 1121483,
                        pageSt = 1180,
                        visiblePostId = "143876380",
                        scrollOffset = 10099,
                        scrollRatio = 0.764,
                        status = TopicBackSnapshotStatus.CAPTURED,
                ),
        )
        assertTrue(TopicOpenScrollRestorePolicy.savedScrollRestoreAllowed(target, ThemeLoadAction.Back))
    }
}
