package forpdateam.ru.forpda.model.data.cache.notes

import forpdateam.ru.forpda.entity.app.notes.NoteFolder
import forpdateam.ru.forpda.entity.app.notes.NoteItem
import forpdateam.ru.forpda.entity.app.notes.NoteSortMode
import forpdateam.ru.forpda.entity.db.notes.NoteFolderDao
import forpdateam.ru.forpda.entity.db.notes.NoteFolderRoom
import forpdateam.ru.forpda.entity.db.notes.NoteItemDao
import forpdateam.ru.forpda.entity.db.notes.NoteItemRoom
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class NotesCacheRoom(
    private val noteItemDao: NoteItemDao,
    private val noteFolderDao: NoteFolderDao
) {

    data class FolderFilter(val folderId: Long?, val includeAllFolders: Boolean = true)

    private val _items = MutableStateFlow<List<NoteItem>>(emptyList())
    fun observeItems(): StateFlow<List<NoteItem>> = _items.asStateFlow()

    private val _folders = MutableStateFlow<List<NoteFolder>>(emptyList())
    fun observeFolders(): StateFlow<List<NoteFolder>> = _folders.asStateFlow()

    private var currentFolderFilter: FolderFilter = FolderFilter(folderId = null, includeAllFolders = true)
    private var currentSortMode: NoteSortMode = NoteSortMode.CREATED_DESC

    suspend fun getItems(
        folderFilter: FolderFilter = currentFolderFilter,
        sortMode: NoteSortMode = currentSortMode
    ): List<NoteItem> {
        currentFolderFilter = folderFilter
        currentSortMode = sortMode
        val items = if (folderFilter.includeAllFolders) {
            when (sortMode) {
                NoteSortMode.CREATED_DESC -> noteItemDao.getAllNotesCreatedDesc()
                NoteSortMode.UPDATED_DESC -> noteItemDao.getAllNotesUpdatedDesc()
                NoteSortMode.TITLE_ASC -> noteItemDao.getAllNotesTitleAsc()
                NoteSortMode.MANUAL -> noteItemDao.getAllNotesManual()
            }
        } else {
            when (sortMode) {
                NoteSortMode.CREATED_DESC -> noteItemDao.getNotesByFolderCreatedDesc(folderFilter.folderId)
                NoteSortMode.UPDATED_DESC -> noteItemDao.getNotesByFolderUpdatedDesc(folderFilter.folderId)
                NoteSortMode.TITLE_ASC -> noteItemDao.getNotesByFolderTitleAsc(folderFilter.folderId)
                NoteSortMode.MANUAL -> noteItemDao.getNotesByFolderManual(folderFilter.folderId)
            }
        }
        val noteItems = items.map { it.toAppItem() }
        _items.value = noteItems
        return noteItems
    }

    suspend fun getAllItemsForExport(): List<NoteItem> =
        noteItemDao.getAllNotesList().map { it.toAppItem() }

    suspend fun update(item: NoteItem) {
        val now = System.currentTimeMillis()
        val noteItemRoom = item.toRoomItem(
            createdAt = item.createdAt.takeIf { it > 0 } ?: now,
            updatedAt = now
        )
        noteItemDao.updateNote(noteItemRoom)
        getItems()
    }

    suspend fun delete(id: Long) {
        noteItemDao.deleteNote(id)
        getItems()
    }

    suspend fun delete(ids: List<Long>) {
        if (ids.isEmpty()) return
        noteItemDao.deleteNotes(ids)
        getItems()
    }

    suspend fun add(item: NoteItem) {
        val now = System.currentTimeMillis()
        val noteItemRoom = item.toRoomItem(
            createdAt = item.createdAt.takeIf { it > 0 } ?: now,
            updatedAt = item.updatedAt.takeIf { it > 0 } ?: now
        )
        noteItemDao.insertNote(noteItemRoom)
        getItems()
    }

    suspend fun add(items: List<NoteItem>) {
        val now = System.currentTimeMillis()
        val noteItemsRoom = items.map {
            it.toRoomItem(
                createdAt = it.createdAt.takeIf { createdAt -> createdAt > 0 } ?: now,
                updatedAt = it.updatedAt.takeIf { updatedAt -> updatedAt > 0 } ?: now
            )
        }
        noteItemDao.insertNotes(noteItemsRoom)
        getItems()
    }

    suspend fun getFolders(): List<NoteFolder> {
        val folders = noteFolderDao.getAllFoldersList().map { it.toAppItem() }
        _folders.value = folders
        return folders
    }

    suspend fun createFolder(name: String): NoteFolder {
        val now = System.currentTimeMillis()
        val folder = NoteFolderRoom(
            name = name,
            sortOrder = now,
            createdAt = now,
            updatedAt = now
        )
        val id = noteFolderDao.insertFolder(folder)
        getFolders()
        return NoteFolder(id = id, name = name, sortOrder = now, createdAt = now, updatedAt = now)
    }

    suspend fun renameFolder(id: Long, name: String) {
        noteFolderDao.renameFolder(id, name, System.currentTimeMillis())
        getFolders()
    }

    suspend fun deleteFolder(id: Long) {
        val now = System.currentTimeMillis()
        noteItemDao.clearFolder(id, now)
        noteFolderDao.deleteFolder(id)
        getFolders()
        getItems()
    }

    suspend fun moveNoteToFolder(noteId: Long, folderId: Long?) {
        noteItemDao.moveNoteToFolder(noteId, folderId, System.currentTimeMillis())
        getItems()
    }

    /**
     * Двигает заметку [noteId] на одну позицию вверх (или вниз) в ручном порядке
     * ([NoteSortMode.MANUAL]) внутри её собственной папки. Соседи считаются только среди
     * заметок с тем же folderId, поэтому перестановка корректна и в древовидном виде, и
     * при фильтре по одной папке.
     *
     * Порядок хранится в поле sortOrder. Так как исторически у всех заметок sortOrder == 0,
     * после перестановки нормализуем весь диапазон папки в монотонный 0..n-1 — это делает
     * последующие перемещения детерминированными. updatedAt намеренно не трогаем, чтобы
     * ручная сортировка не засоряла режим «по дате изменения».
     */
    suspend fun moveNote(noteId: Long, up: Boolean) {
        val note = noteItemDao.getNoteById(noteId) ?: return
        val ordered = noteItemDao.getNotesByFolderManual(note.folderId).toMutableList()
        val index = ordered.indexOfFirst { it.id == noteId }
        if (index < 0) return
        val target = if (up) index - 1 else index + 1
        if (target < 0 || target >= ordered.size) return
        ordered[index] = ordered[target].also { ordered[target] = ordered[index] }
        ordered.forEachIndexed { position, item ->
            if (item.sortOrder != position.toLong()) {
                noteItemDao.updateSortOrder(item.id, position.toLong())
            }
        }
        getItems()
    }

    suspend fun moveNotesToFolder(noteIds: List<Long>, folderId: Long?) {
        if (noteIds.isEmpty()) return
        val now = System.currentTimeMillis()
        if (folderId == null) {
            noteItemDao.moveNotesWithoutFolder(noteIds, now)
        } else {
            noteItemDao.moveNotesToFolder(noteIds, folderId, now)
        }
        getItems()
    }

    private fun NoteItemRoom.toAppItem(): NoteItem = NoteItem().apply {
        id = this@toAppItem.id
        title = this@toAppItem.title
        link = this@toAppItem.link
        content = this@toAppItem.content
        folderId = this@toAppItem.folderId
        createdAt = this@toAppItem.createdAt
        updatedAt = this@toAppItem.updatedAt
        sortOrder = this@toAppItem.sortOrder
    }

    private fun NoteItem.toRoomItem(
        createdAt: Long,
        updatedAt: Long
    ): NoteItemRoom = NoteItemRoom(
        id = id,
        title = title ?: "",
        link = link ?: "",
        content = content ?: "",
        folderId = folderId,
        createdAt = createdAt,
        updatedAt = updatedAt,
        sortOrder = sortOrder
    )

    private fun NoteFolderRoom.toAppItem(): NoteFolder = NoteFolder(
        id = id,
        name = name,
        sortOrder = sortOrder,
        createdAt = createdAt,
        updatedAt = updatedAt
    )
}
