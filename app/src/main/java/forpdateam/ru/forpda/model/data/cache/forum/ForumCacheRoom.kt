package forpdateam.ru.forpda.model.data.cache.forum

import forpdateam.ru.forpda.entity.db.forum.ForumItemFlatDao
import forpdateam.ru.forpda.entity.db.forum.ForumItemFlatRoom
import forpdateam.ru.forpda.entity.remote.forum.ForumItemFlat

class ForumCacheRoom(private val forumItemFlatDao: ForumItemFlatDao) {

    suspend fun getItems(): List<ForumItemFlat> {
        val items = forumItemFlatDao.getAllForumItemsList()
        return items.map { ForumItemFlat().apply {
            id = it.id
            parentId = it.parentId
            level = it.level
            title = it.title
        } }
    }

    suspend fun saveItems(items: List<ForumItemFlat>) {
        forumItemFlatDao.deleteAllForumItems()
        val forumItems = items.mapIndexed { index, item -> ForumItemFlatRoom(
            id = item.id,
            parentId = item.parentId,
            level = item.level,
            title = item.title,
            position = index
        ) }
        forumItemFlatDao.insertForumItems(forumItems)
    }
}
