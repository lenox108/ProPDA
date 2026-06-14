package forpdateam.ru.forpda.presentation.theme

import forpdateam.ru.forpda.common.Preferences as AppPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Stale list hints from topic A must not change how topic B is opened. */
class TopicOpenCrossTopicResolverTest {

    @Test
    fun staleUnreadUrlFromOtherTopic_isIgnored() {
        val resolution = TopicOpenTargetResolver.resolve(
                TopicOpenContext(
                        rawUrl = "https://4pda.to/forum/index.php?showtopic=200",
                        setting = AppPreferences.Main.TopicOpenTarget.LAST_UNREAD,
                        sourceScreen = "topics",
                        unreadUrlFromList = "https://4pda.to/forum/index.php?showtopic=100&view=getnewpost"
                )
        )
        assertEquals(
                "https://4pda.to/forum/index.php?showtopic=200&view=getnewpost",
                resolution.url
        )
        assertEquals(TopicOpenTargetType.SETTING_LAST_UNREAD, resolution.targetType)
        assertTrue(resolution.suppressScrollRestore)
    }
}
