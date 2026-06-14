package forpdateam.ru.forpda.model.repository.forum

import forpdateam.ru.forpda.entity.remote.forum.Announce
import forpdateam.ru.forpda.entity.remote.forum.ForumItemFlat
import forpdateam.ru.forpda.entity.remote.forum.ForumItemTree
import forpdateam.ru.forpda.entity.remote.forum.ForumRules
import forpdateam.ru.forpda.model.data.cache.forum.ForumCacheRoom
import forpdateam.ru.forpda.model.data.remote.api.forum.ForumApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/**
 * Created by radiationx on 03.01.18.
 */

class ForumRepository(
        private val forumApi: ForumApi,
        private val forumCache: ForumCacheRoom
) {

    suspend fun getForums(): ForumItemTree = withContext(Dispatchers.IO) {
        withTimeout(30_000L) {
            forumApi.getForums()
        }
    }

    suspend fun getCache(): ForumItemTree = withContext(Dispatchers.IO) {
        ForumItemTree().apply {
            forumApi.transformToTree(forumCache.getItems(), this)
        }
    }

    suspend fun markAllRead(): Any = withContext(Dispatchers.IO) {
        forumApi.markAllRead()
    }

    suspend fun markRead(id: Int): Any = withContext(Dispatchers.IO) {
        forumApi.markRead(id)
    }

    suspend fun getRules(): ForumRules = withContext(Dispatchers.IO) {
        withTimeout(30_000L) {
            forumApi.getRules()
        }
    }

    suspend fun getAnnounce(id: Int, forumId: Int): Announce = withContext(Dispatchers.IO) {
        withTimeout(30_000L) {
            forumApi.getAnnounce(id, forumId)
        }
    }

    suspend fun saveCache(rootForum: ForumItemTree) = withContext(Dispatchers.IO) {
        val items = mutableListOf<ForumItemFlat>().apply {
            transformToList(this, rootForum)
        }
        forumCache.saveItems(items)
    }

    private fun transformToList(list: MutableList<ForumItemFlat>, rootForum: ForumItemTree) {
        rootForum.forums?.forEach { forum ->
            list.add(ForumItemFlat(forum))
            transformToList(list, forum)
        }
    }


}
