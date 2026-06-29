package forpdateam.ru.forpda.presentation.theme

/**
 * Topic post highlight — *visual* (which post gets the left accent + tinted background).
 *
 * Distinct from [TopicOpenTarget] / [TopicBackSnapshot], which are *scroll* targets.
 * A highlight and a scroll target usually coincide (we want the user to see what they
 * were just looking at), but they are computed from different inputs and are stored
 * independently. Collapsing them into one field breaks either the scroll OR the
 * visual contract; see `TopicHighlightResolver.priorityRules` for the resolution order.
 */
sealed class HighlightType {
    /** Stable lowercase identifier used by JS and template CSS classes. */
    abstract val cssClass: String
    /** Stable lowercase identifier for the JS contract (`PageInfo.ppdaHighlight.type`). */
    abstract val jsName: String

    /** No post in the rendered page should be highlighted. */
    object None : HighlightType() {
        override val cssClass: String = "none"
        override val jsName: String = "none"
    }

    /** First unread post on the current page (e.g. topic opened from favorites unread). */
    object FirstUnread : HighlightType() {
        override val cssClass: String = "post-highlight-first-unread"
        override val jsName: String = "first-unread"
    }

    /** Last viewed/read post on the current page (e.g. already-read topic reopen). */
    object LastRead : HighlightType() {
        override val cssClass: String = "post-highlight-last-read"
        override val jsName: String = "last-read"
    }

    /** Deep link to an explicit post (e.g. bookmark, mention, findpost). */
    object Explicit : HighlightType() {
        override val cssClass: String = "post-highlight-explicit"
        override val jsName: String = "explicit"
    }
}

/**
 * Resolved highlight target for a single render of a page.
 *
 * Carries the post id so the template can apply the per-post class, and the visual type
 * so the JS fallback and the native hybrid adapter can decide what to draw.
 */
sealed class HighlightTarget {
    abstract val postId: Long
    abstract val type: HighlightType

    data class FirstUnread(override val postId: Long) : HighlightTarget() {
        override val type: HighlightType = HighlightType.FirstUnread
    }

    data class LastRead(
            override val postId: Long,
            /**
             * True when this is the soft `last_post_on_page_fallback` (already-read topic, no real
             * unread/last-viewed/explicit target — the ring just defaults to the last post on the page).
             * Such a guessed target must NOT drive the highlight auto-scroll, otherwise an explicit page
             * jump (pagination) gets yanked from the page's first post down to its last post.
             */
            val softFallback: Boolean = false
    ) : HighlightTarget() {
        override val type: HighlightType = HighlightType.LastRead
    }

    data class ExplicitPost(override val postId: Long) : HighlightTarget() {
        override val type: HighlightType = HighlightType.Explicit
    }

    /** No highlight; renderer must not add any `post-highlight-*` class. */
    object None : HighlightTarget() {
        override val postId: Long = 0L
        override val type: HighlightType = HighlightType.None
    }
}

/**
 * Snapshot of the user's last-viewed position for a topic. Persisted to
 * [forpdateam.ru.forpda.model.repository.theme.ThemeReadPositionRepository].
 *
 * The data is independent of the scroll target — [HighlightResolver] uses it ONLY
 * to decide whether to draw a `LastRead` highlight when the topic has no unread.
 */
data class ReadPosition(
        val topicId: Long,
        val lastViewedPostId: Long,
        val lastViewedPage: Int,
        val updatedAt: Long = System.currentTimeMillis()
)

/**
 * Unread target for a topic — what the user would see if they opened the topic
 * via `view=getnewpost` or a list-unread row. Loaded by the use case from the
 * open intent / server response and passed into the highlight resolver.
 */
data class UnreadTarget(
        val topicId: Long,
        val firstUnreadPostId: Long?,
        val unreadPage: Int?,
        val unreadUrl: String?
)

/**
 * Lightweight diagnostic struct returned by [HighlightResolver.resolve]. Always
 * present so the renderer can log why nothing was applied.
 */
data class HighlightResolution(
        val target: HighlightTarget,
        val reason: String,
        val hasUnreadInput: Boolean,
        val lastViewedInput: Boolean,
        val explicitInput: Boolean,
        val isRenderable: Boolean = target !is HighlightTarget.None
)
