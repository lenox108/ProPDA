package forpdateam.ru.forpda.entity.db.notes

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

@Dao
interface NoteItemDao {
    @Query("SELECT * FROM notes ORDER BY createdAt DESC, id DESC")
    suspend fun getAllNotesList(): List<NoteItemRoom>

    @Query("SELECT * FROM notes ORDER BY createdAt DESC, id DESC")
    suspend fun getAllNotesCreatedDesc(): List<NoteItemRoom>

    @Query("SELECT * FROM notes ORDER BY updatedAt DESC, id DESC")
    suspend fun getAllNotesUpdatedDesc(): List<NoteItemRoom>

    @Query("SELECT * FROM notes ORDER BY title COLLATE NOCASE ASC, id DESC")
    suspend fun getAllNotesTitleAsc(): List<NoteItemRoom>

    @Query(
        """
        SELECT * FROM notes
        WHERE (:folderId IS NULL AND folderId IS NULL) OR folderId = :folderId
        ORDER BY createdAt DESC, id DESC
        """
    )
    suspend fun getNotesByFolderCreatedDesc(folderId: Long?): List<NoteItemRoom>

    @Query(
        """
        SELECT * FROM notes
        WHERE (:folderId IS NULL AND folderId IS NULL) OR folderId = :folderId
        ORDER BY updatedAt DESC, id DESC
        """
    )
    suspend fun getNotesByFolderUpdatedDesc(folderId: Long?): List<NoteItemRoom>

    @Query(
        """
        SELECT * FROM notes
        WHERE (:folderId IS NULL AND folderId IS NULL) OR folderId = :folderId
        ORDER BY title COLLATE NOCASE ASC, id DESC
        """
    )
    suspend fun getNotesByFolderTitleAsc(folderId: Long?): List<NoteItemRoom>

    @Query("SELECT * FROM notes WHERE id = :id")
    suspend fun getNoteById(id: Long): NoteItemRoom?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNote(note: NoteItemRoom)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNotes(notes: List<NoteItemRoom>)

    @Update
    suspend fun updateNote(note: NoteItemRoom)

    @Query("DELETE FROM notes WHERE id = :id")
    suspend fun deleteNote(id: Long)

    @Query("DELETE FROM notes WHERE id IN (:ids)")
    suspend fun deleteNotes(ids: List<Long>)

    @Query("UPDATE notes SET folderId = :folderId, updatedAt = :updatedAt WHERE id = :noteId")
    suspend fun moveNoteToFolder(noteId: Long, folderId: Long?, updatedAt: Long)

    @Query("UPDATE notes SET folderId = :folderId, updatedAt = :updatedAt WHERE id IN (:ids)")
    suspend fun moveNotesToFolder(ids: List<Long>, folderId: Long, updatedAt: Long)

    @Query("UPDATE notes SET folderId = NULL, updatedAt = :updatedAt WHERE id IN (:ids)")
    suspend fun moveNotesWithoutFolder(ids: List<Long>, updatedAt: Long)

    @Query("UPDATE notes SET folderId = NULL, updatedAt = :updatedAt WHERE folderId = :folderId")
    suspend fun clearFolder(folderId: Long, updatedAt: Long)

    @Query("DELETE FROM notes")
    suspend fun deleteAllNotes()
}
