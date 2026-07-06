package forpdateam.ru.forpda.entity.db.readboundary

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * Отдельная однотабличная БД для клиентской границы прочитанного — специально НЕ в общем
 * [forpdateam.ru.forpda.entity.db.notes.AppDatabase], чтобы не трогать её версии/миграции
 * (там есть исторические down-миграции 7→6/8→6/9→6 от убранной offline-фичи). Здесь версия 1,
 * миграций нет.
 */
@Database(
    entities = [TopicReadBoundaryRoom::class],
    version = 1,
    exportSchema = false,
)
abstract class TopicReadBoundaryDatabase : RoomDatabase() {
    abstract fun topicReadBoundaryDao(): TopicReadBoundaryDao
}
