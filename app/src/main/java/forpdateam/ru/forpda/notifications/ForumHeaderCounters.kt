package forpdateam.ru.forpda.notifications

import forpdateam.ru.forpda.model.data.remote.IWebClient
import java.util.regex.Pattern

/**
 * Parses QMS / favorites / mentions badge counts from the forum index header HTML.
 * Used for lightweight background polling before heavier inspector/mentions requests.
 */
data class HeaderCounters(
        val mentions: Int,
        val favorites: Int,
        val qms: Int,
) {
    fun isInitialized(): Boolean = mentions >= 0 && favorites >= 0 && qms >= 0

    companion object {
        val UNSET = HeaderCounters(-1, -1, -1)
    }
}

object ForumHeaderCounters {

    /**
     * Returns counts with `null` for fields whose regex did not match — useful
     * when callers want to merge into an existing snapshot without overwriting
     * a previously observed value (e.g. [forpdateam.ru.forpda.client.Client.getCounts]).
     */
    data class OptionalHeaderCounters(
            val mentions: Int?,
            val favorites: Int?,
            val qms: Int?,
    )

    fun parse(html: String): HeaderCounters {
        val optional = parseOptional(html)
        return HeaderCounters(
                mentions = optional.mentions ?: 0,
                favorites = optional.favorites ?: 0,
                qms = optional.qms ?: 0,
        )
    }

    fun parseOptional(html: String): OptionalHeaderCounters {
        fun findFirstInt(pattern: Pattern): Int? {
            val m = pattern.matcher(html)
            if (!m.find()) return null
            for (i in 1..m.groupCount()) {
                val v = m.group(i)?.toIntOrNull()
                if (v != null) return v.coerceAtLeast(0)
            }
            return null
        }

        val legacy = IWebClient.countsPattern.matcher(html)
        if (legacy.find()) {
            return OptionalHeaderCounters(
                    mentions = legacy.group(1)?.toIntOrNull()?.coerceAtLeast(0),
                    favorites = legacy.group(2)?.toIntOrNull()?.coerceAtLeast(0),
                    qms = legacy.group(3)?.toIntOrNull()?.coerceAtLeast(0),
            )
        }
        return OptionalHeaderCounters(
                mentions = findFirstInt(IWebClient.mentionsCountPattern),
                favorites = findFirstInt(IWebClient.favoritesCountPattern),
                qms = findFirstInt(IWebClient.qmsCountPattern),
        )
    }
}
