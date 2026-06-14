package forpdateam.ru.forpda.presentation.theme

/**
 * Mirrors hybrid-scroll gating in [theme.js] while unread [INITIAL_ANCHOR] scroll is pending.
 */
object ThemeUnreadInitialAnchorPolicy {

    const val HYBRID_TOP_SUPPRESS_MS = 3200L
    const val HYBRID_BOTTOM_SUPPRESS_MS = 1800L

    fun shouldAllowTopAutoloadDespiteEndScrollPending(isUnderfilledInitialPage: Boolean): Boolean =
            isUnderfilledInitialPage

    fun shouldDeferHybridTopAutoload(
            unreadAnchorPending: Boolean,
            userScrolled: Boolean,
            nowMs: Long,
            initialTopSuppressedUntilMs: Long,
            scrollTop: Int,
            threshold: Int,
            isUnderfilledInitialPage: Boolean,
            hasInitialAnchorTarget: Boolean = false,
    ): Boolean {
        if (userScrolled) return false
        if (unreadAnchorPending) return true
        if (hasInitialAnchorTarget && nowMs < initialTopSuppressedUntilMs) return true
        return nowMs < initialTopSuppressedUntilMs &&
                scrollTop <= threshold &&
                !isUnderfilledInitialPage
    }
}
