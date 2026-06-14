package forpdateam.ru.forpda.model.data.remote.api.theme

import forpdateam.ru.forpda.model.data.remote.api.ApiUtils

class ThemePageMetadataParser {

    fun parseTopicIds(response: String): Pair<Int, Int>? {
        val dataAttributeIds = parseDataAttributeTopicIds(response)
        if (dataAttributeIds != null) return dataAttributeIds

        val topicId = reTopicIdFallback2.find(response)?.groupValues?.get(1)?.toIntOrNull()
        val forumId = reForumIdFallback.find(response)?.groupValues?.get(1)?.toIntOrNull()
        return topicId?.let { Pair(it, forumId ?: 0) }
    }

    fun parseTitle(response: String): String? {
        val rawTitle = reTitleFallback.find(response)?.groupValues?.getOrNull(1)
                ?: reTitleFallback2.find(response)?.groupValues?.getOrNull(1)
        return rawTitle?.let(::decodeTitle)
    }

    private fun parseDataAttributeTopicIds(response: String): Pair<Int, Int>? {
        reTopicIdFallback.find(response)?.let {
            val topicId = it.groupValues[1].toIntOrNull() ?: return@let null
            val forumId = it.groupValues[2].toIntOrNull() ?: return@let null
            return Pair(topicId, forumId)
        }
        reForumTopicIdFallback.find(response)?.let {
            val forumId = it.groupValues[1].toIntOrNull() ?: return@let null
            val topicId = it.groupValues[2].toIntOrNull() ?: return@let null
            return Pair(topicId, forumId)
        }
        return null
    }

    private fun decodeTitle(value: String): String {
        return runCatching { ApiUtils.fromHtml(value) }
                .getOrNull()
                ?.takeIf { it.isNotBlank() }
                ?: value.replace(Regex("<[^>]+>"), "").trim()
    }

    companion object {
        private val reTopicIdFallback = Regex(
                "data-topic=\"(\\d+)\"[^>]*?data-forum=\"(\\d+)\"",
                RegexOption.IGNORE_CASE
        )
        private val reForumTopicIdFallback = Regex(
                "data-forum=\"(\\d+)\"[^>]*?data-topic=\"(\\d+)\"",
                RegexOption.IGNORE_CASE
        )
        private val reTopicIdFallback2 = Regex(
                "showtopic=(\\d+)",
                RegexOption.IGNORE_CASE
        )
        private val reForumIdFallback = Regex(
                "showforum=(\\d+)",
                RegexOption.IGNORE_CASE
        )
        private val reTitleFallback = Regex(
                "<h1[^>]*?>([\\s\\S]*?)</h1>",
                setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        )
        private val reTitleFallback2 = Regex(
                "property=\"og:title\"[^>]*?content=\"([^\"]+)\"",
                RegexOption.IGNORE_CASE
        )
    }
}
