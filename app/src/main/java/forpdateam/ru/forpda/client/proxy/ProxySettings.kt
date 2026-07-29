package forpdateam.ru.forpda.client.proxy

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import timber.log.Timber
import java.util.concurrent.atomic.AtomicInteger

/**
 * Пользовательские настройки прокси (экран «Настройки → Прокси»).
 *
 * Живёт в обычных SharedPreferences (их же пишет [androidx.preference] экрана), КРОМЕ пароля: он
 * лежит в отдельном зашифрованном хранилище — тем же способом, что и auth-куки
 * ([forpdateam.ru.forpda.common.SecureCookiesPreferences]). Если KeyStore недоступен, пароль
 * уходит в обычные prefs (иначе прокси с авторизацией просто перестал бы работать).
 *
 * [version] увеличивается при любом изменении — по нему [forpdateam.ru.forpda.client.Client]
 * пересобирает OkHttp-клиент с новым прокси, не перезапуская приложение.
 */
class ProxySettings(context: Context) {

    private val appContext = context.applicationContext
    private val prefs: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(appContext)
    private val securePrefs: SharedPreferences? = createSecurePrefs()
    private val versionCounter = AtomicInteger(0)

    private val changeListeners = java.util.concurrent.CopyOnWriteArrayList<() -> Unit>()

    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key != null && key.startsWith(KEY_PREFIX)) notifyChanged()
    }

    init {
        prefs.registerOnSharedPreferenceChangeListener(listener)
    }

    /** Меняется при каждой правке настроек — сигнал «пересобери клиент». */
    val version: Int get() = versionCounter.get()

    /**
     * Подписка на правку настроек. Нужна тем, кто не спрашивает [version] сам: клиент с пулом
     * соединений должен закрыть простаивающие, иначе маршрут сменится только через несколько минут.
     */
    fun addChangeListener(listener: () -> Unit) {
        changeListeners += listener
    }

    private fun notifyChanged() {
        versionCounter.incrementAndGet()
        changeListeners.forEach { runCatching { it() } }
    }

    var isEnabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_ENABLED, value).apply()

    val mode: ProxyMode
        get() = if (prefs.getBoolean(KEY_ONLY_BLOCKED, true)) ProxyMode.ONLY_BLOCKED_TOPICS else ProxyMode.ALL

    /**
     * Прокси — платная функция ProPDA Pro, тем же ключом, что и push-уведомления
     * ([forpdateam.ru.forpda.pro.ProLicense]). Проверка стоит здесь, в источнике конфига, а не
     * только на экране настроек: так без ключа маршрут не поднимется, даже если тумблер остался
     * включённым с прошлых версий или из бэкапа настроек.
     */
    fun isUnlocked(): Boolean = forpdateam.ru.forpda.pro.ProLicense.isUnlocked(appContext)

    /** null — прокси выключен, не оплачен или настроен не полностью (нет хоста/порта). */
    fun config(): ProxyConfig? {
        if (!isEnabled || !isUnlocked()) return null
        return ProxyConfig.from(
                type = ProxyType.fromKey(prefs.getString(KEY_TYPE, ProxyType.SOCKS5.key)),
                host = prefs.getString(KEY_HOST, ""),
                port = prefs.getString(KEY_PORT, ""),
                login = prefs.getString(KEY_LOGIN, ""),
                password = readPassword(),
        )
    }

    /**
     * Конфиг без учёта выключателя — для кнопки «Проверить», когда прокси ещё не включён.
     * Ключ Pro проверяется и здесь: «Проверить» ходит в сеть через прокси, то есть пользуется
     * ровно той функцией, за которую платят.
     */
    fun configIgnoringEnabled(): ProxyConfig? = if (!isUnlocked()) null else ProxyConfig.from(
            type = ProxyType.fromKey(prefs.getString(KEY_TYPE, ProxyType.SOCKS5.key)),
            host = prefs.getString(KEY_HOST, ""),
            port = prefs.getString(KEY_PORT, ""),
            login = prefs.getString(KEY_LOGIN, ""),
            password = readPassword(),
    )

    fun readPassword(): String =
            securePrefs?.getString(KEY_PASSWORD, null)
                    ?: prefs.getString(KEY_PASSWORD, "").orEmpty()

    fun writePassword(value: String) {
        val store = securePrefs ?: prefs
        store.edit().putString(KEY_PASSWORD, value).apply()
        // Зашифрованное хранилище — отдельный файл, слушатель обычных prefs про него не узнает.
        if (store !== prefs) notifyChanged()
    }

    private fun createSecurePrefs(): SharedPreferences? = try {
        val masterKey = MasterKey.Builder(appContext)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
        EncryptedSharedPreferences.create(
                appContext,
                "secure_proxy",
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    } catch (e: Throwable) {
        Timber.w(e, "Encrypted proxy store unavailable; proxy password will live in plain prefs")
        null
    }

    companion object {
        const val KEY_PREFIX = "net.proxy."
        const val KEY_ENABLED = "net.proxy.enabled"
        const val KEY_TYPE = "net.proxy.type"
        const val KEY_HOST = "net.proxy.host"
        const val KEY_PORT = "net.proxy.port"
        const val KEY_LOGIN = "net.proxy.login"
        const val KEY_PASSWORD = "net.proxy.password"
        const val KEY_ONLY_BLOCKED = "net.proxy.only_blocked"
    }
}
