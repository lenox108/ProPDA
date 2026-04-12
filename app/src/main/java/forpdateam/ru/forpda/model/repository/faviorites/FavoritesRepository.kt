package forpdateam.ru.forpda.model.repository.faviorites

import android.util.Log
import forpdateam.ru.forpda.entity.app.TabNotification
import forpdateam.ru.forpda.entity.remote.events.NotificationEvent
import forpdateam.ru.forpda.entity.remote.favorites.FavData
import forpdateam.ru.forpda.entity.remote.favorites.FavItem
import forpdateam.ru.forpda.model.AuthHolder
import forpdateam.ru.forpda.model.CountersHolder
import forpdateam.ru.forpda.model.SchedulersProvider
import forpdateam.ru.forpda.model.data.cache.favorites.FavoritesCache
import forpdateam.ru.forpda.model.data.remote.api.events.NotificationEventsApi
import forpdateam.ru.forpda.model.data.remote.api.favorites.FavoritesApi
import forpdateam.ru.forpda.model.data.remote.api.favorites.Sorting
import forpdateam.ru.forpda.model.preferences.ListsPreferencesHolder
import forpdateam.ru.forpda.model.preferences.NotificationPreferencesHolder
import forpdateam.ru.forpda.model.repository.BaseRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.rx2.asCoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * Created by radiationx on 01.01.18.
 */
class FavoritesRepository(
        private val schedulers: SchedulersProvider,
        private val favoritesApi: FavoritesApi,
        private val favoritesCache: FavoritesCache,
        private val authHolder: AuthHolder,
        private val countersHolder: CountersHolder,
        private val listsPreferencesHolder: ListsPreferencesHolder,
        private val notificationPreferencesHolder: NotificationPreferencesHolder,
        private val eventsApi: NotificationEventsApi
) : BaseRepository(schedulers) {

    private val ioDispatcher = schedulers.io().asCoroutineDispatcher()

    fun observeItems(): Flow<List<FavItem>> = favoritesCache.observeItems()

    suspend fun loadCache(): List<FavItem> = withContext(ioDispatcher) {
        favoritesCache.getItems()
    }

    suspend fun loadFavorites(st: Int, all: Boolean, sorting: Sorting): FavData = withContext(ioDispatcher) {
        val data = favoritesApi.getFavorites(st, all, sorting)
        mergeUnreadPostCountsFromInspector(data.items)
        favoritesCache.saveFavorites(data.items)
        data
    }

    suspend fun editFavorites(act: Int, favId: Int, id: Int, type: String?): Boolean = withContext(ioDispatcher) {
        when (act) {
            FavoritesApi.ACTION_EDIT_SUB_TYPE -> favoritesApi.editSubscribeType(type, favId)
            FavoritesApi.ACTION_EDIT_PIN_STATE -> favoritesApi.editPinState(type, favId)
            FavoritesApi.ACTION_DELETE -> favoritesApi.delete(favId)
            FavoritesApi.ACTION_ADD, FavoritesApi.ACTION_ADD_FORUM -> favoritesApi.add(id, act, type)
            else -> false
        }
    }

    suspend fun markRead(topicId: Int) = withContext(ioDispatcher) {
        val favItem = favoritesCache.getItemByTopicId(topicId)
        if (favItem != null) {
            favItem.isNew = false
            favItem.unreadPostCount = 0
            favoritesCache.updateItem(favItem)
        }
    }

    /** Все непрочитанные темы в избранном — на форуме и в локальном кэше. */
    suspend fun markAllFavoritesRead() = withContext(ioDispatcher) {
        val items = favoritesCache.getItems()
        val entries = items.filter { it.isNew && !it.isForum }.map { it.favId to it.topicId }
        if (entries.isNotEmpty()) {
            if (!favoritesApi.markFavoritesTopicsRead(entries)) {
                throw IllegalStateException("mark favorites read failed")
            }
            items.forEach {
                if (it.isNew && !it.isForum) {
                    it.isNew = false
                    it.unreadPostCount = 0
                }
            }
            favoritesCache.saveFavorites(items)
            countersHolder.set(countersHolder.get().apply {
                favorites = 0
            })
            eventsApi.invalidateFavoritesInspectorCache()
        }
    }

    suspend fun handleEvent(event: TabNotification): Int = withContext(ioDispatcher) {
        val favItems = favoritesCache.getItems()
        val sorting = Sorting(
                listsPreferencesHolder.getSortingKey(),
                listsPreferencesHolder.getSortingOrder()
        )
        val count = countersHolder.get().favorites
        handleEventTransaction(favItems, event, sorting, count).also {
            countersHolder.set(countersHolder.get().apply {
                favorites = it
            })
        }
    }

    private fun handleEventTransaction(favItems: List<FavItem>, event: TabNotification, sorting: Sorting, count: Int): Int {
        if (!NotificationEvent.fromTheme(event.source)) return count
        if (!notificationPreferencesHolder.getFavLiveTab()) return count
        if (event.isWebSocket && event.event.isNew) return count

        var newCount = count
        val newFavItems = favItems.toMutableList()
        val loadedEvent = event.event
        val topicId = loadedEvent.sourceId
        val isRead = loadedEvent.isRead

        Log.e("testtabnotify", "handleEventTransaction $newCount, $topicId, $isRead, ${loadedEvent.userNick}")

        if (isRead) {
            newFavItems.find { it.topicId == topicId }?.also {
                if (it.isNew) {
                    val dec = if (it.unreadPostCount > 0) it.unreadPostCount else 1
                    newCount = (count - dec).coerceAtLeast(0)
                    it.isNew = false
                    it.unreadPostCount = 0
                }
                Log.e("testtabnotify", "found item ${it.isNew}, $newCount")
            }
        } else {
            if (event.loadedEvents.isNotEmpty()) {
                val byTopic = event.loadedEvents.associateBy { it.sourceId }
                newFavItems.forEach { fav ->
                    if (fav.isForum) {
                        fav.unreadPostCount = 0
                        return@forEach
                    }
                    val ev = byTopic[fav.topicId]
                    if (ev != null) {
                        if (ev.favoriteInspectorHasUnread()) {
                            fav.isNew = true
                            val hint = ev.inspectorUnreadHint()
                            fav.unreadPostCount = if (hint > 0) {
                                maxOf(fav.unreadPostCount, hint).coerceAtLeast(1)
                            } else {
                                fav.unreadPostCount.coerceAtLeast(1)
                            }
                            fav.lastUserNick = ev.userNick
                            fav.lastUserId = ev.userId
                            fav.isPin = ev.isImportant
                        } else {
                            fav.isNew = false
                            fav.unreadPostCount = 0
                        }
                    } else {
                        if (fav.isNew) {
                            fav.unreadPostCount = fav.unreadPostCount.coerceAtLeast(1)
                        } else {
                            fav.unreadPostCount = 0
                        }
                    }
                }
                newCount = newFavItems.filter { it.isNew && !it.isForum }.sumOf { it.unreadPostCount.coerceAtLeast(1) }
            } else {
                newCount = count
                newFavItems.find { it.topicId == topicId }?.also { fav ->
                    if (fav.lastUserId != authHolder.get().userId) {
                        fav.isNew = true
                    }
                    fav.lastUserNick = loadedEvent.userNick
                    fav.lastUserId = loadedEvent.userId
                    fav.isPin = loadedEvent.isImportant
                    val inspectorRead = loadedEvent.timeStamp > 0 && loadedEvent.lastTimeStamp > 0 &&
                            loadedEvent.timeStamp <= loadedEvent.lastTimeStamp
                    if (inspectorRead) {
                        fav.isNew = false
                        fav.unreadPostCount = 0
                    } else if (fav.isNew) {
                        val hint = loadedEvent.inspectorUnreadHint()
                        fav.unreadPostCount = if (hint > 0) {
                            maxOf(fav.unreadPostCount, hint).coerceAtLeast(1)
                        } else {
                            fav.unreadPostCount.coerceAtLeast(1)
                        }
                    }
                }
            }
            Log.e("testtabnotify", "lalala $newCount")
            if (sorting.key == Sorting.Key.TITLE) {
                if (sorting.order == Sorting.Order.ASC) {
                    newFavItems.sortWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.topicTitle.orEmpty() })
                } else {
                    newFavItems.sortWith(compareByDescending(String.CASE_INSENSITIVE_ORDER) { it.topicTitle.orEmpty() })
                }
            }

            if (sorting.key == Sorting.Key.LAST_POST) {
                newFavItems.find { it.topicId == topicId }?.also {
                    newFavItems.remove(it)
                    if (sorting.order == Sorting.Order.ASC) {
                        newFavItems.add(newFavItems.size, it)
                    } else {
                        newFavItems.add(0, it)
                    }
                }
            }
        }
        favoritesCache.saveFavorites(newFavItems)
        return newCount
    }

    private fun mergeUnreadPostCountsFromInspector(items: MutableList<FavItem>) {
        val events = try {
            eventsApi.favoritesEvents
        } catch (_: Exception) {
            emptyList()
        }
        if (events.isEmpty()) {
            items.forEach { fav ->
                if (!fav.isNew) {
                    fav.unreadPostCount = 0
                } else if (fav.unreadPostCount < 1) {
                    fav.unreadPostCount = 1
                }
            }
            return
        }
        val byTopic = events.associateBy { it.sourceId }
        items.forEach { fav ->
            if (fav.isForum) {
                fav.unreadPostCount = 0
                return@forEach
            }
            val ev = byTopic[fav.topicId]
            when {
                ev != null && ev.favoriteInspectorHasUnread() -> {
                    fav.isNew = true
                    val hint = ev.inspectorUnreadHint()
                    fav.unreadPostCount = if (hint > 0) {
                        maxOf(fav.unreadPostCount, hint).coerceAtLeast(1)
                    } else {
                        fav.unreadPostCount.coerceAtLeast(1)
                    }
                }
                ev != null -> {
                    fav.isNew = false
                    fav.unreadPostCount = 0
                }
                fav.isNew -> {
                    if (fav.unreadPostCount < 1) fav.unreadPostCount = 1
                }
                else -> fav.unreadPostCount = 0
            }
        }
    }
}

/**
 * Есть непрочитанное по строке инспектора fav.
 * Если last_read_ts = 0 (ещё не открывали по трекингу) — считаем непрочитанным при ненулевом last_post_ts.
 */
private fun NotificationEvent.favoriteInspectorHasUnread(): Boolean {
    if (timeStamp <= 0) return false
    if (lastTimeStamp <= 0) return true
    return timeStamp > lastTimeStamp
}

/**
 * Третье поле инспектора (msgCount / posts_num): на 4pda часто совпадает с числом новых постов;
 * очень большие значения трактуем как «всего в теме» и не подмешиваем в бейдж.
 */
private fun NotificationEvent.inspectorUnreadHint(): Int {
    val m = msgCount
    return if (m in 1..INSPECTOR_UNREAD_COUNT_MAX) m else 0
}

private const val INSPECTOR_UNREAD_COUNT_MAX = 199
