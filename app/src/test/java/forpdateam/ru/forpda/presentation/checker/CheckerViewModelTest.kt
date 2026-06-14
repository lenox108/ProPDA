package forpdateam.ru.forpda.presentation.checker

import app.cash.turbine.test
import forpdateam.ru.forpda.entity.remote.checker.UpdateData
import forpdateam.ru.forpda.model.repository.checker.CheckerRepository
import forpdateam.ru.forpda.presentation.IErrorHandler
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CheckerViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var checkerRepository: CheckerRepository
    private lateinit var errorHandler: IErrorHandler

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        checkerRepository = mockk(relaxed = true)
        errorHandler = mockk(relaxed = true)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel() = CheckerViewModel(checkerRepository, errorHandler)

    private fun makeUpdateData(code: Int = 100, name: String = "2.0.1"): UpdateData {
        return UpdateData().apply {
            this.code = code
            this.name = name
            this.build = 13644
            this.date = "2026-04-14"
        }
    }

    @Test
    fun `initial state is loading with no update`() = runTest {
        val vm = createViewModel()
        val state = vm.uiState.value
        assertTrue(state.loading)
        assertNull(state.update)
    }

    @Test
    fun `checkUpdate success sets update data and loading false`() = runTest {
        val data = makeUpdateData()
        coEvery { checkerRepository.checkUpdate(true) } returns data

        val vm = createViewModel()
        vm.checkUpdate(forceRefresh = true)
        advanceUntilIdle()

        val state = vm.uiState.value
        assertFalse(state.loading)
        assertNotNull(state.update)
        assertEquals(100, state.update!!.code)
        assertEquals("2.0.1", state.update!!.name)
    }

    @Test
    fun `checkUpdate passes forceRefresh to repository`() = runTest {
        coEvery { checkerRepository.checkUpdate(any()) } returns makeUpdateData()

        val vm = createViewModel()

        vm.checkUpdate(forceRefresh = false)
        advanceUntilIdle()
        coVerify { checkerRepository.checkUpdate(false) }

        vm.checkUpdate(forceRefresh = true)
        advanceUntilIdle()
        coVerify { checkerRepository.checkUpdate(true) }
    }

    @Test
    fun `checkUpdate error sets loading false and null update`() = runTest {
        val error = RuntimeException("Network error")
        coEvery { checkerRepository.checkUpdate(any()) } throws error

        val vm = createViewModel()
        vm.checkUpdate(forceRefresh = true)
        advanceUntilIdle()

        val state = vm.uiState.value
        assertFalse(state.loading)
        assertNull(state.update)
        verify { errorHandler.handle(error, null) }
    }

    @Test
    fun `checkUpdate loading transitions are correct`() = runTest {
        coEvery { checkerRepository.checkUpdate(any()) } returns makeUpdateData()

        val vm = createViewModel()

        vm.uiState.test {
            // initial: loading=true
            val initial = awaitItem()
            assertTrue(initial.loading)

            vm.checkUpdate(forceRefresh = true)

            // StateFlow conflates — промежуточное loading=true может быть пропущено.
            // Ожидаем итоговое состояние: loading=false, update != null
            var latest = awaitItem()
            // Если ещё loading=true, ждём следующее
            if (latest.loading) {
                latest = awaitItem()
            }
            assertFalse(latest.loading)
            assertNotNull(latest.update)

            cancelAndConsumeRemainingEvents()
        }
    }
}
