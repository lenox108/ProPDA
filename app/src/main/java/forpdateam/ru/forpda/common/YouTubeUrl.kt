package forpdateam.ru.forpda.common

import java.net.URI
import java.net.URISyntaxException

object YouTubeUrl {
    private val VIDEO_ID_REGEX = Regex("^[A-Za-z0-9_-]{11}$")

    fun extractVideoId(rawUrl: String?): String? {
        val value = rawUrl?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val uri = try {
            URI(value)
        } catch (_: URISyntaxException) {
            return null
        } catch (_: IllegalArgumentException) {
            return null
        }

        val host = uri.host?.lowercase() ?: return null
        return when {
            host == "youtu.be" || host.endsWith(".youtu.be") -> uri.pathSegments().firstOrNull()
            host == "youtube.com" || host == "www.youtube.com" || host == "m.youtube.com" -> {
                val segments = uri.pathSegments()
                when {
                    uri.path == "/watch" -> uri.queryParameter("v")
                    segments.firstOrNull() == "embed" -> segments.getOrNull(1)
                    segments.firstOrNull() == "shorts" -> segments.getOrNull(1)
                    segments.firstOrNull() == "live" -> segments.getOrNull(1)
                    segments.firstOrNull() == "v" -> segments.getOrNull(1)
                    else -> null
                }
            }
            host == "youtube-nocookie.com" || host == "www.youtube-nocookie.com" -> {
                if (uri.pathSegments().firstOrNull() == "embed") {
                    uri.pathSegments().getOrNull(1)
                } else {
                    null
                }
            }
            else -> null
        }?.takeIf { VIDEO_ID_REGEX.matches(it) }
    }

    private fun URI.pathSegments(): List<String> {
        return rawPath
                ?.split("/")
                ?.filter { it.isNotEmpty() }
                .orEmpty()
    }

    private fun URI.queryParameter(name: String): String? {
        return rawQuery
                ?.split("&")
                ?.asSequence()
                ?.mapNotNull { parameter ->
                    val index = parameter.indexOf("=")
                    if (index <= 0) return@mapNotNull null
                    parameter.substring(0, index) to parameter.substring(index + 1)
                }
                ?.firstOrNull { it.first == name }
                ?.second
    }
}
