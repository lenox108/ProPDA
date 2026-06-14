package forpdateam.ru.forpda.entity.db.forum

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ForumItemFlatDao {
    @Query("SELECT * FROM forum_items_flat ORDER BY position ASC, rowid ASC")
    fun getAllForumItems(): Flow<List<ForumItemFlatRoom>>

    @Query("SELECT * FROM forum_items_flat ORDER BY position ASC, rowid ASC")
    suspend fun getAllForumItemsList(): List<ForumItemFlatRoom>

    @Query("SELECT * FROM forum_items_flat WHERE id = :id")
    suspend fun getForumItemById(id: Int): ForumItemFlatRoom?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertForumItem(item: ForumItemFlatRoom)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertForumItems(items: List<ForumItemFlatRoom>)

    @Update
    suspend fun updateForumItem(item: ForumItemFlatRoom)

    @Query("DELETE FROM forum_items_flat WHERE id = :id")
    suspend fun deleteForumItem(id: Int)

    @Query("DELETE FROM forum_items_flat")
    suspend fun deleteAllForumItems()
}
