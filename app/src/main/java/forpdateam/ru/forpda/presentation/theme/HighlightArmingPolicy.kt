package forpdateam.ru.forpda.presentation.theme

/**
 * When a topic open arms an INITIAL_ANCHOR / blocking scroll, arming the
 * post-highlight before that scroll settles paints the outline on a post that
 * is still off-screen (log: last-read highlight on 143898449 while getnewpost
 * scroll keeps the viewport on 143898645). Defer native arming until scroll
 * completes, then re-apply once with [ThemeWebController.reapplyTopicHighlightAfterScrollSettled].
 */
internal object HighlightArmingPolicy {

    fun shouldDeferUntilScrollSettled(hasBlockingScrollPending: Boolean): Boolean =
            hasBlockingScrollPending

    /**
     * Each [renderThemePage] bump must allow a fresh highlight arm for the new generation.
     *
     * STEP 2: this is the per-render reset for the *dispatch/armed* bookkeeping, NOT for the
     * sticky explicit-anchor intent (see [shouldClearPendingExplicitAnchor]). The latter survives
     * generation bumps and is only cleared once JS confirms the post is in the viewport.
     */
    fun armedGenerationAfterNewRender(): Int = 0

    /**
     * STEP 2 — sticky pending ScrollIntent for an explicit-anchor open.
     *
     * Returns true when the pending explicit-anchor post id is still awaiting its first
     * settle (the blocking INITIAL_ANCHOR scroll command for it has not yet completed
     * successfully). The caller must, in that case, bypass the per-render `generation_done`
     * completion latch and re-arm the apply for the new generation. The clear criterion is
     * `ThemeWebController.onScrollCommandComplete` for the INITIAL_ANCHOR kind with success —
     * the JS side only reports completion after the `scrollToElementWithRetries` final retry
     * confirmed the anchor is near the viewport top (`isThemeAnchorNearViewportTop`), so it is
     * event/state-based, not a timer.
     *
     * `pendingPostId` is stored separately from `renderGeneration` and is NOT cleared by
     * [armedGenerationAfterNewRender] / `renderThemePage` — only when the anchor settles.
     */
    fun isPendingExplicitAnchorUnsettled(
            pendingPostId: Long,
            anchorSettled: Boolean,
    ): Boolean = pendingPostId > 0L && !anchorSettled

    /**
     * STEP 2 — clear criterion for the sticky explicit-anchor intent. Reliable, event-based:
     * the blocking INITIAL_ANCHOR scroll command reported a successful completion, which the
     * JS side only emits once the anchor is near the viewport top.
     */
    fun shouldClearPendingExplicitAnchor(
            pendingPostId: Long,
            anchorSettled: Boolean,
    ): Boolean = pendingPostId > 0L && anchorSettled

    /**
     * Log 24_06-14-15: a per-render arm guard that only checks `armedGeneration`
     * would skip the first apply for a *same* generation when the post id
     * changed across re-resolves (e.g. openSessionKind flipped from
     * AMBIGUOUS_ALL_READ to READ_RESUME, so the highlight target moved off the
     * bottom post to the realigned redirect hash). Distinguish:
     *  - same `armedGeneration` AND same `armedPostId` → truly armed, skip;
     *  - same `armedGeneration` BUT different `armedPostId` (or never armed for
     *    this post) → re-arm once for the new post id.
     *
     * Returns `true` when the caller MUST apply the highlight (and stamp the
     * post id into the armed state). Returns `false` when the highlight is
     * already armed for this exact (generation, postId) pair.
     */
    fun shouldArmForCurrentTarget(
            armedGeneration: Int,
            armedPostId: Long,
            currentGeneration: Int,
            currentPostId: Long,
    ): Boolean {
        if (currentPostId <= 0L) return false
        if (armedGeneration != currentGeneration) return true
        return armedPostId != currentPostId
    }

    /**
     * H-03 (device log 24_06-20-37): the apply MUST fire whenever the highlight
     * has not been *dispatched* for this exact (generation, postId) — regardless
     * of what the legacy `armed*` bookkeeping says. The `armed*` flags proved
     * unreliable: they were observed equal to the current generation on the very
     * first reapply, with no apply ever dispatched, which permanently suppressed
     * `PPDA_applyHighlight`. Anchoring the decision on the dispatch record makes
     * the first genuine resolve always call apply, while still being idempotent
     * for a real second reapply of the same (generation, postId).
     *
     * Returns `true` when the apply JS must be (re)dispatched.
     */
    fun shouldDispatchApplyForCurrentTarget(
            dispatchedGeneration: Int,
            dispatchedPostId: Long,
            currentGeneration: Int,
            currentPostId: Long,
    ): Boolean {
        if (currentPostId <= 0L) return false
        if (dispatchedGeneration != currentGeneration) return true
        return dispatchedPostId != currentPostId
    }

    /**
     * Double-light-up fix (device log 24_06-23-12): a highlight for a given
     * `(renderGenerationId, postId)` must light up and fade EXACTLY ONCE. The
     * `dispatched*` guard above is reset on every `renderThemePage` re-render and
     * `reapplyTopicHighlightAfterScrollSettled` re-arm, so a smart-patch
     * re-render or a reveal/scroll-settled event for the SAME generation+post
     * cleared the guard and re-dispatched the apply — re-lighting the ring a
     * second time (log: gen=3 post=143876380 applied at 3317 and again at 4117).
     *
     * The fix is an authoritative COMPLETION latch keyed on
     * `(completedGeneration, completedPostId)` that is NOT cleared by render /
     * scroll-settled re-arms. Once a generation+post has been fully dispatched
     * (apply + fadeout scheduled) it is "done": any later re-arm for that exact
     * pair is a no-op. A genuinely NEW generation or a different post does not
     * match the latch, so it still lights up once.
     *
     * Returns `true` when this exact `(generation, postId)` has already completed
     * its single light+fade cycle and MUST NOT be re-dispatched.
     */
    fun isHighlightCycleAlreadyCompleted(
            completedGeneration: Int,
            completedPostId: Long,
            currentGeneration: Int,
            currentPostId: Long,
    ): Boolean {
        if (currentPostId <= 0L) return false
        if (completedGeneration <= 0) return false
        return completedGeneration == currentGeneration && completedPostId == currentPostId
    }
}

