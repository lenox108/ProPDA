package forpdateam.ru.forpda.presentation.theme

import forpdateam.ru.forpda.model.repository.theme.TopicReturnPositionStore

/**
 * Single source of truth for "which post does the app anchor on a topic open, and how is it loaded".
 *
 * ## Why this exists
 * Historically five independent stores could each supply an anchor on open, with overlapping and
 * competing precedence (audit `docs/THEME_NAVIGATION_WEBVIEW_AUDIT_2026-06_FULL_RU.md`):
 *  - A. the server redirect (`getnewpost` / `getlastpost`) — [TopicOpenResolution];
 *  - B. the cross-tab in-progress position — [TopicReturnPositionStore];
 *  - C. the last-viewed read position (highlight only) — `ThemeReadPositionRepository`;
 *  - D. the per-tab history + `authoritativeAnchorPostId`;
 *  - E. the native back snapshot.
 *
 * The recurring bugs (wrong anchor / drifted scroll / stuck repeat-open of an unread topic) came from
 * the [ThemeViewModel.loadUrl] sequence where [TabReentryRestorePolicy.resolveRestore] could override
 * the server resolution with B, even on a fresh favorites open of a still-unread topic. Each prior
 * fix patched one cell of the {openIntent × read-state × server target} matrix.
 *
 * This resolver collapses that matrix into ONE deterministic decision. Stores A–E remain the DATA
 * SOURCES; this object owns the DECISION of which one wins. [ThemeViewModel] builds an [Input] and
 * consumes exactly one [Decision]; no other place decides anchor precedence on open.
 *
 * ## Precedence (the audited-correct table)
 * | openIntent        | readState | winner                                                        |
 * |-------------------|-----------|---------------------------------------------------------------|
 * | EXPLICIT_LINK     | any       | the explicit post (server resolution / findpost) — Normal     |
 * | FRESH_LIST        | UNREAD    | server first-unread (getnewpost) — Normal; ignore saved B     |
 * | FRESH_LIST        | READ      | server getlastpost; restore saved B only to resume read pos   |
 * | TAB_REENTRY       | any       | saved in-progress position B (genuine "where I was") — Back   |
 * | IN_TAB_BACK       | any       | per-tab history / native snapshot (handled by history) — n/a  |
 * | CROSS_TOPIC_BACK  | any       | native back snapshot (handled by history) — n/a               |
 *
 * The decisive correctness rule the audit concluded: a fresh list open of a still-UNREAD topic must
 * ALWAYS resolve to the server first-unread, never to a saved (already-read / drifted) scroll
 * position. A fresh open of a READ topic may resume the saved read position (the documented
 * multi-back tab-reentry restores a read topic to where the user left off); the read-seal keeps the
 * store empty once the topic is genuinely finished so it cannot stick.
 *
 * IN_TAB_BACK and CROSS_TOPIC_BACK do NOT flow through [ThemeViewModel.loadUrl]'s open path — they are
 * served by [ThemeHistoryController] (in-tab pop / cross-topic return guard) and native back snapshots.
 * This resolver only decides the open-from-URL case (FRESH_LIST / TAB_REENTRY / EXPLICIT_LINK); it
 * deliberately returns [Decision.UseServerTarget] for the back kinds so callers never accidentally
 * apply B on a path the history subsystem already owns.
 */
object TopicAnchorResolver {

    /** How the user arrived at this open. Mirrors the audit's openIntent dimension. */
    enum class OpenIntentKind {
        /** Fresh tap from a list (favorites / topics / tracker / news / search) — Forward nav. */
        FRESH_LIST,

        /**
         * Re-entry of a topic whose per-tab history was wiped (tab exhausted / re-created) — the
         * genuine "resume where I was" case the cross-tab [TopicReturnPositionStore] was built for.
         */
        TAB_REENTRY,

        /** In-tab BACK (history pop) — served by [ThemeHistoryController], not this URL-open path. */
        IN_TAB_BACK,

        /** Cross-topic BACK (native back snapshot) — served by history, not this URL-open path. */
        CROSS_TOPIC_BACK,

        /** Explicit findpost / act=findpost / explicit page deep link. */
        EXPLICIT_LINK,
    }

    enum class ReadState { UNREAD, READ, UNKNOWN }

    /**
     * The server target the resolver already computed for this open (data source A). [hasUnreadTarget]
     * etc. are post-load enrichments and are not required for the pre-load decision; they are accepted
     * for completeness so the resolver can be reused if the caller has them.
     */
    data class ServerTarget(
            val url: String,
            val type: TopicOpenTargetType,
            val reason: String,
    )

    data class Input(
            val topicId: Int?,
            val openIntent: OpenIntentKind,
            val readState: ReadState,
            val serverTarget: ServerTarget,
            val savedReturnPosition: TopicReturnPositionStore.Position?,
    )

    /**
     * The single resolved outcome. [UseServerTarget] keeps the resolver-produced URL and a Normal
     * load; [RestoreSavedPosition] overrides with the saved in-progress position and a Back load.
     */
    sealed class Decision {
        abstract val reason: String

        data class UseServerTarget(
                val url: String,
                override val reason: String,
        ) : Decision()

        data class RestoreSavedPosition(
                val url: String,
                val position: TopicReturnPositionStore.Position,
                override val reason: String,
        ) : Decision()
    }

    /** Server targets that re-open at a server bookmark (getlastpost / getnewpost). */
    private val SERVER_BOOKMARK_TARGET_TYPES = setOf(
            TopicOpenTargetType.READ_RESUME,
            TopicOpenTargetType.SERVER_UNREAD_FALLBACK,
            TopicOpenTargetType.SETTING_LAST_UNREAD,
    )

    private val SERVER_BOOKMARK_REOPEN_REASONS = setOf(
            "list_read_use_getlastpost",
            "list_marked_unread_use_getnewpost",
    )

    /** Server targets / reasons that mean "the server wants the first-unread post" (getnewpost). */
    private val SERVER_UNREAD_TARGET_TYPES = setOf(
            TopicOpenTargetType.SERVER_UNREAD_FALLBACK,
            TopicOpenTargetType.SETTING_LAST_UNREAD,
    )

    private val SERVER_UNREAD_REOPEN_REASONS = setOf(
            "list_marked_unread_use_getnewpost",
            "server_unread_url_from_list",
    )

    /**
     * Classify a URL-open into an [OpenIntentKind]. Deterministic mapping from the signals
     * [ThemeViewModel] already tracks. Genuine in-tab/cross-topic BACK is served by the history
     * subsystem and does not reach this open path; when [isRestoreIntent] is set we still classify it
     * IN_TAB_BACK so the resolver yields the server target (history applies the real restore).
     *
     * Note: the URL-open path never produces [OpenIntentKind.TAB_REENTRY] — a genuine exhausted-tab
     * re-open currently reaches `loadUrl` indistinguishably from a fresh list tap, so it is classified
     * FRESH_LIST (server target for unread; saved-resume for read, which matches the documented
     * multi-back behavior). TAB_REENTRY is reached only via [TabReentryRestorePolicy]'s façade
     * (`isFreshOpen=false`). If a real fresh-vs-reentry discriminator is added later, route it here.
     *
     * @param isExplicitLink the open URL targets a specific post/page (findpost / act=findpost /
     *        explicit #entry / explicit st page).
     * @param isFreshOpenIntent [TopicOpenIntentClassifier.isFreshOpenIntent] of the current intent.
     * @param isRestoreIntent [TopicOpenIntentClassifier.isRestoreIntent] (back / rotation / process).
     */
    fun classifyOpenIntent(
            isExplicitLink: Boolean,
            isFreshOpenIntent: Boolean,
            isRestoreIntent: Boolean,
    ): OpenIntentKind = when {
        isExplicitLink -> OpenIntentKind.EXPLICIT_LINK
        isRestoreIntent -> OpenIntentKind.IN_TAB_BACK
        // Both a fresh list tap and an unclassified URL-open behave like a fresh list open (server
        // target for unread; saved-resume only for read) — the safe, audited-correct default.
        isFreshOpenIntent -> OpenIntentKind.FRESH_LIST
        else -> OpenIntentKind.FRESH_LIST
    }

    fun isServerBookmarkReopen(target: ServerTarget): Boolean =
            target.type in SERVER_BOOKMARK_TARGET_TYPES ||
                    target.reason in SERVER_BOOKMARK_REOPEN_REASONS

    fun isServerUnreadReopen(target: ServerTarget): Boolean =
            target.type in SERVER_UNREAD_TARGET_TYPES ||
                    target.reason in SERVER_UNREAD_REOPEN_REASONS

    /**
     * The ONE place anchor precedence is decided for a topic URL-open. Deterministic and pure.
     */
    fun resolve(input: Input): Decision {
        val server = input.serverTarget
        val useServer = { reason: String -> Decision.UseServerTarget(server.url, reason) }

        val topicId = input.topicId
        if (topicId == null || topicId <= 0) {
            return useServer("server_target_no_topic_id")
        }

        when (input.openIntent) {
            // Explicit deep links always land on the requested post; the in-tab findpost
            // authoritative-anchor protection (fixes #1/#3) is enforced downstream in history.
            OpenIntentKind.EXPLICIT_LINK -> return useServer("explicit_link_uses_server_target")

            // History-owned navigation never restores via the cross-tab return store here.
            OpenIntentKind.IN_TAB_BACK -> return useServer("in_tab_back_handled_by_history")
            OpenIntentKind.CROSS_TOPIC_BACK -> return useServer("cross_topic_back_handled_by_native_snapshot")

            OpenIntentKind.FRESH_LIST -> {
                // FRESH_LIST + UNREAD → ALWAYS the server first-unread; never a drifted saved snapshot.
                // This is the cardinal rule that closes the recurring "stuck on an already-read post"
                // class of bugs (logs 25_06-10-52 / 25_06-14-34): the unread server target wins.
                if (isServerUnreadReopen(server) || input.readState == ReadState.UNREAD) {
                    return useServer("fresh_list_unread_uses_server_first_unread")
                }
                // FRESH_LIST + READ → server getlastpost. A fresh list/favorites tap ALWAYS honors the
                // user's global open-target setting (read → last post). It must NEVER be hijacked by a
                // saved in-progress scroll position.
                //
                // Why no saved-restore here (device logs 26_06-10-30 / 26_06-10-34, "fresh open lands
                // on random/earlier posts"): the cross-tab return store records the user's last VISIBLE
                // post when leaving a topic. On a HYBRID page the infinite-scroll top-insertion shifts
                // the viewport, so each restore→leave cycle saves an EARLIER post than the last, walking
                // the user backward through the topic on every subsequent favorites open (143876586 →
                // 143873102 → 143860995). The read-seal only engages once the user reaches the LAST
                // page, so an already-read topic opened mid-way is never sealed and the drift loops
                // forever. Genuine "resume where I was" is served exclusively by the back paths
                // (TAB_REENTRY here; in-tab history + native cross-topic back snapshots downstream), all
                // of which carry the precise click-time anchor and never flow through this fresh-open
                // branch. So a fresh forward open of a read topic always lands on the true last post.
                return useServer("fresh_list_read_uses_server_last_post")
            }

            OpenIntentKind.TAB_REENTRY -> {
                // Genuine re-entry of a topic whose per-tab history was wiped: restore the real
                // in-progress position if we have one and the server produced a bookmark re-open.
                // Even for an unread topic this is a legitimate resume (the user navigated within the
                // session), unlike a fresh list tap.
                val restore = restoreFrom(topicId, server, input.savedReturnPosition)
                return restore ?: useServer("tab_reentry_no_saved_position_uses_server_target")
            }
        }
    }

    private fun restoreFrom(
            topicId: Int,
            server: ServerTarget,
            saved: TopicReturnPositionStore.Position?,
    ): Decision.RestoreSavedPosition? {
        if (!isServerBookmarkReopen(server)) return null
        val position = saved ?: return null
        if (position.topicId != topicId) return null
        val postId = position.postId?.takeIf { it.isNotBlank() } ?: return null
        val url = ThemeBackRestoreUrlPolicy.buildRestoreUrl(
                topicId = topicId,
                st = position.pageSt,
                anchorPostId = postId,
                pageUrl = null,
        )
        return Decision.RestoreSavedPosition(
                url = url,
                position = position,
                reason = "tab_reentry_restore_saved_position",
        )
    }
}
