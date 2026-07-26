package forpdateam.ru.forpda.model.data.remote.api.theme

import androidx.test.core.app.ApplicationProvider
import forpdateam.ru.forpda.client.proxy.BlockedTopicRegistry
import forpdateam.ru.forpda.entity.remote.others.pagination.Pagination
import forpdateam.ru.forpda.entity.remote.theme.ThemePage
import forpdateam.ru.forpda.entity.remote.theme.ThemePost
import forpdateam.ru.forpda.model.data.remote.IWebClient
import forpdateam.ru.forpda.model.data.remote.api.NetworkRequest
import forpdateam.ru.forpda.model.data.remote.api.NetworkResponse
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Тема, закрытая для российских IP, приходит заглушкой без постов. Если настроен прокси, [ThemeApi]
 * обязан повторить тот же запрос через него и запомнить тему, чтобы дальше она сразу шла этим
 * маршрутом. Обратный ход: тема, открывшаяся напрямую, из списка убирается.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ThemeApiProxyRetryTest {

    private val url = "https://4pda.to/forum/index.php?showtopic=777"
    private val topicId = 777

    private lateinit var webClient: IWebClient
    private lateinit var themeParser: ThemeParser
    private lateinit var registry: BlockedTopicRegistry

    @Before
    fun setUp() {
        MovedTopicResolver.clearForTests()
        registry = BlockedTopicRegistry(ApplicationProvider.getApplicationContext())
        registry.clear()
        webClient = mockk(relaxed = true)
        themeParser = mockk(relaxed = true)
        // Страницу отличаем по телу ответа: заглушка — без постов, «настоящая» — с постом.
        every { themeParser.parsePage(any(), any(), any(), any(), any()) } answers {
            val body = firstArg<String>()
            ThemePage().apply {
                this.url = secondArg()
                this.pagination = Pagination()
                if (body == REAL_PAGE) {
                    this.id = topicId
                    this.title = "Настоящее название темы"
                    this.posts.add(ThemePost().apply { id = 100500 })
                } else {
                    this.title = "Ой! Ошибка 404. Такой ссылки не существует"
                }
            }
        }
    }

    @After
    fun tearDown() {
        MovedTopicResolver.clearForTests()
        registry.clear()
    }

    private fun api() = ThemeApi(webClient, themeParser, null, registry)

    @Test
    fun `stub is retried via proxy and the topic is remembered`() {
        every { webClient.isProxyConfigured() } returns true
        every { webClient.get(any()) } returns NetworkResponse(url = url, code = 404, body = STUB_PAGE)
        every { webClient.request(any<NetworkRequest>()) } returns
                NetworkResponse(url = url, code = 200, body = REAL_PAGE)

        val page = api().getTheme(url, hatOpen = false, pollOpen = false)

        assertEquals(1, page.posts.size)
        assertEquals("Настоящее название темы", page.title)
        assertTrue("тема должна попасть в список «через прокси»", registry.isBlocked(topicId))
        verify { webClient.request(match<NetworkRequest> { it.forceProxy && it.url == url }) }
    }

    @Test
    fun `no proxy configured — stub stays a stub`() {
        every { webClient.isProxyConfigured() } returns false
        every { webClient.get(any()) } returns NetworkResponse(url = url, code = 404, body = STUB_PAGE)

        val page = api().getTheme(url, hatOpen = false, pollOpen = false)

        assertTrue(page.posts.isEmpty())
        assertFalse(registry.isBlocked(topicId))
        verify(exactly = 0) { webClient.request(any<NetworkRequest>()) }
    }

    @Test
    fun `stub via proxy too — тема правда удалена, лишний повтор не делаем`() {
        every { webClient.isProxyConfigured() } returns true
        every { webClient.get(any()) } returns NetworkResponse(url = url, code = 404, body = STUB_PAGE)
        every { webClient.request(any<NetworkRequest>()) } returns
                NetworkResponse(url = url, code = 404, body = STUB_PAGE)

        val page = api().getTheme(url, hatOpen = false, pollOpen = false)

        assertTrue(page.posts.isEmpty())
        assertFalse(registry.isBlocked(topicId))
    }

    @Test
    fun `expired entry is dropped when the topic opens directly`() {
        // Отметка старше окна ревалидации → запрос ушёл напрямую и удался: ограничение сняли.
        registry.remember(topicId, nowMs = System.currentTimeMillis() - BlockedTopicRegistry.REVALIDATE_AFTER_MS - 1)
        every { webClient.isProxyConfigured() } returns true
        every { webClient.get(any()) } returns NetworkResponse(url = url, code = 200, body = REAL_PAGE)

        api().getTheme(url, hatOpen = false, pollOpen = false)

        assertEquals(0, registry.size())
    }

    @Test
    fun `fresh entry survives a successful load — маршрут и был через прокси`() {
        registry.remember(topicId)
        every { webClient.isProxyConfigured() } returns true
        every { webClient.get(any()) } returns NetworkResponse(url = url, code = 200, body = REAL_PAGE)

        api().getTheme(url, hatOpen = false, pollOpen = false)

        assertTrue(registry.isBlocked(topicId))
    }

    private companion object {
        const val STUB_PAGE = "<html><body><h1>Ой! Ошибка 404. Такой ссылки не существует</h1></body></html>"
        const val REAL_PAGE = "<html><body>topic with posts</body></html>"
    }
}
