package forpdateam.ru.forpda.model.data.offline

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import forpdateam.ru.forpda.entity.db.notes.AppDatabase
import forpdateam.ru.forpda.entity.db.offline.OfflineItemRoom
import forpdateam.ru.forpda.entity.db.offline.OfflineItemStatus
import forpdateam.ru.forpda.entity.db.offline.OfflineItemType
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Unit tests for the Phase 6 storage-limit / LRU eviction
 * behaviour of [OfflineRepository].
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class OfflineRepositoryEvictionTest {

    private lateinit var db: AppDatabase
    private lateinit var repository: OfflineRepository
    private lateinit var storage: OfflineStorage

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        // Start with a clean filesystem and database.
        val offlineRoot = File(context.filesDir, OfflineStorage.ROOT_DIR)
        if (offlineRoot.exists()) offlineRoot.deleteRecursively()
        context.deleteDatabase("offline-eviction-test")
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        storage = OfflineStorage(context)
        repository = OfflineRepository(db.offlineItemDao(), storage)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun enforceStorageLimit_belowBudget_isNoop() = runBlocking {
        seedItem("article:1", OfflineItemStatus.COMPLETE, savedAtMs = 100L, sizeBytes = 100L)
        val removed = repository.enforceStorageLimit(maxBytes = 10_000L)
        assertEquals(0, removed)
        assertEquals(100L, repository.totalSizeBytes())
    }

    @Test
    fun enforceStorageLimit_evictsPartialItemsFirst() = runBlocking {
        seedItem("article:partial", OfflineItemStatus.PARTIAL, savedAtMs = 50L, sizeBytes = 500L)
        seedItem("article:complete-old", OfflineItemStatus.COMPLETE, savedAtMs = 100L, sizeBytes = 400L)
        seedItem("article:complete-new", OfflineItemStatus.COMPLETE, savedAtMs = 200L, sizeBytes = 300L)

        // Total is 1200. Budget 700 → drop the partial first (500 freed).
        val removed = repository.enforceStorageLimit(maxBytes = 700L)
        assertEquals(1, removed)
        assertNull(repository.getById("article:partial"))
        assertEquals(700L, repository.totalSizeBytes())
    }

    @Test
    fun enforceStorageLimit_evictsOldestCompleteWhenStillOverBudget() = runBlocking {
        seedItem("article:old", OfflineItemStatus.COMPLETE, savedAtMs = 100L, sizeBytes = 400L)
        seedItem("article:new", OfflineItemStatus.COMPLETE, savedAtMs = 200L, sizeBytes = 400L)

        // Total 800. Budget 350 → drop old (400 freed), total 400.
        // Still > 350; drop new. Total 0.
        val removed = repository.enforceStorageLimit(maxBytes = 350L)
        assertEquals(2, removed)
        assertEquals(0L, repository.totalSizeBytes())
    }

    @Test
    fun enforceStorageLimit_zeroBudget_isNoop() = runBlocking {
        seedItem("article:1", OfflineItemStatus.COMPLETE, savedAtMs = 100L, sizeBytes = 100L)
        val removed = repository.enforceStorageLimit(maxBytes = 0L)
        assertEquals(0, removed)
    }

    @Test
    fun enforceStorageLimit_emptyRepo_returnsZero() = runBlocking {
        val removed = repository.enforceStorageLimit(maxBytes = 1L)
        assertEquals(0, removed)
    }

    @Test
    fun enforceStorageLimit_skipsItemsThatAreNotEvictable_enoughToStayUnder() = runBlocking {
        // Single item larger than the budget: even after eviction the
        // size is still over, but we still remove it because partial
        // items go first and we delete the single item.
        seedItem("article:huge", OfflineItemStatus.PARTIAL, savedAtMs = 100L, sizeBytes = 5_000L)
        val removed = repository.enforceStorageLimit(maxBytes = 100L)
        assertEquals(1, removed)
        assertEquals(0L, repository.totalSizeBytes())
    }

    @Test
    fun totalSizeBytes_sumsAcrossItems() = runBlocking {
        seedItem("a", OfflineItemStatus.COMPLETE, savedAtMs = 1L, sizeBytes = 100L)
        seedItem("b", OfflineItemStatus.COMPLETE, savedAtMs = 2L, sizeBytes = 200L)
        seedItem("c", OfflineItemStatus.COMPLETE, savedAtMs = 3L, sizeBytes = 300L)
        assertEquals(600L, repository.totalSizeBytes())
    }

    @Test
    fun delete_removesBothRowAndFiles() = runBlocking {
        repository.save(
                id = "article:delete-me",
                type = OfflineItemType.ARTICLE,
                sourceUrl = "https://4pda.to/news/1",
                title = "Test",
                html = "<html><body>delete me</body></html>",
                modelJson = "{}"
        )
        assertTrue(repository.getById("article:delete-me") != null)
        assertTrue(storage.htmlFile("article:delete-me").exists())
        repository.delete("article:delete-me")
        assertNull(repository.getById("article:delete-me"))
        assertEquals(false, storage.htmlFile("article:delete-me").exists())
    }

    private suspend fun seedItem(
            id: String,
            status: String,
            savedAtMs: Long,
            sizeBytes: Long,
    ) {
        // We don't actually need the file on disk for size accounting
        // — totalSizeBytes is computed from the SQL sum, not from
        // walking the directory. The eviction logic reads sizes
        // straight from the row.
        db.offlineItemDao().insert(
                OfflineItemRoom(
                        id = id,
                        type = OfflineItemType.ARTICLE,
                        sourceUrl = "https://4pda.to/$id",
                        title = id,
                        savedAtMs = savedAtMs,
                        sizeBytes = sizeBytes,
                        status = status,
                        htmlPath = "offline/$id/index.html",
                        modelJson = "{}"
                )
        )
    }
}
