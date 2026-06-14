package forpdateam.ru.forpda.ui.fragments.news.details

import forpdateam.ru.forpda.diagnostic.FpdaPipelineLog
import forpdateam.ru.forpda.presentation.articles.detail.comments.ArticleCommentsState
import forpdateam.ru.forpda.presentation.articles.detail.comments.ArticleCommentsPagination
import forpdateam.ru.forpda.presentation.articles.detail.comments.InlineCommentsBatchConfig
import forpdateam.ru.forpda.presentation.articles.detail.comments.CommentsSectionState
import forpdateam.ru.forpda.presentation.articles.detail.comments.CommentsState

/**
 * Single orchestrator for inline news comments expand/collapse/load/render.
 *
 * All user expand taps must go through [userTapExpand]. The coordinator waits for
 * WebView + bridge + DOM, then loads or reinjects without touching article refresh UI.
 */
class CommentsExpandCoordinator(
        private val debounceMs: Long = DEFAULT_DEBOUNCE_MS,
        private val loadingStuckMs: Long = DEFAULT_LOADING_STUCK_MS,
        private val nowMs: () -> Long = { System.currentTimeMillis() },
) {

    enum class SectionPhase {
        Collapsed,
        Expanding,
        Loading,
        Loaded,
        Error,
    }

    data class Environment(
            val webViewReady: Boolean = false,
            val bridgeReady: Boolean = false,
            val domReady: Boolean = false,
            val footerInDom: Boolean = false,
            val commentsJsReady: Boolean = false,
            val webViewGeneration: Int = 0,
            val articleId: Int = 0,
            val commentsCountHint: Int = 0,
    )

    data class Snapshot(
            val phase: SectionPhase = SectionPhase.Collapsed,
            val collapsed: Boolean = true,
            val vmState: ArticleCommentsState = ArticleCommentsState.NotLoaded,
            val env: Environment = Environment(),
            val pendingExpandSource: String? = null,
            val injectGeneration: Int = 0,
            val loadRequestId: Int = 0,
            val loadStartedAtMs: Long = 0L,
            val expandAttemptId: Int = 0,
            val lastExpandAtMs: Long = 0L,
            val loadingStuckRetryUsed: Boolean = false,
    )

    data class ExpandTrace(
            val attemptId: Int,
            val source: String,
            val outcome: String,
            val failureReason: String? = null,
    )

    sealed class Action {
        data object None : Action()
        data class Ignore(val reason: String) : Action()
        data class QueueExpand(val source: String, val reason: String) : Action()
        data class ExpandDom(val source: String) : Action()
        data class ScrollIntoView(val source: String) : Action()
        data class StartLoad(val source: String, val forceReload: Boolean) : Action()
        data class RenderVmState(val vmState: ArticleCommentsState) : Action()
        data class InjectLoadedComments(
                val vmState: ArticleCommentsState.Loaded,
                val generation: Int,
                val scrollToCommentId: Int = 0,
        ) : Action()
        data class AppendLoadedComments(
                val vmState: ArticleCommentsState.Loaded,
                val generation: Int,
        ) : Action()
        data class VerifyDom(val vmState: ArticleCommentsState.Loaded) : Action()
        data class MountFooter(val reason: String) : Action()
        data class BindSection(val collapsed: Boolean, val domState: String) : Action()
        data class CollapseDom(val source: String) : Action()
        data class LogTrace(val trace: ExpandTrace) : Action()
    }

    private var snapshot = Snapshot()

    fun current(): Snapshot = snapshot

    fun commentsSectionState(): CommentsSectionState {
        val vm = snapshot.vmState
        val loaded = (vm as? ArticleCommentsState.Loaded)?.comments?.size ?: 0
        val total = when (vm) {
            is ArticleCommentsState.Loaded -> vm.totalCount.takeIf { it > 0 }
                    ?: snapshot.env.commentsCountHint.takeIf { it > 0 }
                    ?: loaded
            else -> snapshot.env.commentsCountHint.takeIf { it > 0 } ?: -1
        }
        return CommentsSectionState(
                collapsed = snapshot.collapsed,
                isExpanded = !snapshot.collapsed,
                totalCount = total,
                loadedCount = loaded,
                commentsState = vm.toCommentsState(),
        )
    }

    fun resetForArticle(articleId: Int, webViewGeneration: Int) {
        snapshot = Snapshot(
                env = Environment(
                        articleId = articleId,
                        webViewGeneration = webViewGeneration,
                ),
                injectGeneration = webViewGeneration,
        )
    }

    fun syncVmState(vmState: ArticleCommentsState): List<Action> {
        val previous = snapshot.vmState
        if (previous == vmState) return emptyList()
        snapshot = snapshot.copy(vmState = vmState)
        if (!loadedStateNeedsDomUpdate(previous, vmState)) return emptyList()
        return renderActionsForCurrentExpansion("vm_state_sync")
    }

    fun updateEnvironment(env: Environment): List<Action> {
        logCoordinator(
                "env_updated",
                mapOf(
                        "webViewReady" to env.webViewReady,
                        "bridgeReady" to env.bridgeReady,
                        "domReady" to env.domReady,
                        "footerInDom" to env.footerInDom,
                        "commentsJsReady" to env.commentsJsReady,
                        "webViewGeneration" to env.webViewGeneration,
                        "articleId" to env.articleId,
                        "phase" to snapshot.phase.name,
                        "collapsed" to snapshot.collapsed,
                )
        )
        val wasInjectReady = injectPreconditionsMet()
        snapshot = snapshot.copy(env = env)
        val pending = snapshot.pendingExpandSource
        if (pending != null && expandPreconditionsMet()) {
            return flushPendingExpand("env_ready", pending)
        }
        if (!snapshot.collapsed && !wasInjectReady && injectPreconditionsMet()) {
            return renderActionsForCurrentExpansion("env_inject_ready")
        }
        return emptyList()
    }

    fun onFooterBound(): List<Action> {
        snapshot = snapshot.copy(env = snapshot.env.copy(footerInDom = true))
        val pending = snapshot.pendingExpandSource
        if (pending != null && expandPreconditionsMet()) {
            return flushPendingExpand("footer_bound", pending)
        }
        if (!snapshot.collapsed) {
            return renderActionsForCurrentExpansion("footer_bound_expanded")
        }
        return emptyList()
    }

    fun onArticleDomReady(webViewGeneration: Int): List<Action> {
        snapshot = snapshot.copy(
                env = snapshot.env.copy(
                        domReady = true,
                        footerInDom = true,
                        webViewGeneration = webViewGeneration,
                ),
                injectGeneration = webViewGeneration,
        )
        val pending = snapshot.pendingExpandSource
        if (pending != null && expandPreconditionsMet()) {
            return flushPendingExpand("dom_ready", pending)
        }
        if (!snapshot.collapsed) {
            val actions = renderActionsForCurrentExpansion("dom_ready_expanded")
            if (actions.isNotEmpty()) {
                snapshot = snapshot.copy(pendingExpandSource = null)
            }
            return actions
        }
        return emptyList()
    }

    /**
     * WebView document was replaced (same or new article). DOM/footer markers are invalid
     * until [onArticleDomReady] / [onFooterBound].
     */
    fun onWebViewLoadStarted(webViewGeneration: Int): List<Action> {
        val keepExpanded = !snapshot.collapsed
        val pending = when {
            keepExpanded && snapshot.pendingExpandSource != null -> snapshot.pendingExpandSource
            keepExpanded -> "webview_reload"
            else -> null
        }
        snapshot = snapshot.copy(
                env = snapshot.env.copy(
                        domReady = false,
                        footerInDom = false,
                        webViewGeneration = webViewGeneration,
                ),
                injectGeneration = webViewGeneration,
                pendingExpandSource = pending,
        )
        return emptyList()
    }

    fun injectRetryIfExpanded(): List<Action> {
        if (snapshot.collapsed) return emptyList()
        return renderActionsForCurrentExpansion("inject_retry")
    }

    /**
     * JS retry / [onLoadInlineCommentsRequested] while the section is already expanded.
     * Bypasses expand debounce so error retry is not dropped right after the first tap.
     */
    fun userRequestLoad(source: String): List<Action> {
        if (snapshot.collapsed) {
            return userTapExpand(source)
        }
        val vm = snapshot.vmState
        val loadingStuck = vm is ArticleCommentsState.Loading && isLoadingStuck()
        val shouldLoad = when (vm) {
            is ArticleCommentsState.NotLoaded -> true
            is ArticleCommentsState.Error -> true
            is ArticleCommentsState.Empty -> true
            is ArticleCommentsState.Loading -> loadingStuck
            is ArticleCommentsState.Loaded -> isLoadedUnderfetched(vm, snapshot.env.commentsCountHint)
        }
        if (!shouldLoad) {
            return renderActionsForCurrentExpansion(source)
        }
        val underfetched = vm is ArticleCommentsState.Loaded &&
                isLoadedUnderfetched(vm, snapshot.env.commentsCountHint)
        val forceReload = vm is ArticleCommentsState.Empty ||
                vm is ArticleCommentsState.Error ||
                loadingStuck ||
                underfetched
        return startLoadWhileExpanded(source, forceReload)
    }

    fun userTapExpand(source: String): List<Action> {
        val now = nowMs()
        val attemptId = snapshot.expandAttemptId + 1
        if (now - snapshot.lastExpandAtMs < debounceMs) {
            return listOf(
                    Action.Ignore("duplicate_tap"),
                    Action.LogTrace(
                            ExpandTrace(
                                    attemptId = attemptId,
                                    source = source,
                                    outcome = "ignored",
                                    failureReason = "duplicate_tap",
                            )
                    ),
            )
        }
        snapshot = snapshot.copy(
                lastExpandAtMs = now,
                expandAttemptId = attemptId,
                collapsed = false,
                phase = SectionPhase.Expanding,
        )
        val actions = mutableListOf<Action>(
                Action.LogTrace(
                        ExpandTrace(
                                attemptId = attemptId,
                                source = source,
                                outcome = "tap",
                                failureReason = null,
                        )
                ),
        )
        if (!expandPreconditionsMet()) {
            val failureReason = expandPreconditionsFailureReason()
            logCoordinator("expand_queued", mapOf("source" to source, "reason" to failureReason))
            snapshot = snapshot.copy(pendingExpandSource = source)
            actions += Action.QueueExpand(source, failureReason)
            actions += Action.MountFooter("expand_queued")
            actions += Action.BindSection(
                    collapsed = false,
                    domState = domStateFromVm(snapshot.vmState),
            )
            return actions
        }
        actions += executeExpand(source, attemptId)
        return actions
    }

    fun userCollapse(source: String): List<Action> {
        snapshot = snapshot.copy(
                collapsed = true,
                phase = SectionPhase.Collapsed,
                pendingExpandSource = null,
        )
        return listOf(
                Action.CollapseDom(source),
                Action.BindSection(
                        collapsed = true,
                        domState = domStateFromVm(snapshot.vmState),
                ),
        )
    }

    fun mirrorDomCollapsed(collapsed: Boolean): List<Action> {
        if (collapsed == snapshot.collapsed) return emptyList()
        if (!collapsed) {
            snapshot = snapshot.copy(
                    collapsed = false,
                    phase = SectionPhase.Expanding,
            )
            if (expandPreconditionsMet()) {
                return executeExpand("dom_expand_sync", snapshot.expandAttemptId)
            }
            snapshot = snapshot.copy(pendingExpandSource = "dom_expand_sync")
            return buildList {
                add(Action.QueueExpand("dom_expand_sync", expandPreconditionsFailureReason()))
                add(Action.MountFooter("dom_expand_sync"))
                add(Action.BindSection(false, domStateFromVm(snapshot.vmState)))
            }
        }
        return userCollapse("native_toggle")
    }

    fun onNativeCollapsedSync(collapsed: Boolean): List<Action> {
        if (collapsed == snapshot.collapsed) return emptyList()
        return if (collapsed) {
            userCollapse("native_toggle")
        } else {
            mirrorDomCollapsed(false)
        }
    }

    fun nextInjectGeneration(): Int {
        val next = snapshot.injectGeneration + 1
        snapshot = snapshot.copy(injectGeneration = next)
        return next
    }

    fun isInjectGenerationCurrent(generation: Int): Boolean =
            generation > 0 && generation >= snapshot.injectGeneration

    fun bindSectionAction(): Action.BindSection =
            Action.BindSection(
                    collapsed = snapshot.collapsed,
                    domState = domStateFromVm(snapshot.vmState),
            )

    fun shouldAllowPrefetch(): Boolean = snapshot.collapsed && snapshot.pendingExpandSource == null

    private fun flushPendingExpand(reason: String, source: String): List<Action> {
        snapshot = snapshot.copy(pendingExpandSource = null)
        return executeExpand(source, snapshot.expandAttemptId) +
                listOf(
                        Action.LogTrace(
                                ExpandTrace(
                                        attemptId = snapshot.expandAttemptId,
                                        source = source,
                                        outcome = "flush",
                                        failureReason = reason,
                                )
                        ),
                )
    }

    private fun executeExpand(source: String, attemptId: Int): List<Action> {
        val actions = mutableListOf<Action>(
                Action.ExpandDom(source),
                Action.ScrollIntoView(source),
        )
        val vm = snapshot.vmState
        val loadingStuck = vm is ArticleCommentsState.Loading && isLoadingStuck()
        val underfetched = vm is ArticleCommentsState.Loaded &&
                isLoadedUnderfetched(vm, snapshot.env.commentsCountHint)
        // Empty often comes from phase-1 prefetch before deferred comments metadata; always retry on expand.
        val shouldForceReload = vm is ArticleCommentsState.Empty || loadingStuck || underfetched
        val shouldLoad = when (vm) {
            is ArticleCommentsState.NotLoaded -> true
            is ArticleCommentsState.Error -> isExplicitRetrySource(source)
            is ArticleCommentsState.Empty -> true
            is ArticleCommentsState.Loading -> loadingStuck
            is ArticleCommentsState.Loaded -> isLoadedUnderfetched(vm, snapshot.env.commentsCountHint)
        }
        if (loadingStuck) {
            snapshot = snapshot.copy(loadingStuckRetryUsed = true)
        }
        if (shouldLoad) {
            val nextRequestId = snapshot.loadRequestId + 1
            logCoordinator(
                    "load_requested",
                    mapOf(
                            "source" to source,
                            "requestId" to nextRequestId,
                            "forceReload" to shouldForceReload,
                            "vmState" to vm::class.java.simpleName,
                            "loadingStuck" to loadingStuck,
                    )
            )
            snapshot = snapshot.copy(
                    phase = SectionPhase.Loading,
                    loadRequestId = nextRequestId,
                    loadStartedAtMs = nowMs(),
            )
            actions += Action.StartLoad(source, shouldForceReload)
            actions += Action.RenderVmState(ArticleCommentsState.Loading(snapshot.loadRequestId))
            return actions
        }
        when (vm) {
            is ArticleCommentsState.Loaded -> {
                snapshot = snapshot.copy(phase = SectionPhase.Loaded)
                actions += Action.RenderVmState(vm)
                if (vm.comments.isNotEmpty() && injectPreconditionsMet()) {
                    actions += injectOrAppendAction(vm)
                    actions += Action.VerifyDom(vm)
                }
            }
            is ArticleCommentsState.Loading -> {
                snapshot = snapshot.copy(phase = SectionPhase.Loading)
                actions += Action.RenderVmState(vm)
            }
            is ArticleCommentsState.Error -> {
                snapshot = snapshot.copy(phase = SectionPhase.Error)
                actions += Action.RenderVmState(vm)
            }
            else -> actions += Action.RenderVmState(vm)
        }
        actions += Action.LogTrace(
                ExpandTrace(
                        attemptId = attemptId,
                        source = source,
                        outcome = "executed",
                        failureReason = null,
                )
        )
        return actions
    }

    private fun renderActionsForCurrentExpansion(reason: String): List<Action> {
        if (snapshot.collapsed) return emptyList()
        return when (val vm = snapshot.vmState) {
            is ArticleCommentsState.Loading -> listOf(Action.RenderVmState(vm))
            is ArticleCommentsState.Loaded -> buildList {
                snapshot = snapshot.copy(phase = SectionPhase.Loaded)
                add(Action.RenderVmState(vm))
                if (vm.comments.isNotEmpty() && injectPreconditionsMet()) {
                    add(injectOrAppendAction(vm))
                    add(Action.VerifyDom(vm))
                }
            }
            is ArticleCommentsState.Error -> {
                snapshot = snapshot.copy(phase = SectionPhase.Error)
                listOf(Action.RenderVmState(vm))
            }
            is ArticleCommentsState.NotLoaded -> {
                if (snapshot.collapsed) return emptyList()
                if (snapshot.phase == SectionPhase.Loaded || snapshot.phase == SectionPhase.Error) {
                    return startLoadWhileExpanded("deferred_source_ready", forceReload = false)
                }
                if (snapshot.phase == SectionPhase.Loading || snapshot.phase == SectionPhase.Expanding) {
                    snapshot = snapshot.copy(phase = SectionPhase.Expanding)
                    listOf(Action.RenderVmState(vm))
                } else {
                    emptyList()
                }
            }
            is ArticleCommentsState.Empty -> {
                if (snapshot.collapsed) return emptyList()
                if (snapshot.phase == SectionPhase.Loaded || snapshot.phase == SectionPhase.Error) {
                    return startLoadWhileExpanded("empty_while_expanded", forceReload = true)
                }
                if (snapshot.phase == SectionPhase.Loading || snapshot.phase == SectionPhase.Expanding) {
                    snapshot = snapshot.copy(phase = SectionPhase.Loaded)
                    listOf(Action.RenderVmState(vm))
                } else {
                    emptyList()
                }
            }
        }
    }

    /** Enough to expand the section and start a network load. */
    private fun expandPreconditionsMet(): Boolean =
            snapshot.env.webViewReady &&
                    snapshot.env.bridgeReady &&
                    snapshot.env.domReady &&
                    snapshot.env.footerInDom

    /** Required before injecting prefetched HTML into the WebView list. */
    private fun injectPreconditionsMet(): Boolean =
            expandPreconditionsMet() && snapshot.env.commentsJsReady

    private fun expandPreconditionsFailureReason(): String = when {
        !snapshot.env.webViewReady -> "webview_not_ready"
        !snapshot.env.bridgeReady -> "bridge_not_ready"
        !snapshot.env.domReady -> "dom_not_ready"
        !snapshot.env.footerInDom -> "footer_not_in_dom"
        else -> "unknown"
    }

    private fun logCoordinator(event: String, fields: Map<String, Any?> = emptyMap()) {
        FpdaPipelineLog.comments(
                event,
                fields + mapOf(
                        "coordinatorPhase" to snapshot.phase.name,
                        "loadRequestId" to snapshot.loadRequestId,
                )
        )
    }

    private fun isLoadingStuck(): Boolean =
            snapshot.loadStartedAtMs > 0L &&
                    nowMs() - snapshot.loadStartedAtMs >= loadingStuckMs &&
                    !snapshot.loadingStuckRetryUsed

    private fun startLoadWhileExpanded(source: String, forceReload: Boolean): List<Action> {
        val nextRequestId = snapshot.loadRequestId + 1
        logCoordinator(
                "load_requested",
                mapOf("source" to source, "requestId" to nextRequestId, "forceReload" to forceReload)
        )
        snapshot = snapshot.copy(
                phase = SectionPhase.Loading,
                loadRequestId = nextRequestId,
                loadStartedAtMs = nowMs(),
        )
        return listOf(
                Action.StartLoad(source, forceReload),
                Action.RenderVmState(ArticleCommentsState.Loading(snapshot.loadRequestId)),
        )
    }

    private fun domStateFromVm(state: ArticleCommentsState): String = when (state) {
        ArticleCommentsState.NotLoaded -> "not-loaded"
        is ArticleCommentsState.Loading -> "loading"
        is ArticleCommentsState.Loaded -> if (state.comments.isEmpty()) "empty" else "loaded"
        ArticleCommentsState.Empty -> "empty"
        is ArticleCommentsState.Error -> "error"
    }

    private fun isLoadedUnderfetched(state: ArticleCommentsState.Loaded, expectedCount: Int): Boolean {
        if (state.canLoadMore) return false
        if (expectedCount <= 0) return false
        val parsed = state.totalCount.coerceAtLeast(state.comments.size)
        return parsed < expectedCount && parsed < (expectedCount * 9 + 9) / 10
    }

    private fun loadedStateNeedsDomUpdate(
            previous: ArticleCommentsState,
            next: ArticleCommentsState,
    ): Boolean {
        if (previous !is ArticleCommentsState.Loaded || next !is ArticleCommentsState.Loaded) {
            return true
        }
        if (previous.comments.size != next.comments.size) return true
        if (previous.comments.map { it.id } != next.comments.map { it.id }) return true
        // Metadata-only appendFromIndex reset must not reinject the already rendered batch.
        if (next.appendFromIndex > 0 && previous.appendFromIndex != next.appendFromIndex) return true
        return false
    }

    private fun injectOrAppendAction(vm: ArticleCommentsState.Loaded): Action {
        val generation = nextInjectGeneration()
        return if (vm.appendFromIndex > 0 && vm.appendFromIndex < vm.comments.size) {
            Action.AppendLoadedComments(vmState = vm, generation = generation)
        } else {
            Action.InjectLoadedComments(vmState = vm, generation = generation)
        }
    }

    private fun isExplicitRetrySource(source: String): Boolean =
            source.contains("retry", ignoreCase = true) ||
                    source == "toggle_expand" ||
                    source == "tap" ||
                    source == "dom_expand_sync" ||
                    source == "load_requested" ||
                    source == "onCommentsSectionTapReceived" ||
                    source == "native_toolbar" ||
                    source == "deep_link_comment"

    companion object {
        const val DEFAULT_DEBOUNCE_MS = 350L
        const val DEFAULT_LOADING_STUCK_MS = 4_000L

        fun ArticleCommentsState.toCommentsState(): CommentsState = when (this) {
            ArticleCommentsState.NotLoaded -> CommentsState.NotLoaded
            is ArticleCommentsState.Loading -> CommentsState.LoadingInitial(requestId)
            is ArticleCommentsState.Loaded -> CommentsState.Loaded(
                    comments = comments,
                    canLoadMore = canLoadMore,
                    totalCount = totalCount.coerceAtLeast(comments.size),
                    allParsedCommentsCache = comments,
            )
            ArticleCommentsState.Empty -> CommentsState.Empty
            is ArticleCommentsState.Error -> CommentsState.Error(throwable)
        }
    }
}
