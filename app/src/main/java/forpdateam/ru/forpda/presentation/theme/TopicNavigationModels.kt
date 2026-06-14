package forpdateam.ru.forpda.presentation.theme

/**
 * Single source of truth for "what the caller wants" vs "what we will actually open".
 *
 * This is intentionally decoupled from WebView restore/JS details.
 */
sealed class TopicOpenIntent {
    abstract val rawUrl: String
    abstract val sourceScreen: String

    data class FreshOpenFromForum(
            override val rawUrl: String,
            override val sourceScreen: String
    ) : TopicOpenIntent()

    data class FreshOpenFromFavorites(
            override val rawUrl: String,
            override val sourceScreen: String
    ) : TopicOpenIntent()

    data class FreshOpenFromTracker(
            override val rawUrl: String,
            override val sourceScreen: String
    ) : TopicOpenIntent()

    data class FreshOpenFromSearch(
            override val rawUrl: String,
            override val sourceScreen: String
    ) : TopicOpenIntent()

    data class FreshOpenFromNews(
            override val rawUrl: String,
            override val sourceScreen: String
    ) : TopicOpenIntent()

    /** Explicitly requested a post (findpost, p=, pid=, #entry). */
    data class ExplicitPostLink(
            override val rawUrl: String,
            val postId: Int,
            override val sourceScreen: String
    ) : TopicOpenIntent()

    /** Explicitly requested a page (pagination). */
    data class ExplicitPageLink(
            override val rawUrl: String,
            val pageSt: Int,
            override val sourceScreen: String
    ) : TopicOpenIntent()

    /** User pressed "open unread" from UI. */
    data class OpenUnread(
            override val rawUrl: String,
            override val sourceScreen: String
    ) : TopicOpenIntent()

    data class OpenFirstPage(
            override val rawUrl: String,
            override val sourceScreen: String
    ) : TopicOpenIntent()

    data class OpenLastPost(
            override val rawUrl: String,
            override val sourceScreen: String
    ) : TopicOpenIntent()

    /**
     * Restoring previous topic position after navigating away (Android back / tab pop).
     * MUST NOT apply LAST_UNREAD logic.
     */
    data class BackRestore(
            val entry: TopicBackStackEntry,
            override val rawUrl: String = entry.sourceUrl.orEmpty(),
            override val sourceScreen: String = "back_restore"
    ) : TopicOpenIntent()

    data class RotationRestore(
            override val rawUrl: String,
            override val sourceScreen: String
    ) : TopicOpenIntent()

    data class ProcessRestore(
            override val rawUrl: String,
            override val sourceScreen: String
    ) : TopicOpenIntent()

    /** After posting a reply — show new post, not unread/saved scroll. */
    data class AfterPostSubmit(
            override val rawUrl: String,
            override val sourceScreen: String
    ) : TopicOpenIntent()

    fun isFreshOpen(): Boolean = when (this) {
        is FreshOpenFromForum,
        is FreshOpenFromFavorites,
        is FreshOpenFromTracker,
        is FreshOpenFromSearch,
        is FreshOpenFromNews -> true
        else -> false
    }

    fun isRestore(): Boolean = this is BackRestore || this is RotationRestore || this is ProcessRestore
}

/**
 * Resolved navigation target carried through resolver → ViewModel → render.
 *
 * [allowSavedScrollRestore] is the single owner for whether cached scroll/anchor may win over this open.
 */
sealed class TopicOpenTarget {
    abstract val fetchUrl: String
    abstract val topicId: Int?
    abstract val pageSt: Int?
    abstract val postId: Int?
    abstract val allowSavedScrollRestore: Boolean

    data class Unread(
            override val fetchUrl: String,
            override val topicId: Int?,
            val unreadUrlFromList: String? = null,
            val reason: String = "unread"
    ) : TopicOpenTarget() {
        override val pageSt: Int? = null
        override val postId: Int? = null
        override val allowSavedScrollRestore: Boolean = false
    }

    data class ExplicitPost(
            override val fetchUrl: String,
            override val topicId: Int?,
            override val postId: Int,
            override val pageSt: Int? = null
    ) : TopicOpenTarget() {
        override val allowSavedScrollRestore: Boolean = false
    }

    data class ExplicitPage(
            override val fetchUrl: String,
            override val topicId: Int?,
            override val pageSt: Int
    ) : TopicOpenTarget() {
        override val postId: Int? = null
        override val allowSavedScrollRestore: Boolean = false
    }

    data class BackRestore(
            override val fetchUrl: String,
            override val topicId: Int?,
            override val pageSt: Int?,
            val snapshot: TopicBackSnapshot
    ) : TopicOpenTarget() {
        override val postId: Int? = snapshot.visiblePostId?.toIntOrNull()
        override val allowSavedScrollRestore: Boolean = true
    }

    data class RefreshRestore(
            override val fetchUrl: String,
            override val topicId: Int?,
            override val pageSt: Int?,
            val restoreId: String,
            val mode: String,
            val source: String? = null
    ) : TopicOpenTarget() {
        override val postId: Int? = null
        override val allowSavedScrollRestore: Boolean = true
    }

    /** Jump to last page / bottom (toolbar "end" navigation). */
    data class End(
            override val fetchUrl: String,
            override val topicId: Int?,
            override val pageSt: Int? = null
    ) : TopicOpenTarget() {
        override val postId: Int? = null
        override val allowSavedScrollRestore: Boolean = false
    }

    /** Setting-driven or safe fallback that may reuse saved scroll when allowed by policy. */
    data class Default(
            override val fetchUrl: String,
            override val topicId: Int?,
            override val pageSt: Int? = null,
            override val postId: Int? = null,
            override val allowSavedScrollRestore: Boolean = true,
            val reason: String = "default"
    ) : TopicOpenTarget()
}

enum class TopicBackSnapshotStatus {
    NONE,
    PENDING,
    CAPTURED,
    STALE
}

/**
 * Immutable scroll snapshot for back navigation — separate from mutable [ThemePage] fields.
 */
data class TopicBackSnapshot(
        val topicId: Int,
        val pageSt: Int,
        val visiblePostId: String?,
        val scrollOffset: Int,
        val scrollRatio: Double?,
        val wasNearBottom: Boolean = false,
        val status: TopicBackSnapshotStatus = TopicBackSnapshotStatus.CAPTURED,
        val capturedAtMs: Long = System.currentTimeMillis()
) {
    fun isUsable(): Boolean =
            status == TopicBackSnapshotStatus.CAPTURED || status == TopicBackSnapshotStatus.PENDING

    companion object {
        fun fromPage(
                topicId: Int,
                pageSt: Int,
                visiblePostId: String?,
                scrollOffset: Int,
                scrollRatio: Double?,
                wasNearBottom: Boolean,
                status: TopicBackSnapshotStatus = TopicBackSnapshotStatus.CAPTURED
        ): TopicBackSnapshot = TopicBackSnapshot(
                topicId = topicId,
                pageSt = pageSt,
                visiblePostId = visiblePostId,
                scrollOffset = scrollOffset,
                scrollRatio = scrollRatio,
                wasNearBottom = wasNearBottom,
                status = status
        )

        fun key(topicId: Int, pageSt: Int): String = "$topicId:$pageSt"
    }
}

data class TopicBackStackEntry(
        val topicId: Int,
        val pageSt: Int?,
        val postId: String?,
        val scrollY: Int?,
        val anchorPostId: String?,
        val sourceUrl: String?,
        val timestampMs: Long,
        val renderGenerationId: Int? = null,
        val snapshot: TopicBackSnapshot? = null
)
