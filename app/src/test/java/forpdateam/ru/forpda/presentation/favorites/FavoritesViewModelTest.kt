package forpdateam.ru.forpda.presentation.favorites

import forpdateam.ru.forpda.entity.remote.favorites.FavItem
import forpdateam.ru.forpda.entity.remote.favorites.FavoriteReadState
import forpdateam.ru.forpda.model.AuthHolder
import forpdateam.ru.forpda.model.interactors.CrossScreenInteractor
import forpdateam.ru.forpda.model.interactors.theme.ThemePrefetchService
import forpdateam.ru.forpda.model.preferences.MainPreferencesHolder
import forpdateam.ru.forpda.model.preferences.ListsPreferencesHolder
import forpdateam.ru.forpda.model.preferences.NotificationPreferencesHolder
import forpdateam.ru.forpda.model.repository.events.EventsRepository
import forpdateam.ru.forpda.model.repository.faviorites.FavoritesRepository
import forpdateam.ru.forpda.model.interactors.theme.ThemeUseCase
import forpdateam.ru.forpda.presentation.IErrorHandler
import forpdateam.ru.forpda.common.ClipboardHelper
import forpdateam.ru.forpda.presentation.ILinkHandler
import forpdateam.ru.forpda.presentation.TabRouter
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FavoritesViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val itemsFlow = MutableStateFlow<List<FavItem>>(emptyList())

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun favItem(favId: Int, title: String): FavItem = FavItem().apply {
        this.favId = favId
        topicId = favId * 10
        topicTitle = title
    }

    private fun unreadFavItem(favId: Int, title: String): FavItem = favItem(favId, title).apply {
        isNew = true
        readState = FavoriteReadState.UNREAD
        unreadPostCount = 1
    }

    private lateinit var favoritesRepository: FavoritesRepository
    private lateinit var themeUseCase: ThemeUseCase
    private lateinit var authHolder: AuthHolder

    private fun createViewModel(): FavoritesViewModel {
        favoritesRepository = mockk<FavoritesRepository>(relaxed = true)
        every { favoritesRepository.observeItems(any(), any()) } returns itemsFlow
        coEvery { favoritesRepository.loadCache(any(), any()) } returns emptyList()
        coEvery { favoritesRepository.fetchAllFavoritesForSearch(any()) } returns listOf(
                favItem(1, "Alpha on page 1"),
                favItem(2, "Beta on page 2"),
                favItem(3, "Gamma on page 3"),
        )
        val listsPreferencesHolder = mockk<ListsPreferencesHolder>(relaxed = true)
        every { listsPreferencesHolder.getFavLoadAll() } returns false
        every { listsPreferencesHolder.observeFavLoadAllFlow() } returns flowOf(false)
        every { listsPreferencesHolder.observeShowDotFlow() } returns flowOf(false)
        every { listsPreferencesHolder.observeFavShowUnreadBadgeFlow() } returns flowOf(false)
        every { listsPreferencesHolder.observeUnreadTopFlow() } returns flowOf(false)
        every { listsPreferencesHolder.getSortingKey() } returns ""
        every { listsPreferencesHolder.getSortingOrder() } returns ""
        val eventsRepository = mockk<EventsRepository>(relaxed = true)
        every { eventsRepository.observeEventsTab() } returns emptyFlow()
        val crossScreenInteractor = mockk<CrossScreenInteractor>(relaxed = true)
        every { crossScreenInteractor.observeTopic() } returns emptyFlow()
        themeUseCase = mockk<ThemeUseCase>(relaxed = true)
        authHolder = mockk<AuthHolder>(relaxed = true)
        return FavoritesViewModel(
                favoritesRepository,
                eventsRepository,
                listsPreferencesHolder,
                crossScreenInteractor,
                mockk<TabRouter>(relaxed = true),
                mockk<ILinkHandler>(relaxed = true),
                mockk<IErrorHandler>(relaxed = true),
                mockk<ClipboardHelper>(relaxed = true),
                authHolder,
                mockk<NotificationPreferencesHolder>(relaxed = true),
                mockk<ThemePrefetchService>(relaxed = true),
                mockk<MainPreferencesHolder>(relaxed = true),
                themeUseCase,
        )
    }

    @Test
    fun `searchLocal filters full catalog when paginated`() = runTest {
        val vm = createViewModel()
        val displayed = mutableListOf<List<FavItem>>()
        val collectJob = launch { vm.uiEvents.collect { if (it is FavoritesUiEvent.OnShowFavorite) displayed.add(it.list) } }

        vm.start()
        itemsFlow.value = listOf(favItem(1, "Alpha on page 1"))
        advanceUntilIdle()

        vm.searchLocal("Gamma")
        advanceUntilIdle()

        val last = displayed.last()
        assertEquals(1, last.size)
        assertEquals("Gamma on page 3", last[0].topicTitle)
        coVerify(exactly = 1) { favoritesRepository.fetchAllFavoritesForSearch(any()) }

        collectJob.cancel()
    }

    @Test
    fun `searchLocal uses cache when load all enabled`() = runTest {
        favoritesRepository = mockk<FavoritesRepository>(relaxed = true)
        every { favoritesRepository.observeItems(any(), any()) } returns itemsFlow
        coEvery { favoritesRepository.loadCache(any(), any()) } returns emptyList()
        val listsPreferencesHolder = mockk<ListsPreferencesHolder>(relaxed = true)
        every { listsPreferencesHolder.getFavLoadAll() } returns true
        every { listsPreferencesHolder.observeFavLoadAllFlow() } returns flowOf(true)
        every { listsPreferencesHolder.observeShowDotFlow() } returns flowOf(false)
        every { listsPreferencesHolder.observeFavShowUnreadBadgeFlow() } returns flowOf(false)
        every { listsPreferencesHolder.observeUnreadTopFlow() } returns flowOf(false)
        every { listsPreferencesHolder.getSortingKey() } returns ""
        every { listsPreferencesHolder.getSortingOrder() } returns ""
        val eventsRepository = mockk<EventsRepository>(relaxed = true)
        every { eventsRepository.observeEventsTab() } returns emptyFlow()
        val crossScreenInteractor = mockk<CrossScreenInteractor>(relaxed = true)
        every { crossScreenInteractor.observeTopic() } returns emptyFlow()
        val vm = FavoritesViewModel(
                favoritesRepository,
                eventsRepository,
                listsPreferencesHolder,
                crossScreenInteractor,
                mockk(relaxed = true),
                mockk(relaxed = true),
                mockk(relaxed = true),
                mockk(relaxed = true),
                mockk(relaxed = true),
                mockk(relaxed = true),
                mockk<ThemePrefetchService>(relaxed = true),
                mockk<MainPreferencesHolder>(relaxed = true),
                mockk<forpdateam.ru.forpda.model.interactors.theme.ThemeUseCase>(relaxed = true),
        )
        val displayed = mutableListOf<List<FavItem>>()
        val collectJob = launch { vm.uiEvents.collect { if (it is FavoritesUiEvent.OnShowFavorite) displayed.add(it.list) } }

        vm.start()
        itemsFlow.value = listOf(
                favItem(1, "Alpha"),
                favItem(3, "Gamma"),
        )
        advanceUntilIdle()

        vm.searchLocal("Gamma")
        advanceUntilIdle()

        assertEquals(1, displayed.last().size)
        coVerify(exactly = 0) { favoritesRepository.fetchAllFavoritesForSearch(any()) }

        collectJob.cancel()
    }

    @Test
    fun `refresh does not touch theme server mark-read de-dup cache`() = runTest {
        val vm = createViewModel()
        every { authHolder.get() } returns forpdateam.ru.forpda.entity.common.AuthData(
            userId = 1,
            state = forpdateam.ru.forpda.entity.common.AuthState.AUTH,
        )
        vm.start()
        advanceUntilIdle()

        vm.refresh()
        advanceUntilIdle()

        // A plain favorites refresh is a pure list operation: it must never re-arm the
        // theme server mark-read de-dup, otherwise `GET view=getlastpost` gets re-sent and
        // the topic is marked read on the server without the user (re)reading it.
        coVerify(exactly = 0) { themeUseCase.resetAllServerMarkReadDedup() }
        coVerify(exactly = 0) { themeUseCase.resetServerMarkReadDedup(any()) }
        // Sanity: the refresh actually ran (otherwise the verification is vacuous).
        coVerify(atLeast = 1) { favoritesRepository.loadFavorites(any(), any(), any(), any()) }
    }

    @Test
    fun `displayedItems replays latest repository update without uiEvents collector`() = runTest {
        val vm = createViewModel()
        vm.start()
        itemsFlow.value = listOf(unreadFavItem(1, "Unread"))
        advanceUntilIdle()

        itemsFlow.value = listOf(favItem(1, "Unread").apply {
            isNew = false
            readState = FavoriteReadState.READ
            unreadPostCount = 0
        })
        advanceUntilIdle()

        val latest = vm.displayedItems.filterNotNull().first()
        assertEquals(1, latest.size)
        assertEquals(false, latest.single().isNew)
        assertEquals(FavoriteReadState.READ, latest.single().readState)
        assertEquals(0, latest.single().unreadPostCount)
    }
}
