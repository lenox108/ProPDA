package forpdateam.ru.forpda.entity.remote.search

import forpdateam.ru.forpda.presentation.search.forumSectionSearchUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchSettingsTest {

    @Test
    fun forumSectionSearchUrl_limitsSearchToForumWithoutSubforums() {
        val settings = SearchSettings.parseSettings(forumSectionSearchUrl(42))

        assertEquals(listOf("42"), settings.forums)
        assertEquals(SearchSettings.SOURCE_TITLES.first, settings.source)
        assertEquals(SearchSettings.RESULT_TOPICS.first, settings.result)
        assertEquals(SearchSettings.SUB_FORUMS_FALSE, settings.subforums)
        assertTrue(settings.topics.isEmpty())
        assertTrue(forumSectionSearchUrl(42).contains("source=top"))
        assertTrue(forumSectionSearchUrl(42).contains("result=topics"))
    }

    @Test
    fun toUrl_userSearchWithUserId_omitsEmojiNickAuthorParameter() {
        val url = SearchSettings().apply {
            nick = "⚡ elektrik ⚡"
            userId = 598
            result = SearchSettings.RESULT_POSTS.first
        }.toUrl()

        assertFalse(url.contains("username="))
        assertTrue(url.contains("username-id=598"))
        assertFalse(url.contains("%3F"))
    }

    @Test
    fun toUrl_userSearchWithoutUserId_keepsLegacyNickAuthorParameter() {
        val url = SearchSettings().apply {
            nick = "Пользователь"
            result = SearchSettings.RESULT_POSTS.first
        }.toUrl()

        assertTrue(url.contains("username=%CF%EE%EB%FC%E7%EE%E2%E0%F2%E5%EB%FC"))
        assertFalse(url.contains("username-id="))
    }

    @Test
    fun parseSettings_userSearchWithEmojiNick_restoresNickAndUserId() {
        val settings = SearchSettings.parseSettings(
                "https://4pda.to/forum/index.php?act=search" +
                        "&username=%E2%9A%A1+elektrik+%E2%9A%A1" +
                        "&username-id=598"
        )

        assertEquals("⚡ elektrik ⚡", settings.nick)
        assertEquals(598, settings.userId)
    }
}
