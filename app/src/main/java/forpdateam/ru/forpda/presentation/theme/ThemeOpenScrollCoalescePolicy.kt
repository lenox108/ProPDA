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
            explicitAnchorBlocking: Boolean = false,
    ): Boolean {
        // STEP 1: explicit-post / findpost opens arm the SAME blocking INITIAL_ANCHOR path as
        // unread opens, but WITHOUT the unread side-effects (mark-read, armUnreadInitialAnchorScroll).
        // `hasUnreadTarget` still gates those side-effects downstream; `explicitAnchorBlocking`
        // only broadens the blocking-anchor arming path.
        if (!hasUnreadTarget && !explicitAnchorBlocking) return false
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
            explicitAnchorBlocking: Boolean = false,
    ): Boolean {
        // STEP 1: decouple the blocking INITIAL_ANCHOR reveal gate from `hasUnreadTarget`. An
        // explicit-post open routes its anchor through the blocking path (no unread side-effects)
        // so the reveal holds at alpha=0 until the anchor settles, instead of revealing at scrollY≈0
        // and visibly auto-scrolling while top-autoload pre-empts the anchor.
        if (!hasUnreadTarget && !explicitAnchorBlocking) return false
        if (!shouldArmInitialAnchor) return false
        val anchor = anchorPostId?.takeIf { it.isNotBlank() }
                ?: pageAnchor?.removePrefix("entry")?.takeIf { it.isNotBlank() }
        return !anchor.isNullOrBlank()
    }

    /**
     * Reveal-at-anchor gate for opens/navigations whose post positioning is owned by the
     * INSTANT JS anchor scroll rather than a blocking Kotlin command:
     *  - BACK / refresh restores that target a specific anchor post.
     *
     * STEP 1: explicit-post opens (`view=findpost&p=`) now route through the blocking
     * INITIAL_ANCHOR path via `explicitAnchorBlocking`, so they no longer need the JS-anchor
     * reveal hold. [hasUnreadTarget] is false for them, [explicitAnchorBlocking] is true, so the
     * blocking INITIAL_ANCHOR gate engages and this gate returns false.
     */
    fun expectsJsAnchorPositioningOnOpen(
            loadAction: ThemeLoadAction,
            isExplicitPostOpen: Boolean,
            isEndNavigation: Boolean,
            isRefreshRestoreToBottom: Boolean,
            hasUnreadTarget: Boolean,
            anchorPostId: String?,
            pageAnchor: String?,
            explicitAnchorBlocking: Boolean = false,
    ): Boolean {
        if (hasUnreadTarget) return false
        // STEP 1: explicit-post positioning is owned by the blocking INITIAL_ANCHOR path now.
        if (explicitAnchorBlocking) return false
        if (isEndNavigation) return false
        val landsOnAnchor = !anchorPostId?.takeIf { it.isNotBlank() }.isNullOrBlank() ||
                !pageAnchor?.removePrefix("entry")?.takeIf { it.isNotBlank() }.isNullOrBlank()
        if (!landsOnAnchor) return false
        return when (loadAction) {
            ThemeLoadAction.Back -> true
            ThemeLoadAction.Refresh -> !isRefreshRestoreToBottom
            // STEP 1: explicit-post is now blocking-anchor-routed (handled above); Normal falls through
            // only for non-explicit opens that don't land on an anchor anyway.
            ThemeLoadAction.Normal -> false
            else -> false
        }
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
            // S-01/S-02 family (logs 239158 p=120306250 open, BACK to entry143994024):
            // an explicit-post open or a BACK/refresh restore positions the page via an
            // INSTANT JS anchor scroll that is NOT a blocking Kotlin command. The reveal
            // used to fire at pageComplete/scrollSettled while scrollY≈0, so the user saw a
            // multi-second visible auto-scroll to the post. Defer the reveal for one DOM
            // confirmation so the WebView is uncovered already positioned at the anchor.
            // Bounded: it only holds until the anchor scroll has run (signalled by the
            // safety-fallback watchdog reveal reasons), never indefinitely.
            expectsJsAnchorScroll: Boolean = false,
            jsAnchorScrollSettled: Boolean = false,
    ): Boolean {
        if (ThemePostOpenEnrichmentPolicy.shouldDeferRevealUntilPrimaryOpenComplete(primaryOpenComplete)) {
            return true
        }
        if (expectedPosts <= 0) return false
        val domReadyForReveal = domPostsVerified || contentHeight > blankContentThreshold
        if (expectsJsAnchorScroll &&
                !hasBlockingScrollPending &&
                !expectsInitialAnchorScroll &&
                !jsAnchorScrollSettled &&
                !safetyFallbackReveal &&
                renderCompleteForActiveKey &&
                domReadyForReveal
        ) {
            // Hold a single beat: the instant JS anchor scroll has not confirmed it
            // positioned the viewport yet. A safety-fallback reveal reason or an
            // explicit settle signal releases this (handled by the branches below /
            // the caller's watchdog), so it can never trap alpha=0.
            return true
        }
        if (hasBlockingScrollPending || expectsInitialAnchorScroll) {
            // Log 11_06: all-read getnewpost armed blocking INITIAL_ANCHOR but JS never reported
            // scrollCmdComplete → alpha=0 for 6s+ while DOM already had posts.
            //
            // P1 (anchor-before-reveal): DOM-ready alone is NOT enough to reveal while a blocking
            // anchor scroll is in flight — for a near-bottom anchor `domReadyForReveal` flips true
            // (contentHeight grows) while scrollY is still 0, so revealing here showed the visible
            // top→anchor travel. Hold until the JS anchor scroll has actually settled
            // ([jsAnchorScrollSettled]) or a bounded safety-fallback watchdog reveal fires
            // ([safetyFallbackReveal]); the watchdog guarantees alpha can never trap at 0.
            if (!hasUnreadTarget &&
                    renderCompleteForActiveKey &&
                    domReadyForReveal &&
                    (jsAnchorScrollSettled || safetyFallbackReveal)
            ) {
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
