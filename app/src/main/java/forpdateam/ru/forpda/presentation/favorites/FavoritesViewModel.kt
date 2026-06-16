package forpdateam.ru.forpda.presentation.favorites

import forpdateam.ru.forpda.presentation.BaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

import forpdateam.ru.forpda.common.ClipboardHelper
import forpdateam.ru.forpda.common.Utils
import forpdateam.ru.forpda.entity.app.TabNotification
import forpdateam.ru.forpda.entity.remote.favorites.FavData
import forpdateam.ru.forpda.entity.remote.favorites.FavItem
import forpdateam.ru.forpda.model.AuthHolder
import forpdateam.ru.forpda.model.data.remote.api.favorites.Sorting
import forpdateam.ru.forpda.model.interactors.CrossScreenInteractor
import forpdateam.ru.forpda.model.interactors.theme.ThemePrefetchService
import forpdateam.ru.forpda.model.preferences.MainPreferencesHolder
import forpdateam.ru.forpda.model.repository.events.EventsRepository
import forpdateam.ru.forpda.model.repository.faviorites.FavoritesRepository
import forpdateam.ru.forpda.model.repository.faviorites.FavoriteMarkReadEntry
import forpdateam.ru.forpda.model.repository.faviorites.FavoriteMarkReadProgress
import forpdateam.ru.forpda.model.repository.faviorites.FavoriteMarkReadResult
import forpdateam.ru.forpda.model.preferences.ListsPreferencesHolder
import forpdateam.ru.forpda.model.preferences.NotificationPreferencesHolder
import forpdateam.ru.forpda.presentation.IErrorHandler
import forpdateam.ru.forpda.presentation.ILinkHandler
import forpdateam.ru.forpda.presentation.Screen
import forpdateam.ru.forpda.presentation.TabRouter
import forpdateam.ru.forpda.presentation.theme.TopicUnreadOpenPolicy
import forpdateam.ru.forpda.model.interactors.theme.ThemeUseCase
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@HiltViewModel
@OptIn(kotlinx.coroutines.FlowPreview::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class FavoritesViewModel @Inject constructor(
        private val favoritesRepository: FavoritesRepository,
        private val eventsRepository: EventsRepository,
        private val listsPreferencesHolder: ListsPreferencesHolder,
        private val crossScreenInteractor: CrossScreenInteractor,
        private val router: TabRouter,
        private val linkHandler: ILinkHandler,
        private val errorHandler: IErrorHandler,
        private val clipboardHelper: ClipboardHelper,
        private val authHolder: AuthHolder,
        private val notificationPreferencesHolder: NotificationPreferencesHolder,
        private val themePrefetchService: ThemePrefetchService,
        private val mainPreferencesHolder: MainPreferencesHolder,
        private val themeUseCase: ThemeUseCase
) : BaseViewModel() {

    private var subscriptionsStarted = false
    private val localItems = mutableListOf<FavItem>()
    private var searchQuery = ""
    private var searchCatalogItems: List<FavItem>? = null
    private var searchCatalogSorting: Sorting? = null
    private var searchCatalogJob: Job? = null

    private var currentSt = 0
    private var loadAll = listsPreferencesHolder.getFavLoadAll()
    private var unreadTop = listsPreferencesHolder.getUnreadTop()
    private var sorting: Sorting = Sorting(
            listsPreferencesHolder.getSortingKey(),
            listsPreferencesHolder.getSortingOrder()
    )
    private val _sortingFlow = MutableStateFlow(sorting)
    val sortingFlow: StateFlow<Sorting> = _sortingFlow.asStateFlow()

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    private val _markAllReadRunning = MutableStateFlow(false)
    val markAllReadRunning: StateFlow<Boolean> = _markAllReadRunning.asStateFlow()

    private val _uiEvents = MutableSharedFlow<FavoritesUiEvent>()
    val uiEvents: SharedFlow<FavoritesUiEvent> = _uiEvents.asSharedFlow()

    private val _displayedItems = MutableStateFlow<List<FavItem>?>(null)
    val displayedItems: StateFlow<List<FavItem>?> = _displayedItems.asStateFlow()

    fun start() {
        if (subscriptionsStarted) return
        subscriptionsStarted = true

        scope.launch { _uiEvents.emit(FavoritesUiEvent.InitSorting(sorting)) }

        scope.launch {
            listsPreferencesHolder.observeFavLoadAllFlow().collect { loadAll = it }
        }

        scope.launch {
            listsPreferencesHolder.observeShowDotFlow().collect {
                scope.launch { _uiEvents.emit(FavoritesUiEvent.SetShowDot(it)) }
            }
        }

        scope.launch {
            listsPreferencesHolder.observeFavShowUnreadBadgeFlow().collect {
                scope.launch { _uiEvents.emit(FavoritesUiEvent.SetShowUnreadIndicators(it)) }
            }
        }

        scope.launch {
            listsPreferencesHolder.observeUnreadTopFlow().collect {
                unreadTop = it
                scope.launch { _uiEvents.emit(FavoritesUiEvent.SetUnreadTop(it)) }
            }
        }

        scope.launch {
            eventsRepository.observeEventsTab()
                    .debounce(200L)
                    .collect { handleEvent(it) }
        }

        scope.launch {
            combine(_sortingFlow, listsPreferencesHolder.observeUnreadTopFlow()) { currentSorting, currentUnreadTop ->
                currentSorting to currentUnreadTop
            }.flatMapLatest { (currentSorting, currentUnreadTop) ->
                favoritesRepository.observeItems(currentSorting, currentUnreadTop)
            }.catch { errorHandler.handle(it) }
                    .collect { list ->
                        localItems.replaceWith(list)
                        publishDisplayed()
                    }
        }

        scope.launch {
            runCatching { favoritesRepository.loadCache(sorting, unreadTop) }
                    .onSuccess {
                        localItems.replaceWith(it)
                        publishDisplayed()
                    }
                    .onFailure { errorHandler.handle(it) }
        }

        scope.launch {
            crossScreenInteractor.observeTopic().collect { topicId ->
                markRead(topicId)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
    }

    suspend fun updateSorting(key: String, order: String) {
        // CRITICAL: Sorting is a Java class without equals() — use NEW instance to trigger StateFlow emission
        sorting = Sorting(key, order)
        _sortingFlow.value = sorting
        listsPreferencesHolder.setSortingKey(key)
        listsPreferencesHolder.setSortingOrder(order)
        invalidateSearchCatalog()
        loadFavorites(currentSt)
    }

    fun refresh() {
        invalidateSearchCatalog()
        loadFavorites(0, forceRefresh = true)
    }

    fun searchLocal(query: String) {
        searchQuery = query.trim()
        if (searchQuery.isEmpty()) {
            invalidateSearchCatalog()
            publishDisplayed()
            return
        }
        publishDisplayed()
        if (!loadAll) {
            ensureSearchCatalogLoaded()
        }
    }

    private var loadJob: Job? = null

    fun loadFavorites(pageNum: Int, forceRefresh: Boolean = false) {
        if (!authHolder.get().isAuth()) {
            scope.launch { _uiEvents.emit(FavoritesUiEvent.ShowNeedAuth) }
            return
        }
        loadJob?.cancel()
        currentSt = pageNum
        if (forceRefresh) {
            invalidateSearchCatalog()
        }
        // Log 14_06-19: re-arm the server mark-read de-dup cache on every favorites load so
        // that re-visiting a topic after Inspector re-asserts unread state will re-fire
        // GET view=getlastpost on the next bottom-of-topic exit.
        themeUseCase.resetAllServerMarkReadDedup()
        loadJob = scope.launch {
            _refreshing.value = true
            runCatching {
                favoritesRepository.loadFavorites(currentSt, loadAll, sorting, forceRefresh)
            }.onSuccess {
                scope.launch { _uiEvents.emit(FavoritesUiEvent.OnLoadFavorites(it)) }
            }.onFailure {
                var message: String? = null
                errorHandler.handle(it) { _, handledMessage -> message = handledMessage }
                _uiEvents.emit(FavoritesUiEvent.ShowLoadError(message))
            }
            _refreshing.value = false
        }
    }

    private fun markRead(topicId: Int) {
        scope.launch {
            runCatching {
                favoritesRepository.markRead(topicId)
            }
                    .onFailure { errorHandler.handle(it) }
        }
    }

    private fun handleEvent(event: TabNotification) {
        scope.launch {
            runCatching { favoritesRepository.handleEvent(event) }
                    .onFailure { errorHandler.handle(it) }
        }
    }

    fun markAllFavoritesRead() {
        if (_markAllReadRunning.value) return
        val entries = currentMarkReadEntries()
        if (entries.isEmpty()) {
            scope.launch { _uiEvents.emit(FavoritesUiEvent.OnMarkAllRead(FavoriteMarkReadResult(emptySet(), emptySet()))) }
            return
        }
        scope.launch {
            _markAllReadRunning.value = true
            runCatching {
                favoritesRepository.markFavoriteTopicsRead(entries) { progress ->
                    _uiEvents.emit(FavoritesUiEvent.OnMarkAllReadProgress(progress))
                }
            }.onSuccess { result ->
                _uiEvents.emit(FavoritesUiEvent.OnMarkAllRead(result))
            }.onFailure {
                errorHandler.handle(it)
            }
            _markAllReadRunning.value = false
        }
    }

    fun getMarkAllFavoritesReadCount(): Int = currentMarkReadEntries().size

    fun onItemDisplayed(item: FavItem) {
        // Do not prefetch on bind/display: 4pda `view=getnewpost` can advance server read state.
    }

    fun onItemTouchDown(item: FavItem) {
        // Touch can be cancelled; only an actual open may ask 4pda for unread resolution.
    }

    fun onItemClick(item: FavItem) {
        if (!item.isForum && item.topicId > 0) {
            themePrefetchService.cancelPrefetch(exceptTopicId = item.topicId)
        }
        val args = mapOf<String, String>(
                Screen.ARG_TITLE to item.topicTitle.orEmpty()
        )
        if (item.isForum) {
            linkHandler.handle("https://4pda.to/forum/index.php?showforum=" + item.forumId, router, args)
        } else {
            router.navigateTo(FavoritesTopicNavigationPolicy.buildThemeScreen(item))
        }
    }

    private fun prefetchFavoriteTopic(item: FavItem) {
        if (item.isForum || item.topicId <= 0) return
        val hints = FavoritesTopicNavigationPolicy.buildListHints(item)
        val url = FavoritesTopicNavigationPolicy.resolvePrefetchUrl(
                item,
                mainPreferencesHolder.getTopicOpenTarget()
        ) ?: return
        val topicOpenTarget = mainPreferencesHolder.getTopicOpenTarget()
        themePrefetchService.prefetchTopic(
                item.topicId,
                url,
                openFromUnreadListHint = TopicUnreadOpenPolicy.prefetchParserHint(hints, url, topicOpenTarget),
        )
    }

    fun onItemLongClick(item: FavItem) {
        scope.launch { _uiEvents.emit(FavoritesUiEvent.ShowItemDialogMenu(item)) }
    }

    fun copyLink(item: FavItem) {
        if (item.isForum) {
            clipboardHelper.copyToClipboard("https://4pda.to/forum/index.php?showforum=" + Integer.toString(item.forumId))
        } else {
            clipboardHelper.copyToClipboard("https://4pda.to/forum/index.php?showtopic=" + Integer.toString(item.topicId))
        }
    }

    fun openAttachments(item: FavItem) {
        linkHandler.handle("https://4pda.to/forum/index.php?act=attach&code=showtopic&tid=" + item.topicId, router)
    }

    fun openForum(item: FavItem) {
        linkHandler.handle("https://4pda.to/forum/index.php?showforum=" + item.forumId, router)
    }

    fun changeFav(action: Int, type: String?, favId: Int) {
        scope.launch {
            runCatching { favoritesRepository.editFavorites(action, favId, favId, type) }
                    .onSuccess { ok ->
                        scope.launch { _uiEvents.emit(FavoritesUiEvent.OnChangeFav(ok)) }
                        invalidateSearchCatalog()
                        loadFavorites(currentSt)
                    }
                    .onFailure { errorHandler.handle(it) }
        }
    }

    fun showSubscribeDialog(item: FavItem) {
        scope.launch { _uiEvents.emit(FavoritesUiEvent.ShowSubscribeDialog(item)) }
    }

    fun isTopicMuted(item: FavItem): Boolean = notificationPreferencesHolder.isTopicMuted(item.topicId)

    /** Локально (на устройстве) переключить уведомления для темы избранного. */
    fun toggleTopicMute(item: FavItem) {
        if (item.isForum || item.topicId <= 0) return
        val nowMuted = notificationPreferencesHolder.toggleTopicMute(item.topicId)
        scope.launch { _uiEvents.emit(FavoritesUiEvent.OnToggleMute(item, nowMuted)) }
    }

    private fun MutableList<FavItem>.replaceWith(items: List<FavItem>) {
        clear()
        addAll(items)
    }

    private fun publishDisplayed() {
        val items = filterDisplayedItems()
        _displayedItems.value = items
        scope.launch { _uiEvents.emit(FavoritesUiEvent.OnShowFavorite(items)) }
    }

    private fun filterDisplayedItems(): List<FavItem> {
        val query = searchQuery
        if (query.isEmpty()) {
            return localItems.toList()
        }
        return itemsForSearch().filter { item ->
            item.topicTitle.orEmpty().contains(query, ignoreCase = true)
        }
    }

    private fun currentDisplayedItems(): List<FavItem> = filterDisplayedItems()

    private fun itemsForSearch(): List<FavItem> {
        if (loadAll) {
            return localItems.toList()
        }
        return searchCatalogItems ?: localItems.toList()
    }

    private fun invalidateSearchCatalog() {
        searchCatalogJob?.cancel()
        searchCatalogJob = null
        searchCatalogItems = null
        searchCatalogSorting = null
    }

    private fun ensureSearchCatalogLoaded() {
        if (searchCatalogItems != null && searchCatalogSorting == sorting) {
            return
        }
        searchCatalogJob?.cancel()
        searchCatalogJob = scope.launch {
            runCatching { favoritesRepository.fetchAllFavoritesForSearch(sorting) }
                    .onSuccess { items ->
                        searchCatalogItems = items
                        searchCatalogSorting = sorting
                        if (searchQuery.isNotEmpty()) {
                            publishDisplayed()
                        }
                    }
                    .onFailure { errorHandler.handle(it) }
        }
    }

    private fun currentMarkReadEntries(): List<FavoriteMarkReadEntry> =
            currentDisplayedItems()
                    .asSequence()
                    .filter { item -> item.isNew && !item.isForum && item.topicId > 0 }
                    .distinctBy { item -> item.topicId }
                    .map { item -> FavoriteMarkReadEntry(item.favId, item.topicId) }
                    .toList()
}

sealed class FavoritesUiEvent {
    data class InitSorting(val sorting: Sorting) : FavoritesUiEvent()
    data class SetShowDot(val show: Boolean) : FavoritesUiEvent()
    data class SetShowUnreadIndicators(val show: Boolean) : FavoritesUiEvent()
    data class SetUnreadTop(val unreadTop: Boolean) : FavoritesUiEvent()
    data class OnShowFavorite(val list: List<FavItem>) : FavoritesUiEvent()
    data class OnLoadFavorites(val data: FavData) : FavoritesUiEvent()
    data class OnMarkAllReadProgress(val progress: FavoriteMarkReadProgress) : FavoritesUiEvent()
    data class OnMarkAllRead(val result: FavoriteMarkReadResult) : FavoritesUiEvent()
    data class ShowItemDialogMenu(val item: FavItem) : FavoritesUiEvent()
    data class OnChangeFav(val result: Boolean) : FavoritesUiEvent()
    data class ShowSubscribeDialog(val item: FavItem) : FavoritesUiEvent()
    data class OnToggleMute(val item: FavItem, val nowMuted: Boolean) : FavoritesUiEvent()
    data class ShowLoadError(val message: String?) : FavoritesUiEvent()
    object ShowNeedAuth : FavoritesUiEvent()
}
