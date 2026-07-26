package forpdateam.ru.forpda.entity.db.favorites

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * Отдельная БД для локальных папок избранного — специально НЕ в общем
 * [forpdateam.ru.forpda.entity.db.notes.AppDatabase] (паритет с PostDraftDatabase /
 * TopicReadBoundaryDatabase).
 *
 * Причина конкретная: подъём версии AppDatabase ломает ОТКАТ на любую сборку без этой фичи —
 * Room упирается в «A migration from N to 6 was required but not found» и экран избранного
 * перестаёт открываться. Своя БД версии 1 не пересекается с историей AppDatabase вообще:
 * старые сборки просто не знают про этот файл.
 */
@Database(
    entities = [FavFolderRoom::class, FavFolderItemRoom::class],
    version = 1,
    exportSchema = false,
)
abstract class FavoritesFoldersDatabase : RoomDatabase() {
    abstract fun favFolderDao(): FavFolderDao
}
