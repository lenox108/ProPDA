package forpdateam.ru.forpda.presentation.theme

import forpdateam.ru.forpda.common.Preferences as AppPreferences
import forpdateam.ru.forpda.entity.remote.favorites.FavItem
import forpdateam.ru.forpda.entity.remote.favorites.FavoriteReadState
import forpdateam.ru.forpda.presentation.favorites.FavoritesTopicNavigationPolicy
import forpdateam.ru.forpda.presentation.theme.TopicOpenIntentClassifier.FRESH_FAVORITES
import forpdateam.ru.forpda.presentation.theme.TopicOpenIntentClassifier.FRESH_FORUM
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * LAST_UNREAD open matrix: unread → getnewpost; read list rows → getnewpost (setting always asks server).
 */
class TopicOpenLastUnreadMatrixTest {

    private val plainTopic = "https://4pda.to/forum/index.php?showtopic=123"
    private val getNewPost = "https://4pda.to/forum/index.php?showtopic=123&view=getnewpost"

    @Test
    fun unreadFavoriteWithIsNew_passesGetNewPost() {
        val resolution = resolveFavorite(unreadUrlFromList = getNewPost)
        assertTrue(resolution.url.contains("view=getnewpost"))
        assertEquals(TopicOpenTargetType.SERVER_UNREAD_FALLBACK, resolution.targetType)
    }

    @Test
    fun unreadFavoriteStaleIsNew_readStateUnreadStillPassesGetNewPost() {
        val item = FavItem().apply {
            topicId = 456
            isNew = false
            readState = FavoriteReadState.UNREAD
            unreadPostCount = 2
        }
        val hints = FavoritesTopicNavigationPolicy.buildListHints(item)
        assertTrue(hints.topicMarkedUnread)
        assertEquals(getNewPost.replace("123", "456"), hints.unreadUrlFromList)

        val resolution = TopicOpenTargetResolver.resolve(
                TopicOpenContext(
                        rawUrl = "https://4pda.to/forum/index.php?showtopic=456",
                        setting = AppPreferences.Main.TopicOpenTarget.LAST_UNREAD,
                        sourceScreen = "favorites",
                        openIntentRaw = FRESH_FAVORITES,
                        unreadUrlFromList = hints.unreadUrlFromList,
                        listTopicMarkedUnread = hints.topicMarkedUnread
                )
        )
        assertTrue(resolution.url.contains("view=getnewpost"))
        assertFalse("read topics must not use list_read_no_unread_hint", resolution.reason == "list_read_no_unread_hint")
    }

    @Test
    fun readFavoriteWithGetNewPostInListingHref_opensAtFirstUnread() {
        val item = FavItem().apply {
            topicId = 789
            isNew = false
            readState = FavoriteReadState.READ
            listingHref = "https://4pda.to/forum/index.php?showtopic=789&view=getnewpost"
        }
        val hints = FavoritesTopicNavigationPolicy.buildListHints(item)
        assertFalse(hints.topicMarkedUnread)
        assertEquals(null, hints.unreadUrlFromList)
        assertEquals(
                "https://4pda.to/forum/index.php?showtopic=789&view=getlastpost",
                hints.lastReadUrlFromList
        )

        val resolution = TopicOpenTargetResolver.resolve(
                TopicOpenContext(
                        rawUrl = "https://4pda.to/forum/index.php?showtopic=789",
                        setting = AppPreferences.Main.TopicOpenTarget.LAST_UNREAD,
                        sourceScreen = "favorites",
                        openIntentRaw = FRESH_FAVORITES,
                        lastReadUrlFromList = hints.lastReadUrlFromList
                )
        )
        assertEquals(
                "https://4pda.to/forum/index.php?showtopic=789&view=getnewpost",
                resolution.url
        )
        assertEquals("list_read_use_getnewpost", resolution.reason)
    }

    @Test
    fun readFavoriteOpensAtFirstUnread_log1121483() {
        // Read favorites row under LAST_UNREAD must seek the first unread (getnewpost), never the
        // last-read bookmark (getlastpost), so stale list read-state cannot strand the user on an
        // already-read post.
        val item = FavItem().apply {
            topicId = 1121483
            isNew = false
            readState = FavoriteReadState.READ
        }
        val hints = FavoritesTopicNavigationPolicy.buildListHints(item)
        val resolution = TopicOpenTargetResolver.resolve(
                TopicOpenContext(
                        rawUrl = "https://4pda.to/forum/index.php?showtopic=1121483",
                        setting = AppPreferences.Main.TopicOpenTarget.LAST_UNREAD,
                        sourceScreen = "favorites",
                        openIntentRaw = FRESH_FAVORITES,
                        lastReadUrlFromList = hints.lastReadUrlFromList
                )
        )
        assertTrue(resolution.url.contains("view=getnewpost"))
        assertFalse(resolution.url.contains("view=getlastpost"))
        assertEquals("list_read_use_getnewpost", resolution.reason)
    }

    @Test
    fun forumReadWithoutUnreadMarkers_opensAtFirstUnread() {
        val resolution = TopicOpenTargetResolver.resolve(
                TopicOpenContext(
                        rawUrl = plainTopic,
                        setting = AppPreferences.Main.TopicOpenTarget.LAST_UNREAD,
                        sourceScreen = "topics",
                        openIntentRaw = FRESH_FORUM,
                        lastReadUrlFromList = "https://4pda.to/forum/index.php?showtopic=123&view=getlastpost"
                )
        )
        assertEquals(
                "https://4pda.to/forum/index.php?showtopic=123&view=getnewpost",
                resolution.url
        )
        assertEquals("list_read_use_getnewpost", resolution.reason)
    }

    @Test
    fun forumUnreadWithPlusMarker_plainHrefGetsGetNewPost() {
        val resolution = TopicOpenTargetResolver.resolve(
                TopicOpenContext(
                        rawUrl = plainTopic,
                        setting = AppPreferences.Main.TopicOpenTarget.LAST_UNREAD,
                        sourceScreen = "topics",
                        openIntentRaw = FRESH_FORUM,
                        unreadUrlFromList = getNewPost,
                        listTopicMarkedUnread = true
                )
        )
        assertEquals(getNewPost, resolution.url)
        assertEquals(TopicOpenTargetType.SERVER_UNREAD_FALLBACK, resolution.targetType)
    }

    @Test
    fun listTopicMarkedUnreadWithoutUrl_stillUsesGetNewPost() {
        val resolution = TopicOpenTargetResolver.resolve(
                TopicOpenContext(
                        rawUrl = plainTopic,
                        setting = AppPreferences.Main.TopicOpenTarget.LAST_UNREAD,
                        sourceScreen = "favorites",
                        openIntentRaw = FRESH_FAVORITES,
                        listTopicMarkedUnread = true
                )
        )
        assertTrue(resolution.url.contains("view=getnewpost"))
        assertFalse(resolution.reason == "list_read_no_unread_hint")
    }

    @Test
    fun inspectorMergedUnreadFavorite_usesGetNewPostNotFirstPage_log1103268() {
        val item = FavItem().apply {
            topicId = 1103268
            isNew = true
            readState = FavoriteReadState.UNREAD
            unreadPostCount = 1
            listingHref = "https://4pda.to/forum/index.php?showtopic=1103268&view=getnewpost"
        }
        val hints = FavoritesTopicNavigationPolicy.buildListHints(item)
        val resolution = TopicOpenTargetResolver.resolve(
                TopicOpenContext(
                        rawUrl = "https://4pda.to/forum/index.php?showtopic=1103268",
                        setting = AppPreferences.Main.TopicOpenTarget.LAST_UNREAD,
                        sourceScreen = "favorites",
                        openIntentRaw = FRESH_FAVORITES,
                        unreadUrlFromList = hints.unreadUrlFromList,
                        listTopicMarkedUnread = hints.topicMarkedUnread
                )
        )
        assertTrue(resolution.url.contains("view=getnewpost"))
        assertEquals(TopicOpenTargetType.SERVER_UNREAD_FALLBACK, resolution.targetType)
        assertFalse(resolution.reason == "list_read_no_unread_hint")
    }

    @Test
    fun nonListOpenWithLastUnreadSetting_usesGetNewPost() {
        val resolution = TopicOpenTargetResolver.resolve(
                TopicOpenContext(
                        rawUrl = plainTopic,
                        setting = AppPreferences.Main.TopicOpenTarget.LAST_UNREAD,
                        sourceScreen = "theme_tab"
                )
        )
        assertTrue(resolution.url.contains("view=getnewpost"))
        assertEquals(TopicOpenTargetType.SETTING_LAST_UNREAD, resolution.targetType)
    }

    private fun resolveFavorite(
            topicId: Int = 123,
            unreadUrlFromList: String? = getNewPost
    ): TopicOpenResolution = TopicOpenTargetResolver.resolve(
            TopicOpenContext(
                    rawUrl = "https://4pda.to/forum/index.php?showtopic=$topicId",
                    setting = AppPreferences.Main.TopicOpenTarget.LAST_UNREAD,
                    sourceScreen = "favorites",
                    openIntentRaw = FRESH_FAVORITES,
                    unreadUrlFromList = unreadUrlFromList,
                    listTopicMarkedUnread = !unreadUrlFromList.isNullOrBlank()
            )
    )
}
