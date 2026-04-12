package forpdateam.ru.forpda.presentation.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import forpdateam.ru.forpda.entity.common.AuthState
import forpdateam.ru.forpda.model.AuthHolder
import forpdateam.ru.forpda.model.data.remote.IWebClient
import forpdateam.ru.forpda.model.interactors.other.MenuRepository
import forpdateam.ru.forpda.model.interactors.qms.QmsInteractor
import forpdateam.ru.forpda.model.preferences.OtherPreferencesHolder
import forpdateam.ru.forpda.presentation.ILinkHandler
import forpdateam.ru.forpda.presentation.Screen
import forpdateam.ru.forpda.presentation.TabRouter
import io.reactivex.Single
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.schedulers.Schedulers

class MainViewModel(
        private val router: TabRouter,
        private val authHolder: AuthHolder,
        private val linkHandler: ILinkHandler,
        private val menuRepository: MenuRepository,
        private val qmsInteractor: QmsInteractor,
        private val otherPreferencesHolder: OtherPreferencesHolder,
        private val webClient: IWebClient
) : ViewModel() {

    @Volatile
    private var mainView: MainActivityCallbacks? = null

    fun attachView(view: MainActivityCallbacks) {
        mainView = view
    }

    fun detachView() {
        mainView = null
    }

    private val rxSubscriptions = CompositeDisposable()
    private var flowStarted = false

    private var isRestored: Boolean = false
    private var startLink: String = ""

    @Volatile
    private var lastMenuCountersRefreshAt: Long = 0L

    fun setIsRestored(restored: Boolean) {
        isRestored = restored
    }

    fun setStartLink(link: String) {
        startLink = link
    }

    fun start() {
        if (flowStarted) return
        flowStarted = true
        qmsInteractor.subscribeEvents()

        val firstAppStart = otherPreferencesHolder.getAppFirstStart()
        if (firstAppStart) {
            mainView?.showFirstStartAnimation()
            otherPreferencesHolder.setAppFirstStart(false)
        }

        val linkHandled = linkHandler.handle(startLink, router)

        if (!isRestored && !linkHandled) {
            val authState = authHolder.get().state
            if (firstAppStart && authState == AuthState.NO_AUTH) {
                router.navigateTo(Screen.Auth())
            } else {
                val lastMenuId = menuRepository.getLastOpened()
                val screen: Screen = if (menuRepository.menuItemContains(lastMenuId)) {
                    menuRepository.getMenuItem(lastMenuId).screen ?: Screen.ArticleList()
                } else {
                    Screen.ArticleList()
                }
                router.navigateTo(screen)
            }
        }
    }

    fun openLink(url: String) {
        linkHandler.handle(url, router)
    }

    fun onActivityResume() {
        if (!authHolder.get().isAuth()) return
        val now = System.currentTimeMillis()
        if (now - lastMenuCountersRefreshAt < 30_000L) return
        lastMenuCountersRefreshAt = now
        Single.fromCallable {
            webClient.refreshMenuCountersSilently()
        }
                .subscribeOn(Schedulers.io())
                .subscribe({}, {})
                .also { rxSubscriptions.add(it) }
    }

    override fun onCleared() {
        rxSubscriptions.clear()
        super.onCleared()
    }

    class Factory(
            private val router: TabRouter,
            private val authHolder: AuthHolder,
            private val linkHandler: ILinkHandler,
            private val menuRepository: MenuRepository,
            private val qmsInteractor: QmsInteractor,
            private val otherPreferencesHolder: OtherPreferencesHolder,
            private val webClient: IWebClient
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass != MainViewModel::class.java) {
                throw IllegalArgumentException("Unknown ViewModel class")
            }
            return MainViewModel(
                    router,
                    authHolder,
                    linkHandler,
                    menuRepository,
                    qmsInteractor,
                    otherPreferencesHolder,
                    webClient
            ) as T
        }
    }
}
