package forpdateam.ru.forpda.client.proxy

import java.net.InetSocketAddress
import java.net.Proxy

/**
 * Настройки прокси для запросов к 4PDA.
 *
 * Зачем: часть тем 4PDA закрыта для российских IP — origin отдаёт заглушку «Ошибка 404 / такой
 * ссылки не существует» (см. [forpdateam.ru.forpda.model.data.remote.api.theme.ThemeApi]). Обойти
 * это можно только запросом с другого адреса, но системный VPN для этого не обязателен: достаточно
 * пустить через прокси трафик ОДНОГО приложения — а в режиме [ProxyMode.ONLY_BLOCKED_TOPICS] даже
 * только те темы, которые реально закрыты.
 *
 * ВАЖНО: через прокси уходят cookies авторизации 4PDA, поэтому прокси должен быть свой.
 */
data class ProxyConfig(
        val type: ProxyType,
        val host: String,
        val port: Int,
        val login: String = "",
        val password: String = "",
) {
    val hasCredentials: Boolean get() = login.isNotEmpty()

    fun toJavaProxy(): Proxy = Proxy(type.javaType, InetSocketAddress.createUnresolved(host, port))

    /** Строка правила для WebView (`androidx.webkit.ProxyConfig`), без логина/пароля — WebView их не умеет. */
    fun toWebViewRule(): String = "${type.scheme}://$host:$port"

    /** Человекочитаемо для summary настройки; пароль не показываем никогда. */
    fun describe(): String = buildString {
        append(type.title).append(' ').append(host).append(':').append(port)
        if (hasCredentials) append(" (").append(login).append(')')
    }

    companion object {
        /** null, если данных не хватает или они бессмысленны — вызывающий трактует это как «прокси не настроен». */
        fun from(type: ProxyType, host: String?, port: String?, login: String?, password: String?): ProxyConfig? {
            val cleanHost = host?.trim().orEmpty()
            val cleanPort = port?.trim()?.toIntOrNull() ?: return null
            if (cleanHost.isEmpty() || cleanPort !in 1..65535) return null
            return ProxyConfig(
                    type = type,
                    host = cleanHost,
                    port = cleanPort,
                    login = login?.trim().orEmpty(),
                    password = password.orEmpty(),
            )
        }
    }
}

enum class ProxyType(val key: String, val title: String, val scheme: String, val javaType: Proxy.Type) {
    SOCKS5("socks5", "SOCKS5", "socks5", Proxy.Type.SOCKS),
    HTTP("http", "HTTP", "http", Proxy.Type.HTTP);

    companion object {
        fun fromKey(key: String?): ProxyType = entries.firstOrNull { it.key == key } ?: SOCKS5
    }
}

/** Что именно пускать через прокси. */
enum class ProxyMode {
    /** Весь трафик приложения к 4PDA, без разбора URL — простой предсказуемый режим. */
    ALL,

    /** Только темы, про которые уже известно, что напрямую они отдают заглушку. */
    ONLY_BLOCKED_TOPICS,
}
