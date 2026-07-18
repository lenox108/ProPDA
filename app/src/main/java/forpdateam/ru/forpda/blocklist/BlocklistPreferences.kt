package forpdateam.ru.forpda.blocklist

import android.content.SharedPreferences
import javax.inject.Inject

/**
 * Локальный кэш блоклиста (id + ники). Нужен, чтобы блок работал и без сети: если
 * забаненный отрежет доступ к GitHub, применяется последний загруженный список.
 */
class BlocklistPreferences @Inject constructor(
    private val preferences: SharedPreferences
) {

    /** Последний успешно загруженный блоклист. Пустой, если ещё не грузили. */
    fun getCached(): Blocklist {
        val ids = preferences.getString(KEY_BANNED_IDS, null)
            ?.split(',')
            ?.mapNotNull { it.trim().toIntOrNull() }
            ?.toSet()
            ?: emptySet()
        // Ники разделяем '\n' (в нике возможна запятая, но не перевод строки).
        val nicks = preferences.getString(KEY_BANNED_NICKS, null)
            ?.split('\n')
            ?.mapNotNull { it.trim().lowercase().takeIf(String::isNotEmpty) }
            ?.toSet()
            ?: emptySet()
        return Blocklist(ids, nicks)
    }

    fun setCached(blocklist: Blocklist) {
        preferences.edit()
            .putString(KEY_BANNED_IDS, blocklist.ids.sorted().joinToString(","))
            .putString(KEY_BANNED_NICKS, blocklist.nicks.sorted().joinToString("\n"))
            .apply()
    }

    fun getLastCheckTime(): Long = preferences.getLong(KEY_LAST_CHECK_TIME, 0L)

    fun setLastCheckTime(time: Long) {
        preferences.edit().putLong(KEY_LAST_CHECK_TIME, time).apply()
    }

    companion object {
        const val KEY_BANNED_IDS = "blocklist.banned_ids"
        const val KEY_BANNED_NICKS = "blocklist.banned_nicks"
        const val KEY_LAST_CHECK_TIME = "blocklist.last_check_time"
    }
}
