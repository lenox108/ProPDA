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
