package forpdateam.ru.forpda.presentation.favorites

import forpdateam.ru.forpda.common.Preferences as AppPreferences
import forpdateam.ru.forpda.entity.remote.favorites.FavItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FavoritesTopicNavigationPolicyTest {

    @Test
    fun unreadFavoriteTopic_passesGetNewPostHintAndFreshIntent() {
        val item = FavItem().apply {
            topicId = 456
            topicTitle = "Second topic"
            isNew = true
        }
        val hints = FavoritesTopicNavigationPolicy.buildListHints(item)
        assertEquals(
                "https://4pda.to/forum/index.php?showtopic=456&view=getnewpost",
                hints.unreadUrlFromList
        )

        val screen = FavoritesTopicNavigationPolicy.buildThemeScreen(item)
        assertEquals("https://4pda.to/forum/index.php?showtopic=456", screen.themeUrl)
        assertEquals("favorites", screen.topicOpenSource)
        assertEquals(
                forpdateam.ru.forpda.presentation.theme.TopicOpenIntentClassifier.FRESH_FAVORITES,
                screen.topicOpenIntent
        )
        assertEquals("Second topic", screen.screenTitle)
        assertTrue(screen.isAlone)
        assertEquals(
                "https://4pda.to/forum/index.php?showtopic=456&view=getnewpost",
                screen.unreadUrlFromList
        )
    }

    @Test
    fun readFavoriteTopic_hasLastReadHintButStillFreshOpen() {
        val item = FavItem().apply {
            topicId = 789
            isNew = false
        }
        val hints = FavoritesTopicNavigationPolicy.buildListHints(item)
        assertNull(hints.unreadUrlFromList)
        assertEquals(
                "https://4pda.to/forum/index.php?showtopic=789&view=getlastpost",
                hints.lastReadUrlFromList
        )

        val screen = FavoritesTopicNavigationPolicy.buildThemeScreen(item)
        assertEquals(
                forpdateam.ru.forpda.presentation.theme.TopicOpenIntentClassifier.FRESH_FAVORITES,
                screen.topicOpenIntent
        )
        assertNull(screen.unreadUrlFromList)
        assertEquals(
                "https://4pda.to/forum/index.php?showtopic=789&view=getlastpost",
                screen.lastReadUrlFromList
        )
    }

    @Test
    fun resolvePrefetchUrl_usesGetNewPostForUnreadFavorite() {
        val item = FavItem().apply {
            topicId = 456
            isNew = true
        }
        val url = FavoritesTopicNavigationPolicy.resolvePrefetchUrl(
                item,
                AppPreferences.Main.TopicOpenTarget.LAST_UNREAD
        )
        assertEquals(
                "https://4pda.to/forum/index.php?showtopic=456&view=getnewpost",
                url
        )
    }

    @Test
    fun resolvePrefetchUrl_usesFirstPageWhenSettingRequiresIt() {
        val item = FavItem().apply {
            topicId = 789
            isNew = false
        }
        val url = FavoritesTopicNavigationPolicy.resolvePrefetchUrl(
                item,
                AppPreferences.Main.TopicOpenTarget.FIRST_PAGE
        )
        assertEquals("https://4pda.to/forum/index.php?showtopic=789", url)
    }

    @Test
    fun parsedListingHref_upgradesGetNewPostToLastReadWhenFavoriteIsRead() {
        val item = FavItem().apply {
            topicId = 456
            isNew = false
            listingHref = "https://4pda.to/forum/index.php?showtopic=456&view=getnewpost"
        }
        val hints = FavoritesTopicNavigationPolicy.buildListHints(item)
        assertNull(hints.unreadUrlFromList)
        assertEquals(
                "https://4pda.to/forum/index.php?showtopic=456&view=getlastpost",
                hints.lastReadUrlFromList
        )
    }

    @Test
    fun repeatOpenAfterLocalRead1106099_usesReadPathNotStaleUnreadUrl() {
        val item = FavItem().apply {
            topicId = 1106099
            isNew = false
            readState = forpdateam.ru.forpda.entity.remote.favorites.FavoriteReadState.READ
            unreadPostCount = 0
            localReadPostId = 1106099
            listingHref = "https://4pda.to/forum/index.php?showtopic=1106099&view=getnewpost"
        }

        val screen = FavoritesTopicNavigationPolicy.buildThemeScreen(item)
        val prefetchUrl = FavoritesTopicNavigationPolicy.resolvePrefetchUrl(
                item,
                AppPreferences.Main.TopicOpenTarget.LAST_UNREAD
        )

        assertNull(screen.unreadUrlFromList)
        // List hint still carries the read-resume bookmark URL …
        assertEquals(
                "https://4pda.to/forum/index.php?showtopic=1106099&view=getlastpost",
                screen.lastReadUrlFromList
        )
        // … but under LAST_UNREAD the open/prefetch asks the server for the first unread (log 1121483):
        // getlastpost can only ever resolve to the already-read bookmark, so a stale read row would
        // otherwise strand the user on an old post. getnewpost falls back to the all-read bottom redirect.
        assertEquals(
                "https://4pda.to/forum/index.php?showtopic=1106099&view=getnewpost",
                prefetchUrl
        )
    }

    @Test
    fun parsedListingHref_passesUnreadUrlWhenFavoriteIsUnread() {
        val item = FavItem().apply {
            topicId = 456
            isNew = true
            listingHref = "https://4pda.to/forum/index.php?showtopic=456&view=getnewpost"
        }
        val hints = FavoritesTopicNavigationPolicy.buildListHints(item)
        assertEquals(
                "https://4pda.to/forum/index.php?showtopic=456&view=getnewpost",
                hints.unreadUrlFromList
        )
    }

    @Test
    fun resolvePrefetchUrl_usesGetNewPostForReadFavoriteWithLastUnreadSetting() {
        val item = FavItem().apply {
            topicId = 789
            isNew = false
        }
        val url = FavoritesTopicNavigationPolicy.resolvePrefetchUrl(
                item,
                AppPreferences.Main.TopicOpenTarget.LAST_UNREAD
        )
        // LAST_UNREAD always asks the server for the first unread, even for a read row (log 1121483).
        assertEquals("https://4pda.to/forum/index.php?showtopic=789&view=getnewpost", url)
    }

    @Test
    fun buildThemeScreen_prefersListUnreadUrlWhenPresent() {
        val item = FavItem().apply {
            topicId = 456
            isNew = true
            listingHref = "https://4pda.to/forum/index.php?showtopic=456&st=120&view=getnewpost#entry42"
        }

        val screen = FavoritesTopicNavigationPolicy.buildThemeScreen(item)

        assertTrue(screen.unreadUrlFromList.orEmpty().contains("showtopic=456"))
        assertTrue(screen.unreadUrlFromList.orEmpty().contains("view=getnewpost"))
        assertTrue(screen.listTopicMarkedUnread)
    }

    @Test
    fun buildThemeScreen_preservesListUnreadEntryAnchorWhenPresent() {
        val item = FavItem().apply {
            topicId = 456
            isNew = true
            listingHref = "https://4pda.to/forum/index.php?showtopic=456&st=120&view=getnewpost#entry42"
        }

        val screen = FavoritesTopicNavigationPolicy.buildThemeScreen(item)

        assertEquals(
                "https://4pda.to/forum/index.php?showtopic=456&st=120&view=getnewpost#entry42",
                screen.unreadUrlFromList
        )
        assertTrue(screen.listTopicMarkedUnread)
    }

    @Test
    fun forumFavorite_isIgnored() {
        val item = FavItem().apply {
            isForum = true
            forumId = 10
        }
        assertNull(FavoritesTopicNavigationPolicy.buildListHints(item).unreadUrlFromList)
    }

    @Test
    fun staleIsNewFalse_readStateUnreadStillBuildsUnreadHint() {
        val item = FavItem().apply {
            topicId = 901
            isNew = false
            readState = forpdateam.ru.forpda.entity.remote.favorites.FavoriteReadState.UNREAD
            unreadPostCount = 3
        }
        val hints = FavoritesTopicNavigationPolicy.buildListHints(item)
        assertTrue(hints.topicMarkedUnread)
        assertEquals(
                "https://4pda.to/forum/index.php?showtopic=901&view=getnewpost",
                hints.unreadUrlFromList
        )
    }

    @Test
    fun inspectorMarkedUnreadStillBuildsGetNewPostHintWhenReadStateStale() {
        val item = FavItem().apply {
            topicId = 1103268
            isNew = false
            readState = forpdateam.ru.forpda.entity.remote.favorites.FavoriteReadState.READ
            unreadPostCount = 0
            inspectorMarkedUnread = true
            listingHref = "https://4pda.to/forum/index.php?showtopic=1103268&view=getnewpost"
        }
        val hints = FavoritesTopicNavigationPolicy.buildListHints(item)
        assertTrue(hints.topicMarkedUnread)
        assertEquals(
                "https://4pda.to/forum/index.php?showtopic=1103268&view=getnewpost",
                hints.unreadUrlFromList
        )
    }

    @Test
    fun inspectorUnreadOverStaleHtml_buildsGetNewPostHint_log1103268() {
        val item = FavItem().apply {
            topicId = 1103268
            isNew = true
            readState = forpdateam.ru.forpda.entity.remote.favorites.FavoriteReadState.UNREAD
            unreadPostCount = 1
            inspectorMarkedUnread = true
            listingHref = "https://4pda.to/forum/index.php?showtopic=1103268&view=getnewpost"
        }
        val hints = FavoritesTopicNavigationPolicy.buildListHints(item)
        assertTrue(hints.topicMarkedUnread)
        assertEquals(
                "https://4pda.to/forum/index.php?showtopic=1103268&view=getnewpost",
                hints.unreadUrlFromList
        )
        val prefetchUrl = FavoritesTopicNavigationPolicy.resolvePrefetchUrl(
                item,
                AppPreferences.Main.TopicOpenTarget.LAST_UNREAD
        )
        assertEquals(
                "https://4pda.to/forum/index.php?showtopic=1103268&view=getnewpost",
                prefetchUrl
        )
    }

    @Test
    fun unreadPostCountWithoutIsNew_stillBuildsUnreadHint() {
        val item = FavItem().apply {
            topicId = 902
            isNew = false
            readState = forpdateam.ru.forpda.entity.remote.favorites.FavoriteReadState.UNREAD
            unreadPostCount = 1
            listingHref = "https://4pda.to/forum/index.php?showtopic=902&view=getnewpost"
        }
        val hints = FavoritesTopicNavigationPolicy.buildListHints(item)
        assertTrue(hints.topicMarkedUnread)
        assertEquals(
                "https://4pda.to/forum/index.php?showtopic=902&view=getnewpost",
                hints.unreadUrlFromList
        )
    }
}
