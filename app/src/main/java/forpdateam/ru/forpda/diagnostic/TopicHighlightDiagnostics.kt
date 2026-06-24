package forpdateam.ru.forpda.diagnostic

import forpdateam.ru.forpda.presentation.theme.HighlightType

/**
 * DEBUG-only structured tracing for the topic post highlight pipeline.
 *
 * Filter logcat: `adb logcat -s PPDA_TOPIC_HIGHLIGHT`
 *
 * Events follow the user-facing QA checklist in
 * `docs/topic-highlight-qa.md`. If a highlight is expected but not visible, these
 * events show the resolver decision, the render outcome, and any stale-callback
 * suppression.
 */
object TopicHighlightDiagnostics {

    fun highlightResolveStarted(
            topicId: Long,
            hasUnread: Boolean,
            lastViewed: Boolean,
            explicit: Boolean,
            firstUnreadPostId: Long?,
            lastViewedPostId: Long?,
            explicitPostId: Long?,
            pagePostCount: Int
    ) {
        log(
                "highlight_resolve_started",
                linkedMapOf(
                        "topicId" to topicId,
                        "hasUnread" to hasUnread,
                        "lastViewed" to lastViewed,
                        "explicit" to explicit,
                        "firstUnreadPostId" to firstUnreadPostId,
                        "lastViewedPostId" to lastViewedPostId,
                        "explicitPostId" to explicitPostId,
                        "pagePostCount" to pagePostCount
                )
        )
    }

    fun highlightTargetResolved(
            topicId: Long,
            type: HighlightType,
            postId: Long,
            reason: String
    ) {
        log(
                "highlight_target_resolved",
                linkedMapOf(
                        "topicId" to topicId,
                        "highlightType" to type.jsName,
                        "highlightPostId" to postId,
                        "reason" to reason
                )
        )
    }

    fun highlightTargetMissing(topicId: Long, reason: String) {
        log(
                "highlight_target_missing",
                linkedMapOf(
                        "topicId" to topicId,
                        "reason" to reason
                )
        )
    }

    fun renderHighlightApplied(
            topicId: Long,
            page: Int,
            renderGenerationId: Int,
            mode: String,
            highlightType: String,
            appliedSuccessfully: Boolean,
            postAnchorExists: Boolean
    ) {
        log(
                "render_highlight_applied",
                linkedMapOf(
                        "topicId" to topicId,
                        "page" to page,
                        "renderGenerationId" to renderGenerationId,
                        "mode" to mode,
                        "highlightType" to highlightType,
                        "appliedSuccessfully" to appliedSuccessfully,
                        "postAnchorExists" to postAnchorExists
                )
        )
    }

    fun jsHighlightApplied(
            topicId: Long,
            page: Int,
            renderGenerationId: Int,
            highlightType: String,
            postAnchorExists: Boolean
    ) {
        log(
                "js_highlight_applied",
                linkedMapOf(
                        "topicId" to topicId,
                        "page" to page,
                        "renderGenerationId" to renderGenerationId,
                        "highlightType" to highlightType,
                        "postAnchorExists" to postAnchorExists
                )
        )
    }

    fun nativeHighlightBound(
            topicId: Long,
            page: Int,
            renderGenerationId: Int,
            highlightType: String,
            postId: Long
    ) {
        log(
                "native_highlight_bound",
                linkedMapOf(
                        "topicId" to topicId,
                        "page" to page,
                        "renderGenerationId" to renderGenerationId,
                        "highlightType" to highlightType,
                        "postId" to postId
                )
        )
    }

    /**
     * Emitted whenever [ThemeWebController.reapplyTopicHighlight] returns early
     * before it could arm the JS highlight (i.e. before
     * `native_highlight_bound` / `js_highlight_applied`). Every silent
     * early-return in that method now reports the exact guard that swallowed
     * the arming, so a device log can pinpoint where the native chain dies
     * between `highlight_target_resolved` and the JS apply.
     *
     * `reason` is one of: `disposed_or_no_view`, `no_current_page`,
     * `no_highlight_target`, `highlight_target_none`, `topic_id_non_positive`,
     * `generation_non_positive`, `post_id_non_positive`,
     * `deferred_and_already_armed` (scroll still blocking AND nothing left to
     * schedule), `already_armed` (per-generation guards already satisfied).
     */
    fun highlightArmSkipped(
            topicId: Long,
            reason: String,
            renderGenerationId: Int? = null,
            postId: Long? = null,
            deferApply: Boolean? = null,
            blockingScrollPending: Boolean? = null,
            armedGeneration: Int? = null,
            fadeoutScheduledGeneration: Int? = null
    ) {
        log(
                "highlight_arm_skipped",
                linkedMapOf(
                        "topicId" to topicId,
                        "reason" to reason,
                        "renderGenerationId" to renderGenerationId,
                        "postId" to postId,
                        "deferApply" to deferApply,
                        "blockingScrollPending" to blockingScrollPending,
                        "armedGeneration" to armedGeneration,
                        "fadeoutScheduledGeneration" to fadeoutScheduledGeneration
                )
        )
    }

    fun highlightFailedPostNotFound(
            topicId: Long,
            page: Int,
            renderGenerationId: Int,
            highlightType: String,
            expectedPostId: Long,
            failureReason: String
    ) {
        log(
                "highlight_failed_post_not_found",
                linkedMapOf(
                        "topicId" to topicId,
                        "page" to page,
                        "renderGenerationId" to renderGenerationId,
                        "highlightType" to highlightType,
                        "expectedPostId" to expectedPostId,
                        "failureReason" to failureReason
                )
        )
    }

    fun staleHighlightIgnored(
            topicId: Long,
            page: Int,
            renderGenerationId: Int,
            callbackGenerationId: Int,
            expectedPostId: Long
    ) {
        log(
                "stale_highlight_ignored",
                linkedMapOf(
                        "topicId" to topicId,
                        "page" to page,
                        "renderGenerationId" to renderGenerationId,
                        "callbackGenerationId" to callbackGenerationId,
                        "expectedPostId" to expectedPostId
                )
        )
    }

    /**
     * Native has just armed a JS-side `setTimeout(..., delayMs)` that will
     * trigger the highlight fade-out (add `post-highlight-fading`, then
     * strip the base class on `transitionend`). Emitted from
     * `ThemeWebController.reapplyTopicHighlight` once per render — never
     * on scroll, only on a new render event (topic open / page change /
     * refresh). The `delayMs` is what the JS bridge actually received.
     */
    fun highlightFadeoutScheduled(
            topicId: Long,
            page: Int,
            renderGenerationId: Int,
            delayMs: Int,
            highlightType: String,
            postId: Long
    ) {
        log(
                "highlight_fadeout_scheduled",
                linkedMapOf(
                        "topicId" to topicId,
                        "page" to page,
                        "renderGenerationId" to renderGenerationId,
                        "delayMs" to delayMs,
                        "highlightType" to highlightType,
                        "postId" to postId
                )
        )
    }

    /**
     * JS reports back that the highlight class has been fully removed from
     * the DOM for a given render generation (the `transitionend` handler
     * in `PPDA_scheduleHighlightFadeout` fired, or the defensive 600ms
     * fallback ran). The `renderGenerationId` echoes back the same id the
     * native side armed the timer with, so a future render with a fresh
     * generation will not be confused with this completion.
     */
    fun highlightFadeoutCompleted(
            topicId: Long,
            page: Int,
            renderGenerationId: Int
    ) {
        log(
                "highlight_fadeout_completed",
                linkedMapOf(
                        "topicId" to topicId,
                        "page" to page,
                        "renderGenerationId" to renderGenerationId
                )
        )
    }

    fun readPositionLoaded(
            topicId: Long,
            lastViewedPostId: Long,
            lastViewedPage: Int
    ) {
        log(
                "read_position_loaded",
                linkedMapOf(
                        "topicId" to topicId,
                        "lastViewedPostId" to lastViewedPostId,
                        "lastViewedPage" to lastViewedPage
                )
        )
    }

    fun readPositionSaved(
            topicId: Long,
            lastViewedPostId: Long,
            lastViewedPage: Int
    ) {
        log(
                "read_position_saved",
                linkedMapOf(
                        "topicId" to topicId,
                        "lastViewedPostId" to lastViewedPostId,
                        "lastViewedPage" to lastViewedPage
                )
        )
    }

    fun readPositionSaveSuppressed(
            topicId: Long,
            postId: Long,
            reason: String,
    ) {
        log(
                "read_position_save_suppressed",
                linkedMapOf(
                        "topicId" to topicId,
                        "postId" to postId,
                        "reason" to reason,
                )
        )
    }

    fun unreadTargetLoaded(
            topicId: Long,
            firstUnreadPostId: Long?,
            unreadPage: Int?,
            unreadUrl: String?
    ) {
        log(
                "unread_target_loaded",
                linkedMapOf(
                        "topicId" to topicId,
                        "firstUnreadPostId" to firstUnreadPostId,
                        "unreadPage" to unreadPage,
                        "unreadUrl" to unreadUrl
                )
        )
    }

    private fun log(event: String, fields: Map<String, Any?>) {
        FpdaDebugLog.log("PPDA_TOPIC_HIGHLIGHT", event, fields)
    }
}
