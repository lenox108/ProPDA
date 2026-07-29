package forpdateam.ru.forpda.client.proxy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Отдача логина/пароля в SOCKS5-хендшейк.
 *
 * Регрессия, ради которой тест написан: раньше здесь стояла проверка `requestorType == PROXY`, а
 * SOCKS-стек JDK спрашивает пароль с `requestorType = SERVER`. Из-за этого приложение НИ РАЗУ не
 * отдавало пароль, JDK подставлял системный `user.name` с пустым паролем, и любой SOCKS5-прокси с
 * авторизацией отвечал «SOCKS : authentication failed» (воспроизведено живьём на Android и на JDK 17).
 * Поэтому здесь проверяется адрес и протокол, а не тип запроса.
 */
class SocksProxyAuthTest {

    private val socks = ProxyConfig(ProxyType.SOCKS5, "proxy.example", 1080, "user", "secret")

    @Test
    fun `gives credentials to the configured socks proxy`() {
        val credentials = SocksProxyAuth.credentialsFor(socks, "SOCKS5", "proxy.example", 1080)
        assertEquals("user" to "secret", credentials)
    }

    /** Строку протокола заполняет не каждая реализация — адреса достаточно. */
    @Test
    fun `missing protocol does not block credentials`() {
        assertEquals("user" to "secret", SocksProxyAuth.credentialsFor(socks, null, "proxy.example", 1080))
    }

    @Test
    fun `never leaks credentials to another host or port`() {
        assertNull(SocksProxyAuth.credentialsFor(socks, "SOCKS5", "4pda.to", 1080))
        assertNull(SocksProxyAuth.credentialsFor(socks, "SOCKS5", "proxy.example", 9050))
    }

    @Test
    fun `stays silent for non-socks questions`() {
        assertNull(SocksProxyAuth.credentialsFor(socks, "basic", "proxy.example", 1080))
    }

    @Test
    fun `http proxy is authenticated by okhttp, not here`() {
        val http = socks.copy(type = ProxyType.HTTP)
        assertNull(SocksProxyAuth.credentialsFor(http, "SOCKS5", "proxy.example", 1080))
    }

    @Test
    fun `no config or no login means nothing to give`() {
        assertNull(SocksProxyAuth.credentialsFor(null, "SOCKS5", "proxy.example", 1080))
        assertNull(SocksProxyAuth.credentialsFor(socks.copy(login = ""), "SOCKS5", "proxy.example", 1080))
    }
}
