package forpdateam.ru.forpda.model.repository.news

import android.util.SparseArray
import forpdateam.ru.forpda.entity.remote.news.Comment
import forpdateam.ru.forpda.entity.remote.news.CommentKarmaVoteResult
import forpdateam.ru.forpda.entity.remote.news.DetailsPage
import forpdateam.ru.forpda.entity.remote.news.Material
import forpdateam.ru.forpda.entity.remote.news.NewsItem
import forpdateam.ru.forpda.entity.remote.news.Tag
import forpdateam.ru.forpda.model.data.cache.forumuser.ForumUsersCacheRoom
import forpdateam.ru.forpda.model.data.remote.api.news.ArticleFetchResult
import forpdateam.ru.forpda.model.data.remote.api.news.ArticleParsePhase
import forpdateam.ru.forpda.model.data.remote.api.news.CommentEditContext
import forpdateam.ru.forpda.model.data.remote.api.news.NewsApi
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/**
 * Created by radiationx on 01.01.18.
 */
class NewsRepository(
        private val newsApi: NewsApi,
        private val forumUsersCache: ForumUsersCacheRoom
) {

    private val inFlightFetches = ConcurrentHashMap<String, CompletableDeferred<ArticleFetchResult>>()
    private val inFlightNewsLists = ConcurrentHashMap<String, CompletableDeferred<List<NewsItem>>>()
    private val newsListCache = NewsListMemoryCache()

    suspend fun getNews(category: String, pageNumber: Int, bypassCache: Boolean = false): List<NewsItem> {
        val key = newsListKey(category, pageNumber)
        if (!bypassCache) {
            newsListCache.get(key)?.let { return it }
            while (true) {
                inFlightNewsLists[key]?.let { return it.await().copyNewsItems() }
                val gate = CompletableDeferred<List<NewsItem>>()
                val raced = inFlightNewsLists.putIfAbsent(key, gate)
                if (raced != null) continue
                try {
                    val result = fetchNewsList(category, pageNumber)
                    newsListCache.put(key, result)
                    gate.complete(result.copyNewsItems())
                    return result.copyNewsItems()
                } catch (error: Throwable) {
                    gate.completeExceptionally(error)
                    throw error
                } finally {
                    inFlightNewsLists.remove(key, gate)
                }
            }
        }
        return fetchNewsList(category, pageNumber)
                .also { newsListCache.put(key, it) }
                .copyNewsItems()
    }

    private suspend fun fetchNewsList(category: String, pageNumber: Int): List<NewsItem> =
            withContext(Dispatchers.IO) {
                newsApi.getNews(category, pageNumber).also { data ->
                    val avatarsByAuthorId = forumUsersCache.getUsersByIds(
                            data.map { it.authorId }.filter { it > 0 }
                    )
                    data.forEach { item ->
                        avatarsByAuthorId[item.authorId]?.avatar?.let { item.avatar = it }
                    }
                }
            }

    suspend fun likeComment(articleId: Int, commentId: Int): Boolean =
            withContext(Dispatchers.IO) { newsApi.likeComment(articleId, commentId) }

    suspend fun unlikeComment(articleId: Int, commentId: Int): Boolean =
            withContext(Dispatchers.IO) { newsApi.unlikeComment(articleId, commentId) }

    suspend fun voteComment(action: Comment.Action): CommentKarmaVoteResult =
            withContext(Dispatchers.IO) { newsApi.voteComment(action) }

    suspend fun executeCommentAction(action: Comment.Action, extraFields: Map<String, String> = emptyMap()): Boolean =
            withContext(Dispatchers.IO) { newsApi.executeCommentAction(action, extraFields) }

    suspend fun deleteComment(action: Comment.Action): Boolean =
            withContext(Dispatchers.IO) { newsApi.deleteComment(action) }

    suspend fun editComment(action: Comment.Action, text: String, context: CommentEditContext = CommentEditContext()): Boolean =
            withContext(Dispatchers.IO) { newsApi.editComment(action, text, context) }

    suspend fun loadEditCommentForm(action: Comment.Action, context: CommentEditContext = CommentEditContext()): Comment.Action =
            withContext(Dispatchers.IO) { newsApi.loadEditCommentForm(action, context) }

    suspend fun sendPoll(from: String, pollId: Int, answersId: IntArray): DetailsPage =
            withContext(Dispatchers.IO) { newsApi.sendPoll(from, pollId, answersId) }

    suspend fun votePoll(from: String, pollId: Int, answersId: IntArray): String =
            withContext(Dispatchers.IO) { newsApi.votePoll(from, pollId, answersId) }

    suspend fun replyComment(articleId: Int, commentId: Int, comment: String): DetailsPage =
            withContext(Dispatchers.IO) { newsApi.replyComment(articleId, commentId, comment) }

    suspend fun getDetails(id: Int): DetailsPage =
            withContext(Dispatchers.IO) { newsApi.getDetails(id) }

    suspend fun getDetails(url: String): DetailsPage =
            withContext(Dispatchers.IO) { newsApi.getDetails(url) }

    suspend fun fetchArticleDetails(
            id: Int,
            phase: ArticleParsePhase = ArticleParsePhase.FIRST_RENDER,
            bypassCache: Boolean = false
    ): ArticleFetchResult =
            fetchArticleDetails("https://4pda.to/index.php?p=$id", phase, bypassCache)

    suspend fun fetchArticleDetails(
            url: String,
            phase: ArticleParsePhase = ArticleParsePhase.FIRST_RENDER,
            bypassCache: Boolean = false
    ): ArticleFetchResult {
        if (bypassCache) {
            return withContext(Dispatchers.IO) {
                newsApi.fetchArticleDetails(url, phase, bypassCache = true)
            }
        }
        val key = fetchCoalesceKey(url, phase)
        while (true) {
            inFlightFetches[key]?.let { return it.await().copyForArticleOpen() }
            val gate = CompletableDeferred<ArticleFetchResult>()
            val raced = inFlightFetches.putIfAbsent(key, gate)
            if (raced != null) continue
            try {
                val result = withContext(Dispatchers.IO) {
                    newsApi.fetchArticleDetails(url, phase, bypassCache = false)
                }
                gate.complete(result.copyForArticleOpen())
                return result.copyForArticleOpen()
            } catch (error: Throwable) {
                gate.completeExceptionally(error)
                throw error
            } finally {
                inFlightFetches.remove(key, gate)
            }
        }
    }

    suspend fun enrichDesktopExtras(fetch: ArticleFetchResult): DetailsPage =
            withContext(Dispatchers.IO) { newsApi.enrichDesktopExtras(fetch) }

    suspend fun enrichArticleMetadata(page: DetailsPage, rawBody: String) =
            withContext(Dispatchers.Default) { newsApi.enrichArticleMetadata(page, rawBody) }

    /** Desktop-rendered comment list for lazy-loaded mobile articles (own count > 0, empty shell). */
    suspend fun loadDesktopCommentsSource(url: String): String? =
            withContext(Dispatchers.IO) { newsApi.loadDesktopCommentsSource(url) }

    suspend fun fetchCommentsPageSource(articleUrl: String, commentPage: Int): String? =
            withContext(Dispatchers.IO) { newsApi.fetchCommentsPageSource(articleUrl, commentPage) }

    suspend fun parseCommentsFromSource(
            article: DetailsPage,
            source: String?,
            paginated: Boolean = false,
            commentPage: Int = 1,
    ): Comment = withContext(Dispatchers.Default) {
        newsApi.parseCommentsFromSource(article, source, paginated, commentPage)
    }

    fun capPaginatedCommentBatch(root: Comment, commentPage: Int = 1): Comment =
            newsApi.capPaginatedCommentBatch(root, commentPage)

    suspend fun getComments(article: DetailsPage): Comment =
            withContext(Dispatchers.Default) {
                newsApi.parseComments(article)
            }

    fun rebalanceCommentsSource(article: DetailsPage): Boolean =
            newsApi.rebalanceCommentsSource(article)

    fun hasCommentNodeMarkup(source: String?): Boolean =
            newsApi.hasCommentNodeMarkup(source)

    fun countCommentNodesInSource(source: String?): Int =
            newsApi.countCommentNodesInSource(source)

    fun commentsSourceUnderfetchesExpected(source: String?, expectedCount: Int): Boolean =
            newsApi.commentsSourceUnderfetchesExpected(source, expectedCount)

    private fun fetchCoalesceKey(url: String, phase: ArticleParsePhase): String =
            "${url.trim()}|${phase.name}"

    private fun newsListKey(category: String, pageNumber: Int): String =
            "${category.trim()}|$pageNumber"

    private class NewsListMemoryCache(
            private val maxEntries: Int = 12,
            private val maxAgeMs: Long = 60_000L
    ) {
        private data class Entry(
                val items: List<NewsItem>,
                val storedAtMs: Long
        )

        private val entries = LinkedHashMap<String, Entry>(maxEntries, 0.75f, true)

        @Synchronized
        fun get(key: String, nowMs: Long = System.currentTimeMillis()): List<NewsItem>? {
            val entry = entries[key] ?: return null
            if (nowMs - entry.storedAtMs > maxAgeMs) {
                entries.remove(key)
                return null
            }
            return entry.items.copyNewsItems()
        }

        @Synchronized
        fun put(key: String, items: List<NewsItem>, nowMs: Long = System.currentTimeMillis()) {
            entries[key] = Entry(items.copyNewsItems(), nowMs)
            while (entries.size > maxEntries) {
                entries.remove(entries.keys.firstOrNull() ?: break)
            }
        }
    }

    private companion object {
        fun ArticleFetchResult.copyForArticleOpen(): ArticleFetchResult =
                copy(page = page.copyForArticleOpen())

        fun DetailsPage.copyForArticleOpen(): DetailsPage =
                DetailsPage().also { copy ->
                    copy.id = id
                    copy.commentId = commentId
                    copy.authorId = authorId
                    copy.url = url
                    copy.title = title
                    copy.description = description
                    copy.author = author
                    copy.date = date
                    copy.imgUrl = imgUrl
                    copy.category = category?.let { Tag(it.tag, it.title, it.url) }
                    copy.commentsCount = commentsCount
                    copy.tags.addAll(tags.map { Tag(it.tag, it.title, it.url) })
                    copy.karmaMap = karmaMap.copyForArticleOpen()
                    copy.html = html
                    copy.materials.addAll(materials.map { material ->
                        Material().apply {
                            id = material.id
                            title = material.title
                            imageUrl = material.imageUrl
                        }
                    })
                    copy.navId = navId
                    copy.commentsSource = commentsSource
                    copy.desktopCommentsSource = desktopCommentsSource
                    copy.commentTree = commentTree?.let(::copyCommentTree)
                }

        fun SparseArray<Comment.Karma>.copyForArticleOpen(): SparseArray<Comment.Karma> =
                SparseArray<Comment.Karma>(size()).also { copy ->
                    for (index in 0 until size()) {
                        copy.put(keyAt(index), valueAt(index)?.copy())
                    }
                }

        fun copyCommentTree(comment: Comment): Comment =
                Comment(comment).also { copy ->
                    comment.children.forEach { child ->
                        copy.children.add(copyCommentTree(child))
                    }
                }

        fun List<NewsItem>.copyNewsItems(): List<NewsItem> =
                map { item ->
                    NewsItem().apply {
                        id = item.id
                        authorId = item.authorId
                        url = item.url
                        title = item.title
                        description = item.description
                        author = item.author
                        date = item.date
                        imgUrl = item.imgUrl
                        commentsCount = item.commentsCount
                        avatar = item.avatar
                        tags.addAll(item.tags.map { Tag(it.tag, it.title, it.url) })
                    }
                }
    }
}
