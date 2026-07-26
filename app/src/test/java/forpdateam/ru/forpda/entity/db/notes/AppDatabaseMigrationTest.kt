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
                        NotesMigrations.MIGRATION_6_10,
                        NotesMigrations.MIGRATION_7_10,
                        NotesMigrations.MIGRATION_8_10,
                        NotesMigrations.MIGRATION_9_10
                )
                .allowMainThreadQueries()
                .build()
        db.openHelper.writableDatabase
        assertEquals(10, db.openHelper.writableDatabase.version)
        db.close()
    }

    /**
     * Папки избранного приезжают миграцией 6 → 10 (номера 7..9 заняты удалённой
     * offline-фичей). Пишем в таблицы после миграции: так проверяется не только факт
     * их создания, но и совпадение схемы с ожиданиями Room (иначе — падение на валидации).
     */
    @Test
    fun migrate6To10CreatesFavoriteFolders() {
        val isolatedDb = "$testDbName-6-10"
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase(isolatedDb)
        val db = Room.databaseBuilder(context, AppDatabase::class.java, isolatedDb)
                .addMigrations(
                        NotesMigrations.MIGRATION_5_6,
                        NotesMigrations.MIGRATION_6_10
                )
                .allowMainThreadQueries()
                .build()
        db.openHelper.writableDatabase.execSQL(
                "INSERT INTO fav_folders (name, sortOrder, createdAt, updatedAt) VALUES ('Смартфоны', 1, 1, 1)"
        )
        db.openHelper.writableDatabase.execSQL(
                "INSERT INTO fav_folder_items (targetKey, folderId, updatedAt) VALUES ('t:42', 1, 1)"
        )
        db.openHelper.writableDatabase.query(
                "SELECT folderId FROM fav_folder_items WHERE targetKey = 't:42'"
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(1, cursor.getInt(0))
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
