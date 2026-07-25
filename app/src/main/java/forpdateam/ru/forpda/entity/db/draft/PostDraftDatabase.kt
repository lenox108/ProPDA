package forpdateam.ru.forpda.entity.db.draft

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Отдельная однотабличная БД для черновиков ответа — по образцу
 * [forpdateam.ru.forpda.entity.db.readboundary.TopicReadBoundaryDatabase]: специально НЕ в общем
 * AppDatabase, чтобы не трогать её версии/исторические миграции.
 */
@Database(
    entities = [PostDraftRoom::class],
    version = 2,
    exportSchema = false,
)
abstract class PostDraftDatabase : RoomDatabase() {
    abstract fun postDraftDao(): PostDraftDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE post_draft ADD COLUMN selectionStart INTEGER NOT NULL DEFAULT -1"
                )
                database.execSQL(
                    "ALTER TABLE post_draft ADD COLUMN selectionEnd INTEGER NOT NULL DEFAULT -1"
                )
                database.execSQL(
                    "ALTER TABLE post_draft ADD COLUMN attachmentsJson TEXT NOT NULL DEFAULT '[]'"
                )
                database.execSQL(
                    "ALTER TABLE post_draft ADD COLUMN editorMode TEXT NOT NULL DEFAULT ''"
                )
            }
        }
    }
}
