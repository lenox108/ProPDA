package forpdateam.ru.forpda.entity.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ForumUserDao {
    @Query("SELECT * FROM forum_users")
    fun getAllUsers(): Flow<List<ForumUserRoom>>

    @Query("SELECT * FROM forum_users")
    suspend fun getAllUsersList(): List<ForumUserRoom>

    @Query("SELECT * FROM forum_users WHERE id = :id")
    suspend fun getUserById(id: Int): ForumUserRoom?

    @Query("SELECT * FROM forum_users WHERE id IN (:ids)")
    suspend fun getUsersByIds(ids: List<Int>): List<ForumUserRoom>

    @Query("SELECT * FROM forum_users WHERE nick = :nick")
    suspend fun getUserByNick(nick: String): ForumUserRoom?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: ForumUserRoom)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUsers(users: List<ForumUserRoom>)

    @Update
    suspend fun updateUser(user: ForumUserRoom)

    @Query("DELETE FROM forum_users WHERE id = :id")
    suspend fun deleteUser(id: Int)

    @Query("DELETE FROM forum_users")
    suspend fun deleteAllUsers()
}
