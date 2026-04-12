package forpdateam.ru.forpda.presentation.favorites

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import forpdateam.ru.forpda.common.Utils
import forpdateam.ru.forpda.entity.app.TabNotification
import forpdateam.ru.forpda.entity.remote.favorites.FavItem
import forpdateam.ru.forpda.model.data.remote.api.favorites.Sorting
import forpdateam.ru.forpda.model.interactors.CrossScreenInteractor
import forpdateam.ru.forpda.model.repository.events.EventsRepository
import forpdateam.ru.forpda.model.repository.faviorites.FavoritesRepository
import forpdateam.ru.forpda.model.preferences.ListsPreferencesHolder
import forpdateam.ru.forpda.presentation.IErrorHandler
import forpdateam.ru.forpda.presentation.ILinkHandler
import forpdateam.ru.forpda.presentation.Screen
import forpdateam.ru.forpda.presentation.TabRouter
import io.reactivex.disposables.CompositeDisposable
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class FavoritesViewModel(
        private val favoritesRepository: FavoritesRepository,
        private val eventsRepository: EventsRepository,
        private val listsPreferencesHolder: ListsPreferencesHolder,
        private val crossScreenInteractor: CrossScreenInteractor,
        private val router: TabRouter,
        private val linkHandler: ILinkHandler,
        private val errorHandler: IErrorHandler
) : ViewModel() {

    @Volatile
    private var favoritesView: FavoritesView? = null

    fun attachView(view: FavoritesView) {
        favoritesView = view
    }

    fun detachView() {
        favoritesView = null
    }

    private val rxSubscriptions = CompositeDisposable()
    private var subscriptionsStarted = false

    private var currentSt = 0
    private var loadAll = listsPreferencesHolder.getFavLoadAll()
    private var sorting: Sorting = Sorting(
            listsPreferencesHolder.getSortingKey(),
            listsPreferencesHolder.getSortingOrder()
    )

    fun start() {
        if (subscriptionsStarted) return
        subscriptionsStarted = true

        favoritesView?.initSorting(sorting)

        listsPreferencesHolder
                .observeFavLoadAll()
                .subscribe { loadAll = it }
                .also { rxSubscriptions.add(it) }

        listsPreferencesHolder
                .observeShowDot()
                .subscribe {
                    favoritesView?.setShowDot(it)
                }
                .also { rxSubscriptions.add(it) }

        listsPreferencesHolder
                .observeUnreadTop()
                .subscribe {
                    favoritesView?.setUnreadTop(it)
                }
                .also { rxSubscriptions.add(it) }

        viewModelScope.launch {
            eventsRepository.observeEventsTab()
                    .collect {
                        Log.e("testtabnotify", "fav observeEventsTab $it")
                        handleEvent(it)
                    }
        }

        viewModelScope.launch {
            favoritesRepository.observeItems()
                    .catch { errorHandler.handle(it) }
                    .collect { list ->
                        Log.d("kokos", "observeContacts ${list.size} ${list.joinToString("; ") { "${it.topicId}:${it.isNew}" }}")
                        favoritesView?.onShowFavorite(list)
                    }
        }

        viewModelScope.launch {
            runCatching { favoritesRepository.loadCache() }
                    .onSuccess { favoritesView?.onShowFavorite(it) }
                    .onFailure { errorHandler.handle(it) }
        }

        crossScreenInteractor
                .observeTopic()
                .subscribe {
                    markRead(it)
                }
                .also { rxSubscriptions.add(it) }
    }

    override fun onCleared() {
        rxSubscriptions.clear()
        super.onCleared()
    }

    fun updateSorting(key: String, order: String) {
        sorting.also {
            it.key = key
            it.order = order
        }
        listsPreferencesHolder.setSortingKey(key)
        listsPreferencesHolder.setSortingOrder(order)
        loadFavorites(currentSt)
    }

    fun refresh() {
        loadFavorites(0)
    }

    fun loadFavorites(pageNum: Int) {
        currentSt = pageNum
        viewModelScope.launch {
            favoritesView?.setRefreshing(true)
            runCatching {
                favoritesRepository.loadFavorites(currentSt, loadAll, sorting)
            }.onSuccess { favoritesView?.onLoadFavorites(it) }
                    .onFailure { errorHandler.handle(it) }
            favoritesView?.setRefreshing(false)
        }
    }

    private fun markRead(topicId: Int) {
        viewModelScope.launch {
            runCatching { favoritesRepository.markRead(topicId) }
                    .onFailure { errorHandler.handle(it) }
        }
    }

    private fun handleEvent(event: TabNotification) {
        viewModelScope.launch {
            runCatching { favoritesRepository.handleEvent(event) }
                    .onSuccess { Log.e("testtabnotify", "fav handleEvent $it") }
                    .onFailure { errorHandler.handle(it) }
        }
    }

    fun markAllFavoritesRead() {
        viewModelScope.launch {
            runCatching { favoritesRepository.markAllFavoritesRead() }
                    .onSuccess { favoritesView?.onMarkAllRead() }
                    .onFailure { errorHandler.handle(it) }
        }
    }

    fun onItemClick(item: FavItem) {
        val args = mapOf<String, String>(
                Screen.ARG_TITLE to item.topicTitle.orEmpty()
        )
        if (item.isForum) {
            linkHandler.handle("https://4pda.to/forum/index.php?showforum=" + item.forumId, router, args)
        } else {
            linkHandler.handle("https://4pda.to/forum/index.php?showtopic=" + item.topicId + "&view=getnewpost", router, args)
        }
    }

    fun onItemLongClick(item: FavItem) {
        favoritesView?.showItemDialogMenu(item)
    }

    fun copyLink(item: FavItem) {
        if (item.isForum) {
            Utils.copyToClipBoard("https://4pda.to/forum/index.php?showforum=" + Integer.toString(item.forumId))
        } else {
            Utils.copyToClipBoard("https://4pda.to/forum/index.php?showtopic=" + Integer.toString(item.topicId))
        }
    }

    fun openAttachments(item: FavItem) {
        linkHandler.handle("https://4pda.to/forum/index.php?act=attach&code=showtopic&tid=" + item.topicId, router)
    }

    fun openForum(item: FavItem) {
        linkHandler.handle("https://4pda.to/forum/index.php?showforum=" + item.forumId, router)
    }

    fun changeFav(action: Int, type: String?, favId: Int) {
        viewModelScope.launch {
            runCatching { favoritesRepository.editFavorites(action, favId, favId, type) }
                    .onSuccess { ok ->
                        favoritesView?.onChangeFav(ok)
                        loadFavorites(currentSt)
                    }
                    .onFailure { errorHandler.handle(it) }
        }
    }

    fun showSubscribeDialog(item: FavItem) {
        favoritesView?.showSubscribeDialog(item)
    }

    class Factory(
            private val favoritesRepository: FavoritesRepository,
            private val eventsRepository: EventsRepository,
            private val listsPreferencesHolder: ListsPreferencesHolder,
            private val crossScreenInteractor: CrossScreenInteractor,
            private val router: TabRouter,
            private val linkHandler: ILinkHandler,
            private val errorHandler: IErrorHandler
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass != FavoritesViewModel::class.java) throw IllegalArgumentException("Unknown ViewModel class")
            return FavoritesViewModel(
                    favoritesRepository,
                    eventsRepository,
                    listsPreferencesHolder,
                    crossScreenInteractor,
                    router,
                    linkHandler,
                    errorHandler
            ) as T
        }
    }
}
