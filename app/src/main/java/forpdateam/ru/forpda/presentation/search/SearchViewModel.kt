package forpdateam.ru.forpda.presentation.search

import forpdateam.ru.forpda.presentation.BaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

import forpdateam.ru.forpda.BuildConfig
import forpdateam.ru.forpda.R
import forpdateam.ru.forpda.common.ClipboardHelper
import forpdateam.ru.forpda.common.Utils
import forpdateam.ru.forpda.entity.remote.IBaseForumPost
import forpdateam.ru.forpda.entity.remote.search.SearchItem
import forpdateam.ru.forpda.entity.remote.search.SearchResult
import forpdateam.ru.forpda.entity.remote.search.SearchSettings
import forpdateam.ru.forpda.model.data.remote.api.favorites.FavoritesApi
import forpdateam.ru.forpda.model.repository.faviorites.FavoritesRepository
import forpdateam.ru.forpda.model.preferences.OtherPreferencesHolder
import forpdateam.ru.forpda.model.repository.search.SearchRepository
import forpdateam.ru.forpda.presentation.theme.TopicOpenContext
import forpdateam.ru.forpda.presentation.theme.TopicOpenTargetResolver
import forpdateam.ru.forpda.presentation.theme.TopicUserOpenAction
import forpdateam.ru.forpda.presentation.IErrorHandler
import forpdateam.ru.forpda.presentation.ILinkHandler
import forpdateam.ru.forpda.presentation.Screen
import forpdateam.ru.forpda.presentation.TabRouter
import timber.log.Timber
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@HiltViewModel
class SearchViewModel @Inject constructor(
        private val searchRepository: SearchRepository,
        private val favoritesRepository: FavoritesRepository,
        private val otherPreferencesHolder: OtherPreferencesHolder,
        private val router: TabRouter,
        private val linkHandler: ILinkHandler,
        private val errorHandler: IErrorHandler,
        private val clipboardHelper: ClipboardHelper
) : BaseViewModel() {

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    private val _currentData = MutableStateFlow<SearchResult?>(null)
    val currentData: StateFlow<SearchResult?> = _currentData.asStateFlow()

    private val _uiEvents = MutableSharedFlow<SearchUiEvent>(extraBufferCapacity = 1)
    val uiEvents: SharedFlow<SearchUiEvent> = _uiEvents.asSharedFlow()

    private var subscriptionsStarted = false
    private val expandedPostIds = mutableSetOf<Int>()

    /** In-flight search request. Cancelled before each new one so only the latest result can win. */
    private var searchJob: Job? = null

    companion object {
        const val FIELD_RESOURCE = "resource"
        const val FIELD_RESULT = "result"
        const val FIELD_SORT = "sort"
        const val FIELD_SOURCE = "source"
    }

    private val resourceItems = listOf<String>(SearchSettings.RESOURCE_FORUM.second, SearchSettings.RESOURCE_NEWS.second)
    private val resultItems = listOf<String>(SearchSettings.RESULT_TOPICS.second, SearchSettings.RESULT_POSTS.second)
    private val sortItems = listOf<String>(SearchSettings.SORT_DA.second, SearchSettings.SORT_DD.second, SearchSettings.SORT_REL.second)
    private val sourceItems = listOf<String>(SearchSettings.SOURCE_ALL.second, SearchSettings.SOURCE_TITLES.second, SearchSettings.SOURCE_CONTENT.second)

    private val fields = mapOf(
            FIELD_RESOURCE to resourceItems,
            FIELD_RESULT to resultItems,
            FIELD_SORT to sortItems,
            FIELD_SOURCE to sourceItems
    )

    private var settings = SearchSettings()
    private var hasRouteSettings = false
    private var scopedForumTitle: String? = null

    fun emitFillSettings() {
        _uiEvents.tryEmit(SearchUiEvent.FillSettingsData(settings, fields))
    }

    /** Текущий поисковый запрос — чтобы вернуть его в поле SearchView после сворачивания action-view
     *  при уходе с экрана (иначе поле показывает только плейсхолдер, а результаты остаются валидными). */
    fun currentQuery(): String = settings.query

    fun initSearchSettings(url: String?, forumTitle: String? = null) {
        if (!url.isNullOrEmpty()) {
            hasRouteSettings = true
            settings = SearchSettings.parseSettings(SearchSettings(), url)
            scopedForumTitle = forumTitle?.takeIf { it.isNotBlank() }
        }
    }

    fun start() {
        if (subscriptionsStarted) return
        subscriptionsStarted = true

        scope.launch {
            if (!hasRouteSettings) {
                val savedSettings = otherPreferencesHolder.getSearchSettings()
                if (savedSettings.isNotEmpty()) {
                    settings = SearchSettings.parseSettings(SearchSettings(), savedSettings)
                }
            }
        }
        _uiEvents.tryEmit(SearchUiEvent.FillSettingsData(settings, fields))
        refreshData()
    }

    override fun onCleared() {
        super.onCleared()
    }

    fun refreshData() {
        // Cancel any in-flight request first: rapid submit / pagination / pull-to-refresh could otherwise
        // interleave, and a slower EARLIER response would overwrite _currentData AFTER a newer one (stale
        // results shown). Latest-wins.
        searchJob?.cancel()
        if (settings.query.isEmpty() && settings.nick.isNullOrEmpty() && settings.userId <= 0) {
            searchJob = scope.launch { _uiEvents.emit(SearchUiEvent.ShowInitialState) }
            return
        }
        searchJob = scope.launch {
            _refreshing.value = true
            _uiEvents.emit(SearchUiEvent.OnStartSearch(settings))
            try {
                val result = searchRepository.getSearch(settings)
                expandedPostIds.retainAll(result.items.mapTo(mutableSetOf()) { it.id })
                _currentData.value = result
                _uiEvents.emit(SearchUiEvent.ShowData(result))
            } catch (e: CancellationException) {
                throw e // superseded by a newer search — let cancellation propagate, don't touch the spinner
            } catch (e: Exception) {
                var message: String? = null
                errorHandler.handle(e) { _, handledMessage -> message = handledMessage }
                _uiEvents.emit(SearchUiEvent.ShowLoadError(message))
            } finally {
                // Only the still-active (winning) job clears the spinner; a cancelled one leaves it to the
                // new job that just set it true, avoiding a true→false→(true) flicker race.
                if (isActive) _refreshing.value = false
            }
        }
    }

    fun search(query: String, nick: String) {
        settings.st = 0
        settings.query = query
        // При ручном сабмите поле ника — источник истины: сбрасываем ранее зарезолвленный/пришедший из
        // профиля username-id, иначе смена ника искала бы прежнего пользователя по старому id
        // (репозиторий заново резолвит ник в id перед запросом).
        if (settings.nick != nick) settings.userId = 0
        settings.nick = nick
        refreshData()
    }

    fun search(pageNumber: Int) {
        settings.st = pageNumber
        refreshData()
    }

    /**
     * «Открыть профиль по нику»: резолвим введённый ник в id и сразу переходим в профиль (showuser=id),
     * без поиска постов. Профиль 4pda открывается только по числовому id — прямого профиля по нику нет.
     */
    fun openProfileByNick(rawNick: String) {
        val nick = rawNick.trim()
        if (nick.isEmpty()) {
            _uiEvents.tryEmit(SearchUiEvent.ShowMessage(R.string.chat_creator_enter_nick))
            return
        }
        scope.launch {
            val user = runCatching { searchRepository.findUserByNick(nick) }.getOrNull()
            if (user != null && user.id > 0) {
                // Закрываем нижний лист настроек и прячем клавиатуру, иначе профиль открывается «вторым
                // планом» под всё ещё видимой панелью поиска.
                _uiEvents.emit(SearchUiEvent.CloseSettings)
                linkHandler.handle("https://4pda.to/forum/index.php?showuser=${user.id}", router)
            } else {
                _uiEvents.emit(SearchUiEvent.ShowMessage(R.string.search_user_not_found))
            }
        }
    }

    fun updateSettings(field: String, position: Int) {
        when (field) {
            FIELD_RESOURCE -> {
                val name = resourceItems[position]
                when {
                    checkName(name, SearchSettings.RESOURCE_NEWS) -> {
                        settings.resourceType = SearchSettings.RESOURCE_NEWS.first
                        _uiEvents.tryEmit(SearchUiEvent.SetNewsMode)
                    }
                    checkName(name, SearchSettings.RESOURCE_FORUM) -> {
                        settings.resourceType = SearchSettings.RESOURCE_FORUM.first
                        _uiEvents.tryEmit(SearchUiEvent.SetForumMode)
                    }
                }
            }
            FIELD_RESULT -> {
                val name = resultItems[position]
                when {
                    checkName(name, SearchSettings.RESULT_TOPICS) -> settings.result = SearchSettings.RESULT_TOPICS.first
                    checkName(name, SearchSettings.RESULT_POSTS) -> settings.result = SearchSettings.RESULT_POSTS.first
                }
            }
            FIELD_SORT -> {
                val name = sortItems[position]
                when {
                    checkName(name, SearchSettings.SORT_DA) -> settings.sort = SearchSettings.SORT_DA.first
                    checkName(name, SearchSettings.SORT_DD) -> settings.sort = SearchSettings.SORT_DD.first
                    checkName(name, SearchSettings.SORT_REL) -> settings.sort = SearchSettings.SORT_REL.first
                }
            }
            FIELD_SOURCE -> {
                val name = sourceItems[position]
                when {
                    checkName(name, SearchSettings.SOURCE_ALL) -> settings.source = SearchSettings.SOURCE_ALL.first
                    checkName(name, SearchSettings.SOURCE_TITLES) -> settings.source = SearchSettings.SOURCE_TITLES.first
                    checkName(name, SearchSettings.SOURCE_CONTENT) -> settings.source = SearchSettings.SOURCE_CONTENT.first
                }
            }
        }
    }

    private fun checkName(arg: String, pair: Pair<String, String>): Boolean {
        return arg == pair.second
    }

    suspend fun saveSettings() {
        val saveSettings = SearchSettings()
        saveSettings.resourceType = settings.resourceType
        saveSettings.result = settings.result
        saveSettings.sort = settings.sort
        saveSettings.source = settings.source
        saveSettings.subforums = settings.subforums
        settings.forums.forEach { saveSettings.addForum(it) }
        settings.topics.forEach { saveSettings.addTopic(it) }
        val saveUrl = saveSettings.toUrl()
        otherPreferencesHolder.setSearchSettings(saveUrl)
    }

    fun scopedForumTitleForUi(): String? = scopedForumTitle

    fun onItemClick(item: SearchItem) {
        val url = buildItemClickUrl(item)
        linkHandler.handle(url, router, themeOpenArgsForSearchUrl(url))
    }

    private fun buildItemClickUrl(item: SearchItem): String {
        if (settings.resourceType == SearchSettings.RESOURCE_NEWS.first) {
            return "https://4pda.to/index.php?p=${item.id}"
        }
        // Конкретный пост (поиск по сообщениям, «Сообщения пользователя», «Мои сообщения», …): тап открывает
        // ИМЕННО этот пост в его теме с якорем findpost. Раньше broad-user-search на тапе уводил во вложенный
        // поиск «посты пользователя в этой теме» (userPostsInTopicSearchUrl) — из-за этого «открывалось ещё
        // одно окно поиска вместо перехода к посту», а из-за переиспользования таба поиска тап порой вообще
        // не срабатывал. Drill-down теперь применяется только к результатам-темам (когда поста нет).
        if (item.id != 0) {
            return buildSearchFindPostTopicUrl(item.topicId, item.id)
        }
        if (settings.isBroadUserSearch() && item.topicId > 0) {
            return settings.userPostsInTopicSearchUrl(item)
        }
        return "https://4pda.to/forum/index.php?showtopic=${item.topicId}"
    }

    fun onItemLongClick(item: SearchItem) {
        _uiEvents.tryEmit(SearchUiEvent.ShowItemDialogMenu(item, settings))
    }

    fun copyLink() {
        clipboardHelper.copyToClipboard(settings.toUrl())
    }

    /** Название и ссылка для плитки «Закрепить в меню» (закрепляет фрагмент). */
    fun shortcutTarget(): Pair<String, String> = settings.query to settings.toUrl()

    fun copyLink(item: IBaseForumPost) {
        val url = if (settings.resourceType.equals(SearchSettings.RESOURCE_NEWS.first)) {
            "https://4pda.to/index.php?p=${item.id}"
        } else {
            buildString {
                append("https://4pda.to/forum/index.php?showtopic=${item.topicId}")
                if (item.id != 0) {
                    append("&view=findpost&p=${item.id}")
                }
            }
        }
        clipboardHelper.copyToClipboard(url)
    }

    fun openTopicBegin(item: IBaseForumPost) {
        linkHandler.handle(
                TopicOpenTargetResolver.resolve(
                        TopicOpenContext(
                                rawUrl = "https://4pda.to/forum/index.php?showtopic=${item.topicId}",
                                setting = forpdateam.ru.forpda.common.Preferences.Main.TopicOpenTarget.FIRST_PAGE,
                                sourceScreen = "search",
                                userAction = TopicUserOpenAction.FIRST_PAGE
                        )
                ).url,
                router
        )
    }

    fun openTopicNew(item: IBaseForumPost) {
        linkHandler.handle(
                TopicOpenTargetResolver.resolve(
                        TopicOpenContext(
                                rawUrl = "https://4pda.to/forum/index.php?showtopic=${item.topicId}",
                                setting = forpdateam.ru.forpda.common.Preferences.Main.TopicOpenTarget.LAST_UNREAD,
                                sourceScreen = "search",
                                userAction = TopicUserOpenAction.UNREAD
                        )
                ).url,
                router
        )
    }

    fun openTopicLast(item: IBaseForumPost) {
        linkHandler.handle(
                TopicOpenTargetResolver.resolve(
                        TopicOpenContext(
                                rawUrl = "https://4pda.to/forum/index.php?showtopic=${item.topicId}",
                                setting = forpdateam.ru.forpda.common.Preferences.Main.TopicOpenTarget.FIRST_PAGE,
                                sourceScreen = "search",
                                userAction = TopicUserOpenAction.LAST_POST
                        )
                ).url,
                router
        )
    }

    fun openForum(item: IBaseForumPost) {
        linkHandler.handle("https://4pda.to/forum/index.php?showforum=${item.forumId}", router)
    }

    fun onClickAddInFav(item: IBaseForumPost) {
        _uiEvents.tryEmit(SearchUiEvent.ShowAddInFavDialog(item))
    }

    fun addTopicToFavorite(topicId: Int, subType: String) {
        scope.launch {
            runCatching {
                favoritesRepository.editFavorites(FavoritesApi.ACTION_ADD, -1, topicId, subType)
            }.onSuccess { _uiEvents.emit(SearchUiEvent.OnAddToFavorite(it)) }
                    .onFailure { errorHandler.handle(it) }
        }
    }


}

sealed class SearchUiEvent {
    // Native search (Фаза 7: WebView-движок удалён) эмитит только эти события — результаты, пагинация,
    // настройки и контекстные действия над темой/пользователем. Прежние WebView-driven события поста
    // (голосование/репутация/правка/удаление/меню поста и т.п.) удалены как мёртвые.
    data class UpdateScrollButtonState(val enabled: Boolean) : SearchUiEvent()
    data class FillSettingsData(val settings: SearchSettings, val fields: Map<String, List<String>>) : SearchUiEvent()
    data class OnStartSearch(val settings: SearchSettings) : SearchUiEvent()
    data class ShowData(val data: SearchResult) : SearchUiEvent()
    data class ShowLoadError(val message: String?) : SearchUiEvent()
    data class ShowMessage(val resId: Int) : SearchUiEvent()
    object CloseSettings : SearchUiEvent()
    object ShowInitialState : SearchUiEvent()
    object SetNewsMode : SearchUiEvent()
    object SetForumMode : SearchUiEvent()
    data class ShowItemDialogMenu(val item: SearchItem, val settings: SearchSettings) : SearchUiEvent()
    data class ShowAddInFavDialog(val item: IBaseForumPost) : SearchUiEvent()
    data class OnAddToFavorite(val result: Boolean) : SearchUiEvent()
    object FirstPage : SearchUiEvent()
    object PrevPage : SearchUiEvent()
    object NextPage : SearchUiEvent()
    object LastPage : SearchUiEvent()
    object SelectPage : SearchUiEvent()
    data class ShowNoteCreate(val title: String, val url: String) : SearchUiEvent()
}
