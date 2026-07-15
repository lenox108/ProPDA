package forpdateam.ru.forpda.ui.fragments.news.details

import android.net.Uri
import forpdateam.ru.forpda.common.webview.UrlDecision
import forpdateam.ru.forpda.common.webview.UrlPolicy
import timber.log.Timber

/**
 * Pure URL normalisation for article links, extracted from [ArticleContentFragment].
 *
 * Both functions are side-effect-free (apart from a single defensive log) and depend only on their
 * argument, so they live outside the Fragment and are unit-testable.
 */
object NewsArticleUrls {

    /** Normalises a URL for opening in the external browser, or null if policy blocks it. */
    fun normalizeExternal(url: String): String? =
            when (val decision = UrlPolicy.classify(url)) {
                UrlDecision.Blocked -> {
                    Timber.w("Blocked unsafe article external browser URL")
                    null
                }
                is UrlDecision.Internal -> decision.normalizedUrl
                is UrlDecision.External -> decision.normalizedUrl
            }

    /**
     * Normalises a 4pda taxonomy (category/section) URL to an absolute https link, or null if the
     * URL is not a recognised 4pda taxonomy path.
     */
    fun normalizeTaxonomy(url: String): String? {
        val value = url.trim().takeIf { it.isNotBlank() } ?: return null
        val absolute = when {
            value.startsWith("//") -> "https:$value"
            value.startsWith("/") -> "https://4pda.to$value"
            else -> value
        }
        val uri = Uri.parse(absolute)
        val scheme = uri.scheme?.lowercase()
        val host = uri.host?.lowercase()
        if (scheme != "http" && scheme != "https") return null
        if (host != "4pda.to" && host != "www.4pda.to") return null

        val lowerPath = uri.path.orEmpty().lowercase()
        val isTaxonomyPath = lowerPath.contains("/category/") ||
                Regex("""^/[a-z0-9_-]+/?$""").matches(lowerPath)
        if (!isTaxonomyPath) return null

        return uri.buildUpon().scheme("https").build().toString()
    }
}
