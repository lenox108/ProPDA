package forpdateam.ru.forpda.model.data.cache.favorites

import forpdateam.ru.forpda.entity.db.favorites.FavItemDao
import forpdateam.ru.forpda.entity.db.favorites.FavItemRoom
import forpdateam.ru.forpda.entity.remote.favorites.FavItem
import forpdateam.ru.forpda.entity.remote.favorites.FavoriteReadState
import forpdateam.ru.forpda.entity.remote.favorites.FavoriteReadState.Companion.toStorage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class FavoritesCacheRoom(private val favItemDao: FavItemDao) {

    private val _items = MutableStateFlow<List<FavItem>>(emptyList())
    fun observeItems(): StateFlow<List<FavItem>> = _items.asStateFlow()

    suspend fun getItems(): List<FavItem> {
        return readItems()
    }

    suspend fun ensureItemsPublished(): List<FavItem> {
        return if (_items.value.isEmpty()) {
            publishItems()
        } else {
            _items.value
        }
    }

    private suspend fun publishItems(): List<FavItem> {
        val favItems = readItems()
        _items.value = favItems
        return favItems
    }

    private suspend fun readItems(): List<FavItem> {
        val items = favItemDao.getAllFavoritesList()
        val favItems = items.map { FavItem().apply {
            favId = it.favId
            topicId = it.topicId
            forumId = it.forumId
            authorId = it.authorId
            lastUserId = it.lastUserId
            stParam = it.stParam
            pages = it.pages
            curatorId = it.curatorId
            trackType = it.trackType
            infoColor = it.infoColor
            topicTitle = it.topicTitle
            forumTitle = it.forumTitle
            authorUserNick = it.authorUserNick
            lastUserNick = it.lastUserNick
            date = it.date
            desc = it.desc
            curatorNick = it.curatorNick
            subType = it.subType
            isPin = it.isPin
            isForum = it.isForum
            readState = FavoriteReadState.fromStorage(it.readState)
            isNew = it.isNew || readState == FavoriteReadState.UNREAD
            isPoll = it.isPoll
            isClosed = it.isClosed
            unreadPostCount = when {
                readState == FavoriteReadState.UNREAD -> it.unreadPostCount.coerceAtLeast(1)
                else -> it.unreadPostCount
            }
            localReadPostId = it.localReadPostId
            localReadPostDateMillis = it.localReadPostDateMillis
        } }
        return favItems
    }

    suspend fun saveFavorites(items: List<FavItem>) {
        val favItemsRoom = items.map { FavItemRoom(
            favId = it.favId,
            topicId = it.topicId,
            forumId = it.forumId,
            authorId = it.authorId,
            lastUserId = it.lastUserId,
            stParam = it.stParam,
            pages = it.pages,
            curatorId = it.curatorId,
            trackType = it.trackType,
            infoColor = it.infoColor,
            topicTitle = it.topicTitle,
            forumTitle = it.forumTitle,
            authorUserNick = it.authorUserNick,
            lastUserNick = it.lastUserNick,
            date = it.date,
            desc = it.desc,
            curatorNick = it.curatorNick,
            subType = it.subType,
            isPin = it.isPin,
            isForum = it.isForum,
            isNew = it.isNew,
            readState = it.readState.toStorage(),
            isPoll = it.isPoll,
            isClosed = it.isClosed,
            unreadPostCount = it.unreadPostCount,
            localReadPostId = it.localReadPostId,
            localReadPostDateMillis = it.localReadPostDateMillis
        ) }
        // Атомарный wipe+insert в одной Room-транзакции — раньше это были
        // два write-цикла, что давало окно для гонки при чтении StateFlow.
        favItemDao.replaceFavorites(favItemsRoom)
        publishItems()
    }

    suspend fun getItemByFavId(favId: Int): FavItem? {
        val item = favItemDao.getFavoriteById(favId) ?: return null
        return FavItem().apply {
            this.favId = item.favId
            this.topicId = item.topicId
            this.forumId = item.forumId
            this.authorId = item.authorId
            this.lastUserId = item.lastUserId
            this.stParam = item.stParam
            this.pages = item.pages
            this.curatorId = item.curatorId
            this.trackType = item.trackType
            this.infoColor = item.infoColor
            this.topicTitle = item.topicTitle
            this.forumTitle = item.forumTitle
            this.authorUserNick = item.authorUserNick
            this.lastUserNick = item.lastUserNick
            this.date = item.date
            this.desc = item.desc
            this.curatorNick = item.curatorNick
            this.subType = item.subType
            this.isPin = item.isPin
            this.isForum = item.isForum
            this.isNew = item.isNew
            this.readState = FavoriteReadState.fromStorage(item.readState)
            this.isPoll = item.isPoll
            this.isClosed = item.isClosed
            this.unreadPostCount = item.unreadPostCount
            this.localReadPostId = item.localReadPostId
            this.localReadPostDateMillis = item.localReadPostDateMillis
        }
    }

    suspend fun getItemByTopicId(topicId: Int): FavItem? {
        val items = favItemDao.getAllFavoritesList()
        val item = items.find { it.topicId == topicId } ?: return null
        return FavItem().apply {
            this.favId = item.favId
            this.topicId = item.topicId
            this.forumId = item.forumId
            this.authorId = item.authorId
            this.lastUserId = item.lastUserId
            this.stParam = item.stParam
            this.pages = item.pages
            this.curatorId = item.curatorId
            this.trackType = item.trackType
            this.infoColor = item.infoColor
            this.topicTitle = item.topicTitle
            this.forumTitle = item.forumTitle
            this.authorUserNick = item.authorUserNick
            this.lastUserNick = item.lastUserNick
            this.date = item.date
            this.desc = item.desc
            this.curatorNick = item.curatorNick
            this.subType = item.subType
            this.isPin = item.isPin
            this.isForum = item.isForum
            this.isNew = item.isNew
            this.readState = FavoriteReadState.fromStorage(item.readState)
            this.isPoll = item.isPoll
            this.isClosed = item.isClosed
            this.unreadPostCount = item.unreadPostCount
            this.localReadPostId = item.localReadPostId
            this.localReadPostDateMillis = item.localReadPostDateMillis
        }
    }

    suspend fun updateItem(item: FavItem) {
        val existingItem = favItemDao.getFavoriteById(item.favId) ?: return
        val updatedItem = existingItem.copy(
            topicId = item.topicId,
            forumId = item.forumId,
            authorId = item.authorId,
            lastUserId = item.lastUserId,
            stParam = item.stParam,
            pages = item.pages,
            curatorId = item.curatorId,
            trackType = item.trackType,
            infoColor = item.infoColor,
            topicTitle = item.topicTitle,
            forumTitle = item.forumTitle,
            authorUserNick = item.authorUserNick,
            lastUserNick = item.lastUserNick,
            date = item.date,
            desc = item.desc,
            curatorNick = item.curatorNick,
            subType = item.subType,
            isPin = item.isPin,
            isForum = item.isForum,
            isNew = item.isNew,
            readState = item.readState.toStorage(),
            isPoll = item.isPoll,
            isClosed = item.isClosed,
            unreadPostCount = item.unreadPostCount,
            localReadPostId = item.localReadPostId,
            localReadPostDateMillis = item.localReadPostDateMillis
        )
        favItemDao.updateFavorite(updatedItem)
        publishItems()
    }
}
