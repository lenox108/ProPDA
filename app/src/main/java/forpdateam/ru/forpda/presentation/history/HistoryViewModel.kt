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
import forpdateam.ru.forpda.model.repository.theme.TopicReadBoundaryStore
import forpdateam.ru.forpda.presentation.theme.TopicUnreadFindPostReloadPolicy
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
import kotlinx.coroutines.withTimeoutOrNull

/**
 * История посещений без Moxy.
 */
@HiltViewModel
class HistoryViewModel @Inject constructor(
        private val historyRepository: HistoryRepository,
        private val favoritesCache: FavoritesCacheRoom,
        private val historyUnreadHarvester: HistoryUnreadHarvester,
        private val listsPrefs: ListsPreferencesHolder,
        private val readBoundaryStore: TopicReadBoundaryStore,
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
    private var refreshJob: Job? = null
    private var harvestJob: Job? = null

    init {
        // Сшиваем историю со статусом Избранного, harvest'ом «+» и настройкой «Индикатор новых сообщений».
        // Реактивно: прочитал тему → у избранной FavoritesRepository обновляет кэш, у не-избранной
        // harvester.markOpened гасит её в unread → History пере-сошьёт → точка гаснет. Порядок строк
        // (по времени визита) НЕ трогаем — обогащение только проставляет флаги.
        scope.launch {
            combine(
                    historyRepository.observeItems(),
                    favoritesCache.observeItems(),
                    historyUnreadHarvester.unread,
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

    /** Фоновый read-only harvest флага «+» для не-избранных тем; результат публикуется в harvester.unread. */
    private fun launchHarvest(topicIds: List<Int>) {
        harvestJob?.cancel()
        harvestJob = scope.launch {
            // markOpened + refresh пишут в один StateFlow; ошибку сети в Историю не выносим.
            runCatching { historyUnreadHarvester.refresh(topicIds) }
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

    /**
     * «История» = вернуться туда, где остановился, а не «открыть тему заново».
     *
     * Раньше строка отдавала в [linkHandler] URL, сохранённый в истории — а это ФИНАЛЬНЫЙ url страницы
     * прошлого визита (после редиректа, вместе с `st=`/`p=`/`#entry…`, см. ThemeUseCase.recordThemeVisit).
     * Резолвер видел в нём явную страницу/пост и садился на них, не доходя до настройки «При открытии
     * темы»: пользователь попадал на позицию, зафиксированную прошлым заходом (пост упоминания, тогдашний
     * первый непрочитанный, страница прыжка) — отсюда жалоба «открывается рандомно».
     *
     * Теперь берём клиентскую границу прочитанного ([TopicReadBoundaryStore] — наибольший пост, реально
     * побывавший во вьюпорте) и открываем findpost прямо на неё: резолвер видит явный пост (EXPLICIT_POST)
     * и не подменяет якорь. Тот же путь, что у строки «Продолжить чтение» в «Ещё»
     * (`OtherViewModel.onContinueClick`). Границы нет (старая запись истории, кэш ещё не прогрет) —
     * честный фолбэк на прежнее поведение.
     */
    fun onItemClick(item: HistoryItem) {
        // Кэш границ прогревается из Room асинхронно ([TopicReadBoundaryStore.awaitHydrated]). Тап на
        // холодном старте мог опередить прогрев: граница «отсутствует» → фолбэк на чистый url, то есть
        // не резюм, а обычное открытие по настройке — и без якорного поста, а значит без подсветки.
        // Ждём прогрев ограниченное время; на прогретом кэше ветка синхронна (задержки нет).
        if (item.id > 0 && !readBoundaryStore.isHydrated) {
            scope.launch {
                withTimeoutOrNull(BOUNDARY_HYDRATION_WAIT_MS) { readBoundaryStore.awaitHydrated() }
                openHistoryItem(item)
            }
            return
        }
        openHistoryItem(item)
    }

    private fun openHistoryItem(item: HistoryItem) {
        val topicId = item.id
        val boundaryPostId = if (topicId > 0) readBoundaryStore.lastSeenPostId(topicId) else 0
        if (forpdateam.ru.forpda.BuildConfig.DEBUG) {
            // Без этого «сел на границу» и «упал в фолбэк из-за непрогретого кэша» в логе неразличимы —
            // а это ровно две ветки жалобы «периодически открывает не туда».
            android.util.Log.i("FPDA_CONTINUE_OPEN",
                    "history topic=$topicId boundary=$boundaryPostId hydrated=${readBoundaryStore.isHydrated}")
        }
        if (topicId > 0 && boundaryPostId > 0) {
            router.navigateTo(Screen.Theme().apply {
                themeUrl = TopicUnreadFindPostReloadPolicy.buildFindPostUrl(topicId, boundaryPostId.toString())
                topicOpenSource = "history"
                screenTitle = item.title
            })
            return
        }
        // Границы нет — открываем ЧИСТЫЙ url темы (сработает настройка «При открытии темы»), а не
        // сохранённый url прошлого визита: тот указывает на страницу ВХОДА в прошлый раз (в гибриде она
        // не двигается при прокрутке) и не несёт якорного поста, из-за чего посадка уходит на верх
        // страницы, а подсветка не запрашивается. См. [OtherViewModel.onContinueClick].
        if (topicId > 0) {
            router.navigateTo(Screen.Theme().apply {
                themeUrl = "$TOPIC_BASE_URL$topicId"
                topicOpenSource = "history"
                screenTitle = item.title
            })
            return
        }
        item.url?.let { url ->
            linkHandler.handle(url, router, mapOf(Screen.ARG_TITLE to (item.title ?: "")))
        }
    }

    private companion object {
        const val TOPIC_BASE_URL = "https://4pda.to/forum/index.php?showtopic="

        /** Потолок ожидания прогрева границ прочитанного при тапе (см. [onItemClick]). */
        const val BOUNDARY_HYDRATION_WAIT_MS = 1_500L
    }
}
