package forpdateam.ru.forpda.presentation.theme

import forpdateam.ru.forpda.common.Preferences as AppPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TopicOpenTargetResolverTest {

    private fun resolve(url: String, setting: AppPreferences.Main.TopicOpenTarget = AppPreferences.Main.TopicOpenTarget.LAST_UNREAD) =
            TopicOpenTargetResolver.resolve(
                    TopicOpenContext(rawUrl = url, setting = setting, sourceScreen = "test")
            )

    @Test
    fun lastUnreadAddsGetNewPostForPlainTopic() {
        val resolution = resolve("https://4pda.to/forum/index.php?showtopic=123")
        assertEquals(
                "https://4pda.to/forum/index.php?showtopic=123&view=getnewpost",
                resolution.url
        )
        assertEquals(TopicOpenTargetType.SETTING_LAST_UNREAD, resolution.targetType)
        assertTrue(resolution.suppressScrollRestore)
    }

    @Test
    fun lastUnreadUpgradesGetLastPostToGetNewPost() {
        val resolution = resolve("https://4pda.to/forum/index.php?showtopic=123&view=getlastpost")
        assertEquals(
                "https://4pda.to/forum/index.php?showtopic=123&view=getnewpost",
                resolution.url
        )
    }

    @Test
    fun lastUnreadAddsGetNewPostForStZero() {
        val resolution = resolve("https://4pda.to/forum/index.php?showtopic=123&st=0")
        assertEquals(
                "https://4pda.to/forum/index.php?showtopic=123&st=0&view=getnewpost",
                resolution.url
        )
    }

    @Test
    fun firstPageKeepsPlainTopicUrl() {
        val resolution = resolve(
                "https://4pda.to/forum/index.php?showtopic=123",
                AppPreferences.Main.TopicOpenTarget.FIRST_PAGE
        )
        assertEquals(
                "https://4pda.to/forum/index.php?showtopic=123",
                resolution.url
        )
    }

    @Test
    fun firstPageKeepsGetLastPost() {
        val url = "https://4pda.to/forum/index.php?showtopic=123&view=getlastpost"
        val resolution = resolve(url, AppPreferences.Main.TopicOpenTarget.FIRST_PAGE)
        assertEquals(url, resolution.url)
    }

    @Test
    fun lastUnreadStripsListResumeStAndUpgradesGetLastPost() {
        val cases = listOf(
                "https://4pda.to/forum/index.php?showtopic=123&st=20" to
                        "https://4pda.to/forum/index.php?showtopic=123&view=getnewpost",
                "https://4pda.to/forum/index.php?showtopic=123&st=40&view=getlastpost" to
                        "https://4pda.to/forum/index.php?showtopic=123&view=getnewpost",
        )
        cases.forEach { (input, expected) ->
            val resolution = resolve(input)
            assertEquals(expected, resolution.url)
            assertEquals(TopicOpenTargetType.SETTING_LAST_UNREAD, resolution.targetType)
            assertTrue(resolution.suppressScrollRestore)
        }
    }

    @Test
    fun paginationExplicitPageWinsOverLastUnreadSetting() {
        val url = "https://4pda.to/forum/index.php?showtopic=123&st=20"
        val resolution = TopicOpenTargetResolver.resolve(
                TopicOpenContext(
                        rawUrl = url,
                        setting = AppPreferences.Main.TopicOpenTarget.LAST_UNREAD,
                        sourceScreen = "pagination"
                )
        )
        assertEquals(url, resolution.url)
        assertEquals(TopicOpenTargetType.EXPLICIT_PAGE, resolution.targetType)
    }

    @Test
    fun explicitPageIntentSourcesWinOverLastUnreadSetting() {
        val url = "https://4pda.to/forum/index.php?showtopic=123&st=20"
        val sources = listOf("search", "qms", "internal_link", "history")
        sources.forEach { source ->
            val resolution = TopicOpenTargetResolver.resolve(
                    TopicOpenContext(
                            rawUrl = url,
                            setting = AppPreferences.Main.TopicOpenTarget.LAST_UNREAD,
                            sourceScreen = source
                    )
            )
            assertEquals(url, resolution.url)
            assertEquals(TopicOpenTargetType.EXPLICIT_PAGE, resolution.targetType)
        }
    }

    @Test
    fun explicitTargetsWinOverLastUnreadSetting() {
        val unchanged = listOf(
                "https://4pda.to/forum/index.php?showtopic=123&view=findpost&p=456",
                "https://4pda.to/forum/index.php?act=findpost&pid=456",
                "https://4pda.to/forum/index.php?showtopic=123&view=getnewpost",
                "https://4pda.to/forum/index.php?showtopic=123#entry99"
        )
        unchanged.forEach { url ->
            assertEquals(url, resolve(url).url)
        }
    }

    @Test
    fun bookmarkSourceWithPlainPostParamResolvesToExplicitPost() {
        // Saved bookmark whose link carries only `p=` (no view=findpost) must still open the exact post.
        val resolution = TopicOpenTargetResolver.resolve(
                TopicOpenContext(
                        rawUrl = "https://4pda.to/forum/index.php?showtopic=123&p=456",
                        setting = AppPreferences.Main.TopicOpenTarget.LAST_UNREAD,
                        sourceScreen = "bookmark"
                )
        )
        assertEquals(TopicOpenTargetType.EXPLICIT_POST, resolution.targetType)
        assertEquals(456, resolution.resolvedPostId)
        assertTrue(resolution.url.contains("view=findpost"))
        assertTrue(resolution.url.contains("p=456"))
        assertEquals("explicit_post_source", resolution.reason)
    }

    @Test
    fun explicitPostIntentWithPidResolvesToExplicitPost() {
        val resolution = TopicOpenTargetResolver.resolve(
                TopicOpenContext(
                        rawUrl = "https://4pda.to/forum/index.php?showtopic=123&pid=789",
                        setting = AppPreferences.Main.TopicOpenTarget.LAST_UNREAD,
                        sourceScreen = "link",
                        openIntentRaw = TopicOpenIntentClassifier.EXPLICIT_POST
                )
        )
        assertEquals(TopicOpenTargetType.EXPLICIT_POST, resolution.targetType)
        assertEquals(789, resolution.resolvedPostId)
        assertTrue(resolution.url.contains("view=findpost"))
    }

    @Test
    fun bookmarkSourceWithFindpostStaysExplicitPost() {
        // App-generated bookmark URLs already carry view=findpost — must remain explicit and unchanged.
        val url = "https://4pda.to/forum/index.php?s=&showtopic=123&view=findpost&p=456"
        val resolution = TopicOpenTargetResolver.resolve(
                TopicOpenContext(
                        rawUrl = url,
                        setting = AppPreferences.Main.TopicOpenTarget.LAST_UNREAD,
                        sourceScreen = "bookmark",
                        openIntentRaw = TopicOpenIntentClassifier.EXPLICIT_POST
                )
        )
        assertEquals(TopicOpenTargetType.EXPLICIT_POST, resolution.targetType)
        assertEquals(456, resolution.resolvedPostId)
        assertEquals(url, resolution.url)
    }

    @Test
    fun bookmarkSourceLegacyGetnewpostWithPostParamUpgradesToFindpost() {
        val resolution = TopicOpenTargetResolver.resolve(
                TopicOpenContext(
                        rawUrl = "https://4pda.to/forum/index.php?showtopic=123&view=getnewpost&p=456",
                        setting = AppPreferences.Main.TopicOpenTarget.LAST_UNREAD,
                        sourceScreen = "bookmark"
                )
        )
        assertEquals(TopicOpenTargetType.EXPLICIT_POST, resolution.targetType)
        assertEquals(456, resolution.resolvedPostId)
        assertTrue(resolution.url.contains("view=findpost"))
        assertFalse(resolution.url.contains("view=getnewpost"))
        assertTrue(resolution.url.contains("p=456"))
        assertEquals("explicit_post_source_legacy_view", resolution.reason)
    }

    @Test
    fun bookmarkSourceLegacyAnchorUrlWithoutShowtopicResolvesToExplicitPost() {
        val resolution = TopicOpenTargetResolver.resolve(
                TopicOpenContext(
                        rawUrl = "https://4pda.to/forum/index.php?act=findpost&pid=456&anchor=entry456",
                        setting = AppPreferences.Main.TopicOpenTarget.LAST_UNREAD,
                        sourceScreen = "bookmark",
                        openIntentRaw = TopicOpenIntentClassifier.EXPLICIT_POST
                )
        )
        assertEquals(TopicOpenTargetType.EXPLICIT_POST, resolution.targetType)
        assertEquals(456, resolution.resolvedPostId)
        assertEquals("explicit_post_without_topic_id", resolution.reason)
    }

    @Test
    fun bookmarkSourcePlainTopicStillOpensUnread() {
        // A bookmark to a topic with no saved post must keep unread-open behavior (not invent a post).
        val resolution = TopicOpenTargetResolver.resolve(
                TopicOpenContext(
                        rawUrl = "https://4pda.to/forum/index.php?showtopic=123",
                        setting = AppPreferences.Main.TopicOpenTarget.LAST_UNREAD,
                        sourceScreen = "bookmark",
                        openIntentRaw = TopicOpenIntentClassifier.EXPLICIT_POST
                )
        )
        assertEquals(TopicOpenTargetType.SETTING_LAST_UNREAD, resolution.targetType)
        assertTrue(resolution.url.contains("view=getnewpost"))
    }

    @Test
    fun favoritesReadTopicWithoutUnreadHintStillUsesGetNewPostForLastUnreadSetting() {
        val resolution = TopicOpenTargetResolver.resolve(
                TopicOpenContext(
                        rawUrl = "https://4pda.to/forum/index.php?showtopic=1103268",
                        setting = AppPreferences.Main.TopicOpenTarget.LAST_UNREAD,
                        sourceScreen = "favorites",
                        openIntentRaw = TopicOpenIntentClassifier.FRESH_FAVORITES,
                        lastReadUrlFromList = "https://4pda.to/forum/index.php?showtopic=1103268&view=getlastpost"
                )
        )
        assertEquals(
                "https://4pda.to/forum/index.php?showtopic=1103268&view=getnewpost",
                resolution.url
        )
        assertEquals(TopicOpenTargetType.SETTING_LAST_UNREAD, resolution.targetType)
        assertEquals("list_read_use_getnewpost", resolution.reason)
        assertTrue(resolution.suppressScrollRestore)
    }

    @Test
    fun listSourceWithPlainPostParamStaysLastReadHint() {
        // Favorites/topics list opens still treat `p=` as a last-read hint, not an explicit post.
        val resolution = TopicOpenTargetResolver.resolve(
                TopicOpenContext(
                        rawUrl = "https://4pda.to/forum/index.php?showtopic=123&p=456",
                        setting = AppPreferences.Main.TopicOpenTarget.LAST_UNREAD,
                        sourceScreen = "favorites",
                        openIntentRaw = TopicOpenIntentClassifier.FRESH_FAVORITES
                )
        )
        assertEquals(TopicOpenTargetType.SETTING_LAST_UNREAD, resolution.targetType)
        assertTrue(resolution.url.contains("view=getnewpost"))
        assertFalse(resolution.url.contains("view=findpost"))
    }

    @Test
    fun lastUnreadStripsListLastReadPostParamWithoutFindpost() {
        val cases = listOf(
                "https://4pda.to/forum/index.php?showtopic=123&p=456" to
                        "https://4pda.to/forum/index.php?showtopic=123&view=getnewpost",
                "https://4pda.to/forum/index.php?showtopic=123&pid=456" to
                        "https://4pda.to/forum/index.php?showtopic=123&view=getnewpost",
        )
        cases.forEach { (input, expected) ->
            val resolution = resolve(input)
            assertEquals(expected, resolution.url)
            assertEquals(TopicOpenTargetType.SETTING_LAST_UNREAD, resolution.targetType)
        }
    }

    @Test
    fun userActionUnreadWinsOverFirstPageSetting() {
        val resolution = TopicOpenTargetResolver.resolve(
                TopicOpenContext(
                        rawUrl = "https://4pda.to/forum/index.php?showtopic=123",
                        setting = AppPreferences.Main.TopicOpenTarget.FIRST_PAGE,
                        userAction = TopicUserOpenAction.UNREAD
                )
        )
        assertTrue(resolution.url.contains("view=getnewpost"))
        assertEquals(TopicOpenTargetType.USER_ACTION, resolution.targetType)
    }

    @Test
    fun serverUnreadPostIdFromListIgnoredUsesGetNewPost() {
        val resolution = TopicOpenTargetResolver.resolve(
                TopicOpenContext(
                        rawUrl = "https://4pda.to/forum/index.php?showtopic=123",
                        setting = AppPreferences.Main.TopicOpenTarget.LAST_UNREAD,
                        unreadPostIdFromList = 999
                )
        )
        assertTrue(resolution.url.contains("view=getnewpost"))
        assertFalse(resolution.url.contains("view=findpost"))
        assertEquals(TopicOpenTargetType.SETTING_LAST_UNREAD, resolution.targetType)
        assertEquals("ignored_last_read_post_id_use_getnewpost", resolution.reason)
        assertTrue(resolution.suppressScrollRestore)
    }

    @Test
    fun serverUnreadUrlFromListStripsResumeSt() {
        val resolution = TopicOpenTargetResolver.resolve(
                TopicOpenContext(
                        rawUrl = "https://4pda.to/forum/index.php?showtopic=123&st=40",
                        setting = AppPreferences.Main.TopicOpenTarget.LAST_UNREAD,
                        unreadUrlFromList = "https://4pda.to/forum/index.php?showtopic=123&st=40&view=getnewpost"
                )
        )
        assertEquals(
                "https://4pda.to/forum/index.php?showtopic=123&view=getnewpost",
                resolution.url
        )
        assertEquals(TopicOpenTargetType.SERVER_UNREAD_FALLBACK, resolution.targetType)
        assertEquals("server_unread_url_stripped_list_st", resolution.reason)
    }

    @Test
    fun lastUnreadStripsLastReadPOnGetNewPostUrl() {
        val resolution = resolve("https://4pda.to/forum/index.php?showtopic=123&view=getnewpost&p=999")
        assertEquals(
                "https://4pda.to/forum/index.php?showtopic=123&view=getnewpost",
                resolution.url
        )
        assertEquals(TopicOpenTargetType.SETTING_LAST_UNREAD, resolution.targetType)
        assertTrue(resolution.suppressScrollRestore)
    }

    @Test
    fun serverUnreadUrlFromListStripsLastReadPostParam() {
        val resolution = TopicOpenTargetResolver.resolve(
                TopicOpenContext(
                        rawUrl = "https://4pda.to/forum/index.php?showtopic=123",
                        setting = AppPreferences.Main.TopicOpenTarget.LAST_UNREAD,
                        unreadUrlFromList = "https://4pda.to/forum/index.php?showtopic=123&view=getnewpost&p=999"
                )
        )
        assertEquals(
                "https://4pda.to/forum/index.php?showtopic=123&view=getnewpost",
                resolution.url
        )
        assertEquals(TopicOpenTargetType.SERVER_UNREAD_FALLBACK, resolution.targetType)
    }

    @Test
    fun ordinaryInitialTopicUrlExcludesExplicitTargets() {
        assertTrue(
                TopicOpenTargetResolver.isOrdinaryInitialTopicUrl("https://4pda.to/forum/index.php?showtopic=123")
        )
        assertTrue(
                TopicOpenTargetResolver.isOrdinaryInitialTopicUrl("https://4pda.to/forum/index.php?showtopic=123&view=getlastpost")
        )

        val urls = listOf(
                "https://4pda.to/forum/index.php?showtopic=123&view=findpost&p=456",
                "https://4pda.to/forum/index.php?showtopic=123&st=20",
                "https://4pda.to/forum/index.php?showtopic=123&view=getnewpost",
                "https://4pda.to/forum/index.php?showtopic=123#entry99"
        )

        urls.forEach { url ->
            assertFalse(TopicOpenTargetResolver.isOrdinaryInitialTopicUrl(url))
        }
        assertTrue(
                TopicOpenTargetResolver.isOrdinaryInitialTopicUrl(
                        "https://4pda.to/forum/index.php?showtopic=123&p=456"
                )
        )
    }
}
