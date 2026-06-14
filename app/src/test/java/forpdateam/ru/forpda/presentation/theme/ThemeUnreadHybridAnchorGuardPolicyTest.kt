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
