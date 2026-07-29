package forpdateam.ru.forpda.client.proxy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Маршрутизация запросов между прямым соединением и прокси. Ключевое требование режима
 * «только заблокированные темы»: через прокси уходит ВСЁ, что относится к закрытой теме, включая
 * отправку ответа — она идёт POST'ом на голый `index.php`, и id темы виден только в полях формы.
 */
class ProxyRouterTest {

    private val blocked = setOf(777)
    private fun isBlocked(id: Int) = id in blocked

    private fun route(
            url: String? = null,
            formHeaders: Map<String, String>? = null,
            mode: ProxyMode = ProxyMode.ONLY_BLOCKED_TOPICS,
            hasConfig: Boolean = true,
            force: Boolean = false,
    ) = ProxyRouter.shouldUseProxy(
            hasConfig = hasConfig,
            mode = mode,
            forceProxy = force,
            url = url,
            formTopicId = ProxyRouter.extractTopicIdFromForm(formHeaders),
            isTopicBlocked = ::isBlocked,
    )

    /**
     * Картинки и загрузки идут через прокси только в режиме «весь трафик»: тему по URL картинки не
     * определить, поэтому в режиме «только заблокированные темы» им остаётся прямой маршрут.
     */
    @Test
    fun `images and downloads follow the proxy only in all-traffic mode`() {
        val config = ProxyConfig(ProxyType.SOCKS5, "proxy.example", 1080)
        assertEquals(config, ProxyRouter.proxyForAllTraffic(config, ProxyMode.ALL))
        assertNull(ProxyRouter.proxyForAllTraffic(config, ProxyMode.ONLY_BLOCKED_TOPICS))
        assertNull(ProxyRouter.proxyForAllTraffic(null, ProxyMode.ALL))
    }

    @Test
    fun `without config everything goes direct`() {
        assertFalse(route(url = "https://4pda.to/forum/index.php?showtopic=777", hasConfig = false))
        assertFalse(route(url = "https://4pda.to/forum/index.php?showtopic=777", hasConfig = false, force = true))
        assertFalse(route(url = "https://4pda.to/forum/", mode = ProxyMode.ALL, hasConfig = false))
    }

    @Test
    fun `all mode routes every request`() {
        assertTrue(route(url = "https://4pda.to/forum/index.php?showtopic=1", mode = ProxyMode.ALL))
        assertTrue(route(url = "https://4pda.to/", mode = ProxyMode.ALL))
    }

    @Test
    fun `only blocked topics are routed`() {
        assertTrue(route(url = "https://4pda.to/forum/index.php?showtopic=777&st=40"))
        assertFalse(route(url = "https://4pda.to/forum/index.php?showtopic=778&st=40"))
        assertFalse(route(url = "https://4pda.to/forum/index.php?act=qms"))
    }

    @Test
    fun `forced request always uses proxy`() {
        assertTrue(route(url = "https://4pda.to/forum/index.php?showtopic=778", force = true))
    }

    @Test
    fun `reply to a blocked topic goes through proxy`() {
        // act=Post уходит на голый index.php: тема есть только в форме.
        val form = mapOf("act" to "Post", "CODE" to "03", "f" to "213", "t" to "777")
        assertTrue(route(url = "https://4pda.to/forum/index.php", formHeaders = form))

        val otherTopicForm = mapOf("act" to "Post", "t" to "778")
        assertFalse(route(url = "https://4pda.to/forum/index.php", formHeaders = otherTopicForm))
    }

    @Test
    fun `topic id is parsed from all url shapes`() {
        assertEquals(777, ProxyRouter.extractTopicId("https://4pda.to/forum/index.php?showtopic=777"))
        assertEquals(777, ProxyRouter.extractTopicId("https://4pda.to/forum/index.php?act=ST&f=213&t=777&st=40"))
        assertEquals(777, ProxyRouter.extractTopicId("https://4pda.to/forum/index.php/topic/777-some-topic/"))
        assertNull(ProxyRouter.extractTopicId("https://4pda.to/forum/index.php?showforum=213"))
        assertNull(ProxyRouter.extractTopicId(null))
    }
}
