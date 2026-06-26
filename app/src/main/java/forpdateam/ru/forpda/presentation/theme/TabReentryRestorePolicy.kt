package forpdateam.ru.forpda.presentation.theme

import forpdateam.ru.forpda.model.repository.theme.TopicReturnPositionStore

/**
 * Decides whether a topic open that the resolver classified as a "fresh re-open" (server last-read /
 * first-unread bookmark) must instead RESTORE the position the user actually left this topic at in
 * the current session.
 *
 * Context (log 24_06-23-12-50, multi-back anchor loss): each Theme tab owns its own
 * [ThemeHistoryController]; once a tab's in-tab history is exhausted the next BACK is consumed by the
 * `TabNavigator`, which re-shows a previously-visited Theme tab. That tab's per-tab history/position
 * was wiped by `resetTransientStateForNewTopic -> clear()`, so the re-entry falls through to a FRESH
 * open via the `list_read_use_getlastpost` (READ_RESUME) resolver — landing on the server last-read
 * bookmark (e.g. st=1260#entry143992836, page 64) instead of the post the user was on
 * (st=1180#entry143876380, page 60). The "thrown to a random post" symptom.
 *
 * [TopicReturnPositionStore] remembers the real viewed position per topic ACROSS tabs. This policy
 * consults it: when the resolver produced a fresh getlastpost/getnewpost open for a topic that has a
 * saved session position, we override the open with a clean `showtopic+st(+#entry)` back-restore URL.
 *
 * Genuine first opens are PRESERVED: with no saved position the resolver keeps its
 * LAST_UNREAD/getlastpost behavior (returns null -> no override).
 *
 * UNREAD precedence (log 25_06-14-34-48, topic 1103268 "stuck on already-read 143998164"): a topic
 * that the favorites list still reports as UNREAD must, on a FRESH list open, resolve to the server
 * first-unread post (`getnewpost`) — NOT to a saved in-progress scroll position. The user scrolled UP
 * to read mid-page-1317, leaving a return snapshot at an already-read post; without this guard that
 * snapshot hijacked every subsequent fresh favorites open and stuck there (the topic never became
 * read, so the mark-read seal never engaged). The saved-position restore is for resuming a READ
 * topic where you left off; a fresh open of a still-unread topic always wins with the server unread
 * target. Genuine in-tab BACK / cross-topic restore use the per-tab history + native back snapshots,
 * not this path, so they are unaffected.
 */
object TabReentryRestorePolicy {

    /**
     * True when [resolution] is a fresh server-bookmark (re)open for [topicId] — i.e. the kind of
     * open that should defer to a saved session position. Explicit post/page opens, pagination,
     * refresh, and back navigation are excluded (different reasons / target types).
     */
    private fun TopicOpenResolution.asServerTarget() = TopicAnchorResolver.ServerTarget(
            url = url,
            type = targetType,
            reason = reason,
    )

    fun isServerBookmarkReopen(resolution: TopicOpenResolution): Boolean =
            TopicAnchorResolver.isServerBookmarkReopen(resolution.asServerTarget())

    /** True when the resolver wants the server first-unread post (`getnewpost`) for this open. */
    fun isServerUnreadReopen(resolution: TopicOpenResolution): Boolean =
            TopicAnchorResolver.isServerUnreadReopen(resolution.asServerTarget())

    data class Restore(val url: String, val position: TopicReturnPositionStore.Position)

    /**
     * Returns a back-restore override for [topicId] when:
     *  - the resolver produced a server-bookmark re-open (so this is NOT an explicit/pagination open),
     *  - the open is NOT a fresh open of a still-unread topic (server first-unread takes precedence),
     *  - a saved session position exists for the topic with a usable anchor post id.
     *
     * Otherwise returns null and the original resolution stands (preserves genuine first open and
     * fresh unread opens).
     *
     * @param isFreshOpen whether this open is a fresh list/favorites open (isFreshOpen=true /
     * fresh_favorites). A fresh open of a still-unread topic must always honor the server first-unread
     * resolution and is never overridden by a saved in-progress position. Genuine back/tab-reentry
     * restore does not pass through this path, so READ-topic resume is unaffected.
     */
    fun resolveRestore(
            topicId: Int?,
            resolution: TopicOpenResolution,
            saved: TopicReturnPositionStore.Position?,
            isFreshOpen: Boolean = false,
    ): Restore? {
        // Delegate to the single source of truth so precedence lives in exactly one place
        // ([TopicAnchorResolver]). This object is retained as a thin façade for its existing
        // decision-mirroring tests; production opens route through the resolver directly in
        // [ThemeViewModel.loadUrl].
        val decision = TopicAnchorResolver.resolve(
                TopicAnchorResolver.Input(
                        topicId = topicId,
                        openIntent = if (isFreshOpen) {
                            TopicAnchorResolver.OpenIntentKind.FRESH_LIST
                        } else {
                            TopicAnchorResolver.OpenIntentKind.TAB_REENTRY
                        },
                        readState = TopicAnchorResolver.ReadState.UNKNOWN,
                        serverTarget = TopicAnchorResolver.ServerTarget(
                                url = resolution.url,
                                type = resolution.targetType,
                                reason = resolution.reason,
                        ),
                        savedReturnPosition = saved,
                )
        )
        return when (decision) {
            is TopicAnchorResolver.Decision.RestoreSavedPosition ->
                Restore(url = decision.url, position = decision.position)
            is TopicAnchorResolver.Decision.UseServerTarget -> null
        }
    }
}
