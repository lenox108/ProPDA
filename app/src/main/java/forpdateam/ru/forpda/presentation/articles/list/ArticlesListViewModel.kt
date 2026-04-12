package forpdateam.ru.forpda.presentation.articles.list

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import forpdateam.ru.forpda.BuildConfig
import forpdateam.ru.forpda.common.Utils
import forpdateam.ru.forpda.entity.remote.news.NewsItem
import forpdateam.ru.forpda.model.AuthHolder
import forpdateam.ru.forpda.model.SchedulersProvider
import forpdateam.ru.forpda.model.data.remote.api.news.Constants
import forpdateam.ru.forpda.model.repository.avatar.AvatarRepository
import forpdateam.ru.forpda.model.repository.news.NewsRepository
import forpdateam.ru.forpda.presentation.IErrorHandler
import forpdateam.ru.forpda.presentation.ILinkHandler
import forpdateam.ru.forpda.presentation.Screen
import forpdateam.ru.forpda.presentation.TabRouter
import io.reactivex.Observable
import io.reactivex.disposables.CompositeDisposable

class ArticlesListViewModel(
        private val newsRepository: NewsRepository,
        private val avatarRepository: AvatarRepository,
        private val authHolder: AuthHolder,
        private val router: TabRouter,
        private val linkHandler: ILinkHandler,
        private val errorHandler: IErrorHandler,
        private val schedulers: SchedulersProvider
) : ViewModel() {

    @Volatile
    private var articlesListView: ArticlesListView? = null

    fun attachView(view: ArticlesListView) {
        articlesListView = view
    }

    fun detachView() {
        articlesListView = null
    }

    private val rxSubscriptions = CompositeDisposable()
    private var subscriptionsStarted = false

    private val category = Constants.NEWS_CATEGORY_ROOT
    private var currentPage = 1

    private val currentItems = mutableListOf<NewsItem>()
    private val avatarsData = mutableListOf<Pair<Int, String>>()

    fun start() {
        if (subscriptionsStarted) return
        subscriptionsStarted = true
        refreshArticles()
    }

    override fun onCleared() {
        rxSubscriptions.clear()
        super.onCleared()
    }

    private fun loadArticles(page: Int, withClear: Boolean) {
        currentPage = page
        newsRepository
                .getNews(category, currentPage)
                .doOnSubscribe { articlesListView?.setRefreshing(true) }
                .doAfterTerminate { articlesListView?.setRefreshing(false) }
                .subscribe({
                    if (withClear) {
                        currentItems.clear()
                    }
                    currentItems.addAll(it)
                    articlesListView?.showNews(it, withClear)
                    loadAvatars(it)
                }, {
                    errorHandler.handle(it)
                })
                .also { rxSubscriptions.add(it) }
    }

    private fun loadAvatars(items: List<NewsItem>) {
        if (!authHolder.get().isAuth()) {
            return
        }
        val newAvatarsData = mutableListOf<Pair<Int, String>>()
        items.forEach { item ->
            if (avatarsData.firstOrNull { it.first == item.authorId } == null) {
                Pair(item.authorId, item.author.orEmpty()).also {
                    avatarsData.add(it)
                    newAvatarsData.add(it)
                }
            }
        }
        if (BuildConfig.DEBUG) {
            newAvatarsData.forEach {
                Log.d("ArticlesListViewModel", "newAvatarsData ${it.first} ${it.second}")
            }
        }
        Observable
                .fromIterable(newAvatarsData)
                .flatMapSingle { avatarData ->
                    avatarRepository
                            .getAvatar(avatarData.second)
                            .map { Pair(avatarData, it as String?) }
                            .onErrorReturnItem(Pair(avatarData, null as String?))
                }
                .subscribeOn(schedulers.io())
                .observeOn(schedulers.ui())
                .subscribe({ loaded ->
                    val updItems = currentItems
                            .filter { it.authorId == loaded.first.first && it.avatar != loaded.second }
                    updItems.forEach {
                        it.avatar = loaded.second
                    }
                    articlesListView?.updateItems(updItems)
                }, {
                    errorHandler.handle(it)
                })
                .also { rxSubscriptions.add(it) }
    }

    fun refreshArticles() {
        loadArticles(1, true)
    }

    fun loadMore() {
        loadArticles(currentPage + 1, false)
    }

    fun onItemClick(item: NewsItem) {
        router.navigateTo(Screen.ArticleDetail().apply {
            articleId = item.id
            articleTitle = item.title
            articleAuthorNick = item.author
            articleDate = item.date
            articleImageUrl = item.imgUrl
            articleCommentsCount = item.commentsCount
        })
    }

    fun onItemLongClick(item: NewsItem) {
        articlesListView?.showItemDialogMenu(item)
    }

    fun copyLink(item: NewsItem) {
        Utils.copyToClipBoard("https://4pda.to/index.php?p=${item.id}")
    }

    fun shareLink(item: NewsItem) {
        Utils.shareText("https://4pda.to/index.php?p=${item.id}")
    }

    fun openProfile(item: NewsItem) {
        linkHandler.handle("https://4pda.to/forum/index.php?showuser=${item.authorId}", router)
    }

    fun createNote(item: NewsItem) {
        val url = "https://4pda.to/index.php?p=${item.id}"
        articlesListView?.showCreateNote(item.title.orEmpty(), url)
    }

    fun openSearch() {
        router.navigateTo(Screen.Search().apply {
            searchUrl = "https://4pda.to/?s="
        })
    }

    class Factory(
            private val newsRepository: NewsRepository,
            private val avatarRepository: AvatarRepository,
            private val authHolder: AuthHolder,
            private val router: TabRouter,
            private val linkHandler: ILinkHandler,
            private val errorHandler: IErrorHandler,
            private val schedulers: SchedulersProvider
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass != ArticlesListViewModel::class.java) throw IllegalArgumentException("Unknown ViewModel class")
            return ArticlesListViewModel(
                    newsRepository,
                    avatarRepository,
                    authHolder,
                    router,
                    linkHandler,
                    errorHandler,
                    schedulers
            ) as T
        }
    }
}
