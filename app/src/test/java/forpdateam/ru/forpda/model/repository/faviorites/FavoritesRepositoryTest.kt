package forpdateam.ru.forpda.model.repository.faviorites

import android.content.SharedPreferences
import forpdateam.ru.forpda.entity.app.TabNotification
import forpdateam.ru.forpda.entity.common.AuthData
import forpdateam.ru.forpda.entity.db.favorites.FavItemDao
import forpdateam.ru.forpda.entity.db.favorites.FavItemRoom
import forpdateam.ru.forpda.entity.remote.events.NotificationEvent
import forpdateam.ru.forpda.entity.remote.favorites.FavData
import forpdateam.ru.forpda.entity.remote.favorites.FavItem
import forpdateam.ru.forpda.entity.remote.favorites.FavoriteReadState
import forpdateam.ru.forpda.model.AuthHolder
import forpdateam.ru.forpda.model.CountersHolder
import forpdateam.ru.forpda.model.data.cache.favorites.FavoritesCacheRoom
import forpdateam.ru.forpda.model.data.remote.api.events.NotificationEventsApi
import forpdateam.ru.forpda.model.data.remote.api.favorites.FavoritesApi
import forpdateam.ru.forpda.model.data.remote.api.favorites.Sorting
import forpdateam.ru.forpda.model.preferences.ListsPreferencesHolder
import forpdateam.ru.forpda.model.preferences.NotificationPreferencesHolder
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.Runs
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class FavoritesRepositoryTest {

    @Test
    fun `force refresh bypasses stale request cache and saves network list`() = runTest {
        val favoritesCache = FavoritesCacheRoom(FakeFavItemDao())
        favoritesCache.saveFavorites(
                listOf(
                        FavItem().apply {
                            favId = 1
                            topicId = 42
            topicTitle = "Topic"
                            lastUserNick = "Cached"
                            date = "01.01.2026, 10:00"
                            isNew = false
                        }
                )
        )
        val sorting = Sorting()
        val favoritesApi = mockk<FavoritesApi> {
            every { getFavorites(0, false, sorting, true) } returns favData(
                    FavItem().apply {
                        favId = 1
                        topicId = 42
                        topicTitle = "Topic"
                        lastUserNick = "Network"
                        date = "01.01.2026, 10:05"
                        isNew = true
                        unreadPostCount = 3
                    }
            )
        }
        val eventsApi = mockk<NotificationEventsApi>(relaxed = true) {
            every { invalidateFavoritesInspectorCache() } just Runs
            every { getFavoritesEvents() } returns emptyList()
        }
        val repository = createRepository(favoritesCache, favoritesApi = favoritesApi, eventsApi = eventsApi)

        repository.loadFavorites(0, all = false, sorting = sorting, forceRefresh = true)

        val item = favoritesCache.getItemByTopicId(42)!!
        assertEquals("Network", item.lastUserNick)
        assertEquals("01.01.2026, 10:05", item.date)
        assertEquals(true, item.isNew)
        assertEquals(3, item.unreadPostCount)
    }

    @Test
    fun `force refresh replaces old cached rows atomically`() = runTest {
        val favoritesCache = FavoritesCacheRoom(FakeFavItemDao())
        favoritesCache.saveFavorites(
                listOf(
                        FavItem().apply {
                            favId = 1
                            topicId = 42
            topicTitle = "Old"
                        }
                )
        )
        val sorting = Sorting()
        val favoritesApi = mockk<FavoritesApi> {
            every { getFavorites(0, false, sorting, true) } returns favData(
                    FavItem().apply {
                        favId = 2
                        topicId = 43
                        topicTitle = "Fresh"
                    }
            )
        }
        val eventsApi = mockk<NotificationEventsApi>(relaxed = true) {
            every { invalidateFavoritesInspectorCache() } just Runs
            every { getFavoritesEvents() } returns emptyList()
        }
        val repository = createRepository(favoritesCache, favoritesApi = favoritesApi, eventsApi = eventsApi)

        repository.loadFavorites(0, all = false, sorting = sorting, forceRefresh = true)

        val items = favoritesCache.getItems()
        assertEquals(listOf(43), items.map { it.topicId })
    }

    @Test
    fun `fetchAllFavoritesForSearch returns network list without replacing cache`() = runTest {
        val favoritesCache = FavoritesCacheRoom(FakeFavItemDao())
        favoritesCache.saveFavorites(
                listOf(
                        FavItem().apply {
                            favId = 1
                            topicId = 10
                            topicTitle = "Cached page"
                        }
                )
        )
        val sorting = Sorting()
        val favoritesApi = mockk<FavoritesApi> {
            every { getFavorites(0, true, sorting, false) } returns favData(
                    FavItem().apply {
                        favId = 1
                        topicId = 10
                        topicTitle = "Cached page"
                    },
                    FavItem().apply {
                        favId = 2
                        topicId = 20
                        topicTitle = "Other page"
                    }
            )
        }
        val eventsApi = mockk<NotificationEventsApi>(relaxed = true) {
            every { getFavoritesEvents() } returns emptyList()
        }
        val repository = createRepository(favoritesCache, favoritesApi = favoritesApi, eventsApi = eventsApi)

        val searchItems = repository.fetchAllFavoritesForSearch(sorting)

        assertEquals(listOf(10, 20), searchItems.map { it.topicId })
        assertEquals(listOf(10), favoritesCache.getItems().map { it.topicId })
    }

    @Test
    fun `offline cache read does not re-emit stale snapshot over fresh save`() = runTest {
        val dao = FakeFavItemDao()
        val favoritesCache = FavoritesCacheRoom(dao)
        favoritesCache.saveFavorites(
                listOf(
                        FavItem().apply {
                            favId = 1
                            topicId = 42
                            topicTitle = "Fresh"
                            lastUserNick = "Network"
                            isNew = true
                            unreadPostCount = 4
                        }
                )
        )

        assertEquals(listOf("Network"), favoritesCache.observeItems().value.map { it.lastUserNick })

        dao.replaceRowsWithoutPublishing(
                FavItemRoom(
                        favId = 1,
                        topicId = 42,
                        topicTitle = "Stale",
                        lastUserNick = "Cached",
                        isNew = false,
                        unreadPostCount = 0
                )
        )
        val cachedItems = favoritesCache.getItems()

        assertEquals(listOf("Cached"), cachedItems.map { it.lastUserNick })
        assertEquals(listOf("Network"), favoritesCache.observeItems().value.map { it.lastUserNick })
    }

    @Test
    fun `cached read event clears only matching unread topic once`() = runTest {
        val favoritesCache = FavoritesCacheRoom(FakeFavItemDao())
        favoritesCache.saveFavorites(
                listOf(
                        favoriteTopic(favId = 1, topicId = 42, isNew = true, unreadPostCount = 5),
                        favoriteTopic(favId = 2, topicId = 43, isNew = true, unreadPostCount = 2)
                )
        )
        val repository = createRepository(favoritesCache, initialFavoritesCount = 2)
        val readEvent = tabNotification(event = themeEvent(NotificationEvent.Type.READ, topicId = 42))

        assertEquals(1, repository.handleEvent(readEvent))
        assertEquals(1, repository.handleEvent(readEvent))

        val items = favoritesCache.getItems()
        assertEquals(false, items.single { it.topicId == 42 }.isNew)
        assertEquals(0, items.single { it.topicId == 42 }.unreadPostCount)
        assertEquals(true, items.single { it.topicId == 43 }.isNew)
        assertEquals(2, items.single { it.topicId == 43 }.unreadPostCount)
    }

    @Test
    fun `live tab notification updates one topic badge without using post count as counter`() = runTest {
        val favoritesCache = FavoritesCacheRoom(FakeFavItemDao())
        favoritesCache.saveFavorites(
                listOf(
                        favoriteTopic(favId = 1, topicId = 42, isNew = false),
                        favoriteTopic(favId = 2, topicId = 43, isNew = false)
                )
        )
        val repository = createRepository(favoritesCache)
        val event = tabNotification(
                event = themeEvent(
                        type = NotificationEvent.Type.NEW,
                        topicId = 42,
                        userId = 7,
                        userNick = "NewUser",
                        msgCount = 4,
                        timeStamp = 200,
                        lastTimeStamp = 100
                )
        )

        val counter = repository.handleEvent(event)

        val item = favoritesCache.getItemByTopicId(42)!!
        assertEquals(1, counter)
        assertEquals(true, item.isNew)
        assertEquals(4, item.unreadPostCount)
        assertEquals("NewUser", item.lastUserNick)
    }

    @Test
    fun `websocket new event lights favorites badge without clobbering row metadata`() = runTest {
        val favoritesCache = FavoritesCacheRoom(FakeFavItemDao())
        favoritesCache.saveFavorites(
                listOf(
                        favoriteTopic(favId = 1, topicId = 42, isNew = false),
                        favoriteTopic(favId = 2, topicId = 43, isNew = false)
                )
        )
        val repository = createRepository(favoritesCache)
        // Реальное WS-событие несёт только sourceId/type; ник/важность в нём пусты
        // (NotificationEventsApi.parseWebSocketEvent). Раньше такой NEW игнорировался — бейдж не рос.
        val wsEvent = TabNotification(
                source = NotificationEvent.Source.THEME,
                type = NotificationEvent.Type.NEW,
                event = NotificationEvent(NotificationEvent.Type.NEW, NotificationEvent.Source.THEME).apply {
                    sourceId = 42
                },
                isWebSocket = true
        )

        val counter = repository.handleEvent(wsEvent)

        val item = favoritesCache.getItemByTopicId(42)!!
        assertEquals(1, counter)
        assertEquals(true, item.isNew)
        assertEquals(FavoriteReadState.UNREAD, item.readState)
        assertEquals(true, item.unreadPostCount >= 1)
        // Метаданные строки НЕ затёрты пустыми полями WS-события.
        assertEquals("LastUser", item.lastUserNick)
        assertEquals(1, item.lastUserId)
        // Другая тема не тронута.
        assertEquals(false, favoritesCache.getItemByTopicId(43)!!.isNew)
    }

    @Test
    fun `refresh preserves html unread when inspector reports read`() = runTest {
        val favoritesCache = FavoritesCacheRoom(FakeFavItemDao())
        val sorting = Sorting()
        val favoritesApi = mockk<FavoritesApi> {
            every { getFavorites(0, false, sorting, true) } returns favData(
                    favoriteTopic(favId = 1, topicId = 42, isNew = true, unreadPostCount = 2).apply {
                        isPin = true
                    }
            )
        }
        val eventsApi = mockk<NotificationEventsApi>(relaxed = true) {
            every { invalidateFavoritesInspectorCache() } just Runs
            every { getFavoritesEvents() } returns listOf(
                    themeEvent(
                            type = NotificationEvent.Type.NEW,
                            topicId = 42,
                            msgCount = 1,
                            timeStamp = 100,
                            lastTimeStamp = 200
                    )
            )
        }
        val repository = createRepository(favoritesCache, favoritesApi = favoritesApi, eventsApi = eventsApi)

        repository.loadFavorites(0, all = false, sorting = sorting, forceRefresh = true)

        val item = favoritesCache.getItemByTopicId(42)!!
        assertEquals(true, item.isNew)
        assertEquals(2, item.unreadPostCount)
    }

    @Test
    fun `refresh with inspector hints replaces rows and keeps one row per favorite`() = runTest {
        val favoritesCache = FavoritesCacheRoom(FakeFavItemDao())
        favoritesCache.saveFavorites(
                listOf(
                        favoriteTopic(favId = 1, topicId = 42, isNew = true),
                        favoriteTopic(favId = 2, topicId = 43, isNew = false)
                )
        )
        val sorting = Sorting()
        val favoritesApi = mockk<FavoritesApi> {
            every { getFavorites(0, false, sorting, true) } returns favData(
                    favoriteTopic(favId = 1, topicId = 42, isNew = false),
                    favoriteTopic(favId = 3, topicId = 44, isNew = false)
            )
        }
        val eventsApi = mockk<NotificationEventsApi>(relaxed = true) {
            every { invalidateFavoritesInspectorCache() } just Runs
            every { getFavoritesEvents() } returns listOf(
                    themeEvent(
                            type = NotificationEvent.Type.NEW,
                            topicId = 42,
                            msgCount = 3,
                            timeStamp = 200,
                            lastTimeStamp = 100
                    )
            )
        }
        val repository = createRepository(favoritesCache, favoritesApi = favoritesApi, eventsApi = eventsApi)

        repository.loadFavorites(0, all = false, sorting = sorting, forceRefresh = true)

        val items = favoritesCache.getItems()
        assertEquals(listOf(42, 44), items.map { it.topicId })
        assertEquals(items.size, items.map { it.favId }.toSet().size)
        // Topic 42 was cached UNREAD and the inspector still reports a NEW event for it, so a
        // refresh whose HTML merely lacks the +N marker must NOT downgrade it to READ
        // (preserve_cached_unread_over_stale_html). It only clears once the user opens it or the
        // inspector authoritatively reports read. See FavoriteReadStateMerge + device log 24_06-20-37.
        assertEquals(true, items.single { it.topicId == 42 }.isNew)
        assertEquals(false, items.single { it.topicId == 44 }.isNew)
    }

    @Test
    fun `refresh syncs bottom badge counter from unread favorite topics`() = runTest {
        val favoritesCache = FavoritesCacheRoom(FakeFavItemDao())
        val sorting = Sorting()
        val favoritesApi = mockk<FavoritesApi> {
            every { getFavorites(0, false, sorting, true) } returns favData(
                    favoriteTopic(favId = 1, topicId = 42, isNew = true, unreadPostCount = 5),
                    favoriteTopic(favId = 2, topicId = 43, isNew = true, unreadPostCount = 2),
                    favoriteTopic(favId = 3, topicId = 44, isNew = false),
                    favoriteTopic(favId = 4, topicId = 45, isNew = true).apply { isForum = true }
            )
        }
        val eventsApi = mockk<NotificationEventsApi>(relaxed = true) {
            every { invalidateFavoritesInspectorCache() } just Runs
            every { getFavoritesEvents() } returns emptyList()
        }
        val countersHolder = CountersHolder(counterPreferences())
        val repository = createRepository(
                favoritesCache,
                favoritesApi = favoritesApi,
                eventsApi = eventsApi,
                countersHolder = countersHolder
        )

        repository.loadFavorites(0, all = false, sorting = sorting, forceRefresh = true)

        assertEquals(2, countersHolder.get().favorites)
    }

    @Test
    fun `cache load syncs bottom badge counter after app start`() = runTest {
        val favoritesCache = FavoritesCacheRoom(FakeFavItemDao())
        favoritesCache.saveFavorites(
                listOf(
                        favoriteTopic(favId = 1, topicId = 42, isNew = true),
                        favoriteTopic(favId = 2, topicId = 43, isNew = false)
                )
        )
        val countersHolder = CountersHolder(counterPreferences(initialFavoritesCount = 0))
        val repository = createRepository(favoritesCache, countersHolder = countersHolder)

        repository.loadCache()

        assertEquals(1, countersHolder.get().favorites)
    }

    @Test
    fun `cache load preserves stored unread when inspector reports read`() = runTest {
        val favoritesCache = FavoritesCacheRoom(FakeFavItemDao())
        favoritesCache.saveFavorites(
                listOf(
                        favoriteTopic(favId = 1, topicId = 42, isNew = true, unreadPostCount = 2)
                )
        )
        val eventsApi = mockk<NotificationEventsApi>(relaxed = true) {
            every { getFavoritesEvents() } returns listOf(
                    themeEvent(
                            type = NotificationEvent.Type.NEW,
                            topicId = 42,
                            msgCount = 1,
                            timeStamp = 100,
                            lastTimeStamp = 200
                    )
            )
        }
        val repository = createRepository(favoritesCache, eventsApi = eventsApi)

        repository.loadCache()

        val item = favoritesCache.getItemByTopicId(42)!!
        assertEquals(true, item.isNew)
        assertEquals(2, item.unreadPostCount)
    }

    @Test
    fun `refresh clears unread when html and inspector both report read`() = runTest {
        val favoritesCache = FavoritesCacheRoom(FakeFavItemDao())
        val sorting = Sorting()
        val favoritesApi = mockk<FavoritesApi> {
            every { getFavorites(0, false, sorting, true) } returns favData(
                    favoriteTopic(favId = 1, topicId = 42, isNew = false, unreadPostCount = 0)
            )
        }
        val eventsApi = mockk<NotificationEventsApi>(relaxed = true) {
            every { invalidateFavoritesInspectorCache() } just Runs
            every { getFavoritesEvents() } returns listOf(
                    themeEvent(
                            type = NotificationEvent.Type.NEW,
                            topicId = 42,
                            msgCount = 1,
                            timeStamp = 100,
                            lastTimeStamp = 200
                    )
            )
        }
        val repository = createRepository(favoritesCache, favoritesApi = favoritesApi, eventsApi = eventsApi)

        repository.loadFavorites(0, all = false, sorting = sorting, forceRefresh = true)

        val item = favoritesCache.getItemByTopicId(42)!!
        assertEquals(false, item.isNew)
        assertEquals(0, item.unreadPostCount)
    }

    @Test
    fun `cache load keeps stored read when inspector timestamps say unread`() = runTest {
        val favoritesCache = FavoritesCacheRoom(FakeFavItemDao())
        favoritesCache.saveFavorites(
                listOf(
                        favoriteTopic(favId = 1, topicId = 42, isNew = false),
                        favoriteTopic(favId = 2, topicId = 43, isNew = false)
                )
        )
        val eventsApi = mockk<NotificationEventsApi>(relaxed = true) {
            every { getFavoritesEvents() } returns listOf(
                    themeEvent(
                            type = NotificationEvent.Type.NEW,
                            topicId = 42,
                            msgCount = 3,
                            timeStamp = 200,
                            lastTimeStamp = 100
                    )
            )
        }
        val repository = createRepository(favoritesCache, eventsApi = eventsApi)

        repository.loadCache()

        val item = favoritesCache.getItemByTopicId(42)!!
        assertEquals(false, item.isNew)
        assertEquals(0, item.unreadPostCount)
    }

    @Test
    fun `refresh keeps html read when inspector msgCount hints despite equal timestamps`() = runTest {
        val favoritesCache = FavoritesCacheRoom(FakeFavItemDao())
        val sorting = Sorting()
        val favoritesApi = mockk<FavoritesApi> {
            every { getFavorites(20, false, sorting, false) } returns favData(
                    favoriteTopic(favId = 10, topicId = 900, isNew = false)
            )
        }
        val eventsApi = mockk<NotificationEventsApi>(relaxed = true) {
            every { getFavoritesEvents() } returns listOf(
                    themeEvent(
                            type = NotificationEvent.Type.NEW,
                            topicId = 900,
                            msgCount = 2,
                            timeStamp = 200,
                            lastTimeStamp = 200
                    )
            )
        }
        val repository = createRepository(favoritesCache, favoritesApi = favoritesApi, eventsApi = eventsApi)

        repository.loadFavorites(20, all = false, sorting = sorting)

        val item = favoritesCache.getItemByTopicId(900)!!
        assertEquals(false, item.isNew)
        assertEquals(0, item.unreadPostCount)
    }

    @Test
    fun `refresh keeps html read when inspector timestamps say unread`() = runTest {
        val favoritesCache = FavoritesCacheRoom(FakeFavItemDao())
        val sorting = Sorting()
        val favoritesApi = mockk<FavoritesApi> {
            every { getFavorites(0, false, sorting, true) } returns favData(
                    favoriteTopic(favId = 1, topicId = 461675, isNew = false, unreadPostCount = 0)
            )
        }
        val eventsApi = mockk<NotificationEventsApi>(relaxed = true) {
            every { invalidateFavoritesInspectorCache() } just Runs
            every { getFavoritesEvents() } returns listOf(
                    themeEvent(
                            type = NotificationEvent.Type.NEW,
                            topicId = 461675,
                            msgCount = 371310,
                            timeStamp = 200,
                            lastTimeStamp = 100
                    )
            )
        }
        val repository = createRepository(favoritesCache, favoritesApi = favoritesApi, eventsApi = eventsApi)

        repository.loadFavorites(0, all = false, sorting = sorting, forceRefresh = true)

        val item = favoritesCache.getItemByTopicId(461675)!!
        assertEquals(false, item.isNew)
        assertEquals(FavoriteReadState.READ, item.readState)
        assertEquals(0, item.unreadPostCount)
    }

    @Test
    fun `paginated refresh keeps html unread when topic missing from inspector snapshot`() = runTest {
        val favoritesCache = FavoritesCacheRoom(FakeFavItemDao())
        val sorting = Sorting()
        val favoritesApi = mockk<FavoritesApi> {
            every { getFavorites(20, false, sorting, false) } returns favData(
                    favoriteTopic(favId = 10, topicId = 901, isNew = true, unreadPostCount = 1)
            )
        }
        val eventsApi = mockk<NotificationEventsApi>(relaxed = true) {
            every { getFavoritesEvents() } returns listOf(
                    themeEvent(
                            type = NotificationEvent.Type.NEW,
                            topicId = 999,
                            msgCount = 1,
                            timeStamp = 300,
                            lastTimeStamp = 100
                    )
            )
        }
        val repository = createRepository(favoritesCache, favoritesApi = favoritesApi, eventsApi = eventsApi)

        repository.loadFavorites(20, all = false, sorting = sorting)

        val item = favoritesCache.getItemByTopicId(901)!!
        assertEquals(true, item.isNew)
        assertEquals(1, item.unreadPostCount)
    }

    @Test
    fun `cache load keeps stored read when inspector lastTimeStamp is zero`() = runTest {
        val favoritesCache = FavoritesCacheRoom(FakeFavItemDao())
        favoritesCache.saveFavorites(
                listOf(
                        favoriteTopic(favId = 1, topicId = 42, isNew = false)
                )
        )
        val eventsApi = mockk<NotificationEventsApi>(relaxed = true) {
            every { getFavoritesEvents() } returns listOf(
                    themeEvent(
                            type = NotificationEvent.Type.NEW,
                            topicId = 42,
                            msgCount = 2,
                            timeStamp = 200,
                            lastTimeStamp = 0
                    )
            )
        }
        val repository = createRepository(favoritesCache, eventsApi = eventsApi)

        repository.loadCache()

        val item = favoritesCache.getItemByTopicId(42)!!
        assertEquals(false, item.isNew)
        assertEquals(0, item.unreadPostCount)
    }

    @Test
    fun `refresh keeps html read when saved inspector says unread`() = runTest {
        val favoritesCache = FavoritesCacheRoom(FakeFavItemDao())
        val sorting = Sorting()
        val favoritesApi = mockk<FavoritesApi> {
            every { getFavorites(0, false, sorting, true) } returns favData(
                    favoriteTopic(favId = 1, topicId = 42, isNew = false)
            )
        }
        val inspectorLine = """42 "Topic 42" 3 7 "User" 200 100 0"""
        val eventsApi = mockk<NotificationEventsApi>(relaxed = true) {
            every { invalidateFavoritesInspectorCache() } just Runs
            every { getFavoritesEvents() } throws RuntimeException("network down")
            every { getFavoritesEvents(inspectorLine) } returns listOf(
                    themeEvent(
                            type = NotificationEvent.Type.NEW,
                            topicId = 42,
                            msgCount = 3,
                            timeStamp = 200,
                            lastTimeStamp = 100
                    )
            )
        }
        val notificationPreferencesHolder = mockk<NotificationPreferencesHolder>(relaxed = true) {
            every { getFavLiveTab() } returns true
            every { getDataFavoritesEvents() } returns setOf(inspectorLine)
        }
        val repository = FavoritesRepository(
                favoritesApi = favoritesApi,
                favoritesCache = favoritesCache,
                authHolder = AuthHolder(counterPreferences()).apply { set(AuthData()) },
                countersHolder = CountersHolder(counterPreferences()),
                listsPreferencesHolder = mockk(relaxed = true),
                notificationPreferencesHolder = notificationPreferencesHolder,
                eventsApi = eventsApi,
                readBoundaryStore = mockk(relaxed = true)
        )

        repository.loadFavorites(0, all = false, sorting = sorting, forceRefresh = true)

        val item = favoritesCache.getItemByTopicId(42)!!
        assertEquals(false, item.isNew)
        assertEquals(0, item.unreadPostCount)
    }

    @Test
    fun `cache read server unread wins on force refresh`() = runTest {
        val favoritesCache = FavoritesCacheRoom(FakeFavItemDao())
        favoritesCache.saveFavorites(
                listOf(
                        favoriteTopic(favId = 1, topicId = 42, isNew = false)
                )
        )
        val sorting = Sorting()
        val favoritesApi = mockk<FavoritesApi> {
            every { getFavorites(0, false, sorting, true) } returns favData(
                    favoriteTopic(favId = 1, topicId = 42, isNew = true, unreadPostCount = 3)
            )
        }
        val eventsApi = mockk<NotificationEventsApi>(relaxed = true) {
            every { invalidateFavoritesInspectorCache() } just Runs
            every { getFavoritesEvents() } returns emptyList()
        }
        val repository = createRepository(favoritesCache, favoritesApi = favoritesApi, eventsApi = eventsApi)

        repository.loadFavorites(0, all = false, sorting = sorting, forceRefresh = true)

        val item = favoritesCache.getItemByTopicId(42)!!
        assertEquals(true, item.isNew)
        assertEquals(3, item.unreadPostCount)
    }

    @Test
    fun `unknown unread state does not become false silently when cache was unread`() = runTest {
        val favoritesCache = FavoritesCacheRoom(FakeFavItemDao())
        favoritesCache.saveFavorites(
                listOf(
                        favoriteTopic(favId = 1, topicId = 42, isNew = true, unreadPostCount = 2)
                )
        )
        val sorting = Sorting()
        val favoritesApi = mockk<FavoritesApi> {
            every { getFavorites(0, false, sorting, true) } returns favData(
                    favoriteTopic(favId = 1, topicId = 42, isNew = false)
            )
        }
        val eventsApi = mockk<NotificationEventsApi>(relaxed = true) {
            every { invalidateFavoritesInspectorCache() } just Runs
            every { getFavoritesEvents() } returns emptyList()
        }
        val repository = createRepository(favoritesCache, favoritesApi = favoritesApi, eventsApi = eventsApi)

        repository.loadFavorites(0, all = false, sorting = sorting, forceRefresh = true)

        val item = favoritesCache.getItemByTopicId(42)!!
        assertEquals(true, item.isNew)
        assertEquals(2, item.unreadPostCount)
    }

    @Test
    fun `refresh preserves cached unread when parser and inspector miss markers`() = runTest {
        val favoritesCache = FavoritesCacheRoom(FakeFavItemDao())
        favoritesCache.saveFavorites(
                listOf(
                        favoriteTopic(favId = 1, topicId = 42, isNew = true, unreadPostCount = 4),
                        favoriteTopic(favId = 2, topicId = 43, isNew = false)
                )
        )
        val sorting = Sorting()
        val favoritesApi = mockk<FavoritesApi> {
            every { getFavorites(0, false, sorting, true) } returns favData(
                    favoriteTopic(favId = 1, topicId = 42, isNew = false),
                    favoriteTopic(favId = 2, topicId = 43, isNew = false)
            )
        }
        val eventsApi = mockk<NotificationEventsApi>(relaxed = true) {
            every { invalidateFavoritesInspectorCache() } just Runs
            every { getFavoritesEvents() } returns emptyList()
        }
        val repository = createRepository(favoritesCache, favoritesApi = favoritesApi, eventsApi = eventsApi)

        repository.loadFavorites(0, all = false, sorting = sorting, forceRefresh = true)

        val item = favoritesCache.getItemByTopicId(42)!!
        assertEquals(true, item.isNew)
        assertEquals(4, item.unreadPostCount)
    }

    @Test
    fun `mark read clears bottom badge counter for matching favorite topic`() = runTest {
        val favoritesCache = FavoritesCacheRoom(FakeFavItemDao())
        favoritesCache.saveFavorites(
                listOf(
                        favoriteTopic(favId = 1, topicId = 42, isNew = true, unreadPostCount = 5),
                        favoriteTopic(favId = 2, topicId = 43, isNew = true, unreadPostCount = 2)
                )
        )
        val countersHolder = CountersHolder(counterPreferences(initialFavoritesCount = 2))
        val repository = createRepository(favoritesCache, countersHolder = countersHolder)

        repository.markRead(42)

        assertEquals(1, countersHolder.get().favorites)
    }

    @Test
    fun `mark read stores local sentinel and clears stale unread hints for topic 1106099`() = runTest {
        val favoritesCache = FavoritesCacheRoom(FakeFavItemDao())
        favoritesCache.saveFavorites(
                listOf(
                        favoriteTopic(favId = 15776864, topicId = 1106099, isNew = true, unreadPostCount = 1).apply {
                            listingHref = "https://4pda.to/forum/index.php?showtopic=1106099&view=getnewpost"
                            inspectorMarkedUnread = true
                        }
                )
        )
        val repository = createRepository(favoritesCache)

        repository.markRead(1106099)

        val item = favoritesCache.getItemByTopicId(1106099)!!
        assertEquals(false, item.isNew)
        assertEquals(FavoriteReadState.READ, item.readState)
        assertEquals(0, item.unreadPostCount)
        assertEquals(1106099, item.localReadPostId)
        assertEquals(null, item.listingHref)
        assertEquals(false, item.inspectorMarkedUnread)
    }

    @Test
    fun `local read defeats stale inspector unread when topic 1106099 content did not advance`() = runTest {
        val cached = favoriteTopic(favId = 15776864, topicId = 1106099, isNew = false).apply {
            localReadPostId = 1106099
            localReadPostDateMillis = 1_781_260_593_000L
            lastUserNick = "LastUser"
            date = "Сегодня, 11:56"
            stParam = 2760
            pages = 139
        }

        val result = FavoriteReadStateMerge.merge(
                network = FavoriteReadState.READ,
                networkUnreadCount = 0,
                networkLegacyIsNew = false,
                cached = cached,
                inspectorUnread = true,
                inspectorPresent = true,
                hasNewerContentThanCache = false
        )

        assertEquals(FavoriteReadState.READ, result.readState)
        assertEquals(0, result.unreadPostCount)
        assertEquals("cached_read_over_inspector", result.reason)
    }

    @Test
    fun `synthetic inspector recompute does not relight a just-read favorite`() = runTest {
        // Регрессия коммита 6574e8b: на резюме/foreground публикуется синтетическая inspector-
        // TabNotification (isWebSocket=false, sourceId=-1, loadedEvents=свежий inspector). Тему
        // только что дочитали до конца (локально READ + localRead-сентинел), но серверный
        // last_read_ts ещё не догнал mark-read GET, поэтому inspector отдаёт её непрочитанной
        // (last_post_ts=200 > last_read_ts=100). Событийный пересчёт НЕ должен зажигать её заново.
        val favoritesCache = FavoritesCacheRoom(FakeFavItemDao())
        favoritesCache.saveFavorites(
                listOf(
                        favoriteTopic(favId = 1, topicId = 42, isNew = false).apply {
                            localReadPostId = 42
                            localReadPostDateMillis = 300_000L // 300s > last_post_ts=200s
                        },
                        favoriteTopic(favId = 2, topicId = 43, isNew = false)
                )
        )
        val repository = createRepository(favoritesCache)
        val syntheticInspector = TabNotification(
                source = NotificationEvent.Source.THEME,
                type = NotificationEvent.Type.NEW,
                event = NotificationEvent(NotificationEvent.Type.NEW, NotificationEvent.Source.THEME).apply {
                    sourceId = -1
                },
                isWebSocket = false,
                loadedEvents = listOf(
                        themeEvent(
                                type = NotificationEvent.Type.NEW,
                                topicId = 42,
                                msgCount = 1,
                                timeStamp = 200,
                                lastTimeStamp = 100
                        )
                )
        )

        val counter = repository.handleEvent(syntheticInspector)

        val item = favoritesCache.getItemByTopicId(42)!!
        assertEquals(false, item.isNew)
        assertEquals(FavoriteReadState.READ, item.readState)
        assertEquals(0, item.unreadPostCount)
        assertEquals(0, counter)
    }

    @Test
    fun `network refresh preserves local read markers so stale inspector cannot relight`() = runTest {
        // Жалоба «тема не отмечается прочитанной, пока не зайти второй раз»: сетевые items парсера
        // приходят с нулевыми localRead*-маркерами, и saveFavorites затирал их на каждом рефреше.
        // После затирания защита localReadDefeatsStaleInspector слепла, и следующий синтетический
        // пересчёт по устаревшему (CDN-кэш) инспектору заново зажигал только что прочитанную тему.
        val favoritesCache = FavoritesCacheRoom(FakeFavItemDao())
        favoritesCache.saveFavorites(
                listOf(
                        favoriteTopic(favId = 1, topicId = 42, isNew = false).apply {
                            localReadPostId = 42
                            localReadPostDateMillis = 300_000L // прочитана локально в t=300s
                        }
                )
        )
        val sorting = Sorting()
        val favoritesApi = mockk<FavoritesApi> {
            // Свежая fav-страница уже отдаёт тему без bold/+N (сервер обработал mark-read GET).
            every { getFavorites(0, false, sorting, true) } returns favData(
                    favoriteTopic(favId = 1, topicId = 42, isNew = false, readState = FavoriteReadState.READ)
            )
        }
        val eventsApi = mockk<NotificationEventsApi>(relaxed = true) {
            every { invalidateFavoritesInspectorCache() } just Runs
            every { getFavoritesEvents() } returns emptyList()
        }
        val repository = createRepository(favoritesCache, favoritesApi = favoritesApi, eventsApi = eventsApi)

        repository.loadFavorites(0, all = false, sorting = sorting, forceRefresh = true)

        val refreshed = favoritesCache.getItemByTopicId(42)!!
        assertEquals(42, refreshed.localReadPostId)
        assertEquals(300_000L, refreshed.localReadPostDateMillis)

        // Устаревший инспектор (last_post_ts=200 < localRead=300s) не должен зажечь тему заново.
        val staleInspector = TabNotification(
                source = NotificationEvent.Source.THEME,
                type = NotificationEvent.Type.NEW,
                event = NotificationEvent(NotificationEvent.Type.NEW, NotificationEvent.Source.THEME).apply {
                    sourceId = -1
                },
                isWebSocket = false,
                loadedEvents = listOf(
                        themeEvent(
                                type = NotificationEvent.Type.NEW,
                                topicId = 42,
                                msgCount = 1,
                                timeStamp = 200,
                                lastTimeStamp = 100
                        )
                )
        )
        val counter = repository.handleEvent(staleInspector)

        val item = favoritesCache.getItemByTopicId(42)!!
        assertEquals(false, item.isNew)
        assertEquals(FavoriteReadState.READ, item.readState)
        assertEquals(0, counter)
    }

    @Test
    fun `inspector recompute still relights a read favorite on genuinely newer activity`() = runTest {
        // Обратная сторона защиты: если inspector показывает пост НОВЕЕ момента локального прочтения
        // (last_post_ts=400 > localRead=300s), тема реально получила новое сообщение — зажигаем.
        val favoritesCache = FavoritesCacheRoom(FakeFavItemDao())
        favoritesCache.saveFavorites(
                listOf(
                        favoriteTopic(favId = 1, topicId = 42, isNew = false).apply {
                            localReadPostId = 42
                            localReadPostDateMillis = 300_000L // 300s < last_post_ts=400s
                        }
                )
        )
        val repository = createRepository(favoritesCache)
        val syntheticInspector = TabNotification(
                source = NotificationEvent.Source.THEME,
                type = NotificationEvent.Type.NEW,
                event = NotificationEvent(NotificationEvent.Type.NEW, NotificationEvent.Source.THEME).apply {
                    sourceId = -1
                },
                isWebSocket = false,
                loadedEvents = listOf(
                        themeEvent(
                                type = NotificationEvent.Type.NEW,
                                topicId = 42,
                                msgCount = 2,
                                timeStamp = 400,
                                lastTimeStamp = 100
                        )
                )
        )

        val counter = repository.handleEvent(syntheticInspector)

        val item = favoritesCache.getItemByTopicId(42)!!
        assertEquals(true, item.isNew)
        assertEquals(FavoriteReadState.UNREAD, item.readState)
        assertEquals(1, counter)
    }

    @Test
    fun `stale cache read does not override fresh unread state`() = runTest {
        val dao = FakeFavItemDao()
        val favoritesCache = FavoritesCacheRoom(dao)
        favoritesCache.saveFavorites(
                listOf(
                        favoriteTopic(favId = 1, topicId = 42, isNew = true, unreadPostCount = 4)
                )
        )

        dao.replaceRowsWithoutPublishing(
                FavItemRoom(
                        favId = 1,
                        topicId = 42,
                        topicTitle = "Stale",
                        isNew = false,
                        unreadPostCount = 0
                )
        )
        favoritesCache.getItems()

        val observed = favoritesCache.observeItems().value.single()
        assertEquals(true, observed.isNew)
        assertEquals(4, observed.unreadPostCount)
    }

    @Test
    fun `last post descending puts today before yesterday`() = runTest {
        val items = mutableListOf(
                favoriteTopic(favId = 1, topicId = 101, isNew = false).apply {
                    topicTitle = "Yesterday"
                    date = "Вчера, 19:47"
                },
                favoriteTopic(favId = 2, topicId = 102, isNew = false).apply {
                    topicTitle = "Today"
                    date = "Сегодня, 10:36"
                }
        )

        forpdateam.ru.forpda.model.data.remote.api.favorites.FavoritesSort.apply(
                items,
                Sorting(Sorting.Companion.Key.LAST_POST, Sorting.Companion.Order.DESC),
                unreadTop = true
        )

        assertEquals(listOf(102, 101), items.map { it.topicId })
    }

    @Test
    fun `last post descending ignores unread state when unread top is disabled`() = runTest {
        val items = mutableListOf(
                favoriteTopic(favId = 1, topicId = 101, isNew = false).apply {
                    topicTitle = "Read today"
                    date = "Сегодня, 10:36"
                },
                favoriteTopic(favId = 2, topicId = 102, isNew = true).apply {
                    topicTitle = "Unread yesterday"
                    date = "Вчера, 19:47"
                }
        )

        forpdateam.ru.forpda.model.data.remote.api.favorites.FavoritesSort.apply(
                items,
                Sorting(Sorting.Companion.Key.LAST_POST, Sorting.Companion.Order.DESC),
                unreadTop = false
        )

        assertEquals(listOf(101, 102), items.map { it.topicId })
    }

    @Test
    fun `last post ascending puts yesterday before today`() = runTest {
        val items = mutableListOf(
                favoriteTopic(favId = 1, topicId = 101, isNew = false).apply {
                    topicTitle = "Today"
                    date = "Сегодня, 10:36"
                },
                favoriteTopic(favId = 2, topicId = 102, isNew = false).apply {
                    topicTitle = "Yesterday"
                    date = "Вчера, 19:47"
                }
        )

        forpdateam.ru.forpda.model.data.remote.api.favorites.FavoritesSort.apply(
                items,
                Sorting(Sorting.Companion.Key.LAST_POST, Sorting.Companion.Order.ASC)
        )

        assertEquals(listOf(102, 101), items.map { it.topicId })
    }

    @Test
    fun `last post sorting keeps pinned and normal items sortable by adapter sections`() = runTest {
        val items = mutableListOf(
                favoriteTopic(favId = 1, topicId = 101, isNew = false).apply {
                    topicTitle = "Pinned old"
                    date = "Вчера, 19:47"
                    isPin = true
                },
                favoriteTopic(favId = 2, topicId = 102, isNew = false).apply {
                    topicTitle = "Forum old"
                    date = "Вчера, 20:47"
                    isForum = true
                },
                favoriteTopic(favId = 3, topicId = 103, isNew = false).apply {
                    topicTitle = "Pinned new"
                    date = "Сегодня, 10:36"
                    isPin = true
                },
                favoriteTopic(favId = 4, topicId = 104, isNew = false).apply {
                    topicTitle = "Normal new"
                    date = "Сегодня, 11:36"
                    isNew = true
                }
        )

        forpdateam.ru.forpda.model.data.remote.api.favorites.FavoritesSort.apply(
                items,
                Sorting(Sorting.Companion.Key.LAST_POST, Sorting.Companion.Order.DESC),
                unreadTop = true
        )

        val pinnedSection = items.filter { it.isPin }
        val normalSection = items.filterNot { it.isPin }
        assertEquals(listOf(103, 101), pinnedSection.map { it.topicId })
        assertEquals(listOf(104, 102), normalSection.map { it.topicId })
        assertEquals(items.size, items.map { it.favId }.toSet().size)
    }

    @Test
    fun `title ascending sorts topics and forums by name`() = runTest {
        val items = mutableListOf(
                favoriteTopic(favId = 1, topicId = 101, isNew = false).apply {
                    topicTitle = "Beta"
                    date = "Сегодня, 10:36"
                },
                favoriteTopic(favId = 2, topicId = 102, isNew = false).apply {
                    topicTitle = "alpha"
                    date = "Вчера, 19:47"
                    isForum = true
                },
                favoriteTopic(favId = 3, topicId = 103, isNew = false).apply {
                    topicTitle = "Gamma"
                    date = "15.05.2026, 12:00"
                }
        )

        forpdateam.ru.forpda.model.data.remote.api.favorites.FavoritesSort.apply(
                items,
                Sorting(Sorting.Companion.Key.TITLE, Sorting.Companion.Order.ASC)
        )

        assertEquals(listOf(102, 101, 103), items.map { it.topicId })
    }

    @Test
    fun `title descending reverses title order`() = runTest {
        val items = mutableListOf(
                favoriteTopic(favId = 1, topicId = 101, isNew = false).apply {
                    topicTitle = "Beta"
                    date = "Сегодня, 10:36"
                },
                favoriteTopic(favId = 2, topicId = 102, isNew = false).apply {
                    topicTitle = "alpha"
                    date = "Вчера, 19:47"
                },
                favoriteTopic(favId = 3, topicId = 103, isNew = false).apply {
                    topicTitle = "Gamma"
                    date = "15.05.2026, 12:00"
                }
        )

        forpdateam.ru.forpda.model.data.remote.api.favorites.FavoritesSort.apply(
                items,
                Sorting(Sorting.Companion.Key.TITLE, Sorting.Companion.Order.DESC)
        )

        assertEquals(listOf(103, 101, 102), items.map { it.topicId })
    }

    @Test
    fun `last post descending keeps unread normal topics before read normal topics`() = runTest {
        val items = mutableListOf(
                favoriteTopic(favId = 1, topicId = 101, isNew = false).apply {
                    topicTitle = "Read today"
                    date = "Сегодня, 10:36"
                },
                favoriteTopic(favId = 2, topicId = 102, isNew = true).apply {
                    topicTitle = "Unread yesterday"
                    date = "Вчера, 19:47"
                }
        )

        forpdateam.ru.forpda.model.data.remote.api.favorites.FavoritesSort.apply(
                items,
                Sorting(Sorting.Companion.Key.LAST_POST, Sorting.Companion.Order.DESC),
                unreadTop = true
        )

        assertEquals(listOf(102, 101), items.map { it.topicId })
    }

    @Test
    fun `observed favorites apply latest post descending after cache save`() = runTest {
        val favoritesCache = FavoritesCacheRoom(FakeFavItemDao())
        val repository = createRepository(favoritesCache)
        val sorting = Sorting(Sorting.Companion.Key.LAST_POST, Sorting.Companion.Order.DESC)
        favoritesCache.saveFavorites(
                listOf(
                        favoriteTopic(favId = 1, topicId = 101, isNew = false).apply {
                            topicTitle = "WPS Office + PDF"
                            date = "Сегодня, 10:36"
                        },
                        favoriteTopic(favId = 2, topicId = 102, isNew = false).apply {
                            topicTitle = "Обсуждение модулей для Magisk"
                            date = "Сегодня, 12:46"
                        },
                        favoriteTopic(favId = 3, topicId = 103, isNew = false).apply {
                            topicTitle = "eWeather HD - Прогноз погоды и Барометр"
                            date = "Вчера, 21:48"
                        },
                        favoriteTopic(favId = 4, topicId = 104, isNew = false).apply {
                            topicTitle = "App&Game 4PDA"
                            date = "Сегодня, 05:29"
                        },
                        favoriteTopic(favId = 5, topicId = 105, isNew = true).apply {
                            topicTitle = "Клуб Mod APK"
                            date = "Сегодня, 12:49"
                        },
                        favoriteTopic(favId = 6, topicId = 106, isNew = false).apply {
                            topicTitle = "Выбор сетевого оборудования"
                            date = "Сегодня, 12:13"
                        },
                        favoriteTopic(favId = 7, topicId = 107, isNew = false).apply {
                            topicTitle = "Kinopub [Android]"
                            date = "Сегодня, 11:04"
                        }
                )
        )

        val observed = repository.observeItems(sorting).first()

        assertEquals(listOf(105, 102, 106, 107, 101, 104, 103), observed.map { it.topicId })
    }

    @Test
    fun `observed latest post sorting matches screenshot unread group before read group`() = runTest {
        val favoritesCache = FavoritesCacheRoom(FakeFavItemDao())
        val repository = createRepository(favoritesCache)
        val sorting = Sorting(Sorting.Companion.Key.LAST_POST, Sorting.Companion.Order.DESC)
        favoritesCache.saveFavorites(
                listOf(
                        favoriteTopic(favId = 1, topicId = 101, isNew = true).apply {
                            topicTitle = "Обсуждение модулей для Magisk"
                            date = "Сегодня, 12:46"
                        },
                        favoriteTopic(favId = 2, topicId = 102, isNew = false).apply {
                            topicTitle = "Apple MacBook"
                            date = "Сегодня, 12:13"
                        },
                        favoriteTopic(favId = 3, topicId = 103, isNew = false).apply {
                            topicTitle = "Энергопотребление"
                            date = "Сегодня, 11:31"
                        },
                        favoriteTopic(favId = 4, topicId = 104, isNew = false).apply {
                            topicTitle = "Adguard"
                            date = "Сегодня, 11:25"
                        },
                        favoriteTopic(favId = 5, topicId = 105, isNew = false).apply {
                            topicTitle = "Kinopub"
                            date = "Сегодня, 11:04"
                        },
                        favoriteTopic(favId = 6, topicId = 106, isNew = false).apply {
                            topicTitle = "KernelSU"
                            date = "Сегодня, 10:55"
                        },
                        favoriteTopic(favId = 7, topicId = 107, isNew = true).apply {
                            topicTitle = "WPS Office + PDF"
                            date = "Сегодня, 10:36"
                        },
                        favoriteTopic(favId = 8, topicId = 108, isNew = true).apply {
                            topicTitle = "Обход блокировок WhatsApp и Telegram"
                            date = "Сегодня, 09:33"
                        }
                )
        )

        val observed = repository.observeItems(sorting, unreadTop = true).first()

        assertEquals(listOf(101, 107, 108, 102, 103, 104, 105, 106), observed.map { it.topicId })
    }

    @Test
    fun `cache load publishes rows so unread top toggle re-sorts observed favorites`() = runTest {
        val dao = FakeFavItemDao()
        val favoritesCache = FavoritesCacheRoom(dao)
        val repository = createRepository(favoritesCache)
        val sorting = Sorting(Sorting.Companion.Key.LAST_POST, Sorting.Companion.Order.DESC)
        dao.replaceRowsWithoutPublishing(
                FavItemRoom(
                        favId = 1,
                        topicId = 101,
                        topicTitle = "Read today",
                        date = "Сегодня, 10:36",
                        isNew = false
                ),
                FavItemRoom(
                        favId = 2,
                        topicId = 102,
                        topicTitle = "Unread yesterday",
                        date = "Вчера, 19:47",
                        isNew = true
                )
        )

        repository.loadCache(sorting, unreadTop = false)
        val unreadTopOff = repository.observeItems(sorting, unreadTop = false).first()
        val unreadTopOn = repository.observeItems(sorting, unreadTop = true).first()

        assertEquals(listOf(101, 102), unreadTopOff.map { it.topicId })
        assertEquals(listOf(102, 101), unreadTopOn.map { it.topicId })
    }

    private fun createRepository(
            favoritesCache: FavoritesCacheRoom,
            favoritesApi: FavoritesApi = mockk<FavoritesApi>(relaxed = true),
            eventsApi: NotificationEventsApi = mockk<NotificationEventsApi>(relaxed = true),
            initialFavoritesCount: Int = 0,
            countersHolder: CountersHolder = CountersHolder(counterPreferences(initialFavoritesCount))
    ): FavoritesRepository {
        val preferences = counterPreferences(initialFavoritesCount)
        val notificationPreferencesHolder = mockk<NotificationPreferencesHolder>(relaxed = true) {
            every { getFavLiveTab() } returns true
        }
        return FavoritesRepository(
                favoritesApi = favoritesApi,
                favoritesCache = favoritesCache,
                authHolder = AuthHolder(preferences).apply { set(AuthData()) },
                countersHolder = countersHolder,
                listsPreferencesHolder = mockk<ListsPreferencesHolder>(relaxed = true),
                notificationPreferencesHolder = notificationPreferencesHolder,
                eventsApi = eventsApi,
                readBoundaryStore = mockk(relaxed = true)
        )
    }

    private fun counterPreferences(initialFavoritesCount: Int = 0): SharedPreferences {
        val values = mutableMapOf<String, Any>("counter_favorites" to initialFavoritesCount)
        val editor = mockk<SharedPreferences.Editor>(relaxed = true)
        every { editor.putInt(any(), any()) } answers {
            values[firstArg()] = secondArg<Int>()
            editor
        }
        every { editor.apply() } just Runs
        return mockk(relaxed = true) {
            every { getInt(any(), any()) } answers { values[firstArg()] as? Int ?: secondArg() }
            every { getString(any(), any()) } answers { secondArg() }
            every { edit() } returns editor
        }
    }

    private fun favData(vararg items: FavItem) = FavData().apply {
        this.items.addAll(items)
    }

    private fun favoriteTopic(
            favId: Int,
            topicId: Int,
            isNew: Boolean,
            unreadPostCount: Int = 0,
            readState: FavoriteReadState? = null
    ) = FavItem().apply {
        this.favId = favId
        this.topicId = topicId
        topicTitle = "Topic $topicId"
        lastUserId = 1
        lastUserNick = "LastUser"
        date = "01.01.2026, 10:00"
        this.isNew = isNew
        this.readState = readState ?: when {
            isNew -> FavoriteReadState.UNREAD
            else -> FavoriteReadState.READ
        }
        this.unreadPostCount = unreadPostCount
    }

    private fun themeEvent(
            type: NotificationEvent.Type,
            topicId: Int,
            userId: Int = 2,
            userNick: String = "User",
            msgCount: Int = 0,
            timeStamp: Long = 0,
            lastTimeStamp: Long = 0
    ) = NotificationEvent(type, NotificationEvent.Source.THEME).apply {
        sourceId = topicId
        this.userId = userId
        this.userNick = userNick
        this.msgCount = msgCount
        this.timeStamp = timeStamp
        this.lastTimeStamp = lastTimeStamp
    }

    private fun tabNotification(
            event: NotificationEvent,
            loadedEvents: List<NotificationEvent> = emptyList()
    ) = TabNotification(
            source = NotificationEvent.Source.THEME,
            type = event.type,
            event = event,
            isWebSocket = false,
            loadedEvents = loadedEvents
    )

    private class FakeFavItemDao : FavItemDao {
        private val itemsFlow = MutableStateFlow<List<FavItemRoom>>(emptyList())
        private val items = linkedMapOf<Int, FavItemRoom>()

        override fun getAllFavorites(): Flow<List<FavItemRoom>> = itemsFlow

        override suspend fun getAllFavoritesList(): List<FavItemRoom> = items.values.toList()

        override suspend fun getFavoriteById(favId: Int): FavItemRoom? = items[favId]

        override suspend fun insertFavorite(favorite: FavItemRoom) {
            items[favorite.favId] = favorite
            publish()
        }

        override suspend fun insertFavorites(favorites: List<FavItemRoom>) {
            favorites.forEach { items[it.favId] = it }
            publish()
        }

        override suspend fun updateFavorite(favorite: FavItemRoom) {
            items[favorite.favId] = favorite
            publish()
        }

        override suspend fun deleteFavorite(favId: Int) {
            items.remove(favId)
            publish()
        }

        override suspend fun deleteAllFavorites() {
            items.clear()
            publish()
        }

        fun replaceRowsWithoutPublishing(vararg rows: FavItemRoom) {
            items.clear()
            rows.forEach { items[it.favId] = it }
        }

        private fun publish() {
            itemsFlow.value = items.values.toList()
        }
    }
}
