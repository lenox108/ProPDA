package forpdateam.ru.forpda.presentation.theme

import java.net.URI
import java.net.URISyntaxException
import java.util.Locale

data class ThemeUrlInfo(
        val topicId: Int?,
        val postId: Int?,
        val page: Int?,
        val isFindPost: Boolean,
        val normalizedUrl: String?
)

object ThemeUrlPolicy {

    fun parse(rawUrl: String?): ThemeUrlInfo? {
        val normalizedUrl = normalize(rawUrl) ?: return null
        val uri = parseUri(normalizedUrl) ?: return null
        if (!isSupportedHost(uri)) return null

        val query = parseQuery(uri.rawQuery.orEmpty())
        val lofiUrl = lofiUrlPart(uri)
        val topicId = firstInt(query, "showtopic", "t") ?: topicFromLofiUrl(lofiUrl)
        val postId = firstInt(query, "p", "pid") ?: postIdFromAnchor(query["anchor"]?.lastOrNull()) ?: postIdFromAnchor(uri.rawFragment)
        val page = firstInt(query, "st") ?: pageFromLofiUrl(lofiUrl)
        val view = query["view"]?.lastOrNull()?.lowercase(Locale.ROOT)
        val act = query["act"]?.lastOrNull()?.lowercase(Locale.ROOT)
        val hasFindPostMarker = view == "findpost" || act == "findpost"
        val isFindPost = hasFindPostMarker || (postId != null && (view == null || view == "findpost") && act !in NON_FINDPOST_ACTS)

        if (topicId == null && !isFindPost) return null

        return ThemeUrlInfo(
                topicId = topicId,
                postId = postId,
                page = page,
                isFindPost = isFindPost,
                normalizedUrl = normalizedUrl
        )
    }

    fun isThemeUrl(rawUrl: String?): Boolean = parse(rawUrl) != null

    private fun normalize(rawUrl: String?): String? {
        val value = rawUrl
                ?.trim()
                ?.replace("&amp;", "&")
                ?.replace("&#038;", "&")
                ?.replace("\"", "")
                ?: return null
        if (value.isBlank() || value == "#") return null
        return when {
            value.startsWith("//") -> "https:$value"
            value.startsWith("/") -> "https://4pda.to$value"
            value.startsWith("?") -> "https://4pda.to/forum/index.php$value"
            value.startsWith("./") -> "https://4pda.to/forum/${value.removePrefix("./")}"
            value.startsWith("forum/", ignoreCase = true) -> "https://4pda.to/$value"
            value.startsWith("index.php", ignoreCase = true) -> "https://4pda.to/forum/$value"
            else -> value
        }
    }

    private fun parseUri(url: String): URI? {
        return try {
            URI(url)
        } catch (_: URISyntaxException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    private fun isSupportedHost(uri: URI): Boolean {
        val host = uri.host?.lowercase(Locale.ROOT) ?: return false
        return host == "4pda.to" ||
                host == "www.4pda.to" ||
                host == "4pda.ru" ||
                host == "www.4pda.ru"
    }

    private fun parseQuery(query: String): Map<String, List<String>> {
        if (query.isBlank()) return emptyMap()
        return query.split('&')
                .asSequence()
                .mapNotNull { part ->
                    val index = part.indexOf('=')
                    if (index <= 0) return@mapNotNull null
                    part.substring(0, index).lowercase(Locale.ROOT) to part.substring(index + 1)
                }
                .groupBy({ it.first }, { it.second })
    }

    private fun firstInt(query: Map<String, List<String>>, vararg names: String): Int? {
        return names.asSequence()
                .mapNotNull { name -> query[name.lowercase(Locale.ROOT)]?.lastOrNull()?.toPositiveIntOrNull() }
                .firstOrNull()
    }

    private fun String.toPositiveIntOrNull(): Int? {
        return trim().toIntOrNull()?.takeIf { it > 0 }
    }

    private fun postIdFromAnchor(anchor: String?): Int? {
        if (anchor.isNullOrBlank()) return null
        return Regex("""(?i)(?:^|[^0-9])entry(\d+)""").find(anchor)?.groupValues?.getOrNull(1)?.toPositiveIntOrNull()
    }

    private fun lofiUrlPart(uri: URI): String {
        val path = uri.rawPath.orEmpty()
        val query = uri.rawQuery
        return if (query.isNullOrBlank()) path else "$path?$query"
    }

    private fun topicFromLofiUrl(urlPart: String): Int? {
        return LOFI_TOPIC_PATTERN.find(urlPart)?.groupValues?.getOrNull(1)?.toPositiveIntOrNull()
    }

    private fun pageFromLofiUrl(urlPart: String): Int? {
        return LOFI_TOPIC_PATTERN.find(urlPart)?.groupValues?.getOrNull(2)?.toPositiveIntOrNull()
    }

    private val NON_FINDPOST_ACTS = setOf("post", "report", "qms", "fav", "zmod", "search")
    private val LOFI_TOPIC_PATTERN = Regex("""(?i)/forum/lofiversion/.*?\?t(\d+)(?:-(\d+))?""")
}
