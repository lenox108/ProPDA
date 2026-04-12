package forpdateam.ru.forpda.model.repository.note

import forpdateam.ru.forpda.entity.app.notes.NoteItem
import forpdateam.ru.forpda.model.SchedulersProvider
import forpdateam.ru.forpda.model.data.cache.notes.NotesCache
import forpdateam.ru.forpda.model.data.remote.api.RequestFile
import forpdateam.ru.forpda.model.data.storage.ExternalStorageProvider
import forpdateam.ru.forpda.model.repository.BaseRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.rx2.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class NotesRepository(
        private val schedulers: SchedulersProvider,
        private val notesCache: NotesCache,
        private val externalStorage: ExternalStorageProvider
) : BaseRepository(schedulers) {

    private val ioDispatcher = schedulers.io().asCoroutineDispatcher()

    fun observeItems(): Flow<List<NoteItem>> = notesCache.observeItems()

    suspend fun loadNotes(): List<NoteItem> = withContext(ioDispatcher) {
        notesCache.getItems()
    }

    suspend fun deleteNote(id: Long) = withContext(ioDispatcher) {
        notesCache.delete(id)
    }

    suspend fun updateNote(item: NoteItem) = withContext(ioDispatcher) {
        notesCache.update(item)
    }

    suspend fun addNote(item: NoteItem) = withContext(ioDispatcher) {
        notesCache.add(item)
    }

    suspend fun addNotes(items: List<NoteItem>) = withContext(ioDispatcher) {
        notesCache.add(items)
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
                })
            } catch (e: JSONException) {
                e.printStackTrace()
            }
        }
        notesCache.add(noteItems)
    }

    suspend fun exportNotes(): String = withContext(ioDispatcher) {
        val jsonBody = JSONArray()
        notesCache.getItems().forEach {
            try {
                jsonBody.put(JSONObject().apply {
                    put("id", it.id)
                    put("title", it.title)
                    put("link", it.link)
                    put("content", it.content)
                })
            } catch (e: JSONException) {
                e.printStackTrace()
            }
        }
        val date = SimpleDateFormat("MMddyyy-HHmmss", Locale.getDefault()).format(Date(System.currentTimeMillis()))
        val fileName = "ForPDA_Notes_$date.json"
        externalStorage.saveTextDefault(jsonBody.toString(), fileName)
    }
}
