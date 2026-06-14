package forpdateam.ru.forpda.entity.db.notes

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import forpdateam.ru.forpda.entity.remote.favorites.FavoriteReadState

object NotesMigrations {
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // Migration from version 1 to 2
            // Version 1 had basic tables without the folder system
            // This migration ensures all tables exist with their initial schema
            // Note: this is a no-op migration for safety, as the exact schema of v1 is unknown
            // In production, if users are on v1, they will need to be handled via fallback or manual migration
            // For now, this serves as a placeholder to prevent crashes
        }
    }

    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            val now = System.currentTimeMillis()
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS note_folders (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    name TEXT NOT NULL,
                    sortOrder INTEGER NOT NULL,
                    createdAt INTEGER NOT NULL,
                    updatedAt INTEGER NOT NULL
                )
                """.trimIndent()
            )
            db.execSQL("ALTER TABLE notes ADD COLUMN folderId INTEGER")
            db.execSQL("ALTER TABLE notes ADD COLUMN createdAt INTEGER NOT NULL DEFAULT $now")
            db.execSQL("ALTER TABLE notes ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT $now")
            db.execSQL("ALTER TABLE notes ADD COLUMN sortOrder INTEGER NOT NULL DEFAULT 0")
        }
    }

    val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE forum_items_flat ADD COLUMN position INTEGER NOT NULL DEFAULT 0")
        }
    }

    val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE favorites ADD COLUMN localReadPostId INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE favorites ADD COLUMN localReadPostDateMillis INTEGER NOT NULL DEFAULT 0")
        }
    }

    /**
     * Adds tri-state readState and converts poisoned isNew=0 rows to UNKNOWN so refresh can re-detect unread.
     */
    val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                    "ALTER TABLE favorites ADD COLUMN readState INTEGER NOT NULL DEFAULT ${FavoriteReadState.STORAGE_UNKNOWN}"
            )
            db.execSQL(
                    """
                    UPDATE favorites
                    SET readState = CASE
                        WHEN isNew = 1 THEN ${FavoriteReadState.STORAGE_UNREAD}
                        ELSE ${FavoriteReadState.STORAGE_UNKNOWN}
                    END
                    """.trimIndent()
            )
        }
    }
}
