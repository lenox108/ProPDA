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
     * Adds the [forpdateam.ru.forpda.entity.db.offline.OfflineItemRoom]
     * table for §5.1 (offline reading) Phase 1 — data layer only.
     * HTML and images stay on the filesystem; this table only holds
     * metadata + a serialized model + the path to the HTML file.
     */
    val MIGRATION_6_7 = object : Migration(6, 7) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS offline_items (
                        id TEXT NOT NULL PRIMARY KEY,
                        type TEXT NOT NULL,
                        sourceUrl TEXT NOT NULL,
                        title TEXT NOT NULL,
                        savedAtMs INTEGER NOT NULL,
                        sizeBytes INTEGER NOT NULL,
                        status TEXT NOT NULL,
                        htmlPath TEXT NOT NULL,
                        modelJson TEXT NOT NULL
                    )
                    """.trimIndent()
            )
            db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_offline_items_savedAtMs ON offline_items (savedAtMs DESC)"
            )
        }
    }

    /**
     * No-op bridge migration.
     *
     * Version 8 was briefly published in internal/test builds whose schema was
     * identical to version 7. Devices that reached version 7 already have every
     * table, so upgrading 7 -> 8 requires no schema change. This migration exists
     * only so Room has a valid upward path and never attempts an (unsupported)
     * 8 -> 7 downgrade, which crashed on startup
     * ("A migration from 8 to 7 was required but not found").
     */
    val MIGRATION_7_8 = object : Migration(7, 8) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // Schema identical to v7; nothing to do.
        }
    }

    /**
     * Repairs devices stuck on the stale version 8 whose schema actually
     * predates the offline_items table (it matched the v6 schema). For those
     * devices this creates the missing table; for devices that arrived here via
     * the regular 6 -> 7 -> 8 path the table already exists, so the statements
     * are guarded with IF NOT EXISTS and become no-ops.
     */
    val MIGRATION_8_9 = object : Migration(8, 9) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS offline_items (
                        id TEXT NOT NULL PRIMARY KEY,
                        type TEXT NOT NULL,
                        sourceUrl TEXT NOT NULL,
                        title TEXT NOT NULL,
                        savedAtMs INTEGER NOT NULL,
                        sizeBytes INTEGER NOT NULL,
                        status TEXT NOT NULL,
                        htmlPath TEXT NOT NULL,
                        modelJson TEXT NOT NULL
                    )
                    """.trimIndent()
            )
            db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_offline_items_savedAtMs ON offline_items (savedAtMs DESC)"
            )
        }
    }
}
