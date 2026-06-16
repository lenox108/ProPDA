package forpdateam.ru.forpda.entity.db.favorites

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface FavItemDao {
    @Query("SELECT * FROM favorites")
    fun getAllFavorites(): Flow<List<FavItemRoom>>

    @Query("SELECT * FROM favorites")
    suspend fun getAllFavoritesList(): List<FavItemRoom>

    @Query("SELECT * FROM favorites WHERE favId = :favId")
    suspend fun getFavoriteById(favId: Int): FavItemRoom?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFavorite(favorite: FavItemRoom)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFavorites(favorites: List<FavItemRoom>)

    @Update
    suspend fun updateFavorite(favorite: FavItemRoom)

    @Query("DELETE FROM favorites WHERE favId = :favId")
    suspend fun deleteFavorite(favId: Int)

    @Query("DELETE FROM favorites")
    suspend fun deleteAllFavorites()

    /**
     * Атомарная замена всего набора избранного: важно для пути
     * FavoritesCacheRoom.saveFavorites, который раньше делал два
     * отдельных write-цикла (DELETE + INSERT). Без транзакции
     * concurrent-чтение через Flow.observeItems() могло увидеть
     * пустой список между двумя write'ами и пересоздать
     * RecyclerView-стейт.
     */
    @Transaction
    suspend fun replaceFavorites(favorites: List<FavItemRoom>) {
        deleteAllFavorites()
        if (favorites.isNotEmpty()) {
            insertFavorites(favorites)
        }
    }
}
