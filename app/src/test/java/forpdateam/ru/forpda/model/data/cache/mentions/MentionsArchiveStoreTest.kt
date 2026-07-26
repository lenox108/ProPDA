package forpdateam.ru.forpda.model.data.cache.mentions

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import forpdateam.ru.forpda.entity.db.mentions.MentionArchiveDatabase
import forpdateam.ru.forpda.entity.remote.mentions.MentionItem
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MentionsArchiveStoreTest {
    private lateinit var database: MentionArchiveDatabase
    private var clock = 1_000L
    private lateinit var store: MentionsArchiveStore

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
                ApplicationProvider.getApplicationContext(),
                MentionArchiveDatabase::class.java,
        ).allowMainThreadQueries().build()
        store = MentionsArchiveStore(database.mentionArchiveDao()) { clock }
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun archive_deduplicatesByPostAndKeepsAccountsSeparate() = runTest {
        store.archive(10, listOf(mention(1, 41, "Первый", MentionItem.STATE_UNREAD)))
        clock = 2_000L
        store.archive(10, listOf(mention(1, 42, "Второй", MentionItem.STATE_UNREAD)))
        clock = 3_000L
        store.archive(10, listOf(mention(1, 41, "Первый — обновлён", MentionItem.STATE_READ)))

        val accountOne = store.getPage(10, 0)
        val accountTwo = store.getPage(20, 0)

        assertEquals(listOf("Второй", "Первый — обновлён"), accountOne.items.map { it.title })
        assertEquals(listOf(false, true), accountOne.items.map { it.isRead })
        assertEquals(emptyList<MentionItem>(), accountTwo.items)
    }

    @Test
    fun archive_usesLocalPaginationWithoutDeletingOldRows() = runTest {
        repeat(21) { index ->
            clock = 1_000L + index
            store.archive(10, listOf(mention(1, 100 + index, "Ответ $index", MentionItem.STATE_READ)))
        }

        val first = store.getPage(10, 0)
        val second = store.getPage(10, 20)

        assertEquals(20, first.items.size)
        assertEquals(1, second.items.size)
        assertEquals(2, first.pagination.all)
        assertEquals(1, first.pagination.current)
        assertEquals(2, second.pagination.current)
    }

    @Test
    fun markTopicPostsRead_updatesArchivedRow() = runTest {
        store.archive(10, listOf(
                mention(1, 41, "Первый", MentionItem.STATE_UNREAD),
                mention(1, 42, "Второй", MentionItem.STATE_UNREAD),
        ))

        store.markTopicPostsRead(10, 1, listOf(42))

        val itemsByTitle = store.getPage(10, 0).items.associateBy { it.title }
        assertEquals(false, itemsByTitle.getValue("Первый").isRead)
        assertEquals(true, itemsByTitle.getValue("Второй").isRead)
    }

    private fun mention(topicId: Int, postId: Int, title: String, state: Int) = MentionItem().apply {
        this.title = title
        this.state = state
        type = MentionItem.TYPE_TOPIC
        link = "https://4pda.to/forum/index.php?showtopic=$topicId&view=findpost&p=$postId"
    }
}
