package forpdateam.ru.forpda.model.interactors.theme

import forpdateam.ru.forpda.entity.remote.others.pagination.Pagination
import forpdateam.ru.forpda.entity.remote.theme.ThemePage
import forpdateam.ru.forpda.model.repository.theme.ThemeRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ThemePrefetchServiceTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun themePage(topicId: Int): ThemePage = ThemePage().apply {
        id = topicId
        url = "https://4pda.to/forum/index.php?showtopic=$topicId&view=getnewpost"
        pagination = Pagination().apply {
            current = 2
            all = 4
        }
    }

    @Test
    fun `prefetch warms cache for tap open without second network fetch`() = runTest(dispatcher) {
        val topicId = 42
        val url = "https://4pda.to/forum/index.php?showtopic=$topicId&view=getnewpost"
        val repository = mockk<ThemeRepository> {
            coEvery {
                getTheme(url, _withHtml = true, hatOpen = false, pollOpen = false, openFromUnreadListHint = false)
            } returns themePage(topicId)
        }
        val prefetch = ThemePrefetchService(repository)

        prefetch.prefetchTopic(topicId, url)
        runBlocking { delay(200) }

        val warmed = prefetch.tryConsumeWarm(url)
        assertNotNull(warmed)
        assertEquals(topicId, warmed?.id)
        coVerify(exactly = 1) {
            repository.getTheme(url, _withHtml = true, hatOpen = false, pollOpen = false, openFromUnreadListHint = false)
        }
    }

    @Test
    fun `cancelPrefetch keeps warm-up for tapped topic id`() = runTest(dispatcher) {
        val topicId = 99
        val url = "https://4pda.to/forum/index.php?showtopic=$topicId&view=getnewpost"
        val repository = mockk<ThemeRepository> {
            coEvery {
                getTheme(url, _withHtml = true, hatOpen = false, pollOpen = false, openFromUnreadListHint = false)
            } returns themePage(topicId)
        }
        val prefetch = ThemePrefetchService(repository)

        prefetch.prefetchTopic(topicId, url)
        prefetch.cancelPrefetch(exceptTopicId = topicId)
        runBlocking { delay(200) }

        assertNotNull(prefetch.tryConsumeWarm(url))
    }

    @Test
    fun `cancelPrefetch without except drops warm entry for another topic`() = runTest(dispatcher) {
        val firstUrl = "https://4pda.to/forum/index.php?showtopic=10&view=getnewpost"
        val secondUrl = "https://4pda.to/forum/index.php?showtopic=20&view=getnewpost"
        val repository = mockk<ThemeRepository> {
            coEvery {
                getTheme(firstUrl, _withHtml = true, hatOpen = false, pollOpen = false, openFromUnreadListHint = false)
            } returns themePage(10)
            coEvery {
                getTheme(secondUrl, _withHtml = true, hatOpen = false, pollOpen = false, openFromUnreadListHint = false)
            } coAnswers {
                delay(30)
                themePage(20)
            }
        }
        val prefetch = ThemePrefetchService(repository)

        prefetch.prefetchTopic(10, firstUrl)
        prefetch.cancelPrefetch()
        prefetch.prefetchTopic(20, secondUrl)
        runBlocking { delay(200) }

        assertNull(prefetch.tryConsumeWarm(firstUrl))
        assertNotNull(prefetch.tryConsumeWarm(secondUrl))
    }

    @Test
    fun `awaitWarm lets open reuse in-flight prefetch`() = runTest(dispatcher) {
        val topicId = 55
        val url = "https://4pda.to/forum/index.php?showtopic=$topicId&view=getnewpost"
        val repository = mockk<ThemeRepository> {
            coEvery {
                getTheme(url, _withHtml = true, hatOpen = false, pollOpen = false, openFromUnreadListHint = false)
            } coAnswers {
                delay(30)
                themePage(topicId)
            }
        }
        val prefetch = ThemePrefetchService(repository)

        prefetch.prefetchTopic(topicId, url)
        runBlocking {
            prefetch.awaitWarm(topicId, url)
        }
        val warmed = prefetch.tryConsumeWarm(url)

        assertTrue(warmed != null)
        coVerify(exactly = 1) {
            repository.getTheme(url, _withHtml = true, hatOpen = false, pollOpen = false, openFromUnreadListHint = false)
        }
    }

    @Test
    fun `prefetch unread list hint is passed to repository and consumed only with matching hint`() = runTest(dispatcher) {
        val topicId = 928862
        val url = "https://4pda.to/forum/index.php?showtopic=$topicId&view=getnewpost"
        val repository = mockk<ThemeRepository> {
            coEvery {
                getTheme(url, _withHtml = true, hatOpen = false, pollOpen = false, openFromUnreadListHint = true)
            } returns themePage(topicId)
        }
        val prefetch = ThemePrefetchService(repository)

        prefetch.prefetchTopic(topicId, url, openFromUnreadListHint = true)
        runBlocking { delay(200) }

        assertNull(prefetch.tryConsumeWarm(url, openFromUnreadListHint = false))
        val warmed = prefetch.tryConsumeWarm(url, openFromUnreadListHint = true)
        assertNotNull(warmed)
        coVerify(exactly = 1) {
            repository.getTheme(url, _withHtml = true, hatOpen = false, pollOpen = false, openFromUnreadListHint = true)
        }
    }
}
