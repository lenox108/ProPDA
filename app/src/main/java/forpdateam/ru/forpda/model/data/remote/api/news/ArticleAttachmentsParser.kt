package forpdateam.ru.forpda.model.data.remote.api.news

import forpdateam.ru.forpda.model.data.remote.parser.BaseParser

/**
 * Sub-parser extracted from [ArticleParser] (§1.1 decomposition) responsible for picking
 * attachment-style image URLs out of an article body / header and for the related image
 * normalization (https upgrade, scheme-relative and relative resolution against the article
 * base, deduplication via URL normalization).
 *
 * This complements the `Material` listing already handled by [ArticleTaxonomyParser]. It does
 * NOT parse the article hero image, which stays in [ArticleParser] because it depends on
 * multiple regexes that participate in the [ArticleParsePhase] lifecycle.
 */
internal class ArticleAttachmentsParser(
        private val articleFromHtml: (String?) -> String?,
        private val articleLightboxImageRegex: Regex,
        private val firstImageTagRegex: Regex,
        private val firstSourceTagRegex: Regex,
        private val articleUrlFromResponse: (String) -> String?,
        private val selectArticleImageUrl: (String, String?) -> String?,
        private val parseSrcset: (String?) -> String?,
        private val getAttribute: (String, String) -> String?,
) : BaseParser() {

    fun firstBodyImageUrl(contentHtml: String?): String? =
            articleLightboxImageRegex.find(contentHtml.orEmpty())?.value?.let { selectArticleImageUrl(it, null) }
                    ?: firstImageTagRegex.find(contentHtml.orEmpty())?.value?.let { selectArticleImageUrl(it, null) }

    fun firstBodyLightboxImageUrl(contentHtml: String?): String? =
            articleLightboxImageRegex.find(contentHtml.orEmpty())?.value?.let { selectArticleImageUrl(it, null) }

    fun selectImageUrlFromHtml(html: String): String? {
        firstSourceTagRegex.find(html)?.value?.let { source ->
            parseSrcset(getAttribute(source, "srcset") ?: getAttribute(source, "data-srcset"))
                    ?.let { return it }
            getAttribute(source, "data-src")?.let { return it }
            getAttribute(source, "src")?.let { return it }
        }
        return firstImageTagRegex.find(html)?.value?.let { selectArticleImageUrl(it, null) }
    }

    fun normalizeArticleImageUrl(rawUrl: String?, response: String): String? {
        val url = articleFromHtml(rawUrl)?.trim()?.takeIf { it.isNotBlank() } ?: return null
        return when {
            url.startsWith("https://", ignoreCase = true) -> url
            url.startsWith("http://", ignoreCase = true) -> url.replaceFirst("http://", "https://")
            url.startsWith("//") -> "https:$url"
            url.startsWith("/") -> "https://4pda.to$url"
            else -> articleUrlFromResponse(response)
                    ?.substringBeforeLast('/')
                    ?.let { "$it/$url" }
        }
    }

    fun urlsReferToSameImage(first: String?, second: String?): Boolean {
        if (first.isNullOrBlank() || second.isNullOrBlank()) return false
        fun key(url: String): String =
                url.substringBefore('?')
                        .substringBefore('#')
                        .replace(Regex("""-\d+x\d+(?=\.[a-zA-Z0-9]+$)"""), "")
                        .lowercase()
        return key(first) == key(second)
    }
}
