package forpdateam.ru.forpda.presentation.other

import moxy.InjectViewState
import forpdateam.ru.forpda.common.mvp.BasePresenter
import forpdateam.ru.forpda.entity.app.CloseableInfo
import forpdateam.ru.forpda.entity.app.other.AppMenuItem
import forpdateam.ru.forpda.entity.app.profile.IUserHolder
import forpdateam.ru.forpda.entity.remote.profile.ProfileModel
import forpdateam.ru.forpda.model.AuthHolder
import forpdateam.ru.forpda.model.CloseableInfoHolder
import forpdateam.ru.forpda.model.interactors.other.MenuRepository
import forpdateam.ru.forpda.model.repository.auth.AuthRepository
import forpdateam.ru.forpda.model.repository.profile.ProfileRepository
import forpdateam.ru.forpda.presentation.*
import java.io.UnsupportedEncodingException
import java.net.URLEncoder

@InjectViewState
class OtherPresenter(
        private val router: TabRouter,
        private val authRepository: AuthRepository,
        private val profileRepository: ProfileRepository,
        private val authHolder: AuthHolder,
        private val userHolder: IUserHolder,
        private val errorHandler: IErrorHandler,
        private val menuRepository: MenuRepository,
        private val closeableInfoHolder: CloseableInfoHolder
) : BasePresenter<OtherView>() {

    private val closeableInfoIds = arrayOf(
            CloseableInfoHolder.item_other_menu_drag
    )

    private var localMenu = mapOf<Int, List<AppMenuItem>>()
    private val localCloseableInfo = mutableListOf<CloseableInfo>()

    private var profileItem: ProfileModel? = null

    private var isMenuDragMode = false

    override fun onFirstViewAttach() {
        super.onFirstViewAttach()
        subscribeUser()
        authHolder
                .observe()
                .subscribe {
                    if (!authHolder.get().isAuth()) {
                        profileItem = null
                    }
                    updateMenuItems()
                }
                .untilDestroy()

        menuRepository
                .observerMenu()
                .subscribe {
                    localMenu = it
                    updateMenuItems()
                }
                .untilDestroy()

        closeableInfoHolder
                .observe()
                .subscribe { info ->
                    localCloseableInfo.clear()
                    localCloseableInfo.addAll(info.filter { closeableInfoIds.contains(it.id) && !it.isClosed })
                    updateMenuItems()
                }
                .untilDestroy()
    }

    fun onMenuDragModeChange(isDragMode: Boolean) {
        isMenuDragMode = isDragMode
        updateMenuItems()
    }

    private fun updateMenuItems() {
        if (!isMenuDragMode) {
            viewState.showItems(profileItem, localCloseableInfo, localMenu.map { it.value })
        }
    }

    private fun subscribeUser() {
        profileRepository
                .loadSelf()
                .subscribe({}, {})
                .untilDestroy()
        profileRepository
                .observeCurrentUser()
                .subscribe {
                    profileItem = it.value
                    updateMenuItems()
                }
                .untilDestroy()
    }

    fun signOut() {
        authRepository
                .signOut()
                .subscribe({
                    router.showSystemMessage("Данные авторизации удалены")
                }, {
                    errorHandler.handle(it)
                })
                .untilDestroy()
    }

    fun onMenuClick(item: AppMenuItem) {
        if (item.screen != null) {
            item.screen.also {
                router.navigateTo(it)
            }
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
}
