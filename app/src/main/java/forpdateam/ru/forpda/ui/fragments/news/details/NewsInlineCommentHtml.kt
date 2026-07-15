package forpdateam.ru.forpda.ui.fragments.news.details

import forpdateam.ru.forpda.common.Html
import forpdateam.ru.forpda.entity.remote.news.Comment

/**
 * Pure builders for the inline-comment markup injected into the article WebView.
 *
 * Extracted from [ArticleContentFragment] so the DOM shape lives in one testable place instead of
 * being inlined in the 3.7k-line fragment. Everything here is a pure function of its arguments —
 * localized strings and per-comment predicates are passed in by the caller, keeping this object free
 * of Fragment/ViewModel state.
 */
object NewsInlineCommentHtml {

    /** Max left indent (px) applied to a reply, matching the previous inline computation. */
    private const val INDENT_STEP_PX = 12
    private const val INDENT_MAX_PX = 72

    fun editedMarkerHtml(editedHint: String): String =
            """<span class="news-inline-comment-edited" title="${Html.escapeHtml(editedHint)}">✎</span>"""

    fun metaDateHtml(date: String, isEdited: Boolean, editedHint: String): String {
        val escapedDate = Html.escapeHtml(date)
        if (!isEdited) {
            return """<span class="news-inline-comment-date">$escapedDate</span>"""
        }
        return """<span class="news-inline-comment-meta-date"><span class="news-inline-comment-date">$escapedDate</span>${editedMarkerHtml(editedHint)}</span>"""
    }

    fun commentsHtml(
            comments: List<Comment>,
            editedHint: String,
            deletedLabel: String,
            replyLabel: String,
            canReply: (Comment) -> Boolean,
            hasMenu: (Comment) -> Boolean,
    ): String =
            comments.joinToString(separator = "") { comment ->
                commentHtml(
                        comment = comment,
                        editedHint = editedHint,
                        deletedLabel = deletedLabel,
                        replyLabel = replyLabel,
                        canReply = canReply(comment),
                        hasMenu = hasMenu(comment),
                )
            }

    fun commentHtml(
            comment: Comment,
            editedHint: String,
            deletedLabel: String,
            replyLabel: String,
            canReply: Boolean,
            hasMenu: Boolean,
    ): String {
        val id = comment.id.toString()
        val deleted = comment.isDeleted
        val nick = Html.escapeHtml(comment.userNick.orEmpty())
        val metaDate = metaDateHtml(comment.date.orEmpty(), comment.isEdited, editedHint)
        val rawContent = comment.content.orEmpty()
        val content = when {
            deleted && rawContent.isBlank() -> Html.escapeHtml(deletedLabel)
            else -> rawContent
        }
        val indent = (comment.level.coerceAtLeast(0) * INDENT_STEP_PX).coerceAtMost(INDENT_MAX_PX)
        val actions = if (deleted) "" else actionsHtml(comment, replyLabel, canReply, hasMenu)
        return """
<article class="news-inline-comment" data-news-comment-id="$id" data-deleted="$deleted" style="margin-left:${indent}px">
    <div class="news-inline-comment-meta">
        <button type="button" class="news-inline-comment-author" data-news-comment-action="profile" data-comment-id="$id">$nick</button>
        $metaDate
    </div>
    <div class="news-inline-comment-content">$content</div>
    $actions
</article>
""".trim()
    }

    fun actionsHtml(
            comment: Comment,
            replyLabel: String,
            canReply: Boolean,
            hasMenu: Boolean,
    ): String {
        val id = comment.id.toString()
        val likeVisible = comment.likeAction?.isValid() == true ||
                comment.unlikeAction?.isValid() == true ||
                comment.toggleAction?.isValid() == true ||
                comment.likeCount > 0 ||
                comment.likedByMe
        val like = if (likeVisible) {
            val label = likeLabel(comment.likeCount)
            val likedClass = if (comment.likedByMe) " liked" else " not-liked"
            """<button type="button" class="news-inline-comment-action$likedClass" data-news-comment-action="like" data-comment-id="$id">$label</button>"""
        } else {
            ""
        }
        val reply = if (canReply) {
            """<button type="button" class="news-inline-comment-action" data-news-comment-action="reply" data-comment-id="$id">${Html.escapeHtml(replyLabel)}</button>"""
        } else {
            ""
        }
        val more = if (hasMenu) {
            """<button type="button" class="news-inline-comment-action" data-news-comment-action="menu" data-comment-id="$id">⋯</button>"""
        } else {
            ""
        }
        return """<div class="news-inline-comment-actions">$like$reply$more</div>"""
    }

    fun likeLabel(likeCount: Int): String = if (likeCount > 0) likeCount.toString() else ""
}
