package forpdateam.ru.forpda.presentation.theme

/**
 * Blocks hybrid infinite-scroll page inserts until unread [INITIAL_ANCHOR] scroll settles.
 */
object ThemeUnreadHybridAnchorGuardPolicy {

    const val LOG_TAG = "FPDA_THEME_ANCHOR_GUARD"
    const val ANCHOR_GUARD_MAX_BLOCK_MS = 3200L

    fun shouldBlockHybridUntilInitialAnchorSettled(
            traceId: String,
            initialAnchorScrollSettledTraceId: String?,
            pendingScrollKind: ThemeScrollCommand.Kind?,
            expectsInitialAnchorScroll: Boolean,
    ): Boolean {
        if (pendingScrollKind == ThemeScrollCommand.Kind.INITIAL_ANCHOR) return true
        if (expectsInitialAnchorScroll && initialAnchorScrollSettledTraceId != traceId) return true
        return false
    }

    fun scrollStuckRevealDelayMs(blockingScrollKind: ThemeScrollCommand.Kind?): Long =
            when (blockingScrollKind) {
                ThemeScrollCommand.Kind.INITIAL_ANCHOR -> INITIAL_ANCHOR_SCROLL_STUCK_REVEAL_DELAY_MS
                // A BACK/refresh REFRESH_RESTORE runs the JS BACK-anchor settle loop, which polls until
                // the target post is in the DOM and the HYBRID page is laid out (hard deadline 4000ms,
                // theme.js BACK_ANCHOR_SETTLE_DEADLINE_MS). The old 2000ms stuck-reveal abandoned the
                // command and revealed at the page top BEFORE the settle could land on the post (device
                // logs 26_06-16-50 … 26_06-17-31). Give the watchdog a longer leash than the settle
                // deadline so the restore actually completes on the post; the settle's own deadline
                // still guarantees the reveal is never blocked indefinitely.
                ThemeScrollCommand.Kind.REFRESH_RESTORE -> REFRESH_RESTORE_SCROLL_STUCK_REVEAL_DELAY_MS
                else -> DEFAULT_SCROLL_STUCK_REVEAL_DELAY_MS
            }

    fun shouldAbandonBlockingScrollForSafetyReveal(blockingScrollKind: ThemeScrollCommand.Kind?): Boolean =
            blockingScrollKind != ThemeScrollCommand.Kind.INITIAL_ANCHOR

    fun shouldReleaseAnchorGuardByTimeout(anchorGuardStartedAtMs: Long, nowMs: Long): Boolean =
            anchorGuardStartedAtMs > 0L &&
                    nowMs - anchorGuardStartedAtMs >= ANCHOR_GUARD_MAX_BLOCK_MS

    /**
     * S1 (residual visible scroll, log 25_06-16-26): on a HYBRID mid-topic page the native
     * unread-anchor fallback scrolls the first-unread post near the content top and reveals. The
     * post sitting near the top edge would immediately re-trigger native `top` infinite-scroll
     * (`requestInfinitePage("top")`), which inserts the previous page ABOVE and visibly ramps the
     * scroll. The Kotlin anchor guard is released by that same fallback, so it can no longer gate
     * the native edge. This is a SEPARATE short-lived top-autoload suppression armed by the
     * fallback reveal: top inserts are held for [TOP_AUTOLOAD_SUPPRESS_AFTER_REVEAL_MS] so the
     * revealed anchor stays put; bottom inserts and genuine user scrolling are unaffected.
     */
    fun shouldSuppressTopAutoloadAfterUnreadAnchorReveal(
            suppressTopAutoloadUntilMs: Long,
            nowMs: Long,
    ): Boolean = suppressTopAutoloadUntilMs > 0L && nowMs < suppressTopAutoloadUntilMs

    /**
     * Explicit-post / findpost opens (`view=findpost&p=`) position the page via the INSTANT JS
     * anchor scroll, not a blocking [INITIAL_ANCHOR] command, so [shouldBlockHybridUntilInitialAnchorSettled]
     * never engages. On a deep page the freshly-rendered WebView momentarily sits near the content
     * top (scrollY≈0) before the JS anchor scroll moves the viewport down to the post. During that
     * window the native top edge would fire `requestInfinitePage("top")`, prepend the previous page
     * and reflow/auto-scroll the viewport off the anchored post — the user lands on a random post and
     * the source back-restore position is corrupted (log 1119715 p=143976029: native edge top scrollY=18
     * → st=1040 autoload → highlight_arm_skipped reason=generation_done). Hold the native top autoload
     * until the JS anchor scroll has positioned the viewport ([jsAnchorRevealReleased]); bottom inserts
     * and genuine user scrolling are unaffected.
     */
    fun shouldSuppressTopAutoloadDuringJsAnchorPositioning(
            expectsJsAnchorPositioning: Boolean,
            jsAnchorRevealReleased: Boolean,
    ): Boolean = expectsJsAnchorPositioning && !jsAnchorRevealReleased

    const val TOP_AUTOLOAD_SUPPRESS_AFTER_REVEAL_MS = 1200L

    private const val DEFAULT_SCROLL_STUCK_REVEAL_DELAY_MS = 2000L
    private const val INITIAL_ANCHOR_SCROLL_STUCK_REVEAL_DELAY_MS = 3000L
    /** Must exceed theme.js BACK_ANCHOR_SETTLE_DEADLINE_MS (4000) so the settle can land first. */
    private const val REFRESH_RESTORE_SCROLL_STUCK_REVEAL_DELAY_MS = 4600L
}
