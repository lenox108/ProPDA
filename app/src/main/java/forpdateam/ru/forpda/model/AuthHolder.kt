package forpdateam.ru.forpda.model

import android.content.SharedPreferences
import forpdateam.ru.forpda.entity.common.AuthData
import forpdateam.ru.forpda.entity.common.AuthState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class AuthHolder(
        private val preferences: SharedPreferences
) {
    private val _auth = MutableStateFlow(createInitialAuth())

    private fun createInitialAuth(): AuthData = AuthData().apply {
        userId = preferences.getString("member_id", null)?.toInt() ?: AuthData.NO_ID
        state = enumValueOf(
                preferences.getString("auth_state", null)
                        ?: AuthState.NO_AUTH.toString()
        )

        val cookieMemberId = preferences.getString("cookie_member_id", null)
        val cookiePassHash = preferences.getString("cookie_pass_hash", null)
        if (cookieMemberId != null && cookiePassHash != null) {
            state = AuthState.AUTH
        }
    }

    init {
        persist(_auth.value)
    }

    private fun persist(value: AuthData) {
        preferences
                .edit()
                .putString("member_id", value.userId.toString())
                .putString("auth_state", value.state.toString())
                .apply()
    }

    fun observe(): Flow<AuthData> = _auth.asStateFlow()

    fun get(): AuthData = _auth.value

    fun set(value: AuthData) {
        persist(value)
        _auth.value = value
    }
}
