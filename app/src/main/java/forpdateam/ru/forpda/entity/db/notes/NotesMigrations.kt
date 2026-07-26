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

    /**
     * Drops the `offline_items` table that the now-removed offline-reading
     * feature used to keep in the database. Devices that never had the
     * offline feature (and therefore never created the table) are no-ops
     * thanks to `IF EXISTS`. Devices that reached v7+ during the offline
     * era come back to the v6 schema.
     */
    val MIGRATION_7_6 = object : Migration(7, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("DROP TABLE IF EXISTS offline_items")
        }
    }

    /**
     * Schema on v8 was identical to v7 (no-op bridge). The `offline_items`
     * table is still present on devices that reached v8; drop it the same
     * way as the v7 -> v6 path.
     */
    val MIGRATION_8_6 = object : Migration(8, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("DROP TABLE IF EXISTS offline_items")
        }
    }

    /**
     * Drops the `offline_items` table on devices that reached v9 (the last
     * version that shipped with the offline-reading feature).
     */
    val MIGRATION_9_6 = object : Migration(9, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("DROP TABLE IF EXISTS offline_items")
        }
    }

    /**
     * Локальные папки избранного. Привязка тем к папкам живёт в отдельной таблице
     * (`fav_folder_items`), потому что `favorites` полностью перезаписывается на каждом
     * обновлении списка — колонка внутри неё не пережила бы ни одного refresh.
     *
     * Целевая версия — 10, а не 7: номера 7..9 уже носили устройства из эпохи offline-фичи,
     * и совпадение номера при разной схеме привело бы к падению вместо миграции.
     */
    private fun createFavoriteFolderTables(db: SupportSQLiteDatabase) {
        db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS fav_folders (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    name TEXT NOT NULL,
                    sortOrder INTEGER NOT NULL,
                    createdAt INTEGER NOT NULL,
                    updatedAt INTEGER NOT NULL
                )
                """.trimIndent()
        )
        db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS fav_folder_items (
                    targetKey TEXT NOT NULL,
                    folderId INTEGER NOT NULL,
                    updatedAt INTEGER NOT NULL,
                    PRIMARY KEY(targetKey)
                )
                """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_fav_folder_items_folderId ON fav_folder_items (folderId)")
    }

    val MIGRATION_6_10 = object : Migration(6, 10) {
        override fun migrate(db: SupportSQLiteDatabase) {
            createFavoriteFolderTables(db)
        }
    }

    /** Устройства из эпохи offline-фичи (v7..v9): роняем её таблицу и заводим папки. */
    val MIGRATION_7_10 = object : Migration(7, 10) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("DROP TABLE IF EXISTS offline_items")
            createFavoriteFolderTables(db)
        }
    }

    val MIGRATION_8_10 = object : Migration(8, 10) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("DROP TABLE IF EXISTS offline_items")
            createFavoriteFolderTables(db)
        }
    }

    val MIGRATION_9_10 = object : Migration(9, 10) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("DROP TABLE IF EXISTS offline_items")
            createFavoriteFolderTables(db)
        }
    }
}
