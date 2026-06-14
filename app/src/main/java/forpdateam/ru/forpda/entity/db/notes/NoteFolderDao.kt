package forpdateam.ru.forpda.entity.db.notes

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

@Dao
interface NoteFolderDao {
    @Query("SELECT * FROM note_folders ORDER BY sortOrder ASC, name COLLATE NOCASE ASC")
    suspend fun getAllFoldersList(): List<NoteFolderRoom>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFolder(folder: NoteFolderRoom): Long

    @Update
    suspend fun updateFolder(folder: NoteFolderRoom)

    @Query("UPDATE note_folders SET name = :name, updatedAt = :updatedAt WHERE id = :id")
    suspend fun renameFolder(id: Long, name: String, updatedAt: Long)

    @Query("DELETE FROM note_folders WHERE id = :id")
    suspend fun deleteFolder(id: Long)
}
