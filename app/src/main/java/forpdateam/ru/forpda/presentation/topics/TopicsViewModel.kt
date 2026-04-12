package forpdateam.ru.forpda.presentation.topics

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import forpdateam.ru.forpda.common.topicUrlWithUnreadIfPlainOpen
import forpdateam.ru.forpda.entity.remote.topics.TopicItem
import forpdateam.ru.forpda.entity.remote.topics.TopicsData
import forpdateam.ru.forpda.model.data.remote.api.favorites.FavoritesApi
import forpdateam.ru.forpda.model.interactors.CrossScreenInteractor
import forpdateam.ru.forpda.model.repository.faviorites.FavoritesRepository
import forpdateam.ru.forpda.model.repository.forum.ForumRepository
import forpdateam.ru.forpda.model.repository.topics.TopicsRepository
import forpdateam.ru.forpda.presentation.IErrorHandler
import forpdateam.ru.forpda.presentation.ILinkHandler
import forpdateam.ru.forpda.presentation.Screen
import forpdateam.ru.forpda.presentation.TabRouter
import io.reactivex.disposables.CompositeDisposable
import kotlinx.coroutines.launch

class TopicsViewModel(
        private val topicsRepository: TopicsRepository,
        private val forumRepository: ForumRepository,
        private val favoritesRepository: FavoritesRepository,
        private val crossScreenInteractor: CrossScreenInteractor,
        private val router: TabRouter,
        private val linkHandler: ILinkHandler,
        private val errorHandler: IErrorHandler
) : ViewModel() {

    @Volatile
    private var topicsView: TopicsView? = null

    fun attachView(view: TopicsView) {
        topicsView = view
    }

    fun detachView() {
        topicsView = null
    }

    private val rxSubscriptions = CompositeDisposable()
    private var subscriptionsStarted = false

    var id = 0
    private var currentSt = 0
    var currentData: TopicsData? = null

    fun start() {
        if (subscriptionsStarted) return
        subscriptionsStarted = true
        crossScreenInteractor
                .observeTopic()
                .subscribe {
                    markRead(it)
                }
                .also { rxSubscriptions.add(it) }
        loadTopics()
    }

    override fun onCleared() {
        rxSubscriptions.clear()
        super.onCleared()
    }

    fun loadTopics() {
        topicsRepository
                .getTopics(id, currentSt)
                .doOnSubscribe { topicsView?.setRefreshing(true) }
                .doAfterTerminate { topicsView?.setRefreshing(false) }
                .subscribe({
                    currentData = it
                    topicsView?.showTopics(it)
                }, {
                    errorHandler.handle(it)
                })
                .also { rxSubscriptions.add(it) }
    }

    fun loadPage(st: Int) {
        currentSt = st
        loadTopics()
    }

    fun addForumToFavorite(forumId: Int, subType: String) {
        viewModelScope.launch {
            runCatching {
                favoritesRepository.editFavorites(FavoritesApi.ACTION_ADD_FORUM, -1, forumId, subType)
            }.onSuccess { topicsView?.onAddToFavorite(it) }
                    .onFailure { errorHandler.handle(it) }
        }
    }

    fun addTopicToFavorite(topicId: Int, subType: String) {
        viewModelScope.launch {
            runCatching {
                favoritesRepository.editFavorites(FavoritesApi.ACTION_ADD, -1, topicId, subType)
            }.onSuccess { topicsView?.onAddToFavorite(it) }
                    .onFailure { errorHandler.handle(it) }
        }
    }

    fun markRead() {
        forumRepository
                .markRead(id)
                .subscribe({
                    topicsView?.onMarkRead()
                }, {
                    errorHandler.handle(it)
                })
                .also { rxSubscriptions.add(it) }
    }

    private fun markRead(id: Int) {
        currentData?.also { currentData ->
            currentData.topicItems.firstOrNull { it.id == id }?.isNew = false
            currentData.pinnedItems.firstOrNull { it.id == id }?.isNew = false
            topicsView?.updateList()
        }
    }

    fun openForum() {
        router.navigateTo(Screen.Forum().apply {
            forumId = id
        })
    }

    fun openSearch() {
        router.navigateTo(Screen.Search().apply {
            searchUrl = "https://4pda.to/forum/index.php?act=search&source=all&forums%5B%5D=$id"
        })
    }

    fun openTopicForum() {
        currentData?.let {
            linkHandler.handle("https://4pda.to/forum/index.php?showforum=${it.id}", router)
        }
    }

    fun onItemClick(item: TopicItem) {
        if (item.isAnnounce) {
            linkHandler.handle(item.announceUrl, router, mapOf(
                    Screen.ARG_TITLE to item.title
            ))
            return
        }
        if (item.isForum) {
            linkHandler.handle("https://4pda.to/forum/index.php?showforum=${item.id}", router)
            return
        }
        val topicUrl = topicUrlWithUnreadIfPlainOpen(
                Uri.parse("https://4pda.to/forum/index.php?showtopic=${item.id}")
        )
        linkHandler.handle(topicUrl, router, mapOf(
                Screen.ARG_TITLE to item.title
        ))
    }

    fun onItemLongClick(item: TopicItem) {
        topicsView?.showItemDialogMenu(item)
    }

    class Factory(
            private val topicsRepository: TopicsRepository,
            private val forumRepository: ForumRepository,
            private val favoritesRepository: FavoritesRepository,
            private val crossScreenInteractor: CrossScreenInteractor,
            private val router: TabRouter,
            private val linkHandler: ILinkHandler,
            private val errorHandler: IErrorHandler
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass != TopicsViewModel::class.java) throw IllegalArgumentException("Unknown ViewModel class")
            return TopicsViewModel(
                    topicsRepository,
                    forumRepository,
                    favoritesRepository,
                    crossScreenInteractor,
                    router,
                    linkHandler,
                    errorHandler
            ) as T
        }
    }
}
