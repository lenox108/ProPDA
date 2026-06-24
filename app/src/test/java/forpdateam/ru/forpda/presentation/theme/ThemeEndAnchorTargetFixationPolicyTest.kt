package forpdateam.ru.forpda.presentation.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tripwire for the device-log regression where opening a topic produced
 * 3+ different `endAnchorBand` events for the same page-load, with 3
 * different anchor post ids (e.g. 143987289 → 143986756 → 143987281) and
 * one `anchorMissing`. Each `setLoadAnchorPostId` from a follow-up render
 * used to retrigger `scrollToEndAnchorOrBottomWithRetries` against the
 * LATEST id, so the viewport blinked / jumped between posts mid-load.
 *
 * The authoritative latch lives in JS as `endAnchorScrollSettledAt`
 * (set on the final retry of the end-anchor scroll, cleared by user
 * scroll or by a fresh `setLoadAction`). This test pins the *decision*
 * on the Kotlin side so the contract is testable without a WebView.
 */
class ThemeEndAnchorTargetFixationPolicyTest {

    @Test
    fun freshPageLoad_startsWithLatchCleared() {
        val state = ThemeEndAnchorTargetFixationPolicy.onNewPageLoad()
        assertFalse(
                "A fresh page-load must start with the latch cleared so the " +
                        "very first end-anchor scroll is allowed.",
                state.isLatched,
        )
        // First setLoadAnchorPostId is allowed.
        assertEquals(
                "143987289",
                ThemeEndAnchorTargetFixationPolicy.onSetLoadAnchorPostId(state, "143987289"),
        )
    }

    @Test
    fun endAnchorSettles_latchesFollowupPostIds() {
        var state = ThemeEndAnchorTargetFixationPolicy.onNewPageLoad()
        state = ThemeEndAnchorTargetFixationPolicy.onEndAnchorSettled(state, now = 1000L)
        assertTrue(
                "Latch must be set after the end-anchor scroll settles.",
                state.isLatched,
        )
        // First follow-up setLoadAnchorPostId from a follow-up render — IGNORED.
        assertNull(
                "Follow-up setLoadAnchorPostId must be IGNORED once the latch " +
                        "is set (the original target 143987289 is honoured, not the " +
                        "follow-up 143986756 from the second render).",
                ThemeEndAnchorTargetFixationPolicy.onSetLoadAnchorPostId(state, "143986756"),
        )
        // Second follow-up — still IGNORED.
        assertNull(
                "Latch must hold across MULTIPLE follow-up setLoadAnchorPostId " +
                        "calls (this is the 3-endAnchorBand regression).",
                ThemeEndAnchorTargetFixationPolicy.onSetLoadAnchorPostId(state, "143987281"),
        )
    }

    @Test
    fun userScroll_clearsLatchAndAllowsFreshTarget() {
        var state = ThemeEndAnchorTargetFixationPolicy.onNewPageLoad()
        state = ThemeEndAnchorTargetFixationPolicy.onEndAnchorSettled(state, now = 1000L)
        assertTrue(state.isLatched)
        // User touches / scrolls the topic — the latch is cleared.
        state = ThemeEndAnchorTargetFixationPolicy.onUserScroll(state)
        assertFalse(
                "Latch must be cleared once the user scrolls (the user is in " +
                        "control now; a future navigation should be allowed to " +
                        "drive a fresh end-anchor scroll).",
                state.isLatched,
        )
        // Now a new setLoadAnchorPostId (e.g. a link the user tapped in the
        // already-loaded content) is ALLOWED.
        assertEquals(
                "143887940",
                ThemeEndAnchorTargetFixationPolicy.onSetLoadAnchorPostId(state, "143887940"),
        )
    }

    @Test
    fun newPageLoadAfterSettled_resetsLatch() {
        var state = ThemeEndAnchorTargetFixationPolicy.onNewPageLoad()
        state = ThemeEndAnchorTargetFixationPolicy.onEndAnchorSettled(state, now = 1000L)
        // User navigates to a different topic → setLoadAction("NORMAL") →
        // JS calls endAnchorScrollSettledAt = 0; on the Kotlin side this is
        // modelled as onNewPageLoad().
        state = ThemeEndAnchorTargetFixationPolicy.onNewPageLoad()
        assertFalse(
                "A fresh page-load must reset the latch even if a previous " +
                        "topic had settled it (otherwise the new topic would " +
                        "inherit the old end-anchor scroll, which is wrong).",
                state.isLatched,
        )
        // The new topic's setLoadAnchorPostId is allowed.
        assertEquals(
                "143990000",
                ThemeEndAnchorTargetFixationPolicy.onSetLoadAnchorPostId(state, "143990000"),
        )
    }

    @Test
    fun latchIsNotLatched_whenSettledTimestampIsZero() {
        // Defensive: an `onEndAnchorSettled` call with a 0 timestamp must
        // still arm the latch (Date.now() in the JS world is always > 0,
        // but the policy is exposed to other call-sites; the timestamp
        // 0 sentinel must not be confused with "unarmed").
        val state = ThemeEndAnchorTargetFixationPolicy
                .onEndAnchorSettled(
                        ThemeEndAnchorTargetFixationPolicy.onNewPageLoad(),
                        now = 0L,
                )
        assertTrue(
                "endAnchorSettledAt=0 from a settled-event must be normalised " +
                        "to a non-zero latch value (otherwise the policy would " +
                        "think the latch is clear and allow the follow-up " +
                        "setLoadAnchorPostId, which is the regression).",
                state.isLatched,
        )
    }
}
