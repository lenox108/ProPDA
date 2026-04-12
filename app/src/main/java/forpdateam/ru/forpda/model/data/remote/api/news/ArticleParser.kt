package forpdateam.ru.forpda.model.data.remote.api.news

import android.util.Log
import android.util.SparseArray
import forpdateam.ru.forpda.BuildConfig
import forpdateam.ru.forpda.entity.remote.news.*
import forpdateam.ru.forpda.model.data.remote.ParserPatterns
import forpdateam.ru.forpda.model.data.remote.api.ApiUtils
import forpdateam.ru.forpda.model.data.remote.api.regex.parser.Node
import forpdateam.ru.forpda.model.data.remote.api.regex.parser.Parser
import forpdateam.ru.forpda.model.data.remote.parser.BaseParser
import forpdateam.ru.forpda.model.data.storage.IPatternProvider
import java.util.regex.Matcher

class ArticleParser(
        private val patternProvider: IPatternProvider
) : BaseParser() {

    private val scope = ParserPatterns.Articles

    fun parseArticles(response: String): List<NewsItem> = patternProvider
            .getPattern(scope.scope, scope.list)
            .matcher(response)
            .map { matcher ->
                NewsItem().apply {
                    val isReview = matcher.group(1) == null
                    if (!isReview) {
                        url = matcher.group(1).orEmpty()
                        id = matcher.group(2).orEmpty().toIntOrNull() ?: 0
                        title = matcher.group(3).orEmpty().fromHtml().fromHtml()
                        imgUrl = matcher.group(4)
                        commentsCount = matcher.group(5).orEmpty().toIntOrNull() ?: 0
                        date = matcher.group(6)
                        authorId = matcher.group(7).orEmpty().toIntOrNull() ?: 0
                        author = matcher.group(8).orEmpty().fromHtml()
                        description = matcher.group(9).orEmpty().fromHtml()
                        matcher.group(10)?.let {
                            tags.addAll(parseTags(it))
                        }
                    } else {
                        url = matcher.group(11).orEmpty()
                        id = matcher.group(12).orEmpty().toIntOrNull() ?: 0
                        imgUrl = matcher.group(13)
                        title = matcher.group(14).orEmpty().fromHtml().fromHtml()
                        commentsCount = matcher.group(15).orEmpty().toIntOrNull() ?: 0
                        date = matcher.group(17).orEmpty().replace('-', '.')
                        author = matcher.group(18).orEmpty().fromHtml()
                        description = matcher.group(20).orEmpty().trim().fromHtml()
                    }
                }
            }

    fun parseArticle(response: String): DetailsPage = patternProvider
            .getPattern(scope.scope, ParserPatterns.Articles.detail_detector)
            .matcher(response)
            .mapOnce {
                val hasV1 = !it.group(1).isNullOrEmpty()
                val hasV2 = !it.group(2).isNullOrEmpty()
                when {
                    hasV1 -> parseArticleV1(response)
                    hasV2 -> parseArticleV2(response)
                    else -> null
                }
            } ?: throw Exception("Not found article type")

    private fun parseArticleV1(response: String): DetailsPage = patternProvider
            .getPattern(scope.scope, scope.detail)
            .matcher(response)
            .mapOnce { matcher ->
                DetailsPage().apply {
                    id = matcher.group(1).orEmpty().toIntOrNull() ?: 0
                    imgUrl = matcher.group(3)
                    title = matcher.group(4).orEmpty().fromHtml()
                    matcher.group(5)?.let {
                        tags.addAll(parseTags(it))
                    }
                    date = matcher.group(6)
                    authorId = matcher.group(7).orEmpty().toIntOrNull() ?: 0
                    author = matcher.group(8).orEmpty().fromHtml()
                    commentsCount = matcher.group(9).orEmpty().toIntOrNull() ?: 0
                    html = matcher.group(10)
                    matcher.group(11)?.also {
                        materials.addAll(parseMaterials(it))
                    }
                    //todo ignore group 12 after 22 PatternVersion
                    navId = matcher.group(12)

                    karmaMap = parseKarma(response)

                    commentsSource = stripCommentForm(matcher.group(13))
                    if (commentsSource.isNullOrBlank()) {
                        commentsSource = stripCommentForm(extractCommentsUlHtmlFromPage(response))
                    }

                    /*Comment commentTree = parseComments(getKarmaMap(), getCommentsSource());
                    setCommentTree(commentTree);*/
                }
            } ?: throw Exception("Not found article by pattern v1")

    private fun parseArticleV2(response: String): DetailsPage = patternProvider
            .getPattern(scope.scope, scope.detail_v2)
            .matcher(response)
            .mapOnce { matcher ->
                DetailsPage().apply {
                    id = matcher.group(1).orEmpty().toIntOrNull() ?: 0

                    patternProvider
                            .getPattern(ParserPatterns.Global.scope, ParserPatterns.Global.meta_tags)
                            .matcher(response)
                            .findAll {
                                val metaTarget = it.group(1)
                                val metaType = it.group(2)
                                val metaContent = it.group(3)
                                if (metaTarget == "og" && metaType == "image") {
                                    imgUrl = metaContent
                                }
                            }

                    //imgUrl = matcher.group(3)
                    title = matcher.group(3).orEmpty().fromHtml()
                    date = matcher.group(4)

                    //Дефолтный юзер с ником News
                    authorId = 204809
                    author = "News"

                    commentsCount = matcher.group(5).orEmpty().toIntOrNull() ?: 0
                    html = matcher.group(6)
                    matcher.group(7)?.let {
                        tags.addAll(parseTags(it))
                    }
                    matcher.group(8)?.also {
                        materials.addAll(parseMaterials(it))
                    }
                    //todo ignore group 9 after 22 PatternVersion
                    navId = matcher.group(9)

                    karmaMap = parseKarma(response)

                    commentsSource = stripCommentForm(matcher.group(10))
                    if (commentsSource.isNullOrBlank()) {
                        commentsSource = stripCommentForm(extractCommentsUlHtmlFromPage(response))
                    }

                    /*Comment commentTree = parseComments(getKarmaMap(), getCommentsSource());
                    setCommentTree(commentTree);*/
                }
            } ?: throw Exception("Not found article by pattern v2")

    private fun parseMaterials(source: String): List<Material> = patternProvider
            .getPattern(scope.scope, scope.materials)
            .matcher(source)
            .map {
                Material().apply {
                    imageUrl = it.group(1)
                    id = it.group(2).orEmpty().toIntOrNull() ?: 0
                    title = it.group(3).orEmpty().fromHtml()
                }
            }

    private fun parseTags(source: String): List<Tag> = patternProvider
            .getPattern(scope.scope, scope.tags)
            .matcher(source)
            .map {
                Tag().apply {
                    tag = it.group(1).orEmpty()
                    title = it.group(2).orEmpty().fromHtml()
                }
            }

    private fun parseKarma(source: String): SparseArray<Comment.Karma> {
        val result = SparseArray<Comment.Karma>()
        patternProvider
                .getPattern(scope.scope, scope.karmaSource)
                .matcher(source)
                .findOnce {
                    if (BuildConfig.DEBUG) {
                        Log.d("ArticleParser", "karma: ${it.group(1)}")
                    }
                    patternProvider
                            .getPattern(scope.scope, scope.karma)
                            .matcher(it.group(1).orEmpty())
                            .findAll {
                                try {
                                    val commentId = it.group(1).orEmpty().toIntOrNull() ?: 0
                                    result.put(commentId, Comment.Karma().apply {
                                        status = it.group(2).orEmpty().toIntOrNull() ?: 0
                                        count = it.group(5).orEmpty().toIntOrNull() ?: 0
                                    })
                                } catch (ex: Exception) {
                                    ex.printStackTrace()
                                }
                            }
                }
        return result
    }

    private fun stripCommentForm(comments: String?): String? {
        val raw = comments ?: return null
        return patternProvider
                .getPattern(scope.scope, scope.exclude_form_comment)
                .matcher(raw)
                .replaceFirst("")
    }

    /**
     * Если группа detail/detail_v2 не совпала с вёрсткой 4PDA, вытаскиваем &lt;ul class="…comment-list…"&gt; из полного HTML.
     */
    private fun extractCommentsUlHtmlFromPage(fullHtml: String): String? {
        if (fullHtml.isBlank()) return null
        return try {
            val doc = Parser.parse(fullHtml)
            val ul = findUlCommentList(doc) ?: return null
            Parser.getHtml(ul, false)
        } catch (ex: Exception) {
            if (BuildConfig.DEBUG) {
                Log.w("ArticleParser", "extractCommentsUlHtmlFromPage", ex)
            }
            null
        }
    }

    private fun findUlCommentList(node: Node?): Node? {
        if (node == null) return null
        if (Parser.isNotElement(node)) return null
        if ("ul".equals(node.getName(), ignoreCase = true)) {
            val cls = node.getAttribute("class")
            if (cls != null && (cls.contains("comment-list") || cls.contains("comments-list"))) return node
        }
        val nodes = node.getNodes() ?: return null
        for (i in 0 until nodes.size) {
            findUlCommentList(nodes[i])?.let { return it }
        }
        return null
    }

    private fun findCommentAnchorNode(li: Node): Node? {
        Parser.findNode(li, "div", "id", "comment-")?.let { return it }
        Parser.findNode(li, "article", "id", "comment-")?.let { return it }
        return findNodeWithCommentId(li)
    }

    /** Обход, если id вынесен не на div (например data-атрибуты меняли вёрстку). */
    private fun findNodeWithCommentId(node: Node?): Node? {
        if (node == null || Parser.isNotElement(node)) return null
        val id = node.getAttribute("id")
        if (id != null && id.contains("comment-")) {
            val m = patternProvider.getPattern(scope.scope, scope.comment_id).matcher(id)
            if (m.find()) return node
        }
        val nodes = node.getNodes() ?: return null
        for (i in 0 until nodes.size) {
            findNodeWithCommentId(nodes[i])?.let { return it }
        }
        return null
    }

    private fun findCommentContentNode(scope: Node): Node? {
        Parser.findNode(scope, "p", "class", "content")?.let { return it }
        Parser.findNode(scope, "div", "class", "content")?.let { return it }
        Parser.findNode(scope, "div", "class", "comment-content")?.let { return it }
        return null
    }

    fun parseComments(karmaMap: SparseArray<Comment.Karma>, source: String?): Comment {
        val comments = Comment()
        if (source != null) {
            val document = Parser.parse(source)
            recurseComments(karmaMap, document, comments, 0)
        }
        return comments
    }


    private fun recurseComments(karmaMap: SparseArray<Comment.Karma>, root: Node, parentComment: Comment, argLevel: Int): Comment {
        var level = argLevel
        val rootComments = Parser.findNode(root, "ul", "class", "comment-list")
                ?: Parser.findNode(root, "ul", "class", "comments-list")
                ?: findUlCommentList(root)
        if (rootComments == null) {
            return parentComment
        }
        val commentNodes = Parser.findChildNodes(rootComments, "li", null, null)

        /*if (commentNodes.size() == 0) {
            return null;
        }*/
        for (commentNode in commentNodes) {
            val comment = Comment()

            var id: String?
            var userId: String?
            var userNick: String?
            var date: String?
            var content: String?
            var matcher: Matcher
            val anchorNode = findCommentAnchorNode(commentNode) ?: continue

            id = anchorNode.getAttribute("id")
            if (id != null) {
                matcher = patternProvider
                        .getPattern(scope.scope, scope.comment_id)
                        .matcher(id)
                if (matcher.find()) {
                    id = matcher.group(1)
                    comment.id = id?.toIntOrNull() ?: 0
                }
            }

            val deletedString = anchorNode.getAttribute("class")
            val isDeleted = deletedString != null && deletedString.contains("deleted")
            comment.isDeleted = isDeleted

            if (!isDeleted) {
                val avatarNode = Parser.findNode(commentNode, "a", "class", "comment-avatar")
                val nickNode = Parser.findNode(commentNode, "a", "class", "nickname")
                        ?: Parser.findNode(commentNode, "span", "class", "nickname")
                val metaNode = Parser.findNode(commentNode, "a", "class", "date")

                userId = avatarNode?.getAttribute("href")
                if (userId != null) {
                    matcher = patternProvider
                            .getPattern(scope.scope, scope.comment_user_id)
                            .matcher(userId)
                    if (matcher.find()) {
                        userId = matcher.group(1)
                        comment.userId = userId?.toIntOrNull() ?: 0
                    }
                }

                userNick = nickNode?.let { Parser.getHtml(it, true) }
                comment.userNick = ApiUtils.fromHtml(userNick.orEmpty())

                date = metaNode?.let { Parser.ownText(metaNode).trim() }
                comment.date = date
            }

            val contentNode = findCommentContentNode(commentNode)
            content = contentNode?.let { Parser.getHtml(it, true) }
            comment.content = ApiUtils.fromHtml(content.orEmpty())
            comment.level = level
            comment.karma = karmaMap.get(comment.id)

            parentComment.children.add(comment)

            level++
            recurseComments(karmaMap, commentNode, comment, level)
            level--
        }

        return parentComment
    }

}
