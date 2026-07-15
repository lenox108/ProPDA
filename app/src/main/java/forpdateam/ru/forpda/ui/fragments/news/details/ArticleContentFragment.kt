package forpdateam.ru.forpda.ui.fragments.news.details

import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.widget.EditText
import android.widget.Toast

import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle

import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

import forpdateam.ru.forpda.R
import forpdateam.ru.forpda.common.ArticleCommentsUserMessage
import forpdateam.ru.forpda.common.FourPdaImageUrls
import forpdateam.ru.forpda.common.Html
import forpdateam.ru.forpda.common.getColorFromAttr
import forpdateam.ru.forpda.ui.chromeCanvasColor
import forpdateam.ru.forpda.common.showSnackbar
import forpdateam.ru.forpda.common.webview.CustomWebChromeClient
import forpdateam.ru.forpda.common.webview.CustomWebViewClient
import forpdateam.ru.forpda.common.webview.DialogsHelper
import forpdateam.ru.forpda.common.webview.WebViewLoadDispatchPolicy
import forpdateam.ru.forpda.common.webview.WebViewRenderController
import forpdateam.ru.forpda.common.webview.WebViewRenderSession
import forpdateam.ru.forpda.diagnostic.WebViewRenderDiagnostics
import forpdateam.ru.forpda.entity.remote.news.DetailsPage
import forpdateam.ru.forpda.entity.remote.news.Comment
import forpdateam.ru.forpda.model.AuthHolder
import forpdateam.ru.forpda.presentation.articles.detail.content.ArticleContentViewModel
import forpdateam.ru.forpda.presentation.articles.detail.comments.ArticleCommentUiEvent
import forpdateam.ru.forpda.presentation.articles.detail.comments.ArticleCommentViewModel
import forpdateam.ru.forpda.presentation.articles.detail.comments.ArticleCommentsPagination
import forpdateam.ru.forpda.presentation.articles.detail.comments.ArticleCommentsState
import forpdateam.ru.forpda.presentation.articles.detail.comments.InlineCommentsDisplayCount
import forpdateam.ru.forpda.presentation.articles.detail.comments.CommentsSectionState
import forpdateam.ru.forpda.presentation.articles.detail.comments.CommentsState
import forpdateam.ru.forpda.ui.activities.imageviewer.ImageViewerActivity
import forpdateam.ru.forpda.ui.fragments.TabTopScroller
import forpdateam.ru.forpda.ui.fragments.WebViewTopScroller
import forpdateam.ru.forpda.ui.views.ExtendedWebView
import forpdateam.ru.forpda.ui.views.WebViewSecurityProfile
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import forpdateam.ru.forpda.model.preferences.MainPreferencesHolder
import forpdateam.ru.forpda.common.ClipboardHelper
import forpdateam.ru.forpda.common.ArticleLinkResolver
import forpdateam.ru.forpda.common.ExternalBrowserLauncher
import forpdateam.ru.forpda.common.YouTubeLauncher
import forpdateam.ru.forpda.common.YouTubeUrl
import forpdateam.ru.forpda.common.Utils
import forpdateam.ru.forpda.model.repository.avatar.AvatarRepository
import forpdateam.ru.forpda.presentation.IErrorHandler
import forpdateam.ru.forpda.presentation.ILinkHandler
import forpdateam.ru.forpda.presentation.ISystemLinkHandler
import forpdateam.ru.forpda.presentation.TabRouter
import forpdateam.ru.forpda.ui.TemplateManager
import forpdateam.ru.forpda.ui.views.dialog.showWithStyledButtons
import org.json.JSONObject
import timber.log.Timber
import javax.inject.Inject
import android.os.SystemClock
import forpdateam.ru.forpda.model.interactors.news.ArticleReadingProgressStore
import forpdateam.ru.forpda.model.interactors.news.ArticleDeferredExtrasMerger
import forpdateam.ru.forpda.ui.fragments.news.details.modules.ArticleReadingProgressController
import forpdateam.ru.forpda.diagnostic.ArticleCacheTrace
import forpdateam.ru.forpda.diagnostic.StateRaceTrace
import forpdateam.ru.forpda.presentation.articles.detail.ArticleOpenTrace
import forpdateam.ru.forpda.presentation.articles.detail.ArticleTemplate
import forpdateam.ru.forpda.diagnostic.FpdaDebugLog
import forpdateam.ru.forpda.diagnostic.FpdaPipelineLog

/**
 * Created by radiationx on 03.09.17.
 *
 * Fragment для отображения содержимого статьи новостей.
 *
 * JS Bridge обоснование:
 * - loadInlineComments(): загрузка комментариев внизу статьи
 * - sendPoll(): отправка опросов в статьях (постинг на сервер)
 * - openImage(): открытие изображений в ImageViewerActivity
 *
 * Контент: локально генерируемый HTML из API новостей (доверенный статический контент).
 * Профиль безопасности: TRUSTED_STATIC_ARTICLE (JS включён, базовый bridge запрещён, допускаются узкоспециализированные интерфейсы).
 */
@AndroidEntryPoint
class ArticleContentFragment : Fragment(), TabTopScroller {
    @Inject lateinit var linkHandler: ILinkHandler
    @Inject lateinit var router: TabRouter
    @Inject lateinit var systemLinkHandler: ISystemLinkHandler
    @Inject lateinit var mainPreferencesHolder: MainPreferencesHolder
    @Inject lateinit var templateManager: TemplateManager
    @Inject lateinit var errorHandler: IErrorHandler
    @Inject lateinit var clipboardHelper: ClipboardHelper
    @Inject lateinit var avatarRepository: AvatarRepository
    @Inject lateinit var authHolder: AuthHolder
    @Inject lateinit var readingProgressStore: ArticleReadingProgressStore
    @Inject lateinit var articleTemplate: ArticleTemplate

    private lateinit var contentRoot: View
    private lateinit var webView: ExtendedWebView
    private lateinit var topScroller: WebViewTopScroller
    private var currentTrustedArticleHtml: String? = null
    private var newsBridgeAttached = false
    private var inlineComments = emptyList<Comment>()
    private var lastInlineCommentsState: ArticleCommentsState = ArticleCommentsState.NotLoaded
    private val commentsExpandCoordinator = CommentsExpandCoordinator(
            nowMs = { SystemClock.elapsedRealtime() }
    )
    private var commentsSectionRequestId: Int = 0
    private var lastBoundArticleIdForComments: Int = -1
    private var commentsBindRetryCount: Int = 0
    private var commentsBindEpoch: Int = 0
    private var commentsInjectRetryCount: Int = 0
    private var commentsVerifyPass: Int = 0
    // Target comment to scroll into view after a post/reply/deep-link, kept live across the render
    // pipeline: a late full re-inject or a slow batch reload would otherwise reset scroll to the top
    // before the freshly rendered comment is reachable. Re-asserted on each render-complete until
    // the deadline, so it stops fighting the user once comments settle.
    private var pendingCommentScrollId: Int = 0
    private var pendingCommentScrollDeadline: Long = 0L
    // Self-heal of broken article styles: at most STYLE_HEAL_MAX full re-renders per article open
    // (keyed by id so a same-article heal re-render does NOT reset the budget → no infinite loop).
    private var styleHealArticleId: Int = 0
    private var styleHealCount: Int = 0
    private var commentsFooterMountAttempted: Boolean = false
    private var commentsFooterInDom: Boolean = false
    private var commentsJsReady: Boolean = false
    private var lastDomConfirmedArticleId: Int = -1
    private var lastDomConfirmedHtmlHash: Int = 0
    private var inflightRenderArticleId: Int = -1
    private var inflightRenderHtmlHash: Int = 0
    private var lastRequestedRenderArticleId: Int = -1
    private var renderLoadDispatched: Boolean = false
    private var pendingArticle: DetailsPage? = null
    private var blankRenderRetryCount: Int = 0
    private var blankRetryArticleId: Int = -1
    private var layoutConfirmAttempts: Int = 0
    private var articleRequestId: Int = 0
    private var domContentLoadedRequestId: Int = 0

    /**
     * Additive shared-controller mirror (Phase 9, News). Runs alongside the existing
     * [articleRequestId] / [confirmArticleRenderFromWebView] system without changing any dispatch
     * decision — diagnostics only. The existing per-feature logic stays authoritative.
     */
    private val sharedRenderController = WebViewRenderController()
    private var sharedRenderSession: WebViewRenderSession? = null

    private var renderTimeoutRunnable: Runnable? = null
    private var renderProbeToken: Int = 0
    private var articleRenderErrorShown: Boolean = false
    private var ensureRenderPosted: Boolean = false
    private var unpaintedRecoveryAttempts: Int = 0
    private var unpaintedWebViewResetUsed: Boolean = false
    private var lastBlankEscalationRequestId: Int = -1
    private var lastBlankEscalationAtMs: Long = 0L
    private var pendingExpandWatchdogRunnable: Runnable? = null
    private var renderLoadStartedAt: Long = 0L
    private var renderPageFinishedRequestId: Int = -1
    private var renderPageStartedRequestId: Int = -1

    private val readingProgressController: ArticleReadingProgressController by lazy {
        ArticleReadingProgressController(
                store = readingProgressStore,
                webViewProvider = { if (::webView.isInitialized) webView else null },
                currentArticleIdProvider = { lastDomConfirmedArticleId },
                isWebViewReadyProvider = { isWebViewReady() },
                postDelayed = { runnable, delayMs ->
                    if (::webView.isInitialized) {
                        webView.postDelayed(runnable, delayMs)
                        true
                    } else false
                },
        )
    }

    private val viewModel: ArticleContentViewModel by viewModels {
        val interactor = (parentFragment as NewsDetailsFragment).provideChildInteractor()
        ArticleContentViewModel.Factory(interactor, mainPreferencesHolder, templateManager, errorHandler)
    }

    private val commentsViewModel: ArticleCommentViewModel by viewModels(
            ownerProducer = { requireParentFragment() },
    ) {
        ArticleCommentViewModel.Factory(hostFragment().provideChildInteractor(), router, linkHandler, authHolder, errorHandler)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        contentRoot = inflater.inflate(R.layout.fragment_article_content, container, false)
        // Полотно вокруг статьи через ChromeCanvas (XML держит статический Lowest):
        // под Material You тонируется обоями, вне MY fallback = тот же Lowest.
        contentRoot.setBackgroundColor(
                requireContext().chromeCanvasColor(com.google.android.material.R.attr.colorSurfaceContainerLowest))
        val webContainer = contentRoot.findViewById<FrameLayout>(R.id.article_webview_container)
        webView = ExtendedWebView(requireContext()).also {
            it.systemLinkHandler = systemLinkHandler
            it.init(WebViewSecurityProfile.TRUSTED_STATIC_ARTICLE)
            it.prepareForContentLoad()
            it.settings.mediaPlaybackRequiresUserGesture = false
        }
        webContainer.addView(
                webView,
                FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                )
        )
        (parentFragment as? NewsDetailsFragment)?.attachWebView(webView)
        topScroller = WebViewTopScroller(webView, (parentFragment as NewsDetailsFragment).getAppBar())
        webView.setDialogsHelper(DialogsHelper(
                webView.context,
                linkHandler,
                systemLinkHandler,
                router,
                clipboardHelper
        ))
        registerForContextMenu(webView)
        webView.webViewClient = ArticleWebViewClient()
        webView.webChromeClient = CustomWebChromeClient()
        webView.setOnScrollListener(object : ExtendedWebView.OnScrollListener {
            override fun onScrollChange(scrollX: Int, scrollY: Int, oldScrollX: Int, oldScrollY: Int) {
                readingProgressController.onScrollChanged(scrollY)
            }
        })
        attachNewsBridge()
        return contentRoot
    }

    private val commentsSectionState: CommentsSectionState
        get() = commentsExpandCoordinator.commentsSectionState()

    private fun refreshCommentsCoordinatorEnvironment(): CommentsExpandCoordinator.Environment {
        val article = hostFragment().currentArticle()
        val domReadyForCurrentWebView =
                domContentLoadedRequestId > 0 && domContentLoadedRequestId == articleRequestId
        val effectiveWebViewGeneration = domContentLoadedRequestId.takeIf { it > 0 }
                ?: articleRequestId
        val footerPresent = commentsFooterInDom ||
                (domReadyForCurrentWebView && !commentsFooterMountAttempted)
        return CommentsExpandCoordinator.Environment(
                webViewReady = isWebViewReady(),
                bridgeReady = newsBridgeAttached,
                domReady = domReadyForCurrentWebView,
                footerInDom = footerPresent && domReadyForCurrentWebView,
                commentsJsReady = commentsJsReady && domReadyForCurrentWebView,
                webViewGeneration = effectiveWebViewGeneration,
                articleId = hostFragment().currentArticleId(),
                commentsCountHint = resolvedCommentsCount(article),
        )
    }

    private fun dispatchCommentsCoordinatorActions(actions: List<CommentsExpandCoordinator.Action>) {
        for (action in actions) {
            when (action) {
                CommentsExpandCoordinator.Action.None -> Unit
                is CommentsExpandCoordinator.Action.Ignore -> Unit
                is CommentsExpandCoordinator.Action.QueueExpand -> {
                    logCommentsSection(
                            "expand_queued",
                            commentsBridgeFields(
                                    eventSource = action.source,
                                    extra = mapOf("reason" to action.reason)
                            )
                    )
                    schedulePendingExpandWatchdog()
                }
                is CommentsExpandCoordinator.Action.LogTrace -> {
                    FpdaDebugLog.log(
                            FpdaDebugLog.TAG_COMMENTS_SECTION,
                            "expand_attempt",
                            mapOf(
                                    "attemptId" to action.trace.attemptId,
                                    "source" to action.trace.source,
                                    "outcome" to action.trace.outcome,
                                    "failureReason" to action.trace.failureReason,
                                    "articleId" to hostFragment().currentArticleId(),
                            )
                    )
                }
                is CommentsExpandCoordinator.Action.ExpandDom -> evalInlineCommentsExpandOnly()
                is CommentsExpandCoordinator.Action.CollapseDom -> evalInlineCommentsCollapseOnly()
                is CommentsExpandCoordinator.Action.ScrollIntoView -> scrollInlineCommentsSectionIntoView()
                is CommentsExpandCoordinator.Action.MountFooter -> mountCommentsFooterIfMissing()
                is CommentsExpandCoordinator.Action.BindSection ->
                    bindInlineCommentsSection(action.collapsed, action.domState)
                is CommentsExpandCoordinator.Action.StartLoad -> {
                    hostFragment().clearArticleWebViewPendingRenderIfDisplayed()
                    commentsSectionRequestId += 1
                    FpdaDebugLog.log(
                            FpdaDebugLog.TAG_COMMENTS_SECTION,
                            "load_started",
                            mapOf(
                                    "articleId" to hostFragment().currentArticleId(),
                                    "requestId" to commentsSectionRequestId,
                                    "source" to action.source,
                                    "forceReload" to action.forceReload,
                                    "commentsUrlPresent" to !hostFragment().currentArticle()
                                            ?.commentsSource.isNullOrBlank(),
                            )
                    )
                    if (action.forceReload) {
                        commentsViewModel.loadCommentsIfNeeded(forceReload = true)
                    } else {
                        commentsViewModel.loadCommentsIfNeeded()
                    }
                }
                is CommentsExpandCoordinator.Action.RenderVmState -> renderInlineCommentsUi(action.vmState)
                is CommentsExpandCoordinator.Action.InjectLoadedComments ->
                    injectCommentsHtml(action.vmState, action.generation, action.scrollToCommentId)
                is CommentsExpandCoordinator.Action.AppendLoadedComments ->
                    appendCommentsHtml(action.vmState, action.generation)
                is CommentsExpandCoordinator.Action.VerifyDom ->
                    verifyLoadedCommentsInDomOrReinject(action.vmState)
            }
        }
    }

    private fun runCommentsCoordinator(block: () -> List<CommentsExpandCoordinator.Action>) {
        dispatchCommentsCoordinatorActions(
                commentsExpandCoordinator.updateEnvironment(refreshCommentsCoordinatorEnvironment())
        )
        dispatchCommentsCoordinatorActions(block())
        if (commentsExpandCoordinator.current().pendingExpandSource == null) {
            cancelPendingExpandWatchdog()
        }
    }

    private fun requestCommentsExpand(source: String) {
        commentsInjectRetryCount = 0
        commentsBindRetryCount = 0
        commentsBindEpoch++
        hostFragment().clearArticleWebViewPendingRenderIfDisplayed()
        attachNewsBridge()
        scheduleInlineCommentsBinding()
        logCommentsSection(
                "expand_click",
                commentsBridgeFields(
                        eventSource = source,
                        stateBefore = commentsSectionState.commentsState,
                )
        )
        runCommentsCoordinator { commentsExpandCoordinator.userTapExpand(source) }
    }

    private fun collapseInlineComments(source: String) {
        runCommentsCoordinator { commentsExpandCoordinator.userCollapse(source) }
        logCommentsSection("collapse_clicked", mapOf("source" to source))
    }

    private fun resolvedCommentsCount(article: DetailsPage?): Int {
        val articleCount = (article?.commentsCount ?: hostFragment().currentCommentsCount()).coerceAtLeast(0)
        val hint = hostFragment().provideChildInteractor().initData.hintCommentsCount
        return InlineCommentsDisplayCount.resolveExpectedCount(articleCount, hint)
    }

    private fun logCommentsSection(event: String, fields: Map<String, Any?> = emptyMap()) {
        FpdaDebugLog.log(FpdaDebugLog.TAG_COMMENTS_SECTION, event, fields)
    }

    private fun commentsBridgeFields(
            eventSource: String,
            stateBefore: CommentsState? = null,
            stateAfter: CommentsState? = null,
            extra: Map<String, Any?> = emptyMap()
    ): Map<String, Any?> {
        val article = hostFragment().currentArticle()
        return buildMap {
            put("articleId", hostFragment().currentArticleId())
            put("eventSource", eventSource)
            put("commentsUrlSanitized", FpdaDebugLog.sanitizeUrl(article?.url))
            put("commentsCountExpected", article?.commentsCount)
            put("requestId", commentsSectionRequestId)
            put("webViewGenerationId", articleRequestId)
            put("currentCollapsedState", commentsSectionState.collapsed)
            stateBefore?.let { put("commentsStateBefore", it::class.java.simpleName) }
            stateAfter?.let { put("commentsStateAfter", it::class.java.simpleName) }
            putAll(extra)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.uiState.collect { state ->
                        state.article?.let { article ->
                            renderArticle(article)
                            patchCommentsCountIfDomReady(article, "article_model")
                            scheduleInlineCommentsBinding()
                        }
                        applyWebViewStyle(state)
                    }
                }
                launch {
                    commentsViewModel.commentsState.collect { state ->
                        lastInlineCommentsState = state
                        runCommentsCoordinator { commentsExpandCoordinator.syncVmState(state) }
                        reconcileCommentsDisplayCountFromState(state)
                        if (!commentsSectionState.collapsed && state is ArticleCommentsState.Loaded && state.comments.isNotEmpty()) {
                            updateCommentsLoadMoreVisibility(
                                    state.canLoadMore,
                                    state.totalCount,
                                    state.comments.size,
                            )
                        }
                    }
                }
                launch {
                    commentsViewModel.uiEvents.collect { event ->
                        handleCommentUiEvent(event)
                    }
                }
                launch {
                    hostFragment().provideChildInteractor().observeExtrasPatch().collect { patch ->
                        applyDeferredExtrasPatch(patch)
                    }
                }
            }
        }
        commentsViewModel.start()
    }

    fun ensureRendered() {
        if (ensureRenderPosted) return
        if (!::webView.isInitialized) {
            ensureRenderedInternal()
            return
        }
        ensureRenderPosted = true
        webView.post {
            ensureRenderPosted = false
            if (isAdded) ensureRenderedInternal()
        }
    }

    private fun ensureRenderedInternal() {
        val article = viewModel.uiState.value.article ?: hostFragment().currentArticle()
        if (article != null) {
            val force = blankRenderRetryCount > 0 ||
                    shouldForceEnsureRender(article.id)
            renderArticle(article, force = force)
        } else if (!hostFragment().isArticleLoading()) {
            hostFragment().reloadArticle()
        }
    }

    private fun cancelArticleRenderProbes() {
        renderProbeToken++
        renderTimeoutRunnable?.let { runnable ->
            if (::webView.isInitialized) webView.removeCallbacks(runnable)
        }
        renderTimeoutRunnable = null
    }

    private fun renderInflightSnapshot(): ArticleRenderInflightPolicy.Snapshot =
            ArticleRenderInflightPolicy.Snapshot(
                    inflightArticleId = inflightRenderArticleId,
                    inflightHtmlHash = inflightRenderHtmlHash,
                    renderLoadDispatched = renderLoadDispatched,
                    articleRequestId = articleRequestId,
                    domContentLoadedRequestId = domContentLoadedRequestId,
                    lastDomConfirmedArticleId = lastDomConfirmedArticleId,
                    lastRequestedArticleId = lastRequestedRenderArticleId,
            )

    private fun shouldForceEnsureRender(articleId: Int): Boolean =
            ArticleRenderInflightPolicy.shouldForceEnsureRender(articleId, renderInflightSnapshot())

    fun scheduleInlineCommentsBinding() {
        if (!::webView.isInitialized) return
        // Competing evaluateJavascript bind probes during the first loadDataWithBaseURL stall
        // WebView paint for seconds (see FPDA bind_probe + slow_probe_no_content logs).
        if (domContentLoadedRequestId != articleRequestId || domContentLoadedRequestId <= 0) return
        val action = Runnable {
            if (!isWebViewReady()) return@Runnable
            attachNewsBridge()
            runCommentsCoordinator { emptyList() }
            bindInlineCommentsInWebView()
            // Comments are manual-load: never auto-fetch during article render/bind.
        }
        if (webView.isAttachedToWindow) {
            webView.post(action)
        } else {
            webView.post { webView.post(action) }
        }
    }

    private fun bindInlineCommentsInWebView() {
        if (!isWebViewReady()) return
        val bind = commentsExpandCoordinator.bindSectionAction()
        val bindEpoch = ++commentsBindEpoch
        bindInlineCommentsSection(bind.collapsed, bind.domState, probeAfterBind = true, bindEpoch = bindEpoch)
        val loaded = commentsViewModel.commentsState.value
        if (loaded is ArticleCommentsState.Loaded && loaded.comments.isNotEmpty()) {
            updateCommentsLoadMoreVisibility(
                    loaded.canLoadMore,
                    loaded.totalCount,
                    loaded.comments.size,
            )
        }
    }

    private fun bindNewsPollsInWebView() {
        if (!isWebViewReady()) return
        val articleId = hostFragment().currentArticleId()
        FpdaDebugLog.logArticle(
                FpdaDebugLog.ArticleArea.POLL,
                "bind_probe_start",
                mapOf(
                        "articleId" to articleId,
                        "renderGenerationId" to articleRequestId
                )
        )
        webView.evaluateJavascript(NewsWebViewScripts.pollBind()) { raw ->
            if (!isWebViewReady()) return@evaluateJavascript
            logNewsPollWebViewProbe(articleId, raw)
        }
    }

    private fun logNewsPollWebViewProbe(articleId: Int, raw: String?) {
        val payload = NewsWebViewProbes.parsePoll(raw)
        val mappedHtml = currentTrustedArticleHtml.orEmpty()
        FpdaDebugLog.logArticle(
                FpdaDebugLog.ArticleArea.POLL,
                "webview_bind_probe",
                mapOf(
                        "articleId" to articleId,
                        "pollRootFound" to payload.pollRootFound,
                        "pollId" to payload.pollId,
                        "optionsCount" to payload.optionsCount,
                        "canVote" to payload.canVote,
                        "hasToken" to payload.hasToken,
                        "renderedPollBlock" to payload.renderedPollBlock,
                        "readOnlyResults" to payload.readOnlyResults,
                        "boundSubmit" to payload.boundSubmit,
                        "mappedHasNormalizedPoll" to mappedHtml.contains("news-poll-normalized", ignoreCase = true),
                        "mappedHasPollToken" to mappedHtml.contains("data-news-poll-token", ignoreCase = true),
                        "sanitizerRemovedPoll" to (
                                mappedHtml.contains("poll", ignoreCase = true) &&
                                        !mappedHtml.contains("<form", ignoreCase = true) &&
                                        !mappedHtml.contains("data-poll-fallback", ignoreCase = true)
                        ),
                        "probeError" to payload.error
                )
        )
    }

    private fun bindInlineCommentsSection(
            collapsed: Boolean,
            domState: String,
            probeAfterBind: Boolean = false,
            bindEpoch: Int = commentsBindEpoch,
    ) {
        if (!isWebViewReady()) return
        val articleId = hostFragment().currentArticleId()
        FpdaDebugLog.log(
                FpdaDebugLog.TAG_COMMENTS_SECTION,
                "bind_timing",
                mapOf(
                        "articleId" to articleId,
                        "collapsed" to collapsed,
                        "domState" to domState,
                        "kotlinState" to lastInlineCommentsState::class.java.simpleName,
                        "webViewGeneration" to articleRequestId,
                )
        )
        if (!probeAfterBind) {
            webView.evaluateJavascript(
                    NewsWebViewScripts.bindCommentsSection(collapsed, domState, articleRequestId),
                    null,
            )
            return
        }
        webView.evaluateJavascript(
                NewsWebViewScripts.bindCommentsSection(collapsed, domState, articleRequestId)
        ) { raw ->
            if (!isWebViewReady() || bindEpoch != commentsBindEpoch) return@evaluateJavascript
            val probe = NewsWebViewProbes.parseCommentsBind(raw)
            commentsFooterInDom = probe.hasRoot
            FpdaDebugLog.log(
                    FpdaDebugLog.TAG_COMMENTS_SECTION,
                    "bind_probe",
                    mapOf(
                            "articleId" to articleId,
                            "hasRoot" to probe.hasRoot,
                            "hasToggle" to probe.hasToggle,
                            "commentsJsReady" to probe.commentsJsReady,
                            "delegation" to probe.delegationInstalled,
                            "bindEpoch" to bindEpoch,
                    )
            )
            val bindOk = probe.hasRoot && probe.hasToggle && probe.commentsJsReady
            commentsJsReady = bindOk
            if ((!probe.hasRoot && probe.sectionCount == 0 && !commentsFooterMountAttempted) ||
                    (probe.hasRoot && !probe.hasToggle && !commentsFooterMountAttempted)
            ) {
                commentsFooterMountAttempted = true
                mountCommentsFooterIfMissing()
            } else if (probe.sectionCount > 1) {
                webView.evaluateJavascript(
                        "try{if(typeof newsInlineCommentsPruneDuplicates==='function')newsInlineCommentsPruneDuplicates();}catch(e){}",
                        null
                )
            }
            if (!bindOk && commentsBindRetryCount < MAX_COMMENTS_BIND_RETRIES) {
                commentsBindRetryCount++
                webView.postDelayed({ bindInlineCommentsInWebView() }, COMMENTS_BIND_RETRY_MS)
            } else if (bindOk) {
                commentsBindRetryCount = 0
                syncCommentsFooterCountFromModel()
                val loaded = commentsViewModel.commentsState.value
                if (loaded is ArticleCommentsState.Loaded && !collapsed) {
                    updateCommentsLoadMoreVisibility(
                            loaded.canLoadMore,
                            loaded.totalCount,
                            loaded.comments.size,
                    )
                }
                runCommentsCoordinator { commentsExpandCoordinator.onFooterBound() }
                if (!commentsExpandCoordinator.current().collapsed) {
                    runCommentsCoordinator { commentsExpandCoordinator.injectRetryIfExpanded() }
                }
            } else if (!commentsExpandCoordinator.current().collapsed) {
                runCommentsCoordinator { emptyList() }
            }
        }
    }

    private fun commentsDomStateFrom(state: ArticleCommentsState): String = when (state) {
        ArticleCommentsState.NotLoaded -> "not-loaded"
        is ArticleCommentsState.Loading -> "loading"
        is ArticleCommentsState.Loaded -> if (state.comments.isEmpty()) "empty" else "loaded"
        ArticleCommentsState.Empty -> "empty"
        is ArticleCommentsState.Error -> "error"
    }

    private fun ensureCommentsBoundToArticle(articleId: Int) {
        if (articleId <= 0) return
        if (lastBoundArticleIdForComments != articleId) {
            lastBoundArticleIdForComments = articleId
            commentsFooterMountAttempted = false
            commentsFooterInDom = false
            commentsJsReady = false
            commentsBindRetryCount = 0
            commentsExpandCoordinator.resetForArticle(articleId, articleRequestId)
            commentsViewModel.onArticleChanged(articleId)
        }
    }

    @JavascriptInterface
    fun onCommentsSectionJsEvent(payload: String?) {
        if (!forpdateam.ru.forpda.BuildConfig.DEBUG) return
        val (event, fields) = FpdaPipelineLog.parseJsCommentsPayload(payload)
        FpdaPipelineLog.comments(
                event,
                fields + mapOf(
                        "articleId" to hostFragment().currentArticleId(),
                        "method" to "onCommentsSectionJsEvent",
                )
        )
    }

    @JavascriptInterface
    fun onInlineCommentsDomStats(payload: String?) {
        FpdaDebugLog.log(
                FpdaDebugLog.TAG_COMMENTS_SECTION,
                "dom_stats",
                mapOf(
                        "articleId" to hostFragment().currentArticleId(),
                        "payloadLen" to (payload?.length ?: 0),
                        "payload" to payload?.take(180)
                )
        )
    }

    fun renderArticle(article: DetailsPage, force: Boolean = false) {
        if (!::webView.isInitialized) {
            pendingArticle = article
            return
        }
        ensureCommentsBoundToArticle(article.id)
        if (article.id != lastDomConfirmedArticleId) {
            // Preserve the retry budget while a blank-render retry for THIS article is in flight
            // (handleBlankArticleBodyDetected clears lastDomConfirmedArticleId before re-rendering);
            // otherwise the counter resets every cycle and the escalation (refetch/error) never fires.
            if (article.id != blankRetryArticleId) {
                blankRenderRetryCount = 0
            }
            val switchedArticle = lastDomConfirmedArticleId > 0 && lastDomConfirmedArticleId != article.id
            if (switchedArticle) {
                commentsSectionRequestId = 0
                commentsFooterInDom = false
                commentsFooterMountAttempted = false
                commentsExpandCoordinator.resetForArticle(article.id, articleRequestId)
            }
            lastInlineCommentsState = commentsViewModel.commentsState.value
            inlineComments = (lastInlineCommentsState as? ArticleCommentsState.Loaded)?.comments.orEmpty()
            commentsExpandCoordinator.syncVmState(lastInlineCommentsState)
        }
        val html = article.html.orEmpty()
        val htmlHash = html.hashCode()
        lastRequestedRenderArticleId = article.id
        FpdaDebugLog.logArticle(
                FpdaDebugLog.ArticleArea.RENDER,
                if (force) "render_force" else "render_start",
                mapOf(
                        "articleId" to article.id,
                        "renderGenerationId" to articleRequestId,
                        "htmlLen" to html.length,
                        "htmlHash" to htmlHash,
                        "force" to force,
                        "inflightId" to inflightRenderArticleId
                )
        )
        if (ArticleRenderInflightPolicy.shouldSkipInflightDuplicate(
                        force = force,
                        articleId = article.id,
                        htmlHash = htmlHash,
                        snapshot = renderInflightSnapshot()
                )
        ) {
            ArticleCacheTrace.log(
                    event = "webview_skip_duplicate",
                    articleId = article.id,
                    cacheLayer = "webview",
                    hit = true,
                    valid = true,
                    mappedHtmlLen = html.length,
                    reason = "inflight_render"
            )
            return
        }
        if (!force &&
                article.id == inflightRenderArticleId &&
                htmlHash == inflightRenderHtmlHash &&
                !renderLoadDispatched
        ) {
            inflightRenderArticleId = -1
            inflightRenderHtmlHash = 0
        }
        val alreadyRendered = article.id == lastDomConfirmedArticleId &&
                htmlHash == lastDomConfirmedHtmlHash &&
                domContentLoadedRequestId > 0 &&
                domContentLoadedRequestId == articleRequestId
        if (!force && alreadyRendered && webView.contentHeight > WEBVIEW_BLANK_CONTENT_HEIGHT_THRESHOLD) {
            ArticleCacheTrace.log(
                    event = "webview_skip_duplicate",
                    articleId = article.id,
                    cacheLayer = "webview",
                    hit = true,
                    valid = true,
                    mappedHtmlLen = html.length,
                    reason = "already_rendered"
            )
            hostFragment().onArticleWebViewRendered()
            syncCommentsAfterWebViewReload()
            return
        }
        pendingArticle = null
        inflightRenderArticleId = article.id
        inflightRenderHtmlHash = htmlHash
        currentTrustedArticleHtml = html
        attachNewsBridge()
        hostFragment().markArticleWebViewLoadStarted()
        val loadAction = object : Runnable {
            override fun run() {
                if (!isWebViewReady()) {
                    webView.post(this)
                    return
                }
                if (WebViewLoadDispatchPolicy.shouldDeferLoadUntilLayout(webView.width, webView.height)) {
                    webView.post(this)
                    return
                }
                if (html.isBlank()) {
                    inflightRenderArticleId = -1
                    inflightRenderHtmlHash = 0
                    renderLoadDispatched = false
                    showArticleErrorPlaceholder("Пустой контент статьи")
                    return
                }
                domContentLoadedRequestId = 0
                renderPageFinishedRequestId = -1
                renderPageStartedRequestId = -1
                renderLoadDispatched = false
                layoutConfirmAttempts = 0
                unpaintedRecoveryAttempts = 0
                unpaintedWebViewResetUsed = false
                articleRenderErrorShown = false
                cancelArticleRenderProbes()
                commentsFooterInDom = false
                commentsJsReady = false
                commentsFooterMountAttempted = false
                commentsBindRetryCount = 0
                commentsInjectRetryCount = 0
                commentsVerifyPass = 0
                webView.stopLoading()
                webView.clearQueuedJs()
                val requestId = ++articleRequestId
                val sharedSession = sharedRenderController.beginRender(
                        owner = WebViewRenderSession.Owner.NEWS,
                        targetId = requestId,
                        contentHash = htmlHash,
                )
                sharedRenderSession = sharedSession
                if (forpdateam.ru.forpda.BuildConfig.DEBUG) {
                    WebViewRenderDiagnostics.log(
                            sharedSession,
                            WebViewRenderDiagnostics.Event.RENDER_REQUESTED,
                            mapOf("articleId" to article.id, "articleRequestId" to requestId),
                    )
                }
                runCommentsCoordinator { commentsExpandCoordinator.onWebViewLoadStarted(requestId) }
                val startedAt = SystemClock.elapsedRealtime()
                hostFragment().provideChildInteractor().openSession?.markWebViewLoadStart(requestId)
                ArticleOpenTrace.log(
                        articleId = article.id,
                        requestId = requestId,
                        generation = hostFragment().provideChildInteractor().currentArticleGeneration(),
                        phase = "webview_load_start",
                        url = article.url,
                        mappedHtmlLen = html.length,
                        extra = mapOf("renderGenerationId" to requestId)
                )
                val injectedHtml = injectRequestIdScript(html, requestId)
                renderLoadDispatched = true
                // Ensure timers/layout are active before loadDataWithBaseURL; a paused WebView can
                // stall without onPageStarted/onPageFinished for tens of seconds (see FPDA logs).
                webView.onResume()
                webView.loadDataWithBaseURL(
                        TRUSTED_ARTICLE_BASE_URL,
                        injectedHtml,
                        "text/html",
                        "utf-8",
                        null
                )
                sharedRenderSession?.let { session ->
                    if (session.targetId == requestId) {
                        sharedRenderController.markLoadDispatched(session)
                        if (forpdateam.ru.forpda.BuildConfig.DEBUG) {
                            WebViewRenderDiagnostics.log(session, WebViewRenderDiagnostics.Event.LOAD_DISPATCHED)
                        }
                    }
                }
                forceWebViewRepaint()
                renderTimeoutRunnable?.let(webView::removeCallbacks)
                renderLoadStartedAt = startedAt
                scheduleArticleRenderProbe(requestId, article.id, startedAt, isHardDeadline = false)
            }
        }
        if (webView.isAttachedToWindow) {
            webView.post(loadAction)
        } else {
            webView.post { webView.post(loadAction) }
        }
    }

    private fun applyWebViewStyle(state: ArticleContentViewModel.UiState) {
        if (!::webView.isInitialized || !isWebViewReady()) return
        webView.evalJs("changeStyleType(\"${state.styleType}\")")
        webView.setRelativeFontSize(state.fontSize)
        webView.setAppFontMode(state.appFontMode)
    }

    private fun showArticleErrorPlaceholder(message: String? = null) {
        showSnackbar(message ?: getString(R.string.error_occurred))
        webView.loadDataWithBaseURL(
                TRUSTED_ARTICLE_BASE_URL,
                ARTICLE_ERROR_PLACEHOLDER_HTML,
                "text/html",
                "utf-8",
                null
        )
        hostFragment().onArticleWebViewRendered()
    }

    fun showError(message: String) {
        if (!::webView.isInitialized) return
        if (hasConfirmedArticleRender()) return
        showArticleErrorPlaceholder(message)
    }

    fun hasConfirmedArticleRender(): Boolean =
            lastDomConfirmedArticleId > 0 && domContentLoadedRequestId > 0

    override fun onDestroyView() {
        cancelPendingExpandWatchdog()
        cancelArticleRenderProbes()
        if (::webView.isInitialized) {
            detachNewsBridge()
            webView.setJsLifeCycleListener(null)
            webView.webViewClient = android.webkit.WebViewClient()
            webView.webChromeClient = null
            webView.endWork()
        }
        lastDomConfirmedArticleId = -1
        lastDomConfirmedHtmlHash = 0
        inflightRenderArticleId = -1
        inflightRenderHtmlHash = 0
        lastRequestedRenderArticleId = -1
        renderLoadDispatched = false
        pendingArticle = null
        blankRenderRetryCount = 0
        blankRetryArticleId = -1
        unpaintedRecoveryAttempts = 0
        unpaintedWebViewResetUsed = false
        lastBlankEscalationRequestId = -1
        lastBlankEscalationAtMs = 0L
        articleRenderErrorShown = false
        domContentLoadedRequestId = 0
        commentsFooterInDom = false
        commentsJsReady = false
        commentsFooterMountAttempted = false
        sharedRenderController.cleanup()
        sharedRenderSession = null
        super.onDestroyView()
    }

    override fun toggleScrollTop() {
        topScroller.toggleScrollTop()
    }

    fun hostFragment(): NewsDetailsFragment =
            parentFragment as NewsDetailsFragment

    private fun isWebViewReady(): Boolean =
            isAdded && view != null && ::webView.isInitialized && !webView.isDestroyedForReuse()

    /** Clears a hung WebView load so blank-body retry can paint on a fresh document. */
    private fun resetWebViewForArticleReload() {
        if (!::webView.isInitialized) return
        renderTimeoutRunnable?.let(webView::removeCallbacks)
        renderTimeoutRunnable = null
        detachNewsBridge()
        webView.endWork()
        webView.prepareForContentLoad()
        webView.init(WebViewSecurityProfile.TRUSTED_STATIC_ARTICLE)
        webView.webViewClient = ArticleWebViewClient()
        webView.webChromeClient = CustomWebChromeClient()
        attachNewsBridge()
        domContentLoadedRequestId = 0
        renderLoadDispatched = false
        commentsFooterInDom = false
        commentsJsReady = false
        commentsFooterMountAttempted = false
    }

    private fun verifyRenderedContentOrRetry() {
        val html = currentTrustedArticleHtml.orEmpty()
        if (html.isBlank() || blankRenderRetryCount >= MAX_BLANK_RENDER_RETRIES) return
        val requestId = articleRequestId
        if (domContentLoadedRequestId != requestId) return
        probeArticleBodyState { state ->
            if (!isWebViewReady() || requestId != articleRequestId) return@probeArticleBodyState
            val stillBlank = ArticleWebViewRenderProbe.isConfirmedRenderBlank(
                    bodyVisibleByText = state.bodyVisible,
                    contentHeight = webView.contentHeight,
                    blankContentHeightThreshold = WEBVIEW_BLANK_CONTENT_HEIGHT_THRESHOLD
            )
            if (!stillBlank) return@probeArticleBodyState
            handleBlankArticleBodyDetected(
                    requestId = requestId,
                    source = if (state.bodyVisible) "post_confirm_zero_height" else "post_confirm_verify",
                    contentLen = state.contentLen,
                    mappedHtmlLen = html.length,
                    jsScrollHeight = state.scrollHeight
            )
        }
    }

    private fun probeArticleBodyVisible(onResult: (bodyVisible: Boolean, contentLen: Int) -> Unit) {
        probeArticleBodyState { state ->
            onResult(state.bodyVisible, state.contentLen)
        }
    }

    private data class ArticleBodyProbeState(
            val bodyVisible: Boolean,
            val contentLen: Int,
            val scrollHeight: Int = 0,
            val viewportHeight: Int = 0
    )

    private fun probeArticleBodyState(onResult: (ArticleBodyProbeState) -> Unit) {
        webView.evaluateJavascript(ArticleWebViewRenderProbe.visibilityProbeScript()) { raw ->
            if (!isWebViewReady()) return@evaluateJavascript
            val result = ArticleWebViewRenderProbe.parseVisibilityResult(raw)
            onResult(
                    ArticleBodyProbeState(
                            bodyVisible = ArticleWebViewRenderProbe.isArticleBodyVisible(result),
                            contentLen = result.contentLen,
                            scrollHeight = result.scrollHeight,
                            viewportHeight = result.viewportHeight
                    )
            )
        }
    }

    private fun shouldDebounceBlankEscalation(requestId: Int): Boolean {
        val now = SystemClock.elapsedRealtime()
        return requestId == lastBlankEscalationRequestId &&
                now - lastBlankEscalationAtMs < BLANK_ESCALATION_DEBOUNCE_MS
    }

    private fun markBlankEscalation(requestId: Int) {
        lastBlankEscalationRequestId = requestId
        lastBlankEscalationAtMs = SystemClock.elapsedRealtime()
    }

    private fun forceWebViewRepaint() {
        if (!::webView.isInitialized) return
        webView.onResume()
        webView.invalidate()
        webView.requestLayout()
        (webView.parent as? android.view.View)?.let { parent ->
            parent.invalidate()
            parent.requestLayout()
        }
    }

    private fun scheduleUnpaintedBodyRecovery(
            requestId: Int,
            articleId: Int,
            source: String,
            contentLen: Int,
            jsScrollHeight: Int
    ) {
        if (requestId != articleRequestId || domContentLoadedRequestId == requestId) return
        if (unpaintedRecoveryAttempts >= MAX_UNPAINTED_RECOVERY_ATTEMPTS) {
            forceWebViewRepaint()
            webView.postDelayed({
                if (!isWebViewReady() || requestId != articleRequestId) return@postDelayed
                if (domContentLoadedRequestId == requestId) return@postDelayed
                if (webView.contentHeight > WEBVIEW_BLANK_CONTENT_HEIGHT_THRESHOLD) {
                    confirmArticleRenderFromWebView(requestId, "unpainted_repaint_ok")
                    return@postDelayed
                }
                handleBlankArticleBodyDetected(
                        requestId = requestId,
                        source = source,
                        contentLen = contentLen,
                        mappedHtmlLen = currentTrustedArticleHtml.orEmpty().length
                )
            }, UNPAINTED_RECOVERY_BASE_DELAY_MS * 2)
            return
        }
        unpaintedRecoveryAttempts++
        if (unpaintedRecoveryAttempts == UNPAINTED_WEBVIEW_RESET_ATTEMPT && !unpaintedWebViewResetUsed) {
            unpaintedWebViewResetUsed = true
            val cached = viewModel.uiState.value.article ?: hostFragment().currentArticle()
            if (cached != null) {
                resetWebViewForArticleReload()
                renderArticle(cached, force = true)
                return
            }
        }
        FpdaDebugLog.log(
                FpdaDebugLog.TAG_ARTICLE_WEBVIEW,
                "unpainted_body_recovery",
                mapOf(
                        "articleId" to articleId,
                        "requestId" to requestId,
                        "source" to source,
                        "contentLen" to contentLen,
                        "contentHeight" to webView.contentHeight,
                        "jsScrollHeight" to jsScrollHeight,
                        "attempt" to unpaintedRecoveryAttempts
                )
        )
        forceWebViewRepaint()
        val delayMs = UNPAINTED_RECOVERY_BASE_DELAY_MS * unpaintedRecoveryAttempts
        webView.postDelayed({
            if (!isWebViewReady() || requestId != articleRequestId) return@postDelayed
            if (domContentLoadedRequestId == requestId) return@postDelayed
            confirmArticleRenderFromWebView(requestId, "unpainted_recovery")
        }, delayMs)
    }

    private fun handleBlankArticleBodyDetected(
            requestId: Int,
            source: String,
            contentLen: Int,
            mappedHtmlLen: Int,
            jsScrollHeight: Int = 0
    ) {
        if (requestId != articleRequestId) return
        if (ArticleWebViewRenderProbe.shouldDeferBlankEscalation(
                        bodyVisibleByText = contentLen >= ArticleWebViewRenderProbe.MIN_VISIBLE_TEXT_LENGTH,
                        contentLen = contentLen,
                        contentHeight = webView.contentHeight,
                        jsScrollHeight = jsScrollHeight,
                        blankContentHeightThreshold = WEBVIEW_BLANK_CONTENT_HEIGHT_THRESHOLD
                )
        ) {
            val articleId = inflightRenderArticleId.takeIf { it > 0 } ?: hostFragment().currentArticleId()
            scheduleUnpaintedBodyRecovery(
                    requestId = requestId,
                    articleId = articleId,
                    source = source,
                    contentLen = contentLen,
                    jsScrollHeight = jsScrollHeight
            )
            return
        }
        if (shouldDebounceBlankEscalation(requestId)) return
        markBlankEscalation(requestId)
        blankRenderRetryCount++
        domContentLoadedRequestId = 0
        renderLoadDispatched = false
        lastDomConfirmedArticleId = -1
        lastDomConfirmedHtmlHash = 0
        cancelArticleRenderProbes()
        hostFragment().markArticleWebViewLoadStarted()
        hostFragment().provideChildInteractor().openSession?.markWebViewBlankProbe(contentLen, blankRenderRetryCount)
        val logArticleId = inflightRenderArticleId.takeIf { it > 0 } ?: hostFragment().currentArticleId()
        if (logArticleId > 0) blankRetryArticleId = logArticleId
        if (source == "hard_timeout") {
            hostFragment().provideChildInteractor().openSession?.markWebViewError("no_visible_content")
            ArticleOpenTrace.log(
                    articleId = logArticleId,
                    requestId = requestId,
                    generation = hostFragment().provideChildInteractor().currentArticleGeneration(),
                    phase = "webview_timeout",
                    elapsedMs = renderLoadStartedAt.takeIf { it > 0L }
                            ?.let { SystemClock.elapsedRealtime() - it },
                    reason = "no_visible_content"
            )
        }
        FpdaDebugLog.warn(
                FpdaDebugLog.TAG_ARTICLE_WEBVIEW,
                "blank_body_detected",
                mapOf(
                        "articleId" to logArticleId,
                        "requestId" to requestId,
                        "source" to source,
                        "contentLen" to contentLen,
                        "retryCount" to blankRenderRetryCount,
                        "contentHeight" to webView.contentHeight,
                        "mappedHtmlLen" to mappedHtmlLen
                )
        )
        if (forpdateam.ru.forpda.BuildConfig.DEBUG) {
            Timber.w(
                    "Article body blank, retry=%d id=%d contentLen=%d htmlLen=%d",
                    blankRenderRetryCount,
                    hostFragment().currentArticleId(),
                    contentLen,
                    mappedHtmlLen
            )
        }
        when (BlankRenderRetryPolicy.decide(blankRenderRetryCount, MAX_BLANK_RENDER_RETRIES)) {
            BlankRenderRetryPolicy.Decision.GIVE_UP -> {
                cancelArticleRenderProbes()
                articleRenderErrorShown = true
                inflightRenderArticleId = -1
                inflightRenderHtmlHash = 0
                renderLoadDispatched = false
                blankRetryArticleId = -1
                showArticleErrorPlaceholder("Не удалось отобразить текст новости")
            }
            BlankRenderRetryPolicy.Decision.REFETCH -> {
                cancelArticleRenderProbes()
                articleRenderErrorShown = false
                FpdaDebugLog.warn(
                        FpdaDebugLog.TAG_ARTICLE_WEBVIEW,
                        "blank_body_force_refresh",
                        mapOf(
                                "articleId" to logArticleId,
                                "requestId" to requestId,
                                "retryCount" to blankRenderRetryCount
                        )
                )
                hostFragment().reloadArticle()
            }
            BlankRenderRetryPolicy.Decision.RERENDER_CACHED -> {
                cancelArticleRenderProbes()
                articleRenderErrorShown = false
                val cached = viewModel.uiState.value.article ?: hostFragment().currentArticle()
                if (cached != null) {
                    resetWebViewForArticleReload()
                    renderArticle(cached, force = true)
                } else {
                    hostFragment().reloadArticle()
                }
            }
        }
    }

    @JavascriptInterface
    fun toComments() {
        loadInlineComments()
    }

    @JavascriptInterface
    fun retryOpenArticle() {
        webView.runInUiThread {
            if (!isWebViewReady()) return@runInUiThread
            hostFragment().reloadArticle()
        }
    }

    @JavascriptInterface
    fun onArticleDomContentLoaded(requestId: String?) {
        val id = requestId?.toIntOrNull() ?: return
        webView.runInUiThread {
            confirmArticleRenderFromWebView(id, "dom_content_loaded")
        }
    }

    private fun confirmArticleRenderFromWebView(requestId: Int, source: String) {
        if (!isWebViewReady()) return
        if (requestId != articleRequestId) {
            StateRaceTrace.log(
                    domain = "article_webview",
                    event = "stale_ignored",
                    requestId = requestId,
                    currentGeneration = articleRequestId,
                    articleId = inflightRenderArticleId,
                    reason = source
            )
            return
        }
        if (domContentLoadedRequestId == requestId) return
        val articleId = inflightRenderArticleId.takeIf { it > 0 } ?: lastDomConfirmedArticleId
        if (articleId <= 0) return
        probeArticleBodyState { state ->
            val bodyVisible = state.bodyVisible
            val contentLen = state.contentLen
            if (!isWebViewReady() || requestId != articleRequestId) return@probeArticleBodyState
            if (domContentLoadedRequestId == requestId) return@probeArticleBodyState
            if (!bodyVisible) {
                FpdaDebugLog.log(
                        FpdaDebugLog.TAG_ARTICLE_WEBVIEW,
                        "confirm_deferred_blank_body",
                        mapOf(
                                "articleId" to articleId,
                                "requestId" to requestId,
                                "source" to source,
                                "contentLen" to contentLen,
                                "contentHeight" to webView.contentHeight
                        )
                )
                scheduleArticleRenderProbe(
                        requestId = requestId,
                        articleId = articleId,
                        startedAt = renderLoadStartedAt.takeIf { it > 0L }
                                ?: SystemClock.elapsedRealtime(),
                        isHardDeadline = false
                )
                webView.postDelayed({
                    if (domContentLoadedRequestId == requestId) return@postDelayed
                    handleBlankArticleBodyDetected(
                            requestId = requestId,
                            source = source,
                            contentLen = contentLen,
                            mappedHtmlLen = currentTrustedArticleHtml.orEmpty().length,
                            jsScrollHeight = state.scrollHeight
                    )
                }, BLANK_RENDER_VERIFY_DELAY_MS)
                return@probeArticleBodyState
            }
            val nativeContentHeight = webView.contentHeight
            if (ArticleWebViewRenderProbe.isUnpaintedButLaidOutInDom(
                            bodyVisibleByText = bodyVisible,
                            contentHeight = nativeContentHeight,
                            jsScrollHeight = state.scrollHeight,
                            blankContentHeightThreshold = WEBVIEW_BLANK_CONTENT_HEIGHT_THRESHOLD
                    )
            ) {
                if (layoutConfirmAttempts < MAX_LAYOUT_CONFIRM_ATTEMPTS) {
                    layoutConfirmAttempts++
                    FpdaDebugLog.log(
                            FpdaDebugLog.TAG_ARTICLE_WEBVIEW,
                            "confirm_deferred_unpainted_body",
                            mapOf(
                                    "articleId" to articleId,
                                    "requestId" to requestId,
                                    "source" to source,
                                    "contentLen" to contentLen,
                                    "contentHeight" to nativeContentHeight,
                                    "jsScrollHeight" to state.scrollHeight,
                                    "layoutAttempt" to layoutConfirmAttempts
                            )
                    )
                    forceWebViewRepaint()
                    webView.postDelayed({
                        confirmArticleRenderFromWebView(requestId, source)
                    }, LAYOUT_CONFIRM_DELAY_MS)
                    return@probeArticleBodyState
                }
                scheduleUnpaintedBodyRecovery(
                        requestId = requestId,
                        articleId = articleId,
                        source = source,
                        contentLen = contentLen,
                        jsScrollHeight = state.scrollHeight
                )
                return@probeArticleBodyState
            }
            if (ArticleWebViewRenderProbe.isConfirmedRenderBlank(
                            bodyVisibleByText = bodyVisible,
                            contentHeight = nativeContentHeight,
                            blankContentHeightThreshold = WEBVIEW_BLANK_CONTENT_HEIGHT_THRESHOLD
                    )
            ) {
                handleBlankArticleBodyDetected(
                        requestId = requestId,
                        source = source,
                        contentLen = contentLen,
                        mappedHtmlLen = currentTrustedArticleHtml.orEmpty().length,
                        jsScrollHeight = state.scrollHeight
                )
                return@probeArticleBodyState
            }
            layoutConfirmAttempts = 0
            unpaintedRecoveryAttempts = 0
            unpaintedWebViewResetUsed = false
            finalizeArticleRenderConfirmation(requestId, source, articleId)
        }
    }

    private fun finalizeArticleRenderConfirmation(requestId: Int, source: String, articleId: Int) {
        if (!isWebViewReady() || requestId != articleRequestId) return
        if (domContentLoadedRequestId == requestId) return
        domContentLoadedRequestId = requestId
        sharedRenderSession?.let { session ->
            if (session.targetId == requestId) {
                sharedRenderController.markDomConfirmed(session)
                if (forpdateam.ru.forpda.BuildConfig.DEBUG) {
                    WebViewRenderDiagnostics.log(
                            session,
                            WebViewRenderDiagnostics.Event.DOM_CONFIRMED,
                            mapOf("source" to source, "articleId" to articleId),
                    )
                }
            }
        }
        renderLoadDispatched = false
        blankRetryArticleId = -1
        // The article WebView uses TRUSTED_STATIC_ARTICLE, which never enables the IBase bridge, so
        // ExtendedWebView.domContentLoaded() never flips isJsReady and every loadDataWithBaseURL /
        // detach-reattach leaves it false. Without this, all evalJs()-gated calls (live theme
        // changeStyleType, font mode, comment helpers) are queued into actionsForWebView and silently
        // dropped. The INews DOM-ready callback that got us here proves JS runs, so unblock the queue.
        webView.acknowledgeJsBridgeFromNativeProbe()
        commentsFooterInDom = true
        renderTimeoutRunnable?.let(webView::removeCallbacks)
        renderTimeoutRunnable = null
        lastDomConfirmedArticleId = articleId
        lastDomConfirmedHtmlHash = inflightRenderHtmlHash.takeIf { inflightRenderArticleId > 0 }
                ?: lastDomConfirmedHtmlHash
        inflightRenderArticleId = -1
        inflightRenderHtmlHash = 0
        hostFragment().provideChildInteractor().openSession?.markWebViewFinished(source)
        hostFragment().onArticleWebViewRendered()
        hostFragment().clearArticleWebViewPendingRenderIfDisplayed()
        applyWebViewStyle(viewModel.uiState.value)
        sanitizeAndLogNestedArticleDom(requestId, source)
        bindNewsPollsInWebView()
        bindInlineCommentsInWebView()
        syncCommentsFooterCountFromModel("dom_ready")
        runCommentsCoordinator { commentsExpandCoordinator.onArticleDomReady(requestId) }
        if (commentsSectionState.collapsed) {
            renderInlineCommentsUi(lastInlineCommentsState)
        }
        maybeExpandInlineCommentsForDeepLink()
        FpdaDebugLog.log(
                FpdaDebugLog.TAG_COMMENTS_SECTION,
                "section_rendered",
                mapOf(
                        "articleId" to lastDomConfirmedArticleId,
                        "requestId" to commentsSectionRequestId,
                        "stateAfter" to commentsSectionState.commentsState::class.java.simpleName,
                        "collapsed" to commentsSectionState.collapsed
                )
        )
        webView.postDelayed({ verifyRenderedContentOrRetry() }, BLANK_RENDER_VERIFY_DELAY_MS)
        // Проверяем применение _news.css/_main.css после рендера ТЕЛА статьи (не только комментов) —
        // чтобы срыв вёрстки лечился, даже когда комменты не загрузились. Небольшая задержка на укладку.
        webView.postDelayed({ probeArticleStyleState("render_confirmed") }, 250L)
        readingProgressController.restoreFor(articleId)
        FpdaDebugLog.log(
                FpdaDebugLog.TAG_ARTICLE_WEBVIEW,
                "render_confirmed",
                mapOf(
                        "articleId" to articleId,
                        "requestId" to requestId,
                        "source" to source,
                        "mappedHtmlLen" to currentTrustedArticleHtml.orEmpty().length
                )
        )
    }

    private fun applyDeferredExtrasPatch(patch: ArticleDeferredExtrasMerger.Patch) {
        if (!isWebViewReady() || patch.articleId != hostFragment().currentArticleId()) return
        val hint = hostFragment().provideChildInteractor().initData.hintCommentsCount
        val effectiveCount = InlineCommentsDisplayCount.resolveExpectedCount(
                maxOf(resolvedCommentsCount(hostFragment().currentArticle()), patch.commentsCount),
                hint,
        )
        commentsViewModel.onDeferredCommentsSourceAvailable(effectiveCount, patch.hasCommentsSource)
        val article = hostFragment().currentArticle()
        val footerHtml = article?.let {
            articleTemplate.commentsFooterHtml(
                    commentsCount = effectiveCount,
                    hasCommentsSource = patch.hasCommentsSource
            )
        }.orEmpty()
        webView.evaluateJavascript(
                buildCommentsFooterPatchScript(effectiveCount, footerHtml),
                null
        )
        applyCommentsDisplayCountInWebView(effectiveCount, "deferred_extras")
        scheduleInlineCommentsBinding()
        FpdaDebugLog.log(
                FpdaDebugLog.TAG_ARTICLE_DEFERRED,
                "patch_applied",
                mapOf(
                        "articleId" to patch.articleId,
                        "commentsCount" to effectiveCount,
                        "patchCommentsCount" to patch.commentsCount,
                        "hasCommentsSource" to patch.hasCommentsSource
                )
        )
    }

    private fun sanitizeAndLogNestedArticleDom(requestId: Int, source: String) {
        if (!isWebViewReady()) return
        val articleId = hostFragment().currentArticleId()
        webView.evaluateJavascript(
                """(function(){
                    try{
                        var contents=document.querySelectorAll('.content');
                        var outer=document.querySelector('body#news .content') || document.querySelector('.content');
                        var nested=outer ? outer.querySelectorAll('.content').length : 0;
                        var fixed=false;
                        if(outer && nested>0){
                            var inner=outer.querySelector('.content');
                            if(inner){
                                outer.innerHTML=inner.innerHTML;
                                fixed=true;
                            }
                        }
                        return JSON.stringify({
                            contents: contents ? contents.length : 0,
                            nested: nested,
                            fixed: fixed,
                            hasNewsBody: !!document.querySelector('body#news'),
                            hasHeader: !!document.querySelector('.news-detail-header')
                        });
                    }catch(e){ return ''; }
                })();""",
        ) { raw ->
            if (!isWebViewReady()) return@evaluateJavascript
            if (requestId != articleRequestId) return@evaluateJavascript
            val payload = raw?.trim()?.removeSurrounding("\"")?.replace("\\\"", "\"")
            FpdaDebugLog.log(
                    FpdaDebugLog.TAG_ARTICLE_WEBVIEW,
                    "dom_sanitize",
                    mapOf(
                            "articleId" to articleId,
                            "requestId" to requestId,
                            "source" to source,
                            "stats" to payload?.take(220)
                    )
            )
        }
    }

    private fun scheduleArticleRenderProbe(
            requestId: Int,
            articleId: Int,
            startedAt: Long,
            isHardDeadline: Boolean
    ) {
        if (articleRenderErrorShown) return
        renderTimeoutRunnable?.let(webView::removeCallbacks)
        if (domContentLoadedRequestId == requestId) return
        val elapsedSinceStart = SystemClock.elapsedRealtime() - startedAt
        val delayMs = when {
            isHardDeadline -> 0L
            elapsedSinceStart >= WEBVIEW_RENDER_HARD_TIMEOUT_MS -> 0L
            elapsedSinceStart >= WEBVIEW_RENDER_SOFT_TIMEOUT_MS ->
                WEBVIEW_RENDER_PROBE_INTERVAL_MS
            elapsedSinceStart >= WEBVIEW_RENDER_EARLY_RELOAD_MS ->
                WEBVIEW_RENDER_PROBE_INTERVAL_MS
            else -> (WEBVIEW_STALLED_LOAD_MS - elapsedSinceStart).coerceAtLeast(WEBVIEW_RENDER_PROBE_INTERVAL_MS)
        }
        val probeToken = ++renderProbeToken
        val runnable = Runnable {
            if (probeToken != renderProbeToken || articleRenderErrorShown) return@Runnable
            runArticleRenderProbe(requestId, articleId, startedAt, isHardDeadline || delayMs == 0L)
        }
        renderTimeoutRunnable = runnable
        webView.postDelayed(runnable, delayMs)
    }

    private fun runArticleRenderProbe(
            requestId: Int,
            articleId: Int,
            startedAt: Long,
            isHardDeadline: Boolean
    ) {
        if (!isWebViewReady()) return
        if (requestId != articleRequestId) return
        if (domContentLoadedRequestId == requestId) return
        val elapsedMs = SystemClock.elapsedRealtime() - startedAt
        probeArticleBodyState { state ->
            if (!isWebViewReady()) return@probeArticleBodyState
            if (requestId != articleRequestId) return@probeArticleBodyState
            if (domContentLoadedRequestId == requestId) return@probeArticleBodyState
            val contentLen = state.contentLen
            val nativeContentHeight = webView.contentHeight
            if (state.bodyVisible && nativeContentHeight > WEBVIEW_BLANK_CONTENT_HEIGHT_THRESHOLD) {
                ArticleOpenTrace.log(
                        articleId = articleId,
                        requestId = requestId,
                        generation = hostFragment().provideChildInteractor().currentArticleGeneration(),
                        phase = "webview_content_probe",
                        elapsedMs = elapsedMs
                )
                confirmArticleRenderFromWebView(requestId, "content_probe")
                return@probeArticleBodyState
            }
            if (ArticleWebViewRenderProbe.shouldForceStalledEmptyDomReload(
                            elapsedMs = elapsedMs,
                            contentLen = contentLen,
                            jsScrollHeight = state.scrollHeight,
                            domConfirmedRequestId = domContentLoadedRequestId,
                            requestId = requestId,
                            stalledLoadMs = WEBVIEW_STALLED_LOAD_MS
                    )
            ) {
                handleBlankArticleBodyDetected(
                        requestId = requestId,
                        source = "stalled_empty_dom",
                        contentLen = contentLen,
                        mappedHtmlLen = currentTrustedArticleHtml.orEmpty().length,
                        jsScrollHeight = state.scrollHeight
                )
                return@probeArticleBodyState
            }
            if (ArticleWebViewRenderProbe.shouldWaitForPageFinishBeforeBlankEscalation(
                            renderPageFinishedRequestId = renderPageFinishedRequestId,
                            requestId = requestId,
                            elapsedMs = elapsedMs,
                            softTimeoutMs = WEBVIEW_RENDER_SOFT_TIMEOUT_MS,
                            contentLen = contentLen,
                            jsScrollHeight = state.scrollHeight
                    )
            ) {
                scheduleArticleRenderProbe(requestId, articleId, startedAt, isHardDeadline = false)
                return@probeArticleBodyState
            }
            if (renderLoadDispatched &&
                    renderPageStartedRequestId != requestId &&
                    elapsedMs >= WEBVIEW_STALLED_LOAD_MS
            ) {
                handleBlankArticleBodyDetected(
                        requestId = requestId,
                        source = "stalled_load_no_page_start",
                        contentLen = contentLen,
                        mappedHtmlLen = currentTrustedArticleHtml.orEmpty().length,
                        jsScrollHeight = state.scrollHeight
                )
                return@probeArticleBodyState
            }
            if (ArticleWebViewRenderProbe.shouldDeferBlankEscalation(
                            bodyVisibleByText = state.bodyVisible,
                            contentLen = contentLen,
                            contentHeight = nativeContentHeight,
                            jsScrollHeight = state.scrollHeight,
                            blankContentHeightThreshold = WEBVIEW_BLANK_CONTENT_HEIGHT_THRESHOLD
                    )
            ) {
                scheduleUnpaintedBodyRecovery(
                        requestId = requestId,
                        articleId = articleId,
                        source = "slow_probe_unpainted",
                        contentLen = contentLen,
                        jsScrollHeight = state.scrollHeight
                )
                return@probeArticleBodyState
            }
            if (!state.bodyVisible) {
                val probeArticleId = inflightRenderArticleId.takeIf { it > 0 } ?: hostFragment().currentArticleId()
                FpdaDebugLog.log(
                        FpdaDebugLog.TAG_ARTICLE_WEBVIEW,
                        "visibility_probe",
                        mapOf(
                                "articleId" to probeArticleId,
                                "contentLen" to contentLen,
                                "textLen" to state.contentLen,
                                "hasContent" to state.bodyVisible,
                                "hasHeader" to false,
                                "scrollHeight" to state.scrollHeight
                        )
                )
            }
            if (elapsedMs >= WEBVIEW_RENDER_EARLY_RELOAD_MS &&
                    blankRenderRetryCount < MAX_BLANK_RENDER_RETRIES &&
                    inflightRenderArticleId == articleId
            ) {
                val domStillEmpty = ArticleWebViewRenderProbe.isDomCompletelyEmpty(
                        contentLen,
                        state.scrollHeight
                )
                if (!domStillEmpty &&
                        renderPageFinishedRequestId != requestId &&
                        elapsedMs < WEBVIEW_RENDER_SOFT_TIMEOUT_MS
                ) {
                    scheduleArticleRenderProbe(requestId, articleId, startedAt, isHardDeadline = false)
                    return@probeArticleBodyState
                }
                if (!domStillEmpty &&
                        renderLoadDispatched &&
                        domContentLoadedRequestId != requestId &&
                        elapsedMs < WEBVIEW_RENDER_SOFT_TIMEOUT_MS
                ) {
                    scheduleArticleRenderProbe(requestId, articleId, startedAt, isHardDeadline = false)
                    return@probeArticleBodyState
                }
                handleBlankArticleBodyDetected(
                        requestId = requestId,
                        source = "slow_probe_no_content",
                        contentLen = contentLen,
                        mappedHtmlLen = currentTrustedArticleHtml.orEmpty().length,
                        jsScrollHeight = state.scrollHeight
                )
                return@probeArticleBodyState
            }
            val hardDeadlineReached = isHardDeadline || elapsedMs >= WEBVIEW_RENDER_HARD_TIMEOUT_MS
            if (hardDeadlineReached) {
                handleBlankArticleBodyDetected(
                        requestId = requestId,
                        source = "hard_timeout",
                        contentLen = contentLen,
                        mappedHtmlLen = currentTrustedArticleHtml.orEmpty().length,
                        jsScrollHeight = state.scrollHeight
                )
                return@probeArticleBodyState
            }
            scheduleArticleRenderProbe(requestId, articleId, startedAt, isHardDeadline = false)
        }
    }

    private fun probeArticleVisibility(onResult: (visible: Boolean, contentLen: Int) -> Unit) {
        webView.evaluateJavascript(ArticleWebViewRenderProbe.visibilityProbeScript()) { raw ->
            if (!isWebViewReady()) return@evaluateJavascript
            val result = ArticleWebViewRenderProbe.parseVisibilityResult(raw)
            val visible = ArticleWebViewRenderProbe.isArticleBodyVisible(result)
            if (!visible) {
                val probeArticleId = inflightRenderArticleId.takeIf { it > 0 } ?: hostFragment().currentArticleId()
                FpdaDebugLog.log(
                        FpdaDebugLog.TAG_ARTICLE_WEBVIEW,
                        "visibility_probe",
                        mapOf(
                                "articleId" to probeArticleId,
                                "contentLen" to result.contentLen,
                                "textLen" to result.textLen,
                                "hasContent" to result.hasContent,
                                "hasHeader" to result.hasHeader
                        )
                )
            }
            onResult(visible, result.contentLen)
        }
    }

    @JavascriptInterface
    fun loadInlineComments() {
        if (context == null)
            return
        webView.runInUiThread {
            if (!isWebViewReady()) return@runInUiThread
            FpdaDebugLog.log(
                    FpdaDebugLog.TAG_COMMENTS_SECTION,
                    "tap_received",
                    mapOf(
                            "articleId" to hostFragment().currentArticleId(),
                            "source" to "native_toolbar",
                            "state" to commentsViewModel.commentsState.value::class.java.simpleName
                    )
            )
            requestCommentsExpand("native_toolbar")
        }
    }

    @JavascriptInterface
    fun onInlineCommentsSectionToggled(collapsed: Boolean) {
        if (context == null) return
        webView.runInUiThread {
            if (!isWebViewReady()) return@runInUiThread
            val before = commentsSectionState.commentsState
            runCommentsCoordinator { commentsExpandCoordinator.onNativeCollapsedSync(collapsed) }
            logCommentsSection(
                    if (collapsed) "collapse_clicked" else "expand_clicked",
                    commentsBridgeFields(
                            eventSource = "onInlineCommentsSectionToggled",
                            stateBefore = before,
                            stateAfter = commentsSectionState.commentsState,
                            extra = mapOf(
                                    "currentCollapsedState" to collapsed,
                                    "commentsCountExpected" to hostFragment().currentArticle()?.commentsCount
                            )
                    )
            )
        }
    }

    @JavascriptInterface
    fun onLoadInlineCommentsRequested() {
        if (context == null) return
        webView.runInUiThread {
            if (!isWebViewReady()) return@runInUiThread
            logCommentsSection(
                    "kotlin_bridge_called",
                    commentsBridgeFields(eventSource = "onLoadInlineCommentsRequested")
            )
            attachNewsBridge()
            scheduleInlineCommentsBinding()
            if (commentsExpandCoordinator.current().collapsed) {
                requestCommentsExpand("load_requested")
            } else {
                runCommentsCoordinator { commentsExpandCoordinator.userRequestLoad("load_requested") }
            }
        }
    }

    @JavascriptInterface
    fun onLoadMoreCommentsRequested() {
        if (context == null) return
        webView.runInUiThread {
            if (!isWebViewReady()) return@runInUiThread
            commentsViewModel.loadMoreComments()
        }
    }

    @JavascriptInterface
    fun onCommentsSectionTapReceived(source: String?) {
        if (context == null) return
        webView.runInUiThread {
            if (!isWebViewReady()) return@runInUiThread
            val before = commentsSectionState.commentsState
            logCommentsSection(
                    "kotlin_bridge_called",
                    commentsBridgeFields(
                            eventSource = "onCommentsSectionTapReceived",
                            stateBefore = before,
                            extra = mapOf("source" to source.orEmpty())
                    )
            )
            requestCommentsExpand(source ?: "tap")
        }
    }

    private fun evalInlineCommentsExpandOnly() {
        if (!isWebViewReady()) return
        webView.runInUiThread {
            if (!isWebViewReady()) return@runInUiThread
            webView.evaluateJavascript(INLINE_COMMENTS_EXPAND_JS, null)
        }
    }

    private fun evalInlineCommentsCollapseOnly() {
        if (!isWebViewReady()) return
        webView.runInUiThread {
            if (!isWebViewReady()) return@runInUiThread
            webView.evaluateJavascript(INLINE_COMMENTS_COLLAPSE_JS, null)
        }
    }

    private fun scrollInlineCommentsSectionIntoView() {
        if (!isWebViewReady()) return
        webView.runInUiThread {
            if (!isWebViewReady()) return@runInUiThread
            webView.evaluateJavascript(
                """(function(){
                  var root = typeof newsInlineCommentsRoot === 'function' ? newsInlineCommentsRoot() : null;
                  if (root) root.scrollIntoView({block:'start', behavior:'smooth'});
                })();""",
                null
            )
        }
    }

    private fun patchCommentsCountIfDomReady(article: DetailsPage, source: String) {
        if (domContentLoadedRequestId <= 0) return
        val count = resolvedCommentsCount(article)
        if (count == 0) return
        applyCommentsDisplayCountInWebView(count, source)
    }

    private fun syncCommentsFooterCountFromModel(source: String = "model_sync") {
        if (!isWebViewReady()) return
        val article = hostFragment().currentArticle() ?: viewModel.uiState.value.article ?: return
        val count = resolvedCommentsCount(article)
        if (count == 0) return
        applyCommentsDisplayCountInWebView(count, source)
    }

    private fun reconcileCommentsDisplayCountFromState(state: ArticleCommentsState) {
        val parsedCount = when (state) {
            is ArticleCommentsState.Loaded -> state.totalCount.coerceAtLeast(state.comments.size)
            ArticleCommentsState.Empty -> 0
            else -> return
        }
        val article = hostFragment().currentArticle() ?: return
        if (parsedCount <= 0 && state !is ArticleCommentsState.Empty) return
        when {
            parsedCount > article.commentsCount ->
                hostFragment().provideChildInteractor().reconcileCommentsCountFromParsed(parsedCount)
            parsedCount > 0 && article.commentsCount > parsedCount -> {
                val skipReconcile = when (state) {
                    is ArticleCommentsState.Loaded -> ArticleCommentsPagination.shouldPreserveExpectedCount(
                            loadedCount = state.comments.size,
                            totalExpected = article.commentsCount,
                            paginatedSessionActive = state.canLoadMore ||
                                    ArticleCommentsPagination.hasMore(
                                            state.comments.size,
                                            article.commentsCount,
                                    ),
                    )
                    else -> false
                }
                if (!skipReconcile) {
                    hostFragment().provideChildInteractor().reconcileCommentsCountFromParsed(parsedCount)
                }
            }
        }
        if (state is ArticleCommentsState.Empty && article.commentsCount <= 0) return
        val reconciled = resolvedCommentsCount(hostFragment().currentArticle())
        if (reconciled > 0 || state is ArticleCommentsState.Empty) {
            applyCommentsDisplayCountInWebView(reconciled, "prefetch_reconcile")
        }
    }

    private fun applyCommentsDisplayCountInWebView(count: Int, source: String = "model_sync") {
        if (!isWebViewReady()) return
        logCommentsSection(
                "count_initial_patch",
                mapOf(
                        "count" to count,
                        "eventSource" to source,
                        "domReady" to (domContentLoadedRequestId > 0),
                )
        )
        webView.evaluateJavascript(
                """(function(){
                  var count = $count;
                  var sections = document.querySelectorAll('#news-comments-section');
                  var root = sections.length ? sections[0] : null;
                  if (root && typeof newsInlineCommentsUpdateCount === 'function') {
                    newsInlineCommentsUpdateCount(root, count);
                  }
                  if (typeof newsInlineCommentsUpdateHeaderCount === 'function') {
                    newsInlineCommentsUpdateHeaderCount(count);
                  }
                })();""",
                null
        )
    }

    private fun syncCommentsAfterWebViewReload() {
        lastInlineCommentsState = commentsViewModel.commentsState.value
        runCommentsCoordinator { commentsExpandCoordinator.syncVmState(lastInlineCommentsState) }
        bindInlineCommentsInWebView()
        syncCommentsFooterCountFromModel()
    }

    private fun verifyLoadedCommentsInDomOrReinject(
            state: ArticleCommentsState.Loaded,
            pass: Int = 0,
    ) {
        if (commentsSectionState.collapsed) {
            renderInlineCommentsUi(state)
            return
        }
        val expectedInDom = state.comments.size
        webView.evaluateJavascript(
                """(function(){
                  var list = document.querySelector('#news-inline-comments-list');
                  if (!list) return 'no_list';
                  var have = list.querySelectorAll('[data-news-comment-id]').length;
                  if (have <= 0) return 'empty';
                  return have >= $expectedInDom ? 'ok' : ('short:' + have);
                })();"""
        ) { raw ->
            if (!isWebViewReady() || commentsSectionState.collapsed) return@evaluateJavascript
            val status = raw?.trim()?.removeSurrounding("\"").orEmpty()
            if (status == "ok") {
                commentsVerifyPass = 0
                updateCommentsLoadMoreVisibility(state.canLoadMore, state.totalCount, state.comments.size)
                logCommentsSection(
                        "visible_confirmed",
                        commentsBridgeFields(
                                eventSource = "verify_loaded_dom",
                                extra = mapOf(
                                        "parsedCommentsCount" to state.comments.size,
                                        "domStatus" to status,
                                        "pass" to pass,
                                )
                        )
                )
                return@evaluateJavascript
            }
            if (pass == 0) {
                val generation = commentsExpandCoordinator.nextInjectGeneration()
                injectCommentsHtml(state, generation, 0)
            }
            if (pass < MAX_COMMENTS_VERIFY_PASSES) {
                webView.postDelayed({
                    verifyLoadedCommentsInDomOrReinject(state, pass + 1)
                }, COMMENTS_VERIFY_DELAY_MS)
            } else {
                scheduleCommentsInjectRetry(state, "verify_dom_$status")
            }
        }
    }

    private fun schedulePendingExpandWatchdog() {
        if (!::webView.isInitialized) return
        cancelPendingExpandWatchdog()
        val runnable = Runnable {
            if (!isWebViewReady()) return@Runnable
            val pending = commentsExpandCoordinator.current().pendingExpandSource ?: return@Runnable
            FpdaDebugLog.log(
                    FpdaDebugLog.TAG_COMMENTS_SECTION,
                    "expand_watchdog",
                    mapOf(
                            "articleId" to hostFragment().currentArticleId(),
                            "pendingSource" to pending,
                            "commentsJsReady" to commentsJsReady,
                            "footerInDom" to commentsFooterInDom,
                            "domReady" to (domContentLoadedRequestId > 0 && domContentLoadedRequestId == articleRequestId),
                    )
            )
            mountCommentsFooterIfMissing()
            commentsBindRetryCount = 0
            scheduleInlineCommentsBinding()
            runCommentsCoordinator { emptyList() }
        }
        pendingExpandWatchdogRunnable = runnable
        webView.postDelayed(runnable, PENDING_EXPAND_WATCHDOG_MS)
    }

    private fun cancelPendingExpandWatchdog() {
        pendingExpandWatchdogRunnable?.let {
            if (::webView.isInitialized) {
                webView.removeCallbacks(it)
            }
        }
        pendingExpandWatchdogRunnable = null
    }

    private fun scheduleCommentsInjectRetry(
            state: ArticleCommentsState.Loaded,
            reason: String,
            @Suppress("UNUSED_PARAMETER") scrollToCommentId: Int = 0,
    ) {
        if (commentsSectionState.collapsed || commentsInjectRetryCount >= MAX_COMMENTS_INJECT_RETRIES) {
            return
        }
        commentsInjectRetryCount++
        logCommentsSection(
                "inject_retry_scheduled",
                commentsBridgeFields(
                        eventSource = reason,
                        extra = mapOf(
                                "retryCount" to commentsInjectRetryCount,
                                "parsedCommentsCount" to state.comments.size,
                        )
                )
        )
        webView.postDelayed({
            if (!isWebViewReady() || commentsSectionState.collapsed) return@postDelayed
            scheduleInlineCommentsBinding()
            runCommentsCoordinator { commentsExpandCoordinator.injectRetryIfExpanded() }
        }, COMMENTS_INJECT_RETRY_MS)
    }

    /**
     * Fixes the intermittent «сломалась вёрстка» bug: sometimes the whole `${'$'}{style_type}_news.css`
     * / `_main.css` stops applying (video card, blockquote, comment cards render native/unstyled) while
     * the base theme from md_colors + inline overrides survives, and it used to heal only on app restart.
     * Root signal: `.news-comments-section .news-inline-comment` gets `border-radius: .75rem` ONLY from
     * `_news.css`; if the computed radius is ~0 the external stylesheets did not apply. When detected
     * (once per render dispatch, [styleHealAttempted]), re-apply ALL `<link>` stylesheets by cloning +
     * replacing each node, which forces the WebView to re-fetch and re-apply them — no restart needed.
     * NB: `s.cssRules` is cross-origin-blocked for file:// asset sheets under the https base, so it is
     * NOT a reliable "loaded" signal (always throws) — the computed radius is.
     */
    private fun probeArticleStyleState(reason: String) {
        if (!isWebViewReady()) return
        val script = """(function(){
            try {
                // Обе метки получают border-radius .75rem ТОЛЬКО из _news.css. Тоггл присутствует
                // всегда (даже когда комменты свёрнуты/грузятся/таймаутнули), поэтому ловим срыв стилей
                // и БЕЗ загруженных комментов — иначе тело статьи оставалось нестилизованным без лечения.
                var c = document.querySelector('.news-comments-section .news-inline-comment')
                     || document.querySelector('.news-comments-section .news-comments-toggle');
                if (!c) return JSON.stringify({applied: null});
                var cs = getComputedStyle(c);
                var radius = parseFloat(cs.borderTopLeftRadius) || 0;
                // _news.css задаёт .75rem (~9-12px). ~0 => правила не применились.
                var applied = radius >= 4;
                var hrefs = [];
                if (!applied) {
                    var links = document.querySelectorAll("link[rel='stylesheet'],link[rel=stylesheet]");
                    for (var i = 0; i < links.length; i++) {
                        hrefs.push((links[i].getAttribute('href')||'').split('/').slice(-2).join('/'));
                    }
                }
                return JSON.stringify({applied: applied, radius: radius, bg: cs.backgroundColor, hrefs: hrefs});
            } catch(e){ return 'probe_error:'+e; }
        })();"""
        webView.evaluateJavascript(script) { raw ->
            // Асинхронный колбэк: фрагмент мог отсоединиться от NewsDetailsFragment
            // → hostFragment() (parentFragment as ...) ронял NPE. Гейт как у прочих.
            if (!isWebViewReady()) return@evaluateJavascript
            val payload = raw?.trim()?.removeSurrounding("\"")?.replace("\\\"", "\"").orEmpty()
            val brokenStyles = payload.contains("\"applied\":false")
            val articleId = hostFragment().currentArticleId()
            if (styleHealArticleId != articleId) {
                styleHealArticleId = articleId
                styleHealCount = 0
            }
            if (brokenStyles && styleHealCount < STYLE_HEAL_MAX) {
                // Первопричина — гонка общего MiniTemplator: в HTML запёкся ПУСТОЙ ${'$'}{style_type},
                // ссылки стали `.../styles//_main.css` (нет папки light/dark) и не загрузились. Ре-рендер
                // не лечит (пути уже запечены). Чиним прямо в DOM: подставляем тип в сломанный href —
                // `/styles//` → `/styles/<type>/<type>`, WebView до-фетчивает валидный CSS и применяет.
                styleHealCount++
                val styleType = viewModel.uiState.value.styleType.takeIf { it == "light" || it == "dark" } ?: "light"
                FpdaDebugLog.warn(
                        FpdaDebugLog.TAG_ARTICLE_WEBVIEW,
                        "style_state_heal_href",
                        mapOf("reason" to reason, "articleId" to articleId, "attempt" to styleHealCount, "type" to styleType, "state" to payload)
                )
                val healScript = """(function(){
                    var links = document.querySelectorAll("link[rel='stylesheet'],link[rel=stylesheet]");
                    var fixed = 0;
                    for (var i = 0; i < links.length; i++) {
                        var h = links[i].getAttribute('href') || '';
                        if (h.indexOf('/styles//') !== -1) {
                            links[i].setAttribute('href', h.replace('/styles//', '/styles/$styleType/$styleType'));
                            fixed++;
                        }
                    }
                    return fixed;
                })();"""
                webView.evaluateJavascript(healScript, null)
            } else if (forpdateam.ru.forpda.BuildConfig.DEBUG) {
                FpdaDebugLog.log(
                        FpdaDebugLog.TAG_ARTICLE_WEBVIEW,
                        "style_state_probe",
                        mapOf("reason" to reason, "articleId" to hostFragment().currentArticleId(), "state" to payload)
                )
            }
        }
    }

    private fun injectCommentsHtml(
            state: ArticleCommentsState.Loaded,
            generation: Int,
            scrollToCommentId: Int,
    ) {
        if (!isWebViewReady() || commentsSectionState.collapsed) return
        if (!commentsExpandCoordinator.isInjectGenerationCurrent(generation)) {
            FpdaDebugLog.log(
                    FpdaDebugLog.TAG_COMMENTS_SECTION,
                    "inject_skipped_stale",
                    mapOf(
                            "articleId" to hostFragment().currentArticleId(),
                            "generation" to generation,
                            "webViewGeneration" to articleRequestId,
                    )
            )
            scheduleCommentsInjectRetry(state, "inject_skipped_stale", scrollToCommentId)
            return
        }
        val html = buildInlineCommentsHtml(state.comments)
        inlineComments = state.comments
        logCommentsSection(
                "render_started",
                commentsBridgeFields(
                        eventSource = "injectCommentsHtml",
                        stateAfter = with(CommentsExpandCoordinator) { state.toCommentsState() },
                        extra = mapOf(
                                "parsedCommentsCount" to state.comments.size,
                                "generatedCommentsHtmlSize" to html.length,
                                "generation" to generation,
                        )
                )
        )
        val script = """(function(){
            if (typeof newsInlineCommentsSetCollapsed === 'function') {
                newsInlineCommentsSetCollapsed(false, false);
            }
            if (typeof newsInlineCommentsInjectHtml !== 'function') return 'missing_fn';
            var root = typeof newsInlineCommentsRoot === 'function' ? newsInlineCommentsRoot() : null;
            if (!root && typeof newsInlineCommentsEnsureFooter === 'function') {
                newsInlineCommentsEnsureFooter(0, '', false);
                root = typeof newsInlineCommentsRoot === 'function' ? newsInlineCommentsRoot() : null;
            }
            if (root) root.setAttribute('data-fpda-webview-gen', '${articleRequestId}');
            return newsInlineCommentsInjectHtml(${JSONObject.quote(html)}, ${generation}, ${scrollToCommentId}, ${state.canLoadMore}, ${state.totalCount}, ${state.comments.size});
        })();"""
        webView.evaluateJavascript(script) { raw ->
            // Асинхронный колбэк: фрагмент мог отсоединиться от NewsDetailsFragment
            // → hostFragment() в commentsBridgeFields ронял NPE. Гейт как у прочих.
            if (!isWebViewReady()) return@evaluateJavascript
            val result = raw?.trim()?.removeSurrounding("\"").orEmpty()
            if (result == "ok") {
                commentsInjectRetryCount = 0
                updateCommentsLoadMoreVisibility(state.canLoadMore, state.totalCount, state.comments.size)
                logCommentsSection(
                        "render_success",
                        commentsBridgeFields(
                                eventSource = "injectCommentsHtml",
                                extra = mapOf(
                                        "parsedCommentsCount" to state.comments.size,
                                        "generation" to generation,
                                )
                        )
                )
                reassertPendingCommentScroll()
                probeArticleStyleState("render_success")
            } else {
                logCommentsSection(
                        "render_error",
                        commentsBridgeFields(
                                eventSource = "injectCommentsHtml",
                                extra = mapOf("domResult" to result, "generation" to generation)
                        )
                )
                if (result in COMMENTS_INJECT_RETRY_RESULTS) {
                    scheduleCommentsInjectRetry(state, "inject_dom_$result", scrollToCommentId)
                }
            }
        }
    }

    private fun appendCommentsHtml(
            state: ArticleCommentsState.Loaded,
            generation: Int,
    ) {
        if (commentsSectionState.collapsed) return
        if (!isWebViewReady()) {
            // WebView not ready yet (e.g. append arrived while paused/reattaching): don't drop the
            // new comment silently — retry, mirroring injectCommentsHtml, so it lands once ready.
            scheduleCommentsInjectRetry(state, "append_webview_not_ready")
            return
        }
        val fromIndex = state.appendFromIndex.coerceIn(0, state.comments.size)
        if (fromIndex >= state.comments.size) return
        val batch = state.comments.subList(fromIndex, state.comments.size)
        if (batch.isEmpty()) return
        if (!commentsExpandCoordinator.isInjectGenerationCurrent(generation)) {
            scheduleCommentsInjectRetry(state, "append_skipped_stale")
            return
        }
        val html = buildInlineCommentsHtml(batch)
        inlineComments = state.comments
        logCommentsSection(
                "render_append_started",
                commentsBridgeFields(
                        eventSource = "appendCommentsHtml",
                        extra = mapOf(
                                "appendFromIndex" to fromIndex,
                                "appendedCount" to batch.size,
                                "renderedCount" to state.comments.size,
                                "generation" to generation,
                        )
                )
        )
        val script = """(function(){
            if (typeof newsInlineCommentsAppendHtml !== 'function') return 'missing_fn';
            return newsInlineCommentsAppendHtml(${JSONObject.quote(html)}, ${generation}, ${state.canLoadMore}, ${state.totalCount}, ${state.comments.size});
        })();"""
        webView.evaluateJavascript(script) { raw ->
            // Асинхронный колбэк: фрагмент мог отсоединиться от NewsDetailsFragment
            // → hostFragment() в commentsBridgeFields ронял NPE. Гейт как у прочих.
            if (!isWebViewReady()) return@evaluateJavascript
            val result = raw?.trim()?.removeSurrounding("\"").orEmpty()
            if (result == "ok") {
                updateCommentsLoadMoreVisibility(state.canLoadMore, state.totalCount, state.comments.size)
                logCommentsSection(
                        "batch_appended",
                        commentsBridgeFields(
                                eventSource = "appendCommentsHtml",
                                extra = mapOf(
                                        "appendedCount" to batch.size,
                                        "renderedCount" to state.comments.size,
                                        "canLoadMore" to state.canLoadMore,
                                )
                        )
                )
                logCommentsSection(
                        "render_append_success",
                        commentsBridgeFields(
                                eventSource = "appendCommentsHtml",
                                extra = mapOf(
                                        "appendedCount" to batch.size,
                                        "renderedCount" to state.comments.size,
                                )
                        )
                )
                reassertPendingCommentScroll()
            } else {
                logCommentsSection(
                        "render_append_error",
                        commentsBridgeFields(
                                eventSource = "appendCommentsHtml",
                                extra = mapOf("domResult" to result, "generation" to generation)
                        )
                )
                if (result in COMMENTS_INJECT_RETRY_RESULTS) {
                    scheduleCommentsInjectRetry(state, "append_dom_$result")
                }
            }
        }
    }

    @JavascriptInterface
    fun commentLike(id: String) {
        val comment = findInlineComment(id) ?: return
        webView.runInUiThread {
            if (!isWebViewReady()) return@runInUiThread
            if (!authHolder.get().isAuth()) {
                Utils.showNeedAuthDialog(requireContext(), router)
                return@runInUiThread
            }
            webView.evalJs("newsInlineCommentSetPending(${JSONObject.quote(comment.id.toString())}, true);")
            commentsViewModel.toggleLikeComment(comment)
        }
    }

    @JavascriptInterface
    fun commentMenu(id: String) {
        val comment = findInlineComment(id) ?: return
        webView.runInUiThread {
            if (!isWebViewReady()) return@runInUiThread
            showInlineCommentMenu(comment)
        }
    }

    @JavascriptInterface
    fun commentProfile(id: String) {
        val comment = findInlineComment(id) ?: return
        webView.runInUiThread {
            if (!isWebViewReady()) return@runInUiThread
            commentsViewModel.openProfile(comment)
        }
    }

    @JavascriptInterface
    fun commentReply(id: String) {
        val comment = findInlineComment(id) ?: return
        webView.runInUiThread {
            if (!isWebViewReady()) return@runInUiThread
            showReplyDialog(comment)
        }
    }

    private fun findInlineComment(id: String): Comment? {
        val commentId = id.toIntOrNull() ?: return null
        return commentsViewModel.findComment(commentId)
                ?: inlineComments.firstOrNull { it.id == commentId }
    }

    private fun maybeExpandInlineCommentsForDeepLink() {
        val targetCommentId = hostFragment().provideChildInteractor().initData.commentId
        if (targetCommentId <= 0 || !commentsSectionState.collapsed) return
        requestCommentsExpand("deep_link_comment")
    }

    private fun renderInlineCommentsUi(state: ArticleCommentsState) {
        lastInlineCommentsState = state
        if (commentsSectionState.collapsed) return
        FpdaDebugLog.log(
                FpdaDebugLog.TAG_COMMENTS_SECTION,
                "render_started",
                mapOf(
                        "articleId" to hostFragment().currentArticleId(),
                        "requestId" to ((state as? ArticleCommentsState.Loading)?.requestId ?: commentsSectionRequestId),
                        "state" to state::class.java.simpleName,
                        "collapsed" to commentsSectionState.collapsed
                )
        )
        when (state) {
            ArticleCommentsState.NotLoaded -> {
                inlineComments = emptyList()
                evalInlineCommentsState("not-loaded", "", null)
            }
            is ArticleCommentsState.Loading -> {
                if (inlineComments.isEmpty()) {
                    evalInlineCommentsState("loading", "Загружаем комментарии...", null)
                }
            }
            is ArticleCommentsState.Loaded -> {
                inlineComments = state.comments
                if (state.comments.isEmpty()) {
                    evalInlineCommentsState("empty", "Комментариев пока нет", null)
                }
                // Non-empty HTML is injected by InjectLoadedComments / AppendLoadedComments only.
            }
            ArticleCommentsState.Empty -> {
                inlineComments = emptyList()
                evalInlineCommentsState("empty", "Комментариев пока нет", null)
            }
            is ArticleCommentsState.Error -> {
                val message = ArticleCommentsUserMessage.forThrowable(state.throwable)
                evalInlineCommentsState("error", Html.escapeHtml(message), null)
            }
        }
        val event = when (state) {
            ArticleCommentsState.NotLoaded -> "section_rendered"
            is ArticleCommentsState.Loading -> "load_started"
            is ArticleCommentsState.Loaded -> "load_success"
            ArticleCommentsState.Empty -> "load_success"
            is ArticleCommentsState.Error -> "load_error"
        }
        FpdaDebugLog.log(
                FpdaDebugLog.TAG_COMMENTS_SECTION,
                event,
                mapOf(
                        "articleId" to hostFragment().currentArticleId(),
                        "requestId" to commentsSectionRequestId,
                        "stateAfter" to state::class.java.simpleName,
                        "collapsed" to commentsSectionState.collapsed,
                        "count" to hostFragment().currentArticle()?.commentsCount,
                        "renderedCount" to (state as? ArticleCommentsState.Loaded)?.comments?.size,
                        "error" to (state as? ArticleCommentsState.Error)?.throwable?.message,
                        "errorClass" to FpdaDebugLog.errorClass((state as? ArticleCommentsState.Error)?.throwable)
                )
        )
    }

    private fun updateCommentsLoadMoreVisibility(canLoadMore: Boolean, totalCount: Int, renderedCount: Int) {
        if (!isWebViewReady()) return
        val script = """(function(){
            if (typeof newsInlineCommentsUpdateLoadMore !== 'function') return;
            newsInlineCommentsUpdateLoadMore(${canLoadMore}, ${totalCount}, ${renderedCount});
        })();"""
        webView.runInUiThread {
            if (!isWebViewReady()) return@runInUiThread
            webView.evaluateJavascript(script, null)
        }
    }

    private fun handleCommentUiEvent(event: ArticleCommentUiEvent) {
        when (event) {
            is ArticleCommentUiEvent.ShowComments -> {
                inlineComments = event.comments
                val scrollId = event.scrollToCommentId
                if (event.revealSection) {
                    requestCommentsExpand("show_comments_event")
                }
                if (!commentsSectionState.collapsed && event.comments.isNotEmpty()) {
                    val expectedTotal = hostFragment().provideChildInteractor()
                            .expectedCommentsCount()
                            .coerceAtLeast(event.comments.size)
                    val loaded = ArticleCommentsState.Loaded(
                            comments = event.comments,
                            canLoadMore = ArticleCommentsPagination.hasMore(
                                    event.comments.size,
                                    expectedTotal,
                            ),
                            totalCount = expectedTotal,
                    )
                    injectCommentsHtml(loaded, commentsExpandCoordinator.nextInjectGeneration(), scrollId)
                }
                if (scrollId > 0) {
                    webView.postDelayed({ scrollInlineCommentIntoView(scrollId) }, 180L)
                    webView.postDelayed({ scrollInlineCommentIntoView(scrollId) }, 480L)
                }
            }
            is ArticleCommentUiEvent.ScrollToComment -> armPendingCommentScroll(event.commentId, event.index)
            is ArticleCommentUiEvent.ShowEditComment -> showEditCommentDialog(event.action, event.text)
            is ArticleCommentUiEvent.UpdateCommentLike -> {
                patchInlineCommentLike(
                        commentId = event.commentId,
                        likedByMe = event.likedByMe,
                        likeCount = event.likeCount,
                        pending = event.pending,
                )
            }
            ArticleCommentUiEvent.OnReplyComment -> {
                hostFragment().showInlineComments()
                requestCommentsExpand("reply_comment")
            }
            is ArticleCommentUiEvent.PatchComment -> patchInlineCommentNode(event.comment)
            ArticleCommentUiEvent.RefreshLoadMoreUi -> {
                val loaded = commentsViewModel.commentsState.value
                if (loaded is ArticleCommentsState.Loaded) {
                    updateCommentsLoadMoreVisibility(
                            loaded.canLoadMore,
                            loaded.totalCount,
                            loaded.comments.size,
                    )
                } else if (isWebViewReady()) {
                    webView.evaluateJavascript(
                            """(function(){
                                var root = typeof newsInlineCommentsRoot === 'function' ? newsInlineCommentsRoot() : null;
                                if (root) root.dataset.newsCommentsLoadingMore = "false";
                            })();""",
                            null
                    )
                }
            }
        }
    }

    private fun evalInlineCommentsState(
            state: String,
            message: String,
            html: String?,
            scrollToCommentId: Int = 0,
            canLoadMore: Boolean = false,
            totalCount: Int = 0,
            renderedCount: Int = 0,
            onComplete: (() -> Unit)? = null
    ) {
        if (!isWebViewReady()) {
            logCommentsSection("render_target_missing", commentsBridgeFields(eventSource = "evalInlineCommentsState"))
            return
        }
        val script = NewsWebViewScripts.inlineCommentsState(
                state,
                message,
                html,
                scrollToCommentId,
                canLoadMore,
                totalCount,
                renderedCount,
        )
        webView.runInUiThread {
            if (!isWebViewReady()) {
                logCommentsSection("render_target_missing", commentsBridgeFields(eventSource = "evalInlineCommentsState.posted"))
                return@runInUiThread
            }
            try {
                webView.evaluateJavascript(script) {
                    logCommentsSection(
                            "render_success",
                            commentsBridgeFields(
                                    eventSource = "evalInlineCommentsState",
                                    extra = mapOf("state" to state, "hasHtml" to (html != null))
                            )
                    )
                    if (state == "loaded" || state == "empty") {
                        logCommentsSection(
                                "visible_confirmed",
                                commentsBridgeFields(eventSource = "evalInlineCommentsState", extra = mapOf("state" to state))
                        )
                    }
                    onComplete?.invoke()
                }
            } catch (error: Throwable) {
                logCommentsSection(
                        "render_error",
                        commentsBridgeFields(
                                eventSource = "evalInlineCommentsState",
                                extra = mapOf(
                                        "errorClass" to FpdaDebugLog.errorClass(error),
                                        "errorMessage" to error.message
                                )
                        )
                )
                Timber.e(error, "Unable to update inline news comments")
            }
        }
    }

    private fun evalInlineCommentsState(state: ArticleCommentsState) {
        when (state) {
            ArticleCommentsState.NotLoaded -> evalInlineCommentsState("not-loaded", "", null)
            is ArticleCommentsState.Loading -> evalInlineCommentsState("loading", "Загружаем комментарии...", null)
            is ArticleCommentsState.Loaded -> {
                val message = if (state.comments.isEmpty()) "Нет комментариев" else ""
                evalInlineCommentsState(
                        "loaded",
                        message,
                        buildInlineCommentsHtml(state.comments),
                        0,
                        state.canLoadMore,
                        state.totalCount,
                        state.comments.size,
                )
            }
            ArticleCommentsState.Empty -> evalInlineCommentsState("empty", "Нет комментариев", null)
            is ArticleCommentsState.Error -> {
                val message = ArticleCommentsUserMessage.forThrowable(state.throwable)
                evalInlineCommentsState("error", Html.escapeHtml(message), null)
            }
        }
    }

    /**
     * Arms a durable scroll-to-comment target and scrolls now. The target is re-asserted by
     * [reassertPendingCommentScroll] after every subsequent comment render, so a late re-inject or a
     * slow batched reload can't strand the fresh comment at the top. Also used for deep-link/mention
     * navigation to a specific comment.
     */
    private fun armPendingCommentScroll(commentId: Int, index: Int = -1) {
        val targetId = commentId.takeIf { it > 0 }
                ?: inlineComments.getOrNull(index)?.id
                ?: return
        pendingCommentScrollId = targetId
        pendingCommentScrollDeadline = SystemClock.uptimeMillis() + PENDING_COMMENT_SCROLL_WINDOW_MS
        scrollInlineCommentIntoView(targetId, index)
    }

    /** Re-fires the pending scroll after a render completes, until the comment is reached or the
     *  short window expires. Tied to render events (not a timer), so it stops once comments settle. */
    private fun reassertPendingCommentScroll() {
        val id = pendingCommentScrollId
        if (id <= 0) return
        if (SystemClock.uptimeMillis() > pendingCommentScrollDeadline) {
            pendingCommentScrollId = 0
            return
        }
        scrollInlineCommentIntoView(id)
    }

    private fun scrollInlineCommentIntoView(commentId: Int, index: Int = -1) {
        val targetId = commentId.takeIf { it > 0 }
                ?: inlineComments.getOrNull(index)?.id
                ?: return
        if (!isWebViewReady()) return
        val script = "newsInlineCommentScrollIntoView(" + targetId + ");"
        webView.post {
            webView.evalJs(script)
            // Re-assert across a wider window: after posting a reply a full list re-inject can fire
            // a few hundred ms later and reset scroll to the top, so a single early scroll would be
            // overridden and the fresh comment appears "lost" at the top.
            longArrayOf(120L, 320L, 600L, 900L).forEach { delay ->
                webView.postDelayed({ webView.evalJs(script) }, delay)
            }
        }
    }

    private fun buildInlineCommentsHtml(comments: List<Comment>): String =
            NewsInlineCommentHtml.commentsHtml(
                    comments = comments,
                    editedHint = getString(R.string.comment_edited_hint),
                    deletedLabel = getString(R.string.comment_deleted),
                    replyLabel = getString(R.string.reply),
                    canReply = { commentsViewModel.canReply(it) },
                    hasMenu = { inlineCommentMenuItems(it).isNotEmpty() },
            )

    private fun patchInlineCommentLike(commentId: Int, likedByMe: Boolean, likeCount: Int, pending: Boolean) {
        if (!isWebViewReady()) return
        val script = """(function(){
            if (typeof newsInlineCommentUpdateLike !== 'function') return;
            newsInlineCommentUpdateLike(
                ${JSONObject.quote(commentId.toString())},
                ${if (likedByMe) "true" else "false"},
                $likeCount,
                ${if (pending) "true" else "false"}
            );
        })();"""
        webView.evalJs(script)
    }

    private fun patchInlineCommentNode(comment: Comment) {
        if (!isWebViewReady()) return
        val html = NewsInlineCommentHtml.commentHtml(
                comment = comment,
                editedHint = getString(R.string.comment_edited_hint),
                deletedLabel = getString(R.string.comment_deleted),
                replyLabel = getString(R.string.reply),
                canReply = commentsViewModel.canReply(comment),
                hasMenu = inlineCommentMenuItems(comment).isNotEmpty(),
        )
        val script = """(function(){
            if (typeof newsInlineCommentReplaceNode !== 'function') return;
            newsInlineCommentReplaceNode(${JSONObject.quote(comment.id.toString())}, ${JSONObject.quote(html)});
        })();"""
        webView.evalJs(script)
        inlineComments = inlineComments.map { if (it.id == comment.id) comment else it }
    }

    private fun showInlineCommentMenu(comment: Comment) {
        val items = inlineCommentMenuItems(comment)
        if (items.isEmpty()) {
            showSnackbar(R.string.comment_action_unavailable)
            return
        }
        MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.comment_actions_title)
                .setItems(items.map { it.title }.toTypedArray()) { _, which -> items[which].action() }
                .showWithStyledButtons()
    }

    private fun inlineCommentMenuItems(comment: Comment): List<InlineCommentMenuItem> {
        val auth = authHolder.get().isAuth()
        val authUserId = authHolder.get().userId
        val isOwnComment = ArticleCommentActionVisibility.isOwnCommentForActions(authUserId, comment)
        val actions = comment.actions
        logInlineCommentMenuDecision(comment, auth, authUserId, isOwnComment)
        return buildList {
            if (actions.profile?.isValid() == true || comment.userId > 0) {
                add(InlineCommentMenuItem(getString(R.string.comment_open_profile)) {
                    commentsViewModel.openProfile(comment)
                })
            }
            if (ArticleCommentActionVisibility.canShowEdit(auth, authUserId, comment)) {
                add(InlineCommentMenuItem(getString(R.string.edit)) {
                    commentsViewModel.loadEditCommentForm(comment)
                })
            }
            if (ArticleCommentActionVisibility.canShowDelete(auth, authUserId, comment)) {
                add(InlineCommentMenuItem(getString(R.string.delete)) {
                    showDeleteCommentDialog(comment)
                })
            }
            if (isOwnComment && comment.id > 0) {
                add(InlineCommentMenuItem(getString(R.string.copy_link)) {
                    copyCommentLink(comment)
                })
            }
            val karmaPlusAction = actions.karmaPlus
            if (auth && !isOwnComment && karmaPlusAction?.isValid() == true) {
                add(InlineCommentMenuItem(getString(R.string.comment_plus_karma)) {
                    commentsViewModel.executeCommentAction(karmaPlusAction)
                })
            }
            val hideAction = actions.hide
            if (auth && hideAction?.isValid() == true) {
                add(InlineCommentMenuItem(getString(R.string.comment_hide)) {
                    commentsViewModel.executeCommentAction(hideAction)
                })
            }
            if (auth && !isOwnComment && isRealReputationAction(actions.reputationPlus)) {
                add(InlineCommentMenuItem(getString(R.string.comment_reputation_plus)) {
                    showReasonActionDialog(actions.reputationPlus, R.string.comment_reputation_reason)
                })
            }
            if (auth && !isOwnComment && isRealReputationAction(actions.reputationMinus)) {
                add(InlineCommentMenuItem(getString(R.string.comment_reputation_minus)) {
                    showReasonActionDialog(actions.reputationMinus, R.string.comment_reputation_reason)
                })
            }
            if (auth && !isOwnComment && actions.report?.isValid() == true) {
                add(InlineCommentMenuItem(getString(R.string.report)) {
                    showReasonActionDialog(actions.report, R.string.comment_report_reason, "message")
                })
            }
        }
    }

    private fun logInlineCommentMenuDecision(comment: Comment, auth: Boolean, authUserId: Int, isOwnComment: Boolean) {
        if (!forpdateam.ru.forpda.BuildConfig.DEBUG) return
        if (!isOwnComment && comment.userId != authUserId) return
        val reason = ArticleCommentActionVisibility.editHiddenReason(auth, authUserId, comment)
        Timber.d(
                "NewsInlineComments menu id=%d authorId=%d authUserId=%d isOwn=%s hasEdit=%s hasDelete=%s hasRep=%s source=%s editHidden=%s",
                comment.id,
                comment.userId,
                authUserId,
                isOwnComment,
                comment.actions.edit?.isValid() == true,
                comment.actions.delete?.isValid() == true,
                ArticleCommentActionVisibility.hasReputationAction(comment),
                if (comment.actions.edit?.isValid() == true || comment.actions.delete?.isValid() == true) "mobile/desktop merged" else "mobile",
                reason ?: "shown"
        )
    }

    private fun isRealReputationAction(action: Comment.Action?): Boolean =
            action?.isValid() == true &&
                    (action.fields["act"]?.equals("rep", ignoreCase = true) == true ||
                            action.url.orEmpty().contains("act=rep", ignoreCase = true))

    private fun showReasonActionDialog(action: Comment.Action?, titleRes: Int, fieldName: String? = null) {
        if (!authHolder.get().isAuth()) {
            Utils.showNeedAuthDialog(requireContext(), router)
            return
        }
        if (action?.isValid() != true) return
        val input = EditText(requireContext()).apply {
            minLines = 3
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
        }
        MaterialAlertDialogBuilder(requireContext())
                .setTitle(titleRes)
                .setView(input)
                .setPositiveButton(R.string.send) { _, _ ->
                    val reasonField = fieldName ?: action.reasonFieldName ?: "message"
                    commentsViewModel.executeCommentAction(action, mapOf(reasonField to input.text.toString()))
                }
                .setNegativeButton(R.string.cancel, null)
                .showWithStyledButtons()
    }

    private fun showReplyDialog(comment: Comment) {
        if (!authHolder.get().isAuth()) {
            Utils.showNeedAuthDialog(requireContext(), router)
            return
        }
        if (!commentsViewModel.canReply(comment)) {
            showSnackbar(R.string.comment_action_unavailable)
            return
        }
        val input = EditText(requireContext()).apply {
            minLines = 3
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            setText("${comment.userNick},\n")
            setSelection(text.length)
        }
        MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.reply)
                .setView(input)
                .setPositiveButton(R.string.send) { _, _ ->
                    commentsViewModel.replyComment(comment.id, input.text.toString())
                }
                .setNegativeButton(R.string.cancel, null)
                .showWithStyledButtons()
    }

    private fun showEditCommentDialog(action: Comment.Action, text: String) {
        val input = EditText(requireContext()).apply {
            minLines = 3
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            setText(forpdateam.ru.forpda.model.data.remote.api.ApiUtils.spannedFromHtml(text).toString())
            setSelection(this.text.length)
        }
        MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.edit)
                .setView(input)
                .setPositiveButton(R.string.send) { _, _ ->
                    commentsViewModel.editComment(action, input.text.toString())
                }
                .setNegativeButton(R.string.cancel, null)
                .showWithStyledButtons()
    }

    private fun showDeleteCommentDialog(comment: Comment) {
        val action = comment.actions.delete?.takeIf { it.isValid() } ?: return
        if (!authHolder.get().isAuth()) {
            Utils.showNeedAuthDialog(requireContext(), router)
            return
        }
        MaterialAlertDialogBuilder(requireContext())
                .setMessage(R.string.comment_delete_confirm)
                .setPositiveButton(R.string.delete) { _, _ ->
                    commentsViewModel.deleteComment(action)
                }
                .setNegativeButton(R.string.cancel, null)
                .showWithStyledButtons()
    }

    private fun copyCommentLink(comment: Comment) {
        val articleId = hostFragment().currentArticleId().takeIf { it > 0 } ?: return
        clipboardHelper.copyToClipboard("https://4pda.to/index.php?p=$articleId#comment-${comment.id}")
        showSnackbar(R.string.link_copied)
    }

    @JavascriptInterface
    fun sendPoll(id: String, answer: String, from: String, token: String) {
        if (context == null)
            return
        webView.runInUiThread {
            if (!isWebViewReady()) return@runInUiThread
            val pollId = id.toIntOrNull()
            if (pollId == null) {
                sendPollError(id, "Некорректный идентификатор опроса")
                return@runInUiThread
            }
            if (!isTrustedNewsPollToken(pollId, token)) {
                Timber.w("Rejected news poll vote: pollId=%s hasToken=%s", id, token.isNotBlank())
                sendPollError(id, "Не удалось подтвердить опрос")
                return@runInUiThread
            }
            val answersId = answer
                    .split(",".toRegex())
                    .mapNotNull { it.toIntOrNull() }
                    .toIntArray()
            if (answersId.isEmpty()) {
                sendPollError(id, "Выберите вариант ответа")
                return@runInUiThread
            }
            FpdaDebugLog.log(
                    FpdaDebugLog.TAG_ARTICLE_POLL,
                    "vote_submit_start",
                    mapOf(
                            "articleId" to hostFragment().currentArticleId(),
                            "pollId" to pollId,
                            "answersCount" to answersId.size,
                            "hasToken" to token.isNotBlank(),
                            "authenticated" to authHolder.get().isAuth()
                    )
            )
            viewModel.votePoll(
                    from = from,
                    pollId = pollId,
                    answersId = answersId,
                    onSuccess = { html ->
                        FpdaDebugLog.log(
                                FpdaDebugLog.TAG_ARTICLE_POLL,
                                "vote_submit_result",
                                mapOf(
                                        "articleId" to hostFragment().currentArticleId(),
                                        "pollId" to pollId,
                                        "voteSubmitResult" to "success",
                                        "responseLen" to html.length,
                                        "hasNormalizedPoll" to html.contains("news-poll-normalized", ignoreCase = true)
                                )
                        )
                        webView.runInUiThread {
                            if (isWebViewReady()) {
                                webView.evalJs(
                                        "onNewsPollVoteSuccess(" +
                                                JSONObject.quote(pollId.toString()) +
                                                "," +
                                                JSONObject.quote(html) +
                                                ");"
                                )
                                bindNewsPollsInWebView()
                            }
                        }
                    },
                    onError = { message ->
                        FpdaDebugLog.log(
                                FpdaDebugLog.TAG_ARTICLE_POLL,
                                "vote_submit_result",
                                mapOf(
                                        "articleId" to hostFragment().currentArticleId(),
                                        "pollId" to pollId,
                                        "voteSubmitResult" to "error",
                                        "message" to message
                                )
                        )
                        sendPollError(pollId.toString(), message)
                    }
            )
        }
    }

    private fun isTrustedNewsPollToken(pollId: Int, token: String): Boolean {
        val html = currentTrustedArticleHtml.orEmpty()
        if (token.isBlank() || html.isBlank()) return false
        return html.contains("""data-news-poll-token="${htmlAttrToken(token)}"""") &&
                (html.contains("poll_id=$pollId") ||
                        html.contains("""name="poll_id" value="$pollId"""") ||
                        html.contains("""name="poll" value="$pollId""""))
    }

    private fun htmlAttrToken(token: String): String =
            token
                    .replace("&", "&amp;")
                    .replace("\"", "&quot;")
                    .replace("<", "&lt;")
                    .replace(">", "&gt;")

    private fun attachNewsBridge() {
        if (!newsBridgeAttached && ::webView.isInitialized) {
            webView.addJavascriptInterface(this, JS_INTERFACE)
            newsBridgeAttached = true
            runCommentsCoordinator { emptyList() }
        }
    }

    private fun detachNewsBridge() {
        if (newsBridgeAttached) {
            webView.removeJavascriptInterface(JS_INTERFACE)
            newsBridgeAttached = false
        }
    }

    private fun sendPollError(pollId: String, message: String) {
        webView.runInUiThread {
            if (!isWebViewReady()) return@runInUiThread
            webView.evalJs(
                    "onNewsPollVoteError(" +
                            JSONObject.quote(pollId) +
                            "," +
                            JSONObject.quote(message) +
                            ");"
            )
        }
    }

    override fun onResume() {
        super.onResume()
        if (::webView.isInitialized) {
            webView.onResume()
            // Reattaching the WebView (onAttachedToWindow) resets isJsReady to false while the
            // already-rendered document and its JS context survive, and no new domContentLoaded
            // fires. Re-acknowledge when we can prove the body is still rendered so evalJs-gated
            // theme/font/comment calls keep working after returning to the screen.
            if (hasConfirmedArticleRender() && webView.contentHeight > WEBVIEW_BLANK_CONTENT_HEIGHT_THRESHOLD) {
                webView.acknowledgeJsBridgeFromNativeProbe()
            }
            reconcileInlineCommentsOnResume()
        }
    }

    /**
     * A comment sent right before the screen was backgrounded can finish loading after the
     * lifecycle collector was already cancelled, so its render never reached the DOM. On return,
     * reconcile the WebView list against the latest Loaded state (count-checked, idempotent) so the
     * new comment shows without a manual refresh.
     */
    private fun reconcileInlineCommentsOnResume() {
        if (commentsSectionState.collapsed) return
        val loaded = commentsViewModel.commentsState.value as? ArticleCommentsState.Loaded ?: return
        if (loaded.comments.isEmpty()) return
        webView.postDelayed({
            if (!isWebViewReady() || commentsSectionState.collapsed) return@postDelayed
            verifyLoadedCommentsInDomOrReinject(loaded)
        }, COMMENTS_VERIFY_DELAY_MS)
    }

    override fun onPause() {
        if (::webView.isInitialized && isWebViewReady()) {
            readingProgressController.onScrollChanged(webView.scrollY)
        }
        super.onPause()
        webView.onPause()
    }

    @JavascriptInterface
    fun openImage(url: String) {
        webView.runInUiThread {
            if (!isWebViewReady()) return@runInUiThread
            val viewerUrl = FourPdaImageUrls.resolveViewerUrl(url)
            ImageViewerActivity.startActivity(webView.context, viewerUrl)
        }
    }

    @JavascriptInterface
    fun openExternalBrowser(url: String) {
        webView.runInUiThread {
            if (!isWebViewReady()) return@runInUiThread
            openExternalBrowserOnly(url)
        }
    }

    @JavascriptInterface
    fun openArticleLink(url: String) {
        webView.runInUiThread {
            if (!isWebViewReady()) return@runInUiThread
            val safeUrl = ArticleLinkResolver.resolveForNavigation(url) ?: return@runInUiThread
            linkHandler.handle(safeUrl, router)
        }
    }

    @JavascriptInterface
    fun playVideoInArticle(videoId: String) {
        webView.runInUiThread {
            if (!isWebViewReady()) return@runInUiThread
            embedYoutubeVideoInArticle(videoId)
        }
    }

    @JavascriptInterface
    fun openVideo(url: String) {
        webView.runInUiThread {
            if (!isWebViewReady()) return@runInUiThread
            val safeUrl = NewsArticleUrls.normalizeExternal(url) ?: return@runInUiThread
            val youtubeVideoId = YouTubeUrl.extractVideoId(safeUrl)
            if (youtubeVideoId != null && YouTubeLauncher.openApp(webView.context, youtubeVideoId)) {
                return@runInUiThread
            }
            openExternalBrowserOnly(safeUrl)
        }
    }

    private fun embedYoutubeVideoInArticle(videoId: String) {
        val safeId = videoId.trim()
        if (!YOUTUBE_VIDEO_ID_REGEX.matches(safeId)) return
        webView.evalJs(
                """(function(){
                  var card=document.querySelector('.news-video-card[data-video-id=${JSONObject.quote(safeId)}]');
                  if(card&&typeof renderYoutubePlayer==='function'){renderYoutubePlayer(card);}
                })();"""
        )
    }

    @JavascriptInterface
    fun openTaxonomy(url: String) {
        webView.runInUiThread {
            if (!isWebViewReady()) return@runInUiThread
            val safeUrl = NewsArticleUrls.normalizeTaxonomy(url)
            if (safeUrl == null) {
                Timber.w("Blocked unsafe article taxonomy URL")
                return@runInUiThread
            }
            openExternalBrowserOnly(safeUrl)
        }
    }

    private fun openExternalBrowserOnly(url: String) {
        val context = webView.context
        val safeUrl = NewsArticleUrls.normalizeExternal(url) ?: return
        try {
            ExternalBrowserLauncher.open(context, safeUrl)
        } catch (e: Exception) {
            Timber.w(e, "Unable to open external browser for %s", safeUrl)
            Toast.makeText(context, R.string.error_occurred, Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * WebViewClient для новостей:
     * — перехватывает ссылки → linkHandler
     * — изображения обрабатываются через JS-интерфейс (openImage)
     */
    private inner class ArticleWebViewClient : CustomWebViewClient(avatarRepository, linkHandler, systemLinkHandler) {

        override fun handleUri(view: WebView, uri: Uri): Boolean {
            val url = uri.toString()
            // Внутренние asset-ресурсы не перехватываем
            if (url.startsWith("file:///android_asset")) return false
            // Разрешаем только контролируемые YouTube embed URL, которые
            // генерируются локальным шаблоном статьи.
            if (isTrustedYoutubeEmbedUrl(uri)) return false
            YouTubeUrl.extractVideoId(url)?.let { videoId ->
                embedYoutubeVideoInArticle(videoId)
                return true
            }
            val resolved = ArticleLinkResolver.resolveForNavigation(url) ?: return true
            linkHandler.handle(resolved, router)
            return true
        }

        override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
            super.onPageStarted(view, url, favicon)
            if (isTrustedArticlePageUrl(url)) {
                renderPageStartedRequestId = articleRequestId
            }
            if (!isTrustedArticlePageUrl(url)) {
                Timber.w("Removing INews bridge for unexpected article WebView URL: %s", url)
                currentTrustedArticleHtml = null
                detachNewsBridge()
            }
        }

        override fun onPageFinished(view: WebView, url: String) {
            super.onPageFinished(view, url)
            if (!isAdded) return
            if (isTrustedArticlePageUrl(url)) {
                attachNewsBridge()
                val requestId = articleRequestId
                if (domContentLoadedRequestId != requestId && !articleRenderErrorShown) {
                    renderPageFinishedRequestId = requestId
                    hostFragment().clearArticleWebViewPendingRenderIfDisplayed()
                    confirmArticleRenderFromWebView(requestId, "page_finished")
                }
            }
            // Вешаем обработчик кликов по изображениям после загрузки DOM и
            // фиксируем размеры lazy-media, которые появились уже после шаблона.
            // Приоритет: data-full-src > parent<a data-lightbox href> > data-src > currentSrc > src
            // Также убираем WordPress-суффикс размера (-NNNxNNN) для получения оригинала
            view.evaluateJavascript(
                """(function(){
                var wpSuffix=/-\d+x\d+(?=\.[^.]+$)/gi;
                function stripWp(u){return u?u.replace(wpSuffix,''):u;}
                function stabilize(el){
                  if(!el)return;
                  el.classList.add('app-stable-media');
                  if(!el.style.aspectRatio){
                    var w=parseInt(el.getAttribute('width')||el.naturalWidth||0,10);
                    var h=parseInt(el.getAttribute('height')||el.naturalHeight||0,10);
                    if(w>0&&h>0) el.style.aspectRatio=(w/h).toFixed(4);
                    else el.style.aspectRatio='1.7778';
                  }
                  el.style.width='100%';
                  el.style.height='auto';
                }
                var imgs=document.querySelectorAll('img');
                for(var i=0;i<imgs.length;i++){
                  (function(img){
                    stabilize(img);
                    img.style.cursor='pointer';
                    img.addEventListener('click',function(e){
                      e.preventDefault();e.stopPropagation();
                      var src=null;
                      // 1. Проверяем data-full-src (полное изображение)
                      src=img.getAttribute('data-full-src');
                      if(src){INews.openImage(stripWp(src));return;}
                      // 1b. data-preview (полный URL вложения, как в теме форума)
                      src=img.getAttribute('data-preview');
                      if(src){INews.openImage(stripWp(src));return;}
                      // 2. Проверяем родительский <a data-lightbox>
                      var parent=img.parentElement;
                      while(parent){
                        if(parent.tagName==='A'&&parent.hasAttribute('data-lightbox')){
                          var href=parent.getAttribute('href');
                          if(href){INews.openImage(stripWp(href));return;}
                        }
                        parent=parent.parentElement;
                      }
                      // 3. Проверяем data-src (lazy loading)
                      src=img.getAttribute('data-src');
                      if(src){INews.openImage(stripWp(src));return;}
                      // 4. Проверяем data-original
                      src=img.getAttribute('data-original');
                      if(src){INews.openImage(stripWp(src));return;}
                      // 5. Берем текущий src или currentSrc
                      src=img.currentSrc||img.src;
                      if(src){INews.openImage(stripWp(src));}
                    });
                  })(imgs[i]);
                }
                var iframes=document.querySelectorAll('iframe');
                for(var j=0;j<iframes.length;j++) stabilize(iframes[j]);
                })();""",
                null
            )
            view.evaluateJavascript(
                    """(function(){
                      try{
                        if(typeof newsVideoCardsPruneDuplicates==='function'){newsVideoCardsPruneDuplicates();}
                        if(typeof bindVideoCards==='function'){bindVideoCards();}
                        if(typeof bindArticleContentLinks==='function'){bindArticleContentLinks();}
                        else if(typeof bindArticleTargetBlankLinks==='function'){bindArticleTargetBlankLinks();}
                      }catch(e){}
                    })();""",
                    null
            )
            syncCommentsAfterWebViewReload()
            // No auto-load here: comments are manual by design.
        }

        override fun onMainFrameLoadError(
                view: WebView,
                request: android.webkit.WebResourceRequest?,
                errorCode: Int,
                description: String?
        ) {
            if (!isAdded || !isWebViewReady()) return
            renderTimeoutRunnable?.let(webView::removeCallbacks)
            FpdaDebugLog.warn(
                    FpdaDebugLog.TAG_ARTICLE_WEBVIEW,
                    "main_frame_error",
                    mapOf(
                            "articleId" to (inflightRenderArticleId.takeIf { it > 0 }
                                    ?: hostFragment().currentArticleId()),
                            "requestId" to articleRequestId,
                            "errorCode" to errorCode,
                            "description" to description?.take(120)
                    )
            )
            showArticleErrorPlaceholder(getString(R.string.error_occurred))
        }

        override fun onMainFrameHttpError(
                view: WebView,
                request: android.webkit.WebResourceRequest?,
                statusCode: Int
        ) {
            if (!isAdded || !isWebViewReady() || statusCode in 200..299) return
            renderTimeoutRunnable?.let(webView::removeCallbacks)
            FpdaDebugLog.warn(
                    FpdaDebugLog.TAG_ARTICLE_WEBVIEW,
                    "main_frame_http_error",
                    mapOf(
                            "articleId" to (inflightRenderArticleId.takeIf { it > 0 }
                                    ?: hostFragment().currentArticleId()),
                            "requestId" to articleRequestId,
                            "statusCode" to statusCode
                    )
            )
            showArticleErrorPlaceholder(getString(R.string.error_occurred))
        }

        override fun onRenderProcessGone(view: WebView, detail: android.webkit.RenderProcessGoneDetail): Boolean {
            if (!isAdded) return true
            Timber.w(
                    "Article WebView render process gone: didCrash=%s priorityAtExit=%d",
                    detail.didCrash(),
                    detail.rendererPriorityAtExit()
            )
            ArticleOpenTrace.log(
                    articleId = lastDomConfirmedArticleId,
                    requestId = articleRequestId,
                    generation = hostFragment().provideChildInteractor().currentArticleGeneration(),
                    phase = "webview_render_process_gone",
                    reason = if (detail.didCrash()) "crash" else "killed"
            )
            showArticleErrorPlaceholder("WebView перезапущен. Нажмите «Повторить».")
            return true
        }
    }

    private fun mountCommentsFooterIfMissing() {
        if (!isWebViewReady()) return
        val article = hostFragment().currentArticle() ?: viewModel.uiState.value.article ?: return
        val count = resolvedCommentsCount(article)
        val footerHtml = articleTemplate.commentsFooterHtml(
                commentsCount = count,
                hasCommentsSource = !article.commentsSource.isNullOrBlank()
        )
        if (footerHtml.isBlank()) return
        webView.evaluateJavascript(
                buildCommentsFooterPatchScript(count, footerHtml),
        ) {
            syncCommentsFooterCountFromModel("footer_mounted")
            scheduleInlineCommentsBinding()
        }
    }

    private fun buildCommentsFooterPatchScript(commentsCount: Int, footerHtml: String): String {
        val footerJson = JSONObject.quote(footerHtml)
        val collapsed = commentsSectionState.collapsed
        val generation = articleRequestId
        return """(function(){
                  var count = $commentsCount;
                  var footerHtml = $footerJson;
                  var collapsed = $collapsed;
                  var generation = $generation;
                  if (typeof newsInlineCommentsEnsureFooter === 'function') {
                    newsInlineCommentsEnsureFooter(count, footerHtml, collapsed);
                    return;
                  }
                  var prune = typeof newsInlineCommentsPruneDuplicates === 'function'
                    ? newsInlineCommentsPruneDuplicates
                    : function () {
                        var roots = document.querySelectorAll('#news-comments-section');
                        if (!roots || roots.length <= 1) return;
                        var canonical = roots[0];
                        for (var i = 1; i < roots.length; i++) {
                          var node = roots[i];
                          if (node && node !== canonical && node.parentNode) {
                            node.parentNode.removeChild(node);
                          }
                        }
                      };
                  prune();
                  var sections = document.querySelectorAll('#news-comments-section');
                  var root = sections.length ? sections[0] : null;
                  if (!root && footerHtml) {
                    var host = document.querySelector('#news') || document.body;
                    if (host) {
                      var wrap = document.createElement('div');
                      wrap.innerHTML = footerHtml;
                      var script = host.querySelector('script');
                      while (wrap.firstChild) {
                        if (script) host.insertBefore(wrap.firstChild, script);
                        else host.appendChild(wrap.firstChild);
                      }
                    }
                    prune();
                    sections = document.querySelectorAll('#news-comments-section');
                    root = sections.length ? sections[0] : null;
                  }
                  if (!root) return;
                  root.setAttribute('data-fpda-webview-gen', String(generation));
                  root.setAttribute('data-comments-count', String(count));
                  var toggle = root.querySelector('#news-comments-toggle');
                  var title = toggle ? toggle.querySelector('.news-comments-toggle-title') : null;
                  if (title) {
                    var base = (title.textContent || '').replace(/\s*(\(\d+\)|\(\?\))\s*$/, '').trim();
                    if (!base) base = 'Комментарии';
                    var suffix = count > 0 ? ' (' + count + ')' : (count < 0 ? ' (?)' : '');
                    title.textContent = base + suffix;
                  }
                  if (typeof newsInlineCommentsUpdateHeaderCount === 'function') {
                    newsInlineCommentsUpdateHeaderCount(count);
                  }
                  prune();
                  if (typeof bindCommentsSection === 'function') {
                    bindCommentsSection(collapsed, root.getAttribute('data-state') || 'not-loaded');
                  } else if (typeof bindNewsInlineCommentsLoad === 'function') {
                    bindNewsInlineCommentsLoad();
                  }
                })();"""
    }

    companion object {
        const val JS_INTERFACE = "INews"
        private const val TRUSTED_ARTICLE_BASE_URL = ArticleLinkResolver.ARTICLE_WEBVIEW_BASE_URL
        private const val WEBVIEW_BLANK_CONTENT_HEIGHT_THRESHOLD = 4
        private const val MAX_BLANK_RENDER_RETRIES = 2
        private const val MAX_LAYOUT_CONFIRM_ATTEMPTS = 8
        private const val LAYOUT_CONFIRM_DELAY_MS = 100L
        private const val BLANK_RENDER_VERIFY_DELAY_MS = 150L
        private const val BLANK_ESCALATION_DEBOUNCE_MS = 450L
        private const val MAX_UNPAINTED_RECOVERY_ATTEMPTS = 10
        private const val UNPAINTED_RECOVERY_BASE_DELAY_MS = 120L
        private const val UNPAINTED_WEBVIEW_RESET_ATTEMPT = 5
        // Max full re-renders to recover from broken article CSS (see probeArticleStyleState).
        private const val STYLE_HEAL_MAX = 2
        private const val COMMENTS_BIND_RETRY_MS = 120L
        private const val COMMENTS_INJECT_RETRY_MS = 150L
        private const val MAX_COMMENTS_BIND_RETRIES = 12
        private const val MAX_COMMENTS_INJECT_RETRIES = 4
        private val COMMENTS_INJECT_RETRY_RESULTS = setOf(
                "missing_fn",
                "stale",
                "collapsed",
                "no_root",
                "no_list",
        )
        private const val COMMENTS_VERIFY_DELAY_MS = 180L
        // How long a scroll-to-new-comment target stays live across re-renders after a post/reply.
        // Long enough to survive a slow batched reload + late re-inject, short enough to stop fighting
        // the user once comments settle.
        private const val PENDING_COMMENT_SCROLL_WINDOW_MS = 4_000L
        private const val MAX_COMMENTS_VERIFY_PASSES = 5
        private const val PENDING_EXPAND_WATCHDOG_MS = 2_500L
        private const val WEBVIEW_RENDER_SOFT_TIMEOUT_MS = 6_000L
        private const val WEBVIEW_STALLED_LOAD_MS = 3_000L
        private const val WEBVIEW_RENDER_EARLY_RELOAD_MS = 4_000L
        private const val WEBVIEW_RENDER_HARD_TIMEOUT_MS = 30_000L
        private const val WEBVIEW_RENDER_PROBE_INTERVAL_MS = 2_000L
        private const val INLINE_COMMENTS_EXPAND_JS = """(function(){
                  var root=document.querySelector('#news-comments-section');
                  if(typeof newsInlineCommentsSetCollapsed==='function'){
                    newsInlineCommentsSetCollapsed(false,false,root||undefined);
                    return;
                  }
                  if(!root)return;
                  root.setAttribute('data-collapsed','false');
                  var body=root.querySelector('#news-comments-body');
                  if(body)body.hidden=false;
                  var toggle=root.querySelector('#news-comments-toggle');
                  if(toggle)toggle.setAttribute('aria-expanded','true');
                  var list=root.querySelector('#news-inline-comments-list');
                  if(list)list.style.display='';
                })();"""
        private const val INLINE_COMMENTS_COLLAPSE_JS = """(function(){
                  var root=document.querySelector('#news-comments-section');
                  if(typeof newsInlineCommentsSetCollapsed==='function'){
                    newsInlineCommentsSetCollapsed(true,false,root||undefined);
                    return;
                  }
                  if(!root)return;
                  root.setAttribute('data-collapsed','true');
                  var body=root.querySelector('#news-comments-body');
                  if(body)body.hidden=true;
                  var toggle=root.querySelector('#news-comments-toggle');
                  if(toggle)toggle.setAttribute('aria-expanded','false');
                })();"""
        private const val ARTICLE_ERROR_PLACEHOLDER_HTML = """
            <html><head><meta name="viewport" content="width=device-width, initial-scale=1"/></head>
            <body style="font-family:system-ui,sans-serif;padding:16px;">
            <h3 style="margin:0 0 8px 0;">Не удалось загрузить новость</h3>
            <div style="opacity:.8;margin:0 0 12px 0;">Потяните вниз, чтобы обновить, или попробуйте ещё раз.</div>
            <button type="button" style="padding:10px 14px;border-radius:10px;border:1px solid rgba(127,127,127,.35);background:transparent;"
                onclick="try{INews.retryOpenArticle();}catch(e){}">Повторить</button>
            </body></html>
        """

        private fun isTrustedArticlePageUrl(url: String?): Boolean {
            if (url.isNullOrBlank()) return true
            return url == TRUSTED_ARTICLE_BASE_URL ||
                    url == "about:blank" ||
                    url.startsWith("file:///android_asset")
        }

        private fun isTrustedYoutubeEmbedUrl(uri: Uri): Boolean {
            val scheme = uri.scheme?.lowercase()
            val host = uri.host?.lowercase()
            val pathSegments = uri.pathSegments
            if (scheme != "https") return false
            if (host != "www.youtube-nocookie.com" && host != "youtube-nocookie.com") return false
            if (pathSegments.size < 2 || pathSegments[0] != "embed") return false
            return YOUTUBE_VIDEO_ID_REGEX.matches(pathSegments[1])
        }

        private val YOUTUBE_VIDEO_ID_REGEX = Regex("""^[A-Za-z0-9_-]{11}$""")
    }

    private data class InlineCommentMenuItem(
            val title: String,
            val action: () -> Unit
    )

    private fun injectRequestIdScript(html: String, requestId: Int): String {
        val script = """
            <script>(function(){
              try{
                var id=${requestId};
                var notified=false;
                function notify(){
                  if(notified) return;
                  notified=true;
                  try{ if(window.INews && INews.onArticleDomContentLoaded) INews.onArticleDomContentLoaded(String(id)); }catch(e){}
                }
                function hasVisibleBody(){
                  var body=document.body;
                  if(!body) return false;
                  var content=body.querySelector('body#news .content, body#news div.content, div.content');
                  if(!content) return false;
                  var text=(content.innerText||content.textContent||'').replace(/\s+/g,' ').trim();
                  return text.length>=${ArticleWebViewRenderProbe.MIN_VISIBLE_TEXT_LENGTH};
                }
                if(document.readyState === 'loading'){
                  document.addEventListener('DOMContentLoaded', notify, {once:true});
                } else if(hasVisibleBody()){
                  notify();
                } else {
                  document.addEventListener('DOMContentLoaded', notify, {once:true});
                }
                var attempts=0;
                var timer=setInterval(function(){
                  if(notified){ clearInterval(timer); return; }
                  if(hasVisibleBody()){ notify(); clearInterval(timer); return; }
                  if(++attempts>120){ clearInterval(timer); }
                }, 50);
              }catch(e){}
            })();</script>
        """.trimIndent()
        val headOpen = Regex("""<head\b[^>]*>""", RegexOption.IGNORE_CASE)
        val match = headOpen.find(html)
        return if (match != null) {
            val insertAt = match.range.last + 1
            html.substring(0, insertAt) + script + html.substring(insertAt)
        } else {
            script + html
        }
    }

    private fun ArticleCommentsState.toCommentsState(): CommentsState = when (this) {
        ArticleCommentsState.NotLoaded -> CommentsState.NotLoaded
        is ArticleCommentsState.Loading -> CommentsState.LoadingInitial(requestId)
        is ArticleCommentsState.Loaded -> {
            if (comments.isEmpty()) {
                CommentsState.Empty
            } else {
                CommentsState.Loaded(
                        comments = comments,
                        page = 1,
                        canLoadMore = canLoadMore,
                        totalCount = totalCount.coerceAtLeast(comments.size),
                )
            }
        }
        ArticleCommentsState.Empty -> CommentsState.Empty
        is ArticleCommentsState.Error -> CommentsState.Error(throwable, canRetry)
    }
}
