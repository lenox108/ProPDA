package forpdateam.ru.forpda.settingsbackup

import androidx.room.withTransaction
import forpdateam.ru.forpda.entity.db.notes.AppDatabase
import forpdateam.ru.forpda.entity.db.notes.NoteFolderRoom
import forpdateam.ru.forpda.entity.db.notes.NoteItemRoom
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * Закладки (заметки) и их папки в файле бэкапа. Формат раздела совпадает с форматом
 * экспорта закладок (`{ version, folders, notes }`), поэтому раздел из бэкапа можно
 * скормить обычному импорту закладок, и наоборот.
 *
 * В отличие от импорта, восстановление из бэкапа — это именно замена: текущие закладки
 * и папки удаляются, id из файла сохраняются как есть, чтобы ссылки заметок на папки
 * и ручная сортировка не поехали.
 */
class NotesBackupStore(private val database: AppDatabase) {

    class Snapshot internal constructor(
        internal val folders: List<NoteFolderRoom>,
        internal val notes: List<NoteItemRoom>,
    )

    suspend fun export(): JSONObject {
        val folders = JSONArray()
        database.noteFolderDao().getAllFoldersList().forEach { folder ->
            folders.put(
                JSONObject()
                    .put("id", folder.id)
                    .put("name", folder.name)
                    .put("sortOrder", folder.sortOrder)
                    .put("createdAt", folder.createdAt)
                    .put("updatedAt", folder.updatedAt),
            )
        }
        val notes = JSONArray()
        database.noteItemDao().getAllNotesList().forEach { note ->
            notes.put(
                JSONObject()
                    .put("id", note.id)
                    .put("title", note.title)
                    .put("link", note.link)
                    .put("content", note.content)
                    .apply { note.folderId?.let { put("folderId", it) } }
                    .put("createdAt", note.createdAt)
                    .put("updatedAt", note.updatedAt)
                    .put("sortOrder", note.sortOrder),
            )
        }
        return JSONObject()
            .put("version", EXPORT_FORMAT_VERSION)
            .put("folders", folders)
            .put("notes", notes)
    }

    /** Разбирает раздел закладок целиком, ничего не меняя в базе. */
    fun decode(root: JSONObject): Snapshot = try {
        val foldersJson = root.optJSONArray("folders") ?: JSONArray()
        val folders = (0 until foldersJson.length()).map { index ->
            val item = foldersJson.getJSONObject(index)
            NoteFolderRoom(
                id = item.getLong("id"),
                name = item.getString("name"),
                sortOrder = item.optLong("sortOrder"),
                createdAt = item.optLong("createdAt"),
                updatedAt = item.optLong("updatedAt"),
            )
        }
        val folderIds = folders.mapTo(HashSet()) { it.id }
        val notesJson = root.optJSONArray("notes") ?: JSONArray()
        val notes = (0 until notesJson.length()).map { index ->
            val item = notesJson.getJSONObject(index)
            val id = item.getLong("id")
            val createdAt = item.optLong("createdAt", id)
            NoteItemRoom(
                id = id,
                title = item.optString("title"),
                link = item.optString("link"),
                content = item.optString("content"),
                // Заметку из удалённой/битой папки не теряем — она уедет в «Без папки».
                folderId = item.optLong("folderId", 0L).takeIf { it in folderIds },
                createdAt = createdAt,
                updatedAt = item.optLong("updatedAt", createdAt),
                sortOrder = item.optLong("sortOrder"),
            )
        }
        Snapshot(folders, notes)
    } catch (error: JSONException) {
        throw BackupException("Повреждён раздел закладок", error)
    }

    suspend fun restore(snapshot: Snapshot) {
        database.withTransaction {
            database.noteItemDao().deleteAllNotes()
            database.noteFolderDao().deleteAllFolders()
            database.noteFolderDao().insertFolders(snapshot.folders)
            database.noteItemDao().insertNotes(snapshot.notes)
        }
    }

    private companion object {
        // Совпадает с NotesRepository.EXPORT_FORMAT_VERSION.
        const val EXPORT_FORMAT_VERSION = 2
    }
}
