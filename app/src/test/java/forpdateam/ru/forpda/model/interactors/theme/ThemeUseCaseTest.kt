package forpdateam.ru.forpda.model.interactors.theme

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import forpdateam.ru.forpda.model.repository.theme.ThemeRepository
import forpdateam.ru.forpda.presentation.theme.ThemeTemplate
import forpdateam.ru.forpda.ui.TemplateManager
import forpdateam.ru.forpda.model.AuthHolder
import forpdateam.ru.forpda.model.interactors.CrossScreenInteractor
import forpdateam.ru.forpda.model.data.remote.IWebClient
import forpdateam.ru.forpda.presentation.IErrorHandler
import forpdateam.ru.forpda.model.repository.events.EventsRepository
import forpdateam.ru.forpda.model.repository.faviorites.FavoritesRepository
import forpdateam.ru.forpda.model.preferences.TopicPreferencesHolder
import forpdateam.ru.forpda.model.preferences.MainPreferencesHolder
import forpdateam.ru.forpda.entity.app.profile.IUserHolder
import forpdateam.ru.forpda.entity.remote.others.pagination.Pagination
import forpdateam.ru.forpda.entity.remote.theme.ThemePage
import forpdateam.ru.forpda.entity.remote.theme.ThemePost
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import io.mockk.verify

@OptIn(ExperimentalCoroutinesApi::class)
class ThemeUseCaseTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var themeUseCase: ThemeUseCase
    private val themeRepository: ThemeRepository = mockk(relaxed = true)
    private val themeTemplate: ThemeTemplate = mockk(relaxed = true)
    private val templateManager: TemplateManager = mockk(relaxed = true)
    private val authHolder: AuthHolder = mockk(relaxed = true)
    private val crossScreenInteractor: CrossScreenInteractor = mockk(relaxed = true)
    private val webClient: IWebClient = mockk(relaxed = true)
    private val editorUseCase: ThemeEditorUseCase = mockk(relaxed = true)
    private val errorHandler: IErrorHandler = mockk(relaxed = true)
    private val eventsRepository: EventsRepository = mockk(relaxed = true)
    private val favoritesRepository: FavoritesRepository = mockk(relaxed = true)
    private val userHolder: IUserHolder = mockk(relaxed = true)
    private val topicPreferencesHolder: TopicPreferencesHolder = mockk(relaxed = true)
    private val mainPreferencesHolder: MainPreferencesHolder = mockk(relaxed = true)
    private val returnPositionStore =
            forpdateam.ru.forpda.model.repository.theme.TopicReturnPositionStore()
    private val topicForumStore: forpdateam.ru.forpda.model.repository.theme.TopicForumStore =
            mockk(relaxed = true)
    private val historyUnreadHarvester: forpdateam.ru.forpda.model.repository.history.HistoryUnreadHarvester =
            mockk(relaxed = true)
    private val appScope: CoroutineScope = TestScope(dispatcher)

    @Before
    fun setup() {
        Dispatchers.setMain(dispatcher)
        themeUseCase = ThemeUseCase(
            themeRepository,
            themeTemplate,
            templateManager,
            authHolder,
            crossScreenInteractor,
            webClient,
            editorUseCase,
            errorHandler,
            eventsRepository,
            favoritesRepository,
            returnPositionStore,
            userHolder,
            topicPreferencesHolder,
            mainPreferencesHolder,
            topicForumStore,
            historyUnreadHarvester,
            appScope = appScope
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state is not null`() = runTest {
        assert(themeUseCase != null)
    }

    @Test
    fun `primary theme loaded marks topic read on last page`() = runTest {
        val page = ThemePage().apply {
            id = 903891
            pagination = Pagination().apply {
                current = 10
                all = 10
            }
        }

        themeUseCase.onPrimaryThemeLoaded(page)

        verify(exactly = 1) { crossScreenInteractor.onLoadTopic(903891) }
        verify(exactly = 1) { eventsRepository.onTopicRead(903891) }
    }

    @Test
    fun `hybrid neighbor page loaded does not mark topic read`() = runTest {
        val page = ThemePage().apply {
            id = 903891
            pagination = Pagination().apply {
                current = 9
                all = 10
            }
            posts.add(ThemePost().apply { id = 100; canEdit = false })
        }

        themeUseCase.onNeighborPageLoaded(page)

        verify(exactly = 0) { crossScreenInteractor.onLoadTopic(any()) }
        verify(exactly = 0) { eventsRepository.onTopicRead(any()) }
    }

    @Test
    fun `hybrid top page on last topic does not mark topic read`() = runTest {
        val page = ThemePage().apply {
            id = 1106099
            pagination = Pagination().apply {
                current = 10
                all = 10
            }
            posts.add(ThemePost().apply { id = 200; canEdit = false })
        }

        themeUseCase.onNeighborPageLoaded(page)

        verify(exactly = 0) { crossScreenInteractor.onLoadTopic(any()) }
        verify(exactly = 0) { eventsRepository.onTopicRead(any()) }
    }

    @Test
    fun `theme load does not mark mention posts read before render`() = runTest {
        val page = ThemePage().apply {
            id = 7
            pagination = Pagination().apply {
                current = 1
                all = 1
            }
            posts.add(ThemePost().apply { id = 42 })
        }

        themeUseCase.onThemeLoaded(page)

        verify(exactly = 0) { eventsRepository.onTopicPostsRead(any(), any()) }
    }

    @Test
    fun `rendered topic posts notify read lifecycle`() = runTest {
        themeUseCase.onRenderedTopicPosts(7, listOf(42))

        verify(exactly = 1) { eventsRepository.onTopicPostsRead(7, listOf(42)) }
    }

    @Test
    fun `loadTheme with unread list hint does not consume prefetch parsed without hint`() = runTest(dispatcher) {
        val topicId = 928862
        val requestUrl = "https://4pda.to/forum/index.php?showtopic=$topicId&view=getnewpost"
        val finalUrl = "https://4pda.to/forum/index.php?showtopic=$topicId&st=104140#entry143799968"
        val wrongAnchorPage = ThemePage().apply {
            id = topicId
            this.url = finalUrl
            addAnchor("entry143799968")
            hasUnreadTarget = false
            pagination = Pagination().apply {
                current = 5208
                all = 5208
            }
        }
        val correctAnchorPage = ThemePage().apply {
            id = topicId
            this.url = finalUrl
            addAnchor("entry143799900")
            hasUnreadTarget = true
            pagination = wrongAnchorPage.pagination
        }
        val repository = mockk<ThemeRepository> {
            coEvery {
                getTheme(requestUrl, _withHtml = true, hatOpen = false, pollOpen = false, openFromUnreadListHint = false)
            } coAnswers {
                delay(20)
                wrongAnchorPage
            }
            coEvery {
                getTheme(requestUrl, _withHtml = true, hatOpen = false, pollOpen = false, openFromUnreadListHint = true)
            } returns correctAnchorPage
        }
        val prefetch = ThemePrefetchService(repository)
        val useCase = ThemeUseCase(
                themeRepository = repository,
                themeTemplate = themeTemplate,
                templateManager = templateManager,
                authHolder = authHolder,
                crossScreenInteractor = crossScreenInteractor,
                webClient = webClient,
                editorUseCase = editorUseCase,
                errorHandler = errorHandler,
                eventsRepository = eventsRepository,
                favoritesRepository = favoritesRepository,
                returnPositionStore = returnPositionStore,
                userHolder = userHolder,
                topicPreferencesHolder = topicPreferencesHolder,
                mainPreferencesHolder = mainPreferencesHolder,
                topicForumStore = topicForumStore,
                historyUnreadHarvester = historyUnreadHarvester,
                prefetchService = prefetch,
                appScope = appScope
        )

        prefetch.prefetchTopic(topicId, requestUrl, openFromUnreadListHint = false)
        dispatcher.scheduler.advanceUntilIdle()

        val result = useCase.loadTheme(requestUrl, hatOpen = false, pollOpen = false, openFromUnreadListHint = true)

        assertTrue(result is ThemeUseCase.LoadResult.Success)
        val page = (result as ThemeUseCase.LoadResult.Success).page
        assertEquals("entry143799900", page.anchor)
        assertTrue(page.hasUnreadTarget)
        coVerify(exactly = 1) {
            repository.getTheme(requestUrl, _withHtml = true, hatOpen = false, pollOpen = false, openFromUnreadListHint = true)
        }
    }

    @Test
    fun `loadTheme accepts warm ambiguous all-read without second network fetch log903891`() = runTest(dispatcher) {
        val topicId = 903891
        val requestUrl = "https://4pda.to/forum/index.php?showtopic=$topicId&view=getnewpost"
        val finalUrl = "https://4pda.to/forum/index.php?showtopic=$topicId&st=2740#entry143733850"
        val warmPage = ThemePage().apply {
            id = topicId
            this.url = finalUrl
            ambiguousLastUnreadBottomRedirect = true
            hasUnreadTarget = false
            openSessionKind = "AMBIGUOUS_ALL_READ"
            posts.add(ThemePost().apply { id = 143733850 })
            pagination = Pagination().apply {
                current = 10
                all = 10
            }
        }
        val repository = mockk<ThemeRepository> {
            coEvery {
                getTheme(requestUrl, _withHtml = true, hatOpen = false, pollOpen = false, openFromUnreadListHint = true)
            } coAnswers {
                delay(20)
                warmPage
            }
        }
        val prefetch = ThemePrefetchService(repository)
        val useCase = ThemeUseCase(
                themeRepository = repository,
                themeTemplate = themeTemplate,
                templateManager = templateManager,
                authHolder = authHolder,
                crossScreenInteractor = crossScreenInteractor,
                webClient = webClient,
                editorUseCase = editorUseCase,
                errorHandler = errorHandler,
                eventsRepository = eventsRepository,
                favoritesRepository = favoritesRepository,
                returnPositionStore = returnPositionStore,
                userHolder = userHolder,
                topicPreferencesHolder = topicPreferencesHolder,
                mainPreferencesHolder = mainPreferencesHolder,
                topicForumStore = topicForumStore,
                historyUnreadHarvester = historyUnreadHarvester,
                prefetchService = prefetch,
                appScope = appScope
        )

        prefetch.prefetchTopic(topicId, requestUrl, openFromUnreadListHint = true)
        dispatcher.scheduler.advanceUntilIdle()

        val result = useCase.loadTheme(requestUrl, hatOpen = false, pollOpen = false, openFromUnreadListHint = true)

        assertTrue(result is ThemeUseCase.LoadResult.Success)
        val page = (result as ThemeUseCase.LoadResult.Success).page
        assertEquals("AMBIGUOUS_ALL_READ", page.openSessionKind)
        assertTrue(page.ambiguousLastUnreadBottomRedirect)
        coVerify(exactly = 1) {
            repository.getTheme(requestUrl, _withHtml = true, hatOpen = false, pollOpen = false, openFromUnreadListHint = true)
        }
    }

    @Test
    fun `primary theme loaded does not prefetch editor`() = runTest {
        val page = ThemePage().apply {
            id = 903891
            pagination = Pagination().apply {
                current = 1
                all = 1
            }
            posts.add(ThemePost().apply { id = 100; canEdit = true })
        }

        themeUseCase.onPrimaryThemeLoaded(page)

        verify(exactly = 0) { editorUseCase.prefetchEditForPosts(any()) }
    }

    @Test
    fun `post open editor prefetch runs separately from primary load`() = runTest {
        val auth = mockk<forpdateam.ru.forpda.entity.common.AuthData>(relaxed = true) {
            every { isAuth() } returns true
            every { userId } returns 42
        }
        every { authHolder.get() } returns auth
        val page = ThemePage().apply {
            id = 903891
            posts.add(ThemePost().apply { id = 100; canEdit = true; userId = 42 })
        }

        themeUseCase.prefetchEditorForOpenedPage(page)

        verify(exactly = 1) { editorUseCase.prefetchEditForPosts(listOf(100)) }
    }

}
