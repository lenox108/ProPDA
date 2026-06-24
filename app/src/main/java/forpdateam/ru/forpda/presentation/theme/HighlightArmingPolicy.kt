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

    /** Each [renderThemePage] bump must allow a fresh highlight arm for the new generation. */
    fun armedGenerationAfterNewRender(): Int = 0

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
}

