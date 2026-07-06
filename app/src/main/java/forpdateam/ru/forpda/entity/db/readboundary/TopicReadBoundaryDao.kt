package forpdateam.ru.forpda.entity.db.readboundary

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface TopicReadBoundaryDao {

    @Query("SELECT * FROM topic_read_boundary")
    suspend fun getAll(): List<TopicReadBoundaryRoom>

    @Query("SELECT * FROM topic_read_boundary WHERE topicId = :topicId")
    suspend fun get(topicId: Int): TopicReadBoundaryRoom?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: TopicReadBoundaryRoom)

    @Query("DELETE FROM topic_read_boundary WHERE topicId = :topicId")
    suspend fun delete(topicId: Int)
}
