package forpdateam.ru.forpda.entity.db.draft

import android.content.Context
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class PostDraftDatabaseMigrationTest {

    @Test
    fun `migration 2 to 3 preserves draft and adds attachment edit marker`() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val databaseName = "post-draft-migration-2-3"
        context.deleteDatabase(databaseName)
        context.openOrCreateDatabase(databaseName, Context.MODE_PRIVATE, null).use { database ->
            database.execSQL(
                """
                CREATE TABLE post_draft (
                    `key` TEXT NOT NULL,
                    `message` TEXT NOT NULL,
                    `updatedAt` INTEGER NOT NULL,
                    `selectionStart` INTEGER NOT NULL,
                    `selectionEnd` INTEGER NOT NULL,
                    `attachmentsJson` TEXT NOT NULL,
                    `editorMode` TEXT NOT NULL,
                    PRIMARY KEY(`key`)
                )
                """.trimIndent(),
            )
            database.execSQL(
                """
                INSERT INTO post_draft
                (`key`, `message`, `updatedAt`, `selectionStart`, `selectionEnd`, `attachmentsJson`, `editorMode`)
                VALUES ('post:7', 'body', 1, 0, 4, '[]', 'full')
                """.trimIndent(),
            )
            database.version = 2
        }

        val room = Room.databaseBuilder(context, PostDraftDatabase::class.java, databaseName)
            .addMigrations(PostDraftDatabase.MIGRATION_2_3)
            .allowMainThreadQueries()
            .build()
        room.openHelper.writableDatabase.query(
            "SELECT message, attachmentsChanged FROM post_draft WHERE `key` = 'post:7'",
        ).use { cursor ->
            cursor.moveToFirst()
            assertEquals("body", cursor.getString(0))
            assertEquals(0, cursor.getInt(1))
        }
        assertEquals(3, room.openHelper.writableDatabase.version)
        room.close()
        context.deleteDatabase(databaseName)
    }
}
