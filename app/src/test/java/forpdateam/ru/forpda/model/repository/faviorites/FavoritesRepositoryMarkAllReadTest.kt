package forpdateam.ru.forpda.model.repository.faviorites

import android.content.SharedPreferences
import forpdateam.ru.forpda.entity.common.AuthData
import forpdateam.ru.forpda.entity.db.favorites.FavItemDao
import forpdateam.ru.forpda.entity.db.favorites.FavItemRoom
import forpdateam.ru.forpda.entity.remote.favorites.FavItem
import forpdateam.ru.forpda.entity.remote.favorites.FavoriteReadState
import forpdateam.ru.forpda.model.AuthHolder
import forpdateam.ru.forpda.model.CountersHolder
import forpdateam.ru.forpda.model.data.cache.favorites.FavoritesCacheRoom
import forpdateam.ru.forpda.model.data.remote.api.events.NotificationEventsApi
import forpdateam.ru.forpda.model.data.remote.api.favorites.FavoritesApi
import forpdateam.ru.forpda.model.preferences.ListsPreferencesHolder
import forpdateam.ru.forpda.model.preferences.NotificationPreferencesHolder
import forpdateam.ru.forpda.model.repository.theme.TopicReadBoundaryStore
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.Runs
import io.mockk.verify
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Fix #7: [FavoritesRepository.markFavoriteTopicsRead] must reset
 * [FavItem.inspectorMarkedUnread] to `false` for every topic whose mark-read call to the
 * server succeeded, in addition to flipping the row to READ. This is the explicit
 * "mark all favorites as read" path: the user has committed to the action, so a stale
 * inspector hint must not be allowed to resurrect the badge on the next refresh.
 *
 * Topics that failed to mark-read on the server must keep their current
 * `inspectorMarkedUnread` value — they have not been cleared yet, so the badge stays
 * until the next successful call.
 */
class FavoritesRepositoryMarkAllReadTest {

    @Test
    fun `mark all favorites read clears inspectorMarkedUnread for successful topics`() = runTest {
        // The production cache strips inspectorMarkedUnread on round-trip, so we mock the
        // cache to observe the in-memory FavItem directly. This lets us verify that
        // markFavoriteTopicsRead actually flips the flag to false for every successful topic.
        val item42 = favoriteTopic(favId = 1, topicId = 42, isNew = true, unreadPostCount = 3).apply {
            inspectorMarkedUnread = true
        }
        val item43 = favoriteTopic(favId = 2, topicId = 43, isNew = true, unreadPostCount = 1).apply {
            inspectorMarkedUnread = false
        }
        val favoritesCache = mockk<FavoritesCacheRoom>(relaxed = true) {
            coEvery { getItems() } returns listOf(item42, item43)
            coEvery { saveFavorites(any()) } returns Unit
        }
        val favoritesApi = mockk<FavoritesApi> {
            coEvery { markFavoriteTopicRead(42) } returns true
            coEvery { markFavoriteTopicRead(43) } returns true
        }
        val eventsApi = mockk<NotificationEventsApi>(relaxed = true) {
            every { invalidateFavoritesInspectorCache() } just Runs
        }
        val repository = createRepository(favoritesCache, favoritesApi = favoritesApi, eventsApi = eventsApi)

        val result = repository.markFavoriteTopicsRead(
                entries = listOf(
                        FavoriteMarkReadEntry(favId = 1, topicId = 42),
                        FavoriteMarkReadEntry(favId = 2, topicId = 43)
                ),
                onProgress = { /* no-op */ }
        )

        // Both topics were marked read on the server.
        assertEquals(setOf(42, 43), result.successTopicIds)
        assertEquals(emptySet<Int>(), result.failedTopicIds)

        // Fix #7: inspectorMarkedUnread must be false for every successful topic,
        // even when the cached value was already true.
        assertEquals(false, item42.inspectorMarkedUnread)
        assertEquals(false, item43.inspectorMarkedUnread)
        // Standard read-state transitions.
        assertEquals(false, item42.isNew)
        assertEquals(FavoriteReadState.READ, item42.readState)
        assertEquals(0, item42.unreadPostCount)
        assertEquals(false, item43.isNew)
        assertEquals(FavoriteReadState.READ, item43.readState)
        assertEquals(0, item43.unreadPostCount)
    }

    @Test
    fun `mark all favorites read keeps inspectorMarkedUnread for failed topics`() = runTest {
        // When the server rejects the mark-read call, the success branch of
        // markFavoriteTopicsRead is skipped entirely — neither inspectorMarkedUnread nor any
        // other field is touched on the FavItem. The production cache strips inspectorMarkedUnread
        // on round-trip, so we mock the cache to observe the in-memory FavItem directly.
        val cachedItem = favoriteTopic(favId = 1, topicId = 42, isNew = true, unreadPostCount = 3).apply {
            inspectorMarkedUnread = true
        }
        val favoritesCache = mockk<FavoritesCacheRoom>(relaxed = true) {
            coEvery { getItems() } returns listOf(cachedItem)
            coEvery { saveFavorites(any()) } returns Unit
        }
        val favoritesApi = mockk<FavoritesApi> {
            coEvery { markFavoriteTopicRead(42) } returns false
        }
        val eventsApi = mockk<NotificationEventsApi>(relaxed = true)
        val repository = createRepository(favoritesCache, favoritesApi = favoritesApi, eventsApi = eventsApi)

        val result = repository.markFavoriteTopicsRead(
                entries = listOf(FavoriteMarkReadEntry(favId = 1, topicId = 42)),
                onProgress = { /* no-op */ }
        )

        // Server rejected the mark-read call.
        assertEquals(emptySet<Int>(), result.successTopicIds)
        assertEquals(setOf(42), result.failedTopicIds)

        // Fix #7: when the call fails, the row is not modified — inspectorMarkedUnread stays
        // at its pre-call value (true). The saveFavorites mock is not invoked.
        coVerify(exactly = 0) { favoritesCache.saveFavorites(any()) }
        assertEquals(true, cachedItem.inspectorMarkedUnread)
        assertEquals(true, cachedItem.isNew)
        assertEquals(FavoriteReadState.UNREAD, cachedItem.readState)
        assertEquals(3, cachedItem.unreadPostCount)
    }

    @Test
    fun `mark all favorites read reports progress for every entry`() = runTest {
        val favoritesCache = FavoritesCacheRoom(FakeFavItemDao())
        favoritesCache.saveFavorites(
                listOf(
                        favoriteTopic(favId = 1, topicId = 42, isNew = true, unreadPostCount = 1),
                        favoriteTopic(favId = 2, topicId = 43, isNew = true, unreadPostCount = 1)
                )
        )
        val favoritesApi = mockk<FavoritesApi> {
            coEvery { markFavoriteTopicRead(42) } returns true
            coEvery { markFavoriteTopicRead(43) } returns true
        }
        val eventsApi = mockk<NotificationEventsApi>(relaxed = true) {
            every { invalidateFavoritesInspectorCache() } just Runs
        }
        val repository = createRepository(favoritesCache, favoritesApi = favoritesApi, eventsApi = eventsApi)

        val progressEvents = mutableListOf<FavoriteMarkReadProgress>()
        repository.markFavoriteTopicsRead(
                entries = listOf(
                        FavoriteMarkReadEntry(favId = 1, topicId = 42),
                        FavoriteMarkReadEntry(favId = 2, topicId = 43)
                ),
                onProgress = { progressEvents.add(it) }
        )

        assertEquals(2, progressEvents.size)
        assertEquals(FavoriteMarkReadProgress(processed = 1, total = 2, success = 1, failed = 0), progressEvents[0])
        assertEquals(FavoriteMarkReadProgress(processed = 2, total = 2, success = 2, failed = 0), progressEvents[1])
    }

    @Test
    fun `mark all favorites read on empty entries returns empty result`() = runTest {
        val favoritesCache = FavoritesCacheRoom(FakeFavItemDao())
        val favoritesApi = mockk<FavoritesApi>(relaxed = true)
        val eventsApi = mockk<NotificationEventsApi>(relaxed = true)
        val repository = createRepository(favoritesCache, favoritesApi = favoritesApi, eventsApi = eventsApi)

        val result = repository.markFavoriteTopicsRead(entries = emptyList(), onProgress = { /* no-op */ })

        assertTrue(result.successTopicIds.isEmpty())
        assertTrue(result.failedTopicIds.isEmpty())
    }

    @Test
    fun `mark all favorites read resets bottom badge counter`() = runTest {
        val favoritesCache = FavoritesCacheRoom(FakeFavItemDao())
        favoritesCache.saveFavorites(
                listOf(
                        favoriteTopic(favId = 1, topicId = 42, isNew = true, unreadPostCount = 5).apply {
                            inspectorMarkedUnread = true
                        },
                        favoriteTopic(favId = 2, topicId = 43, isNew = true, unreadPostCount = 2).apply {
                            inspectorMarkedUnread = true
                        }
                )
        )
        val favoritesApi = mockk<FavoritesApi> {
            coEvery { markFavoriteTopicRead(42) } returns true
            coEvery { markFavoriteTopicRead(43) } returns true
        }
        val eventsApi = mockk<NotificationEventsApi>(relaxed = true) {
            every { invalidateFavoritesInspectorCache() } just Runs
        }
        val countersHolder = CountersHolder(counterPreferences(initialFavoritesCount = 2))
        val repository = createRepository(
                favoritesCache,
                favoritesApi = favoritesApi,
                eventsApi = eventsApi,
                countersHolder = countersHolder
        )

        repository.markFavoriteTopicsRead(
                entries = listOf(
                        FavoriteMarkReadEntry(favId = 1, topicId = 42),
                        FavoriteMarkReadEntry(favId = 2, topicId = 43)
                ),
                onProgress = { /* no-op */ }
        )

        assertEquals(0, countersHolder.get().favorites)
    }

    @Test
    fun `mark all favorites read clears client read boundary for successful topics only`() = runTest {
        // Регресс: явная отметка «прочитано» в списке избранного должна снимать клиентскую границу
        // прочитанного, иначе при следующем открытии темы юзера отбрасывает на позавчерашний пост
        // (TopicReadBoundaryPolicy резюмит findpost'ом на устаревшую границу вместо свежих постов).
        val item42 = favoriteTopic(favId = 1, topicId = 42, isNew = true, unreadPostCount = 3)
        val item43 = favoriteTopic(favId = 2, topicId = 43, isNew = true, unreadPostCount = 1)
        val favoritesCache = mockk<FavoritesCacheRoom>(relaxed = true) {
            coEvery { getItems() } returns listOf(item42, item43)
            coEvery { saveFavorites(any()) } returns Unit
        }
        val favoritesApi = mockk<FavoritesApi> {
            coEvery { markFavoriteTopicRead(42) } returns true
            coEvery { markFavoriteTopicRead(43) } returns false // сервер отклонил — граница НЕ снимается
        }
        val eventsApi = mockk<NotificationEventsApi>(relaxed = true) {
            every { invalidateFavoritesInspectorCache() } just Runs
        }
        val readBoundaryStore = mockk<TopicReadBoundaryStore>(relaxed = true)
        val repository = createRepository(
                favoritesCache,
                favoritesApi = favoritesApi,
                eventsApi = eventsApi,
                readBoundaryStore = readBoundaryStore
        )

        repository.markFavoriteTopicsRead(
                entries = listOf(
                        FavoriteMarkReadEntry(favId = 1, topicId = 42),
                        FavoriteMarkReadEntry(favId = 2, topicId = 43)
                ),
                onProgress = { /* no-op */ }
        )

        verify(exactly = 1) { readBoundaryStore.clear(42) }
        verify(exactly = 0) { readBoundaryStore.clear(43) }
    }

    private fun createRepository(
            favoritesCache: FavoritesCacheRoom,
            favoritesApi: FavoritesApi = mockk<FavoritesApi>(relaxed = true),
            eventsApi: NotificationEventsApi = mockk<NotificationEventsApi>(relaxed = true),
            initialFavoritesCount: Int = 0,
            countersHolder: CountersHolder = CountersHolder(counterPreferences(initialFavoritesCount)),
            readBoundaryStore: TopicReadBoundaryStore = mockk(relaxed = true)
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
                readBoundaryStore = readBoundaryStore
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

        private fun publish() {
            itemsFlow.value = items.values.toList()
        }
    }
}
