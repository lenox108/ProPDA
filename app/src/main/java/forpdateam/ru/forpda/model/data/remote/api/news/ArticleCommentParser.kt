package forpdateam.ru.forpda.model.data.remote.api.news

import forpdateam.ru.forpda.model.data.remote.ParserPatterns
import forpdateam.ru.forpda.model.data.remote.api.regex.parser.Node
import forpdateam.ru.forpda.model.data.remote.api.regex.parser.Parser
import forpdateam.ru.forpda.model.data.remote.parser.BaseParser
import forpdateam.ru.forpda.model.data.storage.IPatternProvider

/**
 * Sub-parser extracted from [ArticleParser] (§1.1 decomposition) responsible for locating and
 * extracting the comment tree (the &lt;ul/ol class="…comment-list…"&gt; block) from a raw article
 * response. It does not parse the comment tree itself — that logic stays in [ArticleParser] —
 * only the DOM-walk and HTML-shaping helpers.
 *
 * The class is intentionally stateless beyond the constructor-injected dependencies it shares
 * with [ArticleParser] (the [IPatternProvider] and the `commentNumericIdRegex`). It returns
 * plain HTML strings / DOM [Node] references so the existing public API stays compatible.
 */
internal class ArticleCommentParser(
        private val patternProvider: IPatternProvider,
        private val commentNumericIdRegex: Regex,
) : BaseParser() {

    fun stripCommentForm(comments: String?): String? {
        val raw = comments ?: return null
        return patternProvider
                .getPattern(ParserPatterns.Articles.scope, ParserPatterns.Articles.exclude_form_comment)
                .matcher(raw)
                .replaceFirst("")
    }

    /**
     * Если группа detail/detail_v2 не совпала с вёрсткой 4PDA, вытаскиваем <ul/ol class="…comment-list…"> из полного HTML.
     */
    fun extractCommentsUlHtmlFromPage(pageContext: ArticleParser.ArticlePageContext): String? {
        if (pageContext.response.isBlank()) return null
        return try {
            val doc = pageContext.documentOrNull() ?: return null
            val ul = findUlCommentList(doc)
            if (ul == null) return null
            Parser.getHtml(ul, false)
        } catch (ex: Exception) {
            // The debug log is recorded by the caller (ArticleParser) to preserve the
            // pre-extraction behavior.
            null
        }
    }

    fun findUlCommentList(node: Node?): Node? {
        if (node == null) return null
        if (Parser.isNotElement(node)) return null
        if ("ul".equals(node.name, ignoreCase = true) || "ol".equals(node.name, ignoreCase = true)) {
            val cls = node.getAttribute("class")
            if (cls != null && (cls.contains("comment-list") || cls.contains("comments-list"))) return node
        }
        val nodes = node.getNodes() ?: return null
        for (i in 0 until nodes.size) {
            findUlCommentList(nodes[i])?.let { return it }
        }
        return null
    }

    fun findCommentAnchorNode(li: Node): Node? {
        Parser.findNode(li, "div", "id", "comment-")?.let { return it }
        Parser.findNode(li, "article", "id", "comment-")?.let { return it }
        Parser.findNode(li, "a", "id", "comment")?.let { anchor ->
            commentNumericIdFromAttribute(anchor.getAttribute("id"))?.let { return anchor }
        }
        commentNumericIdFromAttribute(li.getAttribute("data-comment-id"))?.let { return li }
        commentNumericIdFromAttribute(li.getAttribute("data-comment"))?.let { return li }
        return findNodeWithCommentId(li)
    }

    fun commentNumericIdFromAttribute(raw: String?): Int? {
        val value = raw?.trim().orEmpty()
        if (value.isBlank()) return null
        commentNumericIdRegex.find(value)?.groupValues?.getOrNull(1)?.toIntOrNull()?.takeIf { it > 0 }?.let { return it }
        return value.toIntOrNull()?.takeIf { it > 0 }
    }

    /** Обход, если id вынесен не на div (например data-атрибуты меняли вёрстку). */
    fun findNodeWithCommentId(node: Node?): Node? {
        if (node == null || Parser.isNotElement(node)) return null
        val id = node.getAttribute("id")
        if (id != null && id.contains("comment-")) {
            val m = patternProvider.getPattern(ParserPatterns.Articles.scope, ParserPatterns.Articles.comment_id).matcher(id)
            if (m.find()) return node
        }
        val nodes = node.getNodes() ?: return null
        for (i in 0 until nodes.size) {
            findNodeWithCommentId(nodes[i])?.let { return it }
        }
        return null
    }

    fun findCommentContentNode(scope: Node): Node? {
        // Prefer explicit comment body containers. Generic ".content" is too broad and may capture
        // nested article blocks or wrappers, leading to "duplicated article" inside comment body.
        Parser.findNode(scope, "div", "class", "comment-content")?.let { return it }
        Parser.findNode(scope, "p", "class", "comment-content")?.let { return it }
        Parser.findNode(scope, "p", "class", "content")?.let { return it }
        Parser.findNode(scope, "div", "class", "content")?.let { return it }
        return null
    }
}
