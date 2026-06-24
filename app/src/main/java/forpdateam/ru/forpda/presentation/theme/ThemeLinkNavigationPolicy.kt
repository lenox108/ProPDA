package forpdateam.ru.forpda.presentation.theme

import android.net.Uri
import forpdateam.ru.forpda.common.ArticleLinkResolver
import forpdateam.ru.forpda.common.FourPdaImageUrls
import forpdateam.ru.forpda.common.SiteUrls
import java.util.regex.Pattern

enum class ThemeLinkNavigationAction {
    OPEN_IMAGE_VIEWER,
    NAVIGATE_TO_URL,
    DOWNLOAD_URL,
}

data class ThemeLinkNavigationDecision(
        val action: ThemeLinkNavigationAction,
        val url: String,
)

/**
 * Resolves WebView link taps where the navigation target ([resolvedUrl]) may differ from the
 * surrounding anchor href (image-wrapped forum links, APK previews, etc.).
 */
object ThemeLinkNavigationPolicy {

    private val DOWNLOAD_PATTERN: Pattern = Pattern.compile(
            ".*\\.(apk|zip|rar|7z|tar|gz|bz2|pdf|doc|docx|xls|xlsx|ppt|pptx|txt|csv|mp3|mp4|avi|mkv|mov|wmv|flv|wav|ogg|exe|dmg|iso|img|torrent|bin|patch)(\\?.*)?\$",
            Pattern.CASE_INSENSITIVE
    )
    private val P4PDA_DOWNLOAD_PATTERN: Pattern = Pattern.compile(
            "https?://.*4pda\\.to/.*(?:dl/|download|attach|upload)[^\\.]*(?:\\.(?!jpg|jpeg|png|gif|bmp|webp)[a-z0-9]+)?\$",
            Pattern.CASE_INSENSITIVE
    )

    @JvmStatic
    fun resolve(resolvedUrl: String, sourceHref: String? = null): ThemeLinkNavigationDecision {
        val resolved = ArticleLinkResolver.resolveForNavigation(resolvedUrl) ?: resolvedUrl
        val source = sourceHref
                ?.takeIf { it.isNotBlank() }
                ?.let { ArticleLinkResolver.resolveForNavigation(it) ?: it }

        if (source != null) {
            val normalizedSource = FourPdaImageUrls.normalizeAbsolute(source)
            val normalizedResolved = FourPdaImageUrls.normalizeAbsolute(resolved)
            if (FourPdaImageUrls.isViewableInViewer(resolved) &&
                    FourPdaImageUrls.isViewableInViewer(source) &&
                    normalizedSource == normalizedResolved
            ) {
                return ThemeLinkNavigationDecision(ThemeLinkNavigationAction.DOWNLOAD_URL, resolved)
            }
            if (FourPdaImageUrls.isViewableInViewer(resolved) &&
                    !FourPdaImageUrls.isViewableInViewer(source)
            ) {
                if (isDownloadableUrl(source)) {
                    return ThemeLinkNavigationDecision(ThemeLinkNavigationAction.DOWNLOAD_URL, source)
                }
                if (isForumNavigationUrl(source)) {
                    return ThemeLinkNavigationDecision(ThemeLinkNavigationAction.NAVIGATE_TO_URL, source)
                }
            }
        }

        val viewerUrl = FourPdaImageUrls.resolveViewerUrl(resolved, sourceHref)
        return ThemeLinkNavigationDecision(ThemeLinkNavigationAction.OPEN_IMAGE_VIEWER, viewerUrl)
    }

    @JvmStatic
    fun isDownloadableUrl(url: String): Boolean {
        val normalized = FourPdaImageUrls.normalizeAbsolute(url)
        if (!SiteUrls.isSiteUri(Uri.parse(normalized))) return false
        val lower = normalized.lowercase()
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg") ||
                lower.endsWith(".png") || lower.endsWith(".gif") ||
                lower.endsWith(".bmp") || lower.endsWith(".webp")
        ) {
            return false
        }
        return DOWNLOAD_PATTERN.matcher(normalized).matches() ||
                P4PDA_DOWNLOAD_PATTERN.matcher(normalized).matches()
    }

    @JvmStatic
    fun isForumNavigationUrl(url: String): Boolean {
        val uri = Uri.parse(FourPdaImageUrls.normalizeAbsolute(url))
        if (!SiteUrls.isSiteUri(uri)) return false
        if (uri.pathSegments.getOrNull(0) != "forum") return false
        if (!uri.getQueryParameter("showtopic").isNullOrBlank()) return true
        val view = uri.getQueryParameter("view").orEmpty()
        if (view.equals("findpost", ignoreCase = true)) return true
        if (!uri.getQueryParameter("p").isNullOrBlank()) return true
        if (!uri.getQueryParameter("pid").isNullOrBlank()) return true
        val act = uri.getQueryParameter("act").orEmpty()
        return act.equals("findpost", ignoreCase = true) ||
                act.equals("st", ignoreCase = true) ||
                act.equals("qms", ignoreCase = true)
    }
}
