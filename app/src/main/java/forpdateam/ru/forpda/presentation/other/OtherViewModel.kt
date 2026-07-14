package forpdateam.ru.forpda.presentation.other

import forpdateam.ru.forpda.presentation.BaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

import forpdateam.ru.forpda.entity.app.CloseableInfo
import forpdateam.ru.forpda.entity.app.history.HistoryItem
import forpdateam.ru.forpda.entity.app.other.AppMenuItem
import forpdateam.ru.forpda.entity.app.other.MenuShortcut
import forpdateam.ru.forpda.entity.app.other.OtherMenuBlock
import forpdateam.ru.forpda.entity.app.other.QuickSetting
import forpdateam.ru.forpda.entity.app.profile.IUserHolder
import forpdateam.ru.forpda.entity.remote.profile.ProfileModel
import forpdateam.ru.forpda.entity.remote.search.SearchSettings
import forpdateam.ru.forpda.model.AuthHolder
import forpdateam.ru.forpda.model.CloseableInfoHolder
import forpdateam.ru.forpda.model.interactors.other.MenuRepository
import forpdateam.ru.forpda.model.interactors.other.MenuShortcutsRepository
import forpdateam.ru.forpda.model.preferences.MainPreferencesHolder
import forpdateam.ru.forpda.model.preferences.OtherPreferencesHolder
import forpdateam.ru.forpda.model.repository.auth.AuthRepository
import forpdateam.ru.forpda.model.repository.history.HistoryRepository
import forpdateam.ru.forpda.model.repository.profile.ProfileRepository
import forpdateam.ru.forpda.presentation.IErrorHandler
import forpdateam.ru.forpda.presentation.ILinkHandler
import forpdateam.ru.forpda.presentation.Screen
import forpdateam.ru.forpda.presentation.TabRouter
import forpdateam.ru.forpda.ui.views.drawers.adapters.OtherMenuSection
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/**
 * Экран «Полное меню» без Moxy.
 */
@HiltViewModel
class OtherViewModel @Inject constructor(
        private val router: TabRouter,
        private val authRepository: AuthRepository,
        private val profileRepository: ProfileRepository,
        private val authHolder: AuthHolder,
        private val userHolder: IUserHolder,
        private val errorHandler: IErrorHandler,
        private val menuRepository: MenuRepository,
        private val closeableInfoHolder: CloseableInfoHolder,
        private val mainPreferencesHolder: MainPreferencesHolder,
        private val otherPreferencesHolder: OtherPreferencesHolder,
        private val menuShortcutsRepository: MenuShortcutsRepository,
        private val historyRepository: HistoryRepository,
        private val readBoundaryStore: forpdateam.ru.forpda.model.repository.theme.TopicReadBoundaryStore,
        private val linkHandler: ILinkHandler
) : BaseViewModel() {

    data class UiState(
            val isReady: Boolean = false,
            val profileItem: ProfileModel? = null,
            val infoList: List<CloseableInfo> = emptyList(),
            val menu: List<List<AppMenuItem>> = emptyList(),
            val menuTileLayout: Map<OtherMenuSection, List<Int>> = emptyMap(),
            val bottomNavDuplicateIds: Set<Int> = emptySet(),
            val shortcuts: List<MenuShortcut> = emptyList(),
            val continueItems: List<HistoryItem> = emptyList(),
            val quickSettings: List<QuickSetting> = QuickSetting.DEFAULT,
            val hiddenBlocks: Set<OtherMenuBlock> = emptySet()
    )

    /** Одноразовые события для диалогов закрепления. */
    sealed interface ShortcutEvent {
        data class HistoryLoaded(
                val section: OtherMenuSection,
                val items: List<HistoryItem>
        ) : ShortcutEvent
    }

    // Старая подсказка (item_other_menu_drag) знала только про перетаскивание и у большинства
    // пользователей уже закрыта — показываем вместо неё новую, про настройку меню целиком.
    private val closeableInfoIds = arrayOf(CloseableInfoHolder.item_other_menu_customize)

    private var profileItem: ProfileModel? = null
    private var menuMap: Map<Int, List<AppMenuItem>> = emptyMap()
    private var menuTileLayout: Map<OtherMenuSection, List<Int>> = emptyMap()
    private var bottomNavColumns = mainPreferencesHolder.getBottomNavColumns()
    private var isMenuLoaded = false
    private var isMenuTileLayoutLoaded = false
    private val localCloseableInfo = mutableListOf<CloseableInfo>()
    private var isMenuDragMode = false

    private var shortcuts: List<MenuShortcut> = emptyList()
    private var continueItems: List<HistoryItem> = emptyList()
    private var quickSettings: List<QuickSetting> = QuickSetting.DEFAULT
    private var hiddenBlocks: Set<OtherMenuBlock> = emptySet()

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _shortcutEvents = MutableSharedFlow<ShortcutEvent>(extraBufferCapacity = 1)
    val shortcutEvents: SharedFlow<ShortcutEvent> = _shortcutEvents.asSharedFlow()

    init {
        scope.launch {
            menuShortcutsRepository.observe()
                    .catch { }
                    .collect { items ->
                        shortcuts = items
                        // force: добавление и удаление ярлыков происходит прямо в режиме
                        // редактирования, где обычные обновления меню намеренно заморожены.
                        publishMenu(force = true)
                    }
        }
        scope.launch {
            // observeItems() — StateFlow-кэш в памяти: после холодного старта он пуст, пока историю
            // кто-нибудь не прочитает. Без этого прогрева «Продолжить чтение» не появлялось до
            // первого захода в «Историю» или в тему.
            runCatching { historyRepository.getHistory() }
            historyRepository.observeItems()
                    .catch { }
                    .collect { items ->
                        continueItems = items
                                .filter { !it.url.isNullOrBlank() && !it.title.isNullOrBlank() }
                                .take(CONTINUE_LIMIT)
                        publishMenu()
                    }
        }
        scope.launch {
            runCatching { profileRepository.loadSelf() }
        }
        scope.launch {
            profileRepository.observeCurrentUser()
                    .catch { }
                    .collect { wrapper ->
                        profileItem = wrapper.value
                        publishMenu()
                    }
        }
        scope.launch {
            authHolder.observe()
                    .catch { }
                    .collect {
                        if (!authHolder.get().isAuth()) {
                            profileItem = null
                        }
                        publishMenu()
                    }
        }
        scope.launch {
            menuRepository.observerMenu()
                    .catch { }
                    .collect { menu ->
                        menuMap = menu
                        isMenuLoaded = true
                        publishMenu()
                    }
        }
        scope.launch {
            mainPreferencesHolder.observeBottomNavColumnsFlow()
                    .catch { }
                    .collect { columns ->
                        bottomNavColumns = columns
                        publishMenu()
                    }
        }
        scope.launch {
            closeableInfoHolder.observe()
                    .catch { }
                    .collect { info ->
                        localCloseableInfo.clear()
                        localCloseableInfo.addAll(
                                info.filter { item ->
                                    closeableInfoIds.contains(item.id) && !item.isClosed
                                }
                        )
                        publishMenu()
                    }
        }
        scope.launch {
            menuTileLayout = otherPreferencesHolder.getOtherMenuTileLayout()
            isMenuTileLayoutLoaded = true
            publishMenu()
        }
        scope.launch {
            otherPreferencesHolder.observeOtherMenuQuickSettingsFlow()
                    .catch { }
                    .collect { items ->
                        quickSettings = items
                        // force: состав правят прямо в режиме редактирования (см. подписку на ярлыки).
                        publishMenu(force = true)
                    }
        }
        scope.launch {
            otherPreferencesHolder.observeOtherMenuHiddenBlocksFlow()
                    .catch { }
                    .collect { items ->
                        hiddenBlocks = items
                        publishMenu(force = true)
                    }
        }
    }

    fun onMenuDragModeChange(isDragMode: Boolean) {
        isMenuDragMode = isDragMode
        publishMenu()
    }

    private fun publishMenu(force: Boolean = false) {
        if (isMenuDragMode && !force) return
        if (!isMenuLoaded || !isMenuTileLayoutLoaded) return
        _uiState.value = UiState(
                isReady = true,
                profileItem = profileItem,
                infoList = localCloseableInfo.toList(),
                menu = menuMap.map { it.value },
                menuTileLayout = menuTileLayout,
                bottomNavDuplicateIds = resolveBottomNavDuplicateIds(),
                shortcuts = shortcuts,
                continueItems = continueItems,
                quickSettings = quickSettings,
                hiddenBlocks = hiddenBlocks
        )
    }

    /** Быстрая настройка «ЧС» — это навигация на экран чёрного списка форума, а не пикер. */
    fun onOpenForumBlackList() {
        router.navigateTo(Screen.ForumBlackList())
    }

    fun onChangeQuickSettings(items: List<QuickSetting>) {
        scope.launch {
            otherPreferencesHolder.setOtherMenuQuickSettings(items)
        }
    }

    fun onToggleBlockHidden(block: OtherMenuBlock) {
        scope.launch {
            val updated = if (hiddenBlocks.contains(block)) hiddenBlocks - block else hiddenBlocks + block
            otherPreferencesHolder.setOtherMenuHiddenBlocks(updated)
        }
    }

    /**
     * «Продолжить чтение» = сесть ровно туда, где пользователь остановился, а не «открыть тему».
     *
     * Раньше строка отдавала URL из истории в [linkHandler] — обычное открытие темы с источником
     * "link" и без хинтов списка. Дальше всё решала настройка «Открытие темы»: FIRST_PAGE сажал на
     * страницу 1, LAST_UNREAD уходил в серверный getnewpost (а 4PDA метит страницы прочитанными по
     * факту GET, так что якорь уезжал на чужой пост). Отсюда и жалоба «перескакивает».
     *
     * Теперь берём клиентскую границу прочитанного ([TopicReadBoundaryStore] — наибольший пост,
     * реально побывавший во вьюпорте) и открываем findpost прямо на неё: резолвер видит явный пост
     * (EXPLICIT_POST) и не подменяет якорь настройкой. Границы нет (тема из старой истории, кэш не
     * прогрет) — честный фолбэк на прежнее поведение.
     */
    fun onContinueClick(item: HistoryItem) {
        val url = item.url ?: return
        val topicId = item.id
        val boundaryPostId = if (topicId > 0) readBoundaryStore.lastSeenPostId(topicId) else 0
        if (topicId > 0 && boundaryPostId > 0) {
            router.navigateTo(Screen.Theme().apply {
                themeUrl = "https://4pda.to/forum/index.php?showtopic=$topicId&view=findpost&p=$boundaryPostId"
                topicOpenSource = "history"
                screenTitle = item.title
            })
            return
        }
        linkHandler.handle(url, router, mapOf(Screen.ARG_TITLE to item.title.orEmpty()))
    }

    private fun resolveBottomNavDuplicateIds(): Set<Int> {
        val visibleBottomSlots = (bottomNavColumns - 1).coerceAtLeast(0)
        return menuMap[MenuRepository.group_main]
                .orEmpty()
                .filter { it.id != MenuRepository.item_auth }
                .take(visibleBottomSlots)
                .map { it.id }
                .toSet()
    }

    fun signOut() {
        scope.launch {
            runCatching { authRepository.signOut() }
                    .onSuccess {
                        router.showSystemMessage("Данные авторизации удалены")
                    }
                    .onFailure { e ->
                        errorHandler.handle(e)
                    }
        }
    }

    fun onMenuClick(item: AppMenuItem) {
        val shortcut = item.shortcut
        if (shortcut != null) {
            // Все типы ярлыков — обычные ссылки 4PDA; разбор темы/раздела/диалога/поиска
            // уже живёт в LinkHandler, дублировать роутинг здесь незачем.
            linkHandler.handle(shortcut.url, router)
            return
        }
        if (item.screen != null) {
            router.navigateTo(item.screen)
            menuRepository.setLastOpened(item.id)
        } else {
            when (item.id) {
                MenuRepository.item_auth -> {
                    onProfileClick()
                    menuRepository.setLastOpened(item.id)
                }
                MenuRepository.item_my_messages -> {
                    if (!authHolder.get().isAuth()) {
                        router.navigateTo(Screen.Auth())
                    } else {
                        val nick = userHolder.user?.nick.orEmpty()
                        if (nick.isEmpty()) {
                            router.navigateTo(Screen.Auth())
                        } else {
                            try {
                                val url = SearchSettings().apply {
                                    source = SearchSettings.SOURCE_CONTENT.first
                                    this.nick = nick
                                    result = SearchSettings.RESULT_POSTS.first
                                }.toUrl()
                                router.navigateTo(Screen.Search().apply { searchUrl = url })
                            } catch (e: Exception) {
                                errorHandler.handle(e)
                            }
                        }
                    }
                    menuRepository.setLastOpened(item.id)
                }
            }
        }
    }

    fun onProfileClick() {
        if (authHolder.get().isAuth()) {
            router.navigateTo(Screen.Profile())
        } else {
            router.navigateTo(Screen.Auth())
        }
    }

    fun onChangeMenuSequence(layout: Map<OtherMenuSection, List<AppMenuItem>>) {
        val sections = listOf(OtherMenuSection.QUICK, OtherMenuSection.PERSONAL, OtherMenuSection.TOOLS)
        val visibleLayout = layout.mapValues { entry -> entry.value.map { it.id } }
        val visibleIds = visibleLayout.values.flatten().toSet()
        val previousHiddenLayout = menuTileLayout.mapValues { entry ->
            entry.value.filterNot { visibleIds.contains(it) }
        }
        menuTileLayout = sections.associateWith { section ->
            visibleLayout[section].orEmpty() + previousHiddenLayout[section].orEmpty()
        }
        scope.launch {
            otherPreferencesHolder.setOtherMenuTileOrder(otherPreferencesHolder.encodeOtherMenuTileLayout(menuTileLayout))
        }
    }

    /**
     * Возврат экрана меню к виду по умолчанию: раскладка плиток, состав быстрых настроек и
     * видимость блоков. Пользовательские плитки-ярлыки при этом сохраняются — их удаляют крестиком.
     */
    fun onResetMenuLayout() {
        menuTileLayout = emptyMap()
        scope.launch {
            otherPreferencesHolder.setOtherMenuTileOrder("")
            otherPreferencesHolder.setOtherMenuQuickSettings(QuickSetting.DEFAULT)
            otherPreferencesHolder.setOtherMenuHiddenBlocks(emptySet())
        }
        publishMenu()
    }

    fun onPickShortcutFromHistory(section: OtherMenuSection) {
        scope.launch {
            val items = runCatching { historyRepository.getHistory() }.getOrDefault(emptyList())
            _shortcutEvents.emit(ShortcutEvent.HistoryLoaded(section, items.take(HISTORY_PICKER_LIMIT)))
        }
    }

    fun onAddShortcut(type: MenuShortcut.Type, title: String, url: String, section: OtherMenuSection) {
        scope.launch {
            menuShortcutsRepository.add(type, title, url, section)
        }
    }

    fun onRemoveShortcut(id: Int) {
        scope.launch {
            menuShortcutsRepository.remove(id)
            // Мёртвый id в сохранённом порядке плиток ничего не ломает, но копится — чистим.
            val cleaned = menuTileLayout.mapValues { entry -> entry.value.filterNot { it == id } }
            if (cleaned != menuTileLayout) {
                menuTileLayout = cleaned
                otherPreferencesHolder.setOtherMenuTileOrder(
                        otherPreferencesHolder.encodeOtherMenuTileLayout(menuTileLayout)
                )
            }
        }
    }

    fun onCloseInfo(item: CloseableInfo) {
        closeableInfoHolder.close(item)
    }

    private companion object {
        const val HISTORY_PICKER_LIMIT = 50
        const val CONTINUE_LIMIT = 3
    }
}
