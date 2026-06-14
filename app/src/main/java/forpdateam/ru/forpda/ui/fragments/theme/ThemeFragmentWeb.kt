package forpdateam.ru.forpda.ui.fragments.theme

import dagger.hilt.android.AndroidEntryPoint
import forpdateam.ru.forpda.common.getVecDrawable
import forpdateam.ru.forpda.common.showSnackbar
import android.annotation.SuppressLint
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.ProgressBar
import android.widget.TextView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import timber.log.Timber
import kotlinx.coroutines.launch
import android.view.ActionMode
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.VelocityTracker
import android.view.ViewConfiguration
import android.util.Log
import android.os.SystemClock
import android.webkit.WebView
import forpdateam.ru.forpda.BuildConfig
import forpdateam.ru.forpda.R
import forpdateam.ru.forpda.ui.dp8
import forpdateam.ru.forpda.ui.dp16
import forpdateam.ru.forpda.common.getColorFromAttr
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import forpdateam.ru.forpda.common.extractPostBodyHtml
import forpdateam.ru.forpda.entity.remote.IBaseForumPost
import forpdateam.ru.forpda.entity.remote.theme.ThemePage
import forpdateam.ru.forpda.ui.views.messagepanel.MessagePanel
import forpdateam.ru.forpda.diagnostic.TopicScrollTrace
import forpdateam.ru.forpda.presentation.theme.TopicOpenContext
import forpdateam.ru.forpda.presentation.theme.TopicOpenResolution
import forpdateam.ru.forpda.presentation.theme.TopicOpenTargetType
import forpdateam.ru.forpda.presentation.theme.TopicOpenTrace
import forpdateam.ru.forpda.presentation.theme.TopicOpenTraceExtras
import forpdateam.ru.forpda.presentation.theme.ThemeJsInterface
import forpdateam.ru.forpda.presentation.theme.ThemeRefreshScrollRestorePolicy
import forpdateam.ru.forpda.presentation.theme.ThemeDomLoadAnchorPolicy
import forpdateam.ru.forpda.presentation.theme.ThemeRenderGuard
import forpdateam.ru.forpda.presentation.theme.ThemeHtmlMetrics
import forpdateam.ru.forpda.presentation.theme.ThemeInfiniteScrollController
import forpdateam.ru.forpda.presentation.theme.ThemeOpenScrollCoalescePolicy
import forpdateam.ru.forpda.presentation.theme.ThemeUnreadHybridAnchorGuardPolicy
import forpdateam.ru.forpda.presentation.theme.ThemePostedPageScrollPolicy
import forpdateam.ru.forpda.presentation.theme.ThemeScrollCommand
import forpdateam.ru.forpda.presentation.theme.ThemeSmartEndNavigation
import forpdateam.ru.forpda.presentation.theme.ThemeToolbarScrollPolicy
import forpdateam.ru.forpda.presentation.theme.TopicTopChromePaddingPolicy
import forpdateam.ru.forpda.presentation.theme.ThemeHatToolbarClickAction
import forpdateam.ru.forpda.presentation.theme.ThemeViewModel
import forpdateam.ru.forpda.ui.views.ExtendedWebView
import forpdateam.ru.forpda.ui.fragments.TabFragment
import forpdateam.ru.forpda.ui.fragments.theme.modules.ThemeFabCoordinator
import forpdateam.ru.forpda.ui.fragments.theme.modules.ThemeScrollHandler
import forpdateam.ru.forpda.ui.fragments.theme.modules.ThemeStyleHandler
import forpdateam.ru.forpda.ui.fragments.theme.modules.ThemeBridgeHandler
import forpdateam.ru.forpda.ui.fragments.theme.modules.BottomRefreshGestureController
import forpdateam.ru.forpda.ui.fragments.theme.modules.ThemePaginationHandler
import forpdateam.ru.forpda.ui.fragments.theme.modules.ThemeWebController
import forpdateam.ru.forpda.ui.fragments.theme.modules.ThemeUiBinder
import forpdateam.ru.forpda.ui.fragments.theme.modules.ThemeJsApi
import forpdateam.ru.forpda.ui.fragments.theme.modules.ThemeLoadingIndicator
import forpdateam.ru.forpda.ui.fragments.theme.modules.ThemeToolbarScrollController
import forpdateam.ru.forpda.ui.fragments.theme.modules.ThemeUiModule
import forpdateam.ru.forpda.ui.fragments.theme.modules.ThemeUiModuleRegistry
import forpdateam.ru.forpda.ui.fragments.theme.modules.ThemeWebViewHost
import forpdateam.ru.forpda.ui.BottomNavWindowInset
import org.json.JSONObject
import org.json.JSONArray
import java.util.*
import forpdateam.ru.forpda.common.ClipboardHelper
import forpdateam.ru.forpda.common.Preferences as AppPreferences
import forpdateam.ru.forpda.model.repository.avatar.AvatarRepository
import forpdateam.ru.forpda.presentation.ILinkHandler
import forpdateam.ru.forpda.presentation.ISystemLinkHandler
import forpdateam.ru.forpda.presentation.TabRouter
import javax.inject.Inject
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

private const val REFRESH_SCROLL_TAG = "RefreshScroll"
private const val THEME_INSETS_TAG = "ThemeInsets"
private const val WEBVIEW_BLANK_CONTENT_HEIGHT_THRESHOLD = 4
private const val MAX_THEME_BLANK_RENDER_RETRIES = 2
private const val THEME_BLANK_RENDER_VERIFY_DELAY_MS = 450L
private const val THEME_ALPHA_REVEAL_SAFETY_DELAY_MS = 3200L
/** After [SCROLL_ANCHOR_RETRY_DELAYS_MS] final retry (900ms) + layout settle. */
private const val THEME_SCROLL_STUCK_REVEAL_DELAY_MS = 2000L
private const val MAX_INITIAL_ANCHOR_REVEAL_ATTEMPTS = 1
private const val NATIVE_UNREAD_ANCHOR_FALLBACK_DELAY_MS = 500L
private var initialAnchorGuardWatchdogGeneration = 0
private var nativeUnreadAnchorFallbackGeneration = 0
private const val THEME_RENDER_WATCHDOG_BASE_DELAY_MS = 2500L
private const val THEME_RENDER_WATCHDOG_LARGE_HTML_DELAY_MS = 5000L
private const val THEME_RENDER_WATCHDOG_HUGE_HTML_DELAY_MS = 8000L
private const val THEME_RENDER_WATCHDOG_LARGE_HTML_LEN = 150_000
private const val THEME_RENDER_WATCHDOG_HUGE_HTML_LEN = 400_000

/**
 * Created by radiationx on 20.10.16.
 *
 * Fragment для отображения темы форума в WebView.
 *
 * JS Bridge обоснование:
 * - ThemeJsInterface: взаимодействие с темой форума (голосования, цитирование, редактирование, навигация)
 * - IBase: базовый интерфейс для обратных вызовов из JS (domContentLoaded, onPageComplete)
 * - Контент: локально генерируемый HTML из API тем форума (доверенный шаблонный контент)
 * - Профиль безопасности: TRUSTED_LOCAL_TEMPLATE (JS включён, базовый bridge разрешён)
 */
@AndroidEntryPoint
class ThemeFragmentWeb : ThemeFragment(), ExtendedWebView.JsLifeCycleListener {
    @Inject lateinit var linkHandler: ILinkHandler
    @Inject lateinit var systemLinkHandler: ISystemLinkHandler
    @Inject lateinit var clipboardHelper: ClipboardHelper
    @Inject lateinit var avatarRepository: AvatarRepository
    private var topicTitlePopup: PopupWindow? = null

    override fun useTopBarHorizontalPlaqueInset(): Boolean = true
    override fun shouldShowHatToolbarButton(): Boolean = true
    override fun shouldDrawBehindBottomNav(): Boolean = true
    override fun messagePanelBaseBottomMargin(): Int =
            if (isMessagePanelReady && messagePanel.visibility == View.VISIBLE) {
                currentBottomChromePadding
            } else {
                0
            }

    override fun editPost(post: IBaseForumPost) {
        if (!::webView.isInitialized) return
        webView.extractPostBodyHtml(post.id) { domHtml ->
            presenter.openEditPostForm(post.id, domHtml)
        }
    }
    private lateinit var webView: ExtendedWebView
    private lateinit var jsInterface: ThemeJsInterface
    private lateinit var jsApi: ThemeJsApi
    private val renderGuard = ThemeRenderGuard()

    // Decomposed modules
    private lateinit var scrollHandler: ThemeScrollHandler
    private lateinit var styleHandler: ThemeStyleHandler
    private lateinit var bridgeHandler: ThemeBridgeHandler
    private lateinit var paginationHandler: ThemePaginationHandler
    private lateinit var webController: ThemeWebController
    private lateinit var webViewHost: ThemeWebViewHost
    private lateinit var uiBinder: ThemeUiBinder
    private lateinit var fabCoordinator: ThemeFabCoordinator
    private lateinit var toolbarScrollController: ThemeToolbarScrollController
    private val moduleRegistry = ThemeUiModuleRegistry()
    private val viewHandler = Handler(Looper.getMainLooper())

    /** ScrollY сохранённый перед открытием ImageViewer — восстанавливаем при возврате. */
    private var savedScrollYForImageViewer: Int = -1
    private var currentBottomChromePadding: Int = 0
    private var currentBottomChromeSpacerPadding: Int = 0
    private var isMessagePanelReady: Boolean = false
    private var currentTopicScrollMode: AppPreferences.Main.TopicScrollMode = AppPreferences.Main.TopicScrollMode.HYBRID
    private var isTopicPageSwipeEnabled: Boolean = false
    private var isBottomRefreshGestureEnabled: Boolean = true
    private var isToolbarAutoHideEnabled: Boolean = false
    private var isTopicHatOverlayOpen: Boolean = false
    /** True while hat toolbar JS/render is in flight — keeps auto-hide from hiding chrome before setHatOpen. */
    private var hatToolbarOpenInFlight: Boolean = false
    private var isTopicPollOverlayOpen: Boolean = false
    private var pageSwipeDetector: TopicPageSwipeDetector? = null
    private var bottomRefreshController: BottomRefreshGestureController? = null
    private var bottomRefreshOverlay: ViewGroup? = null
    private var bottomRefreshProgress: ProgressBar? = null
    private var bottomRefreshLabel: TextView? = null
    private var bottomRefreshRestorePending: Boolean = false
    private var bottomRefreshRestoreId: String? = null
    private var hiddenUntilFirstRestoreId: String? = null
    private var renderTransitionOverlay: View? = null
    private var renderTransitionRestoreId: String? = null
    private var lastTabUnreadJumpTopicId: Int = -1
    private var lastTabUnreadJumpAt: Long = 0L
    private var lastRenderKey: String? = null
    private var themeBlankRetryCount = 0
    private var renderWatchdogGeneration = 0
    private var alphaRevealWatchdogGeneration = 0
    private var blockingScrollStuckWatchdogGeneration = 0
    private var initialAnchorRevealAttempts = 0
    private var lastRenderAt: Long = 0L
    private var pendingRenderPage: ThemePage? = null
    private var renderCount: Int = 0
    private var renderedThemeOnce: Boolean = false
    private var viewRuntimeGeneration: Int = 0
    private var pageSwipeOverlay: ViewGroup? = null
    private var pageSwipeProgress: ProgressBar? = null
    private var pageSwipeLabel: TextView? = null
    private var pageSwipeDirectionLabel: TextView? = null
    private lateinit var loadingIndicator: ThemeLoadingIndicator
    private var lastHybridEdgeLogAt = 0L
    private var suppressToolbarScrollUntilMs = 0L
    private var programmaticToolbarScrollSuppressActive = false
    private val hybridEdgeThresholdPx: Int
        get() = (800f * resources.displayMetrics.density).toInt()
    private val smartScrollPostActionsSafeOffsetPx: Int
        get() = (80f * resources.displayMetrics.density).toInt()

    /**
     * Та же тема уже открыта в другой вкладке — грузим новый URL (findpost и т.д.) без дубликата вкладки.
     * Вызывается из [forpdateam.ru.forpda.ui.navigation.TabNavigator] до [updateFragmentsState].
     */
    fun loadThemeUrlFromNavigator(
            url: String,
            sourceScreen: String = "navigator",
            openIntent: String = "fresh",
            listHints: forpdateam.ru.forpda.common.TopicOpenListHints? = null
    ) {
        // Navigator reuse must always hit ViewModel even if WebView is not created yet (view destroyed).
        val incomingTopicId = forpdateam.ru.forpda.model.data.remote.api.theme.ThemeApi.extractTopicIdFromUrl(url)
        val openTopicId = getOpenTopicIdForReuse()
        if (ThemeViewModel.shouldClearToolbarOnTopicSwitch(incomingTopicId, openTopicId) && view != null) {
            clearTopicToolbarForLoading()
        }
        presenter.setNavigationTopicTitle(arguments?.getString(TabFragment.ARG_TITLE))
        presenter.initThemeUrl(url)
        listHints?.let { presenter.initTopicOpenHints(it, sourceScreen) }
        presenter.setTopicOpenIntent(openIntent)
        presenter.loadUrl(url, sourceScreen = sourceScreen, listHints = listHints)
    }

    /** Для [TabNavigator]: после навигации внутри темы в [ARG_TAB] остаётся старый showtopic, topic id берём из модели. */
    fun getOpenTopicIdForReuse(): Int? {
        if (!presenter.isPageLoaded()) return null
        return webController.getOpenTopicIdForReuse()
    }

    /**
     * Вызывается из [forpdateam.ru.forpda.ui.navigation.TabNavigator] после каждой смены текущей вкладки
     * (список «Открытые вкладки», [com.github.terrakok.cicerone.Forward] на уже открытый экран и т.д.).
     */
    fun onTabStackBecameCurrent() {
        Timber.d("onTabStackBecameCurrent: isAdded=$isAdded pageLoaded=${presenter.isPageLoaded()}")
        presenter.reconcileStuckLoadOnFragmentVisible()
        renderPendingThemePageIfReady()
        reconcileStuckThemeRender()
        scheduleJumpToUnreadAfterTabSwitch()
    }

    fun scrollToAnchor(anchor: String?) {
        if (anchor.isNullOrBlank()) return
        if (!::webController.isInitialized) return
        webController.executeScrollCommand(ThemeScrollCommand.anchor(anchor))
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewRuntimeGeneration++
        currentTopicScrollMode = mainPreferencesHolder.getTopicScrollMode()
        isTopicPageSwipeEnabled = mainPreferencesHolder.getTopicPageSwipeEnabled()
        isBottomRefreshGestureEnabled = mainPreferencesHolder.getTopicBottomRefreshGestureEnabled()

        consumeHeaderTouchGaps()

        jsInterface = ThemeJsInterface(presenter, renderGuard)

        // Fragment owns the view lifecycle; ThemeWebController owns WebView runtime wiring
        // (security profile, clients, direction listener and download handling) exactly once.
        webView = ExtendedWebView(requireContext())
        jsApi = ThemeJsApi(webView)

        webController = ThemeWebController(
                webView = webView,
                fragment = this,
                presenter = presenter,
                linkHandler = linkHandler,
                systemLinkHandler = systemLinkHandler,
                avatarRepository = avatarRepository,
                onDirectionChanged = { direction ->
                    fabCoordinator.onDirectionChanged(direction)
                },
                onActionModeClick = { _, item ->
                    var result = false
                    when (item.itemId) {
                        R.id.action_mode_item_copy -> {
                            bridgeHandler.copySelectedText()
                            result = true
                        }
                        R.id.action_mode_item_quote -> {
                            bridgeHandler.selectionToQuote()
                            result = true
                        }
                        R.id.action_mode_item_select_all -> {
                            bridgeHandler.selectAllPostText()
                            result = true
                        }
                        R.id.action_mode_item_share -> {
                            bridgeHandler.shareSelectedText()
                            result = true
                        }
                    }
                    result
                },
                onActionModeCreate = { _, _ ->
                    // Action mode creation logic
                },
                onProgrammaticScrollStarted = ::armToolbarSuppressForProgrammaticScroll,
        )
        webController.init()

        // Initialize modules before wiring the FAB coordinator: it uses scrollHandler immediately.
        scrollHandler = ThemeScrollHandler(webView, appBarLayout, fragmentContent)
        toolbarScrollController = ThemeToolbarScrollController(
                appBarLayout = appBarLayout,
                linkedTranslationViews = listOf(preLpShadow),
                shouldStayVisible = {
                    messagePanel.visibility == View.VISIBLE ||
                            isTopicHatOverlayOpen ||
                            hatToolbarOpenInFlight ||
                            isTopicPollOverlayOpen ||
                            webView.isActionModeActive()
                }
        )
        styleHandler = ThemeStyleHandler(webView, requireContext())
        loadingIndicator = ThemeLoadingIndicator(
                context = requireContext(),
                fragmentContent = fragmentContent,
                contentProgress = contentProgress,
                dp8 = dp8,
                dp16 = dp16,
                screenWidthPx = { resources.displayMetrics.widthPixels }
        )
        // ThemeBridgeHandler is the single owner of JS bridge registration/removal.
        bridgeHandler = ThemeBridgeHandler(webView, jsInterface)
        paginationHandler = ThemePaginationHandler(webView)
        webViewHost = ThemeWebViewHost(
                ThemeWebViewHost.Config(
                        webView = webView,
                        refreshLayout = refreshLayout,
                        linkHandler = linkHandler,
                        systemLinkHandler = systemLinkHandler,
                        router = router,
                        clipboardHelper = clipboardHelper,
                        jsLifeCycleListener = this
                )
        )
        if (BuildConfig.DEBUG) Log.i("HybridScroll", "register native listener fragment=ThemeFragmentWeb initialMode=$currentTopicScrollMode")
        scrollHandler.setHeaderScrollListener(object : ExtendedWebView.OnScrollListener {
            override fun onScrollChange(scrollX: Int, scrollY: Int, oldScrollX: Int, oldScrollY: Int) {
                requestHybridPageIfNearEdge(scrollY)
                if (!isToolbarAutoHideEnabled) return
                if (webView.isUserScrollActive()) {
                    releaseToolbarSuppressOnUserScroll()
                }
                val programmaticSuppressed = isToolbarProgrammaticScrollSuppressed()
                val shouldReact = ThemeToolbarScrollPolicy.shouldReactToScrollChange(
                        programmaticScrollSuppressed = programmaticSuppressed,
                )
                val userScrollActive = webView.isUserScrollActive()
                if (BuildConfig.DEBUG && scrollY != oldScrollY) {
                    val logLevel = if (!shouldReact && programmaticSuppressed) Log.WARN else Log.INFO
                    Log.println(
                            logLevel,
                            "ToolbarAutoHide",
                            "scroll y=$scrollY dy=${scrollY - oldScrollY} react=$shouldReact " +
                                    "progSuppressed=$programmaticSuppressed pendingCmd=${presenter.getPendingScrollCommand()?.kind} " +
                                    "stickySuppress=$programmaticToolbarScrollSuppressActive " +
                                    "suppressUntil=$suppressToolbarScrollUntilMs " +
                                    "userScroll=$userScrollActive state=${toolbarScrollController.state} " +
                                    "appBarY=${appBarLayout.translationY}",
                    )
                }
                if (shouldReact) {
                    toolbarScrollController.onScroll(scrollY, oldScrollY, userScrollActive)
                }
            }
        })

        // Register lifecycle-aware modules; disposal happens in reverse order.
        moduleRegistry.register(webController)
        moduleRegistry.register(object : ThemeUiModule {
            override fun init() = toolbarScrollController.bind()
            override fun dispose() = toolbarScrollController.dispose()
        })
        moduleRegistry.register(scrollHandler)
        moduleRegistry.register(styleHandler)
        moduleRegistry.register(loadingIndicator)
        moduleRegistry.register(ThemeBridgeModuleAdapter(bridgeHandler))
        moduleRegistry.register(webViewHost)

        fabCoordinator = ThemeFabCoordinator(
                context = requireContext(),
                fab = fab,
                coordinatorLayout = coordinatorLayout,
                fabBehavior = fabBehavior,
                webView = webView,
                scrollHandler = scrollHandler,
                lifecycleScope = viewLifecycleOwner.lifecycleScope,
                otherPreferencesHolder = otherPreferencesHolder,
                onLoadPage = { presenter.loadPage(it) },
                onLoadLastPageAndScrollToBottom = { presenter.loadLastPageAndScrollToBottom() },
                onLoadNewPosts = { presenter.loadNewPosts() },
                onGetPagination = { presenter.getCurrentPageInstance()?.pagination },
                onGetVisibleCurrentPage = { presenter.getVisibleCurrentPage() },
                onHasUnread = { presenter.getCurrentPageInstance()?.hasUnreadTarget == true }
        )
        fabCoordinator.init()
        moduleRegistry.register(fabCoordinator)

        val bottomBarHeight = resources.getDimensionPixelSize(R.dimen.bottom_nav_tab_bar_height)
        val fabMargin = resources.getDimensionPixelSize(R.dimen.fab_margin)
        (fab.layoutParams as? ViewGroup.MarginLayoutParams)?.also { params ->
            params.bottomMargin = bottomBarHeight + fabMargin + smartScrollPostActionsSafeOffsetPx
            fab.layoutParams = params
        }

        // Initialize uiBinder
        isMessagePanelReady = true
        uiBinder = ThemeUiBinder(webView, messagePanel, messagePanelHost) {
            syncBottomChromeSpacer()
        }

        registerForContextMenu(webView)
        configureOverlayHeaderForWebView()
        updateTopicPostDensityChrome(mainPreferencesHolder.getTopicPostDensity())

        setFontSize(mainPreferencesHolder.getWebViewFontSize())

        // Setup action mode listener through webController
        webController.setupActionModeListener { authHolder }

        styleThemeProgressIndicators()
        showInitialLoadingIndicator()
        observeViewModel()

        scrollHandler.init()
        toolbarScrollController.bind()
        // super.onViewCreated enables auto-hide before the controller exists; sync now.
        onToolbarAutoHideEnabledChanged(isToolbarAutoHideEnabled)
        styleHandler.init()
        bridgeHandler.init()
        webViewHost.init()

        uiBinder.setupMessagePanelHeightListener()
        // ThemeFragment.onViewCreated может вызвать show/hide панели до setHeightChangeListener — синхронизируем отступ.
        uiBinder.syncWebViewPaddingWithMessagePanel()
        attachWebView(webView)
        pageSwipeDetector = TopicPageSwipeDetector(
                target = webView,
                onProgress = { direction, progress, armed ->
                    updatePageSwipeOverlay(direction, progress, armed)
                },
                onReset = {
                    hidePageSwipeOverlay("gestureReset")
                }
        )
        bottomRefreshController = BottomRefreshGestureController(
                target = webView,
                canRefresh = { presenter.canRefreshFromBottomOverscroll() },
                isHatOpen = { isTopicHatOverlayOpen || presenter.isTopicHatOpen() },
                onProgress = { progress, canRelease, active ->
                    updateBottomRefreshOverlay(progress, canRelease, active)
                },
                onRefresh = { triggerBottomOverscrollRefresh() }
        ).also { it.isEnabled = isBottomRefreshGestureEnabled }
        webView.setOnTouchListener { v, event ->
            val bottomHandled = bottomRefreshController?.onTouch(v, event) == true
            val pageSwipeHandled = if (bottomHandled) false else pageSwipeDetector?.onTouch(v, event) == true
            if (bottomHandled || pageSwipeHandled) return@setOnTouchListener true
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    webView.markUserTouchForScroll()
                    releaseToolbarSuppressOnUserScroll()
                }
                MotionEvent.ACTION_MOVE,
                MotionEvent.ACTION_UP,
                MotionEvent.ACTION_CANCEL -> webView.markUserTouchForScroll()
            }
            v.onTouchEvent(event)
        }
        installBottomRefreshOverlay()
        installPageSwipeOverlay()

        webController.applyImeInsets(coordinatorLayout, messagePanel, refreshLayout)
        syncBottomChromeSpacer()

        updateClassicOnlyGestures()
        presenter.start()
        presenter.getCurrentPageInstance()?.takeIf { !it.html.isNullOrBlank() }?.let { page ->
            postOnActiveWebView {
                renderThemePageSafely(page)
            }
        }
    }

    private fun configureOverlayHeaderForWebView() {
        clearToolbarScrollFlags()
        appBarLayout.setExpanded(true, false)
        ensureOpaquePinnedToolbarUnderlay()
        webView.isNestedScrollingEnabled = false
        appBarLayout.translationY = 0f
        preLpShadow.translationY = 0f
        fragmentContent.translationY = 0f
    }

    override fun onToolbarAutoHideEnabledChanged(enabled: Boolean) {
        val changed = isToolbarAutoHideEnabled != enabled
        isToolbarAutoHideEnabled = enabled
        if (::toolbarScrollController.isInitialized) {
            toolbarScrollController.setEnabled(enabled)
        }
        if (changed) {
            syncTopChromeSpacer()
        }
    }

    private fun syncTopChromeSpacer() {
        if (!::webView.isInitialized || !webView.isJsReady) return
        jsApi.eval(jsApi.setTopChromePaddingInline(currentTopChromePaddingCssPx()))
    }

    private fun armToolbarSuppressForProgrammaticScroll() {
        programmaticToolbarScrollSuppressActive = true
        val fallbackUntil = SystemClock.uptimeMillis() +
                ThemeToolbarScrollPolicy.PROGRAMMATIC_SCROLL_FALLBACK_SUPPRESS_MS
        if (fallbackUntil > suppressToolbarScrollUntilMs) {
            suppressToolbarScrollUntilMs = fallbackUntil
        }
    }

    private fun clearToolbarProgrammaticScrollSuppress() {
        programmaticToolbarScrollSuppressActive = false
        suppressToolbarScrollUntilMs = 0L
    }

    private fun releaseToolbarSuppressOnUserScroll() {
        if (programmaticToolbarScrollSuppressActive || suppressToolbarScrollUntilMs > 0L) {
            clearToolbarProgrammaticScrollSuppress()
        }
    }

    private fun isToolbarProgrammaticScrollSuppressed(): Boolean {
        val resolution = ThemeToolbarScrollPolicy.resolveProgrammaticScrollSuppressed(
                nowUptimeMs = SystemClock.uptimeMillis(),
                suppressUntilMs = suppressToolbarScrollUntilMs,
                stickySuppressActive = programmaticToolbarScrollSuppressActive,
        )
        if (resolution.clearSticky) {
            programmaticToolbarScrollSuppressActive = false
        }
        return resolution.suppressed
    }

    private fun currentTopChromePaddingCssPx(): Float {
        val density = resources.displayMetrics.density
        val stabilizeAutoHidePadding = isToolbarAutoHideEnabled
        if (::webView.isInitialized && webView.isAttachedToWindow && webView.height > 0) {
            val webLoc = IntArray(2)
            webView.getLocationInWindow(webLoc)
            val paddingPx = TopicTopChromePaddingPolicy.paddingPxFromChromeBottom(
                    webViewWindowY = webLoc[1],
                    chromeBottomWindowY = measureTopicChromeBottomInWindow(
                            stabilizeAutoHideTranslation = stabilizeAutoHidePadding,
                    ),
            )
            return paddingPx / density
        }
        if (!isToolbarAutoHideEnabled) {
            return 0f
        }
        return measureTopicChromeBottomInWindow(stabilizeAutoHideTranslation = true)
                .minus(
                        if (::webView.isInitialized && webView.isAttachedToWindow) {
                            val webLoc = IntArray(2)
                            webView.getLocationInWindow(webLoc)
                            webLoc[1]
                        } else {
                            appBarLayout.let {
                                val appLoc = IntArray(2)
                                it.getLocationInWindow(appLoc)
                                appLoc[1]
                            }
                        }
                )
                .coerceAtLeast(0) / density
    }

    private fun consumeHeaderTouchGaps() {
        listOf(appBarLayout, toolbarLayout, toolbar).forEach { headerView ->
            headerView.isClickable = true
            ViewCompat.setElevation(headerView, resources.getDimension(R.dimen.dp4))
            ViewCompat.setTranslationZ(headerView, resources.getDimension(R.dimen.dp4))
        }
        titlesWrapper.isClickable = true
        titlesWrapper.setOnTouchListener { _, _ ->
            true
        }
        toolbarTitleView.apply {
            isClickable = true
            setOnClickListener {
                showFullTopicTitle()
            }
        }
        appBarLayout.bringToFront()
    }

    private fun showFullTopicTitle() {
        val topicTitle = getTitle().trim()
        if (topicTitle.isEmpty()) return
        if (topicTitlePopup?.isShowing == true) return

        val contentView = androidx.appcompat.widget.AppCompatTextView(requireContext()).apply {
            text = topicTitle
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyLarge)
            setTextColor(requireContext().getColorFromAttr(R.attr.default_text_color))
            gravity = Gravity.START
            maxLines = 4
            maxWidth = resources.displayMetrics.widthPixels - dp16 * 2
            setPadding(dp16, dp8, dp16, dp8)
        }

        topicTitlePopup = PopupWindow(
                contentView,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                true
        ).apply {
            isOutsideTouchable = true
            elevation = resources.getDimension(R.dimen.dp4)
            setBackgroundDrawable(GradientDrawable().apply {
                setColor(requireContext().getColorFromAttr(R.attr.cards_background))
                cornerRadius = dp8.toFloat()
            })
            setOnDismissListener {
                if (topicTitlePopup === this) {
                    topicTitlePopup = null
                }
            }
            showAsDropDown(toolbarTitleView, 0, dp8)
        }
    }

    private fun installBottomRefreshOverlay() {
        if (bottomRefreshOverlay != null) return
        val context = requireContext()
        val progressTint = ColorStateList.valueOf(context.getColorFromAttr(R.attr.colorAccent))
        val overlay = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            visibility = View.GONE
            alpha = 0f
            isClickable = false
            setPadding(0, 0, 0, 0)
        }
        val progress = ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progress = 0
            isIndeterminate = false
            progressTintList = progressTint
            progressBackgroundTintList = ColorStateList.valueOf(context.getColorFromAttr(R.attr.divider_line))
        }
        val label = TextView(context).apply {
            text = getString(R.string.theme_bottom_refresh_pull)
            gravity = Gravity.CENTER
            setTextColor(context.getColorFromAttr(R.attr.default_text_color))
            textSize = 12f
            maxLines = 1
        }
        overlay.addView(label, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = dp8 / 2
        })
        overlay.addView(progress, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, resources.getDimensionPixelSize(R.dimen.dp4)))
        fragmentContent.addView(overlay, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM).apply {
            leftMargin = dp16
            rightMargin = dp16
            bottomMargin = currentBottomChromePadding + dp8
        })
        bottomRefreshOverlay = overlay
        bottomRefreshProgress = progress
        bottomRefreshLabel = label
        if (BuildConfig.DEBUG) Log.i("BottomRefresh", "overlay installed controller=${bottomRefreshController?.javaClass?.simpleName} enabled=$isBottomRefreshGestureEnabled")
    }

    private fun updateBottomRefreshOverlay(progress: Float, canRelease: Boolean, active: Boolean) {
        val overlay = bottomRefreshOverlay ?: return
        val normalized = min(1f, max(0f, progress))
        bottomRefreshProgress?.progress = (normalized * 100f).toInt()
        bottomRefreshLabel?.text = when {
            canRelease -> getString(R.string.theme_bottom_refresh_release)
            active -> getString(R.string.theme_bottom_refresh_pull)
            else -> getString(R.string.theme_bottom_refresh_pull)
        }
        overlay.visibility = if (active && normalized > 0f) View.VISIBLE else View.GONE
        overlay.alpha = if (active) min(1f, 0.35f + normalized * 0.65f) else 0f
        (overlay.layoutParams as? FrameLayout.LayoutParams)?.let { params ->
            params.bottomMargin = currentBottomChromePadding + dp8
            overlay.layoutParams = params
        }
        overlay.bringToFront()
        if (BuildConfig.DEBUG) {
            Log.i(
                    "BottomRefresh",
                    "overlay progress=$normalized canRelease=$canRelease active=$active y=${if (::webView.isInitialized) webView.scrollY else -1}"
            )
        }
    }

    private fun hideBottomRefreshOverlay(reason: String) {
        val overlay = bottomRefreshOverlay ?: return
        bottomRefreshProgress?.progress = 0
        bottomRefreshLabel?.text = getString(R.string.theme_bottom_refresh_pull)
        overlay.alpha = 0f
        overlay.visibility = View.GONE
        if (BuildConfig.DEBUG) Log.i("BottomRefresh", "overlay hide reason=$reason")
    }

    private fun installPageSwipeOverlay() {
        if (pageSwipeOverlay != null) return
        val context = requireContext()
        val progressTint = ColorStateList.valueOf(context.getColorFromAttr(R.attr.colorAccent))
        val overlay = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            visibility = View.GONE
            alpha = 0f
            isClickable = false
            setPadding(dp16, dp8, dp16, dp8)
            background = GradientDrawable().apply {
                setColor(context.getColorFromAttr(R.attr.cards_background))
                cornerRadius = dp16.toFloat()
            }
            elevation = resources.getDimension(R.dimen.dp4)
        }
        val directionLabel = TextView(context).apply {
            gravity = Gravity.CENTER
            setTextColor(context.getColorFromAttr(R.attr.default_text_color))
            textSize = 20f
            maxLines = 1
        }
        val label = TextView(context).apply {
            gravity = Gravity.CENTER
            setTextColor(context.getColorFromAttr(R.attr.default_text_color))
            textSize = 12f
            maxLines = 1
        }
        val progress = ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progress = 0
            isIndeterminate = false
            progressTintList = progressTint
            progressBackgroundTintList = ColorStateList.valueOf(context.getColorFromAttr(R.attr.divider_line))
        }
        overlay.addView(directionLabel, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        overlay.addView(label, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp8 / 2
            bottomMargin = dp8 / 2
        })
        overlay.addView(progress, LinearLayout.LayoutParams(resources.getDimensionPixelSize(R.dimen.dp120), resources.getDimensionPixelSize(R.dimen.dp4)))
        fragmentContent.addView(overlay, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER))
        pageSwipeOverlay = overlay
        pageSwipeProgress = progress
        pageSwipeLabel = label
        pageSwipeDirectionLabel = directionLabel
        if (BuildConfig.DEBUG) Log.i("PageSwipe", "overlay installed")
    }

    private fun updatePageSwipeOverlay(direction: PageSwipeDirection, progress: Float, armed: Boolean) {
        val overlay = pageSwipeOverlay ?: return
        val normalized = min(1f, max(0f, progress))
        pageSwipeProgress?.progress = (normalized * 100f).toInt()
        pageSwipeDirectionLabel?.text = when (direction) {
            PageSwipeDirection.PREVIOUS -> "←"
            PageSwipeDirection.NEXT -> "→"
        }
        pageSwipeLabel?.text = when (direction) {
            PageSwipeDirection.PREVIOUS -> if (armed) {
                getString(R.string.theme_page_swipe_release_previous)
            } else {
                getString(R.string.theme_page_swipe_pull_previous)
            }
            PageSwipeDirection.NEXT -> if (armed) {
                getString(R.string.theme_page_swipe_release_next)
            } else {
                getString(R.string.theme_page_swipe_pull_next)
            }
        }
        overlay.visibility = if (normalized > 0f) View.VISIBLE else View.GONE
        overlay.alpha = min(1f, 0.35f + normalized * 0.65f)
        overlay.bringToFront()
    }

    private fun hidePageSwipeOverlay(reason: String) {
        val overlay = pageSwipeOverlay ?: return
        pageSwipeProgress?.progress = 0
        overlay.alpha = 0f
        overlay.visibility = View.GONE
        if (BuildConfig.DEBUG) Log.i("PageSwipe", "overlay hide reason=$reason")
    }

    private fun showInitialLoadingIndicator() {
        loadingIndicator.show()
    }

    private fun hideInitialLoadingIndicator() {
        loadingIndicator.hide()
    }

    /**
     * Вызывается из [forpdateam.ru.forpda.ui.navigation.TabNavigator] после снятия верхнего фрагмента
     * (другая тема по ссылке). Восстанавливает страницу и позицию прокрутки вместо полной перезагрузки.
     */
    fun onRestoredAfterChildFragmentRemoved() {
        Timber.d("onRestoredAfterChildFragmentRemoved")
        postOnActiveView {
            if (::webView.isInitialized) {
                presenter.setSkipNextUnreadJumpAfterTabSwitch(true)
                presenter.restoreFromChildTab()
            }
        }
    }

    fun findNext(next: Boolean) {
        if (!::webView.isInitialized) return
        webController.findNext(next)
    }

    fun findText(text: String) {
        if (!::webView.isInitialized) return
        webController.findText(text)
    }

    override fun onSearchOnPageTextChanged(text: String) {
        if (!::webView.isInitialized) return
        webView.findAllAsync(text)
    }

    override fun onSearchOnPageOpened() {
        if (!::webView.isInitialized) return
        webView.clearMatches()
        webController.resetImeInsets(coordinatorLayout, messagePanelHost)
        postOnActiveView {
            if (::webView.isInitialized) {
                webController.forceFullLayoutReset(coordinatorLayout, messagePanel, messagePanelHost, refreshLayout)
            }
        }
    }

    override fun onSearchOnPageNext(next: Boolean) {
        if (!::webView.isInitialized) return
        webView.findNext(next)
    }

    override fun onSearchOnPageClear() {
        if (!::webView.isInitialized) return
        webView.clearMatches()
        // OEM bug: IME insets/visibility can "stick" after SearchView (find on page) closes,
        // leaving extra blank space at the bottom until next insets dispatch.
        // Force re-evaluation and decay padding to 0 when keyboard is actually hidden.
        webController.forceReapplyImeInsets(coordinatorLayout, messagePanel, messagePanelHost, refreshLayout)
        // Aggressive reset: align WebView/refreshLayout heights and bg with theme to eliminate
        // residual white/gray strip at the bottom (see [ThemeWebController.forceFullLayoutReset]).
        postOnActiveView {
            if (::webView.isInitialized) {
                webController.forceFullLayoutReset(coordinatorLayout, messagePanel, messagePanelHost, refreshLayout)
            }
        }
    }

    override fun onPollToolbarClick() {
        if (!::webView.isInitialized) return
        if (!isAdded || view == null) return
        webView.evaluateJavascript(
                "if(typeof suppressThemeInfiniteScrollFor==='function'){suppressThemeInfiniteScrollFor(1200);}toggleThemePollFromToolbar();",
                null
        )
    }

    override fun onHatToolbarClick() {
        if (!::webView.isInitialized) return
        if (!isAdded || view == null) return
        presenter.getCurrentPageInstance()?.let { applyTopicToolbarState(it) }
        hatToolbarOpenInFlight = true
        if (::toolbarScrollController.isInitialized) {
            toolbarScrollController.forceVisible()
        }
        val action = presenter.handleHatToolbarClick()
        if (BuildConfig.DEBUG) {
            Log.i("ThemeHat", "toolbarClick action=$action")
        }
        when (action) {
            ThemeHatToolbarClickAction.RUN_JS -> dispatchHatToolbarClickJs(openOnly = false, syncTopChrome = true)
            ThemeHatToolbarClickAction.RENDER_SCHEDULED,
            ThemeHatToolbarClickAction.PENDING_METADATA -> Unit
            ThemeHatToolbarClickAction.UNAVAILABLE -> {
                hatToolbarOpenInFlight = false
                return
            }
        }
    }

    private fun dispatchHatToolbarClickJs(openOnly: Boolean, syncTopChrome: Boolean = false) {
        if (!::webView.isInitialized) return
        val runJs = {
            val script = buildHatToolbarOverlayJs(openOnly, syncTopChrome)
            webView.evaluateJavascript(script) { raw ->
                if (!isAdded || view == null || !::webView.isInitialized) return@evaluateJavascript
                if (parseThemeJsBoolean(raw)) {
                    presenter.acknowledgeHatToolbarOverlayOpened()
                    return@evaluateJavascript
                }
                if (!openOnly) {
                    dispatchHatToolbarClickJs(openOnly = true)
                    return@evaluateJavascript
                }
                if (BuildConfig.DEBUG) {
                    Log.i("ThemeHat", "toolbarClick jsFailed fallback=forceInjectOverlay")
                }
                when (presenter.handleHatToolbarClick(forceInjectOverlay = true)) {
                    ThemeHatToolbarClickAction.RENDER_SCHEDULED,
                    ThemeHatToolbarClickAction.PENDING_METADATA -> Unit
                    ThemeHatToolbarClickAction.RUN_JS -> dispatchOpenHatOverlayJs()
                    ThemeHatToolbarClickAction.UNAVAILABLE -> {
                        hatToolbarOpenInFlight = false
                        presenter.cancelHatToolbarOpenAttempt()
                    }
                }
            }
        }
        if (syncTopChrome) {
            webView.post { runJs() }
        } else {
            runJs()
        }
    }

    private fun parseThemeJsBoolean(raw: String?): Boolean =
            raw?.trim()?.equals("true", ignoreCase = true) == true

    private fun dispatchOpenHatOverlayJs() {
        if (!::webView.isInitialized) return
        webView.post {
            if (!isAdded || view == null || !::webView.isInitialized) return@post
            webView.evaluateJavascript(buildHatToolbarOverlayJs(openOnly = true, syncTopChrome = true), null)
        }
    }

    private fun buildHatToolbarOverlayJs(openOnly: Boolean, syncTopChrome: Boolean): String {
        val paddingPrefix = if (syncTopChrome) {
            jsApi.setTopChromePaddingInline(currentTopChromePaddingCssPx())
        } else {
            ""
        }
        val actionJs = if (openOnly) HAT_TOOLBAR_OPEN_OVERLAY_JS else HAT_TOOLBAR_TOGGLE_JS
        return paddingPrefix + actionJs
    }

    override fun onBackPressed(): Boolean {
        if (::fabCoordinator.isInitialized && fabCoordinator.onBackPressed()) {
            return true
        }
        if (topicTitlePopup?.isShowing == true) {
            topicTitlePopup?.dismiss()
            return true
        }
        if (isTopicHatOverlayOpen || presenter.isTopicHatOpen() || presenter.isTopicPollOpen()) {
            closeThemeToolbarOverlays()
            return true
        }
        if (pageSwipeOverlay?.visibility == View.VISIBLE) {
            pageSwipeDetector?.isEnabled = pageSwipeDetector?.isEnabled == true
            hidePageSwipeOverlay("back")
            return true
        }
        if (bottomRefreshOverlay?.visibility == View.VISIBLE) {
            hideBottomRefreshOverlay("back")
            return true
        }
        return super.onBackPressed()
    }

    private fun closeThemeToolbarOverlays() {
        isTopicHatOverlayOpen = false
        isTopicPollOverlayOpen = false
        presenter.onHatHeaderClick(false)
        presenter.onPollHeaderClick(false)
        if (!::webView.isInitialized) return
        webView.evaluateJavascript(
                "if(typeof closeThemeToolbarOverlaysForNavigation==='function'){closeThemeToolbarOverlaysForNavigation(true);}",
                null
        )
    }

    fun openSearchOnPage() {
        try {
            openSearchOnPageBar()
        } catch (e: Exception) {
            // MenuItem not initialized yet
        }
    }

    fun setStyleType(type: String) {
        if (!::webView.isInitialized) return
        webController.setStyleType(type)
    }

    override fun updateView(page: ThemePage) {
        renderThemePageSafely(page)
    }

    private fun renderThemePageSafely(page: ThemePage) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            postOnActiveView {
                renderThemePageSafely(page)
            }
            return
        }
        stopNativeRefreshBeforeWebRender(page)
        presenter.rememberSessionTopicTitleFromFragment(
                arguments?.getString(TabFragment.ARG_TITLE)?.trim()?.takeIf { it.isNotEmpty() }
        )
        applyTopicToolbarState(page)
        val renderKey = currentRenderKey(page)
        if (!::webView.isInitialized) {
            pendingRenderPage = page
            if (BuildConfig.DEBUG) {
                Log.i(
                        REFRESH_SCROLL_TAG,
                        "defer theme render key=$renderKey initialized=false"
                )
            }
            return
        }
        ensureWebViewAttached()
        if (!webView.isAttachedToWindow || !webView.isShown) {
            pendingRenderPage = page
            if (BuildConfig.DEBUG) {
                Log.i(
                        REFRESH_SCROLL_TAG,
                        "defer theme render key=$renderKey attached=${webView.isAttachedToWindow} parent=${webView.parent?.javaClass?.simpleName} visible=${webView.visibility} shown=${webView.isShown}"
                )
            }
            postOnActiveView {
                if (pendingRenderPage !== page) return@postOnActiveView
                pendingRenderPage = null
                renderThemePageSafely(page)
            }
            return
        }
        if (pendingRenderPage === page) {
            pendingRenderPage = null
        }
        if (renderedThemeOnce) {
            revealWebView("beforeRender")
        }
        val now = SystemClock.uptimeMillis()
        val isDuplicateRender = renderKey == lastRenderKey && now - lastRenderAt < 500L
        val forceRender = shouldForceThemeRender()
        if (isDuplicateRender && !forceRender && webController.hasCompletedRender(renderKey)) {
            if (BuildConfig.DEBUG) Log.i(REFRESH_SCROLL_TAG, "skip duplicate updateView key=$renderKey")
            TopicScrollTrace.render(
                    event = "skip_duplicate_render",
                    traceId = presenter.getThemeLoadTraceId(),
                    topicId = page.id,
                    renderGenerationId = renderCount,
                    viewRuntimeGeneration = viewRuntimeGeneration,
                    htmlLen = page.html?.length,
                    reason = "duplicate_render_key"
            )
            return
        }
        lastRenderKey = renderKey
        lastRenderAt = now
        val restoreMode = page.refreshRestoreMode
        val restoreId = page.refreshRestoreId
        if (renderedThemeOnce && !restoreId.isNullOrBlank() && (restoreMode == "ANCHOR" || restoreMode == "BOTTOM")) {
            showRenderTransitionOverlay(restoreId, restoreMode)
        }
        if (BuildConfig.DEBUG) {
            renderCount++
            Log.i(
                    REFRESH_SCROLL_TAG,
                    "fragment updateView count=$renderCount t=${SystemClock.uptimeMillis()} restoreId=$restoreId restoreMode=$restoreMode nativeY=${webView.scrollY} content=${webView.contentHeight} height=${webView.height} attached=${webView.isAttachedToWindow} visible=${webView.visibility} appBarY=${appBarLayout.translationY} swipe=${refreshLayout.isRefreshing} html=${page.html?.length ?: 0} loadedPage=${page.pagination.current}/${page.pagination.all}"
            )
        }
        webView.evaluateJavascript("if(typeof cancelThemeAnchorScrollRetries==='function'){cancelThemeAnchorScrollRetries();}", null)
        val shouldStartRender = forceRender || !webController.hasCompletedRender(renderKey)
        if (!shouldStartRender) return
        renderGuard.invalidate()
        themeBlankRetryCount = 0
        val renderStarted = webController.renderThemePage(page, force = true)
        TopicScrollTrace.render(
                event = if (renderStarted) "render_start" else "render_skipped",
                traceId = presenter.getThemeLoadTraceId(),
                topicId = page.id,
                renderGenerationId = renderCount,
                viewRuntimeGeneration = viewRuntimeGeneration,
                htmlLen = page.html?.length,
                contentHeight = if (::webView.isInitialized) webView.contentHeight else null,
                webViewHeight = if (::webView.isInitialized) webView.height else null,
                lifecycle = "updateView",
                extra = mapOf("restoreMode" to restoreMode, "restoreId" to restoreId, "renderKey" to renderKey)
        )
        if (!renderStarted) {
            revealThemeContentIfReady("renderSkipped")
            return
        }
        webView.alpha = 0f
        super.updateView(page)
        if (presenter.isEndNavigationPending()) {
            webView.postDelayed({
                if (!isAdded || view == null || !::webView.isInitialized) return@postDelayed
                webController.scheduleEndNavigationScrollIfNeeded("renderDeferred")
            }, 280L)
        }
        scheduleRenderCompletionWatchdog(renderKey, page)
        scheduleAlphaRevealSafetyWatchdog(renderKey, page)
        verifyThemeRenderedOrRetry()
        revealThemeContentIfReady("renderStarted")
        renderedThemeOnce = true
        postOnActiveWebView {
            syncBottomChromeSpacer()
        }
        if (::fabCoordinator.isInitialized) {
            fabCoordinator.onPageUpdated()
        }
        syncBottomChromeSpacer()
    }

    private fun ensureWebViewAttached() {
        if (!::webView.isInitialized) return
        val reattached = if (::webViewHost.isInitialized) {
            webViewHost.ensureWebViewAttached()
        } else {
            val parent = webView.parent
            var changed = false
            if (parent is ViewGroup && parent !== refreshLayout) {
                parent.removeView(webView)
                changed = true
            }
            if (webView.parent == null) {
                refreshLayout.addView(webView)
                changed = true
            }
            webView.visibility = View.VISIBLE
            refreshLayout.visibility = View.VISIBLE
            changed
        }
        if (reattached) {
            resetThemeRenderLifecycle("webViewReattached")
        }
        if (BuildConfig.DEBUG) {
            Log.i(
                    REFRESH_SCROLL_TAG,
                    "ensureWebViewAttached parent=${webView.parent?.javaClass?.simpleName} content=${webView.contentHeight}"
            )
        }
    }

    private fun shouldForceThemeRender(): Boolean {
        if (!::webView.isInitialized) return true
        val detached = webView.parent == null || !webView.isAttachedToWindow
        val blankContent = webView.contentHeight <= WEBVIEW_BLANK_CONTENT_HEIGHT_THRESHOLD
        val incomplete = !webController.hasCompletedRender(lastRenderKey.orEmpty())
        val hatOverlayPending = presenter.isHatOverlayReinjectionRender() ||
                presenter.hasPendingHatToolbarOverlayOpen()
        val force = detached || blankContent || incomplete || hatOverlayPending
        if (force && BuildConfig.DEBUG) {
            Log.i(
                    REFRESH_SCROLL_TAG,
                    "force theme render detached=$detached blankContent=$blankContent incomplete=$incomplete hatOverlayPending=$hatOverlayPending content=${webView.contentHeight} parent=${webView.parent?.javaClass?.simpleName} jsReady=${webView.isJsReady}"
            )
        }
        return force
    }

    private fun resetThemeRenderLifecycle(reason: String) {
        isTopicHatOverlayOpen = false
        hatToolbarOpenInFlight = false
        isTopicPollOverlayOpen = false
        clearToolbarProgrammaticScrollSuppress()
        renderWatchdogGeneration++
        alphaRevealWatchdogGeneration++
        blockingScrollStuckWatchdogGeneration++
        initialAnchorRevealAttempts = 0
        lastRenderKey = null
        lastRenderAt = 0L
        renderedThemeOnce = false
        renderGuard.invalidate()
        if (::webController.isInitialized) {
            webController.resetRenderState()
        }
        if (::webView.isInitialized) {
            webView.stopLoading()
            webView.loadUrl("about:blank")
        }
        if (BuildConfig.DEBUG) {
            Log.i(REFRESH_SCROLL_TAG, "reset theme render lifecycle reason=$reason")
        }
    }

    private fun currentRenderKey(page: ThemePage): String {
        val html = page.html.orEmpty()
        return "${presenter.getThemeLoadTraceId()}:${page.refreshRestoreId.orEmpty()}:${page.id}:${page.st}:${page.renderSignature.orEmpty()}:${html.length}:${html.hashCode()}"
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

    private fun showRenderTransitionOverlay(restoreId: String, restoreMode: String?) {
        if (webView.width <= 0 || webView.height <= 0 || webView.contentHeight <= 0) return
        val overlay = renderTransitionOverlay ?: View(requireContext()).apply {
            setBackgroundColor(requireContext().getColorFromAttr(R.attr.background_for_lists))
            alpha = 0f
            isClickable = false
            fragmentContent.addView(this, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            renderTransitionOverlay = this
        }
        renderTransitionRestoreId = restoreId
        overlay.bringToFront()
        bottomRefreshOverlay?.bringToFront()
        pageSwipeOverlay?.bringToFront()
        overlay.visibility = View.VISIBLE
        overlay.animate().cancel()
        overlay.alpha = 0.18f
        if (BuildConfig.DEBUG) Log.i(REFRESH_SCROLL_TAG, "transitionOverlay show id=$restoreId mode=$restoreMode")
    }

    private fun hideRenderTransitionOverlay(restoreId: String?, reason: String) {
        val overlay = renderTransitionOverlay ?: return
        if (restoreId != null && renderTransitionRestoreId != null && restoreId != renderTransitionRestoreId) {
            if (BuildConfig.DEBUG) Log.i(REFRESH_SCROLL_TAG, "transitionOverlay stale hide id=$restoreId current=$renderTransitionRestoreId reason=$reason")
            return
        }
        renderTransitionRestoreId = null
        overlay.animate().cancel()
        overlay.animate()
                .alpha(0f)
                .setDuration(90L)
                .withEndAction { overlay.visibility = View.GONE }
                .start()
        if (BuildConfig.DEBUG) Log.i(REFRESH_SCROLL_TAG, "transitionOverlay hide id=$restoreId reason=$reason")
    }

    private fun revealWebView(reason: String) {
        if (!::webView.isInitialized) return
        if (webView.visibility == View.VISIBLE && webView.alpha >= 1f) return
        webView.animate().cancel()
        webView.visibility = View.VISIBLE
        webView.alpha = 1f
        if (BuildConfig.DEBUG) Log.i(REFRESH_SCROLL_TAG, "webView reveal reason=$reason")
    }

    private fun postOnActiveView(delayMillis: Long = 0L, action: () -> Unit) {
        val generation = viewRuntimeGeneration
        val guarded = Runnable {
            if (generation != viewRuntimeGeneration || !isAdded || view == null) {
                if (generation != viewRuntimeGeneration) {
                    TopicScrollTrace.log(
                            event = "stale_callback_ignored",
                            traceId = presenter.getThemeLoadTraceId(),
                            topicId = presenter.getId().takeIf { it > 0 },
                            viewRuntimeGeneration = viewRuntimeGeneration,
                            reason = "view_generation_mismatch",
                            extra = mapOf("scheduledGeneration" to generation)
                    )
                }
                return@Runnable
            }
            action()
        }
        if (delayMillis > 0L) {
            viewHandler.postDelayed(guarded, delayMillis)
        } else {
            viewHandler.post(guarded)
        }
    }

    private fun postOnActiveWebView(delayMillis: Long = 0L, action: () -> Unit) {
        if (!::webView.isInitialized) return
        val target = webView
        val generation = viewRuntimeGeneration
        val guarded = Runnable {
            if (generation != viewRuntimeGeneration || !isAdded || view == null) return@Runnable
            if (!::webView.isInitialized || webView !== target) return@Runnable
            action()
        }
        if (delayMillis > 0L) {
            target.postDelayed(guarded, delayMillis)
        } else {
            target.post(guarded)
        }
    }

    fun updateShowAvatarState(isShow: Boolean) {
        if (!::webView.isInitialized) return
        webController.updateShowAvatarState(isShow)
    }

    fun updateTypeAvatarState(isCircle: Boolean) {
        if (!::webView.isInitialized) return
        webController.updateTypeAvatarState(isCircle)
    }

    fun updateScrollButtonState(isEnabled: Boolean) {
        isSmartScrollButtonEnabled = isEnabled
        uiBinder.updateScrollButtonState(isEnabled, fab)
        if (!isEnabled) {
            fab.visibility = View.GONE
            fab.isEnabled = false
            fab.isClickable = false
            fab.isFocusable = false
        }
        if (::fabCoordinator.isInitialized) {
            fabCoordinator.onFabVisibilityUpdated()
        }
    }

    private fun updateTopicScrollMode(mode: AppPreferences.Main.TopicScrollMode) {
        currentTopicScrollMode = mode
        if (BuildConfig.DEBUG) Log.i("HybridScroll", "mode updated mode=$mode pageLoaded=${presenter.isPageLoaded()}")
        updateTopicScrollModeSubtitle(mode)
        updateClassicOnlyGestures()
    }

    private fun updateTopicPageSwipeState(isEnabled: Boolean) {
        isTopicPageSwipeEnabled = isEnabled
        updateClassicOnlyGestures()
    }

    private fun updateBottomRefreshGestureState(isEnabled: Boolean) {
        isBottomRefreshGestureEnabled = isEnabled
        bottomRefreshController?.isEnabled = isEnabled
        if (!isEnabled) updateBottomRefreshOverlay(0f, canRelease = false, active = false)
        if (BuildConfig.DEBUG) Log.i("BottomRefresh", "gesture enabled=$isEnabled")
    }

    private fun updateClassicOnlyGestures() {
        val isClassic = currentTopicScrollMode == AppPreferences.Main.TopicScrollMode.CLASSIC
        if (view != null) {
            refreshLayout.isEnabled = false
        }
        pageSwipeDetector?.isEnabled = isClassic && isTopicPageSwipeEnabled
        if (!isClassic || !isTopicPageSwipeEnabled) hidePageSwipeOverlay("classicOnlyDisabled")
        if (BuildConfig.DEBUG) {
            Log.i(
                    "BottomRefresh",
                    "legacy SwipeRefreshLayout disabled; controller=${bottomRefreshController?.javaClass?.simpleName} enabled=$isBottomRefreshGestureEnabled mode=$currentTopicScrollMode pageSwipe=${pageSwipeDetector?.isEnabled}"
            )
        }
    }

    private fun triggerBottomOverscrollRefresh() {
        if (isTopicHatOverlayOpen || presenter.isTopicHatOpen()) {
            if (BuildConfig.DEBUG) {
                Log.i(
                        "BottomRefresh",
                        "bottomSwipe trigger blocked: hatOpen fragment=$isTopicHatOverlayOpen presenter=${presenter.isTopicHatOpen()}"
                )
            }
            return
        }
        if (!presenter.canRefreshFromBottomOverscroll()) return
        if (BuildConfig.DEBUG) {
            Log.i(
                    REFRESH_SCROLL_TAG,
                    "bottomSwipe trigger t=${SystemClock.uptimeMillis()} y=${webView.scrollY} content=${webView.contentHeight} height=${webView.height} appBarY=${appBarLayout.translationY} swipe=${refreshLayout.isRefreshing}"
            )
        }
        onThemeRefreshRequested("bottomSwipeRefresh")
    }

    override fun onThemeRefreshRequested(source: String) {
        if (!::webController.isInitialized || !::webView.isInitialized) {
            super.onThemeRefreshRequested(source)
            return
        }
        if (BuildConfig.DEBUG) {
            Log.i(
                    REFRESH_SCROLL_TAG,
                    "refreshRequested source=$source t=${SystemClock.uptimeMillis()} nativeY=${webView.scrollY} userScrollActive=${webView.isUserScrollActive()}"
            )
        }
        var reloadScheduled = false
        fun scheduleReload() {
            if (reloadScheduled) return
            reloadScheduled = true
            logRefreshTimeline(source, "beforeReload")
            postOnActiveWebView(delayMillis = 16L) {
                if (isAdded) {
                    if (source == "bottomSwipeRefresh") hideBottomRefreshOverlay("reloadStart")
                    presenter.reload(skipScrollCapture = true)
                    scheduleRefreshTimeline(source)
                }
            }
        }
        if (source == "bottomSwipeRefresh") {
            bottomRefreshRestorePending = true
            bottomRefreshRestoreId = presenter.beginBottomRefreshRestore()
            hideBottomRefreshOverlay("refreshRequested")
            webController.updateHistoryLastHtml(bottomRefreshRestoreId) {
                scheduleReload()
            }
        } else {
            val requestId = presenter.beginRefreshRequest(source, "ANCHOR")
            webController.updateHistoryLastHtml(requestId) {
                scheduleReload()
            }
        }
    }

    private fun scheduleRefreshTimeline(source: String) {
        if (!BuildConfig.DEBUG) return
        val delays = longArrayOf(80L, 240L, 600L, 1200L, 2000L)
        delays.forEach { delayMs ->
            postOnActiveWebView(delayMillis = delayMs) {
                logRefreshTimeline(source, "after+${delayMs}ms")
            }
        }
    }

    private fun logRefreshTimeline(source: String, phase: String) {
        if (!BuildConfig.DEBUG) return
        if (!::webView.isInitialized) return
        val nativeMax = ((webView.contentHeight * webView.scale).toInt() - webView.height).coerceAtLeast(0)
        Log.i(
                REFRESH_SCROLL_TAG,
                "timeline source=$source phase=$phase t=${SystemClock.uptimeMillis()} nativeY=${webView.scrollY} nativeMax=$nativeMax content=${webView.contentHeight} scale=${webView.scale} webViewHeight=${webView.height} bottomChrome=$currentBottomChromePadding swipe=${refreshLayout.isRefreshing} userScrollActive=${webView.isUserScrollActive()}"
        )
        if (!webView.isJsReady) return
        val script = """
            (function(){
                var doc=document.documentElement||{};
                var body=document.body||{};
                var spacer=document.getElementById('bottom_chrome_spacer');
                var spacerHeight=spacer?spacer.getBoundingClientRect().height:0;
                var scrollY=window.pageYOffset||doc.scrollTop||body.scrollTop||0;
                var scrollHeight=Math.max(doc.scrollHeight||0,body.scrollHeight||0);
                var clientHeight=window.innerHeight||doc.clientHeight||0;
                var containers=document.querySelectorAll('.theme_page_container[data-page-number]').length;
                var separators=document.querySelectorAll('.theme_page_separator[data-page-number]').length;
                var posts=document.querySelectorAll('.post_container[data-post-id]').length;
                var bottomPadding=(typeof bottomChromePadding!=='undefined'?bottomChromePadding:null);
                var messagePadding=(typeof messagePanelPadding!=='undefined'?messagePanelPadding:null);
                return JSON.stringify({scrollY:scrollY,scrollHeight:scrollHeight,clientHeight:clientHeight,maxScroll:Math.max(0,scrollHeight-clientHeight),spacerHeight:spacerHeight,bottomPadding:bottomPadding,messagePadding:messagePadding,loadAction:window.loadAction||'',containers:containers,separators:separators,posts:posts});
            })();
        """.trimIndent()
        webView.evaluateJavascript(script) { result ->
            if (!isAdded || view == null) return@evaluateJavascript
            val raw = result
                    ?.let { runCatching { JSONObject("{\"v\":$it}").optString("v") }.getOrNull() }
                    ?.takeIf { it.isNotEmpty() }
            Log.i(REFRESH_SCROLL_TAG, "timelineDom source=$source phase=$phase t=${SystemClock.uptimeMillis()} $raw")
        }
    }

    private fun requestHybridPageIfNearEdge(scrollY: Int) {
        if (currentTopicScrollMode != AppPreferences.Main.TopicScrollMode.HYBRID) return
        if (!::webView.isInitialized || !presenter.isPageLoaded()) return
        if (presenter.shouldBlockHybridUntilInitialAnchorSettled()) return

        val contentHeightPx = (webView.contentHeight * webView.scale).toInt()
        val viewportHeightPx = webView.height
        if (contentHeightPx <= 0 || viewportHeightPx <= 0) {
            logHybridEdgeThrottled("skip native edge: invalid size content=$contentHeightPx viewport=$viewportHeightPx scrollY=$scrollY")
            return
        }

        val bottomDistance = contentHeightPx - (scrollY + viewportHeightPx)
        when {
            scrollY <= hybridEdgeThresholdPx -> {
                logHybridEdgeThrottled("native edge top scrollY=$scrollY content=$contentHeightPx viewport=$viewportHeightPx")
                presenter.requestInfinitePage("top")
            }
            bottomDistance <= hybridEdgeThresholdPx -> {
                logHybridEdgeThrottled("native edge bottom distance=$bottomDistance content=$contentHeightPx viewport=$viewportHeightPx")
                presenter.requestInfinitePage("bottom")
            }
        }
    }

    private fun logHybridEdgeThrottled(message: String) {
        val now = System.currentTimeMillis()
        if (now - lastHybridEdgeLogAt < 700L) return
        lastHybridEdgeLogAt = now
        if (BuildConfig.DEBUG) Log.i("HybridScroll", message)
    }

    fun setFontSize(size: Int) {
        if (!::webView.isInitialized) return
        webController.setFontSize(size)
    }

    fun updateHistoryLastHtml() {
        // Эта операция формирует ОЧЕНЬ большой HTML и может фризить UI на некоторых устройствах.
        // Оставляем только scrollY + anchorPostId (достаточно для возврата назад), а HTML не сохраняем.
        if (!::webView.isInitialized) return
        if (bottomRefreshRestorePending) {
            if (BuildConfig.DEBUG) Log.i(REFRESH_SCROLL_TAG, "skip updateHistoryLastHtml: bottomRefreshRestorePending")
            return
        }
        webController.updateHistoryLastHtml()
    }

    private fun scheduleJumpToUnreadAfterTabSwitch() {
        if (!presenter.shouldScheduleUnreadJumpOnTabFocus()) return
        val runner = Runnable {
            if (!isAdded) return@Runnable
            val page = presenter.getCurrentPageInstance()
            val now = SystemClock.uptimeMillis()
            Timber.d(
                    "scheduleJumpToUnread: isPageLoaded=${presenter.isPageLoaded()} topic=${page?.id} unread=${page?.hasUnreadTarget} restore=${page?.refreshRestoreId}"
            )
            TopicScrollTrace.log(
                    event = "scroll_to_unread_scheduled",
                    traceId = presenter.getThemeLoadTraceId(),
                    topicId = page?.id,
                    viewRuntimeGeneration = viewRuntimeGeneration,
                    reason = if (page == null) "page_null" else if (page.refreshRestoreId != null) "restore_pending" else "tab_focus"
            )
            if (!presenter.isPageLoaded() || page == null) return@Runnable
            if (page.refreshRestoreId != null) return@Runnable
            if (page.id == lastTabUnreadJumpTopicId && now - lastTabUnreadJumpAt < TAB_UNREAD_JUMP_COOLDOWN_MS) return@Runnable
            lastTabUnreadJumpTopicId = page.id
            lastTabUnreadJumpAt = now
            TopicScrollTrace.log(
                    event = "scroll_to_unread_executed",
                    traceId = presenter.getThemeLoadTraceId(),
                    topicId = page.id,
                    viewRuntimeGeneration = viewRuntimeGeneration,
                    command = "loadNewPosts",
                    reason = "tab_focus"
            )
            presenter.loadNewPosts(fromTabSwitch = true)
        }
        postOnActiveView(delayMillis = TAB_UNREAD_JUMP_DELAY_MS) {
            lifecycleScope.launch {
                runner.run()
            }
        }
    }

    fun saveScrollYForImageViewer() {
        if (!::webView.isInitialized) return
        webController.saveScrollYForImageViewer()
    }

    override fun onResumeOrShow() {
        super.onResumeOrShow()
        uiBinder.onResumeMessagePanel()
        webController.restoreScrollYAfterImageViewer()
        // Fix: Force hide keyboard and reset IME insets after returning from search
        hideKeyboard()
        postOnActiveView {
            if (::webView.isInitialized) {
                webController.resetImeInsets(coordinatorLayout, messagePanelHost)
                // After returning from "Search in topic" (and back), windowVisibleDisplayFrame /
                // IME insets can stay reduced on some OEMs (visible "blank strip" at the bottom).
                // Force a full layout reset so refreshLayout / WebView / messagePanelHost are
                // aligned with the actual window state.
                webController.forceFullLayoutReset(coordinatorLayout, messagePanel, messagePanelHost, refreshLayout)
                syncBottomChromeSpacer()
                presenter.reconcileStuckLoadOnFragmentVisible()
                renderPendingThemePageIfReady()
                reconcileStuckThemeRender()
                presenter.getCurrentPageInstance()
                        ?.takeIf { !it.html.isNullOrBlank() && presenter.shouldPreserveCachedScrollOnTabShow() }
                        ?.let { page ->
                            renderThemePageSafely(page)
                        }
            }
        }
    }

    private fun reconcileStuckThemeRender() {
        val page = presenter.getCurrentPageInstance() ?: return
        if (page.html.isNullOrBlank()) return
        if (renderedThemeOnce && ::webController.isInitialized &&
                webController.hasCompletedRender(lastRenderKey.orEmpty())
        ) {
            return
        }
        renderThemePageSafely(page)
    }

    private fun renderPendingThemePageIfReady() {
        val page = pendingRenderPage ?: return
        ensureWebViewAttached()
        if (!::webView.isInitialized || !webView.isAttachedToWindow || !webView.isShown) return
        pendingRenderPage = null
        renderThemePageSafely(page)
    }

    override fun onBottomChromePaddingChanged(padding: Int) {
        super.onBottomChromePaddingChanged(padding)
        syncBottomChromeSpacer(padding)
    }

    override fun attachWebView(webView: ExtendedWebView) {
        super.attachWebView(webView)
        ensureWebViewAttached()
    }

    private fun syncBottomChromeSpacer(paddingOverride: Int? = null) {
        if (!::webController.isInitialized) return
        val chromePadding = paddingOverride ?: run {
            val rootInsets = ViewCompat.getRootWindowInsets(fragmentContainer)
            val baseBar = resources.getDimensionPixelSize(R.dimen.bottom_nav_tab_bar_height)
            BottomNavWindowInset.fragmentsBottomPaddingPx(
                    baseTabBarPx = baseBar,
                    rootInsets = rootInsets,
                    fallbackNavBottomPx = dimensionsProvider.getDimensions().navigationBar
            )
        }
        val isMessagePanelVisible = isMessagePanelReady && messagePanel.visibility == View.VISIBLE
        val spacerPadding = if (isMessagePanelVisible) {
            0
        } else {
            chromePadding + dp8
        }
        currentBottomChromePadding = chromePadding
        currentBottomChromeSpacerPadding = spacerPadding
        if (BuildConfig.DEBUG) {
            val rootInsets = ViewCompat.getRootWindowInsets(fragmentContainer)
            val systemInsetBottom = rootInsets?.getInsets(WindowInsetsCompat.Type.navigationBars())?.bottom ?: dimensionsProvider.getDimensions().navigationBar
            val bottomBarHeight = resources.getDimensionPixelSize(R.dimen.bottom_nav_tab_bar_height)
            Log.i(
                    THEME_INSETS_TAG,
                    "sync bottomBarHeight=$bottomBarHeight systemInsetBottom=$systemInsetBottom chromePadding=$chromePadding spacerHeight=$spacerPadding webViewH=${if (::webView.isInitialized) webView.height else 0} source=${if (paddingOverride != null) "activity" else "local"}"
            )
        }
        webController.setBottomChromePadding(spacerPadding)
        (bottomRefreshOverlay?.layoutParams as? FrameLayout.LayoutParams)?.let { params ->
            params.bottomMargin = chromePadding + dp8
            bottomRefreshOverlay?.layoutParams = params
        }
        syncMessagePanelImeWithDimensions()
    }

    override fun onPauseOrHide() {
        super.onPauseOrHide()
        topicTitlePopup?.dismiss()
        hideBottomRefreshOverlay("pauseOrHide")
        hidePageSwipeOverlay("pauseOrHide")
        // Сохраняем scrollY + anchorPostId в историю перед скрытием вкладки (открытие дочерней вкладки по ссылке),
        // чтобы при возврате восстановить позицию прокрутки.
        if (::webView.isInitialized) {
            webController.saveScrollYOnHide()
            webController.clearFocusAndHideKeyboard()
            hideKeyboard()
        }
        // Fix phantom white area: reset messagePanelHost padding and margin when leaving theme
        uiBinder.resetMessagePanelHostPadding()
    }

    override fun onDestroyView() {
        TopicScrollTrace.render(
                event = "view_destroy",
                traceId = presenter.getThemeLoadTraceId(),
                topicId = presenter.getId().takeIf { it > 0 },
                viewRuntimeGeneration = viewRuntimeGeneration,
                lifecycle = "onDestroyView"
        )
        viewRuntimeGeneration++
        viewHandler.removeCallbacksAndMessages(null)
        topicTitlePopup?.dismiss()
        topicTitlePopup = null
        hideBottomRefreshOverlay("destroyView")
        hidePageSwipeOverlay("destroyView")
        bottomRefreshOverlay?.let { overlay ->
            (overlay.parent as? ViewGroup)?.removeView(overlay)
        }
        pageSwipeOverlay?.let { overlay ->
            (overlay.parent as? ViewGroup)?.removeView(overlay)
        }
        bottomRefreshOverlay = null
        bottomRefreshProgress = null
        bottomRefreshLabel = null
        renderTransitionOverlay?.let { overlay ->
            (overlay.parent as? ViewGroup)?.removeView(overlay)
        }
        renderTransitionOverlay = null
        renderTransitionRestoreId = null
        pageSwipeOverlay = null
        pageSwipeProgress = null
        pageSwipeLabel = null
        pageSwipeDirectionLabel = null
        bottomRefreshRestorePending = false
        bottomRefreshRestoreId = null
        hiddenUntilFirstRestoreId = null
        lastTabUnreadJumpTopicId = -1
        lastTabUnreadJumpAt = 0L
        renderCount = 0
        pendingRenderPage = null
        resetThemeRenderLifecycle("destroyView")
        isMessagePanelReady = false
        if (::webView.isInitialized) {
            unregisterForContextMenu(webView)
            webView.setOnTouchListener(null)
            webView.animate().cancel()
        }
        bottomRefreshController = null
        pageSwipeDetector = null
        // Dispose all registered UI modules in reverse order (dependents before dependencies).
        moduleRegistry.disposeAll()
        super.onDestroyView()
    }

    override fun onDomContentComplete(actions: ArrayList<String>) {
        if (!webController.tryClaimDomLifecycle()) {
            if (BuildConfig.DEBUG) {
                Log.w(REFRESH_SCROLL_TAG, "domComplete ignored stale lifecycle trace=${presenter.getThemeLoadTraceId()}")
            }
            return
        }
        if (BuildConfig.DEBUG) Timber.d("DOMContentLoaded")
        val loadAction = presenter.loadAction
        val isEndNavigation = presenter.isEndNavigationPending()
        val scrollY = presenter.getPageScrollY()
        val anchorPostId = presenter.getAnchorPostId()
        val anchorOffsetTop = presenter.getAnchorOffsetTop()
        val scrollRatio = presenter.getScrollRatio()
        val wasNearBottom = presenter.wasNearBottomBeforeRefresh()
        val restoreId = presenter.getRefreshRestoreId()
        val restoreMode = presenter.getRefreshRestoreMode()
        val restoreSource = presenter.getRefreshRestoreSource()
        val density = webView.resources.displayMetrics.density
        syncBottomChromeSpacer()
        val bottomSpacerCssPx = currentBottomChromeSpacerPadding / density
        if (BuildConfig.DEBUG) {
            Timber.d("onDomContentComplete: loadAction=$loadAction scrollY=$scrollY density=$density anchorPostId=$anchorPostId")
            Log.i(
                    REFRESH_SCROLL_TAG,
                    "domComplete t=${SystemClock.uptimeMillis()} action=$loadAction restoreId=$restoreId restoreMode=$restoreMode source=$restoreSource nativeY=${webView.scrollY} savedY=$scrollY content=${webView.contentHeight} height=${webView.height} viewportH=${webView.height} bottomChrome=$currentBottomChromePadding spacerHeight=$currentBottomChromeSpacerPadding anchor=$anchorPostId offset=$anchorOffsetTop ratio=$scrollRatio wasNearBottom=$wasNearBottom appBarY=${appBarLayout.translationY}"
            )
        }
        if (loadAction == forpdateam.ru.forpda.presentation.theme.ThemeLoadAction.Back || !anchorPostId.isNullOrBlank()) {
            Log.i(
                    "ThemeHistory",
                    "dom restore fields action=$loadAction restoreId=$restoreId mode=$restoreMode source=$restoreSource postId=$anchorPostId currentAnchor=${presenter.getCurrentPageAnchor()} offset=$anchorOffsetTop y=$scrollY ratio=$scrollRatio bottom=$wasNearBottom"
            )
        }
        if (BuildConfig.DEBUG) {
            Timber.d(
                    "domContent trace=${presenter.getThemeLoadTraceId()} elem=${presenter.getCurrentPageAnchor()} url=${presenter.getCurrentPageUrl()} loadAction=$loadAction"
            )
        }
        actions.add(jsApi.setBottomChromePaddingInline(bottomSpacerCssPx))
        actions.add(jsApi.setTopChromePaddingInline(currentTopChromePaddingCssPx()))
        actions.add(jsApi.setLoadAction(
                if (isEndNavigation) {
                    forpdateam.ru.forpda.presentation.theme.ThemeLoadAction.End.toString()
                } else {
                    loadAction.toString()
                }
        ))
        val isRefreshNavigation = loadAction == forpdateam.ru.forpda.presentation.theme.ThemeLoadAction.Refresh
        val pageInstance = presenter.getCurrentPageInstance()
        val hasUnreadTarget = pageInstance?.hasUnreadTarget == true
        val suppressAmbiguousAllReadTopBootstrap =
                ThemeDomLoadAnchorPolicy.shouldSuppressAmbiguousAllReadInitialTopBootstrap(
                        ambiguousBottomRedirect = pageInstance?.ambiguousLastUnreadBottomRedirect == true,
                        hasUnreadTarget = hasUnreadTarget,
                )
        val shouldBlockScrollRestoreForUnread = !isEndNavigation && !isRefreshNavigation && (
                presenter.shouldSuppressScrollRestoreOnRender() ||
                        (hasUnreadTarget && restoreId.isNullOrBlank() && !presenter.isRefreshScrollRestoreActive())
        )
        TopicScrollTrace.render(
                event = "dom_content_complete",
                traceId = presenter.getThemeLoadTraceId(),
                topicId = presenter.getId().takeIf { it > 0 },
                renderGenerationId = renderCount,
                viewRuntimeGeneration = viewRuntimeGeneration,
                domReady = true,
                contentHeight = webView.contentHeight,
                webViewHeight = webView.height,
                extra = mapOf(
                        "loadAction" to loadAction.toString(),
                        "scrollY" to scrollY,
                        "anchorPostId" to anchorPostId,
                        "restoreId" to restoreId,
                        "restoreMode" to restoreMode,
                        "blockScrollRestoreForUnread" to shouldBlockScrollRestoreForUnread,
                        "hasUnreadTarget" to hasUnreadTarget
                )
        )
        actions.add(jsApi.setLoadScrollY(((if (shouldBlockScrollRestoreForUnread) 0 else scrollY) / density).toInt()))
        // Keep server anchors from topic redirects (p=, pid=, #entry, getlastpost) so DOM retries
        // can land on the intended post instead of falling back to the top.
        val currentAnchor = presenter.getCurrentPageAnchor()
        val endPage = presenter.getEndScrollTargetPage()
        val refreshPage = presenter.getCurrentPageInstance()
        val effectiveRestoreMode = if (isRefreshNavigation && refreshPage != null) {
            ThemeRefreshScrollRestorePolicy.effectiveRestoreMode(
                    requestedMode = restoreMode,
                    wasNearBottom = wasNearBottom,
                    scrollRatio = scrollRatio,
                    page = refreshPage,
                    scrollMode = currentTopicScrollMode
            )
        } else {
            restoreMode
        }
        val postedScrollAnchor = ThemePostedPageScrollPolicy.resolveDomScrollAnchor(
                pendingPostedAnchor = presenter.getPendingPostedPageScrollAnchor(),
                page = refreshPage,
                exactAnchor = presenter.isPendingPostedPageScrollExact()
        )
        val isPostedPageScroll = ThemePostedPageScrollPolicy.shouldApplyPostedScroll(postedScrollAnchor)
        val anchorToUse = when {
            isPostedPageScroll -> postedScrollAnchor.orEmpty()
            isEndNavigation -> {
                endPage?.let { ThemeSmartEndNavigation.resolveEndScrollAnchorPostId(it) }
                        ?: when {
                            !anchorPostId.isNullOrBlank() -> anchorPostId
                            !currentAnchor.isNullOrBlank() -> currentAnchor
                            else -> ""
                        }
            }
            isRefreshNavigation && effectiveRestoreMode == "BOTTOM" -> ""
            isRefreshNavigation && refreshPage != null -> {
                ThemeRefreshScrollRestorePolicy.resolveRefreshAnchorPostId(
                        page = refreshPage,
                        anchorPostId = anchorPostId?.takeIf { it.isNotBlank() }
                                ?: currentAnchor?.removePrefix("entry"),
                        wasNearBottom = wasNearBottom,
                        scrollRatio = scrollRatio,
                        scrollMode = currentTopicScrollMode
                ).orEmpty()
            }
            shouldBlockScrollRestoreForUnread ->
                ThemeDomLoadAnchorPolicy.resolveBlockedScrollRestoreAnchor(
                        anchorPostId = anchorPostId,
                        pageAnchor = currentAnchor,
                )
            anchorPostId.isNullOrBlank() && !currentAnchor.isNullOrBlank() -> currentAnchor
            else -> anchorPostId
        }
        actions.add(jsApi.setLoadAnchorUnreadTarget(hasUnreadTarget))
        actions.add(jsApi.setLoadAmbiguousAllReadBottom(suppressAmbiguousAllReadTopBootstrap))
        actions.add(jsApi.setLoadOpenSessionKind(pageInstance?.openSessionKind))
        actions.add(jsApi.setLoadAnchorPostId(anchorToUse ?: ""))
        actions.add(jsApi.setLoadAnchorOffsetTop(anchorOffsetTop))
        actions.add(jsApi.setLoadScrollRatio(if (shouldBlockScrollRestoreForUnread) null else scrollRatio))
        actions.add(jsApi.setLoadWasNearBottom(if (shouldBlockScrollRestoreForUnread) false else wasNearBottom))
        actions.add(jsApi.setRefreshRestoreRequest(if (shouldBlockScrollRestoreForUnread) null else restoreId, effectiveRestoreMode ?: restoreMode, restoreSource))
        // Reset hybrid runtime before scheduling end/restore scroll commands: running this after
        // executeThemeScrollCommand() used to call cancelThemeAnchorScrollRetries() and drop the
        // freshly armed smart-end retries (log: scrollY=0 → bootstrap requestInitialTop on last page).
        actions.add("if(typeof resetThemeRuntimeState==='function'){resetThemeRuntimeState();}")
        // Log 292: scheduleSoftLoadAnchorScroll must run AFTER resetThemeRuntimeState — reset calls
        // clearThemeRuntimeAsyncWork() and cancelled the 1ms soft-scroll timer (read topics stayed at y=0).
        if (ThemeDomLoadAnchorPolicy.shouldScheduleSoftAnchorScroll(
                        hasUnreadTarget = hasUnreadTarget,
                        loadAction = loadAction,
                        isEndNavigation = isEndNavigation,
                        isRefreshNavigation = isRefreshNavigation,
                        isPostedPageScroll = isPostedPageScroll,
                        anchorPostId = anchorToUse,
                )
        ) {
            ThemeDomLoadAnchorPolicy.normalizeAnchorPostId(anchorToUse)?.let { postId ->
                // Log 1122662: all-read bottom-redirect resolved to the last post of the last page.
                // Scroll to the BOTTOM of that final post (like END) instead of its top, otherwise a
                // tall final post strands the viewport mid-page (ratio ~0.66-0.89, bottom=false).
                if (pageInstance?.resumeToLastPageBottom == true) {
                    actions.add(jsApi.scheduleSoftLoadAnchorBottomScroll(postId))
                } else {
                    actions.add(jsApi.scheduleSoftLoadAnchorScroll(postId))
                }
            }
            actions.add(jsApi.clearUnreadAnchorHybridGuard("soft_anchor_scheduled"))
            // Soft anchor has no scroll-command completion callback — short sticky+fallback only.
            armToolbarSuppressForProgrammaticScroll()
        }
        if (isPostedPageScroll && !postedScrollAnchor.isNullOrBlank()) {
            actions.add(
                    webController.buildScrollCommandAction(
                            ThemeScrollCommand.endAnchorOrBottom(postedScrollAnchor)
                    )
            )
            presenter.clearPendingPostedPageScroll()
        } else if (isEndNavigation) {
            val endScrollPage = presenter.getEndScrollTargetPage()
            if (endScrollPage != null) {
                actions.add(
                        webController.buildScrollCommandAction(
                                ThemeSmartEndNavigation.endScrollCommand(endScrollPage)
                        )
                )
            }
        }
        // The bridge only accepts destructive calls from the latest completed trusted render.
        actions.add("window.__themeRenderToken=${JSONObject.quote(renderGuard.newToken())};")
        if (!shouldBlockScrollRestoreForUnread &&
                (effectiveRestoreMode == "ANCHOR" || effectiveRestoreMode == "BOTTOM") &&
                !restoreId.isNullOrBlank()
        ) {
            actions.add(
                    webController.buildScrollCommandAction(
                            ThemeScrollCommand.refreshRestore(restoreId, effectiveRestoreMode ?: restoreMode.orEmpty())
                    )
            )
        }
        if (loadAction == forpdateam.ru.forpda.presentation.theme.ThemeLoadAction.Normal &&
                !isEndNavigation &&
                !isRefreshNavigation &&
                !isPostedPageScroll &&
                presenter.shouldArmInitialAnchorOnPageComplete() &&
                !anchorToUse.isNullOrBlank()
        ) {
            actions.add(
                    webController.buildScrollCommandAction(ThemeScrollCommand.initialAnchor())
            )
        }
        if (shouldBlockScrollRestoreForUnread && !restoreId.isNullOrBlank()) {
            TopicOpenTrace.log(
                    TopicOpenContext(
                            rawUrl = presenter.getThemeUrl(),
                            setting = forpdateam.ru.forpda.common.Preferences.Main.TopicOpenTarget.LAST_UNREAD,
                            sourceScreen = "webview_dom",
                            openIntentRaw = presenter.getLastOpenIntent()
                    ),
                    TopicOpenResolution(
                            url = presenter.getThemeUrl(),
                            targetType = TopicOpenTargetType.SETTING_LAST_UNREAD,
                            reason = "dom_blocked_saved_scroll"
                    ),
                    TopicOpenTraceExtras(
                            event = "saved_scroll_overrode_unread",
                            traceId = presenter.getThemeLoadTraceId(),
                            renderGenerationId = renderCount,
                            refreshRestoreId = restoreId,
                            finalScrollY = scrollY,
                            scrollRestoreScheduled = true,
                            hasUnreadTarget = hasUnreadTarget,
                            openIntent = presenter.getLastOpenIntent(),
                            isFreshOpen = true,
                            savedScrollOverrodeUnread = true
                    )
            )
        }
        if (restoreMode == "TARGET_POST" && !restoreId.isNullOrBlank()) {
            hiddenUntilFirstRestoreId = restoreId
            if (BuildConfig.DEBUG) {
                Log.i(REFRESH_SCROLL_TAG, "targetRestore failOpen id=$restoreId mode=$restoreMode")
            }
        }
        webController.flushPendingScrollCommand()
        webController.scheduleMissedPageLifecycleProbe(400L, 1200L)
        webController.probeMissedPageLifecycleAfterDomVerified()
        postOnActiveWebView(600L) {
            if (::webView.isInitialized && webView.alpha < 1f && presenter.isPageLoaded()) {
                forceRevealIfDomHasPosts("domContentRevealSafety")
            }
        }
        if (shouldBlockScrollRestoreForUnread && hasUnreadTarget) {
            scheduleNativeUnreadAnchorScrollFallback(anchorToUse)
        }
    }

    private fun scheduleNativeUnreadAnchorScrollFallback(anchorPostId: String?) {
        val normalized = ThemeDomLoadAnchorPolicy.normalizeAnchorPostId(anchorPostId) ?: return
        if (presenter.getCurrentPageInstance()?.hasUnreadTarget != true) return
        val generation = ++nativeUnreadAnchorFallbackGeneration
        val traceId = presenter.getThemeLoadTraceId()
        val anchorName = "entry$normalized"
        postOnActiveWebView(NATIVE_UNREAD_ANCHOR_FALLBACK_DELAY_MS) {
            if (!isAdded || view == null || !::webView.isInitialized) return@postOnActiveWebView
            if (generation != nativeUnreadAnchorFallbackGeneration) return@postOnActiveWebView
            if (traceId != presenter.getThemeLoadTraceId()) return@postOnActiveWebView
            if (presenter.getCurrentPageInstance()?.hasUnreadTarget != true) return@postOnActiveWebView
            val nearTopThreshold = (48 * resources.displayMetrics.density).toInt()
            if (webView.scrollY > nearTopThreshold &&
                    !presenter.shouldBlockHybridUntilInitialAnchorSettled()
            ) {
                return@postOnActiveWebView
            }
            webView.evaluateJavascript(jsApi.nativeScrollToAnchorPost(anchorName)) { result ->
                if (!isAdded || view == null || !::webView.isInitialized) return@evaluateJavascript
                val cssY = result?.trim()?.trim('"')?.toDoubleOrNull()
                if (cssY != null && cssY >= 0) {
                    val nativeY = (cssY * resources.displayMetrics.density).toInt()
                    if (webView.scrollY <= nearTopThreshold) {
                        webView.scrollTo(0, nativeY.coerceAtLeast(0))
                    }
                }
                if (presenter.shouldBlockHybridUntilInitialAnchorSettled()) {
                    presenter.releaseUnreadAnchorHybridGuard("native_anchor_fallback")
                }
                revealThemeContentIfReady("nativeAnchorFallback")
            }
        }
    }

    override fun onPageComplete(actions: ArrayList<String>) {
        if (!webController.tryClaimPageLifecycle()) {
            if (BuildConfig.DEBUG) {
                Log.w(
                        REFRESH_SCROLL_TAG,
                        "pageComplete ignored stale lifecycle trace=${presenter.getThemeLoadTraceId()} action=${presenter.loadAction}"
                )
            }
            return
        }
        val wasBack = presenter.loadAction == forpdateam.ru.forpda.presentation.theme.ThemeLoadAction.Back
        val wasRefresh = presenter.loadAction == forpdateam.ru.forpda.presentation.theme.ThemeLoadAction.Refresh
        val wasEnd = presenter.isEndNavigationPending()
        val shouldScrollToBottom = wasEnd
        val restoreId = presenter.getRefreshRestoreId()
        val restoreMode = presenter.getRefreshRestoreMode()
        val restoreSource = presenter.getRefreshRestoreSource()
        val endAnchorPostId = if (wasEnd) {
            presenter.getEndScrollTargetPage()?.let { ThemeSmartEndNavigation.resolveEndScrollAnchorPostId(it) }
                    ?: presenter.getAnchorPostId()?.takeIf { it.isNotBlank() }
                    ?: presenter.getCurrentPageAnchor()?.takeIf { it.isNotBlank() }
        } else {
            null
        }
        syncBottomChromeSpacer()
        val bottomSpacerCssPx = currentBottomChromeSpacerPadding / webView.resources.displayMetrics.density
        val initialAnchorCommand = if (!wasBack && !wasRefresh && !wasEnd &&
                presenter.shouldArmInitialAnchorOnPageComplete()
        ) {
            ThemeScrollCommand.initialAnchor().also { presenter.beginScrollCommand(it) }
        } else {
            null
        }
        if (BuildConfig.DEBUG) {
            Log.i(
                    REFRESH_SCROLL_TAG,
                    "pageComplete t=${SystemClock.uptimeMillis()} action=${presenter.loadAction} restoreId=$restoreId restoreMode=$restoreMode source=$restoreSource nativeY=${webView.scrollY} content=${webView.contentHeight} height=${webView.height} viewportH=${webView.height} bottomChrome=$currentBottomChromePadding spacerHeight=$currentBottomChromeSpacerPadding bottomPending=$bottomRefreshRestorePending appBarY=${appBarLayout.translationY} swipe=${refreshLayout.isRefreshing}"
            )
        }
        webController.onDomRendered()
        revealThemeContentIfReady("pageComplete")
        hideBottomRefreshOverlay("pageComplete")
        hidePageSwipeOverlay("pageComplete")
        presenter.getCurrentPageInstance()?.let { page ->
            presenter.onRenderedTopicPage(page)
        }
        if (presenter.consumePendingHatToolbarOpenAfterRender()) {
            dispatchOpenHatOverlayJs()
        }
        presenter.resetLoadAction()
        val themeDiag = BuildConfig.DEBUG
        if (themeDiag) {
            Timber.d(
                    "pageLoad trace=${presenter.getThemeLoadTraceId()} elem=${presenter.getCurrentPageAnchor()} retriesMs=SCROLL_ANCHOR_RETRY_DELAYS_MS"
            )
        }
        actions.add(jsApi.setBottomChromePaddingInline(bottomSpacerCssPx))
        actions.add(jsApi.setTopChromePaddingInline(currentTopChromePaddingCssPx()))
        actions.add(jsApi.setThemeScrollAnchorDiag(themeDiag))
        val blockScrollRestoreForUnread = !wasRefresh && presenter.shouldSuppressScrollRestoreOnRender()
        actions.add(
                jsApi.setRefreshRestoreRequest(
                        if (blockScrollRestoreForUnread) null else restoreId,
                        restoreMode,
                        restoreSource
                )
        )
        if (!blockScrollRestoreForUnread &&
                !wasEnd &&
                (restoreMode == "ANCHOR" || restoreMode == "BOTTOM") &&
                !restoreId.isNullOrBlank()
        ) {
            actions.add(
                    webController.buildScrollCommandAction(
                            ThemeScrollCommand.refreshRestore(restoreId, restoreMode)
                    )
            )
        }
        if (!wasBack && !wasRefresh && !wasEnd &&
                presenter.shouldArmInitialAnchorOnPageComplete()
        ) {
            val command = initialAnchorCommand ?: ThemeScrollCommand.initialAnchor()
            actions.add(webController.buildScrollCommandAction(command))
        }
        if (presenter.isHatOverlayReinjectionRender()) {
            val reinjectionScrollY = presenter.getPageScrollY()
            if (reinjectionScrollY > 0) {
                webView.post {
                    if (!isAdded || view == null || !::webView.isInitialized) return@post
                    webView.scrollTo(0, reinjectionScrollY)
                    presenter.onHatOverlayReinjectionScrollRestored()
                }
            } else {
                presenter.onHatOverlayReinjectionScrollRestored()
            }
        }
        if (shouldScrollToBottom) {
            val endPage = presenter.getEndScrollTargetPage()
            actions.add(
                    webController.buildScrollCommandAction(
                            if (endPage != null) {
                                ThemeSmartEndNavigation.endScrollCommand(endPage)
                            } else {
                                ThemeSmartEndNavigation.endScrollCommand(endAnchorPostId)
                            }
                    )
            )
            actions.add(jsApi.setLoadAction(forpdateam.ru.forpda.presentation.theme.ThemeLoadAction.End.toString()))
        } else {
            actions.add(jsApi.setLoadAction(forpdateam.ru.forpda.presentation.theme.ThemeLoadAction.Normal.toString()))
        }
        presenter.onTopicRenderSettled()
        if (restoreMode == "TARGET_POST" && !restoreId.isNullOrBlank()) {
            postOnActiveWebView(delayMillis = 450L) {
                if (hiddenUntilFirstRestoreId == restoreId) {
                    webView.animate().alpha(1f).setDuration(90L).start()
                    hiddenUntilFirstRestoreId = null
                    hideRenderTransitionOverlay(restoreId, "targetFallbackReveal")
                    presenter.completeRefreshRestore(restoreId)
                    if (BuildConfig.DEBUG) Log.i(REFRESH_SCROLL_TAG, "fallbackReveal id=$restoreId")
                }
            }
        }
        if ((restoreMode == "ANCHOR" || restoreMode == "BOTTOM") && !restoreId.isNullOrBlank()) {
            postOnActiveWebView(delayMillis = 700L) {
                val completeReason = if (restoreMode == "BOTTOM") "bottomRestoreComplete" else "anchorRestoreComplete"
                hideRenderTransitionOverlay(restoreId, completeReason)
                presenter.completeRefreshRestore(restoreId)
                if (bottomRefreshRestoreId == restoreId) {
                    bottomRefreshRestorePending = false
                    bottomRefreshRestoreId = null
                    hideBottomRefreshOverlay("restoreComplete")
                }
                if (BuildConfig.DEBUG) Log.i(REFRESH_SCROLL_TAG, "$completeReason id=$restoreId")
            }
        }
        actions.add("if(typeof initThemeInfiniteScroll==='function'){initThemeInfiniteScroll();}")
        actions.add("if(typeof refreshThemeDynamicPostBlocks==='function'){refreshThemeDynamicPostBlocks(document);}")
        verifyThemeRenderedOrRetry()
        if (blockScrollRestoreForUnread || wasRefresh) {
            presenter.onUnreadOpenRenderCompleted()
        }
        if (blockScrollRestoreForUnread && presenter.getCurrentPageInstance()?.hasUnreadTarget == true) {
            val anchor = presenter.getAnchorPostId()?.takeIf { it.isNotBlank() }
                    ?: presenter.getCurrentPageAnchor()
            scheduleNativeUnreadAnchorScrollFallback(anchor)
        }
    }

    private fun renderWatchdogDelayMs(htmlLen: Int): Long {
        return when {
            htmlLen >= THEME_RENDER_WATCHDOG_HUGE_HTML_LEN -> THEME_RENDER_WATCHDOG_HUGE_HTML_DELAY_MS
            htmlLen >= THEME_RENDER_WATCHDOG_LARGE_HTML_LEN -> THEME_RENDER_WATCHDOG_LARGE_HTML_DELAY_MS
            else -> THEME_RENDER_WATCHDOG_BASE_DELAY_MS
        }
    }

    private fun expectedListPostsForReveal(page: ThemePage?): Int {
        val listPostsInHtml = ThemeHtmlMetrics.countListPostContainers(page?.html)
        if (listPostsInHtml > 0) return listPostsInHtml
        val parsedPosts = page?.posts?.size ?: 0
        if (parsedPosts > 0) return parsedPosts
        return 0
    }

    private fun scheduleRenderCompletionWatchdog(renderKey: String, page: ThemePage) {
        val expectedPosts = expectedListPostsForReveal(page)
        if (expectedPosts == 0) return
        val generation = ++renderWatchdogGeneration
        val delayMs = renderWatchdogDelayMs(page.html?.length ?: 0)
        webView.postDelayed({
            if (!isAdded || view == null || !::webView.isInitialized) return@postDelayed
            if (generation != renderWatchdogGeneration) return@postDelayed
            if (renderKey != lastRenderKey) return@postDelayed
            if (webController.hasCompletedRender(renderKey)) {
                revealThemeContentIfReady("renderWatchdogAlreadyComplete")
                return@postDelayed
            }
            if (BuildConfig.DEBUG) {
                Log.w(
                        REFRESH_SCROLL_TAG,
                        "render watchdog trigger trace=${presenter.getThemeLoadTraceId()} key=$renderKey content=${webView.contentHeight} jsReady=${webView.isJsReady} delayMs=$delayMs"
                )
            }
            forceRevealIfDomHasPosts("renderWatchdogDomProbe")
            webController.probeMissedFullLifecycleIfStuck()
            verifyThemeRenderedOrRetry()
        }, delayMs)
    }

    private fun scheduleAlphaRevealSafetyWatchdog(renderKey: String, page: ThemePage) {
        val expectedPosts = expectedListPostsForReveal(page)
        if (expectedPosts == 0) return
        val generation = ++alphaRevealWatchdogGeneration
        webView.postDelayed({
            if (!isAdded || view == null || !::webView.isInitialized) return@postDelayed
            if (generation != alphaRevealWatchdogGeneration) return@postDelayed
            if (renderKey != lastRenderKey) return@postDelayed
            if (webView.alpha >= 1f) return@postDelayed
            if (!presenter.isPageLoaded()) return@postDelayed
            forceRevealIfDomHasPosts("alphaRevealSafety")
        }, THEME_ALPHA_REVEAL_SAFETY_DELAY_MS)
    }

    private fun forceRevealIfDomHasPosts(reason: String) {
        if (!::webView.isInitialized) return
        webView.evaluateJavascript(
                "(function(){return document.querySelectorAll('.post_container[data-post-id]').length;})();"
        ) { raw ->
            if (!isAdded || view == null) return@evaluateJavascript
            val domPosts = raw?.trim()?.removeSurrounding("\"")?.toIntOrNull() ?: 0
            if (domPosts <= 0) return@evaluateJavascript
            webController.markRenderVerifiedFromDom(domPosts)
            webController.probeMissedFullLifecycleIfStuck()
            revealThemeContentIfReady(reason, domPostsVerified = true)
        }
    }

    private fun revealThemeContentIfReady(reason: String, domPostsVerified: Boolean = false) {
        if (!::webView.isInitialized) return
        val page = presenter.getCurrentPageInstance()
        val expectedPosts = expectedListPostsForReveal(page)
        val renderKey = lastRenderKey.orEmpty()
        val renderComplete = renderKey.isNotEmpty() && webController.hasCompletedRender(renderKey)
        val expectsInitialAnchorScroll = presenter.expectsInitialAnchorScrollOnOpen()
        val hasUnreadTarget = page?.hasUnreadTarget == true
        val safetyFallbackReveal = ThemeOpenScrollCoalescePolicy.isSafetyFallbackRevealReason(reason)
        if (ThemeOpenScrollCoalescePolicy.shouldDeferWebViewReveal(
                        hasBlockingScrollPending = presenter.hasBlockingScrollPending(),
                        expectedPosts = expectedPosts,
                        contentHeight = webView.contentHeight,
                        blankContentThreshold = WEBVIEW_BLANK_CONTENT_HEIGHT_THRESHOLD,
                        renderCompleteForActiveKey = renderComplete,
                        domPostsVerified = domPostsVerified,
                        expectsInitialAnchorScroll = expectsInitialAnchorScroll,
                        safetyFallbackReveal = safetyFallbackReveal,
                        blockingScrollKind = presenter.getPendingScrollCommand()?.kind,
                        hasUnreadTarget = hasUnreadTarget,
                        primaryOpenComplete = presenter.isPrimaryOpenComplete(),
                )
        ) {
            if (BuildConfig.DEBUG) {
                Log.i(
                        REFRESH_SCROLL_TAG,
                        "defer webView reveal reason=$reason content=${webView.contentHeight} expectedPosts=$expectedPosts blockingScroll=${presenter.hasBlockingScrollPending()} renderComplete=$renderComplete domPostsVerified=$domPostsVerified expectsInitialAnchor=$expectsInitialAnchorScroll safetyFallback=$safetyFallbackReveal"
                )
            }
            if (presenter.hasBlockingScrollPending() && renderComplete && renderKey.isNotEmpty()) {
                scheduleBlockingScrollStuckRevealIfNeeded(renderKey)
            } else if (expectsInitialAnchorScroll && renderComplete && renderKey.isNotEmpty()) {
                scheduleInitialAnchorGuardReleaseIfNeeded(renderKey)
            }
            return
        }
        if (safetyFallbackReveal && presenter.hasBlockingScrollPending()) {
            val pendingKind = presenter.getPendingScrollCommand()?.kind
            if (ThemeUnreadHybridAnchorGuardPolicy.shouldAbandonBlockingScrollForSafetyReveal(pendingKind)) {
                presenter.abandonBlockingScrollForSafetyReveal(reason)
            }
        }
        revealWebView(reason)
        hideInitialLoadingIndicator()
    }

    private fun scheduleInitialAnchorGuardReleaseIfNeeded(renderKey: String) {
        val generation = ++initialAnchorGuardWatchdogGeneration
        val delayMs = ThemeUnreadHybridAnchorGuardPolicy.ANCHOR_GUARD_MAX_BLOCK_MS
        webView.postDelayed({
            if (!isAdded || view == null || !::webView.isInitialized) return@postDelayed
            if (generation != initialAnchorGuardWatchdogGeneration) return@postDelayed
            if (renderKey != lastRenderKey) return@postDelayed
            if (!presenter.expectsInitialAnchorScrollOnOpen()) return@postDelayed
            if (presenter.hasBlockingScrollPending()) return@postDelayed
            Log.i(
                    ThemeUnreadHybridAnchorGuardPolicy.LOG_TAG,
                    "anchor_guard_timeout trace=${presenter.getThemeLoadTraceId()}"
            )
            presenter.releaseUnreadAnchorHybridGuard("render_watchdog_timeout")
            revealThemeContentIfReady("anchorGuardTimeout")
        }, delayMs)
    }

    private fun scheduleBlockingScrollStuckRevealIfNeeded(renderKey: String) {
        val pendingKind = presenter.getPendingScrollCommand()?.kind
        val delayMs = ThemeUnreadHybridAnchorGuardPolicy.scrollStuckRevealDelayMs(pendingKind)
        val generation = ++blockingScrollStuckWatchdogGeneration
        val anchorAttempt = if (pendingKind == ThemeScrollCommand.Kind.INITIAL_ANCHOR) {
            ++initialAnchorRevealAttempts
        } else {
            0
        }
        webView.postDelayed({
            if (!isAdded || view == null || !::webView.isInitialized) return@postDelayed
            if (generation != blockingScrollStuckWatchdogGeneration) return@postDelayed
            if (renderKey != lastRenderKey) return@postDelayed
            if (webView.alpha >= 1f) return@postDelayed
            if (!presenter.isPageLoaded()) return@postDelayed
            if (!webController.hasCompletedRender(renderKey)) return@postDelayed
            if (!presenter.hasBlockingScrollPending()) {
                revealThemeContentIfReady("scrollSettled")
                return@postDelayed
            }
            val blockingKind = presenter.getPendingScrollCommand()?.kind
            if (!ThemeUnreadHybridAnchorGuardPolicy.shouldAbandonBlockingScrollForSafetyReveal(blockingKind) &&
                    anchorAttempt < MAX_INITIAL_ANCHOR_REVEAL_ATTEMPTS
            ) {
                scheduleBlockingScrollStuckRevealIfNeeded(renderKey)
                return@postDelayed
            }
            if (blockingKind == ThemeScrollCommand.Kind.INITIAL_ANCHOR) {
                Log.i(
                        ThemeUnreadHybridAnchorGuardPolicy.LOG_TAG,
                        "anchor_reveal_timeout attempts=$anchorAttempt trace=${presenter.getThemeLoadTraceId()}"
                )
            }
            presenter.abandonBlockingScrollForSafetyReveal("scrollStuckReveal")
            forceRevealIfDomHasPosts("scrollStuckReveal")
        }, delayMs)
    }

    private fun verifyThemeRenderedOrRetry() {
        val page = presenter.getCurrentPageInstance() ?: return
        val expectedPosts = expectedListPostsForReveal(page)
        if (expectedPosts == 0) return
        webView.postDelayed({
            if (!isAdded || view == null || !::webView.isInitialized) return@postDelayed
            webView.evaluateJavascript(
                    "(function(){return document.querySelectorAll('.post_container[data-post-id]').length;})();"
            ) { raw ->
                if (!isAdded || view == null) return@evaluateJavascript
                val domPosts = raw?.trim()?.removeSurrounding("\"")?.toIntOrNull() ?: 0
                val looksBlank = domPosts == 0 &&
                        webView.contentHeight <= WEBVIEW_BLANK_CONTENT_HEIGHT_THRESHOLD
                if (!looksBlank) {
                    themeBlankRetryCount = 0
                    webController.markRenderVerifiedFromDom(domPosts)
                    webController.probeMissedFullLifecycleIfStuck()
                    revealThemeContentIfReady("blankVerifyOk", domPostsVerified = true)
                    webController.probeMissedDomLifecycleAfterRender()
                    return@evaluateJavascript
                }
                if (themeBlankRetryCount >= MAX_THEME_BLANK_RENDER_RETRIES) return@evaluateJavascript
                themeBlankRetryCount++
                if (BuildConfig.DEBUG) {
                    Log.w(
                            REFRESH_SCROLL_TAG,
                            "Topic WebView looks blank, retry=$themeBlankRetryCount id=${page.id} htmlLen=${page.html?.length ?: 0} contentHeight=${webView.contentHeight}"
                    )
                }
                webController.resetRenderState()
                renderThemePageSafely(page)
            }
        }, THEME_BLANK_RENDER_VERIFY_DELAY_MS)
    }

    /** Adapter that bridges [ThemeBridgeHandler] into the [ThemeUiModule] contract. */
    private class ThemeBridgeModuleAdapter(
        private val handler: ThemeBridgeHandler
    ) : ThemeUiModule {
        override fun init() = handler.init()
        override fun dispose() = handler.cleanup()
    }

    fun deletePostUi(post: IBaseForumPost) {
        webController.deletePostUi(post)
    }

    fun openAnchorDialog(post: IBaseForumPost, anchorName: String) {
        dialogsHelper.openAnchorDialog(presenter, post, anchorName)
    }

    fun openSpoilerLinkDialog(post: IBaseForumPost, spoilNumber: String) {
        dialogsHelper.openSpoilerLinkDialog(presenter, post, spoilNumber)
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    presenter.uiEvents.collect { event ->
                        handleUiEvent(event)
                    }
                }
            }
        }
    }

    private fun handleUiEvent(event: forpdateam.ru.forpda.presentation.theme.ThemeUiEvent) {
        when (event) {
            is forpdateam.ru.forpda.presentation.theme.ThemeUiEvent.UpdateShowAvatarState -> updateShowAvatarState(event.show)
            is forpdateam.ru.forpda.presentation.theme.ThemeUiEvent.UpdateTypeAvatarState -> updateTypeAvatarState(event.circle)
            is forpdateam.ru.forpda.presentation.theme.ThemeUiEvent.UpdateScrollButtonState -> updateScrollButtonState(event.enabled)
            is forpdateam.ru.forpda.presentation.theme.ThemeUiEvent.UpdateTopicPaginationPanelState -> updateTopicPaginationPanelState(event.enabled)
            is forpdateam.ru.forpda.presentation.theme.ThemeUiEvent.UpdateTopicScrollMode -> updateTopicScrollMode(event.mode)
            is forpdateam.ru.forpda.presentation.theme.ThemeUiEvent.UpdateTopicPostDensity -> updateTopicPostDensityChrome(event.density)
            is forpdateam.ru.forpda.presentation.theme.ThemeUiEvent.UpdateTopicToolbarBehavior -> updateTopicToolbarBehaviorChrome(event.behavior)
            is forpdateam.ru.forpda.presentation.theme.ThemeUiEvent.UpdateTopicPageSwipeState -> updateTopicPageSwipeState(event.enabled)
            is forpdateam.ru.forpda.presentation.theme.ThemeUiEvent.UpdateBottomRefreshGestureState -> updateBottomRefreshGestureState(event.enabled)
            is forpdateam.ru.forpda.presentation.theme.ThemeUiEvent.UpdateHatOpenState -> onTopicHatOpenStateChanged(event.open)
            is forpdateam.ru.forpda.presentation.theme.ThemeUiEvent.UpdatePollOpenState -> onTopicPollOpenStateChanged(event.open)
            is forpdateam.ru.forpda.presentation.theme.ThemeUiEvent.RefreshToolbarMenu -> refreshToolbarMenuItems(true)
            is forpdateam.ru.forpda.presentation.theme.ThemeUiEvent.UpdateTopicToolbar -> applyTopicToolbarState(event.page)
            is forpdateam.ru.forpda.presentation.theme.ThemeUiEvent.ClearUnreadAnchorHybridGuard -> {
                if (::webView.isInitialized) {
                    webView.evaluateJavascript(jsApi.clearUnreadAnchorHybridGuard(event.reason), null)
                }
            }
            is forpdateam.ru.forpda.presentation.theme.ThemeUiEvent.SetFontSize -> setFontSize(event.size)
            is forpdateam.ru.forpda.presentation.theme.ThemeUiEvent.SetAppFontMode -> setAppFontMode(event.mode)
            is forpdateam.ru.forpda.presentation.theme.ThemeUiEvent.SetStyleType -> setStyleType(event.styleType)
            is forpdateam.ru.forpda.presentation.theme.ThemeUiEvent.OnEventNew -> onEventNew(event.event)
            is forpdateam.ru.forpda.presentation.theme.ThemeUiEvent.OnEventRead -> onEventRead(event.event)
            is forpdateam.ru.forpda.presentation.theme.ThemeUiEvent.UpdateHistoryLastHtml -> updateHistoryLastHtml()
            is forpdateam.ru.forpda.presentation.theme.ThemeUiEvent.SyncEditPost -> syncEditPost(event.sync)
            is forpdateam.ru.forpda.presentation.theme.ThemeUiEvent.OnUploadFiles -> onUploadFiles(event.items)
            is forpdateam.ru.forpda.presentation.theme.ThemeUiEvent.OnDeleteFiles -> onDeleteFiles(event.items)
            is forpdateam.ru.forpda.presentation.theme.ThemeUiEvent.FirstPage -> firstPage()
            is forpdateam.ru.forpda.presentation.theme.ThemeUiEvent.PrevPage -> prevPage()
            is forpdateam.ru.forpda.presentation.theme.ThemeUiEvent.NextPage -> nextPage()
            is forpdateam.ru.forpda.presentation.theme.ThemeUiEvent.LastPage -> lastPage()
            is forpdateam.ru.forpda.presentation.theme.ThemeUiEvent.SelectPage -> selectPage()
            is forpdateam.ru.forpda.presentation.theme.ThemeUiEvent.SelectPageInput -> selectPageInput()
            is forpdateam.ru.forpda.presentation.theme.ThemeUiEvent.ShowUserMenu -> showUserMenu(event.post)
            is forpdateam.ru.forpda.presentation.theme.ThemeUiEvent.ShowReputationMenu -> showReputationMenu(event.post)
            is forpdateam.ru.forpda.presentation.theme.ThemeUiEvent.ShowPostMenu -> showPostMenu(event.post)
            is forpdateam.ru.forpda.presentation.theme.ThemeUiEvent.ReportPost -> reportPost(event.post)
            is forpdateam.ru.forpda.presentation.theme.ThemeUiEvent.InsertText -> insertText(event.text)
            is forpdateam.ru.forpda.presentation.theme.ThemeUiEvent.DeletePost -> deletePost(event.post)
            is forpdateam.ru.forpda.presentation.theme.ThemeUiEvent.EditPost -> editPost(event.post)
            is forpdateam.ru.forpda.presentation.theme.ThemeUiEvent.VotePost -> votePost(event.post, event.type)
            is forpdateam.ru.forpda.presentation.theme.ThemeUiEvent.OpenSpoilerLinkDialog -> openSpoilerLinkDialog(event.post, event.spoilNumber)
            is forpdateam.ru.forpda.presentation.theme.ThemeUiEvent.OpenAnchorDialog -> openAnchorDialog(event.post, event.name)
            is forpdateam.ru.forpda.presentation.theme.ThemeUiEvent.Log -> log(event.text)
            is forpdateam.ru.forpda.presentation.theme.ThemeUiEvent.ScrollToAnchor -> scrollToAnchor(event.anchor)
            is forpdateam.ru.forpda.presentation.theme.ThemeUiEvent.SaveScrollYForImageViewer -> saveScrollYForImageViewer()
            is forpdateam.ru.forpda.presentation.theme.ThemeUiEvent.ShowDeleteInFavDialog -> showDeleteInFavDialog(event.page)
            is forpdateam.ru.forpda.presentation.theme.ThemeUiEvent.ShowAddInFavDialog -> showAddInFavDialog(event.page)
            is forpdateam.ru.forpda.presentation.theme.ThemeUiEvent.ShowChangeReputation -> showChangeReputation(event.post, event.type)
            is forpdateam.ru.forpda.presentation.theme.ThemeUiEvent.DeletePostUi -> deletePostUi(event.post)
            is forpdateam.ru.forpda.presentation.theme.ThemeUiEvent.ShowNoteCreate -> showNoteCreate(event.title, event.url)
            is forpdateam.ru.forpda.presentation.theme.ThemeUiEvent.ShowError -> {
                hideInitialLoadingIndicator()
                showThemeLoadErrorState(event.message)
            }
            is forpdateam.ru.forpda.presentation.theme.ThemeUiEvent.ShowSnackbar -> showSnackbar(event.message)
            is forpdateam.ru.forpda.presentation.theme.ThemeUiEvent.ShowAllReadHint -> showSnackbar(getString(R.string.theme_all_read_hint))
            is forpdateam.ru.forpda.presentation.theme.ThemeUiEvent.ApplyInfinitePage -> {
                if (presenter.shouldBlockHybridUntilInitialAnchorSettled()) {
                    Log.i(
                            ThemeUnreadHybridAnchorGuardPolicy.LOG_TAG,
                            "blocked_prepend reason=awaiting_anchor direction=${event.direction}"
                    )
                    webController.setInfiniteState(
                            event.direction,
                            forpdateam.ru.forpda.presentation.theme.ThemeInfiniteScrollController.InfiniteState.IDLE.jsName,
                            null
                    )
                } else {
                    armToolbarSuppressForProgrammaticScroll()
                    webController.applyInfinitePage(event.direction, event.html)
                    presenter.notifyHybridPageToolbarRefresh()
                }
            }
            is forpdateam.ru.forpda.presentation.theme.ThemeUiEvent.SetInfiniteState ->
                webController.setInfiniteState(event.direction, event.state, event.message)
            is forpdateam.ru.forpda.presentation.theme.ThemeUiEvent.UpdateVisiblePage ->
                updateVisiblePage(event.pageNumber, event.allPages, event.perPage, event.isForum)
            is forpdateam.ru.forpda.presentation.theme.ThemeUiEvent.ScrollToPage ->
                webController.scrollToPage(event.pageNumber)
            is forpdateam.ru.forpda.presentation.theme.ThemeUiEvent.ScrollToPageAndBottom ->
                webController.scrollToPageAndBottom(event.pageNumber)
            is forpdateam.ru.forpda.presentation.theme.ThemeUiEvent.ScrollToEndAnchorOrBottom ->
                webController.executeScrollCommand(ThemeSmartEndNavigation.endScrollCommand(event.anchorPostId))
            is forpdateam.ru.forpda.presentation.theme.ThemeUiEvent.ScrollToBottom ->
                webController.executeScrollCommand(ThemeScrollCommand.bottom())
            is forpdateam.ru.forpda.presentation.theme.ThemeUiEvent.CancelPendingSmartScroll ->
                webController.cancelPendingSmartScroll()
            is forpdateam.ru.forpda.presentation.theme.ThemeUiEvent.ProgrammaticScrollEnded ->
                    clearToolbarProgrammaticScrollSuppress()
            is forpdateam.ru.forpda.presentation.theme.ThemeUiEvent.RevealThemeContent ->
                    revealThemeContentIfReady("scrollSettled")
            is forpdateam.ru.forpda.presentation.theme.ThemeUiEvent.ApplySmartPostsPatch ->
                applySmartPostsPatch(event.page, event.patch)
            is forpdateam.ru.forpda.presentation.theme.ThemeUiEvent.PatchPostRatingUi ->
                webController.applyPostRatingPatch(event.postId, event.ratingText, event.canPlus, event.canMinus)
            is forpdateam.ru.forpda.presentation.theme.ThemeUiEvent.PatchUserPostCountUi ->
                webController.applyUserPostCountPatch(event.postId, event.userPostCount)
            is forpdateam.ru.forpda.presentation.theme.ThemeUiEvent.StripPrependedTopicHatFromDom ->
                webController.stripPrependedTopicHatFromList(event.hatPostId)
            is forpdateam.ru.forpda.presentation.theme.ThemeUiEvent.InjectTopicHatOverlay ->
                webController.injectTopicHatOverlayHost(event.overlayHostHtml, event.openAfterInject) { ok ->
                    if (event.openAfterInject && ok) {
                        presenter.acknowledgeHatToolbarOverlayOpened()
                    }
                }
            is forpdateam.ru.forpda.presentation.theme.ThemeUiEvent.ResetRenderLifecycle ->
                resetThemeRenderLifecycle("topicChanged:${event.topicId}")
        }
    }

    private fun applySmartPostsPatch(
            page: ThemePage,
            patch: forpdateam.ru.forpda.presentation.theme.SmartPostsPatch
    ) {
        super.updateView(page)
        stopNativeRefreshBeforeWebRender(page)
        hideInitialLoadingIndicator()
        val payload = JSONObject().apply {
            put("expectedPostIds", JSONArray(patch.expectedPostIds))
            put("changedPosts", JSONArray().apply {
                patch.changedPosts.forEach { post ->
                    put(JSONObject().apply {
                        put("id", post.id)
                        put("html", post.html)
                    })
                }
            })
            put("addedPosts", JSONArray().apply {
                patch.addedPosts.forEach { post ->
                    put(JSONObject().apply {
                        put("id", post.id)
                        put("html", post.html)
                    })
                }
            })
            put("pageNumber", patch.pageNumber)
            put("allPages", patch.allPages)
            put("postsOnPage", patch.postsOnPage)
            put("keepBottom", patch.keepBottom)
            put("restoreId", patch.requestId)
            put("restoreMode", page.refreshRestoreMode.orEmpty())
            put("restoreSource", page.refreshRestoreSource.orEmpty())
            put("restoreScrollY", page.scrollY)
            put("restoreAnchorPostId", page.anchorPostId.orEmpty())
            page.anchorOffsetTop?.let { put("restoreAnchorOffsetTop", it) }
            page.scrollRatio?.let { put("restoreScrollRatio", it) }
            put("restoreWasNearBottom", page.wasNearBottom)
        }
        if (BuildConfig.DEBUG) {
            Log.i(
                    REFRESH_SCROLL_TAG,
                    "smartRefresh apply source=${patch.source} requestId=${patch.requestId} changed=${patch.changedPosts.size} added=${patch.addedPosts.size} html=${page.html?.length ?: 0}"
            )
        }
        webController.tryApplyPostsPatch(payload) { ok, reason ->
            if (!isAdded || view == null) return@tryApplyPostsPatch
            if (ok) {
                if (BuildConfig.DEBUG) {
                    Log.i(REFRESH_SCROLL_TAG, "smartRefresh applied reason=$reason requestId=${patch.requestId}")
                }
                hideBottomRefreshOverlay("smartPatch")
                hideRenderTransitionOverlay(patch.requestId, "smartPatch")
                presenter.completeRefreshRestore(patch.requestId)
                syncBottomChromeSpacer()
                if (::fabCoordinator.isInitialized) {
                    fabCoordinator.onPageUpdated()
                }
            } else {
                if (BuildConfig.DEBUG) {
                    Log.i(REFRESH_SCROLL_TAG, "smartRefresh fallbackAfterJs reason=$reason requestId=${patch.requestId}")
                }
                updateView(page)
            }
        }
    }

    private fun onTopicHatOpenStateChanged(open: Boolean) {
        isTopicHatOverlayOpen = open
        if (open) {
            hatToolbarOpenInFlight = false
            bottomRefreshController?.cancelFromHatOpen()
            if (::toolbarScrollController.isInitialized) {
                toolbarScrollController.forceVisible()
            }
            appBarLayout.setExpanded(true, false)
            appBarLayout.translationY = 0f
            preLpShadow.translationY = 0f
            syncTopChromeSpacer()
        } else {
            hatToolbarOpenInFlight = false
            if (isToolbarAutoHideEnabled && ::toolbarScrollController.isInitialized) {
                toolbarScrollController.reset()
                syncTopChromeSpacer()
            }
        }
        if (BuildConfig.DEBUG) Log.i("ThemeHat", "open=$open bottomRefreshArmed=${bottomRefreshController?.isTracking() == true}")
    }

    private fun onTopicPollOpenStateChanged(open: Boolean) {
        isTopicPollOverlayOpen = open
        if (open) {
            if (::toolbarScrollController.isInitialized) {
                toolbarScrollController.show(force = true)
            }
            appBarLayout.translationY = 0f
            syncTopChromeSpacer()
        } else if (isToolbarAutoHideEnabled && ::toolbarScrollController.isInitialized) {
            toolbarScrollController.reset()
            if (::webView.isInitialized && webView.scrollY > 0) {
                toolbarScrollController.hide(force = false)
            }
            syncTopChromeSpacer()
        }
    }

    private fun setAppFontMode(mode: forpdateam.ru.forpda.ui.AppFontMode) {
        if (!::webView.isInitialized) return
        webView.setAppFontMode(mode)
    }

    private fun navigateByPageSwipe(toNext: Boolean): Boolean {
        val page = presenter.getCurrentPageInstance() ?: return false
        val pagination = page.pagination
        val current = presenter.getVisibleCurrentPage().coerceAtLeast(1)
        val target = if (toNext) current + 1 else current - 1
        if (target < 1 || target > pagination.all.coerceAtLeast(1)) return false
        hidePageSwipeOverlay("navigate")
        presenter.loadPage((target - 1) * pagination.perPage.coerceAtLeast(1))
            return true
        }

    private fun canNavigateByPageSwipe(toNext: Boolean): Boolean {
        val page = presenter.getCurrentPageInstance() ?: return false
        val pagination = page.pagination
        val current = presenter.getVisibleCurrentPage().coerceAtLeast(1)
        val target = if (toNext) current + 1 else current - 1
        return target in 1..pagination.all.coerceAtLeast(1)
    }

    private inner class TopicPageSwipeDetector(
            private val target: ExtendedWebView,
            private val onProgress: (direction: PageSwipeDirection, progress: Float, armed: Boolean) -> Unit,
            private val onReset: () -> Unit
    ) : View.OnTouchListener {
        var isEnabled: Boolean = false
            set(value) {
                field = value
                if (!value) reset()
            }

        private val density = target.resources.displayMetrics.density
        private val minDistancePx = 96f * density
        private val minFlingDistancePx = 48f * density
        private val minVelocityPx = 600f * density
        private val dominanceRatio = 1.7f
        private val maxClickSlop = ViewConfiguration.get(target.context).scaledTouchSlop
        private val progressStartPx = max(maxClickSlop * 2f, 32f * density)
        private val longPressTimeoutMs = ViewConfiguration.getLongPressTimeout().toLong()
        private var downX = 0f
        private var downY = 0f
        private var downTime = 0L
        private var blocked = false
        private var captured = false
        private var touchMoved = false
        private var velocityTracker: VelocityTracker? = null

        override fun onTouch(v: View, event: MotionEvent): Boolean {
            if (!isEnabled) {
                onReset()
                return false
            }
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    reset()
                    downX = event.x
                    downY = event.y
                    downTime = SystemClock.uptimeMillis()
                    blocked = event.pointerCount > 1 || shouldIgnoreStart()
                    requestInteractiveHitTest(event.x, event.y)
                    velocityTracker = VelocityTracker.obtain().also { it.addMovement(event) }
                }
                MotionEvent.ACTION_POINTER_DOWN -> block()
                MotionEvent.ACTION_MOVE -> {
                    velocityTracker?.addMovement(event)
                    if (abs(event.x - downX) > maxClickSlop || abs(event.y - downY) > maxClickSlop) {
                        touchMoved = true
                    }
                    if (target.isActionModeActive()) {
                        if (captured || !blocked) block()
                        return false
                    }
                    updateProgress(event)
                    if (captured) return true
                }
                MotionEvent.ACTION_UP -> {
                    velocityTracker?.addMovement(event)
                    val wasCaptured = captured
                    val handled = !blocked && touchMoved && tryNavigate(event)
                    reset()
                    if (handled || wasCaptured) return true
                }
                MotionEvent.ACTION_CANCEL -> reset()
            }
            return false
        }

        private fun shouldIgnoreStart(): Boolean {
            if (target.isActionModeActive()) return true
            return when (target.hitTestResult?.type) {
                WebView.HitTestResult.SRC_ANCHOR_TYPE,
                WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE,
                WebView.HitTestResult.IMAGE_TYPE,
                WebView.HitTestResult.EDIT_TEXT_TYPE -> true
                else -> false
            }
        }

        private fun requestInteractiveHitTest(x: Float, y: Float) {
            if (!target.isJsReady) return
            val script = """
                (function(){
                    var scale = window.visualViewport ? window.visualViewport.scale : 1;
                    var el = document.elementFromPoint(${x / density} * scale, ${y / density} * scale);
                    for (var n = el; n; n = n.parentElement) {
                        var tag = (n.tagName || '').toLowerCase();
                        if (tag === 'a' || tag === 'img' || tag === 'input' || tag === 'textarea' || tag === 'select' || tag === 'button' || n.isContentEditable) return true;
                        var cls = n.className || '';
                        if (typeof cls === 'string' && (cls.indexOf('spoil') >= 0 || cls.indexOf('poll') >= 0)) return true;
                        var style = window.getComputedStyle(n);
                        if ((style.overflowX === 'auto' || style.overflowX === 'scroll') && n.scrollWidth > n.clientWidth) return true;
                    }
                    return false;
                })();
            """.trimIndent()
            target.evaluateJavascript(script) { result ->
                if (!isAdded || view == null) return@evaluateJavascript
                if (result == "true" && !captured) block()
            }
        }

        private fun updateProgress(event: MotionEvent) {
            if (blocked || event.pointerCount != 1) return
            val dx = event.x - downX
            val dy = event.y - downY
            val absX = abs(dx)
            val absY = abs(dy)
            val elapsed = SystemClock.uptimeMillis() - downTime
            if (elapsed < longPressTimeoutMs && absX < minDistancePx) {
                if (captured) {
                    captured = false
                    onReset()
                }
                return
            }
            if (absX < progressStartPx) {
                if (captured) {
                    captured = false
                    onReset()
                }
                return
            }
            if (absX < absY * dominanceRatio) {
                if (absY > maxClickSlop) block()
                return
            }

            val toNext = dx < 0
            if (target.canScrollHorizontally(if (toNext) 1 else -1) || !canNavigateByPageSwipe(toNext)) {
                block()
                return
            }

            captured = true
            target.parent?.requestDisallowInterceptTouchEvent(true)
            val direction = if (toNext) PageSwipeDirection.NEXT else PageSwipeDirection.PREVIOUS
            onProgress(direction, min(1f, absX / minDistancePx), absX >= minDistancePx)
        }

        private fun tryNavigate(event: MotionEvent): Boolean {
            val dx = event.x - downX
            val dy = event.y - downY
            val absX = abs(dx)
            val absY = abs(dy)
            if (absX < minDistancePx && absX < minFlingDistancePx) return false
            if (absX < absY * dominanceRatio) return false

            velocityTracker?.computeCurrentVelocity(1000)
            val velocityX = velocityTracker?.xVelocity ?: 0f
            val velocityY = velocityTracker?.yVelocity ?: 0f
            val distancePass = absX >= minDistancePx
            val flingPass = absX >= minFlingDistancePx && abs(velocityX) >= minVelocityPx && abs(velocityX) >= abs(velocityY) * dominanceRatio
            if (!distancePass && !flingPass) return false

            val toNext = dx < 0
            if (target.canScrollHorizontally(if (toNext) 1 else -1)) return false
            return navigateByPageSwipe(toNext)
        }

        private fun block() {
            blocked = true
            captured = false
            target.parent?.requestDisallowInterceptTouchEvent(false)
            onReset()
        }

        private fun reset() {
            velocityTracker?.recycle()
            velocityTracker = null
            blocked = false
            captured = false
            touchMoved = false
            target.parent?.requestDisallowInterceptTouchEvent(false)
            onReset()
        }
    }

    private enum class PageSwipeDirection {
        PREVIOUS,
        NEXT
    }

    companion object {
        private val LOG_TAG = ThemeFragmentWeb::class.java.simpleName
        private const val TAB_UNREAD_JUMP_DELAY_MS = 120L
        private const val TAB_UNREAD_JUMP_COOLDOWN_MS = 10_000L
        /** Имя объекта в JS (шаблоны, theme.js); реализация — [ThemeWebCallbacks] через [ThemeJsInterface]. */
        const val JS_INTERFACE = "IThemePresenter"

        private const val HAT_TOOLBAR_TOGGLE_JS =
                "(function(){try{if(typeof onToolbarHeaderButtonClickWithResult==='function'){return onToolbarHeaderButtonClickWithResult();}if(typeof onToolbarHeaderButtonClick==='function'){return onToolbarHeaderButtonClick()===true;}if(typeof openTopicHeaderOverlayFromToolbarWithResult==='function'){return openTopicHeaderOverlayFromToolbarWithResult();}if(typeof openTopicHeaderOverlayFromToolbar==='function'){return openTopicHeaderOverlayFromToolbar()===true;}if(typeof toggleThemeHatFromFixed==='function'){return toggleThemeHatFromFixed(true)===true;}}catch(e){}return false;})()"

        private const val HAT_TOOLBAR_OPEN_OVERLAY_JS =
                "(function(){try{if(typeof openTopicHeaderOverlayFromToolbarWithResult==='function'){return openTopicHeaderOverlayFromToolbarWithResult();}if(typeof openTopicHeaderOverlayFromToolbar==='function'){return openTopicHeaderOverlayFromToolbar()===true;}if(typeof showTopicHeaderOverlay==='function'){return showTopicHeaderOverlay()===true;}if(typeof toggleThemeHatFromFixed==='function'){return toggleThemeHatFromFixed(true)===true;}}catch(e){}return false;})()"
    }

}
