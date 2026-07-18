package forpdateam.ru.forpda.presentation.history

import forpdateam.ru.forpda.presentation.BaseViewModel

import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import forpdateam.ru.forpda.common.ClipboardHelper
import forpdateam.ru.forpda.common.Utils
import forpdateam.ru.forpda.entity.app.history.HistoryItem
import forpdateam.ru.forpda.entity.remote.favorites.FavItem
import forpdateam.ru.forpda.model.data.cache.favorites.FavoritesCacheRoom
import forpdateam.ru.forpda.model.preferences.ListsPreferencesHolder
import forpdateam.ru.forpda.model.repository.history.HistoryRepository
import forpdateam.ru.forpda.model.repository.history.HistoryUnreadHarvester
import forpdateam.ru.forpda.presentation.IErrorHandler
import forpdateam.ru.forpda.presentation.ILinkHandler
import forpdateam.ru.forpda.presentation.Screen
import forpdateam.ru.forpda.presentation.TabRouter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * История посещений без Moxy.
 */
@HiltViewModel
class HistoryViewModel @Inject constructor(
        private val historyRepository: HistoryRepository,
        private val favoritesCache: FavoritesCacheRoom,
        private val historyUnreadHarvester: HistoryUnreadHarvester,
        private val listsPrefs: ListsPreferencesHolder,
        private val router: TabRouter,
        private val linkHandler: ILinkHandler,
        private val errorHandler: IErrorHandler,
        private val clipboardHelper: ClipboardHelper
) : BaseViewModel() {

    data class UiState(
            val items: List<HistoryItem> = emptyList(),
            val showDot: Boolean = false,
            val loading: Boolean = false
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()
    // Темы истории с новыми ответами по read-only harvest флага «+» из списков разделов (не-избранные).
    private val _harvestUnread = MutableStateFlow<Set<Int>>(emptySet())
    private var refreshJob: Job? = null
    private var harvestJob: Job? = null

    init {
        // Сшиваем историю со статусом Избранного, harvest'ом «+» и настройкой «Индикатор новых сообщений».
        // Реактивно: прочитал тему → FavoritesRepository обновляет кэш → History пере-сошьёт → точка гаснет.
        // Порядок строк (по времени визита) НЕ трогаем — обогащение только проставляет флаги.
        scope.launch {
            combine(
                    historyRepository.observeItems(),
                    favoritesCache.observeItems(),
                    _harvestUnread,
                    listsPrefs.observeShowDotFlow(),
            ) { history, favs, harvestUnread, showDot -> enrich(history, favs, harvestUnread) to showDot }
                    .catch { e -> errorHandler.handle(e) }
                    .collect { (items, showDot) ->
                        _uiState.update { it.copy(items = items, showDot = showDot) }
                    }
        }
        refresh()
    }

    /**
     * Проставляет флаг «непрочитано» строкам истории. Два read-only источника (порядок строк не меняем):
     *  1. Кэш Избранного ([FavItem.isUnreadForDisplay]) — для избранных тем, даёт и счётчик непрочитанных.
     *  2. [HistoryUnreadHarvester] — для не-избранных тем по флагу «+» из списка их раздела (счётчика нет →
     *     пустая точка).
     * Пробить статус самой темы сетью нельзя: 4PDA метит тему прочитанной по факту загрузки страницы,
     * так что проба «съела» бы непрочитанное.
     */
    private fun enrich(
            history: List<HistoryItem>,
            favs: List<FavItem>,
            harvestUnread: Set<Int>,
    ): List<HistoryItem> {
        if (history.isEmpty()) return history
        val favUnreadCountByTopicId = HashMap<Int, Int>(favs.size)
        for (f in favs) {
            if (f.topicId > 0 && f.isUnreadForDisplay()) {
                favUnreadCountByTopicId[f.topicId] = f.unreadPostCount
            }
        }
        return history.map { item ->
            val favCount = favUnreadCountByTopicId[item.id]
            val unread = favCount != null || item.id in harvestUnread
            val count = favCount ?: 0
            if (item.isUnread == unread && item.unreadCount == count) item
            else item.copy(isUnread = unread, unreadCount = count)
        }
    }

    fun refresh() {
        refreshJob?.cancel()
        refreshJob = scope.launch {
            _uiState.update { it.copy(loading = true) }
            runCatching {
                // Прогреть статусы Избранного из БД в StateFlow (на случай, если вкладку Избранное
                // не открывали — иначе observeItems() пуст). ensureItemsPublished публикует только если
                // ещё пусто; живые mark-read идут через FavoritesRepository.publishItems. Затем историю.
                // Оба пушат в свои StateFlow → combine пере-сошьёт items.
                favoritesCache.ensureItemsPublished()
                historyRepository.getHistory()
            }
                    .onSuccess { items ->
                        _uiState.update { it.copy(loading = false) }
                        launchHarvest(items.map { it.id })
                    }
                    .onFailure { e ->
                        _uiState.update { it.copy(loading = false) }
                        errorHandler.handle(e)
                    }
        }
    }

    /** Фоновый read-only harvest флага «+» для не-избранных тем; результат вливается через _harvestUnread. */
    private fun launchHarvest(topicIds: List<Int>) {
        harvestJob?.cancel()
        harvestJob = scope.launch {
            runCatching { historyUnreadHarvester.harvest(topicIds) }
                    .onSuccess { _harvestUnread.value = it }
            // onFailure: намеренно тихо — оставляем прежние точки, ошибку сети в Историю не выносим.
        }
    }

    fun remove(id: Int) {
        scope.launch {
            _uiState.update { it.copy(loading = true) }
            try {
                historyRepository.remove(id)
            } catch (e: Exception) {
                errorHandler.handle(e)
            }
            _uiState.update { it.copy(loading = false) }
        }
    }

    override fun onUserClear() {
        scope.launch {
            _uiState.update { it.copy(loading = true) }
            try {
                historyRepository.clear()
            } catch (e: Exception) {
                errorHandler.handle(e)
            }
            _uiState.update { it.copy(loading = false) }
        }
    }

    fun copyLink(item: HistoryItem) {
        item.url?.let { clipboardHelper.copyToClipboard(it) }
    }

    fun onItemClick(item: HistoryItem) {
        item.url?.let { url ->
            linkHandler.handle(url, router, mapOf(Screen.ARG_TITLE to (item.title ?: "")))
        }
    }

}
