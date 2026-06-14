package forpdateam.ru.forpda.diagnostic

/**
 * Structured anchor-resolution tracing for `view=getnewpost` opens.
 *
 * Filter logcat: `adb logcat -s FPDA_THEME_UNREAD_DIAG`
 */
object TopicUnreadAnchorDiagnostics {

    const val LOG_TAG = "FPDA_THEME_UNREAD_DIAG"

    fun anchorResolved(
            topicId: Int,
            anchorSource: String,
            postId: String?,
            hasUnreadTarget: Boolean,
            listUnreadHint: Boolean,
            redirectEntryId: Int? = null,
            htmlUnreadCount: Int = 0,
            pageCurrent: Int = 0,
            pageTotal: Int = 0,
            extra: Map<String, Any?> = emptyMap(),
    ) {
        FpdaDebugLog.log(
                LOG_TAG,
                "anchor_resolved",
                mapOf(
                        "anchorSource" to anchorSource,
                        "postId" to (postId ?: "none"),
                        "hasUnreadTarget" to hasUnreadTarget,
                        "listUnreadHint" to listUnreadHint,
                        "topicId" to topicId,
                        "redirectEntryId" to redirectEntryId,
                        "htmlUnreadCount" to htmlUnreadCount,
                        "pageCurrent" to pageCurrent.takeIf { it > 0 },
                        "pageTotal" to pageTotal.takeIf { pageCurrent > 0 },
                ) + extra,
        )
    }

    fun findPostReloadSkipped(
            topicId: Int,
            reason: String,
            anchorPostId: String?,
            hasUnreadTarget: Boolean,
    ) {
        FpdaDebugLog.log(
                LOG_TAG,
                "findpost_reload_skipped",
                mapOf(
                        "topicId" to topicId,
                        "reason" to reason,
                        "anchor" to anchorPostId,
                        "hasUnreadTarget" to hasUnreadTarget,
                ),
        )
    }

    fun findPostReloadStarted(topicId: Int, anchorPostId: String, traceId: String) {
        FpdaDebugLog.log(
                LOG_TAG,
                "findpost_reload_started",
                mapOf(
                        "topicId" to topicId,
                        "anchor" to anchorPostId,
                        "trace" to traceId,
                ),
        )
    }

    fun openTarget(
            topicId: Int,
            openTarget: String,
            expectedPostId: String?,
            actualPostId: String?,
            hasUnreadTarget: Boolean,
            anchorSource: String? = null,
            traceId: String? = null,
    ) {
        FpdaDebugLog.log(
                LOG_TAG,
                "open_target=$openTarget",
                mapOf(
                        "topicId" to topicId,
                        "expected" to (expectedPostId ?: "none"),
                        "actual" to (actualPostId ?: "none"),
                        "hasUnreadTarget" to hasUnreadTarget,
                        "anchorSource" to anchorSource,
                        "trace" to traceId,
                ),
        )
    }
}
