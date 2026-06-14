package forpdateam.ru.forpda.presentation.theme

/**
 * Resolves the post id passed to [setLoadAnchorPostId] on first DOM render.
 */
object ThemeDomLoadAnchorPolicy {

    /**
     * When saved scroll is blocked on a fresh open, still pass the server redirect anchor so hybrid
     * bootstrap can apply the 3200ms top suppress. Only unread opens arm the blocking hybrid guard.
     */
    fun resolveBlockedScrollRestoreAnchor(
            anchorPostId: String?,
            pageAnchor: String?,
    ): String = when {
        !anchorPostId.isNullOrBlank() -> anchorPostId
        !pageAnchor.isNullOrBlank() -> pageAnchor
        else -> ""
    }

    /**
     * Log 11_06-679: read-row getnewpost passed last-read anchor to JS, which always armed
     * [armUnreadInitialAnchorScroll] without Kotlin INITIAL_ANCHOR — DOM scroll was skipped and
     * the viewport stayed at the first post (143733850) instead of 143805431.
     */
    fun shouldArmUnreadHybridGuard(hasUnreadTarget: Boolean): Boolean = hasUnreadTarget

    /**
     * Post-reveal JS scroll after [resetThemeRuntimeState]. Runs for read-topic resume and as a
     * non-blocking backup when unread [INITIAL_ANCHOR] fails (log 033: scrollY=0 on last page).
     */
    fun shouldScheduleSoftAnchorScroll(
            hasUnreadTarget: Boolean,
            loadAction: ThemeLoadAction,
            isEndNavigation: Boolean,
            isRefreshNavigation: Boolean,
            isPostedPageScroll: Boolean,
            anchorPostId: String?,
    ): Boolean {
        if (loadAction != ThemeLoadAction.Normal) return false
        if (isEndNavigation || isRefreshNavigation || isPostedPageScroll) return false
        return !anchorPostId.isNullOrBlank()
    }

    fun normalizeAnchorPostId(anchor: String?): String? =
            anchor?.trim()
                    ?.removePrefix("entry")
                    ?.takeIf { it.isNotBlank() }

    /** Read-topic opens must never arm the unread hybrid top gate from a server anchor alone. */
    fun shouldSuppressHybridTopForInitialAnchor(hasUnreadTarget: Boolean): Boolean = hasUnreadTarget

    /**
     * Ambiguous all-read bottom redirect (last page, no unread marker): suppress automatic HYBRID
     * top bootstrap on open; manual scroll-up still loads neighbor pages.
     */
    fun shouldSuppressAmbiguousAllReadInitialTopBootstrap(
            ambiguousBottomRedirect: Boolean,
            hasUnreadTarget: Boolean,
    ): Boolean = ambiguousBottomRedirect && !hasUnreadTarget
}
