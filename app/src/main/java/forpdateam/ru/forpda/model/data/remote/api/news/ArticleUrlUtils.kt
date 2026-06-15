package forpdateam.ru.forpda.model.data.remote.api.news

/**
 * Internal helpers shared between [ArticleParser] and the
 * extracted sub-parsers (e.g. [ArticleTaxonomyParser]). Kept in
 * its own file so the §1.1 decomposition does not duplicate
 * the URL-normalisation rules across multiple files.
 */

internal fun normalize4pdaUrl(rawHref: String): String? {
    val href = rawHref.trim().takeIf { it.isNotBlank() } ?: return null
    return when {
        href.startsWith("https://4pda.to/", ignoreCase = true) -> href
        href.startsWith("http://4pda.to/", ignoreCase = true) ->
            "https://4pda.to/" + href.substringAfter("://").substringAfter("/")
        href.startsWith("//4pda.to/", ignoreCase = true) -> "https:$href"
        href.startsWith("/") -> "https://4pda.to$href"
        href.startsWith("comment.php", ignoreCase = true) ||
                href.startsWith("admin-ajax.php", ignoreCase = true) -> "https://4pda.to/wp-admin/$href"
        else -> null
    }
}
