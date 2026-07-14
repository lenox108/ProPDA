package forpdateam.ru.forpda.entity.remote.search

import android.net.Uri
import forpdateam.ru.forpda.common.Cp1251Codec
import java.net.URLDecoder
import java.nio.charset.Charset

/**
 * Настройки поиска 4pda (форум / новости).
 *
 * Форумный поиск (legacy IPB) требует `query` в windows-1251.
 * Для поиска по пользователю предпочитаем `username-id`: так ники с emoji
 * не попадают в legacy-поле `username`, которое сервер может обработать
 * через cp1251 и превратить символы вне кодировки в `?`.
 */
class SearchSettings {

    var resourceType: String = RESOURCE_FORUM.first
    var result: String = RESULT_TOPICS.first
    var sort: String = SORT_DD.first
    var source: String = SOURCE_TITLES.first
    var query: String = ""
    var nick: String? = ""
    var userId: Int = 0
    var subforums: String? = SUB_FORUMS_TRUE
    var excludeTrash: Int = 0
    var st: Int = 0

    val forums: MutableList<String> = mutableListOf()
    val topics: MutableList<String> = mutableListOf()

    fun addForum(forum: String) {
        forums.add(forum)
    }

    fun addTopic(topic: String) {
        topics.add(topic)
    }

    fun toUrl(): String = toUrl(this)

    companion object {
        private val ARGS_PATTERN = Regex("(?:\\?|&)([^=]*?)=([\\s\\S]*?)(?=&| |$)")

        val RESOURCE_NEWS = "news" to "Новости"
        val RESOURCE_FORUM = "forum" to "Форум"

        const val ARG_RESULT = "result"
        const val ARG_SORT = "sort"
        const val ARG_SOURCE = "source"
        const val ARG_QUERY_FORUM = "query"
        const val ARG_QUERY_NEWS = "s"
        const val ARG_NICK = "username"
        const val ARG_FORUMS_SIMPLE = "forums"
        const val ARG_TOPICS_SIMPLE = "topics"
        const val ARG_FORUMS = "forums[]"
        const val ARG_TOPICS = "topics[]"
        const val ARG_SUB_FORUMS = "subforums"
        const val ARG_NO_FORM = "noform"
        const val ARG_ST = "st"
        const val ARG_USER_ID = "username-id"
        const val ARG_EXCLUDE_TRASH = "exclude_trash"

        val RESULT_TOPICS = "topics" to "Темы"
        val RESULT_POSTS = "posts" to "Сообщения"

        val SORT_DA = "da" to "Возрастание даты"
        val SORT_DD = "dd" to "Убывание даты"
        val SORT_REL = "rel" to "Соответствие"

        val SOURCE_ALL = "all" to "Везде"
        val SOURCE_TITLES = "top" to "Заголовки"
        val SOURCE_CONTENT = "pst" to "Содержание"

        const val SUB_FORUMS_TRUE = "1"
        const val SUB_FORUMS_FALSE = "0"

        @JvmStatic
        fun parseSettings(url: String): SearchSettings = parseSettings(SearchSettings(), url)

        @JvmStatic
        fun parseSettings(settings: SearchSettings, url: String): SearchSettings {
            for (match in ARGS_PATTERN.findAll(url)) {
                val rawName = match.groupValues[1].lowercase()
                val name = runCatching { URLDecoder.decode(rawName, "UTF-8") }.getOrDefault(rawName)
                val value = match.groupValues[2]

                when (name) {
                    ARG_ST -> value.toIntOrNull()?.let { settings.st = it }
                    ARG_RESULT -> settings.result = value
                    ARG_SORT -> settings.sort = value
                    ARG_SOURCE -> settings.source = value
                    ARG_QUERY_FORUM -> {
                        settings.resourceType = RESOURCE_FORUM.first
                        // decodeAuto, не decode: свой поиск шлёт cp1251, а ссылки-теги под постом 4pda
                        // отдаёт в UTF-8 — жёсткий cp1251-декод превращал тег в мохибейк.
                        settings.query = Cp1251Codec.decodeAuto(value)
                    }
                    ARG_QUERY_NEWS -> {
                        settings.resourceType = RESOURCE_NEWS.first
                        settings.query = Cp1251Codec.decodeAuto(value)
                    }
                    ARG_NICK -> {
                        settings.nick = Cp1251Codec.decodeSmart(value)
                    }
                    ARG_USER_ID -> value.toIntOrNull()?.let { settings.userId = it }
                    ARG_SUB_FORUMS -> settings.subforums = value
                    ARG_EXCLUDE_TRASH -> value.toIntOrNull()?.let { settings.excludeTrash = it }
                }

                if (name == ARG_FORUMS || name == ARG_FORUMS_SIMPLE) {
                    settings.addForum(value)
                }
                if (name == ARG_TOPICS || name == ARG_TOPICS_SIMPLE) {
                    settings.addTopic(value)
                }
            }
            return settings
        }

        @JvmStatic
        fun toUrl(settings: SearchSettings): String {
            if (settings.resourceType == RESOURCE_NEWS.first) {
                return Uri.Builder()
                        .scheme("https").authority("4pda.to")
                        .appendPath("page")
                        .appendPath(settings.st.toString())
                        .appendQueryParameter(ARG_QUERY_NEWS, settings.query)
                        .build()
                        .toString()
            }

            // Для форума 4pda (legacy IPB) query нужен в windows-1251.
            val sb = StringBuilder("https://4pda.to/forum/index.php")
            sb.append("?act=search")
            sb.append('&').append(ARG_RESULT).append('=').append(encodeCp1251(settings.result))
            sb.append('&').append(ARG_SORT).append('=').append(encodeCp1251(settings.sort))
            sb.append('&').append(ARG_SOURCE).append('=').append(encodeCp1251(settings.source))
            if (settings.query.isNotEmpty()) {
                sb.append('&').append(ARG_QUERY_FORUM).append('=').append(encodeCp1251(settings.query))
            }
            settings.nick?.takeIf { it.isNotEmpty() && (settings.userId <= 0 || canEncodeCp1251(it)) }?.let { nick ->
                sb.append('&').append(ARG_NICK).append('=').append(encodeNick(nick))
            }
            if (settings.userId > 0) {
                sb.append('&').append(ARG_USER_ID).append('=').append(settings.userId)
            }
            for (forum in settings.forums) {
                sb.append('&').append(ARG_FORUMS_SIMPLE).append('=').append(forum)
            }
            for (topic in settings.topics) {
                sb.append('&').append(ARG_TOPICS_SIMPLE).append('=').append(topic)
            }
            settings.subforums?.let {
                sb.append('&').append(ARG_SUB_FORUMS).append('=').append(it)
            }
            sb.append('&').append(ARG_NO_FORM).append("=1")
            sb.append('&').append(ARG_ST).append('=').append(settings.st)
            sb.append('&').append(ARG_EXCLUDE_TRASH).append('=').append(settings.excludeTrash)
            return sb.toString()
        }

        private fun encodeCp1251(value: String): String = Cp1251Codec.encode(value)
        private fun encodeNick(value: String): String = Cp1251Codec.encodeSmart(value)
        private fun canEncodeCp1251(value: String): Boolean =
                Charset.forName(Cp1251Codec.NAME).newEncoder().canEncode(value)
    }
}
