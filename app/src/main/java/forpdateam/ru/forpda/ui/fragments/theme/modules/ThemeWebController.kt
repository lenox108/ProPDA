package forpdateam.ru.forpda.ui.fragments.theme.modules

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import android.view.ActionMode
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.core.view.ViewCompat
import forpdateam.ru.forpda.BuildConfig
import forpdateam.ru.forpda.diagnostic.FpdaDebugLog
import forpdateam.ru.forpda.diagnostic.TopicHighlightDiagnostics
import forpdateam.ru.forpda.diagnostic.TopicScrollTrace
import forpdateam.ru.forpda.R
import forpdateam.ru.forpda.common.ArticleLinkResolver
import forpdateam.ru.forpda.common.FourPdaImageUrls
import forpdateam.ru.forpda.common.SiteUrls
import forpdateam.ru.forpda.common.getColorFromAttr
import forpdateam.ru.forpda.common.getVecDrawable
import forpdateam.ru.forpda.common.webview.CustomWebChromeClient
import forpdateam.ru.forpda.common.webview.CustomWebViewClient
import forpdateam.ru.forpda.common.webview.WebViewRenderController
import forpdateam.ru.forpda.common.webview.WebViewRenderSession
import forpdateam.ru.forpda.diagnostic.WebViewRenderDiagnostics
import forpdateam.ru.forpda.entity.remote.IBaseForumPost
import forpdateam.ru.forpda.entity.remote.theme.ThemePage
import forpdateam.ru.forpda.model.repository.avatar.AvatarRepository
import forpdateam.ru.forpda.presentation.ILinkHandler
import forpdateam.ru.forpda.presentation.ISystemLinkHandler
import forpdateam.ru.forpda.presentation.theme.HighlightArmingPolicy
import forpdateam.ru.forpda.presentation.theme.HighlightTarget
import forpdateam.ru.forpda.presentation.theme.ReadPositionSaveGate
import forpdateam.ru.forpda.presentation.theme.ThemeLinkNavigationAction
import forpdateam.ru.forpda.presentation.theme.ThemeLinkNavigationPolicy
import forpdateam.ru.forpda.presentation.theme.ThemeHtmlMetrics
import forpdateam.ru.forpda.presentation.theme.ThemeMissedPageLifecyclePolicy
import forpdateam.ru.forpda.presentation.theme.ThemeRenderCompletePolicy
import forpdateam.ru.forpda.presentation.theme.ThemePostedPageScrollPolicy
import forpdateam.ru.forpda.presentation.theme.ThemePostedScrollPendingPolicy
import forpdateam.ru.forpda.presentation.theme.ThemeSmartEndNavigation
import forpdateam.ru.forpda.presentation.theme.ThemeLinkSourceAnchor
import forpdateam.ru.forpda.presentation.theme.ThemeScrollCommand
import forpdateam.ru.forpda.presentation.theme.ThemeViewModel
import forpdateam.ru.forpda.ui.fragments.WebViewTopScroller
import forpdateam.ru.forpda.ui.views.ExtendedWebView
import forpdateam.ru.forpda.ui.views.messagepanel.MessagePanel
import forpdateam.ru.forpda.ui.views.WebViewSecurityProfile
import timber.log.Timber
import java.util.ArrayList
import java.util.regex.Pattern
import org.json.JSONObject

/**
 * Runtime owner for Theme WebView wiring.
 *
 * Fragment owns the view lifecycle and gesture overlays; this controller owns one-time
 * WebView security/profile setup, clients, direction listener and download handling.
 */
class ThemeWebController(
        private val webView: ExtendedWebView,
        private val fragment: androidx.fragment.app.Fragment,
        private val presenter: ThemeViewModel,
        private val linkHandler: ILinkHandler,
        private val systemLinkHandler: ISystemLinkHandler,
        private val avatarRepository: AvatarRepository,
        private val onDirectionChanged: (Int) -> Unit,
        private val onActionModeClick: (ActionMode, MenuItem) -> Boolean,
        private val onActionModeCreate: (ActionMode, ActionMode.Callback) -> Unit,
        private val onProgrammaticScrollStarted: () -> Unit = {},
) : ThemeUiModule {
    companion object {
        private const val REFRESH_SCROLL_TAG = "RefreshScroll"
        /** JS anchor capture can outlive 240ms on large topics; avoid reloading with a zero native snapshot. */
        private const val REFRESH_SCROLL_CAPTURE_TIMEOUT_MS = 1200L
        private const val THEME_RENDER_TAG = "ThemeRender"
        private const val THEME_HISTORY_TAG = "ThemeHistory"
        private const val WEBVIEW_BLANK_CONTENT_HEIGHT_THRESHOLD = 4
        private val SMART_SCROLL_RETRY_DELAYS_MS = longArrayOf(0L, 48L, 120L, 280L, 520L, 900L)
        /**
         * Default visible window for the topic-post highlight (the user
         * wants a ~2-second flash, not a permanent state). The JS-side
         * fade-out arms a 300ms CSS transition (opacity/background/
         * border-left) right after this deadline; the class is then
         * stripped on `transitionend`. See `template_theme.html`'s
         * `PPDA_scheduleHighlightFadeout` and the `post-highlight-fading`
         * CSS rule in the per-theme stylesheets.
         */
        const val HIGHLIGHT_FADEOUT_DELAY_MS = 2000
    }

    private lateinit var webViewClient: WebViewClient
    private lateinit var chromeClient: WebChromeClient
    private val jsApi = ThemeJsApi(webView)
    private val imeInsetsController = ThemeImeInsetsController(webView)

    private var savedScrollYForImageViewer: Int = -1
    private var lastLoadDataAt: Long = 0L
    private var loadDataCount: Int = 0
    private var activeRenderKey: String? = null
    private var completedRenderKey: String? = null
    private var activeRenderExpectedPosts: Int = 0
    private var completedRenderHasPosts: Boolean = false
    /** Incremented on each [renderThemePage]; stale DOM/page callbacks from superseded loads are ignored. */
    private var renderGeneration: Int = 0
    private var domLifecycleGeneration: Int = 0
    private var pageLifecycleGeneration: Int = 0
    /**
     * Render generation for which the topic-post highlight JS (apply +
     * fadeout-schedule) has already been armed. The highlight can be triggered
     * from more than one render-completion path (the `onPageComplete` →
     * [onDomRendered] lifecycle path AND the DOM-posts-verified fallback path
     * [markRenderVerifiedFromDom] used when the native page-loaded lifecycle is
     * missed/late). This guard makes [reapplyTopicHighlight] idempotent for a
     * given generation so whichever path wins arms it exactly once — no double
     * eval, no double diagnostics, and no re-flash on scroll (scroll does not
     * bump the generation).
     */
    private var highlightArmedGeneration: Int = 0
    /** Post id the highlight was last armed for. See [HighlightArmingPolicy.shouldArmForCurrentTarget]. */
    private var highlightArmedPostId: Long = 0L
    /** Generation for which the JS fade-out timer has been armed (independent of apply). */
    private var highlightFadeoutScheduledGeneration: Int = 0
    /**
     * H-03 (device log 24_06-20-37, topics 1121483 / 1103268 / 1115025): the
     * `already_armed` guard short-circuited the VERY FIRST reapply of a fresh
     * render — `armedGeneration` already equalled the page's `renderGenerationId`
     * — yet ZERO `js_highlight_applied` / `native_highlight_bound` ever appeared
     * in the whole session. The armed bookkeeping had drifted into "satisfied"
     * without the apply JS ever being dispatched, so `PPDA_applyHighlight` was
     * never called and the outline never painted.
     *
     * These two fields are the authoritative record of an *actual* dispatch:
     * they are written ONLY immediately after `jsApi.eval(applyHighlight(...))`
     * runs. The `already_armed` skip now requires that the apply was genuinely
     * dispatched for THIS exact (generation, postId); a generation/postId that
     * was merely "armed" but never dispatched no longer suppresses the apply.
     */
    private var highlightApplyDispatchedGeneration: Int = 0
    private var highlightApplyDispatchedPostId: Long = 0L
    /**
     * Double-light-up latch (device log 24_06-23-12): the authoritative record
     * that a `(renderGenerationId, postId)` has ALREADY run its single
     * light-up + fade-out cycle. Unlike [highlightApplyDispatchedGeneration]
     * (reset by [renderThemePage] / [resetRenderState] /
     * [reapplyTopicHighlightAfterScrollSettled]), these fields are NEVER cleared
     * by a re-render or a reveal/scroll-settled re-arm — they are only
     * superseded when a genuinely NEW generation+post is dispatched. This makes
     * a smart-patch re-render or a repeated reveal event for the SAME
     * generation+post a NO-OP, so the ring lights once and fades once.
     * See [HighlightArmingPolicy.isHighlightCycleAlreadyCompleted].
     */
    private var highlightCompletedGeneration: Int = 0
    private var highlightCompletedPostId: Long = 0L
    /**
     * One-highlight-per-open guard (device log 27_06-10-46, topic 1093640). A getnewpost open landed on
     * and highlighted the redirect post (144025372); the user then scrolled, HYBRID infinite-scroll
     * appended the next page, [onDomRendered] re-ran [reapplyTopicHighlight], the target re-resolved to a
     * DIFFERENT post (the true first-unread 144027468) and a SECOND ring flashed below — the reported
     * "scrolled and another post highlighted" bug. The highlight is a "where you landed" affordance for
     * a page render; it must not re-flash a different post on an infinite-scroll re-render. This records
     * the post actually highlighted since the last [renderThemePage]; a re-resolve to a DIFFERENT post
     * within the same page render is suppressed. Reset by [renderThemePage] (a genuine page/topic
     * navigation), NOT by infinite-scroll appends ([applyInfinitePage]), so navigating to a new page
     * still highlights its target.
     */
    private var highlightAppliedPostIdSinceRender: Long = 0L
    /**
     * STEP 2 — sticky pending ScrollIntent for an explicit-anchor open. Stored separately from
     * `renderGeneration` so a generation bump (reload / smart-patch re-render) does NOT drop the
     * pending anchor. Cleared via [ThemeViewModel.isExplicitAnchorScrollSettledForController]
     * once the blocking INITIAL_ANCHOR scroll for it reports success — the JS side only reports
     * completion after the `scrollToElementWithRetries` final retry confirmed the anchor is near
     * the viewport top (`isThemeAnchorNearViewportTop`), so the clear is event/state-based.
     */
    private var pendingExplicitAnchorPostId: Long = 0L
    /**
     * H-02 (audit Finding H-02): last page `renderGenerationId` observed by [reapplyTopicHighlight].
     * Used to explicitly invalidate the per-render armed/fadeout flags when the page changes (a new
     * generation is stamped) even on paths that bypass [renderThemePage]'s reset (cached-page restore,
     * back-nav remap, smart-patch re-render). Without this, a stale armed generation could make
     * [HighlightArmingPolicy.shouldArmForCurrentTarget] skip a valid new target.
     */
    private var lastObservedHighlightRenderGeneration: Int = 0

    /**
     * All updates to the per-render armed flag go through this helper so we
     * can observe the previous→new transition in logcat. The native-only
     * invariant is: the flag MUST only ever move from its current value to a
     * `renderGenerationId` AFTER `jsApi.eval(applyHighlight(...))` has
     * returned. A log here that jumps to the new render's generation
     * without a matching `js_highlight_applied` / `native_highlight_bound`
     * event in the same frame pinpoints a regression (see device log
     * 24_06-12-16-08_230: `armed=25` on the first reapply with zero
     * `js_highlight_applied` events in the log).
     */
    private fun setHighlightArmedGeneration(newValue: Int, caller: String) {
        if (BuildConfig.DEBUG && highlightArmedGeneration != newValue) {
            TopicHighlightDiagnostics.highlightArmFlagUpdated(
                    flag = "highlightArmedGeneration",
                    previousValue = highlightArmedGeneration,
                    newValue = newValue,
                    caller = caller
            )
        }
        highlightArmedGeneration = newValue
    }

    /** Companion to [setHighlightArmedGeneration]; records the post id the last arm applied. */
    private fun setHighlightArmedPostId(newValue: Long, caller: String) {
        if (BuildConfig.DEBUG && highlightArmedPostId != newValue) {
            TopicHighlightDiagnostics.highlightArmFlagUpdated(
                    flag = "highlightArmedPostId",
                    previousValue = highlightArmedPostId.toInt(),
                    newValue = newValue.toInt(),
                    caller = caller
            )
        }
        highlightArmedPostId = newValue
    }

    /** Same guard for the fade-out flag — see [setHighlightArmedGeneration]. */
    private fun setHighlightFadeoutScheduledGeneration(newValue: Int, caller: String) {
        if (BuildConfig.DEBUG && highlightFadeoutScheduledGeneration != newValue) {
            TopicHighlightDiagnostics.highlightArmFlagUpdated(
                    flag = "highlightFadeoutScheduledGeneration",
                    previousValue = highlightFadeoutScheduledGeneration,
                    newValue = newValue,
                    caller = caller
            )
        }
        highlightFadeoutScheduledGeneration = newValue
    }
    /**
     * Render generation for which the native-side fallback already replayed the
     * `nativeEvents` JS queues from [markRenderVerifiedFromDom]. Prevents double-flushes
     * when both the fallback and the eventual `IBase.domContentLoaded` race to run.
     */
    private var missedLifecycleFlushGeneration: Int = 0
    private var lastProgressJsAt: Long = 0L
    private var progressCallbackCount: Int = 0
    private var progressJsNotifyCount: Int = 0
    private var lastProgressCallbackAt: Long = 0L
    private var disposed: Boolean = false
    private val pendingCaptureTimeouts = mutableSetOf<Runnable>()
    private var smartScrollRetryGeneration = 0
    private var flushingPendingScrollCommand = false
    /**
     * Command id of an anchor/restore scroll command that was DROPPED at dispatch because the JS
     * bridge was not ready (device log 26_06-17-02, cross-topic BACK: the REFRESH_RESTORE was dropped
     * at jsReady=false and never executed). [flushPendingScrollCommand] replays exactly this command
     * once the render is ready; cleared when any anchor/restore command is actually eval'd so a
     * successfully-dispatched command is never double-executed.
     */
    private var deferredAnchorScrollCommandId: String? = null
    private var pendingSmartScrollYBefore = 0

    /**
     * Phase 4 (additive only): shared cross-pipeline render controller. Runs ALONGSIDE the
     * existing [renderGeneration]/ThemeRenderGuard/ThemeRenderSession systems purely as a
     * diagnostic/guard layer. It does NOT drive any Theme behavior; DEBUG logs surface
     * disagreements between the old and new generation systems.
     */
    private val sharedRenderController = WebViewRenderController()
    private var sharedRenderSession: WebViewRenderSession? = null

    override fun init() {
        disposed = false
        webView.systemLinkHandler = systemLinkHandler
        webView.init(WebViewSecurityProfile.TRUSTED_LOCAL_TEMPLATE)

        webViewClient = ThemeWebViewClient()
        chromeClient = ThemeChromeClient()

        webView.webViewClient = webViewClient
        webView.webChromeClient = chromeClient

        webView.setOnDirectionListener(object : ExtendedWebView.OnDirectionListener {
            override fun onDirectionChanged(direction: Int) {
                this@ThemeWebController.onDirectionChanged(direction)
            }
        })

        webView.setBackgroundColor(webView.context.getColorFromAttr(R.attr.background_for_lists))

        // Иначе HW-композит WebView рисуется поверх панели ответа (поле «исчезает», клавиатура есть).
        ViewCompat.setElevation(webView, 0f)
        ViewCompat.setTranslationZ(webView, 0f)
    }

    fun setupActionModeListener(authHolder: () -> forpdateam.ru.forpda.model.AuthHolder) {
        webView.setActionModeListener(object : ExtendedWebView.OnStartActionModeListener {
            override fun onCreate(actionMode: ActionMode, callback: ActionMode.Callback) {
                val menu = actionMode.menu
                val items = ArrayList<MenuItem>()
                for (i in 0 until menu.size()) {
                    items.add(menu.getItem(i))
                }
                menu.clear()

                menu.add(0, R.id.action_mode_item_copy, 0, R.string.copy)
                        .setIcon(webView.context.getVecDrawable(R.drawable.ic_toolbar_content_copy))
                        .setShowAsActionFlags(MenuItem.SHOW_AS_ACTION_ALWAYS)

                if (!authHolder().get().isAuth() || presenter.canQuote()) {
                    menu.add(0, R.id.action_mode_item_quote, 0, R.string.quote)
                            .setIcon(webView.context.getVecDrawable(R.drawable.ic_toolbar_quote_post))
                            .setShowAsActionFlags(MenuItem.SHOW_AS_ACTION_ALWAYS)
                }

                menu.add(0, R.id.action_mode_item_select_all, 0, R.string.all_text)
                        .setIcon(webView.context.getVecDrawable(R.drawable.ic_toolbar_select_all))
                        .setShowAsActionFlags(MenuItem.SHOW_AS_ACTION_ALWAYS)

                menu.add(0, R.id.action_mode_item_share, 0, R.string.share)
                        .setIcon(webView.context.getVecDrawable(R.drawable.ic_toolbar_share))
                        .setShowAsActionFlags(MenuItem.SHOW_AS_ACTION_ALWAYS)

                for (item in items) {
                    if (BuildConfig.DEBUG) Timber.d("fillItem %d : %s : %s : %s : %s", item.itemId, item.title, item.titleCondensed, item.intent, item.menuInfo)
                    if (item.intent != null) {
                        menu.add(item.groupId, item.itemId, item.order, item.title)
                                .setIntent(item.intent)
                                .setNumericShortcut(item.numericShortcut).alphabeticShortcut = item.alphabeticShortcut
                    }
                }
                onActionModeCreate(actionMode, callback)
            }

            override fun onClick(actionMode: ActionMode, item: MenuItem): Boolean {
                if (BuildConfig.DEBUG) Timber.d("onClick %d", item.itemId)
                return onActionModeClick(actionMode, item)
            }
        })
    }

    fun loadThemeUrlFromNavigator(url: String) {
        presenter.loadUrl(url, "navigator")
    }

    fun getOpenTopicIdForReuse(): Int? {
        if (!presenter.isPageLoaded()) return null
        val id = presenter.getId()
        return if (id > 0) id else null
    }

    fun scrollToAnchor(anchor: String?, scrollHandler: ThemeScrollHandler) {
        if (anchor.isNullOrBlank()) return
        if (!fragment.isAdded || fragment.view == null) return
        scrollHandler.scrollToAnchor(anchor)
    }

    fun toggleScrollTop(topScroller: WebViewTopScroller) {
        topScroller.toggleScrollTop()
    }

    fun findNext(next: Boolean) {
        webView.findOnPageNext(next)
    }

    fun findText(text: String) {
        webView.findOnPage(text)
    }

    fun setStyleType(type: String) {
        jsApi.changeStyleType(type)
    }

    fun updateView(page: ThemePage) {
        renderThemePage(page)
    }

    fun renderThemePage(page: ThemePage, force: Boolean = false): Boolean {
        if (disposed || fragment.view == null) return false
        if (webView.parent == null || !webView.isAttachedToWindow) {
            if (BuildConfig.DEBUG) Log.i(REFRESH_SCROLL_TAG, "controller skipRender detached parent=${webView.parent?.javaClass?.simpleName}")
            return false
        }
        if (page.html.isNullOrBlank()) {
            // Never silently render empty HTML: this causes "blank but successful" state and can lock future renders.
            if (BuildConfig.DEBUG) {
                Log.w(
                        REFRESH_SCROLL_TAG,
                        "controller skipRender emptyHtml trace=${presenter.getThemeLoadTraceId()} id=${page.id} st=${page.st} action=${presenter.loadAction} restoreId=${page.refreshRestoreId} restoreMode=${page.refreshRestoreMode}"
                )
            }
            resetRenderState()
            presenter.onEmptyThemeHtmlDetected()
            return false
        }
        val startedAt = SystemClock.uptimeMillis()
        val renderKey = buildRenderKey(page)
        if (!presenter.getPendingPostedPageScrollAnchor().isNullOrBlank()) {
            webView.scrollTo(0, 0)
            page.scrollY = 0
        }
        val expectedPosts = ThemeHtmlMetrics.countListPostContainers(page.html)
        if (expectedPosts == 0 && page.posts.isNotEmpty()) {
            if (BuildConfig.DEBUG) {
                Log.w(
                        THEME_RENDER_TAG,
                        "controller skipRender noPostsInHtml trace=${presenter.getThemeLoadTraceId()} id=${page.id} st=${page.st} pagePosts=${page.posts.size}"
                )
            }
            resetRenderState()
            presenter.onEmptyThemeHtmlDetected()
            return false
        }
        val hasRenderableContent = webView.parent != null && webView.contentHeight > WEBVIEW_BLANK_CONTENT_HEIGHT_THRESHOLD
        if (!force && renderKey == completedRenderKey && completedRenderHasPosts && webView.isJsReady && hasRenderableContent) {
            if (BuildConfig.DEBUG) Log.i(REFRESH_SCROLL_TAG, "controller skipDuplicateLoad renderKey=$renderKey postsVerified=true content=${webView.contentHeight}")
            return false
        }
        activeRenderKey = renderKey
        activeRenderExpectedPosts = expectedPosts
        completedRenderHasPosts = false
        renderGeneration++
        domLifecycleGeneration = 0
        pageLifecycleGeneration = 0
        setHighlightArmedGeneration(
                newValue = HighlightArmingPolicy.armedGenerationAfterNewRender(),
                caller = "renderThemePage"
        )
        setHighlightArmedPostId(newValue = 0L, caller = "renderThemePage")
        setHighlightFadeoutScheduledGeneration(
                newValue = 0,
                caller = "renderThemePage"
        )
        // H-03: a new render must always allow a fresh apply dispatch for the new generation.
        highlightApplyDispatchedGeneration = 0
        highlightApplyDispatchedPostId = 0L
        // One-highlight-per-open: a genuine page/topic render may highlight a (possibly new) target.
        // Infinite-scroll appends do NOT go through renderThemePage, so this stays set across them and
        // suppresses a re-flash on a different post mid-scroll.
        highlightAppliedPostIdSinceRender = 0L
        // Additive shared-controller mirror (Phase 4): no behavior change, diagnostics only.
        val sharedSession = sharedRenderController.beginRender(
                owner = WebViewRenderSession.Owner.THEME,
                targetId = page.id,
                contentHash = (page.html?.hashCode() ?: 0),
                bridgeToken = null,
                createdAt = startedAt,
        )
        sharedRenderSession = sharedSession
        if (BuildConfig.DEBUG) {
            WebViewRenderDiagnostics.log(
                    sharedSession,
                    WebViewRenderDiagnostics.Event.RENDER_REQUESTED,
                    mapOf(
                            "controllerGeneration" to renderGeneration,
                            "renderKey" to renderKey,
                    )
            )
            if (sharedSession.renderGeneration != renderGeneration) {
                WebViewRenderDiagnostics.log(
                        sharedSession,
                        "generation_disagreement",
                        mapOf("controllerGeneration" to renderGeneration),
                        warn = true,
                )
            }
        }
        webView.clearQueuedJs()
        loadDataCount++
        lastLoadDataAt = startedAt
        lastProgressJsAt = 0L
        progressCallbackCount = 0
        progressJsNotifyCount = 0
        lastProgressCallbackAt = 0L
        logRenderHighlightApplied(page)
        if (BuildConfig.DEBUG) {
            Log.i(
                    REFRESH_SCROLL_TAG,
                    "controller updateView trace=${presenter.getThemeLoadTraceId()} renderKey=$renderKey loadCount=$loadDataCount t=$startedAt scrollY=${webView.scrollY} content=${webView.contentHeight} height=${webView.height} pageScroll=${page.scrollY} anchor=${page.anchorPostId} offset=${page.anchorOffsetTop} ratio=${page.scrollRatio} restoreId=${page.refreshRestoreId} restoreMode=${page.refreshRestoreMode} html=${page.html?.length ?: 0}"
            )
            Log.i(
                    THEME_RENDER_TAG,
                    "controller updateView trace=${presenter.getThemeLoadTraceId()} loadCount=$loadDataCount html=${page.html?.length ?: 0} containers=${countHtmlOccurrences(page.html, "theme_page_container")} postsInHtml=$expectedPosts nativeContent=${webView.contentHeight} nativeHeight=${webView.height} scrollY=${webView.scrollY}"
            )
        }
        if (BuildConfig.DEBUG) {
            Log.i(
                    REFRESH_SCROLL_TAG,
                    "controller loadDataStart trace=${presenter.getThemeLoadTraceId()} restoreId=${page.refreshRestoreId} restoreMode=${page.refreshRestoreMode} loadCount=$loadDataCount html=${page.html?.length ?: 0}"
            )
        }
        webView.loadDataWithBaseURL(ArticleLinkResolver.THEME_WEBVIEW_BASE_URL, page.html ?: "", "text/html", "utf-8", null)
        sharedRenderController.markLoadDispatched(sharedSession)
        if (BuildConfig.DEBUG) {
            WebViewRenderDiagnostics.log(sharedSession, WebViewRenderDiagnostics.Event.LOAD_DISPATCHED)
            Log.i(
                    REFRESH_SCROLL_TAG,
                    "controller loadDataEnd trace=${presenter.getThemeLoadTraceId()} callMs=${SystemClock.uptimeMillis() - startedAt} html=${page.html?.length ?: 0}"
            )
        }
        webView.updatePaddingBottom()
        if (presenter.loadAction == forpdateam.ru.forpda.presentation.theme.ThemeLoadAction.Refresh &&
                presenter.isRefreshScrollRestoreActive()
        ) {
            scheduleMissedDomLifecycleProbe(120L)
            scheduleMissedDomLifecycleProbe(480L)
        } else {
            scheduleMissedDomLifecycleProbe(400L)
            scheduleMissedDomLifecycleProbe(1200L)
        }
        return true
    }

    /**
     * Kotlin-side fallback when [IBase.domContentLoaded] was missed but DOM posts are present
     * (log: blankVerifyOk renderComplete=false, no domComplete for trace).
     */
    fun markRenderVerifiedFromDom(domPosts: Int): Boolean {
        // Tripwire log — must be FIRST so we see the call even when one of the
        // guards below returns false. The user-reported regression on topic
        // 1103268 (log 24_06-13-00-28_912): no `markRenderVerifiedFromDom`
        // log line at all and zero `highlight_arm_*` events. With the existing
        // late-Log.i inside the function, we cannot tell whether (a) the call
        // site never ran, or (b) it ran but one of the guards returned false.
        // The entered / exited log pair below is the diagnostic that
        // distinguishes (a) from (b) on the next device log.
        val traceAtEntry = if (BuildConfig.DEBUG) presenter.getThemeLoadTraceId() else ""
        if (BuildConfig.DEBUG) {
            val activeRk = activeRenderKey
            val jsReady = webView.isJsReady
            Log.i(
                    REFRESH_SCROLL_TAG,
                    "controller markRenderVerifiedFromDom_entered trace=$traceAtEntry activeRenderKey=$activeRk jsReady=$jsReady domPosts=$domPosts expected=$activeRenderExpectedPosts disposed=$disposed viewAttached=${fragment.view != null}"
            )
        }
        if (disposed || fragment.view == null) {
            if (BuildConfig.DEBUG) {
                Log.i(
                        REFRESH_SCROLL_TAG,
                        "controller markRenderVerifiedFromDom_exited trace=$traceAtEntry reason=disposed_or_no_view"
                )
            }
            return false
        }
        val renderKey = activeRenderKey
        if (renderKey == null) {
            if (BuildConfig.DEBUG) {
                Log.i(
                        REFRESH_SCROLL_TAG,
                        "controller markRenderVerifiedFromDom_exited trace=$traceAtEntry reason=no_active_render_key"
                )
            }
            return false
        }
        if (!webView.isJsReady || domPosts <= 0) {
            if (BuildConfig.DEBUG) {
                Log.i(
                        REFRESH_SCROLL_TAG,
                        "controller markRenderVerifiedFromDom_exited trace=$traceAtEntry reason=js_not_ready_or_no_posts jsReady=${webView.isJsReady} domPosts=$domPosts"
                )
            }
            return false
        }
        completedRenderKey = renderKey
        completedRenderHasPosts = true
        // The native page-loaded lifecycle (`onPageComplete` → onDomRendered)
        // can be missed/late on rapid loadDataWithBaseURL; this DOM-posts
        // fallback is then the only path that confirms a usable render. Arm the
        // topic-post highlight here too so the JS apply + fadeout schedule run
        // regardless of which completion path wins. The per-generation guard in
        // [reapplyTopicHighlight] keeps it a single arming.
        reapplyTopicHighlight()
        // Safety net: when the page-loaded lifecycle is missed/late, JS modules that
        // hook into the `nativeEvents.DOM`/`nativeEvents.PAGE` queues (e.g. delegated
        // link-anchor listeners, post re-bindings) may not have been flushed. Force
        // a flush from native so the queue is drained exactly once for this render
        // generation. `IBase` is guaranteed to be defined at this point (the
        // `webView.isJsReady` guard above is set by [IBase.domContentLoaded]), so
        // the in-process call to [IBase.domContentLoaded] will reach JS and replay
        // the queued callbacks. The replay is idempotent — the JS side guards the
        // click-listener re-bind with `removeEventListener` first.
        flushMissedJsLifecycleQueues()
        if (BuildConfig.DEBUG) {
            Log.i(
                    REFRESH_SCROLL_TAG,
                    "controller markRenderVerifiedFromDom trace=$traceAtEntry renderKey=$renderKey domPosts=$domPosts expected=$activeRenderExpectedPosts"
            )
        }
        return true
    }

    /**
     * Replays the `nativeDomComplete` / `nativePageComplete` queues from the JS side
     * without going through [IBase.domContentLoaded]/[IBase.onPageLoaded] (those have
     * already done their work for this render). The script also handles the case where
     * the document `DOMContentLoaded` event itself never fired.
     */
    private fun flushMissedJsLifecycleQueues() {
        if (disposed || fragment.view == null) return
        if (!webView.isJsReady) return
        if (renderGeneration <= 0) return
        // Only flush once per render generation. The probe + `IBase` lifecycle already
        // cover the normal path; this is the fallback for the late/missed-lifecycle case.
        if (missedLifecycleFlushGeneration == renderGeneration) return
        missedLifecycleFlushGeneration = renderGeneration
        webView.evalJs("nativeEvents.onNativeDomComplete();nativeEvents.onNativePageComplete();")
    }

    fun probeMissedFullLifecycleIfStuck() {
        if (disposed || fragment.view == null) return
        if (renderGeneration <= 0) return
        if (domLifecycleGeneration != renderGeneration) {
            probeMissedDomLifecycle()
        } else {
            probeMissedPageLifecycle()
        }
    }

    fun hasCompletedRender(renderKey: String): Boolean =
            ThemeRenderCompletePolicy.hasCompletedRender(
                    renderKey = renderKey,
                    completedRenderKey = completedRenderKey,
                    completedRenderHasPosts = completedRenderHasPosts,
                    jsReady = webView.isJsReady,
                    hasParent = webView.parent != null,
                    contentHeight = webView.contentHeight,
                    blankContentThreshold = WEBVIEW_BLANK_CONTENT_HEIGHT_THRESHOLD,
            )

    fun resetRenderState() {
        activeRenderKey = null
        completedRenderKey = null
        activeRenderExpectedPosts = 0
        completedRenderHasPosts = false
        domLifecycleGeneration = 0
        pageLifecycleGeneration = 0
        setHighlightArmedGeneration(newValue = 0, caller = "resetRenderState")
        setHighlightArmedPostId(newValue = 0L, caller = "resetRenderState")
        highlightApplyDispatchedGeneration = 0
        highlightApplyDispatchedPostId = 0L
        // STEP 2: a full render reset (topic change) drops the sticky explicit-anchor intent.
        pendingExplicitAnchorPostId = 0L
        missedLifecycleFlushGeneration = 0
        if (BuildConfig.DEBUG) Log.i(REFRESH_SCROLL_TAG, "controller resetRenderState")
    }

    fun tryClaimDomLifecycle(): Boolean {
        if (renderGeneration <= 0) return false
        if (domLifecycleGeneration == renderGeneration) return false
        if (domLifecycleGeneration != 0) return false
        domLifecycleGeneration = renderGeneration
        sharedRenderSession?.let { session ->
            sharedRenderController.markDomConfirmed(session)
            if (BuildConfig.DEBUG) {
                WebViewRenderDiagnostics.log(session, WebViewRenderDiagnostics.Event.DOM_CONFIRMED)
            }
        }
        return true
    }

    fun wasDomLifecycleClaimedForCurrentRender(): Boolean =
            renderGeneration > 0 && domLifecycleGeneration == renderGeneration

    fun getControllerRenderGeneration(): Int = renderGeneration

    fun tryClaimPageLifecycle(): Boolean {
        if (renderGeneration <= 0) return false
        if (domLifecycleGeneration != renderGeneration) return false
        if (pageLifecycleGeneration == renderGeneration) return false
        if (pageLifecycleGeneration != 0) return false
        pageLifecycleGeneration = renderGeneration
        sharedRenderSession?.let { session ->
            sharedRenderController.markPageConfirmed(session)
            if (BuildConfig.DEBUG) {
                WebViewRenderDiagnostics.log(session, WebViewRenderDiagnostics.Event.PAGE_CONFIRMED)
            }
        }
        return true
    }

    private fun buildRenderKey(page: ThemePage): String {
        val html = page.html.orEmpty()
        return "${presenter.getThemeLoadTraceId()}:${page.refreshRestoreId.orEmpty()}:${page.id}:${page.st}:${page.renderSignature.orEmpty()}:${html.length}:${html.hashCode()}"
    }

    fun tryApplyPostsPatch(payload: JSONObject, onResult: (Boolean, String) -> Unit) {
        if (disposed || fragment.view == null) {
            onResult(false, "disposed")
            return
        }
        if (!webView.isJsReady) {
            onResult(false, "js_not_ready")
            return
        }
        jsApi.applyPostsPatch(payload) { result ->
            val raw = result
                    ?.let { runCatching { JSONObject("{\"v\":$it}").optString("v") }.getOrNull() }
                    ?.takeIf { it.isNotEmpty() }
            val json = raw?.let { runCatching { JSONObject(it) }.getOrNull() }
            val ok = json?.optBoolean("ok") == true
            val reason = json?.optString("reason").orEmpty().ifBlank { if (ok) "patched" else "bad_result" }
            if (BuildConfig.DEBUG) {
                Log.i(
                        REFRESH_SCROLL_TAG,
                        "controller smartPatch result ok=$ok reason=$reason jsonLen=${raw?.length ?: 0}"
                )
            }
            onResult(ok, reason)
        }
    }

    fun applyInfinitePage(direction: String, html: String) {
        if (disposed || fragment.view == null) return
        if (BuildConfig.DEBUG) {
            Log.i(
                    THEME_RENDER_TAG,
                    "controller applyInfinite direction=$direction fragmentHtml=${html.length} containers=${countHtmlOccurrences(html, "theme_page_container")} postsInHtml=${countHtmlOccurrences(html, "post_container")} content=${webView.contentHeight} height=${webView.height} scrollY=${webView.scrollY}"
            )
        }
        jsApi.applyInfinitePage(direction, html)
    }

    fun stripPrependedTopicHatFromList(hatPostId: Int) {
        if (disposed || fragment.view == null) return
        jsApi.stripPrependedTopicHatFromList(hatPostId)
    }

    fun injectTopicHatOverlayHost(
            overlayHostHtml: String,
            openAfterInject: Boolean,
            onResult: ((Boolean) -> Unit)? = null,
    ) {
        if (disposed || fragment.view == null) return
        if (onResult != null) {
            jsApi.injectTopicHatOverlayHost(overlayHostHtml, openAfterInject) { raw ->
                onResult(parseThemeJsBoolean(raw))
            }
        } else {
            jsApi.injectTopicHatOverlayHost(overlayHostHtml, openAfterInject)
        }
    }

    private fun parseThemeJsBoolean(raw: String?): Boolean =
            raw?.trim()?.equals("true", ignoreCase = true) == true

    fun setInfiniteState(direction: String, state: String, message: String?) {
        if (disposed || fragment.view == null) return
        jsApi.setInfiniteState(direction, state, message)
    }

    fun cancelPendingSmartScroll() {
        ++smartScrollRetryGeneration
        presenter.clearPendingScrollCommand()
        if (disposed || fragment.view == null) return
        jsApi.cancelScrollRetries()
    }

    fun scrollToPage(pageNumber: Int) {
        TopicScrollTrace.log(
                event = "scroll_to_page",
                traceId = presenter.getThemeLoadTraceId(),
                topicId = presenter.getId().takeIf { it > 0 },
                command = "scrollToPage",
                extra = mapOf("pageNumber" to pageNumber)
        )
        jsApi.scrollToPage(pageNumber)
    }

    fun scrollToPageAndBottom(pageNumber: Int) {
        if (disposed || fragment.view == null) {
            logSmartScrollDropped("disposed_or_no_view", pageNumber = pageNumber)
            presenter.reportSmartEndUnavailable("disposed_or_no_view")
            return
        }
        // Persist BOTTOM so [flushPendingScrollCommand] can replay after dom/reack if JS is not ready yet.
        presenter.beginScrollCommand(ThemeScrollCommand.bottom())
        onProgrammaticScrollStarted()
        pendingSmartScrollYBefore = webView.scrollY
        dispatchSmartScroll(
                pageNumber = pageNumber,
                attempt = 0,
                command = presenter.getPendingScrollCommand(),
                onReady = {
                    val commandId = presenter.getPendingScrollCommand()?.commandId.orEmpty()
                    jsApi.scrollToPageAndBottom(pageNumber, commandId)
                }
        )
    }

    fun scrollToBottom() {
        TopicScrollTrace.log(
                event = "scroll_to_bottom",
                traceId = presenter.getThemeLoadTraceId(),
                topicId = presenter.getId().takeIf { it > 0 },
                command = "scrollToBottom"
        )
        executeScrollCommand(ThemeScrollCommand.bottom())
    }

    /** Registers command in ViewModel and runs [executeThemeScrollCommand] when JS is ready. */
    fun executeScrollCommand(command: ThemeScrollCommand) {
        if (disposed || fragment.view == null) {
            presenter.reportSmartEndUnavailable("disposed_or_no_view")
            return
        }
        presenter.beginScrollCommand(command)
        onProgrammaticScrollStarted()
        pendingSmartScrollYBefore = webView.scrollY
        if (command.kind == ThemeScrollCommand.Kind.BOTTOM ||
                command.kind == ThemeScrollCommand.Kind.END_ANCHOR_OR_BOTTOM
        ) {
            dispatchSmartScroll(
                    pageNumber = null,
                    attempt = 0,
                    command = command,
                    onReady = { jsApi.eval(jsApi.executeScrollCommand(command)) }
            )
        } else {
            reacknowledgeJsBridgeIfRenderedButFlagStale()
            if (webView.isJsReady) {
                deferredAnchorScrollCommandId = null
                if (BuildConfig.DEBUG) {
                    Log.i(REFRESH_SCROLL_TAG, "controller execScrollCmd kind=${command.kind} id=${command.commandId} jsReady=true")
                }
                jsApi.eval(jsApi.executeScrollCommand(command))
            } else {
                // Defer instead of dropping: a BACK/refresh REFRESH_RESTORE (or INITIAL_ANCHOR) dropped
                // here used to be abandoned via scrollStuckReveal, landing the user on the page top.
                // [flushPendingScrollCommand] replays it once the render is ready.
                deferredAnchorScrollCommandId = command.commandId
                if (BuildConfig.DEBUG) {
                    Log.w(REFRESH_SCROLL_TAG, "controller execScrollCmd DEFERRED kind=${command.kind} id=${command.commandId} jsReady=false content=${webView.contentHeight} posts=$completedRenderHasPosts")
                }
            }
        }
    }

    /**
     * Kotlin-side fallback when [IBase.domContentLoaded] / [IBase.onPageLoaded] are missed after
     * smart-end [loadDataWithBaseURL] (log: page 1206 rendered, scrollY=0, no execScrollCmd).
     */
    fun schedulePostedPageScrollIfNeeded(source: String) {
        if (disposed || fragment.view == null) return
        val pendingAnchor = presenter.getPendingPostedPageScrollAnchor() ?: return
        val page = presenter.getCurrentPageInstance() ?: return
        val anchorPostId = ThemePostedPageScrollPolicy.resolveDomScrollAnchor(pendingAnchor, page).orEmpty()
        if (anchorPostId.isBlank()) return
        val command = ThemeScrollCommand.endAnchorOrBottom(anchorPostId)
        if (BuildConfig.DEBUG) {
            FpdaDebugLog.log(
                    FpdaDebugLog.TAG_THEME_RENDER,
                    "posted_scroll_scheduled",
                    mapOf(
                            "source" to source,
                            "commandId" to command.commandId,
                            "anchorPostId" to anchorPostId,
                            "page" to page.pagination.current,
                            "jsReady" to webView.isJsReady,
                            "contentHeight" to webView.contentHeight
                    )
            )
        }
        val setupJs = buildString {
            append(jsApi.setLoadAction(forpdateam.ru.forpda.presentation.theme.ThemeLoadAction.Normal.toString()))
            append(jsApi.setLoadAnchorPostId(anchorPostId))
            append("if(typeof resetThemeRuntimeState==='function'){resetThemeRuntimeState();}")
        }
        reacknowledgeJsBridgeIfRenderedButFlagStale()
        if (webView.isJsReady) {
            jsApi.eval(setupJs)
            executeScrollCommand(command)
            presenter.clearPendingPostedPageScroll()
        } else {
            presenter.beginScrollCommand(command)
            onProgrammaticScrollStarted()
            jsApi.eval(setupJs)
            dispatchSmartScroll(
                    pageNumber = null,
                    attempt = 0,
                    command = command,
                    onReady = {
                        jsApi.eval(jsApi.executeScrollCommand(command))
                        presenter.clearPendingPostedPageScroll()
                    }
            )
        }
    }

    fun scheduleEndNavigationScrollIfNeeded(source: String) {
        if (disposed || fragment.view == null) return
        if (!presenter.isEndNavigationPending()) return
        val endPage = presenter.getEndScrollTargetPage() ?: presenter.getCurrentPageInstance() ?: return
        val anchorPostId = ThemeSmartEndNavigation.resolveEndScrollAnchorPostId(endPage).orEmpty()
        val command = ThemeSmartEndNavigation.endScrollCommand(endPage)
        if (BuildConfig.DEBUG) {
            FpdaDebugLog.log(
                    FpdaDebugLog.TAG_SMART_BUTTON,
                    "end_scroll_scheduled",
                    mapOf(
                            "source" to source,
                            "commandId" to command.commandId,
                            "kind" to command.kind.name,
                            "anchorPostId" to anchorPostId,
                            "page" to endPage.pagination.current,
                            "jsReady" to webView.isJsReady,
                            "contentHeight" to webView.contentHeight
                    )
            )
        }
        val setupJs = buildString {
            append(jsApi.setLoadAction(forpdateam.ru.forpda.presentation.theme.ThemeLoadAction.End.toString()))
            append(jsApi.setLoadAnchorPostId(anchorPostId))
            append("if(typeof resetThemeRuntimeState==='function'){resetThemeRuntimeState();}")
        }
        reacknowledgeJsBridgeIfRenderedButFlagStale()
        if (webView.isJsReady) {
            jsApi.eval(setupJs)
            executeScrollCommand(command)
        } else {
            presenter.beginScrollCommand(command)
            onProgrammaticScrollStarted()
            pendingSmartScrollYBefore = webView.scrollY
            jsApi.eval(setupJs)
            dispatchSmartScroll(
                    pageNumber = null,
                    attempt = 0,
                    command = command,
                    onReady = { jsApi.eval(jsApi.executeScrollCommand(command)) }
            )
        }
    }

    fun flushPendingScrollCommand() {
        if (disposed || fragment.view == null || flushingPendingScrollCommand) return
        val command = presenter.getPendingScrollCommand() ?: return
        // Replay an anchor/restore command that was DEFERRED at dispatch (JS bridge not ready). Only
        // the exact deferred command is replayed (commandId match), so a command that DID dispatch is
        // never re-executed. The JS side coalesces / guards re-entry (restoreSkipReentry), so even a
        // late replay is safe. Device log 26_06-17-02: without this the BACK REFRESH_RESTORE never ran.
        if (command.commandId == deferredAnchorScrollCommandId &&
                (command.kind == ThemeScrollCommand.Kind.INITIAL_ANCHOR ||
                        command.kind == ThemeScrollCommand.Kind.REFRESH_RESTORE ||
                        command.kind == ThemeScrollCommand.Kind.ANCHOR)
        ) {
            reacknowledgeJsBridgeIfRenderedButFlagStale()
            val renderable = webView.contentHeight > WEBVIEW_BLANK_CONTENT_HEIGHT_THRESHOLD ||
                    completedRenderHasPosts
            if (webView.isJsReady && renderable) {
                deferredAnchorScrollCommandId = null
                if (BuildConfig.DEBUG) {
                    Log.i(REFRESH_SCROLL_TAG, "controller execScrollCmd REPLAY kind=${command.kind} id=${command.commandId} jsReady=true content=${webView.contentHeight}")
                }
                // Same as the direct dispatch path: arm toolbar auto-hide suppression so the replayed
                // anchor scroll can't be misread as a user scroll and toggle the chrome.
                onProgrammaticScrollStarted()
                flushingPendingScrollCommand = true
                try {
                    jsApi.eval(jsApi.executeScrollCommand(command))
                } finally {
                    flushingPendingScrollCommand = false
                }
            }
            return
        }
        if (command.kind != ThemeScrollCommand.Kind.BOTTOM &&
                command.kind != ThemeScrollCommand.Kind.END_ANCHOR_OR_BOTTOM
        ) return
        val hasRenderableContent = webView.contentHeight > WEBVIEW_BLANK_CONTENT_HEIGHT_THRESHOLD
        val endScrollPending = presenter.isEndNavigationPending()
        val postedScrollPending = presenter.isPostedPageScrollPending()
        if (ThemePostedScrollPendingPolicy.shouldDeferFlushUntilRenderable(
                        hasRenderableContent = hasRenderableContent,
                        completedRenderHasPosts = completedRenderHasPosts,
                        endNavigationPending = endScrollPending,
                        postedPageScrollPending = postedScrollPending,
                )
        ) {
            if (BuildConfig.DEBUG) {
                FpdaDebugLog.log(
                        FpdaDebugLog.TAG_SMART_BUTTON,
                        "scroll_replay_deferred",
                        mapOf(
                                "commandId" to command.commandId,
                                "reason" to "render_not_ready",
                                "contentHeight" to webView.contentHeight,
                                "completedRenderHasPosts" to completedRenderHasPosts,
                                "endNavigationPending" to endScrollPending,
                                "postedPageScrollPending" to postedScrollPending
                        )
                )
            }
            return
        }
        if (BuildConfig.DEBUG) {
            FpdaDebugLog.log(
                    FpdaDebugLog.TAG_SMART_BUTTON,
                    "scroll_replay",
                    mapOf(
                            "commandId" to command.commandId,
                            "jsReady" to webView.isJsReady,
                            "contentHeight" to webView.contentHeight
                    )
            )
        }
        pendingSmartScrollYBefore = webView.scrollY
        flushingPendingScrollCommand = true
        try {
            dispatchSmartScroll(
                    pageNumber = null,
                    attempt = 0,
                    command = command,
                    onReady = { jsApi.eval(jsApi.executeScrollCommand(command)) }
            )
        } finally {
            flushingPendingScrollCommand = false
        }
    }

    private fun dispatchSmartScroll(
            pageNumber: Int?,
            attempt: Int,
            command: ThemeScrollCommand? = null,
            onReady: () -> Unit
    ) {
        reacknowledgeJsBridgeIfRenderedButFlagStale()
        val domReady = webView.isJsReady
        if (domReady) {
            if (BuildConfig.DEBUG) {
                FpdaDebugLog.log(
                        FpdaDebugLog.TAG_SMART_BUTTON,
                        "scroll_exec",
                        mapOf(
                                "pageNumber" to pageNumber,
                                "jsReady" to true,
                                "domReady" to true,
                                "webViewReady" to (webView.parent != null && webView.isAttachedToWindow),
                                "contentHeight" to webView.contentHeight,
                                "scrollYBefore" to pendingSmartScrollYBefore,
                                "attempt" to attempt,
                                "commandId" to command?.commandId
                        )
                )
            }
            onReady()
            return
        }
        if (attempt >= SMART_SCROLL_RETRY_DELAYS_MS.lastIndex) {
            if (command != null) {
                if (BuildConfig.DEBUG) {
                    FpdaDebugLog.warn(
                            FpdaDebugLog.TAG_SMART_BUTTON,
                            "scroll_deferred",
                            mapOf(
                                    "reason" to "js_not_ready",
                                    "pageNumber" to pageNumber,
                                    "attempt" to attempt,
                                    "commandId" to command.commandId,
                                    "kind" to command.kind.name
                            )
                    )
                }
                return
            }
            logSmartScrollDropped("js_not_ready", pageNumber = pageNumber, attempt = attempt)
            executeScrollCommand(ThemeScrollCommand.bottom())
            return
        }
        if (BuildConfig.DEBUG && attempt == 0) {
            FpdaDebugLog.log(
                    FpdaDebugLog.TAG_SMART_BUTTON,
                    "scroll_defer",
                    mapOf(
                            "reason" to "js_not_ready",
                            "pageNumber" to pageNumber,
                            "commandId" to command?.commandId,
                            "contentHeight" to webView.contentHeight,
                            "completedRenderHasPosts" to completedRenderHasPosts
                    )
            )
        }
        val generation = ++smartScrollRetryGeneration
        val delayMs = SMART_SCROLL_RETRY_DELAYS_MS[attempt + 1] - SMART_SCROLL_RETRY_DELAYS_MS[attempt]
        webView.postDelayed({
            if (disposed || generation != smartScrollRetryGeneration) return@postDelayed
            dispatchSmartScroll(pageNumber, attempt + 1, command, onReady)
        }, delayMs)
    }

    private fun logSmartScrollDropped(reason: String, pageNumber: Int? = null, attempt: Int? = null) {
        if (!BuildConfig.DEBUG) return
        FpdaDebugLog.warn(
                FpdaDebugLog.TAG_SMART_BUTTON,
                "scroll_dropped",
                mapOf(
                        "reason" to reason,
                        "pageNumber" to pageNumber,
                        "attempt" to attempt,
                        "contentHeight" to webView.contentHeight,
                        "completedRenderHasPosts" to completedRenderHasPosts,
                        "scrollYBefore" to pendingSmartScrollYBefore
                )
        )
    }

    /** Registers command in ViewModel and returns a JS batch snippet for [executeThemeScrollCommand]. */
    fun buildScrollCommandAction(command: ThemeScrollCommand): String {
        presenter.beginScrollCommand(command)
        onProgrammaticScrollStarted()
        return jsApi.executeScrollCommand(command)
    }

    /**
     * [ExtendedWebView.onAttachedToWindow]/[ExtendedWebView.onDetachedFromWindow] reset
     * [ExtendedWebView.isJsReady] to false on every detach/reattach, but the rendered document and
     * the theme.js context survive a reattach (no new `domContentLoaded` fires). Unlike QMS, the
     * theme screen never re-acknowledged the bridge, so after returning to an already-rendered topic
     * every isJsReady-gated path (jump-to-end scroll command, refresh-restore anchor capture) was
     * silently dropped. Re-acknowledge the bridge when we can prove a document is still rendered so
     * those guarded JS calls run again.
     */
    private fun reacknowledgeJsBridgeIfRenderedButFlagStale() {
        if (webView.isJsReady) return
        val rendered = completedRenderHasPosts &&
                webView.parent != null &&
                webView.contentHeight > WEBVIEW_BLANK_CONTENT_HEIGHT_THRESHOLD
        if (!rendered) return
        if (BuildConfig.DEBUG) {
            Log.i(REFRESH_SCROLL_TAG, "controller reackJsBridge afterReattach content=${webView.contentHeight} posts=$completedRenderHasPosts")
        }
        webView.acknowledgeJsBridgeFromNativeProbe()
        flushPendingScrollCommand()
    }

    private fun closeToolbarOverlaysBeforeNavigation() {
        presenter.closeToolbarOverlaysForNavigation()
        if (webView.isJsReady) {
            jsApi.closeToolbarOverlaysForNavigation()
        }
    }

    /** In-place DOM update after vote — avoids full reload / scroll jump. */
    fun applyPostRatingPatch(postId: Int, ratingText: String, canPlus: Boolean, canMinus: Boolean) {
        if (disposed || fragment.view == null || !webView.isJsReady) return
        jsApi.applyPostRatingPatch(postId, ratingText, canPlus, canMinus)
    }

    fun applyUserPostCountPatch(postId: Int, userPostCount: Int) {
        if (disposed || fragment.view == null || !webView.isJsReady) return
        jsApi.applyUserPostCountPatch(postId, userPostCount)
    }

    fun updateShowAvatarState(isShow: Boolean) {
        jsApi.updateShowAvatarState(isShow)
    }

    fun updateTypeAvatarState(isCircle: Boolean) {
        jsApi.updateTypeAvatarState(isCircle)
    }

    fun setFontSize(size: Int) {
        webView.setRelativeFontSize(size)
    }

    fun setBottomChromePadding(padding: Int) {
        webView.setBottomChromePadding(padding)
        webView.flushQueuedJs()
    }

    fun updateHistoryLastHtml(
            requestId: String? = null,
            linkSourceUrl: String? = null,
            authoritativeLinkAnchor: ThemeLinkSourceAnchor? = null,
            onCaptured: (() -> Unit)? = null
    ) {
        if (disposed || fragment.view == null) {
            onCaptured?.invoke()
            return
        }
        // Restore the bridge flag after a detach/reattach so the precise JS anchor capture below is
        // not skipped (otherwise refresh restores by coarse ratio only and can snap back to the top).
        reacknowledgeJsBridgeIfRenderedButFlagStale()
        val scrollY = webView.scrollY
        val targetPage = presenter.getRefreshCapturePageInstance()
        val maxScroll = ((webView.contentHeight * webView.scale).toInt() - webView.height).coerceAtLeast(0)
        val ratio = if (maxScroll > 0) scrollY.toDouble() / maxScroll.toDouble() else 0.0
        val nearBottomTolerancePx = (24f * webView.resources.displayMetrics.density).toInt()
        val wasNearBottom = scrollY >= maxScroll - nearBottomTolerancePx
        val captureSource = presenter.getRefreshRestoreSource().orEmpty()
        var completed = false
        fun completeCapture() {
            if (completed) return
            completed = true
            onCaptured?.invoke()
        }
        if (BuildConfig.DEBUG) {
            Timber.d("updateHistoryLastHtml: scrollY=$scrollY targetPage.id=${targetPage?.id} targetPage.st=${targetPage?.st}")
            Log.i(
                    REFRESH_SCROLL_TAG,
                    "capture native requestId=$requestId source=$captureSource t=${SystemClock.uptimeMillis()} scrollY=$scrollY max=$maxScroll content=${webView.contentHeight} scale=${webView.scale} height=${webView.height} ratio=$ratio wasNearBottom=$wasNearBottom tolerance=$nearBottomTolerancePx appBarY=${webView.translationY}"
            )
        }
        if (authoritativeLinkAnchor != null && targetPage != null) {
            val sourceScrollY = authoritativeLinkAnchor.scrollY
            val sourceOffsetTop = authoritativeLinkAnchor.offsetTop ?: 0.0
            val sourceRatio = authoritativeLinkAnchor.ratio ?: ratio
            presenter.updatePageRefreshScrollSnapshot(
                    targetPage,
                    sourceScrollY,
                    authoritativeLinkAnchor.postId,
                    sourceOffsetTop,
                    sourceRatio,
                    false
            )
            Log.i(
                    THEME_HISTORY_TAG,
                    "link authoritative sourceUrl=$linkSourceUrl sourcePostId=${authoritativeLinkAnchor.postId} sourceOffset=$sourceOffsetTop sourceY=$sourceScrollY sourceRatio=$sourceRatio sourceEvent=${authoritativeLinkAnchor.eventType} sourcePage=${targetPage.pagination.current} target=$linkSourceUrl"
            )
            if (!webView.isJsReady) {
                completeCapture()
                return
            }
        }
        if (targetPage != null && authoritativeLinkAnchor == null) {
            presenter.updatePageRefreshScrollSnapshot(targetPage, scrollY, null, null, ratio, wasNearBottom)
        }
        if (!webView.isJsReady) {
            if (BuildConfig.DEBUG) {
                Timber.d("updateHistoryLastHtml: skip captureThemeRefreshScrollAnchor (JS not ready)")
                Log.i(REFRESH_SCROLL_TAG, "capture skipJsReady requestId=$requestId source=$captureSource")
            }
            completeCapture()
            return
        }
        var jsCaptureCompleted = false
        lateinit var timeoutRunnable: Runnable
        timeoutRunnable = Runnable {
            pendingCaptureTimeouts.remove(timeoutRunnable)
            if (jsCaptureCompleted) return@Runnable
            if (BuildConfig.DEBUG) {
                Log.i(
                        REFRESH_SCROLL_TAG,
                        "capture timeout requestId=$requestId source=$captureSource nativeY=$scrollY"
                )
            }
            completeCapture()
        }
        pendingCaptureTimeouts.add(timeoutRunnable)
        webView.postDelayed(timeoutRunnable, REFRESH_SCROLL_CAPTURE_TIMEOUT_MS)
        val captureScript = if (linkSourceUrl != null) {
            """
            (function(){
                window.refreshRestoreSource=${JSONObject.quote(captureSource)};
                var targetUrl=${JSONObject.quote(linkSourceUrl)};
                if (typeof captureThemeLinkSourceAnchor==='function') {
                    var linkSource=captureThemeLinkSourceAnchor(targetUrl);
                    if (linkSource) return linkSource;
                }
                return (typeof captureThemeRefreshScrollAnchor==='function')?captureThemeRefreshScrollAnchor('link-click'):'';
            })();
            """.trimIndent()
        } else {
            """
            (function(){
                window.refreshRestoreSource=${JSONObject.quote(captureSource)};
                return (typeof captureThemeRefreshScrollAnchor==='function')?captureThemeRefreshScrollAnchor():'';
            })();
            """.trimIndent()
        }
        webView.evaluateJavascript(captureScript) { result ->
            if (disposed) return@evaluateJavascript
            if (!fragment.isAdded || fragment.view == null) return@evaluateJavascript
            val currentPage = presenter.getCurrentPageInstance()
            if (currentPage !== targetPage) {
                Timber.d("updateHistoryLastHtml: page changed, skipping callback")
                if (BuildConfig.DEBUG) Log.i(REFRESH_SCROLL_TAG, "capture stale requestId=$requestId source=$captureSource reason=pageChanged")
                return@evaluateJavascript
            }
            val pendingRefreshId = presenter.getPendingRefreshRequestId()
            if (requestId != null &&
                    presenter.getRefreshRestoreId() != requestId &&
                    pendingRefreshId != requestId
            ) {
                if (BuildConfig.DEBUG) {
                    Log.i(
                            REFRESH_SCROLL_TAG,
                            "capture stale requestId=$requestId source=$captureSource current=${presenter.getRefreshRestoreId()} pending=$pendingRefreshId"
                    )
                }
                return@evaluateJavascript
            }
            val raw = result?.let { JSONObject(jsonEscape(it)).optString("v") }?.takeIf { it.isNotEmpty() }
            val json = raw?.let { runCatching { JSONObject(it) }.getOrNull() }
            val postId = json?.optString("postId")?.takeIf { it.isNotEmpty() }
            val offsetTop = json?.optDouble("offsetTop")?.takeIf { !it.isNaN() }
            val domScrollY = json?.optDouble("scrollY")?.takeIf { !it.isNaN() }
            val domRatio = json?.optDouble("ratio")?.takeIf { !it.isNaN() } ?: ratio
            val domWasNearBottom = json?.optBoolean("wasNearBottom") ?: wasNearBottom
            val selectedTop = json?.optDouble("selectedPostTop")?.takeIf { !it.isNaN() }
            val selectedDistance = json?.optDouble("selectedDistance")?.takeIf { !it.isNaN() }
            val containers = json?.optInt("containers", -1) ?: -1
            val separators = json?.optInt("separators", -1) ?: -1
            val posts = json?.optInt("posts", -1) ?: -1
            val anchorSource = json?.optString("source")?.takeIf { it.isNotEmpty() }
            if (BuildConfig.DEBUG) {
                Timber.d("updateHistoryLastHtml: postId=$postId offsetTop=$offsetTop domScrollY=$domScrollY ratio=$domRatio")
                Log.i(
                        REFRESH_SCROLL_TAG,
                        "capture dom requestId=$requestId source=$captureSource anchorSource=$anchorSource t=${SystemClock.uptimeMillis()} scrollY=$scrollY viewportH=${webView.height} bodyH=${webView.contentHeight} post=$postId selectedTop=$selectedTop selectedDistance=$selectedDistance offset=$offsetTop domY=$domScrollY ratio=$domRatio wasNearBottom=$domWasNearBottom containers=$containers separators=$separators posts=$posts jsonLen=${raw?.length ?: 0}"
                )
                if (linkSourceUrl != null) {
                    Log.i(
                            THEME_HISTORY_TAG,
                            "link sourceUrl=$linkSourceUrl sourcePostId=$postId sourceOffset=$offsetTop sourcePage=${currentPage?.pagination?.current} visiblePage=${presenter.getRefreshCapturePageInstance()?.pagination?.current} anchorSource=$anchorSource"
                    )
                }
            }
            if (currentPage != null) {
                if (authoritativeLinkAnchor == null) {
                    presenter.updatePageRefreshScrollSnapshot(currentPage, (domScrollY ?: scrollY.toDouble()).toInt(), postId, offsetTop, domRatio, domWasNearBottom)
                } else {
                    Log.i(
                            THEME_HISTORY_TAG,
                            "link authoritative kept sourcePostId=${authoritativeLinkAnchor.postId} ignoredAsyncPost=$postId target=$linkSourceUrl"
                    )
                }
            }
            jsCaptureCompleted = true
            webView.removeCallbacks(timeoutRunnable)
            pendingCaptureTimeouts.remove(timeoutRunnable)
            completeCapture()
        }
    }

    fun captureBottomRefreshScrollSnapshot() {
        val targetPage = presenter.getRefreshCapturePageInstance()
        val maxScroll = ((webView.contentHeight * webView.scale).toInt() - webView.height).coerceAtLeast(0)
        if (BuildConfig.DEBUG) {
            Log.i(
                    REFRESH_SCROLL_TAG,
                    "capture bottomForced t=${SystemClock.uptimeMillis()} scrollY=${webView.scrollY} max=$maxScroll content=${webView.contentHeight} scale=${webView.scale} height=${webView.height} ratio=1.0 wasNearBottom=true"
            )
        }
        if (targetPage != null) {
            presenter.updatePageRefreshScrollSnapshot(targetPage, maxScroll, null, null, 1.0, true)
        }
    }

    private fun jsonEscape(value: String): String {
        return "{\"v\":$value}"
    }

    fun saveScrollYForImageViewer() {
        savedScrollYForImageViewer = webView.scrollY
    }

    fun restoreScrollYAfterImageViewer() {
        val scrollY = savedScrollYForImageViewer
        if (scrollY >= 0) {
            savedScrollYForImageViewer = -1
            webView.post {
                if (!disposed && fragment.view != null) webView.scrollTo(0, scrollY)
            }
        }
    }

    fun clearFocusAndHideKeyboard() {
        webView.clearFocus()
        // hideKeyboard() должен вызываться извне
    }

    fun applyImeInsets(
            coordinatorLayout: View,
            messagePanel: MessagePanel,
            refreshLayout: View
    ) = imeInsetsController.applyImeInsets(coordinatorLayout, messagePanel, refreshLayout)

    fun forceReapplyImeInsets(
            coordinatorLayout: View,
            messagePanel: MessagePanel,
            messagePanelHost: View,
            refreshLayout: View
    ) = imeInsetsController.forceReapplyImeInsets(coordinatorLayout, messagePanel, messagePanelHost, refreshLayout)

    fun resetImeInsets(
            coordinatorLayout: View,
            messagePanelHost: View
    ) = imeInsetsController.resetImeInsets(coordinatorLayout, messagePanelHost)

    fun forceFullLayoutReset(
            coordinatorLayout: View,
            messagePanel: MessagePanel,
            messagePanelHost: View,
            refreshLayout: View
    ) = imeInsetsController.forceFullLayoutReset(coordinatorLayout, messagePanel, messagePanelHost, refreshLayout)

    fun saveScrollYOnHide() {
        if (disposed || fragment.view == null) return
        // Always capture best-effort scroll snapshot before leaving the topic.
        // LAST_UNREAD must not break BackRestore (Android back should restore exact position).
        val scrollY = webView.scrollY
        val targetPage = presenter.getCurrentPageInstance()
        val targetPageId = targetPage?.id
        val maxScroll = ((webView.contentHeight * webView.scale).toInt() - webView.height).coerceAtLeast(0)
        val ratio = if (maxScroll > 0) scrollY.toDouble() / maxScroll.toDouble() else 0.0
        val nearBottomTolerancePx = (24f * webView.resources.displayMetrics.density).toInt()
        val wasNearBottom = scrollY >= maxScroll - nearBottomTolerancePx
        if (BuildConfig.DEBUG) {
            Timber.d("onPauseOrHide: scrollY=$scrollY targetPage.id=$targetPageId targetPage.st=${targetPage?.st}")
            Log.i(
                    REFRESH_SCROLL_TAG,
                    "hide capture native scrollY=$scrollY max=$maxScroll ratio=$ratio bottom=$wasNearBottom target=$targetPageId st=${targetPage?.st}"
            )
        }
        if (targetPage != null) {
            presenter.updatePageHistoryHtml(targetPage, "", scrollY, scrollRatio = ratio, wasNearBottom = wasNearBottom)
        }
        if (webView.isJsReady) {
            webView.evaluateJavascript("(typeof captureThemeRefreshScrollAnchor==='function')?captureThemeRefreshScrollAnchor():''") { result ->
                if (disposed) return@evaluateJavascript
                if (!fragment.isAdded || fragment.view == null) return@evaluateJavascript
                val currentPage = presenter.getCurrentPageInstance()
                if (currentPage?.id != targetPageId) {
                    if (BuildConfig.DEBUG) Timber.d("onPauseOrHide: page changed, skipping callback (was=$targetPageId, now=${currentPage?.id})")
                    return@evaluateJavascript
                }
                val raw = result?.let { JSONObject(jsonEscape(it)).optString("v") }?.takeIf { it.isNotEmpty() }
                val json = raw?.let { runCatching { JSONObject(it) }.getOrNull() }
                val postId = json?.optString("postId")?.takeIf { it.isNotEmpty() }
                val offsetTop = json?.optDouble("offsetTop")?.takeIf { !it.isNaN() }
                val domRatio = json?.optDouble("ratio")?.takeIf { !it.isNaN() } ?: ratio
                val domWasNearBottom = json?.optBoolean("wasNearBottom") ?: wasNearBottom
                if (BuildConfig.DEBUG) {
                    Timber.d("onPauseOrHide: postId=$postId offsetTop=$offsetTop ratio=$domRatio")
                    Log.i(
                            REFRESH_SCROLL_TAG,
                            "hide capture dom post=$postId offset=$offsetTop ratio=$domRatio bottom=$domWasNearBottom jsonLen=${raw?.length ?: 0}"
                    )
                }
                if (currentPage != null) {
                    // Не затираем ранний снапшот пустым DOM-якорём: если JS не
                    // нашёл пост-якорь (postId пуст), сохраняем только уточнённые
                    // ratio/wasNearBottom, оставив anchorPostId из раннего прохода.
                    val safePostId = postId?.takeIf { it.isNotBlank() }
                    presenter.updatePageHistoryHtml(
                            currentPage,
                            "",
                            scrollY,
                            safePostId,
                            if (safePostId != null) offsetTop else null,
                            domRatio,
                            domWasNearBottom
                    )
                }
            }
        } else {
            if (BuildConfig.DEBUG) Timber.d("onPauseOrHide: skip captureThemeRefreshScrollAnchor (JS not ready)")
        }
    }

    fun deletePostUi(post: IBaseForumPost) {
        webView.evalJs("deletePost(" + post.id + ");")
    }

    fun cleanup() {
        if (disposed) return
        disposed = true
        sharedRenderController.cleanup()
        sharedRenderSession = null
        webView.clearQueuedJs()
        pendingCaptureTimeouts.forEach(webView::removeCallbacks)
        pendingCaptureTimeouts.clear()
        if (webView.isJsReady) {
            webView.evaluateJavascript(
                    "if(typeof destroyThemeRuntime==='function'){destroyThemeRuntime('android-destroy');}",
                    null
            )
        }
        webView.setJsLifeCycleListener(null)
        webView.setOnDirectionListener(null)
        webView.setDialogsHelper(null)
        webView.setDownloadListener(null)
        webView.systemLinkHandler = null
        webView.endWork()
    }

    override fun dispose() = cleanup()

    private inner class ThemeWebViewClient : CustomWebViewClient(avatarRepository, linkHandler, systemLinkHandler) {
        private val p = Pattern.compile("\\.(jpg|png|gif|bmp)")
        private val m = p.matcher("")
        private var lastHandledUrl: String? = null

        override fun handleUri(view: WebView, uri: Uri): Boolean {
            if (super.handleUri(view, uri)) {
                return true
            }
            if (disposed || fragment.view == null) return true
            val resolved = ArticleLinkResolver.resolveForNavigation(uri.toString()) ?: return true
            val resolvedUri = Uri.parse(resolved)
            if (SiteUrls.isSiteUri(resolvedUri)) {
                val sourceAnchor = presenter.consumeLinkSourceAnchorFor(resolved)
                val sourcePage = presenter.getRefreshCapturePageInstance()
                Log.i(
                        THEME_HISTORY_TAG,
                        "WebViewClient internal navigation target=$resolved sourceTopic=${sourcePage?.id} sourceSt=${sourcePage?.st} sourcePage=${sourcePage?.pagination?.current} sourcePostId=${sourceAnchor?.postId} sourceEvent=${sourceAnchor?.eventType}"
                )
                val navigation = ThemeLinkNavigationPolicy.resolve(resolved, sourceAnchor?.href)
                when (navigation.action) {
                    ThemeLinkNavigationAction.DOWNLOAD_URL -> {
                        closeToolbarOverlaysBeforeNavigation()
                        systemLinkHandler.handleDownload(navigation.url, null, webView.context, null)
                        return true
                    }
                    ThemeLinkNavigationAction.NAVIGATE_TO_URL,
                    ThemeLinkNavigationAction.OPEN_IMAGE_VIEWER -> {
                        updateHistoryLastHtml(
                                linkSourceUrl = resolved,
                                authoritativeLinkAnchor = sourceAnchor,
                                onCaptured = {
                                    val capturedSourcePage = presenter.getRefreshCapturePageInstance()
                                    Log.i(
                                            THEME_HISTORY_TAG,
                                            "link captured sourceTopic=${capturedSourcePage?.id} sourceSt=${capturedSourcePage?.st} sourcePage=${capturedSourcePage?.pagination?.current} historyEntryPost=${capturedSourcePage?.anchorPostId} historyEntryOffset=${capturedSourcePage?.anchorOffsetTop} historyEntryUrl=${capturedSourcePage?.url} target=${navigation.url}"
                                    )
                                    // TODO restore on next pass: ThemeViewModel.markLinkHistoryCaptured is not in the tracked code yet.
                                    closeToolbarOverlaysBeforeNavigation()
                                    presenter.handleNewUrl(Uri.parse(navigation.url), sourceAnchor?.href)
                                }
                        )
                        return true
                    }
                }
            }
            closeToolbarOverlaysBeforeNavigation()
            linkHandler.handle(resolved, null)
            return true
        }

        override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
            super.onPageStarted(view, url, favicon)
            if (disposed || fragment.view == null) return
            if (BuildConfig.DEBUG) {
                Log.i(
                        REFRESH_SCROLL_TAG,
                        "controller pageStarted trace=${presenter.getThemeLoadTraceId()} dt=${SystemClock.uptimeMillis() - lastLoadDataAt} url=$url progress=${view.progress}"
                )
            }
            if (url != lastHandledUrl && (url.contains("showtopic=") || url.contains("act=findpost"))) {
                lastHandledUrl = url
                val viewUrl = view.url
                if (!url.contains("android_asset") && viewUrl != null && !viewUrl.contains("android_asset")) {
                    val resolved = ArticleLinkResolver.resolveForNavigation(url) ?: return
                    closeToolbarOverlaysBeforeNavigation()
                    presenter.handleNewUrl(Uri.parse(resolved))
                }
            }
        }

        override fun onPageCommitVisible(view: WebView, url: String) {
            super.onPageCommitVisible(view, url)
            if (disposed || fragment.view == null) return
            if (BuildConfig.DEBUG) {
                Log.i(
                        REFRESH_SCROLL_TAG,
                        "controller pageCommitVisible trace=${presenter.getThemeLoadTraceId()} dt=${SystemClock.uptimeMillis() - lastLoadDataAt} url=$url progress=${view.progress} content=${webView.contentHeight}"
                )
            }
        }

        override fun onLoadResource(view: WebView, url: String) {
            super.onLoadResource(view, url)
            if (disposed || fragment.view == null) return
            if (shouldNotifyThemeProgress()) {
                if (!url.contains("forum/uploads") && !url.contains("android_asset") && !url.contains("style_images") && m.reset(url).find()) {
                    notifyThemeProgressChanged()
                }
            }
        }

        override fun onPageFinished(view: WebView, url: String) {
            super.onPageFinished(view, url)
            if (disposed || fragment.view == null) return
            if (BuildConfig.DEBUG) {
                Log.i(
                        REFRESH_SCROLL_TAG,
                        "controller pageFinished trace=${presenter.getThemeLoadTraceId()} dt=${SystemClock.uptimeMillis() - lastLoadDataAt} url=$url progress=${view.progress} content=${webView.contentHeight} progressCallbacks=$progressCallbackCount progressJs=$progressJsNotifyCount"
                )
            }
            if (presenter.isEndNavigationPending() ||
                    (presenter.loadAction == forpdateam.ru.forpda.presentation.theme.ThemeLoadAction.Refresh &&
                            presenter.isRefreshScrollRestoreActive())
            ) {
                scheduleMissedDomLifecycleProbe(120L)
            }
        }
    }

    fun onDomRendered() {
        val traceAtEntry = if (BuildConfig.DEBUG) presenter.getThemeLoadTraceId() else ""
        if (BuildConfig.DEBUG) {
            val activeRk = activeRenderKey
            val jsReady = webView.isJsReady
            Log.i(
                    REFRESH_SCROLL_TAG,
                    "controller onDomRendered_entered trace=$traceAtEntry activeRenderKey=$activeRk jsReady=$jsReady disposed=$disposed viewAttached=${fragment.view != null}"
            )
        }
        if (disposed || fragment.view == null) {
            if (BuildConfig.DEBUG) {
                Log.i(
                        REFRESH_SCROLL_TAG,
                        "controller onDomRendered_exited trace=$traceAtEntry reason=disposed_or_no_view"
                )
            }
            return
        }
        val renderKey = activeRenderKey
        if (renderKey == null) {
            if (BuildConfig.DEBUG) {
                Log.i(
                        REFRESH_SCROLL_TAG,
                        "controller onDomRendered_exited trace=$traceAtEntry reason=no_active_render_key"
                )
            }
            return
        }
        if (!webView.isJsReady) {
            if (BuildConfig.DEBUG) {
                Log.i(
                        REFRESH_SCROLL_TAG,
                        "controller onDomRendered_exited trace=$traceAtEntry reason=js_not_ready"
                )
            }
            return
        }
        val expectedPosts = activeRenderExpectedPosts
        val script = """
            (function(){
                var posts=document.querySelectorAll('.post_container[data-post-id]:not(.topic_hat_entry):not(.topic_hat_fixed)').length;
                var containers=document.querySelectorAll('.theme_page_container[data-page-number]').length;
                return JSON.stringify({posts:posts,containers:containers});
            })();
        """.trimIndent()
        webView.evaluateJavascript(script) { result ->
            if (disposed) return@evaluateJavascript
            if (!fragment.isAdded || fragment.view == null) return@evaluateJavascript
            if (activeRenderKey != renderKey) return@evaluateJavascript
            val raw = result
                    ?.let { runCatching { JSONObject("{\"v\":$it}").optString("v") }.getOrNull() }
                    ?.takeIf { it.isNotEmpty() }
            val json = raw?.let { runCatching { JSONObject(it) }.getOrNull() }
            val domPosts = json?.optInt("posts", 0) ?: 0
            val hasExpectedPosts = when {
                expectedPosts <= 0 -> domPosts > 0
                else -> domPosts >= expectedPosts
            }
            if (BuildConfig.DEBUG) {
                Log.i(
                        REFRESH_SCROLL_TAG,
                        "controller onDomRendered_evaluate_result trace=$traceAtEntry renderKey=$renderKey raw=$result domPosts=$domPosts expected=$expectedPosts hasExpectedPosts=$hasExpectedPosts"
                )
            }
            completedRenderKey = renderKey
            completedRenderHasPosts = hasExpectedPosts
            if (hasExpectedPosts) {
                reapplyTopicHighlight()
            }
            flushPendingScrollCommand()
            if (hasExpectedPosts) {
                presenter.revealThemeContentAfterDomRendered()
            }
            if (presenter.isEndNavigationPending() && hasExpectedPosts) {
                scheduleEndNavigationScrollIfNeeded("domRendered")
            } else if (hasExpectedPosts) {
                schedulePostedPageScrollIfNeeded("domRendered")
            }
            if (BuildConfig.DEBUG) {
                val domContainers = json?.optInt("containers", 0) ?: 0
                Log.i(
                        THEME_RENDER_TAG,
                        "controller domRendered trace=${presenter.getThemeLoadTraceId()} renderKey=$renderKey expectedPosts=$expectedPosts domPosts=$domPosts containers=$domContainers complete=$hasExpectedPosts"
                )
            }
        }
    }

    /**
     * Re-applies the topic-post highlight on the live WebView once the DOM posts are
     * confirmed present. The renderer already stamps the `post-highlight-*` class
     * into the static HTML in [template_theme.html], so this is a defensive
     * re-assertion that survives smart-patch / infinite-scroll DOM mutations and
     * also lights up the diagnostic `js_highlight_applied` / `native_highlight_bound`
     * events the QA checklist requires.
     *
     * Stale generations are filtered by the JS guard in `window.PPDA_applyHighlight`
     * which calls back into [onJsStaleHighlight] via [IThemePresenter.reportStaleHighlight].
     */
    /**
     * Re-arm the highlight after programmatic scroll settles or the WebView becomes
     * visible. Clears only the apply guard so [reapplyTopicHighlight] can re-assert
     * the JS class when scroll deferred the first arm. The fade-out timer is never
     * reset here — [highlightFadeoutScheduledGeneration] stays idempotent per render
     * generation so the original 2-second deadline is preserved.
     */
    fun reapplyTopicHighlightAfterScrollSettled() {
        setHighlightArmedGeneration(
                newValue = 0,
                caller = "reapplyTopicHighlightAfterScrollSettled"
        )
        setHighlightArmedPostId(
                newValue = 0L,
                caller = "reapplyTopicHighlightAfterScrollSettled"
        )
        // H-03: clear the dispatch record too so the deferred re-arm can actually dispatch the
        // apply that the initial (deferApply=true) pass skipped.
        highlightApplyDispatchedGeneration = 0
        highlightApplyDispatchedPostId = 0L
        reapplyTopicHighlight()
    }

    /** Called when the WebView alpha flips to visible; skips while scroll is pending. */
    fun onWebViewContentRevealed() {
        if (HighlightArmingPolicy.shouldDeferUntilScrollSettled(presenter.hasBlockingScrollPending())) {
            return
        }
        reapplyTopicHighlightAfterScrollSettled()
    }

    /**
     * Resolve the post-highlight target for the current page and apply the
     * matching JS class via [ThemeJsApi.applyHighlight] / arm the fadeout
     * timer via [ThemeJsApi.scheduleHighlightFadeout]. Idempotent for a given
     * render generation: the per-generation guards ([highlightArmedGeneration]
     * / [highlightFadeoutScheduledGeneration]) keep a single arming and the
     * JS-side `PPDA_applyHighlight` ignores stale callbacks.
     */
    private fun reapplyTopicHighlight() {
        if (BuildConfig.DEBUG) {
            val trace = presenter.getThemeLoadTraceId()
            Log.i(
                    THEME_RENDER_TAG,
                    "controller reapplyTopicHighlight_called trace=$trace disposed=$disposed viewAttached=${fragment.view != null}"
            )
        }
        if (disposed || fragment.view == null) {
            if (BuildConfig.DEBUG) {
                TopicHighlightDiagnostics.highlightArmSkipped(
                        topicId = 0L,
                        reason = "disposed_or_no_view"
                )
            }
            return
        }
        // The renderer path (mapEntity → applyToPage) is the canonical place to
        // stamp `highlightTarget` / `renderGenerationId`, but it can be bypassed
        // (cached-page restore, smart-patch re-apply, fast re-render on tab show).
        // Resolving it here as a defensive step guarantees the page always has a
        // stamped target before we ask the JS side to highlight it.
        val resolution = runCatching { presenter.applyHighlightForCurrentPage() }.getOrNull()
        if (resolution != null && BuildConfig.DEBUG) {
            // Silence unused-var warning in release; the value is observable in
            // the diagnostic logs emitted by TopicHighlightApply.
            @Suppress("UNUSED_VARIABLE")
            val unused = resolution.reason
        }
        val page = presenter.getCurrentPageInstance()
        if (page == null) {
            if (BuildConfig.DEBUG) {
                TopicHighlightDiagnostics.highlightArmSkipped(
                        topicId = 0L,
                        reason = "no_current_page"
                )
            }
            return
        }
        val highlight = page.highlightTarget
        if (highlight == null) {
            if (BuildConfig.DEBUG) {
                TopicHighlightDiagnostics.highlightArmSkipped(
                        topicId = page.id.toLong(),
                        reason = "no_highlight_target",
                        renderGenerationId = page.renderGenerationId
                )
            }
            return
        }
        if (highlight is forpdateam.ru.forpda.presentation.theme.HighlightTarget.None) {
            if (BuildConfig.DEBUG) {
                TopicHighlightDiagnostics.highlightArmSkipped(
                        topicId = page.id.toLong(),
                        reason = "highlight_target_none",
                        renderGenerationId = page.renderGenerationId
                )
            }
            return
        }
        val topicId = page.id
        if (topicId <= 0) {
            if (BuildConfig.DEBUG) {
                TopicHighlightDiagnostics.highlightArmSkipped(
                        topicId = topicId.toLong(),
                        reason = "topic_id_non_positive",
                        renderGenerationId = page.renderGenerationId,
                        postId = highlight.postId
                )
            }
            return
        }
        val generation = page.renderGenerationId
        if (generation <= 0) {
            if (BuildConfig.DEBUG) {
                TopicHighlightDiagnostics.highlightArmSkipped(
                        topicId = topicId.toLong(),
                        reason = "generation_non_positive",
                        renderGenerationId = generation,
                        postId = highlight.postId
                )
            }
            return
        }
        val postId = highlight.postId
        if (postId <= 0L) {
            if (BuildConfig.DEBUG) {
                TopicHighlightDiagnostics.highlightArmSkipped(
                        topicId = topicId.toLong(),
                        reason = "post_id_non_positive",
                        renderGenerationId = generation,
                        postId = postId
                )
            }
            return
        }
        // One-highlight-per-render: once ANY post has been highlighted since the last page render,
        // suppress every later re-resolve for this render — whether it targets a different post OR the
        // same post under a new generation. Two cases seen on device:
        //  - different post: infinite-scroll append re-resolves to the true first-unread and flashes a
        //    second ring below the landing post (log 27_06-10-46, 1093640: 144025372 → 144027468).
        //  - SAME post, new generation: the async unread-target load flips the type (last-read →
        //    first-unread) for the SAME post, which bumps renderGenerationId in applyToPage so the
        //    (gen,post) done-latch no longer matches and the ring re-flashes (log 28_06-10-03, 138395:
        //    144035134 lit at gen 6 last-read, then again at gen 7 first-unread).
        // A genuine page/topic navigation resets the guard in renderThemePage, so a new page still
        // highlights its target.
        if (highlightAppliedPostIdSinceRender != 0L) {
            if (BuildConfig.DEBUG) {
                TopicHighlightDiagnostics.highlightArmSkipped(
                        topicId = topicId.toLong(),
                        reason = if (highlightAppliedPostIdSinceRender == postId)
                            "already_highlighted_same_post_this_render"
                        else
                            "already_highlighted_other_post_this_render",
                        renderGenerationId = generation,
                        postId = postId
                )
            }
            return
        }
        // STEP 2 — arm the sticky explicit-anchor intent. An explicit-post / findpost open arms the
        // blocking INITIAL_ANCHOR path (Step 1). Store the target postId separately from generation
        // so a generation bump (reload / smart-patch re-render) does NOT drop the pending anchor.
        // Cleared by [onInitialAnchorScrollSettled] once the blocking scroll reports success —
        // event/state-based, not a timer.
        if (presenter.isExplicitAnchorBlockingOpenForController() && postId != pendingExplicitAnchorPostId) {
            pendingExplicitAnchorPostId = postId
            presenter.resetExplicitAnchorScrollSettled()
        }
        // H-02 (audit Finding H-02): when the page's render generation changed since we last armed,
        // invalidate the stale armed/fadeout flags so the new target re-arms cleanly. Covers page-change
        // paths that bypass renderThemePage's reset. Idempotent for an unchanged generation.
        if (generation != lastObservedHighlightRenderGeneration) {
            if (lastObservedHighlightRenderGeneration != 0 &&
                    (highlightArmedGeneration != 0 || highlightFadeoutScheduledGeneration != 0)
            ) {
                setHighlightArmedGeneration(newValue = 0, caller = "reapplyTopicHighlight.generationChanged")
                setHighlightArmedPostId(newValue = 0L, caller = "reapplyTopicHighlight.generationChanged")
                setHighlightFadeoutScheduledGeneration(
                        newValue = 0,
                        caller = "reapplyTopicHighlight.generationChanged"
                )
                // H-03: a changed render generation invalidates the prior dispatch too.
                highlightApplyDispatchedGeneration = 0
                highlightApplyDispatchedPostId = 0L
            }
            lastObservedHighlightRenderGeneration = generation
        }
        // Double-light-up latch (device log 24_06-23-12): once this exact
        // (generation, postId) has run its single light+fade cycle, ALL later
        // re-arms for the same pair are no-ops — no re-dispatch, no re-schedule.
        // This survives renderThemePage / resetRenderState /
        // reapplyTopicHighlightAfterScrollSettled (which reset the dispatch
        // guard) so a smart-patch re-render or a repeated reveal/scroll-settled
        // event cannot re-light the ring. A genuinely new generation+post does
        // not match the latch and still lights up once.
        //
        // STEP 2 — sticky explicit-anchor exception: an explicit-anchor open whose post has
        // NOT yet settled near the viewport top must NOT be dropped by this latch. A generation
        // bump (reload / smart-patch re-render) used to drop the pending anchor with
        // `reason=generation_done` and the user landed on a random post. The sticky intent
        // survives the bump and is re-armed until the JS side confirms the post is in the
        // viewport (`isThemeAnchorNearViewportTop`).
        if (HighlightArmingPolicy.isHighlightCycleAlreadyCompleted(
                        completedGeneration = highlightCompletedGeneration,
                        completedPostId = highlightCompletedPostId,
                        currentGeneration = generation,
                        currentPostId = postId,
                ) && !HighlightArmingPolicy.isPendingExplicitAnchorUnsettled(
                        pendingPostId = pendingExplicitAnchorPostId,
                        anchorSettled = presenter.isExplicitAnchorScrollSettledForController(),
                )) {
            if (BuildConfig.DEBUG) {
                TopicHighlightDiagnostics.highlightArmSkipped(
                        topicId = topicId.toLong(),
                        reason = "generation_done",
                        renderGenerationId = generation,
                        postId = postId,
                        armedGeneration = highlightApplyDispatchedGeneration,
                        fadeoutScheduledGeneration = highlightFadeoutScheduledGeneration
                )
            }
            return
        }
        val typeName = highlight.type.jsName
        // A soft last_post_on_page_fallback ring must not drive the highlight auto-scroll: on a
        // pagination/page jump the page is at its FIRST post and scrolling to the last-post guess
        // would yank it to the bottom. Genuine unread/last-read/explicit targets still scroll.
        val allowHighlightScroll = !((highlight as? HighlightTarget.LastRead)?.softFallback ?: false)
        val blockingScrollPending = presenter.hasBlockingScrollPending()
        val deferApply = HighlightArmingPolicy.shouldDeferUntilScrollSettled(blockingScrollPending)
        // H-03 (log 24_06-20-37): anchor the apply decision on whether the apply JS was actually
        // DISPATCHED for this (generation, postId) — not on the `armed*` bookkeeping, which the log
        // showed pre-satisfied on the first reapply with zero applies ever dispatched.
        val shouldApply = !deferApply && HighlightArmingPolicy.shouldDispatchApplyForCurrentTarget(
                dispatchedGeneration = highlightApplyDispatchedGeneration,
                dispatchedPostId = highlightApplyDispatchedPostId,
                currentGeneration = generation,
                currentPostId = postId,
        )
        // H-01 (audit Finding H-01): the 2-second fade-out window must be armed ONLY after the
        // highlight is actually applied (confirmed js_highlight_applied). Previously the fade-out
        // was scheduled independently of apply, so when apply was deferred until the blocking scroll
        // settled (deferApply=true) the timer started counting against an invisible/unpainted post —
        // the ring could fade before it was ever visible. When apply is deferred we now defer the
        // fade-out scheduling too; it is armed on the deferred re-arm path
        // (reapplyTopicHighlightAfterScrollSettled → onWebViewContentRevealed).
        val shouldScheduleFadeout = shouldApply && highlightFadeoutScheduledGeneration != generation
        if (!shouldScheduleFadeout && !shouldApply) {
            if (BuildConfig.DEBUG) {
                TopicHighlightDiagnostics.highlightArmSkipped(
                        topicId = topicId.toLong(),
                        // H-01: distinguish "apply (and fade-out) deferred until scroll settles" from
                        // "already armed for this exact (generation, postId)". The deferred case re-arms
                        // later via reapplyTopicHighlightAfterScrollSettled.
                        reason = if (deferApply) "deferred_until_scroll_settled" else "already_dispatched",
                        renderGenerationId = generation,
                        postId = postId,
                        deferApply = deferApply,
                        blockingScrollPending = blockingScrollPending,
                        armedGeneration = highlightApplyDispatchedGeneration,
                        fadeoutScheduledGeneration = highlightFadeoutScheduledGeneration
                )
            }
            return
        }
        val js = buildString {
            append(jsApi.setReadPosObserverEnabled(false))
            if (shouldApply) {
                append('\n')
                append(jsApi.applyHighlight(postId, typeName, generation, allowHighlightScroll))
            }
            if (shouldScheduleFadeout) {
                append('\n')
                append(jsApi.scheduleHighlightFadeout(generation, HIGHLIGHT_FADEOUT_DELAY_MS))
            }
        }
        jsApi.eval(js)
        if (shouldApply) {
            // H-03: record the *dispatch* the instant the apply JS leaves native, BEFORE the armed
            // flags. This is the value the `already_dispatched` guard reads, so the first genuine
            // resolve can never be suppressed by stale armed bookkeeping.
            highlightApplyDispatchedGeneration = generation
            highlightApplyDispatchedPostId = postId
            // One-highlight-per-open: remember the post we actually lit so an infinite-scroll re-render
            // cannot flash a different post until the next genuine page render (renderThemePage resets).
            highlightAppliedPostIdSinceRender = postId
            TopicHighlightDiagnostics.nativeHighlightDispatched(
                    topicId = topicId.toLong(),
                    page = page.pagination.current,
                    renderGenerationId = generation,
                    highlightType = typeName,
                    postId = postId
            )
        }
        if (shouldScheduleFadeout) {
            setHighlightFadeoutScheduledGeneration(
                    newValue = generation,
                    caller = "reapplyTopicHighlight.scheduleFadeout"
            )
            // Double-light-up latch: the single light+fade cycle for this exact
            // (generation, postId) is now committed (apply dispatched + fadeout
            // armed). Record it durably so any later re-render / reveal /
            // scroll-settled re-arm for the same pair short-circuits above and
            // can never re-light the ring. NOT cleared by render/reset paths.
            highlightCompletedGeneration = generation
            highlightCompletedPostId = postId
            ReadPositionSaveGate.onHighlightArmed(generation)
            TopicHighlightDiagnostics.highlightFadeoutScheduled(
                    topicId = topicId.toLong(),
                    page = page.pagination.current,
                    renderGenerationId = generation,
                    delayMs = HIGHLIGHT_FADEOUT_DELAY_MS,
                    highlightType = typeName,
                    postId = highlight.postId
            )
        }
        if (shouldApply) {
            setHighlightArmedGeneration(
                    newValue = generation,
                    caller = "reapplyTopicHighlight.applyHighlight"
            )
            setHighlightArmedPostId(
                    newValue = postId,
                    caller = "reapplyTopicHighlight.applyHighlight"
            )
            TopicHighlightDiagnostics.nativeHighlightBound(
                    topicId = topicId.toLong(),
                    page = page.pagination.current,
                    renderGenerationId = generation,
                    highlightType = typeName,
                    postId = postId
            )
            TopicHighlightDiagnostics.jsHighlightApplied(
                    topicId = topicId.toLong(),
                    page = page.pagination.current,
                    renderGenerationId = generation,
                    highlightType = typeName,
                    postAnchorExists = true
            )
        }
    }

    /**
     * Emits the `render_highlight_applied` diagnostic after the renderer stamps
     * the static `post-highlight-*` class into the page HTML. `appliedSuccessfully`
     * is true when the highlight target post is present in the rendered page list
     * (so the static class will be present in the DOM); false when the target
     * post is missing from this page (e.g. the user landed on a different page
     * than the highlight target). This is the post-render counterpart of the JS
     * `js_highlight_applied` event that fires from [reapplyTopicHighlight].
     */
    private fun logRenderHighlightApplied(page: ThemePage) {
        val highlight = page.highlightTarget
        val topicId = page.id.toLong()
        val pageNumber = page.pagination.current
        val generation = page.renderGenerationId
        if (highlight == null || highlight is forpdateam.ru.forpda.presentation.theme.HighlightTarget.None) {
            if (BuildConfig.DEBUG && generation > 0 && topicId > 0L) {
                TopicHighlightDiagnostics.renderHighlightApplied(
                        topicId = topicId,
                        page = pageNumber,
                        renderGenerationId = generation,
                        mode = "none",
                        highlightType = "none",
                        appliedSuccessfully = false,
                        postAnchorExists = false
                )
            }
            return
        }
        if (generation <= 0 || topicId <= 0L) return
        val postId = highlight.postId
        val postAnchorExists = postId > 0L && page.posts.any { it.id.toLong() == postId }
        TopicHighlightDiagnostics.renderHighlightApplied(
                topicId = topicId,
                page = pageNumber,
                renderGenerationId = generation,
                mode = "static",
                highlightType = highlight.type.jsName,
                appliedSuccessfully = postAnchorExists,
                postAnchorExists = postAnchorExists
        )
        if (!postAnchorExists) {
            TopicHighlightDiagnostics.highlightFailedPostNotFound(
                    topicId = topicId,
                    page = pageNumber,
                    renderGenerationId = generation,
                    highlightType = highlight.type.jsName,
                    expectedPostId = postId,
                    failureReason = "post_not_in_page"
            )
        }
    }

    private inner class ThemeChromeClient : CustomWebChromeClient() {
        override fun onProgressChanged(view: WebView, progress: Int) {
            if (disposed || fragment.view == null) return
            if (BuildConfig.DEBUG) {
                val now = SystemClock.uptimeMillis()
                val gap = if (lastProgressCallbackAt == 0L) 0L else now - lastProgressCallbackAt
                lastProgressCallbackAt = now
                progressCallbackCount++
                if (progressCallbackCount <= 6 || progress >= 100 || gap > 64L) {
                    Log.i(
                            REFRESH_SCROLL_TAG,
                            "controller progress trace=${presenter.getThemeLoadTraceId()} count=$progressCallbackCount progress=$progress dt=${now - lastLoadDataAt} gap=$gap jsCount=$progressJsNotifyCount"
                    )
                }
            }
            if (shouldNotifyThemeProgress()) {
                notifyThemeProgressChanged()
            }
        }
    }

    fun probeMissedDomLifecycleAfterRender() {
        if (presenter.loadAction != forpdateam.ru.forpda.presentation.theme.ThemeLoadAction.Refresh ||
                !presenter.isRefreshScrollRestoreActive()
        ) {
            return
        }
        scheduleMissedDomLifecycleProbe(0L)
    }

    /**
     * Kotlin-side fallback when [IBase.onPageLoaded] is missed after rapid [loadDataWithBaseURL]
     * (log: domComplete + blankVerifyOk but no onNativePageComplete → renderComplete=false).
     */
    fun scheduleMissedPageLifecycleProbe(vararg delayMs: Long) {
        if (delayMs.isEmpty()) {
            scheduleMissedPageLifecycleProbe(400L)
            return
        }
        val probeGeneration = renderGeneration
        delayMs.forEach { delay ->
            webView.postDelayed({
                if (disposed || fragment.view == null) return@postDelayed
                if (probeGeneration != renderGeneration) return@postDelayed
                probeMissedPageLifecycle()
            }, delay)
        }
    }

    fun probeMissedPageLifecycleAfterDomVerified() {
        scheduleMissedPageLifecycleProbe(0L)
    }

    private fun scheduleMissedDomLifecycleProbe(delayMs: Long) {
        webView.postDelayed({
            if (disposed || fragment.view == null) return@postDelayed
            probeMissedDomLifecycle()
        }, delayMs)
    }

    private fun probeMissedPageLifecycle() {
        if (disposed || fragment.view == null) return
        if (!ThemeMissedPageLifecyclePolicy.shouldProbeMissedPageLifecycle(
                        renderGeneration = renderGeneration,
                        domLifecycleGeneration = domLifecycleGeneration,
                        pageLifecycleGeneration = pageLifecycleGeneration,
                )
        ) {
            return
        }
        val script = """
            (function(){
                try{
                    var posts=document.querySelectorAll('.post_container[data-post-id]:not(.topic_hat_entry):not(.topic_hat_fixed)').length;
                    if(posts<=0) return false;
                    if(typeof IBase!=='undefined' && typeof IBase.onPageLoaded==='function'){
                        IBase.onPageLoaded();
                        return true;
                    }
                    return false;
                }catch(e){return false;}
            })();
        """.trimIndent()
        webView.evaluateJavascript(script) { raw ->
            if (disposed) return@evaluateJavascript
            val recovered = raw?.trim()?.equals("true", ignoreCase = true) == true
            if (BuildConfig.DEBUG) {
                Log.i(
                        REFRESH_SCROLL_TAG,
                        "page_lifecycle_probe recovered=$recovered trace=${presenter.getThemeLoadTraceId()} gen=$renderGeneration content=${webView.contentHeight}"
                )
            }
        }
    }

    private fun probeMissedDomLifecycle() {
        if (disposed || fragment.view == null) return
        if (domLifecycleGeneration == renderGeneration && renderGeneration > 0) return
        if (renderGeneration <= 0) return
        val refreshRestore = presenter.loadAction == forpdateam.ru.forpda.presentation.theme.ThemeLoadAction.Refresh &&
                presenter.isRefreshScrollRestoreActive()
        val domLifecycleMissed = domLifecycleGeneration != renderGeneration
        if (!presenter.isEndNavigationPending() && !refreshRestore && !domLifecycleMissed) return
        val script = """
            (function(){
                try{
                    var posts=document.querySelectorAll('.post_container[data-post-id]').length;
                    if(posts<=0) return false;
                    var recovered=false;
                    if(typeof IBase!=='undefined'){
                        if(typeof IBase.domContentLoaded==='function'){
                            IBase.domContentLoaded();
                            recovered=true;
                        }
                        if(typeof IBase.onPageLoaded==='function'){
                            IBase.onPageLoaded();
                        }
                    }
                    return recovered;
                }catch(e){return false;}
            })();
        """.trimIndent()
        webView.evaluateJavascript(script) { raw ->
            if (disposed) return@evaluateJavascript
            val recovered = raw?.trim()?.equals("true", ignoreCase = true) == true
            if (BuildConfig.DEBUG) {
                val tag = if (refreshRestore) REFRESH_SCROLL_TAG else FpdaDebugLog.TAG_SMART_BUTTON
                val event = when {
                    recovered && refreshRestore -> "refresh_dom_recovered"
                    recovered -> "end_dom_recovered"
                    refreshRestore -> "refresh_dom_probe_miss"
                    else -> "end_dom_probe_miss"
                }
                if (refreshRestore) {
                    Log.i(
                            tag,
                            "$event trace=${presenter.getThemeLoadTraceId()} jsReady=${webView.isJsReady} content=${webView.contentHeight} restoreId=${presenter.getRefreshRestoreId()}"
                    )
                } else {
                    FpdaDebugLog.log(
                            tag,
                            event,
                            mapOf(
                                    "traceId" to presenter.getThemeLoadTraceId(),
                                    "jsReady" to webView.isJsReady,
                                    "contentHeight" to webView.contentHeight
                            )
                    )
                }
            }
            if (!recovered && presenter.isEndNavigationPending()) {
                scheduleEndNavigationScrollIfNeeded("domProbe")
            }
        }
    }

    private fun shouldNotifyThemeProgress(): Boolean {
        val action = presenter.loadAction
        return action == forpdateam.ru.forpda.presentation.theme.ThemeLoadAction.Normal ||
                action == forpdateam.ru.forpda.presentation.theme.ThemeLoadAction.Refresh ||
                action == forpdateam.ru.forpda.presentation.theme.ThemeLoadAction.End ||
                presenter.isEndNavigationPending()
    }

    private fun notifyThemeProgressChanged() {
        if (disposed || fragment.view == null) return
        val now = SystemClock.uptimeMillis()
        if (now - lastProgressJsAt < 100L) return
        lastProgressJsAt = now
        progressJsNotifyCount++
        webView.evalJs("onProgressChanged()")
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
}
