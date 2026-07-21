package forpdateam.ru.forpda.model.repository.history

import forpdateam.ru.forpda.entity.remote.topics.TopicItem
import forpdateam.ru.forpda.entity.remote.topics.TopicsData
import forpdateam.ru.forpda.model.data.remote.api.topcis.TopicsApi
import forpdateam.ru.forpda.model.repository.theme.TopicForumStore
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Покрывает гонку [HistoryUnreadHarvester.refresh] ↔ [HistoryUnreadHarvester.markOpened]: refresh делает
 * полный перезатир `_unread` ПОСЛЕ сетевого запроса, а список раздела ещё какое-то время отдаёт
 * `isNew=true` у только что прочитанной темы (лаг сервера) — без подавления точка воскресала бы.
 */
class HistoryUnreadHarvesterTest {

    private val topicsApi: TopicsApi = mockk(relaxed = true)
    private val topicForumStore: TopicForumStore = mockk(relaxed = true)
    private val harvester = HistoryUnreadHarvester(topicsApi, topicForumStore)

    private fun forumListingWithNew(topicId: Int) = TopicsData().apply {
        topicItems.add(TopicItem(id = topicId, isNew = true))
    }

    @Test
    fun `refresh lights a topic that has new posts`() = runBlocking {
        every { topicForumStore.get(100) } returns 7
        every { topicsApi.getTopics(7, 0) } returns forumListingWithNew(100)

        harvester.refresh(listOf(100))

        assertTrue(100 in harvester.unread.value)
    }

    @Test
    fun `markOpened suppresses re-lighting by a later refresh with stale isNew`() = runBlocking {
        every { topicForumStore.get(100) } returns 7
        every { topicsApi.getTopics(7, 0) } returns forumListingWithNew(100)

        // Открыли тему → точка гаснет немедленно.
        harvester.markOpened(100)
        assertFalse(100 in harvester.unread.value)

        // Запоздавший/параллельный refresh видит stale isNew=true, но НЕ должен вернуть точку.
        harvester.refresh(listOf(100))
        assertFalse(100 in harvester.unread.value)
    }
}
