package forpdateam.ru.forpda.model.data.remote.api.events
import timber.log.Timber

import forpdateam.ru.forpda.entity.remote.events.NotificationEvent
import forpdateam.ru.forpda.model.data.remote.IWebClient
import forpdateam.ru.forpda.model.data.remote.api.ApiUtils
import forpdateam.ru.forpda.model.data.remote.api.NetworkResponse
import java.util.regex.Matcher
import java.util.regex.Pattern

/**
 * Created by radiationx on 31.07.17.
 */

/**
 * Безопасные extension-функции для извлечения групп из Matcher.
 * Возвращают null вместо краша при отсутствии группы или ошибке парсинга.
 */
private fun Matcher.groupInt(group: Int): Int? {
    val value = this.group(group) ?: return null
    return value.toIntOrNull()
}

private fun Matcher.groupLong(group: Int): Long? {
    val value = this.group(group) ?: return null
    return value.toLongOrNull()
}

private fun Matcher.groupString(group: Int): String {
    return this.group(group) ?: ""
}

class NotificationEventsApi(private val webClient: IWebClient) {

    companion object {
        @JvmField
        val inspectorFavoritesPattern: Pattern = Pattern.compile("(\\d+) \"([\\s\\S]*?)\" (\\d+) (\\d+) \"([\\s\\S]*?)\" (\\d+) (\\d+) (\\d+)")
        @JvmField
        val inspectorQmsPattern: Pattern = Pattern.compile("(\\d+) \"([\\s\\S]*?)\" (\\d+) \"([\\s\\S]*?)\" (\\d+) (\\d+) (\\d+)")
        @JvmField
        val webSocketEventPattern: Pattern = Pattern.compile("\\[(\\d+),(\\d+),\"([\\s\\S])(\\d+)\",(\\d+),(\\d+)\\]")
        private const val FAV_INSPECTOR_URL = "https://4pda.to/forum/index.php?act=inspector&CODE=fav"
        private const val QMS_INSPECTOR_URL = "https://4pda.to/forum/index.php?act=inspector&CODE=qms"
        private const val INSPECTOR_CACHE_TTL_MS = 8000L
    }

    /**
     * На возврате приложения на передний план опрос inspector'а запрашивают три независимых
     * места подряд. Короткий TTL схлопывает этот всплеск в один сетевой запрос, а
     * [fetchInProgress] не даёт параллельным вызовам продублировать его.
     */
    private inner class InspectorCache(
            private val url: String,
            private val parse: (String) -> List<NotificationEvent>
    ) {
        private val lock = Any()
        private var cached: List<NotificationEvent>? = null
        private var cachedAtMs: Long = 0L
        private var fetchInProgress = false

        fun invalidate() {
            synchronized(lock) {
                cached = null
                cachedAtMs = 0L
            }
        }

        @Throws(Exception::class)
        fun get(): List<NotificationEvent> {
            synchronized(lock) {
                while (fetchInProgress) {
                    try {
                        (lock as Object).wait(30000L)
                    } catch (ie: InterruptedException) {
                        Thread.currentThread().interrupt()
                        throw ie
                    }
                }
                val snapshot = cached
                if (snapshot != null && (System.currentTimeMillis() - cachedAtMs) < INSPECTOR_CACHE_TTL_MS) {
                    return ArrayList(snapshot)
                }
                fetchInProgress = true
            }
            try {
                // Кэш-бастер: CDN 4PDA кэширует даже авторизованные GET (см. фикс bottom-refresh
                // с «&s=»). Без него фоновый опрос мог получать стейл-ответ «ничего нового».
                val response: NetworkResponse = webClient.get("$url&s=${System.currentTimeMillis()}")
                val parsed = parse(response.body)
                synchronized(lock) {
                    cached = ArrayList(parsed)
                    cachedAtMs = System.currentTimeMillis()
                    fetchInProgress = false
                    (lock as Object).notifyAll()
                }
                return ArrayList(parsed)
            } catch (e: Exception) {
                synchronized(lock) {
                    fetchInProgress = false
                    (lock as Object).notifyAll()
                }
                throw e
            }
        }
    }

    private val favInspectorCache = InspectorCache(FAV_INSPECTOR_URL) { getFavoritesEvents(it) }
    private val qmsInspectorCache = InspectorCache(QMS_INSPECTOR_URL) { getQmsEvents(it) }

    fun invalidateFavoritesInspectorCache() {
        favInspectorCache.invalidate()
    }

    fun parseWebSocketEvent(message: String): NotificationEvent? {
        val matcher = webSocketEventPattern.matcher(message)
        return parseWebSocketEvent(matcher)
    }

    fun parseWebSocketEvent(matcher: Matcher): NotificationEvent? {
        var wsEvent: NotificationEvent? = null
        if (matcher.find()) {
            wsEvent = NotificationEvent(NotificationEvent.Type.NEW, NotificationEvent.Source.THEME)
            when (matcher.group(3)) {
                NotificationEvent.SRC_TYPE_THEME -> wsEvent.source = NotificationEvent.Source.THEME
                NotificationEvent.SRC_TYPE_SITE -> wsEvent.source = NotificationEvent.Source.SITE
                NotificationEvent.SRC_TYPE_QMS -> wsEvent.source = NotificationEvent.Source.QMS
                else -> return null
            }
            wsEvent.sourceId = matcher.groupInt(4) ?: return null
            when (matcher.groupInt(5)) {
                NotificationEvent.SRC_EVENT_NEW -> wsEvent.type = NotificationEvent.Type.NEW
                NotificationEvent.SRC_EVENT_READ -> wsEvent.type = NotificationEvent.Type.READ
                NotificationEvent.SRC_EVENT_MENTION -> wsEvent.type = NotificationEvent.Type.MENTION
                NotificationEvent.SRC_EVENT_HAT_EDITED -> wsEvent.type = NotificationEvent.Type.HAT_EDITED
                else -> return null
            }
            wsEvent.messageId = matcher.groupInt(6) ?: return null
        }
        return wsEvent
    }

    @Throws(Exception::class)
    fun getFavoritesEvents(): List<NotificationEvent> = favInspectorCache.get()

    fun getFavoritesEvents(response: String): List<NotificationEvent> {
        val events = mutableListOf<NotificationEvent>()
        val matcher = inspectorFavoritesPattern.matcher(response)
        while (matcher.find()) {
            getFavoritesEvent(matcher)?.let { events.add(it) }
        }
        return events
    }

    fun getFavoritesEvent(matcher: Matcher): NotificationEvent? {
        val event = NotificationEvent(NotificationEvent.Type.NEW, NotificationEvent.Source.THEME)
        event.sourceEventText = matcher.group()
        event.source = NotificationEvent.Source.THEME
        event.type = NotificationEvent.Type.NEW
        event.sourceId = matcher.groupInt(1) ?: return null
        event.sourceTitle = ApiUtils.fromHtml(matcher.group(2)).orEmpty()
        event.msgCount = matcher.groupInt(3) ?: return null
        event.userId = matcher.groupInt(4) ?: return null
        event.userNick = ApiUtils.fromHtml(matcher.group(5)).orEmpty()
        event.timeStamp = matcher.groupLong(6) ?: return null
        event.lastTimeStamp = matcher.groupLong(7) ?: return null
        event.isImportant = matcher.group(8) == "1"
        return event
    }

    @Throws(Exception::class)
    fun getQmsEvents(): List<NotificationEvent> = qmsInspectorCache.get()

    fun getQmsEvents(response: String): List<NotificationEvent> {
        val events = mutableListOf<NotificationEvent>()
        val matcher = inspectorQmsPattern.matcher(response)
        while (matcher.find()) {
            getQmsEvent(matcher)?.let { events.add(it) }
        }
        return events
    }

    fun getQmsEvent(matcher: Matcher): NotificationEvent? {
        val event = NotificationEvent(NotificationEvent.Type.NEW, NotificationEvent.Source.QMS)
        event.sourceEventText = matcher.group()
        event.source = NotificationEvent.Source.QMS
        event.type = NotificationEvent.Type.NEW
        event.sourceId = matcher.groupInt(1) ?: return null
        event.sourceTitle = ApiUtils.fromHtml(matcher.group(2)).orEmpty()
        event.userId = matcher.groupInt(3) ?: return null
        event.userNick = ApiUtils.fromHtml(matcher.group(4)).orEmpty()
        event.timeStamp = matcher.groupLong(5) ?: return null
        event.msgCount = matcher.groupInt(6) ?: return null
        if (event.userNick.isEmpty() && event.sourceId == 0) {
            event.userNick = "Сообщения 4PDA"
        }
        return event
    }
}
