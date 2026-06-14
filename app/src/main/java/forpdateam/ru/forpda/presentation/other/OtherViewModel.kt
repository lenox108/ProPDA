package forpdateam.ru.forpda.presentation.other

import forpdateam.ru.forpda.presentation.BaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

import forpdateam.ru.forpda.entity.app.CloseableInfo
import forpdateam.ru.forpda.entity.app.other.AppMenuItem
import forpdateam.ru.forpda.entity.app.profile.IUserHolder
import forpdateam.ru.forpda.entity.remote.profile.ProfileModel
import forpdateam.ru.forpda.entity.remote.search.SearchSettings
import forpdateam.ru.forpda.model.AuthHolder
import forpdateam.ru.forpda.model.CloseableInfoHolder
import forpdateam.ru.forpda.model.interactors.other.MenuRepository
import forpdateam.ru.forpda.model.preferences.MainPreferencesHolder
import forpdateam.ru.forpda.model.preferences.OtherPreferencesHolder
import forpdateam.ru.forpda.model.repository.auth.AuthRepository
import forpdateam.ru.forpda.model.repository.profile.ProfileRepository
import forpdateam.ru.forpda.presentation.IErrorHandler
import forpdateam.ru.forpda.presentation.Screen
import forpdateam.ru.forpda.presentation.TabRouter
import forpdateam.ru.forpda.ui.views.drawers.adapters.OtherMenuSection
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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
        private val otherPreferencesHolder: OtherPreferencesHolder
) : BaseViewModel() {

    data class UiState(
            val isReady: Boolean = false,
            val profileItem: ProfileModel? = null,
            val infoList: List<CloseableInfo> = emptyList(),
            val menu: List<List<AppMenuItem>> = emptyList(),
            val menuTileLayout: Map<OtherMenuSection, List<Int>> = emptyMap(),
            val bottomNavDuplicateIds: Set<Int> = emptySet()
    )

    private val closeableInfoIds = arrayOf(CloseableInfoHolder.item_other_menu_drag)

    private var profileItem: ProfileModel? = null
    private var menuMap: Map<Int, List<AppMenuItem>> = emptyMap()
    private var menuTileLayout: Map<OtherMenuSection, List<Int>> = emptyMap()
    private var bottomNavColumns = mainPreferencesHolder.getBottomNavColumns()
    private var isMenuLoaded = false
    private var isMenuTileLayoutLoaded = false
    private val localCloseableInfo = mutableListOf<CloseableInfo>()
    private var isMenuDragMode = false

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
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
    }

    fun onMenuDragModeChange(isDragMode: Boolean) {
        isMenuDragMode = isDragMode
        publishMenu()
    }

    private fun publishMenu() {
        if (isMenuDragMode) return
        if (!isMenuLoaded || !isMenuTileLayoutLoaded) return
        _uiState.value = UiState(
                isReady = true,
                profileItem = profileItem,
                infoList = localCloseableInfo.toList(),
                menu = menuMap.map { it.value },
                menuTileLayout = menuTileLayout,
                bottomNavDuplicateIds = resolveBottomNavDuplicateIds()
        )
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

    fun onCloseInfo(item: CloseableInfo) {
        closeableInfoHolder.close(item)
    }

}
