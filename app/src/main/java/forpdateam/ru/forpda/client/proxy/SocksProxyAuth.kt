package forpdateam.ru.forpda.client.proxy

/**
 * Логин/пароль для SOCKS5-хендшейка.
 *
 * Передать их в SOCKS можно только через глобальный [java.net.Authenticator] — у OkHttp нет своего
 * пути (его `proxyAuthenticator` работает лишь с HTTP-прокси и заголовком `Proxy-Authorization`).
 * Глобальный Authenticator отвечает на ВСЕ вопросы о паролях в процессе, поэтому отдаём данные
 * только тогда, когда спрашивают ровно про наш прокси.
 *
 * ВАЖНО про [requestorType]: `SocksSocketImpl` спрашивает пароль шестиаргументным
 * `Authenticator.requestPasswordAuthentication(host, addr, port, "SOCKS5", …)`, а тот выставляет
 * `RequestorType.SERVER`, НЕ `PROXY` (проверено на JDK 17 и живьём на Android). Поэтому фильтровать
 * по `requestorType == PROXY` нельзя: так отсекались все SOCKS-запросы, JDK откатывался на
 * `user.name` с пустым паролем, и прокси отвечал «SOCKS : authentication failed». Вместо типа
 * сверяем протокол и адрес — это и точнее, и не зависит от версии JDK.
 */
object SocksProxyAuth {

    /** Протокол, с которым SOCKS-стек JDK спрашивает пароль. */
    private const val SOCKS_PROTOCOL_PREFIX = "SOCKS"

    /**
     * @return логин и пароль, если вопрос относится к настроенному SOCKS5-прокси с авторизацией;
     *   иначе null — тогда JDK/OkHttp просто не получит от нас данных.
     */
    fun credentialsFor(
            config: ProxyConfig?,
            requestingProtocol: String?,
            requestingHost: String?,
            requestingPort: Int,
    ): Pair<String, String>? {
        if (config == null || config.type != ProxyType.SOCKS5 || !config.hasCredentials) return null
        // null пропускаем: важен адрес, а строку протокола разные реализации могут не заполнить.
        if (requestingProtocol != null &&
                !requestingProtocol.startsWith(SOCKS_PROTOCOL_PREFIX, ignoreCase = true)) return null
        if (!config.host.equals(requestingHost, ignoreCase = true)) return null
        if (requestingPort != config.port) return null
        return config.login to config.password
    }
}
