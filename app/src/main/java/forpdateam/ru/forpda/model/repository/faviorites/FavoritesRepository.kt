package forpdateam.ru.forpda.model.repository.faviorites

import forpdateam.ru.forpda.common.Utils
import forpdateam.ru.forpda.diagnostic.FavoritesUnreadTrace
import forpdateam.ru.forpda.entity.app.TabNotification
import forpdateam.ru.forpda.entity.remote.events.NotificationEvent
import forpdateam.ru.forpda.entity.remote.favorites.FavData
import forpdateam.ru.forpda.entity.remote.favorites.FavItem
import forpdateam.ru.forpda.entity.remote.favorites.FavoriteReadState
import forpdateam.ru.forpda.entity.remote.theme.ThemePage
import forpdateam.ru.forpda.model.AuthHolder
import forpdateam.ru.forpda.model.CountersHolder
import forpdateam.ru.forpda.model.data.cache.favorites.FavoritesCacheRoom
import forpdateam.ru.forpda.model.data.remote.api.events.NotificationEventsApi
import forpdateam.ru.forpda.diagnostic.ThemePostReadStateDiagnostics
import forpdateam.ru.forpda.model.data.remote.api.favorites.FavoritesApi
import forpdateam.ru.forpda.model.data.remote.api.favorites.FavoritesSort
import forpdateam.ru.forpda.model.data.remote.api.favorites.Sorting
import forpdateam.ru.forpda.model.preferences.ListsPreferencesHolder
import forpdateam.ru.forpda.model.preferences.NotificationPreferencesHolder
import forpdateam.ru.forpda.model.repository.theme.TopicReadBoundaryStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Created by radiationx on 01.01.18.
 */
class FavoritesRepository(
        private val favoritesApi: FavoritesApi,
        private val favoritesCache: FavoritesCacheRoom,
        private val authHolder: AuthHolder,
        private val countersHolder: CountersHolder,
        private val listsPreferencesHolder: ListsPreferencesHolder,
        private val notificationPreferencesHolder: NotificationPreferencesHolder,
        private val eventsApi: NotificationEventsApi,
        private val readBoundaryStore: TopicReadBoundaryStore
) {

    /**
     * Сериализует все секции «снапшот кэша → мутация → запись» этого репозитория. Обработчики
     * событий ([handleEvent]) и сетевой refresh ([loadFavorites]) пишут в кэш ВЕСЬ список из
     * point-in-time снапшота, а [markRead]/[syncSubmittedTopicLastPost] — одну строку; без общего
     * лока полная запись по устаревшему снапшоту молча откатывала конкурентный markRead (строка
     * снова UNREAD) или затирала его localRead*-маркеры, ослепляя анти-relight гейты — «дочитал
     * тему, вышел, а в избранном висит непрочитанной до refresh». Сетевые вызовы держим ВНЕ лока.
     */
    private val cacheMutex = Mutex()

    fun observeItems(sorting: Sorting? = null, unreadTop: Boolean = listsPreferencesHolder.getUnreadTop()): Flow<List<FavItem>> =
            favoritesCache.observeItems().map { items -> items.sortedForFavorites(sorting, unreadTop) }

    suspend fun loadCache(sorting: Sorting? = null, unreadTop: Boolean = listsPreferencesHolder.getUnreadTop()): List<FavItem> = withContext(Dispatchers.IO) {
        FavoritesUnreadTrace.loadStarted(source = "cache", sortUnreadOnTop = unreadTop)
        val inspectorEvents = loadInspectorEvents()
        cacheMutex.withLock {
            val items = favoritesCache.getItems().map(::FavItem).toMutableList()
            mergeInspectorIntoCachedItems(items, inspectorEvents)
            persistInspectorReadStateChanges(items)
        }
        favoritesCache.ensureItemsPublished().sortedForFavorites(sorting, unreadTop).also {
            traceSortedItems(it, unreadTop)
            syncFavoritesCounter(it, source = "favorites_cache", protectFromHeaderInflation = false)
        }
    }

    /**
     * Тихо пересчитывает бейдж непрочитанного избранного из кэша + свежего inspector'а, НЕ публикуя
     * список в UI-поток. Нужен, чтобы циферка появлялась на «Полном меню»/в навигации без захода в
     * избранное (у QMS эту роль играет [forpdateam.ru.forpda.model.interactors.qms.QmsInteractor],
     * у упоминаний — EventsRepository). Дёргается из
     * [forpdateam.ru.forpda.model.interactors.favorites.FavoritesInteractor] на старте/возврате.
     *
     * Пустой кэш (избранное ещё ни разу не грузили) пропускаем: считать нечего, а сетевой inspector
     * без списка тем всё равно не даст корректный счёт.
     */
    suspend fun seedFavoritesCounter() = withContext(Dispatchers.IO) {
        if (!authHolder.get().isAuth()) return@withContext
        if (favoritesCache.getItems().isEmpty()) return@withContext
        val inspectorEvents = loadInspectorEvents()
        val items = cacheMutex.withLock {
            favoritesCache.getItems().map(::FavItem).toMutableList().also {
                mergeInspectorIntoCachedItems(it, inspectorEvents)
                persistInspectorReadStateChanges(it)
            }
        }
        syncFavoritesCounter(items, source = "favorites_counter_seed", protectFromHeaderInflation = false)
    }

    suspend fun loadFavorites(st: Int, all: Boolean, sorting: Sorting, forceRefresh: Boolean = false): FavData = withContext(Dispatchers.IO) {
        val unreadTop = listsPreferencesHolder.getUnreadTop()
        FavoritesUnreadTrace.loadStarted(
                source = if (forceRefresh) "network_refresh" else "network",
                sortUnreadOnTop = unreadTop
        )
        if (forceRefresh) {
            eventsApi.invalidateFavoritesInspectorCache()
        }
        val data = favoritesApi.getFavorites(st, all, sorting, forceRefresh)
        val inspectorEvents = loadInspectorEvents()
        cacheMutex.withLock {
            val cachedByFavId = favoritesCache.getItems().associateBy { it.favId }
            mergeNetworkFavoriteReadStates(data.items, cachedByFavId, inspectorEvents, networkIsFreshRefresh = forceRefresh)
            favoritesCache.saveFavorites(data.items)
        }
        traceSortedItems(data.items, unreadTop)
        syncFavoritesCounter(data.items, source = "favorites_refresh")
        data
    }

    /** Full favorites list for toolbar search; does not replace paginated page cache. */
    suspend fun fetchAllFavoritesForSearch(sorting: Sorting): List<FavItem> = withContext(Dispatchers.IO) {
        val data = favoritesApi.getFavorites(0, all = true, sorting, bypassCache = false)
        mergeInspectorIntoCachedItems(data.items, loadInspectorEvents())
        data.items.sortedForFavorites(sorting, listsPreferencesHolder.getUnreadTop())
    }

    suspend fun editFavorites(act: Int, favId: Int, id: Int, type: String?): Boolean = withContext(Dispatchers.IO) {
        when (act) {
            FavoritesApi.ACTION_EDIT_SUB_TYPE -> favoritesApi.editSubscribeType(type, favId)
            FavoritesApi.ACTION_EDIT_PIN_STATE -> favoritesApi.editPinState(type, favId)
            FavoritesApi.ACTION_DELETE -> favoritesApi.delete(favId)
            FavoritesApi.ACTION_ADD, FavoritesApi.ACTION_ADD_FORUM -> favoritesApi.add(id, act, type)
            else -> false
        }
    }

    /**
     * Server-side mark-read for a single topic: GET ...showtopic=ID&view=getlastpost.
     * Used by ThemeUseCase after natural bottom-of-topic read.
     */
    suspend fun markFavoriteTopicRead(topicId: Int): Boolean = withContext(Dispatchers.IO) {
        if (topicId <= 0) return@withContext false
        runCatching { favoritesApi.markFavoriteTopicRead(topicId) }.getOrDefault(false)
    }

    suspend fun markRead(topicId: Int) = withContext(Dispatchers.IO) {
        cacheMutex.withLock { markReadLocked(topicId) }
    }

    private suspend fun markReadLocked(topicId: Int) {
        val favItem = favoritesCache.getItemByTopicId(topicId)
        if (favItem == null) {
            // Debug #3: cache miss — must be visible in logs, otherwise we silently drop a mark-read.
            ThemePostReadStateDiagnostics.markReadSkipped(
                    topicId = topicId,
                    reason = "item_not_in_cache",
                    source = "favorites_repository",
                    currentPage = 0,
                    allPages = 0
            )
            return
        }
        val prevIsNew = favItem.isNew
        val prevReadState = favItem.readState
        val prevUnreadCount = favItem.unreadPostCount
        favItem.isNew = false
        favItem.readState = FavoriteReadState.READ
        favItem.unreadPostCount = 0
        favItem.listingHref = null
        // Не сбрасываем inspectorMarkedUnread — он должен пересчитываться только в merge на
        // следующем refresh. Иначе теряем бейдж до прихода inspector.
        favItem.localReadPostId = topicId
        favItem.localReadPostDateMillis = System.currentTimeMillis()
        favoritesCache.updateItem(favItem)
        // Тема помечена прочитанной — снимаем клиентскую границу прочитанного, иначе при следующем
        // открытии TopicReadBoundaryPolicy резюмнёт findpost'ом на старую границу (последний реально
        // виденный пост), и юзера отбросит на позавчерашний пост вместо свежих. См.
        // [TopicReadBoundaryStore]/[maybeResumeToReadBoundary].
        readBoundaryStore.clear(topicId)
        syncFavoritesCounter(favoritesCache.getItems(), source = "favorites_mark_read")
        // Debug #2: фиксируем фактическое изменение (было/стало) для последующего аудита.
        ThemePostReadStateDiagnostics.markReadApplied(
                topicId = topicId,
                reason = "cross_screen_topic_changed",
                source = "favorites_repository",
                prevIsNew = prevIsNew,
                prevReadState = prevReadState.name,
                prevUnreadCount = prevUnreadCount,
                newIsNew = favItem.isNew,
                newReadState = favItem.readState.name,
                newUnreadCount = favItem.unreadPostCount,
                itemPresent = true
        )
    }

    suspend fun syncTopicLastPost(page: ThemePage) = withContext(Dispatchers.IO) {
        // Intentionally no-op: manual Favorites refresh should trust the network list.
    }

    suspend fun syncSubmittedTopicLastPost(
            topicId: Int,
            currentUserId: Int,
            currentUserNick: String?,
            sentAtMillis: Long,
            page: ThemePage?
    ) = withContext(Dispatchers.IO) {
        // Оптимистичный апдейт «последнего поста» в избранном ПОСЛЕ нашей собственной отправки.
        // Без него список показывает прежнего автора/время (например «натали76, 13:19»), хотя свежий
        // пост в теме уже наш — пока не придёт ручной/событийный refresh. В отличие от просмотра темы
        // ([syncTopicLastPost] остаётся no-op), отправка реально создаёт новый последний пост, поэтому
        // локальный оверрайд здесь корректен. Безопасность: любой сетевой refresh ([loadFavorites] →
        // saveFavorites) перезаписывает date/lastUserNick серверной правдой (merge сохраняет только
        // read-state), так что оверрайд недолговечен и не «затирает более свежие данные».
        if (topicId <= 0 || sentAtMillis <= 0L) return@withContext
        cacheMutex.withLock {
            val favItem = favoritesCache.getItemByTopicId(topicId) ?: return@withLock
            if (favItem.isForum) return@withLock
            // Гард: применяем только если наш пост действительно новее уже сохранённого последнего
            // (на случай, если refresh успел принести ещё более свежую запись).
            val currentMillis = Utils.parseForumDateTime(favItem.date)?.time ?: Long.MIN_VALUE
            if (sentAtMillis <= currentMillis) return@withLock
            favItem.lastUserId = currentUserId
            if (!currentUserNick.isNullOrBlank()) {
                favItem.lastUserNick = currentUserNick
            }
            favItem.date = Utils.getForumDateTime(java.util.Date(sentAtMillis))
            // Мы только что отписались — тема прочитана до нашего поста.
            favItem.isNew = false
            favItem.readState = FavoriteReadState.READ
            favItem.unreadPostCount = 0
            favItem.localReadPostId = topicId
            favItem.localReadPostDateMillis = sentAtMillis
            page?.pagination?.all?.takeIf { it > favItem.pages }?.let { favItem.pages = it }
            favoritesCache.updateItem(favItem)
            syncFavoritesCounter(favoritesCache.getItems(), source = "favorites_submitted_last_post")
        }
    }

    /** Все непрочитанные темы в избранном - на форуме и в локальном кэше. */
    suspend fun markFavoriteTopicsRead(
            entries: List<FavoriteMarkReadEntry>,
            onProgress: suspend (FavoriteMarkReadProgress) -> Unit
    ): FavoriteMarkReadResult = withContext(Dispatchers.IO) {
        if (entries.isEmpty()) {
            return@withContext FavoriteMarkReadResult(successTopicIds = emptySet(), failedTopicIds = emptySet())
        }

        val successTopicIds = linkedSetOf<Int>()
        val failedTopicIds = linkedSetOf<Int>()

        entries.forEachIndexed { index, entry ->
            val ok = favoritesApi.markFavoriteTopicRead(entry.topicId)
            if (ok) {
                successTopicIds.add(entry.topicId)
            } else {
                failedTopicIds.add(entry.topicId)
            }
            onProgress(
                    FavoriteMarkReadProgress(
                            processed = index + 1,
                            total = entries.size,
                            success = successTopicIds.size,
                            failed = failedTopicIds.size
                    )
            )
        }

        if (successTopicIds.isNotEmpty()) {
            // Явная отметка «прочитано» из списка избранного — снимаем клиентскую границу прочитанного
            // по каждой затронутой теме. Иначе при следующем открытии TopicReadBoundaryPolicy увидит,
            // что сервер сел бы НИЖЕ устаревшей границы (позавчерашний последний виденный пост), решит
            // это walk-down'ом и резюмнёт findpost'ом на старую границу — юзера отбросит на старый пост
            // вместо свежих. cold-miss после clear → резюм молчит → серверный getlastpost/getnewpost
            // ведёт на свежак, чего пользователь и ждёт. См. [TopicReadBoundaryStore].
            successTopicIds.forEach { readBoundaryStore.clear(it) }
            cacheMutex.withLock {
                val items = favoritesCache.getItems()
                items.forEach {
                    if (it.topicId in successTopicIds && it.isNew && !it.isForum) {
                        it.isNew = false
                        it.readState = FavoriteReadState.READ
                        it.unreadPostCount = 0
                        // Намеренно сбрасываем inspectorMarkedUnread здесь: при markAllFavoritesRead
                        // пользователь явно потребовал считать тему прочитанной — последующий merge
                        // не должен заново поднять бейдж до прихода нового инспектора.
                        it.inspectorMarkedUnread = false
                    }
                }
                favoritesCache.saveFavorites(items)
                countersHolder.set(countersHolder.get().apply {
                    favorites = countUnreadFavoriteTopics(items)
                }, source = "favorites_mark_all_read")
            }
            eventsApi.invalidateFavoritesInspectorCache()
        }

        FavoriteMarkReadResult(successTopicIds = successTopicIds, failedTopicIds = failedTopicIds)
    }

    suspend fun handleEvent(event: TabNotification): Int = withContext(Dispatchers.IO) {
        // Снапшот и полная запись списка в handleEventTransaction обязаны быть атомарны
        // относительно markRead и сетевого refresh — иначе full-save по устаревшему снапшоту
        // молча откатывает свежую локальную отметку прочитанного.
        cacheMutex.withLock {
            val favItems = favoritesCache.getItems()
            val sorting = Sorting(
                    listsPreferencesHolder.getSortingKey(),
                    listsPreferencesHolder.getSortingOrder()
            )
            val count = countersHolder.get().favorites
            handleEventTransaction(favItems, event, sorting, count).also {
                countersHolder.set(countersHolder.get().apply {
                    favorites = it
                }, source = "favorites_events")
            }
        }
    }

    private suspend fun handleEventTransaction(favItems: List<FavItem>, event: TabNotification, sorting: Sorting, count: Int): Int {
        if (!NotificationEvent.fromTheme(event.source)) return count
        if (!notificationPreferencesHolder.getFavLiveTab()) return count

        // Раньше здесь стоял ранний `return count`: WS-событие о новом посте в избранной теме
        // намеренно игнорировалось, а бейдж ждал follow-up от inspector'а (~2.5s). Из-за этого
        // счётчик избранного не рос в реальном времени — он двигался только на seed/резюме или
        // при полном заходе в раздел. WS-событие несёт лишь sourceId/type (ник/аватар/важность и
        // временные метки в нём пусты — см. NotificationEventsApi.parseWebSocketEvent), поэтому
        // помечаем тему непрочитанной МИНИМАЛЬНО, не затирая метаданные строки. Точные данные
        // (ник, важность, порядок) подтянет follow-up inspector или полный рефреш списка.
        if (event.isWebSocket && event.event.isNew) {
            val newFavItems = favItems.toMutableList()
            newFavItems.find { it.topicId == event.event.sourceId && !it.isForum && it.topicId > 0 }?.also { fav ->
                // WS-«новое» может быть ЗАДЕРЖАННЫМ событием о посте, который мы только что открыли и
                // прочитали (гонка доставки WS и нашего markRead) → не зажигаем обратно. Точной метки
                // времени у WS-события нет (parseWebSocketEvent их не заполняет), поэтому гейтим по
                // свежести локального прочтения. Настоящий новый пост позже окна честно зажжёт тему
                // (через inspector/полный рефреш).
                val recentlyReadLocally = fav.readState == FavoriteReadState.READ &&
                        !fav.isNew &&
                        fav.localReadPostId > 0 &&
                        fav.localReadPostDateMillis > 0L &&
                        System.currentTimeMillis() - fav.localReadPostDateMillis < RECENT_LOCAL_READ_WS_GUARD_MS
                if (recentlyReadLocally) return@also
                if (!fav.isNew) {
                    fav.isNew = true
                    fav.readState = FavoriteReadState.UNREAD
                }
                fav.unreadPostCount = fav.unreadPostCount.coerceAtLeast(1)
            }
            favoritesCache.saveFavorites(newFavItems)
            return countUnreadFavoriteTopics(newFavItems)
        }

        var newCount: Int
        val newFavItems = favItems.toMutableList()
        val loadedEvent = event.event
        val topicId = loadedEvent.sourceId
        val isRead = loadedEvent.isRead

        if (isRead) {
            newFavItems.find { it.topicId == topicId }?.also {
                if (it.isNew) {
                    it.isNew = false
                    it.readState = FavoriteReadState.READ
                    it.unreadPostCount = 0
                }
            }
            newCount = countUnreadFavoriteTopics(newFavItems)
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
                        if (ev.favoriteInspectorHasUnread() && !localReadDefeatsStaleInspector(fav, ev)) {
                            fav.isNew = true
                            fav.readState = FavoriteReadState.UNREAD
                            val hint = ev.inspectorUnreadHint()
                            fav.unreadPostCount = if (hint > 0) {
                                maxOf(fav.unreadPostCount, hint).coerceAtLeast(1)
                            } else {
                                fav.unreadPostCount.coerceAtLeast(1)
                            }
                            fav.lastUserNick = ev.userNick
                            fav.lastUserId = ev.userId
                            fav.isPin = ev.isImportant
                        } else if (!fav.isNew) {
                            fav.readState = FavoriteReadState.READ
                            fav.unreadPostCount = 0
                        } else if (fav.unreadPostCount < 1) {
                            fav.unreadPostCount = 1
                        }
                    } else {
                        // Inspector omits topics with no tracking row; absence is not "mark read".
                        if (fav.isNew) {
                            fav.unreadPostCount = fav.unreadPostCount.coerceAtLeast(1)
                        }
                    }
                }
                newCount = countUnreadFavoriteTopics(newFavItems)
            } else {
                newFavItems.find { it.topicId == topicId }?.also { fav ->
                    // Не зажигаем обратно тему, которую мы только что прочитали локально, если пост не
                    // новее момента прочтения (устаревший инспектор/лаг). Раньше эта ветка (в отличие от
                    // батч-ветки выше) не проверяла localReadDefeatsStaleInspector — из-за чего свежая
                    // одиночная inspector-нотификация зажигала прочитанную тему («не засчитывает с
                    // первого раза»). Настоящий новый пост даёт timeStamp > localRead → зажжёт как надо.
                    if (fav.lastUserId != authHolder.get().userId &&
                            !localReadDefeatsStaleInspector(fav, loadedEvent)) {
                        fav.isNew = true
                        fav.readState = FavoriteReadState.UNREAD
                    }
                    fav.lastUserNick = loadedEvent.userNick
                    fav.lastUserId = loadedEvent.userId
                    fav.isPin = loadedEvent.isImportant
                    val inspectorRead = loadedEvent.timeStamp > 0 && loadedEvent.lastTimeStamp > 0 &&
                            loadedEvent.timeStamp <= loadedEvent.lastTimeStamp
                    if (inspectorRead) {
                        fav.isNew = false
                        fav.readState = FavoriteReadState.READ
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
                newCount = countUnreadFavoriteTopics(newFavItems)
            }
            if (sorting.key == Sorting.Companion.Key.TITLE) {
                if (sorting.order == Sorting.Companion.Order.ASC) {
                    newFavItems.sortWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.topicTitle.orEmpty() })
                } else {
                    newFavItems.sortWith(compareByDescending(String.CASE_INSENSITIVE_ORDER) { it.topicTitle.orEmpty() })
                }
            }

            if (sorting.key == Sorting.Companion.Key.LAST_POST) {
                newFavItems.find { it.topicId == topicId }?.also {
                    newFavItems.remove(it)
                    if (sorting.order == Sorting.Companion.Order.ASC) {
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

    private fun countUnreadFavoriteTopics(items: List<FavItem>): Int {
        return items.count { it.isNew && !it.isForum && it.topicId > 0 }
    }

    private fun syncFavoritesCounter(items: List<FavItem>, source: String, protectFromHeaderInflation: Boolean = true) {
        if (protectFromHeaderInflation) {
            countersHolder.protectFavoritesFromHeaderInflation()
        }
        countersHolder.set(countersHolder.get().apply {
            favorites = countUnreadFavoriteTopics(items)
        }, source = source)
    }

    /**
     * Синхронизирует read/unread из inspector-merge в Room без полного saveFavorites
     * (полный save из loadCache затирал бы свежий refresh — гонка coroutine при старте вкладки).
     */
    private suspend fun persistInspectorReadStateChanges(merged: List<FavItem>) {
        val currentByFavId = favoritesCache.getItems().associateBy { it.favId }
        for (mergedItem in merged) {
            val current = currentByFavId[mergedItem.favId] ?: continue
            val readStateChanged = current.readState != mergedItem.readState
            val isNewChanged = current.isNew != mergedItem.isNew
            val countChanged = current.unreadPostCount != mergedItem.unreadPostCount
            if (!readStateChanged && !isNewChanged && !countChanged) continue
            current.readState = mergedItem.readState
            current.isNew = mergedItem.isNew
            current.unreadPostCount = mergedItem.unreadPostCount
            favoritesCache.updateItem(current)
        }
    }

    private fun mergeNetworkFavoriteReadStates(
            items: MutableList<FavItem>,
            cachedByFavId: Map<Int, FavItem>,
            inspectorEvents: List<NotificationEvent>,
            networkIsFreshRefresh: Boolean = false
    ) {
        val byTopic = inspectorEvents.associateBy { it.sourceId }
        val inspectorPresent = inspectorEvents.isNotEmpty()
        items.forEach { item ->
            if (item.isForum || item.topicId <= 0) {
                item.unreadPostCount = 0
                item.readState = FavoriteReadState.READ
                item.isNew = false
                return@forEach
            }
            val cached = cachedByFavId[item.favId]
            val ev = byTopic[item.topicId]
            val inspectorUnread = ev?.favoriteInspectorHasUnread() == true
            item.inspectorMarkedUnread = inspectorUnread
            val networkReadState = item.readState
            val result = FavoriteReadStateMerge.merge(
                    network = networkReadState,
                    networkUnreadCount = item.unreadPostCount,
                    networkLegacyIsNew = item.isNew,
                    cached = cached,
                    inspectorUnread = inspectorUnread,
                    inspectorPresent = inspectorPresent && ev != null,
                    hasNewerContentThanCache = hasNewerFavoriteContent(item, cached),
                    networkIsFreshRefresh = networkIsFreshRefresh,
                    inspectorTimeStampSeconds = ev?.timeStamp ?: 0L,
                    localReadTimeSeconds = cached?.localReadPostDateMillis?.let { it / 1000L } ?: 0L,
                    inspectorSnapshotPresent = inspectorPresent
            )
            FavoritesUnreadTrace.inspectorRowMerged(
                    topicId = item.topicId,
                    title = item.topicTitle,
                    inInspector = ev != null,
                    timeStamp = ev?.timeStamp,
                    lastTimeStamp = ev?.lastTimeStamp,
                    msgCount = ev?.msgCount,
                    inspectorUnread = inspectorUnread,
                    htmlReadState = networkReadState.name,
                    mergeReason = result.reason
            )
            FavoriteReadStateMerge.applyTo(item, result)
            // Сетевые items парсер создаёт с нулевыми маркерами локального прочтения, а merge переносит
            // только read-state. Без переноса маркеров из кэша saveFavorites затирал их на КАЖДОМ
            // сетевом рефреше, и защита от повторного зажигания ([localReadDefeatsStaleInspector],
            // «cached_read_over_inspector») слепла: следующий пересчёт по устаревшему (CDN-кэш)
            // инспектору снова метил только что прочитанную тему непрочитанной.
            if (cached != null && item.localReadPostId <= 0) {
                item.localReadPostId = cached.localReadPostId
                item.localReadPostDateMillis = cached.localReadPostDateMillis
            }
            if (item.readState == FavoriteReadState.UNREAD && inspectorUnread) {
                val hint = ev?.inspectorUnreadHint() ?: 0
                if (hint > 0) {
                    item.unreadPostCount = maxOf(item.unreadPostCount, hint)
                }
            }
            if (result.reason == "preserve_cached_unread" ||
                    result.reason == "preserve_cached_unread_over_stale_html" ||
                    result.reason == "cache_unread_over_read" ||
                    result.reason == "inspector_unread_over_read"
            ) {
                FavoritesUnreadTrace.cacheMerged(
                        topicId = item.topicId,
                        title = item.topicTitle,
                        cachedIsUnread = cached?.isNew,
                        cachedReadState = cached?.readState?.name,
                        finalReadState = item.readState.name,
                        finalIsUnread = item.isNew,
                        finalUnreadPostCount = item.unreadPostCount,
                        source = if (result.reason == "preserve_cached_unread") "cache" else "merged",
                        reason = result.reason
                )
            } else if (item.readState == FavoriteReadState.UNREAD) {
                FavoritesUnreadTrace.cacheMerged(
                        topicId = item.topicId,
                        title = item.topicTitle,
                        cachedIsUnread = cached?.isNew,
                        cachedReadState = cached?.readState?.name,
                        finalReadState = item.readState.name,
                        finalIsUnread = true,
                        finalUnreadPostCount = item.unreadPostCount,
                        source = "network",
                        reason = result.reason
                )
            }
            FavoritesUnreadTrace.modelMapped(
                    topicId = item.topicId,
                    title = item.topicTitle,
                    parsedReadState = item.readState.name,
                    parsedIsUnread = item.isNew,
                    unreadPostCount = item.unreadPostCount,
                    source = when (result.reason) {
                        "network_unread" -> "html"
                        "inspector_unread",
                        "inspector_unread_over_stale_html",
                        "inspector_unread_over_read",
                        "inspector_read" -> "inspector"
                        "preserve_cached_unread",
                        "preserve_cached_unread_over_stale_html",
                        "cache_unread_over_read",
                        "cached_read_over_inspector",
                        "cached_read_over_stale_inspector" -> "cache"
                        else -> "merged"
                    }
            )
        }
    }

    private fun hasNewerFavoriteContent(network: FavItem, cached: FavItem?): Boolean {
        if (cached == null) return false
        val networkDate = network.date?.trim().orEmpty()
        val cachedDate = cached.date?.trim().orEmpty()
        if (networkDate.isNotEmpty() && cachedDate.isNotEmpty() && networkDate != cachedDate) return true
        if (network.lastUserId > 0 && cached.lastUserId > 0 && network.lastUserId != cached.lastUserId) return true
        if (!network.lastUserNick.isNullOrBlank() &&
                !cached.lastUserNick.isNullOrBlank() &&
                network.lastUserNick != cached.lastUserNick
        ) return true
        if (network.stParam > cached.stParam) return true
        if (network.pages > cached.pages) return true
        return false
    }

    private fun mergeInspectorIntoCachedItems(
            items: MutableList<FavItem>,
            events: List<NotificationEvent>
    ) {
        if (events.isEmpty()) {
            items.forEach { fav ->
                if (!fav.isForum && fav.readState == FavoriteReadState.UNREAD) {
                    if (fav.unreadPostCount < 1) fav.unreadPostCount = 1
                    fav.isNew = true
                } else if (!fav.isNew) {
                    fav.unreadPostCount = 0
                }
                if (!fav.isForum) {
                    FavoritesUnreadTrace.modelMapped(
                            topicId = fav.topicId,
                            title = fav.topicTitle,
                            parsedReadState = fav.readState.name,
                            parsedIsUnread = fav.isNew,
                            unreadPostCount = fav.unreadPostCount,
                            source = "cache"
                    )
                }
            }
            return
        }
        mergeNetworkFavoriteReadStates(items, items.associateBy { it.favId }, events)
    }

    private fun loadInspectorEvents(): List<NotificationEvent> {
        val events = try {
            eventsApi.getFavoritesEvents()
        } catch (_: Exception) {
            loadSavedInspectorEvents()
        }.ifEmpty {
            loadSavedInspectorEvents()
        }
        val unreadTopics = events.count { it.favoriteInspectorHasUnread() }
        FavoritesUnreadTrace.inspectorSnapshot(
                eventCount = events.size,
                unreadTopicCount = unreadTopics,
        )
        return events
    }

    /** Последний успешный снимок инспектора (EventsRepository / worker) — запасной путь при сбое сети. */
    private fun loadSavedInspectorEvents(): List<NotificationEvent> {
        return try {
            val saved = notificationPreferencesHolder.getDataFavoritesEvents()
            if (saved.isEmpty()) {
                emptyList()
            } else {
                eventsApi.getFavoritesEvents(saved.joinToString("\n"))
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun traceSortedItems(items: List<FavItem>, unreadTop: Boolean) {
        FavoritesUnreadTrace.sortingApplied(
                sortUnreadOnTop = unreadTop,
                unreadCount = items.count { it.isNew && !it.isForum && it.topicId > 0 },
                totalCount = items.size
        )
    }

    private fun List<FavItem>.sortedForFavorites(sorting: Sorting?, unreadTop: Boolean): List<FavItem> {
        if (sorting == null) return this
        return map(::FavItem).toMutableList().also { FavoritesSort.apply(it, sorting, unreadTop) }
    }
}

/**
 * Есть непрочитанное по строке инспектора fav.
 * Если last_read_ts = 0 (ещё не открывали по трекингу) - считаем непрочитанным при ненулевом last_post_ts.
 */
private fun NotificationEvent.favoriteInspectorHasUnread(): Boolean {
    if (timeStamp <= 0) return false
    // last_read_ts = 0 — по трекингу 4pda тему ещё не открывали; непрочитанное при ненулевом last_post_ts.
    if (lastTimeStamp <= 0) return true
    if (timeStamp > lastTimeStamp) return true
    // When last_post_ts == last_read_ts, msgCount (1..199) is the unread hint if timestamps lag.
    if (timeStamp == lastTimeStamp && inspectorUnreadHint() > 0) return true
    return false
}

/**
 * Локальное прочтение побеждает устаревший inspector.
 *
 * Тему только что дочитали до конца (или отправили в неё свой пост) — строка уже READ и взведены
 * [FavItem.localReadPostId]/[FavItem.localReadPostDateMillis], — а серверный last_read_ts ещё не
 * догнал fire-and-forget mark-read GET, поэтому inspector секунду-другую продолжает отдавать тему
 * непрочитанной (last_post_ts > last_read_ts, либо last_read_ts=0 у нетрекнутой темы). Пока inspector
 * не покажет активность НОВЕЕ момента локального прочтения, событийный пересчёт не должен заново
 * зажигать индикатор.
 *
 * Зеркалит защиту [FavoriteReadStateMerge] («cached_read_over_inspector»), которой сетевой refresh
 * пользуется через inspectorTimeStampSeconds/localReadTimeSeconds, а событийный путь
 * ([handleEventTransaction]) раньше не имел — из-за чего синтетический foreground-пересчёт inspector'а
 * (EventsRepository, коммит 6574e8b) заново помечал свежепрочитанную тему непрочитанной.
 */
private fun localReadDefeatsStaleInspector(fav: FavItem, ev: NotificationEvent): Boolean {
    if (fav.isNew || fav.readState != FavoriteReadState.READ) return false
    if (fav.localReadPostId <= 0 || fav.localReadPostDateMillis <= 0L) return false
    val localReadSeconds = fav.localReadPostDateMillis / 1000L
    // ev.timeStamp — last_post_ts (секунды). Пост не новее момента прочтения ⇒ тема прочитана.
    return ev.timeStamp <= localReadSeconds
}

/**
 * Третье поле инспектора (msgCount / posts_num): на 4pda часто совпадает с числом новых постов;
 * очень большие значения трактуем как "всего в теме" и не подмешиваем в бейдж.
 */
private fun NotificationEvent.inspectorUnreadHint(): Int {
    val m = msgCount
    return if (m in 1..INSPECTOR_UNREAD_COUNT_MAX) m else 0
}

data class FavoriteMarkReadEntry(
        val favId: Int,
        val topicId: Int
)

data class FavoriteMarkReadProgress(
        val processed: Int,
        val total: Int,
        val success: Int,
        val failed: Int
)

data class FavoriteMarkReadResult(
        val successTopicIds: Set<Int>,
        val failedTopicIds: Set<Int>
) {
    val successCount: Int get() = successTopicIds.size
    val failCount: Int get() = failedTopicIds.size
}

private const val INSPECTOR_UNREAD_COUNT_MAX = 199

/** Окно после локального markRead, в котором задержанное WS-«new» об уже прочитанном посте игнорируется. */
private const val RECENT_LOCAL_READ_WS_GUARD_MS = 20_000L
