package forpdateam.ru.forpda.entity.db.offline

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface OfflineItemDao {

    @Query("SELECT * FROM offline_items ORDER BY savedAtMs DESC")
    fun observeAll(): Flow<List<OfflineItemRoom>>

    @Query("SELECT * FROM offline_items ORDER BY savedAtMs DESC")
    suspend fun getAll(): List<OfflineItemRoom>

    @Query("SELECT * FROM offline_items WHERE id = :id")
    suspend fun getById(id: String): OfflineItemRoom?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: OfflineItemRoom)

    @Update
    suspend fun update(item: OfflineItemRoom)

    @Query("DELETE FROM offline_items WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM offline_items")
    suspend fun deleteAll()

    @Query("UPDATE offline_items SET status = :status, sizeBytes = :sizeBytes WHERE id = :id")
    suspend fun updateStatus(id: String, status: String, sizeBytes: Long)

    /** Sum of sizeBytes across all saved items, for storage-limit UI. */
    @Query("SELECT IFNULL(SUM(sizeBytes), 0) FROM offline_items")
    suspend fun sumSize(): Long

    /**
     * Phase 6 (storage-limit / LRU eviction). Returns the saved
     * items ordered oldest first (smallest [OfflineItemRoom.savedAtMs]
     * first), so the caller can delete the LRU candidates until the
     * total size falls under the configured budget. The list is
     * capped at [limit] rows for cheap queries.
     */
    @Query("SELECT * FROM offline_items ORDER BY savedAtMs ASC LIMIT :limit")
    suspend fun getOldestFirst(limit: Int): List<OfflineItemRoom>
}
