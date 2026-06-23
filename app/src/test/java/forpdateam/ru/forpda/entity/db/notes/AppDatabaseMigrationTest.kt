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
                        NotesMigrations.MIGRATION_9_6
                )
                .allowMainThreadQueries()
                .build()
        db.openHelper.writableDatabase
        assertEquals(6, db.openHelper.writableDatabase.version)
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
