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
    fun `reveals all read topic when dom verified despite blocking initial anchor log1121483`() {
        assertFalse(
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
}
