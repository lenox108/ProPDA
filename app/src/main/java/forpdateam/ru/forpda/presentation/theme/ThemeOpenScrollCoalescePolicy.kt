package forpdateam.ru.forpda.presentation.theme

/**
 * Prevents a second programmatic scroll or WebView reload while the first open anchor scroll
 * is still in flight (log: render_start → scrollCmdComplete → hatOverlayEnsure → render_start → jerk).
 */
internal object ThemeOpenScrollCoalescePolicy {

    fun shouldArmInitialAnchorOnPageComplete(
            traceId: String,
            initialAnchorScrollSettledTraceId: String?,
            pendingScrollKind: ThemeScrollCommand.Kind?,
            hatOverlayReinjectionTraceId: String?,
            hasUnreadTarget: Boolean,
    ): Boolean {
        if (!hasUnreadTarget) return false
        if (traceId.isBlank()) return false
        if (hatOverlayReinjectionTraceId == traceId) return false
        if (initialAnchorScrollSettledTraceId == traceId) return false
        if (pendingScrollKind == ThemeScrollCommand.Kind.INITIAL_ANCHOR) return false
        return true
    }

    fun shouldDeferHatOverlayWebViewReload(
            traceId: String,
            initialAnchorScrollSettledTraceId: String?,
            renderSettledTraceId: String?,
            hasBlockingScrollPending: Boolean,
    ): Boolean {
        if (traceId.isBlank()) return true
        if (hasBlockingScrollPending) return true
        if (initialAnchorScrollSettledTraceId == traceId) return false
        if (renderSettledTraceId == traceId) return false
        return true
    }

    fun shouldFlushDeferredHatMetadataViewUpdate(userHatOpenOverride: Boolean?): Boolean =
            ThemeHatMetadataLoadPolicy.shouldEmitViewUpdateAfterDeferredHatMetadataLoad(
                    userHatOpenOverride
            )

    fun expectsInitialAnchorScrollOnOpen(
            shouldArmInitialAnchor: Boolean,
            anchorPostId: String?,
            pageAnchor: String?,
            hasUnreadTarget: Boolean,
    ): Boolean {
        if (!hasUnreadTarget) return false
        if (!shouldArmInitialAnchor) return false
        val anchor = anchorPostId?.takeIf { it.isNotBlank() }
                ?: pageAnchor?.removePrefix("entry")?.takeIf { it.isNotBlank() }
        return !anchor.isNullOrBlank()
    }

    /**
     * Log: blankVerifyOk/webView reveal fired while INITIAL_ANCHOR retries were still running, and
     * stale native contentHeight from the previous topic triggered renderStarted reveal early.
     */
    fun isSafetyFallbackRevealReason(reason: String): Boolean =
            reason == "alphaRevealSafety" ||
                    reason == "renderWatchdogDomProbe" ||
                    reason == "renderWatchdogAlreadyComplete" ||
                    reason == "scrollStuckReveal"

    fun shouldDeferWebViewReveal(
            hasBlockingScrollPending: Boolean,
            expectedPosts: Int,
            contentHeight: Int,
            blankContentThreshold: Int,
            renderCompleteForActiveKey: Boolean,
            domPostsVerified: Boolean = false,
            expectsInitialAnchorScroll: Boolean = false,
            safetyFallbackReveal: Boolean = false,
            blockingScrollKind: ThemeScrollCommand.Kind? = null,
            hasUnreadTarget: Boolean = false,
            primaryOpenComplete: Boolean = true,
    ): Boolean {
        if (ThemePostOpenEnrichmentPolicy.shouldDeferRevealUntilPrimaryOpenComplete(primaryOpenComplete)) {
            return true
        }
        if (expectedPosts <= 0) return false
        val domReadyForReveal = domPostsVerified || contentHeight > blankContentThreshold
        if (hasBlockingScrollPending || expectsInitialAnchorScroll) {
            // Log 11_06: all-read getnewpost armed blocking INITIAL_ANCHOR but JS never reported
            // scrollCmdComplete → alpha=0 for 6s+ while DOM already had posts.
            if (!hasUnreadTarget && renderCompleteForActiveKey && domReadyForReveal) {
                return false
            }
            // Log 11_06 (1103268/1121483): renderWatchdogAlreadyComplete abandoned INITIAL_ANCHOR
            // while scroll retries were still running — user stayed at scrollY=0 on a short last page.
            if (safetyFallbackReveal &&
                    renderCompleteForActiveKey &&
                    domReadyForReveal &&
                    blockingScrollKind != ThemeScrollCommand.Kind.INITIAL_ANCHOR
            ) {
                return false
            }
            if (safetyFallbackReveal &&
                    hasUnreadTarget &&
                    renderCompleteForActiveKey &&
                    domReadyForReveal &&
                    blockingScrollKind == ThemeScrollCommand.Kind.INITIAL_ANCHOR
            ) {
                return false
            }
            return true
        }
        if (renderCompleteForActiveKey) {
            // DOM posts verified in onDomRendered — native contentHeight can stay 0 briefly on
            // deep pages (log: pageComplete content=0, dom_posts verified) and must not trap alpha=0.
            return false
        }
        if (domPostsVerified) {
            // blankVerifyOk / safety watchdog confirmed posts while IBase lifecycle was missed
            // (log: blankVerifyOk content=4456 renderComplete=false, alpha stuck at 0).
            return false
        }
        if (contentHeight <= blankContentThreshold) return true
        // Stale native contentHeight from the previous topic must not reveal before this render
        // finishes; once renderCompleteForActiveKey is true, reveal regardless of scroll retries.
        return true
    }
}
