package forpdateam.ru.forpda.presentation.articles.detail.comments

import forpdateam.ru.forpda.common.ui.IBaseView
import forpdateam.ru.forpda.entity.remote.news.Comment

/**
 * Created by radiationx on 01.01.18.
 */
interface ArticleCommentView : IBaseView {
    fun onReplyComment()
    fun showComments(comments: List<Comment>)
    fun setSendRefreshing(isRefreshing: Boolean)
    fun scrollToComment(position: Int)
    fun setMessageFieldVisible(isVisible: Boolean)
}
