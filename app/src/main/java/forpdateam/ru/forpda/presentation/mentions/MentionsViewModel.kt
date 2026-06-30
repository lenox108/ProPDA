package forpdateam.ru.forpda.presentation.mentions

import android.os.SystemClock
import forpdateam.ru.forpda.BuildConfig
import forpdateam.ru.forpda.presentation.BaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

import forpdateam.ru.forpda.common.ClipboardHelper
import forpdateam.ru.forpda.entity.remote.mentions.MentionItem
import forpdateam.ru.forpda.model.CountersHolder
import forpdateam.ru.forpda.model.data.remote.api.favorites.FavoritesApi
import forpdateam.ru.forpda.model.repository.faviorites.FavoritesRepository
import forpdateam.ru.forpda.model.repository.mentions.MentionsRepository
import forpdateam.ru.forpda.presentation.IErrorHandler
import forpdateam.ru.forpda.presentation.ILinkHandler
import forpdateam.ru.forpda.presentation.Screen
import forpdateam.ru.forpda.presentation.TabRouter
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.regex.Pattern

@HiltViewModel
class MentionsViewModel @Inject constructor(
        private val mentionsRepository: MentionsRepository,
        private val favoritesRepository: FavoritesRepository,
        private val countersHolder: CountersHolder,
        private val router: TabRouter,
        private val linkHandler: ILinkHandler,
        private val errorHandler: IErrorHandler,
        private val clipboardHelper: ClipboardHelper
) : BaseViewModel() {

    private var loadJob: Job? = null
    private var subscriptionsStarted = false
    private var lastLoadStartedElapsedMs = 0L

    private val _currentSt = MutableStateFlow(0)
    val currentSt: StateFlow<Int> = _currentSt.asStateFlow()

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    private val _uiEvents = MutableSharedFlow<MentionsUiEvent>()
    val uiEvents: SharedFlow<MentionsUiEvent> = _uiEvents.asSharedFlow()

    fun setCurrentSt(st: Int) {
        _currentSt.value = st
    }

    fun start() {
        Timber.d("start() called, subscriptionsStarted=$subscriptionsStarted")
        if (subscriptionsStarted) return
        subscriptionsStarted = true
        getMentions(cacheFirst = true)
    }

    /**
     * Сверяем бейдж «Ответы» с реальным состоянием при каждом показе вкладки. Счётчик упоминаний
     * в шапке форума (source=index_header) обновляется любым запросом (избранное/тема) и может
     * «висеть» на старом значении даже после того, как пользователь прочитал все упоминания —
     * локальный непрочитанный снапшот при этом уже 0, но никто не перетягивал бейдж к нему, пока
     * вкладка не перезагрузит список act=mentions. Дёргаем перезагрузку на показе (с дебаунсом,
     * чтобы переключения вкладок не спамили сеть и не дублировали стартовую загрузку).
     */
    fun onShown() {
        if (!subscriptionsStarted) return
        if (loadJob?.isActive == true) return
        if (SystemClock.elapsedRealtime() - lastLoadStartedElapsedMs < SHOW_REFRESH_MIN_INTERVAL_MS) return
        getMentions(cacheFirst = false)
    }

    fun getMentions(cacheFirst: Boolean = true) {
        Timber.d("getMentions() called, currentSt=${_currentSt.value}")
        loadJob?.cancel()
        lastLoadStartedElapsedMs = SystemClock.elapsedRealtime()
        loadJob = scope.launch {
            val page = _currentSt.value
            val cacheStartedAt = SystemClock.uptimeMillis()
            val cachedData = if (cacheFirst) mentionsRepository.getCachedMentions(page) else null
            cachedData?.let {
                logPerf("emit cached list", cacheStartedAt, "page=$page items=${it.items.size}")
                _uiEvents.emit(MentionsUiEvent.ShowMentions(it))
            }
            _refreshing.value = true
            try {
                val refreshStartedAt = SystemClock.uptimeMillis()
                val data = mentionsRepository.refreshMentions(page)
                logPerf("emit refreshed list", refreshStartedAt, "page=$page items=${data.items.size}")
                val badgeStartedAt = SystemClock.uptimeMillis()
                countersHolder.setMentions(mentionsRepository.getUnreadSnapshot().unreadCount, source = "mentions_refresh_changed")
                logPerf("badge recompute", badgeStartedAt, "source=mentions_refresh_changed")
                Timber.d("getMentions success, got ${data.items.size} items")
                _uiEvents.emit(MentionsUiEvent.ShowMentions(data))
            } catch (e: Exception) {
                Timber.e(e, "getMentions failed")
                var message: String? = null
                errorHandler.handle(e) { _, handledMessage -> message = handledMessage }
                _uiEvents.emit(MentionsUiEvent.ShowLoadError(message))
            } finally {
                _refreshing.value = false
            }
        }
    }

    fun addTopicToFavorite(topicId: Int, subType: String) {
        scope.launch {
            runCatching {
                favoritesRepository.editFavorites(FavoritesApi.ACTION_ADD, -1, topicId, subType)
            }.onSuccess { _uiEvents.emit(MentionsUiEvent.OnAddToFavorite(it)) }
                    .onFailure { errorHandler.handle(it) }
        }
    }

    fun onItemClick(item: MentionItem) {
        linkHandler.handle(item.link, router, mapOf(
                Screen.ARG_TITLE to item.title.orEmpty(),
                Screen.Theme.ARG_TOPIC_OPEN_SOURCE to "mentions"
        ))
    }

    fun onItemLongClick(item: MentionItem) {
        scope.launch { _uiEvents.emit(MentionsUiEvent.ShowItemDialogMenu(item)) }
    }

    fun copyLink(item: MentionItem) {
        clipboardHelper.copyToClipboard(item.link)
    }

    fun addToFavorites(item: MentionItem) {
        var id = 0
        val matcher = Pattern.compile("showtopic=(\\d+)").matcher(item.link.orEmpty())
        if (matcher.find()) {
            id = matcher.group(1)?.toIntOrNull() ?: 0
        }
        scope.launch { _uiEvents.emit(MentionsUiEvent.ShowAddFavoritesDialog(id)) }
    }

    private fun logPerf(label: String, startedAt: Long, extra: String = "") {
        if (BuildConfig.DEBUG) {
            Timber.d("MentionsPerf %s took %dms %s", label, SystemClock.uptimeMillis() - startedAt, extra)
        }
    }

    private companion object {
        // Достаточно мал, чтобы возврат из прочитанной темы обновил бейдж, и достаточно велик,
        // чтобы погасить дубль стартовой загрузки (onViewCreated.start() + первый onResumeOrShow).
        const val SHOW_REFRESH_MIN_INTERVAL_MS = 2_000L
    }
}

sealed class MentionsUiEvent {
    data class MentionMarkedRead(val item: MentionItem) : MentionsUiEvent()
    data class ShowMentions(val data: forpdateam.ru.forpda.entity.remote.mentions.MentionsData) : MentionsUiEvent()
    data class ShowItemDialogMenu(val item: MentionItem) : MentionsUiEvent()
    data class ShowAddFavoritesDialog(val id: Int) : MentionsUiEvent()
    data class OnAddToFavorite(val result: Boolean) : MentionsUiEvent()
    data class ShowLoadError(val message: String?) : MentionsUiEvent()
}
