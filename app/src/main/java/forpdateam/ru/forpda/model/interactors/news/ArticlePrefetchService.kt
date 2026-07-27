package forpdateam.ru.forpda.model.interactors.news

import forpdateam.ru.forpda.client.FourPdaRequestGovernor
import forpdateam.ru.forpda.diagnostic.ArticleCacheTrace
import forpdateam.ru.forpda.model.data.remote.api.news.ArticleParsePhase
import forpdateam.ru.forpda.model.repository.news.NewsRepository
import forpdateam.ru.forpda.presentation.articles.detail.ArticleTemplate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
        private val memoryCache: ArticleMemoryCache,
        private val prefetchDebounceMs: Long = DEFAULT_PREFETCH_DEBOUNCE_MS,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var prefetchJob: Job? = null
    /** Only one speculative article request may touch 4PDA at a time. */
    private val prefetchMutex = Mutex()
    private val inflightByArticleId = ConcurrentHashMap<Int, Deferred<Unit>>()
    private val networkPrefetchTimestamps = ArrayDeque<Long>()
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
    @Synchronized
    fun cancelPrefetch(exceptArticleId: Int = -1) {
        if (exceptArticleId > 0 && activePrefetchId == exceptArticleId) return
        prefetchJob?.cancel()
        prefetchJob = null
        activePrefetchId = -1
    }

    fun prefetchNextArticle(articleId: Int) = prefetchArticle(articleId)

    @Synchronized
    fun prefetchArticle(articleId: Int) {
        schedulePrefetch(articleId, prefetchDebounceMs)
    }

    /**
     * A deliberate tap must not wait for the speculative row-bind debounce. Такая загрузка уже не
     * спекулятивная — она идёт с пользовательским приоритетом, мимо бюджета и паузы после 429.
     */
    @Synchronized
    fun prefetchArticleNow(articleId: Int) {
        prefetchJob?.cancel()
        inflightByArticleId.remove(articleId)?.cancel()
        schedulePrefetch(articleId, debounceMs = 0L, deliberate = true)
    }

    private fun schedulePrefetch(articleId: Int, debounceMs: Long, deliberate: Boolean = false) {
        if (articleId <= 0) return
        if (inflightByArticleId[articleId]?.isActive == true) return
        if (articleId == lastPrefetchedId && memoryCache.get(articleId).valid) return
        // 4pda прямо сейчас ограничивает нас по частоте — спекулятивную работу не начинаем вовсе.
        if (!deliberate && FourPdaRequestGovernor.isCoolingDown()) return

        // RecyclerView binds several visible rows in one burst. Previously every bind launched a full
        // article GET and only the last Job reference was retained, so opening News could hit 4PDA with
        // several parallel speculative requests and trigger HTTP 429. Keep only the latest candidate;
        // the short grace period lets the initial bind/fast scroll settle before any network work starts.
        prefetchJob?.cancel()
        activePrefetchId = articleId
        val deferred = scope.async(start = CoroutineStart.LAZY) {
            try {
                if (debounceMs > 0L) delay(debounceMs)
                prefetchMutex.withLock {
                    prefetchArticleLocked(articleId, deliberate)
                }
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
        deferred.start()
    }

    private suspend fun prefetchArticleLocked(articleId: Int, deliberate: Boolean = false) {
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
        if (!deliberate && !consumeNetworkBudget()) {
            ArticleCacheTrace.log(
                    event = "prefetch_skip",
                    articleId = articleId,
                    cacheLayer = "network",
                    hit = false,
                    valid = false,
                    reason = "budget_exhausted"
            )
            return
        }
        val fetch = newsRepository.fetchArticleDetails(
                id = articleId,
                phase = ArticleParsePhase.FIRST_RENDER,
                background = !deliberate
        )
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

    /**
     * Скользящее окно на [PREFETCH_BUDGET] сетевых префетчей в минуту. Кэш-попадания бюджет не
     * тратят: платим только за реальные обращения к 4pda. Долгий скролл ленты с остановками больше
     * не может превратиться в непрерывный поток спекулятивных загрузок статей.
     */
    private fun consumeNetworkBudget(nowMs: Long = System.currentTimeMillis()): Boolean {
        synchronized(networkPrefetchTimestamps) {
            while (networkPrefetchTimestamps.isNotEmpty() &&
                    nowMs - networkPrefetchTimestamps.first() > PREFETCH_BUDGET_WINDOW_MS) {
                networkPrefetchTimestamps.removeFirst()
            }
            if (networkPrefetchTimestamps.size >= PREFETCH_BUDGET) return false
            networkPrefetchTimestamps.addLast(nowMs)
            return true
        }
    }

    private companion object {
        /** Coalesces the row-bind burst; deliberate taps bypass it via [prefetchArticleNow]. */
        const val DEFAULT_PREFETCH_DEBOUNCE_MS = 350L
        const val PREFETCH_BUDGET = 6
        const val PREFETCH_BUDGET_WINDOW_MS = 60_000L
    }
}
