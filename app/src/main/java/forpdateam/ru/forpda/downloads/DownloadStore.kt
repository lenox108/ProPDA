package forpdateam.ru.forpda.downloads

import android.content.SharedPreferences
import java.util.UUID

class DownloadStore(private val prefs: SharedPreferences) {

    data class Meta(
        val url: String,
        val fileName: String,
        val mime: String?,
        val completedAt: Long?
    )

    fun put(id: UUID, url: String, fileName: String, mime: String?) {
        prefs.edit()
            .putString(key(id, "url"), url)
            .putString(key(id, "fileName"), fileName)
            .putString(key(id, "mime"), mime)
            .apply()
    }

    fun get(id: UUID): Meta? {
        val url = prefs.getString(key(id, "url"), null) ?: return null
        val fileName = prefs.getString(key(id, "fileName"), null) ?: return null
        val mime = prefs.getString(key(id, "mime"), null)
        val completedAt = prefs.getLong(key(id, "completedAt"), 0L).takeIf { it > 0L }
        return Meta(url = url, fileName = fileName, mime = mime, completedAt = completedAt)
    }

    fun markCompleted(id: UUID, completedAt: Long = System.currentTimeMillis()) {
        prefs.edit()
            .putLong(key(id, "completedAt"), completedAt)
            .apply()
    }

    fun remove(id: UUID) {
        prefs.edit()
            .remove(key(id, "url"))
            .remove(key(id, "fileName"))
            .remove(key(id, "mime"))
            .remove(key(id, "completedAt"))
            .apply()
    }

    fun clearAll() {
        val editor = prefs.edit()
        for (key in prefs.all.keys) {
            if (key.startsWith("download:")) editor.remove(key)
        }
        editor.apply()
    }

    private fun key(id: UUID, field: String) = "download:${id}:$field"
}
