package forpdateam.ru.forpda.model.data.remote.api.theme

import forpdateam.ru.forpda.entity.remote.theme.ThemePage
import forpdateam.ru.forpda.entity.remote.theme.ThemePost
import forpdateam.ru.forpda.entity.remote.others.pagination.Pagination
import forpdateam.ru.forpda.model.data.remote.IWebClient
import forpdateam.ru.forpda.model.data.remote.api.NetworkResponse
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import io.mockk.verifyOrder
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Cценарий бага «Beholder 404» из ветки Android - Игры (forum 213):
 *  - В списке тема-указатель: `?showtopic=1121632&view=getnewpost`.
 *  - Сервер 4PDA отдаёт **404 без redirect-подсказок** (canonical/meta-refresh/href нет в теле).
 *  - На «голый» `?showtopic=1121632` сервер возвращает 302 → `?showtopic=1121568` (новый id).
 *
 * После исправления `ThemeApi` должен:
 *  1) Получить 404 на `view=getnewpost`.
 *  2) Сделать strip-probe `?showtopic=1121632`.
 *  3) Прочитать новый id из `response.redirect`.
 *  4) Перезапросить `?showtopic=1121568&view=getnewpost` и распарсить эту страницу.
 *  5) Запомнить mapping в [MovedTopicResolver].
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ThemeApiMovedTopicProbeTest {

    private val originalUrl = "https://4pda.to/forum/index.php?showtopic=1121632&view=getnewpost"
    private val strippedProbeUrl = "https://4pda.to/forum/index.php?showtopic=1121632"
    private val redirectedProbeUrl = "https://4pda.to/forum/index.php?showtopic=1121568"
    private val finalRetryUrl = "https://4pda.to/forum/index.php?showtopic=1121568&view=getnewpost"

    private lateinit var webClient: IWebClient
    private lateinit var themeParser: ThemeParser

    @Before
    fun setUp() {
        MovedTopicResolver.clearForTests()
        webClient = mockk(relaxed = true)
        themeParser = mockk(relaxed = true)
        every { webClient.get(any()) } answers { NetworkResponse(url = firstArg()) }
        // По умолчанию парсер возвращает пустую тему — это эмулирует «404 без постов».
        every { themeParser.parsePage(any(), any(), any(), any(), any()) } answers {
            ThemePage().apply {
                this.url = secondArg()
                this.pagination = Pagination()
            }
        }
    }

    @After
    fun tearDown() {
        MovedTopicResolver.clearForTests()
    }

    @Test
    fun strippedProbeFollowsRedirectAndRetriesOnNewId() {
        // 1) первый запрос (с view=getnewpost) — 404 без подсказок
        every { webClient.get(originalUrl) } returns NetworkResponse(
                url = originalUrl,
                code = 404,
                body = """<html><body><h1>Ой! Ошибка 404.</h1></body></html>"""
        )
        // 2) strip-probe — сервер 302-редиректит на новый id, OkHttp прозрачно следует редиректу,
        //    итоговый response.redirect содержит новый showtopic.
        every { webClient.get(strippedProbeUrl) } returns NetworkResponse(
                url = strippedProbeUrl,
                code = 200,
                redirect = redirectedProbeUrl,
                locationHeader = redirectedProbeUrl,
                body = "<html>topic page</html>"
        )
        // 3) повторный запрос «showtopic=NEW&view=getnewpost» — нормальная тема
        every { webClient.get(finalRetryUrl) } returns NetworkResponse(
                url = finalRetryUrl,
                code = 200,
                redirect = finalRetryUrl,
                body = "<html>topic page with posts</html>"
        )
        // парсер для финального URL возвращает страницу с постами
        every { themeParser.parsePage(any(), finalRetryUrl, any(), any(), any()) } answers {
            ThemePage().apply {
                this.url = finalRetryUrl
                this.id = 1121568
                this.posts.add(ThemePost().apply { id = 4242 })
                this.pagination = Pagination()
            }
        }

        val api = ThemeApi(webClient, themeParser)
        val page = api.getTheme(originalUrl, hatOpen = false, pollOpen = false)

        assertEquals(1121568, page.id)
        assertEquals(1, page.posts.size)
        // mapping сохранён в кэше — следующий запрос с тем же OLD-id попадёт сразу на NEW.
        assertEquals(1121568, MovedTopicResolver.resolve(1121632))

        verifyOrder {
            webClient.get(originalUrl)
            webClient.get(strippedProbeUrl)
            webClient.get(finalRetryUrl)
        }
    }

    @Test
    fun cachedMappingShortCircuitsFutureRequests() {
        MovedTopicResolver.remember(oldTopicId = 1121632, newTopicId = 1121568)

        every { webClient.get(finalRetryUrl) } returns NetworkResponse(
                url = finalRetryUrl,
                code = 200,
                redirect = finalRetryUrl,
                body = "<html>topic page</html>"
        )
        every { themeParser.parsePage(any(), any(), any(), any(), any()) } answers {
            ThemePage().apply {
                this.url = secondArg()
                this.id = 1121568
                this.posts.add(ThemePost().apply { id = 1 })
                this.pagination = Pagination()
            }
        }

        val api = ThemeApi(webClient, themeParser)
        api.getTheme(originalUrl, hatOpen = false, pollOpen = false)

        // Должен идти сразу на новый id, миная strip-probe.
        verify(exactly = 0) { webClient.get(originalUrl) }
        verify(exactly = 0) { webClient.get(strippedProbeUrl) }
        verify(atLeast = 1) { webClient.get(finalRetryUrl) }
    }

    @Test
    fun strippedProbeReturningSameIdIsIgnored() {
        every { webClient.get(originalUrl) } returns NetworkResponse(
                url = originalUrl,
                code = 404,
                body = "<html><body>404</body></html>"
        )
        // strip-probe тоже даёт 404 / возвращает тот же id — относиться к нему как к настоящему 404
        every { webClient.get(strippedProbeUrl) } returns NetworkResponse(
                url = strippedProbeUrl,
                code = 404,
                redirect = strippedProbeUrl,
                body = "<html><body>404</body></html>"
        )

        val api = ThemeApi(webClient, themeParser)
        val page = api.getTheme(originalUrl, hatOpen = false, pollOpen = false)

        // Ничего не упало, в кэш ничего не положили.
        assertTrue(page.posts.isEmpty())
        assertEquals(null, MovedTopicResolver.resolve(1121632))
    }
}
