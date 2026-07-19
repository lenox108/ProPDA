package forpdateam.ru.forpda.entity.db.draft

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * Отдельная однотабличная БД для черновиков ответа — по образцу
 * [forpdateam.ru.forpda.entity.db.readboundary.TopicReadBoundaryDatabase]: специально НЕ в общем
 * AppDatabase, чтобы не трогать её версии/исторические миграции.
 */
@Database(
    entities = [PostDraftRoom::class],
    version = 1,
    exportSchema = false,
)
abstract class PostDraftDatabase : RoomDatabase() {
    abstract fun postDraftDao(): PostDraftDao
}
