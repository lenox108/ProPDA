package forpdateam.ru.forpda.presentation.theme.blacklist

import app.cash.turbine.test
import forpdateam.ru.forpda.model.preferences.ForumBlacklistedUser
import forpdateam.ru.forpda.model.preferences.TopicPreferencesHolder
import forpdateam.ru.forpda.presentation.ILinkHandler
import forpdateam.ru.forpda.presentation.TabRouter
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
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
class ForumBlackListViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var topicPreferencesHolder: TopicPreferencesHolder
    private lateinit var router: TabRouter
    private lateinit var linkHandler: ILinkHandler

    private val blacklistFlow = MutableStateFlow<List<ForumBlacklistedUser>>(emptyList())

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        topicPreferencesHolder = mockk(relaxed = true)
        router = mockk(relaxed = true)
        linkHandler = mockk(relaxed = true)

        every { topicPreferencesHolder.observeForumBlacklistFlow() } returns blacklistFlow
        coEvery { topicPreferencesHolder.removeForumBlacklistedUser(any()) } returns Unit
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): ForumBlackListViewModel {
        return ForumBlackListViewModel(topicPreferencesHolder, router, linkHandler)
    }

    @Test
    fun `uiState reflects blacklist flow updates`() = runTest {
        val viewModel = createViewModel()

        viewModel.uiState.test {
            assertEquals(emptyList<ForumBlacklistedUser>(), awaitItem().users)

            blacklistFlow.value = listOf(ForumBlacklistedUser(42, "Tester"))
            assertEquals(listOf(ForumBlacklistedUser(42, "Tester")), awaitItem().users)
        }
    }

    @Test
    fun `removeUser persists removal`() = runTest {
        val user = ForumBlacklistedUser(42, "Tester")
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.removeUser(user)
        advanceUntilIdle()

        coVerify { topicPreferencesHolder.removeForumBlacklistedUser(user) }
    }

    @Test
    fun `openProfile navigates when user id is known`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.openProfile(ForumBlacklistedUser(42, "Tester"))

        verify { linkHandler.handle("https://4pda.to/forum/index.php?showuser=42", router) }
    }
}
