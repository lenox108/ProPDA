package forpdateam.ru.forpda.model.data.remote.api.news

import android.util.SparseArray
import forpdateam.ru.forpda.BuildConfig
import forpdateam.ru.forpda.diagnostic.ArticleParseTrace
import forpdateam.ru.forpda.diagnostic.FpdaDebugLog
import forpdateam.ru.forpda.common.Cp1251Codec
import forpdateam.ru.forpda.entity.remote.news.Comment
import forpdateam.ru.forpda.entity.remote.news.CommentKarmaVoteResult
import forpdateam.ru.forpda.entity.remote.news.DetailsPage
import forpdateam.ru.forpda.entity.remote.news.NewsItem
import forpdateam.ru.forpda.model.data.remote.IWebClient
import forpdateam.ru.forpda.model.data.remote.api.NetworkRequest
import forpdateam.ru.forpda.model.data.remote.api.NetworkResponse
import forpdateam.ru.forpda.model.data.remote.api.news.Constants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import timber.log.Timber
import java.net.URLEncoder
import java.util.Locale

data class CommentEditContext(
        val commentsSource: String? = null,
        val articleHtml: String? = null,
        val articleUrl: String? = null,
        val articleId: Int = 0,
)

data class ArticleFetchResult(
        val page: DetailsPage,
        val rawBody: String,
        val response: NetworkResponse,
        val originalUrl: String,
        val probeUrl: String,
        /** Parser body HTML before [ArticleTemplate] mapping; used for phase-2 poll merge. */
        val parsedBodyHtml: String = page.html.orEmpty()
)

/**
 * Created by radiationx on 31.07.16.
 */
class NewsApi(
        private val webClient: IWebClient,
        private val articleParser: ArticleParser
) {

    fun getNews(category: String, pageNumber: Int): List<NewsItem> {
        if (category == Constants.NEWS_CATEGORY_TECH) {
            return getTechNews(pageNumber)
        }
        val url = getLink(category, pageNumber)
        val response = webClient.get(url)
        return articleParser.parseArticles(response.body)
    }

    private fun getTechNews(pageNumber: Int): List<NewsItem> {
        return runBlocking {
            val gate = Semaphore(TECH_NEWS_CONCURRENCY)
            TECH_URLS
                    .map { url ->
                        async(Dispatchers.IO) {
                            gate.withPermit {
                                articleParser.parseArticles(webClient.get(getPageLink(url, pageNumber)).body)
                            }
                        }
                    }
                    .awaitAll()
                    .flatten()
                    .distinctBy { it.id }
        }
    }

    fun getDetails(id: Int): DetailsPage =
            fetchArticleDetails("https://4pda.to/index.php?p=$id").page

    fun getDetails(url: String): DetailsPage = fetchArticleDetails(url).page

    fun fetchArticleDetails(
            url: String,
            phase: ArticleParsePhase = ArticleParsePhase.FIRST_RENDER,
            bypassCache: Boolean = false
    ): ArticleFetchResult {
        val response = webClient.request(buildArticleRequest(url, bypassCache))
        val body = response.body
        if (BuildConfig.DEBUG) {
            // Debug-only: trace the raw response characteristics without logging sensitive data.
            // For successful 2xx/3xx skip the SHA-256 classifyHtml (it costs O(8KB) per fetch);
            // log only endpoint+code+len. For 4xx/5xx/redirects keep the full classifier
            // since those are the paths we actually want fingerprints for.
            val needsFullClassify = response.code !in 200..399
            FpdaDebugLog.log(
                    FpdaDebugLog.TAG_COMMENTS_SECTION,
                    "article_response",
                    buildMap {
                        put("endpoint", FpdaDebugLog.sanitizeUrl(response.url.ifBlank { url }))
                        put("code", response.code)
                        if (needsFullClassify) {
                            putAll(FpdaDebugLog.classifyHtml(body))
                        } else {
                            put("htmlLen", body.length)
                        }
                    }
            )
        }
        rejectUnexpectedArticleBody(body)
        syncPollVoteCookies()
        val article = articleParser.parseArticle(body, phase)
        article.url = response.redirectWithFragment
        val probeUrl = response.redirect.takeIf { it.isNotBlank() } ?: url
        if (BuildConfig.DEBUG && article.html.isNullOrBlank()) {
            ArticleParseTrace.log(
                    event = "network_empty_body",
                    articleId = article.id.takeIf { it > 0 },
                    bodyLen = body.length,
                    reason = "empty_after_parse"
            )
        }
        return ArticleFetchResult(
                page = article,
                rawBody = body,
                response = response,
                originalUrl = url,
                probeUrl = probeUrl,
                parsedBodyHtml = article.html.orEmpty()
        )
    }

    /** Phase-2: optional second network fetch for desktop comments/poll (not on first-render path). */
    suspend fun enrichDesktopExtras(fetch: ArticleFetchResult): DetailsPage =
            loadDesktopExtrasIfMissing(fetch.originalUrl, fetch.response, fetch.page, fetch.rawBody)

    fun enrichArticleMetadata(page: DetailsPage, rawBody: String) {
        articleParser.enrichArticleMetadata(page, rawBody)
    }

    /**
     * Fetch the desktop article page (desktop UA, no mobile cookie) and extract its server-rendered
     * comment list. The phase-1 mobile page ships an EMPTY `<ul class="comment-list">` shell even for
     * articles with hundreds of comments (they are lazy-loaded by the mobile site's JS), so this is
     * the reliable way to obtain real comment nodes when the own comment count is positive.
     *
     * Returns the comment-list HTML only when it carries actual comment NODES (not another empty
     * shell); otherwise null so callers can keep the article's own count and surface a retry.
     */
    fun loadDesktopCommentsSource(url: String): String? {
        if (url.isBlank()) return null
        val body = loadDesktopArticleBody(url, bypassCache = false)?.takeIf { it.isNotBlank() }
                ?: return null
        val source = articleParser.extractCommentsSourceFromPage(body)
                ?.takeIf { it.contains("comment", ignoreCase = true) }
                ?: return null
        return source.takeIf { articleParser.hasCommentNodeMarkup(it) }
    }

    /**
     * Fetches one WordPress comment page (`cp` / comment-page-N) and returns the comment-list HTML.
     * Used for paginated inline comments — one network round-trip per batch (~20 nodes).
     */
    fun fetchCommentsPageSource(articleUrl: String, commentPage: Int): String? {
        if (articleUrl.isBlank() || commentPage <= 0) return null
        val pageUrl = forpdateam.ru.forpda.presentation.articles.detail.comments
                .ArticleCommentsPagination.withCommentPage(articleUrl, commentPage)
        val body = loadDesktopArticleBody(pageUrl, bypassCache = false)?.takeIf { it.isNotBlank() }
                ?: return null
        val source = articleParser.extractCommentsSourceFromPage(body)
                ?.takeIf { it.contains("comment", ignoreCase = true) }
                ?: return null
        return source.takeIf { articleParser.hasCommentNodeMarkup(it) }
    }

    fun parseCommentsFromSource(
            article: DetailsPage,
            source: String?,
            paginated: Boolean = false,
            commentPage: Int = 1,
    ): Comment {
        val snapshot = DetailsPage().apply {
            id = article.id
            commentsCount = article.commentsCount
            karmaMap = article.karmaMap
            commentsSource = source
            desktopCommentsSource = article.desktopCommentsSource
            url = article.url
        }
        return parseComments(snapshot, paginated = paginated, commentPage = commentPage)
    }

    private fun rejectUnexpectedArticleBody(body: String) {
        when (ArticleHtmlValidator.classifyRawHtml(body)) {
            ArticleHtmlValidator.PageKind.LOGIN ->
                    throw IllegalStateException("login_page")
            ArticleHtmlValidator.PageKind.ERROR,
            ArticleHtmlValidator.PageKind.CAPTCHA ->
                    throw IllegalStateException("error_page")
            else -> Unit
        }
        if (body.length < ArticleHtmlValidator.MIN_RAW_HTML_LEN &&
                !ArticleHtmlValidator.looksLikeArticlePage(body)) {
            throw IllegalStateException("unexpected_html")
        }
    }

    private suspend fun loadDesktopExtrasIfMissing(
            originalUrl: String,
            primaryResponse: NetworkResponse,
            article: DetailsPage,
            @Suppress("UNUSED_PARAMETER") cachedPrimaryBody: String? = null
    ): DetailsPage = coroutineScope {
        val probeUrl = primaryResponse.redirect.takeIf { it.isNotBlank() } ?: originalUrl
        val probeComments = shouldProbeDesktopComments(originalUrl, probeUrl, article)
        val articleHasRenderablePoll = articleParser.hasNormalizedNewsPollBlock(article.html) ||
                articleParser.hasFallbackNewsPollBlock(article.html)
        val probePoll = !articleHasRenderablePoll && shouldProbeDesktopPoll(originalUrl, probeUrl, article)
        if (!probeComments && !probePoll) return@coroutineScope article

        if (probePoll) {
            syncPollVoteCookies()
        }

        val startedAt = System.currentTimeMillis()
        // Comments and poll need the same desktop body; fetch it once and
        // share the result. Previously each branch scheduled its own
        // loadDesktopArticleBody(probeUrl, ...) which doubled the network
        // round-trip and any side effects (cache writes, log emissions).
        val bodyDeferred = async(Dispatchers.IO) {
            loadDesktopArticleBody(probeUrl, bypassCache = false)
        }

        var commentsError: Throwable? = null
        var pollError: Throwable? = null
        val body = runCatching { bodyDeferred.await() }.getOrElse { e ->
            logDeferredExtrasError("desktop_body", e)
            commentsError = e
            pollError = e
            null
        }
        if (body != null) {
            if (probeComments) {
                article.desktopCommentsSource = articleParser.extractCommentsSourceFromPage(body)
                        ?.takeIf { it.contains("comment", ignoreCase = true) }
                        ?: run {
                            commentsError = IllegalStateException("no comments source in body")
                            null
                        }
            }
            if (probePoll) {
                articleParser.appendPollFromResponse(article, body)
            }
        }
        if (BuildConfig.DEBUG) {
            FpdaDebugLog.log(
                    FpdaDebugLog.TAG_COMMENTS_SECTION,
                    "parallel_extras",
                    mapOf(
                            "parallelExtrasMs" to (System.currentTimeMillis() - startedAt),
                            "probeComments" to probeComments,
                            "probePoll" to probePoll,
                            "commentsError" to commentsError?.let { it::class.java.simpleName },
                            "pollError" to pollError?.let { it::class.java.simpleName }
                    )
            )
        }
        article
    }

    private fun logDeferredExtrasError(stage: String, error: Throwable) {
        Timber.d(
                "NewsApi deferred extras stage=%s error=%s",
                stage,
                error::class.java.simpleName
        )
    }

    private fun syncPollVoteCookies() {
        articleParser.syncPollVoteCookies(webClient.getClientCookies())
    }

    private fun loadDesktopArticleBody(url: String, bypassCache: Boolean = false): String? =
            runCatching {
                webClient.requestWithoutMobileCookie(
                        NetworkRequest.Builder()
                                .copyFrom(buildArticleRequest(url, bypassCache))
                                .addHeader("User-Agent", DESKTOP_USER_AGENT)
                                .build()
                ).body
            }.getOrNull()

    private fun shouldProbeDesktopComments(originalUrl: String, probeUrl: String, article: DetailsPage): Boolean {
        if (!is4pdaArticleUrl(originalUrl, probeUrl, article)) return false
        val commentsSource = article.commentsSource
        if (commentsSource.isNullOrBlank()) return false
        if (!isAuthorized() || !commentsSource.contains("comment", ignoreCase = true)) return false
        val userId = currentUserId()
        if (articleParser.commentsSourceNeedsDesktopProbe(commentsSource, userId)) {
            return true
        }
        if (userId > 0 &&
                commentsSource.contains("comment-list", ignoreCase = true) &&
                !commentsSource.contains("act=rep", ignoreCase = true)) {
            return true
        }
        if (userId > 0 &&
                commentsSource.contains("act=rep", ignoreCase = true) &&
                !commentsSource.contains("editcomment", ignoreCase = true)) {
            return true
        }
        if (commentsSource.contains("comment-list", ignoreCase = true) &&
                commentsSource.contains("id=\"comment-", ignoreCase = true) &&
                !commentsSource.contains("showuser=", ignoreCase = true)) {
            return true
        }
        if (userId > 0 &&
                !commentsSource.contains("showuser=$userId", ignoreCase = true) &&
                commentsSource.contains("editcomment", ignoreCase = true)) {
            return false
        }
        return userId > 0
    }

    private fun buildArticleRequest(url: String, bypassCache: Boolean): NetworkRequest {
        val builder = NetworkRequest.Builder()
                .url(url)
                .addHeader("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
        if (bypassCache) {
            builder.addHeader("Cache-Control", "no-cache, no-store, must-revalidate")
            builder.addHeader("Pragma", "no-cache")
        }
        return builder.build()
    }

    private fun shouldProbeDesktopPoll(originalUrl: String, probeUrl: String, article: DetailsPage): Boolean {
        if (!is4pdaArticleUrl(originalUrl, probeUrl, article)) return false
        val hasPollCandidate = articleParser.hasFallbackNewsPollBlock(article.html) ||
                articleParser.hasRawTemplatePollMarker(article.html) ||
                article.id == KNOWN_DESKTOP_POLL_ARTICLE_ID ||
                originalUrl.contains("p=$KNOWN_DESKTOP_POLL_ARTICLE_ID", ignoreCase = true) ||
                probeUrl.contains("p=$KNOWN_DESKTOP_POLL_ARTICLE_ID", ignoreCase = true) ||
                article.title.orEmpty().contains("опрос", ignoreCase = true) ||
                article.html.orEmpty().contains("опрос", ignoreCase = true) ||
                article.html.orEmpty().contains("голос", ignoreCase = true)
        if (!hasPollCandidate) {
            return false
        }
        return originalUrl.contains("index.php?p=", ignoreCase = true) ||
                probeUrl.contains("index.php?p=", ignoreCase = true) ||
                articleSlugUrlRegex.containsMatchIn(originalUrl) ||
                articleSlugUrlRegex.containsMatchIn(probeUrl) ||
                article.id > 0
    }

    private fun is4pdaArticleUrl(originalUrl: String, probeUrl: String, article: DetailsPage): Boolean {
        if (!originalUrl.contains("4pda.to", ignoreCase = true) &&
                !probeUrl.contains("4pda.to", ignoreCase = true)) {
            return false
        }
        return originalUrl.contains("index.php?p=", ignoreCase = true) ||
                probeUrl.contains("index.php?p=", ignoreCase = true) ||
                articleSlugUrlRegex.containsMatchIn(originalUrl) ||
                articleSlugUrlRegex.containsMatchIn(probeUrl) ||
                article.id > 0
    }

    private fun isAuthorized(): Boolean =
            currentUserId() > 0 || webClient.getAuthKey().takeIf { it.isNotBlank() && it != "0" } != null

    private fun currentUserId(): Int =
            webClient.getClientCookies().values.firstNotNullOfOrNull { cookie ->
                cookie.value.toIntOrNull()?.takeIf {
                    cookie.name.equals("member_id", ignoreCase = true) && it > 0
                }
            }.orZero()

    private companion object {
        val articleSlugUrlRegex = Regex("""/\d{4}/\d{2}/\d{2}/\d+/""")
        const val KNOWN_DESKTOP_POLL_ARTICLE_ID = 456521
        const val DESKTOP_USER_AGENT = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
        const val TECH_NEWS_CONCURRENCY = 3
        val TECH_URLS = listOf(
                Constants.NEWS_URL_TECH_SMARTPHONES,
                Constants.NEWS_URL_TECH_LAPTOPS,
                Constants.NEWS_URL_TECH_AUDIO,
                Constants.NEWS_URL_TECH_MONITORS,
                Constants.NEWS_URL_TECH_APPLIANCES,
                Constants.NEWS_URL_TECH_PC
        )
    }

    fun sendPoll(from: String, pollId: Int, answersId: IntArray): DetailsPage {
        val pollHtml = votePoll(from, pollId, answersId)
        syncPollVoteCookies()
        return articleParser.parseArticle(pollHtml)
    }

    fun votePoll(from: String, pollId: Int, answersId: IntArray): String {
        require(pollId > 0) { "Invalid poll id" }
        require(answersId.isNotEmpty()) { "No poll answer selected" }
        val url = "https://4pda.to/pages/poll/?act=vote&poll_id=$pollId"
        val body = buildString {
            append("from=")
            append(URLEncoder.encode(from.ifBlank { "/pages/poll/?poll_id=$pollId" }, "UTF-8"))
            answersId.forEach {
                append("&answer%5B%5D=")
                append(URLEncoder.encode(it.toString(), "UTF-8"))
            }
        }
        val request = NetworkRequest.Builder()
                .url(url)
                .xhrHeader()
                .addHeader("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .rawBody(body)
                .build()

        val response = webClient.request(request)
        syncPollVoteCookies()
        val pollBlock = articleParser.extractNormalizedPollBlock(response.body, pollId.toString())
                ?: articleParser.extractNormalizedPollBlock(response.body)
        return pollBlock ?: throw IllegalStateException("Unable to read updated poll")
    }

    fun voteComment(action: Comment.Action): CommentKarmaVoteResult {
        val body = requestCommentAction(action)
        ensureCommentActionAccepted(body)
        return parseKarmaVoteResult(action, body)
    }

    fun likeComment(articleId: Int, commentId: Int): Boolean =
            voteComment(buildCommentVoteAction(articleId, commentId, Comment.Karma.LIKED)).likedByMe

    fun unlikeComment(articleId: Int, commentId: Int): Boolean =
            voteComment(buildCommentVoteAction(articleId, commentId, Comment.Karma.NOT_LIKED)).likedByMe

    fun executeCommentAction(action: Comment.Action, extraFields: Map<String, String> = emptyMap()): Boolean {
        if ((action.type == Comment.Action.Type.REPUTATION_PLUS || action.type == Comment.Action.Type.REPUTATION_MINUS) &&
                action.method.equals(Comment.Action.METHOD_GET, ignoreCase = true)) {
            return executeReputationAction(action, extraFields)
        }
        ensureCommentActionAccepted(requestCommentAction(action, extraFields))
        return true
    }

    private fun requestCommentAction(
            action: Comment.Action,
            extraFields: Map<String, String> = emptyMap(),
    ): String {
        val url = action.url?.takeIf { it.isNotBlank() } ?: throw IllegalArgumentException("Empty action url")
        if (url.startsWith("#") || url.startsWith("javascript:", ignoreCase = true)) {
            throw IllegalArgumentException("Action has no network endpoint")
        }
        val fields = LinkedHashMap<String, String>().apply {
            putAll(action.fields)
            putAll(extraFields)
        }
        val method = action.method.uppercase(Locale.US)
        val request = if (method == Comment.Action.METHOD_POST || fields.isNotEmpty()) {
            NetworkRequest.Builder()
                    .url(url)
                    .xhrHeader()
                    .formHeaders(fields)
                    .build()
        } else {
            NetworkRequest.Builder()
                    .url(url)
                    .xhrHeader()
                    .build()
        }
        val response = webClient.request(request)
        return response.body
    }

    private fun parseKarmaVoteResult(action: Comment.Action, body: String): CommentKarmaVoteResult {
        val commentId = karmaActionCommentId(action)
        val vote = karmaActionVote(action)
        val parsedMap = articleParser.parseKarmaMap(body)
        val karma = when {
            commentId > 0 && parsedMap.get(commentId) != null -> parsedMap.get(commentId)!!.copy()
            parsedMap.size() == 1 -> parsedMap.valueAt(0).copy()
            else -> inferKarmaAfterVote(vote)
        }
        val resolvedCommentId = commentId.takeIf { it > 0 }
                ?: if (parsedMap.size() == 1) parsedMap.keyAt(0) else 0
        return CommentKarmaVoteResult(resolvedCommentId, karma)
    }

    private fun inferKarmaAfterVote(vote: Int): Comment.Karma =
            Comment.Karma().apply {
                status = when (vote) {
                    Comment.Karma.LIKED -> Comment.Karma.LIKED
                    Comment.Karma.NOT_LIKED -> Comment.Karma.NOT_LIKED
                    else -> 0
                }
            }

    private fun karmaActionCommentId(action: Comment.Action): Int =
            action.fields["c"]?.toIntOrNull()
                    ?: karmaQueryField(action.url, "c")?.toIntOrNull()
                    ?: 0

    private fun karmaActionVote(action: Comment.Action): Int =
            action.fields["v"]?.toIntOrNull()
                    ?: karmaQueryField(action.url, "v")?.toIntOrNull()
                    ?: when (action.type) {
                        Comment.Action.Type.COMMENT_UNLIKE -> Comment.Karma.NOT_LIKED
                        Comment.Action.Type.COMMENT_LIKE -> Comment.Karma.LIKED
                        else -> 0
                    }

    private fun karmaQueryField(url: String?, key: String): String? {
        val query = url.orEmpty().substringAfter('?', "")
        if (query.isEmpty()) return null
        return query.split('&')
                .mapNotNull { part ->
                    val pieces = part.split('=', limit = 2)
                    if (pieces.size == 2 && pieces[0].equals(key, ignoreCase = true)) pieces[1] else null
                }
                .firstOrNull()
    }

    private fun executeReputationAction(action: Comment.Action, extraFields: Map<String, String>): Boolean {
        val url = action.url?.takeIf { it.isNotBlank() } ?: throw IllegalArgumentException("Empty reputation action url")
        val response = webClient.request(
                NetworkRequest.Builder()
                        .url(url)
                        .xhrHeader()
                        .build()
        )
        ensureCommentActionAccepted(response.body, allowForm = true)
        val userId = action.fields["mid"]?.toIntOrNull() ?: 0
        val formAction = articleParser.parseReputationAction(response.body, action.type, userId)
                ?: throw IllegalStateException("Сервер не вернул форму изменения репутации")
        val reasonField = formAction.reasonFieldName ?: action.reasonFieldName ?: "message"
        executeCommentAction(formAction, mapOf(reasonField to extraFields.values.firstOrNull().orEmpty()))
        return true
    }

    fun deleteComment(action: Comment.Action): Boolean {
        val submitAction = when {
            action.fields.isNotEmpty() -> action
            else -> buildDeleteActionFromUrl(action) ?: loadDeleteCommentForm(action)
                    ?: throw IllegalStateException("Сервер не вернул форму удаления комментария")
        }
        executeCommentAction(submitAction)
        return true
    }

    fun editComment(
            action: Comment.Action,
            text: String,
            context: CommentEditContext = CommentEditContext()
    ): Boolean {
        val formAction = when {
            hasParsedEditForm(action) -> action
            else -> runCatching { loadEditCommentForm(action, context) }
                    .getOrNull()
                    ?: buildEditActionFromUrl(action)
                    ?: throw IllegalStateException("Unable to parse comment edit form")
        }
        val textField = formAction.fields.keys.firstOrNull { isCommentTextField(it) }
                ?: throw IllegalStateException("Unable to find comment edit field")
        val url = formAction.url?.takeIf { it.isNotBlank() } ?: throw IllegalArgumentException("Empty edit action url")
        val fields = LinkedHashMap(formAction.fields)
        fields.remove(textField)
        val request = buildEditCommentSubmitRequest(url, fields, textField, text, formAction, context.articleUrl)
        if (BuildConfig.DEBUG) {
            Timber.d("NewsCommentEdit submit url=%s fields=%s", url, fields.keys)
        }
        val response = webClient.request(request)
        if (BuildConfig.DEBUG) {
            Timber.d(
                    "NewsCommentEdit submit response url=%s bodyLength=%d",
                    response.url,
                    response.body.length
            )
        }
        ensureCommentActionApplied(response.body)
        return true
    }

    fun loadEditCommentForm(action: Comment.Action, context: CommentEditContext = CommentEditContext()): Comment.Action {
        if (hasParsedEditForm(action)) return action
        val commentId = resolveEditCommentId(action)
        if (!isHttpCommentActionUrl(action.url)) {
            resolveInlineEditForm(action, context, commentId)?.let { return it }
        }
        resolveEditFormFromContext(action, context, commentId)?.let { return it }
        if (!action.editableHtml.isNullOrBlank() || !action.editableElementId.isNullOrBlank()) {
            enrichInlineEditAction(action, context)?.let { return it }
        }
        val tryAjaxPost = shouldProbeCommentEditAjaxPost(action)
        val articleReferer = context.articleUrl
        for (url in buildCommentModerationProbeUrls(action, commentId, edit = true)) {
            val body = requestCommentModerationHtml(
                    url,
                    commentId,
                    edit = true,
                    tryAjaxPost = tryAjaxPost,
                    articleReferer = articleReferer
            )
            logCommentEditProbe(url, body)
            articleParser.parseCommentEditAction(body)?.let { parsed ->
                logCommentEditFormResolved(url, parsed, "parsed-form")
                return parsed
            }
            articleParser.extractCommentEditActionFromHtml(body, commentId)?.let { parsed ->
                logCommentEditFormResolved(url, parsed, "extracted-nonce")
                return parsed
            }
        }
        buildEditActionFromUrl(action)?.let { parsed ->
            logCommentEditFormResolved(action.url.orEmpty(), parsed, "url-nonce")
            return parsed
        }
        refetchArticleHtmlForCommentEdit(context)?.let { freshHtml ->
            val refreshedContext = context.copy(articleHtml = freshHtml)
            resolveEditFormFromContext(action, refreshedContext, commentId)?.let { return it }
            resolveInlineEditForm(action, refreshedContext, commentId)?.let { return it }
        }
        throw IllegalStateException("Unable to parse comment edit form")
    }

    private fun resolveInlineEditForm(
            action: Comment.Action,
            context: CommentEditContext,
            commentId: Int
    ): Comment.Action? {
        if (commentId <= 0) return null
        val inlineText = action.editableHtml?.takeIf { it.isNotBlank() }
        val sources = listOfNotNull(context.articleHtml, context.commentsSource).distinct()
        sources.forEach { source ->
            articleParser.buildInlineCommentEditAction(
                    source = source,
                    commentId = commentId,
                    articleId = context.articleId,
                    inlineText = inlineText,
                    editableElementId = action.editableElementId,
                    submitText = action.submitText
            )?.let { parsed ->
                logCommentEditFormResolved(source.take(80), parsed, "inline-wp-comments-post")
                return mergeEditActionWithInlineSource(parsed, action)
            }
        }
        return null
    }

    private fun enrichInlineEditAction(
            action: Comment.Action,
            context: CommentEditContext
    ): Comment.Action? {
        val commentId = resolveEditCommentId(action).takeIf { it > 0 } ?: return null
        val inlineText = action.editableHtml?.takeIf { it.isNotBlank() }
                ?: context.commentsSource?.let { source ->
                    articleParser.extractCommentEditActionFromHtml(source, commentId)
                            ?.fields
                            ?.entries
                            ?.firstOrNull { isCommentTextField(it.key) }
                            ?.value
                }
                ?: return null
        val resolved = resolveEditFormFromContext(action, context, commentId)
                ?: return null
        val fields = LinkedHashMap(resolved.fields)
        val textField = fields.keys.firstOrNull { isCommentTextField(it) } ?: "content"
        fields[textField] = inlineText
        return mergeEditActionWithInlineSource(resolved.copy(fields = fields), action)
    }

    private fun resolveEditFormFromContext(
            action: Comment.Action,
            context: CommentEditContext,
            commentId: Int
    ): Comment.Action? {
        buildEditActionFromUrl(action)?.let { parsed ->
            val enriched = enrichEditActionWithArticleId(parsed, context.articleId)
            logCommentEditFormResolved(action.url.orEmpty(), enriched, "url-nonce")
            return mergeEditActionWithInlineSource(enriched, action)
        }
        if (commentId <= 0) return null
        listOfNotNull(context.commentsSource, context.articleHtml)
                .distinct()
                .forEach { source ->
                    articleParser.extractCommentEditActionFromHtml(source, commentId)?.let { parsed ->
                        logCommentEditFormResolved(source.take(80), parsed, "page-extracted")
                        return mergeEditActionWithInlineSource(parsed, action)
                    }
                    articleParser.parseCommentEditAction(source)?.let { parsed ->
                        logCommentEditFormResolved(source.take(80), parsed, "page-parsed")
                        return mergeEditActionWithInlineSource(parsed, action)
                    }
                }
        articleParser.extractCommentEditNonceFromPage(context.articleHtml)?.let { (nonceName, nonce) ->
            val built = buildEditActionFromPageNonce(
                    commentId = commentId,
                    articleId = context.articleId,
                    nonceName = nonceName,
                    nonce = nonce
            )
            logCommentEditFormResolved(context.articleUrl.orEmpty(), built, "page-nonce")
            return mergeEditActionWithInlineSource(built, action)
        }
        return null
    }

    private fun buildEditActionFromPageNonce(
            commentId: Int,
            articleId: Int,
            nonceName: String,
            nonce: String
    ): Comment.Action {
        val usesAjaxNonce = nonceName.contains("ajax", ignoreCase = true)
        val submitUrl = if (usesAjaxNonce) {
            "https://4pda.to/wp-admin/admin-ajax.php?action=editcomment"
        } else {
            "https://4pda.to/wp-admin/comment.php"
        }
        val actionName = if (usesAjaxNonce) "editcomment" else "editedcomment"
        return Comment.Action(
                url = submitUrl,
                method = Comment.Action.METHOD_POST,
                fields = linkedMapOf<String, String>().apply {
                    put(nonceName, nonce)
                    put("comment_ID", commentId.toString())
                    put("c", commentId.toString())
                    put("action", actionName)
                    put("content", "")
                    if (articleId > 0) put("comment_post_ID", articleId.toString())
                },
                type = Comment.Action.Type.EDIT
        )
    }

    private fun enrichEditActionWithArticleId(action: Comment.Action, articleId: Int): Comment.Action {
        if (articleId <= 0 || action.fields.containsKey("comment_post_ID")) return action
        val fields = LinkedHashMap(action.fields)
        fields["comment_post_ID"] = articleId.toString()
        return action.copy(fields = fields)
    }

    private fun mergeEditActionWithInlineSource(
            resolved: Comment.Action,
            source: Comment.Action
    ): Comment.Action {
        val fields = LinkedHashMap(resolved.fields)
        source.editableHtml?.takeIf { it.isNotBlank() }?.let { inlineText ->
            val textField = fields.keys.firstOrNull { isCommentTextField(it) } ?: "content"
            fields[textField] = inlineText
        }
        return resolved.copy(
                fields = fields,
                editableHtml = source.editableHtml ?: resolved.editableHtml,
                editableElementId = source.editableElementId ?: resolved.editableElementId,
                submitText = source.submitText ?: resolved.submitText
        )
    }

    private fun resolveEditCommentId(action: Comment.Action): Int =
            extractCommentIdFromModerationUrl(action.url.orEmpty())
                    ?: action.editableElementId
                            ?.removePrefix("comment-form-edit-")
                            ?.toIntOrNull()
                    ?: action.fields["comment_ID"]?.toIntOrNull()
                    ?: action.fields["c"]?.toIntOrNull()
                    ?: 0

    private fun refetchArticleHtmlForCommentEdit(context: CommentEditContext): String? {
        val articleUrl = context.articleUrl?.takeIf { it.isNotBlank() } ?: return null
        if (!articleUrl.contains("4pda", ignoreCase = true)) return null
        return runCatching {
            webClient.requestWithoutMobileCookie(
                    NetworkRequest.Builder()
                            .url(articleUrl)
                            .addHeader("User-Agent", DESKTOP_USER_AGENT)
                            .addHeader("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                            .addHeader("Cache-Control", "no-cache, no-store, must-revalidate")
                            .addHeader("Pragma", "no-cache")
                            .build()
            ).body
        }.getOrNull()?.takeIf { it.isNotBlank() }
    }

    fun loadDeleteCommentForm(action: Comment.Action): Comment.Action? {
        buildDeleteActionFromUrl(action)?.let { return it }
        for (url in buildCommentModerationProbeUrls(action, resolveEditCommentId(action), edit = false)) {
            val body = requestCommentModerationHtml(
                    url,
                    extractCommentIdFromModerationUrl(url) ?: 0,
                    edit = false,
                    tryAjaxPost = false
            )
            ensureCommentActionAccepted(body, allowForm = true)
            articleParser.parseCommentDeleteAction(body)?.let { return it }
        }
        return null
    }

    private fun shouldProbeCommentEditAjaxPost(action: Comment.Action): Boolean {
        if (action.url.orEmpty().contains("_wpnonce=", ignoreCase = true)) return false
        return extractCommentIdFromModerationUrl(action.url.orEmpty())?.let { it > 0 } == true
    }

    private fun requestCommentModerationHtml(
            url: String,
            commentId: Int = 0,
            edit: Boolean = false,
            tryAjaxPost: Boolean = false,
            articleReferer: String? = null
    ): String {
        val referer = moderationReferer(url, articleReferer)
        val accept = "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8"
        val attempts = buildList {
            if (edit && commentId > 0 && tryAjaxPost) {
                add { requestCommentEditAjaxPost(commentId, referer) }
            }
            if (url.contains("wp-admin", ignoreCase = true)) {
                add {
                    webClient.requestWithoutMobileCookie(
                            NetworkRequest.Builder()
                                    .url(url)
                                    .addHeader("User-Agent", DESKTOP_USER_AGENT)
                                    .addHeader("Accept", accept)
                                    .addHeader("Referer", referer)
                                    .build()
                    ).body
                }
            }
            add {
                webClient.request(
                        NetworkRequest.Builder()
                                .url(url)
                                .addHeader("Accept", accept)
                                .addHeader("Referer", referer)
                                .build()
                ).body
            }
            add {
                webClient.request(
                        NetworkRequest.Builder()
                                .url(url)
                                .xhrHeader()
                                .addHeader("Referer", referer)
                                .build()
                ).body
            }
            if (!url.contains("wp-admin", ignoreCase = true)) {
                add {
                    webClient.requestWithoutMobileCookie(
                            NetworkRequest.Builder()
                                    .url(url)
                                    .addHeader("User-Agent", DESKTOP_USER_AGENT)
                                    .addHeader("Accept", accept)
                                    .addHeader("Referer", referer)
                                    .build()
                    ).body
                }
            }
        }
        var lastBody = ""
        attempts.forEach { fetch ->
            val body = runCatching { fetch() }.getOrDefault("")
            lastBody = body
            if (looksLikeCommentModerationForm(body)) return body
        }
        return lastBody
    }

    private fun requestCommentEditAjaxPost(commentId: Int, referer: String): String {
        fun buildRequest(desktopUa: Boolean): NetworkRequest {
            val builder = NetworkRequest.Builder()
                    .url("https://4pda.to/wp-admin/admin-ajax.php")
                    .xhrHeader()
                    .addHeader("Referer", referer)
                    .formHeader("action", "editcomment")
                    .formHeader("c", commentId.toString())
            if (desktopUa) {
                builder.addHeader("User-Agent", DESKTOP_USER_AGENT)
            }
            return builder.build()
        }
        val attempts = listOf(
                { webClient.requestWithoutMobileCookie(buildRequest(desktopUa = true)).body },
                { webClient.request(buildRequest(desktopUa = false)).body },
        )
        var lastBody = ""
        attempts.forEach { fetch ->
            val body = fetch()
            lastBody = body
            if (looksLikeCommentModerationForm(body)) return body
        }
        return lastBody
    }

    private fun moderationReferer(url: String, articleReferer: String? = null): String =
            articleReferer?.takeIf { it.contains("4pda", ignoreCase = true) }
                    ?: if (url.contains("wp-admin", ignoreCase = true)) {
                        "https://4pda.to/"
                    } else {
                        "https://4pda.to/"
                    }

    private fun buildEditCommentSubmitRequest(
            url: String,
            fields: LinkedHashMap<String, String>,
            textField: String,
            text: String,
            sourceAction: Comment.Action,
            articleReferer: String? = null
    ): NetworkRequest {
        val referer = articleReferer?.takeIf { it.contains("4pda", ignoreCase = true) }
                ?: moderationReferer(sourceAction.url.orEmpty(), articleReferer)
        val builder = NetworkRequest.Builder()
                .url(url)
                .addHeader("Referer", referer)
                .formHeaders(fields)
                .formHeader(textField, Cp1251Codec.encode(text), true)
        return if (url.contains("admin-ajax.php", ignoreCase = true)) {
            builder.xhrHeader().build()
        } else {
            builder.build()
        }
    }

    private fun hasParsedEditForm(action: Comment.Action): Boolean =
            action.fields.keys.any { isCommentTextField(it) } &&
                    action.fields.keys.any { isModerationNonceField(it) } &&
                    !action.url.isNullOrBlank()

    private fun looksLikeCommentModerationForm(body: String): Boolean {
        if (body.isBlank()) return false
        return articleParser.canExtractCommentEditAction(body) ||
                articleParser.parseCommentDeleteAction(body) != null
    }

    private fun buildCommentModerationProbeUrls(
            action: Comment.Action,
            commentId: Int,
            edit: Boolean
    ): List<String> {
        val primary = action.url?.takeIf { isHttpCommentActionUrl(it) }
        val resolvedCommentId = commentId.takeIf { it > 0 }
                ?: primary?.let { extractCommentIdFromModerationUrl(it) }
        if (primary == null && resolvedCommentId == null) {
            throw IllegalArgumentException("Empty comment moderation action url")
        }
        return buildList {
            if (resolvedCommentId != null) {
                if (edit) {
                    add("https://4pda.to/wp-admin/comment.php?action=editcomment&c=$resolvedCommentId")
                } else {
                    add("https://4pda.to/wp-admin/comment.php?action=deletecomment&c=$resolvedCommentId")
                }
            }
            primary?.let { add(it) }
            if (resolvedCommentId != null) {
                if (edit) {
                    add("https://4pda.to/wp-admin/admin-ajax.php?action=editcomment&c=$resolvedCommentId")
                } else {
                    add("https://4pda.to/wp-admin/admin-ajax.php?action=deletecomment&c=$resolvedCommentId")
                }
            }
        }.distinct()
    }

    private fun isHttpCommentActionUrl(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        return url.startsWith("http://", ignoreCase = true) || url.startsWith("https://", ignoreCase = true)
    }

    private fun buildDeleteActionFromUrl(action: Comment.Action): Comment.Action? {
        val params = extractModerationQueryParams(action.url) ?: return null
        val nonce = params["_wpnonce"] ?: return null
        val commentId = params["c"] ?: params["comment_ID"] ?: return null
        val submitUrl = when {
            action.url.orEmpty().contains("admin-ajax.php", ignoreCase = true) ->
                "https://4pda.to/wp-admin/admin-ajax.php?action=deletecomment"
            else -> "https://4pda.to/wp-admin/comment.php"
        }
        return Comment.Action(
                url = submitUrl,
                method = Comment.Action.METHOD_POST,
                fields = linkedMapOf(
                        "_wpnonce" to nonce,
                        "comment_ID" to commentId,
                        "action" to (params["action"] ?: "deletecomment")
                ),
                type = Comment.Action.Type.DELETE,
                requiresConfirmation = true
        )
    }

    private fun buildEditActionFromUrl(action: Comment.Action): Comment.Action? {
        val params = extractModerationQueryParams(action.url) ?: return null
        val nonce = params["_wpnonce"] ?: return null
        val commentId = params["c"] ?: params["comment_ID"] ?: return null
        val usesCommentPhp = action.url.orEmpty().contains("comment.php", ignoreCase = true)
        val submitUrl = when {
            usesCommentPhp -> "https://4pda.to/wp-admin/comment.php"
            else -> "https://4pda.to/wp-admin/admin-ajax.php?action=editcomment"
        }
        val textField = "content"
        return Comment.Action(
                url = submitUrl,
                method = Comment.Action.METHOD_POST,
                fields = linkedMapOf(
                        "_wpnonce" to nonce,
                        "comment_ID" to commentId,
                        "action" to if (usesCommentPhp) "editedcomment" else "editcomment",
                        textField to ""
                ),
                type = Comment.Action.Type.EDIT
        )
    }

    private fun extractModerationQueryParams(url: String?): Map<String, String>? {
        if (url.isNullOrBlank()) return null
        return try {
            val uri = android.net.Uri.parse(url)
            buildMap {
                listOf("_wpnonce", "c", "comment_ID", "action").forEach { key ->
                    uri.getQueryParameter(key)?.takeIf { it.isNotBlank() }?.let { put(key, it) }
                }
            }.takeIf { it.isNotEmpty() }
        } catch (_: Throwable) {
            null
        }
    }

    private fun extractCommentIdFromModerationUrl(url: String): Int? {
        return try {
            val uri = android.net.Uri.parse(url)
            uri.getQueryParameter("c")?.toIntOrNull()?.takeIf { it > 0 }
                    ?: uri.getQueryParameter("comment_ID")?.toIntOrNull()?.takeIf { it > 0 }
        } catch (_: Throwable) {
            null
        }
    }

    private fun buildCommentVoteAction(articleId: Int, commentId: Int, vote: Int): Comment.Action {
        val articlePart = articleId.takeIf { it > 0 }?.let { "p=$it&" }.orEmpty()
        return Comment.Action(
                url = "https://4pda.to/pages/karma?${articlePart}c=$commentId&v=$vote",
                type = if (vote == Comment.Karma.NOT_LIKED) {
                    Comment.Action.Type.COMMENT_UNLIKE
                } else {
                    Comment.Action.Type.COMMENT_LIKE
                }
        )
    }

    private fun ensureCommentActionAccepted(body: String, allowForm: Boolean = false) {
        detectCommentActionError(body, allowForm)?.let { throw IllegalStateException(it) }
    }

    private fun ensureCommentActionApplied(body: String) {
        detectCommentActionError(body)?.let { throw IllegalStateException(it) }
        if (body.isBlank()) {
            throw IllegalStateException("Сервер вернул пустой ответ")
        }
    }

    private fun detectCommentActionError(body: String, allowForm: Boolean = false): String? {
        if (body.isBlank()) return null
        val normalized = body.lowercase(Locale.US)
        if (allowForm && normalized.contains("<form")) return null
        if (isCommentModerationSuccessResponse(normalized)) return null
        return when {
            normalized.contains("не указан пользователь") ->
                "Не указан пользователь, сообщение или сообщение слишком длинное"
            normalized.contains("no permission") || normalized.contains("нет прав") ->
                "Нет прав для выполнения действия"
            normalized.contains("not allowed") || normalized.contains("not permitted") ||
                    normalized.contains("you are not allowed") -> "Нет прав для выполнения действия"
            normalized.contains("not authorized") || normalized.contains("войдите") ->
                "Требуется авторизация"
            isNonceSecurityError(normalized) -> "Истёк токен действия"
            else -> null
        }
    }

    private fun isNonceSecurityError(normalized: String): Boolean {
        if (normalized.contains("invalid nonce")) return true
        if (normalized.contains("nonce failed") || normalized.contains("nonce failure")) return true
        if (normalized.contains("nonce verification")) return true
        if (normalized.contains("check_ajax_referer")) return true
        if (normalized.contains("link you followed has expired")) return true
        if (normalized.contains("security check failed") && !normalized.contains("name=\"_wpnonce\"")) return true
        if (normalized.contains("истёк") && (normalized.contains("ссылк") || normalized.contains("токен"))) return true
        if (normalized.contains("токен") && normalized.contains("недейств")) return true
        if (normalized.contains("token") && normalized.contains("expired")) return true
        return false
    }

    private fun isCommentModerationSuccessResponse(normalized: String): Boolean {
        if (normalized.contains("editedcomment") && normalized.contains("comment updated")) return true
        if (normalized.contains("комментарий") &&
                (normalized.contains("обновлен") || normalized.contains("обновлён") || normalized.contains("сохранен"))) {
            return true
        }
        if (normalized.contains("comment updated") || normalized.contains("comment has been updated")) return true
        return false
    }

    private fun isCommentTextField(name: String): Boolean =
            name.equals("comment", ignoreCase = true) ||
                    name.equals("content", ignoreCase = true) ||
                    name.equals("message", ignoreCase = true) ||
                    name.equals("text", ignoreCase = true) ||
                    name.equals("newcomment_content", ignoreCase = true)

    private fun isModerationNonceField(name: String): Boolean =
            name.equals("_wpnonce", ignoreCase = true) ||
                    name.equals("_ajax_nonce-replyto-comment", ignoreCase = true) ||
                    name.equals("_ajax_nonce", ignoreCase = true) ||
                    name.equals("wpnonce", ignoreCase = true)

    fun parseComments(karmaMap: SparseArray<Comment.Karma>, source: String?): Comment {
        return articleParser.parseComments(karmaMap, source)
    }

    fun rebalanceCommentsSource(article: DetailsPage): Boolean {
        val balanced = articleParser.ensureBalancedCommentsHtml(article.commentsSource)
        if (balanced.isNullOrBlank() || balanced == article.commentsSource) return false
        article.commentsSource = balanced
        return true
    }

    fun hasCommentNodeMarkup(source: String?): Boolean =
            articleParser.hasCommentNodeMarkup(source)

    fun countCommentNodesInSource(source: String?): Int =
            articleParser.countCommentNodesInSource(source)

    fun commentsSourceUnderfetchesExpected(source: String?, expectedCount: Int): Boolean =
            articleParser.commentsSourceUnderfetchesExpected(source, expectedCount)

    /** Caps a parsed tree to one inline batch (depth-first, max [COMMENTS_PER_PAGE] nodes). */
    fun capPaginatedCommentBatch(root: Comment, commentPage: Int = 1): Comment =
            limitPaginatedCommentBatch(root, commentPage.coerceAtLeast(1))

    fun parseComments(article: DetailsPage, paginated: Boolean = false, commentPage: Int = 1): Comment {
        val expectedCount = article.commentsCount.coerceAtLeast(0)
        val mobileSource = articleParser.ensureBalancedCommentsHtml(article.commentsSource)
                ?: article.commentsSource
        if (paginated) {
            val page = commentPage.coerceAtLeast(1)
            val perPage = forpdateam.ru.forpda.presentation.articles.detail.comments
                    .ArticleCommentsPagination.COMMENTS_PER_PAGE
            val skip = (page - 1).coerceAtLeast(0) * perPage
            var comments = articleParser.parseCommentsBatch(
                    article.karmaMap,
                    mobileSource,
                    skip,
                    perPage,
            )
            if (articleParser.countParsedComments(comments) <= 0) {
                val tagTree = articleParser.parseCommentsViaTagsOnly(article.karmaMap, mobileSource)
                comments = limitPaginatedCommentBatch(tagTree, page)
                if (articleParser.countParsedComments(comments) <= 0) {
                    comments = limitPaginatedCommentBatch(
                            articleParser.parseComments(article.karmaMap, mobileSource),
                            page,
                    )
                }
            }
            comments = articleParser.mergeCommentDesktopActions(comments, article.desktopCommentsSource)
            val userId = currentUserId()
            if (userId > 0) {
                articleParser.applyFallbackOwnCommentActions(comments, userId)
            }
            if (page <= 1) {
                logOwnCommentActions(comments, article.desktopCommentsSource)
            }
            articleParser.ensureCommentLikeActions(
                    comments,
                    article.id,
                    mobileSource ?: article.commentsSource,
            )
            return comments
        }
        var comments = articleParser.parseComments(article.karmaMap, mobileSource)
        var parsedCount = articleParser.countParsedComments(comments)
        val mobileUnderfetches = expectedCount > 0 &&
                articleParser.commentsSourceUnderfetchesExpected(mobileSource, expectedCount)
        if (!paginated &&
                (comments.children.isEmpty() || (mobileUnderfetches && parsedCount < expectedCount))
        ) {
            article.desktopCommentsSource
                    ?.let { articleParser.ensureBalancedCommentsHtml(it) ?: it }
                    ?.let { desktop ->
                        val desktopTree = articleParser.parseComments(article.karmaMap, desktop)
                        val desktopParsed = articleParser.countParsedComments(desktopTree)
                        if (desktopTree.children.isNotEmpty() && desktopParsed > parsedCount) {
                            comments = desktopTree
                            parsedCount = desktopParsed
                            if (!article.commentsSource.isNullOrBlank()) {
                                article.commentsSource = desktop
                            }
                        }
                    }
        }
        comments = articleParser.mergeCommentDesktopActions(comments, article.desktopCommentsSource)
        articleParser.ensureCommentLikeActions(
                comments,
                article.id,
                mobileSource ?: article.commentsSource,
        )
        val userId = currentUserId()
        if (userId > 0) {
            articleParser.applyFallbackOwnCommentActions(comments, userId)
        }
        logOwnCommentActions(comments, article.desktopCommentsSource)
        return comments
    }

    /**
     * Paginated loads must never surface more than one WP comment page (~20) per batch.
     * Desktop `comment-page-N` HTML may still contain the full list; [commentPage] skips
     * earlier pages in depth-first render order before taking the next batch.
     */
    private fun limitPaginatedCommentBatch(root: Comment, commentPage: Int): Comment {
        val max = forpdateam.ru.forpda.presentation.articles.detail.comments
                .ArticleCommentsPagination.COMMENTS_PER_PAGE
        val skip = (commentPage - 1).coerceAtLeast(0) * max
        val flattenedCount = root.flattenComments().size
        if (flattenedCount <= skip) return Comment()
        if (commentPage <= 1 && flattenedCount <= max) return root
        val cursor = PaginatedCommentCursor(skip, max)
        val limited = Comment()
        for (child in root.children) {
            if (cursor.budgetExhausted()) break
            val (limitedChild, added) = takeCommentSubtreeForPaginatedBatch(child, cursor)
            if (added > 0) {
                limited.children.add(limitedChild)
            }
        }
        if (flattenedCount > max || skip > 0) {
            FpdaDebugLog.log(
                    FpdaDebugLog.TAG_COMMENTS_SECTION,
                    "paginated_batch_capped",
                    mapOf(
                            "parsedCount" to flattenedCount,
                            "topLevelCount" to root.children.size,
                            "cappedTo" to max,
                            "skip" to skip,
                            "page" to commentPage,
                            "batchCount" to cursor.takenCount(),
                    )
            )
        }
        return limited
    }

    private class PaginatedCommentCursor(
            private val skip: Int,
            private val max: Int,
    ) {
        private var seen = 0
        private var taken = 0

        fun budgetExhausted(): Boolean = taken >= max

        fun takenCount(): Int = taken

        fun onNode(): PaginatedNodeDisposition {
            if (taken >= max) return PaginatedNodeDisposition.STOP
            val index = seen++
            return if (index < skip) {
                PaginatedNodeDisposition.SKIP
            } else {
                taken++
                PaginatedNodeDisposition.TAKE
            }
        }
    }

    private enum class PaginatedNodeDisposition {
        SKIP,
        TAKE,
        STOP,
    }

    /** Depth-first walk matching inline comment render order. */
    private fun takeCommentSubtreeForPaginatedBatch(
            node: Comment,
            cursor: PaginatedCommentCursor,
    ): Pair<Comment, Int> {
        when (cursor.onNode()) {
            PaginatedNodeDisposition.STOP -> return Comment() to 0
            PaginatedNodeDisposition.SKIP -> {
                for (child in node.children) {
                    if (cursor.budgetExhausted()) break
                    takeCommentSubtreeForPaginatedBatch(child, cursor)
                }
                return Comment() to 0
            }
            PaginatedNodeDisposition.TAKE -> {
                val copy = Comment(node).apply { children.clear() }
                var used = 1
                for (child in node.children) {
                    if (cursor.budgetExhausted()) break
                    val (limitedChild, added) = takeCommentSubtreeForPaginatedBatch(child, cursor)
                    if (added > 0) {
                        copy.children.add(limitedChild)
                        used += added
                    }
                }
                return copy to used
            }
        }
    }

    private fun logCommentEditProbe(url: String, body: String) {
        if (!BuildConfig.DEBUG) return
        Timber.d(
                "NewsCommentEdit probe url=%s hasForm=%s parsed=%s extracted=%s bodyLength=%d",
                url,
                body.contains("<form", ignoreCase = true),
                articleParser.parseCommentEditAction(body) != null,
                articleParser.extractCommentEditActionFromHtml(body) != null,
                body.length
        )
    }

    private fun logCommentEditFormResolved(url: String, action: Comment.Action, source: String) {
        if (!BuildConfig.DEBUG) return
        Timber.d(
                "NewsCommentEdit resolved source=%s url=%s submit=%s hasNonce=%s textField=%s",
                source,
                url,
                action.url,
                action.fields.containsKey("_wpnonce"),
                action.fields.keys.firstOrNull { isCommentTextField(it) }
        )
    }

    private fun logOwnCommentActions(root: Comment, desktopCommentsSource: String?) {
        if (!BuildConfig.DEBUG) return
        val userId = currentUserId()
        if (userId <= 0) return
        val source = if (desktopCommentsSource.isNullOrBlank()) "mobile" else "mobile+desktop"
        root.flattenComments()
                .filter {
                    it.userId == userId ||
                            it.actions.edit?.isValid() == true ||
                            it.actions.delete?.isValid() == true
                }
                .forEach { comment ->
                    Timber.d(
                            "NewsComments ownActions id=%d authorId=%d isOwn=%s hasEdit=%s hasDelete=%s hasRep=%s source=%s profile=%s like=%s report=%s",
                            comment.id,
                            comment.userId,
                            comment.userId == userId ||
                                    comment.actions.edit?.isValid() == true ||
                                    comment.actions.delete?.isValid() == true,
                            comment.actions.edit?.isValid() == true,
                            comment.actions.delete?.isValid() == true,
                            comment.actions.reputationPlus?.isValid() == true ||
                                    comment.actions.reputationMinus?.isValid() == true,
                            source,
                            comment.actions.profile?.isValid() == true,
                            comment.likeAction?.isValid() == true || comment.unlikeAction?.isValid() == true,
                            comment.actions.report?.isValid() == true
                    )
                }
    }

    private fun Comment.flattenComments(): List<Comment> =
            children.flatMap { listOf(it) + it.flattenComments() }

    suspend fun replyComment(articleId: Int, commentId: Int, text: String): DetailsPage {
        val comment = Cp1251Codec.encode(text)
        val articleUrl = "https://4pda.to/index.php?p=$articleId"

        val builder = NetworkRequest.Builder()
                .url("https://4pda.to/wp-comments-post.php")
                .formHeader("comment_post_ID", articleId.toString())
                .formHeader("comment_reply_ID", commentId.toString())
                .formHeader("comment_reply_dp", if (commentId == 0) "0" else "1")
                .formHeader("comment", comment, true)
        val response = webClient.request(builder.build())
        syncPollVoteCookies()
        val article = articleParser.parseArticle(response.body)
        article.url = response.redirectWithFragment
        return loadDesktopExtrasIfMissing(articleUrl, response, article)
    }


    private fun getLink(category: String?, pageNumber: Int): String {
        return getPageLink(getUrlCategory(category), pageNumber)
    }

    private fun getPageLink(url: String, pageNumber: Int): String {
        if (pageNumber < 2) {
            return url
        }
        return url + "page/" + pageNumber + "/"
    }

    private fun getUrlCategory(category: String?): String =
            Constants.getNewsCategoryUrl(Constants.normalizeNewsCategory(category))

    private fun Int?.orZero(): Int = this ?: 0
}
