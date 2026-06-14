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
            if (blockingScrollKind == ThemeScrollCommand.Kind.INITIAL_ANCHOR) {
                INITIAL_ANCHOR_SCROLL_STUCK_REVEAL_DELAY_MS
            } else {
                DEFAULT_SCROLL_STUCK_REVEAL_DELAY_MS
            }

    fun shouldAbandonBlockingScrollForSafetyReveal(blockingScrollKind: ThemeScrollCommand.Kind?): Boolean =
            blockingScrollKind != ThemeScrollCommand.Kind.INITIAL_ANCHOR

    fun shouldReleaseAnchorGuardByTimeout(anchorGuardStartedAtMs: Long, nowMs: Long): Boolean =
            anchorGuardStartedAtMs > 0L &&
                    nowMs - anchorGuardStartedAtMs >= ANCHOR_GUARD_MAX_BLOCK_MS

    private const val DEFAULT_SCROLL_STUCK_REVEAL_DELAY_MS = 2000L
    private const val INITIAL_ANCHOR_SCROLL_STUCK_REVEAL_DELAY_MS = 3000L
}
