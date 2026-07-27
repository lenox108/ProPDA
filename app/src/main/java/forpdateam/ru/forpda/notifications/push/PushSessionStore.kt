package forpdateam.ru.forpda.notifications.push

import android.content.Context
import forpdateam.ru.forpda.common.SecureCookiesPreferences

/**
 * Хранилище app-протокольной сессии для push: member_id + login_key (получены один раз через
 * `ml`-логин), плюс последний зарегистрированный токен/битмаск (чтобы не слать `ai` повторно без
 * изменений). login_key — долгоживущий секрет входа, поэтому лежит в том же зашифрованном
 * хранилище, что и cookies ([SecureCookiesPreferences]).
 */
class PushSessionStore(context: Context) {

    private val prefs = SecureCookiesPreferences.getInstance(context)

    var memberId: Int
        get() = prefs.getString(KEY_MEMBER_ID, null)?.toIntOrNull() ?: 0
        set(value) = prefs.putString(KEY_MEMBER_ID, value.toString())

    var loginKey: String?
        get() = prefs.getString(KEY_LOGIN_KEY, null)
        set(value) = if (value == null) prefs.remove(KEY_LOGIN_KEY) else prefs.putString(KEY_LOGIN_KEY, value)

    /** Последний успешно зарегистрированный токен — для дедупа `ai`. */
    var lastRegisteredToken: String?
        get() = prefs.getString(KEY_LAST_TOKEN, null)
        set(value) = if (value == null) prefs.remove(KEY_LAST_TOKEN) else prefs.putString(KEY_LAST_TOKEN, value)

    var lastRegisteredBitmask: Int
        get() = prefs.getString(KEY_LAST_BITMASK, null)?.toIntOrNull() ?: -1
        set(value) = prefs.putString(KEY_LAST_BITMASK, value.toString())

    fun hasSession(): Boolean = memberId != 0 && !loginKey.isNullOrEmpty()

    fun saveSession(memberId: Int, loginKey: String) {
        this.memberId = memberId
        this.loginKey = loginKey
    }

    fun clear() {
        prefs.remove(KEY_LOGIN_KEY)
        prefs.remove(KEY_LAST_TOKEN)
        prefs.remove(KEY_LAST_BITMASK)
    }

    companion object {
        private const val KEY_MEMBER_ID = "push_member_id"
        private const val KEY_LOGIN_KEY = "push_login_key"
        private const val KEY_LAST_TOKEN = "push_last_token"
        private const val KEY_LAST_BITMASK = "push_last_bitmask"
    }
}
