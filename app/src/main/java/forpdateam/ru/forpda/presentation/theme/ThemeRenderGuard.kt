package forpdateam.ru.forpda.presentation.theme

import java.security.SecureRandom
import java.util.Base64

class ThemeRenderGuard(
    private val random: SecureRandom = SecureRandom()
) {

    private var token: String? = null

    fun newToken(): String {
        val bytes = ByteArray(TOKEN_BYTES)
        var next: String
        do {
            random.nextBytes(bytes)
            next = encoder.encodeToString(bytes)
        } while (next.isBlank())
        token = next
        return next
    }

    fun currentToken(): String? = token

    fun invalidate() {
        token = null
    }

    fun isValid(token: String?): Boolean {
        val current = this.token ?: return false
        return !token.isNullOrBlank() && token == current
    }

    companion object {
        private const val TOKEN_BYTES = 32
        private val encoder = Base64.getUrlEncoder().withoutPadding()
    }
}
