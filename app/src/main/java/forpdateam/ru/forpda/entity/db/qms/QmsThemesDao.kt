package forpdateam.ru.forpda.entity.db.qms

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface QmsThemesDao {
    @Query("SELECT * FROM qms_themes_list")
    fun getAllThemesList(): Flow<List<QmsThemesRoom>>

    @Query("SELECT * FROM qms_themes_list")
    suspend fun getAllThemesListSync(): List<QmsThemesRoom>

    @Query("SELECT * FROM qms_themes_list WHERE userId = :userId")
    suspend fun getThemesByUserId(userId: Int): QmsThemesRoom?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertThemes(themes: QmsThemesRoom)

    @Update
    suspend fun updateThemes(themes: QmsThemesRoom)

    @Query("DELETE FROM qms_themes_list WHERE userId = :userId")
    suspend fun deleteThemes(userId: Int)

    @Query("DELETE FROM qms_themes_list")
    suspend fun deleteAllThemes()
}
