package forpdateam.ru.forpda.presentation.theme

import forpdateam.ru.forpda.common.Preferences as AppPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TopicUnreadFindPostReloadPolicyTest {

    private val getNewPostUrl = "https://4pda.to/forum/index.php?showtopic=1121483&view=getnewpost"

    @Test
    fun reloadsHybridUnreadOpenAfterGetNewPostResolvesAnchor() {
        assertTrue(
                TopicUnreadFindPostReloadPolicy.shouldReloadAsFindPost(
                        requestUrl = getNewPostUrl,
                        loadAction = ThemeLoadAction.Normal,
                        scrollMode = AppPreferences.Main.TopicScrollMode.HYBRID,
                        suppressScrollRestore = true,
                        openedViaFindPost = false,
                        alreadyUpgradedThisTrace = false,
                        hasUnreadTarget = true,
                        anchorPostId = "143805431",
                        pageAnchor = null,
                )
        )
    }

    @Test
    fun skipsFindPostReloadWhenAllReadBottomRedirect_log1121483() {
        assertFalse(
                TopicUnreadFindPostReloadPolicy.shouldReloadAsFindPost(
                        requestUrl = getNewPostUrl,
                        loadAction = ThemeLoadAction.Normal,
                        scrollMode = AppPreferences.Main.TopicScrollMode.HYBRID,
                        suppressScrollRestore = true,
                        openedViaFindPost = false,
                        alreadyUpgradedThisTrace = false,
                        hasUnreadTarget = false,
                        anchorPostId = "143805431",
                        pageAnchor = "entry143805431",
                )
        )
    }

    @Test
    fun skipsWhenAlreadyUpgradedOrOpenedViaFindPost() {
        assertFalse(
                TopicUnreadFindPostReloadPolicy.shouldReloadAsFindPost(
                        requestUrl = getNewPostUrl,
                        loadAction = ThemeLoadAction.Normal,
                        scrollMode = AppPreferences.Main.TopicScrollMode.HYBRID,
                        suppressScrollRestore = true,
                        openedViaFindPost = false,
                        alreadyUpgradedThisTrace = true,
                        hasUnreadTarget = true,
                        anchorPostId = "143805431",
                        pageAnchor = null,
                )
        )
        assertFalse(
                TopicUnreadFindPostReloadPolicy.shouldReloadAsFindPost(
                        requestUrl = getNewPostUrl,
                        loadAction = ThemeLoadAction.Normal,
                        scrollMode = AppPreferences.Main.TopicScrollMode.HYBRID,
                        suppressScrollRestore = true,
                        openedViaFindPost = true,
                        alreadyUpgradedThisTrace = false,
                        hasUnreadTarget = true,
                        anchorPostId = "143805431",
                        pageAnchor = null,
                )
        )
    }

    @Test
    fun buildFindPostUrlUsesResolvedAnchor() {
        assertEquals(
                "https://4pda.to/forum/index.php?showtopic=1103268&view=findpost&p=143805718",
                TopicUnreadFindPostReloadPolicy.buildFindPostUrl(1103268, "143805718")
        )
    }

    @Test
    fun skipsFindPostReloadWhenAnchorNotOnGetNewPostPage_log1103268() {
        assertFalse(
                TopicUnreadFindPostReloadPolicy.shouldReloadAsFindPost(
                        requestUrl = getNewPostUrl,
                        loadAction = ThemeLoadAction.Normal,
                        scrollMode = AppPreferences.Main.TopicScrollMode.HYBRID,
                        suppressScrollRestore = true,
                        openedViaFindPost = false,
                        alreadyUpgradedThisTrace = false,
                        hasUnreadTarget = true,
                        anchorPostId = "135617646",
                        pageAnchor = "entry135617646",
                        loadedPagePostIds = listOf(143813700, 143813742),
                )
        )
    }

    @Test
    fun reloadsWhenAnchorOnLoadedPage() {
        assertTrue(
                TopicUnreadFindPostReloadPolicy.shouldReloadAsFindPost(
                        requestUrl = getNewPostUrl,
                        loadAction = ThemeLoadAction.Normal,
                        scrollMode = AppPreferences.Main.TopicScrollMode.HYBRID,
                        suppressScrollRestore = true,
                        openedViaFindPost = false,
                        alreadyUpgradedThisTrace = false,
                        hasUnreadTarget = true,
                        anchorPostId = "143733850",
                        pageAnchor = null,
                        loadedPagePostIds = listOf(143733850, 143805431),
                )
        )
    }

    @Test
    fun reloadsAmbiguousListUnreadBottomRedirect_log752_1122662() {
        val bottomId = 143827372
        val firstUnreadOnPage = 143681911
        val postIds = listOf(firstUnreadOnPage, 143804835, 143805636, 143808406, bottomId)
        assertEquals(
                firstUnreadOnPage.toString(),
                TopicUnreadFindPostReloadPolicy.resolveAmbiguousListUnreadFindPostAnchor(
                        loadedPagePostIds = postIds,
                        redirectEntryId = bottomId,
                )
        )
        assertTrue(
                TopicUnreadFindPostReloadPolicy.shouldReloadAmbiguousListUnreadAsFindPost(
                        requestUrl = "https://4pda.to/forum/index.php?showtopic=1122662&view=getnewpost",
                        loadAction = ThemeLoadAction.Normal,
                        scrollMode = AppPreferences.Main.TopicScrollMode.HYBRID,
                        suppressScrollRestore = true,
                        openedViaFindPost = false,
                        alreadyUpgradedThisTrace = false,
                        parserListUnreadHint = true,
                        ambiguousBottomRedirect = true,
                        hasUnreadTarget = false,
                        fallbackAnchorPostId = firstUnreadOnPage.toString(),
                )
        )
    }

    @Test
    fun ambiguousReload_skipsPageTopRedirect_log1122662() {
        // Page-top redirect (redirect == first content entry on page 1): every loaded post is read,
        // the real unread is on a later page. No valid on-page anchor → no findpost reload.
        val topRedirectId = 10000001
        val postIds = listOf(topRedirectId, 10000002, 10000003)
        assertNull(
                TopicUnreadFindPostReloadPolicy.resolveAmbiguousListUnreadFindPostAnchor(
                        loadedPagePostIds = postIds,
                        redirectEntryId = topRedirectId,
                )
        )
        assertFalse(
                TopicUnreadFindPostReloadPolicy.shouldReloadAmbiguousListUnreadAsFindPost(
                        requestUrl = "https://4pda.to/forum/index.php?showtopic=1122662&view=getnewpost",
                        loadAction = ThemeLoadAction.Normal,
                        scrollMode = AppPreferences.Main.TopicScrollMode.HYBRID,
                        suppressScrollRestore = true,
                        openedViaFindPost = false,
                        alreadyUpgradedThisTrace = false,
                        parserListUnreadHint = true,
                        ambiguousBottomRedirect = true,
                        hasUnreadTarget = false,
                        fallbackAnchorPostId = null,
                )
        )
    }

    @Test
    fun ambiguousReload_skipsWhenOnlyBottomPostOnPage() {
        val bottomId = 143827370
        assertNull(
                TopicUnreadFindPostReloadPolicy.resolveAmbiguousListUnreadFindPostAnchor(
                        loadedPagePostIds = listOf(bottomId),
                        redirectEntryId = bottomId,
                )
        )
        assertFalse(
                TopicUnreadFindPostReloadPolicy.shouldReloadAmbiguousListUnreadAsFindPost(
                        requestUrl = "https://4pda.to/forum/index.php?showtopic=601691&view=getnewpost",
                        loadAction = ThemeLoadAction.Normal,
                        scrollMode = AppPreferences.Main.TopicScrollMode.HYBRID,
                        suppressScrollRestore = true,
                        openedViaFindPost = false,
                        alreadyUpgradedThisTrace = false,
                        parserListUnreadHint = true,
                        ambiguousBottomRedirect = true,
                        hasUnreadTarget = false,
                        fallbackAnchorPostId = null,
                )
        )
    }

    @Test
    fun ambiguousReload_skipsPrependedHat_log752_601691() {
        val hatId = 34_432_486
        val bottomId = 143_827_370
        val middleId = 143_814_677
        assertEquals(
                middleId.toString(),
                TopicUnreadFindPostReloadPolicy.resolveAmbiguousListUnreadFindPostAnchor(
                        loadedPagePostIds = listOf(hatId, middleId, bottomId),
                        redirectEntryId = bottomId,
                )
        )
    }

    /**
     * Log 24_06-20-07 (1103268, trace 415ab025): a GENUINELY unread favorites topic
     * (`topicMarkedUnread=true`) whose new post arrived at the bottom of the last page. The server
     * getnewpost redirected to that bottom #entry; previously the bottom-entry guard stranded the
     * user there. Now the bottom entry is the first-unread → reload findpost directly to it.
     */
    @Test
    fun reloadsGenuineListUnreadBottomRedirect_log1103268_415ab025() {
        val bottomId = 143_993_409
        val postIds = listOf(135_617_646, bottomId)
        assertEquals(
                bottomId.toString(),
                TopicUnreadFindPostReloadPolicy.resolveListUnreadBottomRedirectFindPostAnchor(
                        loadedPagePostIds = postIds,
                        redirectEntryId = bottomId,
                )
        )
        assertTrue(
                TopicUnreadFindPostReloadPolicy.shouldReloadAmbiguousListUnreadAsFindPost(
                        requestUrl = "https://4pda.to/forum/index.php?showtopic=1103268&view=getnewpost",
                        loadAction = ThemeLoadAction.Normal,
                        scrollMode = AppPreferences.Main.TopicScrollMode.HYBRID,
                        suppressScrollRestore = true,
                        openedViaFindPost = false,
                        alreadyUpgradedThisTrace = false,
                        parserListUnreadHint = true,
                        ambiguousBottomRedirect = true,
                        hasUnreadTarget = false,
                        fallbackAnchorPostId = bottomId.toString(),
                        redirectIsBottomEntry = true,
                        listTopicMarkedUnread = true,
                )
        )
    }

    /**
     * Log 24_06-20-07 (1121483): a READ favorites row opened via getnewpost only because of the
     * LAST_UNREAD setting (`topicMarkedUnread=false`) lands on the all-read bottom bookmark. This must
     * still NOT reload findpost — the bottom redirect is the last-read bookmark, not a new unread.
     */
    @Test
    fun doesNotReloadReadResumeBottomRedirect_log1121483() {
        val bottomId = 143_992_836
        assertFalse(
                TopicUnreadFindPostReloadPolicy.shouldReloadAmbiguousListUnreadAsFindPost(
                        requestUrl = "https://4pda.to/forum/index.php?showtopic=1121483&view=getnewpost",
                        loadAction = ThemeLoadAction.Normal,
                        scrollMode = AppPreferences.Main.TopicScrollMode.HYBRID,
                        suppressScrollRestore = true,
                        openedViaFindPost = false,
                        alreadyUpgradedThisTrace = false,
                        parserListUnreadHint = true,
                        ambiguousBottomRedirect = true,
                        hasUnreadTarget = false,
                        fallbackAnchorPostId = bottomId.toString(),
                        redirectIsBottomEntry = true,
                        listTopicMarkedUnread = false,
                )
        )
    }
}
