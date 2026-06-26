package forpdateam.ru.forpda.presentation.theme

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeUnreadHybridAnchorGuardPolicyTest {

    @Test
    fun blocksHybridWhileInitialAnchorPending() {
        assertTrue(
                ThemeUnreadHybridAnchorGuardPolicy.shouldBlockHybridUntilInitialAnchorSettled(
                        traceId = "abc",
                        initialAnchorScrollSettledTraceId = null,
                        pendingScrollKind = ThemeScrollCommand.Kind.INITIAL_ANCHOR,
                        expectsInitialAnchorScroll = true,
                )
        )
    }

    @Test
    fun blocksHybridForExplicitAnchorBlockingPathEvenWithoutUnread() {
        // STEP 1 regression: the blocking INITIAL_ANCHOR path is armed for explicit-post opens
        // (via `explicitAnchorBlocking`) WITHOUT hasUnreadTarget. The policy already fires on
        // `pendingScrollKind == INITIAL_ANCHOR` regardless of unread, so top-autoload is held
        // until the anchor settles — no timer, no unread side-effects.
        assertTrue(
                "pending INITIAL_ANCHOR blocks hybrid even when expectsInitialAnchorScroll is false",
                ThemeUnreadHybridAnchorGuardPolicy.shouldBlockHybridUntilInitialAnchorSettled(
                        traceId = "abc",
                        initialAnchorScrollSettledTraceId = null,
                        pendingScrollKind = ThemeScrollCommand.Kind.INITIAL_ANCHOR,
                        expectsInitialAnchorScroll = false,
                )
        )
        assertTrue(
                "expectsInitialAnchorScroll blocks until settled (covers the post-pageComplete window)",
                ThemeUnreadHybridAnchorGuardPolicy.shouldBlockHybridUntilInitialAnchorSettled(
                        traceId = "abc",
                        initialAnchorScrollSettledTraceId = null,
                        pendingScrollKind = null,
                        expectsInitialAnchorScroll = true,
                )
        )
        // Releases once the blocking anchor has settled for this trace.
        assertFalse(
                ThemeUnreadHybridAnchorGuardPolicy.shouldBlockHybridUntilInitialAnchorSettled(
                        traceId = "abc",
                        initialAnchorScrollSettledTraceId = "abc",
                        pendingScrollKind = null,
                        expectsInitialAnchorScroll = true,
                )
        )
    }

    @Test
    fun doesNotAbandonInitialAnchorForSafetyReveal() {
        assertFalse(
                ThemeUnreadHybridAnchorGuardPolicy.shouldAbandonBlockingScrollForSafetyReveal(
                        ThemeScrollCommand.Kind.INITIAL_ANCHOR
                )
        )
        assertTrue(
                ThemeUnreadHybridAnchorGuardPolicy.shouldAbandonBlockingScrollForSafetyReveal(
                        ThemeScrollCommand.Kind.BOTTOM
                )
        )
    }

    @Test
    fun suppressesTopAutoloadWithinPostRevealWindow() {
        // S1: after the native unread-anchor fallback reveals, native `top` infinite-scroll must be
        // held briefly so the just-positioned anchor (near content top on a HYBRID mid-topic page)
        // does not get pushed down by an auto-inserted previous page (the visible scroll ramp).
        val armedAt = 10_000L
        val until = armedAt + ThemeUnreadHybridAnchorGuardPolicy.TOP_AUTOLOAD_SUPPRESS_AFTER_REVEAL_MS
        assertTrue(
                "top autoload suppressed at reveal moment",
                ThemeUnreadHybridAnchorGuardPolicy.shouldSuppressTopAutoloadAfterUnreadAnchorReveal(
                        suppressTopAutoloadUntilMs = until,
                        nowMs = armedAt,
                )
        )
        assertTrue(
                "top autoload still suppressed mid-window",
                ThemeUnreadHybridAnchorGuardPolicy.shouldSuppressTopAutoloadAfterUnreadAnchorReveal(
                        suppressTopAutoloadUntilMs = until,
                        nowMs = until - 1,
                )
        )
    }

    @Test
    fun topAutoloadSuppressionIsBoundedAndOptIn() {
        val armedAt = 10_000L
        val until = armedAt + ThemeUnreadHybridAnchorGuardPolicy.TOP_AUTOLOAD_SUPPRESS_AFTER_REVEAL_MS
        // Bounded: once the window elapses, top autoload resumes (never permanently disables hybrid).
        assertFalse(
                ThemeUnreadHybridAnchorGuardPolicy.shouldSuppressTopAutoloadAfterUnreadAnchorReveal(
                        suppressTopAutoloadUntilMs = until,
                        nowMs = until,
                )
        )
        // Opt-in: an un-armed window (0) never suppresses, so non-unread / non-fallback opens are
        // completely unaffected.
        assertFalse(
                ThemeUnreadHybridAnchorGuardPolicy.shouldSuppressTopAutoloadAfterUnreadAnchorReveal(
                        suppressTopAutoloadUntilMs = 0L,
                        nowMs = 5_000L,
                )
        )
    }

    @Test
    fun suppressesNativeTopAutoloadWhileExplicitPostJsAnchorIsPositioning() {
        // Regression (log 1119715 p=143976029): a findpost / explicit-post open lands on a deep page
        // and positions via the INSTANT JS anchor scroll. Before that scroll moves the viewport off
        // the content top, the native top edge fired `requestInfinitePage("top")`, prepended st=1040
        // and auto-scrolled the viewport away from the anchored post (highlight_arm_skipped
        // reason=generation_done → user landed on a random post + corrupted back-restore). The native
        // top autoload must be held until the JS anchor scroll has positioned the viewport.
        assertTrue(
                "top autoload suppressed while JS anchor still positioning",
                ThemeUnreadHybridAnchorGuardPolicy.shouldSuppressTopAutoloadDuringJsAnchorPositioning(
                        expectsJsAnchorPositioning = true,
                        jsAnchorRevealReleased = false,
                )
        )
    }

    @Test
    fun allowsNativeTopAutoloadOnceJsAnchorPositioned() {
        // Bounded: once the JS anchor scroll has positioned the viewport (reveal released), the guard
        // lifts so a genuine user scroll-to-top can still autoload the previous page.
        assertFalse(
                ThemeUnreadHybridAnchorGuardPolicy.shouldSuppressTopAutoloadDuringJsAnchorPositioning(
                        expectsJsAnchorPositioning = true,
                        jsAnchorRevealReleased = true,
                )
        )
        // Opt-in: non explicit-post / non JS-anchor opens (e.g. plain page-1 open, unread path) are
        // completely unaffected.
        assertFalse(
                ThemeUnreadHybridAnchorGuardPolicy.shouldSuppressTopAutoloadDuringJsAnchorPositioning(
                        expectsJsAnchorPositioning = false,
                        jsAnchorRevealReleased = false,
                )
        )
    }

    @Test
    fun releasesAnchorGuardAfterMaxBlockWindow() {
        val startedAt = 1_000L
        assertFalse(
                ThemeUnreadHybridAnchorGuardPolicy.shouldReleaseAnchorGuardByTimeout(
                        anchorGuardStartedAtMs = startedAt,
                        nowMs = startedAt + ThemeUnreadHybridAnchorGuardPolicy.ANCHOR_GUARD_MAX_BLOCK_MS - 1,
                )
        )
        assertTrue(
                ThemeUnreadHybridAnchorGuardPolicy.shouldReleaseAnchorGuardByTimeout(
                        anchorGuardStartedAtMs = startedAt,
                        nowMs = startedAt + ThemeUnreadHybridAnchorGuardPolicy.ANCHOR_GUARD_MAX_BLOCK_MS,
                )
        )
    }
}
