package forpdateam.ru.forpda.presentation.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TopicOpenTargetMapperTest {

    @Test
    fun explicitPostResolution_mapsToExplicitPostAtSavedPost() {
        // Bookmark/explicit-post open: must map to ExplicitPost at the saved post with scroll restore blocked.
        val target = TopicOpenTargetMapper.from(
                resolution = TopicOpenResolution(
                        url = "https://4pda.to/forum/index.php?showtopic=123&p=456&view=findpost",
                        targetType = TopicOpenTargetType.EXPLICIT_POST,
                        resolvedPostId = 456,
                        reason = "explicit_post_source"
                ),
                loadAction = ThemeLoadAction.Normal,
                openIntentRaw = TopicOpenIntentClassifier.EXPLICIT_POST
        )
        assertTrue(target is TopicOpenTarget.ExplicitPost)
        assertEquals(456, (target as TopicOpenTarget.ExplicitPost).postId)
        assertEquals(123, target.topicId)
        assertFalse(target.allowSavedScrollRestore)
    }

    @Test
    fun unreadResolution_mapsToUnreadTargetWithScrollBlocked() {
        val resolution = TopicOpenResolution(
                url = "https://4pda.to/forum/index.php?showtopic=1&view=getnewpost",
                targetType = TopicOpenTargetType.SETTING_LAST_UNREAD,
                suppressScrollRestore = true,
                reason = "added_getnewpost"
        )
        val target = TopicOpenTargetMapper.from(
                resolution = resolution,
                loadAction = ThemeLoadAction.Normal,
                openIntentRaw = TopicOpenIntentClassifier.FRESH_FORUM
        )
        assertTrue(target is TopicOpenTarget.Unread)
        assertFalse(target.allowSavedScrollRestore)
    }

    @Test
    fun backLoad_mapsToBackRestoreWithSnapshot() {
        val snapshot = TopicBackSnapshot.fromPage(
                topicId = 42,
                pageSt = 20,
                visiblePostId = "999",
                scrollOffset = 120,
                scrollRatio = 0.5,
                wasNearBottom = false
        )
        val target = TopicOpenTargetMapper.from(
                resolution = TopicOpenResolution(
                        url = "https://4pda.to/forum/index.php?showtopic=42&st=20",
                        targetType = TopicOpenTargetType.EXPLICIT_PAGE,
                        resolvedPageSt = 20,
                        reason = "back"
                ),
                loadAction = ThemeLoadAction.Back,
                openIntentRaw = TopicOpenIntentClassifier.BACK_RESTORE,
                backSnapshot = snapshot
        )
        assertTrue(target is TopicOpenTarget.BackRestore)
        assertTrue(target.allowSavedScrollRestore)
        assertTrue(TopicOpenScrollRestorePolicy.savedScrollRestoreAllowed(target, ThemeLoadAction.Back))
    }

    @Test
    fun refreshRestoreTarget_allowsSavedScroll() {
        val target = TopicOpenTargetMapper.from(
                resolution = TopicOpenResolution(
                        url = "https://4pda.to/forum/index.php?showtopic=5&st=0",
                        targetType = TopicOpenTargetType.EXPLICIT_PAGE,
                        reason = "refresh"
                ),
                loadAction = ThemeLoadAction.Refresh,
                openIntentRaw = TopicOpenIntentClassifier.FRESH_FORUM,
                refreshRestoreId = "abc12345",
                refreshRestoreMode = "ANCHOR"
        )
        assertTrue(target is TopicOpenTarget.RefreshRestore)
        assertTrue(TopicOpenScrollRestorePolicy.savedScrollRestoreAllowed(target, ThemeLoadAction.Refresh))
    }

    @Test
    fun endNavigation_mapsToEndTarget() {
        val target = TopicOpenTargetMapper.from(
                resolution = TopicOpenResolution(
                        url = "https://4pda.to/forum/index.php?showtopic=9&view=getlastpost",
                        targetType = TopicOpenTargetType.USER_ACTION,
                        reason = "user_action_last_post"
                ),
                loadAction = ThemeLoadAction.End,
                openIntentRaw = "open_last_post"
        )
        assertTrue(target is TopicOpenTarget.End)
        assertFalse(target.allowSavedScrollRestore)
    }
}
