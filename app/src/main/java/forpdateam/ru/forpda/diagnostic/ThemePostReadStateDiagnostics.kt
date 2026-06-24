package forpdateam.ru.forpda.diagnostic

/**
 * DEBUG-only structured tracing for in-topic read/unread pipeline:
 * anchor resolution, favorites open hints, prefetch, scroll settle, mark-read.
 *
 * Filter logcat: `adb logcat -s FPDA_THEME_POST_READ_STATE`
 */
object ThemePostReadStateDiagnostics {

    fun parserAnchorResolved(
            topicId: Int,
            url: String?,
            listUnreadHint: Boolean,
            reason: String,
            anchorPostId: String?,
            hasUnreadTarget: Boolean,
            serverUnreadPostIds: Collection<Int>,
            htmlUnreadCount: Int,
            pageCurrent: Int,
            pageTotal: Int,
            redirectEntryId: Int?,
            parsedPostCount: Int = 0,
            extra: Map<String, Any?> = emptyMap()
    ) {
        log(
                "parser_anchor_resolved",
                linkedMapOf(
                        "topicId" to topicId,
                        "url" to FpdaDebugLog.sanitizeUrl(url),
                        "listUnreadHint" to listUnreadHint,
                        "reason" to reason,
                        "anchorPostId" to anchorPostId,
                        "hasUnreadTarget" to hasUnreadTarget,
                        "serverUnreadPostIds" to formatPostIdList(serverUnreadPostIds),
                        "htmlUnreadCount" to htmlUnreadCount,
                        "pageCurrent" to pageCurrent,
                        "pageTotal" to pageTotal,
                        "redirectEntryId" to redirectEntryId,
                        "parsedPostCount" to parsedPostCount
                ) + extra
        )
    }

    fun favoritesOpenPath(
            topicId: Int,
            isNew: Boolean,
            unreadUrlFromListPresent: Boolean,
            openFromUnreadListHint: Boolean,
            unreadUrlFromList: String? = null,
            source: String = "favorites",
            readState: String? = null,
            unreadPostCount: Int? = null,
            topicMarkedUnread: Boolean? = null,
            inspectorMarkedUnread: Boolean? = null
    ) {
        log(
                "favorites_open_path",
                mapOf(
                        "topicId" to topicId,
                        "isNew" to isNew,
                        "readState" to readState,
                        "unreadPostCount" to unreadPostCount,
                        "topicMarkedUnread" to topicMarkedUnread,
                        "inspectorMarkedUnread" to inspectorMarkedUnread,
                        "unreadUrlFromListPresent" to unreadUrlFromListPresent,
                        "openFromUnreadListHint" to openFromUnreadListHint,
                        "unreadUrlFromList" to FpdaDebugLog.sanitizeUrl(unreadUrlFromList),
                        "source" to source
                )
        )
    }

    fun prefetchStart(
            topicId: Int,
            url: String,
            openFromUnreadListHint: Boolean
    ) {
        log(
                "prefetch_start",
                mapOf(
                        "topicId" to topicId,
                        "url" to FpdaDebugLog.sanitizeUrl(url),
                        "openFromUnreadListHint" to openFromUnreadListHint
                )
        )
    }

    fun prefetchConsume(
            url: String,
            openFromUnreadListHint: Boolean,
            hit: Boolean,
            anchorPostId: String? = null,
            hasUnreadTarget: Boolean? = null,
            topicId: Int? = null
    ) {
        log(
                if (hit) "prefetch_hit" else "prefetch_miss",
                mapOf(
                        "topicId" to topicId,
                        "url" to FpdaDebugLog.sanitizeUrl(url),
                        "openFromUnreadListHint" to openFromUnreadListHint,
                        "anchorPostId" to anchorPostId,
                        "hasUnreadTarget" to hasUnreadTarget
                )
        )
    }

    fun viewModelLoadComplete(
            topicId: Int,
            traceId: String?,
            anchorPostId: String?,
            hasUnreadTarget: Boolean,
            blockScrollRestoreForUnread: Boolean,
            loadAction: String? = null,
            pageCurrent: Int? = null,
            pageTotal: Int? = null
    ) {
        log(
                "vm_load_complete",
                mapOf(
                        "topicId" to topicId,
                        "traceId" to traceId,
                        "anchorPostId" to anchorPostId,
                        "hasUnreadTarget" to hasUnreadTarget,
                        "blockScrollRestoreForUnread" to blockScrollRestoreForUnread,
                        "loadAction" to loadAction,
                        "pageCurrent" to pageCurrent,
                        "pageTotal" to pageTotal
                )
        )
    }

    fun postsMapped(
            topicId: Int,
            parsedPostCount: Int,
            htmlUnreadStyledCount: Int
    ) {
        if (parsedPostCount <= 0 && htmlUnreadStyledCount <= 0) return
        log(
                "posts_mapped",
                mapOf(
                        "topicId" to topicId,
                        "parsedPostCount" to parsedPostCount,
                        "htmlUnreadStyledCount" to htmlUnreadStyledCount
                )
        )
    }

    fun scrollSettled(
            topicId: Int?,
            traceId: String?,
            anchorPostId: String?,
            finalScrolledPostId: String?,
            scrollKind: String,
            success: Boolean,
            reason: String? = null
    ) {
        val normalizedAnchor = normalizePostId(anchorPostId)
        val normalizedFinal = normalizePostId(finalScrolledPostId)
        log(
                "scroll_settled",
                mapOf(
                        "topicId" to topicId,
                        "traceId" to traceId,
                        "anchorPostId" to normalizedAnchor,
                        "finalScrolledPostId" to normalizedFinal,
                        "anchorMatch" to when {
                            normalizedAnchor.isNullOrBlank() || normalizedFinal.isNullOrBlank() -> null
                            else -> normalizedAnchor == normalizedFinal
                        },
                        "scrollKind" to scrollKind,
                        "success" to success,
                        "reason" to reason
                )
        )
    }

    fun markReadTriggered(
            topicId: Int,
            reason: String,
            source: String? = null
    ) {
        log(
                "mark_read_triggered",
                mapOf(
                        "topicId" to topicId,
                        "reason" to reason,
                        "source" to source
                )
        )
    }

    fun markReadApplied(
            topicId: Int,
            reason: String,
            source: String,
            prevIsNew: Boolean? = null,
            prevReadState: String? = null,
            prevUnreadCount: Int? = null,
            newIsNew: Boolean? = null,
            newReadState: String? = null,
            newUnreadCount: Int? = null,
            itemPresent: Boolean? = null
    ) {
        log(
                "mark_read_applied",
                linkedMapOf(
                        "topicId" to topicId,
                        "reason" to reason,
                        "source" to source,
                        "prevIsNew" to prevIsNew,
                        "prevReadState" to prevReadState,
                        "prevUnreadCount" to prevUnreadCount,
                        "newIsNew" to newIsNew,
                        "newReadState" to newReadState,
                        "newUnreadCount" to newUnreadCount,
                        "itemPresent" to itemPresent
                )
        )
    }

    fun markReadSkipped(
            topicId: Int,
            reason: String,
            source: String,
            currentPage: Int = 0,
            allPages: Int = 0
    ) {
        log(
                "mark_read_skipped",
                mapOf(
                        "topicId" to topicId,
                        "reason" to reason,
                        "source" to source,
                        "current" to currentPage,
                        "all" to allPages
                )
        )
    }

    fun markReadGateCheck(
            topicId: Int,
            currentPage: Int,
            allPages: Int,
            wasNearBottom: Boolean?,
            scrollRatio: Double?,
            result: String
    ) {
        log(
                "mark_read_gate_check",
                mapOf(
                        "topicId" to topicId,
                        "current" to currentPage,
                        "all" to allPages,
                        "wasNearBottom" to wasNearBottom,
                        "scrollRatio" to scrollRatio,
                        "result" to result
                )
        )
    }

    /** Tracks server-side mark-read GET view=getlastpost fired from natural bottom-of-topic. */
    fun markReadServerSent(
            topicId: Int,
            sent: Boolean,
            source: String
    ) {
        log(
                "mark_read_server_sent",
                mapOf(
                        "topicId" to topicId,
                        "sent" to sent,
                        "source" to source
                )
        )
    }

    fun parseLastPostFromScrollReason(reason: String): String? {
        val match = LAST_POST_IN_REASON.find(reason) ?: return null
        return match.groupValues.getOrNull(1)?.takeIf { it.isNotBlank() }
    }

    internal fun formatPostIdList(ids: Collection<Int>): String =
            if (ids.isEmpty()) {
                "none"
            } else {
                ids.distinct().sorted().joinToString(separator = ",")
            }

    internal fun normalizePostId(postId: String?): String? =
            postId?.trim()
                    ?.removePrefix("entry")
                    ?.removePrefix("ENTRY")
                    ?.takeIf { it.isNotBlank() }

    private val LAST_POST_IN_REASON = Regex("""\|lastPost=(\d*)""")

    private fun log(event: String, fields: Map<String, Any?>) {
        FpdaDebugLog.log(FpdaDebugLog.TAG_THEME_POST_READ_STATE, event, fields)
    }
}
