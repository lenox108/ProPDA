package forpdateam.ru.forpda.entity.db.qms

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface QmsThemeDao {
    @Query("SELECT * FROM qms_themes")
    fun getAllThemes(): Flow<List<QmsThemeRoom>>

    @Query("SELECT * FROM qms_themes")
    suspend fun getAllThemesList(): List<QmsThemeRoom>

    @Query("SELECT * FROM qms_themes WHERE userId = :userId")
    suspend fun getThemesByUserId(userId: Int): List<QmsThemeRoom>

    @Query("SELECT * FROM qms_themes WHERE id = :id")
    suspend fun getThemeById(id: Int): QmsThemeRoom?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTheme(theme: QmsThemeRoom)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertThemes(themes: List<QmsThemeRoom>)

    @Update
    suspend fun updateTheme(theme: QmsThemeRoom)

    @Query("DELETE FROM qms_themes WHERE id = :id")
    suspend fun deleteTheme(id: Int)

    @Query("DELETE FROM qms_themes WHERE userId = :userId")
    suspend fun deleteThemesByUserId(userId: Int)

    @Query("DELETE FROM qms_themes")
    suspend fun deleteAllThemes()
}
