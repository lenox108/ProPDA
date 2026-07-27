package forpdateam.ru.forpda.settingsbackup

import android.net.Uri
import androidx.preference.PreferenceManager
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import forpdateam.ru.forpda.common.Preferences
import forpdateam.ru.forpda.common.SecureCookiesPreferences
import forpdateam.ru.forpda.entity.db.notes.AppDatabase
import forpdateam.ru.forpda.entity.db.notes.NoteFolderRoom
import forpdateam.ru.forpda.entity.db.notes.NoteItemRoom
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class SettingsBackupServiceTest {

    private val database: AppDatabase = Room
        .inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        )
        .allowMainThreadQueries()
        .build()

    private fun createService(): SettingsBackupService = SettingsBackupService(
        ApplicationProvider.getApplicationContext(),
        NotesBackupStore(database),
    )

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun settingsOnlyBackupDoesNotContainOrReplaceCurrentAccount() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        preferences.edit()
            .clear()
            .putString(TEST_SETTING, "from-backup")
            .putString(Preferences.Auth.USER_ID, "101")
            .putString("auth_state", "AUTH")
            .putString("current_user", "backup-profile")
            .commit()

        val file = File(context.cacheDir, "settings-only-backup.json")
        val service = createService()
        service.write(Uri.fromFile(file), includeSession = false)

        val backupText = file.readText()
        assertFalse(backupText.contains("backup-profile"))
        assertFalse(backupText.contains("\"cookie_member_id\""))

        preferences.edit()
            .putString(TEST_SETTING, "changed-after-backup")
            .putString(Preferences.Auth.USER_ID, "202")
            .putString("auth_state", "AUTH")
            .putString("current_user", "current-profile")
            .commit()

        service.restore(Uri.fromFile(file))

        assertEquals("from-backup", preferences.getString(TEST_SETTING, null))
        assertEquals("202", preferences.getString(Preferences.Auth.USER_ID, null))
        assertEquals("AUTH", preferences.getString("auth_state", null))
        assertEquals("current-profile", preferences.getString("current_user", null))
    }

    @Test
    fun backupWithSessionRestoresProfileAndCookies() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        val securePreferences = SecureCookiesPreferences.getInstance(context)
        preferences.edit()
            .clear()
            .putString(Preferences.Auth.USER_ID, "101")
            .putString("auth_state", "AUTH")
            .putString("current_user", "backup-profile")
            .commit()
        securePreferences.restoreAuthCookies(
            mapOf(
                Preferences.Auth.COOKIE_MEMBER_ID to "member-cookie",
                Preferences.Auth.COOKIE_PASS_HASH to "pass-cookie",
            ),
        )

        val file = File(context.cacheDir, "backup-with-session.json")
        val service = createService()
        service.write(Uri.fromFile(file), includeSession = true)

        preferences.edit()
            .putString(Preferences.Auth.USER_ID, "202")
            .putString("auth_state", "NO_AUTH")
            .putString("current_user", "changed-profile")
            .commit()
        securePreferences.restoreAuthCookies(emptyMap())

        service.restore(Uri.fromFile(file))

        assertEquals("101", preferences.getString(Preferences.Auth.USER_ID, null))
        assertEquals("AUTH", preferences.getString("auth_state", null))
        assertEquals("backup-profile", preferences.getString("current_user", null))
        assertEquals(
            "member-cookie",
            securePreferences.getString(Preferences.Auth.COOKIE_MEMBER_ID),
        )
        assertEquals(
            "pass-cookie",
            securePreferences.getString(Preferences.Auth.COOKIE_PASS_HASH),
        )
    }

    @Test
    fun backupRestoresBookmarksAndTheirFolders() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        database.noteFolderDao().insertFolder(
            NoteFolderRoom(id = 7, name = "Прошивки", sortOrder = 1, createdAt = 10, updatedAt = 10),
        )
        database.noteItemDao().insertNotes(
            listOf(
                NoteItemRoom(1, "В папке", "link-1", "body-1", 7, 20, 21, 3),
                NoteItemRoom(2, "Без папки", "link-2", "body-2", null, 30, 31, 4),
            ),
        )

        val file = File(context.cacheDir, "backup-with-bookmarks.json")
        val service = createService()
        service.write(Uri.fromFile(file), includeSession = false)

        // Полностью подменяем закладки, чтобы восстановление именно заменяло, а не дополняло.
        database.noteItemDao().deleteAllNotes()
        database.noteFolderDao().deleteAllFolders()
        database.noteItemDao().insertNote(
            NoteItemRoom(99, "Появилась позже", "link-99", "body-99", null, 40, 41, 5),
        )

        service.restore(Uri.fromFile(file))

        val folders = database.noteFolderDao().getAllFoldersList()
        assertEquals(listOf(7L to "Прошивки"), folders.map { it.id to it.name })
        val notes = database.noteItemDao().getAllNotesList().sortedBy { it.id }
        assertEquals(listOf(1L, 2L), notes.map { it.id })
        assertEquals(listOf("В папке", "Без папки"), notes.map { it.title })
        assertEquals(listOf(7L, null), notes.map { it.folderId })
        assertEquals(listOf("body-1", "body-2"), notes.map { it.content })
        assertEquals(listOf(3L, 4L), notes.map { it.sortOrder })
        assertNull(database.noteItemDao().getNoteById(99))
    }

    @Test
    fun backupWithoutBookmarksSectionKeepsCurrentBookmarks() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val file = File(context.cacheDir, "backup-v1.json")
        val service = createService()
        service.write(Uri.fromFile(file), includeSession = false)
        // Имитируем файл первой версии — там раздела закладок ещё не было.
        val legacy = org.json.JSONObject(file.readText())
            .put("version", 1)
        legacy.remove("bookmarks")
        file.writeText(legacy.toString())

        database.noteItemDao().insertNote(
            NoteItemRoom(5, "Своя закладка", "link-5", "body-5", null, 50, 51, 0),
        )

        service.restore(Uri.fromFile(file))

        assertEquals(listOf(5L), database.noteItemDao().getAllNotesList().map { it.id })
    }

    private companion object {
        const val TEST_SETTING = "backup_test_setting"
    }
}
