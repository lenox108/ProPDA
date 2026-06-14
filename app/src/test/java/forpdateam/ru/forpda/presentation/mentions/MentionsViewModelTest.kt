package forpdateam.ru.forpda.presentation.mentions

import android.content.SharedPreferences
import forpdateam.ru.forpda.common.ClipboardHelper
import forpdateam.ru.forpda.entity.remote.mentions.MentionItem
import forpdateam.ru.forpda.entity.remote.mentions.MentionsData
import forpdateam.ru.forpda.model.CountersHolder
import forpdateam.ru.forpda.model.data.remote.api.favorites.FavoritesApi
import forpdateam.ru.forpda.model.repository.faviorites.FavoritesRepository
import forpdateam.ru.forpda.model.repository.mentions.MentionsRepository
import forpdateam.ru.forpda.presentation.IErrorHandler
import forpdateam.ru.forpda.presentation.ILinkHandler
import forpdateam.ru.forpda.presentation.TabRouter
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.coVerify
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MentionsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var mentionsRepository: MentionsRepository
    private lateinit var favoritesRepository: FavoritesRepository
    private lateinit var router: TabRouter
    private lateinit var linkHandler: ILinkHandler
    private lateinit var errorHandler: IErrorHandler
    private lateinit var clipboardHelper: ClipboardHelper
    private lateinit var preferences: SharedPreferences
    private lateinit var countersHolder: CountersHolder

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        mentionsRepository = mockk()
        favoritesRepository = mockk(relaxed = true)
        router = mockk(relaxed = true)
        linkHandler = mockk(relaxed = true)
        errorHandler = mockk(relaxed = true)
        clipboardHelper = mockk(relaxed = true)
        preferences = mockk(relaxed = true) {
            every { getInt(any(), any()) } answers { secondArg() }
            every { edit() } returns mockk(relaxed = true)
        }
        countersHolder = CountersHolder(preferences)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): MentionsViewModel {
        return MentionsViewModel(mentionsRepository, favoritesRepository, countersHolder, router, linkHandler, errorHandler, clipboardHelper)
    }

    @Test
    fun `loading mentions hides badge when list has no unread`() = runTest {
        countersHolder.update { it.mentions = 2 }
        coEvery { mentionsRepository.getCachedMentions(0) } returns null
        coEvery { mentionsRepository.refreshMentions(0) } returns MentionsData().apply {
            items.add(MentionItem().apply {
                state = MentionItem.STATE_READ
                type = MentionItem.TYPE_TOPIC
                link = "https://4pda.to/forum/index.php?showtopic=1&view=findpost&p=42"
            })
        }
        coEvery { mentionsRepository.getUnreadSnapshot() } returns MentionsRepository.UnreadMentionsSnapshot(
                unreadCount = 0,
                topicPostIds = emptyList()
        )

        createViewModel().getMentions()
        advanceUntilIdle()

        assertEquals(0, countersHolder.get().mentions)
    }

    @Test
    fun `loading mentions keeps badge when unread item remains`() = runTest {
        countersHolder.update { it.mentions = 1 }
        coEvery { mentionsRepository.getCachedMentions(0) } returns null
        coEvery { mentionsRepository.refreshMentions(0) } returns MentionsData().apply {
            items.add(MentionItem().apply {
                state = MentionItem.STATE_UNREAD
                type = MentionItem.TYPE_TOPIC
                link = "https://4pda.to/forum/index.php?showtopic=1&view=findpost&p=42"
            })
        }
        coEvery { mentionsRepository.getUnreadSnapshot() } returns MentionsRepository.UnreadMentionsSnapshot(
                unreadCount = 1,
                topicPostIds = listOf(42)
        )

        createViewModel().getMentions()
        advanceUntilIdle()

        assertEquals(1, countersHolder.get().mentions)
    }

    @Test
    fun `loading mentions emits cached data before background refresh`() = runTest {
        val cached = MentionsData().apply {
            items.add(MentionItem().apply {
                state = MentionItem.STATE_UNREAD
                type = MentionItem.TYPE_TOPIC
                title = "Cached"
                link = "https://4pda.to/forum/index.php?showtopic=1&view=findpost&p=42"
            })
        }
        val refreshed = MentionsData().apply {
            items.add(MentionItem().apply {
                state = MentionItem.STATE_UNREAD
                type = MentionItem.TYPE_TOPIC
                title = "Refreshed"
                link = "https://4pda.to/forum/index.php?showtopic=1&view=findpost&p=42"
            })
        }
        coEvery { mentionsRepository.getCachedMentions(0) } returns cached
        coEvery { mentionsRepository.refreshMentions(0) } returns refreshed
        coEvery { mentionsRepository.getUnreadSnapshot() } returns MentionsRepository.UnreadMentionsSnapshot(
                unreadCount = 1,
                topicPostIds = listOf(42)
        )

        createViewModel().getMentions()
        advanceUntilIdle()

        assertEquals(1, countersHolder.get().mentions)
        coVerify { mentionsRepository.getCachedMentions(0) }
        coVerify { mentionsRepository.refreshMentions(0) }
    }

    @Test
    fun `opening unread mention navigates without clearing badge`() = runTest {
        countersHolder.update { it.mentions = 2 }
        val item = MentionItem().apply {
            state = MentionItem.STATE_UNREAD
            type = MentionItem.TYPE_TOPIC
            title = "Topic"
            link = "https://4pda.to/forum/index.php?showtopic=1&view=findpost&p=42"
        }

        createViewModel().onItemClick(item)
        advanceUntilIdle()

        assertEquals(MentionItem.STATE_UNREAD, item.state)
        assertEquals(2, countersHolder.get().mentions)
        verify { linkHandler.handle(item.link, router, any()) }
        coVerify(exactly = 0) { mentionsRepository.markMentionItemRead(any()) }
    }
}
