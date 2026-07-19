package forpdateam.ru.forpda.entity.db.draft

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface PostDraftDao {

    @Query("SELECT * FROM post_draft WHERE key = :key")
    suspend fun get(key: String): PostDraftRoom?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: PostDraftRoom)

    @Query("DELETE FROM post_draft WHERE key = :key")
    suspend fun delete(key: String)
}
