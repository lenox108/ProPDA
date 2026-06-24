package forpdateam.ru.forpda.presentation.theme

import forpdateam.ru.forpda.common.Preferences as AppPreferences
import forpdateam.ru.forpda.model.repository.theme.ThemePageMemoryCache
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Eight reliability scenarios from OPEN_UNREAD hardening spec.
 */
class TopicOpenReliabilityTest {

    private val topicUrl = "https://4pda.to/forum/index.php?showtopic=123"
    private val unreadUrl = "https://4pda.to/forum/index.php?showtopic=123&view=getnewpost"

    @Test
    fun scenario1_freshFavoritesWithUnreadUrl_blocksSavedScrollAndSelectsUnread() {
        val resolution = TopicOpenTargetResolver.resolve(
                TopicOpenContext(
                        rawUrl = topicUrl,
                        setting = AppPreferences.Main.TopicOpenTarget.LAST_UNREAD,
                        sourceScreen = "favorites",
                        openIntentRaw = TopicOpenIntentClassifier.FRESH_FAVORITES,
                        unreadUrlFromList = unreadUrl,
                        cachedLastPage = 5,
                        cachedScrollPosition = 1200
                )
        )
        assertEquals(TopicOpenTargetType.SERVER_UNREAD_FALLBACK, resolution.targetType)
        assertTrue(resolution.url.contains("view=getnewpost"))
        assertTrue(resolution.suppressScrollRestore)
        assertFalse(
                TopicOpenScrollRestorePolicy.savedScrollRestoreAllowed(
                        openIntentRaw = TopicOpenIntentClassifier.FRESH_FAVORITES,
                        setting = AppPreferences.Main.TopicOpenTarget.LAST_UNREAD,
                        loadAction = ThemeLoadAction.Normal
                )
        )
    }

    @Test
    fun scenario2b_freshFavoritesWithoutUnreadUrl_resumesAtLastRead_log24_06_14() {
        // Log 24_06-14-15: a fully-read favorites row under LAST_UNREAD now
        // uses `view=getlastpost` (server last-read bookmark) so the user
        // resumes at the actual last-read post and the highlight lands on
        // it. The previous `getnewpost` contract resolved to the all-read
        // bottom bookmark of an already-read topic — the user was stranded
        // on the last page top with no highlight.
        val resolution = TopicOpenTargetResolver.resolve(
                TopicOpenContext(
                        rawUrl = topicUrl,
                        setting = AppPreferences.Main.TopicOpenTarget.LAST_UNREAD,
                        sourceScreen = "favorites",
                        openIntentRaw = TopicOpenIntentClassifier.FRESH_FAVORITES,
                        lastReadUrlFromList = "https://4pda.to/forum/index.php?showtopic=123&view=getlastpost",
                        cachedLastPage = 1216,
                        cachedScrollPosition = 4756
                )
        )
        assertEquals(
                "https://4pda.to/forum/index.php?showtopic=123&view=getlastpost",
                resolution.url
        )
        assertEquals(TopicOpenTargetType.READ_RESUME, resolution.targetType)
        assertEquals("list_read_use_getlastpost", resolution.reason)
        assertTrue(resolution.suppressScrollRestore)
    }

    @Test
    fun scenario2_freshForumWithUnreadPostIdHint_usesGetNewPostNotFindpost() {
        val resolution = TopicOpenTargetResolver.resolve(
                TopicOpenContext(
                        rawUrl = topicUrl,
                        setting = AppPreferences.Main.TopicOpenTarget.LAST_UNREAD,
                        sourceScreen = "topics",
                        openIntentRaw = TopicOpenIntentClassifier.FRESH_FORUM,
                        unreadPostIdFromList = 999,
                        cachedLastPage = 3,
                        cachedScrollPosition = 800
                )
        )
        assertEquals(TopicOpenTargetType.SETTING_LAST_UNREAD, resolution.targetType)
        assertTrue(resolution.url.contains("view=getnewpost"))
        assertFalse(resolution.url.contains("findpost"))
        assertTrue(resolution.suppressScrollRestore)
    }

    @Test
    fun scenario3_secondFreshOpenSameTopic_stillUnreadWhenMetadataPresent() {
        val first = TopicOpenTargetResolver.resolve(
                TopicOpenContext(
                        rawUrl = topicUrl,
                        setting = AppPreferences.Main.TopicOpenTarget.LAST_UNREAD,
                        sourceScreen = "favorites",
                        openIntentRaw = TopicOpenIntentClassifier.FRESH_FAVORITES,
                        unreadUrlFromList = unreadUrl
                )
        )
        val second = TopicOpenTargetResolver.resolve(
                TopicOpenContext(
                        rawUrl = topicUrl,
                        setting = AppPreferences.Main.TopicOpenTarget.LAST_UNREAD,
                        sourceScreen = "favorites",
                        openIntentRaw = TopicOpenIntentClassifier.FRESH_FAVORITES,
                        unreadUrlFromList = unreadUrl,
                        cachedLastPage = 5,
                        cachedScrollPosition = 2400
                )
        )
        assertEquals(first.url, second.url)
        assertTrue(second.suppressScrollRestore)
        assertFalse(
                TopicOpenScrollRestorePolicy.savedScrollRestoreAllowed(
                        TopicOpenIntentClassifier.FRESH_FAVORITES,
                        AppPreferences.Main.TopicOpenTarget.LAST_UNREAD,
                        ThemeLoadAction.Normal
                )
        )
    }

    @Test
    fun scenario4_backRestore_allowsSavedScrollDespiteOpenUnreadSetting() {
        assertTrue(
                TopicOpenScrollRestorePolicy.savedScrollRestoreAllowed(
                        openIntentRaw = TopicOpenIntentClassifier.BACK_RESTORE,
                        setting = AppPreferences.Main.TopicOpenTarget.LAST_UNREAD,
                        loadAction = ThemeLoadAction.Back
                )
        )
        assertTrue(TopicOpenIntentClassifier.isRestoreIntent(TopicOpenIntentClassifier.BACK_RESTORE))
    }

    @Test
    fun scenario5_rotationRestore_allowsSavedScrollDespiteOpenUnreadSetting() {
        assertTrue(
                TopicOpenScrollRestorePolicy.savedScrollRestoreAllowed(
                        openIntentRaw = TopicOpenIntentClassifier.ROTATION_RESTORE,
                        setting = AppPreferences.Main.TopicOpenTarget.LAST_UNREAD,
                        loadAction = ThemeLoadAction.Normal
                )
        )
    }

    @Test
    fun scenario6_explicitPostLink_winsOverOpenUnreadSetting() {
        val url = "https://4pda.to/forum/index.php?showtopic=123&view=findpost&p=456"
        val resolution = TopicOpenTargetResolver.resolve(
                TopicOpenContext(
                        rawUrl = url,
                        setting = AppPreferences.Main.TopicOpenTarget.LAST_UNREAD,
                        sourceScreen = "search",
                        openIntentRaw = TopicOpenIntentClassifier.EXPLICIT_POST
                )
        )
        assertEquals(TopicOpenTargetType.EXPLICIT_POST, resolution.targetType)
        assertEquals(url, resolution.url)
        assertFalse(resolution.suppressScrollRestore)
    }

    @Test
    fun scenario7_cacheSkippedForGetNewPost_doesNotUseOldPageAsTarget() {
        assertTrue(ThemePageMemoryCache.shouldSkipCache(unreadUrl))
        assertFalse(
                ThemePageMemoryCache.shouldSkipCache(
                        "https://4pda.to/forum/index.php?showtopic=123&st=40"
                )
        )
    }

    @Test
    fun scenario8_topicRefresh_doesNotSuppressSavedScrollDespiteFreshUnreadOpen() {
        assertFalse(
                TopicOpenScrollRestorePolicy.shouldSuppressScrollRestoreOnRender(
                        suppressScrollRestoreForOpen = true,
                        pendingUnreadOpenSuppressScroll = true,
                        loadAction = ThemeLoadAction.Refresh,
                        hasActiveRefreshRestore = true,
                        themeUrl = unreadUrl,
                        topicOpenTarget = AppPreferences.Main.TopicOpenTarget.LAST_UNREAD
                )
        )
        assertTrue(
                TopicOpenScrollRestorePolicy.savedScrollRestoreAllowed(
                        openIntentRaw = TopicOpenIntentClassifier.FRESH_FAVORITES,
                        setting = AppPreferences.Main.TopicOpenTarget.LAST_UNREAD,
                        loadAction = ThemeLoadAction.Refresh
                )
        )
    }

    @Test
    fun scenario9_topicRefresh_preservesAnchorDespiteOpenUnreadAndStShift() {
        assertTrue(
                TopicOpenScrollRestorePolicy.savedScrollRestoreAllowed(
                        openIntentRaw = TopicOpenIntentClassifier.FRESH_FAVORITES,
                        setting = AppPreferences.Main.TopicOpenTarget.LAST_UNREAD,
                        loadAction = ThemeLoadAction.Refresh
                )
        )
        assertFalse(
                TopicOpenScrollRestorePolicy.shouldSuppressScrollRestoreOnRender(
                        suppressScrollRestoreForOpen = true,
                        pendingUnreadOpenSuppressScroll = true,
                        loadAction = ThemeLoadAction.Refresh,
                        hasActiveRefreshRestore = false,
                        themeUrl = unreadUrl,
                        topicOpenTarget = AppPreferences.Main.TopicOpenTarget.LAST_UNREAD
                )
        )
        assertTrue(
                TopicOpenScrollRestorePolicy.refreshRestorePageMatches(
                        requestTopicId = 123,
                        requestPageSt = 40,
                        requestTargetPageNumber = 3,
                        pageTopicId = 123,
                        pageSt = 60,
                        pageNumber = 3
                )
        )
        assertFalse(
                TopicOpenScrollRestorePolicy.detectSavedScrollOverrodeUnread(
                        openIntentRaw = TopicOpenIntentClassifier.FRESH_FAVORITES,
                        setting = AppPreferences.Main.TopicOpenTarget.LAST_UNREAD,
                        suppressScrollRestore = true,
                        scrollY = 900,
                        refreshRestoreId = "abc12345",
                        restoreScheduled = true,
                        scrollToUnreadExecuted = null,
                        loadAction = ThemeLoadAction.Refresh
                )
        )
    }

    @Test
    fun scenario10_refreshRestoreMatcher_requiresSameTopic() {
        assertFalse(
                TopicOpenScrollRestorePolicy.refreshRestorePageMatches(
                        requestTopicId = 123,
                        requestPageSt = 0,
                        requestTargetPageNumber = 1,
                        pageTopicId = 456,
                        pageSt = 0,
                        pageNumber = 1
                )
        )
    }

    @Test
    fun scenario11_staleRenderGuard_rejectsOldToken() {
        val guard = ThemeRenderGuard()
        val token = guard.newToken()
        assertTrue(guard.isValid(token))
        guard.invalidate()
        assertFalse(guard.isValid(token))
        val newToken = guard.newToken()
        assertFalse(guard.isValid(token))
        assertTrue(guard.isValid(newToken))
    }
}
