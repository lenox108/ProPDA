package forpdateam.ru.forpda.presentation.topics

import forpdateam.ru.forpda.BuildConfig
import forpdateam.ru.forpda.presentation.BaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import forpdateam.ru.forpda.common.topicOpenListHintsFromListing
import forpdateam.ru.forpda.diagnostic.ThemePostReadStateDiagnostics
import forpdateam.ru.forpda.common.topicUrlForOpeningFromListing
import forpdateam.ru.forpda.entity.remote.topics.TopicItem
import timber.log.Timber
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
import forpdateam.ru.forpda.presentation.search.forumSectionSearchUrl
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class TopicsViewModel @Inject constructor(
        private val topicsRepository: TopicsRepository,
        private val forumRepository: ForumRepository,
        private val favoritesRepository: FavoritesRepository,
        private val crossScreenInteractor: CrossScreenInteractor,
        private val router: TabRouter,
        private val linkHandler: ILinkHandler,
        private val errorHandler: IErrorHandler
) : BaseViewModel() {

    private var loadJob: Job? = null
    private var subscriptionsStarted = false

    private val _id = MutableStateFlow(0)
    val id: StateFlow<Int> = _id.asStateFlow()
    private var currentSt = 0
    private val _currentData = MutableStateFlow<TopicsData?>(null)
    val currentData: StateFlow<TopicsData?> = _currentData.asStateFlow()

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    private val _uiEvents = MutableSharedFlow<TopicsUiEvent>()
    val uiEvents: SharedFlow<TopicsUiEvent> = _uiEvents.asSharedFlow()

    fun setId(forumId: Int) {
        _id.value = forumId
    }

    fun start() {
        if (subscriptionsStarted) return
        subscriptionsStarted = true
        scope.launch {
            crossScreenInteractor.observeTopic().collect { topicId ->
                markRead(topicId)
            }
        }
        loadTopics()
    }

    fun loadTopics() {
        loadJob?.cancel()
        loadJob = scope.launch {
            _refreshing.value = true
            try {
                val data = topicsRepository.getTopics(_id.value, currentSt)
                _currentData.value = data
                _uiEvents.emit(TopicsUiEvent.ShowTopics(data))
            } catch (e: Exception) {
                var message: String? = null
                errorHandler.handle(e) { _, handledMessage -> message = handledMessage }
                _uiEvents.emit(TopicsUiEvent.ShowLoadError(message))
            } finally {
                _refreshing.value = false
            }
        }
    }

    fun loadPage(st: Int) {
        currentSt = st
        loadTopics()
    }

    fun addForumToFavorite(forumId: Int, subType: String) {
        scope.launch {
            runCatching {
                favoritesRepository.editFavorites(FavoritesApi.ACTION_ADD_FORUM, -1, forumId, subType)
            }.onSuccess { _uiEvents.emit(TopicsUiEvent.OnAddToFavorite(it)) }
                    .onFailure { errorHandler.handle(it) }
        }
    }

    fun addTopicToFavorite(topicId: Int, subType: String) {
        scope.launch {
            runCatching {
                favoritesRepository.editFavorites(FavoritesApi.ACTION_ADD, -1, topicId, subType)
            }.onSuccess { _uiEvents.emit(TopicsUiEvent.OnAddToFavorite(it)) }
                    .onFailure { errorHandler.handle(it) }
        }
    }

    fun markRead() {
        scope.launch {
            try {
                forumRepository.markRead(_id.value)
                _uiEvents.emit(TopicsUiEvent.OnMarkRead)
            } catch (e: Exception) {
                errorHandler.handle(e)
            }
        }
    }

    private fun markRead(id: Int) {
        ThemePostReadStateDiagnostics.markReadApplied(
                topicId = id,
                reason = "cross_screen_topic",
                source = "topics_view_model"
        )
        _currentData.value?.also { currentData ->
            currentData.topicItems.firstOrNull { it.id == id }?.isNew = false
            currentData.pinnedItems.firstOrNull { it.id == id }?.isNew = false
            scope.launch { _uiEvents.emit(TopicsUiEvent.UpdateList) }
        }
    }

    fun openForum() {
        router.navigateTo(Screen.Forum().apply {
            forumId = _id.value
        })
    }

    fun openSearch() {
        val forumId = _currentData.value?.id?.takeIf { it > 0 } ?: _id.value
        if (forumId <= 0) return
        router.navigateTo(Screen.Search().apply {
            searchUrl = forumSectionSearchUrl(forumId)
            screenSubTitle = _currentData.value?.title
        })
    }

    fun openTopicForum() {
        _currentData.value?.let {
            linkHandler.handle("https://4pda.to/forum/index.php?showforum=${it.id}", router)
        }
    }

    fun onItemClick(item: TopicItem) {
        if (item.isAnnounce) {
            item.announceUrl?.let { url ->
                linkHandler.handle(url, router, mapOf(
                        Screen.ARG_TITLE to (item.title ?: "")
                ))
            }
            return
        }
        if (item.isForum) {
            linkHandler.handle("https://4pda.to/forum/index.php?showforum=${item.id}", router)
            return
        }
        val topicUrl = topicUrlForOpeningFromListing(
                listingHref = item.listingHref,
                topicId = item.id,
                isRelocated = item.isRelocated
        )
        if (BuildConfig.DEBUG) {
            Timber.tag("TopicsOpen").d(
                    "onItemClick title=%s topicId=%d isRelocated=%s listingHref=%s -> finalUrl=%s",
                    item.title.orEmpty(),
                    item.id,
                    item.isRelocated.toString(),
                    item.listingHref ?: "",
                    topicUrl
            )
        }
        val listHints = topicOpenListHintsFromListing(
                listingHref = item.listingHref,
                topicId = item.id,
                isRelocated = item.isRelocated,
                topicMarkedUnread = item.isNew
        )
        if (item.isRelocated) {
            // Минуем LinkHandler: для тем-указателей важно сохранить серверный href без нормализации.
            router.navigateTo(Screen.Theme().apply {
                themeUrl = topicUrl
                screenTitle = item.title.orEmpty()
                topicOpenSource = "topics"
                topicOpenIntent = forpdateam.ru.forpda.presentation.theme.TopicOpenIntentClassifier.FRESH_FORUM
                unreadUrlFromList = listHints.unreadUrlFromList
                unreadPostIdFromList = listHints.unreadPostIdFromList ?: 0
            })
            return
        }
        linkHandler.handle(topicUrl, router, buildMap {
            put(Screen.ARG_TITLE, item.title ?: "")
            put(Screen.Theme.ARG_TOPIC_OPEN_SOURCE, "topics")
            put(
                    Screen.Theme.ARG_TOPIC_OPEN_INTENT,
                    forpdateam.ru.forpda.presentation.theme.TopicOpenIntentClassifier.FRESH_FORUM
            )
            listHints.unreadUrlFromList?.let { put(Screen.Theme.ARG_UNREAD_URL_FROM_LIST, it) }
            listHints.unreadPostIdFromList?.let { put(Screen.Theme.ARG_UNREAD_POST_ID_FROM_LIST, it.toString()) }
        })
    }

    fun onItemLongClick(item: TopicItem) {
        scope.launch { _uiEvents.emit(TopicsUiEvent.ShowItemDialogMenu(item)) }
    }
}

sealed class TopicsUiEvent {
    data class ShowTopics(val data: TopicsData) : TopicsUiEvent()
    object UpdateList : TopicsUiEvent()
    data class ShowItemDialogMenu(val item: TopicItem) : TopicsUiEvent()
    data class OnAddToFavorite(val result: Boolean) : TopicsUiEvent()
    object OnMarkRead : TopicsUiEvent()
    data class ShowLoadError(val message: String?) : TopicsUiEvent()
}
