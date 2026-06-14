package forpdateam.ru.forpda.model.repository.search

import forpdateam.ru.forpda.entity.remote.others.user.ForumUser
import forpdateam.ru.forpda.entity.remote.search.SearchResult
import forpdateam.ru.forpda.entity.remote.search.SearchSettings
import forpdateam.ru.forpda.model.data.cache.forumuser.ForumUsersCacheRoom
import forpdateam.ru.forpda.model.data.remote.api.search.SearchApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Created by radiationx on 01.01.18.
 */

class SearchRepository(
        private val searchApi: SearchApi,
        private val forumUsersCache: ForumUsersCacheRoom,
        private val forumSectionTitleIndex: ForumSectionTitleIndex
) {

    suspend fun getSearch(settings: SearchSettings): SearchResult = withContext(Dispatchers.IO) {
        searchApi.getSearch(settings)
                .also { saveUsers(it) }
                .also { appendForumSectionTitleSuggestions(it, settings) }
    }

    private suspend fun saveUsers(page: SearchResult) {
        val forumUsers = page.items.map { post ->
            ForumUser().apply {
                id = post.userId
                nick = post.nick
                avatar = post.avatar
            }
        }
        forumUsersCache.saveUsers(forumUsers)
    }

    private fun appendForumSectionTitleSuggestions(result: SearchResult, settings: SearchSettings) {
        val suggestions = forumSectionTitleIndex.suggestions(settings, result.items)
        if (suggestions.isEmpty()) return
        result.items.addAll(suggestions)
    }

}
