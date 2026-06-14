package forpdateam.ru.forpda.presentation.auth

import forpdateam.ru.forpda.presentation.BaseViewModel

import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import forpdateam.ru.forpda.entity.common.AuthData
import forpdateam.ru.forpda.entity.common.AuthState
import forpdateam.ru.forpda.entity.remote.auth.AuthForm
import forpdateam.ru.forpda.entity.remote.profile.ProfileModel
import forpdateam.ru.forpda.model.AuthHolder
import forpdateam.ru.forpda.model.repository.auth.AuthRepository
import forpdateam.ru.forpda.model.repository.profile.ProfileRepository
import forpdateam.ru.forpda.presentation.IErrorHandler
import forpdateam.ru.forpda.presentation.ISystemLinkHandler
import forpdateam.ru.forpda.presentation.TabRouter

@HiltViewModel
class AuthViewModel @Inject constructor(
        private val authRepository: AuthRepository,
        private val profileRepository: ProfileRepository,
        private val router: TabRouter,
        private val authHolder: AuthHolder,
        private val errorHandler: IErrorHandler,
        private val systemLinkHandler: ISystemLinkHandler
) : BaseViewModel() {

    @Volatile
    private var authView: AuthFragmentCallbacks? = null

    fun attachView(view: AuthFragmentCallbacks) {
        authView = view
    }

    fun detachView() {
        authView = null
    }

    private var formJob: Job? = null
    private var signInJob: Job? = null
    private var subscriptionsStarted = false

    private var fieldsFilled = false
    private var authForm: AuthForm? = null

    fun start() {
        if (subscriptionsStarted) return
        subscriptionsStarted = true
        loadForm()
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
            signInJob?.cancel()
            signInJob = scope.launch {
                authView?.setSendRefreshing(true)
                try {
                    authRepository.signIn(form)
                    authView?.onSuccessAuth()
                    loadProfile("https://4pda.to/forum/index.php?showuser=${authHolder.get().userId}")
                } catch (e: Exception) {
                    form.captcha = null
                    authView?.onFormLoaded(form)
                    loadForm()
                    errorHandler.handle(e)
                } finally {
                    authView?.setSendRefreshing(false)
                }
            }
        }
    }

    fun onClickSkip() {
        val currentState = authHolder.get().state
        authHolder.set(authHolder.get().copy(
            userId = AuthData.NO_ID,
            state = if (currentState != AuthState.AUTH) AuthState.SKIP else currentState
        ))
        router.exit()
    }

    fun onRegistrationClick() {
        systemLinkHandler.handle("https://4pda.to/forum/index.php?act=auth#reg")
    }

    private fun loadForm() {
        formJob?.cancel()
        formJob = scope.launch {
            authView?.setSendEnabled(false)
            try {
                val form = authRepository.loadForm()
                form.apply {
                    nick = authForm?.nick
                    password = authForm?.password
                }
                authForm = form
                authView?.onFormLoaded(form)
            } catch (e: Exception) {
                errorHandler.handle(e)
            } finally {
                authView?.setSendEnabled(fieldsFilled)
            }
        }
    }

    private fun loadProfile(url: String) {
        scope.launch {
            runCatching { profileRepository.loadProfile(url) }
                    .onSuccess { profile ->
                        authView?.showProfile(profile)
                        delayedExit(profile)
                    }
                    .onFailure { errorHandler.handle(it) }
        }
    }

    private fun delayedExit(_profile: ProfileModel) {
        scope.launch {
            delay(2000L)
            router.exit()
        }
    }

}
