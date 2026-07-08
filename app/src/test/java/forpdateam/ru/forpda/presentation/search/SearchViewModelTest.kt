package forpdateam.ru.forpda.presentation.search

import android.content.Context
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.slot
import io.mockk.mockk
import io.mockk.verify
import forpdateam.ru.forpda.entity.remote.search.SearchItem
import forpdateam.ru.forpda.entity.remote.search.SearchResult
import forpdateam.ru.forpda.entity.remote.search.SearchSettings
import forpdateam.ru.forpda.model.repository.search.SearchRepository
import forpdateam.ru.forpda.model.repository.reputation.ReputationRepository
import forpdateam.ru.forpda.model.repository.posteditor.PostEditorRepository
import forpdateam.ru.forpda.model.repository.theme.ThemeRepository
import forpdateam.ru.forpda.model.repository.faviorites.FavoritesRepository
import forpdateam.ru.forpda.model.preferences.MainPreferencesHolder
import forpdateam.ru.forpda.model.preferences.OtherPreferencesHolder
import forpdateam.ru.forpda.model.preferences.TopicPreferencesHolder
import forpdateam.ru.forpda.presentation.IErrorHandler
import forpdateam.ru.forpda.presentation.ILinkHandler
import forpdateam.ru.forpda.presentation.TabRouter
import forpdateam.ru.forpda.common.ClipboardHelper
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var context: Context
    private lateinit var searchRepository: SearchRepository
    private lateinit var editPostRepository: PostEditorRepository
    private lateinit var favoritesRepository: FavoritesRepository
    private lateinit var themeRepository: ThemeRepository
    private lateinit var reputationRepository: ReputationRepository
    private lateinit var topicPreferencesHolder: TopicPreferencesHolder
    private lateinit var mainPreferencesHolder: MainPreferencesHolder
    private lateinit var otherPreferencesHolder: OtherPreferencesHolder
    private lateinit var router: TabRouter
    private lateinit var linkHandler: ILinkHandler
    private lateinit var errorHandler: IErrorHandler
    private lateinit var clipboardHelper: ClipboardHelper

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        context = mockk(relaxed = true)
        searchRepository = mockk(relaxed = true)
        editPostRepository = mockk(relaxed = true)
        favoritesRepository = mockk(relaxed = true)
        themeRepository = mockk(relaxed = true)
        reputationRepository = mockk(relaxed = true)
        topicPreferencesHolder = mockk(relaxed = true)
        mainPreferencesHolder = mockk(relaxed = true)
        otherPreferencesHolder = mockk(relaxed = true)
        router = mockk(relaxed = true)
        linkHandler = mockk(relaxed = true)
        errorHandler = mockk(relaxed = true)
        clipboardHelper = mockk(relaxed = true)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel() = SearchViewModel(
        context,
        searchRepository,
        editPostRepository,
        favoritesRepository,
        themeRepository,
        reputationRepository,
        topicPreferencesHolder,
        mainPreferencesHolder,
        otherPreferencesHolder,
        router,
        linkHandler,
        errorHandler,
        clipboardHelper
    )

    @Test
    fun `initial state is not null`() = runTest {
        val vm = createViewModel()
        assertTrue(vm != null)
    }

    @Test
    fun `route search settings are not overwritten by saved settings`() = runTest {
        val routeUrl = SearchSettings().apply {
            source = SearchSettings.SOURCE_CONTENT.first
            userId = 598
            result = SearchSettings.RESULT_POSTS.first
            subforums = SearchSettings.SUB_FORUMS_FALSE
            addForum("10")
            addTopic("20")
        }.toUrl()
        val savedUrl = SearchSettings().apply {
            nick = "OldNick"
            result = SearchSettings.RESULT_TOPICS.first
        }.toUrl()
        coEvery { otherPreferencesHolder.getSearchSettings() } returns savedUrl
        coEvery { searchRepository.getSearch(any()) } answers {
            SearchResult().apply { settings = firstArg() }
        }

        val vm = createViewModel()
        vm.initSearchSettings(routeUrl)
        vm.start()
        advanceUntilIdle()
        vm.search("needle", "")
        advanceUntilIdle()

        coVerify { searchRepository.getSearch(match { settings ->
            settings.query == "needle" &&
                    settings.userId == 598 &&
                    settings.result == SearchSettings.RESULT_POSTS.first &&
                    settings.source == SearchSettings.SOURCE_CONTENT.first &&
                    settings.subforums == SearchSettings.SUB_FORUMS_FALSE &&
                    settings.forums == listOf("10") &&
                    settings.topics == listOf("20")
        }) }
    }

    @Test
    fun `route search can start with user id only`() = runTest {
        val routeUrl = SearchSettings().apply {
            source = SearchSettings.SOURCE_CONTENT.first
            userId = 598
            result = SearchSettings.RESULT_POSTS.first
            subforums = SearchSettings.SUB_FORUMS_FALSE
            addForum("10")
            addTopic("20")
        }.toUrl()
        coEvery { otherPreferencesHolder.getSearchSettings() } returns ""
        coEvery { searchRepository.getSearch(any()) } answers {
            SearchResult().apply { settings = firstArg() }
        }

        val vm = createViewModel()
        vm.initSearchSettings(routeUrl)
        advanceUntilIdle()
        vm.refreshData()
        advanceUntilIdle()

        coVerify { searchRepository.getSearch(match { settings ->
            settings.nick.isNullOrEmpty() &&
                    settings.userId == 598 &&
                    settings.result == SearchSettings.RESULT_POSTS.first &&
                    settings.source == SearchSettings.SOURCE_CONTENT.first &&
                    settings.subforums == SearchSettings.SUB_FORUMS_FALSE &&
                    settings.forums == listOf("10") &&
                    settings.topics == listOf("20")
        }) }
    }

    @Test
    fun `user post search result opens posts by same user in selected topic`() = runTest {
        val routeUrl = SearchSettings().apply {
            source = SearchSettings.SOURCE_ALL.first
            userId = 598
            result = SearchSettings.RESULT_POSTS.first
            subforums = SearchSettings.SUB_FORUMS_TRUE
        }.toUrl()
        val openedUrl = slot<String>()
        coEvery { otherPreferencesHolder.getSearchSettings() } returns ""
        coEvery { searchRepository.getSearch(any()) } returns SearchResult()
        every { linkHandler.handle(capture(openedUrl), router, any()) } returns true

        val vm = createViewModel()
        vm.initSearchSettings(routeUrl)
        advanceUntilIdle()
        vm.onItemClick(SearchItem().apply {
            topicId = 12345
            forumId = 678
            userId = 598
            nick = "Tester"
            id = 999
        })

        val settings = SearchSettings.parseSettings(openedUrl.captured)
        assertEquals(SearchSettings.RESULT_POSTS.first, settings.result)
        assertEquals(SearchSettings.SOURCE_CONTENT.first, settings.source)
        assertEquals(SearchSettings.SUB_FORUMS_FALSE, settings.subforums)
        assertEquals(598, settings.userId)
        assertEquals(listOf("678"), settings.forums)
        assertEquals(listOf("12345"), settings.topics)
        assertFalse(openedUrl.captured.contains("view=findpost"))
        assertFalse(openedUrl.captured.contains("&p=999"))
    }

    @Test
    fun `user topic search result opens posts by searched user in selected topic`() = runTest {
        val routeUrl = SearchSettings().apply {
            source = SearchSettings.SOURCE_ALL.first
            userId = 598
            nick = "SearchedUser"
            result = SearchSettings.RESULT_TOPICS.first
        }.toUrl()
        val openedUrl = slot<String>()
        coEvery { otherPreferencesHolder.getSearchSettings() } returns ""
        coEvery { searchRepository.getSearch(any()) } returns SearchResult()
        every { linkHandler.handle(capture(openedUrl), router, any()) } returns true

        val vm = createViewModel()
        vm.initSearchSettings(routeUrl)
        advanceUntilIdle()
        vm.onItemClick(SearchItem().apply {
            topicId = 12345
            forumId = 678
            userId = 999
            nick = "TopicAuthor"
        })

        val settings = SearchSettings.parseSettings(openedUrl.captured)
        assertEquals(SearchSettings.RESULT_POSTS.first, settings.result)
        assertEquals(SearchSettings.SOURCE_CONTENT.first, settings.source)
        assertEquals(SearchSettings.SUB_FORUMS_FALSE, settings.subforums)
        assertEquals(598, settings.userId)
        assertEquals("SearchedUser", settings.nick)
        assertEquals(listOf("678"), settings.forums)
        assertEquals(listOf("12345"), settings.topics)
    }

    @Test
    fun `forum section post search result opens exact post with explicit intent`() = runTest {
        val routeUrl = forumSectionSearchUrl(285)
        val openedUrl = slot<String>()
        val openArgs = slot<Map<String, String>>()
        coEvery { otherPreferencesHolder.getSearchSettings() } returns ""
        coEvery { searchRepository.getSearch(any()) } returns SearchResult()
        every { linkHandler.handle(capture(openedUrl), router, capture(openArgs)) } returns true

        val vm = createViewModel()
        vm.initSearchSettings(routeUrl, forumTitle = "AdGuard")
        advanceUntilIdle()
        vm.onItemClick(SearchItem().apply {
            topicId = 12345
            forumId = 285
            id = 999
        })

        assertEquals("https://4pda.to/forum/index.php?showtopic=12345&view=findpost&p=999", openedUrl.captured)
        assertEquals("search", openArgs.captured["topic_open_source"])
        assertEquals("explicit_post", openArgs.captured["topic_open_intent"])
        val settings = SearchSettings.parseSettings(routeUrl)
        assertEquals(listOf("285"), settings.forums)
        assertEquals(SearchSettings.RESULT_TOPICS.first, settings.result)
        assertEquals(SearchSettings.SOURCE_TITLES.first, settings.source)
        assertEquals(SearchSettings.SUB_FORUMS_FALSE, settings.subforums)
    }

    @Test
    fun `regular post search result still opens exact post`() = runTest {
        val routeUrl = SearchSettings().apply {
            source = SearchSettings.SOURCE_CONTENT.first
            query = "needle"
            result = SearchSettings.RESULT_POSTS.first
        }.toUrl()
        val openedUrl = slot<String>()
        coEvery { otherPreferencesHolder.getSearchSettings() } returns ""
        coEvery { searchRepository.getSearch(any()) } returns SearchResult()
        every { linkHandler.handle(capture(openedUrl), router, any()) } returns true

        val vm = createViewModel()
        vm.initSearchSettings(routeUrl)
        advanceUntilIdle()
        vm.onItemClick(SearchItem().apply {
            topicId = 12345
            userId = 598
            id = 999
        })

        assertEquals("https://4pda.to/forum/index.php?showtopic=12345&view=findpost&p=999", openedUrl.captured)
    }

}
