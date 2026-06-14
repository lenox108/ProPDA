package forpdateam.ru.forpda.presentation.topics

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.verify
import forpdateam.ru.forpda.model.repository.topics.TopicsRepository
import forpdateam.ru.forpda.model.repository.forum.ForumRepository
import forpdateam.ru.forpda.model.repository.faviorites.FavoritesRepository
import forpdateam.ru.forpda.model.interactors.CrossScreenInteractor
import forpdateam.ru.forpda.presentation.IErrorHandler
import forpdateam.ru.forpda.presentation.ILinkHandler
import forpdateam.ru.forpda.presentation.TabRouter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TopicsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var topicsRepository: TopicsRepository
    private lateinit var forumRepository: ForumRepository
    private lateinit var favoritesRepository: FavoritesRepository
    private lateinit var crossScreenInteractor: CrossScreenInteractor
    private lateinit var router: TabRouter
    private lateinit var linkHandler: ILinkHandler
    private lateinit var errorHandler: IErrorHandler

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        topicsRepository = mockk(relaxed = true)
        forumRepository = mockk(relaxed = true)
        favoritesRepository = mockk(relaxed = true)
        crossScreenInteractor = mockk(relaxed = true)
        router = mockk(relaxed = true)
        linkHandler = mockk(relaxed = true)
        errorHandler = mockk(relaxed = true)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel() = TopicsViewModel(
        topicsRepository,
        forumRepository,
        favoritesRepository,
        crossScreenInteractor,
        router,
        linkHandler,
        errorHandler
    )

    @Test
    fun `initial state is not null`() = runTest {
        val vm = createViewModel()
        assertTrue(vm != null)
    }

    @Test
    fun `loadTopics calls repository getTopics`() = runTest {
        val vm = createViewModel()
        vm.loadTopics()
        advanceUntilIdle()
        
        coVerify { topicsRepository.getTopics(any(), any()) }
    }
}
