package forpdateam.ru.forpda.presentation.theme

import forpdateam.ru.forpda.diagnostic.TopicHighlightDiagnostics

/**
 * Single resolver for the topic-post highlight. The previous attempt wired the
 * visual flash to `doScroll()`'s `.active` class with a 2s timer — that effect is
 * one-shot, has no data model behind it, and never survives a refresh / scroll
 * restore. This resolver is the *only* entry point for picking which post to
 * highlight on a freshly-rendered page.
 *
 * **Priority** (highest first):
 *  1. `unread.firstUnreadPostId != null` (topic is unread; list-unread or
 *     `view=getnewpost` resolution) → `FirstUnread`. Wins over every other input
 *     except the on-page check below.
 *  2. If the unread target's post is not on the current page, fall through to
 *     `LastRead` (or explicit if present), so we don't lose the highlight on
 *     page hops.
 *  3. `readPosition.lastViewedPostId` (the user's last viewed post for this topic)
 *     AND it is on the rendered page → `LastRead`.
 *  4. Explicit findpost deep link (NOT scroll anchors) → `ExplicitPost`.
 *  5. Already-read topic, no unread: last post on current page when read
 *     position is missing or off-page → `LastRead` (`last_post_on_page_fallback`).
 *  6. Otherwise → `None` and `reason=highlight_target_missing`.
 *
 * **The resolver MUST NOT depend on the scroll target.** Highlight and scroll are
 * independently stored fields on the renderer and on the renderer's per-render
 * `StateFlow`; see `TopicHighlightApply.onRender` for the wiring.
 *
 * **Determinism.** Given the same `(unread, readPosition, explicit, pagePostIds)`
 * the resolver returns the same result. Refresh re-invokes it with the same
 * inputs; the result is stable.
 */
object HighlightResolver {

    /**
     * Resolve the highlight target for a single page render.
     *
     * @param topicId topic id (for diagnostics only).
     * @param unread unresolved unread target (may be null when input is unknown).
     * @param readPosition last viewed position from the repository (may be null).
     * @param explicitPostId post id from an explicit deep link (bookmark /
     *        mention / findpost with `p=`). May be null.
     * @param pagePostIds post ids of the currently-rendered page, in order.
     *        Used both to filter out targets that aren't on this page and to
     *        pick a safe fallback when the desired post is missing.
     */
    fun resolve(
            topicId: Long,
            unread: UnreadTarget?,
            readPosition: ReadPosition?,
            explicitPostId: Long?,
            pagePostIds: List<Long>,
            lastReadSource: String? = null,
    ): HighlightResolution {
        val unreadId = unread?.firstUnreadPostId?.takeIf { it > 0L }
        val readId = readPosition?.lastViewedPostId?.takeIf { it > 0L }
        val explicitId = explicitPostId?.takeIf { it > 0L }
        val hasUnread = unreadId != null
        val hasLastViewed = readId != null
        val hasExplicit = explicitId != null

        TopicHighlightDiagnostics.highlightResolveStarted(
                topicId = topicId,
                hasUnread = hasUnread,
                lastViewed = hasLastViewed,
                explicit = hasExplicit,
                firstUnreadPostId = unread?.firstViewedPostIdOrNull(),
                lastViewedPostId = readPosition?.lastViewedPostId,
                explicitPostId = explicitPostId,
                pagePostCount = pagePostIds.size,
                lastReadSource = lastReadSource,
        )

        // 1. First unread wins — but only if the post is actually on the page.
        if (unreadId != null && unreadId in pagePostIds) {
            val target = HighlightTarget.FirstUnread(unreadId)
            val resolution = HighlightResolution(
                    target = target,
                    reason = "first_unread",
                    hasUnreadInput = true,
                    lastViewedInput = hasLastViewed,
                    explicitInput = hasExplicit
            )
            TopicHighlightDiagnostics.highlightTargetResolved(
                    topicId = topicId,
                    type = target.type,
                    postId = target.postId,
                    reason = resolution.reason
            )
            return resolution
            // Unread target is on a different page — fall through to last read.
        }

        // 2. Last read for already-read topic. The highlight reflects the exact
        //    saved `lastViewedPostId` when it is on the page. We deliberately do
        //    NOT silently "upgrade" the saved post to the last post on the page:
        //    that forward-jump was a source of the highlight landing on the bottom
        //    post and of the saved read position drifting forward across opens.
        if (readId != null && readId in pagePostIds) {
            val target = HighlightTarget.LastRead(readId)
            val reason = "last_read"
            val resolution = HighlightResolution(
                    target = target,
                    reason = reason,
                    hasUnreadInput = hasUnread,
                    lastViewedInput = true,
                    explicitInput = hasExplicit
            )
            TopicHighlightDiagnostics.highlightTargetResolved(
                    topicId = topicId,
                    type = target.type,
                    postId = target.postId,
                    reason = resolution.reason
            )
            return resolution
        }

        // 3. Explicit deep link. Must not override an unread target, but an
        //    unread target that has been determined to be off-page has already
        //    fallen through, so explicit wins here.
        if (explicitId != null && explicitId in pagePostIds) {
            val target = HighlightTarget.ExplicitPost(explicitId)
            val resolution = HighlightResolution(
                    target = target,
                    reason = "explicit_post",
                    hasUnreadInput = hasUnread,
                    lastViewedInput = hasLastViewed,
                    explicitInput = true
            )
            TopicHighlightDiagnostics.highlightTargetResolved(
                    topicId = topicId,
                    type = target.type,
                    postId = target.postId,
                    reason = resolution.reason
            )
            return resolution
        }

        // 4. Already-read topic with no unread: highlight the last post on the
        //    current page when read position is unknown or off-page (user read
        //    to the bottom of this page).
        if (!hasUnread && pagePostIds.isNotEmpty()) {
            val lastPostId = pagePostIds.last()
            // Soft fallback: no genuine target, the ring just defaults to the last post on the page.
            // Mark it so the view is NOT auto-scrolled to it (a pagination/page jump must stay on the
            // page's first post; only real unread/last-read/explicit targets drive the highlight scroll).
            val target = HighlightTarget.LastRead(lastPostId, softFallback = true)
            val resolution = HighlightResolution(
                    target = target,
                    reason = "last_post_on_page_fallback",
                    hasUnreadInput = false,
                    lastViewedInput = hasLastViewed,
                    explicitInput = hasExplicit
            )
            TopicHighlightDiagnostics.highlightTargetResolved(
                    topicId = topicId,
                    type = target.type,
                    postId = target.postId,
                    reason = resolution.reason
            )
            return resolution
        }

        // 5. Nothing to highlight.
        val reason = when {
            !hasUnread && !hasLastViewed && !hasExplicit -> "no_inputs"
            hasUnread -> "unread_off_page"
            hasLastViewed -> "last_read_off_page"
            hasExplicit -> "explicit_off_page"
            else -> "unknown"
        }
        TopicHighlightDiagnostics.highlightTargetMissing(topicId = topicId, reason = reason)
        return HighlightResolution(
                target = HighlightTarget.None,
                reason = reason,
                hasUnreadInput = hasUnread,
                lastViewedInput = hasLastViewed,
                explicitInput = hasExplicit,
                isRenderable = false
        )
    }

    private fun UnreadTarget.firstViewedPostIdOrNull(): Long? =
            firstUnreadPostId?.takeIf { it > 0L }
}
