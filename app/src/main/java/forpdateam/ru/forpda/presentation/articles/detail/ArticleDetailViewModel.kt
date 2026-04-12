package forpdateam.ru.forpda.presentation.articles.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import forpdateam.ru.forpda.common.Utils
import forpdateam.ru.forpda.entity.remote.news.DetailsPage
import forpdateam.ru.forpda.model.interactors.news.ArticleInteractor
import forpdateam.ru.forpda.presentation.IErrorHandler
import forpdateam.ru.forpda.presentation.ILinkHandler
import forpdateam.ru.forpda.presentation.TabRouter
import io.reactivex.disposables.CompositeDisposable

class ArticleDetailViewModel(
        private val articleInteractor: ArticleInteractor,
        private val router: TabRouter,
        private val linkHandler: ILinkHandler,
        private val errorHandler: IErrorHandler
) : ViewModel() {

    @Volatile
    private var articleDetailView: ArticleDetailView? = null

    fun attachView(view: ArticleDetailView) {
        articleDetailView = view
    }

    fun detachView() {
        articleDetailView = null
    }

    private val rxSubscriptions = CompositeDisposable()
    private var subscriptionsStarted = false

    var currentData: DetailsPage? = null

    fun start() {
        if (subscriptionsStarted) return
        subscriptionsStarted = true
        loadArticle()
        articleInteractor
                .observeData()
                .subscribe({
                    currentData = it
                }, {
                    errorHandler.handle(it)
                })
                .also { rxSubscriptions.add(it) }
    }

    override fun onCleared() {
        rxSubscriptions.clear()
        super.onCleared()
    }

    fun loadArticle() {
        articleInteractor
                .loadArticle()
                .doOnSubscribe { articleDetailView?.setRefreshing(true) }
                .doAfterTerminate { articleDetailView?.setRefreshing(false) }
                .subscribe({
                    articleDetailView?.showArticle(it)
                }, {
                    errorHandler.handle(it)
                })
                .also { rxSubscriptions.add(it) }
    }

    fun openAuthorProfile() {
        currentData?.let {
            linkHandler.handle("https://4pda.to/forum/index.php?showuser=${it.authorId}", router)
        }
    }

    fun copyLink() {
        currentData?.let {
            Utils.copyToClipBoard("https://4pda.to/index.php?p=${it.id}")
        }
    }

    fun shareLink() {
        currentData?.let {
            Utils.shareText("https://4pda.to/index.php?p=${it.id}")
        }
    }

    fun createNote() {
        currentData?.let {
            val url = "https://4pda.to/index.php?p=${it.id}"
            articleDetailView?.showCreateNote(it.title.orEmpty(), url)
        }
    }

    class Factory(
            private val articleInteractor: ArticleInteractor,
            private val router: TabRouter,
            private val linkHandler: ILinkHandler,
            private val errorHandler: IErrorHandler
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass != ArticleDetailViewModel::class.java) throw IllegalArgumentException("Unknown ViewModel class")
            return ArticleDetailViewModel(articleInteractor, router, linkHandler, errorHandler) as T
        }
    }
}
