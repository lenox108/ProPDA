package forpdateam.ru.forpda.model.repository.note

import forpdateam.ru.forpda.entity.app.notes.NoteFolder
import forpdateam.ru.forpda.entity.app.notes.NoteItem
import forpdateam.ru.forpda.entity.app.notes.NoteSortMode
import forpdateam.ru.forpda.model.data.cache.notes.NotesCacheRoom
import forpdateam.ru.forpda.model.data.remote.api.RequestFile
import forpdateam.ru.forpda.model.data.storage.ExternalStorageProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import timber.log.Timber
import java.io.OutputStream
import java.io.OutputStreamWriter

class NotesRepository(
        private val notesCacheRoom: NotesCacheRoom,
        private val externalStorage: ExternalStorageProvider
) {

    private val ioDispatcher = Dispatchers.IO

    fun observeItems(): Flow<List<NoteItem>> = notesCacheRoom.observeItems()
    fun observeFolders(): Flow<List<NoteFolder>> = notesCacheRoom.observeFolders()

    suspend fun loadNotes(
        folderId: Long? = null,
        includeAllFolders: Boolean = true,
        sortMode: NoteSortMode = NoteSortMode.CREATED_DESC
    ): List<NoteItem> = withContext(ioDispatcher) {
        notesCacheRoom.getItems(
            NotesCacheRoom.FolderFilter(folderId = folderId, includeAllFolders = includeAllFolders),
            sortMode
        )
    }

    suspend fun loadFolders(): List<NoteFolder> = withContext(ioDispatcher) {
        notesCacheRoom.getFolders()
    }

    suspend fun deleteNote(id: Long) = withContext(ioDispatcher) {
        notesCacheRoom.delete(id)
    }

    suspend fun deleteNotes(ids: List<Long>) = withContext(ioDispatcher) {
        notesCacheRoom.delete(ids)
    }

    suspend fun updateNote(item: NoteItem) = withContext(ioDispatcher) {
        notesCacheRoom.update(item)
    }

    suspend fun addNote(item: NoteItem) = withContext(ioDispatcher) {
        notesCacheRoom.add(item)
    }

    suspend fun addNotes(items: List<NoteItem>) = withContext(ioDispatcher) {
        notesCacheRoom.add(items)
    }

    suspend fun createFolder(name: String): NoteFolder = withContext(ioDispatcher) {
        notesCacheRoom.createFolder(name)
    }

    suspend fun renameFolder(id: Long, name: String) = withContext(ioDispatcher) {
        notesCacheRoom.renameFolder(id, name)
    }

    suspend fun deleteFolder(id: Long) = withContext(ioDispatcher) {
        notesCacheRoom.deleteFolder(id)
    }

    suspend fun moveNoteToFolder(noteId: Long, folderId: Long?) = withContext(ioDispatcher) {
        notesCacheRoom.moveNoteToFolder(noteId, folderId)
    }

    suspend fun moveNotesToFolder(noteIds: List<Long>, folderId: Long?) = withContext(ioDispatcher) {
        notesCacheRoom.moveNotesToFolder(noteIds, folderId)
    }

    suspend fun reorderNotes(orderedIds: List<Long>) = withContext(ioDispatcher) {
        notesCacheRoom.reorderNotes(orderedIds)
    }

    suspend fun importNotes(file: RequestFile) = withContext(ioDispatcher) {
        val text = if (file.fileName.matches("[\\s\\S]*?\\.json$".toRegex())) {
            externalStorage.getText(file.fileStream)
        } else {
            throw Exception("Файл имеет неправильное расширение")
        }
        importNotesFromJsonString(text)
    }

    suspend fun importNotesFromJsonString(jsonSource: String) = withContext(ioDispatcher) {
        val jsonBody = JSONArray(jsonSource)
        val noteItems = mutableListOf<NoteItem>()
        for (i in 0 until jsonBody.length()) {
            try {
                val jsonItem = jsonBody.getJSONObject(i)
                noteItems.add(NoteItem().apply {
                    id = jsonItem.getLong("id")
                    title = jsonItem.getString("title")
                    link = jsonItem.getString("link")
                    content = jsonItem.getString("content")
                    createdAt = jsonItem.optLong("createdAt", id)
                    updatedAt = jsonItem.optLong("updatedAt", createdAt)
                })
            } catch (e: JSONException) {
                Timber.e(e, "Notes parse error")
            }
        }
        notesCacheRoom.add(noteItems)
    }

    suspend fun exportNotes(outputStream: OutputStream) = withContext(ioDispatcher) {
        OutputStreamWriter(outputStream).use {
            it.append(createExportJson().toString())
        }
    }

    fun createExportFileName(): String {
        val dateFormatter = DateTimeFormatter.ofPattern("MMddyyy-HHmmss", Locale.getDefault())
        val date = dateFormatter.format(LocalDateTime.now())
        return "ForPDA_Notes_$date.json"
    }

    private suspend fun createExportJson(): JSONArray {
        val jsonBody = JSONArray()
        notesCacheRoom.getAllItemsForExport().forEach {
            try {
                jsonBody.put(JSONObject().apply {
                    put("id", it.id)
                    put("title", it.title)
                    put("link", it.link)
                    put("content", it.content)
                })
            } catch (e: JSONException) {
                Timber.e(e, "Notes save error")
            }
        }
        return jsonBody
    }
}
