package forpdateam.ru.forpda.ui.fragments.news.details

import forpdateam.ru.forpda.entity.remote.news.Comment

object ArticleCommentActionVisibility {

    fun isOwnCommentForActions(authUserId: Int, comment: Comment): Boolean =
            (authUserId > 0 && comment.userId == authUserId) ||
                    hasServerOwnAction(comment)

    fun canShowEdit(auth: Boolean, authUserId: Int, comment: Comment): Boolean =
            auth &&
                    comment.actions.edit?.isValid() == true &&
                    isOwnCommentForActions(authUserId, comment)

    fun canShowDelete(auth: Boolean, authUserId: Int, comment: Comment): Boolean =
            auth &&
                    isActionableModeration(comment.actions.delete) &&
                    isOwnCommentForActions(authUserId, comment)

    fun isActionableModeration(action: Comment.Action?): Boolean {
        if (action == null || !action.isValid()) return false
        if (action.fields.keys.any(::isModerationNonceField)) return true
        if (action.fields.keys.any(::isCommentTextField)) return true
        return action.url.orEmpty().contains("_wpnonce=", ignoreCase = true)
    }

    private fun isModerationNonceField(name: String): Boolean =
            name.equals("_wpnonce", ignoreCase = true) ||
                    name.equals("_ajax_nonce-replyto-comment", ignoreCase = true) ||
                    name.equals("_ajax_nonce", ignoreCase = true) ||
                    name.equals("wpnonce", ignoreCase = true)

    private fun isCommentTextField(name: String): Boolean =
            name.equals("comment", ignoreCase = true) ||
                    name.equals("content", ignoreCase = true) ||
                    name.equals("message", ignoreCase = true) ||
                    name.equals("text", ignoreCase = true)

    fun editHiddenReason(auth: Boolean, authUserId: Int, comment: Comment): String? {
        val action = comment.actions.edit
        return when {
            canShowEdit(auth, authUserId, comment) -> null
            !auth -> "not-authenticated"
            action == null -> "missing-edit-action"
            !action.enabled -> "edit-action-disabled"
            action.url.isNullOrBlank() -> "edit-action-empty-url"
            !isOwnCommentForActions(authUserId, comment) -> "not-own"
            else -> "unknown"
        }
    }

    fun hasReputationAction(comment: Comment): Boolean =
            comment.actions.reputationPlus?.isValid() == true ||
                    comment.actions.reputationMinus?.isValid() == true

    private fun hasServerOwnAction(comment: Comment): Boolean =
            comment.actions.edit?.isValid() == true ||
                    comment.actions.delete?.isValid() == true
}
