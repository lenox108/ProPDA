package forpdateam.ru.forpda.presentation.theme

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeOpenScrollCoalescePolicyTest {

    @Test
    fun `skips initial anchor when already settled for trace`() {
        assertFalse(
                ThemeOpenScrollCoalescePolicy.shouldArmInitialAnchorOnPageComplete(
                        traceId = "abc",
                        initialAnchorScrollSettledTraceId = "abc",
                        pendingScrollKind = null,
                        hatOverlayReinjectionTraceId = null,
                        hasUnreadTarget = true,
                )
        )
    }

    @Test
    fun `skips initial anchor during hat overlay reinjection render`() {
        assertFalse(
                ThemeOpenScrollCoalescePolicy.shouldArmInitialAnchorOnPageComplete(
                        traceId = "abc",
                        initialAnchorScrollSettledTraceId = null,
                        pendingScrollKind = null,
                        hatOverlayReinjectionTraceId = "abc",
                        hasUnreadTarget = true,
                )
        )
    }

    @Test
    fun `skips initial anchor when command already pending`() {
        assertFalse(
                ThemeOpenScrollCoalescePolicy.shouldArmInitialAnchorOnPageComplete(
                        traceId = "abc",
                        initialAnchorScrollSettledTraceId = null,
                        pendingScrollKind = ThemeScrollCommand.Kind.INITIAL_ANCHOR,
                        hatOverlayReinjectionTraceId = null,
                        hasUnreadTarget = true,
                )
        )
    }

    @Test
    fun `skips blocking initial anchor when topic has no unread target log1121483`() {
        assertFalse(
                ThemeOpenScrollCoalescePolicy.shouldArmInitialAnchorOnPageComplete(
                        traceId = "abc",
                        initialAnchorScrollSettledTraceId = null,
                        pendingScrollKind = null,
                        hatOverlayReinjectionTraceId = null,
                        hasUnreadTarget = false,
                )
        )
    }

    @Test
    fun `arms initial anchor on first page complete when unread`() {
        assertTrue(
                ThemeOpenScrollCoalescePolicy.shouldArmInitialAnchorOnPageComplete(
                        traceId = "abc",
                        initialAnchorScrollSettledTraceId = null,
                        pendingScrollKind = null,
                        hatOverlayReinjectionTraceId = null,
                        hasUnreadTarget = true,
                )
        )
    }

    @Test
    fun `defers hat overlay reload until render settled and scroll idle`() {
        assertTrue(
                ThemeOpenScrollCoalescePolicy.shouldDeferHatOverlayWebViewReload(
                        traceId = "abc",
                        initialAnchorScrollSettledTraceId = null,
                        renderSettledTraceId = null,
                        hasBlockingScrollPending = false,
                )
        )
        assertTrue(
                ThemeOpenScrollCoalescePolicy.shouldDeferHatOverlayWebViewReload(
                        traceId = "abc",
                        initialAnchorScrollSettledTraceId = null,
                        renderSettledTraceId = "abc",
                        hasBlockingScrollPending = true,
                )
        )
        assertFalse(
                ThemeOpenScrollCoalescePolicy.shouldDeferHatOverlayWebViewReload(
                        traceId = "abc",
                        initialAnchorScrollSettledTraceId = "abc",
                        renderSettledTraceId = null,
                        hasBlockingScrollPending = false,
                )
        )
        assertFalse(
                ThemeOpenScrollCoalescePolicy.shouldDeferHatOverlayWebViewReload(
                        traceId = "abc",
                        initialAnchorScrollSettledTraceId = null,
                        renderSettledTraceId = "abc",
                        hasBlockingScrollPending = false,
                )
        )
    }

    @Test
    fun `deferred hat metadata view update requires explicit hat open`() {
        assertFalse(ThemeOpenScrollCoalescePolicy.shouldFlushDeferredHatMetadataViewUpdate(null))
        assertTrue(ThemeOpenScrollCoalescePolicy.shouldFlushDeferredHatMetadataViewUpdate(true))
    }

    @Test
    fun `defers webView reveal while blocking scroll pending even after render completes`() {
        assertTrue(
                ThemeOpenScrollCoalescePolicy.shouldDeferWebViewReveal(
                        hasBlockingScrollPending = true,
                        expectedPosts = 16,
                        contentHeight = 5000,
                        blankContentThreshold = 4,
                        renderCompleteForActiveKey = true,
                        hasUnreadTarget = true,
                )
        )
    }

    @Test
    fun `safety fallback reveals when blocking scroll stuck but dom and render complete`() {
        assertFalse(
                ThemeOpenScrollCoalescePolicy.shouldDeferWebViewReveal(
                        hasBlockingScrollPending = true,
                        expectedPosts = 21,
                        contentHeight = 6676,
                        blankContentThreshold = 4,
                        renderCompleteForActiveKey = true,
                        domPostsVerified = true,
                        safetyFallbackReveal = true,
                )
        )
    }

    @Test
    fun `safety fallback reveals when initial anchor stuck but unread dom verified`() {
        assertFalse(
                ThemeOpenScrollCoalescePolicy.shouldDeferWebViewReveal(
                        hasBlockingScrollPending = true,
                        expectedPosts = 9,
                        contentHeight = 6676,
                        blankContentThreshold = 4,
                        renderCompleteForActiveKey = true,
                        domPostsVerified = true,
                        safetyFallbackReveal = true,
                        blockingScrollKind = ThemeScrollCommand.Kind.INITIAL_ANCHOR,
                        hasUnreadTarget = true,
                )
        )
    }

    @Test
    fun `P1 anchor-before-reveal defers all-read blocking initial anchor until settle or fallback`() {
        // P1: a non-unread blocking INITIAL_ANCHOR (explicit-post / read resume / first-page near a
        // bottom anchor) must NOT reveal merely because the DOM is ready — `domReadyForReveal` flips
        // true while scrollY is still 0, so revealing here showed the visible top→anchor travel.
        // Hold until the JS anchor scroll settles or the bounded fallback fires.
        assertTrue(
                "must defer while blocking anchor scroll is still in flight (no settle/fallback)",
                ThemeOpenScrollCoalescePolicy.shouldDeferWebViewReveal(
                        hasBlockingScrollPending = true,
                        expectedPosts = 14,
                        contentHeight = 3794,
                        blankContentThreshold = 4,
                        renderCompleteForActiveKey = true,
                        domPostsVerified = true,
                        blockingScrollKind = ThemeScrollCommand.Kind.INITIAL_ANCHOR,
                        hasUnreadTarget = false,
                )
        )
        // Reveals once the JS anchor scroll reports it settled (instant, already positioned).
        assertFalse(
                "reveals after the JS anchor scroll settled",
                ThemeOpenScrollCoalescePolicy.shouldDeferWebViewReveal(
                        hasBlockingScrollPending = true,
                        expectedPosts = 14,
                        contentHeight = 3794,
                        blankContentThreshold = 4,
                        renderCompleteForActiveKey = true,
                        domPostsVerified = true,
                        blockingScrollKind = ThemeScrollCommand.Kind.INITIAL_ANCHOR,
                        hasUnreadTarget = false,
                        jsAnchorScrollSettled = true,
                )
        )
        // Bounded fallback still guarantees a reveal so alpha can never trap at 0 (log1121483).
        assertFalse(
                "bounded safety fallback still reveals so alpha never traps",
                ThemeOpenScrollCoalescePolicy.shouldDeferWebViewReveal(
                        hasBlockingScrollPending = true,
                        expectedPosts = 14,
                        contentHeight = 3794,
                        blankContentThreshold = 4,
                        renderCompleteForActiveKey = true,
                        domPostsVerified = true,
                        blockingScrollKind = ThemeScrollCommand.Kind.INITIAL_ANCHOR,
                        hasUnreadTarget = false,
                        safetyFallbackReveal = true,
                )
        )
    }

    @Test
    fun `safety fallback reveals when render complete and native content height populated`() {
        assertFalse(
                ThemeOpenScrollCoalescePolicy.shouldDeferWebViewReveal(
                        hasBlockingScrollPending = true,
                        expectedPosts = 20,
                        contentHeight = 6676,
                        blankContentThreshold = 4,
                        renderCompleteForActiveKey = true,
                        domPostsVerified = false,
                        safetyFallbackReveal = true,
                )
        )
    }

    @Test
    fun `safety fallback still defers before render completes`() {
        assertTrue(
                ThemeOpenScrollCoalescePolicy.shouldDeferWebViewReveal(
                        hasBlockingScrollPending = true,
                        expectedPosts = 21,
                        contentHeight = 6676,
                        blankContentThreshold = 4,
                        renderCompleteForActiveKey = false,
                        domPostsVerified = true,
                        safetyFallbackReveal = true,
                )
        )
    }

    @Test
    fun `recognizes safety fallback reveal reasons`() {
        assertTrue(ThemeOpenScrollCoalescePolicy.isSafetyFallbackRevealReason("alphaRevealSafety"))
        assertTrue(ThemeOpenScrollCoalescePolicy.isSafetyFallbackRevealReason("scrollStuckReveal"))
        assertFalse(ThemeOpenScrollCoalescePolicy.isSafetyFallbackRevealReason("pageComplete"))
        assertFalse(ThemeOpenScrollCoalescePolicy.isSafetyFallbackRevealReason("blankVerifyOk"))
    }

    @Test
    fun `defers webView reveal while initial anchor scroll expected before lifecycle completes`() {
        assertTrue(
                ThemeOpenScrollCoalescePolicy.shouldDeferWebViewReveal(
                        hasBlockingScrollPending = false,
                        expectedPosts = 20,
                        contentHeight = 4456,
                        blankContentThreshold = 4,
                        renderCompleteForActiveKey = false,
                        domPostsVerified = true,
                        expectsInitialAnchorScroll = true,
                )
        )
    }

    @Test
    fun `expects initial anchor scroll when fresh open has server anchor`() {
        assertTrue(
                ThemeOpenScrollCoalescePolicy.expectsInitialAnchorScrollOnOpen(
                        shouldArmInitialAnchor = true,
                        anchorPostId = "143801065",
                        pageAnchor = null,
                        hasUnreadTarget = true,
                )
        )
        assertFalse(
                ThemeOpenScrollCoalescePolicy.expectsInitialAnchorScrollOnOpen(
                        shouldArmInitialAnchor = false,
                        anchorPostId = "143801065",
                        pageAnchor = null,
                        hasUnreadTarget = true,
                )
        )
        assertFalse(
                ThemeOpenScrollCoalescePolicy.expectsInitialAnchorScrollOnOpen(
                        shouldArmInitialAnchor = true,
                        anchorPostId = null,
                        pageAnchor = null,
                        hasUnreadTarget = true,
                )
        )
        assertFalse(
                ThemeOpenScrollCoalescePolicy.expectsInitialAnchorScrollOnOpen(
                        shouldArmInitialAnchor = true,
                        anchorPostId = "143805431",
                        pageAnchor = null,
                        hasUnreadTarget = false,
                )
        )
    }

    @Test
    fun `defers webView reveal while blocking scroll pending`() {
        assertTrue(
                ThemeOpenScrollCoalescePolicy.shouldDeferWebViewReveal(
                        hasBlockingScrollPending = true,
                        expectedPosts = 16,
                        contentHeight = 5000,
                        blankContentThreshold = 4,
                        renderCompleteForActiveKey = false,
                )
        )
    }

    @Test
    fun `defers webView reveal for stale native content before render completes`() {
        assertTrue(
                ThemeOpenScrollCoalescePolicy.shouldDeferWebViewReveal(
                        hasBlockingScrollPending = false,
                        expectedPosts = 16,
                        contentHeight = 752,
                        blankContentThreshold = 4,
                        renderCompleteForActiveKey = false,
                )
        )
    }

    @Test
    fun `allows webView reveal after render and scroll settle`() {
        assertFalse(
                ThemeOpenScrollCoalescePolicy.shouldDeferWebViewReveal(
                        hasBlockingScrollPending = false,
                        expectedPosts = 16,
                        contentHeight = 5000,
                        blankContentThreshold = 4,
                        renderCompleteForActiveKey = true,
                )
        )
    }

    @Test
    fun `reveals webView when render completes despite zero native contentHeight`() {
        assertFalse(
                ThemeOpenScrollCoalescePolicy.shouldDeferWebViewReveal(
                        hasBlockingScrollPending = false,
                        expectedPosts = 9,
                        contentHeight = 0,
                        blankContentThreshold = 4,
                        renderCompleteForActiveKey = true,
                )
        )
    }

    @Test
    fun `reveals hybrid page when list post count is small not cached total`() {
        assertFalse(
                ThemeOpenScrollCoalescePolicy.shouldDeferWebViewReveal(
                        hasBlockingScrollPending = false,
                        expectedPosts = 3,
                        contentHeight = 1952,
                        blankContentThreshold = 4,
                        renderCompleteForActiveKey = true,
                )
        )
        assertTrue(
                ThemeOpenScrollCoalescePolicy.shouldDeferWebViewReveal(
                        hasBlockingScrollPending = false,
                        expectedPosts = 145,
                        contentHeight = 1952,
                        blankContentThreshold = 4,
                        renderCompleteForActiveKey = false,
                )
        )
    }

    @Test
    fun `reveals webView when dom posts verified despite missed lifecycle`() {
        assertFalse(
                ThemeOpenScrollCoalescePolicy.shouldDeferWebViewReveal(
                        hasBlockingScrollPending = false,
                        expectedPosts = 13,
                        contentHeight = 4456,
                        blankContentThreshold = 4,
                        renderCompleteForActiveKey = false,
                        domPostsVerified = true,
                )
        )
    }

    @Test
    fun `defers webView reveal until primary open complete`() {
        assertTrue(
                ThemeOpenScrollCoalescePolicy.shouldDeferWebViewReveal(
                        hasBlockingScrollPending = false,
                        expectedPosts = 13,
                        contentHeight = 752,
                        blankContentThreshold = 4,
                        renderCompleteForActiveKey = true,
                        domPostsVerified = true,
                        primaryOpenComplete = false,
                )
        )
        assertFalse(
                ThemeOpenScrollCoalescePolicy.shouldDeferWebViewReveal(
                        hasBlockingScrollPending = false,
                        expectedPosts = 13,
                        contentHeight = 752,
                        blankContentThreshold = 4,
                        renderCompleteForActiveKey = true,
                        domPostsVerified = true,
                        primaryOpenComplete = true,
                )
        )
    }

    // --- Reveal-at-anchor (A/B): JS-owned anchor scroll hold -------------------------------------

    @Test
    fun `defers webView reveal for explicit-post open so JS anchor scroll lands before reveal`() {
        // Defect A: explicit-post open (239158 p=143994024). render complete + DOM verified, but the
        // INSTANT JS anchor scroll has not confirmed it positioned the viewport yet -> hold so the
        // WebView is uncovered already at the post instead of revealing at scrollY~0 then animating.
        assertTrue(
                ThemeOpenScrollCoalescePolicy.shouldDeferWebViewReveal(
                        hasBlockingScrollPending = false,
                        expectedPosts = 20,
                        contentHeight = 8000,
                        blankContentThreshold = 4,
                        renderCompleteForActiveKey = true,
                        domPostsVerified = true,
                        expectsJsAnchorScroll = true,
                        jsAnchorScrollSettled = false,
                )
        )
    }

    @Test
    fun `reveals once JS anchor scroll has settled`() {
        assertFalse(
                ThemeOpenScrollCoalescePolicy.shouldDeferWebViewReveal(
                        hasBlockingScrollPending = false,
                        expectedPosts = 20,
                        contentHeight = 8000,
                        blankContentThreshold = 4,
                        renderCompleteForActiveKey = true,
                        domPostsVerified = true,
                        expectsJsAnchorScroll = true,
                        jsAnchorScrollSettled = true,
                )
        )
    }

    @Test
    fun `JS anchor hold releases on safety fallback watchdog so alpha never traps at zero`() {
        assertFalse(
                ThemeOpenScrollCoalescePolicy.shouldDeferWebViewReveal(
                        hasBlockingScrollPending = false,
                        expectedPosts = 20,
                        contentHeight = 8000,
                        blankContentThreshold = 4,
                        renderCompleteForActiveKey = true,
                        domPostsVerified = true,
                        expectsJsAnchorScroll = true,
                        jsAnchorScrollSettled = false,
                        safetyFallbackReveal = true,
                )
        )
    }

    @Test
    fun `JS anchor hold does not engage before render completes (no extra hold)`() {
        // Before render completes the existing gates already defer; the JS-anchor branch must not
        // change that path, and must not hold a command-less load that has no anchor.
        assertFalse(
                ThemeOpenScrollCoalescePolicy.shouldDeferWebViewReveal(
                        hasBlockingScrollPending = false,
                        expectedPosts = 20,
                        contentHeight = 8000,
                        blankContentThreshold = 4,
                        renderCompleteForActiveKey = true,
                        domPostsVerified = true,
                        expectsJsAnchorScroll = false,
                        jsAnchorScrollSettled = false,
                )
        )
    }

    @Test
    fun `explicit post open no longer holds JS anchor gate - routes through blocking INITIAL_ANCHOR`() {
        // STEP 1: explicit-post opens now arm the blocking INITIAL_ANCHOR path (via
        // `explicitAnchorBlocking`), so the JS-anchor reveal hold must NOT engage. The blocking
        // INITIAL_ANCHOR gate handles the reveal-at-anchor itself.
        assertFalse(
                ThemeOpenScrollCoalescePolicy.expectsJsAnchorPositioningOnOpen(
                        loadAction = ThemeLoadAction.Normal,
                        isExplicitPostOpen = true,
                        isEndNavigation = false,
                        isRefreshRestoreToBottom = false,
                        hasUnreadTarget = false,
                        anchorPostId = "143994024",
                        pageAnchor = null,
                        explicitAnchorBlocking = true,
                )
        )
        // Without the explicitAnchorBlocking flag (legacy callers), the old behavior is preserved
        // so a Normal non-explicit open still does not engage the JS-anchor hold.
        assertFalse(
                ThemeOpenScrollCoalescePolicy.expectsJsAnchorPositioningOnOpen(
                        loadAction = ThemeLoadAction.Normal,
                        isExplicitPostOpen = true,
                        isEndNavigation = false,
                        isRefreshRestoreToBottom = false,
                        hasUnreadTarget = false,
                        anchorPostId = "143994024",
                        pageAnchor = null,
                )
        )
    }

    @Test
    fun `explicit post open arms blocking INITIAL_ANCHOR when anchor present`() {
        // STEP 1 regression: an explicit-post open arms the SAME blocking INITIAL_ANCHOR path as an
        // unread open, without hasUnreadTarget. This is what routes the anchor through the event-based
        // guard `shouldBlockHybridUntilInitialAnchorSettled` so top-autoload cannot pre-empt it.
        assertTrue(
                ThemeOpenScrollCoalescePolicy.shouldArmInitialAnchorOnPageComplete(
                        traceId = "abc",
                        initialAnchorScrollSettledTraceId = null,
                        pendingScrollKind = null,
                        hatOverlayReinjectionTraceId = null,
                        hasUnreadTarget = false,
                        explicitAnchorBlocking = true,
                )
        )
        assertTrue(
                ThemeOpenScrollCoalescePolicy.expectsInitialAnchorScrollOnOpen(
                        shouldArmInitialAnchor = true,
                        anchorPostId = "143994024",
                        pageAnchor = null,
                        hasUnreadTarget = false,
                        explicitAnchorBlocking = true,
                )
        )
        // No anchor → no blocking arm (nothing to land on).
        assertFalse(
                ThemeOpenScrollCoalescePolicy.expectsInitialAnchorScrollOnOpen(
                        shouldArmInitialAnchor = true,
                        anchorPostId = null,
                        pageAnchor = null,
                        hasUnreadTarget = false,
                        explicitAnchorBlocking = true,
                )
        )
    }

    @Test
    fun `expects JS anchor positioning on back restore to a post`() {
        assertTrue(
                ThemeOpenScrollCoalescePolicy.expectsJsAnchorPositioningOnOpen(
                        loadAction = ThemeLoadAction.Back,
                        isExplicitPostOpen = false,
                        isEndNavigation = false,
                        isRefreshRestoreToBottom = false,
                        hasUnreadTarget = false,
                        anchorPostId = null,
                        pageAnchor = "entry143876380",
                )
        )
    }

    @Test
    fun `does not expect JS anchor positioning without an anchor`() {
        assertFalse(
                ThemeOpenScrollCoalescePolicy.expectsJsAnchorPositioningOnOpen(
                        loadAction = ThemeLoadAction.Back,
                        isExplicitPostOpen = false,
                        isEndNavigation = false,
                        isRefreshRestoreToBottom = false,
                        hasUnreadTarget = false,
                        anchorPostId = null,
                        pageAnchor = null,
                )
        )
    }

    @Test
    fun `does not expect JS anchor positioning when unread target owns INITIAL_ANCHOR`() {
        // Unread opens keep their own blocking INITIAL_ANCHOR gate; the JS-anchor hold must defer to it.
        assertFalse(
                ThemeOpenScrollCoalescePolicy.expectsJsAnchorPositioningOnOpen(
                        loadAction = ThemeLoadAction.Normal,
                        isExplicitPostOpen = true,
                        isEndNavigation = false,
                        isRefreshRestoreToBottom = false,
                        hasUnreadTarget = true,
                        anchorPostId = "143994024",
                        pageAnchor = null,
                )
        )
    }

    @Test
    fun `does not expect JS anchor positioning for end navigation or refresh-to-bottom`() {
        // End navigation and refresh-restore-to-bottom land at the page bottom, not on a post anchor.
        assertFalse(
                ThemeOpenScrollCoalescePolicy.expectsJsAnchorPositioningOnOpen(
                        loadAction = ThemeLoadAction.Normal,
                        isExplicitPostOpen = true,
                        isEndNavigation = true,
                        isRefreshRestoreToBottom = false,
                        hasUnreadTarget = false,
                        anchorPostId = "143994024",
                        pageAnchor = null,
                )
        )
        assertFalse(
                ThemeOpenScrollCoalescePolicy.expectsJsAnchorPositioningOnOpen(
                        loadAction = ThemeLoadAction.Refresh,
                        isExplicitPostOpen = false,
                        isEndNavigation = false,
                        isRefreshRestoreToBottom = true,
                        hasUnreadTarget = false,
                        anchorPostId = "143994024",
                        pageAnchor = null,
                )
        )
    }

    @Test
    fun `normal open without explicit-post flag does not hold for JS anchor`() {
        assertFalse(
                ThemeOpenScrollCoalescePolicy.expectsJsAnchorPositioningOnOpen(
                        loadAction = ThemeLoadAction.Normal,
                        isExplicitPostOpen = false,
                        isEndNavigation = false,
                        isRefreshRestoreToBottom = false,
                        hasUnreadTarget = false,
                        anchorPostId = "143994024",
                        pageAnchor = null,
                )
        )
    }
}
