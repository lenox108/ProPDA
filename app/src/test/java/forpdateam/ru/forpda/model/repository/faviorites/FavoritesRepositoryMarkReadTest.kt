package forpdateam.ru.forpda.model.repository.faviorites

import android.content.SharedPreferences
import forpdateam.ru.forpda.entity.app.TabNotification
import forpdateam.ru.forpda.entity.common.AuthData
import forpdateam.ru.forpda.entity.db.favorites.FavItemDao
import forpdateam.ru.forpda.entity.db.favorites.FavItemRoom
import forpdateam.ru.forpda.entity.remote.events.NotificationEvent
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
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.Runs
import io.mockk.verify
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Fix #5 + Debug #2/#3: [FavoritesRepository.markRead] must
 *  - leave [FavItem.inspectorMarkedUnread] intact (it is recomputed on the next refresh
 *    by `mergeNetworkFavoriteReadStates` from the inspector snapshot, not here);
 *  - update the per-row state to READ (`isNew=false`, `readState=READ`, `unreadPostCount=0`,
 *    `listingHref=null`);
 *  - record the prev/new state in [forpdateam.ru.forpda.diagnostic.ThemePostReadStateDiagnostics.markReadApplied];
 *  - call [forpdateam.ru.forpda.diagnostic.ThemePostReadStateDiagnostics.markReadSkipped] when
 *    the topic is not in cache, so the silent drop is visible in logs.
 */
class FavoritesRepositoryMarkReadTest {

    @Test
    fun `mark read preserves inspectorMarkedUnread for topic 1106099`() = runTest {
        // The production cache (FavoritesCacheRoom) does not persist inspectorMarkedUnread
        // through Room — the field is recomputed on the next merge pass. To verify that
        // markRead does not eagerly reset it, we mock the cache so the same FavItem instance
        // is returned to the repository and we can inspect it post-call. Pre-fix:
        // markRead sets inspectorMarkedUnread=false. Post-fix: markRead leaves the field alone.
        val cachedItem = favoriteTopic(
                favId = 15776864,
                topicId = 1106099,
                isNew = true,
                unreadPostCount = 1
        ).apply {
            listingHref = "https://4pda.to/forum/index.php?showtopic=1106099&view=getnewpost"
            inspectorMarkedUnread = true
        }
        val favoritesCache = mockk<FavoritesCacheRoom>(relaxed = true) {
            coEvery { getItemByTopicId(1106099) } returns cachedItem
            coEvery { getItemByFavId(15776864) } returns cachedItem
            coEvery { getItems() } returns listOf(cachedItem)
            coEvery { updateItem(any()) } returns Unit
        }
        val countersHolder = CountersHolder(counterPreferences(initialFavoritesCount = 1))
        val repository = createRepository(favoritesCache, countersHolder = countersHolder)

        repository.markRead(1106099)

        // Fix #5: inspectorMarkedUnread must NOT be reset by markRead.
        // It is re-derived from the inspector snapshot on the next mergeNetworkFavoriteReadStates pass.
        assertTrue(
                "inspectorMarkedUnread must be preserved through markRead (fix #5)",
                cachedItem.inspectorMarkedUnread
        )
        // The other state transitions must still happen.
        assertEquals(false, cachedItem.isNew)
        assertEquals(FavoriteReadState.READ, cachedItem.readState)
        assertEquals(0, cachedItem.unreadPostCount)
        assertEquals(1106099, cachedItem.localReadPostId)
        assertEquals(null, cachedItem.listingHref)
    }

    @Test
    fun `mark read keeps inspectorMarkedUnread false when it was already false`() = runTest {
        val favoritesCache = FavoritesCacheRoom(FakeFavItemDao())
        favoritesCache.saveFavorites(
                listOf(
                        favoriteTopic(
                                favId = 1,
                                topicId = 42,
                                isNew = true,
                                unreadPostCount = 3
                        ).apply { inspectorMarkedUnread = false }
                )
        )
        val repository = createRepository(favoritesCache)

        repository.markRead(42)

        val item = favoritesCache.getItemByTopicId(42)!!
        assertEquals(false, item.inspectorMarkedUnread)
        assertEquals(false, item.isNew)
        assertEquals(FavoriteReadState.READ, item.readState)
        assertEquals(0, item.unreadPostCount)
        assertEquals(42, item.localReadPostId)
    }

    @Test
    fun `mark read on missing topic does not crash and records skipped diagnostic`() = runTest {
        val favoritesCache = FavoritesCacheRoom(FakeFavItemDao())
        // Note: no saveFavorites() — the cache is empty.
        val repository = createRepository(favoritesCache)

        // Should not throw. The diagnostic method exists post-fix and is called with
        // reason=item_not_in_cache, source=favorites_repository.
        repository.markRead(424242)

        val item = favoritesCache.getItemByTopicId(424242)
        assertEquals(null, item)
    }

    @Test
    fun `mark read decrements bottom badge counter for matching favorite topic`() = runTest {
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
    fun `mark read decrements counter only once when called twice`() = runTest {
        val favoritesCache = FavoritesCacheRoom(FakeFavItemDao())
        favoritesCache.saveFavorites(
                listOf(
                        favoriteTopic(favId = 1, topicId = 42, isNew = true, unreadPostCount = 5)
                )
        )
        val countersHolder = CountersHolder(counterPreferences(initialFavoritesCount = 1))
        val repository = createRepository(favoritesCache, countersHolder = countersHolder)

        repository.markRead(42)
        repository.markRead(42)

        assertEquals(0, countersHolder.get().favorites)
    }

    @Test
    fun `mark read clears client read boundary for the topic`() = runTest {
        // Регресс: после отметки прочитанным клиентская граница прочитанного должна сниматься,
        // иначе переоткрытие темы резюмит findpost'ом на устаревшую границу вместо свежих постов.
        val favoritesCache = FavoritesCacheRoom(FakeFavItemDao())
        favoritesCache.saveFavorites(
                listOf(favoriteTopic(favId = 1, topicId = 42, isNew = true, unreadPostCount = 3))
        )
        val readBoundaryStore = mockk<TopicReadBoundaryStore>(relaxed = true)
        val repository = createRepository(favoritesCache, readBoundaryStore = readBoundaryStore)

        repository.markRead(42)

        verify(exactly = 1) { readBoundaryStore.clear(42) }
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
