package forpdateam.ru.forpda.client.proxy

/**
 * Решает, каким маршрутом отправить запрос: напрямую или через прокси.
 *
 * Правила (по убыванию приоритета):
 *  1. Прокси не настроен → всегда напрямую.
 *  2. `forceProxy` у запроса → через прокси. Это автоповтор заглушки в
 *     [forpdateam.ru.forpda.model.data.remote.api.theme.ThemeApi] и ручное «открыть через прокси».
 *  3. Режим [ProxyMode.ALL] → через прокси, без разбора URL.
 *  4. Режим [ProxyMode.ONLY_BLOCKED_TOPICS] → через прокси, только если запрос относится к теме
 *     из [BlockedTopicRegistry].
 *
 * Тема определяется по URL (`showtopic=`, `?t=`, `/topic/<id>`) и по полю формы `t` — отправка
 * сообщения уходит POST'ом на голый `index.php`, id темы лежит только в теле формы. Без этого
 * ответ в закрытую тему ушёл бы напрямую и получил ту же заглушку, потеряв набранный текст.
 */
object ProxyRouter {

    private val topicIdInQuery = Regex("""[?&](?:showtopic|t)=(\d+)""", RegexOption.IGNORE_CASE)
    private val topicIdInPath = Regex("""(?:forum/index\.php/topic/|/topic/)(\d+)""", RegexOption.IGNORE_CASE)

    fun shouldUseProxy(
            hasConfig: Boolean,
            mode: ProxyMode,
            forceProxy: Boolean,
            url: String?,
            formTopicId: Int?,
            isTopicBlocked: (Int) -> Boolean,
    ): Boolean {
        if (!hasConfig) return false
        if (forceProxy) return true
        if (mode == ProxyMode.ALL) return true
        val topicId = formTopicId?.takeIf { it > 0 } ?: extractTopicId(url) ?: return false
        return isTopicBlocked(topicId)
    }

    /**
     * Маршрут для клиентов, которые не разбирают запрос: картинки и загрузка файлов. Тему по URL
     * картинки не определить, поэтому «только заблокированные темы» для них означает прямой путь,
     * а прокси включается лишь в режиме «весь трафик приложения».
     *
     * @return конфиг, если этим клиентам сейчас положено идти через прокси, иначе null.
     */
    fun proxyForAllTraffic(config: ProxyConfig?, mode: ProxyMode): ProxyConfig? =
            config?.takeIf { mode == ProxyMode.ALL }

    /** id темы из URL запроса, если он вообще относится к теме. */
    fun extractTopicId(url: String?): Int? {
        if (url.isNullOrBlank()) return null
        topicIdInQuery.find(url)?.groupValues?.getOrNull(1)?.toIntOrNull()?.let { return it }
        return topicIdInPath.find(url)?.groupValues?.getOrNull(1)?.toIntOrNull()
    }

    /**
     * id темы из полей формы. `t` кладут и отправка сообщения (`act=Post`), и голосование в опросе,
     * и жалоба — во всех случаях это именно тема, к которой относится действие.
     */
    fun extractTopicIdFromForm(formHeaders: Map<String, String>?): Int? =
            formHeaders?.entries
                    ?.firstOrNull { it.key.equals("t", ignoreCase = true) }
                    ?.value
                    ?.trim()
                    ?.toIntOrNull()
                    ?.takeIf { it > 0 }
}
