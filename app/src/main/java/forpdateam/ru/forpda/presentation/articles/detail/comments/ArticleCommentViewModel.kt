package forpdateam.ru.forpda.presentation.articles.detail.comments

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import forpdateam.ru.forpda.entity.remote.news.Comment
import forpdateam.ru.forpda.model.AuthHolder
import forpdateam.ru.forpda.model.interactors.news.ArticleInteractor
import forpdateam.ru.forpda.presentation.IErrorHandler
import forpdateam.ru.forpda.presentation.ILinkHandler
import forpdateam.ru.forpda.presentation.TabRouter
import io.reactivex.disposables.CompositeDisposable
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import java.util.ArrayList

class ArticleCommentViewModel(
        private val articleInteractor: ArticleInteractor,
        private val router: TabRouter,
        private val linkHandler: ILinkHandler,
        private val authHolder: AuthHolder,
        private val errorHandler: IErrorHandler
) : ViewModel() {

    @Volatile
    private var articleCommentView: ArticleCommentView? = null

    fun attachView(view: ArticleCommentView) {
        articleCommentView = view
    }

    fun detachView() {
        articleCommentView = null
    }

    private val rxSubscriptions = CompositeDisposable()
    private var subscriptionsStarted = false

    private var firstShow: Boolean = true

    fun start() {
        if (subscriptionsStarted) return
        subscriptionsStarted = true
        articleInteractor
                .observeComments()
                .map { commentsToList(it) }
                .doOnTerminate { articleCommentView?.setRefreshing(true) }
                .doAfterTerminate { articleCommentView?.setRefreshing(false) }
                .subscribe({
                    articleCommentView?.showComments(it)
                    if (firstShow) {
                        val targetCommentId = articleInteractor.initData.commentId
                        val index = it.indexOfFirst { it.id == targetCommentId }
                        articleCommentView?.scrollToComment(index)
                        firstShow = false
                    }
                }, {
                    errorHandler.handle(it)
                })
                .also { rxSubscriptions.add(it) }

        viewModelScope.launch {
            authHolder.observe().collect {
                articleCommentView?.setMessageFieldVisible(it.isAuth())
            }
        }
    }

    override fun onCleared() {
        rxSubscriptions.clear()
        super.onCleared()
    }

    fun updateComments() {
        articleInteractor
                .loadArticle()
                .doOnSubscribe { articleCommentView?.setRefreshing(true) }
                .doAfterTerminate { articleCommentView?.setRefreshing(false) }
                .subscribe({ }, {
                    errorHandler.handle(it)
                })
                .also { rxSubscriptions.add(it) }
    }

    fun replyComment(commentId: Int, text: String) {
        articleInteractor
                .replyComment(commentId, text)
                .doOnSubscribe { articleCommentView?.setSendRefreshing(true) }
                .doAfterTerminate { articleCommentView?.setSendRefreshing(false) }
                .subscribe({
                    articleCommentView?.onReplyComment()
                }, {
                    errorHandler.handle(it)
                })
                .also { rxSubscriptions.add(it) }
    }

    fun likeComment(commentId: Int) {
        articleInteractor
                .likeComment(commentId)
                .subscribe({}, {
                    errorHandler.handle(it)
                })
                .also { rxSubscriptions.add(it) }
    }

    fun commentsToList(comment: Comment): ArrayList<Comment> {
        val comments = ArrayList<Comment>()
        recurseCommentsToList(comments, comment)
        return comments
    }

    fun recurseCommentsToList(comments: ArrayList<Comment>, comment: Comment) {
        for (child in comment.children) {
            comments.add(Comment(child))
            recurseCommentsToList(comments, child)
        }
    }

    fun openProfile(comment: Comment) {
        linkHandler.handle("https://4pda.to/forum/index.php?showuser=${comment.userId}", router)
    }

    class Factory(
            private val articleInteractor: ArticleInteractor,
            private val router: TabRouter,
            private val linkHandler: ILinkHandler,
            private val authHolder: AuthHolder,
            private val errorHandler: IErrorHandler
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass != ArticleCommentViewModel::class.java) throw IllegalArgumentException("Unknown ViewModel class")
            return ArticleCommentViewModel(articleInteractor, router, linkHandler, authHolder, errorHandler) as T
        }
    }
}
