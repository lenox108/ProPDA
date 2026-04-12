package forpdateam.ru.forpda.presentation.forum

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import forpdateam.ru.forpda.common.Utils
import forpdateam.ru.forpda.entity.remote.forum.ForumItemTree
import forpdateam.ru.forpda.model.data.remote.api.favorites.FavoritesApi
import forpdateam.ru.forpda.model.repository.faviorites.FavoritesRepository
import forpdateam.ru.forpda.model.repository.forum.ForumRepository
import forpdateam.ru.forpda.presentation.IErrorHandler
import forpdateam.ru.forpda.presentation.Screen
import forpdateam.ru.forpda.presentation.TabRouter
import io.reactivex.disposables.CompositeDisposable
import kotlinx.coroutines.launch

class ForumViewModel(
        private val forumRepository: ForumRepository,
        private val favoritesRepository: FavoritesRepository,
        private val router: TabRouter,
        private val errorHandler: IErrorHandler
) : ViewModel() {

    @Volatile
    private var forumView: ForumView? = null

    fun attachView(view: ForumView) {
        forumView = view
    }

    fun detachView() {
        forumView = null
    }

    private val rxSubscriptions = CompositeDisposable()
    private var subscriptionsStarted = false

    var targetForumId = -1

    fun start() {
        if (subscriptionsStarted) return
        subscriptionsStarted = true
        getCacheForums()
        loadForums()
    }

    override fun onCleared() {
        rxSubscriptions.clear()
        super.onCleared()
    }

    fun loadForums() {
        forumRepository
                .getForums()
                .doOnSubscribe { forumView?.setRefreshing(true) }
                .doAfterTerminate { forumView?.setRefreshing(false) }
                .subscribe({
                    forumView?.showForums(it)
                    scrollToTarget()
                    saveCacheForums(it)
                }, {
                    errorHandler.handle(it)
                })
                .also { rxSubscriptions.add(it) }
    }

    private fun getCacheForums() {
        forumRepository
                .getCache()
                .doOnSubscribe { forumView?.setRefreshing(true) }
                .doAfterTerminate { forumView?.setRefreshing(false) }
                .subscribe({ it ->
                    if (it.forums == null) {
                        loadForums()
                    } else {
                        forumView?.showForums(it)
                        scrollToTarget()
                    }
                }, {
                    errorHandler.handle(it)
                })
                .also { rxSubscriptions.add(it) }
    }

    private fun scrollToTarget() {
        if (targetForumId != -1) {
            forumView?.scrollToForum(targetForumId)
            targetForumId = -1
        }
    }

    private fun saveCacheForums(rootForum: ForumItemTree) {
        forumRepository
                .saveCache(rootForum)
                .doOnTerminate { forumView?.setRefreshing(true) }
                .doAfterTerminate { forumView?.setRefreshing(false) }
                .subscribe({

                }, {
                    errorHandler.handle(it)
                })
                .also { rxSubscriptions.add(it) }
    }

    fun markRead(id: Int) {
        forumRepository
                .markRead(id)
                .subscribe({
                    forumView?.onMarkRead()
                }, {
                    errorHandler.handle(it)
                })
                .also { rxSubscriptions.add(it) }
    }

    fun markAllRead() {
        forumRepository
                .markAllRead()
                .subscribe({
                    forumView?.onMarkAllRead()
                }, {
                    errorHandler.handle(it)
                })
                .also { rxSubscriptions.add(it) }
    }

    fun addToFavorite(forumId: Int, subType: String) {
        viewModelScope.launch {
            runCatching {
                favoritesRepository.editFavorites(FavoritesApi.ACTION_ADD_FORUM, -1, forumId, subType)
            }.onSuccess { forumView?.onAddToFavorite(it) }
                    .onFailure { errorHandler.handle(it) }
        }
    }

    fun copyLink(item: ForumItemTree) {
        Utils.copyToClipBoard("https://4pda.to/forum/index.php?showforum=${item.id}")
    }

    fun navigateToForum(item: ForumItemTree) {
        router.navigateTo(Screen.Topics().apply {
            forumId = item.id
        })
    }

    fun navigateToSearch(item: ForumItemTree) {
        router.navigateTo(Screen.Search().apply {
            searchUrl = "https://4pda.to/forum/index.php?act=search&source=all&forums%5B%5D=${item.id}"
        })
    }

    class Factory(
            private val forumRepository: ForumRepository,
            private val favoritesRepository: FavoritesRepository,
            private val router: TabRouter,
            private val errorHandler: IErrorHandler
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass != ForumViewModel::class.java) throw IllegalArgumentException("Unknown ViewModel class")
            return ForumViewModel(forumRepository, favoritesRepository, router, errorHandler) as T
        }
    }
}
