package forpdateam.ru.forpda.presentation.forum

import forpdateam.ru.forpda.presentation.BaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

import forpdateam.ru.forpda.common.ClipboardHelper
import forpdateam.ru.forpda.common.Utils
import forpdateam.ru.forpda.entity.remote.forum.ForumItemTree
import forpdateam.ru.forpda.model.data.remote.api.favorites.FavoritesApi
import forpdateam.ru.forpda.model.interactors.CrossScreenInteractor
import forpdateam.ru.forpda.model.repository.faviorites.FavoritesRepository
import forpdateam.ru.forpda.model.repository.forum.ForumRepository
import forpdateam.ru.forpda.presentation.IErrorHandler
import forpdateam.ru.forpda.presentation.Screen
import forpdateam.ru.forpda.presentation.TabRouter
import forpdateam.ru.forpda.presentation.search.forumSectionSearchUrl
import forpdateam.ru.forpda.ui.fragments.forum.ForumTreeRecyclerAdapter
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

@HiltViewModel
class ForumViewModel @Inject constructor(
        private val forumRepository: ForumRepository,
        private val favoritesRepository: FavoritesRepository,
        private val crossScreenInteractor: CrossScreenInteractor,
        private val router: TabRouter,
        private val errorHandler: IErrorHandler,
        private val clipboardHelper: ClipboardHelper
) : BaseViewModel() {

    private var loadJob: Job? = null
    private var subscriptionsStarted = false

    var targetForumId = -1
    var forumListState: ForumTreeRecyclerAdapter.State? = null
    var recyclerState: android.os.Parcelable? = null
    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    private val _uiEvents = MutableSharedFlow<ForumUiEvent>()
    val uiEvents: SharedFlow<ForumUiEvent> = _uiEvents.asSharedFlow()
    /** Флаг: была ли просмотрена хотя бы одна тема — при возврате на форум обновим данные с сервера. */
    var needsRefresh = false
        private set

    /**
     * Вызывается при каждом attach view. Нельзя одноразово «запоминать» флагом:
     * при пересоздании view (поворот, back stack) ViewModel жива, а [detachView] уже был —
     * иначе [start] больше не вызывает загрузку, экран остаётся пустым.
     */
    fun start() {
        if (!subscriptionsStarted) {
            subscriptionsStarted = true
            scope.launch {
                crossScreenInteractor.observeTopic().collect {
                    needsRefresh = true
                }
            }
        }
        // Кэш Realm; при пустом дереве getCacheForums вызовет loadForums().
        getCacheForums()
    }

    /** Сбросить флаг после обновления. */
    fun clearNeedsRefresh() {
        needsRefresh = false
    }

    fun loadForums() {
        loadJob?.cancel()
        loadJob = scope.launch {
            _refreshing.value = true
            try {
                val tree = forumRepository.getForums()
                _uiEvents.emit(ForumUiEvent.ShowForums(tree))
                scrollToTarget()
                saveCacheForums(tree)
            } catch (e: Exception) {
                errorHandler.handle(e)
            } finally {
                _refreshing.value = false
            }
        }
    }

    private fun getCacheForums() {
        loadJob?.cancel()
        loadJob = scope.launch {
            _refreshing.value = true
            try {
                val tree = forumRepository.getCache()
                if (!tree.forums.isNullOrEmpty()) {
                    _uiEvents.emit(ForumUiEvent.ShowForums(tree))
                    scrollToTarget()
                }
                // Всегда тянем сеть: иначе после смены парсера/дерева пользователь видит старый Realm до ручного «Обновить».
                loadForums()
            } catch (e: Exception) {
                errorHandler.handle(e)
            } finally {
                _refreshing.value = false
            }
        }
    }

    private fun scrollToTarget() {
        if (targetForumId != -1) {
            scope.launch { _uiEvents.emit(ForumUiEvent.ScrollToForum(targetForumId)) }
            targetForumId = -1
        }
    }

    private fun saveCacheForums(rootForum: ForumItemTree) {
        scope.launch {
            try {
                forumRepository.saveCache(rootForum)
            } catch (e: Exception) {
                errorHandler.handle(e)
            }
        }
    }

    fun markRead(id: Int) {
        scope.launch {
            try {
                forumRepository.markRead(id)
                _uiEvents.emit(ForumUiEvent.OnMarkRead)
            } catch (e: Exception) {
                errorHandler.handle(e)
            }
        }
    }

    fun markAllRead() {
        scope.launch {
            try {
                forumRepository.markAllRead()
                _uiEvents.emit(ForumUiEvent.OnMarkAllRead)
            } catch (e: Exception) {
                errorHandler.handle(e)
            }
        }
    }

    fun addToFavorite(forumId: Int, subType: String) {
        scope.launch {
            runCatching {
                favoritesRepository.editFavorites(FavoritesApi.ACTION_ADD_FORUM, -1, forumId, subType)
            }.onSuccess { _uiEvents.emit(ForumUiEvent.OnAddToFavorite(it)) }
                    .onFailure { errorHandler.handle(it) }
        }
    }

    fun copyLink(item: ForumItemTree) {
        clipboardHelper.copyToClipboard("https://4pda.to/forum/index.php?showforum=${item.id}")
    }

    fun navigateToForum(item: ForumItemTree) {
        router.navigateTo(Screen.Topics().apply {
            forumId = item.id
        })
    }

    fun navigateToSearch(item: ForumItemTree) {
        if (item.id <= 0) return
        router.navigateTo(Screen.Search().apply {
            searchUrl = forumSectionSearchUrl(item.id)
            screenSubTitle = item.title
        })
    }
}

sealed class ForumUiEvent {
    data class ShowForums(val tree: ForumItemTree) : ForumUiEvent()
    data class ScrollToForum(val forumId: Int) : ForumUiEvent()
    object OnMarkRead : ForumUiEvent()
    object OnMarkAllRead : ForumUiEvent()
    data class OnAddToFavorite(val result: Boolean) : ForumUiEvent()
}
