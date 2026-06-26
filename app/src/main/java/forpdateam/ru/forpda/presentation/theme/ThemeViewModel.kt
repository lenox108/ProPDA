package forpdateam.ru.forpda.presentation.theme

import forpdateam.ru.forpda.presentation.BaseViewModel
import androidx.lifecycle.viewModelScope
import android.net.Uri
import android.util.Log
import android.os.SystemClock
import timber.log.Timber
import forpdateam.ru.forpda.BuildConfig
import io.appmetrica.analytics.AppMetrica
import forpdateam.ru.forpda.R
import forpdateam.ru.forpda.common.ArticleLinkResolver
import forpdateam.ru.forpda.common.FourPdaImageUrls
import forpdateam.ru.forpda.common.SiteUrls
import forpdateam.ru.forpda.common.Preferences as AppPreferences
import forpdateam.ru.forpda.common.TopicOpenListHints
import forpdateam.ru.forpda.common.topicUrlHasNonZeroStParameter
import android.content.Context
import forpdateam.ru.forpda.common.Utils
import forpdateam.ru.forpda.entity.remote.editpost.AttachmentItem
import forpdateam.ru.forpda.entity.app.TabNotification
import forpdateam.ru.forpda.entity.remote.IBaseForumPost
import forpdateam.ru.forpda.entity.remote.editpost.EditPostForm
import forpdateam.ru.forpda.entity.remote.events.NotificationEvent
import forpdateam.ru.forpda.entity.remote.search.SearchSettings
import forpdateam.ru.forpda.entity.remote.theme.ThemePage
import forpdateam.ru.forpda.entity.remote.theme.ThemePost
import forpdateam.ru.forpda.model.interactors.theme.ThemeEditorUseCase
import forpdateam.ru.forpda.model.interactors.theme.ThemeInteractionUseCase
import forpdateam.ru.forpda.model.interactors.theme.ThemeUseCase
import forpdateam.ru.forpda.model.preferences.ForumBlacklistedUser
import forpdateam.ru.forpda.model.data.remote.api.RequestFile
import forpdateam.ru.forpda.model.data.remote.api.theme.ThemeApi
import forpdateam.ru.forpda.model.interactors.theme.ThemeNavigationUseCase
import forpdateam.ru.forpda.model.repository.theme.ThemeReadPositionRepository
import forpdateam.ru.forpda.model.repository.theme.TopicReturnPositionStore
import com.github.terrakok.cicerone.ResultListenerHandler
import forpdateam.ru.forpda.presentation.Screen
import forpdateam.ru.forpda.presentation.TabRouter
import forpdateam.ru.forpda.ui.activities.imageviewer.ImageViewerActivity
import forpdateam.ru.forpda.ui.fragments.theme.ThemeFragmentWeb
import java.io.UnsupportedEncodingException
import java.lang.ref.WeakReference
import java.net.URLEncoder
import java.util.*
import java.util.regex.Pattern
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.launch
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import forpdateam.ru.forpda.entity.app.EditPostSyncData
import forpdateam.ru.forpda.diagnostic.FpdaDebugLog
import forpdateam.ru.forpda.diagnostic.NavBackstackTrace
import forpdateam.ru.forpda.diagnostic.ThemePostReadStateDiagnostics
import kotlinx.coroutines.flow.asSharedFlow
import org.json.JSONObject

private const val REFRESH_SCROLL_TAG = "RefreshScroll"
private const val SMART_END_TAG = "FPDA_THEME_SMART_END"
private const val THEME_RENDER_TAG = "ThemeRender"
private const val THEME_HISTORY_TAG = "ThemeHistory"
private const val THEME_QUOTE_TAG = "ThemeQuote"
private const val MAX_REMEMBERED_TOPIC_TITLES = 32

/**
 * Single TTL for the captured link source anchor (Pack B of the back-navigation
 * audit). Both the cross-topic link path ([ThemeViewModel.consumeLinkSourceAnchorFor])
 * and the same-topic scroll restore ([ThemeViewModel.sourceAnchorAppliesTo]) share
 * this window via the single [ThemeViewModel.pendingHistorySourceAnchor] field.
 *
 * 15s covers a user who pauses between tapping a cross-topic link and the
 * WebViewClient dispatching `handleUri`, plus the same-topic history restore that
 * fires on the new page's `loadData`. The previous 8s/5s split dropped the anchor
 * for slower users and reintroduced the "scroll jumps to top" bug.
 */
internal const val SOURCE_ANCHOR_TTL_MS = 15_000L
/** Grace window protecting a REFRESH_RESTORE settle from safety reveals; > theme.js BACK_ANCHOR_SETTLE_DEADLINE_MS (4000). */
internal const val REFRESH_RESTORE_SETTLE_GRACE_MS = 4200L

data class ThemeLinkSourceAnchor(
        val href: String,
        val postId: String,
        val offsetTop: Double?,
        val scrollY: Int,
        val ratio: Double?,
        val eventType: String,
        val capturedAt: Long
)

/**
 * Created by radiationx on 15.03.18.
 */
@OptIn(FlowPreview::class)
@HiltViewModel
class ThemeViewModel @Inject constructor(
        @ApplicationContext private val context: Context,
        private val themeUseCase: ThemeUseCase,
        private val editorUseCase: ThemeEditorUseCase,
        private val interactionUseCase: ThemeInteractionUseCase,
        private val navigationUseCase: ThemeNavigationUseCase,
        private val router: TabRouter,
        private val readPositionRepository: ThemeReadPositionRepository,
        private val returnPositionStore: TopicReturnPositionStore
) : BaseViewModel(), ThemeWebCallbacks {

    // SharedFlow для MVVM (замена callback-методов ThemeView)
    // SharedFlow (replay=0) — не переигрывает последнее значение при повторной подписке (например, возврат из ImageViewer)
    private val _onLoadData = MutableSharedFlow<ThemePage>(extraBufferCapacity = 1)
    val onLoadData: SharedFlow<ThemePage> = _onLoadData.asSharedFlow()

    private val _updateView = MutableSharedFlow<ThemePage>(extraBufferCapacity = 1)
    val updateView: SharedFlow<ThemePage> = _updateView.asSharedFlow()

    private val _setRefreshing = MutableStateFlow(false)
    val setRefreshing: StateFlow<Boolean> = _setRefreshing.asStateFlow()

    private val _onMessageSent = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val onMessageSent: SharedFlow<Unit> = _onMessageSent.asSharedFlow()

    private val _onAddToFavorite = MutableSharedFlow<Boolean>(extraBufferCapacity = 1)
    val onAddToFavorite: SharedFlow<Boolean> = _onAddToFavorite.asSharedFlow()

    private val _onDeleteFromFavorite = MutableSharedFlow<Boolean>(extraBufferCapacity = 1)
    val onDeleteFromFavorite: SharedFlow<Boolean> = _onDeleteFromFavorite.asSharedFlow()

    private val _setMessageRefreshing = MutableStateFlow(false)
    val setMessageRefreshing: StateFlow<Boolean> = _setMessageRefreshing.asStateFlow()

    /** Incremented on cross-topic navigation so the fragment clears stale title/pagination immediately. */
    private val _topicToolbarClearSignal = MutableStateFlow(0L)
    val topicToolbarClearSignal: StateFlow<Long> = _topicToolbarClearSignal.asStateFlow()

    private val _uiEvents = MutableSharedFlow<ThemeUiEvent>(extraBufferCapacity = 8)
    val uiEvents: SharedFlow<ThemeUiEvent> = _uiEvents.asSharedFlow()

    private var _loadAction: ThemeLoadAction = ThemeLoadAction.Normal
    val loadAction: ThemeLoadAction get() = _loadAction
    /** Survives early [resetLoadAction] until smart-end scroll command completes. */
    private var pendingEndNavigation: Boolean = false
    private var pendingPostedPageScrollCommand: Boolean = false
    private var currentPage: ThemePage? = null

    private var visibleCurrentPage: Int? = null
    private val explicitTargetPostIds = java.util.WeakHashMap<ThemePage, Int>()
    private var currentTopicScrollMode = AppPreferences.Main.TopicScrollMode.HYBRID
    private var currentTopicPostDensity = AppPreferences.Main.TopicPostDensity.COMFORTABLE
    private var currentTopicToolbarBehavior = AppPreferences.Main.TopicToolbarBehavior.PINNED
    private var currentTopicBackBehavior = AppPreferences.Main.TopicBackBehavior.HISTORY
    private var currentTopicOpenTarget = AppPreferences.Main.TopicOpenTarget.LAST_UNREAD
    private var currentTopicHeaderInitialState = AppPreferences.Main.TopicHeaderInitialState.EXPANDED
    private val historyController = ThemeHistoryController()
    private val postEditCoordinator = ThemePostEditCoordinator(
        scope = scope,
        editorUseCase = editorUseCase,
        uiEvents = _uiEvents,
        router = router,
        getCurrentPage = { getPageForEditorAndSubmit() },
        getThemeLoadTraceId = { openTrace.id },
        setThemeLoadTraceId = { openTrace = openTrace.copy(id = it) },
        getSetMessageRefreshing = { _setMessageRefreshing },
        onPostSubmitSuccess = { form, sentAtMillis, page ->
            themeUseCase.syncSubmittedFavoriteLastPost(form.topicId, sentAtMillis, page)
        },
        onApplyPostedThemePage = { page, clear, scrollPostId ->
            applyPostedThemePage(page, clear, scrollPostId)
        },
        onOpenEditPost = { postId ->
            captureScrollAnchorForEditPost(postId)
        }
    )
    private val postActionHandler = ThemePostActionHandler(
        scope = scope,
        interactionUseCase = interactionUseCase,
        navigationUseCase = navigationUseCase,
        uiEvents = _uiEvents,
        router = router,
        getPostById = ::getPostById,
        getCurrentPage = { currentPage },
        shareText = ::shareText,
        logThemeQuote = { message, args -> logThemeQuote(message, *args) }
    )
    private val infiniteScrollController = ThemeInfiniteScrollController(
        scope = scope,
        themeUseCase = themeUseCase,
        uiEvents = _uiEvents,
        buildCleanThemeUrl = ::buildCleanThemeUrl,
        detectTopicHatPost = ::detectTopicHatPost,
        stripDuplicateHatFromNonFirstPage = ::stripDuplicateHatFromNonFirstPage,
        validateNonFirstPagePostNumbers = ::validateNonFirstPagePostNumbers,
        promoteTopicHatForHybridPage = ::promoteTopicHatForHybridPage,
        getCurrentPage = { currentPage },
        getLoadedPages = { loadedPages },
        setLoadedPages = { newPages ->
            loadedPages.clear()
            loadedPages.putAll(newPages)
        },
        getFirstPageHatPostId = { firstPageHatPostId },
        setFirstPageHatPostId = { id -> firstPageHatPostId = id },
        getCurrentTopicScrollMode = { currentTopicScrollMode },
        getUserHatOpenOverride = { userHatOpenOverride }
    )
    private val paginationController = ThemePaginationController()
    private val postOpenEnrichmentController = ThemePostOpenEnrichmentController(
            object : ThemePostOpenEnrichmentController.Callbacks {
                override fun scheduleFavoriteSync(page: ThemePage, traceId: String) {
                    scheduleDeferredFavoriteLastPostSync(page, traceId)
                }

                override fun scheduleMetadataEnrichment(page: ThemePage, traceId: String) {
                    scheduleDeferredPageMetadataEnrichment(page, traceId)
                }

                override fun scheduleHatMetadata(page: ThemePage) {
                    maybeLoadTopicHatMetadata(page)
                }

                override fun scheduleHybridPrefetch(page: ThemePage, traceId: String) {
                    prefetchAdjacentHybridPageIfNeeded(page, traceId)
                }

                override fun prefetchEditor(page: ThemePage) {
                    themeUseCase.prefetchEditorForOpenedPage(page)
                }
            }
    )
    private val realtimeEventsHandler = ThemeRealtimeEventsHandler(
        themeUseCase = themeUseCase,
        uiEvents = _uiEvents,
        isPageLoaded = ::isPageLoaded,
        getId = ::getId
    )
    private var themeUrl: String = ""
    private var lastOpenSourceScreen: String = "unknown"
    private var lastOpenIntent: String = TopicOpenIntentClassifier.FRESH_LEGACY
    private var suppressScrollRestoreForOpen: Boolean = false
    private var pendingUnreadOpenSuppressScroll: Boolean = false
    private var lastOpenResolution: TopicOpenResolution? = null
    private var activeNavigationTarget: TopicOpenTarget? = null
    private var pendingScrollCommand: ThemeScrollCommand? = null
    /** uptimeMillis until which a pending REFRESH_RESTORE settle is protected from safety reveals. */
    private var refreshRestoreSettleDeadlineAtMs: Long = 0L
    private var listOpenHints: TopicOpenListHints? = null
    /** Captured at [resolveTopicOpenUrl]; survives [listOpenHints] clear before async [loadData]. */
    private var pendingParserListUnreadHint: Boolean = false
    /** Per [loadData] trace: survives [resetTransientStateForNewTopic] inside the same load. */
    private var activeLoadParserUnreadHint: Boolean = false
    private var activeLoadListTopicMarkedUnread: Boolean = false
    private var activeOpenSessionKind: TopicUnreadOpenPolicy.TopicOpenSessionKind? = null
    private var pendingRenderedReadTarget: RenderedReadTarget? = null
    /** Last topic id that finished [onLoadData]; drives mandatory reset on cross-topic [loadUrl]. */
    private var activeLoadedTopicId: Int? = null
    private var pendingSmartEndTopicId: Int? = null
    /** Deferred hybrid fallback when [page_not_in_dom] arrives while [loadThemeJob] is still active. */
    private var pendingSmartEndFallback = false

    /** Инициализация URL темы из аргументов Fragment (один раз при создании). */
    fun initThemeUrl(url: String) {
        val incomingTopicId = ThemeApi.extractTopicIdFromUrl(url)
        val loadedTopicId = currentPage?.id
        if (subscriptionsStarted.get() && currentPage != null &&
                incomingTopicId != null && loadedTopicId != null && incomingTopicId == loadedTopicId) {
            return
        }
        themeUrl = url
    }

    fun initTopicOpenHints(hints: TopicOpenListHints?, sourceScreen: String = "unknown") {
        listOpenHints = hints
        if (sourceScreen != "unknown") {
            lastOpenSourceScreen = sourceScreen
        }
    }

    fun setTopicOpenIntent(intent: String) {
        lastOpenIntent = intent.trim().ifEmpty { TopicOpenIntentClassifier.FRESH_LEGACY }
    }

    fun isFreshTopicOpen(): Boolean = TopicOpenIntentClassifier.isFreshOpenIntent(lastOpenIntent)

    fun isRestoreTopicOpen(): Boolean = TopicOpenIntentClassifier.isRestoreIntent(lastOpenIntent)

    fun savedScrollRestoreAllowedForCurrentOpen(): Boolean =
            TopicOpenScrollRestorePolicy.savedScrollRestoreAllowed(
                    openIntentRaw = lastOpenIntent,
                    setting = currentTopicOpenTarget,
                    loadAction = _loadAction,
                    suppressScrollRestoreForOpen = suppressScrollRestoreForOpen || pendingUnreadOpenSuppressScroll,
                    openTarget = activeNavigationTarget
            )

    fun getActiveNavigationTarget(): TopicOpenTarget? = activeNavigationTarget

    fun beginScrollCommand(command: ThemeScrollCommand): ThemeScrollCommand {
        if (ThemePostedScrollPendingPolicy.shouldMarkEndNavigationPending(
                        command.kind,
                        pendingPostedPageScrollAnchor
                )
        ) {
            pendingEndNavigation = true
        }
        if (ThemePostedScrollPendingPolicy.shouldMarkPostedPageScrollPending(
                        command.kind,
                        pendingPostedPageScrollAnchor
                )
        ) {
            pendingPostedPageScrollCommand = true
        }
        pendingScrollCommand = command
        if (command.kind == ThemeScrollCommand.Kind.REFRESH_RESTORE) {
            refreshRestoreSettleDeadlineAtMs =
                    SystemClock.uptimeMillis() + REFRESH_RESTORE_SETTLE_GRACE_MS
        }
        return command
    }

    /**
     * True while a BACK/refresh REFRESH_RESTORE is running its JS settle loop (it polls until the
     * target post is in the progressively-laid-out HYBRID DOM, theme.js BACK_ANCHOR_SETTLE_DEADLINE_MS).
     * The reveal-safety watchdogs (alphaRevealSafety / scrollStuckReveal / renderWatchdog*) must NOT
     * un-hide the WebView or abandon the command before this window elapses, or the page is revealed at
     * the page top mid-settle and the back lands on the wrong post (device logs 26_06-16-50 … 26_06-17-38).
     */
    fun isRefreshRestoreSettleInProgress(): Boolean =
            pendingScrollCommand?.kind == ThemeScrollCommand.Kind.REFRESH_RESTORE &&
                    refreshRestoreSettleDeadlineAtMs > SystemClock.uptimeMillis()

    /** Drops a deferred smart-end scroll so it cannot replay after navigation/render reset. */
    fun clearPendingScrollCommand() {
        pendingScrollCommand = null
        pendingEndNavigation = false
        pendingPostedPageScrollCommand = false
    }

    fun isEndNavigationPending(): Boolean =
            pendingEndNavigation || _loadAction == ThemeLoadAction.End

    fun isPostedPageScrollPending(): Boolean = pendingPostedPageScrollCommand

    fun shouldScheduleUnreadJumpOnTabFocus(): Boolean =
            ThemeTabUnreadJumpPolicy.shouldScheduleUnreadJumpOnTabFocus(
                    reloadUnreadOnTabFocus = shouldReloadUnreadOnTabFocus(),
                    openedViaFindPostLink = openedViaFindPostLink,
                    loadInFlight = loadThemeJob?.isActive == true,
                    renderSettled = isTopicRenderSettled(),
                    pendingPostedPageScroll = !pendingPostedPageScrollAnchor.isNullOrBlank(),
            )

    private fun clearEndNavigationPending() {
        pendingEndNavigation = false
    }

    private fun clearPostedPageScrollPending() {
        pendingPostedPageScrollCommand = false
    }

    fun completeScrollCommand(commandId: String, success: Boolean) {
        val pending = pendingScrollCommand ?: return
        if (pending.commandId != commandId) return
        val wasBlockingReveal = ThemeRenderSettledPolicy.isBlockingScrollKind(pending.kind)
        pendingScrollCommand = null
        if (pending.kind == ThemeScrollCommand.Kind.END_ANCHOR_OR_BOTTOM) {
            clearEndNavigationPending()
            clearPostedPageScrollPending()
        }
        if (pending.kind == ThemeScrollCommand.Kind.INITIAL_ANCHOR) {
            if (success) {
                initialAnchorScrollSettledTraceId = openTrace.id
                Log.i(
                        ThemeUnreadHybridAnchorGuardPolicy.LOG_TAG,
                        "anchor_scroll_settled trace=$openTrace.id kind=${pending.kind}"
                )
                _uiEvents.tryEmit(ThemeUiEvent.ClearUnreadAnchorHybridGuard("initial_anchor_settled"))
                flushPendingHatOverlayRenderAfterScroll()
            } else {
                releaseUnreadAnchorHybridGuard("initial_anchor_failed")
            }
        }
        if (BuildConfig.DEBUG) {
            Timber.d("scrollCommandComplete id=$commandId success=$success kind=${pending.kind}")
        }
        maybeMarkTopicRenderSettledAfterScroll()
        _uiEvents.tryEmit(ThemeUiEvent.ProgrammaticScrollEnded)
        if (wasBlockingReveal) {
            _uiEvents.tryEmit(ThemeUiEvent.RevealThemeContent)
        }
    }

    fun revealThemeContentAfterDomRendered() {
        _uiEvents.tryEmit(ThemeUiEvent.RevealThemeContent)
    }

    /** Primary HTML for [openTrace.id] was emitted; safe to reveal WebView. */
    fun isPrimaryOpenComplete(): Boolean =
            postOpenEnrichmentController.isPrimaryOpenComplete(openTrace.id)

    /** Post-open enrich jobs were scheduled for [openTrace.id]. */
    fun isPostOpenEnrichStarted(): Boolean =
            postOpenEnrichmentController.isPostOpenEnrichStarted(openTrace.id)

    fun shouldArmInitialAnchorOnPageComplete(): Boolean =
            ThemeOpenScrollCoalescePolicy.shouldArmInitialAnchorOnPageComplete(
                    traceId = openTrace.id,
                    initialAnchorScrollSettledTraceId = initialAnchorScrollSettledTraceId,
                    pendingScrollKind = pendingScrollCommand?.kind,
                    hatOverlayReinjectionTraceId = hatOverlayReinjectionTraceId,
                    hasUnreadTarget = currentPage?.hasUnreadTarget == true,
                    explicitAnchorBlocking = isExplicitAnchorBlockingOpen(),
            )

    /**
     * STEP 1: an explicit-post / findpost open (`view=findpost&p=`) arms the SAME blocking
     * INITIAL_ANCHOR path as an unread open, but WITHOUT the unread side-effects (mark-read,
     * `armUnreadInitialAnchorScroll`). True only when this is a fresh Normal open of an
     * EXPLICIT_POST session that actually carries an anchor to land on.
     */
    private fun isExplicitAnchorBlockingOpen(): Boolean {
        val page = currentPage ?: return false
        if (page.hasUnreadTarget) return false
        if (_loadAction != ThemeLoadAction.Normal) return false
        if (page.openSessionKind !=
                TopicUnreadOpenPolicy.TopicOpenSessionKind.EXPLICIT_POST.name
        ) {
            return false
        }
        return !page.anchorPostId.isNullOrBlank() ||
                !page.anchor?.removePrefix("entry")?.takeIf { it.isNotBlank() }.isNullOrBlank()
    }

    /** STEP 2 — controller-facing probe for the sticky explicit-anchor intent. */
    internal fun isExplicitAnchorBlockingOpenForController(): Boolean = isExplicitAnchorBlockingOpen()

    /**
     * STEP 2 — controller-facing probe for whether the blocking INITIAL_ANCHOR scroll for the
     * pending explicit anchor has settled. Set true by [onScrollCommandComplete] when the
     * INITIAL_ANCHOR command reports success; cleared on topic change in [resetTransientStateForNewTopic].
     */
    private var explicitAnchorScrollSettled: Boolean = false

    internal fun isExplicitAnchorScrollSettledForController(): Boolean = explicitAnchorScrollSettled

    fun resetExplicitAnchorScrollSettled() {
        explicitAnchorScrollSettled = false
    }

    fun expectsInitialAnchorScrollOnOpen(): Boolean =
            ThemeOpenScrollCoalescePolicy.expectsInitialAnchorScrollOnOpen(
                    shouldArmInitialAnchor = shouldArmInitialAnchorOnPageComplete(),
                    anchorPostId = currentPage?.anchorPostId,
                    pageAnchor = currentPage?.anchor,
                    hasUnreadTarget = currentPage?.hasUnreadTarget == true,
                    explicitAnchorBlocking = isExplicitAnchorBlockingOpen(),
            )

    /**
     * Reveal-at-anchor gate (A/B): true when the page positioning on this open/navigation is
     * owned by the INSTANT JS anchor scroll (explicit-post open or BACK/refresh restore to a
     * post) rather than a blocking Kotlin command. The reveal is held one DOM confirmation so the
     * WebView is uncovered already at the anchor instead of revealing at scrollY≈0 and then
     * visibly auto-scrolling to the post.
     */
    fun expectsJsAnchorPositioningOnOpen(): Boolean {
        val page = currentPage ?: return false
        return ThemeOpenScrollCoalescePolicy.expectsJsAnchorPositioningOnOpen(
                loadAction = _loadAction,
                isExplicitPostOpen = page.openSessionKind ==
                        TopicUnreadOpenPolicy.TopicOpenSessionKind.EXPLICIT_POST.name,
                isEndNavigation = isEndNavigationPending(),
                isRefreshRestoreToBottom = page.wasNearBottom,
                hasUnreadTarget = page.hasUnreadTarget,
                anchorPostId = page.anchorPostId,
                pageAnchor = page.anchor,
                explicitAnchorBlocking = isExplicitAnchorBlockingOpen(),
        )
    }

    fun isHatOverlayReinjectionRender(): Boolean =
            hatOverlayReinjectionTraceId == openTrace.id

    fun onHatOverlayReinjectionScrollRestored() {
        if (hatOverlayReinjectionTraceId == openTrace.id) {
            hatOverlayReinjectionTraceId = null
        }
    }

    fun hasBlockingScrollPending(): Boolean {
        val pending = pendingScrollCommand ?: return false
        return ThemeRenderSettledPolicy.isBlockingScrollKind(pending.kind)
    }

    /**
     * Safety reveal when [INITIAL_ANCHOR] was armed in Kotlin but JS never reported completion
     * (log: blockingScroll=true renderComplete=true alpha stuck at 0 on last-page getnewpost).
     */
    fun abandonBlockingScrollForSafetyReveal(reason: String) {
        val pending = pendingScrollCommand ?: return
        if (!ThemeRenderSettledPolicy.isBlockingScrollKind(pending.kind)) return
        if (BuildConfig.DEBUG) {
            Timber.d("abandonBlockingScroll reason=$reason kind=${pending.kind} id=${pending.commandId}")
        }
        if (pending.kind == ThemeScrollCommand.Kind.INITIAL_ANCHOR) {
            releaseUnreadAnchorHybridGuard("abandon_$reason")
            return
        }
        pendingScrollCommand = null
        maybeMarkTopicRenderSettledAfterScroll()
    }

    /**
     * On WebView reveal the initial-anchor positioning window is over, so the hybrid top-autoload
     * guard must not stay armed. Releases it when the INITIAL_ANCHOR never marked settled for the
     * current trace — even though [pendingScrollCommand] already cleared, so the
     * [abandonBlockingScrollForSafetyReveal] path (which needs a pending command) cannot fire.
     *
     * Device log 26_06-15-31, topic 528252: the INITIAL_ANCHOR completion was lost to the render-
     * generation race (a findpost reload / hat-preload re-render bumped the generation, JS dropped the
     * stale `scrollCmdComplete`), so `initialAnchorScrollSettledTraceId` never matched the open trace.
     * The guard stayed `awaiting_anchor` forever and hybrid top-autoload of the PREVIOUS pages was
     * blocked — the user hit one post and could not scroll up. Idempotent: a no-op once settled.
     */
    fun releaseInitialAnchorHybridGuardForReveal(reason: String) {
        if (initialAnchorScrollSettledTraceId == openTrace.id) return
        if (currentPage?.hasUnreadTarget != true && !isExplicitAnchorBlockingOpen()) return
        releaseUnreadAnchorHybridGuard("reveal_$reason")
    }

    /** Clears Kotlin + WebView hybrid guard when INITIAL_ANCHOR cannot settle (log 11_06: pageComplete lost). */
    fun releaseUnreadAnchorHybridGuard(reason: String) {
        initialAnchorScrollSettledTraceId = openTrace.id
        pendingScrollCommand = null
        maybeMarkTopicRenderSettledAfterScroll()
        Log.i(
                ThemeUnreadHybridAnchorGuardPolicy.LOG_TAG,
                "anchor_guard_release reason=$reason trace=$openTrace.id"
        )
        _uiEvents.tryEmit(ThemeUiEvent.ClearUnreadAnchorHybridGuard(reason))
    }

    fun isTopicRenderSettled(): Boolean = renderSettledTraceId == openTrace.id

    override fun onScrollCommandComplete(commandId: String, success: Boolean, reason: String) {
        val pending = pendingScrollCommand
        // Diagnostic (device log 26_06-15-31, 528252): confirm whether the JS scroll completion ever
        // reaches Kotlin. If this line is ABSENT for an INITIAL_ANCHOR open while `blocked_infinite`
        // appears, JS dropped the completion (stale render generation) before bridging it here.
        Log.i(
                ThemeUnreadHybridAnchorGuardPolicy.LOG_TAG,
                "scroll_cmd_complete_received id=$commandId success=$success reason=$reason pendingId=${pending?.commandId} pendingKind=${pending?.kind} trace=$openTrace.id"
        )
        completeScrollCommand(commandId, success)
        if (pending?.commandId != commandId) return
        when (pending.kind) {
            ThemeScrollCommand.Kind.INITIAL_ANCHOR -> {
                // STEP 2: a successful INITIAL_ANCHOR completion is the event-based settle signal
                // for the sticky explicit-anchor intent. The JS side only reports completion after
                // the `scrollToElementWithRetries` final retry confirmed the anchor is near the
                // viewport top, so this clears the sticky intent and lets the highlight arm once.
                if (success) {
                    explicitAnchorScrollSettled = true
                }
                ThemePostReadStateDiagnostics.scrollSettled(
                        topicId = currentPage?.id,
                        traceId = openTrace.id,
                        anchorPostId = currentPage?.anchorPostId ?: currentPage?.anchor,
                        finalScrolledPostId = ThemePostReadStateDiagnostics.parseLastPostFromScrollReason(reason),
                        scrollKind = pending.kind.name,
                        success = success,
                        reason = reason
                )
            }
            ThemeScrollCommand.Kind.BOTTOM,
            ThemeScrollCommand.Kind.END_ANCHOR_OR_BOTTOM -> {
                if (!success &&
                        ThemeSmartEndNavigation.shouldFallbackToLastPageLoad(reason, currentTopicScrollMode)
                ) {
                    fallbackSmartEndToLastPageLoad()
                    return
                }
                reportSmartEndScrollResult(
                        commandId = commandId,
                        success = success,
                        reason = reason.ifBlank { if (success) pending.kind.name else "scroll_failed" },
                        scrollYBefore = 0,
                        scrollYAfter = parseScrollYFromCommandReason(reason)
                )
            }
            else -> {
                // Phase 1 (audit §13): emit a structured trace for the remaining scroll-command
                // completions (ANCHOR / SCROLL_Y / UNREAD / REFRESH_RESTORE) so every onScrollCommandComplete
                // exit is observable when diagnosing problems #1/#2/#9. Diagnostics-only, no behavior change.
                if (BuildConfig.DEBUG) {
                    ThemePostReadStateDiagnostics.scrollSettled(
                            topicId = currentPage?.id,
                            traceId = openTrace.id,
                            anchorPostId = currentPage?.anchorPostId ?: currentPage?.anchor,
                            finalScrolledPostId = ThemePostReadStateDiagnostics.parseLastPostFromScrollReason(reason),
                            scrollKind = pending.kind.name,
                            success = success,
                            reason = reason
                    )
                }
            }
        }
    }

    fun getPendingScrollCommand(): ThemeScrollCommand? = pendingScrollCommand

    fun shouldBlockHybridUntilInitialAnchorSettled(): Boolean {
        // STEP 1: explicit-post opens arm the blocking INITIAL_ANCHOR path via `explicitAnchorBlocking`
        // (without unread side-effects). Drop the unread-only pre-gate so the hybrid block fires for
        // those opens too; the underlying policy is event/state-based (pendingScrollKind ==
        // INITIAL_ANCHOR or expectsInitialAnchorScroll) and never enables mark-read itself.
        if (currentPage?.hasUnreadTarget != true && !isExplicitAnchorBlockingOpen()) return false
        return ThemeUnreadHybridAnchorGuardPolicy.shouldBlockHybridUntilInitialAnchorSettled(
                traceId = openTrace.id,
                initialAnchorScrollSettledTraceId = initialAnchorScrollSettledTraceId,
                pendingScrollKind = pendingScrollCommand?.kind,
                expectsInitialAnchorScroll = expectsInitialAnchorScrollOnOpen(),
        )
    }

    fun shouldSuppressScrollRestoreOnRender(): Boolean =
            TopicOpenScrollRestorePolicy.shouldSuppressScrollRestoreOnRender(
                    suppressScrollRestoreForOpen = suppressScrollRestoreForOpen,
                    pendingUnreadOpenSuppressScroll = pendingUnreadOpenSuppressScroll,
                    loadAction = _loadAction,
                    hasActiveRefreshRestore = pendingRefreshRequest != null ||
                            !getRefreshRestoreId().isNullOrBlank(),
                    themeUrl = themeUrl,
                    topicOpenTarget = currentTopicOpenTarget,
                    navigationTarget = activeNavigationTarget
            )

    fun isRefreshScrollRestoreActive(): Boolean =
            _loadAction == ThemeLoadAction.Refresh ||
                    pendingRefreshRequest != null ||
                    !getRefreshRestoreId().isNullOrBlank()

    /** Called after WebView finished initial unread scroll so cached scrollY cannot win. */
    fun onUnreadOpenRenderCompleted() {
        pendingUnreadOpenSuppressScroll = false
        suppressScrollRestoreForOpen = false
    }

    /** Текущий URL темы (запроса, с getnewpost и другими параметрами). */
    fun getThemeUrl(): String = themeUrl

    fun getLastOpenIntent(): String = lastOpenIntent

    /** Сброс _loadAction в Normal после завершения загрузки WebView. */
    fun resetLoadAction() {
        _loadAction = ThemeLoadAction.Normal
    }

    fun getCurrentPageAnchor(): String? = currentPage?.anchor
    fun getCurrentPageUrl(): String? = currentPage?.url
    fun getVisibleCurrentPage(): Int = visibleCurrentPage ?: currentPage?.pagination?.current ?: 1
    fun getCurrentTopicScrollMode(): AppPreferences.Main.TopicScrollMode = currentTopicScrollMode

    fun isRefreshing(): Boolean = _setRefreshing.value || loadThemeJob?.isActive == true

    fun canRefreshFromBottomOverscroll(): Boolean {
        val page = currentPage ?: return false
        if (page.id <= 0 || isRefreshing()) return false
        if (isTopicHatOpen()) return false
        val lastPage = page.pagination.all.coerceAtLeast(1)
        return when (currentTopicScrollMode) {
            AppPreferences.Main.TopicScrollMode.CLASSIC ->
                (visibleCurrentPage ?: page.pagination.current).coerceAtLeast(1) >= lastPage
            AppPreferences.Main.TopicScrollMode.HYBRID ->
                (loadedPages.keys.maxOrNull() ?: page.pagination.current).coerceAtLeast(1) >= lastPage
        }
    }

    fun isTopicHatOpen(): Boolean {
        return userHatOpenOverride == true || currentPage?.isHatOpen == true
    }

    fun isTopicPollOpen(): Boolean {
        return currentPage?.isPollOpen == true
    }

    /** Тема открыта по findpost; при возврате на вкладку не грузим getnewpost — иначе теряется якорь поста. */
    private var openedViaFindPostLink = false

    /** При открытии ImageViewer из темы — не скроллить к непрочитанному при возврате. */
    private var skipNextUnreadJumpAfterTabSwitch = false
    private var initialOpenSettledAt = 0L
    /** First WebView paint for [openTrace.id] finished; hat metadata must not preempt it. */
    private var renderSettledTraceId: String? = null
    /** Successful INITIAL_ANCHOR for [openTrace.id]; suppresses a second scroll on pageComplete. */
    private var initialAnchorScrollSettledTraceId: String? = null
    /** Topic title preserved across deep hybrid pages when pagination HTML omits it. */
    private var sessionTopicTitle: String? = null
    /**
     * Durable last-known toolbar title keyed by topicId. Unlike [sessionTopicTitle] this survives
     * session bumps ([infiniteSession]++ on every loadData), same-topic reloads and cross-topic
     * switches, so a deep page that arrives without [ThemePage.title] can always recover the label
     * for ITS topic even if the async first-page fetch lost a session race. Gated by topicId so a
     * stale title never leaks onto a different topic.
     */
    private val lastKnownTopicTitleByTopicId = linkedMapOf<Int, String>()
    /** Title from favorites/topics navigator args; survives [resetTransientStateForNewTopic]. */
    private var navigationTopicTitle: String? = null
    private var titleFromFirstPageJob: Job? = null
    /** One-shot getnewpost→findpost upgrade per open trace (hybrid unread anchor guard). */
    private var unreadFindPostUpgradeTraceId: String? = null
    /**
     * Trace whose getnewpost redirect hit the BOTTOM boundary of a non-last page, so we advanced to
     * the next page to reach the genuine first-unread. When that next page carries no HTML unread
     * markers its first content post IS the first-unread (device log 26_06-15-24, topic 1050118).
     */
    private var nextPageUnreadReloadTraceId: String? = null
    /** De-dups the high-frequency `blocked_infinite awaiting_anchor` log so it can't flood logcat. */
    private var lastBlockedInfiniteLogKey: String? = null
    /** Hat overlay WebView reload scheduled after first anchor scroll settles. */
    private var pendingHatOverlayRenderAfterScroll = false
    /** Marks hatOverlayEnsure reload so pageComplete restores scroll natively instead of INITIAL_ANCHOR. */
    private var hatOverlayReinjectionTraceId: String? = null
    private var pendingHatMetadataEmitTraceId: String? = null
    private var pendingRenderSettledAfterScroll = false
    private var lastLinkSourceAnchor: ThemeLinkSourceAnchor? = null
    private var pendingHistorySourceAnchor: ThemeLinkSourceAnchor? = null
    private var pendingHistorySourceTopicId: Int? = null
    private var pendingHistorySourceSt: Int? = null
    /** R-02: gesture-scoped single-dispatch guard for the triple navigation entry path. */
    private val navigationGestureGuard = NavigationGestureDispatchGuard()

    /** После успешной отправки из дочернего редактора не откатываемся на сохранённый scrollY. */
    private var restorePostedPageAfterChildRemoval = false
    /** Scroll anchor after post submit; survives unread-open scroll suppression until DOM scroll runs. */
    private var pendingPostedPageScrollAnchor: String? = null
    /** true после редактирования — не заменять якорь последним постом страницы. */
    private var pendingPostedPageScrollExact = false
    /** Якорь редактируемого поста при открытии формы; восстанавливаем при отмене без reload. */
    private var pendingEditCancelScrollPostId: Int? = null

    fun setSkipNextUnreadJumpAfterTabSwitch(skip: Boolean) {
        skipNextUnreadJumpAfterTabSwitch = skip
    }

    /**
     * Per-load trace. Carries both a short string [OpenTrace.id] (for log stitching — same
     * contract as the previous [themeLoadTraceId] field) AND the [OpenTrace.topicId] plus a
     * monotonically increasing [OpenTrace.callIndex] so the late-response guards can
     * distinguish a stale `getnewpost → findpost` escalation from the currently active load
     * even when the [OpenTrace.id] string was just re-rolled on BACK.
     */
    private data class OpenTrace(val id: String, val topicId: Int?, val callIndex: Long)
    private var openTrace: OpenTrace = OpenTrace("", null, 0L)
    private var loadCallCounter: Long = 0L
    private var pendingRefreshRequest: RefreshRequest? = null

    /** Отменяем предыдущий HTTP-запрос темы, иначе два ответа гоняются и в WebView попадает случайный. */
    private var loadThemeJob: Job? = null
    private var hatMetadataJob: Job? = null
    private var pageMetadataEnrichmentJob: Job? = null
    private var infiniteSession = 0
    private val loadedPages = linkedMapOf<Int, ThemePage>()
    private var firstPageHatPostId: Int? = null
    private var emptyTopicReloadKey: String? = null
    private var emptyTopicReloadAttempts: Int = 0
    private var topicHatPost: ThemePost? = null
    private var topicHatTopicId: Int? = null
    private var userHatOpenOverride: Boolean? = null
    private var pendingHatToolbarClick: Boolean = false
    private var pendingHatToolbarOpenAfterRender: Boolean = false

    private val subscriptionsStarted = AtomicBoolean(false)

    fun start() {
        if (!subscriptionsStarted.compareAndSet(false, true)) return
        currentTopicScrollMode = themeUseCase.getTopicScrollMode()
        currentTopicPostDensity = themeUseCase.getTopicPostDensity()
        currentTopicToolbarBehavior = themeUseCase.getTopicToolbarBehavior()
        currentTopicBackBehavior = themeUseCase.getTopicBackBehavior()
        currentTopicOpenTarget = themeUseCase.getTopicOpenTarget()
        currentTopicHeaderInitialState = themeUseCase.getTopicHeaderInitialState()
        if (BuildConfig.DEBUG) {
            Log.i(THEME_HISTORY_TAG, "start backBehavior=$currentTopicBackBehavior")
        }
        scope.launch {
            themeUseCase.observeShowAvatarsFlow().collect {
                _uiEvents.emit(ThemeUiEvent.UpdateShowAvatarState(it))
            }
        }

        scope.launch {
            themeUseCase.observeCircleAvatarsFlow().collect {
                _uiEvents.emit(ThemeUiEvent.UpdateTypeAvatarState(it))
            }
        }

        scope.launch {
            themeUseCase.observeWebViewFontSizeFlow().collect {
                _uiEvents.emit(ThemeUiEvent.SetFontSize(it))
            }
        }

        scope.launch {
            themeUseCase.observeAppFontModeFlow().collect {
                _uiEvents.emit(ThemeUiEvent.SetAppFontMode(it))
            }
        }

        scope.launch {
            themeUseCase.observeThemeTypeFlow().collect {
                _uiEvents.emit(ThemeUiEvent.SetStyleType(it))
            }
        }
        scope.launch {
            themeUseCase.observeScrollButtonEnabledFlow().collect {
                _uiEvents.emit(ThemeUiEvent.UpdateScrollButtonState(it))
            }
        }
        scope.launch {
            themeUseCase.observeTopicPaginationPanelEnabledFlow().collect {
                _uiEvents.emit(ThemeUiEvent.UpdateTopicPaginationPanelState(it))
            }
        }
        scope.launch {
            themeUseCase.observeTopicScrollModeFlow().collect {
                handleTopicScrollModeChanged(it)
            }
        }
        scope.launch {
            themeUseCase.observeTopicPostDensityFlow().collect {
                handleTopicPostDensityChanged(it)
            }
        }
        scope.launch {
            themeUseCase.observeTopicToolbarBehaviorFlow().collect {
                handleTopicToolbarBehaviorChanged(it)
            }
        }
        scope.launch {
            themeUseCase.observeTopicBackBehaviorFlow().collect {
                currentTopicBackBehavior = it
                if (BuildConfig.DEBUG) {
                    Log.i(THEME_HISTORY_TAG, "settings backBehavior=$it")
                }
            }
        }
        scope.launch {
            themeUseCase.observeTopicOpenTargetFlow().collect {
                currentTopicOpenTarget = it
            }
        }
        scope.launch {
            themeUseCase.observeTopicHeaderInitialStateFlow().collect {
                currentTopicHeaderInitialState = it
            }
        }
        scope.launch {
            themeUseCase.observeTopicPageSwipeEnabledFlow().collect {
                _uiEvents.emit(ThemeUiEvent.UpdateTopicPageSwipeState(it))
            }
        }
        scope.launch {
            themeUseCase.observeTopicBottomRefreshGestureEnabledFlow().collect {
                _uiEvents.emit(ThemeUiEvent.UpdateBottomRefreshGestureState(it))
            }
        }
        scope.launch {
            themeUseCase.observeEventsTab()
                    .sample(300L)
                    .collect { realtimeEventsHandler.handleEvent(it) }
        }
        loadUrl(themeUrl.trim(), lastOpenSourceScreen.takeIf { it != "unknown" } ?: "theme_tab")
    }

    private suspend fun handleTopicScrollModeChanged(mode: AppPreferences.Main.TopicScrollMode) {
        val previousMode = currentTopicScrollMode
        currentTopicScrollMode = mode
        _uiEvents.tryEmit(ThemeUiEvent.UpdateTopicScrollMode(mode))
        if (previousMode == mode) return

        infiniteScrollController.cancelAll()

        val page = currentPage ?: return
        val pageToRender = if (mode == AppPreferences.Main.TopicScrollMode.CLASSIC) {
            loadedPages[visibleCurrentPage ?: page.pagination.current] ?: page
        } else {
            page
        }
        pageToRender.url?.takeIf { it.isNotBlank() }?.let { themeUrl = it }
        currentPage = pageToRender
        visibleCurrentPage = pageToRender.pagination.current
        loadedPages.clear()
        loadedPages[pageToRender.pagination.current] = pageToRender
        themeUseCase.mapEntity(pageToRender, "scrollMode")
        _updateView.tryEmit(pageToRender)
    }

    private suspend fun handleTopicPostDensityChanged(density: AppPreferences.Main.TopicPostDensity) {
        val previousDensity = currentTopicPostDensity
        currentTopicPostDensity = density
        _uiEvents.tryEmit(ThemeUiEvent.UpdateTopicPostDensity(density))
        if (previousDensity == density) return

        val page = currentPage ?: return
        themeUseCase.mapEntity(page, "postDensity")
        _updateView.tryEmit(page)
    }

    private fun handleTopicToolbarBehaviorChanged(behavior: AppPreferences.Main.TopicToolbarBehavior) {
        currentTopicToolbarBehavior = behavior
        _uiEvents.tryEmit(ThemeUiEvent.UpdateTopicToolbarBehavior(behavior))
    }

    override fun onCleared() {
        loadThemeJob?.cancel()
        hatMetadataJob?.cancel()
        titleFromFirstPageJob?.cancel()
        pageMetadataEnrichmentJob?.cancel()
        postEditCoordinator.dispose()
        infiniteScrollController.cancelAll()
        super.onCleared()
    }

    private fun resolveTopicOpenUrl(rawUrl: String, sourceScreen: String): TopicOpenResolution {
        val previousPage = currentPage
        val hints = listOpenHints
        val openTarget = themeUseCase.getTopicOpenTarget().also { currentTopicOpenTarget = it }
        val context = TopicOpenContext(
                rawUrl = rawUrl,
                setting = openTarget,
                sourceScreen = sourceScreen,
                sourceUrl = rawUrl,
                openIntentRaw = lastOpenIntent,
                unreadUrlFromList = hints?.unreadUrlFromList,
                unreadPostIdFromList = hints?.unreadPostIdFromList,
                listTopicMarkedUnread = hints?.topicMarkedUnread == true,
                inspectorMarkedUnread = hints?.inspectorMarkedUnread == true,
                lastReadUrlFromList = hints?.lastReadUrlFromList,
                cachedLastPage = previousPage?.pagination?.current,
                cachedScrollPosition = previousPage?.scrollY
        )
        val resolution = TopicOpenTargetResolver.resolve(context)
        pendingParserListUnreadHint = TopicUnreadOpenPolicy.parserTrustsGetNewPostUnread(
                hints,
                resolution.url,
                openTarget,
        )
        activeOpenSessionKind = TopicUnreadOpenPolicy.resolveOpenSessionKindAtResolve(context, resolution)
        lastOpenResolution = resolution
        activeNavigationTarget = TopicOpenTargetMapper.from(
                resolution = resolution,
                loadAction = _loadAction,
                openIntentRaw = lastOpenIntent,
                backSnapshot = historyController.peekBackSnapshot(
                        context.topicId ?: ThemeApi.extractTopicIdFromUrl(rawUrl) ?: 0,
                        ThemeApi.extractStFromUrl(rawUrl) ?: 0
                ),
                refreshRestoreId = getRefreshRestoreId(),
                refreshRestoreMode = getRefreshRestoreMode(),
                refreshRestoreSource = getRefreshRestoreSource()
        )
        val suppressScroll = !TopicOpenScrollRestorePolicy.savedScrollRestoreAllowed(
                openTarget = activeNavigationTarget,
                loadAction = _loadAction
        ) || resolution.suppressScrollRestore
        suppressScrollRestoreForOpen = suppressScroll
        pendingUnreadOpenSuppressScroll = resolution.suppressScrollRestore
        val scrollRestoreAllowed = savedScrollRestoreAllowedForCurrentOpen()
        TopicOpenTrace.log(
                context,
                resolution,
                TopicOpenTraceExtras(
                        event = "resolve_url",
                        traceId = openTrace.id.takeIf { it.isNotBlank() },
                        requestId = openTrace.id.takeIf { it.isNotBlank() },
                        openIntent = lastOpenIntent,
                        isFreshOpen = isFreshTopicOpen(),
                        isBackRestore = isRestoreTopicOpen(),
                        savedScrollRestoreAllowed = scrollRestoreAllowed,
                        savedScrollRestoreBlockedReason = TopicOpenScrollRestorePolicy.blockedReason(
                                openIntentRaw = lastOpenIntent,
                                setting = openTarget,
                                loadAction = ThemeLoadAction.Normal,
                                suppressScrollRestoreForOpen = suppressScroll
                        ),
                        scrollRestoreAllowed = scrollRestoreAllowed,
                        savedPage = previousPage?.pagination?.current,
                        savedPostId = previousPage?.anchorPostId,
                        savedScrollY = previousPage?.scrollY,
                        suppressScrollRestore = suppressScroll,
                        openSessionKind = activeOpenSessionKind?.name,
                )
        )
        return resolution
    }

    private fun updateActiveNavigationTarget(url: String, action: ThemeLoadAction) {
        val resolution = lastOpenResolution ?: TopicOpenTargetResolver.resolve(
                TopicOpenContext(
                        rawUrl = url,
                        setting = currentTopicOpenTarget,
                        sourceScreen = lastOpenSourceScreen,
                        openIntentRaw = lastOpenIntent,
                        unreadUrlFromList = listOpenHints?.unreadUrlFromList,
                        unreadPostIdFromList = listOpenHints?.unreadPostIdFromList,
                        listTopicMarkedUnread = listOpenHints?.topicMarkedUnread == true,
                        inspectorMarkedUnread = listOpenHints?.inspectorMarkedUnread == true,
                        lastReadUrlFromList = listOpenHints?.lastReadUrlFromList
                )
        )
        val topicId = ThemeApi.extractTopicIdFromUrl(url) ?: 0
        val pageSt = ThemeApi.extractStFromUrl(url) ?: 0
        val backSnapshot = when (action) {
            ThemeLoadAction.Back -> historyController.peekBackSnapshot(topicId, pageSt)
            else -> historyController.peekBackSnapshot(topicId, pageSt)
        }
        activeNavigationTarget = TopicOpenTargetMapper.from(
                resolution = resolution,
                loadAction = action,
                openIntentRaw = lastOpenIntent,
                backSnapshot = backSnapshot,
                refreshRestoreId = pendingRefreshRequest?.id ?: getRefreshRestoreId(),
                refreshRestoreMode = pendingRefreshRequest?.restoreMode?.name ?: getRefreshRestoreMode(),
                refreshRestoreSource = pendingRefreshRequest?.source ?: getRefreshRestoreSource()
        )
        if (action == ThemeLoadAction.Normal || action == ThemeLoadAction.End) {
            val allowSaved = TopicOpenScrollRestorePolicy.savedScrollRestoreAllowed(
                    openTarget = activeNavigationTarget,
                    loadAction = action
            )
            if (!allowSaved) {
                suppressScrollRestoreForOpen = true
            }
        }
    }

    private fun explicitTargetPostIdFromUrl(url: String?): Int? {
        val raw = url?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val info = ThemeUrlPolicy.parse(raw)
        return info?.postId?.takeIf { it > 0 && (info.isFindPost || raw.contains("#entry", ignoreCase = true)) }
    }

    private data class RenderedReadTarget(
            val topicId: Int,
            val postId: Int? = null,
            val page: Int? = null
    )

    fun exit() {
        router.exit()
    }

    fun getPageScrollY() = currentPage?.scrollY ?: 0

    fun getAnchorPostId() = currentPage?.anchorPostId ?: currentPage?.anchor

    fun getPendingPostedPageScrollAnchor(): String? = pendingPostedPageScrollAnchor

    fun isPendingPostedPageScrollExact(): Boolean = pendingPostedPageScrollExact

    fun clearPendingPostedPageScroll() {
        pendingPostedPageScrollAnchor = null
        pendingPostedPageScrollExact = false
        clearPostedPageScrollPending()
    }

    private fun captureScrollAnchorForEditPost(postId: Int) {
        if (postId <= 0) return
        pendingEditCancelScrollPostId = postId
        currentPage?.let { page ->
            page.anchorPostId = postId.toString()
            page.anchors.clear()
            page.addAnchor("entry$postId")
            page.scrollY = 0
            page.scrollRatio = null
            page.wasNearBottom = false
            page.anchorOffsetTop = null
        }
    }

    private fun applyEditCancelScrollRestoreIfNeeded() {
        val postId = pendingEditCancelScrollPostId ?: return
        pendingEditCancelScrollPostId = null
        pendingPostedPageScrollAnchor = postId.toString()
        pendingPostedPageScrollExact = true
        currentPage?.let { page ->
            page.anchorPostId = postId.toString()
            page.anchors.clear()
            page.addAnchor("entry$postId")
            page.scrollY = 0
            page.scrollRatio = null
            page.wasNearBottom = false
            page.anchorOffsetTop = null
        }
    }

    fun canQuote() = currentPage?.canQuote ?: false

    fun isPageLoaded() = currentPage != null

    /**
     * Re-emits a loaded page or clears an orphaned spinner when the fragment becomes visible again
     * after a missed [updateView] emission or a cancelled [loadThemeJob].
     */
    fun reconcileStuckLoadOnFragmentVisible() {
        val page = currentPage
        val pageHtml = page?.html
        if (ThemeStuckLoadRecoveryPolicy.shouldReEmitLoadedPage(pageHtml)) {
            scope.launch {
                if (page != null && openTrace.id.isNotBlank()) {
                    _updateView.tryEmit(page)
                }
            }
            return
        }
        if (ThemeStuckLoadRecoveryPolicy.shouldClearOrphanedRefreshing(
                        isRefreshing = isRefreshing(),
                        loadJobActive = loadThemeJob?.isActive == true,
                        pageHtml = pageHtml,
                )
        ) {
            _setRefreshing.value = false
        }
    }

    fun isInFavorites() = currentPage?.isInFavorite ?: false

    fun hasPoll() = currentPage?.poll != null

    fun hasTopicHat(): Boolean {
        val page = currentPage ?: return false
        return TopicHatAvailability.hasTopicHat(
                page = page,
                cachedHat = cachedTopicHatFor(page),
                cachedHatTopicId = topicHatTopicId,
                firstPageHatPostId = firstPageHatPostId,
        )
    }

    fun hasPendingHatToolbarOverlayOpen(): Boolean = pendingHatToolbarOpenAfterRender

    fun cancelHatToolbarOpenAttempt() {
        pendingHatToolbarClick = false
        pendingHatToolbarOpenAfterRender = false
        if (userHatOpenOverride == true && currentPage?.isHatOpen != true) {
            userHatOpenOverride = null
            _uiEvents.tryEmit(ThemeUiEvent.UpdateHatOpenState(false))
        }
    }

    fun acknowledgeHatToolbarOverlayOpened() {
        pendingHatToolbarClick = false
        pendingHatToolbarOpenAfterRender = false
    }

    /**
     * Handles toolbar «шапка темы» tap: toggles floating overlay via JS when host exists,
     * otherwise injects overlay HTML for the current page (no navigation to page 1).
     *
     * Does not set [userHatOpenOverride] on the JS path — [ThemeJsInterface.setHatOpen] confirms
     * the overlay actually opened (log: hatOpen=true without ThemeHat open=true).
     */
    fun handleHatToolbarClick(forceInjectOverlay: Boolean = false): ThemeHatToolbarClickAction {
        val page = currentPage ?: return ThemeHatToolbarClickAction.UNAVAILABLE
        ThemeToolbarTitlePolicy.mergeTitleFromSession(page, currentPage, loadedPages.values)
        rememberSessionTopicTitle(page.title)
        val overlayInHtml = ThemeHatToolbarClickPolicy.shouldToggleOverlayViaJs(page.html)
        if (!forceInjectOverlay && overlayInHtml) {
            return ThemeHatToolbarClickAction.RUN_JS
        }
        userHatOpenOverride = true
        page.isHatOpen = false
        var hat = resolveTopicHatForOverlay(page)
        if (hat == null) {
            cachedTopicHatFor(page)?.let { cached ->
                page.topicHatPost = cached
                hat = cached
            }
        }
        if (hat == null) {
            pendingHatToolbarClick = true
            pendingHatToolbarOpenAfterRender = true
            maybeLoadTopicHatMetadata(page, forceToolbarOpen = true)
            hat = resolveTopicHatForOverlay(page)
        }
        val resolvedHat = hat ?: return ThemeHatToolbarClickAction.PENDING_METADATA
        pendingHatToolbarClick = false
        page.topicHatPost = resolvedHat
        enrichTopicHatPostFromKnownOriginal(page, resolvedHat)
        scheduleHatOverlayWebViewRender(page, resolvedHat)
        return ThemeHatToolbarClickAction.RENDER_SCHEDULED
    }

    private fun scheduleHatOverlayWebViewRender(page: ThemePage, hat: ThemePost) {
        page.topicHatPost = hat
        enrichTopicHatPostFromKnownOriginal(page, hat)
        pendingHatToolbarClick = false
        pendingHatToolbarOpenAfterRender = true
        val traceId = openTrace.id
        hatOverlayReinjectionTraceId = traceId
        scope.launch {
            ThemeToolbarTitlePolicy.mergeTitleFromSession(page, currentPage, loadedPages.values)
            rememberSessionTopicTitle(page.title)
            TopicHatOpenPolicy.prepareOverlayStateForRender(
                    page = page,
                    userHatOpenOverride = userHatOpenOverride,
                    pendingToolbarOverlayOpen = pendingHatToolbarOpenAfterRender,
            )
            stripDuplicateHatFromNonFirstPage(page, page.pagination.current)
            mapEntityWithPostListGuard(page, "hatOverlayEnsure")
            if (traceId != openTrace.id) return@launch
            if (tryScheduleHatOverlayJsPatch(page, hat, openAfterInject = true)) {
                hatOverlayReinjectionTraceId = null
                pendingHatToolbarOpenAfterRender = false
                _uiEvents.tryEmit(ThemeUiEvent.UpdateHatOpenState(true))
                return@launch
            }
            remapHybridPagesForCurrentTopic(page, "hatOverlayEnsure")
            if (traceId != openTrace.id) return@launch
            _updateView.tryEmit(page)
            _uiEvents.tryEmit(ThemeUiEvent.UpdateHatOpenState(true))
        }
    }

    private suspend fun tryScheduleHatOverlayJsPatch(
            page: ThemePage,
            hat: ThemePost,
            openAfterInject: Boolean,
    ): Boolean {
        if (hat.id <= 0) return false
        val overlayHtml = themeUseCase.extractTopHatOverlayHostHtml(page.html) ?: return false
        emitHatOverlayJsPatch(hat.id, overlayHtml, openAfterInject)
        return true
    }

    private fun emitHatOverlayJsPatch(hatPostId: Int, overlayHostHtml: String, openAfterInject: Boolean) {
        if (hatPostId <= 0 || overlayHostHtml.isBlank()) return
        _uiEvents.tryEmit(ThemeUiEvent.StripPrependedTopicHatFromDom(hatPostId))
        _uiEvents.tryEmit(ThemeUiEvent.InjectTopicHatOverlay(overlayHostHtml, openAfterInject))
    }

    private suspend fun preloadHatOverlayHostForToolbar(page: ThemePage, hat: ThemePost) {
        if (hat.id <= 0 || page.pagination.current <= 1) return
        page.topicHatPost = hat
        enrichTopicHatPostFromKnownOriginal(page, hat)
        stripDuplicateHatFromNonFirstPage(page, page.pagination.current)
        TopicHatOpenPolicy.prepareOverlayStateForRender(
                page = page,
                userHatOpenOverride = userHatOpenOverride,
                pendingToolbarOverlayOpen = false,
        )
        mapEntityWithPostListGuard(page, "hatMetadataPreload")
        tryScheduleHatOverlayJsPatch(page, hat, openAfterInject = false)
    }

    private fun flushPendingHatOverlayRenderAfterScroll() {
        if (!pendingHatOverlayRenderAfterScroll || userHatOpenOverride != true) return
        val page = currentPage ?: return
        val hat = resolveTopicHatForOverlay(page) ?: return
        pendingHatOverlayRenderAfterScroll = false
        scheduleHatOverlayWebViewRender(page, hat)
    }

    fun requestHatOverlayInjection() {
        handleHatToolbarClick(forceInjectOverlay = true)
    }

    fun consumePendingHatToolbarClick(): Boolean = pendingHatToolbarClick.also { pendingHatToolbarClick = false }

    fun consumePendingHatToolbarOpenAfterRender(): Boolean {
        val pending = pendingHatToolbarOpenAfterRender
        pendingHatToolbarOpenAfterRender = false
        return TopicHatOpenPolicy.shouldDispatchOverlayOpenAfterRender(
                userHatOpenOverride = userHatOpenOverride,
                pendingToolbarOverlayOpen = pending,
        )
    }

    fun getId() = currentPage?.id ?: -1

    fun getSessionTopicTitle(): String? =
            sessionTopicTitle?.trim()?.takeIf { it.isNotEmpty() }
                    ?: lastKnownTopicTitle(currentPage?.id ?: activeLoadedTopicId)

    /**
     * Last-known title for the topic the toolbar is loading, used by the fragment to avoid showing
     * an empty toolbar for a page whose HTML omitted the title. Gated by topicId so cross-topic
     * switches never inherit the previous topic's label.
     */
    fun getLastKnownTopicTitleForToolbar(page: ThemePage): String? =
            lastKnownTopicTitle(page.id)

    fun setNavigationTopicTitle(title: String?) {
        navigationTopicTitle = title?.trim()?.takeIf { it.isNotEmpty() }
        rememberSessionTopicTitle(navigationTopicTitle)
    }

    fun rememberSessionTopicTitleFromFragment(title: String?) {
        rememberSessionTopicTitle(title)
        title?.trim()?.takeIf { it.isNotEmpty() }?.let { navigationTopicTitle = it }
    }

    fun notifyHybridPageToolbarRefresh() {
        currentPage?.let { page ->
            ThemeToolbarTitlePolicy.mergeTitleFromSession(page, null, loadedPages.values)
            rememberSessionTopicTitle(page.title)
            _uiEvents.tryEmit(ThemeUiEvent.UpdateTopicToolbar(page))
        }
    }

    fun shouldApplyToolbarPage(page: ThemePage): Boolean =
            Companion.shouldApplyToolbarPage(
                    pageTopicId = page.id,
                    targetTopicId = ThemeApi.extractTopicIdFromUrl(themeUrl),
                    loadedTopicId = currentPage?.id
            )

    private fun loadData(url: String, action: ThemeLoadAction, preserveOpenTrace: Boolean = false) {
        val startedAt = SystemClock.uptimeMillis()
        activeLoadParserUnreadHint = TopicUnreadOpenPolicy.captureParserListUnreadHintForLoad(
                pendingHintFromResolve = pendingParserListUnreadHint,
                hints = listOpenHints,
                fetchUrl = url,
                loadAction = action,
                setting = currentTopicOpenTarget,
        )
        activeLoadListTopicMarkedUnread = listOpenHints?.topicMarkedUnread == true
        val explicitTargetPostId = explicitTargetPostIdFromUrl(url)
        // R-02: a fresh load settles the previous gesture; open a new dispatch window so the next
        // distinct user navigation is allowed even if no source-anchor capture preceded it.
        navigationGestureGuard.reset()
        clearPendingScrollCommand()
        loadThemeJob?.cancel()
        postOpenEnrichmentController.reset()
        deferredFavoriteSyncJob?.cancel()
        hybridPrefetchJob?.cancel()
        infiniteScrollController.cancelAll()
        hatMetadataJob?.cancel()
        titleFromFirstPageJob?.cancel()
        pageMetadataEnrichmentJob?.cancel()
        infiniteSession++
        editorUseCase.bumpEditPrefetchGeneration()
        val requestedTopicId = ThemeApi.extractTopicIdFromUrl(url)
        val loadedTopicId = currentPage?.id
        val crossTopicLoad = isCrossTopicLoad(requestedTopicId, loadedTopicId)
        val freshSameTopicOpen = action == ThemeLoadAction.Normal &&
                isFreshTopicOpen() &&
                requestedTopicId != null &&
                requestedTopicId == loadedTopicId
        if (crossTopicLoad && requestedTopicId != null) {
            val preserveHistory = ThemeViewModel.shouldPreserveHistoryOnCrossTopicOpen(lastOpenSourceScreen)
            if (preserveHistory) {
                _uiEvents.tryEmit(ThemeUiEvent.UpdateHistoryLastHtml)
            }
            resetTransientStateForNewTopic(
                    requestedTopicId,
                    clearToolbar = true,
                    clearHistory = !preserveHistory
            )
            clearTopicHatCache()
            currentPage = null
        } else if (freshSameTopicOpen && requestedTopicId != null) {
            val preserveHistory = ThemeViewModel.shouldPreserveHistoryOnCrossTopicOpen(lastOpenSourceScreen)
            if (preserveHistory) {
                _uiEvents.tryEmit(ThemeUiEvent.UpdateHistoryLastHtml)
            }
            resetTransientStateForNewTopic(
                    requestedTopicId,
                    clearHistory = !preserveHistory,
                    preserveSessionTitle = true
            )
        }
        if (!preserveOpenTrace) {
            renderSettledTraceId = null
            initialAnchorScrollSettledTraceId = null
            unreadFindPostUpgradeTraceId = null
            nextPageUnreadReloadTraceId = null
        }
        pendingHatOverlayRenderAfterScroll = false
        hatOverlayReinjectionTraceId = null
        pendingHatMetadataEmitTraceId = null
        pendingRenderSettledAfterScroll = false
        if (!ThemeRefreshScrollRestorePolicy.shouldPreserveLoadedPagesOnRefresh(
                        action = action,
                        crossTopicLoad = crossTopicLoad,
                        freshSameTopicOpen = freshSameTopicOpen,
                        requestedTopicId = requestedTopicId,
                        currentTopicId = loadedTopicId
                )
        ) {
            loadedPages.clear()
        }
        val isBack = action == ThemeLoadAction.Back
        if (isBack || !preserveOpenTrace) {
            loadCallCounter += 1
            openTrace = OpenTrace(
                id = UUID.randomUUID().toString().replace("-", "").take(8),
                topicId = requestedTopicId,
                callIndex = loadCallCounter,
            )
        }
        val loadTraceId = openTrace.id
        if (action == ThemeLoadAction.Normal && !pendingHatToolbarOpenAfterRender) {
            userHatOpenOverride = null
        }
        val inlineHatOpen = initialInlineHatOpenForLoad(url, requestedTopicId)
        val suppressStoredInlineHatPreference = TopicInlineHatOpenPolicy.shouldSuppressStoredHatPreferenceForLoad(
                url = url,
                requestedTopicId = requestedTopicId,
                topicOpenTarget = currentTopicOpenTarget,
                sourceScreen = lastOpenSourceScreen,
                currentPage = currentPage,
        )
        val hatOpen = userHatOpenOverride ?: false
        var pollOpen = false
        currentPage?.let {
            pollOpen = it.isPollOpen
        }
        themeUrl = url
        val low = url.lowercase()
        openedViaFindPostLink = low.contains("view=findpost") || low.contains("act=findpost")
        _loadAction = action
        if (action == ThemeLoadAction.End) {
            pendingEndNavigation = true
        } else if (action != ThemeLoadAction.Refresh && action != ThemeLoadAction.Back) {
            pendingEndNavigation = false
        }
        updateActiveNavigationTarget(url, action)
        if (action == ThemeLoadAction.Refresh && requestedTopicId != null && requestedTopicId > 0) {
            themeUseCase.invalidateTopicPageCache(requestedTopicId)
        }
        if (action == ThemeLoadAction.Normal) {
            if (suppressScrollRestoreForOpen) {
                pendingRefreshRequest = null
            }
        } else if (action == ThemeLoadAction.End) {
            pendingRefreshRequest = null
            pendingUnreadOpenSuppressScroll = false
            suppressScrollRestoreForOpen = false
        }
        if (action == ThemeLoadAction.Normal &&
                openedViaFindPostLink &&
                loadThemeJob?.isActive == true &&
                requestedTopicId != null &&
                requestedTopicId > 0 &&
                requestedTopicId == currentPage?.id
        ) {
            if (BuildConfig.DEBUG) {
                Log.i(
                        REFRESH_SCROLL_TAG,
                        "skip loadData duplicate findpost in flight trace=$openTrace.id topic=$requestedTopicId"
                )
            }
            return
        }
        if (BuildConfig.DEBUG) {
            Timber.d("loadData: url=$url action=$action openedViaFindPost=$openedViaFindPostLink currentPage.id=${currentPage?.id}")
            Log.i(
                    REFRESH_SCROLL_TAG,
                    "vm loadData action=$action trace=$openTrace.id t=$startedAt url=$url currentScroll=${currentPage?.scrollY} anchor=${currentPage?.anchorPostId} offset=${currentPage?.anchorOffsetTop} ratio=${currentPage?.scrollRatio} wasNearBottom=${currentPage?.wasNearBottom}"
            )
        }
        FpdaDebugLog.logTheme(
                FpdaDebugLog.ThemeArea.LOAD,
                "load_start",
                mapOf(
                        "traceId" to loadTraceId,
                        "action" to action::class.simpleName.orEmpty(),
                        "topicId" to requestedTopicId,
                        "url" to FpdaDebugLog.sanitizeUrl(url),
                        "crossTopic" to crossTopicLoad,
                        "freshSameTopicOpen" to freshSameTopicOpen
                )
        )
        // При BACK scrollY уже сохранён в onPauseOrHide; не перезаписываем его текущим webView.scrollY,
        // который может быть 0 (вкладка была скрыта).
        // REFRESH из WebView-пути сам делает синхронный snapshot перед reload; второй emit здесь
        // может прийти уже после начала жеста/перерисовки и затереть bottom-состояние на scrollY=0.
        val isUnreadFirstOpen = action == ThemeLoadAction.Normal &&
                url.contains("view=getnewpost", ignoreCase = true) &&
                explicitTargetPostId == null &&
                currentTopicOpenTarget == AppPreferences.Main.TopicOpenTarget.LAST_UNREAD
        if (action != ThemeLoadAction.Back && !skipNextRefreshScrollCapture && !isUnreadFirstOpen) {
            _uiEvents.tryEmit(ThemeUiEvent.UpdateHistoryLastHtml)
        }
        skipNextRefreshScrollCapture = false

        // Special handling for QMS URLs (act=qms) - load directly without theme templating
        if (low.contains("act=qms")) {
            val previousQmsJob = loadThemeJob
            loadThemeJob = scope.launch {
                if (previousQmsJob != null && previousQmsJob.isActive) {
                    try { previousQmsJob.join() } catch (_: kotlinx.coroutines.CancellationException) { /* expected */ }
                }
                _setRefreshing.value = true
                when (val result = themeUseCase.loadQms(url)) {
                    is ThemeUseCase.LoadResult.Success -> onLoadData(result.page)
                    is ThemeUseCase.LoadResult.Error -> {
                        _uiEvents.tryEmit(ThemeUiEvent.ShowError(result.userMessage))
                    }
                }
                _setRefreshing.value = false
            }
            return
        }

        val previousLoadJob = loadThemeJob
        loadThemeJob = scope.launch {
            // Wait for the previous load to fully finish (or be cancelled) before starting
            // a new network call. The previous job was already cancelled at the top of
            // loadData via loadThemeJob?.cancel(); join() here ensures its body has stopped
            // executing before we kick off a new one, so two responses cannot interleave.
            if (previousLoadJob != null && previousLoadJob.isActive) {
                try { previousLoadJob.join() } catch (_: kotlinx.coroutines.CancellationException) { /* expected */ }
            }
            val networkStartedAt = SystemClock.uptimeMillis()
            _setRefreshing.value = true
            if (BuildConfig.DEBUG) Log.i(REFRESH_SCROLL_TAG, "vm networkStart action=$action trace=$loadTraceId dt=${networkStartedAt - startedAt}")
            try {
                val openFromUnreadListHint = activeLoadParserUnreadHint
                when (val result = themeUseCase.loadTheme(url, hatOpen, pollOpen, openFromUnreadListHint)) {
                    is ThemeUseCase.LoadResult.Success -> {
                        if (loadTraceId != openTrace.id) {
                            if (BuildConfig.DEBUG) {
                                Log.w(
                                        REFRESH_SCROLL_TAG,
                                        "vm dropStaleThemeResponse trace=$loadTraceId currentTrace=$openTrace.id topic=${result.page.id}"
                                )
                            }
                            FpdaDebugLog.logTheme(
                                    FpdaDebugLog.ThemeArea.LOAD,
                                    "stale_response_ignored",
                                    mapOf(
                                            "traceId" to loadTraceId,
                                            "currentTraceId" to openTrace.id,
                                            "topicId" to result.page.id
                                    ),
                                    warn = true
                            )
                            return@launch
                        }
                        val expectedTopicId = ThemeApi.extractTopicIdFromUrl(themeUrl)
                        if (expectedTopicId != null && expectedTopicId > 0 && result.page.id != expectedTopicId) {
                            if (BuildConfig.DEBUG) {
                                Log.w(
                                        REFRESH_SCROLL_TAG,
                                        "vm dropMismatchedThemeResponse trace=$loadTraceId expectedTopic=$expectedTopicId loadedTopic=${result.page.id} url=$themeUrl"
                                )
                            }
                            return@launch
                        }
                        // Fix C: second guard. Even when the trace id matches (no
                        // rotation happened in between), a stale response whose topic
                        // doesn't match the currently displayed topic must be dropped,
                        // so it cannot overwrite a newer topic's state.
                        if (openTrace.callIndex > 0L &&
                                activeLoadedTopicId != null &&
                                result.page.id != activeLoadedTopicId) {
                            FpdaDebugLog.logTheme(
                                    FpdaDebugLog.ThemeArea.LOAD,
                                    "dropMismatched",
                                    mapOf(
                                            "traceId" to openTrace.id,
                                            "expectedTopicId" to activeLoadedTopicId,
                                            "loadedTopicId" to result.page.id,
                                    ),
                                    warn = true
                            )
                            return@launch
                        }
                        if (BuildConfig.DEBUG) {
                            Log.i(
                                    REFRESH_SCROLL_TAG,
                                    "vm networkSuccess action=$action trace=$loadTraceId dt=${SystemClock.uptimeMillis() - startedAt} posts=${result.page.posts.size} html=${result.page.html?.length ?: 0}"
                            )
                            Log.i(
                                    THEME_RENDER_TAG,
                                    "networkSuccess action=$action trace=$loadTraceId topic=${result.page.id} page=${result.page.pagination.current}/${result.page.pagination.all} posts=${result.page.posts.size} html=${result.page.html?.length ?: 0} url=${result.page.url}"
                            )
                        }
                        FpdaDebugLog.logTheme(
                                FpdaDebugLog.ThemeArea.LOAD,
                                "network_success",
                                mapOf(
                                        "traceId" to loadTraceId,
                                        "action" to action::class.simpleName.orEmpty(),
                                        "topicId" to result.page.id,
                                        "posts" to result.page.posts.size,
                                        "htmlLen" to result.page.html?.length,
                                        "page" to result.page.pagination.current,
                                        "allPages" to result.page.pagination.all,
                                        "durationMs" to (SystemClock.uptimeMillis() - startedAt)
                                )
                        )
                        result.page.isInlineHatOpen = TopicInlineHatOpenPolicy.resolveOpenStateForLoad(
                                storedOpen = result.page.isInlineHatOpen,
                                hasStoredPreference = !suppressStoredInlineHatPreference &&
                                        themeUseCase.hasInlineHatPreference(result.page.id),
                                initialFromSetting = inlineHatOpen,
                        )
                        explicitTargetPostId?.let { explicitTargetPostIds[result.page] = it }
                        onLoadData(result.page, startedAt, loadTraceId)
                        if (!isActive || loadTraceId != openTrace.id) return@launch
                        if (runPendingSmartEndFallbackIfNeeded()) return@launch
                    }
                    is ThemeUseCase.LoadResult.Error -> {
                        if (loadTraceId == openTrace.id) {
                            _uiEvents.tryEmit(ThemeUiEvent.ShowError(result.userMessage))
                        }
                    }
                }
                if (!isActive || loadTraceId != openTrace.id) return@launch
                if (runPendingSmartEndFallbackIfNeeded()) return@launch
                if (BuildConfig.DEBUG) Log.i(REFRESH_SCROLL_TAG, "vm refreshFalse action=$action trace=$loadTraceId dt=${SystemClock.uptimeMillis() - startedAt}")
            } finally {
                if (ThemeLoadRefreshingPolicy.shouldClearRefreshingOnJobEnd(loadTraceId, openTrace.id)) {
                    _setRefreshing.value = false
                }
            }
        }
    }

    private suspend fun onLoadData(
            page: ThemePage,
            startedAt: Long = SystemClock.uptimeMillis(),
            traceId: String = openTrace.id
    ) {
        if (traceId != openTrace.id) {
            if (BuildConfig.DEBUG) {
                Log.w(
                        REFRESH_SCROLL_TAG,
                        "vm dropStaleOnLoadData trace=$traceId currentTrace=$openTrace.id topic=${page.id}"
                )
            }
            return
        }
        if (BuildConfig.DEBUG) {
            Log.i(
                    "HybridScroll",
                    "screen loaded mode=$currentTopicScrollMode topic=${page.id} current=${page.pagination.current} all=${page.pagination.all} perPage=${page.pagination.perPage}"
            )
        }
        if (BuildConfig.DEBUG) {
            val serverP = ThemeApi.extractScrollPostIdFromFinalTopicUrl(page.url.orEmpty())
            Timber.d(
                    "onLoadData trace=$openTrace.id topicId=${page.id} _loadAction=$_loadAction anchor=${page.anchor} anchors=${page.anchors.size} finalUrl=${page.url} requestUrl=$themeUrl serverP=$serverP"
            )
        }
        if (BuildConfig.DEBUG) Timber.d("onLoadData: topicId=${page.id} pagination.current=${page.pagination.current} pagination.perPage=${page.pagination.perPage} st=${page.st} _loadAction=$_loadAction historySize=${historyController.size}")
        if (!validateLoadedPage(page)) {
            _uiEvents.tryEmit(ThemeUiEvent.ShowError("Ошибка загрузки страницы"))
            return
        }
        activeOpenSessionKind = TopicUnreadOpenPolicy.parseOpenSessionKind(page.openSessionKind)
                ?: TopicUnreadOpenPolicy.resolveOpenSessionKindFromPage(
                        page = page,
                        initialRequestUrl = themeUrl,
                        openFromUnreadListHint = activeLoadParserUnreadHint,
                )
        if (TopicUnreadOpenPolicy.shouldPreserveUnreadTargetAfterLoad(
                        setting = currentTopicOpenTarget,
                        loadAction = _loadAction,
                        parserListUnreadHint = activeLoadParserUnreadHint,
                        openedViaFindPost = openedViaFindPostLink,
                        findPostUpgradeTraceMatches = unreadFindPostUpgradeTraceId == openTrace.id,
                        requestUrl = themeUrl,
                        anchorPostId = page.anchorPostId ?: page.anchor?.removePrefix("entry"),
                        ambiguousBottomRedirect = page.ambiguousLastUnreadBottomRedirect,
                )
        ) {
            page.hasUnreadTarget = true
        }
        // Next-page unread reload landed (the getnewpost redirect hit the bottom boundary of the
        // previous page and we advanced here). If this page has no HTML unread markers, the genuine
        // first-unread is simply its FIRST content post — the post right after the boundary (device
        // log 26_06-15-24, topic 1050118: page 4396 had htmlUnreadCount=0, so the open lost the unread
        // target and fell to last_post_on_page_fallback / a visible drift). Anchor there explicitly.
        if (nextPageUnreadReloadTraceId == openTrace.id) {
            nextPageUnreadReloadTraceId = null
            if (!page.hasUnreadTarget) {
                val firstUnreadPostId = TopicPrependedHatPolicy
                        .filterPostsForPageList(page)
                        .firstOrNull { it.id > 0 }
                        ?.id
                if (firstUnreadPostId != null) {
                    page.anchors.clear()
                    page.addAnchor("entry$firstUnreadPostId")
                    page.anchorPostId = firstUnreadPostId.toString()
                    page.hasUnreadTarget = true
                    Log.i(
                            TopicUnreadFindPostReloadPolicy.LOG_TAG,
                            "next_page_first_unread_anchor topic=${page.id} st=${page.st} page=${page.pagination.current} firstUnread=$firstUnreadPostId trace=$openTrace.id"
                    )
                }
            }
        }
        if (themeUrl.contains("view=getnewpost", ignoreCase = true)) {
            // Log 25_06-10-39 (1103268): a genuine list-unread getnewpost redirected to the BOTTOM
            // post of page 1317 while pageTotal=1318 — the real first-unread is the top of page 1318.
            // Parking on the page-1317 bottom post made the open "show the last post", blocked the
            // mark-read gate (`not_last_page` current=1317 < all=1318), and left the topic unread
            // forever (every re-open re-resolved the same boundary). Advance to the next page once per
            // trace so the user reaches the genuine unread content and the topic can later be marked
            // read by the normal scroll-to-bottom exit policy.
            val nextPageUnreadSt = TopicUnreadOpenPolicy.nextPageUnreadReloadSt(page)
            if (nextPageUnreadSt != null &&
                    page.id > 0 &&
                    unreadFindPostUpgradeTraceId != openTrace.id
            ) {
                unreadFindPostUpgradeTraceId = openTrace.id
                nextPageUnreadReloadTraceId = openTrace.id
                // Plain page URL (NOT view=getnewpost): the server's getnewpost ignores st and would
                // re-redirect to the same page-1317 bottom boundary. Loading the next page directly
                // lands on the real final page (log line 1941: st=26340 → page 1318/1318), whose HTML
                // unread markers let the parser resolve the genuine first-unread post.
                val nextPageUrl =
                        "https://4pda.to/forum/index.php?showtopic=${page.id}&st=$nextPageUnreadSt"
                forpdateam.ru.forpda.diagnostic.TopicUnreadAnchorDiagnostics.findPostReloadStarted(
                        topicId = page.id,
                        anchorPostId = "next_page_st_$nextPageUnreadSt",
                        traceId = openTrace.id,
                )
                Log.w(
                        TopicUnreadFindPostReloadPolicy.LOG_TAG,
                        "reload_next_page_unread topic=${page.id} st=$nextPageUnreadSt current=${page.pagination.current} all=${page.pagination.all} trace=$openTrace.id"
                )
                loadData(nextPageUrl, ThemeLoadAction.Normal, preserveOpenTrace = true)
                return
            }
            // Log 14_06-21-08: a fully-read read-row (1121483) redirects via getnewpost to a
            // bottom bookmark (#entry143852904) that is NOT on the loaded last page window
            // (last on-page post is 143852868). realignOffPageGetNewPostAnchor would otherwise
            // strand the user on an earlier on-page post. Reload at view=findpost&p=<redirectId>
            // once per trace to fetch the real last page that contains the redirect post.
            val offPageReloadId = TopicUnreadOpenPolicy.offPageReadResumeFindPostReloadId(page)
            if (offPageReloadId != null &&
                    page.id > 0 &&
                    unreadFindPostUpgradeTraceId != openTrace.id
            ) {
                unreadFindPostUpgradeTraceId = openTrace.id
                val offPageReloadAnchor = offPageReloadId.toString()
                val findPostUrl = TopicUnreadFindPostReloadPolicy.buildFindPostUrl(page.id, offPageReloadAnchor)
                forpdateam.ru.forpda.diagnostic.TopicUnreadAnchorDiagnostics.findPostReloadStarted(
                        topicId = page.id,
                        anchorPostId = offPageReloadAnchor,
                        traceId = openTrace.id,
                )
                Log.i(
                        TopicUnreadFindPostReloadPolicy.LOG_TAG,
                        "reload_findpost_offpage topic=${page.id} anchor=$offPageReloadAnchor trace=$openTrace.id"
                )
                loadData(findPostUrl, ThemeLoadAction.Normal, preserveOpenTrace = true)
                return
            }
            // U-01 (audit Finding U-01, Critical): a GENUINE unread open whose confirmed first-unread
            // post is off the loaded page window must not fall through to realignOffPageGetNewPostAnchor
            // (which would anchor to the first parsed post of the loaded — usually last — page). Reload
            // once at view=findpost&p=<unreadPostId> to fetch the real page that contains the unread post,
            // so the highlight/scroll target is actually present.
            val offPageUnreadReloadId = TopicUnreadOpenPolicy.offPageUnreadFindPostReloadId(page)
            if (offPageUnreadReloadId != null &&
                    page.id > 0 &&
                    unreadFindPostUpgradeTraceId != openTrace.id
            ) {
                unreadFindPostUpgradeTraceId = openTrace.id
                val offPageUnreadAnchor = offPageUnreadReloadId.toString()
                val findPostUrl = TopicUnreadFindPostReloadPolicy.buildFindPostUrl(page.id, offPageUnreadAnchor)
                forpdateam.ru.forpda.diagnostic.TopicUnreadAnchorDiagnostics.openTarget(
                        topicId = page.id,
                        openTarget = "unread_off_page",
                        expectedPostId = offPageUnreadAnchor,
                        actualPostId = page.posts.firstOrNull { it.id > 0 }?.id?.toString(),
                        hasUnreadTarget = true,
                        anchorSource = "off_page_unread_findpost_reload",
                        traceId = openTrace.id,
                )
                forpdateam.ru.forpda.diagnostic.TopicUnreadAnchorDiagnostics.findPostReloadStarted(
                        topicId = page.id,
                        anchorPostId = offPageUnreadAnchor,
                        traceId = openTrace.id,
                )
                Log.w(
                        TopicUnreadFindPostReloadPolicy.LOG_TAG,
                        "reload_findpost_unread_offpage topic=${page.id} anchor=$offPageUnreadAnchor trace=$openTrace.id"
                )
                loadData(findPostUrl, ThemeLoadAction.Normal, preserveOpenTrace = true)
                return
            }
            TopicUnreadOpenPolicy.realignOffPageGetNewPostAnchor(page)
            // Log 14_06-18-05-59: 1121483/1103268/1109539/528252/1115025 are fully read.
            // The server returns a bottom #entry redirect (last-read bookmark) and the parser
            // nulls out `page.anchor` to avoid an "unread" target. We re-align the anchor to
            // the server last-read post so the WebView scrolls there instead of stranding on
            // the page hat (which the user reads as "random post 143733850").
            TopicUnreadOpenPolicy.resolveReadResumeBottomRedirect(
                    page = page,
                    listMarkedUnread = activeLoadListTopicMarkedUnread,
            )
        }
        val loadedPostIds = page.posts.map { it.id }
        val unreadAnchorPostId = TopicUnreadFindPostReloadPolicy.resolveAnchorPostId(
                anchorPostId = page.anchorPostId,
                pageAnchor = page.anchor,
        )
        val redirectEntryId = forpdateam.ru.forpda.model.data.remote.api.theme.ThemeApi
                .extractHashEntryPostIdFromTopicUrl(page.url.orEmpty())
                ?.toIntOrNull()
        val redirectIsBottomEntry = redirectEntryId != null &&
                loadedPostIds.lastOrNull() == redirectEntryId
        val listUnreadBottomRedirectAnchor = TopicUnreadFindPostReloadPolicy
                .resolveListUnreadBottomRedirectFindPostAnchor(
                        loadedPagePostIds = loadedPostIds,
                        redirectEntryId = redirectEntryId,
                )
        val ambiguousFindPostAnchor = if (redirectIsBottomEntry && activeLoadListTopicMarkedUnread) {
            listUnreadBottomRedirectAnchor
        } else {
            TopicUnreadFindPostReloadPolicy.resolveAmbiguousListUnreadFindPostAnchor(
                    loadedPagePostIds = loadedPostIds,
                    redirectEntryId = redirectEntryId,
            )
        }
        val shouldReloadFindPost = TopicUnreadFindPostReloadPolicy.shouldReloadAsFindPost(
                requestUrl = themeUrl,
                loadAction = _loadAction,
                scrollMode = currentTopicScrollMode,
                suppressScrollRestore = shouldSuppressScrollRestoreOnRender(),
                openedViaFindPost = openedViaFindPostLink,
                alreadyUpgradedThisTrace = unreadFindPostUpgradeTraceId == openTrace.id,
                hasUnreadTarget = page.hasUnreadTarget,
                anchorPostId = page.anchorPostId,
                pageAnchor = page.anchor,
                loadedPagePostIds = loadedPostIds,
        )
        val shouldReloadAmbiguousFindPost = TopicUnreadFindPostReloadPolicy.shouldReloadAmbiguousListUnreadAsFindPost(
                requestUrl = themeUrl,
                loadAction = _loadAction,
                scrollMode = currentTopicScrollMode,
                suppressScrollRestore = shouldSuppressScrollRestoreOnRender(),
                openedViaFindPost = openedViaFindPostLink,
                alreadyUpgradedThisTrace = unreadFindPostUpgradeTraceId == openTrace.id,
                parserListUnreadHint = activeLoadParserUnreadHint,
                ambiguousBottomRedirect = page.ambiguousLastUnreadBottomRedirect,
                hasUnreadTarget = page.hasUnreadTarget,
                fallbackAnchorPostId = ambiguousFindPostAnchor,
                redirectIsBottomEntry = redirectIsBottomEntry,
                listTopicMarkedUnread = activeLoadListTopicMarkedUnread,
        )
        val findPostUpgradeAnchor = when {
            shouldReloadFindPost -> unreadAnchorPostId
            shouldReloadAmbiguousFindPost -> ambiguousFindPostAnchor
            else -> null
        }
        if (findPostUpgradeAnchor == null &&
                themeUrl.contains("view=getnewpost", ignoreCase = true) &&
                currentTopicScrollMode == AppPreferences.Main.TopicScrollMode.HYBRID &&
                !page.hasUnreadTarget
        ) {
            val skipReason = when {
                page.ambiguousLastUnreadBottomRedirect && activeLoadParserUnreadHint ->
                    "ambiguous_all_read_no_fallback_anchor"
                unreadAnchorPostId != null -> "all_read_no_unread_target"
                else -> "getnewpost_no_unread_target"
            }
            forpdateam.ru.forpda.diagnostic.TopicUnreadAnchorDiagnostics.findPostReloadSkipped(
                    topicId = page.id,
                    reason = skipReason,
                    anchorPostId = unreadAnchorPostId ?: ambiguousFindPostAnchor,
                    hasUnreadTarget = false,
            )
        }
        if (findPostUpgradeAnchor != null && page.id > 0) {
            unreadFindPostUpgradeTraceId = openTrace.id
            val findPostUrl = TopicUnreadFindPostReloadPolicy.buildFindPostUrl(page.id, findPostUpgradeAnchor)
            forpdateam.ru.forpda.diagnostic.TopicUnreadAnchorDiagnostics.findPostReloadStarted(
                    topicId = page.id,
                    anchorPostId = findPostUpgradeAnchor,
                    traceId = openTrace.id,
            )
            Log.i(
                    TopicUnreadFindPostReloadPolicy.LOG_TAG,
                    "reload_findpost topic=${page.id} anchor=$findPostUpgradeAnchor ambiguous=${shouldReloadAmbiguousFindPost} trace=$openTrace.id"
            )
            loadData(findPostUrl, ThemeLoadAction.Normal, preserveOpenTrace = true)
            return
        }
        pendingRenderedReadTarget = renderedReadTargetFor(page, lastOpenResolution, activeNavigationTarget)
        normalizeStandalonePageHat(page)
        TopicHatOpenPolicy.prepareOverlayStateForRender(
                page = page,
                userHatOpenOverride = userHatOpenOverride,
                pendingToolbarOverlayOpen = pendingHatToolbarOpenAfterRender,
        )
        val cachedHat = cachedTopicHatFor(page)
        if (ThemeHatMetadataLoadPolicy.shouldEnrichHatFromCache(
                        page,
                        cachedHat,
                        visiblePageNumber = getVisibleCurrentPage(),
                )
        ) {
            cachedHat?.let { enrichTopicHatPostFromKnownOriginal(page, it) }
        }
        if (page.posts.isEmpty() && page.pagination.all > 0 && page.id > 0) {
            if (retryLoadAfterEmptyTopicPage(page)) return
        }
        val shouldRunPendingSmartEnd = pendingSmartEndTopicId == page.id && _loadAction != ThemeLoadAction.End
        val beforeMapAt = SystemClock.uptimeMillis()
        mapEntityWithPostListGuard(page, "initialLoad")
        if (page.posts.isNotEmpty()) {
            emptyTopicReloadKey = null
            emptyTopicReloadAttempts = 0
        }
        if (BuildConfig.DEBUG) {
            Log.i(
                    REFRESH_SCROLL_TAG,
                    "vm templateReady trace=$openTrace.id mapMs=${SystemClock.uptimeMillis() - beforeMapAt} dt=${SystemClock.uptimeMillis() - startedAt} posts=${page.posts.size} html=${page.html?.length ?: 0} loadedBefore=${loadedPages.keys.joinToString()}"
            )
            Log.i(
                    THEME_RENDER_TAG,
                    "templateReady reason=initialLoad trace=$openTrace.id action=$_loadAction mode=$currentTopicScrollMode topic=${page.id} page=${page.pagination.current}/${page.pagination.all} pagePosts=${page.posts.size} html=${page.html?.length ?: 0} containers=${countHtmlOccurrences(page.html, "theme_page_container")} postsInHtml=${countHtmlOccurrences(page.html, "post_container")} loadedBefore=${loadedPages.keys.joinToString()}"
            )
        }
        val beforeThemeLoadedAt = SystemClock.uptimeMillis()
        themeUseCase.onThemeLoaded(page)
        if (BuildConfig.DEBUG) {
            Log.i(
                    REFRESH_SCROLL_TAG,
                    "vm onThemeLoadedReady trace=$openTrace.id ms=${SystemClock.uptimeMillis() - beforeThemeLoadedAt} dt=${SystemClock.uptimeMillis() - startedAt}"
            )
        }
        val previousPage = currentPage
        ThemeToolbarTitlePolicy.mergeTitleFromSession(page, previousPage, loadedPages.values)
        ThemeToolbarTitlePolicy.mergeTitleFromNavigation(page, navigationTopicTitle)
        if (tryMergeTitleFromLoadedFirstPage(page)) {
            emitTopicToolbarTitleUpdate(page)
        } else if (ThemeToolbarTitlePolicy.needsTitleFromFirstPage(page)) {
            ensureTopicTitleFromFirstPageIfNeeded(page)
        }
        rememberSessionTopicTitle(page.title)
        // Fix C: second guard right before the commit. Even if the earlier-bail
        // path was bypassed (e.g. when [activeLoadedTopicId] is set by a different
        // load that just completed), refuse to overwrite the current page with a
        // stale-topic response.
        if (openTrace.callIndex > 0L &&
                activeLoadedTopicId != null &&
                page.id != activeLoadedTopicId) {
            FpdaDebugLog.logTheme(
                    FpdaDebugLog.ThemeArea.LOAD,
                    "dropMismatchedCommit",
                    mapOf(
                            "traceId" to openTrace.id,
                            "expectedTopicId" to activeLoadedTopicId,
                            "loadedTopicId" to page.id,
                    ),
                    warn = true
            )
            return
        }
        currentPage = page
        activeLoadedTopicId = page.id.takeIf { it > 0 }
        visibleCurrentPage = page.pagination.current
        val canPreserveHybridPages = ThemeRefreshScrollRestorePolicy.shouldPreserveLoadedPagesOnRefresh(
                action = _loadAction,
                crossTopicLoad = false,
                freshSameTopicOpen = false,
                requestedTopicId = page.id.takeIf { it > 0 },
                currentTopicId = previousPage?.id
        ) &&
                currentTopicScrollMode == AppPreferences.Main.TopicScrollMode.HYBRID &&
                loadedPages.isNotEmpty()
        if (canPreserveHybridPages) {
            loadedPages[page.pagination.current] = page
            val ordered = loadedPages.toSortedMap()
            loadedPages.clear()
            loadedPages.putAll(ordered)
            themeUseCase.mapHybridPages(page, loadedPages.values, "refreshPreservePages")
        } else {
            loadedPages.clear()
            loadedPages[page.pagination.current] = page
        }
        if (BuildConfig.DEBUG) {
            Log.i(
                    THEME_RENDER_TAG,
                    "loadedPagesReady trace=$openTrace.id action=$_loadAction preserveHybrid=$canPreserveHybridPages loaded=${loadedPages.keys.joinToString()} html=${page.html?.length ?: 0} containers=${countHtmlOccurrences(page.html, "theme_page_container")} postsInHtml=${countHtmlOccurrences(page.html, "post_container")}"
            )
        }
        if (shouldRunPendingSmartEnd) {
            if (BuildConfig.DEBUG) {
                Log.i(SMART_END_TAG, "goToEnd resume after load topic=${page.id}")
            }
            if (applySmartEndNavigation(page, resumedAfterDefer = true)) {
                return
            }
        }
        applyPendingRefreshRestoreRequest(page, previousPage)
        applyBackHistoryRestoreSnapshot(page)
        clearScrollRestoreForUnreadOpen(page)
        if (_loadAction == ThemeLoadAction.End) {
            clearAllScrollRestoreFields(page)
            val endPage = endScrollTargetPage(page, page.pagination.all.coerceAtLeast(1), transition = null)
            ThemeSmartEndNavigation.applyLoadedEndScrollTarget(endPage)
            page.anchorPostId = endPage.anchorPostId
            page.wasNearBottom = endPage.wasNearBottom
            page.anchors.clear()
            page.hasUnreadTarget = false
            beginScrollCommand(ThemeSmartEndNavigation.endScrollCommand(endPage))
        }
        val scrollRestoreAllowed = savedScrollRestoreAllowedForCurrentOpen()
        val savedRestoreDecision = when {
            _loadAction != ThemeLoadAction.Normal -> "allowed_non_normal_action"
            !scrollRestoreAllowed -> TopicOpenScrollRestorePolicy.blockedReason(
                    openIntentRaw = lastOpenIntent,
                    setting = currentTopicOpenTarget,
                    loadAction = _loadAction,
                    suppressScrollRestoreForOpen = suppressScrollRestoreForOpen || pendingUnreadOpenSuppressScroll
            ) ?: "blocked_policy"
            else -> "allowed"
        }
        if (!scrollRestoreAllowed && _loadAction == ThemeLoadAction.Normal) {
            clearAllScrollRestoreFields(page)
        }
        lastOpenResolution?.let { resolution ->
            TopicOpenTrace.log(
                    TopicOpenContext(
                            rawUrl = themeUrl,
                            setting = currentTopicOpenTarget,
                            sourceScreen = lastOpenSourceScreen,
                            sourceUrl = themeUrl,
                            openIntentRaw = lastOpenIntent,
                            cachedLastPage = previousPage?.pagination?.current,
                            cachedScrollPosition = previousPage?.scrollY
                    ),
                    resolution,
                    TopicOpenTraceExtras(
                            event = "page_loaded",
                            traceId = openTrace.id,
                            requestId = openTrace.id,
                            openIntent = lastOpenIntent,
                            isFreshOpen = isFreshTopicOpen(),
                            isBackRestore = isRestoreTopicOpen(),
                            savedRestoreDecision = savedRestoreDecision,
                            savedScrollRestoreAllowed = scrollRestoreAllowed,
                            savedScrollRestoreBlockedReason = savedRestoreDecision.takeIf { it.startsWith("blocked") },
                            scrollRestoreAllowed = scrollRestoreAllowed,
                            scrollRestoreApplied = page.refreshRestoreId != null,
                            scrollRestoreScheduled = page.refreshRestoreId != null,
                            serverRedirectUrl = page.url,
                            finalLoadedPage = page.pagination.current,
                            finalScrolledPostId = page.anchorPostId ?: page.anchor,
                            finalScrollY = page.scrollY,
                            lastViewedPostId = forpdateam.ru.forpda.model.data.remote.api.theme.ThemeApi
                                    .extractHashEntryPostIdFromTopicUrl(page.url.orEmpty())
                                    ?.toLongOrNull()
                                    ?: page.anchorPostId?.toLongOrNull(),
                            lastReadSource = when {
                                !page.anchorPostId.isNullOrBlank() -> "page_anchor"
                                !forpdateam.ru.forpda.model.data.remote.api.theme.ThemeApi
                                        .extractHashEntryPostIdFromTopicUrl(page.url.orEmpty())
                                        .isNullOrBlank() -> "redirect_url"
                                else -> "none"
                            },
                            savedPage = previousPage?.pagination?.current,
                            savedPostId = previousPage?.anchorPostId,
                            savedScrollY = previousPage?.scrollY,
                            unreadPage = page.pagination.current.takeIf { page.hasUnreadTarget },
                            suppressScrollRestore = suppressScrollRestoreForOpen || pendingUnreadOpenSuppressScroll,
                            hasUnreadTarget = page.hasUnreadTarget,
                            openSessionKind = activeOpenSessionKind?.name,
                            loadAction = _loadAction::class.simpleName,
                            refreshRestoreId = page.refreshRestoreId
                    )
            )
        }
        ThemePostReadStateDiagnostics.viewModelLoadComplete(
                topicId = page.id,
                traceId = openTrace.id,
                anchorPostId = page.anchorPostId ?: page.anchor?.removePrefix("entry"),
                hasUnreadTarget = page.hasUnreadTarget,
                blockScrollRestoreForUnread = shouldSuppressScrollRestoreOnRender(),
                loadAction = _loadAction::class.simpleName,
                pageCurrent = page.pagination.current,
                pageTotal = page.pagination.all
        )
        if (_loadAction == ThemeLoadAction.Normal &&
                (themeUrl.contains("view=getnewpost", ignoreCase = true) ||
                        openedViaFindPostLink ||
                        unreadFindPostUpgradeTraceId == openTrace.id)
        ) {
            val actualPostId = page.anchorPostId ?: page.anchor?.removePrefix("entry")
            val redirectPostId = ThemeApi.extractHashEntryPostIdFromTopicUrl(page.url.orEmpty())
            forpdateam.ru.forpda.diagnostic.TopicUnreadAnchorDiagnostics.openTarget(
                    topicId = page.id,
                    openTarget = "FIRST_UNREAD",
                    expectedPostId = actualPostId,
                    actualPostId = actualPostId,
                    hasUnreadTarget = page.hasUnreadTarget,
                    anchorSource = lastOpenResolution?.reason,
                    traceId = openTrace.id,
            )
            if (page.hasUnreadTarget &&
                    redirectPostId != null &&
                    actualPostId != null &&
                    redirectPostId != actualPostId
            ) {
                forpdateam.ru.forpda.diagnostic.TopicUnreadAnchorDiagnostics.openTarget(
                        topicId = page.id,
                        openTarget = "FIRST_UNREAD",
                        expectedPostId = actualPostId,
                        actualPostId = redirectPostId,
                        hasUnreadTarget = page.hasUnreadTarget,
                        anchorSource = "redirect_mismatch",
                        traceId = openTrace.id,
                )
            }
        }
        if (_loadAction == ThemeLoadAction.Normal) {
            initialOpenSettledAt = SystemClock.uptimeMillis()
        }
        val beforeEmitAt = SystemClock.uptimeMillis()
        val smartPatch = smartRefreshFallback("disabled", previousPage, page)
        if (BuildConfig.DEBUG) {
            Log.i(
                    REFRESH_SCROLL_TAG,
                    "vm emitUpdateViewStart trace=$openTrace.id dt=${beforeEmitAt - startedAt} html=${page.html?.length ?: 0} smartPatch=${smartPatch != null} preserveHybrid=$canPreserveHybridPages loaded=${loadedPages.keys.joinToString()}"
            )
            Log.i(
                    THEME_RENDER_TAG,
                    "emitUpdateView source=${if (smartPatch != null) "smartPatch" else "loadData"} trace=$openTrace.id action=$_loadAction html=${page.html?.length ?: 0} containers=${countHtmlOccurrences(page.html, "theme_page_container")} postsInHtml=${countHtmlOccurrences(page.html, "post_container")} loaded=${loadedPages.keys.joinToString()} smartPatch=${smartPatch != null}"
            )
        }
        postOpenEnrichmentController.markPrimaryOpenComplete(traceId)
        if (smartPatch != null) {
            _uiEvents.tryEmit(ThemeUiEvent.ApplySmartPostsPatch(page, smartPatch))
        } else {
            _onLoadData.tryEmit(page)
        }
        // Snackbar ShowAllReadHint removed per user request: this toast on every AMBIGUOUS_ALL_READ
        // open was too noisy. Anchor realignment in resolveReadResumeBottomRedirect already
        // scrolls the WebView to the last-read post, which is the meaningful signal.
        postOpenEnrichmentController.startPostOpenEnrichment(
                page = page,
                traceId = traceId,
                sessionKind = activeOpenSessionKind,
                loadAction = _loadAction,
                scrollMode = currentTopicScrollMode,
        )
        if (BuildConfig.DEBUG) {
            Log.i(
                    REFRESH_SCROLL_TAG,
                    "vm postOpenEnrichStarted trace=$traceId primaryComplete=${postOpenEnrichmentController.isPrimaryOpenComplete(traceId)} enrichStarted=${postOpenEnrichmentController.isPostOpenEnrichStarted(traceId)} session=${activeOpenSessionKind?.name}"
            )
        }
        if (BuildConfig.DEBUG) {
            Log.i(
                    REFRESH_SCROLL_TAG,
                    "vm emitUpdateViewEnd trace=$openTrace.id emitMs=${SystemClock.uptimeMillis() - beforeEmitAt} dt=${SystemClock.uptimeMillis() - startedAt}"
            )
        }
        if (_loadAction == ThemeLoadAction.Normal || _loadAction == ThemeLoadAction.End) {
            // Multi-back anchor loss fix (log 239158): a page opened via an explicit findpost / `p=`
            // link is pushed with the CLICKED post as its anchor. Record it as the authoritative
            // history anchor so a later in-tab link tapped from a different (scrolled-to) post cannot
            // overwrite the entry's anchor with that trailing visible post — BACK must return to the
            // exact source post the page was opened at, not a neighbor (push #entry132558585, not 132558226).
            //
            // Regression fix (log 25_06-09-41-46): scope this to a genuine IN-TAB findpost link tap.
            // A FRESH topic open (favorites/topics/unread/already-read) also resolves through a
            // getnewpost/findpost redirect URL, so openedViaFindPostLink is incidentally true there;
            // pinning that open anchor as authoritative froze the entry on the server bookmark and
            // blocked the user's real scroll position from being saved, so tab re-entry restored a
            // stale post. A fresh open must land on last-read/unread via the read/return position.
            if (ThemeAuthoritativeAnchorPolicy.shouldRecordAuthoritativeAnchor(
                            openedViaFindPostLink = openedViaFindPostLink,
                            isFreshTopicOpen = isFreshTopicOpen(),
                            isExplicitPostTarget =
                                    lastOpenResolution?.targetType == TopicOpenTargetType.EXPLICIT_POST,
                    )
            ) {
                val explicitAnchor = (page.anchorPostId ?: page.anchor?.removePrefix("entry"))
                        ?.takeIf { it.isNotBlank() }
                if (explicitAnchor != null) {
                    page.authoritativeAnchorPostId = explicitAnchor
                }
            }
            historyController.saveToHistory(page)
        }
        if (_loadAction == ThemeLoadAction.Refresh || _loadAction == ThemeLoadAction.Back) {
            historyController.updateHistoryLast(page)
        }
    }

    private fun initialInlineHatOpenForLoad(url: String, requestedTopicId: Int?): Boolean =
            TopicInlineHatOpenPolicy.shouldOpenForLoad(
                    url = url,
                    requestedTopicId = requestedTopicId,
                    currentPage = currentPage,
                    topicHeaderInitialState = currentTopicHeaderInitialState,
                    topicOpenTarget = currentTopicOpenTarget,
                    sourceScreen = lastOpenSourceScreen,
                    preserveInSessionInlineHatState = TopicInlineHatOpenPolicy.shouldTreatAsInSessionForInlineHat(
                            topicOpenTarget = currentTopicOpenTarget,
                            sourceScreen = lastOpenSourceScreen,
                            requestedTopicId = requestedTopicId,
                            currentPage = currentPage,
                    ) || !isFreshTopicOpen(),
            )

    fun addTopicToFavorite(topicId: Int, subType: String) {
        scope.launch {
            when (val result = interactionUseCase.addTopicToFavorite(topicId, subType)) {
                is ThemeInteractionUseCase.FavoriteResult.Add -> {
                    if (result.success) currentPage?.isInFavorite = true
                    _onAddToFavorite.tryEmit(result.success)
                }
                is ThemeInteractionUseCase.FavoriteResult.Delete -> {
                    _onAddToFavorite.tryEmit(false)
                }
                is ThemeInteractionUseCase.FavoriteResult.Error -> { /* handled in UseCase */ }
            }
        }
    }

    fun deleteTopicFromFavorite(favId: Int) {
        scope.launch {
            when (val result = interactionUseCase.deleteTopicFromFavorite(favId)) {
                is ThemeInteractionUseCase.FavoriteResult.Delete -> {
                    if (result.success) currentPage?.isInFavorite = false
                    _onDeleteFromFavorite.tryEmit(result.success)
                }
                is ThemeInteractionUseCase.FavoriteResult.Add -> {
                    _onDeleteFromFavorite.tryEmit(false)
                }
                is ThemeInteractionUseCase.FavoriteResult.Error -> { /* handled in UseCase */ }
            }
        }
    }

    fun openEditPostForm(
        message: String,
        attachments: MutableList<AttachmentItem>,
        selectionRange: IntArray? = null
    ) {
        postEditCoordinator.openEditPostForm(message, attachments, selectionRange)
    }

    fun openEditPostForm(postId: Int) {
        postEditCoordinator.openEditPostForm(postId)
    }

    /**
     * [domBodyHtml] — только если явно передан (например из WebView); HTML из модели темы не подставляем —
     * это разметка поста без BBCode, из‑за неё в редакторе «пустой» текст без [b]/[code] до ответа сервера.
     */
    fun openEditPostForm(postId: Int, domBodyHtml: String?) {
        postEditCoordinator.openEditPostForm(postId, domBodyHtml)
    }

    /**
     * После отправки/редактирования поста: якорь для прокрутки + NORMAL _loadAction (иначе theme.js при BACK
     * скроллит по scrollY и игнорирует entry). HTML пересобираем после добавления якоря.
     */
    private fun applyPostedThemePage(
            themePage: ThemePage,
            clearMessagePanel: Boolean,
            scrollToPostId: Int? = null
    ) {
        pendingEditCancelScrollPostId = null
        loadCallCounter += 1
        openTrace = OpenTrace(
            id = UUID.randomUUID().toString().replace("-", "").take(8),
            topicId = themePage.id.takeIf { it > 0 },
            callIndex = loadCallCounter,
        )
        val resolvedPostId = scrollToPostId?.takeIf { it > 0 }
                ?: themePage.anchorPostId?.toIntOrNull()
                ?: themePage.anchor?.removePrefix("entry")?.toIntOrNull()
        val isEditPost = !clearMessagePanel
        if (resolvedPostId != null && resolvedPostId > 0) {
            themePage.anchors.clear()
            themePage.addAnchor("entry$resolvedPostId")
            themePage.anchorPostId = resolvedPostId.toString()
        } else {
            ThemeApi.ensureScrollAnchorForPostedPage(themePage, null, openTrace.id)
            themePage.anchorPostId = themePage.anchor
                    ?.removePrefix("entry")
                    ?.takeIf { it.isNotEmpty() }
        }
        val postedScrollAnchor = ThemePostedScrollPendingPolicy.resolvePostedScrollAnchor(
                isEditPost = isEditPost,
                explicitPostId = resolvedPostId,
                smartEndPostId = ThemeSmartEndNavigation.resolveEndScrollAnchorPostId(themePage),
                pageAnchorPostId = themePage.anchorPostId?.takeIf { it.isNotBlank() },
        )
        if (!postedScrollAnchor.isNullOrBlank()) {
            themePage.anchors.clear()
            themePage.addAnchor("entry$postedScrollAnchor")
            themePage.anchorPostId = postedScrollAnchor
        }
        pendingPostedPageScrollAnchor = postedScrollAnchor
        pendingPostedPageScrollExact = isEditPost && !postedScrollAnchor.isNullOrBlank()
        themePage.scrollY = 0
        themePage.scrollRatio = null
        themePage.wasNearBottom = false
        themePage.refreshRestoreId = null
        themePage.refreshRestoreMode = null
        themePage.refreshRestoreSource = null
        _loadAction = ThemeLoadAction.Normal
        if (themePage.id > 0) {
            themeUseCase.invalidateTopicPageCache(themePage.id)
        }
        scope.launch {
            themePage.url?.takeIf { it.isNotBlank() }?.let { themeUrl = it }
            restorePostedPageAfterChildRemoval = true
            if (clearMessagePanel) {
                _onMessageSent.tryEmit(Unit)
            }
            onLoadData(themePage)
        }
    }

    private fun applyInlinePostedThemePage(themePage: ThemePage, scrollToPostId: Int? = null) {
        applyPostedThemePage(themePage, clearMessagePanel = true, scrollToPostId)
    }

    fun sendMessage(message: String, attachments: MutableList<AttachmentItem>) {
        postEditCoordinator.sendMessage(message, attachments)
    }

    fun uploadFiles(files: List<RequestFile>, pending: List<AttachmentItem>) {
        postEditCoordinator.uploadFiles(files, pending)
    }

    fun deleteFiles(items: List<AttachmentItem>) {
        postEditCoordinator.deleteFiles(items)
    }

    fun shouldReloadUnreadOnTabFocus(): Boolean =
            currentTopicOpenTarget == AppPreferences.Main.TopicOpenTarget.LAST_UNREAD

    fun shouldPreserveCachedScrollOnTabShow(): Boolean =
            !shouldReloadUnreadOnTabFocus() || openedViaFindPostLink

    fun loadUrl(url: String, sourceScreen: String = "unknown", listHints: TopicOpenListHints? = null) {
        val trimmedUrl = url.trim()
        val incomingTopicId = ThemeApi.extractTopicIdFromUrl(trimmedUrl)
        // Use only [currentPage?.id] for the reset decision. The previous
        // `activeLoadedTopicId ?: currentPage?.id` form short-circuited to a stale
        // "no reset" when [activeLoadedTopicId] was null (e.g. before the first
        // onLoadData commit), which allowed a cross-topic loadUrl to skip the
        // reset block and leave stale UI state.
        val previouslyLoadedId = currentPage?.id
        val preserveHistoryOnCrossTopic = ThemeViewModel.shouldPreserveHistoryOnCrossTopicOpen(sourceScreen)
        if (needsTopicSwitchReset(incomingTopicId, previouslyLoadedId)) {
            if (preserveHistoryOnCrossTopic) {
                _uiEvents.tryEmit(ThemeUiEvent.UpdateHistoryLastHtml)
            }
            resetTransientStateForNewTopic(
                    incomingTopicId!!,
                    clearToolbar = true,
                    clearHistory = !preserveHistoryOnCrossTopic
            )
            currentPage = null
            activeLoadedTopicId = null
            FpdaDebugLog.log(
                    FpdaDebugLog.TAG_TOPIC_SWITCH,
                    event = "loadUrl_cross_topic_reset",
                    fields = mapOf(
                            "incomingTopicId" to incomingTopicId,
                            "previousTopicId" to previouslyLoadedId,
                            "source" to sourceScreen,
                            "preserveHistory" to preserveHistoryOnCrossTopic
                    )
            )
        }
        val freshSameTopicOpen = isFreshSameTopicOpen(
                incomingTopicId,
                previouslyLoadedId,
                hasLoadedPage = currentPage != null,
                isFreshOpen = isFreshTopicOpen()
        )
        if (freshSameTopicOpen && incomingTopicId != null) {
            // Resolve LAST_UNREAD from request/list hints only; old page/scroll state belongs to the previous open.
            if (preserveHistoryOnCrossTopic) {
                _uiEvents.tryEmit(ThemeUiEvent.UpdateHistoryLastHtml)
            }
            resetTransientStateForNewTopic(
                    incomingTopicId,
                    clearHistory = !preserveHistoryOnCrossTopic
            )
            currentPage = null
            activeLoadedTopicId = null
        }
        if (incomingTopicId != null && incomingTopicId != currentPage?.id &&
                !shouldPreserveTopicOpenIntentOnCrossTopicOpen(lastOpenIntent, sourceScreen)) {
            lastOpenIntent = TopicOpenIntentClassifier.freshIntentForSource(sourceScreen)
        }
        if (listHints != null) {
            listOpenHints = listHints
        } else if (TopicOpenTargetResolver.hasFindPostMarker(trimmedUrl) ||
                explicitTargetPostIdFromUrl(url) != null ||
                shouldClearListOpenHints(hasLoadedPage = currentPage != null)
        ) {
            // Hints apply only to the first open in this tab (favorites/topics). Pagination,
            // navigator reuse, and same-tab re-open must not keep stale unread URLs.
            // Explicit findpost from search/QMS must not inherit list unread fallback.
            listOpenHints = null
        }
        lastOpenSourceScreen = sourceScreen
        val resolution = resolveTopicOpenUrl(trimmedUrl, sourceScreen)
        FpdaDebugLog.logTheme(
                FpdaDebugLog.ThemeArea.OPEN,
                "load_url",
                mapOf(
                        "traceId" to openTrace.id,
                        "sourceScreen" to sourceScreen,
                        "incomingTopicId" to incomingTopicId,
                        "resolvedUrl" to FpdaDebugLog.sanitizeUrl(resolution.url),
                        "reason" to resolution.reason
                )
        )
        // Single source of truth for anchor precedence on a URL-open. [TopicAnchorResolver] collapses
        // the historically competing reads (server target vs saved return position vs history) into
        // ONE deterministic decision driven by {openIntent × server target}. A fresh list open of a
        // still-unread topic ALWAYS resolves to the server first-unread (never a drifted saved
        // snapshot — logs 25_06-10-52 / 25_06-14-34); only a read-topic resume / genuine re-entry
        // restores a saved in-progress position. In-tab / cross-topic BACK are served by the history
        // subsystem and do not reach this path.
        val isExplicitLink = TopicOpenTargetResolver.hasFindPostMarker(trimmedUrl) ||
                explicitTargetPostIdFromUrl(url) != null ||
                resolution.targetType == TopicOpenTargetType.EXPLICIT_POST ||
                resolution.targetType == TopicOpenTargetType.EXPLICIT_PAGE
        val anchorDecision = TopicAnchorResolver.resolve(
                TopicAnchorResolver.Input(
                        topicId = incomingTopicId,
                        openIntent = TopicAnchorResolver.classifyOpenIntent(
                                isExplicitLink = isExplicitLink,
                                isFreshOpenIntent = isFreshTopicOpen(),
                                isRestoreIntent = isRestoreTopicOpen(),
                        ),
                        readState = TopicAnchorResolver.ReadState.UNKNOWN,
                        serverTarget = TopicAnchorResolver.ServerTarget(
                                url = resolution.url,
                                type = resolution.targetType,
                                reason = resolution.reason,
                        ),
                        savedReturnPosition = incomingTopicId?.let { returnPositionStore.peek(it) },
                )
        )
        if (anchorDecision is TopicAnchorResolver.Decision.RestoreSavedPosition) {
            Log.i(
                    THEME_HISTORY_TAG,
                    "loadUrl tab_reentry_restore target=${anchorDecision.url} source=$sourceScreen topic=$incomingTopicId savedSt=${anchorDecision.position.pageSt} savedPost=${anchorDecision.position.postId} resolverReason=${resolution.reason} decision=${anchorDecision.reason}"
            )
            themeUrl = anchorDecision.url
            suppressScrollRestoreForOpen = false
            pendingUnreadOpenSuppressScroll = false
            loadData(anchorDecision.url, ThemeLoadAction.Back)
            return
        }
        Log.i(
                THEME_HISTORY_TAG,
                "loadUrl normal target=${resolution.url} source=$sourceScreen sourceTopic=${currentPage?.id} sourceSt=${getRefreshCapturePageInstance()?.st} sourceVisible=$visibleCurrentPage pendingSourcePost=${pendingHistorySourceAnchor?.postId} reason=${resolution.reason}"
        )
        loadData(resolution.url, ThemeLoadAction.Normal)
    }

    private var skipNextRefreshScrollCapture = false

    fun reload(skipScrollCapture: Boolean = false) {
        skipNextRefreshScrollCapture = skipScrollCapture
        val request = pendingRefreshRequest
        var url = request?.targetUrl?.takeIf { it.isNotBlank() } ?: themeUrl
        if (request != null && url.contains("view=getnewpost", ignoreCase = true)) {
            val topicId = request.topicId.takeIf { it > 0 }
                    ?: ThemeApi.extractTopicIdFromUrl(url)
            val st = request.pageSt.takeIf { it >= 0 } ?: currentPage?.st ?: 0
            if (topicId != null && topicId > 0) {
                url = buildCleanThemeUrl(topicId, st)
            }
        }
        if (BuildConfig.DEBUG) {
            Log.i(
                    REFRESH_SCROLL_TAG,
                    "vm reload target id=${request?.id} source=${request?.source} url=$url themeUrl=$themeUrl targetPage=${request?.targetPageNumber} targetSt=${request?.pageSt}"
            )
        }
        loadData(url, ThemeLoadAction.Refresh)
    }

    fun beginRefreshRequest(source: String, restoreMode: String): String {
        val mode = RefreshRestoreMode.from(restoreMode)
        val targetPage = selectRefreshTargetPage(mode)
        val request = RefreshRequest(
                id = UUID.randomUUID().toString().replace("-", "").take(8),
                source = source,
                restoreMode = mode,
                topicId = targetPage?.id ?: currentPage?.id ?: -1,
                pageSt = targetPage?.st ?: currentPage?.st ?: -1,
                targetPageNumber = targetPage?.pagination?.current,
                targetUrl = targetPage
                        ?.takeIf { it.id > 0 }
                        ?.let { buildCleanThemeUrl(it.id, it.st) }
                        ?: themeUrl
        )
        pendingRefreshRequest = request
        // Refresh is explicit «stay here» — must not inherit OPEN_UNREAD scroll suppression.
        suppressScrollRestoreForOpen = false
        pendingUnreadOpenSuppressScroll = false
        targetPage?.let { page ->
            page.refreshRestoreId = request.id
            page.refreshRestoreMode = mode.name
            page.refreshRestoreSource = request.source
            if (mode == RefreshRestoreMode.BOTTOM) {
                page.scrollY = Int.MAX_VALUE
                page.anchorPostId = null
                page.anchorOffsetTop = null
                page.scrollRatio = 1.0
                page.wasNearBottom = true
            } else {
                seedRefreshSnapshotFromPage(page, request)
            }
        }
        if (BuildConfig.DEBUG) {
            Log.i(
                    REFRESH_SCROLL_TAG,
                    "vm refreshRequest begin id=${request.id} source=${request.source} mode=${request.restoreMode} topic=${request.topicId} st=${request.pageSt} page=${request.targetPageNumber} url=${request.targetUrl} visible=$visibleCurrentPage loaded=${loadedPages.keys.joinToString()} trace=$openTrace.id"
            )
        }
        return request.id
    }

    fun beginBottomRefreshRestore(): String = beginRefreshRequest("bottomSwipeRefresh", RefreshRestoreMode.BOTTOM.name)

    private fun selectRefreshTargetPage(mode: RefreshRestoreMode): ThemePage? {
        val page = currentPage
        if (page == null || loadedPages.isEmpty()) return page
        return if (mode == RefreshRestoreMode.BOTTOM) {
            loadedPages[loadedPages.keys.maxOrNull()] ?: page
        } else {
            visibleCurrentPage?.let { loadedPages[it] } ?: page
        }
    }

    /**
     * Восстановление состояния при возврате из дочерней вкладки (по ссылке на другой топик/раздел).
     * В отличие от [reload], сохраняет страницу (st) и позицию прокрутки (scrollY).
     *
     * Логика:
     * 1. Если в истории >1 запись → [backPage] (кэш html или загрузка с правильным st)
     * 2. Если currentPage загружен и html есть → просто восстановить scrollY без перезагрузки
     * 3. Если currentPage загружен, но html пустой → загрузить с правильным st
     * 4. Fallback → [reload]
     */
    fun restoreFromChildTab() {
        skipNextUnreadJumpAfterTabSwitch = true
        if (!restorePostedPageAfterChildRemoval) {
            applyEditCancelScrollRestoreIfNeeded()
        }
        if (restorePostedPageAfterChildRemoval) {
            restorePostedPageAfterChildRemoval = false
            currentPage?.let { page ->
                _loadAction = ThemeLoadAction.Normal
                page.url?.takeIf { it.isNotBlank() }?.let { themeUrl = it }
                if (BuildConfig.DEBUG) {
                    Timber.d(
                            "restoreFromChildTab: emitting posted page anchor=${page.anchor} url=${page.url}"
                    )
                }
                _updateView.tryEmit(page)
                return
            }
        }
        if (BuildConfig.DEBUG) Timber.d("restoreFromChildTab: historySize=${historyController.size} currentPage.id=${currentPage?.id} currentPage.st=${currentPage?.st}")
        val anchorStillValid = currentPage?.let { page ->
            pendingHistorySourceAnchor?.takeIf { sourceAnchorAppliesTo(page, it) }
        } != null
        if (historyController.canGoBack() && !anchorStillValid) {
            backPage()
            return
        }
        val page = currentPage
        if (page != null && !page.html.isNullOrBlank()) {
            // HTML из истории может быть снят с WebView до обновления шаблона/CSS.
            // Пересобираем из модели постов, чтобы после апдейта APK не показывать старую разметку.
            if (BuildConfig.DEBUG) Timber.d("restoreFromChildTab: remapping cached page")
            _loadAction = ThemeLoadAction.Back
            scope.launch {
                themeUseCase.mapEntity(page, "restoreCached")
                _updateView.tryEmit(page)
            }
            return
        }
        if (page != null) {
            // HTML пустой, но страница была загружена — загрузить с правильным st.
            // Строим чистый URL по topicId + st (без findpost/getnewpost/p=PID),
            // иначе сервер редиректит на «последнюю» страницу.
            val urlWithSt = buildBackRestoreUrl(page)
            if (BuildConfig.DEBUG) Timber.d("restoreFromChildTab: loading urlWithSt=$urlWithSt")
            themeUrl = urlWithSt
            loadData(urlWithSt, ThemeLoadAction.Back)
            return
        }
        // Fallback — полная перезагрузка
        if (BuildConfig.DEBUG) Timber.d("restoreFromChildTab: fallback to reload")
        reload()
    }

    /**
     * @param fromTabSwitch true — возврат на вкладку темы; для входа по findpost не перезагружаем на непрочитанное.
     */
    fun loadNewPosts(fromTabSwitch: Boolean = false) {
        if (currentTopicOpenTarget != AppPreferences.Main.TopicOpenTarget.LAST_UNREAD) return
        if (fromTabSwitch && openedViaFindPostLink) return
        if (fromTabSwitch && skipNextUnreadJumpAfterTabSwitch) {
            skipNextUnreadJumpAfterTabSwitch = false
            return
        }
        if (fromTabSwitch && initialOpenSettledAt == 0L) {
            if (BuildConfig.DEBUG) {
                Log.i(
                        REFRESH_SCROLL_TAG,
                        "skip loadNewPosts before initial settle trace=$openTrace.id"
                )
            }
            return
        }
        if (fromTabSwitch && loadThemeJob?.isActive == true) {
            if (BuildConfig.DEBUG) {
                Log.i(
                        REFRESH_SCROLL_TAG,
                        "skip loadNewPosts while theme load in flight trace=$openTrace.id"
                )
            }
            return
        }
        if (fromTabSwitch && SystemClock.uptimeMillis() - initialOpenSettledAt < 2500L) {
            if (BuildConfig.DEBUG) {
                Log.i(
                        REFRESH_SCROLL_TAG,
                        "skip loadNewPosts after initialOpen trace=$openTrace.id dt=${SystemClock.uptimeMillis() - initialOpenSettledAt}"
                )
            }
            return
        }
        currentPage?.let { page ->
            page.scrollY = 0
            page.anchorPostId = null
            page.anchorOffsetTop = null
            page.scrollRatio = null
            page.wasNearBottom = false
            page.refreshRestoreId = null
            page.refreshRestoreMode = null
            page.refreshRestoreSource = null
            page.hasUnreadTarget = false
            page.anchors.clear()
            // Параметр против повторного использования одного и того же ответа при быстром переключении вкладок.
            val ts = System.currentTimeMillis()
            val unreadUrl = "https://4pda.to/forum/index.php?showtopic=${page.id}&view=getnewpost&_=$ts"
            val resolution = resolveTopicOpenUrl(unreadUrl, "tab_focus")
            loadData(resolution.url, ThemeLoadAction.Normal)
        }
    }

    fun loadPage(page: Int) {
        currentPage?.let {
            when (val transition = paginationController.selectPage(page, it.toPaginationState())) {
                is ThemePageTransition.ShowLoadedPage -> {
                    setVisiblePage(transition.pageNumber)
                    _uiEvents.tryEmit(ThemeUiEvent.ScrollToPage(transition.pageNumber))
                    return
                }
                is ThemePageTransition.LoadSt -> {
                    loadThemePageSt(it.id, transition.st)
                    return
                }
                null -> {
                    // Preserve the previous permissive fallback for callers that pass a non-standard st.
                }
            }
            loadThemePageSt(it.id, page)
        }
    }

    private fun loadThemePageSt(topicId: Int, st: Int) {
        var url = "https://4pda.to/forum/index.php?showtopic=$topicId"
        if (st != 0) {
            url = "$url&st=$st"
        }
        loadUrl(url, "pagination")
    }

    fun loadLastPageAndScrollToBottom() {
        val page = currentPage
        if (page == null) {
            ThemeApi.extractTopicIdFromUrl(themeUrl)?.takeIf { it > 0 }?.let { topicId ->
                pendingSmartEndTopicId = topicId
                if (BuildConfig.DEBUG) {
                    Log.i(SMART_END_TAG, "goToEnd deferred: no currentPage topic=$topicId")
                    FpdaDebugLog.log(
                            FpdaDebugLog.TAG_SMART_BUTTON,
                            "go_to_end_deferred",
                            mapOf("reason" to "no_current_page", "topicId" to topicId)
                    )
                }
                return
            }
            if (BuildConfig.DEBUG) {
                Log.w(SMART_END_TAG, "goToEnd ignored: no currentPage")
                FpdaDebugLog.warn(FpdaDebugLog.TAG_SMART_BUTTON, "go_to_end_ignored", mapOf("reason" to "no_current_page"))
            }
            reportSmartEndUnavailable("no_current_page")
            return
        }
        val requestedTopicId = ThemeApi.extractTopicIdFromUrl(themeUrl)
        if (requestedTopicId != null && requestedTopicId > 0 && requestedTopicId != page.id) {
            pendingSmartEndTopicId = requestedTopicId
            if (BuildConfig.DEBUG) {
                Log.i(
                        SMART_END_TAG,
                        "goToEnd deferred: page/topic mismatch current=${page.id} requested=$requestedTopicId loading=${loadThemeJob?.isActive == true}"
                )
                FpdaDebugLog.log(
                        FpdaDebugLog.TAG_SMART_BUTTON,
                        "go_to_end_deferred",
                        mapOf(
                                "reason" to "topic_mismatch",
                                "currentTopicId" to page.id,
                                "requestedTopicId" to requestedTopicId,
                                "loadInFlight" to (loadThemeJob?.isActive == true)
                        )
                )
            }
            return
        }
        if (loadThemeJob?.isActive == true) {
            pendingSmartEndTopicId = page.id
            if (BuildConfig.DEBUG) {
                Log.i(SMART_END_TAG, "goToEnd deferred: in-flight load topic=${page.id}")
                FpdaDebugLog.log(
                        FpdaDebugLog.TAG_SMART_BUTTON,
                        "go_to_end_deferred",
                        mapOf("reason" to "load_in_flight", "topicId" to page.id)
                )
            }
            return
        }
        applySmartEndNavigation(page, resumedAfterDefer = false)
    }

    /**
     * @return true when a network load was started and [onLoadData] must return early.
     */
    private fun applySmartEndNavigation(page: ThemePage, resumedAfterDefer: Boolean): Boolean {
        suppressScrollRestoreForOpen = false
        pendingUnreadOpenSuppressScroll = false
        pendingSmartEndTopicId = null
        clearAllScrollRestoreFields(page)
        page.anchors.clear()
        page.hasUnreadTarget = false
        val paginationState = page.toPaginationState()
        val transition = paginationController.lastPage(paginationState)
        logSmartEndDecision(page, paginationState, transition, resumedAfterDefer)
        val endScrollPage = endScrollTargetPage(page, paginationState.safeAll, transition)
        return when (transition) {
            is ThemePageTransition.ShowLoadedPage -> {
                setVisiblePage(transition.pageNumber)
                emitSmartEndScrollEvent(
                        ThemeSmartEndNavigation.endAnchorScrollEvent(endScrollPage),
                        page,
                        transition,
                        targetPage = transition.pageNumber
                )
                false
            }
            is ThemePageTransition.LoadSt -> {
                cancelPendingSmartEndScroll()
                loadData(
                        "https://4pda.to/forum/index.php?showtopic=${page.id}&view=getlastpost",
                        ThemeLoadAction.End
                )
                true
            }
            null -> {
                val event = ThemeSmartEndNavigation.resolveEndScrollEvent(
                        transition = transition,
                        page = endScrollPage,
                        safeAll = paginationState.safeAll,
                        scrollMode = currentTopicScrollMode
                )
                emitSmartEndScrollEvent(event, page, transition, targetPage = paginationState.safeAll)
                false
            }
        }
    }

    private fun endScrollTargetPage(
            page: ThemePage,
            targetPageNumber: Int,
            transition: ThemePageTransition?
    ): ThemePage {
        val targetPage = when (transition) {
            is ThemePageTransition.ShowLoadedPage -> transition.pageNumber
            else -> targetPageNumber
        }
        return ThemeSmartEndNavigation.pageForEndScrollAnchor(loadedPages, page, targetPage)
    }

    fun getEndScrollTargetPage(): ThemePage? {
        val page = currentPage ?: return null
        val targetPage = page.pagination.all.coerceAtLeast(1)
        return endScrollTargetPage(page, targetPage, transition = null)
    }

    private fun runPendingSmartEndFallbackIfNeeded(): Boolean {
        if (!pendingSmartEndFallback) return false
        pendingSmartEndFallback = false
        fallbackSmartEndToLastPageLoad()
        return loadThemeJob?.isActive == true
    }

    private fun fallbackSmartEndToLastPageLoad() {
        val page = currentPage
        if (page == null) {
            reportSmartEndUnavailable("no_current_page")
            return
        }
        if (ThemeSmartEndNavigation.shouldDeferFallbackWhileLoadInFlight(loadThemeJob?.isActive == true)) {
            pendingSmartEndFallback = true
            if (BuildConfig.DEBUG) {
                Log.i(SMART_END_TAG, "goToEnd fallback deferred: load in flight topic=${page.id}")
            }
            return
        }
        pendingSmartEndFallback = false
        val lastPageNum = page.pagination.all.coerceAtLeast(1)
        loadedPages.remove(lastPageNum)
        suppressScrollRestoreForOpen = false
        pendingUnreadOpenSuppressScroll = false
        clearAllScrollRestoreFields(page)
        page.anchors.clear()
        page.hasUnreadTarget = false
        if (BuildConfig.DEBUG) {
            Log.i(SMART_END_TAG, "goToEnd fallback LoadSt topic=${page.id} reason=${ThemeSmartEndNavigation.PAGE_NOT_IN_DOM}")
            FpdaDebugLog.log(
                    FpdaDebugLog.TAG_SMART_BUTTON,
                    "go_to_end_fallback_load",
                    mapOf(
                            "topicId" to page.id,
                            "reason" to ThemeSmartEndNavigation.PAGE_NOT_IN_DOM,
                            "targetPage" to lastPageNum
                    )
            )
        }
        cancelPendingSmartEndScroll()
        loadData(
                "https://4pda.to/forum/index.php?showtopic=${page.id}&view=getlastpost",
                ThemeLoadAction.End
        )
    }

    private fun cancelPendingSmartEndScroll() {
        clearPendingScrollCommand()
        _uiEvents.tryEmit(ThemeUiEvent.CancelPendingSmartScroll)
    }

    private fun logSmartEndDecision(
            page: ThemePage,
            paginationState: ThemePaginationState,
            transition: ThemePageTransition?,
            resumedAfterDefer: Boolean
    ) {
        if (!BuildConfig.DEBUG) return
        Log.i(
                SMART_END_TAG,
                "goToEnd topic=${page.id} active=${paginationState.activePage} loaded=${paginationState.loadedPages.joinToString()} all=${paginationState.safeAll} transition=${transition?.javaClass?.simpleName} resumed=$resumedAfterDefer"
        )
        FpdaDebugLog.log(
                FpdaDebugLog.TAG_SMART_BUTTON,
                "go_to_end",
                mapOf(
                        "topicId" to page.id,
                        "mode" to currentTopicScrollMode,
                        "action" to "GO_BOTTOM",
                        "currentPage" to paginationState.currentPage,
                        "activePage" to paginationState.activePage,
                        "visiblePage" to paginationState.visiblePage,
                        "loadedPages" to paginationState.loadedPages.joinToString(","),
                        "totalPages" to paginationState.safeAll,
                        "transition" to transition?.javaClass?.simpleName,
                        "resumedAfterDefer" to resumedAfterDefer,
                        "targetPage" to paginationState.safeAll
                )
        )
    }

    private fun emitSmartEndScrollEvent(
            event: ThemeUiEvent,
            page: ThemePage,
            transition: ThemePageTransition?,
            targetPage: Int
    ) {
        val emitted = _uiEvents.tryEmit(event)
        if (BuildConfig.DEBUG) {
            val eventName = when (event) {
                ThemeUiEvent.ScrollToBottom -> "scroll_bottom"
                is ThemeUiEvent.ScrollToPageAndBottom -> "scroll_page_bottom"
                is ThemeUiEvent.ScrollToEndAnchorOrBottom -> "scroll_end_anchor"
                else -> "scroll"
            }
            if (!emitted) {
                Log.w(SMART_END_TAG, "goToEnd $eventName dropped topic=${page.id}")
            }
            FpdaDebugLog.log(
                    FpdaDebugLog.TAG_SMART_BUTTON,
                    if (emitted) "${eventName}_emitted" else "${eventName}_event_dropped",
                    mapOf(
                            "topicId" to page.id,
                            "targetPage" to targetPage,
                            "transition" to transition?.javaClass?.simpleName,
                            "commandSent" to emitted
                    )
            )
        }
        if (!emitted) {
            reportSmartEndUnavailable("scroll_event_dropped")
        }
    }

    fun reportSmartEndScrollResult(
            commandId: String,
            success: Boolean,
            reason: String,
            scrollYBefore: Int,
            scrollYAfter: Int
    ) {
        if (BuildConfig.DEBUG) {
            FpdaDebugLog.log(
                    FpdaDebugLog.TAG_SMART_BUTTON,
                    if (success) "scroll_command_success" else "scroll_command_failed",
                    mapOf(
                            "commandId" to commandId,
                            "commandResult" to success,
                            "errorReason" to reason.takeIf { !success },
                            "scrollYBefore" to scrollYBefore,
                            "scrollYAfter" to scrollYAfter,
                            "topicId" to currentPage?.id
                    )
            )
        }
        if (!success) {
            reportSmartEndUnavailable(reason)
        }
    }

    private fun parseScrollYFromCommandReason(reason: String): Int {
        val match = Regex("""\|y=(-?\d+)""").find(reason) ?: return 0
        return match.groupValues[1].toIntOrNull() ?: 0
    }

    fun reportSmartEndUnavailable(reason: String) {
        _uiEvents.tryEmit(ThemeUiEvent.ShowSnackbar(context.getString(R.string.smart_nav_end_unavailable)))
        if (BuildConfig.DEBUG) {
            FpdaDebugLog.warn(
                    FpdaDebugLog.TAG_SMART_BUTTON,
                    "go_to_end_unavailable",
                    mapOf("errorReason" to reason, "topicId" to currentPage?.id)
            )
        }
    }

    fun closeToolbarOverlaysForNavigation() {
        userHatOpenOverride = false
        currentPage?.let { page ->
            page.isHatOpen = false
            page.isPollOpen = false
        }
    }

    fun requestInfinitePage(direction: String) {
        infiniteScrollController.requestInfinitePage(direction)
    }

    fun retryInfinitePage(direction: String) {
        infiniteScrollController.retryInfinitePage(direction)
    }

    private fun setVisiblePage(pageNumber: Int) {
        if (!shouldAcceptVisiblePageUpdate(pageNumber)) return
        val page = loadedPages[pageNumber] ?: currentPage ?: return
        val change = paginationController.visiblePageChanged(pageNumber, page.toPaginationState()) ?: return
        visibleCurrentPage = change.pageNumber
        _uiEvents.tryEmit(
                ThemeUiEvent.UpdateVisiblePage(
                        pageNumber = change.pageNumber,
                        allPages = change.allPages,
                        perPage = change.perPage,
                        isForum = change.isForum
                )
        )
    }

    private fun ThemePage.toPaginationState(): ThemePaginationState =
            ThemePaginationState(
                    currentPage = pagination.current,
                    allPages = pagination.all,
                    perPage = pagination.perPage,
                    isForum = pagination.isForum,
                    visiblePage = resolvedVisiblePageForPagination(),
                    loadedPages = loadedPageNumbersForTopic(id)
            )

    /** Only pages belonging to [topicId] — stale hybrid keys must not affect «В конец темы». */
    private fun loadedPageNumbersForTopic(topicId: Int): Set<Int> {
        if (topicId <= 0 || loadedPages.isEmpty()) return emptySet()
        return loadedPages.filterValues { it.id == topicId }.keys
    }

    /**
     * Hybrid mode keeps [visibleCurrentPage] across loads; after switching topics the old visible page
     * must not drive pagination (otherwise «В конец темы» thinks we are already on the last page).
     */
    private fun resolvedVisiblePageForPagination(): Int? {
        val visible = visibleCurrentPage ?: return null
        if (loadedPages.isEmpty()) return null
        return visible.takeIf { loadedPages.containsKey(it) }
    }

    private fun requestTopicToolbarClear() {
        _topicToolbarClearSignal.value++
    }

    private fun resetHatOverlayState() {
        userHatOpenOverride = null
        pendingHatToolbarClick = false
        pendingHatToolbarOpenAfterRender = false
        pendingHatOverlayRenderAfterScroll = false
        hatOverlayReinjectionTraceId = null
        currentPage?.isHatOpen = false
        _uiEvents.tryEmit(ThemeUiEvent.UpdateHatOpenState(false))
    }

    override fun onHatOverlayInjectionRequested() {
        handleHatToolbarClick(forceInjectOverlay = true)
    }

    private fun rememberSessionTopicTitle(title: String?) {
        title?.trim()?.takeIf { it.isNotEmpty() }?.let {
            sessionTopicTitle = it
            // Also cache per-topic so the label survives session bumps / same-topic reloads.
            (currentPage?.id ?: activeLoadedTopicId)?.takeIf { id -> id > 0 }?.let { id ->
                rememberLastKnownTopicTitle(id, it)
            }
        }
    }

    /** Records the last-known toolbar title for [topicId]; bounded LRU to avoid unbounded growth. */
    private fun rememberLastKnownTopicTitle(topicId: Int, title: String?) {
        if (topicId <= 0) return
        val clean = title?.trim()?.takeIf { it.isNotEmpty() } ?: return
        lastKnownTopicTitleByTopicId.remove(topicId)
        lastKnownTopicTitleByTopicId[topicId] = clean
        while (lastKnownTopicTitleByTopicId.size > MAX_REMEMBERED_TOPIC_TITLES) {
            val oldest = lastKnownTopicTitleByTopicId.keys.firstOrNull() ?: break
            lastKnownTopicTitleByTopicId.remove(oldest)
        }
    }

    /** Last-known title for [topicId] regardless of session — never returns another topic's title. */
    private fun lastKnownTopicTitle(topicId: Int?): String? {
        val id = topicId?.takeIf { it > 0 } ?: return null
        return lastKnownTopicTitleByTopicId[id]?.trim()?.takeIf { it.isNotEmpty() }
    }

    private fun tryMergeTitleFromLoadedFirstPage(page: ThemePage): Boolean {
        val firstPage = loadedPages[1]
                ?: loadedPages.values.firstOrNull { it.id == page.id && it.pagination.current == 1 }
                ?: return false
        ThemeToolbarTitlePolicy.mergeTitleFromFirstPage(page, firstPage)
        rememberSessionTopicTitle(page.title)
        return !page.title.isNullOrBlank()
    }

    private fun emitTopicToolbarTitleUpdate(page: ThemePage) {
        if (!shouldApplyToolbarPage(page)) return
        if (!page.title.isNullOrBlank()) {
            _uiEvents.tryEmit(ThemeUiEvent.UpdateTopicToolbar(page))
        }
    }

    private fun ensureTopicTitleFromFirstPageIfNeeded(page: ThemePage) {
        if (!ThemeToolbarTitlePolicy.needsTitleFromFirstPage(page)) return
        if (tryMergeTitleFromLoadedFirstPage(page)) {
            emitTopicToolbarTitleUpdate(page)
            return
        }
        // Recover from the durable per-topic cache before touching the network. This closes the
        // race where a previous async first-page fetch was discarded by a session bump (infiniteSession++
        // on every loadData) and never retried, leaving the toolbar empty for the same topic.
        lastKnownTopicTitle(page.id)?.let { cached ->
            page.title = cached
            rememberSessionTopicTitle(cached)
            emitTopicToolbarTitleUpdate(page)
            return
        }
        if (titleFromFirstPageJob?.isActive == true) return
        val topicId = page.id
        val hatOpen = userHatOpenOverride ?: false
        val pollOpen = page.isPollOpen
        titleFromFirstPageJob = scope.launch {
            when (val result = themeUseCase.loadTheme(buildCleanThemeUrl(topicId, 0), hatOpen, pollOpen)) {
                is ThemeUseCase.LoadResult.Success -> {
                    val firstPage = result.page
                    if (firstPage.id != topicId) return@launch
                    // Cache the resolved title even if the user has paginated/scrolled away in the
                    // meantime — a later same-topic page can recover it without another fetch.
                    firstPage.title?.trim()?.takeIf { it.isNotEmpty() }?.let {
                        rememberLastKnownTopicTitle(topicId, it)
                    }
                    // Gate the live toolbar update on topicId (not session): as long as the user is
                    // still on this topic, a late first-page fetch must still fill an empty toolbar.
                    val current = currentPage ?: return@launch
                    if (current.id != topicId) return@launch
                    ThemeToolbarTitlePolicy.mergeTitleFromFirstPage(current, firstPage)
                    rememberSessionTopicTitle(current.title)
                    emitTopicToolbarTitleUpdate(current)
                }
                is ThemeUseCase.LoadResult.Error -> Unit
            }
        }
    }

    private fun resetTransientStateForNewTopic(
            requestedTopicId: Int,
            clearToolbar: Boolean = false,
            clearHistory: Boolean = true,
            preserveSessionTitle: Boolean = false
    ) {
        resetHatOverlayState()
        // Log 1122662: a findpost-reload of the SAME topic (freshSameTopicOpen) re-enters here and
        // used to null sessionTopicTitle. The findpost response for a deep page (current>1) has no
        // page.title, so the session-title fallback was the only source — clearing it left the toolbar
        // empty. Keep the same-topic session title across the reload; cross-topic opens still clear it.
        if (!preserveSessionTitle) {
            sessionTopicTitle = null
        }
        visibleCurrentPage = null
        activeLoadedTopicId = null
        listOpenHints = null
        pendingParserListUnreadHint = false
        activeOpenSessionKind = null
        postOpenEnrichmentController.reset()
        pendingSmartEndTopicId = null
        pendingSmartEndFallback = false
        clearPendingScrollCommand()
        pendingRenderedReadTarget = null
        pendingRefreshRequest = null
        pendingHistorySourceAnchor = null
        pendingHistorySourceTopicId = null
        pendingHistorySourceSt = null
        lastLinkSourceAnchor = null
        skipNextUnreadJumpAfterTabSwitch = false
        initialOpenSettledAt = 0L
        // STEP 2: a new topic (or findpost-reload of the same topic) drops the sticky
        // explicit-anchor intent so the new open arms cleanly.
        resetExplicitAnchorScrollSettled()
        if (clearHistory) {
            historyController.clear()
        }
        if (clearToolbar) {
            requestTopicToolbarClear()
        }
        _uiEvents.tryEmit(ThemeUiEvent.ResetRenderLifecycle(requestedTopicId))
        if (BuildConfig.DEBUG) {
            Log.i(
                    THEME_HISTORY_TAG,
                    "resetTransientStateForNewTopic topic=$requestedTopicId previous=${currentPage?.id} visibleCleared=true historyCleared=$clearHistory"
            )
        }
    }

    fun backPage() {
        val prev = historyController.backPage() ?: return
        currentPage = prev
        visibleCurrentPage = prev.pagination.current
        skipNextUnreadJumpAfterTabSwitch = true
        // Перед loadUrl следующей темы loadData вызывает updateHistoryLastHtml("") — в истории html обнуляется.
        // Назад без сети рисовал бы пустой WebView; ручной refresh как раз делал loadData снова.
        if (prev.html.isNullOrBlank()) {
            // Чистый URL по topicId + st: иначе при findpost/getnewpost/p= сервер
            // редиректит на «последнюю» страницу, и память скрола ломается.
            val urlWithSt = buildBackRestoreUrl(prev)
            if (BuildConfig.DEBUG) {
                Timber.d("backPage: loading urlWithSt=$urlWithSt")
            }
            Log.i(
                    THEME_HISTORY_TAG,
                    "back load topic=${prev.id} st=${prev.st} page=${prev.pagination.current} url=$urlWithSt anchor=${prev.anchorPostId} y=${prev.scrollY} ratio=${prev.scrollRatio}"
            )
            themeUrl = urlWithSt
            loadData(urlWithSt, ThemeLoadAction.Back)
        } else {
            if (BuildConfig.DEBUG) {
                Timber.d("backPage: remapping cached page htmlLen=${prev.html?.length} url=${prev.url}")
            }
            Log.i(
                    THEME_HISTORY_TAG,
                    "back remap topic=${prev.id} st=${prev.st} page=${prev.pagination.current} url=${prev.url} anchor=${prev.anchorPostId} y=${prev.scrollY} ratio=${prev.scrollRatio}"
            )
            themeUrl = prev.url.orEmpty()
            _loadAction = ThemeLoadAction.Back
            scope.launch {
                themeUseCase.mapEntity(prev, "backCached")
                _updateView.tryEmit(prev)
            }
        }
    }

    /**
     * Чистый URL темы по topicId + st (0-based offset). Не содержит view=findpost,
     * view=getnewpost, &p=POST_ID и т.п. — иначе сервер редиректит на конкретный пост
     * (часто на «последнюю страницу»), что ломает память позиции при возврате назад.
     */
    private fun buildCleanThemeUrl(topicId: Int, st: Int): String {
        val safeSt = if (st < 0) 0 else st
        return if (safeSt > 0) {
            "https://4pda.to/forum/index.php?showtopic=$topicId&st=$safeSt"
        } else {
            "https://4pda.to/forum/index.php?showtopic=$topicId"
        }
    }

    private fun buildBackRestoreUrl(page: ThemePage): String {
        // B-02: prefer the page anchor, then the persisted native back snapshot (which
        // survives independently of the 15s JS source-anchor TTL), then any url-hash.
        // This keeps #entry<postId> in the back-restore URL even after a slow read of the
        // target topic expired the JS source-anchor.
        val nativeSnapshot = historyController.peekBackSnapshot(page.id, page.st)
                ?.takeIf { it.isUsable() }
        val nativeSnapshotPostId = nativeSnapshot?.visiblePostId
        // Cross-tab durable fallback (device log 26_06-10-34, cross-topic BACK landed on the page-top
        // post 143861523 instead of the source post). The per-tab back snapshots live in this tab's
        // [historyController] and are wiped by `resetTransientStateForNewTopic -> clear()` when the
        // source topic's tab is REUSED for the cross-topic open. With the snapshot gone, both the
        // page anchor and `peekBackSnapshot`/`findUsableBackSnapshotByPost` yield nothing and the URL
        // degrades to a bare `st=` page — i.e. the first post of that page. [returnPositionStore] is
        // app-scoped and survives the tab reset (its documented cross-tab purpose), so consult it to
        // recover the precise #entry post + the st it was captured on. Strictly a gap-filler: it is
        // only used when the page anchor and per-tab snapshot are both absent, so the working
        // same-tab path (native snapshot present) is unchanged.
        val durableReturn = returnPositionStore.peek(page.id)
                ?.takeIf { !it.postId.isNullOrBlank() }
        // Cross-topic BACK st/anchor mismatch fix (log 1121483): in HYBRID the loaded page.st (e.g.
        // 1260/page-64) can differ from the page the user navigated FROM (st=1240/page-63 where the
        // restore anchor post lives). The native back snapshot for that source post is keyed by the
        // SOURCE st, so reconcile: if the post we are about to restore was captured at a DIFFERENT st,
        // build the URL with the snapshot's st so the #entry post is actually on the loaded page.
        val restoreAnchorPostId = page.anchorPostId?.takeIf { it.isNotBlank() }
                ?: nativeSnapshotPostId
                ?: durableReturn?.postId
        val sourceSnapshot = historyController
                .findUsableBackSnapshotByPost(page.id, restoreAnchorPostId)
                ?.takeIf { it.pageSt != page.st && it.visiblePostId != null }
        // Prefer a per-tab st reconciliation; otherwise, when we are recovering purely from the
        // durable cross-tab store (no page anchor, no per-tab snapshot), use the st it was saved on.
        val durableReturnStForUrl = durableReturn
                ?.takeIf { page.anchorPostId.isNullOrBlank() && nativeSnapshotPostId.isNullOrBlank() }
                ?.pageSt
        val restoreSt = sourceSnapshot?.pageSt ?: durableReturnStForUrl ?: page.st
        if (BuildConfig.DEBUG && sourceSnapshot != null) {
            Log.i(
                    THEME_HISTORY_TAG,
                    "back_restore_st_reconciled topic=${page.id} pageSt=${page.st} sourceSt=${sourceSnapshot.pageSt} post=$restoreAnchorPostId"
            )
        }
        val url = ThemeBackRestoreUrlPolicy.buildRestoreUrl(
                topicId = page.id,
                st = restoreSt,
                anchorPostId = page.anchorPostId,
                pageUrl = page.url,
                // Native per-tab snapshot first; durable cross-tab store fills the gap when the tab
                // was reused and the per-tab snapshot was wiped (keeps #entry instead of page-top).
                nativeSnapshotPostId = nativeSnapshotPostId ?: durableReturn?.postId
        )
        if (BuildConfig.DEBUG &&
                page.anchorPostId.isNullOrBlank() &&
                nativeSnapshotPostId.isNullOrBlank() &&
                durableReturn?.postId != null &&
                url.contains("#entry")
        ) {
            Log.i(
                    THEME_HISTORY_TAG,
                    "back_restore_from_return_store topic=${page.id} st=$restoreSt durablePost=${durableReturn.postId} durableSt=${durableReturn.pageSt} url=$url"
            )
            NavBackstackTrace.log(
                    event = "back_restore_from_return_store",
                    navigator = "ThemeViewModel",
                    topicId = page.id,
                    reason = "per_tab_snapshot_wiped_cross_tab_recover"
            )
        }
        if (BuildConfig.DEBUG &&
                page.anchorPostId.isNullOrBlank() &&
                !nativeSnapshotPostId.isNullOrBlank() &&
                url.contains("#entry")
        ) {
            Log.i(
                    THEME_HISTORY_TAG,
                    "back_restore_from_native_snapshot topic=${page.id} st=${page.st} snapshotPost=$nativeSnapshotPostId status=${nativeSnapshot?.status} url=$url"
            )
            NavBackstackTrace.log(
                    event = "back_restore_from_native_snapshot",
                    navigator = "ThemeViewModel",
                    topicId = page.id,
                    reason = "source_anchor_ttl_independent"
            )
        }
        return url
    }

    private suspend fun mapEntityWithPostListGuard(page: ThemePage, reason: String) {
        themeUseCase.mapEntity(page, reason)
        val expected = themeUseCase.expectedListPostCount(page)
        val underRendered = ThemeHtmlMetrics.isListPostsUnderRendered(page, page.html, expected)
        if (ThemeHatToolbarClickPolicy.shouldPreserveHatOnRenderRetry(
                        userHatOpenOverride,
                        reason,
                        underRendered,
                )
        ) {
            return
        }
        if (!ThemeHtmlMetrics.shouldRetryRenderWithoutHat(page, expected)) return
        if (BuildConfig.DEBUG) {
            Timber.w(
                    "Retry theme render without hat reason=%s expected=%d actual=%d pagePosts=%d topic=%d page=%d",
                    reason,
                    expected,
                    ThemeHtmlMetrics.countListPostContainers(page.html),
                    page.posts.size,
                    page.id,
                    page.pagination.current
            )
        }
        page.topicHatPost = null
        themeUseCase.mapEntity(page, "${reason}NoHat")
    }

    private suspend fun remapHybridPagesForCurrentTopic(page: ThemePage, reason: String) {
        if (currentTopicScrollMode != AppPreferences.Main.TopicScrollMode.HYBRID || loadedPages.isEmpty()) {
            return
        }
        loadedPages[page.pagination.current] = page
        themeUseCase.mapHybridPages(page, loadedPages.values, reason)
    }

    private suspend fun rerenderCurrentPageAfterForumBlacklistChange(anchorPostId: Int) {
        val page = currentPage ?: return
        if (anchorPostId > 0) {
            page.anchorPostId = anchorPostId.toString()
            page.scrollY = 0
            page.anchorOffsetTop = null
            page.scrollRatio = null
            page.wasNearBottom = false
        }
        if (currentTopicScrollMode == AppPreferences.Main.TopicScrollMode.HYBRID && loadedPages.isNotEmpty()) {
            loadedPages[page.pagination.current] = page
            themeUseCase.mapHybridPages(page, loadedPages.values, "forumBlacklist")
        } else {
            mapEntityWithPostListGuard(page, "forumBlacklist")
        }
        _updateView.tryEmit(page)
    }

    private fun detectTopicHatPost(page: ThemePage): ThemePost? =
            TopicPrependedHatPolicy.detectPrependedHat(page)

    private fun clearTopicHatCache() {
        firstPageHatPostId = null
        topicHatPost = null
        topicHatTopicId = null
    }

    private fun cacheTopicHat(page: ThemePage, post: ThemePost?) {
        if (post == null || post.id <= 0 || page.id <= 0) return
        enrichTopicHatPostFromKnownOriginal(page, post)
        topicHatPost = post
        topicHatTopicId = page.id
        firstPageHatPostId = post.id
    }

    private fun cachedTopicHatFor(page: ThemePage): ThemePost? {
        return topicHatPost?.takeIf { topicHatTopicId == page.id && it.id > 0 }
    }

    private fun resolveTopicHatForOverlay(page: ThemePage): ThemePost? {
        page.topicHatPost?.takeIf { it.id > 0 }?.let { return it }
        cachedTopicHatFor(page)?.let { return it }
        if (topicHatTopicId != page.id || page.id <= 0) return null
        val hatId = firstPageHatPostId?.takeIf { it > 0 } ?: return null
        topicHatPost?.takeIf { it.id == hatId }?.let { return it }
        loadedPages.values
                .asSequence()
                .filter { it.id == page.id }
                .mapNotNull { loaded ->
                    loaded.topicHatPost?.takeIf { it.id == hatId }
                            ?: loaded.posts.firstOrNull { post -> post.id == hatId }
                }
                .firstOrNull()
                ?.let { return it }
        return ThemePost().apply {
            id = hatId
            number = 1
        }
    }

    private fun ThemePost.hasPostRatingMetadata(): Boolean =
            !postRating.isNullOrBlank() || canPlusPostRating || canMinusPostRating

    private fun enrichTopicHatPostFromKnownOriginal(page: ThemePage, post: ThemePost) {
        val source = sequenceOf(
                cachedTopicHatFor(page),
                currentPage?.topicHatPost?.takeIf { currentPage?.id == page.id },
                currentPage?.posts?.firstOrNull { it.id == post.id },
                loadedPages.values.asSequence()
                        .filter { it.id == page.id }
                        .mapNotNull { loadedPage ->
                            loadedPage.topicHatPost?.takeIf { it.id == post.id }
                                    ?: loadedPage.posts.firstOrNull { it.id == post.id }
                        }
                        .firstOrNull()
        )
                .filterNotNull()
                .firstOrNull { it !== post && it.id == post.id && it.hasPostRatingMetadata() }
                ?: return

        if (post.postRating.isNullOrBlank() && !source.postRating.isNullOrBlank()) {
            post.postRating = source.postRating
        }
        if (post.userPostCount == null && source.userPostCount?.let { it > 0 } == true) {
            post.userPostCount = source.userPostCount
        }
        if (!post.canPlusPostRating && !post.canMinusPostRating &&
                (source.canPlusPostRating || source.canMinusPostRating)
        ) {
            post.canPlusPostRating = source.canPlusPostRating
            post.canMinusPostRating = source.canMinusPostRating
        }
    }

    private fun validateLoadedPage(page: ThemePage): Boolean {
        val requestedSt = try {
            Uri.parse(themeUrl).getQueryParameter("st")?.toIntOrNull() ?: 0
        } catch (_: Throwable) {
            0
        }
        if (requestedSt <= 0 || page.pagination.perPage <= 0) return true
        val requestedPage = requestedSt / page.pagination.perPage + 1
        if (requestedPage <= 1 || page.pagination.current == requestedPage) return true
        Timber.w(
                "Rejected mismatched theme page: requested=%d loaded=%d url=%s",
                requestedPage,
                page.pagination.current,
                page.url
        )
        return false
    }

    private fun normalizeStandalonePageHat(page: ThemePage) {
        val requestedPage = page.pagination.current.coerceAtLeast(1)
        if (requestedPage <= 1) {
            page.topicHatPost = detectTopicHatPost(page)
                    ?: page.topicHatPost
                    ?: cachedTopicHatFor(page)
                    ?: currentPage?.topicHatPost?.takeIf { currentPage?.id == page.id }
            cacheTopicHat(page, page.topicHatPost)
            page.topicHatPost?.id?.takeIf { it > 0 }?.let { hatId ->
                page.posts.removeAll { it.id == hatId }
            }
            return
        }

        // A direct page jump should render only that page. If the server prepends
        // the topic hat to non-first pages, keep it out of the normal posts list
        // and render the cached topic-level hat separately when it is known.
        val cachedHatId = cachedTopicHatFor(page)?.id?.takeIf { it > 0 }
                ?: currentPage?.topicHatPost?.id?.takeIf { currentPage?.id == page.id && it > 0 }
                ?: firstPageHatPostId
        val resolvedHatId = TopicPrependedHatPolicy.resolvePrependedHatId(
                page = page,
                requestedPage = requestedPage,
                knownHatId = cachedHatId,
        )
        if (resolvedHatId != null) {
            firstPageHatPostId = cachedHatId ?: resolvedHatId
            page.posts.firstOrNull { it.id == resolvedHatId }?.let { cacheTopicHat(page, it) }
        }
        stripDuplicateHatFromNonFirstPage(page, requestedPage)
        // Keep page-1 hat out of the first paint on deep pages. Metadata is cached for the toolbar
        // overlay; HTML is injected on demand when the user opens the hat.
        page.topicHatPost = null
        page.isInlineHatOpen = false
        if (userHatOpenOverride != true) {
            page.isHatOpen = false
        }
    }

    private fun promoteTopicHatForHybridPage(page: ThemePage) {
        val detected = detectTopicHatPost(page)
        if (detected != null) {
            page.topicHatPost = detected
            cacheTopicHat(page, detected)
            return
        }
        page.topicHatPost = cachedTopicHatFor(page)
                ?: currentPage?.topicHatPost?.takeIf { currentPage?.id == page.id }
                ?: firstPageHatPostId?.let { knownHatId ->
                    loadedPages.values
                            .asSequence()
                            .mapNotNull { it.topicHatPost ?: it.posts.firstOrNull { post -> post.id == knownHatId } }
                            .firstOrNull()
                }
        page.topicHatPost?.let { cacheTopicHat(page, it) }
    }

    private fun stripDuplicateHatFromNonFirstPage(page: ThemePage, requestedPage: Int): Boolean {
        val before = page.posts.size
        val beforeIds = if (BuildConfig.DEBUG) page.posts.map { it.id } else emptyList()
        val knownHatId = TopicPrependedHatPolicy.resolvePrependedHatId(
                page = page,
                requestedPage = requestedPage,
                knownHatId = firstPageHatPostId ?: cachedTopicHatFor(page)?.id,
        )
        if (knownHatId != null && firstPageHatPostId == null) {
            firstPageHatPostId = knownHatId
            page.posts.firstOrNull { it.id == knownHatId }?.let { cacheTopicHat(page, it) }
        }
        val kept = TopicPrependedHatPolicy.stripFromNonFirstPage(
                page = page,
                requestedPage = requestedPage,
                knownHatId = knownHatId,
        )
        if (before > page.posts.size) {
            Timber.w("Removed prepended hat post from non-first page")
            if (BuildConfig.DEBUG) {
                val afterIds = page.posts.map { it.id }
                val removed = beforeIds.filterNot { it in afterIds }
                Log.i(
                        THEME_HISTORY_TAG,
                        "hat_strip_diag topic=${page.id} reqPage=$requestedPage knownHatId=$knownHatId " +
                                "anchorPostId=${page.anchorPostId} anchors=${page.anchors} " +
                                "removed=$removed kept=$afterIds"
                )
            }
        }
        return kept
    }

    private fun maybeLoadTopicHatMetadata(page: ThemePage, forceToolbarOpen: Boolean = false) {
        if (_loadAction == ThemeLoadAction.End || pendingEndNavigation) return
        val cachedHat = cachedTopicHatFor(page)
        val visiblePageNumber = getVisibleCurrentPage()
        if (!forceToolbarOpen &&
                !ThemeHatMetadataLoadPolicy.shouldScheduleDeferredHatMetadataLoad(
                        page,
                        cachedHat,
                        hatMetadataJob?.isActive == true,
                        visiblePageNumber = visiblePageNumber,
                )
        ) {
            ensureTopicTitleFromFirstPageIfNeeded(page)
            return
        }
        if (hatMetadataJob?.isActive == true) return
        val topicId = page.id
        val traceId = openTrace.id
        val session = infiniteSession
        val hatOpen = userHatOpenOverride ?: false
        val pollOpen = page.isPollOpen
        hatMetadataJob = scope.launch {
            when (val result = themeUseCase.loadTheme(buildCleanThemeUrl(topicId, 0), hatOpen, pollOpen)) {
                is ThemeUseCase.LoadResult.Success -> {
                    if (session != infiniteSession || traceId != openTrace.id) return@launch
                    val firstPage = result.page
                    if (firstPage.id != topicId) return@launch
                    val detected = detectTopicHatPost(firstPage) ?: firstPage.topicHatPost
                    cacheTopicHat(firstPage, detected)
                    val current = currentPage ?: return@launch
                    if (current.id != topicId || getVisibleCurrentPage() <= 1) return@launch
                    ThemeToolbarTitlePolicy.mergeTitleFromFirstPage(current, firstPage)
                    rememberSessionTopicTitle(current.title)
                    val titleAfter = current.title?.trim().orEmpty()
                    if (titleAfter.isNotEmpty()) {
                        _uiEvents.tryEmit(ThemeUiEvent.UpdateTopicToolbar(current))
                    }
                    val hat = cachedTopicHatFor(current) ?: return@launch
                    current.topicHatPost = hat
                    enrichTopicHatPostFromKnownOriginal(current, hat)
                    stripDuplicateHatFromNonFirstPage(current, current.pagination.current)
                    firstPageHatPostId?.takeIf { it > 0 }?.let { hatId ->
                        _uiEvents.tryEmit(ThemeUiEvent.StripPrependedTopicHatFromDom(hatId))
                    }
                    preloadHatOverlayHostForToolbar(current, hat)
                    if (ThemeHatMetadataLoadPolicy.shouldRefreshToolbarAfterDeferredHatMetadataLoad(
                                    userHatOpenOverride
                            )
                    ) {
                        _uiEvents.tryEmit(ThemeUiEvent.RefreshToolbarMenu)
                    }
                    if (!ThemeHatMetadataLoadPolicy.shouldEmitViewUpdateAfterDeferredHatMetadataLoad(
                                    userHatOpenOverride
                            )
                    ) {
                        return@launch
                    }
                    current.isHatOpen = false
                    pendingHatToolbarClick = false
                    pendingHatToolbarOpenAfterRender = userHatOpenOverride == true
                    TopicHatOpenPolicy.prepareOverlayStateForRender(
                            page = current,
                            userHatOpenOverride = userHatOpenOverride,
                            pendingToolbarOverlayOpen = pendingHatToolbarOpenAfterRender,
                    )
                    mapEntityWithPostListGuard(current, "hatMetadata")
                    if (traceId != openTrace.id) return@launch
                    if (ThemeHtmlMetrics.isListPostsUnderRendered(
                                    current,
                                    current.html,
                                    themeUseCase.expectedListPostCount(current)
                            )
                    ) {
                        return@launch
                    }
                    emitHatMetadataViewUpdate(traceId, current)
                }
                is ThemeUseCase.LoadResult.Error -> {
                    // The page itself is usable; absence of lazy hat metadata should not block reading.
                }
            }
        }
    }

    private fun scheduleDeferredPageMetadataEnrichment(page: ThemePage, traceId: String) {
        if (page.id <= 0 || page.posts.isEmpty()) return
        pageMetadataEnrichmentJob?.cancel()
        pageMetadataEnrichmentJob = scope.launch {
            delay(ThemeDeferredMetadataEnrichmentPolicy.DELAY_MS)
            if (traceId != openTrace.id) return@launch
            val current = currentPage ?: return@launch
            if (current.id != page.id) return@launch
            val navigationBefore = ThemeDeferredMetadataEnrichmentPolicy.navigationSnapshot(current)
            val beforeByPostId = ThemeDeferredMetadataPatcher.snapshotByPostId(current.posts)
            val changed = themeUseCase.enrichPageMetadata(current)
            if (!changed || traceId != openTrace.id) return@launch
            if (current.id != page.id) return@launch
            if (!ThemeDeferredMetadataEnrichmentPolicy.navigationUnchanged(
                            navigationBefore,
                            ThemeDeferredMetadataEnrichmentPolicy.navigationSnapshot(current),
                    )
            ) {
                return@launch
            }
            ThemeDeferredMetadataPatcher.uiEvents(beforeByPostId, current.posts).forEach { event ->
                _uiEvents.tryEmit(event)
            }
            historyController.updateHistoryLast(current)
        }
    }

    private fun validateNonFirstPagePostNumbers(page: ThemePage, requestedPage: Int = page.pagination.current): Boolean {
        if (requestedPage <= 1 || page.pagination.perPage <= 0) return true
        val firstNumber = page.posts.firstOrNull()?.number ?: return true
        val expectedMin = (requestedPage - 1) * page.pagination.perPage + 1
        return firstNumber >= expectedMin
    }

    override fun onPollResultsClick(url: String?) {
        val target = url
                ?.takeIf { it.isNotBlank() }
                ?: themeUrl
                .replaceFirst("#[^&]*", "")
                        .replace("&mode=show", "") + "&mode=show"
        loadUrl(ThemePollUrlPolicy.appendPollOpen(target))
    }

    private fun appendPollOpen(url: String): String = ThemePollUrlPolicy.appendPollOpen(url)

    override fun onPollClick() {
        loadUrl(ThemePollUrlPolicy.buildPollOpenUrl(themeUrl))
    }

    override fun onPollSubmit(action: String, method: String, encodedForm: String) {
        scope.launch {
            when (val result = interactionUseCase.submitPoll(action, method, encodedForm)) {
                is ThemeInteractionUseCase.PollSubmitResult.Success -> {
                    val page = result.page
                    val previousPage = currentPage
                    previousPage?.takeIf { it.id == page.id }?.let {
                        page.scrollY = it.scrollY
                        page.anchorPostId = it.anchorPostId
                        page.anchorOffsetTop = it.anchorOffsetTop
                        page.scrollRatio = it.scrollRatio
                        page.wasNearBottom = it.wasNearBottom
                        page.isInlineHatOpen = it.isInlineHatOpen
                    }
                    page.isPollOpen = true
                    _loadAction = ThemeLoadAction.Refresh
                    themeUrl = page.url ?: themeUrl
                    if (BuildConfig.DEBUG) {
                        Timber.d(
                                "pollSubmit: received topic=${page.id} page=${page.pagination.current}/${page.pagination.all} posts=${page.posts.size} hasPoll=${page.poll != null} cachedHat=${cachedTopicHatFor(page)?.id} detectedHat=${detectTopicHatPost(page)?.id}"
                        )
                    }
                    onLoadData(page)
                    router.showSystemMessage("Голос принят")
                }
                is ThemeInteractionUseCase.PollSubmitResult.Error -> { /* handled in UseCase */ }
            }
        }
    }

    fun updatePageHistoryHtml(
            target: ThemePage,
            html: String,
            scrollY: Int,
            anchorPostId: String? = null,
            anchorOffsetTop: Double? = null,
            scrollRatio: Double? = null,
            wasNearBottom: Boolean? = null
    ) {
        val pendingSource = pendingHistorySourceAnchor?.takeIf { sourceAnchorAppliesTo(target, it) }
        // Multi-back anchor loss fix (log 239158): a trailing snapshot for an in-tab findpost page
        // carries the click-time visible post (e.g. 132558226), which must NOT overwrite the entry's
        // authoritative open anchor (e.g. 132558585). Prefer the authoritative #entry as the restored
        // anchor while still honoring the source anchor's scroll metadata.
        val authoritativeAnchor = target.authoritativeAnchorPostId?.takeIf { it.isNotBlank() }
        val effectiveScrollY = pendingSource?.scrollY ?: scrollY
        val rawEffectiveAnchorPostId = pendingSource?.postId ?: anchorPostId
        val keepAuthoritativeAnchor = ThemeAuthoritativeAnchorPolicy.shouldKeepAuthoritative(
                authoritativeAnchorPostId = authoritativeAnchor,
                candidateAnchorPostId = rawEffectiveAnchorPostId,
        )
        val effectiveAnchorPostId = ThemeAuthoritativeAnchorPolicy.resolveEntryAnchor(
                authoritativeAnchorPostId = authoritativeAnchor,
                candidateAnchorPostId = rawEffectiveAnchorPostId,
        )
        val effectiveAnchorOffsetTop = pendingSource?.offsetTop ?: anchorOffsetTop
        val effectiveScrollRatio = pendingSource?.ratio ?: scrollRatio
        // Log 14_06-19: prior code hard-reset wasNearBottom=false whenever a sourceAnchor was
        // active, which prevented the AMBIGUOUS_ALL_READ mark-read path from firing on the
        // last page even when the user had scrolled to the bottom before the link click.
        // Derive wasNearBottom from the source anchor's own ratio when present, so a
        // near-bottom click in the topic still triggers the mark-read GET view=getlastpost.
        val effectiveWasNearBottom = if (pendingSource != null) {
            val r = pendingSource.ratio
            (r != null && r >= TopicReadExitPolicy.LAST_PAGE_MARK_READ_RATIO_THRESHOLD)
        } else {
            wasNearBottom
        }
        markTopicReadIfEndReached(
                target = target,
                wasNearBottom = effectiveWasNearBottom,
                scrollRatio = effectiveScrollRatio,
                source = "history_snapshot"
        )
        historyController.updatePageHistoryHtml(
                target,
                html,
                effectiveScrollY,
                effectiveAnchorPostId,
                effectiveAnchorOffsetTop,
                effectiveScrollRatio,
                effectiveWasNearBottom
        )
        // Multi-back anchor loss: mirror the committed in-tab scroll position into the cross-tab
        // return-position store so tab re-entry can restore it (the per-tab history is cleared on
        // topic switch). Uses effective anchor/scroll, which already honor an active source anchor.
        //
        // Trailing-capture overwrite fix (device log 25_06-19-16-48, cross-topic back to wrong
        // post): when the JS source-anchor TTL has expired and the user has manually scrolled
        // within the source page, a trailing onPauseOrHide JS capture of the visible post (e.g.
        // 143873895) flows through updatePageHistoryHtml. The shouldKeepAuthoritative branch above
        // protects the page's own anchorPostId, but a raw `returnPositionStore.save(effectiveAnchorPostId)`
        // would still overwrite the durable cross-topic back snapshot's post id (143876380) with
        // that scrolled-to visible post. The next reentry / tab switch / favorites re-open would
        // then restore the wrong post — the dynamic wrong-post smoking gun (143860995, 143986594,
        // 143873895 — different per log because it's the user's last visible post).
        //
        // The durable back-snapshot was stamped at crossTopicOpen time with the click-time source
        // post and survives independently of the JS TTL. Prefer it for the cross-tab save. Falls
        // back to the authoritative explicit-open anchor (in-tab findpost) and finally to the raw
        // effective anchor (normal scroll pages keep updating their genuine viewed anchor).
        val durableBackSnapshotPostId = historyController.peekBackSnapshot(target.id, target.st)
                ?.takeIf { it.isUsable() }
                ?.visiblePostId
        val returnPostId = ThemeAuthoritativeAnchorPolicy.resolveReturnPositionPostId(
                durableBackSnapshotPostId = durableBackSnapshotPostId,
                pageAuthoritativeAnchorPostId = target.authoritativeAnchorPostId,
                candidateAnchorPostId = effectiveAnchorPostId,
        )
        if (target.id > 0 && !returnPostId.isNullOrBlank()) {
            returnPositionStore.save(
                    topicId = target.id,
                    pageSt = target.st,
                    postId = returnPostId,
                    scrollY = effectiveScrollY
            )
        }
        if (keepAuthoritativeAnchor) {
            Log.i(
                    THEME_HISTORY_TAG,
                    "history snapshot kept authoritative topic=${target.id} st=${target.st} page=${target.pagination.current} authoritativePostId=$authoritativeAnchor ignoredPost=$rawEffectiveAnchorPostId href=${pendingSource?.href}"
            )
        }
        if (pendingSource != null) {
            Log.i(
                    THEME_HISTORY_TAG,
                    "history snapshot kept sourceAnchor topic=${target.id} st=${target.st} page=${target.pagination.current} sourcePostId=${pendingSource.postId} ignoredPost=$anchorPostId href=${pendingSource.href}"
            )
        }
        if (BuildConfig.DEBUG) {
            Log.i(
                    THEME_HISTORY_TAG,
                    "snapshot history topic=${target.id} st=${target.st} page=${target.pagination.current} y=$effectiveScrollY anchor=$effectiveAnchorPostId offset=$effectiveAnchorOffsetTop ratio=$effectiveScrollRatio bottom=$effectiveWasNearBottom"
            )
        }
    }

    private fun markTopicReadIfEndReached(
            target: ThemePage,
            wasNearBottom: Boolean?,
            scrollRatio: Double?,
            source: String
    ) {
        if (target.id <= 0) {
            ThemePostReadStateDiagnostics.markReadSkipped(
                    topicId = target.id,
                    reason = "invalid_topic_id",
                    source = source
            )
            return
        }
        if (target.pagination.current < target.pagination.all) {
            ThemePostReadStateDiagnostics.markReadSkipped(
                    topicId = target.id,
                    reason = "not_last_page",
                    source = source,
                    currentPage = target.pagination.current,
                    allPages = target.pagination.all
            )
            FpdaDebugLog.warn(
                    FpdaDebugLog.TAG_THEME_POST_READ_STATE,
                    "mark_read_gate_not_last_page",
                    mapOf(
                            "topicId" to target.id,
                            "current" to target.pagination.current,
                            "all" to target.pagination.all,
                            "source" to source
                    )
            )
            return
        }
        // Fix #4: end-action path. If the user is loading the last page via End action
        // and we've already scrolled (scrollY > 0), treat it as bottom-reached.
        if (_loadAction == ThemeLoadAction.End && target.scrollY > 0) {
            ThemePostReadStateDiagnostics.markReadGateCheck(
                    topicId = target.id,
                    currentPage = target.pagination.current,
                    allPages = target.pagination.all,
                    wasNearBottom = wasNearBottom,
                    scrollRatio = scrollRatio,
                    result = "pass_end_action"
            )
            themeUseCase.markTopicRead(
                    topicId = target.id,
                    reason = "theme_last_page_loaded_after_end",
                    source = source
            )
            return
        }
        if (!TopicReadExitPolicy.shouldMarkReadOnLastPageExit(wasNearBottom, scrollRatio)) {
            ThemePostReadStateDiagnostics.markReadSkipped(
                    topicId = target.id,
                    reason = "exit_policy_fail",
                    source = source,
                    currentPage = target.pagination.current,
                    allPages = target.pagination.all
            )
            ThemePostReadStateDiagnostics.markReadGateCheck(
                    topicId = target.id,
                    currentPage = target.pagination.current,
                    allPages = target.pagination.all,
                    wasNearBottom = wasNearBottom,
                    scrollRatio = scrollRatio,
                    result = "skip_exit_policy"
            )
            return
        }
        ThemePostReadStateDiagnostics.markReadGateCheck(
                topicId = target.id,
                currentPage = target.pagination.current,
                allPages = target.pagination.all,
                wasNearBottom = wasNearBottom,
                scrollRatio = scrollRatio,
                result = "pass"
        )
        themeUseCase.markTopicRead(
                topicId = target.id,
                reason = "theme_last_page_bottom_reached",
                source = source
        )
    }

    override fun onLinkSourceAnchorCaptured(payload: String) {
        val json = runCatching { JSONObject(payload) }.getOrNull()
        val postId = json?.optString("postId").orEmpty().takeIf { it.isNotBlank() && it != "0" }
        if (postId == null) {
            Log.i(THEME_HISTORY_TAG, "native sourceAnchor ignored payload=$payload")
            return
        }
        val sourceJson = json ?: return
        val anchor = ThemeLinkSourceAnchor(
                href = sourceJson.optString("href").orEmpty(),
                postId = postId,
                offsetTop = sourceJson.optDouble("offsetTop").takeIf { !it.isNaN() },
                scrollY = sourceJson.optDouble("scrollY").takeIf { !it.isNaN() }?.toInt() ?: currentPage?.scrollY ?: 0,
                ratio = sourceJson.optDouble("ratio").takeIf { !it.isNaN() },
                eventType = sourceJson.optString("eventType").orEmpty(),
                capturedAt = SystemClock.uptimeMillis()
        )
        lastLinkSourceAnchor = anchor
        // R-02: a capture-phase source-anchor is the start of a NEW user gesture. Open a fresh
        // dispatch window so the next handleNewUrl claim succeeds, while repeat dispatches from
        // the same gesture (handleUri + onPageStarted for the same tap) are suppressed.
        navigationGestureGuard.beginGesture()
        val sourcePage = getRefreshCapturePageInstance()
        pendingHistorySourceAnchor = anchor
        pendingHistorySourceTopicId = sourcePage?.id
        pendingHistorySourceSt = sourcePage?.st
        if (sourcePage != null) {
            applyLinkSourceAnchorSnapshot(sourcePage, anchor, "jsBridge")
        }
        Log.i(
                THEME_HISTORY_TAG,
                "native sourceAnchor received href=${anchor.href} sourceTopic=${sourcePage?.id} sourceSt=${sourcePage?.st} sourcePage=${sourcePage?.pagination?.current} sourcePostId=${anchor.postId} offset=${anchor.offsetTop} y=${anchor.scrollY} ratio=${anchor.ratio} event=${anchor.eventType}"
        )
    }

    fun consumeLinkSourceAnchorFor(targetUrl: String): ThemeLinkSourceAnchor? {
        val anchor = lastLinkSourceAnchor ?: return null
        val ageMs = SystemClock.uptimeMillis() - anchor.capturedAt
        if (ageMs > SOURCE_ANCHOR_TTL_MS) {
            Log.i(
                    THEME_HISTORY_TAG,
                    "native sourceAnchor stale target=$targetUrl href=${anchor.href} sourcePostId=${anchor.postId} ageMs=$ageMs"
            )
            lastLinkSourceAnchor = null
            return null
        }
        lastLinkSourceAnchor = null
        // BACK-to-wrong-post fix: do NOT clear pendingHistorySourceAnchor here.
        // The navigation consumes the anchor for the OUTGOING load, but the SOURCE page's
        // trailing onPauseOrHide async DOM capture still runs ~150ms later and would otherwise
        // overwrite the authoritative clicked-post anchor (e.g. entry143876380) with the
        // viewport-top post (e.g. entry143876586). Keeping pendingHistorySourceAnchor alive lets
        // updatePageHistoryHtml() honor sourceAnchorAppliesTo() and preserve the exact source post.
        // It stays topic/st-scoped, is TTL-bounded (SOURCE_ANCHOR_TTL_MS), and is reset on any fresh load.
        Log.i(
                THEME_HISTORY_TAG,
                "native sourceAnchor consume target=$targetUrl href=${anchor.href} sourcePostId=${anchor.postId} offset=${anchor.offsetTop} y=${anchor.scrollY} ratio=${anchor.ratio} event=${anchor.eventType} ageMs=$ageMs"
        )
        return anchor
    }

    fun updatePageRefreshScrollSnapshot(
            target: ThemePage,
            scrollY: Int,
            anchorPostId: String?,
            anchorOffsetTop: Double?,
            scrollRatio: Double?,
            wasNearBottom: Boolean
    ) {
        val request = pendingRefreshRequest
        val matchingRequest = request?.takeIf { it.matches(target) }
        val pendingSource = pendingHistorySourceAnchor
                ?.takeIf { matchingRequest == null }
                ?.takeIf { sourceAnchorAppliesTo(target, it) }
        val effectiveScrollY = pendingSource?.scrollY ?: scrollY
        val rawEffectiveAnchorPostId = pendingSource?.postId ?: anchorPostId
        // Multi-back anchor loss fix (log 239158, in-tab findpost): this is the SECOND snapshot path
        // (parallel to updatePageHistoryHtml). A trailing source/visible-anchor snapshot for an
        // in-tab findpost page carries the click-time visible post (e.g. 121429251) which must NOT
        // overwrite the entry's authoritative open anchor (e.g. 121429450) — BACK must pop the source
        // post, not the neighbor. Mirror the updatePageHistoryHtml guard so both snapshot paths agree.
        val authoritativeAnchor = target.authoritativeAnchorPostId?.takeIf { it.isNotBlank() }
        val keepAuthoritativeAnchor = ThemeAuthoritativeAnchorPolicy.shouldKeepAuthoritative(
                authoritativeAnchorPostId = authoritativeAnchor,
                candidateAnchorPostId = rawEffectiveAnchorPostId,
        )
        val effectiveAnchorPostId = ThemeAuthoritativeAnchorPolicy.resolveEntryAnchor(
                authoritativeAnchorPostId = authoritativeAnchor,
                candidateAnchorPostId = rawEffectiveAnchorPostId,
        )
        val effectiveAnchorOffsetTop = pendingSource?.offsetTop ?: anchorOffsetTop
        val effectiveScrollRatio = pendingSource?.ratio ?: scrollRatio
        val effectiveWasNearBottom = if (pendingSource != null) false else wasNearBottom
        // Geometry-consistency guard (device log 25_06-22-18-38, cross-topic / in-tab BACK lands on
        // the wrong post 143860995 instead of source 143876380). When this is a source-anchor capture
        // (NO matching refresh request) for a page whose authoritative explicit-open anchor DIFFERS
        // from the visible post, the candidate carries the authoritative post id (kept by
        // resolveEntryAnchor) but the VISIBLE post's pixel geometry (`effectiveScrollY` = the post the
        // user scrolled to, e.g. 143860995 at y=11810). [applyRefreshSnapshot] would write that
        // mismatched tuple onto the live page instance, and the in-tab `cross_topic_return_in_place`
        // BACK restores `top.scrollY` directly — so BACK shows `#entry143876380` scrolled to 11810
        // (143860995's location). Reject the geometry mutation here, exactly like
        // [updatePageHistoryHtml]'s shouldKeepAuthoritative branch, so the page keeps the
        // authoritative post's own geometry. A genuine refresh restore (matchingRequest != null) is
        // never affected; the cross-topic equal-value case has keepAuthoritativeAnchor=false.
        if (keepAuthoritativeAnchor && matchingRequest == null) {
            Log.i(
                    THEME_HISTORY_TAG,
                    "refresh snapshot rejected mismatched geometry topic=${target.id} st=${target.st} page=${target.pagination.current} authoritativePostId=$authoritativeAnchor ignoredPost=$rawEffectiveAnchorPostId ignoredY=$effectiveScrollY href=${pendingSource?.href}"
            )
            return
        }
        applyRefreshSnapshot(target, matchingRequest, effectiveScrollY, effectiveAnchorPostId, effectiveAnchorOffsetTop, effectiveScrollRatio, effectiveWasNearBottom)
        if (matchingRequest != null) {
            val upgradedMode = ThemeRefreshScrollRestorePolicy.effectiveRestoreMode(
                    requestedMode = matchingRequest.restoreMode.name,
                    wasNearBottom = effectiveWasNearBottom,
                    scrollRatio = effectiveScrollRatio,
                    page = target,
                    scrollMode = currentTopicScrollMode
            )
            if (upgradedMode.equals("BOTTOM", ignoreCase = true) &&
                    !matchingRequest.restoreMode.name.equals("BOTTOM", ignoreCase = true)
            ) {
                pendingRefreshRequest = matchingRequest.copy(restoreMode = RefreshRestoreMode.BOTTOM)
            } else {
                pendingRefreshRequest = matchingRequest.withSnapshot(
                        scrollY = effectiveScrollY,
                        anchorPostId = target.anchorPostId,
                        anchorOffsetTop = target.anchorOffsetTop,
                        scrollRatio = target.scrollRatio,
                        wasNearBottom = target.wasNearBottom
                )
            }
        }
        if (keepAuthoritativeAnchor) {
            Log.i(
                    THEME_HISTORY_TAG,
                    "refresh snapshot kept authoritative topic=${target.id} st=${target.st} page=${target.pagination.current} authoritativePostId=$authoritativeAnchor ignoredPost=$rawEffectiveAnchorPostId href=${pendingSource?.href}"
            )
        }
        if (pendingSource != null) {
            Log.i(
                    THEME_HISTORY_TAG,
                    "snapshot kept sourceAnchor topic=${target.id} st=${target.st} page=${target.pagination.current} sourcePostId=${pendingSource.postId} ignoredPost=$anchorPostId href=${pendingSource.href}"
            )
        }
        if (BuildConfig.DEBUG) {
            Log.i(
                    REFRESH_SCROLL_TAG,
                    "vm capture apply id=${request?.id} source=${request?.source} mode=${request?.restoreMode} matched=${matchingRequest != null} targetTopic=${target.id} targetSt=${target.st} scrollY=$effectiveScrollY anchor=$effectiveAnchorPostId offset=$effectiveAnchorOffsetTop ratio=$effectiveScrollRatio bottom=$effectiveWasNearBottom"
            )
            Log.i(
                    THEME_HISTORY_TAG,
                    "snapshot capture topic=${target.id} st=${target.st} page=${target.pagination.current} y=$effectiveScrollY anchor=$effectiveAnchorPostId offset=$effectiveAnchorOffsetTop ratio=$effectiveScrollRatio bottom=$effectiveWasNearBottom request=${request?.id}"
            )
        }
    }

    private fun sourceAnchorAppliesTo(target: ThemePage, anchor: ThemeLinkSourceAnchor): Boolean {
        val ageMs = SystemClock.uptimeMillis() - anchor.capturedAt
        if (ageMs > SOURCE_ANCHOR_TTL_MS) {
            pendingHistorySourceAnchor = null
            pendingHistorySourceTopicId = null
            pendingHistorySourceSt = null
            Log.i(THEME_HISTORY_TAG, "sourceAnchor expired topic=${target.id} st=${target.st} sourcePostId=${anchor.postId} ageMs=$ageMs")
            return false
        }
        val topicId = pendingHistorySourceTopicId ?: return false
        val st = pendingHistorySourceSt ?: return false
        return target.id == topicId && target.st == st
    }

    private fun applyLinkSourceAnchorSnapshot(target: ThemePage, anchor: ThemeLinkSourceAnchor, reason: String) {
        // Multi-back anchor loss fix (log 239158): if this page was opened at an explicit findpost
        // post, a NEW in-tab link tapped from a DIFFERENT (scrolled-to) post must not overwrite the
        // entry's authoritative anchor with that click-time visible post. Keep the authoritative
        // #entry as the restore target; the new source anchor still updates scroll metadata + native
        // back snapshot. When the source post EQUALS the authoritative anchor (the working cross-topic
        // case, e.g. 143876380), this guard is a no-op and behavior is unchanged.
        val authoritative = target.authoritativeAnchorPostId?.takeIf { it.isNotBlank() }
        val keepAuthoritative = ThemeAuthoritativeAnchorPolicy.shouldKeepAuthoritative(
                authoritativeAnchorPostId = authoritative,
                candidateAnchorPostId = anchor.postId,
        )
        if (keepAuthoritative) {
            // Geometry consistency fix (device log 25_06-22-18-38, in-tab link from a DIFFERENT
            // post than the page's authoritative anchor): the click-time visible post (e.g.
            // 143860995 at y=11810) describes a DIFFERENT screen location than the authoritative
            // anchor (143876380 at y=3807). Earlier fixes correctly preserved `anchorPostId` but
            // still adopted the visible post's `scrollY`/`anchorOffsetTop`/`scrollRatio` and fed
            // that inconsistent tuple into the native back snapshot — so BACK loaded
            // `#entry143876380` but scrolled to y=11810 (the location of 143860995), leaving the
            // user looking at the wrong post. The authoritative post's recorded geometry (from the
            // last render at that post) is the only self-consistent value, so when we keep the
            // authoritative anchor we must reject the ENTIRE geometry mutation from this snapshot
            // and must NOT capture a new back snapshot (the existing one for the authoritative
            // post survives untouched). The cross-topic equal-value case is unaffected because
            // `keepAuthoritative` is false there.
            Log.i(
                    THEME_HISTORY_TAG,
                    "sourceAnchor kept authoritative reason=$reason topic=${target.id} st=${target.st} authoritativePostId=$authoritative ignoredSourcePost=${anchor.postId} ignoredY=${anchor.scrollY} href=${anchor.href}"
            )
            return
        }
        target.scrollY = anchor.scrollY
        target.anchorPostId = anchor.postId
        // Multi-hop BACK wrong-post fix (log 239158): the cached back-remap path replays `prev.url`
        // and renders `page.anchor` for the scroll. Keep url + anchor self-consistent with the
        // post that actually wins the restore (the clicked source post here).
        val restoreAnchorPostId = target.anchorPostId
                ?.takeIf { it.isNotBlank() }
                ?.removePrefix("entry")
        if (restoreAnchorPostId != null) {
            // `anchor` is a computed getter over `anchors.last()`; rewrite the list so the cached
            // back-remap render scrolls to the authoritative source post, not the stale one.
            val entryName = "entry$restoreAnchorPostId"
            if (target.anchor != entryName) {
                target.anchors.clear()
                target.anchors.add(entryName)
            }
            target.url = ThemeBackRestoreUrlPolicy.replaceEntryHash(target.url, restoreAnchorPostId)
        }
        target.anchorOffsetTop = anchor.offsetTop ?: 0.0
        target.scrollRatio = anchor.ratio
        target.wasNearBottom = (anchor.ratio ?: 0.0) >= TopicReadExitPolicy.LAST_PAGE_MARK_READ_RATIO_THRESHOLD
        // B-02/B-01: persist a native back snapshot scoped to topicId+st the moment the user
        // follows a link. This is independent of the 15s JS source-anchor TTL, so building the
        // back-restore URL ([buildBackRestoreUrl]) can keep #entry<postId> even if the JS anchor
        // expired during a slow read of the target topic.
        captureNativeBackSnapshot(target, reason)
        Log.i(
                THEME_HISTORY_TAG,
                "sourceAnchor applied reason=$reason topic=${target.id} st=${target.st} page=${target.pagination.current} sourcePostId=${anchor.postId} offset=${target.anchorOffsetTop} y=${anchor.scrollY} ratio=${anchor.ratio} href=${anchor.href}"
        )
    }

    /**
     * B-01/B-02: durably capture the current source page position into the history controller
     * as a native back snapshot. Scoped to topicId+st, it survives independently of the JS
     * source-anchor TTL so that returning to this topic restores the original post/scroll even
     * when the cross-topic target was read for longer than [SOURCE_ANCHOR_TTL_MS].
     */
    private fun captureNativeBackSnapshot(target: ThemePage, reason: String) {
        if (target.id <= 0) return
        val effectiveVisiblePostId = effectiveVisiblePostIdOf(target)
        historyController.captureBackSnapshot(
                TopicBackSnapshot.fromPage(
                        topicId = target.id,
                        pageSt = target.st,
                        visiblePostId = effectiveVisiblePostId,
                        scrollOffset = target.scrollY,
                        scrollRatio = target.scrollRatio,
                        wasNearBottom = target.wasNearBottom,
                        status = TopicBackSnapshotStatus.CAPTURED
                )
        )
        saveReturnPosition(target)
        if (BuildConfig.DEBUG) {
            Log.i(
                    THEME_HISTORY_TAG,
                    "native back snapshot captured reason=$reason topic=${target.id} st=${target.st} post=$effectiveVisiblePostId y=${target.scrollY} ratio=${target.scrollRatio}"
            )
        }
    }

    /**
     * STEP 3 — effective visible post id for a back-restore snapshot. Always prefers
     * [ThemePage.anchorPostId], falls back to the entry-prefixed [ThemePage.anchor] so the
     * snapshot carries a post id even on parser paths that only fill `anchor` (findpost reload).
     * A snapshot without a post id falls back to lossy pixel/ratio restore, which a reflow
     * (prepend, late image render) corrupts — so we always populate it when any anchor exists.
     */
    private fun effectiveVisiblePostIdOf(page: ThemePage): String? {
        page.anchorPostId?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
        return page.anchor
                ?.trim()
                ?.removePrefix("entry")
                ?.removePrefix("ENTRY")
                ?.takeIf { it.isNotEmpty() }
    }

    /**
     * Multi-back anchor loss (log 24_06-23-12-50): persist the real per-topic viewed position into
     * the app-scoped [returnPositionStore] so a later TAB re-entry (after the per-tab
     * [ThemeHistoryController] was cleared) can restore it instead of re-opening fresh via
     * getlastpost/getnewpost. Stores only when a usable anchor post id is present (the store itself
     * ignores blank ids), so a bare page-top open never downgrades a good saved position.
     */
    private fun saveReturnPosition(target: ThemePage) {
        if (target.id <= 0) return
        returnPositionStore.save(
                topicId = target.id,
                pageSt = target.st,
                postId = effectiveVisiblePostIdOf(target),
                scrollY = target.scrollY
        )
    }

    private fun applyRefreshSnapshot(
            target: ThemePage,
            request: RefreshRequest?,
            scrollY: Int,
            anchorPostId: String?,
            anchorOffsetTop: Double?,
            scrollRatio: Double?,
            wasNearBottom: Boolean
    ) {
        val effectiveModeName = ThemeRefreshScrollRestorePolicy.effectiveRestoreMode(
                requestedMode = request?.restoreMode?.name,
                wasNearBottom = wasNearBottom,
                scrollRatio = scrollRatio,
                page = target,
                scrollMode = currentTopicScrollMode
        )
        val preferBottom = effectiveModeName.equals("BOTTOM", ignoreCase = true)
        target.scrollY = scrollY
        // Reliability: never poison the current page with blank HTML.
        // Refresh/restore must restore scroll anchors without turning a previously loaded page into empty content.
        target.wasNearBottom = wasNearBottom || preferBottom
        target.refreshRestoreId = request?.id
        target.refreshRestoreMode = effectiveModeName
        target.refreshRestoreSource = request?.source
        if (preferBottom) {
            target.anchorPostId = null
            target.anchorOffsetTop = null
            target.scrollRatio = scrollRatio?.takeIf { it >= 0.92 } ?: 1.0
        } else {
            target.anchorPostId = ThemeRefreshScrollRestorePolicy.resolveRefreshAnchorPostId(
                    page = target,
                    anchorPostId = anchorPostId,
                    wasNearBottom = wasNearBottom,
                    scrollRatio = scrollRatio,
                    scrollMode = currentTopicScrollMode
            )
            target.anchorOffsetTop = anchorOffsetTop
            target.scrollRatio = scrollRatio
        }
    }

    fun getAnchorOffsetTop() = currentPage?.anchorOffsetTop

    fun getScrollRatio() = currentPage?.scrollRatio

    fun wasNearBottomBeforeRefresh() = currentPage?.wasNearBottom == true

    fun getRefreshRestoreId() = currentPage?.refreshRestoreId

    fun getRefreshRestoreMode() = currentPage?.refreshRestoreMode

    fun getRefreshRestoreSource() = currentPage?.refreshRestoreSource

    fun getPendingRefreshRequestId(): String? = pendingRefreshRequest?.id

    fun completeRefreshRestore(requestId: String?) {
        val currentId = currentPage?.refreshRestoreId
        if (requestId != null && currentId != null && requestId != currentId) return
        if (BuildConfig.DEBUG) {
            Log.i(
                    REFRESH_SCROLL_TAG,
                    "vm refreshRequest complete id=${requestId ?: currentId} pending=${pendingRefreshRequest?.id}"
            )
        }
        currentPage?.refreshRestoreId = null
        currentPage?.refreshRestoreMode = null
        currentPage?.refreshRestoreSource = null
        pendingRefreshRequest = null
    }

    fun completeBottomRefreshRestore(requestId: String?) = completeRefreshRestore(requestId)

    fun getCurrentPageInstance(): ThemePage? = currentPage

    /**
     * Resolve the topic-post highlight for [ThemeWebController] just before
     * it arms the JS apply + fadeout timer. Idempotent: the underlying
     * [TopicHighlightApply.applyToPage] keeps the same `renderGenerationId`
     * when the inputs / resolved target are unchanged (refresh of the same
     * page), so the JS guard still accepts the callback for the current
     * render and only ignores older ones.
     *
     * Called by the controller on every `reapplyTopicHighlight()` entry so
     * that the rendered page always has a `highlightTarget` /
     * `renderGenerationId` stamped onto it even when the previous render
     * path (`mapEntity` → `applyToPage`) was bypassed.
     */
    fun applyHighlightForCurrentPage(): forpdateam.ru.forpda.presentation.theme.HighlightResolution? {
        val page = currentPage ?: return null
        val forceLastViewed = forpdateam.ru.forpda.presentation.theme.TopicUnreadOpenPolicy
                .parseOpenSessionKind(page.openSessionKind) ==
                forpdateam.ru.forpda.presentation.theme.TopicUnreadOpenPolicy.TopicOpenSessionKind.AMBIGUOUS_ALL_READ
        val inputs = forpdateam.ru.forpda.presentation.theme.HighlightOpenInputsPolicy
                .resolveOpenInputs(
                        page = page,
                        openedViaFindPost = openedViaFindPostLink,
                        forceLastViewedInput = forceLastViewed,
                )
        return forpdateam.ru.forpda.presentation.theme.TopicHighlightApply.applyToPage(
                page = page,
                readPositionRepository = readPositionRepository,
                explicitPostId = inputs.explicitPostId,
                unreadUrl = inputs.unreadUrl,
                firstUnreadPostId = inputs.firstUnreadPostId,
                unreadPage = inputs.unreadPage,
                readPositionOverride = inputs.readPosition,
                lastReadSource = inputs.lastReadSource.name,
        )
    }

    fun getRefreshCapturePageInstance(): ThemePage? {
        val request = pendingRefreshRequest ?: return visibleCurrentPage
                ?.let { loadedPages[it] }
                ?: currentPage
        return loadedPages.values.firstOrNull { request.matches(it) } ?: currentPage
    }

    /** Страница, с которой пользователь пишет/отправляет пост (видимая в hybrid, не «первая загруженная»). */
    fun getPageForEditorAndSubmit(): ThemePage? = getRefreshCapturePageInstance()

    override fun shareText(text: String) {
        Utils.shareText(context, text)
    }

    fun copyLink() {
        currentPage?.let { interactionUseCase.copyLink(it.id) }
    }

    fun openSearch() {
        currentPage?.let {
            val url = forpdateam.ru.forpda.entity.remote.search.SearchSettings().apply {
                addForum(Integer.toString(it.forumId))
                addTopic(Integer.toString(it.id))
                source = forpdateam.ru.forpda.entity.remote.search.SearchSettings.SOURCE_CONTENT.first
                result = forpdateam.ru.forpda.entity.remote.search.SearchSettings.RESULT_POSTS.first
                subforums = forpdateam.ru.forpda.entity.remote.search.SearchSettings.SUB_FORUMS_FALSE
            }.toUrl()
            if (BuildConfig.DEBUG) Timber.d("openSearch url=$url")
            router.navigateTo(Screen.Search().apply {
                searchUrl = url
            })
        }
    }

    fun openSearchMyPosts() {
        currentPage?.let {
            navigationUseCase.openSearchMyPosts(it.id, it.forumId)
        }
    }

    fun openForum() {
        currentPage?.let { navigationUseCase.openForum(it.forumId) }
    }


    private fun getPostById(postId: Int): IBaseForumPost? =
            loadedPages.values
                    .asSequence()
                    .flatMap { page -> sequenceOf(page.topicHatPost) + page.posts.asSequence() }
                    .filterNotNull()
                    .firstOrNull { it.id == postId }
                    ?: currentPage?.topicHatPost?.takeIf { it.id == postId }
                    ?: currentPage?.posts?.firstOrNull { it.id == postId }
                    ?: topicHatPost?.takeIf { topicHatTopicId == currentPage?.id && it.id == postId }

    fun onRenderedTargetPost(topicId: Int, postId: Int?) {
        if (topicId <= 0 || postId == null || postId <= 0) return
        if (!shouldCommitRenderedRead(topicId, postId, page = null)) return
        themeUseCase.onRenderedTopicPosts(topicId, listOf(postId))
    }

    fun onTopicRenderSettled() {
        if (hasBlockingScrollPending()) {
            pendingRenderSettledAfterScroll = true
            return
        }
        markTopicRenderSettled()
    }

    private fun maybeMarkTopicRenderSettledAfterScroll() {
        if (!pendingRenderSettledAfterScroll || hasBlockingScrollPending()) return
        pendingRenderSettledAfterScroll = false
        markTopicRenderSettled()
    }

    private fun markTopicRenderSettled() {
        renderSettledTraceId = openTrace.id
        flushPendingHatOverlayRenderAfterScroll()
        flushPendingHatMetadataViewUpdate()
    }

    private fun flushPendingHatMetadataViewUpdate() {
        val pendingTrace = pendingHatMetadataEmitTraceId ?: return
        if (pendingTrace != openTrace.id) return
        if (!ThemeOpenScrollCoalescePolicy.shouldFlushDeferredHatMetadataViewUpdate(userHatOpenOverride)) {
            pendingHatMetadataEmitTraceId = null
            return
        }
        pendingHatMetadataEmitTraceId = null
        currentPage?.let { page ->
            if (page.id > 0) {
                _updateView.tryEmit(page)
            }
        }
    }

    private fun emitHatMetadataViewUpdate(traceId: String, page: ThemePage) {
        if (traceId != openTrace.id) return
        if (hasBlockingScrollPending() || renderSettledTraceId != traceId) {
            pendingHatMetadataEmitTraceId = traceId
            return
        }
        pendingHatMetadataEmitTraceId = null
        _updateView.tryEmit(page)
    }

    fun onRenderedTopicPage(page: ThemePage) {
        if (page.id <= 0) return
        val renderedPostIds = linkedSetOf<Int>()
        page.topicHatPost?.id?.takeIf { it > 0 }?.let { renderedPostIds.add(it) }
        page.posts
                .asSequence()
                .map { it.id }
                .filter { it > 0 }
                .forEach { renderedPostIds.add(it) }
        if (renderedPostIds.isEmpty()) return
        val pageNumber = page.pagination.current.takeIf { it > 0 }
        val explicitTargetPostId = explicitTargetPostIds[page]?.takeIf { it > 0 }
        if (explicitTargetPostId != null &&
                shouldCommitRenderedRead(page.id, explicitTargetPostId, page = pageNumber)) {
            themeUseCase.onRenderedTopicPosts(page.id, listOf(explicitTargetPostId))
            return
        }
        val pendingTargetPostId = pendingRenderedReadTarget?.postId?.takeIf { it > 0 }
        if (pendingTargetPostId != null && pendingTargetPostId in renderedPostIds &&
                shouldCommitRenderedRead(page.id, pendingTargetPostId, page = pageNumber)) {
            themeUseCase.onRenderedTopicPosts(page.id, renderedPostIds)
            return
        }
        if (shouldCommitRenderedRead(page.id, postId = null, page = pageNumber)) {
            themeUseCase.onRenderedTopicPosts(page.id, renderedPostIds)
            return
        }
        val lastRenderedPostId = renderedPostIds.lastOrNull()
        if (lastRenderedPostId != null && shouldCommitRenderedRead(page.id, lastRenderedPostId, page = pageNumber)) {
            themeUseCase.onRenderedTopicPosts(page.id, listOf(lastRenderedPostId))
        }
    }

    private fun renderedReadTargetFor(
            page: ThemePage,
            resolution: TopicOpenResolution?,
            navigationTarget: TopicOpenTarget?
    ): RenderedReadTarget? {
        if (_loadAction != ThemeLoadAction.Normal) return null
        if (page.id <= 0) return null
        navigationTarget?.let { target ->
            return when (target) {
                is TopicOpenTarget.ExplicitPost -> RenderedReadTarget(
                        topicId = page.id,
                        postId = target.postId.takeIf { it > 0 }
                                ?: explicitTargetPostIds[page]?.takeIf { it > 0 }
                                ?: page.anchorPostId?.toIntOrNull()?.takeIf { it > 0 },
                        page = page.pagination.current.takeIf { it > 0 }
                )
                is TopicOpenTarget.Unread -> {
                    val postId = (resolution?.resolvedPostId ?: explicitTargetPostIds[page])
                            ?.takeIf { it > 0 }
                            ?: ThemeApi.extractScrollPostIdFromFinalTopicUrl(page.url.orEmpty())?.toIntOrNull()?.takeIf { it > 0 }
                            ?: page.anchorPostId?.toIntOrNull()?.takeIf { it > 0 }
                            ?: page.anchor?.removePrefix("entry")?.toIntOrNull()?.takeIf { it > 0 }
                    RenderedReadTarget(page.id, postId = postId, page = page.pagination.current.takeIf { it > 0 })
                }
                is TopicOpenTarget.ExplicitPage -> RenderedReadTarget(
                        topicId = page.id,
                        page = page.pagination.current.takeIf { it > 0 }
                )
                is TopicOpenTarget.End,
                is TopicOpenTarget.BackRestore,
                is TopicOpenTarget.RefreshRestore -> null
                is TopicOpenTarget.Default -> renderedReadTargetFromResolution(page, resolution)
            }
        }
        return renderedReadTargetFromResolution(page, resolution)
    }

    private fun renderedReadTargetFromResolution(
            page: ThemePage,
            resolution: TopicOpenResolution?
    ): RenderedReadTarget? {
        resolution ?: return null
        return when (resolution.targetType) {
            TopicOpenTargetType.EXPLICIT_POST,
            TopicOpenTargetType.USER_ACTION,
            TopicOpenTargetType.SETTING_LAST_UNREAD,
            TopicOpenTargetType.READ_RESUME,
            TopicOpenTargetType.SERVER_UNREAD_FALLBACK -> {
                val postId = (resolution.resolvedPostId ?: explicitTargetPostIds[page])
                        ?.takeIf { it > 0 }
                        ?: ThemeApi.extractScrollPostIdFromFinalTopicUrl(page.url.orEmpty())?.toIntOrNull()?.takeIf { it > 0 }
                        ?: page.anchorPostId?.toIntOrNull()?.takeIf { it > 0 }
                        ?: page.anchor?.removePrefix("entry")?.toIntOrNull()?.takeIf { it > 0 }
                RenderedReadTarget(page.id, postId = postId, page = page.pagination.current.takeIf { it > 0 })
            }
            TopicOpenTargetType.EXPLICIT_PAGE,
            TopicOpenTargetType.SETTING_FIRST_PAGE -> RenderedReadTarget(
                    topicId = page.id,
                    page = page.pagination.current.takeIf { it > 0 }
            )
            TopicOpenTargetType.SAFE_FALLBACK -> null
        }
    }

    private fun shouldCommitRenderedRead(topicId: Int, postId: Int?, page: Int?): Boolean =
            pendingRenderedReadTarget
                    ?.let { pending ->
                        shouldCommitRenderedRead(
                                pending = pending,
                                topicId = topicId,
                                postId = postId,
                                page = page
                        )
                    }
                    ?: shouldCommitCurrentRenderedRead(topicId, postId, page)

    private fun shouldCommitCurrentRenderedRead(topicId: Int, postId: Int?, page: Int?): Boolean {
        if (topicId <= 0) return false
        val current = currentPage ?: return false
        if (current.id != topicId) return false
        if (page != null && page > 0 && current.pagination.current != page) return false
        return postId == null || postId > 0
    }

    override fun onFirstPageClick() {
        _uiEvents.tryEmit(ThemeUiEvent.FirstPage)
    }

    override fun onPrevPageClick() {
        _uiEvents.tryEmit(ThemeUiEvent.PrevPage)
    }

    override fun onNextPageClick() {
        _uiEvents.tryEmit(ThemeUiEvent.NextPage)
    }

    override fun onLastPageClick() {
        _uiEvents.tryEmit(ThemeUiEvent.LastPage)
    }

    override fun onSelectPageClick() {
        _uiEvents.tryEmit(ThemeUiEvent.SelectPage)
    }

    override fun onSelectPageInputClick() {
        _uiEvents.tryEmit(ThemeUiEvent.SelectPageInput)
    }

    override fun onInfiniteScrollRequest(direction: String) {
        if (shouldBlockHybridUntilInitialAnchorSettled()) {
            // Throttle: this fires on every infinite-scroll tick (~30ms) while the guard is armed and
            // floods logcat (50+ identical lines) — enough to trip Android's per-process LOG_FLOWCTRL
            // quota and DROP the genuinely useful lines (the INITIAL_ANCHOR completion diagnostics we
            // need). Log once per (trace, direction) block instead.
            val blockKey = "${openTrace.id}:$direction"
            if (blockKey != lastBlockedInfiniteLogKey) {
                lastBlockedInfiniteLogKey = blockKey
                Log.i(
                        ThemeUnreadHybridAnchorGuardPolicy.LOG_TAG,
                        "blocked_infinite direction=$direction reason=awaiting_anchor trace=$openTrace.id (throttled)"
                )
            }
            _uiEvents.tryEmit(
                    ThemeUiEvent.SetInfiniteState(direction, ThemeInfiniteScrollController.InfiniteState.IDLE.jsName, null)
            )
            return
        }
        lastBlockedInfiniteLogKey = null
        if (direction == "top" && isEndNavigationPending()) {
            if (BuildConfig.DEBUG) {
                FpdaDebugLog.log(
                        FpdaDebugLog.TAG_SMART_BUTTON,
                        "infinite_top_blocked",
                        mapOf(
                                "topicId" to currentPage?.id,
                                "loadAction" to _loadAction.toString(),
                                "pendingEnd" to pendingEndNavigation
                        )
                )
            }
            return
        }
        requestInfinitePage(direction)
    }

    override fun onVisiblePageChanged(pageNumber: Int) {
        if (!shouldAcceptVisiblePageUpdate(pageNumber)) return
        setVisiblePage(pageNumber)
    }

    override fun onInfiniteRetry(direction: String) {
        retryInfinitePage(direction)
    }

    override fun onUserMenuClick(postId: Int) {
        getPostById(postId)?.let { _uiEvents.tryEmit(ThemeUiEvent.ShowUserMenu(it)) }
    }

    override fun onReputationMenuClick(postId: Int) {
        getPostById(postId)?.let { _uiEvents.tryEmit(ThemeUiEvent.ShowReputationMenu(it)) }
    }

    override fun onPostMenuClick(postId: Int) {
        // Прогрев формы редактирования до клика по «Изменить»: BBCode и вложения будут
        // уже в warm-кэше, и EditPost откроется без паузы на загрузку с сервера.
        postEditCoordinator.kickWarmNetworkLoad(postId)
        getPostById(postId)?.let { _uiEvents.tryEmit(ThemeUiEvent.ShowPostMenu(it)) }
    }

    override fun toggleForumBlacklist(postId: Int) {
        val post = getPostById(postId) ?: return
        if (post.userId > 0 && post.userId == themeUseCase.authUserId()) {
            return
        }
        val user = ForumBlacklistedUser(post.userId, post.nick.orEmpty())
        viewModelScope.launch {
            val wasBlacklisted = themeUseCase.isForumBlacklisted(user.userId, user.nick)
            if (wasBlacklisted) {
                themeUseCase.removeForumBlacklistedUser(user)
            } else {
                themeUseCase.addForumBlacklistedUser(user)
            }
            _uiEvents.tryEmit(
                    ThemeUiEvent.ShowSnackbar(
                            context.getString(
                                    if (wasBlacklisted) {
                                        R.string.forum_blacklist_removed
                                    } else {
                                        R.string.forum_blacklist_added
                                    }
                            )
                    )
            )
            rerenderCurrentPageAfterForumBlacklistChange(postId)
        }
    }

    override fun onReportPostClick(postId: Int) {
        postActionHandler.showReportPost(postId)
    }

    override fun onReplyPostClick(postId: Int) {
        postActionHandler.replyPost(postId)
    }

    override fun onQuotePostClick(postId: Int, text: String, displayedDate: String?) {
        postActionHandler.quotePost(postId, text, displayedDate)
    }

    override fun onQuoteFullPostClick(postId: Int, displayedDate: String?) {
        postActionHandler.quoteFullPost(postId, displayedDate)
    }

    override fun onDeletePostClick(postId: Int) {
        postActionHandler.showDeletePost(postId)
    }

    override fun onEditPostClick(postId: Int) {
        postActionHandler.showEditPost(postId)
    }

    override fun onVotePostClick(postId: Int, type: Boolean) {
        postActionHandler.showVotePost(postId, type)
    }

    override fun onSpoilerCopyLinkClick(postId: Int, spoilNumber: String) {
        getPostById(postId)?.let { _uiEvents.tryEmit(ThemeUiEvent.OpenSpoilerLinkDialog(it, spoilNumber)) }
    }

    override fun onAnchorClick(postId: Int, name: String) {
        getPostById(postId)?.let { _uiEvents.tryEmit(ThemeUiEvent.OpenAnchorDialog(it, name)) }
    }

    override fun onOpenLink(url: String) {
        navigationUseCase.handleLink(url)
    }

    override fun onPollHeaderClick(bValue: Boolean) {
        currentPage?.let { it.isPollOpen = bValue }
        _uiEvents.tryEmit(ThemeUiEvent.UpdatePollOpenState(bValue))
    }

    override fun onHatHeaderClick(bValue: Boolean) {
        userHatOpenOverride = bValue
        currentPage?.let { it.isHatOpen = bValue }
        _uiEvents.tryEmit(ThemeUiEvent.UpdateHatOpenState(bValue))
    }

    override fun onInlineHatHeaderClick(topicId: Int, bValue: Boolean, persistPreference: Boolean) {
        if (persistPreference) {
            viewModelScope.launch { themeUseCase.setInlineHatOpened(topicId, bValue) }
        }
        currentPage?.takeIf { it.id == topicId }?.let { it.isInlineHatOpen = bValue }
        loadedPages.values
                .filter { it.id == topicId }
                .forEach { it.isInlineHatOpen = bValue }
    }

    override fun setHistoryBody(index: Int, body: String) {
        historyController.setHistoryBody(index, body)
    }

    override fun copyText(text: String) {
        interactionUseCase.copyText(text)
    }

    override fun toast(text: String) {
        //themeView?.toast(text)
        router.showSystemMessage(text)
    }

    override fun log(text: String) {
        _uiEvents.tryEmit(ThemeUiEvent.Log(text))
    }

    private val LOG_TAG = ThemeFragmentWeb::class.java.simpleName

    fun getThemeLoadTraceId(): String = openTrace.id

    fun onEmptyThemeHtmlDetected() {
        val page = currentPage
        if (page != null && page.id > 0 && page.pagination.all > 0) {
            scope.launch {
                if (retryLoadAfterEmptyTopicPage(page)) return@launch
            }
            return
        }
        _uiEvents.tryEmit(ThemeUiEvent.ShowError("Не удалось отобразить тему"))
    }

    private suspend fun retryLoadAfterEmptyTopicPage(page: ThemePage): Boolean {
        val reloadKey = "${page.id}:${page.pagination.current}"
        if (emptyTopicReloadKey == reloadKey && emptyTopicReloadAttempts >= 1) {
            _uiEvents.tryEmit(ThemeUiEvent.ShowError("Не удалось загрузить сообщения темы"))
            return true
        }
        emptyTopicReloadKey = reloadKey
        emptyTopicReloadAttempts++
        val perPage = page.pagination.perPage.coerceAtLeast(1)
        val st = (page.pagination.current - 1).coerceAtLeast(0) * perPage
        val url = buildCleanThemeUrl(page.id, st)
        Timber.w("Retrying topic load after empty posts key=$reloadKey url=$url")
        loadData(url, _loadAction)
        return true
    }

    private fun countHtmlOccurrences(html: String?, token: String): Int {
        if (html.isNullOrEmpty() || token.isEmpty()) return 0
        var count = 0
        var index = 0
        while (true) {
            index = html.indexOf(token, startIndex = index)
            if (index < 0) return count
            count++
            index += token.length
        }
    }

    /**
     * Ссылка на конкретный пост: view/act=findpost или короткий вид ?showtopic=&p= / &pid= (без act ответа/жалобы и т.д.).
     */
    private fun isFindPostNavigation(uri: Uri): Boolean {
        val view = uri.getQueryParameter("view")
        val act = uri.getQueryParameter("act")
        if (view == "findpost" || act == "findpost") return true
        val hasPid = !uri.getQueryParameter("pid").isNullOrBlank()
                || !uri.getQueryParameter("p").isNullOrBlank()
        if (!hasPid) return false
        if (act != null) {
            when (act.lowercase(Locale.ROOT)) {
                "post", "report", "qms", "fav", "zmod", "search" -> return false
            }
        }
        if (view != null && view != "findpost") return false
        return true
    }

    fun handleNewUrl(uri: Uri, linkSourceHref: String? = null) {
        val url = ArticleLinkResolver.resolveForNavigation(uri.toString()) ?: return
        if (BuildConfig.DEBUG) Timber.d("handle $url")
        val normalizedUri = Uri.parse(url)
        try {
            if (checkIsPoll(url)) {
                return
            }
            if (handleForumNavigation(normalizedUri, url)) {
                        return
                    }
            if (handleImageNavigation(url, linkSourceHref)) {
                return
            }
        } catch (ex: Exception) {
            AppMetrica.reportError("${ex.message ?: ex.toString()}; uri $normalizedUri", ex)
        }
        navigationUseCase.handleLink(url)
    }

    private fun handleForumNavigation(uri: Uri, url: String): Boolean {
        if (!SiteUrls.isSiteHost(uri.host)) {
            return false
        }
        if (uri.pathSegments.getOrNull(0) != "forum") {
            return false
        }

        val showTopicParam = uri.getQueryParameter("showtopic")
        if (BuildConfig.DEBUG) Timber.d("param showtopic: $showTopicParam")

        val isCrossTopicOpen = showTopicParam != null &&
                showTopicParam != Uri.parse(themeUrl).getQueryParameter("showtopic")
        val isFindPost = isFindPostNavigation(uri)
        // R-02: a single user gesture can reach handleForumNavigation via both
        // WebViewClient.handleUri and WebViewClient.onPageStarted. Claim the gesture once so a
        // single tap cannot double-open a tab / double-push history. Distinct gestures (each
        // preceded by a fresh source-anchor capture) and fresh loadData windows still dispatch.
        if ((isCrossTopicOpen || isFindPost) && !navigationGestureGuard.tryClaimDispatch()) {
            NavBackstackTrace.log(
                    event = "nav_dispatch_suppressed_same_gesture",
                    navigator = "ThemeViewModel",
                    reason = if (isCrossTopicOpen) "cross_topic" else "findpost"
            )
            if (BuildConfig.DEBUG) {
                Timber.d("handleForumNavigation: suppressed duplicate dispatch for url=$url")
            }
            return true
        }

        if (isCrossTopicOpen) {
            setTopicOpenIntent(TopicOpenIntentClassifier.freshIntentForSource(lastOpenSourceScreen))
            // B-01 (lower-risk option): BEFORE opening Topic B in a new tab, durably capture
            // Topic A's clicked source-anchor / current position as a native back snapshot.
            // This makes Topic A's restore robust independent of the JS source-anchor TTL,
            // without touching the new-tab behavior (the high-risk option). When no source
            // anchor is available we emit a diagnostic so the loss is observable in logs.
            captureBackSnapshotBeforeCrossTopicOpen(url)
            // Fix E: push a new top-level Theme tab so system back works even
            // if the in-tab history is corrupted. Fall back to in-tab loadUrl
            // if the router refused (e.g. the URL did not yield a topic id).
            val opened = router.tryOpenTopicInNewTab(url, sourceScreen = "in_topic_link")
            if (!opened) {
                loadUrl(url, sourceScreen = "in_topic_link")
            }
            return true
        }

        if (isFindPost) {
            handleFindPostNavigation(uri, url)
            return true
        }

        return false
    }

    /**
     * B-01: durably capture Topic A's position right before a cross-topic link opens Topic B
     * in a new tab. The in-tab history controller of Topic A does not record the cross-topic
     * transition, so returning to Topic A's tab relies on this native snapshot (independent of
     * the JS source-anchor TTL) rather than on the JS anchor still being live.
     */
    private fun captureBackSnapshotBeforeCrossTopicOpen(targetUrl: String) {
        val sourcePage = getRefreshCapturePageInstance()
        if (sourcePage == null || sourcePage.id <= 0) {
            NavBackstackTrace.log(
                    event = "source_anchor_missing",
                    navigator = "ThemeViewModel",
                    reason = "cross_topic_no_source_page"
            )
            Log.i(THEME_HISTORY_TAG, "source_anchor_missing target=$targetUrl reason=no_source_page")
            return
        }
        val pendingAnchor = pendingHistorySourceAnchor?.takeIf { sourceAnchorAppliesTo(sourcePage, it) }
        if (pendingAnchor != null) {
            applyLinkSourceAnchorSnapshot(sourcePage, pendingAnchor, "crossTopicOpen")
        } else if (sourcePage.anchorPostId.isNullOrBlank()) {
            NavBackstackTrace.log(
                    event = "source_anchor_missing",
                    navigator = "ThemeViewModel",
                    topicId = sourcePage.id,
                    reason = "cross_topic_no_anchor"
            )
            Log.i(
                    THEME_HISTORY_TAG,
                    "source_anchor_missing topic=${sourcePage.id} st=${sourcePage.st} target=$targetUrl reason=no_anchor"
            )
        }
        // Geometry-consistency back-snapshot guard (device log 25_06-22-18-38, cross-topic BACK lands
        // on the wrong post 143860995 instead of source 143876380). When the page carries an
        // authoritative explicit-open anchor (143876380) but the user tapped the link from a DIFFERENT
        // visible post (143860995 at y=11810), `sourcePage.scrollY`/`scrollRatio` still describe that
        // visible post, while [applyLinkSourceAnchorSnapshot] kept `anchorPostId=143876380` (geometry
        // rejected, early return). Re-capturing here would pair the authoritative post id with the
        // visible post's pixel offset, so BACK loads `#entry143876380` but scrolls to y=11810 → the
        // user lands on 143860995. Skip the overwrite: the existing back snapshot (captured at the
        // last render that was actually AT the authoritative post) holds the only self-consistent
        // geometry. When the tapped source post EQUALS the authoritative anchor (normal cross-topic
        // case), the guard is false and the snapshot is captured with genuine geometry as before.
        val authoritativeAnchor = sourcePage.authoritativeAnchorPostId?.takeIf { it.isNotBlank() }
        val tappedSourcePostId = pendingAnchor?.postId
        val rejectMismatchedGeometry = ThemeAuthoritativeAnchorPolicy
                .shouldRejectAuthoritativeMismatchedBackSnapshot(
                        authoritativeAnchorPostId = authoritativeAnchor,
                        candidateVisiblePostId = tappedSourcePostId,
                )
        if (rejectMismatchedGeometry) {
            Log.i(
                    THEME_HISTORY_TAG,
                    "crossTopicOpen kept authoritative geometry topic=${sourcePage.id} st=${sourcePage.st} authoritativePostId=$authoritativeAnchor ignoredVisiblePost=$tappedSourcePostId ignoredY=${sourcePage.scrollY} target=$targetUrl"
            )
        } else {
            // Always persist the best-known position so back-restore can rebuild #entry from a
            // native snapshot even after the JS TTL window closes during a long read of Topic B.
            captureNativeBackSnapshot(sourcePage, "crossTopicOpen")
        }
        // C-fix: the cross-topic link opens Topic B in a NEW tab without pushing an in-tab entry,
        // so the source page (sourcePage) stays the in-tab history TOP. Arm the return guard so
        // the first back after returning to this tab restores THIS exact page (st + #entry) rather
        // than popping past it onto an earlier page of the same topic (log: landed on st=1260/page
        // 64 instead of st=1180/post 143876380).
        historyController.armCrossTopicReturnGuard(sourcePage.id, sourcePage.st)
    }

    private fun handleFindPostNavigation(uri: Uri, url: String) {
        val postId = extractPostId(uri)
        if (BuildConfig.DEBUG) Timber.d("param postId: $postId")

        if (postId != null && getPostById(Integer.parseInt(postId.trim { it <= ' ' })) != null) {
            val elem = extractScrollElement(url)
            val finalAnchor = buildFinalAnchor(elem, postId)

            currentPage?.addAnchor(finalAnchor)

            _uiEvents.tryEmit(ThemeUiEvent.ScrollToAnchor(finalAnchor))
        } else {
            loadUrl(url, sourceScreen = "in_topic_link")
        }
    }

    private fun extractPostId(uri: Uri): String? {
        var postId = uri.getQueryParameter("pid")
        if (postId == null) {
                            postId = uri.getQueryParameter("p")
        }
        if (BuildConfig.DEBUG) Timber.d("param pid|p: $postId")

                        if (postId != null) {
                            postId = postId.replace("[^\\d][\\s\\S]*?".toRegex(), "")
                        }
        return postId
    }

    private fun extractScrollElement(url: String): String? {
                            val matcher = ThemeApi.elemToScrollPattern.matcher(url)
                            var elem: String? = null
                            while (matcher.find()) {
                                elem = matcher.group(1)
                            }
        return elem
    }

    private fun buildFinalAnchor(elem: String?, postId: String): String {
        if (BuildConfig.DEBUG) Timber.d(" scroll to $postId : $elem")
        return (if (elem == null) "entry" else "") + (if (elem != null) elem else postId)
    }

    private fun handleImageNavigation(url: String, linkSourceHref: String? = null): Boolean {
        val targetImageUrl = FourPdaImageUrls.resolveViewerUrl(url, linkSourceHref)
        if (!FourPdaImageUrls.isViewableInViewer(targetImageUrl)) {
            return false
        }

        currentPage?.let { page ->
            for (post in page.posts) {
                for (image in post.attachImages) {
                    if (normalizeThemeImageUrl(image.first) == targetImageUrl) {
                        val imageList = ArrayList(
                                post.attachImages.map { FourPdaImageUrls.resolveViewerUrl(it.first) }
                        )
                        skipNextUnreadJumpAfterTabSwitch = true
                        _uiEvents.tryEmit(ThemeUiEvent.SaveScrollYForImageViewer)
                        ImageViewerActivity.startActivity(context, imageList, post.attachImages.indexOf(image))
                        return true
                    }
                }
            }
        }
        skipNextUnreadJumpAfterTabSwitch = true
        _uiEvents.tryEmit(ThemeUiEvent.SaveScrollYForImageViewer)
        ImageViewerActivity.startActivity(context, arrayListOf(targetImageUrl), 0)
        return true
    }

    private fun normalizeThemeImageUrl(url: String): String = FourPdaImageUrls.normalizeAbsolute(url)

    private fun checkIsPoll(url: String): Boolean {
        val page = currentPage ?: return false
        val stOffset = page.pagination.current * page.pagination.perPage
        val rewritten = ThemePollUrlPolicy.rewriteAddPoll(url, page.id, stOffset)
                ?: return false
        loadUrl(rewritten)
        return true
    }


    fun onClickDeleteInFav() {
        currentPage?.let { scope.launch { _uiEvents.emit(ThemeUiEvent.ShowDeleteInFavDialog(it)) } }
    }

    fun onClickAddInFav() {
        currentPage?.let { scope.launch { _uiEvents.emit(ThemeUiEvent.ShowAddInFavDialog(it)) } }
    }

    fun onBackPressed(): Boolean {
        Log.i(
                THEME_HISTORY_TAG,
                "vm back setting=$currentTopicBackBehavior historySize=${historyController.size} canGoBack=${historyController.canGoBack()} currentTopic=${currentPage?.id} currentSt=${currentPage?.st} currentPost=${currentPage?.anchorPostId}"
        )
            currentPage?.let {
                if (it.anchors.size > 1) {
                    it.removeAnchor()
                scope.launch { _uiEvents.emit(ThemeUiEvent.ScrollToAnchor(it.anchor.orEmpty())) }
                    return true
                }
            }
        if (currentTopicBackBehavior == AppPreferences.Main.TopicBackBehavior.HISTORY && historyController.canGoBack()) {
            backPage()
            return true
        }
        return false
    }


    override fun openProfile(postId: Int) {
        postActionHandler.openProfile(postId)
    }

    override fun openQms(postId: Int) {
        postActionHandler.openQms(postId)
    }

    override fun openSearchUserTopic(postId: Int) {
        postActionHandler.openSearchUserTopic(postId)
    }

    override fun openSearchInTopic(postId: Int) {
        postActionHandler.openSearchInTopic(postId)
    }

    override fun openSearchUserMessages(postId: Int) {
        postActionHandler.openSearchUserMessages(postId)
    }

    override fun onChangeReputationClick(postId: Int, type: Boolean) {
        postActionHandler.showChangeReputation(postId, type)
    }

    override fun changeReputation(postId: Int, type: Boolean, message: String) {
        postActionHandler.changeReputation(postId, type, message)
    }

    override fun votePost(postId: Int, type: Boolean) {
        postActionHandler.votePost(postId, type)
    }

    override fun openReputationHistory(postId: Int) {
        postActionHandler.openReputationHistory(postId)
    }

    override fun quoteFromBuffer(postId: Int) {
        postActionHandler.quoteFromBuffer(postId)
    }

    override fun reportPost(postId: Int, message: String) {
        postActionHandler.reportPost(postId, message)
    }

    override fun deletePost(postId: Int) {
        postActionHandler.deletePost(postId)
    }

    override fun createNote(postId: Int) {
        postActionHandler.createNote(postId)
    }

    override fun copyPostLink(postId: Int) {
        postActionHandler.copyPostLink(postId)
    }

    override fun sharePostLink(postId: Int) {
        postActionHandler.sharePostLink(postId)
    }

    override fun copyAnchorLink(postId: Int, name: String) {
        postActionHandler.copyAnchorLink(postId, name)
    }

    override fun copySpoilerLink(postId: Int, spoilNumber: String) {
        postActionHandler.copySpoilerLink(postId, spoilNumber)
    }


    private enum class InfiniteDirection(val jsName: String) {
        TOP("top"),
        BOTTOM("bottom");

        companion object {
            fun from(value: String): InfiniteDirection? = values().firstOrNull {
                it.jsName.equals(value, ignoreCase = true)
            }
        }
    }

    private enum class InfiniteState(val jsName: String) {
        IDLE("idle"),
        LOADING("loading"),
        ERROR("error")
    }

    private enum class RefreshRestoreMode {
        ANCHOR,
        BOTTOM,
        TARGET_POST,
        NONE;

        companion object {
            fun from(value: String): RefreshRestoreMode {
                return values().firstOrNull { it.name.equals(value, ignoreCase = true) } ?: ANCHOR
            }
        }
    }

    private data class RefreshRequest(
            val id: String,
            val source: String,
            val restoreMode: RefreshRestoreMode,
            val topicId: Int,
            val pageSt: Int,
            val targetPageNumber: Int? = null,
            val targetUrl: String = "",
            val snapshotScrollY: Int? = null,
            val snapshotAnchorPostId: String? = null,
            val snapshotAnchorOffsetTop: Double? = null,
            val snapshotScrollRatio: Double? = null,
            val snapshotWasNearBottom: Boolean? = null
    ) {
        fun matches(page: ThemePage): Boolean =
                TopicOpenScrollRestorePolicy.refreshRestorePageMatches(
                        requestTopicId = topicId,
                        requestPageSt = pageSt,
                        requestTargetPageNumber = targetPageNumber,
                        pageTopicId = page.id,
                        pageSt = page.st,
                        pageNumber = page.pagination.current
                )

        fun withSnapshot(
                scrollY: Int,
                anchorPostId: String?,
                anchorOffsetTop: Double?,
                scrollRatio: Double?,
                wasNearBottom: Boolean
        ) = copy(
                snapshotScrollY = scrollY,
                snapshotAnchorPostId = anchorPostId,
                snapshotAnchorOffsetTop = anchorOffsetTop,
                snapshotScrollRatio = scrollRatio,
                snapshotWasNearBottom = wasNearBottom
        )
    }

    private fun seedRefreshSnapshotFromPage(page: ThemePage, request: RefreshRequest) {
        val anchor = page.anchorPostId
                ?: page.anchor?.removePrefix("entry")?.takeIf { it.isNotEmpty() }
        val hasSnapshot = page.scrollY > 0 ||
                !anchor.isNullOrBlank() ||
                page.scrollRatio != null ||
                page.wasNearBottom
        if (!hasSnapshot) return
        pendingRefreshRequest = request.withSnapshot(
                scrollY = page.scrollY,
                anchorPostId = anchor,
                anchorOffsetTop = page.anchorOffsetTop,
                scrollRatio = page.scrollRatio,
                wasNearBottom = page.wasNearBottom
        )
        if (BuildConfig.DEBUG) {
            Log.i(
                    REFRESH_SCROLL_TAG,
                    "vm refreshRequest seed id=${request.id} scrollY=${page.scrollY} anchor=$anchor ratio=${page.scrollRatio} bottom=${page.wasNearBottom}"
            )
        }
    }

    private fun applyPendingRefreshRestoreRequest(page: ThemePage, previousPage: ThemePage?) {
        val request = pendingRefreshRequest ?: return
        if (!request.matches(page)) {
            if (request.topicId > 0 && page.id == request.topicId) {
                if (BuildConfig.DEBUG) {
                    Log.w(
                            REFRESH_SCROLL_TAG,
                            "vm refreshRequest relaxMatch id=${request.id} pageSt=${page.st} requestSt=${request.pageSt} page=${page.pagination.current} requestPage=${request.targetPageNumber}"
                    )
                }
            } else {
                if (BuildConfig.DEBUG) {
                    Log.i(
                            REFRESH_SCROLL_TAG,
                            "vm refreshRequest drop id=${request.id} source=${request.source} mode=${request.restoreMode} pageTopic=${page.id} pageSt=${page.st} requestTopic=${request.topicId} requestSt=${request.pageSt}"
                    )
                }
                pendingRefreshRequest = null
                return
            }
        }
        val snapshotWasNearBottom = request.snapshotWasNearBottom
                ?: previousPage?.wasNearBottom
                ?: false
        val snapshotScrollRatio = request.snapshotScrollRatio ?: previousPage?.scrollRatio
        val effectiveModeName = ThemeRefreshScrollRestorePolicy.effectiveRestoreMode(
                requestedMode = request.restoreMode.name,
                wasNearBottom = snapshotWasNearBottom,
                scrollRatio = snapshotScrollRatio,
                page = page,
                scrollMode = currentTopicScrollMode
        )
        val effectiveMode = RefreshRestoreMode.from(effectiveModeName)
        page.refreshRestoreId = request.id
        page.refreshRestoreMode = effectiveMode.name
        page.refreshRestoreSource = request.source
        if (effectiveMode == RefreshRestoreMode.BOTTOM) {
            page.scrollY = request.snapshotScrollY ?: Int.MAX_VALUE
            page.anchorPostId = null
            page.anchorOffsetTop = null
            page.scrollRatio = 1.0
            page.wasNearBottom = true
        } else {
            page.scrollY = request.snapshotScrollY ?: previousPage?.scrollY ?: page.scrollY
            page.anchorOffsetTop = request.snapshotAnchorOffsetTop ?: previousPage?.anchorOffsetTop ?: page.anchorOffsetTop
            page.scrollRatio = snapshotScrollRatio ?: page.scrollRatio
            page.wasNearBottom = snapshotWasNearBottom
            val rawAnchor = request.snapshotAnchorPostId ?: previousPage?.anchorPostId ?: page.anchorPostId
            page.anchorPostId = ThemeRefreshScrollRestorePolicy.resolveRefreshAnchorPostId(
                    page = page,
                    anchorPostId = rawAnchor,
                    wasNearBottom = snapshotWasNearBottom,
                    scrollRatio = snapshotScrollRatio,
                    scrollMode = currentTopicScrollMode
            )
        }
        if (BuildConfig.DEBUG) {
            Log.i(
                    REFRESH_SCROLL_TAG,
                    "vm refreshRequest apply id=${request.id} source=${request.source} mode=${request.restoreMode} effectiveMode=$effectiveMode trace=$openTrace.id page=${page.pagination.current}/${page.pagination.all} st=${page.st} snapshotY=${request.snapshotScrollY} snapshotAnchor=${request.snapshotAnchorPostId} anchor=${page.anchorPostId} offset=${page.anchorOffsetTop} ratio=${page.scrollRatio} bottom=${page.wasNearBottom}"
            )
        }
    }

    private fun clearScrollRestoreForUnreadOpen(page: ThemePage) {
        if (_loadAction != ThemeLoadAction.Normal) return
        if (!themeUrl.contains("view=getnewpost", ignoreCase = true)) return
        if (!savedScrollRestoreAllowedForCurrentOpen()) {
            clearScrollRestoreFieldsPreservingServerAnchor(page)
            return
        }
        if (!page.hasUnreadTarget && currentTopicOpenTarget != AppPreferences.Main.TopicOpenTarget.LAST_UNREAD) return
        clearScrollRestoreFieldsPreservingServerAnchor(page)
    }

    private fun clearScrollRestoreFieldsPreservingServerAnchor(page: ThemePage) {
        syncServerAnchorPostId(page)
        page.scrollY = 0
        page.anchorOffsetTop = null
        page.scrollRatio = null
        page.wasNearBottom = false
        page.refreshRestoreId = null
        page.refreshRestoreMode = null
        page.refreshRestoreSource = null
    }

    private fun syncServerAnchorPostId(page: ThemePage) {
        if (page.anchorPostId.isNullOrBlank()) {
            page.anchorPostId = page.anchor?.removePrefix("entry")?.takeIf { it.isNotBlank() }
        }
    }

    private fun clearAllScrollRestoreFields(page: ThemePage) {
        page.scrollY = 0
        page.anchorPostId = null
        page.anchorOffsetTop = null
        page.scrollRatio = null
        page.wasNearBottom = false
        page.refreshRestoreId = null
        page.refreshRestoreMode = null
        page.refreshRestoreSource = null
    }

    private fun applyBackHistoryRestoreSnapshot(page: ThemePage) {
        if (_loadAction != ThemeLoadAction.Back) return
        val snapshot = (activeNavigationTarget as? TopicOpenTarget.BackRestore)?.snapshot?.takeIf { it.isUsable() }
                ?: historyController.consumeBackSnapshot(page.id, page.st)?.takeIf { it.isUsable() }
                ?: historyController.peekBackSnapshot(page.id, page.st)?.takeIf { it.isUsable() }
                // By-post fallback: in HYBRID the loaded page.st can differ from the st the snapshot
                // was keyed on, so the exact-key lookups above miss; recover it by the anchor post id
                // the restore URL was built with (mirrors [buildBackRestoreUrl]'s st reconciliation).
                ?: historyController.findUsableBackSnapshotByPost(page.id, page.anchorPostId)
        if (snapshot != null) {
            page.scrollY = snapshot.scrollOffset
            page.anchorPostId = snapshot.visiblePostId ?: page.anchorPostId
            page.scrollRatio = snapshot.scrollRatio ?: page.scrollRatio
            page.wasNearBottom = snapshot.wasNearBottom
            // HYBRID infinite-scroll reanchor fix (device log 25_06-23-04-44, cross-topic BACK to the
            // source post 143876380 lands on the neighbor 143860995). The back snapshot carries a
            // post id + page scrollY but NO trustworthy intra-post offsetTop — the only offsetTop on
            // the page is the stale CLICK-TIME value (e.g. 278.44, captured when the user tapped the
            // link with 143876380 scrolled to the lower part of the viewport, ratio=0.759). The JS
            // STEP-3 restore subtracts that as an intra-post offset, so it places 143876380 LOW on
            // screen; the post ABOVE it (143860995) becomes the topmost-visible post, and the STEP-4
            // HYBRID top-prepend then pins to that neighbor (`applyInfiniteEnd ... pinned=143860995`)
            // — the scroll drifts and the user lands on 143860995, not the source post. A back-restore
            // must TOP-ALIGN its anchor post (offset 0, like a findpost open and like the working
            // `offset=null` back path) so the source post is the topmost-visible post and the prepend
            // pins it. Drop the stale click-time offset; the snapshot never carried a real one.
            if (ThemeBackRestoreUrlPolicy.shouldTopAlignBackRestoreAnchor(snapshot.visiblePostId)) {
                page.anchorOffsetTop = null
            }
            if (!page.anchorPostId.isNullOrBlank() || page.scrollY > 0 || page.scrollRatio != null || page.wasNearBottom) {
                page.refreshRestoreId = UUID.randomUUID().toString().replace("-", "").take(8)
                page.refreshRestoreMode = "ANCHOR"
                page.refreshRestoreSource = "historyBackSnapshot"
            }
            logBackRestoreApplied(page, source = "historyBackSnapshot")
            return
        }
        val historyPage = historyController.currentPage
        if (historyPage == null || historyPage.id != page.id || historyPage.st != page.st) {
            applyDurableReturnRestore(page)
            return
        }
        page.scrollY = historyPage.scrollY
        page.anchorPostId = historyPage.anchorPostId ?: page.anchorPostId
        page.anchorOffsetTop = historyPage.anchorOffsetTop ?: page.anchorOffsetTop
        page.scrollRatio = historyPage.scrollRatio ?: page.scrollRatio
        page.wasNearBottom = historyPage.wasNearBottom
        // Same HYBRID infinite-scroll reanchor fix as the snapshot branch above (device log
        // 25_06-23-04-44): a back restore with a real anchor post must top-align it so the prepend
        // pins the source post, not a neighbor that happened to be above it on screen.
        if (ThemeBackRestoreUrlPolicy.shouldTopAlignBackRestoreAnchor(page.anchorPostId)) {
            page.anchorOffsetTop = null
        }
        if (!page.anchorPostId.isNullOrBlank() || page.scrollY > 0 || page.scrollRatio != null || page.wasNearBottom) {
            page.refreshRestoreId = historyPage.refreshRestoreId
                    ?: UUID.randomUUID().toString().replace("-", "").take(8)
            page.refreshRestoreMode = "ANCHOR"
            page.refreshRestoreSource = "historyBack"
        }
        logBackRestoreApplied(page, source = "historyBack")
    }

    /**
     * Last-resort BACK restore from the app-scoped, cross-tab [returnPositionStore] (device log
     * 26_06-10-34, cross-topic BACK to page-top 143861523). Reached only when BOTH the per-tab back
     * snapshots and the per-tab history are gone — the documented "source topic's tab was reused for
     * the cross-topic open, so `resetTransientStateForNewTopic -> clear()` wiped them" case. The
     * return store survives the tab reset, so it still knows the precise post the user left on.
     *
     * Only engages when the restore URL already targets that durable post (i.e. [buildBackRestoreUrl]
     * recovered the same #entry from the store): we just top-align it and arm the restore fields so
     * the HYBRID prepend pins the source post instead of letting the page settle at the page-top post.
     * Never overrides a real per-tab snapshot/history (those returned earlier).
     */
    private fun applyDurableReturnRestore(page: ThemePage) {
        val durable = returnPositionStore.peek(page.id)?.takeIf { !it.postId.isNullOrBlank() } ?: return
        if (page.st != durable.pageSt) return
        val anchor = page.anchorPostId?.removePrefix("entry")?.takeIf { it.isNotBlank() }
        if (anchor != durable.postId) return
        if (durable.scrollY > 0) page.scrollY = durable.scrollY
        // Top-align the recovered anchor post (same HYBRID reanchor fix as the snapshot branch) so the
        // source post is the topmost-visible post and the top-prepend pins it, not a neighbor.
        if (ThemeBackRestoreUrlPolicy.shouldTopAlignBackRestoreAnchor(page.anchorPostId)) {
            page.anchorOffsetTop = null
        }
        page.refreshRestoreId = UUID.randomUUID().toString().replace("-", "").take(8)
        page.refreshRestoreMode = "ANCHOR"
        page.refreshRestoreSource = "returnPositionStore"
        logBackRestoreApplied(page, source = "returnPositionStore")
    }

    private fun logBackRestoreApplied(page: ThemePage, source: String) {
        forpdateam.ru.forpda.diagnostic.ReadStateTrace.log(
                event = "back_restore_applied",
                topicId = page.id,
                pageSt = page.st,
                scrollY = page.scrollY,
                anchorPostId = page.anchorPostId,
                allowedAsNavTarget = true,
                source = source,
                reason = "applyBackHistoryRestoreSnapshot"
        )
        if (BuildConfig.DEBUG) {
            Log.i(
                    REFRESH_SCROLL_TAG,
                    "vm backRestoreSnapshot trace=$openTrace.id topic=${page.id} st=${page.st} scrollY=${page.scrollY} anchor=${page.anchorPostId} offset=${page.anchorOffsetTop} ratio=${page.scrollRatio} bottom=${page.wasNearBottom} restoreId=${page.refreshRestoreId}"
            )
            Log.i(
                    THEME_HISTORY_TAG,
                    "new ThemePage restore fields action=$_loadAction topic=${page.id} st=${page.st} page=${page.pagination.current} url=${page.url} postId=${page.anchorPostId} offset=${page.anchorOffsetTop} y=${page.scrollY} ratio=${page.scrollRatio} bottom=${page.wasNearBottom} restoreId=${page.refreshRestoreId}"
            )
        }
    }

    private var deferredFavoriteSyncJob: Job? = null
    private var hybridPrefetchJob: Job? = null

    private fun scheduleDeferredFavoriteLastPostSync(page: ThemePage, traceId: String) {
        if (page.id <= 0) return
        deferredFavoriteSyncJob?.cancel()
        deferredFavoriteSyncJob = scope.launch {
            val startedAt = SystemClock.uptimeMillis()
            themeUseCase.syncFavoriteLastPost(page)
            if (BuildConfig.DEBUG && traceId == openTrace.id) {
                Log.i(
                        REFRESH_SCROLL_TAG,
                        "vm favoriteSyncDeferred trace=$traceId topic=${page.id} ms=${SystemClock.uptimeMillis() - startedAt}"
                )
            }
        }
    }

    private fun prefetchAdjacentHybridPageIfNeeded(page: ThemePage, traceId: String) {
        if (_loadAction != ThemeLoadAction.Normal) return
        if (TopicUnreadOpenPolicy.shouldSuppressHybridPreload(activeOpenSessionKind)) return
        if (currentTopicScrollMode != AppPreferences.Main.TopicScrollMode.HYBRID) return
        if (page.id <= 0 || page.pagination.current != 1 || page.pagination.all <= 1) return
        if (loadedPages.containsKey(2)) return
        hybridPrefetchJob?.cancel()
        val topicId = page.id
        val perPage = page.pagination.perPage.coerceAtLeast(1)
        val hatOpen = userHatOpenOverride ?: false
        val pollOpen = page.isPollOpen
        val prefetchUrl = buildCleanThemeUrl(topicId, perPage)
        hybridPrefetchJob = scope.launch {
            when (val result = themeUseCase.loadTheme(prefetchUrl, hatOpen, pollOpen)) {
                is ThemeUseCase.LoadResult.Success -> {
                    val loaded = result.page
                    if (traceId != openTrace.id || loaded.id != topicId) return@launch
                    if (loaded.pagination.current != 2 || loadedPages.containsKey(2)) return@launch
                    loadedPages[2] = loaded
                    if (BuildConfig.DEBUG) {
                        Log.i(
                                "HybridScroll",
                                "prefetch page=2 topic=$topicId posts=${loaded.posts.size} trace=$traceId"
                        )
                    }
                }
                is ThemeUseCase.LoadResult.Error -> Unit
            }
        }
    }

    private fun smartRefreshFallback(reason: String, previousPage: ThemePage?, newPage: ThemePage): SmartPostsPatch? {
        if (BuildConfig.DEBUG && pendingRefreshRequest != null && _loadAction == ThemeLoadAction.Refresh) {
            Log.i(
                    REFRESH_SCROLL_TAG,
                    "smartRefresh decision=fallback reason=$reason source=${pendingRefreshRequest?.source} oldTopic=${previousPage?.id} newTopic=${newPage.id} oldPage=${previousPage?.pagination?.current} newPage=${newPage.pagination.current}"
            )
        }
        return null
    }

    private fun logThemeQuote(message: String, vararg args: Any?) {
        if (BuildConfig.DEBUG) {
            Timber.tag(THEME_QUOTE_TAG).d(message, *args)
        }
    }

    internal companion object {
        /**
         * List unread hints are bound to the first [loadUrl] in an empty tab. Any later navigation
         * without explicit [listHints] must drop them (pagination, navigator reuse, poll toggles).
         */
        fun shouldClearListOpenHints(hasLoadedPage: Boolean): Boolean = hasLoadedPage

        fun needsTopicSwitchReset(incomingTopicId: Int?, previouslyLoadedTopicId: Int?): Boolean {
            if (incomingTopicId == null || incomingTopicId <= 0) return false
            if (previouslyLoadedTopicId == null) return false
            return incomingTopicId != previouslyLoadedTopicId
        }

        /** Internal topic links must keep [ThemeHistoryController] so BACK returns to link origin. */
        fun shouldPreserveHistoryOnCrossTopicOpen(sourceScreen: String): Boolean =
                sourceScreen == "in_topic_link"

        fun isFreshSameTopicOpen(
                incomingTopicId: Int?,
                previouslyLoadedTopicId: Int?,
                hasLoadedPage: Boolean,
                isFreshOpen: Boolean = true
        ): Boolean {
            if (!isFreshOpen) return false
            if (!hasLoadedPage) return false
            if (incomingTopicId == null || incomingTopicId <= 0) return false
            if (previouslyLoadedTopicId == null || previouslyLoadedTopicId <= 0) return false
            return incomingTopicId == previouslyLoadedTopicId
        }

        /** Cross-topic navigation must reset toolbar chrome before the network response arrives. */
        fun shouldClearToolbarOnTopicSwitch(incomingTopicId: Int?, previouslyLoadedTopicId: Int?): Boolean =
                needsTopicSwitchReset(incomingTopicId, previouslyLoadedTopicId)

        /**
         * Reject toolbar updates for a page that does not match the URL currently being loaded
         * (e.g. buffered [updateView] from the previous topic after [loadUrl]).
         */
        fun shouldApplyToolbarPage(
                pageTopicId: Int,
                targetTopicId: Int?,
                loadedTopicId: Int?
        ): Boolean {
            if (pageTopicId <= 0) return true
            if (targetTopicId == null || targetTopicId <= 0) return true
            if (pageTopicId == targetTopicId) return true
            return loadedTopicId != null && loadedTopicId == pageTopicId
        }

        fun isCrossTopicLoad(requestedTopicId: Int?, loadedTopicId: Int?): Boolean {
            if (requestedTopicId == null || requestedTopicId <= 0) return false
            if (loadedTopicId == null || loadedTopicId <= 0) return false
            return requestedTopicId != loadedTopicId
        }

        /** Mirrors [ThemeViewModel.loadData] hat-open resolution after optional session reset. */
        fun hatOpenForLoad(userHatOpenOverride: Boolean?): Boolean = userHatOpenOverride ?: false

        fun shouldAcceptVisiblePageUpdate(
                pageNumber: Int,
                loadedPages: Set<Int>,
                loadInFlight: Boolean
        ): Boolean {
            if (loadInFlight) return false
            if (pageNumber <= 0) return false
            if (loadedPages.isEmpty()) return true
            return loadedPages.contains(pageNumber)
        }

        private fun shouldCommitRenderedRead(
                pending: RenderedReadTarget?,
                topicId: Int,
                postId: Int?,
                page: Int?
        ): Boolean {
            return shouldCommitRenderedRead(
                    pendingTopicId = pending?.topicId,
                    pendingPostId = pending?.postId,
                    pendingPage = pending?.page,
                    topicId = topicId,
                    postId = postId,
                    page = page
            )
        }

        fun shouldCommitRenderedRead(
                pendingTopicId: Int?,
                pendingPostId: Int?,
                pendingPage: Int?,
                topicId: Int,
                postId: Int?,
                page: Int?
        ): Boolean {
            if (topicId <= 0 || pendingTopicId == null || pendingTopicId != topicId) return false
            if (pendingPostId != null && pendingPostId > 0) {
                return postId == pendingPostId
            }
            if (pendingPage != null && pendingPage > 0 && page != null && page > 0) {
                return page == pendingPage
            }
            return postId != null && postId > 0
        }
    }

    private fun isCrossTopicLoad(requestedTopicId: Int?, loadedTopicId: Int?): Boolean =
            Companion.isCrossTopicLoad(requestedTopicId, loadedTopicId)

    private fun shouldAcceptVisiblePageUpdate(pageNumber: Int): Boolean =
            Companion.shouldAcceptVisiblePageUpdate(
                    pageNumber = pageNumber,
                    loadedPages = loadedPages.keys,
                    loadInFlight = loadThemeJob?.isActive == true
            )

    /** Bookmark / mention opens carry explicit-post intent — do not downgrade to fresh on topic switch. */
    private fun shouldPreserveTopicOpenIntentOnCrossTopicOpen(intent: String, sourceScreen: String): Boolean {
        if (intent == TopicOpenIntentClassifier.EXPLICIT_POST) return true
        return when (sourceScreen.lowercase()) {
            "bookmark", "bookmarks", "note", "notes", "mentions", "mention" -> true
            else -> false
        }
    }

}

sealed class ThemeUiEvent {
    data class UpdateShowAvatarState(val show: Boolean) : ThemeUiEvent()
    data class UpdateTypeAvatarState(val circle: Boolean) : ThemeUiEvent()
    data class UpdateScrollButtonState(val enabled: Boolean) : ThemeUiEvent()
    data class UpdateTopicPaginationPanelState(val enabled: Boolean) : ThemeUiEvent()
    data class UpdateTopicScrollMode(val mode: AppPreferences.Main.TopicScrollMode) : ThemeUiEvent()
    data class UpdateTopicPostDensity(val density: AppPreferences.Main.TopicPostDensity) : ThemeUiEvent()
    data class UpdateTopicToolbarBehavior(val behavior: AppPreferences.Main.TopicToolbarBehavior) : ThemeUiEvent()
    data class UpdateTopicPageSwipeState(val enabled: Boolean) : ThemeUiEvent()
    data class UpdateBottomRefreshGestureState(val enabled: Boolean) : ThemeUiEvent()
    data class UpdateHatOpenState(val open: Boolean) : ThemeUiEvent()
    data class UpdatePollOpenState(val open: Boolean) : ThemeUiEvent()
    object RefreshToolbarMenu : ThemeUiEvent()
    data class UpdateTopicToolbar(val page: ThemePage) : ThemeUiEvent()
    data class ClearUnreadAnchorHybridGuard(val reason: String) : ThemeUiEvent()
    data class SetFontSize(val size: Int) : ThemeUiEvent()
    data class SetAppFontMode(val mode: forpdateam.ru.forpda.ui.AppFontMode) : ThemeUiEvent()
    data class SetStyleType(val styleType: String) : ThemeUiEvent()
    data class OnEventNew(val event: TabNotification) : ThemeUiEvent()
    data class OnEventRead(val event: TabNotification) : ThemeUiEvent()
    object UpdateHistoryLastHtml : ThemeUiEvent()
    data class SyncEditPost(val sync: EditPostSyncData) : ThemeUiEvent()
    data class OnUploadFiles(val items: List<AttachmentItem>) : ThemeUiEvent()
    data class OnDeleteFiles(val items: List<AttachmentItem>) : ThemeUiEvent()
    object FirstPage : ThemeUiEvent()
    object PrevPage : ThemeUiEvent()
    object NextPage : ThemeUiEvent()
    object LastPage : ThemeUiEvent()
    object SelectPage : ThemeUiEvent()
    object SelectPageInput : ThemeUiEvent()
    data class ShowUserMenu(val post: IBaseForumPost) : ThemeUiEvent()
    data class ShowReputationMenu(val post: IBaseForumPost) : ThemeUiEvent()
    data class ShowPostMenu(val post: IBaseForumPost) : ThemeUiEvent()
    data class ReportPost(val post: IBaseForumPost) : ThemeUiEvent()
    data class InsertText(val text: String) : ThemeUiEvent()
    data class DeletePost(val post: IBaseForumPost) : ThemeUiEvent()
    data class EditPost(val post: IBaseForumPost) : ThemeUiEvent()
    data class VotePost(val post: IBaseForumPost, val type: Boolean) : ThemeUiEvent()
    data class OpenSpoilerLinkDialog(val post: IBaseForumPost, val spoilNumber: String) : ThemeUiEvent()
    data class OpenAnchorDialog(val post: IBaseForumPost, val name: String) : ThemeUiEvent()
    data class Log(val text: String) : ThemeUiEvent()
    data class ScrollToAnchor(val anchor: String) : ThemeUiEvent()
    object SaveScrollYForImageViewer : ThemeUiEvent()
    data class ShowDeleteInFavDialog(val page: ThemePage) : ThemeUiEvent()
    data class ShowAddInFavDialog(val page: ThemePage) : ThemeUiEvent()
    data class ShowChangeReputation(val post: IBaseForumPost, val type: Boolean) : ThemeUiEvent()
    data class DeletePostUi(val post: IBaseForumPost) : ThemeUiEvent()
    data class ShowNoteCreate(val title: String, val url: String) : ThemeUiEvent()
    data class ShowError(val message: String) : ThemeUiEvent()
    data class ApplyInfinitePage(val direction: String, val pageNumber: Int, val html: String) : ThemeUiEvent()
    data class SetInfiniteState(val direction: String, val state: String, val message: String?) : ThemeUiEvent()
    data class UpdateVisiblePage(
            val pageNumber: Int,
            val allPages: Int,
            val perPage: Int,
            val isForum: Boolean
    ) : ThemeUiEvent()
    data class ShowSnackbar(val message: String) : ThemeUiEvent()
    /**
     * Topic opened on the last (read) page and the server redirected to the bottom entry —
     * tell the user the topic is fully read so they don't think they landed on a random post.
     */
    object ShowAllReadHint : ThemeUiEvent()
    data class ScrollToPage(val pageNumber: Int) : ThemeUiEvent()
    data class ScrollToPageAndBottom(val pageNumber: Int) : ThemeUiEvent()
    data class ScrollToEndAnchorOrBottom(val anchorPostId: String?) : ThemeUiEvent()
    object ScrollToBottom : ThemeUiEvent()
    object CancelPendingSmartScroll : ThemeUiEvent()
    /** WebView was hidden/deferred until programmatic anchor/restore scroll finished. */
    object RevealThemeContent : ThemeUiEvent()
    /** Programmatic scroll command finished — release toolbar auto-hide suppress. */
    object ProgrammaticScrollEnded : ThemeUiEvent()
    data class ApplySmartPostsPatch(val page: ThemePage, val patch: SmartPostsPatch) : ThemeUiEvent()

    /** Refresh post rating row after vote without reloading the WebView. */
    data class PatchPostRatingUi(
            val postId: Int,
            val ratingText: String,
            val canPlus: Boolean,
            val canMinus: Boolean
    ) : ThemeUiEvent()

    /** Inject desktop/profile user post count after deferred metadata merge. */
    data class PatchUserPostCountUi(
            val postId: Int,
            val userPostCount: Int,
    ) : ThemeUiEvent()

    /** Remove a prepended hat post from the live DOM without reloading the WebView. */
    data class StripPrependedTopicHatFromDom(val hatPostId: Int) : ThemeUiEvent()

    /** Inject or replace the floating hat overlay host markup via JS. */
    data class InjectTopicHatOverlay(val overlayHostHtml: String, val openAfterInject: Boolean) : ThemeUiEvent()

    /** WebView must drop render dedupe keys when [ThemeViewModel] switches to another topic in the same tab. */
    data class ResetRenderLifecycle(val topicId: Int) : ThemeUiEvent()
}

data class SmartPostsPatch(
        val expectedPostIds: List<Int>,
        val changedPosts: List<SmartPatchPost>,
        val addedPosts: List<SmartPatchPost>,
        val pageNumber: Int,
        val allPages: Int,
        val postsOnPage: Int,
        val keepBottom: Boolean,
        val requestId: String?,
        val source: String
)

data class SmartPatchPost(
        val id: Int,
        val html: String
)