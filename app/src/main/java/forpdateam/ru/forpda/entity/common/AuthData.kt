package forpdateam.ru.forpda.entity.common

/**
 * Immutable data class for authentication state.
 * Use copy() to create modified instances.
 */
data class AuthData(
    val userId: Int = NO_ID,
    val state: AuthState = AuthState.NO_AUTH
) {
    companion object {
        const val NO_ID = 0
    }

    fun isAuth() = state == AuthState.AUTH
}