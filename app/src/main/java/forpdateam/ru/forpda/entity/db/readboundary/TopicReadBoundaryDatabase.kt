package forpdateam.ru.forpda.entity.db.readboundary

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Отдельная однотабличная БД для клиентской границы прочитанного — специально НЕ в общем
 * [forpdateam.ru.forpda.entity.db.notes.AppDatabase], чтобы не трогать её версии/миграции
 * (там есть исторические down-миграции 7→6/8→6/9→6 от убранной offline-фичи).
 *
 * v1 → v2: добавлены [TopicReadBoundaryRoom.maxLoadedPostId] / [TopicReadBoundaryRoom.maxLoadedPage]
 * (детект кросс-девайс прогресса). Миграция ADD COLUMN с дефолтом 0 — существующие границы НЕ
 * теряются (иначе первое открытие после апдейта проскочило бы непрочитанное). Пока новые колонки = 0
 * (кросс-девайс детект выключен) поведение остаётся прежним, пока поле не наполнится загрузками.
 */
@Database(
    entities = [TopicReadBoundaryRoom::class],
    version = 2,
    exportSchema = false,
)
abstract class TopicReadBoundaryDatabase : RoomDatabase() {
    abstract fun topicReadBoundaryDao(): TopicReadBoundaryDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE topic_read_boundary ADD COLUMN maxLoadedPostId INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE topic_read_boundary ADD COLUMN maxLoadedPage INTEGER NOT NULL DEFAULT 0")
            }
        }
    }
}
