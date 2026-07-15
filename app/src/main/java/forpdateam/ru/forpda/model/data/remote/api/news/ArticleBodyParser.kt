package forpdateam.ru.forpda.model.data.remote.api.news

import forpdateam.ru.forpda.model.data.remote.api.regex.parser.Node
import forpdateam.ru.forpda.model.data.remote.api.regex.parser.Parser
import forpdateam.ru.forpda.model.data.remote.parser.BaseParser

/**
 * Sub-parser extracted from [ArticleParser] (§1.1 decomposition) responsible for resolving and
 * shaping the article body content (lead, body, media). It owns DOM-tree classification of
 * article nodes and the text-normalization helper used during deduplication.
 *
 * The class is intentionally stateless beyond the constructor-injected dependencies it shares
 * with [ArticleParser] (helpers as lambdas, regexes and tag-name sets as plain data). It returns
 * the parent's nested [ArticleParser.ArticleContent] data class so the existing public API and
 * the previously extracted [ArticleTaxonomyParser] stay compatible.
 */
internal class ArticleBodyParser(
        private val articleFromHtml: (String?) -> String?,
        private val cleanPollText: (String) -> String,
        private val articleContentRegexes: List<Regex>,
        private val leadClassMarkers: List<String>,
        private val articleBodyClassMarkers: List<String>,
        private val articleMediaClassMarkers: List<String>,
        private val skippedArticleClassMarkers: List<String>,
        private val articleBodyMetaClassMarkers: List<String>,
        private val contentHeadingTagNames: Set<String>,
        private val whitespaceRegex: Regex,
        private val extractYoutubeVideoId: (String) -> String?,
) : BaseParser() {

    /**
     * Phase-1 tap-to-open: prefer detail-regex / lightweight regex extraction and skip full-page
     * [Parser.parse] — mobile article HTML is ~350KB and DOM walks blocked first paint (~3.5s in logs).
     */
    fun resolveArticleBodyContent(
            pageContext: ArticleParser.ArticlePageContext,
            phase: ArticleParsePhase,
            regexFallback: String? = null
    ): ArticleParser.ArticleContent {
        regexFallback?.trim()?.takeIf { it.isNotBlank() }?.let { fallback ->
            return ArticleParser.ArticleContent(
                    prependArticleAnonsLead(pageContext.response, fallback),
                    false
            )
        }
        extractArticleContentByRegex(pageContext)?.let { regexContent ->
            // When the regex matched only entry-content (narrow capture), also look for standalone
            // lead/media nodes that are siblings (not inside the matched entry-content) and prepend them.
            val html = regexContent.html.orEmpty()
            if (!html.contains("article-header", ignoreCase = true) &&
                    !html.contains("article-meta", ignoreCase = true) &&
                    !html.contains("article-anons", ignoreCase = true)) {
                val prepended = prependStandaloneLeadAndMedia(pageContext, html)
                if (prepended != html) {
                    return ArticleParser.ArticleContent(prepended, regexContent.hasInlineHeroMedia || hasInlineMediaMarker(prepended))
                }
            }
            return regexContent
        }
        if (phase == ArticleParsePhase.FIRST_RENDER) {
            return ArticleParser.ArticleContent(null, false)
        }
        extractOrderedArticleContent(pageContext)?.let { return it }
        return ArticleParser.ArticleContent(null, false)
    }

    private fun prependStandaloneLeadAndMedia(
            pageContext: ArticleParser.ArticlePageContext,
            bodyHtml: String
    ): String {
        val document = pageContext.documentOrNull() ?: return bodyHtml
        val article = findArticleNode(document) ?: return bodyHtml
        val preBlocks = mutableListOf<String>()
        val seenTextBlocks = mutableSetOf<String>()
        for (child in article.getNodes()) {
            if (Parser.isNotElement(child)) continue
            if (isArticleHeaderNode(child)) continue
            if (isArticleFooterNode(child)) continue
            if (isArticleBodyNode(child)) continue
            if (isLeadNode(child) || isArticleMediaNode(child)) {
                val html = Parser.getHtml(child, false)
                val trimmed = html.trim()
                if (trimmed.isNotBlank()) {
                    val textKey = normalizeArticleText(trimmed)
                    if (textKey.isBlank() || seenTextBlocks.add(textKey)) {
                        preBlocks += trimmed
                    }
                }
            }
        }
        if (preBlocks.isEmpty()) return bodyHtml
        return preBlocks.joinToString("\n") + "\n" + bodyHtml
    }

    // Captures the lead paragraph(s) 4pda nests in `article-header > article-anons`. The block also
    // holds an `article-meta` (date/comment) row that has no `<p>`, so skipping to the first `<p>`
    // lands on the lead. Kept as a cheap raw-HTML regex (no DOM walk) so the fast first-render path
    // stays fast.
    private val articleAnonsLeadRegex = Regex(
            """(?is)<div\b(?=[^>]*\bclass\s*=\s*["'][^"']*\barticle-anons\b[^"']*["'])[^>]*>(?:(?!<p\b)[\s\S])*?((?:<p\b[^>]*>[\s\S]*?</p>\s*)+)"""
    )

    /**
     * The `detail_v2` body group starts *after* `<div class="article-header">`, so the lead
     * paragraph 4pda nests in `article-header > article-anons` never reaches the extracted body
     * (the reader saw the article jump straight from the title to the hero image). Pull that lead
     * out of the raw page and prepend it, unless [bodyHtml] already carries the same text (another
     * extraction path may have included it — keep the operation idempotent).
     */
    private fun prependArticleAnonsLead(response: String, bodyHtml: String): String {
        val lead = articleAnonsLeadRegex.find(response)
                ?.groupValues?.getOrNull(1)?.trim()
                ?.takeIf { it.isNotBlank() }
                ?: return bodyHtml
        val leadKey = normalizeArticleText(lead)
        if (leadKey.isBlank()) return bodyHtml
        if (normalizeArticleText(bodyHtml).contains(leadKey)) return bodyHtml
        return "$lead\n$bodyHtml"
    }

    private fun hasInlineMediaMarker(html: String): Boolean =
            html.contains("data-lightbox=\"post-", ignoreCase = true) ||
                    html.contains("data-lightbox='post-", ignoreCase = true)

    fun extractArticleContent(
            pageContext: ArticleParser.ArticlePageContext,
            phase: ArticleParsePhase = ArticleParsePhase.FULL
    ): ArticleParser.ArticleContent = resolveArticleBodyContent(pageContext, phase)

    private fun extractArticleContentByRegex(pageContext: ArticleParser.ArticlePageContext): ArticleParser.ArticleContent? {
        for (pattern in articleContentRegexes) {
            pattern.find(pageContext.response)?.groupValues?.get(1)?.let { body ->
                if (body.isNotBlank()) return ArticleParser.ArticleContent(body, false)
            }
        }
        return null
    }

    private fun extractOrderedArticleContent(pageContext: ArticleParser.ArticlePageContext): ArticleParser.ArticleContent? {
        val document = pageContext.documentOrNull() ?: return null
        val article = findArticleNode(document) ?: return null
        val blocks = mutableListOf<String>()
        val seenTextBlocks = mutableSetOf<String>()
        var hasInlineHeroMedia = false

        for (child in article.getNodes()) {
            if (Parser.isTextNode(child)) {
                appendArticleBlock(Parser.getHtml(child, false), blocks, seenTextBlocks)
                continue
            }
            if (Parser.isNotElement(child)) continue
            if (isArticleFooterNode(child)) continue
            if (isArticleHeaderNode(child)) {
                appendArticleHeaderLead(child, blocks, seenTextBlocks)
                continue
            }
            if (shouldSkipArticleChild(child)) continue

            when {
                isArticleBodyNode(child) -> {
                    if (appendArticleBodyChildren(child, blocks, seenTextBlocks)) {
                        hasInlineHeroMedia = true
                    }
                }
                isLeadNode(child) || isArticleMediaNode(child) -> {
                    if (isArticleMediaNode(child)) hasInlineHeroMedia = true
                    appendArticleBlock(Parser.getHtml(child, false), blocks, seenTextBlocks)
                }
                hasArticleContentMarker(child) -> {
                    appendArticleBlock(Parser.getHtml(child, false), blocks, seenTextBlocks)
                }
            }
        }

        return blocks
                .takeIf { it.isNotEmpty() }
                ?.joinToString("\n")
                ?.let { ArticleParser.ArticleContent(it, hasInlineHeroMedia) }
    }

    private fun appendArticleBodyChildren(
            node: Node,
            blocks: MutableList<String>,
            seenTextBlocks: MutableSet<String>
    ): Boolean {
        var hasInlineMedia = false
        node.getNodes().forEach { child ->
            if (Parser.isNotElement(child)) {
                appendArticleBlock(Parser.getHtml(child, false), blocks, seenTextBlocks)
                return@forEach
            }
            if (shouldSkipArticleBodyChild(child)) return@forEach
            if (isArticleMediaNode(child)) hasInlineMedia = true
            appendArticleBlock(Parser.getHtml(child, false), blocks, seenTextBlocks)
        }
        return hasInlineMedia
    }

    private fun appendArticleHeaderLead(
            node: Node,
            blocks: MutableList<String>,
            seenTextBlocks: MutableSet<String>
    ) {
        node.getNodes().forEach { child ->
            if (Parser.isNotElement(child)) return@forEach
            if (isArticleAnonsNode(child) || isLeadNode(child)) {
                appendArticleLeadChildren(child, blocks, seenTextBlocks)
            }
        }
    }

    private fun appendArticleLeadChildren(
            node: Node,
            blocks: MutableList<String>,
            seenTextBlocks: MutableSet<String>
    ) {
        node.getNodes().forEach { child ->
            if (Parser.isNotElement(child)) {
                appendArticleBlock(Parser.getHtml(child, false), blocks, seenTextBlocks)
                return@forEach
            }
            if (isArticleMetaNode(child)) return@forEach
            appendArticleBlock(Parser.getHtml(child, false), blocks, seenTextBlocks)
        }
    }

    private fun appendArticleBlock(
            html: String,
            blocks: MutableList<String>,
            seenTextBlocks: MutableSet<String>
    ) {
        val trimmed = html.trim()
        if (trimmed.isBlank()) return
        val textKey = normalizeArticleText(trimmed)
        if (textKey.isNotBlank() && !seenTextBlocks.add(textKey)) return
        blocks += trimmed
    }

    private fun findArticleNode(node: Node?): Node? {
        if (node == null || Parser.isNotElement(node)) return null
        if (isMainArticleNode(node)) return node
        node.getNodes().forEach { child ->
            findArticleNode(child)?.let { return it }
        }
        return null
    }

    private fun isMainArticleNode(node: Node): Boolean {
        val tag = node.name.orEmpty()
        val classes = node.getAttribute("class").orEmpty()
        if (!tag.equals("article", ignoreCase = true) &&
                !hasClassMarker(classes, listOf("article"))) {
            return false
        }
        if (hasClassMarker(classes, listOf("article-header", "article-footer"))) return false
        return hasDescendant(node) { child ->
            isArticleBodyNode(child) || isLeadNode(child) || isArticleMediaNode(child)
        }
    }

    private fun hasDescendant(node: Node, predicate: (Node) -> Boolean): Boolean {
        node.getNodes().forEach { child ->
            if (Parser.isNotElement(child)) return@forEach
            if (predicate(child) || hasDescendant(child, predicate)) return true
        }
        return false
    }

    private fun isArticleBodyNode(node: Node): Boolean {
        val classes = node.getAttribute("class").orEmpty()
        return hasClassMarker(classes, articleBodyClassMarkers) ||
                node.getAttribute("itemprop").orEmpty().equals("articleBody", ignoreCase = true)
    }

    private fun isLeadNode(node: Node): Boolean =
            hasClassMarker(node.getAttribute("class").orEmpty(), leadClassMarkers)

    private fun isArticleHeaderNode(node: Node): Boolean =
            hasClassMarker(node.getAttribute("class").orEmpty(), listOf("article-header"))

    private fun isArticleAnonsNode(node: Node): Boolean =
            hasClassMarker(node.getAttribute("class").orEmpty(), listOf("article-anons"))

    private fun isArticleMetaNode(node: Node): Boolean =
            hasClassMarker(node.getAttribute("class").orEmpty(), listOf("article-meta"))

    private fun isArticleMediaNode(node: Node): Boolean {
        val tag = node.name.orEmpty()
        if (tag.equals("figure", ignoreCase = true) ||
                tag.equals("picture", ignoreCase = true) ||
                tag.equals("video", ignoreCase = true) ||
                tag.equals("iframe", ignoreCase = true) ||
                tag.equals("embed", ignoreCase = true) ||
                tag.equals("object", ignoreCase = true) ||
                tag.equals("oembed", ignoreCase = true) ||
                tag.equals("img", ignoreCase = true)) {
            return true
        }
        val classes = node.getAttribute("class").orEmpty()
        if (hasClassMarker(classes, articleMediaClassMarkers)) {
            return true
        }
        return findYoutubeUrlInNode(node) != null
    }

    private fun hasArticleContentMarker(node: Node): Boolean {
        val tag = node.name.orEmpty()
        if (tag.equals("p", ignoreCase = true) ||
                tag.equals("blockquote", ignoreCase = true) ||
                contentHeadingTagNames.any { tag.equals(it, ignoreCase = true) } ||
                tag.equals("pre", ignoreCase = true) ||
                tag.equals("hr", ignoreCase = true) ||
                tag.equals("ul", ignoreCase = true) ||
                tag.equals("ol", ignoreCase = true)) {
            return normalizeArticleText(Parser.getHtml(node, true)).isNotBlank()
        }
        return false
    }

    private fun isArticleFooterNode(node: Node): Boolean =
            hasClassMarker(node.getAttribute("class").orEmpty(), listOf("article-footer"))

    private fun shouldSkipArticleChild(node: Node): Boolean =
            hasClassMarker(node.getAttribute("class").orEmpty(), skippedArticleClassMarkers) ||
                    node.name.orEmpty().equals("meta", ignoreCase = true) ||
                    node.name.orEmpty().equals("script", ignoreCase = true) ||
                    node.name.orEmpty().equals("style", ignoreCase = true)

    private fun shouldSkipArticleBodyChild(node: Node): Boolean {
        if (shouldSkipArticleChild(node) || isArticleHeaderNode(node)) return true
        val tag = node.name.orEmpty()
        val classes = node.getAttribute("class").orEmpty()
        return tag.equals("time", ignoreCase = true) ||
                tag.equals("h1", ignoreCase = true) ||
                hasArticleBodyMetaClass(classes)
    }

    private fun findYoutubeUrlInNode(node: Node): String? {
        if (Parser.isNotElement(node)) return null
        val tag = node.name.orEmpty()
        if (tag.equals("iframe", ignoreCase = true) || tag.equals("embed", ignoreCase = true)) {
            listOf("src", "data-src").forEach { attr ->
                articleFromHtml(node.getAttribute(attr))
                        ?.takeIf { extractYoutubeVideoId(it) != null }
                        ?.let { return it }
            }
        }
        if (tag.equals("oembed", ignoreCase = true)) {
            articleFromHtml(node.getAttribute("url"))
                    ?.takeIf { extractYoutubeVideoId(it) != null }
                    ?.let { return it }
        }
        if (tag.equals("a", ignoreCase = true)) {
            articleFromHtml(node.getAttribute("href"))
                    ?.takeIf { extractYoutubeVideoId(it) != null }
                    ?.let { return it }
        }
        node.getNodes().forEach { child ->
            findYoutubeUrlInNode(child)?.let { return it }
        }
        return null
    }

    private fun hasClassMarker(classes: String, markers: List<String>): Boolean {
        val normalizedClasses = classes.lowercase()
        return markers.any { marker -> normalizedClasses.contains(marker.lowercase()) }
    }

    private fun hasArticleBodyMetaClass(classes: String): Boolean {
        val tokens = classes
                .lowercase()
                .split(Regex("\\s+"))
                .filter { it.isNotBlank() }
                .toSet()
        return articleBodyMetaClassMarkers.any { marker -> marker.lowercase() in tokens }
    }

    fun normalizeArticleText(html: String): String =
            cleanPollText(html)
                    .replace('\u00A0', ' ')
                    .replace(whitespaceRegex, " ")
                    .trim()
                    .lowercase()
}
