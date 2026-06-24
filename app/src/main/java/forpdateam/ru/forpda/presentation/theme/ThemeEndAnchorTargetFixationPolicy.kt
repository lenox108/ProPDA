package forpdateam.ru.forpda.presentation.theme

/**
 * Tripwire policy for the device-log regression where opening a topic
 * produced 3+ different `endAnchorBand` events for the same page-load, with
 * 3 different anchor post ids (e.g. 143987289 → 143986756 → 143987281) and
 * one `anchorMissing`. Each `setLoadAnchorPostId` from a follow-up render
 * used to retrigger `scrollToEndAnchorOrBottomWithRetries` against the
 * LATEST id, so the viewport blinked / jumped between posts mid-load.
 *
 * The authoritative latch lives in JS as `endAnchorScrollSettledAt` (set on
 * the final retry of the end-anchor scroll, cleared by user scroll or by a
 * fresh `setLoadAction`). This Kotlin object mirrors the *decision* that the
 * JS code makes so the contract is testable without spinning up a WebView.
 *
 * The contract is intentionally small and stateless — it is consulted on
 * the JS side. A future change may also call this from `ThemeFragmentWeb`
 * to short-circuit a redundant `setLoadAnchorPostId` before it is even
 * shipped to the WebView, but for now this is a testable specification of
 * the "fixation" rule.
 *
 * Visible-for-testing accessors expose the latch arithmetic in the simplest
 * form possible so the unit tests in [ThemeEndAnchorTargetFixationPolicyTest]
 * can pin:
 *  - a fresh page-load starts with the latch CLEARED (a new end-anchor
 *    scroll must always be allowed);
 *  - once the end-anchor scroll has settled, follow-up setLoadAnchorPostId
 *    calls must be IGNORED — the original target is honoured;
 *  - a user scroll (touchstart / touchmove / wheel) clears the latch — the
 *    user is in control now and a future navigation can drive a fresh
 *    end-anchor scroll;
 *  - a fresh `setLoadAction` (new page-load) also clears the latch.
 */
object ThemeEndAnchorTargetFixationPolicy {

    /**
     * Mirror of the JS [endAnchorScrollSettledAt] state. `0` means "no
     * end-anchor scroll has settled yet (or the latch was cleared)" and
     * means a new end-anchor scroll is allowed. A non-zero value means the
     * end-anchor scroll for the current page-load has settled and follow-up
     * `setLoadAnchorPostId` calls must be ignored.
     */
    data class LatchState(
            /** Monotonic timestamp of the last successful initial end-anchor scroll. `0` if no latch. */
            val endAnchorScrollSettledAt: Long = 0L,
            /** True when the user has scrolled since the latch was armed. Clears the latch. */
            val userScrolled: Boolean = false,
    ) {
        val isLatched: Boolean
            get() = endAnchorScrollSettledAt > 0L && !userScrolled
    }

    /**
     * Latch state for a fresh page-load. The very first end-anchor scroll for
     * a new page-load must always be allowed — the latch is CLEARED.
     */
    fun onNewPageLoad(): LatchState = LatchState(endAnchorScrollSettledAt = 0L, userScrolled = false)

    /**
     * Called when the JS end-anchor scroll finally settles on the final retry.
     * Arms the latch with the current timestamp. Subsequent
     * [onSetLoadAnchorPostId] calls for this same page-load will be ignored
     * (until the user scrolls or a fresh [onNewPageLoad] is observed).
     */
    fun onEndAnchorSettled(state: LatchState, now: Long): LatchState =
            state.copy(endAnchorScrollSettledAt = if (now > 0L) now else 1L)

    /**
     * Called when the WebView reports a user-initiated scroll (touchstart /
     * touchmove / wheel / native infinite-scroll onThemeInfiniteScroll with
     * no scroll command driving it). Clears the latch — the user is in
     * control and a future navigation should be allowed to drive a fresh
     * end-anchor scroll.
     */
    fun onUserScroll(state: LatchState): LatchState =
            state.copy(userScrolled = true, endAnchorScrollSettledAt = 0L)

    /**
     * Called when a follow-up `setLoadAnchorPostId(postId)` arrives. The
     * [requestedPostId] is the post id from the most recent render / redirect
     * resolution / read-state update. If the latch is still set (no user
     * scroll since settle), the call is IGNORED — the original target is
     * honoured. If the latch is clear (fresh page-load or user scrolled), the
     * call is ALLOWED — the new target is honoured.
     *
     * @return the input post id if the call is allowed, or `null` if the
     * latch is set and the call must be ignored. The Kotlin-side caller can
     * use the null to short-circuit the `setLoadAnchorPostId` call into the
     * WebView entirely.
     */
    fun onSetLoadAnchorPostId(state: LatchState, requestedPostId: String): String? =
            if (state.isLatched) null else requestedPostId
}
