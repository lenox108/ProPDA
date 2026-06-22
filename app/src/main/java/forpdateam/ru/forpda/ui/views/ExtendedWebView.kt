package forpdateam.ru.forpda.ui.views

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.ActionMode
import android.view.ContextMenu
import android.view.Menu
import android.view.MenuItem
import android.view.SoundEffectConstants
import android.view.View
import android.view.ViewParent
import android.webkit.DownloadListener
import android.webkit.JavascriptInterface
import android.webkit.URLUtil
import android.webkit.ValueCallback
import android.webkit.WebSettings
import android.webkit.WebViewClient
import forpdateam.ru.forpda.BuildConfig
import forpdateam.ru.forpda.R
import forpdateam.ru.forpda.common.BatteryDebugLogger
import forpdateam.ru.forpda.common.getColorFromAttr
import forpdateam.ru.forpda.common.webview.DialogsHelper
import forpdateam.ru.forpda.common.webview.WebViewJsBatchMetrics
import forpdateam.ru.forpda.common.webview.UrlDecision
import forpdateam.ru.forpda.common.webview.UrlPolicy
import forpdateam.ru.forpda.common.webview.jsinterfaces.IBase
import forpdateam.ru.forpda.presentation.ISystemLinkHandler
import forpdateam.ru.forpda.ui.FontController
import java.util.LinkedList
import timber.log.Timber

/**
 * Профиль безопасности для WebView.
 * Определяет уровень доверия к контенту и какие JS интерфейсы разрешены.
 */
enum class WebViewSecurityProfile {
    /**
     * Полностью доверенный локальный контент (темы форума).
     * JS включён, разрешён базовый bridge IBase.
     */
    TRUSTED_LOCAL_TEMPLATE,

    /**
     * QMS-чат: HTML собирается клиентом из API-ответов 4PDA и подмешивается
     * в локальный шаблон, но сообщения/ники берутся с сервера и потенциально
     * содержат пользовательский контент. JS включён, базовый bridge IBase
     * разрешён (нужен для `domContentLoaded`/`onPageComplete`), но это
     * **отдельный** уровень доверия — уже, чем [TRUSTED_LOCAL_TEMPLATE]
     * (где шаблон полностью локальный), и шире, чем [TRUSTED_STATIC_ARTICLE]
     * (где bridge запрещён).
     */
    TRUSTED_QMS_CHAT,

    /**
     * Статический контент, генерируемый локально (статьи, объявления, правила форума).
     * JS включён, но базовый bridge IBase запрещён.
     * Допускаются узкоспециализированные интерфейсы с read-only методами.
     */
    TRUSTED_STATIC_ARTICLE,

    /**
     * Недоверенный внешний контент (внешние URL, поиск).
     * JS по необходимости, bridge запрещён.
     */
    UNTRUSTED_EXTERNAL
}

/**
 * Created by radiationx on 01.11.16.
 */
@SuppressLint("SetJavaScriptEnabled")
open class ExtendedWebView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : NestedWebView(context, attrs, defStyleAttr), IBase {
    internal var direction: Int = DIRECTION_NONE
    private var relativeScale: Int = 100
    private var fontScale: Float = 1.0f
    private var paddingBottomValue: Int = 0
    private var bottomChromePaddingValue: Int = Int.MIN_VALUE
    var isJsReady: Boolean = false

    private var onDirectionListener: OnDirectionListener? = null
    private var onScrollListener: OnScrollListener? = null
    // Состояние «поиска на странице». Хранит активный запрос и счётчик совпадений,
    // чтобы findNext выполнялся только после успешного findAllAsync (см. FindOnPageState).
    private val findOnPageState = forpdateam.ru.forpda.presentation.theme.FindOnPageState()
    private var findResultListener: OnFindResultListener? = null
    private var findListenerInstalled = false
    private var audioManager: AudioManager? = null
    private val mHandler: Handler = Handler(Looper.getMainLooper())
    private var mUiThread: Thread? = null
    private val actionsForWebView: LinkedList<Runnable> = LinkedList()
    private var jsLifeCycleListener: JsLifeCycleListener? = null
    private var dialogsHelper: DialogsHelper? = null
    var systemLinkHandler: ISystemLinkHandler? = null
    private var securityProfile: WebViewSecurityProfile = WebViewSecurityProfile.UNTRUSTED_EXTERNAL
    private var destroyedForReuse: Boolean = false
    // Стартуем в "paused" состоянии: WebView не учтён в глобальном счётчике активных,
    // пока его фрагмент не вызовет onResume(). Это держит баланс счётчика
    // activeTimerWebViews (первый onResume инкрементит, парный onPause декрементит).
    private var timersPaused: Boolean = true

    private val jsBatchLock = Any()
    private val pendingJs = StringBuilder()
    private var jsFlushPosted = false
    /** Number of commands currently buffered in [pendingJs] (guarded by [jsBatchLock]). */
    private var pendingJsCommandCount = 0
    /** DEBUG-oriented batching metrics (Phase 5). Counters are cheap; logging is DEBUG-gated. */
    val jsBatchMetrics = WebViewJsBatchMetrics()
    private val jsFlushRunnable = Runnable {
        val pair = synchronized(jsBatchLock) {
            jsFlushPosted = false
            if (pendingJs.isEmpty()) return@Runnable
            val batch = pendingJs.toString()
            val count = pendingJsCommandCount
            pendingJs.setLength(0)
            pendingJsCommandCount = 0
            batch to count
        }
        jsBatchMetrics.onFlush(pair.second.toLong())
        evalJsImmediate(pair.first)
    }

    interface OnDirectionListener {
        fun onDirectionChanged(direction: Int)
    }

    interface OnScrollListener {
        fun onScrollChange(scrollX: Int, scrollY: Int, oldScrollX: Int, oldScrollY: Int)
    }

    interface JsLifeCycleListener {
        fun onDomContentComplete(actions: ArrayList<String>)
        fun onPageComplete(actions: ArrayList<String>)
    }

    interface OnStartActionModeListener {
        fun onCreate(actionMode: ActionMode, callback: ActionMode.Callback)
        fun onClick(actionMode: ActionMode, item: MenuItem): Boolean
    }

    /** Уведомляет о результате «поиска на странице»: индекс активного совпадения и их общее число. */
    interface OnFindResultListener {
        fun onFindResult(activeMatchIndex: Int, matchCount: Int)
    }

    fun init(profile: WebViewSecurityProfile = WebViewSecurityProfile.UNTRUSTED_EXTERNAL) {
        destroyedForReuse = false
        securityProfile = profile
        mUiThread = Thread.currentThread()
        audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val settings = settings
        settings.layoutAlgorithm = WebSettings.LayoutAlgorithm.NORMAL
        settings.builtInZoomControls = false
        settings.minimumFontSize = 1
        settings.minimumLogicalFontSize = 1
        settings.defaultFontSize = 16
        // X-04: compute the user-font-scale-adjusted text zoom **once**, before
        // the WebView lays out its first page. The previous code path set
        // textZoom=100 here and only later reset it to `fontScale * 100`
        // further down, causing a visible "jumps from 100% → 130%" glitch
        // on the first paint of any theme/news screen. Setting it here means
        // the WebView never paints at the wrong size.
        settings.textZoom = (resources.configuration.fontScale * 100).toInt()
        
        // JavaScript включается в зависимости от профиля безопасности
        settings.javaScriptEnabled = when (profile) {
            WebViewSecurityProfile.UNTRUSTED_EXTERNAL -> false
            else -> true
        }
        if (profile != WebViewSecurityProfile.UNTRUSTED_EXTERNAL) {
            settings.javaScriptCanOpenWindowsAutomatically = true
            settings.setSupportMultipleWindows(true)
        }
        settings.domStorageEnabled = true
        settings.loadsImagesAutomatically = true
        settings.cacheMode = WebSettings.LOAD_DEFAULT
        settings.allowFileAccess = false
        settings.allowContentAccess = false
        settings.allowFileAccessFromFileURLs = false
        settings.allowUniversalAccessFromFileURLs = false
        // S-07: in release builds, refuse mixed (http inside https) content
        // outright. In debug builds, keep the legacy compatibility mode so QA
        // can see what would have been blocked before shipping — this catches
        // CDN regressions locally instead of after a release.
        settings.mixedContentMode = if (BuildConfig.DEBUG) {
            WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        } else {
            WebSettings.MIXED_CONTENT_NEVER_ALLOW
        }
        setRelativeFontSize(16)
        setBackgroundColor(context.getColorFromAttr(R.attr.background_base))
        // Note: textZoom is set earlier (X-04) so the first paint uses the
        // correct font scale. Do not reset it here.

        val systemLinkHandler = this.systemLinkHandler
        if (systemLinkHandler != null) {
            setDownloadListener(DownloadListener { url, _, contentDisposition, mimetype, _ ->
                val safeUrl = when (val decision = UrlPolicy.classify(url)) {
                    UrlDecision.Blocked -> {
                        Timber.w("Blocked unsafe WebView download URL")
                        return@DownloadListener
                    }
                    is UrlDecision.Internal -> decision.normalizedUrl
                    is UrlDecision.External -> decision.normalizedUrl
                }
                var isImage = false
                if (mimetype != null && mimetype.startsWith("image/")) {
                    isImage = true
                }
                val lowerUrl = safeUrl.lowercase()
                if (lowerUrl.endsWith(".jpg") || lowerUrl.endsWith(".jpeg") ||
                    lowerUrl.endsWith(".png") || lowerUrl.endsWith(".gif") ||
                    lowerUrl.endsWith(".bmp") || lowerUrl.endsWith(".webp")
                ) {
                    isImage = true
                }

                if (isImage) {
                    val intent = Intent(Intent.ACTION_VIEW)
                    intent.setDataAndType(android.net.Uri.parse(safeUrl), "image/*")
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    intent.addCategory(Intent.CATEGORY_BROWSABLE)
                    try {
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        val browserIntent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(safeUrl))
                        browserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        browserIntent.addCategory(Intent.CATEGORY_BROWSABLE)
                        context.startActivity(browserIntent)
                    }
                    return@DownloadListener
                }
                val fileName = URLUtil.guessFileName(safeUrl, contentDisposition, mimetype)
                systemLinkHandler.handleDownload(safeUrl, fileName, context, contentDisposition)
            })
        }
    }

    /**
     * Явно включает базовый JS bridge [IBase].
     * Вызывать только для trusted content (тема, QMS).
     * Не вызывать для внешних/неизвестных URL (статьи, поиск).
     * Проверяет профиль безопасности перед включением bridge.
     * Разрешённые профили: [WebViewSecurityProfile.TRUSTED_LOCAL_TEMPLATE]
     * (полностью локальный шаблон) и [WebViewSecurityProfile.TRUSTED_QMS_CHAT]
     * (QMS-чат, где bridge нужен для `domContentLoaded`/`onPageComplete`).
     */
    fun enableBaseBridge() {
        if (securityProfile != WebViewSecurityProfile.TRUSTED_LOCAL_TEMPLATE &&
            securityProfile != WebViewSecurityProfile.TRUSTED_QMS_CHAT
        ) {
            Timber.w("enableBaseBridge called with non-trusted profile: $securityProfile")
            return
        }
        addJavascriptInterface(this, IBase.JS_BASE_INTERFACE)
    }

    /** Удаляет базовый JS bridge. Вызывать в onDestroyView / перед загрузкой внешнего URL. */
    fun removeBaseBridge() {
        removeJavascriptInterface(IBase.JS_BASE_INTERFACE)
    }

    /**
     * Устанавливает профиль безопасности для WebView.
     * @param profile Новый профиль безопасности
     * @param enableBridge Автоматически включить базовый bridge для TRUSTED_LOCAL_TEMPLATE
     */
    fun setSecurityProfile(profile: WebViewSecurityProfile, enableBridge: Boolean = false) {
        securityProfile = profile
        settings.javaScriptEnabled = when (profile) {
            WebViewSecurityProfile.UNTRUSTED_EXTERNAL -> false
            else -> true
        }
        if (enableBridge && (profile == WebViewSecurityProfile.TRUSTED_LOCAL_TEMPLATE ||
                    profile == WebViewSecurityProfile.TRUSTED_QMS_CHAT)
        ) {
            enableBaseBridge()
        }
    }

    fun getSecurityProfile(): WebViewSecurityProfile = securityProfile

    override fun onPause() {
        super.onPause()
        if (!timersPaused) {
            timersPaused = true
            // pauseTimers()/resumeTimers() в Android — ПРОЦЕССНО-ГЛОБАЛЬНЫЕ: они
            // замораживают JS/таймеры во ВСЕХ WebView приложения, а не только в этом.
            // Поэтому глобальную паузу ставим лишь когда на паузу ушёл последний
            // активный WebView. Иначе фоновый WebView (например, тема в бэкстеке)
            // заморозит JS у видимого WebView поиска и контент не отрендерится.
            val remaining = activeTimerWebViews.updateAndGet { if (it > 0) it - 1 else 0 }
            if (remaining == 0) {
                pauseTimers()
                BatteryDebugLogger.logState("WebView", "pauseTimers", "profile=$securityProfile")
            }
        }
        clearQueuedJs()
        if (BuildConfig.DEBUG) Timber.v("onPause $this")
    }

    override fun onResume() {
        super.onResume()
        if (timersPaused) {
            timersPaused = false
            // Возобновляем глобальные таймеры при появлении первого активного WebView.
            if (activeTimerWebViews.incrementAndGet() == 1) {
                resumeTimers()
                BatteryDebugLogger.logState("WebView", "resumeTimers", "profile=$securityProfile")
            }
        }
        if (BuildConfig.DEBUG) Timber.v("onResume $this")
    }

    fun setOnDirectionListener(onDirectionListener: OnDirectionListener?) {
        this.onDirectionListener = onDirectionListener
    }

    fun setOnScrollListener(onScrollListener: OnScrollListener?) {
        this.onScrollListener = onScrollListener
    }

    /**
     * Слушатель результатов «поиска на странице». Нужен, чтобы UI (строка поиска)
     * мог показать счётчик совпадений / включить кнопки prev/next.
     * Установка слушателя также регистрирует нативный [FindListener] (см. [ensureFindListener]).
     */
    fun setOnFindResultListener(listener: OnFindResultListener?) {
        this.findResultListener = listener
        if (listener != null) ensureFindListener()
    }

    /**
     * Регистрирует нативный [WebView.FindListener]. БЕЗ него [findAllAsync] на многих
     * устройствах не подсвечивает совпадения и не скроллит к ним — это и есть корневая
     * причина неработающего «поиска на странице». Ставим один раз (идемпотентно).
     */
    private fun ensureFindListener() {
        if (findListenerInstalled) return
        findListenerInstalled = true
        setFindListener { activeMatchOrdinal, numberOfMatches, _ ->
            findOnPageState.onFindResult(activeMatchOrdinal, numberOfMatches)
            if (BuildConfig.DEBUG) {
                Timber.d("onFindResult numberOfMatches=%d active=%d", numberOfMatches, activeMatchOrdinal)
            }
            findResultListener?.onFindResult(
                findOnPageState.activeMatchIndex,
                findOnPageState.matchCount
            )
        }
    }

    /**
     * Запускает «поиск на странице». Сначала очищает прошлую подсветку, затем
     * запускает [findAllAsync] для непустого запроса (FindListener подсветит совпадения).
     * Пустая строка трактуется как очистка.
     */
    fun findOnPage(text: String) {
        ensureFindListener()
        if (BuildConfig.DEBUG) Timber.d("findOnPage len=%d", text.trim().length)
        when (val decision = findOnPageState.onTextChanged(text)) {
            is forpdateam.ru.forpda.presentation.theme.FindOnPageState.Decision.Clear -> {
                clearMatches()
                findResultListener?.onFindResult(-1, 0)
            }
            is forpdateam.ru.forpda.presentation.theme.FindOnPageState.Decision.Find -> {
                // clearMatches перед новым запросом — иначе на части устройств остаётся
                // старая подсветка и счётчик не пересчитывается.
                clearMatches()
                findAllAsync(decision.query)
            }
        }
    }

    /** Переход к следующему/предыдущему совпадению. Работает только после успешного [findOnPage]. */
    fun findOnPageNext(next: Boolean) {
        if (!findOnPageState.canFindNext()) return
        findNext(next)
    }

    /** Очищает подсветку и сбрасывает состояние «поиска на странице» (закрытие панели). */
    fun clearFindOnPage() {
        findOnPageState.reset()
        clearMatches()
        findResultListener?.onFindResult(-1, 0)
    }

    override fun onScrollChanged(scrollX: Int, scrollY: Int, oldScrollX: Int, oldScrollY: Int) {
        super.onScrollChanged(scrollX, scrollY, oldScrollX, oldScrollY)
        onScrollListener?.onScrollChange(scrollX, scrollY, oldScrollX, oldScrollY)
        if (!isUserScrollActive() || scrollY == oldScrollY) return
        val newDirection = if (scrollY > oldScrollY) DIRECTION_DOWN else DIRECTION_UP
        if (newDirection != direction) {
            direction = newDirection
            onDirectionListener?.onDirectionChanged(newDirection)
        }
    }

    fun getDirection(): Int = direction

    @JvmName("loadDataFixed")
    fun loadData(data: String, mimeType: String, encoding: String) {
        isJsReady = false
        bottomChromePaddingValue = Int.MIN_VALUE
        super.loadData(data, mimeType, encoding)
    }

    override fun loadDataWithBaseURL(baseUrl: String?, data: String, mimeType: String?, encoding: String?, historyUrl: String?) {
        if (destroyedForReuse) return
        isJsReady = false
        bottomChromePaddingValue = Int.MIN_VALUE
        super.loadDataWithBaseURL(baseUrl, data, mimeType, encoding, historyUrl)
    }

    override fun loadUrl(url: String) {
        if (destroyedForReuse && url != "about:blank") return
        isJsReady = false
        bottomChromePaddingValue = Int.MIN_VALUE
        super.loadUrl(url)
    }

    override fun loadUrl(url: String, additionalHttpHeaders: Map<String, String>) {
        if (destroyedForReuse) return
        isJsReady = false
        bottomChromePaddingValue = Int.MIN_VALUE
        super.loadUrl(url, additionalHttpHeaders)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (BuildConfig.DEBUG) Timber.v("onAttachedToWindow")
        isJsReady = false
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        if (BuildConfig.DEBUG) Timber.v("onDetachedFromWindow")
        mHandler.removeCallbacksAndMessages(null)
        isJsReady = false
    }

    override fun setInitialScale(scaleInPercent: Int) {
        super.setInitialScale(scaleInPercent)
        if (BuildConfig.DEBUG) Timber.v("SET INIT SCALE $scaleInPercent")
        setPaddingBottom(paddingBottomValue, force = true)
    }

    fun setRelativeScale(scale: Float) {
        try {
            relativeScale = (scale * (resources.displayMetrics.density * 100)).toInt()
            fontScale = scale
        } catch (ignore: Exception) {
            Timber.e(ignore, "WebView font scale error")
        }
        setInitialScale(relativeScale)
    }

    fun setRelativeFontSize(fontSize: Int) {
        settings.defaultFontSize = fontSize
        updatePaddingBottom()
    }

    fun setAppFontMode(mode: forpdateam.ru.forpda.ui.AppFontMode) {
        val fontFamily = FontController.webFontFamily(mode)
        val fontClass = FontController.webFontClass(mode)
        evalJs(
            """
            document.documentElement.style.setProperty("--app-font-family", ${fontFamily.jsStringLiteral()});
            document.documentElement.classList.remove(${FontController.ROBOTO_WEB_FONT_CLASS.jsStringLiteral()}, ${FontController.INTER_WEB_FONT_CLASS.jsStringLiteral()}, ${FontController.SOURCE_SANS_3_WEB_FONT_CLASS.jsStringLiteral()}, ${FontController.OPEN_SANS_WEB_FONT_CLASS.jsStringLiteral()}, ${FontController.SYSTEM_WEB_FONT_CLASS.jsStringLiteral()});
            document.documentElement.classList.add(${fontClass.jsStringLiteral()});
            """.trimIndent()
        )
        if (BuildConfig.DEBUG) {
            Timber.d(
                "selectedFontMode=%s webCssFontFamily=%s webViewRerendered=false",
                mode,
                fontFamily
            )
        }
    }

    fun setUseSystemFont(enabled: Boolean) {
        setAppFontMode(FontController.mode(enabled))
    }

    fun updatePaddingBottom() {
        setPaddingBottom(paddingBottomValue, force = true)
    }

    fun setBottomChromePadding(padding: Int) {
        val p = padding.coerceAtLeast(0)
        if (p == bottomChromePaddingValue) return
        bottomChromePaddingValue = p
        val density = resources.displayMetrics.density
        val cssPx = (p / density) * (1 / fontScale)
        runWhenScrollIdle {
            evalJs("if (typeof setBottomChromePadding === 'function') setBottomChromePadding($cssPx);")
        }
    }

    /**
     * Synchronously delivers the bottom-chrome padding to JS without waiting for
     * scroll-idle. Use only on cold page load (e.g. onDomContentComplete) so the
     * spacer is in place when the first 1ms scroll attempt fires; otherwise prefer
     * [setBottomChromePadding] which defers to avoid resize-during-scroll jank.
     */
    fun setBottomChromePaddingImmediate(padding: Int) {
        val p = padding.coerceAtLeast(0)
        if (p == bottomChromePaddingValue) return
        bottomChromePaddingValue = p
        val density = resources.displayMetrics.density
        val cssPx = (p / density) * (1 / fontScale)
        evalJs("if (typeof setBottomChromePadding === 'function') setBottomChromePadding($cssPx);", null)
    }

    fun setPaddingBottom(padding: Int) {
        setPaddingBottom(padding, force = false)
    }

    private fun setPaddingBottom(padding: Int, force: Boolean) {
        val maxPx = resources.getDimensionPixelSize(R.dimen.message_panel_web_bottom_padding_max)
        val p = Math.max(0, Math.min(padding, maxPx))
        if (!force && p == paddingBottomValue) return
        paddingBottomValue = p
        val cssPx = (paddingBottomValue / resources.displayMetrics.density) * (1 / fontScale)
        runWhenScrollIdle {
            evalJs("setPaddingBottom($cssPx);")
        }
    }

    fun evalJs(script: String) {
        enqueueEvalJs(script)
    }

    fun evalJs(script: String, resultCallback: ValueCallback<String>?) {
        if (destroyedForReuse) {
            jsBatchMetrics.onIgnoredAfterDestroy()
            if (BuildConfig.DEBUG) Timber.w("evalJs(callback) ignored after destroy")
            resultCallback?.onReceiveValue(null)
            return
        }
        syncWithJs { evaluateJavascript(script, resultCallback) }
    }

    private fun enqueueEvalJs(script: String?) {
        if (script == null || script.isEmpty()) return
        // Phase 5: never enqueue JS after the WebView was torn down; it can never run and
        // would only leak runnables/memory.
        if (destroyedForReuse) {
            jsBatchMetrics.onIgnoredAfterDestroy()
            if (BuildConfig.DEBUG) Timber.w("evalJs ignored after destroy")
            return
        }
        jsBatchMetrics.onCommandEnqueued()
        var overflow = false
        synchronized(jsBatchLock) {
            pendingJs.append(script)
            val t = script.trim()
            if (!t.endsWith(";")) {
                pendingJs.append(';')
            }
            pendingJsCommandCount++
            // Cap the buffer: a runaway producer must not build an unbounded script string.
            if (pendingJs.length >= MAX_PENDING_JS_LENGTH) {
                overflow = true
            } else if (!jsFlushPosted) {
                jsFlushPosted = true
                mHandler.postDelayed(jsFlushRunnable, JS_BATCH_DELAY_MS)
            }
        }
        if (overflow) {
            // Flush synchronously on the UI thread (idempotent; runnable re-checks emptiness).
            flushQueuedJs()
        }
    }

    fun flushQueuedJs() {
        mHandler.removeCallbacks(jsFlushRunnable)
        jsFlushRunnable.run()
    }

    fun clearQueuedJs() {
        mHandler.removeCallbacks(jsFlushRunnable)
        val dropped = synchronized(jsBatchLock) {
            val count = pendingJsCommandCount
            pendingJs.setLength(0)
            pendingJsCommandCount = 0
            jsFlushPosted = false
            count
        }
        jsBatchMetrics.onClear(dropped.toLong())
        actionsForWebView.clear()
    }

    private fun evalJsImmediate(script: String) {
        try {
            syncWithJs { evaluateJavascript(script, null) }
        } catch (error: Exception) {
            Timber.e(error, "WebView evaluateJS error")
            loadUrl("javascript:$script")
        }
    }

    /**
     * Phase 5: explicit immediate (non-batched) fire-and-forget eval for commands that must not
     * be delayed by the 16ms batch window. Still respects the destroy guard. Use sparingly —
     * most commands should go through the batched [evalJs].
     */
    fun evalJsNow(script: String?) {
        if (script.isNullOrEmpty()) return
        if (destroyedForReuse) {
            jsBatchMetrics.onIgnoredAfterDestroy()
            if (BuildConfig.DEBUG) Timber.w("evalJsNow ignored after destroy")
            return
        }
        jsBatchMetrics.onForcedImmediate()
        evalJsImmediate(script)
    }

    @JavascriptInterface
    override fun playClickEffect() {
        runInUiThread { tryPlayClickEffect() }
    }

    @JavascriptInterface
    override fun domContentLoaded() {
        runInUiThread {
            if (BuildConfig.DEBUG) Timber.v("domContentLoaded $isJsReady")
            isJsReady = true
            for (action in actionsForWebView) {
                try {
                    runInUiThread(action)
                } catch (exception: Exception) {
                    Timber.e(exception, "WebView runInUiThread error")
                }
            }
            actionsForWebView.clear()

            val actions = ArrayList<String>()
            jsLifeCycleListener?.let {
                try {
                    it.onDomContentComplete(actions)
                } catch (exception: Exception) {
                    Timber.e(exception, "WebView onDomContentComplete error")
                }
            }
            actions.add("nativeEvents.onNativeDomComplete();")

            val sb = StringBuilder(actions.size * 64)
            for (action in actions) {
                sb.append(action)
            }
            evalJs(sb.toString())
        }
    }

    @JavascriptInterface
    override fun onPageLoaded() {
        runInUiThread {
            if (BuildConfig.DEBUG) Timber.v("onPageLoaded $isJsReady")
            val actions = ArrayList<String>()
            jsLifeCycleListener?.let {
                try {
                    it.onPageComplete(actions)
                } catch (exception: Exception) {
                    Timber.e(exception, "WebView onPageComplete error")
                }
            }
            actions.add("nativeEvents.onNativePageComplete();")

            val sb = StringBuilder(actions.size * 64)
            for (action in actions) {
                sb.append(action)
            }
            evalJs(sb.toString())
        }
    }

    @SuppressLint("WrongConstant")
    fun tryPlayClickEffect() {
        try {
            audioManager?.playSoundEffect(SoundEffectConstants.CLICK)
        } catch (_: Exception) {
        }
    }

    fun runInUiThread(action: Runnable) {
        if (Thread.currentThread() == mUiThread) {
            action.run()
        } else {
            mHandler.post(action)
        }
    }

    fun setJsLifeCycleListener(jsLifeCycleListener: JsLifeCycleListener?) {
        this.jsLifeCycleListener = jsLifeCycleListener
    }

    fun syncWithJs(action: Runnable) {
        if (destroyedForReuse) return
        if (!isJsReady) {
            actionsForWebView.add(action)
        } else {
            try {
                runInUiThread(action)
            } catch (ex: Exception) {
                Timber.e(ex, "WebView runInUiThread error")
            }
        }
    }

    /**
     * Unblocks [syncWithJs] when native DOM probes confirm JS helpers exist but
     * [domContentLoaded] was missed (detach/reattach, fast reload, race with loadData).
     */
    fun acknowledgeJsBridgeFromNativeProbe() {
        if (destroyedForReuse || isJsReady) return
        isJsReady = true
        val queued = ArrayList(actionsForWebView)
        actionsForWebView.clear()
        for (action in queued) {
            try {
                runInUiThread(action)
            } catch (exception: Exception) {
                Timber.e(exception, "WebView acknowledgeJsBridge runInUiThread error")
            }
        }
    }

    @JavascriptInterface
    fun onActionModeComplete() {
        runInUiThread {
            currentActionMode?.finish()
        }
    }

    private var actionModeListener: OnStartActionModeListener? = null
    private var currentActionMode: ActionMode? = null

    fun isActionModeActive(): Boolean = currentActionMode != null

    fun setActionModeListener(actionModeListener: OnStartActionModeListener?) {
        this.actionModeListener = actionModeListener
    }

    fun setDialogsHelper(dialogsHelper: DialogsHelper?) {
        this.dialogsHelper = dialogsHelper
    }

    override fun startActionMode(callback: ActionMode.Callback): ActionMode? {
        return myActionMode(callback, 0)
    }

    override fun startActionMode(callback: ActionMode.Callback, type: Int): ActionMode? {
        return myActionMode(callback, type)
    }

    private fun myActionMode(callback: ActionMode.Callback, type: Int): ActionMode? {
        val parent: ViewParent? = parent
        if (parent == null) {
            return null
        }

        val customCallback = getActionModeCallback(callback)
        val actionMode: ActionMode? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            super.startActionMode(customCallback, type)
        } else {
            super.startActionMode(customCallback)
        }

        currentActionMode = actionMode
        // Кастомизация меню перенесена внутрь customCallback.onCreateActionMode/onPrepareActionMode,
        // чтобы пункты («Цитировать» и др.) применялись синхронно до первой отрисовки floating popup
        // и переживали инвалидации (text-processing intents подгружаются асинхронно).
        return actionMode
    }

    private fun applyCustomMenu(mode: ActionMode, callback: ActionMode.Callback) {
        actionModeListener?.onCreate(mode, callback)
    }

    private fun getActionModeCallback(callback: ActionMode.Callback): ActionMode.Callback {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return object : ActionMode.Callback2() {
                override fun onGetContentRect(mode: ActionMode, view: View, outRect: Rect) {
                    if (callback is ActionMode.Callback2) {
                        callback.onGetContentRect(mode, view, outRect)
                    } else {
                        super.onGetContentRect(mode, view, outRect)
                    }
                }

                override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
                    val result = callback.onCreateActionMode(mode, menu)
                    applyCustomMenu(mode, this)
                    return result
                }

                override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean {
                    callback.onPrepareActionMode(mode, menu)
                    // Перестраиваем меню и при invalidate, чтобы наши пункты не вытеснялись
                    // системой после асинхронной подгрузки text-processing intents.
                    applyCustomMenu(mode, this)
                    return true
                }

                override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
                    if (actionModeListener?.onClick(mode, item) == true) {
                        return true
                    }
                    return callback.onActionItemClicked(mode, item)
                }

                override fun onDestroyActionMode(mode: ActionMode) {
                    currentActionMode = null
                    callback.onDestroyActionMode(mode)
                }
            }
        } else {
            return object : ActionMode.Callback {
                override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
                    val result = callback.onCreateActionMode(mode, menu)
                    applyCustomMenu(mode, this)
                    return result
                }

                override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean {
                    callback.onPrepareActionMode(mode, menu)
                    applyCustomMenu(mode, this)
                    return true
                }

                override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
                    if (actionModeListener?.onClick(mode, item) == true) {
                        return true
                    }
                    return callback.onActionItemClicked(mode, item)
                }

                override fun onDestroyActionMode(mode: ActionMode) {
                    currentActionMode = null
                    callback.onDestroyActionMode(mode)
                }
            }
        }
    }

    override fun onCreateContextMenu(menu: ContextMenu) {
        super.onCreateContextMenu(menu)
        requestFocusNodeHref(
            Handler { msg ->
                val result = hitTestResult
                if (dialogsHelper != null) {
                    dialogsHelper?.handleContextMenu(
                        context,
                        result.type,
                        result.extra ?: "",
                        msg.data.getString("url")
                    )
                }
                true
            }.obtainMessage()
        )
    }

    fun isDestroyedForReuse(): Boolean = destroyedForReuse

    /** Allows loading trusted HTML again after [endWork] on the same instance (normally a new WebView is created). */
    fun prepareForContentLoad() {
        destroyedForReuse = false
    }

    fun endWork() {
        destroyedForReuse = true
        onPause()
        clearQueuedJs()
        isJsReady = false
        setOnScrollListener(null)
        setOnDirectionListener(null)
        setDialogsHelper(null)
        setActionModeListener(null)
        setWebChromeClient(null)
        setWebViewClient(WebViewClient())
        super.loadUrl("about:blank")
        clearHistory()
        clearSslPreferences()
        clearDisappearingChildren()
        clearFocus()
        clearFormData()
        clearMatches()
    }

    companion object {
        private val LOG_TAG = ExtendedWebView::class.java.simpleName
        const val DIRECTION_NONE = 0
        const val DIRECTION_UP = 1
        const val DIRECTION_DOWN = 2

        /** Batch window for queued JS commands (ms). */
        private const val JS_BATCH_DELAY_MS = 16L
        /**
         * Upper bound on the buffered JS script length before a forced early flush. Protects
         * against an unbounded producer building a giant script string between flushes.
         */
        private const val MAX_PENDING_JS_LENGTH = 256 * 1024

        /**
         * Число WebView в состоянии "resumed/active" во всём процессе.
         *
         * [android.webkit.WebView.pauseTimers]/[android.webkit.WebView.resumeTimers]
         * — ГЛОБАЛЬНЫЕ для процесса: пауза одного WebView замораживает JS/таймеры во
         * всех WebView приложения. Чтобы фоновый WebView (тема в бэкстеке) не замораживал
         * видимый (поиск/другая вкладка), глобальную паузу ставим только когда активных
         * WebView не осталось, а возобновляем — при появлении первого активного.
         */
        private val activeTimerWebViews = java.util.concurrent.atomic.AtomicInteger(0)
    }
}

private fun String.jsStringLiteral(): String = buildString(length + 2) {
    append('"')
    this@jsStringLiteral.forEach { char ->
        when (char) {
            '\\' -> append("\\\\")
            '"' -> append("\\\"")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            else -> append(char)
        }
    }
    append('"')
}
