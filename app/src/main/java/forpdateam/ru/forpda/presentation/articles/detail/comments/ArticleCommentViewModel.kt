package forpdateam.ru.forpda.presentation.articles.detail.comments

import androidx.lifecycle.ViewModel
import forpdateam.ru.forpda.R
import forpdateam.ru.forpda.presentation.BaseViewModel
import androidx.lifecycle.ViewModelProvider

import forpdateam.ru.forpda.entity.remote.news.Comment
import forpdateam.ru.forpda.model.AuthHolder
import forpdateam.ru.forpda.model.interactors.news.ArticleInteractor
import forpdateam.ru.forpda.model.data.remote.api.news.stripNewsCommentEditedMarker
import forpdateam.ru.forpda.presentation.IErrorHandler
import forpdateam.ru.forpda.presentation.ILinkHandler
import forpdateam.ru.forpda.presentation.articles.detail.comments.ArticleCommentsPagination
import forpdateam.ru.forpda.presentation.TabRouter
import forpdateam.ru.forpda.ui.fragments.news.details.ArticleCommentActionVisibility
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import forpdateam.ru.forpda.diagnostic.FpdaDebugLog
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.ArrayList
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicInteger

class ArticleCommentViewModel(
        private val articleInteractor: ArticleInteractor,
        private val router: TabRouter,
        private val linkHandler: ILinkHandler,
        private val authHolder: AuthHolder,
        private val errorHandler: IErrorHandler
) : BaseViewModel() {

    private var subscriptionsStarted = false
    private val loadMutex = Mutex()
    private val loadRequestId = AtomicInteger(0)
    private var boundArticleId: Int = -1
    private var staleLoadRetryUsed = false
    private var loadingTimeoutJob: Job? = null
    private var loadingMoreTimeoutJob: Job? = null
    private var commentsPrefetchStarted = false

    private var firstShow: Boolean = true
    private var deepLinkReloadTried: Boolean = false
    private var deepLinkScrollArmed: Boolean = false
    private var deepLinkPaginateCount: Int = 0
    // «Потолок» догрузки: сервер повторяет ту же партию и больше не отдаёт новых комментов, хотя
    // expectedCount>rendered (фантомный «Показать ещё (N)»). Ставится, когда loadMore не дал прироста.
    private var loadMoreCeilingReached: Boolean = false
    private var allComments = ArrayList<Comment>()
    private var serverHasMore: Boolean = false
    private var loadingMoreComments: Boolean = false
    // Size of the loaded list captured the moment a load-more starts, BEFORE the batch is applied.
    // The interactor both emits the merged tree to the comments Flow AND returns it, so a load-more
    // batch is applied twice; comparing the post-load size against the live [allComments] (which the
    // first application already grew) made the second application see "no growth" and falsely trip
    // the ceiling — stranding the reader at ~40/N comments. Comparing against this stable baseline
    // is immune to the double-apply.
    private var loadMoreBaselineSize: Int = 0
    private val pendingLikeCommentIds = mutableSetOf<Int>()
    private val _commentsState = MutableStateFlow<ArticleCommentsState>(ArticleCommentsState.NotLoaded)
    val commentsState: StateFlow<ArticleCommentsState> = _commentsState.asStateFlow()

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    private val _sendRefreshing = MutableStateFlow(false)
    val sendRefreshing: StateFlow<Boolean> = _sendRefreshing.asStateFlow()

    private val _messageFieldVisible = MutableStateFlow(false)
    val messageFieldVisible: StateFlow<Boolean> = _messageFieldVisible.asStateFlow()

    private val _uiEvents = MutableSharedFlow<ArticleCommentUiEvent>()
    val uiEvents: SharedFlow<ArticleCommentUiEvent> = _uiEvents.asSharedFlow()

    /** Clears in-memory comments when the WebView shows a different article. */
    fun onArticleChanged(articleId: Int) {
        if (articleId <= 0 || articleId == boundArticleId) return
        boundArticleId = articleId
        staleLoadRetryUsed = false
        commentsPrefetchStarted = false
        cancelLoadingTimeout()
        cancelLoadingMoreTimeout()
        allComments = ArrayList()
        serverHasMore = false
        loadingMoreComments = false
        loadMoreBaselineSize = 0
        firstShow = true
        deepLinkReloadTried = false
        deepLinkScrollArmed = false
        deepLinkPaginateCount = 0
        loadMoreCeilingReached = false
        _commentsState.value = ArticleCommentsState.NotLoaded
        FpdaDebugLog.log(
                FpdaDebugLog.TAG_COMMENTS_SECTION,
                "viewmodel_reset",
                mapOf("articleId" to articleId)
        )
    }

    fun start() {
        if (subscriptionsStarted) return
        subscriptionsStarted = true
        scope.launch {
            articleInteractor.observeComments()
                    .catch { errorHandler.handle(it) }
                    .collect { comment ->
                        val previousAllCount = allComments.size
                        val list = commentsToList(comment)
                        when (val current = _commentsState.value) {
                            is ArticleCommentsState.Loading -> {
                                publishVisibleComments()
                                val scrollTargetId = articleInteractor.takePendingScrollCommentId()
                                val deepLinkCommentId = articleInteractor.initData.commentId
                                val revealSection = scrollTargetId > 0 ||
                                        (deepLinkCommentId > 0 && firstShow)
                                if (revealSection) {
                                    _uiEvents.emit(
                                            ArticleCommentUiEvent.ShowComments(
                                                    ArrayList(list),
                                                    scrollTargetId,
                                                    revealSection = true
                                            )
                                    )
                                }
                                if (scrollTargetId > 0) {
                                    val index = list.indexOfFirst { it.id == scrollTargetId }
                                    if (index >= 0) {
                                        _uiEvents.emit(ArticleCommentUiEvent.ScrollToComment(index, scrollTargetId))
                                    }
                                }
                                if (firstShow) {
                                    val targetCommentId = articleInteractor.initData.commentId
                                    if (targetCommentId > 0) {
                                        val index = list.indexOfFirst { it.id == targetCommentId }
                                        if (index >= 0) {
                                            _uiEvents.emit(ArticleCommentUiEvent.ScrollToComment(index, targetCommentId))
                                        }
                                    }
                                    firstShow = false
                                }
                            }
                            is ArticleCommentsState.Loaded -> {
                                // A reply/comment posted while comments are already Loaded arrives here
                                // (not in the Loading branch). Consume the pending scroll target first: a
                                // post is never a "spurious replay", so it must not be suppressed by the
                                // bulk-replay guard, and the freshly posted comment must be published.
                                val scrollTargetId = articleInteractor.takePendingScrollCommentId()
                                val postedByUser = scrollTargetId > 0
                                val spuriousBulkReplay = !loadingMoreComments && !postedByUser &&
                                        list.size > previousAllCount + InlineCommentsBatchConfig.BATCH_SIZE
                                if (spuriousBulkReplay) {
                                    FpdaDebugLog.log(
                                            FpdaDebugLog.TAG_COMMENTS_SECTION,
                                            "tree_replay_ignored",
                                            mapOf(
                                                    "articleId" to boundArticleId,
                                                    "replayCount" to list.size,
                                                    "visibleCount" to current.comments.size,
                                            )
                                    )
                                } else if (list.size > previousAllCount) {
                                    publishVisibleComments(appendFromIndex = previousAllCount)
                                } else if (current.comments.size != list.size ||
                                        current.canLoadMore != serverHasMore
                                ) {
                                    publishVisibleComments(appendFromIndex = 0)
                                }
                                if (scrollTargetId > 0) {
                                    // Emit even when the id is not in this list snapshot yet — the
                                    // fragment arms the target by comment id and re-asserts the scroll on
                                    // every subsequent render until the node exists in the DOM.
                                    val index = list.indexOfFirst { it.id == scrollTargetId }
                                    _uiEvents.emit(
                                            ArticleCommentUiEvent.ScrollToComment(index, scrollTargetId)
                                    )
                                }
                            }
                            else -> {
                                val deepLinkCommentId = articleInteractor.initData.commentId
                                if (deepLinkCommentId > 0 && firstShow && list.isNotEmpty()) {
                                    publishVisibleComments()
                                    _uiEvents.emit(
                                            ArticleCommentUiEvent.ShowComments(
                                                    ArrayList(list),
                                                    deepLinkCommentId,
                                                    revealSection = true
                                            )
                                    )
                                    val index = list.indexOfFirst { it.id == deepLinkCommentId }
                                    if (index >= 0) {
                                        _uiEvents.emit(ArticleCommentUiEvent.ScrollToComment(index, deepLinkCommentId))
                                    }
                                    firstShow = false
                                } else {
                                    FpdaDebugLog.log(
                                            FpdaDebugLog.TAG_COMMENTS_SECTION,
                                            "tree_cached_silent",
                                            mapOf(
                                                    "articleId" to boundArticleId,
                                                    "parsedCount" to list.size,
                                                    "state" to current::class.java.simpleName
                                            )
                                    )
                                }
                            }
                        }
                        maybeArmDeepLinkCommentScroll(list)
                        ensureDeepLinkCommentLoaded(list)
                    }
        }

        // Двигаем последовательную догрузку deep-link комментов по завершении каждой загрузки: emit из
        // observeComments может прийти, пока идёт загрузка (_refreshing=true) и шаг пропустится — а этот
        // коллектор ловит переход refreshing→false и запускает следующий шаг (форс-релоад → пагинация).
        scope.launch {
            _refreshing.collect { isRefreshing ->
                if (!isRefreshing) {
                    (_commentsState.value as? ArticleCommentsState.Loaded)?.let { loaded ->
                        ensureDeepLinkCommentLoaded(loaded.comments)
                    }
                }
            }
        }

        scope.launch {
            authHolder.observe().collect {
                _messageFieldVisible.value = it.isAuth()
            }
        }
    }

    /**
     * Opening a news article from a mention/deep-link can hit a stale cached comment tree whose
     * count already looks "complete", so the underfetch guard won't refetch and the freshly posted
     * comment that mentioned the user is simply missing. If the deep-link target comment isn't in
     * the loaded list, force one fresh load and arm the scroll so it lands once it renders. One-shot
     * per article (see [deepLinkReloadTried]) to avoid loops when the comment is genuinely gone.
     */
    /**
     * Center the deep-link/mention target comment the first time it actually appears in the loaded
     * list — regardless of which batch or collect emit surfaced it. The `Loading`+`firstShow` path
     * only fires when the target is already in the very first snapshot, and
     * the old force-reload path armed a scroll only when the target was *missing*; a
     * target that loads in a later batch (or was present all along after a slow load) was therefore
     * never scrolled to, leaving the reader parked at the top of the comments section instead of on
     * the comment that mentioned them. Emitting [ArticleCommentUiEvent.ScrollToComment] here (once,
     * guarded by [deepLinkScrollArmed]) lets the fragment arm + re-assert the scroll until the node
     * renders. No-op when there is no deep-link target.
     */
    private fun maybeArmDeepLinkCommentScroll(list: List<Comment>) {
        if (deepLinkScrollArmed) return
        val targetCommentId = articleInteractor.initData.commentId
        if (targetCommentId <= 0) return
        val index = list.indexOfFirst { it.id == targetCommentId }
        if (index < 0) return
        deepLinkScrollArmed = true
        FpdaDebugLog.log(
                FpdaDebugLog.TAG_COMMENTS_SECTION,
                "deeplink_comment_scroll_armed",
                mapOf(
                        "articleId" to boundArticleId,
                        "targetCommentId" to targetCommentId,
                        "index" to index,
                )
        )
        scope.launch { _uiEvents.emit(ArticleCommentUiEvent.ScrollToComment(index, targetCommentId)) }
    }

    /**
     * Единый последовательный загрузчик комментов при заходе из УПОМИНАНИЯ (deep-link commentId > 0).
     *
     * Раньше это делали два конкурирующих метода (форс-релоад + пагинация «до цели»), и они не работали:
     * ⓐ форс-релоад и пагинация стартовали в ОДНОМ emit и мешали друг другу (форс-релоад ресетил
     *    пагинацию, которую тут же дёргала пагинация);
     * ⓑ пагинация останавливалась, как только «цель» найдена — а якорь news-упоминания часто указывает
     *    на РОДИТЕЛЬСКИЙ коммент (он уже на первой странице), НЕ на сам ответ. Ответ же — самый новый,
     *    лежит в непрогруженном хвосте (грузится 20 из 31), поэтому «цель найдена» → стоп → ответа нет.
     *
     * Теперь строго по шагам (по одному действию за emit, гейт по флагам загрузки, чтобы не наслаивать):
     *  1) один раз — форс-релоад свежей первой страницы (ответ новый, кэш мог быть устаревшим);
     *  2) затем — догружаем ВЕСЬ тред (`loadMoreComments`, пока `canLoadMore`), НЕ останавливаясь на
     *     «цели», до [MAX_DEEPLINK_PAGINATE] страниц. Так ответ гарантированно попадёт в список,
     *     где бы он ни был, а [maybeArmDeepLinkCommentScroll] прокрутит к нему, когда он появится.
     */
    private fun ensureDeepLinkCommentLoaded(list: List<Comment>) {
        val targetCommentId = articleInteractor.initData.commentId
        if (targetCommentId <= 0) return
        val current = _commentsState.value as? ArticleCommentsState.Loaded ?: return
        if (loadingMoreComments || _refreshing.value) return
        if (!deepLinkReloadTried) {
            deepLinkReloadTried = true
            // Цель-ответ УЖЕ в загруженном дереве (кэш/первая страница его содержат) — НЕ делаем
            // force-reload. Раньше он срабатывал безусловно: сбрасывал только что показанные
            // комменты обратно в Loading и перезагружал ВСЁ заново. На медленной сети это
            // десятки секунд «пустой новости без ответа» — из-за чего казалось, что ответ
            // «не показывается». Скролл к цели армит maybeArmDeepLinkCommentScroll.
            if (list.any { it.id == targetCommentId }) {
                FpdaDebugLog.log(
                        FpdaDebugLog.TAG_COMMENTS_SECTION,
                        "deeplink_comment_already_present",
                        mapOf("articleId" to boundArticleId, "targetCommentId" to targetCommentId, "loadedCount" to list.size)
                )
                return
            }
            FpdaDebugLog.log(
                    FpdaDebugLog.TAG_COMMENTS_SECTION,
                    "deeplink_comment_force_reload",
                    mapOf("articleId" to boundArticleId, "targetCommentId" to targetCommentId, "loadedCount" to list.size)
            )
            scope.launch { _uiEvents.emit(ArticleCommentUiEvent.ScrollToComment(-1, targetCommentId)) }
            loadCommentsIfNeeded(forceReload = true)
            return
        }
        if (current.canLoadMore && deepLinkPaginateCount < MAX_DEEPLINK_PAGINATE) {
            deepLinkPaginateCount++
            FpdaDebugLog.log(
                    FpdaDebugLog.TAG_COMMENTS_SECTION,
                    "deeplink_comment_paginate",
                    mapOf(
                            "articleId" to boundArticleId,
                            "targetCommentId" to targetCommentId,
                            "loadedCount" to list.size,
                            "attempt" to deepLinkPaginateCount,
                            "hasTarget" to list.any { it.id == targetCommentId },
                    )
            )
            loadMoreComments()
        }
    }

    fun loadMoreComments() {
        val current = _commentsState.value
        if (current !is ArticleCommentsState.Loaded) return
        if (!current.canLoadMore || loadingMoreComments) {
            FpdaDebugLog.log(
                    FpdaDebugLog.TAG_COMMENTS_SECTION,
                    "load_more_blocked",
                    mapOf(
                            "articleId" to boundArticleId,
                            "reason" to when {
                                loadingMoreComments -> "loading_more"
                                !current.canLoadMore -> "cannot_load_more"
                                else -> "unknown"
                            },
                            "loadedCount" to current.comments.size,
                            "expectedCount" to articleInteractor.expectedCommentsCount(),
                    )
            )
            scope.launch { _uiEvents.emit(ArticleCommentUiEvent.RefreshLoadMoreUi) }
            return
        }
        val requestId = loadRequestId.incrementAndGet()
        scope.launch {
            loadMutex.withLock {
                if (requestId != loadRequestId.get()) return@withLock
                try {
                    loadingMoreComments = true
                    loadMoreBaselineSize = allComments.size
                    _refreshing.value = true
                    scheduleLoadingMoreTimeout(requestId)
                    FpdaDebugLog.log(
                            FpdaDebugLog.TAG_COMMENTS_SECTION,
                            "load_more",
                            mapOf(
                                    "articleId" to boundArticleId,
                                    "loadedCount" to allComments.size,
                                    "expectedCount" to articleInteractor.expectedCommentsCount(),
                            )
                    )
                    applyCommentLoadResult(articleInteractor.loadCommentsNextPage(), requestId)
                } catch (e: Throwable) {
                    cancelLoadingMoreTimeout()
                    errorHandler.handle(e)
                    FpdaDebugLog.log(
                            FpdaDebugLog.TAG_COMMENTS_SECTION,
                            "load_more_error",
                            mapOf(
                                    "articleId" to boundArticleId,
                                    "error" to e.message,
                            )
                    )
                } finally {
                    cancelLoadingMoreTimeout()
                    loadingMoreComments = false
                    _refreshing.value = false
                    _uiEvents.emit(ArticleCommentUiEvent.RefreshLoadMoreUi)
                }
            }
        }
    }

    private fun publishVisibleComments(appendFromIndex: Int = 0) {
        if (allComments.isEmpty()) {
            if (_commentsState.value is ArticleCommentsState.Loading) return
            _commentsState.value = ArticleCommentsState.Empty
            return
        }
        cancelLoadingTimeout()
        val visibleCount = allComments.size
        val expectedTotal = articleInteractor.expectedCommentsCount().coerceAtLeast(visibleCount)
        val canLoadMore = !loadMoreCeilingReached &&
                (serverHasMore || ArticleCommentsPagination.hasMore(visibleCount, expectedTotal))
        _commentsState.value = ArticleCommentsState.Loaded(
                comments = ArrayList(allComments),
                canLoadMore = canLoadMore,
                totalCount = expectedTotal,
                appendFromIndex = appendFromIndex.coerceIn(0, visibleCount),
        )
    }

    fun updateComments() {
        val requestId = loadRequestId.incrementAndGet()
        scope.launch {
            loadMutex.withLock {
                if (requestId != loadRequestId.get()) return@withLock
                try {
                    _refreshing.value = true
                    if (_commentsState.value !is ArticleCommentsState.Loaded) {
                        setCommentsState(ArticleCommentsState.Loading(requestId), "load_requested")
                    }
                    staleLoadRetryUsed = false
                    scheduleLoadingTimeout(requestId)
                    runCommentLoad(requestId, forceReload = true)
                } catch (e: Throwable) {
                    cancelLoadingTimeout()
                    errorHandler.handle(e)
                    setCommentsState(ArticleCommentsState.Error(e), "network_error")
                } finally {
                    _refreshing.value = false
                }
            }
        }
    }

    /**
     * Comments load only after the user expands the section; no background warm-up.
     */
    fun prefetchCommentsIfNeeded(reason: String = "article_ready") {
        FpdaDebugLog.log(
                FpdaDebugLog.TAG_COMMENTS_SECTION,
                "prefetch_skipped",
                mapOf("articleId" to boundArticleId, "reason" to reason)
        )
    }

    fun onDeferredCommentsSourceAvailable(commentsCount: Int, hasCommentsSource: Boolean) {
        if (_commentsState.value !is ArticleCommentsState.Empty) return
        if (commentsCount <= 0 && !hasCommentsSource) return
        commentsPrefetchStarted = false
        _commentsState.value = ArticleCommentsState.NotLoaded
        FpdaDebugLog.log(
                FpdaDebugLog.TAG_COMMENTS_SECTION,
                "empty_reset_after_deferred_source",
                mapOf(
                        "articleId" to boundArticleId,
                        "commentsCount" to commentsCount,
                        "hasSource" to hasCommentsSource
                )
        )
    }

    fun loadCommentsIfNeeded(forceReload: Boolean = false) {
        val current = _commentsState.value
        if (!forceReload) {
            when (current) {
                is ArticleCommentsState.Loaded -> {
                    if (current.comments.isNotEmpty()) {
                        FpdaDebugLog.log(
                                FpdaDebugLog.TAG_COMMENTS_SECTION,
                                "tap_instant_cache",
                                mapOf(
                                        "articleId" to boundArticleId,
                                        "parsedCount" to current.comments.size,
                                        "canLoadMore" to current.canLoadMore,
                                )
                        )
                        logLoadSkipped("already_loaded")
                        return
                    }
                }
                is ArticleCommentsState.Empty -> {
                    logLoadSkipped("already_empty")
                    return
                }
                is ArticleCommentsState.Loading -> {
                    logLoadSkipped("already_loading")
                    return
                }
                is ArticleCommentsState.NotLoaded,
                is ArticleCommentsState.Error -> Unit
            }
        }
        val requestId = loadRequestId.incrementAndGet()
        scope.launch {
            loadMutex.withLock {
                if (requestId != loadRequestId.get()) {
                    logLoadSkipped("superseded_before_start")
                    return@withLock
                }
                try {
                    _refreshing.value = true
                    setCommentsState(ArticleCommentsState.Loading(requestId), "load_requested")
                    staleLoadRetryUsed = false
                    scheduleLoadingTimeout(requestId)
                    runCommentLoad(requestId, forceReload = forceReload)
                } catch (e: Throwable) {
                    cancelLoadingTimeout()
                    errorHandler.handle(e)
                    setCommentsState(ArticleCommentsState.Error(e), "network_error")
                } finally {
                    _refreshing.value = false
                }
            }
        }
    }

    private fun scheduleLoadingTimeout(requestId: Int) {
        cancelLoadingTimeout()
        loadingTimeoutJob = scope.launch {
            delay(COMMENTS_LOAD_TIMEOUT_MS)
            if (requestId != loadRequestId.get()) return@launch
            if (_commentsState.value is ArticleCommentsState.Loading) {
                FpdaDebugLog.log(
                        FpdaDebugLog.TAG_COMMENTS_SECTION,
                        "load_timeout",
                        mapOf("requestId" to requestId, "articleId" to boundArticleId)
                )
                setCommentsState(ArticleCommentsState.Error(
                        TimeoutException("comments_load_timeout")
                ), "network_error")
            }
        }
    }

    private fun cancelLoadingTimeout() {
        loadingTimeoutJob?.cancel()
        loadingTimeoutJob = null
    }

    private fun scheduleLoadingMoreTimeout(requestId: Int) {
        cancelLoadingMoreTimeout()
        loadingMoreTimeoutJob = scope.launch {
            delay(COMMENTS_LOAD_TIMEOUT_MS)
            if (requestId != loadRequestId.get()) return@launch
            if (!loadingMoreComments) return@launch
            FpdaDebugLog.log(
                    FpdaDebugLog.TAG_COMMENTS_SECTION,
                    "load_more_timeout",
                    mapOf("requestId" to requestId, "articleId" to boundArticleId)
            )
            loadingMoreComments = false
            _uiEvents.emit(ArticleCommentUiEvent.RefreshLoadMoreUi)
        }
    }

    private fun cancelLoadingMoreTimeout() {
        loadingMoreTimeoutJob?.cancel()
        loadingMoreTimeoutJob = null
    }

    private fun setCommentsState(next: ArticleCommentsState, reason: String) {
        val previous = _commentsState.value
        _commentsState.value = next
        FpdaDebugLog.log(
                FpdaDebugLog.TAG_COMMENTS_SECTION,
                "state_changed",
                mapOf(
                        "articleId" to boundArticleId,
                        "previousState" to previous::class.java.simpleName,
                        "nextState" to next::class.java.simpleName,
                        "requestId" to ((next as? ArticleCommentsState.Loading)?.requestId),
                        "canRetry" to ((next as? ArticleCommentsState.Error)?.canRetry),
                        "reason" to reason
                )
        )
    }

    private suspend fun runCommentLoad(requestId: Int, forceReload: Boolean) {
        if (requestId != loadRequestId.get()) return
        FpdaDebugLog.log(
                FpdaDebugLog.TAG_COMMENTS_SECTION,
                "load_started",
                mapOf("articleId" to boundArticleId, "requestId" to requestId, "forceReload" to forceReload)
        )
        applyCommentLoadResult(articleInteractor.loadComments(forceReload = forceReload), requestId)
    }

    private suspend fun applyCommentLoadResult(
            result: ArticleInteractor.CommentLoadResult,
            requestId: Int
    ) {
        if (requestId != loadRequestId.get()) return
        if (result !is ArticleInteractor.CommentLoadResult.Stale) {
            cancelLoadingTimeout()
        }
        when (result) {
            is ArticleInteractor.CommentLoadResult.Loaded -> {
                val previousSize = allComments.size
                val list = commentsToList(result.tree)
                serverHasMore = result.hasMore
                // Догрузка не дала прироста (сервер вернул ту же партию) → потолок: больше комментов
                // не достать, expectedCount оказался завышен (напр. учитывает удалённый коммент).
                // Гасим «Показать ещё», иначе кнопка и автопагинация зацикливаются на фантоме.
                if (loadingMoreComments && list.size <= loadMoreBaselineSize) {
                    loadMoreCeilingReached = true
                    FpdaDebugLog.log(
                            FpdaDebugLog.TAG_COMMENTS_SECTION,
                            "load_more_ceiling_reached",
                            mapOf(
                                    "articleId" to boundArticleId,
                                    "renderedCount" to list.size,
                                    "expectedCount" to articleInteractor.expectedCommentsCount(),
                                    "page" to result.page,
                            )
                    )
                }
                if (list.isEmpty()) {
                    setCommentsState(ArticleCommentsState.Empty, "parse_empty")
                } else {
                    val appendFrom = if (result.append) previousSize.coerceAtLeast(0) else 0
                    if (result.append && appendFrom < list.size) {
                        FpdaDebugLog.log(
                                FpdaDebugLog.TAG_COMMENTS_SECTION,
                                "batch_appended",
                                mapOf(
                                        "articleId" to boundArticleId,
                                        "appendFromIndex" to appendFrom,
                                        "appendedCount" to (list.size - appendFrom),
                                        "renderedCount" to list.size,
                                        "expectedCount" to articleInteractor.expectedCommentsCount(),
                                        "hasMore" to result.hasMore,
                                        "page" to result.page,
                                )
                        )
                    }
                    publishVisibleComments(appendFromIndex = appendFrom)
                }
            }
            is ArticleInteractor.CommentLoadResult.Empty -> {
                if (_commentsState.value is ArticleCommentsState.Loaded && loadingMoreComments) {
                    FpdaDebugLog.log(
                            FpdaDebugLog.TAG_COMMENTS_SECTION,
                            "load_more_error",
                            mapOf(
                                    "articleId" to boundArticleId,
                                    "reason" to result.reason,
                            )
                    )
                } else if (_commentsState.value !is ArticleCommentsState.Loaded) {
                    setCommentsState(ArticleCommentsState.Empty, "parse_empty")
                }
            }
            is ArticleInteractor.CommentLoadResult.Error -> {
                if (_commentsState.value is ArticleCommentsState.Loaded) {
                    FpdaDebugLog.log(
                            FpdaDebugLog.TAG_COMMENTS_SECTION,
                            "load_more_error",
                            mapOf(
                                    "articleId" to boundArticleId,
                                    "error" to result.throwable.message,
                            )
                    )
                } else if (_commentsState.value !is ArticleCommentsState.Loaded) {
                    errorHandler.handle(result.throwable)
                    setCommentsState(ArticleCommentsState.Error(result.throwable), "parse_error")
                }
            }
            ArticleInteractor.CommentLoadResult.Stale -> {
                if (_commentsState.value is ArticleCommentsState.Loading && !staleLoadRetryUsed) {
                    staleLoadRetryUsed = true
                    FpdaDebugLog.log(
                            FpdaDebugLog.TAG_COMMENTS_SECTION,
                            "load_stale_retry",
                            mapOf("requestId" to requestId)
                    )
                    runCommentLoad(requestId, forceReload = false)
                } else if (_commentsState.value is ArticleCommentsState.Loading) {
                    setCommentsState(ArticleCommentsState.NotLoaded, "stale_after_retry")
                    logLoadSkipped("stale_after_retry")
                }
            }
        }
    }

    private fun logLoadSkipped(reason: String) {
        FpdaDebugLog.log(
                FpdaDebugLog.TAG_COMMENTS_SECTION,
                "load_skipped_reason",
                mapOf(
                        "reason" to reason,
                        "state" to _commentsState.value::class.java.simpleName,
                        "articleId" to boundArticleId
                )
        )
    }

    fun replyComment(commentId: Int, text: String) {
        FpdaDebugLog.log(
                FpdaDebugLog.TAG_COMMENT_ACTION,
                "reply_submit",
                mapOf("articleId" to boundArticleId, "commentId" to commentId, "textLen" to text.trim().length)
        )
        scope.launch {
            try {
                _sendRefreshing.value = true
                articleInteractor.replyComment(commentId, text.trim())
                _uiEvents.emit(ArticleCommentUiEvent.OnReplyComment)
            } catch (e: Throwable) {
                errorHandler.handle(e)
            } finally {
                _sendRefreshing.value = false
            }
        }
    }

    fun toggleLikeComment(comment: Comment) {
        val currentComment = allComments.firstOrNull { it.id == comment.id } ?: comment
        if (currentComment.id <= 0 || !pendingLikeCommentIds.add(currentComment.id)) return
        val previousLikedByMe = currentComment.likedByMe
        val previousLikeCount = currentComment.likeCount
        val action = selectCommentLikeAction(currentComment)
        if (previousLikedByMe && action == null) {
            pendingLikeCommentIds.remove(currentComment.id)
            router.showSystemMessage(R.string.comment_like_cannot_remove)
            return
        }
        if (!previousLikedByMe && action == null) {
            pendingLikeCommentIds.remove(currentComment.id)
            router.showSystemMessage(R.string.comment_action_unavailable)
            return
        }
        val voteAction = action ?: return
        setCommentLikeState(comment.id, !previousLikedByMe, pending = true)
        emitCommentLikeUiUpdate(comment.id)
        scope.launch {
            runCatching {
                articleInteractor.voteComment(voteAction)
            }.onFailure {
                setCommentLikeState(currentComment.id, previousLikedByMe, previousLikeCount, pending = false)
                emitCommentLikeUiUpdate(currentComment.id)
                errorHandler.handle(it)
            }.onSuccess { result ->
                val likeCount = result.karma.count.takeIf { it > 0 }
                        ?: nextLikeCount(previousLikeCount, previousLikedByMe, result.likedByMe)
                setCommentLikeState(currentComment.id, result.likedByMe, likeCount, pending = false)
                emitCommentLikeUiUpdate(currentComment.id)
            }
            pendingLikeCommentIds.remove(currentComment.id)
        }
    }

    private fun emitCommentLikeUiUpdate(commentId: Int) {
        val comment = allComments.firstOrNull { it.id == commentId } ?: return
        scope.launch {
            _uiEvents.emit(
                    ArticleCommentUiEvent.UpdateCommentLike(
                            commentId = comment.id,
                            likedByMe = comment.likedByMe,
                            likeCount = comment.likeCount,
                            pending = comment.isLikePending,
                    )
            )
        }
    }

    private fun selectCommentLikeAction(comment: Comment): Comment.Action? =
            if (comment.likedByMe) {
                comment.unlikeAction ?: comment.toggleAction
            } else {
                comment.likeAction ?: comment.toggleAction
            }?.takeIf { isCommentVoteAction(it, comment.id, comment.likedByMe) }

    private fun isCommentVoteAction(action: Comment.Action, commentId: Int, likedByMe: Boolean): Boolean {
        if (commentId <= 0) return false
        val expectedType = if (likedByMe) Comment.Action.Type.COMMENT_UNLIKE else Comment.Action.Type.COMMENT_LIKE
        if (action.type != expectedType) return false
        val url = action.url.orEmpty()
        if (!url.contains("/pages/karma", ignoreCase = true)) return false
        val fields = action.fields + parseQueryFields(url)
        if (fields["c"]?.toIntOrNull() != commentId) return false
        val expectedVote = if (likedByMe) "0" else "1"
        return fields["v"] == expectedVote
    }

    private fun setCommentLikeState(commentId: Int, likedByMe: Boolean, count: Int? = null, pending: Boolean) {
        val index = allComments.indexOfFirst { it.id == commentId }
        if (index < 0) return
        // Copy only the target comment (not the whole list): DiffUtil still detects the change on
        // this one item because it becomes a fresh instance, while the untouched comments keep their
        // identity between snapshots and are correctly diffed as unchanged. Avoids deep-copying
        // every comment on each like toggle (noticeable on threads with hundreds of comments).
        val source = allComments[index]
        val updated = Comment(source).also { copy ->
            copy.likedByMe = likedByMe
            copy.likeCount = count ?: nextLikeCount(source.likeCount, source.likedByMe, likedByMe)
            copy.isLikePending = pending
            val karma = copy.karma ?: Comment.Karma().also { copy.karma = it }
            karma.status = if (likedByMe) Comment.Karma.LIKED else Comment.Karma.NOT_LIKED
            karma.count = copy.likeCount
        }
        val comments = ArrayList(allComments)
        comments[index] = updated
        allComments = comments
        publishVisibleComments()
    }

    private fun nextLikeCount(currentCount: Int, wasLiked: Boolean, nowLiked: Boolean): Int =
            when {
                wasLiked == nowLiked -> currentCount
                nowLiked -> currentCount + 1
                else -> (currentCount - 1).coerceAtLeast(0)
            }

    fun executeCommentAction(action: Comment.Action, extraFields: Map<String, String> = emptyMap()) {
        logCommentAction("execute", action)
        scope.launch {
            try {
                _refreshing.value = true
                articleInteractor.executeCommentAction(action, extraFields)
            } catch (e: Throwable) {
                errorHandler.handle(e)
            } finally {
                _refreshing.value = false
            }
        }
    }

    fun deleteComment(action: Comment.Action) {
        logCommentAction("delete", action)
        scope.launch {
            try {
                _refreshing.value = true
                if (articleInteractor.deleteComment(action)) {
                    resolveCommentId(action)?.let(::applyLocalDeletedComment)
                }
            } catch (e: Throwable) {
                errorHandler.handle(e)
            } finally {
                _refreshing.value = false
            }
        }
    }

    fun editComment(action: Comment.Action, text: String) {
        logCommentAction("edit", action)
        scope.launch {
            try {
                _refreshing.value = true
                articleInteractor.editComment(action, text)
                val editedCommentId = action.fields["comment_ID"]?.toIntOrNull()
                        ?: action.fields["c"]?.toIntOrNull()
                        ?: allComments.firstOrNull { it.actions.edit == action }?.id
                        ?: 0
                if (editedCommentId > 0) {
                    applyLocalEditedComment(editedCommentId, text)
                }
            } catch (e: Throwable) {
                errorHandler.handle(e)
            } finally {
                _refreshing.value = false
            }
        }
    }

    fun findComment(commentId: Int): Comment? =
            allComments.firstOrNull { it.id == commentId }

    fun loadEditCommentForm(comment: Comment) {
        val current = findComment(comment.id) ?: comment
        val action = current.actions.edit ?: return
        scope.launch {
            try {
                _refreshing.value = true
                val inlineEditable = action.editableHtml?.takeIf { it.isNotBlank() }
                val formAction = if (ArticleCommentActionVisibility.isActionableModeration(action)) {
                    action
                } else {
                    articleInteractor.loadEditCommentForm(action)
                }
                // Поле правки открывалось ПУСТЫМ, если серверная форма отдавала пустое (но непустое как
                // ключ) текстовое поле: `?:` падал только на null, не на blank, и `current.content` не
                // подставлялся. takeIf { isNotBlank() } на каждом шаге чинит подстановку текущего текста.
                val rawText = inlineEditable
                        ?: formAction.fields.entries.firstOrNull { isCommentTextField(it.key) }?.value?.takeIf { it.isNotBlank() }
                        ?: current.content?.takeIf { it.isNotBlank() }
                        ?: current.content.orEmpty()
                // Источник текста для редактора (editableHtml / поле серверной формы) содержит хвост
                // «(отредактирован)», хотя в отображении он срезан (карандаш ✎). Без среза он подставлялся
                // в форму и при повторной правке повторно уходил на сервер — маркер копился в контенте.
                val text = stripNewsCommentEditedMarker(rawText)
                _uiEvents.emit(ArticleCommentUiEvent.ShowEditComment(current, formAction, text))
            } catch (e: Throwable) {
                errorHandler.handle(e)
            } finally {
                _refreshing.value = false
            }
        }
    }

    private suspend fun applyLocalEditedComment(commentId: Int, plainText: String) {
        val normalizedHtml = escapeAsHtml(plainText)
        val updated = ArrayList(allComments.map { source ->
            Comment(source).also { copy ->
                if (copy.id == commentId) {
                    copy.content = normalizedHtml
                    copy.isEdited = true
                }
            }
        })
        allComments = updated
        publishVisibleComments()
        // A same-id re-render is skipped by the idempotent append path, so the edited text never
        // reaches the WebView. Patch the single node directly to refresh it in place.
        updated.firstOrNull { it.id == commentId }?.let { edited ->
            _uiEvents.emit(ArticleCommentUiEvent.PatchComment(edited))
        }
    }

    private fun applyLocalDeletedComment(commentId: Int) {
        val updated = ArrayList(allComments.map { source ->
            Comment(source).also { copy ->
                if (copy.id == commentId) {
                    copy.isDeleted = true
                    copy.content = ""
                    copy.actions = Comment.Actions()
                }
            }
        })
        allComments = updated
        publishVisibleComments()
    }

    private fun resolveCommentId(action: Comment.Action): Int? =
            action.fields["comment_ID"]?.toIntOrNull()
                    ?: action.fields["c"]?.toIntOrNull()
                    ?: parseQueryFields(action.url.orEmpty())["comment_ID"]?.toIntOrNull()
                    ?: parseQueryFields(action.url.orEmpty())["c"]?.toIntOrNull()
                    ?: allComments.firstOrNull { it.actions.delete == action || it.actions.edit == action }?.id
                    ?: action.url.orEmpty()
                            .let { Regex("""(?i)(?:comment-|[?&]c=)(\d+)""").find(it)?.groupValues?.getOrNull(1) }
                            ?.toIntOrNull()

    private fun parseQueryFields(url: String): Map<String, String> {
        val query = url.substringAfter("?", missingDelimiterValue = "")
                .substringBefore("#")
                .takeIf { it.isNotBlank() }
                ?: return emptyMap()
        return query.split("&")
                .mapNotNull { part ->
                    val key = part.substringBefore("=", missingDelimiterValue = "")
                            .replace("&amp;", "&")
                            .takeIf { it.isNotBlank() }
                            ?: return@mapNotNull null
                    key to part.substringAfter("=", missingDelimiterValue = "")
                }
                .toMap()
    }

    private fun escapeAsHtml(text: String): String {
        val escaped = android.text.TextUtils.htmlEncode(text)
        return escaped.replace("\n", "<br>")
    }

    private fun isCommentTextField(name: String): Boolean =
            name.equals("comment", ignoreCase = true) ||
                    name.equals("content", ignoreCase = true) ||
                    name.equals("message", ignoreCase = true) ||
                    name.equals("text", ignoreCase = true)

    fun commentsToList(comment: Comment): ArrayList<Comment> {
        val comments = ArrayList<Comment>()
        recurseCommentsToList(comments, comment)
        allComments = comments
        return comments
    }

    fun recurseCommentsToList(comments: ArrayList<Comment>, comment: Comment) {
        for (child in comment.children) {
            comments.add(Comment(child))
            recurseCommentsToList(comments, child)
        }
    }

    fun openProfile(comment: Comment) {
        linkHandler.handle("https://4pda.to/forum/index.php?showuser=${comment.userId}", router)
    }

    fun canReply(comment: Comment): Boolean =
            comment.id > 0 && !comment.isDeleted

    private fun isLoadedStateUnderfetched(state: ArticleCommentsState.Loaded): Boolean {
        if (state.canLoadMore) return false
        val expected = articleInteractor.expectedCommentsCount()
        if (expected <= 0) return false
        return state.comments.size < expected && state.comments.size < (expected * 9 + 9) / 10
    }

    private fun logCommentAction(kind: String, action: Comment.Action) {
        FpdaDebugLog.log(
                FpdaDebugLog.TAG_COMMENT_ACTION,
                kind,
                mapOf(
                        "articleId" to boundArticleId,
                        "actionUrl" to FpdaDebugLog.sanitizeUrl(action.url),
                        "method" to action.method,
                        "fieldCount" to action.fields.size
                )
        )
    }

    private companion object {
        const val COMMENTS_LOAD_TIMEOUT_MS = 15_000L
        // Предохранитель от бесконечной автодогрузки к deep-link комменту (см. ensureDeepLinkCommentLoaded).
        const val MAX_DEEPLINK_PAGINATE = 12
    }

    class Factory(
            private val articleInteractor: ArticleInteractor,
            private val router: TabRouter,
            private val linkHandler: ILinkHandler,
            private val authHolder: AuthHolder,
            private val errorHandler: IErrorHandler
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(ArticleCommentViewModel::class.java)) {
                return ArticleCommentViewModel(articleInteractor, router, linkHandler, authHolder, errorHandler) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}

sealed class ArticleCommentsState {
    object NotLoaded : ArticleCommentsState()
    data class Loading(val requestId: Int) : ArticleCommentsState()
    data class Loaded(
            val comments: List<Comment>,
            val canLoadMore: Boolean = false,
            val totalCount: Int = comments.size,
            /** When > 0, only comments from this index were added (scroll load-more). */
            val appendFromIndex: Int = 0,
    ) : ArticleCommentsState()
    object Empty : ArticleCommentsState()
    data class Error(
            val throwable: Throwable,
            val canRetry: Boolean = true
    ) : ArticleCommentsState()
}

sealed class ArticleCommentUiEvent {
    data class ShowComments(
            val comments: ArrayList<Comment>,
            val scrollToCommentId: Int = 0,
            /** When false, updates in-memory/DOM only if the section is already expanded. */
            val revealSection: Boolean = false
    ) : ArticleCommentUiEvent()
    data class ScrollToComment(val index: Int, val commentId: Int = 0) : ArticleCommentUiEvent()
    data class ShowEditComment(val comment: Comment, val action: Comment.Action, val text: String) : ArticleCommentUiEvent()
    data class UpdateCommentLike(
            val commentId: Int,
            val likedByMe: Boolean,
            val likeCount: Int,
            val pending: Boolean,
    ) : ArticleCommentUiEvent()
    object OnReplyComment : ArticleCommentUiEvent()
    /** Replaces a single already-rendered comment node in place (used after an edit — the
     *  idempotent append path skips existing ids, so editing must patch the node directly). */
    data class PatchComment(val comment: Comment) : ArticleCommentUiEvent()
    /** Clears JS `loading_more` and re-syncs footer attrs after paginated fetch ends. */
    object RefreshLoadMoreUi : ArticleCommentUiEvent()
}
