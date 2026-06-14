package forpdateam.ru.forpda.entity.remote.others.pagination

import java.util.regex.Matcher
import java.util.regex.Pattern

/**
 * Created by radiationx on 03.03.17.
 * Converted to Kotlin.
 */

/**
 * Безопасные extension-функции для извлечения групп из Matcher.
 * Возвращают null вместо краша при отсутствии группы или ошибке парсинга.
 */
private fun Matcher.groupInt(group: Int): Int? {
    val value = this.group(group) ?: return null
    return value.toIntOrNull()
}

class Pagination {
    var perPage: Int = 20
    var all: Int = 1
    var current: Int = 1
    var st: Int = 0
    var isForum: Boolean = true

    fun getPage(page: Int): Int {
        return if (!isForum) page else page * perPage
    }

    companion object {
        private val forumPaginationPattern = Pattern.compile(
            "parseInt\\((\\d*)\\)[\\s\\S]*?parseInt\\(st\\*(\\d*)\\)[\\s\\S]*?pagination\">[\\s\\S]*?<span[^>]*?>([^<]*?)<\\/span>"
        )
        private val newsPaginationPattern = Pattern.compile(
            "class=\"s-count[\\s\\S]*?<strong>(\\d+)<\\/strong>[\\s\\S]*?<ul class=\"page-nav[^>]*?>[\\s\\S]*?<li class=\"active\"><a[^>]*?>(\\d+)"
        )

        @JvmOverloads
        fun parseNews(page: String? = null, pagination: Pagination = Pagination()): Pagination {
            pagination.isForum = false
            page?.let {
                val matcher = newsPaginationPattern.matcher(it)
                if (matcher.find()) {
                    pagination.perPage = 30
                    val allItems = matcher.groupInt(1) ?: return pagination
                    pagination.all = kotlin.math.ceil(allItems / 30.0).toInt()
                    pagination.current = matcher.groupInt(2) ?: return pagination
                }
            }
            return pagination
        }

        @JvmOverloads
        fun parseForum(page: String? = null, pagination: Pagination = Pagination()): Pagination {
            page?.let {
                val matcher = forumPaginationPattern.matcher(it)
                if (matcher.find()) {
                    pagination.all = (matcher.groupInt(1) ?: return pagination) + 1
                    pagination.perPage = matcher.groupInt(2) ?: return pagination
                    pagination.current = matcher.groupInt(3) ?: return pagination
                }
            }
            return pagination
        }
    }
}
