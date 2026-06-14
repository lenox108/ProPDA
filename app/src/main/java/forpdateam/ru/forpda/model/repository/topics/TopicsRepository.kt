package forpdateam.ru.forpda.model.repository.topics

import forpdateam.ru.forpda.entity.remote.topics.TopicsData
import forpdateam.ru.forpda.model.data.remote.api.topcis.TopicsApi
import forpdateam.ru.forpda.model.repository.search.ForumSectionTitleIndex
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/**
 * Created by radiationx on 03.01.18.
 */

class TopicsRepository(
        private val topicsApi: TopicsApi,
        private val forumSectionTitleIndex: ForumSectionTitleIndex
) {

    suspend fun getTopics(id: Int, st: Int): TopicsData = withContext(Dispatchers.IO) {
        withTimeout(30_000L) {
            topicsApi.getTopics(id, st)
        }.also { forumSectionTitleIndex.record(id, it) }
    }

}
