package forpdateam.ru.forpda.presentation.search

import forpdateam.ru.forpda.common.Preferences as AppPreferences
import forpdateam.ru.forpda.presentation.theme.TopicOpenTargetType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchThemeOpenUrlTest {

    @Test
    fun buildSearchFindPostTopicUrl_usesShowtopicAndFindpost() {
        assertEquals(
                "https://4pda.to/forum/index.php?showtopic=12345&view=findpost&p=999",
                buildSearchFindPostTopicUrl(topicId = 12345, postId = 999)
        )
    }

    @Test
    fun buildSearchFindPostTopicUrl_withoutTopicId_usesActFindpost() {
        assertEquals(
                "https://4pda.to/forum/index.php?act=findpost&pid=999",
                buildSearchFindPostTopicUrl(topicId = 0, postId = 999)
        )
    }

    @Test
    fun resolveSearchFindPostThemeOpen_preservesFindpostUnderLastUnreadSetting() {
        val url = buildSearchFindPostTopicUrl(topicId = 12345, postId = 999)
        val resolution = resolveSearchFindPostThemeOpen(
                findPostUrl = url,
                setting = AppPreferences.Main.TopicOpenTarget.LAST_UNREAD
        )
        assertEquals(url, resolution.url)
        assertEquals(TopicOpenTargetType.EXPLICIT_POST, resolution.targetType)
        assertEquals(999, resolution.resolvedPostId)
        assertFalse(resolution.suppressScrollRestore)
    }

    @Test
    fun resolveSearchFindPostThemeOpen_doesNotUpgradePlainPostParamToGetnewpost() {
        val url = "https://4pda.to/forum/index.php?showtopic=12345&p=999"
        val resolution = resolveSearchFindPostThemeOpen(url)
        assertTrue(resolution.url.contains("view=getnewpost"))
        assertFalse(resolution.url.contains("view=findpost"))
    }

    @Test
    fun themeOpenArgsForSearchUrl_marksExplicitPostIntent() {
        val url = buildSearchFindPostTopicUrl(topicId = 1, postId = 42)
        val args = themeOpenArgsForSearchUrl(url)
        assertEquals("search", args["topic_open_source"])
        assertEquals("explicit_post", args["topic_open_intent"])
    }

    @Test
    fun themeOpenArgsForSearchUrl_plainTopicSearchHasNoExplicitIntent() {
        val args = themeOpenArgsForSearchUrl("https://4pda.to/forum/index.php?act=search&query=test")
        assertEquals("search", args["topic_open_source"])
        assertFalse(args.containsKey("topic_open_intent"))
    }
}
