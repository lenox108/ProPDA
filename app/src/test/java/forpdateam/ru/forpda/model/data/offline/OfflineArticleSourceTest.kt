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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Unit tests for the Phase 4 [OfflineArticleSource] probe helper.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class OfflineArticleSourceTest {

    private lateinit var db: AppDatabase
    private lateinit var repository: OfflineRepository
    private lateinit var source: OfflineArticleSource
    private lateinit var storage: OfflineStorage

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val offlineRoot = File(context.filesDir, OfflineStorage.ROOT_DIR)
        if (offlineRoot.exists()) offlineRoot.deleteRecursively()
        context.deleteDatabase("offline-source-test")
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        storage = OfflineStorage(context)
        repository = OfflineRepository(db.offlineItemDao(), storage)
        source = OfflineArticleSource(repository)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun lookupArticle_notSaved_returnsNotSaved() = runBlocking {
        val probe = source.lookupArticle(42L)
        assertEquals(OfflineArticleSource.Probe.NotSaved, probe)
    }

    @Test
    fun lookupArticle_invalidId_returnsNotSaved() = runBlocking {
        assertEquals(OfflineArticleSource.Probe.NotSaved, source.lookupArticle(0L))
        assertEquals(OfflineArticleSource.Probe.NotSaved, source.lookupArticle(-1L))
    }

    @Test
    fun lookupArticle_completeEntry_returnsReady() = runBlocking {
        repository.save(
                id = source.articleId(123L),
                type = OfflineItemType.ARTICLE,
                sourceUrl = "https://4pda.to/news/123",
                title = "Test",
                html = "<html><body>cached</body></html>",
                modelJson = """{"id":123}""",
        )
        repository.markStatus(source.articleId(123L), OfflineItemStatus.COMPLETE, null)

        val probe = source.lookupArticle(123L)
        assertTrue(probe is OfflineArticleSource.Probe.Ready)
        val ready = probe as OfflineArticleSource.Probe.Ready
        assertEquals("<html><body>cached</body></html>", ready.html)
        assertEquals("""{"id":123}""", ready.modelJson)
    }

    @Test
    fun lookupArticle_partialEntry_returnsPartial() = runBlocking {
        repository.save(
                id = source.articleId(456L),
                type = OfflineItemType.ARTICLE,
                sourceUrl = "https://4pda.to/news/456",
                title = "Partial",
                html = "<html><body>in-flight</body></html>",
                modelJson = "{}"
        )
        // Status stays PARTIAL (set by save()).

        val probe = source.lookupArticle(456L)
        assertTrue(probe is OfflineArticleSource.Probe.Partial)
        val partial = probe as OfflineArticleSource.Probe.Partial
        assertEquals("<html><body>in-flight</body></html>", partial.html)
    }

    @Test
    fun lookupArticle_rowWithoutHtml_returnsNotSaved() = runBlocking {
        // Insert a row with status COMPLETE but no HTML on disk.
        db.offlineItemDao().insert(
                OfflineItemRoom(
                        id = source.articleId(789L),
                        type = OfflineItemType.ARTICLE,
                        sourceUrl = "https://4pda.to/news/789",
                        title = "Missing",
                        savedAtMs = 0L,
                        sizeBytes = 0L,
                        status = OfflineItemStatus.COMPLETE,
                        htmlPath = "offline/789/index.html",
                        modelJson = "{}"
                )
        )
        val probe = source.lookupArticle(789L)
        assertEquals(OfflineArticleSource.Probe.NotSaved, probe)
    }

    @Test
    fun lookupTheme_pages_distinctIds() = runBlocking {
        repository.save(
                id = source.themeId(99L, page = 0),
                type = OfflineItemType.THEME,
                sourceUrl = "https://4pda.to/forum/index.php?showtopic=99",
                title = "Theme page 0",
                html = "<html>page 0</html>",
                modelJson = "{}"
        )
        repository.markStatus(source.themeId(99L, 0), OfflineItemStatus.COMPLETE, null)
        repository.save(
                id = source.themeId(99L, page = 1),
                type = OfflineItemType.THEME,
                sourceUrl = "https://4pda.to/forum/index.php?showtopic=99&page=1",
                title = "Theme page 1",
                html = "<html>page 1</html>",
                modelJson = "{}"
        )
        repository.markStatus(source.themeId(99L, 1), OfflineItemStatus.COMPLETE, null)

        val probe0 = source.lookupTheme(99L, page = 0)
        val probe1 = source.lookupTheme(99L, page = 1)
        assertTrue(probe0 is OfflineArticleSource.Probe.Ready)
        assertTrue(probe1 is OfflineArticleSource.Probe.Ready)
        assertEquals("<html>page 0</html>", (probe0 as OfflineArticleSource.Probe.Ready).html)
        assertEquals("<html>page 1</html>", (probe1 as OfflineArticleSource.Probe.Ready).html)
    }

    @Test
    fun lookupTheme_notSaved_returnsNotSaved() = runBlocking {
        assertEquals(OfflineArticleSource.Probe.NotSaved, source.lookupTheme(99L, page = 0))
    }

    @Test
    fun baseUrlConstants_stable() {
        // The WebViewAssetLoader in Phase 4 must agree on these
        // values. Pin them so any drift triggers a test failure
        // here, not a silent "image not found" on device.
        assertEquals("https://offline.local/", OfflineArticleSource.OFFLINE_BASE_URL)
        assertEquals("images/", OfflineArticleSource.OFFLINE_IMAGES_PREFIX)
        assertEquals("index.html", OfflineArticleSource.OFFLINE_HTML_FILENAME)
    }
}
