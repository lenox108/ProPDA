package forpdateam.ru.forpda.presentation.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import forpdateam.ru.forpda.entity.common.AuthData
import forpdateam.ru.forpda.entity.common.AuthState
import forpdateam.ru.forpda.entity.remote.auth.AuthForm
import forpdateam.ru.forpda.entity.remote.profile.ProfileModel
import forpdateam.ru.forpda.model.AuthHolder
import forpdateam.ru.forpda.model.SchedulersProvider
import forpdateam.ru.forpda.model.repository.auth.AuthRepository
import forpdateam.ru.forpda.model.repository.profile.ProfileRepository
import forpdateam.ru.forpda.presentation.IErrorHandler
import forpdateam.ru.forpda.presentation.ISystemLinkHandler
import forpdateam.ru.forpda.presentation.TabRouter
import io.reactivex.disposables.CompositeDisposable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class AuthViewModel(
        private val authRepository: AuthRepository,
        private val profileRepository: ProfileRepository,
        private val router: TabRouter,
        private val schedulers: SchedulersProvider,
        private val authHolder: AuthHolder,
        private val errorHandler: IErrorHandler,
        private val systemLinkHandler: ISystemLinkHandler
) : ViewModel() {

    @Volatile
    private var authView: AuthFragmentCallbacks? = null

    fun attachView(view: AuthFragmentCallbacks) {
        authView = view
    }

    fun detachView() {
        authView = null
    }

    private val rxSubscriptions = CompositeDisposable()
    private var subscriptionsStarted = false

    private var fieldsFilled = false
    private var authForm: AuthForm? = null

    fun start() {
        if (subscriptionsStarted) return
        subscriptionsStarted = true
        loadForm()
    }

    override fun onCleared() {
        rxSubscriptions.clear()
        super.onCleared()
    }

    fun setFieldsFilled(isFilled: Boolean) {
        fieldsFilled = isFilled
        authView?.setSendEnabled(fieldsFilled)
    }

    fun signIn(
            nick: String,
            password: String,
            captcha: String,
            isHidden: Boolean
    ) {
        authForm?.also { form ->
            form.nick = nick
            form.password = password
            form.captcha = captcha
            form.isHidden = isHidden
            authRepository
                    .signIn(form)
                    .doOnSubscribe { authView?.setSendRefreshing(true) }
                    .doAfterTerminate { authView?.setSendRefreshing(false) }
                    .subscribe({
                        authView?.onSuccessAuth()
                        loadProfile("https://4pda.to/forum/index.php?showuser=${authHolder.get().userId}")
                    }, {
                        form.captcha = null
                        authView?.onFormLoaded(form)
                        loadForm()
                        errorHandler.handle(it)
                    })
                    .also { rxSubscriptions.add(it) }
        }
    }

    fun onClickSkip() {
        authHolder.set(authHolder.get().apply {
            userId = AuthData.NO_ID
            if (authHolder.get().state != AuthState.AUTH) {
                state = AuthState.SKIP
            }
        })
        router.exit()
    }

    fun onRegistrationClick() {
        systemLinkHandler.handle("https://4pda.to/forum/index.php?act=auth#reg")
    }

    private fun loadForm() {
        authRepository
                .loadForm()
                .doOnSubscribe { authView?.setSendEnabled(false) }
                .doAfterTerminate { authView?.setSendEnabled(fieldsFilled) }
                .subscribe({
                    it.apply {
                        nick = authForm?.nick
                        password = authForm?.password
                    }
                    authForm = it
                    authView?.onFormLoaded(it)
                }, {
                    errorHandler.handle(it)
                })
                .also { rxSubscriptions.add(it) }
    }

    private fun loadProfile(url: String) {
        viewModelScope.launch {
            runCatching { profileRepository.loadProfile(url) }
                    .onSuccess { profile ->
                        authView?.showProfile(profile)
                        delayedExit(profile)
                    }
                    .onFailure { errorHandler.handle(it) }
        }
    }

    private fun delayedExit(@Suppress("UNUSED_PARAMETER") profile: ProfileModel) {
        viewModelScope.launch {
            delay(2000L)
            router.exit()
        }
    }

    class Factory(
            private val authRepository: AuthRepository,
            private val profileRepository: ProfileRepository,
            private val router: TabRouter,
            private val schedulers: SchedulersProvider,
            private val authHolder: AuthHolder,
            private val errorHandler: IErrorHandler,
            private val systemLinkHandler: ISystemLinkHandler
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass != AuthViewModel::class.java) {
                throw IllegalArgumentException("Unknown ViewModel class")
            }
            return AuthViewModel(
                    authRepository,
                    profileRepository,
                    router,
                    schedulers,
                    authHolder,
                    errorHandler,
                    systemLinkHandler
            ) as T
        }
    }
}
