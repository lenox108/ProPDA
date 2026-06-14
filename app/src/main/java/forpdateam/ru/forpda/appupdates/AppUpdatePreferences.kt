package forpdateam.ru.forpda.appupdates

import android.content.SharedPreferences
import javax.inject.Inject

class AppUpdatePreferences @Inject constructor(
    private val preferences: SharedPreferences
) {

    fun isCheckEnabled(): Boolean = preferences.getBoolean(KEY_CHECK_ENABLED, true)

    fun setCheckEnabled(value: Boolean) {
        preferences.edit().putBoolean(KEY_CHECK_ENABLED, value).apply()
    }

    fun getLastNotifiedVersion(): String? = preferences.getString(KEY_LAST_NOTIFIED_VERSION, null)

    fun setLastNotifiedVersion(version: String) {
        preferences.edit().putString(KEY_LAST_NOTIFIED_VERSION, version).apply()
    }

    fun getLastFoundVersion(): String? = preferences.getString(KEY_LAST_FOUND_VERSION, null)

    fun setLastFoundVersion(version: String?) {
        preferences.edit().apply {
            if (version.isNullOrBlank()) remove(KEY_LAST_FOUND_VERSION) else putString(KEY_LAST_FOUND_VERSION, version)
        }.apply()
    }

    fun getLastCheckTime(): Long = preferences.getLong(KEY_LAST_CHECK_TIME, 0L)

    fun setLastCheckTime(time: Long) {
        preferences.edit().putLong(KEY_LAST_CHECK_TIME, time).apply()
    }

    companion object {
        const val KEY_CHECK_ENABLED = "app_updates.check_enabled"
        const val KEY_LAST_NOTIFIED_VERSION = "app_updates.last_notified_version"
        const val KEY_LAST_FOUND_VERSION = "app_updates.last_found_version"
        const val KEY_LAST_CHECK_TIME = "app_updates.last_check_time"
    }
}
