package forpdateam.ru.forpda.settingsbackup

import androidx.room.withTransaction
import forpdateam.ru.forpda.entity.db.readboundary.TopicReadBoundaryDatabase
import forpdateam.ru.forpda.entity.db.readboundary.TopicReadBoundaryRoom
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * Клиентская граница прочитанного по темам в файле бэкапа.
 *
 * Восстановить её больше неоткуда: 4PDA per-post read tracking не умеет, серверный якорь
 * уползает вниз сам по факту загрузки страницы. Без этого раздела переезд на другое устройство
 * означает, что все темы открываются по серверному якорю, а не там, где юзер реально остановился.
 *
 * Восстановление — замена: границы из файла не мержатся с текущими, иначе устаревший бэкап
 * молча откатывал бы прогресс назад (границы монотонны и сами вниз не ходят).
 */
class ReadBoundaryBackupStore(private val database: TopicReadBoundaryDatabase) {

    class Snapshot internal constructor(internal val items: List<TopicReadBoundaryRoom>)

    suspend fun export(): JSONArray {
        val result = JSONArray()
        database.topicReadBoundaryDao().getAll().forEach { item ->
            result.put(
                JSONObject()
                    .put("topicId", item.topicId)
                    .put("lastSeenPostId", item.lastSeenPostId)
                    .put("lastSeenPage", item.lastSeenPage)
                    .put("updatedAt", item.updatedAt)
                    .put("maxLoadedPostId", item.maxLoadedPostId)
                    .put("maxLoadedPage", item.maxLoadedPage),
            )
        }
        return result
    }

    fun decode(items: JSONArray): Snapshot = try {
        Snapshot(
            (0 until items.length()).map { index ->
                val item = items.getJSONObject(index)
                TopicReadBoundaryRoom(
                    topicId = item.getInt("topicId"),
                    lastSeenPostId = item.optInt("lastSeenPostId"),
                    lastSeenPage = item.optInt("lastSeenPage"),
                    updatedAt = item.optLong("updatedAt"),
                    maxLoadedPostId = item.optInt("maxLoadedPostId"),
                    maxLoadedPage = item.optInt("maxLoadedPage"),
                )
            },
        )
    } catch (error: JSONException) {
        throw BackupException("Повреждён раздел прочитанного", error)
    }

    suspend fun restore(snapshot: Snapshot) {
        database.withTransaction {
            database.topicReadBoundaryDao().deleteAll()
            database.topicReadBoundaryDao().upsertAll(snapshot.items)
        }
    }
}
