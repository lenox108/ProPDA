package forpdateam.ru.forpda.common

import java.net.URLDecoder
import java.util.Locale
import java.util.regex.Pattern

/**
 * Resolves 4PDA preview/thumbnail image URLs to full-quality viewer URLs.
 *
 * Forum attachments often navigate to signed `/s/` CDN URLs while the surrounding
 * link still points at `4pda.to/forum/dl/post/...`. News images use WordPress
 * `-{w}x{h}` suffixes in filenames.
 */
object FourPdaImageUrls {

    private val wpSizeSuffixRegex = Regex("-(\\d+)x(\\d+)(?=\\.[^.]+$)", RegexOption.IGNORE_CASE)

    private val forumDlPattern = Pattern.compile(
            """4pda\.to/forum/dl/post/\d+/[^"'\s<>?#]+\.(?:jpe?g|png|gif|bmp|webp)(?:\?[^"'\s<>]*)?""",
            Pattern.CASE_INSENSITIVE
    )

    private val imageExtensionPattern = Pattern.compile(
            """\.(?:jpe?g|png|gif|bmp|webp)(?:\?.*)?$""",
            Pattern.CASE_INSENSITIVE
    )

    @JvmStatic
    fun normalizeAbsolute(url: String): String {
        val trimmed = url.trim()
        if (trimmed.isEmpty()) return trimmed
        val absolute = when {
            trimmed.startsWith("//") -> "https:$trimmed"
            trimmed.startsWith("/") -> "https://4pda.to$trimmed"
            else -> trimmed
        }
        return try {
            URLDecoder.decode(absolute, "UTF-8")
        } catch (_: Exception) {
            absolute
        }
    }

    @JvmStatic
    fun stripWordPressSizeSuffix(url: String): String {
        return wpSizeSuffixRegex.replace(url, "")
    }

    @JvmStatic
    fun isForumAttachmentFullUrl(url: String): Boolean {
        return forumDlPattern.matcher(normalizeAbsolute(url)).find()
    }

    @JvmStatic
    fun isPreviewOrThumbnailUrl(url: String): Boolean {
        val normalized = normalizeAbsolute(url).lowercase(Locale.ROOT)
        if (wpSizeSuffixRegex.containsMatchIn(normalized)) return true
        if (normalized.contains("4pda.to/s/")) return true
        if (normalized.contains("s.4pda.to/forum/uploads/")) return true
        if (Regex("""4pda\.to/forum/uploads/""").containsMatchIn(normalized)) return true
        return normalized.contains("/thumb-") || normalized.contains("/thumb/")
    }

    @JvmStatic
    fun isViewableInViewer(url: String): Boolean {
        val normalized = normalizeAbsolute(url)
        if (isForumAttachmentFullUrl(normalized)) return true
        val lower = normalized.lowercase(Locale.ROOT)
        if (!imageExtensionPattern.matcher(lower).find()) return false
        return lower.contains("4pda.to/") || lower.contains("4pda.ws") || lower.contains(".4pda.ws")
    }

    /**
     * Picks the best URL for [ImageViewerActivity] / Coil full-quality loading.
     *
     * When [linkSourceHref] points at a forum attachment download URL and [url] is
     * only a preview (`/s/`, thumb CDN, WordPress-sized filename), the download URL wins.
     */
    @JvmStatic
    fun resolveViewerUrl(url: String, linkSourceHref: String? = null): String {
        val normalized = normalizeAbsolute(url)
        val sourceHref = linkSourceHref
                ?.takeIf { it.isNotBlank() }
                ?.let { normalizeAbsolute(it) }

        if (sourceHref != null && isForumAttachmentFullUrl(sourceHref)) {
            if (isPreviewOrThumbnailUrl(normalized) || !isForumAttachmentFullUrl(normalized)) {
                return stripWordPressSizeSuffix(sourceHref)
            }
        }

        if (isForumAttachmentFullUrl(normalized)) {
            return stripWordPressSizeSuffix(normalized)
        }

        return stripWordPressSizeSuffix(normalized)
    }

    @JvmStatic
    fun resolveViewerUrls(urls: Collection<String>, linkSourceHref: String? = null): List<String> {
        return urls.map { resolveViewerUrl(it, linkSourceHref) }
    }
}
