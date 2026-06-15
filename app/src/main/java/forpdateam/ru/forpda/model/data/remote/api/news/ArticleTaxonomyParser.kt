package forpdateam.ru.forpda.model.data.remote.api.news

import forpdateam.ru.forpda.entity.remote.news.Material
import forpdateam.ru.forpda.entity.remote.news.Tag
import forpdateam.ru.forpda.model.data.remote.ParserPatterns
import forpdateam.ru.forpda.model.data.remote.parser.BaseParser
import forpdateam.ru.forpda.model.data.storage.IPatternProvider

// This sub-parser returns [ArticleParser.ArticleTaxonomy],
// which is `internal` to its parent. We reference the fully
// qualified type at the use site to avoid a name clash with
// the surrounding class.

/**
 * Sub-parser extracted from the §1.1 god-class [ArticleParser].
 * Owns the article-taxonomy / materials / tag-link extraction
 * logic. The facade pattern is preserved: [ArticleParser] still
 * owns the public surface and delegates the heavy lifting to
 * this class.
 *
 * Helper functions that used to be private methods on
 * [ArticleParser] (`articleFromHtml`, `cleanPollText`,
 * `articleHtmlEncode`) are passed in as constructor parameters
 * to keep this class free of [ArticleParser] internals and to
 * keep behaviour byte-identical.
 */
internal class ArticleTaxonomyParser(
        private val patternProvider: IPatternProvider,
        private val articleFromHtml: (String?) -> String?,
        private val cleanPollText: (String) -> String,
        private val articleHtmlEncode: (String) -> String,
) : BaseParser() {

    private val scope = ParserPatterns.Articles

    fun parseMaterials(source: String): List<Material> = patternProvider
            .getPattern(scope.scope, scope.materials)
            .matcher(source)
            .map {
                Material().apply {
                    imageUrl = it.group(1)
                    id = it.group(2).orEmpty().toIntOrNull() ?: 0
                    title = articleFromHtml(it.group(3).orEmpty()).orEmpty()
                }
            }

    fun parseTags(source: String): List<Tag> {
        val parsed = patternProvider
                .getPattern(scope.scope, scope.tags)
                .matcher(source)
                .map {
                    val slug = it.group(1).orEmpty()
                    Tag().apply {
                        tag = slug
                        title = articleFromHtml(it.group(2).orEmpty()).orEmpty()
                        url = tagUrlFromSlug(slug)
                    }
                }
        val fallback = parseListTagsFallback(source)
        if (fallback.isNotEmpty()) return fallback
        return parsed
    }

    fun parseArticleTaxonomy(response: String, primarySource: String? = null): ArticleParser.ArticleTaxonomy {
        val categories = linkedMapOf<String, Tag>()
        primarySource?.let { addCategories(categories, parseCategories(it)) }
        taxonomyContainers(response).forEach { container ->
            addCategories(categories, parseCategories(container))
        }

        val category = categories.values.firstOrNull { !isGenericNewsSection(it) }
                ?: categories.values.firstOrNull()
        return ArticleParser.ArticleTaxonomy(category)
    }

    fun parseCategories(source: String): List<Tag> =
            parseLinkedCategories(source).ifEmpty {
                parseArticleSectionFallback(source)?.let { listOf(it) }.orEmpty()
            }

    private fun parseListTagsFallback(source: String): List<Tag> =
            Regex("""(?is)<a\b(?=[^>]*\bhref\s*=\s*(["'])(.*?)\1)[^>]*>([\s\S]*?)</a>""")
                    .findAll(source)
                    .mapNotNull { match ->
                        val href = articleFromHtml(match.groupValues.getOrNull(2).orEmpty()).orEmpty()
                        val title = cleanPollText(match.groupValues.getOrNull(3).orEmpty())
                        val tagUrl = normalize4pdaTagUrl(href)
                        val slug = tagUrl
                                ?.let { Regex("""(?i)/tag/([^/?#"'<>]+)/?""").find(it)?.groupValues?.getOrNull(1) }
                                .orEmpty()
                        if (title.isBlank() || slug.isBlank()) {
                            null
                        } else {
                            Tag(
                                    tag = slug,
                                    title = title,
                                    url = tagUrl
                            )
                        }
                    }
                    .toList()

    private fun parseArticleSectionFallback(source: String): Tag? =
            Regex("""(?is)<a\b(?=[^>]*\bdata-article-section\s*=\s*["']true["'])[^>]*>([\s\S]*?)</a>""")
                    .find(source)
                    ?.let { match ->
                        cleanPollText(match.groupValues.getOrNull(1).orEmpty())
                                .takeIf { it.isNotBlank() }
                                ?.let { title ->
                                    Tag(tag = "category/${title.lowercase().replace(Regex("""\s+"""), "-")}", title = title)
                                }
                    }

    private fun parseLinkedCategories(source: String): List<Tag> =
            Regex("""(?is)<a\b(?=[^>]*\bhref\s*=\s*(["'])(.*?)\1)[^>]*>([\s\S]*?)</a>""")
                    .findAll(source)
                    .mapNotNull { match ->
                        val href = articleFromHtml(match.groupValues.getOrNull(2).orEmpty()).orEmpty()
                        val title = cleanPollText(match.groupValues.getOrNull(3).orEmpty())
                        val categoryUrl = normalize4pdaCategoryUrl(href)
                        val slug = categoryUrl
                                ?.substringAfter("4pda.to", "")
                                ?.substringBefore('?')
                                ?.substringBefore('#')
                                ?.let { path ->
                                    sectionHrefRegex.find(path)?.groupValues?.getOrNull(1)
                                            ?: categoryHrefRegex.find(path)?.groupValues?.getOrNull(1)
                                }
                                .orEmpty()
                        if (title.isBlank() || slug.isBlank()) {
                            null
                        } else {
                            Tag(tag = "category/$slug", title = title, url = categoryUrl)
                        }
                    }
                    .toList()

    private fun addCategories(
            categoryTarget: LinkedHashMap<String, Tag>,
            categories: List<Tag>
    ) {
        categories.forEach { tag ->
            val key = tag.tag.orEmpty().lowercase().ifBlank { tag.title.orEmpty().lowercase() }
            if (key.isBlank()) return@forEach
            categoryTarget.putIfAbsent(key, tag)
        }
    }

    private fun taxonomyContainers(response: String): List<String> {
        val result = mutableListOf<String>()
        Regex("""(?is)<(?:div|nav|ul|section|footer)\b(?=[^>]*\bclass\s*=\s*(["'])(?:(?!\1).)*(?:tags|tag-list|category|categories|breadcrumb|rubric|article-footer-tags|meta)(?:(?!\1).)*\1)[^>]*>[\s\S]*?</(?:div|nav|ul|section|footer)>""")
                .findAll(response)
                .forEach { result += it.value }
        articleSectionMetaRegex
                .find(response)
                ?.groupValues
                ?.getOrNull(2)
                ?.let { articleFromHtml(it) }
                ?.takeIf { it.isNotBlank() }
                ?.let { section: String ->
                    result += """<a data-article-section="true">${articleHtmlEncode(section)}</a>"""
                }
        return result
    }

    private fun isGenericNewsSection(tag: Tag): Boolean {
        val path = tag.url.orEmpty()
                .substringAfter("4pda.to", tag.url.orEmpty())
                .substringBefore('?')
                .substringBefore('#')
                .trim('/')
                .lowercase()
        return path == "news" || path.isBlank()
    }

    private fun tagUrlFromSlug(slug: String): String? =
            slug.takeIf { it.isNotBlank() }?.let { "https://4pda.to/tag/$it/" }

    private fun normalize4pdaTagUrl(rawHref: String): String? {
        val absolute = normalize4pdaUrl(rawHref) ?: return null
        val path = absolute.substringAfter("4pda.to", "").substringBefore('?').substringBefore('#')
        if (!path.contains("/tag/", ignoreCase = true)) {
            return null
        }
        return absolute
    }

    private fun normalize4pdaCategoryUrl(rawHref: String): String? {
        val absolute = normalize4pdaUrl(rawHref) ?: return null
        val path = absolute.substringAfter("4pda.to", "").substringBefore('?').substringBefore('#')
        if (!path.contains("/category/", ignoreCase = true) && !sectionHrefRegex.matches(path)) {
            return null
        }
        return absolute
    }

    private fun normalize4pdaUrl(rawHref: String): String? =
            forpdateam.ru.forpda.model.data.remote.api.news.normalize4pdaUrl(rawHref)

    // Regex constants that used to be top-level private fields in
    // ArticleParser. Kept here so the rest of the class can
    // continue to use them.
    private val sectionHrefRegex = Regex("""(?i)^/([a-z0-9_-]+)/?$""")
    private val categoryHrefRegex = Regex("""(?i)/category/([^/?#"'<>]+)/?""")
    private val articleSectionMetaRegex = Regex(
            """(?is)<meta\b(?=[^>]*(?:property|name)\s*=\s*["']article:section["'])(?=[^>]*content\s*=\s*(["'])(.*?)\1)[^>]*>"""
    )
}
