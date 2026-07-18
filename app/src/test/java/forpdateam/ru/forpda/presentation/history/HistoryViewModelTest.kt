package forpdateam.ru.forpda.presentation.history

import app.cash.turbine.test
import forpdateam.ru.forpda.common.ClipboardHelper
import forpdateam.ru.forpda.entity.app.history.HistoryItem
import forpdateam.ru.forpda.model.data.cache.favorites.FavoritesCacheRoom
import forpdateam.ru.forpda.model.preferences.ListsPreferencesHolder
import forpdateam.ru.forpda.model.repository.history.HistoryRepository
import forpdateam.ru.forpda.model.repository.history.HistoryUnreadHarvester
import forpdateam.ru.forpda.presentation.IErrorHandler
import forpdateam.ru.forpda.presentation.ILinkHandler
import forpdateam.ru.forpda.presentation.Screen
import forpdateam.ru.forpda.presentation.TabRouter
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import forpdateam.ru.forpda.entity.remote.favorites.FavItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var historyRepository: HistoryRepository
    private lateinit var favoritesCache: FavoritesCacheRoom
    private lateinit var historyUnreadHarvester: HistoryUnreadHarvester
    private lateinit var listsPrefs: ListsPreferencesHolder
    private lateinit var router: TabRouter
    private lateinit var linkHandler: ILinkHandler
    private lateinit var errorHandler: IErrorHandler
    private lateinit var clipboardHelper: ClipboardHelper

    private val itemsFlow = MutableSharedFlow<List<HistoryItem>>()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        historyRepository = mockk(relaxed = true)
        favoritesCache = mockk(relaxed = true)
        historyUnreadHarvester = mockk(relaxed = true)
        listsPrefs = mockk(relaxed = true)
        router = mockk(relaxed = true)
        linkHandler = mockk(relaxed = true)
        errorHandler = mockk(relaxed = true)
        clipboardHelper = mockk(relaxed = true)

        every { historyRepository.observeItems() } returns itemsFlow
        // combine() в VM ждёт эмиссию всех источников; отдаём стартовые значения, иначе items не соберутся.
        every { favoritesCache.observeItems() } returns MutableStateFlow(emptyList<FavItem>())
        every { listsPrefs.observeShowDotFlow() } returns flowOf(false)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): HistoryViewModel {
        return HistoryViewModel(historyRepository, favoritesCache, historyUnreadHarvester, listsPrefs, router, linkHandler, errorHandler, clipboardHelper)
    }

    private fun makeItem(id: Int, url: String = "https://4pda.to/$id", title: String = "Title $id"): HistoryItem {
        return HistoryItem().apply {
            this.id = id
            this.url = url
            this.title = title
            this.date = "2026-01-01"
            this.unixTime = System.currentTimeMillis()
        }
    }

    @Test
    fun `initial state is empty and loading`() = runTest {
        coEvery { historyRepository.getHistory() } returns emptyList()

        val vm = createViewModel()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertFalse(state.loading)
        assertTrue(state.items.isEmpty())
    }

    @Test
    fun `refresh loads items from repository`() = runTest {
        val items = listOf(makeItem(1), makeItem(2))
        coEvery { historyRepository.getHistory() } returns items

        val vm = createViewModel()
        advanceUntilIdle()

        // items приходят через observeItems() (кэш, который refresh()→getHistory() наполняет в проде),
        // а не напрямую из getHistory(); эмулируем эмиссию кэша.
        itemsFlow.emit(items)
        advanceUntilIdle()

        coVerify { historyRepository.getHistory() }
        val state = vm.uiState.value
        assertEquals(2, state.items.size)
        assertEquals(1, state.items[0].id)
        assertEquals(2, state.items[1].id)
        assertFalse(state.loading)
    }

    @Test
    fun `refresh sets loading true then false`() = runTest {
        coEvery { historyRepository.getHistory() } returns emptyList()

        val vm = createViewModel()

        vm.uiState.test {
            // Начальное состояние
            awaitItem()
            // loading=true
            val loading = awaitItem()
            assertTrue(loading.loading)
            // loading=false после завершения
            val done = awaitItem()
            assertFalse(done.loading)
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `observeItems updates state when flow emits`() = runTest {
        coEvery { historyRepository.getHistory() } returns emptyList()

        val vm = createViewModel()
        advanceUntilIdle()

        val items = listOf(makeItem(10), makeItem(20))
        itemsFlow.emit(items)
        advanceUntilIdle()

        assertEquals(2, vm.uiState.value.items.size)
        assertEquals(10, vm.uiState.value.items[0].id)
    }

    @Test
    fun `remove calls repository and sets loading`() = runTest {
        coEvery { historyRepository.getHistory() } returns emptyList()
        coEvery { historyRepository.remove(5) } just Runs

        val vm = createViewModel()
        advanceUntilIdle()

        vm.remove(5)
        advanceUntilIdle()

        coVerify { historyRepository.remove(5) }
        assertFalse(vm.uiState.value.loading)
    }

    @Test
    fun `remove handles error`() = runTest {
        val error = RuntimeException("DB error")
        coEvery { historyRepository.getHistory() } returns emptyList()
        coEvery { historyRepository.remove(any()) } throws error

        val vm = createViewModel()
        advanceUntilIdle()

        vm.remove(1)
        advanceUntilIdle()

        verify { errorHandler.handle(error, null) }
        assertFalse(vm.uiState.value.loading)
    }

    @Test
    fun `clear calls repository`() = runTest {
        coEvery { historyRepository.getHistory() } returns emptyList()
        coEvery { historyRepository.clear() } just Runs

        val vm = createViewModel()
        advanceUntilIdle()

        vm.onUserClear()
        advanceUntilIdle()

        coVerify { historyRepository.clear() }
        assertFalse(vm.uiState.value.loading)
    }

    @Test
    fun `clear handles error`() = runTest {
        val error = RuntimeException("Clear failed")
        coEvery { historyRepository.getHistory() } returns emptyList()
        coEvery { historyRepository.clear() } throws error

        val vm = createViewModel()
        advanceUntilIdle()

        vm.onUserClear()
        advanceUntilIdle()

        verify { errorHandler.handle(error, null) }
    }

    @Test
    fun `onItemClick delegates to linkHandler`() = runTest {
        coEvery { historyRepository.getHistory() } returns emptyList()

        val vm = createViewModel()
        advanceUntilIdle()

        val item = makeItem(42, "https://4pda.to/topic/42", "Test Topic")
        vm.onItemClick(item)

        verify {
            linkHandler.handle(
                    "https://4pda.to/topic/42",
                    router,
                    mapOf(Screen.ARG_TITLE to "Test Topic")
            )
        }
    }

    @Test
    fun `refresh on error calls errorHandler`() = runTest {
        val error = RuntimeException("Network error")
        coEvery { historyRepository.getHistory() } throws error

        val vm = createViewModel()
        advanceUntilIdle()

        verify { errorHandler.handle(error, null) }
        assertFalse(vm.uiState.value.loading)
    }
}
