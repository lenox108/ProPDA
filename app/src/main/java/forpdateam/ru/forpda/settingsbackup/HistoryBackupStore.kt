package forpdateam.ru.forpda.settingsbackup

import androidx.room.withTransaction
import forpdateam.ru.forpda.entity.db.history.HistoryItemRoom
import forpdateam.ru.forpda.entity.db.notes.AppDatabase
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * История посещений в файле бэкапа. Хранится только на устройстве — на сервере её нет,
 * так что без этого раздела она теряется при переезде безвозвратно.
 *
 * Восстановление — замена, как и у остальных разделов бэкапа.
 */
class HistoryBackupStore(private val database: AppDatabase) {

    class Snapshot internal constructor(internal val items: List<HistoryItemRoom>)

    suspend fun export(): JSONArray {
        val result = JSONArray()
        database.historyItemDao().getAllHistoryList().forEach { item ->
            result.put(
                JSONObject()
                    .put("id", item.id)
                    .put("url", item.url)
                    .put("date", item.date)
                    .put("title", item.title)
                    .put("unixTime", item.unixTime),
            )
        }
        return result
    }

    fun decode(items: JSONArray): Snapshot = try {
        Snapshot(
            (0 until items.length()).map { index ->
                val item = items.getJSONObject(index)
                HistoryItemRoom(
                    id = item.getInt("id"),
                    url = item.optString("url"),
                    date = item.optString("date"),
                    title = item.optString("title"),
                    unixTime = item.optLong("unixTime"),
                )
            },
        )
    } catch (error: JSONException) {
        throw BackupException("Повреждён раздел истории", error)
    }

    suspend fun restore(snapshot: Snapshot) {
        database.withTransaction {
            database.historyItemDao().deleteAllHistory()
            database.historyItemDao().insertHistoryList(snapshot.items)
        }
    }
}
