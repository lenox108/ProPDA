package forpdateam.ru.forpda.model.repository.faviorites

import forpdateam.ru.forpda.entity.remote.favorites.FavItem
import forpdateam.ru.forpda.entity.remote.favorites.FavoriteReadState

internal object FavoriteReadStateMerge {

    data class Result(
            val readState: FavoriteReadState,
            val unreadPostCount: Int,
            val reason: String?
    )

    /**
     * Merge network/parser state with cache and optional inspector hint.
     *
     * When [inspectorPresent] (inspector returned a row for this topic), inspector is the
     * authority for read/unread. HTML markers (+N, modifier unread, bold title) supply [networkUnreadCount].
     * Topics missing from the inspector snapshot fall back to HTML/cache (see [FavoritesRepository]).
     */
    fun merge(
            network: FavoriteReadState,
            networkUnreadCount: Int,
            networkLegacyIsNew: Boolean = false,
            cached: FavItem?,
            inspectorUnread: Boolean,
            inspectorPresent: Boolean,
            hasNewerContentThanCache: Boolean = false,
            networkIsFreshRefresh: Boolean = false,
            inspectorTimeStampSeconds: Long = 0L,
            localReadTimeSeconds: Long = 0L
    ): Result {
        val htmlPlusCount = when {
            networkUnreadCount > 0 -> networkUnreadCount
            network == FavoriteReadState.UNREAD || networkLegacyIsNew -> 1
            else -> 0
        }
        val cachedUnread = cached?.let {
            it.readState == FavoriteReadState.UNREAD || it.isNew || it.unreadPostCount > 0
        } == true
        val cachedCount = cached?.unreadPostCount ?: 0

        if (inspectorPresent) {
            if (inspectorUnread) {
                val htmlSaysUnread = network == FavoriteReadState.UNREAD ||
                        htmlPlusCount > 0 ||
                        networkLegacyIsNew
                if (cached != null &&
                        cached.readState == FavoriteReadState.READ &&
                        !cached.isNew &&
                        cached.unreadPostCount == 0 &&
                        !htmlSaysUnread &&
                        !hasNewerContentThanCache
                ) {
                    // Log 23_06: 1103268 had cached READ (user opened the topic earlier) but the
                    // inspector reported fresh unread activity (timeStamp > local read moment).
                    // The local read marker is now stale; trust the inspector and flip to UNREAD.
                    if (cached.localReadPostId > 0 &&
                            inspectorTimeStampSeconds > 0L &&
                            localReadTimeSeconds > 0L &&
                            inspectorTimeStampSeconds > localReadTimeSeconds
                    ) {
                        return Result(
                                FavoriteReadState.UNREAD,
                                1,
                                "inspector_unread_fresh_over_local_read"
                        )
                    }
                    val reason = if (cached.localReadPostId > 0) {
                        "cached_read_over_inspector"
                    } else {
                        "cached_read_over_stale_inspector"
                    }
                    return Result(FavoriteReadState.READ, 0, reason)
                }
                if (!htmlSaysUnread && network == FavoriteReadState.READ && !hasNewerContentThanCache) {
                    // Log 480/1103268: favorites HTML often lacks +N while inspector still reports unread.
                    // Do not flip a cached-unread row to READ before the user opens the topic.
                    // On a fresh network refresh, however, the network's READ is the source of
                    // truth and must replace the cached UNREAD; the inspector hint is treated
                    // as stale in that case.
                    if (cachedUnread && !networkIsFreshRefresh) {
                        val count = maxOf(htmlPlusCount, cachedCount).coerceAtLeast(1)
                        return Result(
                                FavoriteReadState.UNREAD,
                                count,
                                "preserve_cached_unread_over_stale_html"
                        )
                    }
                    return Result(FavoriteReadState.READ, 0, "html_read_over_stale_inspector")
                }
                val count = maxOf(htmlPlusCount, cachedCount).coerceAtLeast(1)
                val reason = if (htmlSaysUnread) "inspector_unread" else "inspector_unread_over_stale_html"
                return Result(FavoriteReadState.UNREAD, count, reason)
            }
            // Inspector can lag, but a fresh network READ means the old local unread marker was consumed.
            // Keep unread only when the current HTML/parser still reports unread.
            if (htmlPlusCount > 0) {
                return Result(
                        FavoriteReadState.UNREAD,
                        htmlPlusCount.coerceAtLeast(1),
                        "network_unread_over_inspector"
                )
            }
            return Result(FavoriteReadState.READ, 0, "inspector_read")
        }

        val effectiveNetwork = when {
            network == FavoriteReadState.UNREAD -> FavoriteReadState.UNREAD
            network == FavoriteReadState.READ -> FavoriteReadState.READ
            networkLegacyIsNew -> FavoriteReadState.UNREAD
            else -> FavoriteReadState.UNKNOWN
        }

        when (effectiveNetwork) {
            FavoriteReadState.UNREAD -> {
                val count = when {
                    networkUnreadCount > 0 -> networkUnreadCount
                    inspectorUnread -> cachedCount.coerceAtLeast(1)
                    else -> 1
                }
                return Result(FavoriteReadState.UNREAD, count, "network_unread")
            }
            FavoriteReadState.UNKNOWN -> {
                if (inspectorUnread) {
                    return Result(
                            FavoriteReadState.UNREAD,
                            networkUnreadCount.coerceAtLeast(cachedCount).coerceAtLeast(1),
                            "inspector_unread"
                    )
                }
                if (cachedUnread) {
                    return Result(
                            FavoriteReadState.UNREAD,
                            maxOf(networkUnreadCount, cachedCount).coerceAtLeast(1),
                            "preserve_cached_unread"
                    )
                }
                return Result(FavoriteReadState.READ, 0, "unknown_default_read")
            }
            FavoriteReadState.READ -> {
                if (cachedUnread) {
                    return Result(
                            FavoriteReadState.UNREAD,
                            maxOf(networkUnreadCount, cachedCount).coerceAtLeast(1),
                            "preserve_cached_unread"
                    )
                }
                if (inspectorUnread && (htmlPlusCount > 0 || networkLegacyIsNew)) {
                    return Result(
                            FavoriteReadState.UNREAD,
                            htmlPlusCount.coerceAtLeast(1),
                            "inspector_unread_over_read"
                    )
                }
                return Result(FavoriteReadState.READ, 0, "network_read")
            }
        }
    }

    fun applyTo(item: FavItem, result: Result) {
        item.readState = result.readState
        item.isNew = result.readState == FavoriteReadState.UNREAD
        item.unreadPostCount = if (item.isNew) result.unreadPostCount.coerceAtLeast(1) else 0
    }
}
