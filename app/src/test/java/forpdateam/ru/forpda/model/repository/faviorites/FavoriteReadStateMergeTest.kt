package forpdateam.ru.forpda.model.repository.faviorites

import forpdateam.ru.forpda.entity.remote.favorites.FavItem
import forpdateam.ru.forpda.entity.remote.favorites.FavoriteReadState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FavoriteReadStateMergeTest {

    @Test
    fun networkUnreadWinsOverCachedRead() {
        val cached = fav(readState = FavoriteReadState.READ, isNew = false)
        val result = FavoriteReadStateMerge.merge(
                network = FavoriteReadState.UNREAD,
                networkUnreadCount = 3,
                cached = cached,
                inspectorUnread = false,
                inspectorPresent = false
        )

        assertEquals(FavoriteReadState.UNREAD, result.readState)
        assertEquals(3, result.unreadPostCount)
    }

    @Test
    fun unknownWithCachedUnreadStaysUnread() {
        val cached = fav(readState = FavoriteReadState.UNREAD, isNew = true, unreadPostCount = 2)
        val result = FavoriteReadStateMerge.merge(
                network = FavoriteReadState.UNKNOWN,
                networkUnreadCount = 0,
                cached = cached,
                inspectorUnread = false,
                inspectorPresent = false
        )

        assertEquals(FavoriteReadState.UNREAD, result.readState)
        assertEquals(2, result.unreadPostCount)
        assertEquals("preserve_cached_unread", result.reason)
    }

    @Test
    fun inspectorPresentNetworkReadClearsCachedUnreadWhenInspectorRead_log1103268() {
        val cached = fav(readState = FavoriteReadState.UNREAD, isNew = true, unreadPostCount = 4)
        val result = FavoriteReadStateMerge.merge(
                network = FavoriteReadState.READ,
                networkUnreadCount = 0,
                cached = cached,
                inspectorUnread = false,
                inspectorPresent = true
        )

        assertEquals(FavoriteReadState.READ, result.readState)
        assertEquals(0, result.unreadPostCount)
        assertEquals("inspector_read", result.reason)
    }

    @Test
    fun htmlReadDoesNotBeatStaleInspectorWhenCachedUnread_log480() {
        val cached = fav(readState = FavoriteReadState.UNREAD, isNew = true, unreadPostCount = 1)
        val result = FavoriteReadStateMerge.merge(
                network = FavoriteReadState.READ,
                networkUnreadCount = 0,
                cached = cached,
                inspectorUnread = true,
                inspectorPresent = true
        )

        assertEquals(FavoriteReadState.UNREAD, result.readState)
        assertEquals(1, result.unreadPostCount)
        assertEquals("preserve_cached_unread_over_stale_html", result.reason)
    }

    @Test
    fun freshRefreshDoesNotDowngradeCachedUnreadWhenInspectorStillUnread_log240620() {
        // Device log 24_06-20-37 (FPDA_FAVORITES_UNREAD): pull-to-refresh flipped a
        // genuinely-unread favorite to READ without the user opening it. The cache said
        // UNREAD, the inspector ALSO reported unread, and the favorites HTML merely lacked
        // the +N marker. A fresh refresh must NOT treat that missing marker as authoritative
        // evidence of a read — both the cache and the inspector corroborate unread.
        val cached = fav(readState = FavoriteReadState.UNREAD, isNew = true, unreadPostCount = 3)
        val result = FavoriteReadStateMerge.merge(
                network = FavoriteReadState.READ,
                networkUnreadCount = 0,
                cached = cached,
                inspectorUnread = true,
                inspectorPresent = true,
                networkIsFreshRefresh = true
        )

        assertEquals(FavoriteReadState.UNREAD, result.readState)
        assertEquals(3, result.unreadPostCount)
        assertEquals("preserve_cached_unread_over_stale_html", result.reason)
    }

    @Test
    fun htmlReadBeatsStaleInspectorWithoutCache_log1103268() {
        val result = FavoriteReadStateMerge.merge(
                network = FavoriteReadState.READ,
                networkUnreadCount = 0,
                cached = null,
                inspectorUnread = true,
                inspectorPresent = true
        )

        assertEquals(FavoriteReadState.READ, result.readState)
        assertEquals(0, result.unreadPostCount)
        assertEquals("html_read_over_stale_inspector", result.reason)
    }

    @Test
    fun cachedReadBeatsLaggingInspectorUnreadWhenLocallyRead() {
        val cached = fav(readState = FavoriteReadState.READ, isNew = false).apply {
            localReadPostId = 999
        }
        val result = FavoriteReadStateMerge.merge(
                network = FavoriteReadState.READ,
                networkUnreadCount = 0,
                cached = cached,
                inspectorUnread = true,
                inspectorPresent = true
        )

        assertEquals(FavoriteReadState.READ, result.readState)
        assertEquals(0, result.unreadPostCount)
        assertEquals("cached_read_over_inspector", result.reason)
    }

    @Test
    fun localReadFromThemeLoadedBeatsLaggingInspectorUnread() {
        val cached = fav(readState = FavoriteReadState.READ, isNew = false).apply {
            localReadPostId = topicId
            localReadPostDateMillis = 1_781_209_459_878L
        }
        val result = FavoriteReadStateMerge.merge(
                network = FavoriteReadState.READ,
                networkUnreadCount = 0,
                cached = cached,
                inspectorUnread = true,
                inspectorPresent = true
        )

        assertEquals(FavoriteReadState.READ, result.readState)
        assertEquals(0, result.unreadPostCount)
        assertEquals("cached_read_over_inspector", result.reason)
    }

    @Test
    fun inspectorUnreadWithoutNewerContentDoesNotFlipCachedRead() {
        val cached = fav(readState = FavoriteReadState.READ, isNew = false)
        val result = FavoriteReadStateMerge.merge(
                network = FavoriteReadState.READ,
                networkUnreadCount = 0,
                cached = cached,
                inspectorUnread = true,
                inspectorPresent = true,
                hasNewerContentThanCache = false
        )

        assertEquals(FavoriteReadState.READ, result.readState)
        assertEquals(0, result.unreadPostCount)
        assertEquals("cached_read_over_stale_inspector", result.reason)
    }

    @Test
    fun inspectorUnreadWithNewerContentFlipsCachedReadToUnread() {
        val cached = fav(readState = FavoriteReadState.READ, isNew = false)
        val result = FavoriteReadStateMerge.merge(
                network = FavoriteReadState.READ,
                networkUnreadCount = 0,
                cached = cached,
                inspectorUnread = true,
                inspectorPresent = true,
                hasNewerContentThanCache = true
        )

        assertEquals(FavoriteReadState.UNREAD, result.readState)
        assertEquals(1, result.unreadPostCount)
        assertEquals("inspector_unread_over_stale_html", result.reason)
    }

    @Test
    fun inspectorUnreadStillUpgradesHtmlUnreadRow() {
        val result = FavoriteReadStateMerge.merge(
                network = FavoriteReadState.UNREAD,
                networkUnreadCount = 2,
                cached = null,
                inspectorUnread = true,
                inspectorPresent = true
        )

        assertEquals(FavoriteReadState.UNREAD, result.readState)
        assertEquals(2, result.unreadPostCount)
        assertEquals("inspector_unread", result.reason)
    }

    @Test
    fun confidentReadWithoutCacheStaysRead() {
        val result = FavoriteReadStateMerge.merge(
                network = FavoriteReadState.READ,
                networkUnreadCount = 0,
                cached = null,
                inspectorUnread = false,
                inspectorPresent = true
        )

        assertEquals(FavoriteReadState.READ, result.readState)
        assertEquals(0, result.unreadPostCount)
    }

    @Test
    fun htmlUnreadWinsWhenInspectorRowAbsent() {
        val result = FavoriteReadStateMerge.merge(
                network = FavoriteReadState.UNREAD,
                networkUnreadCount = 1,
                cached = null,
                inspectorUnread = false,
                inspectorPresent = false
        )

        assertEquals(FavoriteReadState.UNREAD, result.readState)
        assertEquals("network_unread", result.reason)
    }

    @Test
    fun inspectorPresentHtmlUnreadWithoutInspectorHintStaysUnread() {
        val result = FavoriteReadStateMerge.merge(
                network = FavoriteReadState.UNREAD,
                networkUnreadCount = 1,
                cached = null,
                inspectorUnread = false,
                inspectorPresent = true,
        )
        assertEquals(FavoriteReadState.UNREAD, result.readState)
        assertEquals("network_unread_over_inspector", result.reason)
    }

    @Test
    fun networkReadPreservesCachedUnreadWhenInspectorAbsent() {
        val cached = fav(readState = FavoriteReadState.UNREAD, isNew = true, unreadPostCount = 1)
        val result = FavoriteReadStateMerge.merge(
                network = FavoriteReadState.READ,
                networkUnreadCount = 0,
                cached = cached,
                inspectorUnread = false,
                inspectorPresent = false,
        )

        assertEquals(FavoriteReadState.UNREAD, result.readState)
        assertEquals(1, result.unreadPostCount)
        assertEquals("preserve_cached_unread", result.reason)
    }

    @Test
    fun inspectorFreshActivityFlipsCachedLocalReadToUnread_log1103268() {
        // Log 23_06 / topic 1103268: the user opened the topic earlier (localReadPostId > 0,
        // localReadPostDateMillis set), cached state is READ, HTML says READ, but the
        // inspector reports fresh unread activity whose timeStamp is newer than the moment
        // the user marked the topic read. The cached read marker is now stale and must
        // NOT be allowed to mask the inspector's fresh unread signal.
        val cached = fav(readState = FavoriteReadState.READ, isNew = false).apply {
            localReadPostId = 1103268
            localReadPostDateMillis = 1_782_231_718_000L
        }
        val result = FavoriteReadStateMerge.merge(
                network = FavoriteReadState.READ,
                networkUnreadCount = 0,
                cached = cached,
                inspectorUnread = true,
                inspectorPresent = true,
                hasNewerContentThanCache = false,
                inspectorTimeStampSeconds = 1_782_232_111L,
                localReadTimeSeconds = cached.localReadPostDateMillis / 1000L,
        )

        assertEquals(FavoriteReadState.UNREAD, result.readState)
        assertEquals(1, result.unreadPostCount)
        assertEquals("inspector_unread_fresh_over_local_read", result.reason)
    }

    @Test
    fun inspectorActivityOlderThanLocalReadKeepsCachedRead() {
        // Regression for the opposite of 1103268: the user just opened the topic (local read
        // moment is *newer* than the inspector's last activity), so the cached READ is
        // genuinely fresh and must NOT be flipped to UNREAD by a lagging inspector.
        val cached = fav(readState = FavoriteReadState.READ, isNew = false).apply {
            localReadPostId = 1103268
            localReadPostDateMillis = 1_782_232_500_000L
        }
        val result = FavoriteReadStateMerge.merge(
                network = FavoriteReadState.READ,
                networkUnreadCount = 0,
                cached = cached,
                inspectorUnread = true,
                inspectorPresent = true,
                hasNewerContentThanCache = false,
                inspectorTimeStampSeconds = 1_782_232_111L,
                localReadTimeSeconds = cached.localReadPostDateMillis / 1000L,
        )

        assertEquals(FavoriteReadState.READ, result.readState)
        assertEquals(0, result.unreadPostCount)
        assertEquals("cached_read_over_inspector", result.reason)
    }

    @Test
    fun inspectorFreshActivityFlipsCachedLocalReadToUnread_2_8() {
        // Same scenario as log 1103268 above, but with bare-bones numbers:
        // cached localReadPostId=100, localReadPostDateMillis=1_000_000 (≈ 1000 sec since epoch).
        // Inspector timeStamp=2000, lastTimeStamp=500. Inspector's last post is at 500 (read),
        // inspector's last post is at 2000 (newer) — i.e. inspector reports fresh unread activity
        // whose timeStamp is newer than the moment the user marked the topic read.
        val cached = fav(readState = FavoriteReadState.READ, isNew = false).apply {
            localReadPostId = 100
            localReadPostDateMillis = 1_000_000L
        }
        val result = FavoriteReadStateMerge.merge(
                network = FavoriteReadState.READ,
                networkUnreadCount = 0,
                cached = cached,
                inspectorUnread = true,
                inspectorPresent = true,
                hasNewerContentThanCache = false,
                inspectorTimeStampSeconds = 2000L,
                localReadTimeSeconds = cached.localReadPostDateMillis / 1000L,
        )

        assertEquals(FavoriteReadState.UNREAD, result.readState)
        assertEquals(1, result.unreadPostCount)
        assertEquals("inspector_unread_fresh_over_local_read", result.reason)
    }

    @Test
    fun unknownWithoutHintsDefaultsToRead() {
        val result = FavoriteReadStateMerge.merge(
                network = FavoriteReadState.UNKNOWN,
                networkUnreadCount = 0,
                cached = null,
                inspectorUnread = false,
                inspectorPresent = false
        )

        assertEquals(FavoriteReadState.READ, result.readState)
        assertEquals("unknown_default_read", result.reason)
    }

    private fun fav(
            readState: FavoriteReadState,
            isNew: Boolean,
            unreadPostCount: Int = 0
    ) = FavItem().apply {
        favId = 1
        topicId = 42
        this.readState = readState
        this.isNew = isNew
        this.unreadPostCount = unreadPostCount
    }
}
