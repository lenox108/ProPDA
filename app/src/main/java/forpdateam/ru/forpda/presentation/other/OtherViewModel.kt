package forpdateam.ru.forpda.presentation.other

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import forpdateam.ru.forpda.entity.app.CloseableInfo
import forpdateam.ru.forpda.entity.app.other.AppMenuItem
import forpdateam.ru.forpda.entity.app.profile.IUserHolder
import forpdateam.ru.forpda.entity.remote.profile.ProfileModel
import forpdateam.ru.forpda.model.AuthHolder
import forpdateam.ru.forpda.model.CloseableInfoHolder
import forpdateam.ru.forpda.model.interactors.other.MenuRepository
import forpdateam.ru.forpda.model.repository.auth.AuthRepository
import forpdateam.ru.forpda.model.repository.profile.ProfileRepository
import forpdateam.ru.forpda.presentation.IErrorHandler
import forpdateam.ru.forpda.presentation.Screen
import forpdateam.ru.forpda.presentation.TabRouter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.rx2.asFlow
import kotlinx.coroutines.rx2.await
import java.io.UnsupportedEncodingException
import java.net.URLEncoder

/**
 * Экран «Полное меню» без Moxy.
 */
class OtherViewModel(
        private val router: TabRouter,
        private val authRepository: AuthRepository,
        private val profileRepository: ProfileRepository,
        private val authHolder: AuthHolder,
        private val userHolder: IUserHolder,
        private val errorHandler: IErrorHandler,
        private val menuRepository: MenuRepository,
        private val closeableInfoHolder: CloseableInfoHolder
) : ViewModel() {

    data class UiState(
            val profileItem: ProfileModel? = null,
            val infoList: List<CloseableInfo> = emptyList(),
            val menu: List<List<AppMenuItem>> = emptyList()
    )

    private val closeableInfoIds = arrayOf(CloseableInfoHolder.item_other_menu_drag)

    private var profileItem: ProfileModel? = null
    private var menuMap: Map<Int, List<AppMenuItem>> = emptyMap()
    private val localCloseableInfo = mutableListOf<CloseableInfo>()
    private var isMenuDragMode = false

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            runCatching { profileRepository.loadSelf() }
        }
        viewModelScope.launch {
            profileRepository.observeCurrentUser()
                    .catch { }
                    .collect { wrapper ->
                        profileItem = wrapper.value
                        publishMenu()
                    }
        }
        viewModelScope.launch {
            authHolder.observe()
                    .catch { }
                    .collect {
                        if (!authHolder.get().isAuth()) {
                            profileItem = null
                        }
                        publishMenu()
                    }
        }
        viewModelScope.launch {
            menuRepository.observerMenu()
                    .asFlow()
                    .catch { }
                    .collect {
                        menuMap = it
                        publishMenu()
                    }
        }
        viewModelScope.launch {
            closeableInfoHolder.observe()
                    .asFlow()
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
    }

    fun onMenuDragModeChange(isDragMode: Boolean) {
        isMenuDragMode = isDragMode
        publishMenu()
    }

    private fun publishMenu() {
        if (isMenuDragMode) return
        _uiState.value = UiState(
                profileItem = profileItem,
                infoList = localCloseableInfo.toList(),
                menu = menuMap.map { it.value }
        )
    }

    fun signOut() {
        viewModelScope.launch {
            runCatching { authRepository.signOut().await() }
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
                MenuRepository.item_my_messages -> {
                    if (!authHolder.get().isAuth()) {
                        router.navigateTo(Screen.Auth())
                    } else {
                        val nick = userHolder.user?.nick.orEmpty()
                        if (nick.isEmpty()) {
                            router.navigateTo(Screen.Auth())
                        } else {
                            try {
                                val url = ("https://4pda.to/forum/index.php?act=search&source=pst&result=posts&username=" +
                                        URLEncoder.encode(nick, "windows-1251"))
                                router.navigateTo(Screen.Search().apply { searchUrl = url })
                            } catch (e: UnsupportedEncodingException) {
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

    fun onChangeMenuSequence(items: List<AppMenuItem>) {
        menuRepository.setMainMenuSequence(items)
    }

    fun onCloseInfo(item: CloseableInfo) {
        closeableInfoHolder.close(item)
    }

    class Factory(
            private val router: TabRouter,
            private val authRepository: AuthRepository,
            private val profileRepository: ProfileRepository,
            private val authHolder: AuthHolder,
            private val userHolder: IUserHolder,
            private val errorHandler: IErrorHandler,
            private val menuRepository: MenuRepository,
            private val closeableInfoHolder: CloseableInfoHolder
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass != OtherViewModel::class.java) {
                throw IllegalArgumentException("Unknown ViewModel class")
            }
            return OtherViewModel(
                    router,
                    authRepository,
                    profileRepository,
                    authHolder,
                    userHolder,
                    errorHandler,
                    menuRepository,
                    closeableInfoHolder
            ) as T
        }
    }
}
