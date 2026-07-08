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
     * Сохраняет ручной порядок ([NoteSortMode.MANUAL]) для заметок drag-and-drop.
     * [orderedIds] — id заметок ОДНОЙ папки в нужном визуальном порядке; каждой
     * присваивается sortOrder = её индекс (0..n-1). Заметки других папок не трогаются
     * (у них свой независимый диапазон), поэтому перетаскивание всегда идёт внутри
     * своей папки. updatedAt намеренно не трогаем, чтобы ручная сортировка не засоряла
     * режим «по дате изменения».
     */
    suspend fun reorderNotes(orderedIds: List<Long>) {
        if (orderedIds.isEmpty()) return
        orderedIds.forEachIndexed { index, id ->
            noteItemDao.updateSortOrder(id, index.toLong())
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
