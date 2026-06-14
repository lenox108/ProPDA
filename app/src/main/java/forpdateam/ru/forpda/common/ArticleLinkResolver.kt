package forpdateam.ru.forpda.common

import android.net.Uri
import java.util.Locale

/**
 * Resolves relative article WebView links against the site root and fixes URLs that were
 * incorrectly resolved against [ARTICLE_WEBVIEW_BASE_URL] (e.g. /forum/index.php?p=).
 */
object ArticleLinkResolver {

    const val ARTICLE_WEBVIEW_BASE_URL = "https://4pda.to/"

    /** Base URL for forum topic WebView ([ThemeWebController] loadDataWithBaseURL). */
    const val THEME_WEBVIEW_BASE_URL = "https://4pda.to/forum/"

    fun resolveForNavigation(rawUrl: String?): String? {
        val trimmed = rawUrl?.trim().orEmpty()
        if (trimmed.isBlank() || trimmed == "#") return null
        if (trimmed.startsWith("#")) return null
        if (trimmed.startsWith("javascript:", ignoreCase = true)) return null

        val absolute = when {
            trimmed.startsWith("//") -> "https:$trimmed"
            trimmed.startsWith("http://", ignoreCase = true) ||
                    trimmed.startsWith("https://", ignoreCase = true) -> trimmed
            trimmed.startsWith("/") -> "${SiteUrls.BASE_HTTPS}$trimmed"
            else -> "${SiteUrls.BASE_HTTPS}/$trimmed"
        }
        return normalizeMisplacedForumPrefix(absolute)
    }

    fun normalizeMisplacedForumPrefix(url: String): String {
        val uri = Uri.parse(url)
        if (!SiteUrls.isSiteUri(uri)) return url
        var path = uri.path.orEmpty()
        if (path.startsWith("/forum/pages/", ignoreCase = true)) {
            path = path.replaceFirst("/forum/pages/", "/pages/", ignoreCase = true)
        } else if (path.startsWith("/forum/stat/", ignoreCase = true)) {
            path = path.replaceFirst("/forum/stat/", "/stat/", ignoreCase = true)
        } else if (path.startsWith("/forum/software/", ignoreCase = true)) {
            path = path.replaceFirst("/forum/software/", "/software/", ignoreCase = true)
        } else if (path.equals("/forum/index.php", ignoreCase = true)) {
            val hasPostId = uri.getQueryParameter("p") != null
            val isForumNavigation = uri.queryParameterNames.any { name ->
                when (name.lowercase(Locale.ROOT)) {
                    "showtopic", "showuser", "showforum", "act" -> true
                    else -> false
                }
            }
            if (hasPostId && !isForumNavigation) {
                path = "/index.php"
            }
        } else {
            return url
        }
        if (path == uri.path.orEmpty()) return url
        return uri.buildUpon().path(path).build().toString()
    }
}
