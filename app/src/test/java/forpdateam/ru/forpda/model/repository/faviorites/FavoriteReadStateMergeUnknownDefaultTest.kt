package forpdateam.ru.forpda.model.repository.faviorites

import forpdateam.ru.forpda.entity.remote.favorites.FavItem
import forpdateam.ru.forpda.entity.remote.favorites.FavoriteReadState
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Fix #6: when the network returns FavoriteReadState.UNKNOWN and the inspector snapshot is
 * empty, [FavoriteReadStateMerge] used to fall back to the [FavoriteReadState.READ] default
 * (`"unknown_default_read"`) even if the cached row still carried a non-zero
 * [FavItem.unreadPostCount]. That lost information: e.g. topic 1103268 had `unreadPostCount = 1`
 * cached after the user pulled-to-refresh while the network had no marker and the inspector was
 * empty — the unread badge disappeared.
 *
 * The fix is to treat a cached row with a non-zero `unreadPostCount` as "unread" for the purposes
 * of the `cachedUnread` shortcut in the UNKNOWN branch, so the merge returns
 * `("preserve_cached_unread", ...)` and the badge survives.
 *
 * The test cases below are written to:
 *  - FAIL on the pre-fix code (which used only `readState == UNREAD || isNew` for `cachedUnread`).
 *  - PASS on the post-fix code (which also considers `unreadPostCount > 0`).
 */
class FavoriteReadStateMergeUnknownDefaultTest {

    @Test
    fun unknownNetworkWithCachedUnreadCountKeepsBadge_log1103268() {
        // cached is readState=READ, isNew=false, but unreadPostCount=1 (legacy hint).
        // network=UNKNOWN, inspector empty.
        // Post-fix: cachedUnread is true (because unreadPostCount > 0) → preserve_cached_unread.
        // Pre-fix: cachedUnread is false → unknown_default_read (loses the badge).
        val cached = fav(readState = FavoriteReadState.READ, isNew = false, unreadPostCount = 1)
        val result = FavoriteReadStateMerge.merge(
                network = FavoriteReadState.UNKNOWN,
                networkUnreadCount = 0,
                cached = cached,
                inspectorUnread = false,
                inspectorPresent = false
        )

        assertEquals(FavoriteReadState.UNREAD, result.readState)
        assertEquals(1, result.unreadPostCount)
        assertEquals("preserve_cached_unread", result.reason)
    }

    @Test
    fun unknownNetworkWithCleanCachedReadStaysRead() {
        // cached is readState=READ, isNew=false, unreadPostCount=0 — truly no hint of unread.
        // network=UNKNOWN, inspector empty.
        // Both pre- and post-fix: cachedUnread is false → unknown_default_read.
        val cached = fav(readState = FavoriteReadState.READ, isNew = false, unreadPostCount = 0)
        val result = FavoriteReadStateMerge.merge(
                network = FavoriteReadState.UNKNOWN,
                networkUnreadCount = 0,
                cached = cached,
                inspectorUnread = false,
                inspectorPresent = false
        )

        assertEquals(FavoriteReadState.READ, result.readState)
        assertEquals(0, result.unreadPostCount)
        assertEquals("unknown_default_read", result.reason)
    }

    @Test
    fun unknownNetworkWithCachedUnreadFlagStaysUnread() {
        // cached is readState=UNREAD, isNew=true, unreadPostCount=5.
        // network=UNKNOWN, inspector empty.
        // Both pre- and post-fix: cachedUnread is true → preserve_cached_unread.
        val cached = fav(readState = FavoriteReadState.UNREAD, isNew = true, unreadPostCount = 5)
        val result = FavoriteReadStateMerge.merge(
                network = FavoriteReadState.UNKNOWN,
                networkUnreadCount = 0,
                cached = cached,
                inspectorUnread = false,
                inspectorPresent = false
        )

        assertEquals(FavoriteReadState.UNREAD, result.readState)
        assertEquals(5, result.unreadPostCount)
        assertEquals("preserve_cached_unread", result.reason)
    }

    @Test
    fun unknownNetworkWithLegacyIsNewAndZeroCountStaysUnread() {
        // cached is readState=READ, isNew=true, unreadPostCount=0 — legacy flag says unread.
        // network=UNKNOWN, inspector empty.
        // Both pre- and post-fix: cachedUnread is true (because isNew) → preserve_cached_unread.
        // The unread count must be at least 1 — the merge coerces it to 1.
        val cached = fav(readState = FavoriteReadState.READ, isNew = true, unreadPostCount = 0)
        val result = FavoriteReadStateMerge.merge(
                network = FavoriteReadState.UNKNOWN,
                networkUnreadCount = 0,
                cached = cached,
                inspectorUnread = false,
                inspectorPresent = false
        )

        assertEquals(FavoriteReadState.UNREAD, result.readState)
        assertEquals(1, result.unreadPostCount)
        assertEquals("preserve_cached_unread", result.reason)
    }

    @Test
    fun unknownNetworkWithoutCacheDefaultsToRead() {
        // No cached row at all → fall through to unknown_default_read.
        // Both pre- and post-fix behavior.
        val result = FavoriteReadStateMerge.merge(
                network = FavoriteReadState.UNKNOWN,
                networkUnreadCount = 0,
                cached = null,
                inspectorUnread = false,
                inspectorPresent = false
        )

        assertEquals(FavoriteReadState.READ, result.readState)
        assertEquals(0, result.unreadPostCount)
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
