package forpdateam.ru.forpda.presentation.favorites

import forpdateam.ru.forpda.presentation.BaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

import forpdateam.ru.forpda.common.ClipboardHelper
import forpdateam.ru.forpda.common.Utils
import forpdateam.ru.forpda.entity.remote.favorites.FavData
import forpdateam.ru.forpda.entity.remote.favorites.FavItem
import forpdateam.ru.forpda.entity.remote.others.pagination.Pagination
import forpdateam.ru.forpda.model.AuthHolder
import forpdateam.ru.forpda.model.data.remote.api.favorites.FavoritesApi
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
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.ceil

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
    // Размер страницы для клиентской пагинации. Сохраняется между сессиями, чтобы холодный
    // старт считал страницы по реальному размеру, а не по дефолту (иначе число страниц
    // «прыгает» после первого refresh).
    private var clientPerPage = listsPreferencesHolder.getFavPerPage()
    private var hiddenTopicIds: Set<Int> = listsPreferencesHolder.getHiddenTopicIds()
    private var hiddenForumIds: Set<Int> = listsPreferencesHolder.getHiddenForumIds()
    // Темы с локально заглушёнными уведомлениями (device-side). Драйвит иконку «уведомления отключены» в строке.
    private var mutedTopicIds: Set<Int> = notificationPreferencesHolder.getMutedTopics()
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

    // Пагинация считается локально по видимым (не скрытым) темам.
    private val _pagination = MutableStateFlow<Pagination?>(null)
    val pagination: StateFlow<Pagination?> = _pagination.asStateFlow()

    // Флаги отображения строк (точка/счётчик непрочитанного, «непрочитанные вверху»).
    // ОБЯЗАТЕЛЬНО StateFlow, а не одноразовые события _uiEvents (replay=0): адаптер
    // пересоздаётся в каждом onViewCreated со значениями по умолчанию (showDot=false),
    // а start() отрабатывает один раз (subscriptionsStarted) и DataStore-поток без изменения
    // значения повторно не эмитит — одноразовое событие терялось бы при пересоздании view
    // (поворот, переключение вкладок), и точки в избранном пропадали навсегда. StateFlow
    // всегда переиграет текущее значение свежей подписке (паритет с sortingFlow/Историей).
    private val _showDot = MutableStateFlow(listsPreferencesHolder.getShowDot())
    val showDotFlow: StateFlow<Boolean> = _showDot.asStateFlow()

    private val _showUnreadIndicators = MutableStateFlow(listsPreferencesHolder.getFavShowUnreadBadge())
    val showUnreadIndicatorsFlow: StateFlow<Boolean> = _showUnreadIndicators.asStateFlow()

    private val _unreadTopFlow = MutableStateFlow(listsPreferencesHolder.getUnreadTop())
    val unreadTopFlow: StateFlow<Boolean> = _unreadTopFlow.asStateFlow()

    fun start() {
        if (subscriptionsStarted) return
        subscriptionsStarted = true

        scope.launch { _uiEvents.emit(FavoritesUiEvent.InitSorting(sorting)) }

        scope.launch {
            listsPreferencesHolder.observeFavLoadAllFlow().collect {
                loadAll = it
                publishDisplayed()
            }
        }

        scope.launch {
            listsPreferencesHolder.observeHiddenTopicIdsFlow().collect {
                hiddenTopicIds = it
                publishDisplayed()
            }
        }

        scope.launch {
            listsPreferencesHolder.observeHiddenForumIdsFlow().collect {
                hiddenForumIds = it
                publishDisplayed()
            }
        }

        scope.launch {
            // Живо обновляем иконку «уведомления отключены» при заглушении/разглушении
            // темы из любого места (Избранное, экран темы и т.п.).
            notificationPreferencesHolder.mutedTopicsFlow().collect {
                mutedTopicIds = it
                publishDisplayed()
            }
        }

        scope.launch {
            listsPreferencesHolder.observeShowDotFlow().collect {
                _showDot.value = it
            }
        }

        scope.launch {
            listsPreferencesHolder.observeFavShowUnreadBadgeFlow().collect {
                _showUnreadIndicators.value = it
            }
        }

        scope.launch {
            listsPreferencesHolder.observeUnreadTopFlow().collect {
                unreadTop = it
                _unreadTopFlow.value = it
            }
        }

        // Событийный пересчёт избранного здесь НЕ подписываем: им владеет процесс-широкий
        // FavoritesInteractor (подписан из MainViewModel до создания любого экрана). Вторая
        // параллельная подписка заставляла каждую TabNotification дважды проходить
        // handleEventTransaction (два конкурентных снапшота и две полные перезаписи кэша) —
        // лишняя работа и лишнее окно для гонок с markRead. Список сюда всё равно приходит
        // через favoritesRepository.observeItems (StateFlow кэша).

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

    /** Локальный переход на страницу (st — смещение элемента), без сетевого запроса. */
    fun selectClientPage(st: Int) {
        currentSt = st
        publishDisplayed()
        scope.launch { _uiEvents.emit(FavoritesUiEvent.ScrollToTop) }
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
        // Favorites refresh/load is a pure list operation and MUST NOT touch the theme
        // server mark-read de-dup cache. Previously this cleared the cache on every load,
        // which re-armed `GET view=getlastpost` so an already-marked topic was re-sent to
        // the server (marking it read) on the next last-page render — even when the user
        // only pulled-to-refresh the list and never (re)read the topic. The de-dup already
        // has its own TTL (SERVER_MARK_READ_DEDUP_TTL_MS) that re-arms on genuine revisits.
        loadJob = scope.launch {
            _refreshing.value = true
            runCatching {
                // Грузим ВЕСЬ список (all=true), чтобы пагинацию можно было считать
                // на клиенте по видимым (не скрытым) темам.
                favoritesRepository.loadFavorites(0, true, sorting, forceRefresh)
            }.onSuccess { data ->
                data.pagination.perPage.takeIf { it > 0 }?.let {
                    clientPerPage = it
                    listsPreferencesHolder.setFavPerPage(it)
                }
                scope.launch { _uiEvents.emit(FavoritesUiEvent.OnLoadFavorites(data)) }
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

    fun markAllFavoritesRead() = runMarkReadEntries(currentMarkReadEntries())

    /** Пакетно отметить прочитанными выбранные темы. */
    fun markFavoritesRead(items: List<FavItem>) = runMarkReadEntries(
            items.asSequence()
                    .filter { item -> !item.isForum && item.topicId > 0 }
                    .distinctBy { item -> item.topicId }
                    .map { item -> FavoriteMarkReadEntry(item.favId, item.topicId) }
                    .toList()
    )

    private fun runMarkReadEntries(entries: List<FavoriteMarkReadEntry>) {
        if (_markAllReadRunning.value) return
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

    /** Пакетно удалить выбранные элементы из избранного (на сервере), затем один перезапрос списка. */
    fun deleteFavorites(items: List<FavItem>) {
        val targets = items.filter { it.favId > 0 }
        if (targets.isEmpty()) return
        scope.launch {
            var ok = true
            targets.forEach { item ->
                runCatching { favoritesRepository.editFavorites(FavoritesApi.ACTION_DELETE, item.favId, item.favId, null) }
                        .onFailure { ok = false; errorHandler.handle(it) }
            }
            invalidateSearchCatalog()
            loadFavorites(currentSt)
            _uiEvents.emit(FavoritesUiEvent.OnChangeFav(ok))
        }
    }

    /** Пакетно закрепить/открепить выбранные элементы, затем один перезапрос списка. */
    fun setFavoritesPinned(items: List<FavItem>, pin: Boolean) {
        val targets = items.filter { it.favId > 0 && it.isPin != pin }
        if (targets.isEmpty()) {
            scope.launch { _uiEvents.emit(FavoritesUiEvent.OnChangeFav(true)) }
            return
        }
        scope.launch {
            var ok = true
            targets.forEach { item ->
                runCatching {
                    favoritesRepository.editFavorites(
                            FavoritesApi.ACTION_EDIT_PIN_STATE,
                            item.favId,
                            item.favId,
                            if (pin) "pin" else "unpin"
                    )
                }.onFailure { ok = false; errorHandler.handle(it) }
            }
            invalidateSearchCatalog()
            loadFavorites(currentSt)
            _uiEvents.emit(FavoritesUiEvent.OnChangeFav(ok))
        }
    }

    /** Пакетно скрыть/показать выбранные элементы локально (одна запись на тип). */
    fun setFavoritesHidden(items: List<FavItem>, hidden: Boolean) {
        if (items.isEmpty()) return
        scope.launch {
            val topicIds = items.filter { !it.isForum && it.topicId > 0 }.map { it.topicId }
            val forumIds = items.filter { it.isForum && it.forumId > 0 }.map { it.forumId }
            val newTopics = hiddenTopicIds.toMutableSet().apply { if (hidden) addAll(topicIds) else removeAll(topicIds) }
            val newForums = hiddenForumIds.toMutableSet().apply { if (hidden) addAll(forumIds) else removeAll(forumIds) }
            hiddenTopicIds = newTopics
            hiddenForumIds = newForums
            listsPreferencesHolder.setHiddenTopicIds(newTopics)
            listsPreferencesHolder.setHiddenForumIds(newForums)
            publishDisplayed()
            _uiEvents.emit(FavoritesUiEvent.OnChangeFav(true))
        }
    }

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
        // Раньше: linkHandler.handle(act=attach&code=showtopic) → LinkHandler не знает этот act →
        // externalIntent → внешний браузер (без forum-кук = логин-стена, фича не работала). Теперь —
        // нативный экран вложений.
        router.navigateTo(Screen.Attachments().apply {
            topicId = item.topicId
            topicTitle = item.topicTitle
        })
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

    /** Скрыта ли тема/форум из основного списка (локально). */
    fun isHidden(item: FavItem): Boolean = isItemHidden(item)

    private fun isItemHidden(item: FavItem): Boolean =
            if (item.isForum) item.forumId > 0 && hiddenForumIds.contains(item.forumId)
            else item.topicId > 0 && hiddenTopicIds.contains(item.topicId)

    /** Локально скрыть/показать тему или форум в списке избранного (на сервере не удаляется). */
    fun toggleHidden(item: FavItem) {
        scope.launch {
            val nowHidden: Boolean
            if (item.isForum) {
                if (item.forumId <= 0) return@launch
                val current = hiddenForumIds.toMutableSet()
                nowHidden = if (current.contains(item.forumId)) {
                    current.remove(item.forumId); false
                } else {
                    current.add(item.forumId); true
                }
                hiddenForumIds = current
                listsPreferencesHolder.setHiddenForumIds(current)
            } else {
                if (item.topicId <= 0) return@launch
                val current = hiddenTopicIds.toMutableSet()
                nowHidden = if (current.contains(item.topicId)) {
                    current.remove(item.topicId); false
                } else {
                    current.add(item.topicId); true
                }
                hiddenTopicIds = current
                listsPreferencesHolder.setHiddenTopicIds(current)
            }
            publishDisplayed()
            _uiEvents.emit(FavoritesUiEvent.OnToggleHidden(item, nowHidden))
        }
    }

    /** Локально (на устройстве) переключить уведомления для темы избранного. */
    fun toggleTopicMute(item: FavItem) {
        if (item.isForum || item.topicId <= 0) return
        val nowMuted = notificationPreferencesHolder.toggleTopicMute(item.topicId)
        scope.launch { _uiEvents.emit(FavoritesUiEvent.OnToggleMute(item, nowMuted)) }
    }

    fun isHatWatched(item: FavItem): Boolean =
            !item.isForum && item.topicId > 0 && notificationPreferencesHolder.isHatWatched(item.topicId)

    /** Переключить слежение за новыми версиями (apk в шапке) для темы. */
    fun toggleHatWatch(item: FavItem) {
        if (item.isForum || item.topicId <= 0) return
        val nowWatched = notificationPreferencesHolder.toggleHatWatch(item.topicId)
        scope.launch { _uiEvents.emit(FavoritesUiEvent.OnToggleHatWatch(item, nowWatched)) }
    }

    private fun MutableList<FavItem>.replaceWith(items: List<FavItem>) {
        clear()
        addAll(items)
    }

    private fun publishDisplayed() {
        val all = applyHiddenFlags(filterDisplayedItems())
        val displayed: List<FavItem>
        val pagination: Pagination
        if (searchQuery.isNotEmpty()) {
            // В поиске пагинация не нужна — показываем все совпадения.
            displayed = all
            pagination = singlePagePagination(all.size)
        } else {
            val hidden = all.filter { it.isHidden }
            val visible = all.filterNot { it.isHidden }
            val (slice, pg) = buildPageAndPagination(visible)
            // Скрытые добавляем после видимой страницы — адаптер сложит их в секцию «Скрытое».
            displayed = slice + hidden
            pagination = pg
        }
        _displayedItems.value = displayed
        _pagination.value = pagination
        scope.launch { _uiEvents.emit(FavoritesUiEvent.OnShowFavorite(displayed)) }
    }

    /** Нарезает видимые темы на текущую страницу и собирает соответствующий [Pagination]. */
    private fun buildPageAndPagination(visible: List<FavItem>): Pair<List<FavItem>, Pagination> {
        // «Загрузить всё» → одна страница со всеми видимыми; иначе — по clientPerPage.
        val perPage = if (loadAll) visible.size.coerceAtLeast(1) else clientPerPage.coerceAtLeast(1)
        val totalPages = if (visible.isEmpty()) 1 else ceil(visible.size / perPage.toDouble()).toInt()
        val pageIndex = (currentSt / perPage).coerceIn(0, totalPages - 1)
        currentSt = pageIndex * perPage
        val from = pageIndex * perPage
        val to = (from + perPage).coerceAtMost(visible.size)
        val slice = if (from < to) visible.subList(from, to).toList() else emptyList()
        val pagination = Pagination().apply {
            isForum = true
            this.perPage = perPage
            all = totalPages
            current = pageIndex + 1
            st = currentSt
        }
        return slice to pagination
    }

    private fun singlePagePagination(itemCount: Int): Pagination = Pagination().apply {
        isForum = true
        perPage = itemCount.coerceAtLeast(1)
        all = 1
        current = 1
        st = 0
    }

    /** Проставляет транзиентные флаги [FavItem.isHidden] и [FavItem.isNotifyMuted] по текущим наборам id. */
    private fun applyHiddenFlags(list: List<FavItem>): List<FavItem> {
        for (item in list) {
            item.isHidden = isItemHidden(item)
            item.isNotifyMuted = !item.isForum && item.topicId > 0 && mutedTopicIds.contains(item.topicId)
        }
        return list
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
                    .filter { item -> item.isNew && !item.isForum && item.topicId > 0 && !isItemHidden(item) }
                    .distinctBy { item -> item.topicId }
                    .map { item -> FavoriteMarkReadEntry(item.favId, item.topicId) }
                    .toList()
}

sealed class FavoritesUiEvent {
    data class InitSorting(val sorting: Sorting) : FavoritesUiEvent()
    data class OnShowFavorite(val list: List<FavItem>) : FavoritesUiEvent()
    data class OnLoadFavorites(val data: FavData) : FavoritesUiEvent()
    data class OnMarkAllReadProgress(val progress: FavoriteMarkReadProgress) : FavoritesUiEvent()
    data class OnMarkAllRead(val result: FavoriteMarkReadResult) : FavoritesUiEvent()
    data class ShowItemDialogMenu(val item: FavItem) : FavoritesUiEvent()
    data class OnChangeFav(val result: Boolean) : FavoritesUiEvent()
    data class ShowSubscribeDialog(val item: FavItem) : FavoritesUiEvent()
    data class OnToggleMute(val item: FavItem, val nowMuted: Boolean) : FavoritesUiEvent()
    data class OnToggleHidden(val item: FavItem, val nowHidden: Boolean) : FavoritesUiEvent()
    data class OnToggleHatWatch(val item: FavItem, val nowWatched: Boolean) : FavoritesUiEvent()
    data class ShowLoadError(val message: String?) : FavoritesUiEvent()
    object ShowNeedAuth : FavoritesUiEvent()
    object ScrollToTop : FavoritesUiEvent()
}
