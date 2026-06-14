package forpdateam.ru.forpda.presentation.main

import forpdateam.ru.forpda.presentation.BaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import forpdateam.ru.forpda.entity.common.AuthState
import forpdateam.ru.forpda.common.Preferences
import forpdateam.ru.forpda.model.AuthHolder
import forpdateam.ru.forpda.model.data.remote.IWebClient
import forpdateam.ru.forpda.model.interactors.qms.QmsInteractor
import forpdateam.ru.forpda.model.preferences.MainPreferencesHolder
import forpdateam.ru.forpda.model.preferences.OtherPreferencesHolder
import forpdateam.ru.forpda.presentation.ILinkHandler
import forpdateam.ru.forpda.presentation.Screen
import forpdateam.ru.forpda.presentation.TabRouter

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@HiltViewModel
class MainViewModel @Inject constructor(
        private val router: TabRouter,
        private val authHolder: AuthHolder,
        private val linkHandler: ILinkHandler,
        private val mainPreferencesHolder: MainPreferencesHolder,
        private val qmsInteractor: QmsInteractor,
        private val otherPreferencesHolder: OtherPreferencesHolder,
        private val webClient: IWebClient
) : BaseViewModel() {

    @Volatile
    private var mainView: MainActivityCallbacks? = null

    fun attachView(view: MainActivityCallbacks) {
        mainView = view
    }

    fun detachView() {
        mainView = null
    }

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

        scope.launch {
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
                    router.navigateTo(resolveStartupScreen(authState))
                }
            }
        }
    }

    private fun resolveStartupScreen(authState: AuthState): Screen {
        val startup = mainPreferencesHolder.getStartupScreen()
        if (startup.requiresAuth() && authState == AuthState.NO_AUTH) {
            return Screen.ArticleList().apply { fromMenu = true }
        }
        return startup.toScreen().apply { fromMenu = true }
    }

    private fun Preferences.Main.StartupScreen.requiresAuth(): Boolean =
            this == Preferences.Main.StartupScreen.FAVORITES ||
                    this == Preferences.Main.StartupScreen.REPLIES ||
                    this == Preferences.Main.StartupScreen.QMS

    private fun Preferences.Main.StartupScreen.toScreen(): Screen = when (this) {
        Preferences.Main.StartupScreen.NEWS -> Screen.ArticleList()
        Preferences.Main.StartupScreen.FAVORITES -> Screen.Favorites()
        Preferences.Main.StartupScreen.FORUM -> Screen.Forum()
        Preferences.Main.StartupScreen.REPLIES -> Screen.Mentions()
        Preferences.Main.StartupScreen.QMS -> Screen.QmsContacts()
    }

    fun openLink(url: String) {
        linkHandler.handle(url, router)
    }

    fun onActivityResume() {
        if (!authHolder.get().isAuth()) return
        val now = System.currentTimeMillis()
        if (now - lastMenuCountersRefreshAt < 30_000L) return
        lastMenuCountersRefreshAt = now
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) { webClient.refreshMenuCountersSilently() }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
    }

}
