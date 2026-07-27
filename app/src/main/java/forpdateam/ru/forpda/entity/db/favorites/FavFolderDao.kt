package forpdateam.ru.forpda.entity.db.favorites

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

@Dao
interface FavFolderDao {

    @Query("SELECT * FROM fav_folders ORDER BY sortOrder ASC, name COLLATE NOCASE ASC")
    suspend fun getFolders(): List<FavFolderRoom>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFolder(folder: FavFolderRoom): Long

    @Query("UPDATE fav_folders SET name = :name, updatedAt = :updatedAt WHERE id = :id")
    suspend fun renameFolder(id: Long, name: String, updatedAt: Long)

    @Query("DELETE FROM fav_folders WHERE id = :id")
    suspend fun deleteFolder(id: Long)

    @Query("SELECT * FROM fav_folder_items")
    suspend fun getAssignments(): List<FavFolderItemRoom>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setAssignments(items: List<FavFolderItemRoom>)

    @Query("DELETE FROM fav_folder_items WHERE targetKey IN (:targetKeys)")
    suspend fun clearAssignments(targetKeys: List<String>)

    @Query("DELETE FROM fav_folder_items WHERE folderId = :folderId")
    suspend fun clearFolderAssignments(folderId: Long)

    /** Удаление папки не трогает сами темы: они просто возвращаются в «Без папки». */
    @Transaction
    suspend fun deleteFolderWithAssignments(folderId: Long) {
        clearFolderAssignments(folderId)
        deleteFolder(folderId)
    }

    @Transaction
    suspend fun moveToFolder(targetKeys: List<String>, folderId: Long?, updatedAt: Long) {
        if (targetKeys.isEmpty()) return
        if (folderId == null) {
            clearAssignments(targetKeys)
        } else {
            setAssignments(targetKeys.map { FavFolderItemRoom(it, folderId, updatedAt) })
        }
    }
}
