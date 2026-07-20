package forpdateam.ru.forpda.common

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import androidx.preference.PreferenceManager
import timber.log.Timber

/**
 * Зашифрованное хранилище для cookies.
 * Использует AndroidX Security Crypto API с MasterKey.
 */
class SecureCookiesPreferences private constructor(context: Context) {

    private val appContext: Context = context.applicationContext

    /**
     * Encrypted store backed by the AndroidKeyStore. Creating it can fail when the
     * keystore is unavailable (no hardware/emulated keystore — e.g. Robolectric
     * unit tests) or when the previously persisted master key / store is corrupted
     * on-device. In those cases we fall back to a plain [SharedPreferences] so the
     * app keeps working instead of crashing on startup; cookies are simply not
     * encrypted at rest in that degraded mode.
     */
    /**
     * true, если зашифрованное хранилище создать не удалось и мы в деградированном режиме
     * (пустой plain-fallback). Полевой лог показал, что в холодном фоновом процессе это роняло
     * авторизацию: worker: skip (not authorized), хотя пользователь залогинен. Флаг — для
     * диагностики в журнале уведомлений.
     */
    @Volatile
    var isUsingFallback: Boolean = false
        private set

    private val encryptedPrefs: SharedPreferences = createEncryptedPrefsWithRetry(context)
        ?: context.getSharedPreferences("secure_cookies_fallback", Context.MODE_PRIVATE).also {
            isUsingFallback = true
        }

    private val migrationKey = "cookies_migrated_to_encrypted"

    /**
     * AndroidKeyStore нередко «не готов» в первые мгновения холодного/фонового старта, и
     * одна попытка отдавала пустой fallback → потеря auth-куки в фоне. Несколько попыток с
     * короткой паузой вытягивают transient-сбой, не теряя доступ к уже сохранённым кукам.
     */
    private fun createEncryptedPrefsWithRetry(context: Context): SharedPreferences? {
        repeat(ENCRYPTED_CREATE_ATTEMPTS) { attempt ->
            createEncryptedPrefs(context)?.let { return it }
            if (attempt < ENCRYPTED_CREATE_ATTEMPTS - 1) {
                runCatching { Thread.sleep(ENCRYPTED_CREATE_RETRY_MS) }
            }
        }
        return null
    }

    private fun createEncryptedPrefs(context: Context): SharedPreferences? {
        return try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                context,
                "secure_cookies",
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Throwable) {
            Timber.e(e, "Encrypted cookie store unavailable; falling back to plain prefs")
            null
        }
    }

    init {
        migrateFromOldPrefs()
    }

    /**
     * Миграция cookie_* ключей из обычных SharedPreferences в зашифрованные.
     * Выполняется один раз при первом запуске.
     */
    private fun migrateFromOldPrefs() {
        if (encryptedPrefs.getBoolean(migrationKey, false)) {
            return // Уже мигрировано
        }

        try {
            val oldPrefs = PreferenceManager.getDefaultSharedPreferences(appContext)
            val editor = encryptedPrefs.edit()

            // Ключи для миграции
            val cookieKeys = listOf(
                "cookie_member_id",
                "cookie_pass_hash",
                "cookie_session_id",
                "cookie_anonymous",
                "cookie_cf_clearance"
            )

            var migratedCount = 0
            for (key in cookieKeys) {
                val value = oldPrefs.getString(key, null)
                if (value != null) {
                    editor.putString(key, value)
                    // Удаляем из старых prefs после успешной миграции
                    oldPrefs.edit().remove(key).apply()
                    migratedCount++
                }
            }

            editor.putBoolean(migrationKey, true)
            editor.apply()

            if (migratedCount > 0) {
                Timber.d("Migrated $migratedCount cookie keys to encrypted storage")
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to migrate cookies to encrypted storage")
        }
    }

    /**
     * Получить строковое значение из зашифрованного хранилища.
     */
    fun getString(key: String, defaultValue: String? = null): String? {
        return encryptedPrefs.getString(key, defaultValue)
    }

    /**
     * Сохранить строковое значение в зашифрованное хранилище.
     */
    fun putString(key: String, value: String?) {
        encryptedPrefs.edit().putString(key, value).apply()
    }

    /**
     * Удалить ключ из зашифрованного хранилища.
     */
    fun remove(key: String) {
        encryptedPrefs.edit().remove(key).apply()
    }

    companion object {
        /** Попыток создать зашифрованное хранилище до fallback (KeyStore transient на cold start). */
        private const val ENCRYPTED_CREATE_ATTEMPTS = 3
        private const val ENCRYPTED_CREATE_RETRY_MS = 120L

        @Volatile
        private var instance: SecureCookiesPreferences? = null

        fun getInstance(context: Context): SecureCookiesPreferences {
            return instance ?: synchronized(this) {
                instance ?: SecureCookiesPreferences(context.applicationContext).also {
                    instance = it
                }
            }
        }
    }
}
