package forpdateam.ru.forpda.presentation.theme

import android.net.Uri
import forpdateam.ru.forpda.common.Preferences as AppPreferences
import forpdateam.ru.forpda.common.TopicOpenListHints
import forpdateam.ru.forpda.common.topicOpenListHintsFromListing
import forpdateam.ru.forpda.common.topicOpenListReadResumeFromListing
import forpdateam.ru.forpda.entity.remote.favorites.FavItem
import forpdateam.ru.forpda.entity.remote.favorites.FavoriteReadState
import forpdateam.ru.forpda.model.data.remote.api.theme.ThemeApi

/**
 * Single policy for favorites/topics read-unread open under [AppPreferences.Main.TopicOpenTarget.LAST_UNREAD].
 *
 * ## Navigation (list row → fetch URL)
 * | Row state | URL |
 * |-----------|-----|
 * | Unread (+N, bold, [readState=UNREAD]) | `view=getnewpost` (strip list `st`/`p`) |
 * | Read (list row) | `view=getlastpost` — resume at the server-side read bookmark |
 * | Non-list / setting default | plain topic or `getnewpost` per setting |
 *
 * ## Parser hint ([parserTrustsGetNewPostUnread])
 * True when `view=getnewpost` should resolve the **first unread**, not last-read resume:
 * list-unread row **or** user setting [AppPreferences.Main.TopicOpenTarget.LAST_UNREAD].
 * Drives anchor heuristics only — not navigation URL selection.
 *
 * ## getnewpost anchor (all opens)
 * 1. HTML `unread` class markers — authoritative first unread
 * 2. Server redirect `#entry` — trusted only when it is not an all-read bottom bookmark
 * 3. `highlight=` / `p=` / canonical (with top/bottom rejection)
 * 4. First non-hat entry on page (list-unread or after bottom reject)
 *
 * ## Bottom `#entry` without HTML unread
 * On plain/resume opens a bottom redirect can be the **last-read bookmark**.
 * For `LAST_UNREAD`/list-unread opens this redirect is ambiguous: keep diagnostics, but do not
 * expose it as an unread scroll target because it skips older unread posts when server state lagged.
 *
 * ## getnewpost anchor (read list row, no list hint)
 * On last page with bottom redirect and no HTML unread → last-read resume (`hasUnreadTarget=false`).
 */
object TopicUnreadOpenPolicy {

    /** Minimal open-session classification for preload, metadata, and mark-read policy. */
    enum class TopicOpenSessionKind {
        FIRST_UNREAD,
        READ_RESUME,
        AMBIGUOUS_ALL_READ,
        EXPLICIT_POST,
    }

    enum class NavigationMode {
        /** List unread or LAST_UNREAD default — open at first unread. */
        GET_NEW_POST,
        /** List read row — resume at server last-read post. */
        GET_LAST_POST,
        /** Explicit first page (FIRST_PAGE setting on non-list open). */
        PLAIN_FIRST_PAGE,
    }

    data class AnchorResolution(
            val anchorEntry: String?,
            val hasUnreadTarget: Boolean,
            val reason: String,
            val bottomHashRejected: Boolean = false,
            val ambiguousBottomRedirect: Boolean = false,
    )

    /** Structured fields for [ThemePostReadStateDiagnostics.parserAnchorResolved] extra map. */
    data class AnchorDiagnostics(
            val firstEntryId: Int?,
            val lastEntryId: Int?,
            val redirectIsBottomEntry: Boolean,
            val contentEntryCount: Int,
    )

    fun buildAnchorDiagnostics(
            entryIds: List<Int>,
            redirectHashId: Int?,
            hatSkip: Int?,
    ): AnchorDiagnostics {
        val contentEntries = if (hatSkip != null) entryIds.filter { it != hatSkip } else entryIds
        val lastEntryId = entryIds.lastOrNull()
        return AnchorDiagnostics(
                firstEntryId = entryIds.firstOrNull(),
                lastEntryId = lastEntryId,
                redirectIsBottomEntry = redirectHashId != null && redirectHashId == lastEntryId,
                contentEntryCount = contentEntries.size,
        )
    }

    data class GetNewPostAnchorContext(
            val html: String,
            val finalUrl: String,
            val entryIds: List<Int>,
            val redirectHashId: Int?,
            val hatEntryIdToSkip: Int?,
            val onLastTopicPage: Boolean,
            /** [parserTrustsListUnread] for this fetch. */
            val listUnreadHint: Boolean,
    )

    /** Row renders as unread for navigation (not legacy [FavItem.isNew] alone). */
    fun FavItem.isListRowUnread(): Boolean =
            readState == FavoriteReadState.UNREAD || isNew || unreadPostCount > 0

    fun buildListHints(
            topicId: Int,
            listingHref: String?,
            topicMarkedUnread: Boolean,
            isRelocated: Boolean = false,
    ): TopicOpenListHints {
        if (topicId <= 0) return TopicOpenListHints()
        if (topicMarkedUnread) {
            if (!listingHref.isNullOrBlank()) {
                return topicOpenListHintsFromListing(
                        listingHref = listingHref,
                        topicId = topicId,
                        topicMarkedUnread = true,
                        isRelocated = isRelocated,
                )
            }
            return topicOpenListHintsFromListing(
                    listingHref = syntheticUnreadUrl(topicId),
                    topicId = topicId,
                    topicMarkedUnread = true,
                    isRelocated = isRelocated,
            )
        }
        return TopicOpenListHints(
                lastReadUrlFromList = topicOpenListReadResumeFromListing(listingHref, topicId)
        )
    }

    fun syntheticUnreadUrl(topicId: Int): String =
            "https://4pda.to/forum/index.php?showtopic=$topicId&view=getnewpost"

    /**
     * Whether [ThemeParser] should trust list-unread semantics for getnewpost anchor resolution.
     * Prefer [topicMarkedUnread] over unread URL presence — URL may be cleared before async load.
     */
    fun parserTrustsListUnread(hints: TopicOpenListHints?, fetchUrl: String): Boolean {
        if (hints?.topicMarkedUnread != true) return false
        return fetchUrl.contains("view=getnewpost", ignoreCase = true)
    }

    /**
     * Read list rows use `getlastpost`; plain LAST_UNREAD opens still use `getnewpost`.
     */
    fun parserTrustsGetNewPostUnread(
            hints: TopicOpenListHints?,
            fetchUrl: String,
            setting: AppPreferences.Main.TopicOpenTarget,
    ): Boolean {
        if (!fetchUrl.contains("view=getnewpost", ignoreCase = true)) return false
        if (parserTrustsListUnread(hints, fetchUrl)) return true
        return setting == AppPreferences.Main.TopicOpenTarget.LAST_UNREAD
    }

    fun prefetchParserHint(
            hints: TopicOpenListHints?,
            prefetchUrl: String,
            setting: AppPreferences.Main.TopicOpenTarget,
    ): Boolean = parserTrustsGetNewPostUnread(hints, prefetchUrl, setting)

    /**
     * Log 486: [resolveTopicOpenUrl] sets [pendingParserListUnreadHint]=true, then [loadData]
     * calls [resetTransientStateForNewTopic] which cleared it before the async fetch read the flag.
     * Capture at [loadData] entry (before reset) or recompute from URL + setting.
     */
    fun captureParserListUnreadHintForLoad(
            pendingHintFromResolve: Boolean,
            hints: TopicOpenListHints?,
            fetchUrl: String,
            loadAction: ThemeLoadAction,
            setting: AppPreferences.Main.TopicOpenTarget,
    ): Boolean {
        if (loadAction != ThemeLoadAction.Normal) return false
        if (pendingHintFromResolve) return true
        return parserTrustsGetNewPostUnread(hints, fetchUrl, setting)
    }

    /**
     * `view=findpost` responses do not run getnewpost anchor resolution; restore unread scroll
     * when this open still seeks first unread under LAST_UNREAD.
     */
    fun shouldPreserveUnreadTargetAfterLoad(
            setting: AppPreferences.Main.TopicOpenTarget,
            loadAction: ThemeLoadAction,
            parserListUnreadHint: Boolean,
            openedViaFindPost: Boolean,
            findPostUpgradeTraceMatches: Boolean,
            requestUrl: String,
            anchorPostId: String?,
            ambiguousBottomRedirect: Boolean = false,
    ): Boolean {
        if (loadAction != ThemeLoadAction.Normal) return false
        if (anchorPostId.isNullOrBlank()) return false
        if (ambiguousBottomRedirect) return false
        if (setting != AppPreferences.Main.TopicOpenTarget.LAST_UNREAD) return false
        if (findPostUpgradeTraceMatches && openedViaFindPost) return true
        if (!parserListUnreadHint) return false
        return requestUrl.contains("view=getnewpost", ignoreCase = true) ||
                (openedViaFindPost && findPostUpgradeTraceMatches)
    }

    fun resolveOpenSessionKindAtResolve(
            context: TopicOpenContext,
            resolution: TopicOpenResolution,
    ): TopicOpenSessionKind? {
        if (isExplicitPostOpen(context, resolution)) return TopicOpenSessionKind.EXPLICIT_POST
        if (resolution.url.contains("view=getlastpost", ignoreCase = true)) {
            return TopicOpenSessionKind.READ_RESUME
        }
        if (resolution.url.contains("view=getnewpost", ignoreCase = true)) {
            return TopicOpenSessionKind.FIRST_UNREAD
        }
        if (resolution.targetType == TopicOpenTargetType.EXPLICIT_PAGE) {
            return TopicOpenSessionKind.EXPLICIT_POST
        }
        return null
    }

    fun resolveOpenSessionKindFromPage(
            page: forpdateam.ru.forpda.entity.remote.theme.ThemePage,
            initialRequestUrl: String?,
            @Suppress("UNUSED_PARAMETER")
            openFromUnreadListHint: Boolean = false,
    ): TopicOpenSessionKind {
        val request = initialRequestUrl.orEmpty()
        if (request.contains("view=findpost", ignoreCase = true) ||
                request.contains("act=findpost", ignoreCase = true)
        ) {
            return TopicOpenSessionKind.EXPLICIT_POST
        }
        if (page.ambiguousLastUnreadBottomRedirect && !page.hasUnreadTarget) {
            return TopicOpenSessionKind.AMBIGUOUS_ALL_READ
        }
        if (request.contains("view=getnewpost", ignoreCase = true)) {
            return if (page.hasUnreadTarget) {
                TopicOpenSessionKind.FIRST_UNREAD
            } else {
                TopicOpenSessionKind.READ_RESUME
            }
        }
        if (request.contains("view=getlastpost", ignoreCase = true)) {
            return TopicOpenSessionKind.READ_RESUME
        }
        return TopicOpenSessionKind.READ_RESUME
    }

    fun shouldSuppressHybridPreload(sessionKind: TopicOpenSessionKind?): Boolean =
            sessionKind == TopicOpenSessionKind.AMBIGUOUS_ALL_READ

    fun shouldSuppressMarkReadForSession(
            sessionKind: TopicOpenSessionKind?,
            page: forpdateam.ru.forpda.entity.remote.theme.ThemePage,
    ): Boolean {
        // Log 14_06-19: AMBIGUOUS_ALL_READ and READ_RESUME are *navigational* classifications
        // for "user opened a fully-read topic on its last page via getnewpost/getlastpost".
        // The mark-read gate is for "user just scrolled to the bottom of a topic they hadn't
        // completed reading", which doesn't apply here. AMBIGUOUS_ALL_READ is precisely the
        // "I read this to the end" case — that's the case the server GET view=getlastpost
        // should fire on. Keep suppression for ambiguousLastUnreadBottomRedirect only when
        // pagination signals we are NOT on the final page (then the bottom redirect is just
        // a bookmark, not a read-completion).
        if (page.ambiguousLastUnreadBottomRedirect &&
                page.pagination.current < page.pagination.all) {
            return true
        }
        return false
    }

    /**
     * Prefetch with [parserTrustsGetNewPostUnread]=false can cache last-read bottom pages (log 486).
     * Reject warm entries when open expects first-unread semantics but page has no unread target.
     * Valid ambiguous all-read warm (log 903891) is accepted when anchor is on-page.
     */
    fun isStaleWarmGetNewPostPage(
            page: forpdateam.ru.forpda.entity.remote.theme.ThemePage,
            requestUrl: String,
            parserListUnreadHint: Boolean,
    ): Boolean {
        if (!parserListUnreadHint) return false
        if (!requestUrl.contains("view=getnewpost", ignoreCase = true)) return false
        if (isOffPageGetNewPostAnchor(page)) return true
        if (page.ambiguousLastUnreadBottomRedirect && !page.hasUnreadTarget) return false
        if (!page.hasUnreadTarget) return true
        return false
    }

    fun shouldAcceptWarmGetNewPostPage(
            page: forpdateam.ru.forpda.entity.remote.theme.ThemePage,
            requestUrl: String,
            parserListUnreadHint: Boolean,
    ): Boolean = !isStaleWarmGetNewPostPage(page, requestUrl, parserListUnreadHint)

    fun parseOpenSessionKind(raw: String?): TopicOpenSessionKind? =
            raw?.let { value -> runCatching { TopicOpenSessionKind.valueOf(value) }.getOrNull() }

    private fun isExplicitPostOpen(
            context: TopicOpenContext,
            resolution: TopicOpenResolution,
    ): Boolean = resolution.targetType == TopicOpenTargetType.EXPLICIT_POST ||
            context.openIntentRaw == TopicOpenIntentClassifier.EXPLICIT_POST ||
            (context.explicitPostId != null &&
                    (resolution.targetType == TopicOpenTargetType.EXPLICIT_POST ||
                            TopicOpenTargetResolver.hasFindPostMarker(context.rawUrl)))

    /**
     * Log 033/1103268: warm prefetch cached page 1222 with hat anchor 135617646 not in posts —
     * findpost then relocated to page 1. Reject stale entry and realign to redirect hash on page.
     */
    fun isOffPageGetNewPostAnchor(page: forpdateam.ru.forpda.entity.remote.theme.ThemePage): Boolean {
        val anchorId = page.anchorPostId?.trim()?.removePrefix("entry")?.toIntOrNull()
                ?: page.anchor?.removePrefix("entry")?.trim()?.toIntOrNull()
                ?: return false
        if (page.posts.isEmpty()) return false
        return page.posts.none { it.id == anchorId }
    }

    /**
     * Log 14_06-21-08: a fully-read read-row opened via getnewpost can redirect to a bottom
     * #entry bookmark whose post is NOT on the loaded last page (the redirect target lives on a
     * later page than the window the parser returned). When that happens, realignOffPageGetNewPostAnchor
     * would fall back to an earlier on-page post, stranding the user above the true last post.
     * Instead we should reload the topic at view=findpost&p=<redirectId> to fetch the real last
     * page that actually contains the redirect post.
     *
     * Returns the off-page redirect post id when a findpost reload is warranted, else null.
     */
    fun offPageReadResumeFindPostReloadId(
            page: forpdateam.ru.forpda.entity.remote.theme.ThemePage,
    ): Int? {
        if (page.hasUnreadTarget) return null
        if (page.posts.isEmpty()) return null
        val redirectId = ThemeApi.extractHashEntryPostIdFromTopicUrl(page.url.orEmpty())?.toIntOrNull()
                ?: return null
        // Only when the redirect target genuinely is NOT on the loaded page window.
        if (page.posts.any { it.id == redirectId }) return null
        // Guard against pathological redirects: id must look like a real post (> 0).
        if (redirectId <= 0) return null
        return redirectId
    }

    /**
     * When getnewpost anchor post is not on the loaded page window, prefer server redirect #entry
     * if that post is present; otherwise first parsed post on the page.
     */
    fun realignOffPageGetNewPostAnchor(page: forpdateam.ru.forpda.entity.remote.theme.ThemePage): Boolean {
        if (!isOffPageGetNewPostAnchor(page)) return false
        val redirectId = ThemeApi.extractHashEntryPostIdFromTopicUrl(page.url.orEmpty())?.toIntOrNull()
        val realignedId = when {
            redirectId != null && page.posts.any { it.id == redirectId } -> redirectId
            else -> page.posts.firstOrNull { it.id > 0 }?.id
        } ?: return false
        page.anchorPostId = realignedId.toString()
        page.anchors.clear()
        page.addAnchor("entry$realignedId")
        return true
    }

    /**
     * Log 1121483: a "read" favorites row opened with `view=getnewpost` only because of the
     * LAST_UNREAD setting (not a genuine list-unread +N row) lands on the all-read bottom redirect.
     * The parser flags it ambiguous (anchor=null) so the genuine list-unread tail-post heuristic can
     * run — but for a setting-default read-resume open there is no list-unread signal, so the topic is
     * simply all-read and the user must resume at the server bottom redirect (the last-read post),
     * exactly as `view=getlastpost` did. Realign the anchor to the redirect bottom entry and drop the
     * ambiguous flag so the WebView scrolls to the last-read post instead of stranding on the page hat.
     */
    fun resolveReadResumeBottomRedirect(
            page: forpdateam.ru.forpda.entity.remote.theme.ThemePage,
            listMarkedUnread: Boolean,
    ): Boolean {
        // Log 14_06-19: listMarkedUnread is a *list-screen* hint about topic-level unread count.
        // It must not gate the anchor realignment for fully-read bottom-redirect topics: when
        // `ambiguousLastUnreadBottomRedirect=true && !hasUnreadTarget && anchorPostId==null`,
        // the redirect hash is the only reliable last-read post for resume scrolling, regardless
        // of whether the list said the topic had unread posts (the row can be stale from a
        // previous session / view=getlastpost click). The remaining three conditions below
        // still prevent over-application: we only realign on the ambiguous all-read bottom
        // redirect case, never on EXPLICIT_POST / FIRST_UNREAD / genuine unread targets.
        @Suppress("UNUSED_PARAMETER")
        val ignoreListMarkedUnread = listMarkedUnread
        if (!page.ambiguousLastUnreadBottomRedirect) return false
        if (page.hasUnreadTarget) return false
        if (!page.anchorPostId.isNullOrBlank()) return false
        val redirectId = ThemeApi.extractHashEntryPostIdFromTopicUrl(page.url.orEmpty())?.toIntOrNull()
                ?: return false
        if (page.posts.isNotEmpty() && page.posts.none { it.id == redirectId }) return false
        page.ambiguousLastUnreadBottomRedirect = false
        page.anchorPostId = redirectId.toString()
        page.anchors.clear()
        page.addAnchor("entry$redirectId")
        // Log 1122662: when the resolved last-read anchor is the LAST (newest) post of the LAST page,
        // the redirect is a bottom bookmark — the user must resume at the END of the page, not at the
        // top of that final post (a tall final post leaves the soft anchor scroll stranded at ratio
        // ~0.66-0.89). Flag a scroll-to-bottom resume so the WebView bottom-aligns like END navigation.
        page.resumeToLastPageBottom = isLastPostOfLastPage(page, redirectId)
        return true
    }

    /**
     * True when [postId] is the last (newest) parsed post of the page AND this is the last page
     * (pagination.current == pagination.all). Used to decide a read-resume bottom redirect should
     * scroll to the page bottom instead of the top of the final post.
     */
    private fun isLastPostOfLastPage(
            page: forpdateam.ru.forpda.entity.remote.theme.ThemePage,
            postId: Int,
    ): Boolean {
        if (page.posts.isEmpty()) return false
        if (page.posts.lastOrNull { it.id > 0 }?.id != postId) return false
        val current = page.pagination.current
        val all = page.pagination.all
        if (current <= 0 || all <= 0) return false
        return current == all
    }

    /** List-screen and list-hint open under LAST_UNREAD when no explicit post/page in URL. */
    fun resolveListOpen(
            context: TopicOpenContext,
            info: ThemeUrlInfo,
            normalizeUnread: (String) -> String = TopicOpenTargetResolver::normalizeLastUnreadNavigationUrl,
            @Suppress("UNUSED_PARAMETER")
            normalizeRead: (String) -> String = TopicOpenTargetResolver::normalizeLastReadNavigationUrl,
    ): TopicOpenResolution? {
        if (context.setting != AppPreferences.Main.TopicOpenTarget.LAST_UNREAD) return null

        val unreadListUrl = context.unreadUrlFromList?.trim().orEmpty()
        if (unreadListUrl.isNotEmpty() && ThemeUrlPolicy.parse(unreadListUrl)?.topicId == info.topicId) {
            val normalized = normalizeUnread(unreadListUrl)
            return TopicOpenResolution(
                    url = normalized,
                    targetType = TopicOpenTargetType.SERVER_UNREAD_FALLBACK,
                    suppressScrollRestore = true,
                    reason = unreadListUrlReason(unreadListUrl, normalized)
            )
        }
        if (!isListScreenOpen(context.sourceScreen)) return null

        if (context.listTopicMarkedUnread) {
            val normalized = normalizeUnread(context.rawUrl)
            return TopicOpenResolution(
                    url = normalized,
                    targetType = TopicOpenTargetType.SETTING_LAST_UNREAD,
                    suppressScrollRestore = true,
                    reason = "list_marked_unread_use_getnewpost"
            )
        }
        // Log 24_06-14: read list row (no list-unread URL, list not marked unread, inspector not
        // marking it unread) under LAST_UNREAD must use `view=getlastpost` to resume at the
        // server-side last-read bookmark. The previous `list_read_use_getnewpost` always resolved
        // to `getnewpost`, which the server redirected to the bottom-bookmark of an already-read
        // topic without any first-unread to seek — leaving the user on the top of the last page
        // with no highlight.
        if (!context.inspectorMarkedUnread &&
                context.unreadUrlFromList.isNullOrBlank() &&
                context.unreadPostIdFromList?.takeIf { it > 0 } == null
        ) {
            val normalized = TopicOpenTargetResolver.normalizeLastReadNavigationUrl(context.rawUrl)
            return TopicOpenResolution(
                    url = normalized,
                    targetType = TopicOpenTargetType.READ_RESUME,
                    suppressScrollRestore = true,
                    reason = "list_read_use_getlastpost"
            )
        }
        if (isListReadOpen(context)) {
            // Log 1121483: a "read" favorites row under LAST_UNREAD must still seek the first unread.
            // getlastpost can only ever resolve to the server last-read bookmark, so when the list
            // read-state lags behind the server (new posts arrived) the user lands on an already-read
            // post. getnewpost resolves to the first unread when posts exist and otherwise falls back
            // to the all-read bottom redirect (handled downstream as ambiguous, never jumping to an
            // older post). Prefer the list last-read href only as the base topic URL, upgraded to
            // getnewpost.
            val base = context.lastReadUrlFromList
                    ?.takeIf { ThemeUrlPolicy.parse(it)?.topicId == info.topicId }
                    ?: context.rawUrl
            val normalized = normalizeUnread(base)
            return TopicOpenResolution(
                    url = normalized,
                    targetType = TopicOpenTargetType.SETTING_LAST_UNREAD,
                    suppressScrollRestore = true,
                    reason = "list_read_use_getnewpost"
            )
        }
        return null
    }

    fun isListReadOpen(context: TopicOpenContext): Boolean {
        if (context.listTopicMarkedUnread) return false
        if (context.unreadUrlFromList?.trim().orEmpty().isNotEmpty()) return false
        if (context.unreadPostIdFromList?.takeIf { it > 0 } != null) return false
        if (hasListLastReadPostHint(context.rawUrl)) return false
        if (!TopicOpenTargetResolver.isOrdinaryInitialTopicUrl(context.rawUrl)) return false
        return isListScreenOpen(context.sourceScreen)
    }

    /**
     * Resolves scroll anchor for `view=getnewpost` after HTML is available.
     * Caller clears [page.anchors] before applying the result.
     */
    fun resolveGetNewPostAnchor(ctx: GetNewPostAnchorContext): AnchorResolution {
        var unreadId = ThemeApi.findUnreadPostEntryIdForGetNewPost(ctx.html, null)
        if (ctx.hatEntryIdToSkip != null && unreadId == ctx.hatEntryIdToSkip) {
            unreadId = ThemeApi.findUnreadPostEntryIdForGetNewPost(ctx.html, ctx.hatEntryIdToSkip)
        }
        unreadId?.let {
            return AnchorResolution("entry$it", hasUnreadTarget = true, reason = "html_unread_marker")
        }

        val redirectHashId = ctx.redirectHashId
        val entryIds = ctx.entryIds
        val hatSkip = ctx.hatEntryIdToSkip ?: inferPrependedHatEntryId(entryIds, redirectHashId)

        if (ctx.onLastTopicPage &&
                ThemeApi.isLikelyAllReadGetNewPostBottomRedirect(redirectHashId, entryIds)
        ) {
            redirectHashId?.let { hashPostId ->
                val ambiguous = ctx.listUnreadHint
                return AnchorResolution(
                        anchorEntry = if (ambiguous) null else "entry$hashPostId",
                        hasUnreadTarget = false,
                        reason = "all_read_bottom_redirect",
                        ambiguousBottomRedirect = ambiguous
                )
            }
        }

        redirectHashId
                ?.takeIf { hashId -> hatSkip == null || hashId != hatSkip }
                ?.takeIf { hashId ->
                    !ThemeApi.isLikelyLastReadPageTopHint(hashId, entryIds, hatSkip) &&
                            !rejectsBottomHash(hashId, entryIds, hatSkip, ctx.listUnreadHint)
                }
                ?.let { hashPostId ->
                    return AnchorResolution(
                            anchorEntry = "entry$hashPostId",
                            hasUnreadTarget = true,
                            reason = "redirect_hash"
                    )
                }

        ThemeApi.extractHighlightPostIdFromTopicUrl(ctx.finalUrl)?.toIntOrNull()?.let { highlightPostId ->
            return AnchorResolution("entry$highlightPostId", hasUnreadTarget = true, reason = "highlight_query")
        }

        ThemeApi.extractLastReadStylePostIdFromTopicUrl(ctx.finalUrl)?.toIntOrNull()
                ?.takeIf { queryPostId ->
                    !ThemeApi.isLikelyLastReadPageTopHint(queryPostId, entryIds, hatSkip) &&
                            !rejectsBottomHash(queryPostId, entryIds, hatSkip, ctx.listUnreadHint)
                }
                ?.let { queryPostId ->
                    return AnchorResolution(
                            anchorEntry = "entry$queryPostId",
                            hasUnreadTarget = true,
                            reason = "query_post_id"
                    )
                }

        Regex("""(?is)<link[^>]+rel=["']canonical["'][^>]+href=["']([^"']+)""").find(ctx.html)
                ?.groupValues?.getOrNull(1)?.let { href ->
                    ThemeApi.extractHighlightPostIdFromTopicUrl(href)?.toIntOrNull()?.let { cid ->
                        return AnchorResolution("entry$cid", hasUnreadTarget = true, reason = "canonical_highlight")
                    }
                    ThemeApi.extractLastReadStylePostIdFromTopicUrl(href)?.toIntOrNull()
                            ?.takeIf { queryPostId ->
                                !ThemeApi.isLikelyLastReadPageTopHint(queryPostId, entryIds, hatSkip) &&
                                        !rejectsBottomHash(queryPostId, entryIds, hatSkip, ctx.listUnreadHint)
                            }
                            ?.let { cid ->
                                return AnchorResolution(
                                        anchorEntry = "entry$cid",
                                        hasUnreadTarget = true,
                                        reason = "canonical_query_post"
                                )
                            }
                }

        // Log 1122662: getnewpost can redirect to the **top of page 1** (`st=0`, `#entry{first}`)
        // when the server has no real first-unread for this topic (all-read, or last-read bookmark
        // sits at the topic top). The top redirect is rejected as a last-read top hint above, but the
        // list-unread fallback below would still anchor to the first (already-read) post on page 1 with
        // `hasUnreadTarget=true` — stranding the user at the very top of page 1. Treat this as an
        // ambiguous all-read open (anchor=null) so the open is not misreported as a confirmed unread
        // target and downstream policies resume at the server bookmark instead of page-1 top.
        // Genuine first-unread on page 1 always carries HTML unread markers and is resolved earlier.
        if (isPageTopRedirectWithoutUnread(ctx, redirectHashId, entryIds, hatSkip)) {
            return AnchorResolution(
                    anchorEntry = null,
                    hasUnreadTarget = false,
                    reason = "page_top_redirect_no_unread",
                    ambiguousBottomRedirect = ctx.listUnreadHint
            )
        }

        val bottomHashRejected = redirectHashId != null &&
                rejectsBottomHash(redirectHashId, entryIds, hatSkip, ctx.listUnreadHint)
        val fallbackEntry = resolveEntryFallback(
                entryIds = entryIds,
                hatSkip = hatSkip,
                bottomHashRejected = bottomHashRejected,
                listUnreadHint = ctx.listUnreadHint,
                redirectHashId = redirectHashId,
        )
        fallbackEntry?.let { entry ->
            return AnchorResolution(
                    anchorEntry = "entry$entry",
                    hasUnreadTarget = true,
                    reason = when {
                        bottomHashRejected -> "fallback_after_bottom_reject"
                        ctx.listUnreadHint -> "list_unread_entry_fallback"
                        else -> "fallback_entry_list"
                    },
                    bottomHashRejected = bottomHashRejected
            )
        }

        Regex("""<div[^>]+data-post=["'](\d+)["']""", RegexOption.IGNORE_CASE)
                .find(ctx.html)
                ?.groupValues
                ?.getOrNull(1)
                ?.toIntOrNull()
                ?.let {
                    return AnchorResolution("entry$it", hasUnreadTarget = true, reason = "data_post_attr")
                }

        return AnchorResolution(anchorEntry = null, hasUnreadTarget = false, reason = "no_anchor")
    }

    fun urlSt(finalUrl: String): Int = runCatching {
        Uri.parse(finalUrl).getQueryParameter("st")?.toIntOrNull() ?: 0
    }.getOrDefault(0)

    /**
     * Log 1122662: `view=getnewpost` redirected to the **top of page 1** (`st=0`) with the redirect
     * `#entry` pointing at the first post on the page, and no HTML unread markers were found. This is
     * a last-read/all-read top hint, not a first-unread target, so anchoring to the page-1 top would
     * wrongly open the topic at its very first (already-read) post. Detect this so it is reported as
     * an ambiguous all-read open instead of a confirmed unread anchor.
     *
     * Requires: multiple entries on the page (single-post pages keep their natural anchor) and the
     * redirect hash equal to the first content entry (after skipping a prepended topic hat).
     */
    private fun isPageTopRedirectWithoutUnread(
            ctx: GetNewPostAnchorContext,
            redirectHashId: Int?,
            entryIds: List<Int>,
            hatSkip: Int?,
    ): Boolean {
        if (redirectHashId == null) return false
        if ((ThemeApi.extractStFromUrl(ctx.finalUrl) ?: 0) != 0) return false
        val contentEntries = if (hatSkip != null) entryIds.filter { it != hatSkip } else entryIds
        if (contentEntries.size <= 1) return false
        return redirectHashId == contentEntries.firstOrNull()
    }

    /**
     * Bottom `#entry` on a multi-post page without HTML unread markers is not a reliable unread
     * target. List-unread opens are handled earlier as ambiguous all-read redirects.
     */
    private fun rejectsBottomHash(
            postId: Int,
            entryIds: List<Int>,
            hatSkip: Int?,
            listUnreadHint: Boolean,
    ): Boolean {
        if (listUnreadHint) return false
        if (!ThemeApi.isLikelyLastReadPageBottomHint(postId, entryIds)) return false
        val contentEntries = if (hatSkip != null) entryIds.filter { it != hatSkip } else entryIds
        return contentEntries.size > 1
    }

    /**
     * Without list hint, legacy second-entry / bottom-reject fallbacks apply.
     * List-unread fallbacks prefer non-hat entries near the redirect tail when bottom was skipped.
     */
    private fun resolveEntryFallback(
            entryIds: List<Int>,
            hatSkip: Int?,
            bottomHashRejected: Boolean,
            listUnreadHint: Boolean,
            redirectHashId: Int?,
    ): Int? {
        if (entryIds.isEmpty()) return null
        if (listUnreadHint) {
            val contentEntries = if (hatSkip != null) entryIds.filter { it != hatSkip } else entryIds
            if (bottomHashRejected && redirectHashId != null) {
                return contentEntries.firstOrNull { it != redirectHashId } ?: contentEntries.firstOrNull()
            }
            return contentEntries.firstOrNull() ?: entryIds.firstOrNull()
        }
        return when {
            hatSkip != null && entryIds.firstOrNull() == hatSkip && entryIds.size > 1 -> entryIds[1]
            bottomHashRejected && entryIds.size == 2 -> entryIds[1]
            bottomHashRejected && redirectHashId != null -> {
                val contentEntries = if (hatSkip != null) entryIds.filter { it != hatSkip } else entryIds
                contentEntries.firstOrNull { it != redirectHashId } ?: contentEntries.firstOrNull()
            }
            bottomHashRejected -> entryIds.firstOrNull { it != hatSkip } ?: entryIds.firstOrNull()
            entryIds.size > 1 -> entryIds[1]
            else -> entryIds.firstOrNull()
        }
    }

    /**
     * Last-page HTML often prepends the topic hat (post #1) before the real page window.
     * Detect when the first `#entry` id is far below the cluster of posts on the redirect page.
     */
    internal fun inferPrependedHatEntryId(entryIds: List<Int>, redirectHashId: Int?): Int? {
        if (entryIds.size < 2 || redirectHashId == null) return null
        val first = entryIds.first()
        if (first == redirectHashId || entryIds.last() != redirectHashId) return null
        val tail = entryIds.drop(1)
        if (tail.isEmpty()) return null
        val clusterMedian = tail.sorted()[tail.size / 2]
        return first.takeIf { clusterMedian - first > PREPENDED_HAT_ID_GAP_THRESHOLD }
    }

    private const val PREPENDED_HAT_ID_GAP_THRESHOLD = 500_000

    private fun isListScreenOpen(sourceScreen: String): Boolean =
            when (sourceScreen.lowercase()) {
                "favorites", "favorites_prefetch", "topics" -> true
                else -> false
            }

    private fun hasListLastReadPostHint(rawUrl: String): Boolean {
        if (Regex("""(?i)[?&]view=findpost""").containsMatchIn(rawUrl)) return false
        if (rawUrl.contains("#entry", ignoreCase = true)) return false
        return ThemeApi.extractLastReadStylePostIdFromTopicUrl(rawUrl) != null
    }

    private fun unreadListUrlReason(source: String, resolved: String): String = when {
        source != resolved && source.replace(Regex("""(?i)[?&]st=\d+"""), "") == resolved ->
            "server_unread_url_stripped_list_st"
        source.contains("view=getlastpost", ignoreCase = true) &&
                resolved.contains("view=getnewpost", ignoreCase = true) ->
            "server_unread_url_upgraded_getnewpost"
        else -> "server_unread_url_from_list"
    }
}
