package forpdateam.ru.forpda.entity.db.history

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface HistoryItemDao {
    @Query("SELECT * FROM history ORDER BY unixTime DESC")
    fun getAllHistory(): Flow<List<HistoryItemRoom>>

    @Query("SELECT * FROM history ORDER BY unixTime DESC")
    suspend fun getAllHistoryList(): List<HistoryItemRoom>

    @Query("SELECT * FROM history WHERE id = :id")
    suspend fun getHistoryById(id: Int): HistoryItemRoom?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistory(history: HistoryItemRoom)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistoryList(historyList: List<HistoryItemRoom>)

    @Update
    suspend fun updateHistory(history: HistoryItemRoom)

    @Query("DELETE FROM history WHERE id = :id")
    suspend fun deleteHistory(id: Int)

    @Query("DELETE FROM history")
    suspend fun deleteAllHistory()
}
