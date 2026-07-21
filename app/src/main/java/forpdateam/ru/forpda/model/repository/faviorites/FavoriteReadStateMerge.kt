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
            localReadTimeSeconds: Long = 0L,
            inspectorSnapshotPresent: Boolean = false
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

        // Универсальная защита от ПЕРЕ-ЗАЖИГАНИЯ только что прочитанной темы устаревшим серверным
        // «unread». Симптом: тема с 1 новым постом «не засчитывается прочитанной с первого раза, только
        // со второго». Причина: сразу после открытия темы сервер ещё какое-то время (CDN/лаг) отдаёт и
        // «+» в HTML списка избранного, и unread в инспекторе для поста, который мы УЖЕ прочитали. Ниже
        // защита `cached_read_over_inspector` применяется только при !htmlSaysUnread, поэтому пока висит
        // «+», полный рефреш зажигал тему обратно.
        // Держим READ строго когда: тема свежо прочитана ЛОКАЛЬНО (есть маркер), нет реально более
        // нового контента, чем в кэше (дата/автор/страницы не изменились — [hasNewerContentThanCache]),
        // и — если инспектор дал метку времени — новейший пост НЕ новее момента прочтения. Настоящий
        // новый пост меняет дату/автора ИЛИ даёт inspectorTimeStamp > localReadTime → сюда не попадёт и
        // честно зажжёт тему. Читанное в ДРУГОМ клиенте сюда тоже не попадёт (localReadPostId==0).
        if (cached != null &&
                cached.readState == FavoriteReadState.READ &&
                !cached.isNew &&
                cached.localReadPostId > 0 &&
                localReadTimeSeconds > 0L &&
                !hasNewerContentThanCache &&
                (inspectorTimeStampSeconds <= 0L || inspectorTimeStampSeconds <= localReadTimeSeconds)
        ) {
            return Result(FavoriteReadState.READ, 0, "local_read_over_stale_unread")
        }

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
                    //
                    // Device log 24_06-20-37 (FPDA_FAVORITES_UNREAD): pull-to-refresh
                    // (networkIsFreshRefresh=true) flipped genuinely-unread rows to READ. The previous
                    // `!networkIsFreshRefresh` exception treated the refresh's HTML READ as the source
                    // of truth — but we are INSIDE `if (inspectorUnread)`: the inspector itself
                    // authoritatively reports unread activity for this topic, and the favorites HTML
                    // simply lacks the +N marker (a known gap, see above). With BOTH the cache AND the
                    // inspector saying unread, a refresh that only fails to find +N is NOT authoritative
                    // evidence the user read the topic — so we MUST preserve UNREAD. Marking READ here
                    // requires authoritative evidence (inspector says read AND html says read, handled
                    // by the inspectorUnread=false branch below, or an explicit open/mark elsewhere).
                    if (cachedUnread) {
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
                    // Both fresh server signals now say READ: the favorites HTML positively reports
                    // read (no bold/+N/unread markers, so effectiveNetwork==READ) AND the inspector
                    // snapshot is present but does NOT list this topic. The inspector enumerates the
                    // unread favorites, so absence from a present snapshot is authoritative read.
                    // The cached UNREAD is therefore stale — most often because the topic was read in
                    // another client or on the site, which never fires our local markRead. Trust the
                    // server and clear it. Without this, a plain refresh could never turn the row read
                    // (preserve_cached_unread is self-perpetuating), diverging from every client that
                    // simply mirrors the server. Guarded by inspectorSnapshotPresent so that when the
                    // inspector failed to load entirely we still preserve the cached unread below.
                    if (inspectorSnapshotPresent) {
                        return Result(
                                FavoriteReadState.READ,
                                0,
                                "inspector_absent_clears_cached_unread"
                        )
                    }
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
