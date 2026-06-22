package forpdateam.ru.forpda.model.data.remote

import forpdateam.ru.forpda.model.data.remote.api.NetworkRequest
import forpdateam.ru.forpda.model.data.remote.api.NetworkResponse
import okhttp3.Cookie
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.regex.Pattern

/**
 * Интерфейс HTTP клиента для работы с API 4pda.
 * 
 * Улучшения в Kotlin-версии:
 * - companion object вместо статических полей
 * - Функциональный тип для ProgressListener
 */
interface IWebClient {
    
    /**
     * Лёгкий GET индекса форума; клиент обновляет счётчики меню из HTML. 
     * Ошибки сети игнорируются.
     */
    fun refreshMenuCountersSilently() {
        try {
            get(COUNTERS_REFRESH_URL)
        } catch (_: Exception) {
            // ignore
        }
    }

    @Throws(Exception::class)
    fun get(url: String): NetworkResponse

    @Throws(Exception::class)
    fun request(request: NetworkRequest): NetworkResponse

    @Throws(Exception::class)
    fun request(request: NetworkRequest, progressListener: ProgressListener?): NetworkResponse

    @Throws(Exception::class)
    fun requestWithoutMobileCookie(request: NetworkRequest): NetworkResponse = request(request)

    fun getAuthKey(): String

    fun getClientCookies(): Map<String, Cookie>

    fun clearCookies()

    /**
     * Сбросить DNS-кеш. Вызывается при смене/потере сети, чтобы не долбиться
     * в мёртвые адреса. Реализация по умолчанию — no-op для моков/тестов.
     */
    fun clearDnsCache() {}

    fun createWebSocketConnection(webSocketListener: WebSocketListener): WebSocket

    /**
     * Функциональный интерфейс для отслеживания прогресса загрузки.
     * В Kotlin можно использовать лямбду: { percent -> ... }
     */
    fun interface ProgressListener {
        fun onProgress(percent: Int)
    }

    companion object {
        /**
         * Паттерн для извлечения счётчиков уведомлений из HTML.
         * Группы: 1 - mentions, 2 - favorites, 3 - qms
         */
        val countsPattern: Pattern = Pattern.compile(
            "<a href=\"(?:https?)?\\/\\/4pda\\.to\\/forum\\/index\\.php\\?act=mentions\" (?:data-count=\"(\\d+)\")?[^>]*?[\\s\\S]*?act=fav&amp;code=no\" (?:data-count=\"(\\d+)\")?[^>]*?[\\s\\S]*?span id=\"events-count\"[\\s\\S]*?(?:data-count=\"(\\d+)\")"
        )

        /**
         * Более устойчивые паттерны для извлечения счётчиков из изменяющейся шапки форума.
         * Используются как fallback, если [countsPattern] перестал совпадать.
         */
        val mentionsCountPattern: Pattern = Pattern.compile(
            "(?i)act=mentions[^\"']*[\"'][^>]*?\\bdata-count\\s*=\\s*\"(\\d+)\""
        )
        val favoritesCountPattern: Pattern = Pattern.compile(
            // Важно: act=fav встречается в нескольких местах; нам нужен именно счётчик пункта «Избранное»
            // (обычно с code=no). Иначе можем вытащить нерелевантный data-count (подписки/уведомления) и получить скачки.
            "(?i)act=fav(?:&amp;|&)code=no[^\"']*[\"'][^>]*?\\bdata-count\\s*=\\s*\"(\\d+)\""
        )
        /** В разных версиях шапки QMS может быть либо по id events-count, либо по ссылке act=qms. */
        val qmsCountPattern: Pattern = Pattern.compile(
            "(?i)(?:\\bid\\s*=\\s*\"events-count\"[^>]*?\\bdata-count\\s*=\\s*\"(\\d+)\"|act=qms[^\"']*[\"'][^>]*?\\bdata-count\\s*=\\s*\"(\\d+)\")"
        )

        /**
         * Паттерн для извлечения ошибок форума из HTML.
         */
        val errorPattern: Pattern = Pattern.compile(
            "^[\\s\\S]*?wr va-m text\">([\\s\\S]*?)</div></div></div></div><div class=\"footer\">"
        )

        const val MINIMAL_PAGE: String = "https://4pda.to/forum/index.php?showforum=200#afterauth"
        
        /**
         * Страница с шапкой форума и data-count для ответов / избранного / QMS.
         */
        const val COUNTERS_REFRESH_URL: String = "https://4pda.to/forum/index.php"
    }
}
