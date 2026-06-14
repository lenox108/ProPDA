package forpdateam.ru.forpda.presentation.theme

import forpdateam.ru.forpda.common.Preferences as AppPreferences
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TopicOpenNavigationTargetPolicyTest {

    @Test
    fun unreadTarget_blocksSavedScrollOnNormalLoad() {
        val target = TopicOpenTarget.Unread(
                fetchUrl = "https://4pda.to/forum/index.php?showtopic=1&view=getnewpost",
                topicId = 1
        )
        assertFalse(
                TopicOpenScrollRestorePolicy.savedScrollRestoreAllowed(
                        openTarget = target,
                        loadAction = ThemeLoadAction.Normal
                )
        )
    }

    @Test
    fun backRestoreTarget_allowsSavedScrollOnNormalLoad() {
        val snapshot = TopicBackSnapshot.fromPage(1, 0, "5", 200, null, false)
        val target = TopicOpenTarget.BackRestore(
                fetchUrl = "https://4pda.to/forum/index.php?showtopic=1",
                topicId = 1,
                pageSt = 0,
                snapshot = snapshot
        )
        assertTrue(
                TopicOpenScrollRestorePolicy.savedScrollRestoreAllowed(
                        openTarget = target,
                        loadAction = ThemeLoadAction.Normal
                )
        )
    }

    @Test
    fun unreadTarget_suppressesScrollOnRender() {
        val target = TopicOpenTarget.Unread(
                fetchUrl = "https://4pda.to/forum/index.php?showtopic=1&view=getnewpost",
                topicId = 1
        )
        assertTrue(
                TopicOpenScrollRestorePolicy.shouldSuppressScrollRestoreOnRender(
                        suppressScrollRestoreForOpen = false,
                        pendingUnreadOpenSuppressScroll = false,
                        loadAction = ThemeLoadAction.Normal,
                        hasActiveRefreshRestore = false,
                        themeUrl = target.fetchUrl,
                        topicOpenTarget = AppPreferences.Main.TopicOpenTarget.LAST_UNREAD,
                        navigationTarget = target
                )
        )
    }

    // Bug A regression guard: jump-to-end must override any cached scroll/anchor (it scrolls to
    // bottom), so saved scroll restore is not allowed and is suppressed on render for End targets.
    @Test
    fun endTarget_blocksSavedScrollRestore() {
        val target = TopicOpenTarget.End(
                fetchUrl = "https://4pda.to/forum/index.php?showtopic=1&view=getlastpost",
                topicId = 1,
                pageSt = null
        )
        assertFalse(
                TopicOpenScrollRestorePolicy.savedScrollRestoreAllowed(
                        openTarget = target,
                        loadAction = ThemeLoadAction.End
                )
        )
        assertTrue(
                TopicOpenScrollRestorePolicy.shouldSuppressScrollRestoreOnRender(
                        suppressScrollRestoreForOpen = false,
                        pendingUnreadOpenSuppressScroll = false,
                        loadAction = ThemeLoadAction.End,
                        hasActiveRefreshRestore = false,
                        themeUrl = target.fetchUrl,
                        topicOpenTarget = AppPreferences.Main.TopicOpenTarget.LAST_UNREAD,
                        navigationTarget = target
                )
        )
    }

    // Bug B regression guard: on topic refresh the saved scroll position MUST be restored,
    // so the render-time suppression guard must NOT block it for the Refresh load action even
    // when the topic-open setting is LAST_UNREAD.
    @Test
    fun refreshLoad_doesNotSuppressScrollRestoreOnRender() {
        assertFalse(
                TopicOpenScrollRestorePolicy.shouldSuppressScrollRestoreOnRender(
                        suppressScrollRestoreForOpen = true,
                        pendingUnreadOpenSuppressScroll = true,
                        loadAction = ThemeLoadAction.Refresh,
                        hasActiveRefreshRestore = true,
                        themeUrl = "https://4pda.to/forum/index.php?showtopic=1&view=getnewpost",
                        topicOpenTarget = AppPreferences.Main.TopicOpenTarget.LAST_UNREAD,
                        navigationTarget = null
                )
        )
    }

    @Test
    fun refreshRestoreTarget_allowsSavedScrollOnRefresh() {
        val target = TopicOpenTarget.RefreshRestore(
                fetchUrl = "https://4pda.to/forum/index.php?showtopic=1&st=20",
                topicId = 1,
                pageSt = 20,
                restoreId = "abc12345",
                mode = "ANCHOR",
                source = "toolbarRefresh"
        )
        assertTrue(
                TopicOpenScrollRestorePolicy.savedScrollRestoreAllowed(
                        openTarget = target,
                        loadAction = ThemeLoadAction.Refresh
                )
        )
    }

    @Test
    fun explicitPostTarget_suppressesSavedScrollOnRender() {
        val target = TopicOpenTarget.ExplicitPost(
                fetchUrl = "https://4pda.to/forum/index.php?showtopic=1&p=9",
                topicId = 1,
                postId = 9
        )
        assertTrue(
                TopicOpenScrollRestorePolicy.shouldSuppressScrollRestoreOnRender(
                        suppressScrollRestoreForOpen = false,
                        pendingUnreadOpenSuppressScroll = false,
                        loadAction = ThemeLoadAction.Normal,
                        hasActiveRefreshRestore = false,
                        themeUrl = target.fetchUrl,
                        topicOpenTarget = AppPreferences.Main.TopicOpenTarget.LAST_UNREAD,
                        navigationTarget = target
                )
        )
    }
}
