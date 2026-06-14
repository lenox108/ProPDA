package forpdateam.ru.forpda.model.interactors.news

import forpdateam.ru.forpda.diagnostic.ArticleCacheTrace
import forpdateam.ru.forpda.model.data.remote.api.news.ArticleParsePhase
import forpdateam.ru.forpda.model.repository.news.NewsRepository
import forpdateam.ru.forpda.presentation.articles.detail.ArticleTemplate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap

/**
 * Low-priority background prefetch for the next article in the news list.
 */
class ArticlePrefetchService(
        private val newsRepository: NewsRepository,
        private val articleTemplate: ArticleTemplate,
        private val diskCache: ArticleDiskCache,
        private val memoryCache: ArticleMemoryCache
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var prefetchJob: Job? = null
    private val inflightByArticleId = ConcurrentHashMap<Int, Deferred<Unit>>()
    @Volatile
    private var lastPrefetchedId: Int = -1
    @Volatile
    private var activePrefetchId: Int = -1

    /** Waits for an in-flight list warm-up so tap-to-open can reuse memory without duplicate map/network. */
    suspend fun awaitWarm(articleId: Int) {
        if (articleId <= 0) return
        try {
            inflightByArticleId[articleId]?.await()
        } catch (_: kotlinx.coroutines.CancellationException) {
            // A newer list warm-up cancelled this prefetch; open path fetches on its own.
        }
    }

    /**
     * Cancel background prefetch for other articles so tap-to-open is not competing for CPU/network.
     * Keeps an in-flight/completed prefetch for [exceptArticleId] so list warm-up can hand off to open.
     */
    fun cancelPrefetch(exceptArticleId: Int = -1) {
        if (exceptArticleId > 0 && activePrefetchId == exceptArticleId) return
        prefetchJob?.cancel()
        prefetchJob = null
        activePrefetchId = -1
    }

    fun prefetchNextArticle(articleId: Int) = prefetchArticle(articleId)

    fun prefetchArticle(articleId: Int) {
        if (articleId <= 0) return
        if (inflightByArticleId[articleId]?.isActive == true) return
        if (articleId == lastPrefetchedId && memoryCache.get(articleId).valid) return
        activePrefetchId = articleId
        val deferred = scope.async {
            try {
                prefetchArticleLocked(articleId)
            } catch (error: kotlinx.coroutines.CancellationException) {
                throw error
            } catch (error: Throwable) {
                Timber.w(error, "Article prefetch failed id=%d", articleId)
            } finally {
                if (activePrefetchId == articleId) {
                    activePrefetchId = -1
                }
            }
        }
        inflightByArticleId[articleId] = deferred
        deferred.invokeOnCompletion { inflightByArticleId.remove(articleId, deferred) }
        prefetchJob = deferred
    }

    private suspend fun prefetchArticleLocked(articleId: Int) {
        val memoryHit = memoryCache.get(articleId)
        if (memoryHit.valid) {
            ArticleCacheTrace.log(
                    event = "prefetch_skip",
                    articleId = articleId,
                    cacheLayer = "memory",
                    hit = true,
                    valid = true,
                    reason = "already_warm"
            )
            return
        }
        val diskHit = diskCache.get(articleId)
        if (diskHit.valid && diskHit.entry != null) {
            memoryCache.put(diskHit.entry.page)
            lastPrefetchedId = articleId
            ArticleCacheTrace.log(
                    event = "prefetch_hit",
                    articleId = articleId,
                    cacheLayer = "disk",
                    hit = true,
                    valid = true,
                    reason = "disk_warm"
            )
            return
        }
        val fetch = newsRepository.fetchArticleDetails(articleId, ArticleParsePhase.FIRST_RENDER)
        val mapped = withContext(Dispatchers.Default) {
            articleTemplate.mapEntity(fetch.page)
        }
        if (memoryCache.put(mapped)) {
            diskCache.put(mapped)
            lastPrefetchedId = articleId
            ArticleCacheTrace.log(
                    event = "prefetch_ok",
                    articleId = articleId,
                    cacheLayer = "disk",
                    hit = false,
                    valid = true,
                    mappedHtmlLen = mapped.html?.length,
                    reason = "network_prefetch"
            )
        }
    }
}
