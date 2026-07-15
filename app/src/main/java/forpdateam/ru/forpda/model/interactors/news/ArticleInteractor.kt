package forpdateam.ru.forpda.model.interactors.news

import forpdateam.ru.forpda.entity.remote.news.Comment
import forpdateam.ru.forpda.entity.remote.news.CommentKarmaVoteResult
import forpdateam.ru.forpda.entity.remote.news.DetailsPage
import forpdateam.ru.forpda.model.data.remote.api.news.CommentEditContext
import forpdateam.ru.forpda.model.repository.news.NewsRepository
import forpdateam.ru.forpda.diagnostic.ArticleCacheTrace
import forpdateam.ru.forpda.diagnostic.FpdaDebugLog
import forpdateam.ru.forpda.presentation.articles.detail.comments.InlineCommentsDisplayCount
import forpdateam.ru.forpda.diagnostic.StateRaceTrace
import forpdateam.ru.forpda.model.interactors.CrossScreenInteractor
import forpdateam.ru.forpda.model.data.remote.api.news.ArticleFetchResult
import forpdateam.ru.forpda.model.data.remote.api.news.ArticleHtmlValidator
import forpdateam.ru.forpda.model.data.remote.api.news.ArticleParsePhase
import forpdateam.ru.forpda.presentation.articles.detail.ArticleOpenSession
import forpdateam.ru.forpda.presentation.articles.detail.ArticleOpenTrace
import forpdateam.ru.forpda.presentation.articles.detail.ArticleTemplate
import forpdateam.ru.forpda.presentation.articles.detail.comments.ArticleCommentsPagination
import forpdateam.ru.forpda.presentation.articles.detail.comments.CommentsTrace
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicInteger
import timber.log.Timber

class ArticleInteractor(
        val initData: InitData,
        private val newsRepository: NewsRepository,
        private val articleTemplate: ArticleTemplate,
        private val diskCache: ArticleDiskCache? = null,
        private val crossScreenInteractor: CrossScreenInteractor? = null,
        private val memoryCache: ArticleMemoryCache = ArticleMemoryCache(),
        private val prefetchService: ArticlePrefetchService? = null
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val loadMutex = Mutex()
    private val articleGeneration = AtomicInteger(0)
    private val commentsGeneration = AtomicInteger(0)
    private val commentsRequestId = AtomicInteger(0)
    private val articleCache = memoryCache
    private val commentsMemoryCache = ArticleCommentsMemoryCache()
    private var deferredExtrasJob: Job? = null
    @Volatile
    private var deferredExtrasArticleId: Int = -1
    private var commentsPrefetchJob: Job? = null
    @Volatile
    private var commentsPrefetchArticleId: Int = -1
    private var desktopCommentsFetchJob: Job? = null
    @Volatile
    private var desktopCommentsFetchArticleId: Int = -1

    @Volatile
    var openSession: ArticleOpenSession? = null
        private set

    private val _data = MutableStateFlow<DetailsPage?>(null)
    fun observeData(): Flow<DetailsPage?> = _data

    private val _extrasPatch = MutableSharedFlow<ArticleDeferredExtrasMerger.Patch>(
            extraBufferCapacity = 1,
            onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    fun observeExtrasPatch(): Flow<ArticleDeferredExtrasMerger.Patch> = _extrasPatch.asSharedFlow()

    private var cachedCommentsArticleId: Int = -1
    private var cachedCommentTree: Comment? = null
    // Full embedded comment tree parsed ONCE, then paged in-memory. Re-parsing the ~400KB embedded
    // source per load-more was a ~8s tagsoup DOM walk each time (measured) — the dominant cost of
    // comment loading. Keyed by source hash so a changed source re-parses.
    @Volatile
    private var fullEmbeddedCommentTree: Comment? = null
    @Volatile
    private var fullEmbeddedCommentKey: Int? = null
    // countCommentNodesInSource is a full-source regex scan (~1.5s on a 400KB embedded source) and was
    // called several times per load-more; memoize it per source since it is deterministic.
    @Volatile
    private var cachedSourceNodeCount: Int = -1
    @Volatile
    private var cachedSourceNodeCountKey: Int? = null
    private var pendingScrollCommentId: Int = 0
    /** Next WordPress comment page (`cp`) to fetch from network after page 1. */
    private var commentsNextNetworkPage: Int = 2
    /** True while [loadCommentsPage] is driving batched cp= loads (not legacy full-tree parse). */
    @Volatile
    private var commentsPaginationActive: Boolean = false

    /** Drop stale in-memory article before a new open (prevents replaying wrong/empty HTML). */
    fun resetForNewOpen() {
        val nextGeneration = articleGeneration.incrementAndGet()
        commentsGeneration.incrementAndGet()
        deferredExtrasJob?.cancel()
        deferredExtrasJob = null
        deferredExtrasArticleId = -1
        desktopCommentsFetchJob?.cancel()
        desktopCommentsFetchJob = null
        desktopCommentsFetchArticleId = -1
        commentsPrefetchJob?.cancel()
        commentsPrefetchJob = null
        commentsPrefetchArticleId = -1
        openSession = null
        _data.value = null
        cachedCommentsArticleId = -1
        cachedCommentTree = null
        fullEmbeddedCommentTree = null
        fullEmbeddedCommentKey = null
        cachedSourceNodeCount = -1
        cachedSourceNodeCountKey = null
        pendingScrollCommentId = 0
        resetCommentsPagination()
        StateRaceTrace.log(
                domain = "article",
                event = "reset_for_new_open",
                generation = nextGeneration,
                articleId = initData.newsId,
                reason = "invalidate_memory"
        )
    }

    fun takePendingScrollCommentId(): Int {
        val id = pendingScrollCommentId
        pendingScrollCommentId = 0
        return id
    }

    fun currentArticleGeneration(): Int = articleGeneration.get()
    fun currentCommentsGeneration(): Int = commentsGeneration.get()

    /** Reconciles article badge with parsed DOM totals (see FPDA_COMMENTS_SECTION logs). */
    fun reconcileCommentsCountFromParsed(parsedCount: Int) {
        if (parsedCount <= 0) return
        val article = _data.value ?: return
        val expected = effectiveCommentsCount(article)
        if (parsedCount > expected) {
            val previousCount = article.commentsCount
            article.commentsCount = parsedCount
            _data.value = article
            notifyListCommentsCountIfChanged(article.id, parsedCount, previousCount)
            FpdaDebugLog.log(
                    FpdaDebugLog.TAG_COMMENTS_SECTION,
                    "count_reconciled_up",
                    mapOf(
                            "articleId" to article.id,
                            "parsedCount" to parsedCount,
                            "previousCount" to previousCount,
                    )
            )
            return
        }
        if (expected <= parsedCount) return
        if (ArticleCommentsPagination.shouldPreserveExpectedCount(
                        loadedCount = parsedCount,
                        totalExpected = expected,
                        paginatedSessionActive = commentsPaginationActive,
                )
        ) {
            FpdaDebugLog.log(
                    FpdaDebugLog.TAG_COMMENTS_SECTION,
                    "count_reconcile_skipped_partial_batch",
                    mapOf(
                            "articleId" to article.id,
                            "parsedCount" to parsedCount,
                            "badgeCount" to expected,
                            "paginatedSession" to commentsPaginationActive,
                            "hasMore" to ArticleCommentsPagination.hasMore(parsedCount, expected),
                    )
            )
            return
        }
        val previousCount = article.commentsCount
        article.commentsCount = parsedCount
        _data.value = article
        notifyListCommentsCountIfChanged(article.id, parsedCount, previousCount)
        FpdaDebugLog.log(
                FpdaDebugLog.TAG_COMMENTS_SECTION,
                "count_reconciled",
                mapOf(
                        "articleId" to article.id,
                        "parsedCount" to parsedCount,
                        "previousCount" to previousCount,
                )
        )
    }

    private fun notifyListCommentsCountIfChanged(articleId: Int, resolvedCount: Int, previousCount: Int) {
        if (resolvedCount < 0 || resolvedCount == previousCount) return
        crossScreenInteractor?.onArticleCommentsCountReconciled(articleId, resolvedCount)
    }

    private val _comments = MutableSharedFlow<Comment>(
            replay = 1,
            extraBufferCapacity = 1,
            onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    fun observeComments(): Flow<Comment> = _comments.asSharedFlow()

    fun close() {
        scope.cancel()
    }

    suspend fun loadArticle(loadComments: Boolean = false, bypassCache: Boolean = false): DetailsPage {
        val generation = articleGeneration.incrementAndGet()
        return loadMutex.withLock {
            if (generation != articleGeneration.get()) {
                StateRaceTrace.log(
                        domain = "article",
                        event = "stale_ignored",
                        generation = generation,
                        currentGeneration = articleGeneration.get(),
                        articleId = initData.newsId,
                        reason = "mutex_wait"
                )
                throw kotlinx.coroutines.CancellationException("Stale article load")
            }
            loadArticleLocked(loadComments, generation, bypassCache)
        }
    }

    private suspend fun loadArticleLocked(
            loadComments: Boolean,
            generation: Int,
            bypassCache: Boolean
    ): DetailsPage {
        val requestId = commentsRequestId.incrementAndGet()
        val requestedUrl = initData.newsUrl?.takeIf { it.isNotBlank() }
                ?: initData.newsId.takeIf { it > 0 }?.let { "https://4pda.to/index.php?p=$it" }
        val session = ArticleOpenSession(
                articleId = initData.newsId,
                requestedUrl = requestedUrl,
                sourceScreen = initData.openSource,
                requestId = requestId,
                generation = generation
        )
        openSession = session
        deferredExtrasJob?.cancel()
        session.emitPhase("load_start")
        StateRaceTrace.log(
                domain = "article",
                event = "request_start",
                requestId = requestId,
                generation = generation,
                articleId = initData.newsId
        )

        if (!bypassCache && initData.newsId > 0) {
            prefetchService?.awaitWarm(initData.newsId)
            tryLoadCachedArticle(session, generation, requestId)?.let { return it }
        } else if (bypassCache && initData.newsId > 0) {
            invalidateArticleCaches(initData.newsId, reason = "force_refresh")
        }

        val (article, fetch) = loadArticleFromNetwork(
                generation = generation,
                requestId = requestId,
                session = session,
                allowRefetchRetry = !bypassCache,
                bypassCache = bypassCache
        )
        if (generation != articleGeneration.get()) {
            StateRaceTrace.log(
                    domain = "article",
                    event = "stale_ignored",
                    requestId = requestId,
                    generation = generation,
                    currentGeneration = articleGeneration.get(),
                    articleId = article.id,
                    reason = "after_template"
            )
            throw kotlinx.coroutines.CancellationException("Stale article load")
        }
        articleCache.put(article)
        diskCache?.put(article)
        preserveCommentsForSameArticle(article)
        updateData(article, loadComments = false, generation, requestId)
        if (articleNeedsDeferredExtras(article)) {
            scheduleDeferredArticleExtras(fetch, generation, requestId, session)
        }
        if (loadComments) {
            parseComments(article, commentsGeneration.get(), requestId, forceReload = true)
        }
        StateRaceTrace.log(
                domain = "article",
                event = "request_complete",
                requestId = requestId,
                generation = generation,
                articleId = article.id
        )
        session.markFinalUiState("Content")
        return article
    }

    private fun scheduleDeferredArticleExtrasForCachedPage(
            article: DetailsPage,
            generation: Int,
            requestId: Int,
            session: ArticleOpenSession
    ) {
        if (!articleNeedsDeferredExtras(article)) return
        if (tryApplyHintOnlyDeferredExtrasPatch(article, generation, requestId, session)) {
            scheduleBackgroundDeferredExtrasRefetch(article, generation, requestId, session)
            return
        }
        val url = resolveArticleFetchUrl(article) ?: return
        if (deferredExtrasJob?.isActive == true && deferredExtrasArticleId == article.id) {
            return
        }
        deferredExtrasJob?.cancel()
        deferredExtrasArticleId = article.id
        deferredExtrasJob = scope.launch(Dispatchers.IO) {
            try {
                session.markDeferredExtrasStart()
                session.emitPhase("deferred_extras_cache_refetch_start")
                val fetch = runCatching {
                    newsRepository.fetchArticleDetails(url, ArticleParsePhase.FULL, bypassCache = false)
                }.getOrElse { error ->
                    Timber.w(error, "Cache deferred extras refetch failed id=%d", article.id)
                    session.emitPhase("deferred_extras_cache_refetch_failed", reason = error.message)
                    return@launch
                }
                if (generation != articleGeneration.get()) return@launch
                scheduleDeferredArticleExtras(fetch, generation, requestId, session)
            } finally {
                if (deferredExtrasArticleId == article.id && deferredExtrasJob?.isActive != true) {
                    deferredExtrasArticleId = -1
                }
            }
        }
    }

    /**
     * Opens from cache with only missing comments metadata: patch footer from list hint immediately
     * so first paint is not blocked by a FULL desktop refetch on the critical path.
     */
    private fun tryApplyHintOnlyDeferredExtrasPatch(
            article: DetailsPage,
            generation: Int,
            requestId: Int,
            session: ArticleOpenSession
    ): Boolean {
        val hint = initData.hintCommentsCount
        if (hint <= 0) return false
        if (articleNeedsPollDeferredExtras(article)) return false
        if (!ArticleDeferredExtrasMerger.needsCommentsMetadataDeferredExtras(
                        article,
                        hintCommentsCount = hint,
                        hintCommentId = initData.commentId
                )
        ) {
            return false
        }
        val patched = article.copyShallowForDeferredPatch().also { target ->
            applyCommentsCountHint(target)
            if (target.commentsCount <= 0) {
                target.commentsCount = hint
            }
            if (target.commentsSource.isNullOrBlank()) {
                target.commentsSource = resolveArticleFetchUrl(target)
                        ?: "https://4pda.to/index.php?p=${target.id}"
            }
        }
        if (generation != articleGeneration.get()) return false
        preserveCommentsForSameArticle(patched)
        initData.newsId = patched.id
        _data.value = patched
        articleCache.put(patched)
        diskCache?.put(patched)
        val patch = ArticleDeferredExtrasMerger.buildPatch(patched)
        scope.launch {
            _extrasPatch.emit(patch)
        }
        session.markDeferredExtrasComplete(fullReload = false, patchApplied = true)
        session.emitPhase(
                "deferred_extras_complete",
                extra = mapOf(
                        "patchOnly" to true,
                        "commentsCount" to patched.commentsCount,
                        "source" to "hint_patch"
                )
        )
        FpdaDebugLog.log(
                FpdaDebugLog.TAG_ARTICLE_DEFERRED,
                "hint_patch_applied",
                mapOf(
                        "articleId" to patched.id,
                        "generation" to generation,
                        "commentsCount" to patched.commentsCount,
                        "hintCommentsCount" to hint
                )
        )
        return true
    }

    private fun scheduleBackgroundDeferredExtrasRefetch(
            article: DetailsPage,
            generation: Int,
            requestId: Int,
            session: ArticleOpenSession
    ) {
        if (!articleNeedsDeferredExtras(article)) return
        val url = resolveArticleFetchUrl(article) ?: return
        if (deferredExtrasJob?.isActive == true && deferredExtrasArticleId == article.id) {
            return
        }
        deferredExtrasJob?.cancel()
        deferredExtrasArticleId = article.id
        deferredExtrasJob = scope.launch(Dispatchers.IO) {
            try {
                kotlinx.coroutines.delay(BACKGROUND_DEFERRED_EXTRAS_DELAY_MS)
                if (generation != articleGeneration.get()) return@launch
                session.emitPhase("deferred_extras_cache_refetch_start")
                val fetch = runCatching {
                    newsRepository.fetchArticleDetails(url, ArticleParsePhase.FULL, bypassCache = false)
                }.getOrElse { error ->
                    Timber.w(error, "Background deferred extras refetch failed id=%d", article.id)
                    session.emitPhase("deferred_extras_cache_refetch_failed", reason = error.message)
                    return@launch
                }
                if (generation != articleGeneration.get()) return@launch
                scheduleDeferredArticleExtras(fetch, generation, requestId, session)
            } finally {
                if (deferredExtrasArticleId == article.id && deferredExtrasJob?.isActive != true) {
                    deferredExtrasArticleId = -1
                }
            }
        }
    }

    private fun DetailsPage.copyShallowForDeferredPatch(): DetailsPage =
            DetailsPage().also { copy ->
                copy.id = id
                copy.title = title
                copy.url = url
                copy.imgUrl = imgUrl
                copy.date = date
                copy.author = author
                copy.authorId = authorId
                copy.commentsCount = commentsCount
                copy.commentsSource = commentsSource
                copy.desktopCommentsSource = desktopCommentsSource
                copy.category = category
                copy.karmaMap = karmaMap
                copy.html = html
                copy.commentTree = commentTree
            }

    private fun articleNeedsPollDeferredExtras(article: DetailsPage): Boolean =
            ArticleDeferredExtrasMerger.needsPollDeferredExtras(article)

    private suspend fun tryLoadCachedArticle(
            session: ArticleOpenSession,
            generation: Int,
            requestId: Int
    ): DetailsPage? {
        val articleId = initData.newsId
        val lookup = articleCache.get(articleId)
        session.cacheHit = lookup.hit
        session.cacheValid = lookup.valid
        session.cacheRejectedReason = lookup.reason
        if (lookup.valid && lookup.entry != null) {
            resolveCachedArticle(lookup.entry.page, session, cacheLayer = "memory")?.let { cached ->
                preserveCommentsForSameArticle(cached)
                updateData(cached, loadComments = false, generation, requestId)
                scheduleDeferredArticleExtrasForCachedPage(cached, generation, requestId, session)
                session.markFinalUiState("Content_cache")
                return cached
            }
        }
        val diskLookup = withContext(Dispatchers.IO) { diskCache?.get(articleId) }
        if (diskLookup != null) {
            if (diskLookup.hit && !diskLookup.valid) {
                session.cacheHit = true
                session.cacheRejectedReason = diskLookup.reason
            }
            if (diskLookup.valid && diskLookup.entry != null) {
                session.cacheHit = true
                session.cacheValid = true
                resolveCachedArticle(diskLookup.entry.page, session, cacheLayer = "disk")?.let { cached ->
                    articleCache.put(cached)
                    preserveCommentsForSameArticle(cached)
                    updateData(cached, loadComments = false, generation, requestId)
                    scheduleDeferredArticleExtrasForCachedPage(cached, generation, requestId, session)
                    session.markFinalUiState("Content_disk_cache")
                    return cached
                }
            }
        }
        return null
    }

    private suspend fun tryResolveMemoryCacheAfterNetworkRace(
            session: ArticleOpenSession,
            generation: Int,
            requestId: Int
    ): DetailsPage? {
        if (initData.newsId <= 0) return null
        val lookup = articleCache.get(initData.newsId)
        if (!lookup.valid || lookup.entry == null) return null
        session.cacheHit = true
        session.cacheValid = true
        session.cacheRejectedReason = lookup.reason ?: "memory_race_after_network"
        return resolveCachedArticle(lookup.entry.page, session, cacheLayer = "memory_race")?.also { cached ->
            preserveCommentsForSameArticle(cached)
            updateData(cached, loadComments = false, generation, requestId)
        }
    }

    private fun articleNeedsDeferredExtras(article: DetailsPage): Boolean =
            ArticleDeferredExtrasMerger.needsDeferredExtras(
                    article = article,
                    hintCommentsCount = initData.hintCommentsCount,
                    hintCommentId = initData.commentId
            )

    private fun scheduleDeferredArticleExtras(
            fetch: ArticleFetchResult,
            generation: Int,
            requestId: Int,
            session: ArticleOpenSession
    ) {
        val articleId = fetch.page.id.takeIf { it > 0 } ?: _data.value?.id ?: -1
        if (articleId > 0) {
            deferredExtrasArticleId = articleId
        }
        deferredExtrasJob = scope.launch(Dispatchers.IO) {
            val deferredStartedAt = System.currentTimeMillis()
            try {
                session.markDeferredExtrasStart()
                session.emitPhase("deferred_extras_start")
                // Phase-1 may already have template-mapped HTML in fetch.page; poll merge must run on
                // the parser body snapshot, otherwise appendPollFromResponse mutates mapped HTML and
                // isBodyUnchanged(fetch.page, enriched) is always true (same object reference).
                val extrasFetch = ArticleDeferredExtrasHelpers.buildDeferredExtrasFetch(fetch)
                val bodyBeforeExtras = extrasFetch.page.html
                val enriched = newsRepository.enrichDesktopExtras(extrasFetch)
                if (generation != articleGeneration.get()) return@launch
                newsRepository.enrichArticleMetadata(enriched, fetch.rawBody)
                if (generation != articleGeneration.get()) return@launch
                val current = _data.value?.takeIf { it.id == enriched.id || it.id == fetch.page.id }
                val bodyUnchanged = ArticleDeferredExtrasMerger.isBodyUnchanged(bodyBeforeExtras, enriched.html)
                if (forpdateam.ru.forpda.BuildConfig.DEBUG) {
                    FpdaDebugLog.log(
                            FpdaDebugLog.TAG_ARTICLE_POLL,
                            "deferred_extras_merge",
                            mapOf(
                                    "articleId" to enriched.id,
                                    "bodyUnchanged" to bodyUnchanged,
                                    "hadPollBefore" to ArticleDeferredExtrasHelpers.hasPollBodyMarker(bodyBeforeExtras),
                                    "hasPollAfter" to ArticleDeferredExtrasHelpers.hasPollBodyMarker(enriched.html),
                                    "hasNormalizedPollAfter" to ArticleDeferredExtrasHelpers.hasNormalizedPollBodyMarker(enriched.html),
                                    "bodyLenBefore" to (bodyBeforeExtras?.length ?: 0),
                                    "bodyLenAfter" to (enriched.html?.length ?: 0)
                            )
                    )
                }
                if (bodyUnchanged && current != null && isRenderableArticle(current)) {
                    val domCount = current.commentTree?.let { countParsedComments(it) }
                    ArticleDeferredExtrasMerger.applyMetadata(current, enriched, domCount)
                    applyCommentsCountHint(current)
                    current.commentTree?.let { tree ->
                        reconcileCommentsCountFromParsed(countParsedComments(tree))
                    }
                    preserveCommentsForSameArticle(current)
                    initData.newsId = current.id
                    _data.value = current
                    articleCache.put(current)
                    diskCache?.put(current)
                    prefetchCommentsIfNeeded("deferred_patch_complete")
                    val patch = ArticleDeferredExtrasMerger.buildPatch(current)
                    _extrasPatch.emit(patch)
                    session.markDeferredExtrasComplete(fullReload = false, patchApplied = true)
                    val durationMs = System.currentTimeMillis() - deferredStartedAt
                    FpdaDebugLog.log(
                            FpdaDebugLog.TAG_ARTICLE_DEFERRED,
                            "complete",
                            mapOf(
                                    "articleId" to current.id,
                                    "generation" to generation,
                                    "durationMs" to durationMs,
                                    "fullReload" to false,
                                    "commentsCount" to current.commentsCount,
                                    "hasCommentsSource" to patch.hasCommentsSource
                            )
                    )
                    session.emitPhase(
                            "deferred_extras_complete",
                            extra = mapOf("patchOnly" to true, "commentsCount" to current.commentsCount)
                    )
                    return@launch
                }
                val remapped = withContext(Dispatchers.Default) {
                    articleTemplate.mapEntity(enriched)
                }
                if (generation != articleGeneration.get()) return@launch
                if (!isRenderableArticle(remapped)) return@launch
                preserveCommentsForSameArticle(remapped)
                initData.newsId = remapped.id
                _data.value = remapped
                articleCache.put(remapped)
                diskCache?.put(remapped)
                prefetchCommentsIfNeeded("deferred_full_complete")
                session.markDeferredExtrasComplete(fullReload = true, patchApplied = false)
                val durationMs = System.currentTimeMillis() - deferredStartedAt
                FpdaDebugLog.log(
                        FpdaDebugLog.TAG_ARTICLE_DEFERRED,
                        "complete",
                        mapOf(
                                "articleId" to remapped.id,
                                "generation" to generation,
                                "durationMs" to durationMs,
                                "fullReload" to true,
                                "mappedHtmlLen" to remapped.html?.length
                        )
                )
                session.emitPhase("deferred_extras_complete", extra = mapOf("mappedHtmlLen" to remapped.html?.length))
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                Timber.w(error, "Deferred article extras failed id=%d", fetch.page.id)
                session.emitPhase("deferred_extras_failed", reason = error.message)
                FpdaDebugLog.warn(
                        FpdaDebugLog.TAG_ARTICLE_DEFERRED,
                        "failed",
                        mapOf(
                                "articleId" to fetch.page.id,
                                "generation" to generation,
                                "error" to error.message,
                                "errorClass" to FpdaDebugLog.errorClass(error)
                        )
                )
            } finally {
                if (articleId > 0 && deferredExtrasArticleId == articleId) {
                    deferredExtrasArticleId = -1
                }
            }
        }
    }


    private fun preserveCommentsForSameArticle(article: DetailsPage) {
        if (cachedCommentsArticleId > 0 &&
                cachedCommentsArticleId == article.id &&
                article.commentTree == null &&
                cachedCommentTree != null
        ) {
            article.commentTree = cachedCommentTree
        }
    }

    private fun applyCommentsCountHint(page: DetailsPage) {
        page.commentsCount = InlineCommentsDisplayCount.resolveExpectedCount(
                page.commentsCount,
                initData.hintCommentsCount,
        )
    }

    /** Applies list hint only; comment-tree parse stays off the article open critical path. */
    private suspend fun preMapReconcileCommentsCount(page: DetailsPage) {
        val before = page.commentsCount
        applyCommentsCountHint(page)
        if (page.commentsCount > 0 && page.commentsCount != before && !page.html.isNullOrBlank()) {
            page.html = articleTemplate.restampCommentsCountInMappedHtml(page.html, page.commentsCount)
        }
    }

    private fun effectiveCommentsCount(page: DetailsPage): Int =
            InlineCommentsDisplayCount.resolveExpectedCount(
                    page.commentsCount,
                    initData.hintCommentsCount,
            )

    /** Article's own comment-count badge for UI / reload guards (list hint included). */
    fun expectedCommentsCount(): Int = _data.value?.let { effectiveCommentsCount(it) }
            ?: initData.hintCommentsCount.coerceAtLeast(0)

    private fun commentsSourceUnderfetches(article: DetailsPage): Boolean =
            newsRepository.commentsSourceUnderfetchesExpected(
                    article.commentsSource,
                    effectiveCommentsCount(article)
            )

    /**
     * True when a parsed tree carries far fewer nodes than the article's own counter badge.
     * Matches [memoryCacheCommentResult] / mobile lazy-batch detection (~10 nodes vs hundreds).
     */
    private fun parsedCommentsUnderfetchExpected(parsedCount: Int, expectedCount: Int): Boolean {
        if (expectedCount <= 0 || parsedCount <= 0) return false
        return parsedCount < expectedCount && parsedCount < (expectedCount * 9 + 9) / 10
    }

    private fun isStoredCommentTreeUnderfetched(article: DetailsPage): Boolean {
        val tree = article.commentTree ?: return false
        return !isCommentTreeFullyLoaded(tree, effectiveCommentsCount(article))
    }

    /** True only when every expected comment is already in [tree] (pagination finished). */
    private fun isCommentTreeFullyLoaded(tree: Comment, expectedCount: Int): Boolean {
        val parsed = countParsedComments(tree)
        return !ArticleCommentsPagination.hasMore(parsed, expectedCount.coerceAtLeast(0))
    }

    private fun clearUnderfetchedCommentCaches(article: DetailsPage) {
        article.commentTree = null
        if (cachedCommentsArticleId == article.id) {
            cachedCommentTree = null
            cachedCommentsArticleId = -1
        }
        commentsMemoryCache.invalidate(article.id)
    }

    private suspend fun resolveCachedArticle(
            page: DetailsPage,
            session: ArticleOpenSession,
            cacheLayer: String
    ): DetailsPage? {
        preMapReconcileCommentsCount(page)
        val remapped = withContext(Dispatchers.Default) {
            articleTemplate.remapWithCurrentTheme(page)
        }
        if (isRenderableArticle(remapped)) return remapped
        if (isRenderableArticle(page)) {
            session.cacheRejectedReason = "remap_not_renderable_used_original"
            return page
        }
        invalidateArticleCaches(page.id, reason = "remap_empty")
        session.cacheRejectedReason = "remap_empty"
        ArticleCacheTrace.log(
                event = "rejected_empty",
                articleId = page.id,
                cacheLayer = cacheLayer,
                hit = true,
                valid = false,
                mappedHtmlLen = remapped.html?.length,
                reason = "remap_empty"
        )
        return null
    }

    private suspend fun loadArticleFromNetwork(
            generation: Int,
            requestId: Int,
            session: ArticleOpenSession,
            allowRefetchRetry: Boolean,
            bypassCache: Boolean
    ): Pair<DetailsPage, ArticleFetchResult> {
        var attempt = 0
        while (true) {
            if (attempt > 0 && initData.newsId > 0) {
                invalidateArticleCaches(initData.newsId, reason = "network_retry")
            }
            session.markNetworkStart()
            val fetch = try {
                fetchArticleFromNetworkSuspend(bypassCache)
            } catch (io: java.io.IOException) {
                if (allowRefetchRetry && attempt == 0) {
                    attempt++
                    session.emitPhase("NETWORK_IO_RETRY", reason = io.javaClass.simpleName)
                    Timber.w(io, "Article fetch IOException, retrying id=%d", initData.newsId)
                    continue
                }
                throw io
            }
            val raw = fetch.page.also(::applyCommentsCountHint)
            session.markNetworkEnd(
                    status = fetch.response.code,
                    bodyBytes = fetch.rawBody.length,
                    rawHtml = fetch.rawBody
            )
            session.markParseStart()
            val bodyMetrics = ArticleHtmlValidator.measureBody(raw.html, raw.title, raw.imgUrl)
            session.markParseEnd(
                    selector = if (attempt == 0) "first_render" else "first_render_retry",
                    metrics = bodyMetrics,
                    commentsEager = false,
                    relatedEager = false,
                    commentsCountHint = raw.commentsCount
            )
            if (!ArticleHtmlValidator.hasNonEmptyParsedBody(raw)) {
                session.emitPhase("EMPTY_SUCCESS_REJECTED", reason = "empty_parsed_body")
                throw ArticleLoadException()
            }
            ArticleOpenTrace.log(
                    articleId = raw.id,
                    requestId = requestId,
                    generation = generation,
                    phase = if (attempt == 0) "network_success" else "network_success_retry",
                    url = raw.url ?: initData.newsUrl,
                    htmlLen = raw.html?.length ?: 0,
                    elapsedMs = session.let { it.networkEndMs - it.networkStartMs }
            )
            if (generation != articleGeneration.get()) {
                StateRaceTrace.log(
                        domain = "article",
                        event = "stale_ignored",
                        requestId = requestId,
                        generation = generation,
                        currentGeneration = articleGeneration.get(),
                        articleId = raw.id,
                        reason = "after_network"
                )
                throw kotlinx.coroutines.CancellationException("Stale article load")
            }
            if (!bypassCache) {
                tryResolveMemoryCacheAfterNetworkRace(session, generation, requestId)?.let { cached ->
                    session.markTemplateDone(0L, cached.html?.length ?: 0)
                    return cached to fetch
                }
            }
            preMapReconcileCommentsCount(raw)
            val templateStartedAt = ArticleOpenTrace.nowMs()
            val article = withContext(Dispatchers.Default) {
                articleTemplate.mapEntity(raw)
            }
            val templateMs = ArticleOpenTrace.nowMs() - templateStartedAt
            session.markTemplateDone(templateMs, article.html?.length ?: 0)
            ArticleOpenTrace.log(
                    articleId = article.id,
                    requestId = requestId,
                    generation = generation,
                    phase = if (attempt == 0) "template_mapped" else "template_mapped_retry",
                    url = article.url ?: initData.newsUrl,
                    htmlLen = raw.html?.length ?: 0,
                    mappedHtmlLen = article.html?.length ?: 0,
                    elapsedMs = templateMs
            )
            if (isRenderableArticle(article)) {
                return article to fetch
            }
            if (allowRefetchRetry && attempt == 0 && fetch.response.code in 500..599) {
                attempt++
                session.emitPhase("EMPTY_MAP_RETRY", reason = "5xx_${fetch.response.code}")
                Timber.w(
                        "Article 5xx mapped HTML not renderable, refetching id=%d code=%d rawLen=%d mappedLen=%d contentLen=%d bodyLen=%d",
                        article.id,
                        fetch.response.code,
                        raw.html?.length ?: 0,
                        article.html?.length ?: 0,
                        ArticleHtmlValidator.mappedContentPlainTextLen(article.html.orEmpty()),
                        ArticleHtmlValidator.mappedBodyPlainTextLen(article.html.orEmpty())
                )
                continue
            }
            rejectEmptyMappedArticle(article, raw, requestId, generation, session)
            throw ArticleLoadException()
        }
    }

    private suspend fun fetchArticleFromNetworkSuspend(bypassCache: Boolean): ArticleFetchResult =
            if (initData.newsId > 0) {
                newsRepository.fetchArticleDetails(initData.newsId, ArticleParsePhase.FIRST_RENDER, bypassCache)
            } else {
                newsRepository.fetchArticleDetails(initData.newsUrl.orEmpty(), ArticleParsePhase.FIRST_RENDER, bypassCache)
            }

    private fun rejectEmptyMappedArticle(
            article: DetailsPage,
            raw: DetailsPage,
            requestId: Int,
            generation: Int,
            session: ArticleOpenSession
    ) {
        if (forpdateam.ru.forpda.BuildConfig.DEBUG) {
            Timber.w(
                    "Article load produced empty HTML id=%d title=%s rawLen=%d mappedLen=%d contentLen=%d bodyLen=%d",
                    article.id,
                    article.title,
                    raw.html?.length ?: 0,
                    article.html?.length ?: 0,
                    ArticleHtmlValidator.mappedContentPlainTextLen(article.html.orEmpty()),
                    ArticleHtmlValidator.mappedBodyPlainTextLen(article.html.orEmpty())
            )
        }
        ArticleCacheTrace.log(
                event = "rejected_empty",
                articleId = article.id,
                cacheLayer = "memory",
                hit = false,
                valid = false,
                htmlLen = raw.html?.length ?: 0,
                mappedHtmlLen = article.html?.length ?: 0,
                reason = "empty_or_unexpected_html"
        )
        ArticleOpenTrace.emptySuccessRejected(
                articleId = article.id,
                requestId = requestId,
                generation = generation,
                htmlLen = raw.html?.length ?: 0,
                mappedHtmlLen = article.html?.length ?: 0,
                reason = "empty_or_unexpected_html"
        )
        session.markFinalUiState("Error_empty")
    }

    private fun isRenderableArticle(article: DetailsPage): Boolean =
            ArticleHtmlValidator.isRenderableMappedHtml(article.html.orEmpty())

    suspend fun likeComment(commentId: Int): Boolean =
            newsRepository.likeComment(initData.newsId, commentId)

    suspend fun unlikeComment(commentId: Int): Boolean =
            newsRepository.unlikeComment(initData.newsId, commentId)

    suspend fun voteComment(action: Comment.Action): CommentKarmaVoteResult {
        val result = newsRepository.voteComment(action)
        applyKarmaVoteLocally(result)
        return result
    }

    suspend fun executeCommentAction(action: Comment.Action, extraFields: Map<String, String> = emptyMap()): Boolean {
        logCommentAction("execute", action)
        val result = newsRepository.executeCommentAction(action, extraFields)
        reloadCommentsAfterMutation()
        return result
    }

    suspend fun deleteComment(action: Comment.Action): Boolean {
        logCommentAction("delete", action)
        val result = newsRepository.deleteComment(action)
        reloadCommentsAfterMutation()
        return result
    }

    suspend fun editComment(action: Comment.Action, text: String): Boolean {
        logCommentAction("edit", action)
        val result = newsRepository.editComment(action, text, commentEditContext())
        reloadCommentsAfterMutation()
        return result
    }

    suspend fun loadEditCommentForm(action: Comment.Action): Comment.Action =
            newsRepository.loadEditCommentForm(action, commentEditContext())

    private fun commentEditContext(): CommentEditContext {
        val article = _data.value
        return CommentEditContext(
                commentsSource = article?.commentsSource?.takeIf { it.isNotBlank() }
                        ?: article?.desktopCommentsSource?.takeIf { it.isNotBlank() },
                articleHtml = article?.html,
                articleUrl = article?.url?.takeIf { it.isNotBlank() }
                        ?: initData.newsUrl?.takeIf { it.isNotBlank() },
                articleId = article?.id?.takeIf { it > 0 } ?: initData.newsId
        )
    }

    suspend fun sendPoll(from: String, pollId: Int, answersId: IntArray): DetailsPage {
        val raw = newsRepository.sendPoll(from, pollId, answersId)
        val article = articleTemplate.mapEntity(raw)
        updateData(article, loadComments = true, articleGeneration.get(), commentsRequestId.get())
        return article
    }

    suspend fun votePoll(from: String, pollId: Int, answersId: IntArray): String =
            newsRepository.votePoll(from, pollId, answersId)

    suspend fun replyComment(commentId: Int, comment: String): DetailsPage {
        FpdaDebugLog.log(
                FpdaDebugLog.TAG_COMMENT_ACTION,
                "reply_submit",
                mapOf(
                        "articleId" to initData.newsId,
                        "commentId" to commentId,
                        "textLen" to comment.trim().length
                )
        )
        commentsPrefetchJob?.cancel()
        commentsPrefetchJob = null
        commentsPrefetchArticleId = -1
        val raw = newsRepository.replyComment(initData.newsId, commentId, comment)
        val mapped = articleTemplate.mapEntity(raw)
        val current = _data.value
        val article = if (current != null &&
                current.id > 0 &&
                current.id == mapped.id &&
                isRenderableArticle(current)
        ) {
            mergeCommentSources(current, mapped)
            current
        } else {
            mapped
        }
        val knownIds = (_data.value?.commentTree ?: cachedCommentTree)?.let(::flattenCommentIds).orEmpty()
        val generation = commentsGeneration.get()
        val requestId = commentsRequestId.incrementAndGet()
        preserveCommentsForSameArticle(article)
        _data.value = article
        // Set the scroll target from the authoritative POST redirect (#commentNNN) BEFORE parsing:
        // parseComments emits the tree, and the collector consumes pendingScrollCommentId while
        // processing that emission — setting it afterwards was a race that dropped the scroll.
        scheduleScrollToNewComment(null, knownIds, article.url, comment)
        val resolvedFromRedirect = pendingScrollCommentId > 0
        val parseResult = parseComments(article, generation, requestId, forceReload = true)
        if (!resolvedFromRedirect) {
            // Redirect lacked the id — fall back to diffing the freshly parsed tree against knownIds.
            val tree = (parseResult as? CommentLoadResult.Loaded)?.tree ?: cachedCommentTree
            scheduleScrollToNewComment(tree, knownIds, article.url, comment)
        }
        return article
    }

    private fun applyKarmaVoteLocally(result: CommentKarmaVoteResult) {
        if (result.commentId <= 0) return
        val article = _data.value ?: return
        article.karmaMap.put(result.commentId, result.karma.copy())
        updateCommentTreeKarma(article.commentTree, result)
        updateCommentTreeKarma(cachedCommentTree, result)
    }

    private fun updateCommentTreeKarma(tree: Comment?, result: CommentKarmaVoteResult) {
        if (tree == null) return
        fun walk(comment: Comment): Boolean {
            if (comment.id == result.commentId) {
                comment.karma = result.karma.copy()
                comment.likedByMe = result.likedByMe
                if (result.karma.count > 0 || comment.likeCount == 0) {
                    comment.likeCount = result.karma.count
                }
                return true
            }
            return comment.children.any { walk(it) }
        }
        walk(tree)
    }

    private suspend fun reloadCommentsAfterMutation() {
        val article = _data.value ?: return
        invalidateCommentsForMutation(article.id)
        val generation = commentsGeneration.get()
        val requestId = commentsRequestId.incrementAndGet()
        parseComments(article, generation, requestId, forceReload = true)
    }

    private fun invalidateCommentsForMutation(articleId: Int) {
        if (articleId <= 0) return
        commentsPrefetchJob?.cancel()
        commentsPrefetchJob = null
        commentsPrefetchArticleId = -1
        commentsMemoryCache.invalidate(articleId)
        _data.value?.takeIf { it.id == articleId }?.let { article ->
            article.commentTree = null
            // Также сбрасываем закэшированный HTML комментов: без этого parseComments(forceReload=true)
            // ПЕРЕПАРСИВАЛ старый article.commentsSource (со старым текстом), и правка/удаление/новые
            // данные не подтягивались — правка отражалась только оптимистичным патчем и терялась при
            // reload. Обнуление заставляет ensureCommentsSource дотянуть свежий HTML с сервера.
            article.commentsSource = null
            article.desktopCommentsSource = null
        }
        cachedCommentsArticleId = -1
        cachedCommentTree = null
        FpdaDebugLog.log(
                FpdaDebugLog.TAG_COMMENTS_SECTION,
                "mutation_cache_invalidated",
                mapOf("articleId" to articleId)
        )
    }

    private fun scheduleScrollToNewComment(
            tree: Comment?,
            knownIds: Set<Int>,
            url: String?,
            submittedText: String? = null
    ) {
        pendingScrollCommentId = NewCommentDetector.resolvePendingScrollCommentId(
                tree = tree,
                knownIds = knownIds,
                redirectUrl = url,
                submittedText = submittedText
        )
    }

    fun extractCommentIdFromUrl(url: String?): Int = NewCommentDetector.extractCommentIdFromUrl(url)

    fun findNewCommentId(tree: Comment, knownIds: Set<Int>): Int =
            NewCommentDetector.findNewCommentId(tree, knownIds)

    fun findNewCommentId(
            tree: Comment,
            knownIds: Set<Int>,
            submittedText: String?,
            currentUserId: Int = 0
    ): Int = NewCommentDetector.findNewCommentId(tree, knownIds, submittedText, currentUserId)

    private fun mergeCommentSources(target: DetailsPage, source: DetailsPage) {
        val domCount = target.commentTree?.let { countParsedComments(it) }
        val merged = InlineCommentsDisplayCount.mergeMetadataCount(target.commentsCount, source.commentsCount, domCount)
        if (merged != target.commentsCount) {
            target.commentsCount = merged
        }
        if (!source.commentsSource.isNullOrBlank()) {
            target.commentsSource = source.commentsSource
        }
        if (!source.desktopCommentsSource.isNullOrBlank()) {
            target.desktopCommentsSource = source.desktopCommentsSource
        }
        if (source.karmaMap != null) {
            target.karmaMap = source.karmaMap
        }
        if (!source.url.isNullOrBlank()) {
            target.url = source.url
        }
        // Keep rendered article HTML; only refresh comment sources on the next parse.
    }

    private fun flattenComments(root: Comment): List<Comment> {
        val result = ArrayList<Comment>()
        fun walk(comment: Comment) {
            comment.children.forEach { child ->
                result.add(child)
                walk(child)
            }
        }
        walk(root)
        return result
    }

    private fun flattenCommentIds(root: Comment): Set<Int> =
            flattenComments(root).map { it.id }.filter { it > 0 }.toSet()

    private fun logCommentAction(kind: String, action: Comment.Action) {
        FpdaDebugLog.log(
                FpdaDebugLog.TAG_COMMENT_ACTION,
                kind,
                mapOf(
                        "articleId" to initData.newsId,
                        "actionUrl" to FpdaDebugLog.sanitizeUrl(action.url),
                        "method" to action.method,
                        "fieldCount" to action.fields.size
                )
        )
    }

    private fun invalidateArticleCaches(
            articleId: Int,
            reason: String,
            includeMemory: Boolean = true,
            includeDisk: Boolean = true,
            includeComments: Boolean = true,
            cancelDeferredExtras: Boolean = true
    ) {
        if (articleId <= 0) return
        if (cancelDeferredExtras) {
            deferredExtrasJob?.cancel()
            deferredExtrasJob = null
            deferredExtrasArticleId = -1
        }
        if (includeMemory) {
            articleCache.invalidate(articleId)
        }
        if (includeDisk) {
            diskCache?.invalidate(articleId)
        }
        if (includeComments) {
            commentsMemoryCache.invalidate(articleId)
            if (_data.value?.id == articleId) {
                _data.value?.commentTree = null
            }
            cachedCommentsArticleId = -1
            cachedCommentTree = null
            commentsPrefetchJob?.cancel()
            commentsPrefetchJob = null
            commentsPrefetchArticleId = -1
        }
        ArticleCacheTrace.log(
                event = "invalidate",
                articleId = articleId,
                cacheLayer = "all",
                hit = false,
                valid = false,
                reason = reason
        )
    }

    /**
     * Comments are loaded only when the user expands the section ([loadComments]).
     * Article open must not fetch or parse comment trees in the background.
     */
    /**
     * Warms the embedded full-comment-tree cache in the background right after the article is ready,
     * so the first "Показать" tap slices instantly instead of blocking on the ~8s one-shot parse.
     * [loadComments] joins this job via [awaitActiveCommentsPrefetch], so a tap mid-parse still gets
     * the warm tree (no duplicate parse). Idempotent and cheap to call repeatedly.
     */
    fun prefetchCommentsIfNeeded(reason: String = "article_ready") {
        val article = _data.value ?: return
        val articleId = article.id
        if (articleId <= 0) return
        val source = article.commentsSource?.takeIf { it.isNotBlank() } ?: return
        // Already warm for this exact source, or a prefetch for it is already running.
        if (fullEmbeddedCommentTree != null && fullEmbeddedCommentKey == source.hashCode()) return
        if (commentsPrefetchJob?.isActive == true && commentsPrefetchArticleId == articleId) return
        commentsPrefetchJob?.cancel()
        commentsPrefetchArticleId = articleId
        // Everything heavy (the eligibility regex + the ~8s parse) runs on Default so the calling
        // thread (Main, from the deferred-extras completion) never blocks.
        commentsPrefetchJob = scope.launch(Dispatchers.Default) {
            if (!shouldUseEmbeddedLocalSlice(article)) return@launch
            runCatching { embeddedFullCommentTree(article, source) }
            FpdaDebugLog.log(
                    FpdaDebugLog.TAG_COMMENTS_SECTION,
                    "prefetch_warm",
                    mapOf(
                            "articleId" to articleId,
                            "reason" to reason,
                            "warmed" to (fullEmbeddedCommentKey == source.hashCode()),
                    )
            )
        }
    }

    private fun resetCommentsPagination() {
        commentsNextNetworkPage = 2
        commentsPaginationActive = false
    }

    /**
     * Loads the first comment batch only (embedded mobile partial or network page 1).
     * Does not fetch the full desktop comment tree.
     */
    suspend fun loadComments(forceReload: Boolean = false): CommentLoadResult {
        if (forceReload) {
            resetCommentsPagination()
            _data.value?.let { article ->
                article.commentTree = null
                // КРИТИЧНО: сбрасываем и закэшированный HTML комментов. Без этого
                // parseComments(forceReload=true) ПЕРЕПАРСИВАЛ старый article.commentsSource (без
                // свежего ответа), и force-reload из упоминания «обновлял», но новый коммент-ответ так
                // и не появлялся. Обнуление заставляет ensureCommentsSource дотянуть свежий HTML.
                article.commentsSource = null
                article.desktopCommentsSource = null
            }
            cachedCommentTree = null
            cachedCommentsArticleId = -1
            commentsMemoryCache.invalidate(_data.value?.id ?: initData.newsId)
        }
        val generation = commentsGeneration.get()
        val articleId = _data.value?.id ?: initData.newsId
        if (!forceReload) {
            awaitActiveCommentsPrefetch(articleId)
        }
        val requestId = commentsRequestId.incrementAndGet()
        FpdaDebugLog.log(
                FpdaDebugLog.TAG_COMMENTS_SECTION,
                "load_requested",
                mapOf(
                        "articleId" to articleId,
                        "requestId" to requestId,
                        "forceReload" to forceReload,
                        "paginated" to true,
                        "page" to 1,
                        "commentsCountExpected" to _data.value?.commentsCount,
                        "commentsUrlPresent" to !_data.value?.commentsSource.isNullOrBlank(),
                )
        )
        val article = _data.value ?: loadArticle(loadComments = false)
        if (generation != commentsGeneration.get()) {
            return CommentLoadResult.Stale
        }
        if (!forceReload) {
            val expected = effectiveCommentsCount(article)
            article.commentTree
                    ?.takeIf { countParsedComments(it) > 0 }
                    ?.takeIf { !isStoredCommentTreeUnderfetched(article) }
                    ?.let { stored ->
                        return emitStoredCommentTree(
                                article = article,
                                tree = stored,
                                generation = generation,
                                requestId = requestId,
                                fromCache = true,
                        )
                    }
            memoryCacheCommentResult(article.id, expected)?.let { cached ->
                if (countParsedComments(cached) > 0) {
                    return emitStoredCommentTree(
                            article = article,
                            tree = cached,
                            generation = generation,
                            requestId = requestId,
                            fromCache = true,
                    )
                }
            }
        }
        return loadCommentsPage(
                article = article,
                page = 1,
                append = false,
                generation = generation,
                requestId = requestId,
                forceReload = forceReload,
        )
    }

    /** Fetches the next comment page from the server and appends to the accumulated tree. */
    suspend fun loadCommentsNextPage(): CommentLoadResult {
        val generation = commentsGeneration.get()
        val article = _data.value ?: return CommentLoadResult.Error(IllegalStateException("no_article"))
        val page = commentsNextNetworkPage
        val requestId = commentsRequestId.incrementAndGet()
        FpdaDebugLog.log(
                FpdaDebugLog.TAG_COMMENTS_SECTION,
                "load_more_requested",
                mapOf(
                        "articleId" to article.id,
                        "requestId" to requestId,
                        "page" to page,
                        "loadedSoFar" to countParsedComments(article.commentTree ?: Comment()),
                        "commentsCountExpected" to effectiveCommentsCount(article),
                )
        )
        if (generation != commentsGeneration.get()) {
            return CommentLoadResult.Stale
        }
        return loadCommentsPage(
                article = article,
                page = page,
                append = true,
                generation = generation,
                requestId = requestId,
                forceReload = false,
        )
    }

    private suspend fun loadCommentsPage(
            article: DetailsPage,
            page: Int,
            append: Boolean,
            generation: Int,
            requestId: Int,
            forceReload: Boolean,
    ): CommentLoadResult {
        if (requestId != commentsRequestId.get()) return CommentLoadResult.Stale
        val batchParse = when {
            !append && !forceReload -> {
                if (shouldUseEmbeddedLocalSlice(article)) {
                    tryParseEmbeddedCommentsPaginated(article)
                } else {
                    null
                } ?: fetchAndParseCommentsPage(article, page)
            }
            append && page > 1 -> resolveAppendCommentBatch(article, page)
            else -> fetchAndParseCommentsPage(article, page)
        } ?: run {
            if (append) {
                tryParseEmbeddedCommentsFull(article)?.let { full ->
                    return emitRecoveredCommentBatch(
                            article = article,
                            batchParse = full,
                            append = true,
                            generation = generation,
                            requestId = requestId,
                            page = page,
                            recovery = "embedded_full_after_empty_page",
                    )
                }
            }
            FpdaDebugLog.log(
                    FpdaDebugLog.TAG_COMMENTS_SECTION,
                    "paginated_fetch_empty",
                    mapOf(
                            "articleId" to article.id,
                            "requestId" to requestId,
                            "page" to page,
                            "append" to append,
                            "commentsCountExpected" to effectiveCommentsCount(article),
                    )
            )
            return if (page == 1 && effectiveCommentsCount(article) <= 0) {
                CommentLoadResult.Empty("no_comments_expected")
            } else {
                CommentLoadResult.Error(IllegalStateException("comments_page_fetch_empty"))
            }
        }
        val batchTree = batchParse.tree
        if (requestId != commentsRequestId.get()) return CommentLoadResult.Stale
        if (generation != commentsGeneration.get()) return CommentLoadResult.Stale
        commentsPaginationActive = true

        val loadedBeforeMerge = if (append) countParsedComments(article.commentTree ?: Comment()) else 0
        val merged = if (append) {
            mergeCommentBatch(article.commentTree, batchTree)
        } else {
            batchTree
        }
        val parsedCount = countParsedComments(merged)
        if (append && parsedCount <= loadedBeforeMerge) {
            tryParseEmbeddedCommentsFull(article)?.let { full ->
                val recovered = mergeCommentBatch(article.commentTree, full.tree)
                val recoveredCount = countParsedComments(recovered)
                if (recoveredCount > loadedBeforeMerge) {
                    return emitRecoveredCommentBatch(
                            article = article,
                            batchParse = full,
                            append = true,
                            generation = generation,
                            requestId = requestId,
                            page = page,
                            recovery = "embedded_full_after_duplicate_page",
                            mergedTree = recovered,
                    )
                }
            }
            return emitCommentBatchExhausted(
                    article = article,
                    tree = article.commentTree ?: merged,
                    generation = generation,
                    requestId = requestId,
                    page = page,
                    reason = "load_more_no_new_comments",
            )
        }
        if (parsedCount <= 0) {
            return if (page == 1) {
                CommentLoadResult.Empty("parse_empty")
            } else {
                CommentLoadResult.Error(IllegalStateException("comments_page_parse_empty"))
            }
        }
        reconcileCommentsCountAfterBatch(parsedCount, batchParse.sourceNodeCount)
        val expected = effectiveCommentsCount(article)
        val hasMore = ArticleCommentsPagination.hasMore(parsedCount, expected)
        if (!hasMore) {
            commentsPaginationActive = false
        }
        article.commentTree = merged
        cachedCommentTree = merged
        cachedCommentsArticleId = article.id
        if (!hasMore) {
            commentsMemoryCache.put(article.id, merged)
        }
        _data.value = article
        _comments.emit(merged)
        if (append || page >= commentsNextNetworkPage) {
            commentsNextNetworkPage = ArticleCommentsPagination.nextPageAfter(page)
        }
        FpdaDebugLog.log(
                FpdaDebugLog.TAG_COMMENTS_SECTION,
                if (append) "load_more_success" else "parse_success",
                mapOf(
                        "articleId" to article.id,
                        "requestId" to requestId,
                        "page" to page,
                        "parsedCommentsCount" to parsedCount,
                        "commentsCountExpected" to expected,
                        "hasMore" to hasMore,
                        "append" to append,
                )
        )
        return CommentLoadResult.Loaded(
                tree = merged,
                fromCache = false,
                hasMore = hasMore,
                append = append,
                page = page,
        )
    }

    private data class CommentBatchParse(val tree: Comment, val sourceNodeCount: Int)

    private suspend fun tryParseEmbeddedCommentsFull(article: DetailsPage): CommentBatchParse? {
        val source = article.commentsSource ?: return null
        if (!newsRepository.hasCommentNodeMarkup(source)) return null
        return withContext(Dispatchers.Default) {
            runCatching {
                newsRepository.parseCommentsFromSource(article, source, paginated = false, commentPage = 1)
            }
                    .getOrNull()
                    ?.takeIf { countParsedComments(it) > 0 }
                    ?.let { CommentBatchParse(it, sourceNodeCount(source)) }
        }?.also { batch ->
            FpdaDebugLog.log(
                    FpdaDebugLog.TAG_COMMENTS_SECTION,
                    "embedded_full_slice",
                    mapOf(
                            "articleId" to article.id,
                            "nodeCount" to batch.sourceNodeCount,
                            "parsedCount" to countParsedComments(batch.tree),
                    )
            )
        }
    }

    private suspend fun emitRecoveredCommentBatch(
            article: DetailsPage,
            batchParse: CommentBatchParse,
            append: Boolean,
            generation: Int,
            requestId: Int,
            page: Int,
            recovery: String,
            mergedTree: Comment? = null,
    ): CommentLoadResult {
        if (requestId != commentsRequestId.get()) return CommentLoadResult.Stale
        if (generation != commentsGeneration.get()) return CommentLoadResult.Stale
        val merged = mergedTree ?: if (append) {
            mergeCommentBatch(article.commentTree, batchParse.tree)
        } else {
            batchParse.tree
        }
        val parsedCount = countParsedComments(merged)
        reconcileCommentsCountAfterBatch(parsedCount, batchParse.sourceNodeCount)
        val expected = effectiveCommentsCount(article)
        val hasMore = ArticleCommentsPagination.hasMore(parsedCount, expected)
        commentsPaginationActive = hasMore
        article.commentTree = merged
        cachedCommentTree = merged
        cachedCommentsArticleId = article.id
        if (!hasMore) {
            commentsMemoryCache.put(article.id, merged)
        }
        _data.value = article
        _comments.emit(merged)
        if (append || page >= commentsNextNetworkPage) {
            commentsNextNetworkPage = ArticleCommentsPagination.nextPageAfter(page)
        }
        FpdaDebugLog.log(
                FpdaDebugLog.TAG_COMMENTS_SECTION,
                "load_more_recovered",
                mapOf(
                        "articleId" to article.id,
                        "requestId" to requestId,
                        "page" to page,
                        "recovery" to recovery,
                        "parsedCommentsCount" to parsedCount,
                        "commentsCountExpected" to expected,
                        "hasMore" to hasMore,
                )
        )
        return CommentLoadResult.Loaded(
                tree = merged,
                fromCache = false,
                hasMore = hasMore,
                append = append,
                page = page,
        )
    }

    private suspend fun emitCommentBatchExhausted(
            article: DetailsPage,
            tree: Comment,
            generation: Int,
            requestId: Int,
            page: Int,
            reason: String,
    ): CommentLoadResult {
        if (requestId != commentsRequestId.get()) return CommentLoadResult.Stale
        if (generation != commentsGeneration.get()) return CommentLoadResult.Stale
        commentsPaginationActive = false
        val parsedCount = countParsedComments(tree)
        article.commentTree = tree
        cachedCommentTree = tree
        cachedCommentsArticleId = article.id
        commentsMemoryCache.put(article.id, tree)
        _data.value = article
        _comments.emit(tree)
        FpdaDebugLog.log(
                FpdaDebugLog.TAG_COMMENTS_SECTION,
                reason,
                mapOf(
                        "articleId" to article.id,
                        "requestId" to requestId,
                        "page" to page,
                        "parsedCommentsCount" to parsedCount,
                        "commentsCountExpected" to effectiveCommentsCount(article),
                )
        )
        return CommentLoadResult.Loaded(
                tree = tree,
                fromCache = false,
                hasMore = false,
                append = false,
                page = page,
        )
    }

    private suspend fun tryParseEmbeddedCommentsPaginated(
            article: DetailsPage,
            commentPage: Int = 1,
    ): CommentBatchParse? {
        if (!shouldUseEmbeddedLocalSlice(article)) return null
        val source = article.commentsSource ?: return null
        if (!newsRepository.hasCommentNodeMarkup(source)) return null
        val nodeCount = sourceNodeCount(source)
        val page = commentPage.coerceAtLeast(1)
        return withContext(Dispatchers.Default) {
            // Parse the full embedded tree once (cached), then slice this page in-memory. Falls back
            // to the per-page parse only if the one-shot full parse fails, so behaviour is unchanged
            // on the unhappy path.
            val sliced = embeddedFullCommentTree(article, source)?.let { full ->
                runCatching { newsRepository.capPaginatedCommentBatch(full, page) }.getOrNull()
            }?.takeIf { countParsedComments(it) > 0 }
            if (sliced != null) {
                CommentBatchParse(sliced, nodeCount)
            } else {
                runCatching {
                    newsRepository.parseCommentsFromSource(article, source, paginated = true, commentPage = page)
                }
                        .getOrNull()
                        ?.takeIf { countParsedComments(it) > 0 }
                        ?.let { CommentBatchParse(it, nodeCount) }
            }
        }?.also { batch ->
            FpdaDebugLog.log(
                    FpdaDebugLog.TAG_COMMENTS_SECTION,
                    "embedded_local_slice",
                    mapOf(
                            "articleId" to article.id,
                            "commentPage" to page,
                            "nodeCount" to batch.sourceNodeCount,
                            "parsedCount" to countParsedComments(batch.tree),
                    )
            )
        }
    }

    /**
     * Parses the full embedded comment tree ONCE and memoizes it (keyed by source hash), so that
     * pagination slices in-memory instead of re-walking the ~400KB source per page. Returns null on
     * parse failure, letting callers fall back to the per-page parse.
     */
    /** Memoized [countCommentNodesInSource] — the raw call is a ~1.5s regex scan of the full source. */
    private fun sourceNodeCount(source: String): Int {
        val key = source.hashCode()
        if (cachedSourceNodeCountKey == key && cachedSourceNodeCount >= 0) return cachedSourceNodeCount
        val count = newsRepository.countCommentNodesInSource(source)
        cachedSourceNodeCount = count
        cachedSourceNodeCountKey = key
        return count
    }

    private suspend fun embeddedFullCommentTree(article: DetailsPage, source: String): Comment? {
        val key = source.hashCode()
        fullEmbeddedCommentTree?.let { if (fullEmbeddedCommentKey == key) return it }
        val startNs = System.nanoTime()
        val full = runCatching {
            newsRepository.parseCommentsFromSource(article, source, paginated = false)
        }.getOrNull()
        if (full == null || countParsedComments(full) <= 0) return null
        fullEmbeddedCommentTree = full
        fullEmbeddedCommentKey = key
        FpdaDebugLog.log(
                FpdaDebugLog.TAG_COMMENTS_SECTION,
                "embedded_full_parse_once",
                mapOf(
                        "articleId" to article.id,
                        "parsedCount" to countParsedComments(full),
                        "ms" to (System.nanoTime() - startNs) / 1_000_000,
                )
        )
        return full
    }

    /**
     * Mobile article HTML may embed hundreds of comment nodes. Page 1 must slice the first batch
     * locally (tag-only + [limitPaginatedCommentBatch]) instead of re-fetching the full article.
     */
    private suspend fun resolveAppendCommentBatch(
            article: DetailsPage,
            page: Int,
    ): CommentBatchParse? {
        val loadedSoFar = countParsedComments(article.commentTree ?: Comment())
        val expected = effectiveCommentsCount(article)
        val embeddedNodes = article.commentsSource?.let { sourceNodeCount(it) } ?: 0
        // Full embedded HTML: slice the next WP page locally instead of re-parsing all nodes.
        if (embeddedNodes > loadedSoFar) {
            tryParseEmbeddedCommentsPaginated(article, page)?.let { sliced ->
                if (countParsedComments(sliced.tree) > 0) {
                    return sliced
                }
            }
        }
        if (embeddedNodes < expected && loadedSoFar < expected) {
            fetchAndParseCommentsPage(article, page)?.let { network ->
                if (countParsedComments(network.tree) > 0) {
                    return network
                }
            }
        }
        return fetchAndParseCommentsPage(article, page)
    }

    private fun shouldUseEmbeddedLocalSlice(article: DetailsPage): Boolean {
        val source = article.commentsSource ?: return false
        if (!newsRepository.hasCommentNodeMarkup(source)) return false
        val nodeCount = sourceNodeCount(source)
        if (nodeCount <= 0) return false
        val expected = effectiveCommentsCount(article)
        if (expected <= 0) {
            return nodeCount > ArticleCommentsPagination.COMMENTS_PER_PAGE
        }
        // Badge fits in one WP page but mobile HTML only embeds a subset — fetch cp=1 instead.
        if (expected <= ArticleCommentsPagination.COMMENTS_PER_PAGE && nodeCount < expected) {
            FpdaDebugLog.log(
                    FpdaDebugLog.TAG_COMMENTS_SECTION,
                    "embedded_batch_skipped_small_article",
                    mapOf(
                            "articleId" to article.id,
                            "nodeCount" to nodeCount,
                            "expectedCount" to expected,
                    )
            )
            return false
        }
        // Mobile lazy batch (~10 nodes) cannot fill the first WP page — fetch cp=1 for 20 instead.
        if (expected > ArticleCommentsPagination.COMMENTS_PER_PAGE &&
                nodeCount < ArticleCommentsPagination.COMMENTS_PER_PAGE
        ) {
            FpdaDebugLog.log(
                    FpdaDebugLog.TAG_COMMENTS_SECTION,
                    "embedded_batch_skipped_prefetch_network",
                    mapOf(
                            "articleId" to article.id,
                            "nodeCount" to nodeCount,
                            "expectedCount" to expected,
                            "minBatch" to ArticleCommentsPagination.COMMENTS_PER_PAGE,
                    )
            )
            return false
        }
        return nodeCount > ArticleCommentsPagination.COMMENTS_PER_PAGE ||
                newsRepository.commentsSourceUnderfetchesExpected(source, expected)
    }

    private suspend fun fetchAndParseCommentsPage(article: DetailsPage, page: Int): CommentBatchParse? {
        if (page == 1 && article.commentsSource.isNullOrBlank()) {
            ensureCommentsSource(article, "paginated_page_1")
        }
        val url = resolveArticleFetchUrl(article) ?: return null
        val strategy = classifyCommentsPaginationStrategy(article, page)
        FpdaDebugLog.log(
                FpdaDebugLog.TAG_COMMENTS_SECTION,
                "response_classified",
                mapOf(
                        "articleId" to article.id,
                        "page" to page,
                        "strategy" to strategy.name,
                        "expectedCount" to effectiveCommentsCount(article),
                        "embeddedNodes" to article.commentsSource
                                ?.let { newsRepository.countCommentNodesInSource(it) },
                )
        )
        val source = resolveCommentsPageSource(article, page, url) ?: return null
        val parseStartedMs = System.currentTimeMillis()
        return withContext(Dispatchers.Default) {
            val nodeCount = newsRepository.countCommentNodesInSource(source)
            FpdaDebugLog.log(
                    FpdaDebugLog.TAG_COMMENTS_SECTION,
                    if (page == 1 && source == article.commentsSource) "embedded_page_source" else "network_page_source",
                    mapOf(
                            "articleId" to article.id,
                            "page" to page,
                            "sourceLen" to source.length,
                            "nodeCount" to nodeCount,
                            "fromEmbedded" to (page == 1 && source == article.commentsSource),
                            "strategy" to strategy.name,
                    )
            )
            val tree = runCatching {
                newsRepository.parseCommentsFromSource(
                        article,
                        source,
                        paginated = true,
                        commentPage = page,
                )
            }.getOrNull()?.takeIf { countParsedComments(it) > 0 }
            FpdaDebugLog.log(
                    FpdaDebugLog.TAG_COMMENTS_SECTION,
                    "parse_batch_timing",
                    mapOf(
                            "articleId" to article.id,
                            "page" to page,
                            "elapsedMs" to (System.currentTimeMillis() - parseStartedMs),
                            "nodeCount" to nodeCount,
                            "parsedCount" to (tree?.let { countParsedComments(it) } ?: 0),
                            "strategy" to strategy.name,
                    )
            )
            tree?.let { CommentBatchParse(it, nodeCount) }
        }
    }

    private enum class CommentsPaginationStrategy {
        /** Mobile phase-1 embeds only the first lazy batch (~10 nodes). */
        LOCAL_SLICE,
        /** All batches fetched via `cp=` / comment-page-N network pages. */
        SERVER_PAGE,
        /** First batch from embedded partial, subsequent pages from network. */
        HYBRID,
    }

    private fun classifyCommentsPaginationStrategy(article: DetailsPage, page: Int): CommentsPaginationStrategy {
        if (page > 1) return CommentsPaginationStrategy.SERVER_PAGE
        return when {
            shouldUseEmbeddedLocalSlice(article) -> {
                val expected = effectiveCommentsCount(article)
                val embeddedCount = article.commentsSource
                        ?.let { newsRepository.countCommentNodesInSource(it) }
                        ?: 0
                if (expected > embeddedCount && ArticleCommentsPagination.hasMore(embeddedCount, expected)) {
                    CommentsPaginationStrategy.HYBRID
                } else {
                    CommentsPaginationStrategy.LOCAL_SLICE
                }
            }
            else -> CommentsPaginationStrategy.SERVER_PAGE
        }
    }

    private fun reconcileCommentsCountAfterBatch(parsedCount: Int, sourceNodeCount: Int) {
        reconcileCommentsCountFromParsed(parsedCount)
        if (sourceNodeCount > parsedCount) {
            reconcileCommentsCountFromParsed(sourceNodeCount)
        }
    }

    private suspend fun resolveCommentsPageSource(article: DetailsPage, page: Int, url: String): String? {
        if (page == 1 && shouldUseEmbeddedLocalSlice(article)) {
            return article.commentsSource
        }
        return newsRepository.fetchCommentsPageSource(url, page)
    }

    private fun mergeCommentBatch(existing: Comment?, batch: Comment): Comment {
        val root = existing ?: Comment()
        val seen = flattenCommentIds(root).toMutableSet()
        batch.children.forEach { child ->
            if (child.id > 0 && seen.add(child.id)) {
                root.children.add(Comment(child))
            }
        }
        return root
    }

    private suspend fun memoryCacheHit(
            tree: Comment,
            generation: Int,
            requestId: Int,
            article: DetailsPage?
    ): CommentLoadResult {
        if (article != null) {
            return emitStoredCommentTree(
                    article = article,
                    tree = tree,
                    generation = generation,
                    requestId = requestId,
                    fromCache = true,
            )
        }
        return CommentLoadResult.Loaded(tree, fromCache = true)
    }

    private suspend fun emitStoredCommentTree(
            article: DetailsPage,
            tree: Comment,
            generation: Int,
            requestId: Int,
            fromCache: Boolean,
    ): CommentLoadResult {
        if (requestId != commentsRequestId.get()) return CommentLoadResult.Stale
        if (generation != commentsGeneration.get()) return CommentLoadResult.Stale
        val parsedCount = countParsedComments(tree)
        val expected = effectiveCommentsCount(article)
        val hasMore = ArticleCommentsPagination.hasMore(parsedCount, expected)
        commentsPaginationActive = hasMore
        article.commentTree = tree
        cachedCommentTree = tree
        cachedCommentsArticleId = article.id
        if (!hasMore) {
            commentsMemoryCache.put(article.id, tree)
        }
        _data.value = article
        _comments.emit(tree)
        FpdaDebugLog.log(
                FpdaDebugLog.TAG_COMMENTS_SECTION,
                if (fromCache) "cache_hit_reemit" else "stored_tree_reemit",
                mapOf(
                        "articleId" to article.id,
                        "requestId" to requestId,
                        "parsedCommentsCount" to parsedCount,
                        "commentsCountExpected" to expected,
                        "hasMore" to hasMore,
                )
        )
        return CommentLoadResult.Loaded(
                tree = tree,
                fromCache = fromCache,
                hasMore = hasMore,
                append = false,
                page = 1,
        )
    }

    private fun memoryCacheCommentResult(articleId: Int, expectedCount: Int = 0): Comment? {
        val tree = commentsMemoryCache.get(articleId).entry?.tree ?: return null
        if (expectedCount <= 0) return tree
        return tree.takeIf { isCommentTreeFullyLoaded(it, expectedCount) }
    }

    private suspend fun emitCachedCommentTree(
            article: DetailsPage,
            tree: Comment,
            requestId: Int,
            generation: Int
    ) {
        if (generation != commentsGeneration.get()) return
        article.commentTree = tree
        cachedCommentTree = tree
        cachedCommentsArticleId = article.id
        val parsedCount = countParsedComments(tree)
        reconcileCommentsCountFromParsed(parsedCount)
        _data.value = article
        _comments.emit(tree)
        CommentsTrace.log(
                articleId = article.id,
                requestId = requestId,
                generation = generation,
                phase = "cache_hit",
                commentsCountHint = article.commentsCount,
                parsedCount = parsedCount,
                state = "Loaded",
                reason = "comments_memory_cache"
        )
    }

    private suspend fun awaitActiveCommentsPrefetch(articleId: Int) {
        if (articleId <= 0) return
        val job = commentsPrefetchJob?.takeIf {
            it.isActive && commentsPrefetchArticleId == articleId
        } ?: return
        runCatching { job.join() }
    }

    private suspend fun storeParsedComments(
            article: DetailsPage,
            tree: Comment,
            requestId: Int,
            generation: Int,
            relaxRequestIdGuard: Boolean = false
    ) {
        if (generation != commentsGeneration.get()) return
        if (!relaxRequestIdGuard && requestId != commentsRequestId.get()) return
        val parsedCount = countParsedComments(tree)
        when (val validated = validateParsedComments(article, tree, parsedCount)) {
            is CommentValidation.Valid -> {
                article.commentTree = tree
                cachedCommentTree = tree
                cachedCommentsArticleId = article.id
                commentsMemoryCache.put(article.id, tree)
                reconcileCommentsCountFromParsed(parsedCount)
                _data.value = article
                _comments.emit(tree)
            }
            else -> Unit
        }
    }

    private suspend fun tryParseEmbeddedComments(article: DetailsPage): Comment? {
        if (!hasEmbeddedCommentList(article.commentsSource)) return null
        if (commentsSourceUnderfetches(article)) return null
        return withContext(Dispatchers.Default) {
            runCatching { newsRepository.getComments(article) }
                    .getOrNull()
                    ?.takeIf { countParsedComments(it) > 0 }
        }
    }

    private fun hasEmbeddedCommentList(source: String?): Boolean {
        val html = source.orEmpty()
        return html.contains("comment-list", ignoreCase = true) ||
                html.contains("comments-list", ignoreCase = true)
    }

    private fun shouldTryResolveCommentsSource(article: DetailsPage, forceReload: Boolean): Boolean {
        if (forceReload) return true
        if (article.commentsCount > 0) return true
        if (initData.hintCommentsCount > 0) return true
        if (initData.commentId > 0) return true
        return false
    }

    private fun resolveArticleFetchUrl(article: DetailsPage): String? =
            article.url?.takeIf { it.isNotBlank() }
                    ?: article.id.takeIf { it > 0 }?.let { "https://4pda.to/index.php?p=$it" }
                    ?: initData.newsUrl?.takeIf { it.isNotBlank() }

    /**
     * Some phase-1 articles ship without a comments UL; refetch metadata before parse.
     */
    private suspend fun ensureCommentsSource(article: DetailsPage, reason: String) {
        if (!article.commentsSource.isNullOrBlank()) return
        val desktop = article.desktopCommentsSource?.takeIf { it.isNotBlank() }
        if (desktop != null) {
            article.commentsSource = desktop
            FpdaDebugLog.log(
                    FpdaDebugLog.TAG_COMMENTS_SECTION,
                    "comments_source_from_desktop",
                    mapOf("articleId" to article.id, "reason" to reason)
            )
            return
        }
        val deferredMerged = mergeDeferredExtrasIntoArticle(article)
        if (!article.commentsSource.isNullOrBlank()) {
            FpdaDebugLog.log(
                    FpdaDebugLog.TAG_COMMENTS_SECTION,
                    "comments_source_from_deferred_extras",
                    mapOf("articleId" to article.id, "reason" to reason)
            )
            return
        }
        if (deferredMerged) {
            FpdaDebugLog.log(
                    FpdaDebugLog.TAG_COMMENTS_SECTION,
                    "comments_source_still_missing_after_deferred",
                    mapOf("articleId" to article.id, "reason" to reason)
            )
            return
        }
        val url = resolveArticleFetchUrl(article) ?: run {
            FpdaDebugLog.log(
                    FpdaDebugLog.TAG_COMMENTS_SECTION,
                    "comments_source_resolve_skipped",
                    mapOf("articleId" to article.id, "reason" to "no_url")
            )
            return
        }
        FpdaDebugLog.log(
                FpdaDebugLog.TAG_COMMENTS_SECTION,
                "comments_source_refetch_start",
                mapOf("articleId" to article.id, "reason" to reason, "url" to FpdaDebugLog.sanitizeUrl(url))
        )
        withContext(Dispatchers.IO) {
            runCatching {
                val fetch = newsRepository.fetchArticleDetails(url, ArticleParsePhase.FULL, bypassCache = false)
                val enriched = newsRepository.enrichDesktopExtras(fetch)
                newsRepository.enrichArticleMetadata(enriched, fetch.rawBody)
                enriched
            }
        }.onSuccess { enriched ->
            if (enriched.id > 0 && (article.id <= 0 || enriched.id == article.id)) {
                ArticleDeferredExtrasMerger.applyMetadata(article, enriched)
                applyCommentsCountHint(article)
                if (article.id <= 0 && enriched.id > 0) {
                    article.id = enriched.id
                }
                _data.value?.takeIf { it.id == article.id }?.let { current ->
                    ArticleDeferredExtrasMerger.applyMetadata(current, enriched)
                    applyCommentsCountHint(current)
                    _data.value = current
                }
            }
            FpdaDebugLog.log(
                    FpdaDebugLog.TAG_COMMENTS_SECTION,
                    "network_success",
                    mapOf(
                            "articleId" to article.id,
                            "commentsUrlSanitized" to FpdaDebugLog.sanitizeUrl(url),
                            "responseSizeBytes" to enriched.commentsSource?.length,
                            "commentsCountExpected" to enriched.commentsCount,
                            "hasSource" to !article.commentsSource.isNullOrBlank()
                    )
            )
            FpdaDebugLog.log(
                    FpdaDebugLog.TAG_COMMENTS_SECTION,
                    "comments_source_refetch_done",
                    mapOf(
                            "articleId" to article.id,
                            "reason" to reason,
                            "hasSource" to !article.commentsSource.isNullOrBlank(),
                            "commentsCount" to article.commentsCount
                    )
            )
        }.onFailure { error ->
            FpdaDebugLog.log(
                    FpdaDebugLog.TAG_COMMENTS_SECTION,
                    "comments_source_refetch_failed",
                    mapOf(
                            "articleId" to article.id,
                            "reason" to reason,
                            "error" to error.message,
                            "errorClass" to FpdaDebugLog.errorClass(error)
                    )
            )
        }
    }

    /**
     * Fetches the desktop-rendered comment list when the article's own count is positive but the
     * mobile source carries no comment node (lazy-loaded shell). Overwrites an empty shell with the
     * real comment nodes so the parser can build the tree instead of resolving to a false Empty.
     */
    private suspend fun ensureDesktopCommentsSource(article: DetailsPage, reason: String) {
        if (effectiveCommentsCount(article) <= 0) return
        mergeDeferredExtrasIntoArticle(article)
        if (newsRepository.hasCommentNodeMarkup(article.commentsSource) &&
                !commentsSourceUnderfetches(article)) {
            return
        }
        val existingDesktop = article.desktopCommentsSource
                ?: _data.value?.takeIf { it.id == article.id }?.desktopCommentsSource
        if (!existingDesktop.isNullOrBlank() && newsRepository.hasCommentNodeMarkup(existingDesktop)) {
            val expected = effectiveCommentsCount(article)
            if (!newsRepository.commentsSourceUnderfetchesExpected(existingDesktop, expected)) {
                article.commentsSource = existingDesktop
                article.desktopCommentsSource = existingDesktop
                FpdaDebugLog.log(
                        FpdaDebugLog.TAG_COMMENTS_SECTION,
                        "desktop_comments_from_existing",
                        mapOf("articleId" to article.id, "reason" to reason)
                )
                return
            }
            FpdaDebugLog.log(
                    FpdaDebugLog.TAG_COMMENTS_SECTION,
                    "desktop_comments_existing_underfetch",
                    mapOf(
                            "articleId" to article.id,
                            "reason" to reason,
                            "commentsCountExpected" to expected,
                            "existingNodes" to newsRepository.countCommentNodesInSource(existingDesktop)
                    )
            )
            article.desktopCommentsSource = null
        }
        val url = resolveArticleFetchUrl(article) ?: run {
            FpdaDebugLog.log(
                    FpdaDebugLog.TAG_COMMENTS_SECTION,
                    "desktop_comments_resolve_skipped",
                    mapOf("articleId" to article.id, "reason" to "no_url")
            )
            return
        }
        FpdaDebugLog.log(
                FpdaDebugLog.TAG_COMMENTS_SECTION,
                "desktop_comments_fetch_start",
                mapOf(
                        "articleId" to article.id,
                        "reason" to reason,
                        "url" to FpdaDebugLog.sanitizeUrl(url),
                        "commentsCountExpected" to article.commentsCount
                )
        )
        val desktopSource = fetchDesktopCommentsSourceDeduped(article.id, url)
        if (!desktopSource.isNullOrBlank() && newsRepository.hasCommentNodeMarkup(desktopSource)) {
            article.commentsSource = desktopSource
            article.desktopCommentsSource = desktopSource
            FpdaDebugLog.log(
                    FpdaDebugLog.TAG_COMMENTS_SECTION,
                    "desktop_comments_fetch_done",
                    mapOf(
                            "articleId" to article.id,
                            "reason" to reason,
                            "sourceLen" to desktopSource.length,
                            "commentsCountExpected" to article.commentsCount
                    )
            )
        } else {
            FpdaDebugLog.log(
                    FpdaDebugLog.TAG_COMMENTS_SECTION,
                    "desktop_comments_fetch_empty",
                    mapOf(
                            "articleId" to article.id,
                            "reason" to reason,
                            "hasSource" to !desktopSource.isNullOrBlank(),
                            "commentsCountExpected" to article.commentsCount
                    )
            )
        }
    }

    private suspend fun mergeDeferredExtrasIntoArticle(article: DetailsPage): Boolean {
        if (deferredExtrasJob?.isActive != true || deferredExtrasArticleId != article.id) {
            return false
        }
        runCatching { deferredExtrasJob?.join() }
        _data.value?.takeIf { it.id == article.id }?.let { current ->
            ArticleDeferredExtrasMerger.applyMetadata(article, current)
            applyCommentsCountHint(article)
            return true
        }
        return false
    }

    private suspend fun fetchDesktopCommentsSourceDeduped(articleId: Int, url: String): String? {
        if (articleId > 0 &&
                desktopCommentsFetchJob?.isActive == true &&
                desktopCommentsFetchArticleId == articleId
        ) {
            runCatching { desktopCommentsFetchJob?.join() }
            val expected = _data.value?.takeIf { it.id == articleId }?.let { effectiveCommentsCount(it) } ?: 0
            return _data.value?.takeIf { it.id == articleId }?.desktopCommentsSource
                    ?.takeIf { newsRepository.hasCommentNodeMarkup(it) }
                    ?.takeIf { expected <= 0 || !newsRepository.commentsSourceUnderfetchesExpected(it, expected) }
        }
        if (articleId > 0) {
            desktopCommentsFetchArticleId = articleId
        }
        return withContext(Dispatchers.IO) {
            runCatching { newsRepository.loadDesktopCommentsSource(url) }.getOrNull()
        }.also {
            if (desktopCommentsFetchArticleId == articleId) {
                desktopCommentsFetchArticleId = -1
            }
            desktopCommentsFetchJob = null
        }
    }

    private suspend fun updateData(
            article: DetailsPage,
            loadComments: Boolean,
            generation: Int = articleGeneration.get(),
            requestId: Int = commentsRequestId.get()
    ) {
        val countBeforeHint = article.commentsCount
        applyCommentsCountHint(article)
        if (article.commentsCount > 0 &&
                article.commentsCount != countBeforeHint &&
                !article.html.isNullOrBlank()
        ) {
            article.html = articleTemplate.restampCommentsCountInMappedHtml(article.html, article.commentsCount)
        }
        initData.newsId = article.id
        article.commentId = initData.commentId
        preserveCommentsForSameArticle(article)
        val previousCount = _data.value?.takeIf { it.id == article.id }?.commentsCount ?: -1
        _data.value = article
        notifyListCommentsCountIfChanged(
                article.id,
                effectiveCommentsCount(article),
                previousCount
        )
        CommentsTrace.log(
                articleId = article.id,
                requestId = requestId,
                generation = generation,
                phase = "article_updated",
                commentsCountHint = article.commentsCount,
                parsedCount = article.commentTree?.let { countParsedComments(it) } ?: -1,
                state = if (article.commentTree != null) "Loaded" else "NotLoaded",
                reason = if (loadComments) "load_with_comments" else "article_only"
        )
        if (loadComments) {
            parseComments(article, commentsGeneration.get(), requestId, forceReload = true)
        }
    }

    private suspend fun parseComments(
            article: DetailsPage,
            generation: Int,
            requestId: Int,
            forceReload: Boolean
    ): CommentLoadResult {
        FpdaDebugLog.log(
                FpdaDebugLog.TAG_COMMENTS_SECTION,
                "parse_started",
                mapOf(
                        "articleId" to article.id,
                        "requestId" to requestId,
                        "commentsCountExpected" to article.commentsCount,
                        "commentsUrlSanitized" to FpdaDebugLog.sanitizeUrl(resolveArticleFetchUrl(article)),
                        "hasSource" to !article.commentsSource.isNullOrBlank(),
                        "forceReload" to forceReload
                )
        )
        CommentsTrace.log(
                articleId = article.id,
                requestId = requestId,
                generation = generation,
                phase = "parse_start",
                commentsCountHint = article.commentsCount,
                state = "Loading",
                reason = if (forceReload) "force" else "lazy"
        )
        // A non-blank comments source that carries no actual comment node (an empty/collapsed
        // comment-list shell shipped on the phase-1 page) must not suppress the (re)fetch of a
        // fuller source. Drop it so the resolver below can fetch the desktop/FULL comments.
        // A partial lazy-loaded batch (a few real nodes, count still below the badge) is treated
        // the same way — see ensureDesktopCommentsSource / commentsSourceUnderfetches.
        if (effectiveCommentsCount(article) > 0 &&
                !article.commentsSource.isNullOrBlank() &&
                !newsRepository.hasCommentNodeMarkup(article.commentsSource)) {
            FpdaDebugLog.log(
                    FpdaDebugLog.TAG_COMMENTS_SECTION,
                    "comments_source_shell_dropped",
                    mapOf(
                            "articleId" to article.id,
                            "requestId" to requestId,
                            "commentsCountExpected" to article.commentsCount,
                            "sourceLen" to article.commentsSource?.length
                    )
            )
            article.commentsSource = null
        }
        if (effectiveCommentsCount(article) > 0 && commentsSourceUnderfetches(article)) {
            ensureDesktopCommentsSource(article, if (forceReload) "force_reload" else "lazy_load")
        }
        if (article.commentsSource.isNullOrBlank() &&
                shouldTryResolveCommentsSource(article, forceReload) &&
                !commentsSourceUnderfetches(article)) {
            ensureCommentsSource(article, if (forceReload) "force_reload" else "lazy_load")
        }
        if (effectiveCommentsCount(article) > 0 && commentsSourceUnderfetches(article)) {
            ensureDesktopCommentsSource(article, "metadata_after_refetch")
        }
        return try {
            var tree = newsRepository.getComments(article)
            var parsedCount = countParsedComments(tree)
            if (parsedCommentsUnderfetchExpected(parsedCount, effectiveCommentsCount(article)) &&
                    commentsSourceUnderfetches(article)) {
                ensureDesktopCommentsSource(article, "parse_underfetch_retry")
                tree = newsRepository.getComments(article)
                parsedCount = countParsedComments(tree)
            }
            if (parsedCount == 0 && article.commentsCount > 0 && newsRepository.rebalanceCommentsSource(article)) {
                FpdaDebugLog.log(
                        FpdaDebugLog.TAG_COMMENTS_SECTION,
                        "parse_retry_balanced_ul",
                        mapOf(
                                "articleId" to article.id,
                                "requestId" to requestId,
                                "commentsCountExpected" to article.commentsCount,
                                "sourceLen" to article.commentsSource?.length
                        )
                )
                tree = newsRepository.getComments(article)
                parsedCount = countParsedComments(tree)
            }
            // RequestId guard: older in-flight parse must not overwrite newer request in the same generation.
            if (requestId != commentsRequestId.get()) {
                StateRaceTrace.log(
                        domain = "article_comments",
                        event = "stale_ignored",
                        requestId = requestId,
                        currentGeneration = commentsGeneration.get(),
                        articleId = article.id,
                        reason = "requestId_guard"
                )
                return CommentLoadResult.Stale
            }
            if (generation != commentsGeneration.get()) {
                CommentLoadResult.Stale
            } else {
                when (val validated = validateParsedComments(article, tree, parsedCount)) {
                    is CommentValidation.Valid -> {
                        article.commentTree = tree
                        cachedCommentTree = tree
                        cachedCommentsArticleId = article.id
                        commentsMemoryCache.put(article.id, tree)
                        _data.value = article
                        _comments.emit(tree)
                        FpdaDebugLog.log(
                                FpdaDebugLog.TAG_COMMENTS_SECTION,
                                "parse_success",
                                mapOf(
                                        "articleId" to article.id,
                                        "requestId" to requestId,
                                        "parsedCommentsCount" to parsedCount,
                                        "commentsCountExpected" to article.commentsCount,
                                        "responseSizeBytes" to article.commentsSource?.length
                                )
                        )
                        CommentsTrace.log(
                                articleId = article.id,
                                requestId = requestId,
                                generation = generation,
                                phase = "parse_success",
                                commentsCountHint = article.commentsCount,
                                parsedCount = parsedCount,
                                state = "Loaded",
                                reason = "ok"
                        )
                        CommentLoadResult.Loaded(tree, fromCache = false)
                    }
                    is CommentValidation.Empty -> {
                        FpdaDebugLog.log(
                                FpdaDebugLog.TAG_COMMENTS_SECTION,
                                "parse_empty",
                                mapOf(
                                        "articleId" to article.id,
                                        "requestId" to requestId,
                                        "parsedCommentsCount" to parsedCount,
                                        "commentsCountExpected" to article.commentsCount,
                                        "errorMessage" to validated.reason
                                )
                        )
                        CommentsTrace.log(
                                articleId = article.id,
                                requestId = requestId,
                                generation = generation,
                                phase = "parse_empty",
                                commentsCountHint = article.commentsCount,
                                parsedCount = parsedCount,
                                state = "Empty",
                                reason = validated.reason
                        )
                        CommentLoadResult.Empty(validated.reason)
                    }
                    is CommentValidation.Invalid -> {
                        FpdaDebugLog.log(
                                FpdaDebugLog.TAG_COMMENTS_SECTION,
                                "parse_error",
                                mapOf(
                                        "articleId" to article.id,
                                        "requestId" to requestId,
                                        "parsedCommentsCount" to parsedCount,
                                        "commentsCountExpected" to article.commentsCount,
                                        "errorMessage" to validated.reason,
                                        "hasCommentListClass" to article.commentsSource.orEmpty()
                                                .contains("comment-list", ignoreCase = true),
                                        "hasCommentIdMarker" to article.commentsSource.orEmpty()
                                                .contains("comment-", ignoreCase = true),
                                        "sourceLen" to article.commentsSource?.length,
                                        "hasDesktopSource" to !article.desktopCommentsSource.isNullOrBlank()
                                )
                        )
                        CommentsTrace.log(
                                articleId = article.id,
                                requestId = requestId,
                                generation = generation,
                                phase = "parse_rejected",
                                commentsCountHint = article.commentsCount,
                                parsedCount = parsedCount,
                                state = "Error",
                                reason = validated.reason
                        )
                        CommentLoadResult.Error(IllegalStateException(validated.reason))
                    }
                }
            }
        } catch (it: Throwable) {
            Timber.e(it, "Article interactor comments error")
            FpdaDebugLog.log(
                    FpdaDebugLog.TAG_COMMENTS_SECTION,
                    "parse_error",
                    mapOf(
                            "articleId" to article.id,
                            "requestId" to requestId,
                            "commentsCountExpected" to article.commentsCount,
                            "errorClass" to FpdaDebugLog.errorClass(it),
                            "errorMessage" to it.message
                    )
            )
            CommentsTrace.log(
                    articleId = article.id,
                    requestId = requestId,
                    generation = generation,
                    phase = "parse_failure",
                    commentsCountHint = article.commentsCount,
                    state = "Error",
                    reason = it.message.orEmpty()
            )
            CommentLoadResult.Error(it)
        }
    }

    private fun countParsedComments(tree: Comment): Int = flattenComments(tree).size

    private fun validateParsedComments(
            article: DetailsPage,
            tree: Comment,
            parsedCount: Int,
            paginated: Boolean = false,
    ): CommentValidation {
        if (parsedCount > 0) {
            if (paginated) {
                return CommentValidation.Valid
            }
            val ownCount = effectiveCommentsCount(article)
            if (parsedCommentsUnderfetchExpected(parsedCount, ownCount)) {
                return CommentValidation.Invalid("comments_partial_batch_unresolved")
            }
            return CommentValidation.Valid
        }
        // Trust the article's OWN comment-count badge (kept clamped from related/popular-widget
        // inflation by reconcileCommentsCountFromParsed). A positive own count means the article
        // really has comments, even if the phase-1 mobile page only shipped an empty lazy shell.
        val ownCount = effectiveCommentsCount(article)
        val source = article.commentsSource.orEmpty()
        if (source.isBlank()) {
            return if (ownCount > 0) {
                CommentValidation.Invalid("comments_count_positive_but_no_source")
            } else {
                CommentValidation.Empty("no_comments_source")
            }
        }
        if (ownCount > 0 && source.length > 24) {
            // Only a source that actually carries comment nodes (ids/data-comment) yet parses to
            // zero is a genuine parser failure.
            if (newsRepository.hasCommentNodeMarkup(source)) {
                return CommentValidation.Invalid("comments_html_present_but_parse_empty")
            }
            // An empty/collapsed comment-list shell WITH a positive own count is NOT an empty
            // article: 4pda's mobile phase-1 page ships an empty shell even for articles with
            // hundreds of comments (they are lazy-loaded). The desktop comment list was supposed
            // to be fetched (ensureDesktopCommentsSource); if we still have no nodes the real
            // comments could not be loaded, so surface a retryable failure — NOT a clean empty.
            return CommentValidation.Invalid("comment_list_shell_unresolved_positive_count")
        }
        // Own count is 0 (or unknown) and nothing parsed: the article genuinely has no comments.
        return CommentValidation.Empty("parsed_zero_with_hint_${article.commentsCount}")
    }

    private sealed class CommentValidation {
        object Valid : CommentValidation()
        data class Empty(val reason: String) : CommentValidation()
        data class Invalid(val reason: String) : CommentValidation()
    }

    sealed class CommentLoadResult {
        data class Loaded(
                val tree: Comment,
                val fromCache: Boolean,
                /** True when more comment pages remain on the server. */
                val hasMore: Boolean = false,
                /** True when this result appends a batch to an already displayed list. */
                val append: Boolean = false,
                val page: Int = 1,
        ) : CommentLoadResult()
        data class Empty(val reason: String = "") : CommentLoadResult()
        data class Error(val throwable: Throwable) : CommentLoadResult()
        object Stale : CommentLoadResult()
    }

    data class InitData(
            var newsUrl: String? = null,
            var newsId: Int = -1,
            var commentId: Int = -1,
            var hintCommentsCount: Int = 0,
            var openSource: String = "news_list"
    )

    private companion object {
        /** Defer FULL desktop refetch until after first WebView paint when hint patch suffices for open. */
        const val BACKGROUND_DEFERRED_EXTRAS_DELAY_MS = 4_000L
    }
}
