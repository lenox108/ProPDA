package forpdateam.ru.forpda.presentation.articles.detail.comments

import forpdateam.ru.forpda.entity.remote.news.Comment

/** WordPress / inline batch size for first expand and each load-more tap. */
const val COMMENTS_PAGE_SIZE = InlineCommentsBatchConfig.BATCH_SIZE

/**
 * Manual/lazy comments section state for news article screen.
 *
 * This is intentionally UI-oriented and does not imply that comments must be
 * loaded/parsed during the first article render.
 */
data class CommentsSectionState(
        /** Inverse of [isExpanded] for DOM `data-collapsed` compatibility. */
        val collapsed: Boolean = true,
        val isExpanded: Boolean = !collapsed,
        /** Authoritative badge total when known; -1 when unknown. */
        val totalCount: Int = -1,
        val loadedCount: Int = 0,
        val commentsState: CommentsState = CommentsState.NotLoaded,
)

sealed class CommentsState {
    data object NotLoaded : CommentsState()
    /** First expand / initial batch fetch. */
    data class LoadingInitial(val requestId: Int) : CommentsState()
    /** Alias kept for coordinator mapping from [ArticleCommentsState.Loading]. */
    data class Loading(val requestId: Int) : CommentsState()
    data class Loaded(
            val comments: List<Comment>,
            val page: Int = 1,
            val canLoadMore: Boolean = false,
            val totalCount: Int = comments.size,
            /** Full flattened cache in ViewModel / interactor (may exceed visible batch). */
            val allParsedCommentsCache: List<Comment> = comments,
            /** Next WordPress `cp` page when [canLoadMore]; null when exhausted. */
            val nextCommentPage: Int? = if (canLoadMore) page + 1 else null,
    ) : CommentsState()
    /** Next cp= page append in flight. */
    data class LoadingMore(val loadedCount: Int) : CommentsState()
    data object Empty : CommentsState()
    data class Error(
            val throwable: Throwable,
            val canRetry: Boolean = true
    ) : CommentsState()
}

