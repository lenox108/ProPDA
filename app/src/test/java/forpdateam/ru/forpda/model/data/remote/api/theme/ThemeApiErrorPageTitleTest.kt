package forpdateam.ru.forpda.model.data.remote.api.theme

import forpdateam.ru.forpda.entity.remote.others.pagination.Pagination
import forpdateam.ru.forpda.entity.remote.theme.ThemePage
import forpdateam.ru.forpda.entity.remote.theme.ThemePost
import forpdateam.ru.forpda.model.data.remote.IWebClient
import forpdateam.ru.forpda.model.data.remote.api.NetworkResponse
import io.mockk.every
import io.mockk.mockk
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Закрытая (недоступная без VPN) тема отдаёт страницу-заглушку: постов нет, а `<h1>`/`og:title`
 * содержат «Ошибка 404 / такой ссылки не существует». Парсер честно кладёт этот текст в
 * [ThemePage.title], и раньше он уезжал в заголовок вкладки и в «Историю» как название темы —
 * после включения VPN оно там и оставалось. [ThemeApi] обязан гасить заголовок страницы без постов.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ThemeApiErrorPageTitleTest {

    private val url = "https://4pda.to/forum/index.php?showtopic=1234567"

    private lateinit var webClient: IWebClient
    private lateinit var themeParser: ThemeParser

    @Before
    fun setUp() {
        MovedTopicResolver.clearForTests()
        webClient = mockk(relaxed = true)
        themeParser = mockk(relaxed = true)
    }

    @After
    fun tearDown() {
        MovedTopicResolver.clearForTests()
    }

    @Test
    fun dropsTitleOfPageWithoutPosts() {
        every { webClient.get(url) } returns NetworkResponse(
                url = url,
                code = 404,
                body = "<html><body><h1>Ой! Ошибка 404. Такой ссылки не существует</h1></body></html>"
        )
        every { themeParser.parsePage(any(), any(), any(), any(), any()) } answers {
            ThemePage().apply {
                this.url = secondArg()
                this.title = "Ой! Ошибка 404. Такой ссылки не существует"
                this.desc = "заглушка"
                this.pagination = Pagination()
            }
        }

        val page = ThemeApi(webClient, themeParser).getTheme(url, hatOpen = false, pollOpen = false)

        assertNull(page.title)
        assertNull(page.desc)
    }

    @Test
    fun keepsTitleOfRealTopicPage() {
        every { webClient.get(url) } returns NetworkResponse(url = url, code = 200, body = "<html>topic</html>")
        every { themeParser.parsePage(any(), any(), any(), any(), any()) } answers {
            ThemePage().apply {
                this.url = secondArg()
                this.id = 1234567
                this.title = "Настоящее название темы"
                this.posts.add(ThemePost().apply { id = 42 })
                this.pagination = Pagination()
            }
        }

        val page = ThemeApi(webClient, themeParser).getTheme(url, hatOpen = false, pollOpen = false)

        assertEquals("Настоящее название темы", page.title)
    }
}
