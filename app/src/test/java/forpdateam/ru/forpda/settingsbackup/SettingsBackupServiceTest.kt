package forpdateam.ru.forpda.settingsbackup

import android.net.Uri
import androidx.preference.PreferenceManager
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import forpdateam.ru.forpda.common.Preferences
import forpdateam.ru.forpda.common.SecureCookiesPreferences
import forpdateam.ru.forpda.entity.db.history.HistoryItemRoom
import forpdateam.ru.forpda.entity.db.notes.AppDatabase
import forpdateam.ru.forpda.entity.db.notes.NoteFolderRoom
import forpdateam.ru.forpda.entity.db.notes.NoteItemRoom
import forpdateam.ru.forpda.entity.db.readboundary.TopicReadBoundaryDatabase
import forpdateam.ru.forpda.entity.db.readboundary.TopicReadBoundaryRoom
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

    private val readBoundaryDatabase: TopicReadBoundaryDatabase = Room
        .inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            TopicReadBoundaryDatabase::class.java,
        )
        .allowMainThreadQueries()
        .build()

    private fun createService(): SettingsBackupService = SettingsBackupService(
        ApplicationProvider.getApplicationContext(),
        NotesBackupStore(database),
        HistoryBackupStore(database),
        ReadBoundaryBackupStore(readBoundaryDatabase),
        forpdateam.ru.forpda.model.repository.theme.TopicReadBoundaryStore(
            readBoundaryDatabase.topicReadBoundaryDao(),
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined),
        ),
    )

    @After
    fun tearDown() {
        database.close()
        readBoundaryDatabase.close()
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

    @Test
    fun backupRestoresHistoryAndReadBoundaries() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        database.historyItemDao().insertHistoryList(
            listOf(
                HistoryItemRoom(11, "https://4pda.to/forum/1", "вчера", "Тема один", 1000),
                HistoryItemRoom(12, "https://4pda.to/forum/2", "сегодня", "Тема два", 2000),
            ),
        )
        readBoundaryDatabase.topicReadBoundaryDao().upsert(
            TopicReadBoundaryRoom(
                topicId = 555,
                lastSeenPostId = 900,
                lastSeenPage = 3,
                updatedAt = 4242,
                maxLoadedPostId = 950,
                maxLoadedPage = 4,
            ),
        )
        val progress = context.getSharedPreferences("article_reading_progress", 0)
        progress.edit().clear().putInt("article.scroll.777", 42).commit()

        val file = File(context.cacheDir, "backup-with-local-state.json")
        val service = createService()
        service.write(Uri.fromFile(file), includeSession = false)

        database.historyItemDao().deleteAllHistory()
        database.historyItemDao().insertHistory(
            HistoryItemRoom(99, "https://4pda.to/forum/9", "потом", "Лишняя", 3000),
        )
        readBoundaryDatabase.topicReadBoundaryDao().deleteAll()
        progress.edit().clear().putInt("article.scroll.777", 5).commit()

        service.restore(Uri.fromFile(file))

        assertEquals(
            listOf(12, 11),
            database.historyItemDao().getAllHistoryList().map { it.id },
        )
        assertEquals(
            listOf("Тема два", "Тема один"),
            database.historyItemDao().getAllHistoryList().map { it.title },
        )
        val boundary = readBoundaryDatabase.topicReadBoundaryDao().get(555)
        assertEquals(900, boundary?.lastSeenPostId)
        assertEquals(3, boundary?.lastSeenPage)
        assertEquals(950, boundary?.maxLoadedPostId)
        assertEquals(4, boundary?.maxLoadedPage)
        assertEquals(42, progress.getInt("article.scroll.777", 0))
    }

    @Test
    fun oldBackupWithoutNewSectionsKeepsCurrentLocalState() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val file = File(context.cacheDir, "backup-v1-sections.json")
        val service = createService()
        service.write(Uri.fromFile(file), includeSession = false)
        // Имитируем файл первой версии: ни закладок, ни истории, ни границ, ни прогресса.
        val legacy = org.json.JSONObject(file.readText()).put("version", 1)
        legacy.remove("bookmarks")
        legacy.remove("history")
        legacy.remove("read_boundary")
        legacy.getJSONObject("shared_preferences").remove("article_reading_progress")
        file.writeText(legacy.toString())

        database.noteItemDao().insertNote(
            NoteItemRoom(5, "Своя закладка", "link-5", "body-5", null, 50, 51, 0),
        )
        database.historyItemDao().insertHistory(
            HistoryItemRoom(7, "https://4pda.to/forum/7", "сегодня", "Своя история", 7000),
        )
        readBoundaryDatabase.topicReadBoundaryDao().upsert(
            TopicReadBoundaryRoom(topicId = 42, lastSeenPostId = 4200, lastSeenPage = 2),
        )
        val progress = context.getSharedPreferences("article_reading_progress", 0)
        progress.edit().clear().putInt("article.scroll.123", 77).commit()

        service.restore(Uri.fromFile(file))

        assertEquals(listOf(5L), database.noteItemDao().getAllNotesList().map { it.id })
        assertEquals(listOf(7), database.historyItemDao().getAllHistoryList().map { it.id })
        assertEquals(4200, readBoundaryDatabase.topicReadBoundaryDao().get(42)?.lastSeenPostId)
        assertEquals(77, progress.getInt("article.scroll.123", 0))
    }

    private companion object {
        const val TEST_SETTING = "backup_test_setting"
    }
}
