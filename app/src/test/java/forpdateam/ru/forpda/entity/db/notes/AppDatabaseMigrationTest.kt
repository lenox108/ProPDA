package forpdateam.ru.forpda.entity.db.notes

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class AppDatabaseMigrationTest {
    private val testDbName = "migration-test"

    @Test
    fun migrateAll() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase(testDbName)
        val db = Room.databaseBuilder(context, AppDatabase::class.java, testDbName)
                .addMigrations(
                        NotesMigrations.MIGRATION_1_2,
                        NotesMigrations.MIGRATION_2_3,
                        NotesMigrations.MIGRATION_3_4,
                        NotesMigrations.MIGRATION_4_5,
                        NotesMigrations.MIGRATION_5_6,
                        NotesMigrations.MIGRATION_7_6,
                        NotesMigrations.MIGRATION_8_6,
                        NotesMigrations.MIGRATION_9_6,
                        NotesMigrations.MIGRATION_10_6
                )
                .allowMainThreadQueries()
                .build()
        db.openHelper.writableDatabase
        assertEquals(6, db.openHelper.writableDatabase.version)
        db.close()
    }

    /**
     * Промежуточная сборка папок избранного держала их таблицы в AppDatabase и поднимала
     * версию до 10, из-за чего сборки без фичи падали на «A migration from 10 to 6 was
     * required but not found». Папки уехали в свою БД; этот тест пиннит путь возврата,
     * иначе устройства с той сборкой останутся с неоткрывающимся избранным.
     */
    @Test
    fun migrate10To6DropsFavoriteFolderTables() {
        val isolatedDb = "$testDbName-10-6"
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase(isolatedDb)
        // Готовим базу «как после промежуточной сборки»: схема v6 + таблицы папок + version = 10.
        val seed = Room.databaseBuilder(context, AppDatabase::class.java, isolatedDb)
                .allowMainThreadQueries()
                .build()
        seed.openHelper.writableDatabase.apply {
            execSQL(
                    "CREATE TABLE IF NOT EXISTS fav_folders (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                            "name TEXT NOT NULL, sortOrder INTEGER NOT NULL, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL)"
            )
            execSQL(
                    "CREATE TABLE IF NOT EXISTS fav_folder_items (targetKey TEXT NOT NULL, folderId INTEGER NOT NULL, " +
                            "updatedAt INTEGER NOT NULL, PRIMARY KEY(targetKey))"
            )
            version = 10
        }
        seed.close()

        val db = Room.databaseBuilder(context, AppDatabase::class.java, isolatedDb)
                .addMigrations(NotesMigrations.MIGRATION_10_6)
                .allowMainThreadQueries()
                .build()
        val opened = db.openHelper.writableDatabase
        assertEquals(6, opened.version)
        opened.query(
                "SELECT name FROM sqlite_master WHERE type = 'table' AND name IN ('fav_folders', 'fav_folder_items')"
        ).use { cursor ->
            assertEquals(0, cursor.count)
        }
        db.close()
    }

    @Test
    fun migrate1To2() {
        val isolatedDb = "$testDbName-1-2"
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase(isolatedDb)
        val db = Room.databaseBuilder(context, AppDatabase::class.java, isolatedDb)
                .addMigrations(NotesMigrations.MIGRATION_1_2)
                .allowMainThreadQueries()
                .build()
        db.openHelper.writableDatabase
        assertTrue(db.openHelper.writableDatabase.version >= 2)
        db.close()
    }
}
