package forpdateam.ru.forpda.presentation.auth

import forpdateam.ru.forpda.entity.remote.auth.AuthForm
import forpdateam.ru.forpda.entity.remote.profile.ProfileModel

/** Колбэки экрана авторизации для [AuthViewModel]. */
interface AuthFragmentCallbacks {
    fun setSendEnabled(isEnabled: Boolean)
    fun setSendRefreshing(isRefreshing: Boolean)
    fun onFormLoaded(authForm: AuthForm)
    fun onSuccessAuth()
    fun showProfile(profile: ProfileModel)
}
