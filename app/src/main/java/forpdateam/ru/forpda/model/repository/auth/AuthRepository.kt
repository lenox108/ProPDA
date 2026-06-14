package forpdateam.ru.forpda.model.repository.auth

import forpdateam.ru.forpda.entity.app.profile.IUserHolder
import forpdateam.ru.forpda.entity.common.AuthData
import forpdateam.ru.forpda.entity.common.AuthState
import forpdateam.ru.forpda.entity.remote.auth.AuthForm
import forpdateam.ru.forpda.model.AuthHolder
import forpdateam.ru.forpda.model.CountersHolder
import forpdateam.ru.forpda.model.data.remote.api.auth.AuthApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Created by radiationx on 02.01.18.
 */

class AuthRepository(
        private val authApi: AuthApi,
        private val authHolder: AuthHolder,
        private val countersHolder: CountersHolder,
        private val userHolder: IUserHolder
) {

    suspend fun loadForm(): AuthForm = withContext(Dispatchers.IO) {
        authApi.getForm()
    }

    suspend fun signIn(authForm: AuthForm): AuthForm = withContext(Dispatchers.IO) {
        authApi.login(authForm)
    }

    suspend fun signOut(): Boolean = withContext(Dispatchers.IO) {
        val result = authApi.logout()
        authHolder.set(authHolder.get().copy(
            userId = AuthData.NO_ID,
            state = AuthState.NO_AUTH
        ))
        countersHolder.set(countersHolder.get().apply {
            mentions = 0
            favorites = 0
            qms = 0
        })
        userHolder.user = null
        result
    }

}
