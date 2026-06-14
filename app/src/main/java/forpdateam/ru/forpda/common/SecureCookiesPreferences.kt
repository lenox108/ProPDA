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
    private val masterKey: MasterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val encryptedPrefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        "secure_cookies",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    private val migrationKey = "cookies_migrated_to_encrypted"

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
