package forpdateam.ru.forpda.model.repository.faviorites

import forpdateam.ru.forpda.entity.remote.favorites.FavItem
import forpdateam.ru.forpda.entity.remote.favorites.FavoriteReadState
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Fix #5: `FavoritesRepository.markRead` must NOT eagerly reset
 * [FavItem.inspectorMarkedUnread] to false on a per-row mark-read. The merge pipeline
 * (`mergeNetworkFavoriteReadStates`) is the single source of truth for that flag and
 * re-derives it from the inspector snapshot on every refresh. If a local mark-read wiped
 * the cached flag before the merge ran, the next refresh would not be able to see that the
 * user had previously marked the topic unread via the inspector and the badge could
 * resurrect incorrectly.
 *
 * This test class focuses on the merge side of the contract:
 *  - Given a cached row with `inspectorMarkedUnread=true`, the merge must produce a Result
 *    whose reason correctly reflects inspector presence, regardless of the cached value of
 *    `inspectorMarkedUnread`. The flag itself is recomputed by the caller (the repository's
 *    `mergeNetworkFavoriteReadStates`) before invoking `merge`, not by `merge` itself.
 *  - The companion test [FavoritesRepositoryMarkReadTest] covers the markRead side of the
 *    fix: the flag must survive markRead intact.
 */
class FavoriteReadStateMergeInspectorMarkedUnreadTest {

    @Test
    fun inspectorUnreadTrueWithInspectorRowRecomputesFlag() {
        // After the fix, the caller of merge() (mergeNetworkFavoriteReadStates) sets
        // item.inspectorMarkedUnread = inspectorUnread for every row, regardless of the
        // cached value. The merge itself only sees the boolean via the inspectorUnread
        // parameter, not via the cached FavItem. So passing inspectorUnread=true must
        // route through the inspector branch, not the cached-read shortcut.
        val cached = fav(readState = FavoriteReadState.READ, isNew = false, unreadPostCount = 0)
                .apply { inspectorMarkedUnread = true }
        val result = FavoriteReadStateMerge.merge(
                network = FavoriteReadState.READ,
                networkUnreadCount = 0,
                cached = cached,
                inspectorUnread = true,
                inspectorPresent = true,
                hasNewerContentThanCache = false
        )

        // The cached-read-over-stale-inspector branch wins (no localReadPostId, no fresh
        // inspector activity newer than the local read moment).
        assertEquals(FavoriteReadState.READ, result.readState)
        assertEquals(0, result.unreadPostCount)
        assertEquals("cached_read_over_stale_inspector", result.reason)
    }

    @Test
    fun inspectorUnreadFalseWithInspectorRowClearsCachedFlag() {
        // When the inspector says "read" for this topic, the caller resets
        // inspectorMarkedUnread=false on the item, and the merge returns "inspector_read".
        // The cached value of inspectorMarkedUnread does not affect the merge outcome —
        // it is the caller's job to re-derive it.
        val cached = fav(readState = FavoriteReadState.READ, isNew = false, unreadPostCount = 0)
                .apply { inspectorMarkedUnread = true }
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
    fun inspectorUnreadFalseWithoutInspectorDoesNotResurrect() {
        // The cached `inspectorMarkedUnread=true` must NOT survive a network refresh where
        // the inspector snapshot is missing/empty. The merge here goes through the READ
        // branch and preserves the cached unread (none, in this case) but does not flip
        // the row back to UNREAD on the cached flag alone.
        val cached = fav(readState = FavoriteReadState.READ, isNew = false, unreadPostCount = 0)
                .apply { inspectorMarkedUnread = true }
        val result = FavoriteReadStateMerge.merge(
                network = FavoriteReadState.READ,
                networkUnreadCount = 0,
                cached = cached,
                inspectorUnread = false,
                inspectorPresent = false
        )

        assertEquals(FavoriteReadState.READ, result.readState)
        assertEquals(0, result.unreadPostCount)
        assertEquals("network_read", result.reason)
    }

    @Test
    fun cachedInspectorMarkedUnreadIgnoredWhenNetworkIsUnread() {
        // Network says UNREAD with count=2, inspector empty.
        // Regardless of cached.inspectorMarkedUnread, the merge returns "network_unread".
        val cached = fav(readState = FavoriteReadState.READ, isNew = false, unreadPostCount = 0)
                .apply { inspectorMarkedUnread = true }
        val result = FavoriteReadStateMerge.merge(
                network = FavoriteReadState.UNREAD,
                networkUnreadCount = 2,
                cached = cached,
                inspectorUnread = false,
                inspectorPresent = false
        )

        assertEquals(FavoriteReadState.UNREAD, result.readState)
        assertEquals(2, result.unreadPostCount)
        assertEquals("network_unread", result.reason)
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
