package forpdateam.ru.forpda.ui.fragments.theme

import android.annotation.SuppressLint
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.ActionMode
import android.view.MenuItem
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import forpdateam.ru.forpda.BuildConfig
import forpdateam.ru.forpda.App
import forpdateam.ru.forpda.R
import forpdateam.ru.forpda.common.webview.CustomWebChromeClient
import forpdateam.ru.forpda.common.webview.CustomWebViewClient
import forpdateam.ru.forpda.common.webview.DialogsHelper
import forpdateam.ru.forpda.common.extractPostBodyHtml
import forpdateam.ru.forpda.entity.remote.IBaseForumPost
import forpdateam.ru.forpda.entity.remote.theme.ThemePage
import forpdateam.ru.forpda.presentation.theme.ThemeJsInterface
import forpdateam.ru.forpda.presentation.theme.ThemeViewModel
import forpdateam.ru.forpda.ui.fragments.TabTopScroller
import forpdateam.ru.forpda.ui.fragments.WebViewTopScroller
import forpdateam.ru.forpda.ui.views.ExtendedWebView
import org.json.JSONObject
import java.util.*
import java.util.regex.Pattern

/**
 * Created by radiationx on 20.10.16.
 */

class ThemeFragmentWeb : ThemeFragment(), ExtendedWebView.JsLifeCycleListener, TabTopScroller {

    override fun editPost(post: IBaseForumPost) {
        if (!::webView.isInitialized) return
        webView.extractPostBodyHtml(post.id) { domHtml ->
            presenter.openEditPostForm(post.id, domHtml)
        }
    }
    private lateinit var webView: ExtendedWebView
    private lateinit var webViewClient: WebViewClient
    private lateinit var chromeClient: WebChromeClient
    private lateinit var jsInterface: ThemeJsInterface
    private lateinit var topScroller: WebViewTopScroller

    /**
     * Та же тема уже открыта в другой вкладке — грузим новый URL (findpost и т.д.) без дубликата вкладки.
     * Вызывается из [forpdateam.ru.forpda.ui.navigation.TabNavigator] до [updateFragmentsState].
     */
    fun loadThemeUrlFromNavigator(url: String) {
        presenter.loadUrl(url)
    }

    /** Для [TabNavigator]: после навигации внутри темы в [ARG_TAB] остаётся старый showtopic, topic id берём из модели. */
    fun getOpenTopicIdForReuse(): Int? {
        if (!presenter.isPageLoaded()) return null
        val id = presenter.getId()
        return if (id > 0) id else null
    }

    /**
     * Вызывается из [forpdateam.ru.forpda.ui.navigation.TabNavigator] после каждой смены текущей вкладки
     * (список «Открытые вкладки», [com.github.terrakok.cicerone.Forward] на уже открытый экран и т.д.).
     */
    fun onTabStackBecameCurrent() {
        scheduleJumpToUnreadAfterTabSwitch()
    }

    /**
     * Пока [presenter] ещё без currentPage, [presenter.loadNewPosts] не срабатывает — откладываем до [updateView].
     */
    private var pendingUnreadAfterFirstPage = false

    override fun scrollToAnchor(anchor: String?) {
        if (anchor.isNullOrBlank()) return
        if (!::webView.isInitialized) return
        webView.evalJs("scrollToElement(" + JSONObject.quote(anchor) + ")")
        if (::topScroller.isInitialized) topScroller.resetState()
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        jsInterface = ThemeJsInterface(presenter)

        webViewClient = ThemeWebViewClient()
        chromeClient = ThemeChromeClient()

        webView = ExtendedWebView(context)
        messagePanel.setHeightChangeListener { newHeight ->
            webView.paddingBottom = newHeight
        }
        // ThemeFragment.onViewCreated может вызвать show/hide панели до setHeightChangeListener — синхронизируем отступ.
        view.post {
            if (!::webView.isInitialized) return@post
            val bottom = if (messagePanel.visibility != View.VISIBLE) {
                0
            } else {
                messagePanel.height + App.px16
            }
            webView.paddingBottom = bottom
        }
        webView.setDialogsHelper(DialogsHelper(
                webView.context,
                App.get().Di().linkHandler,
                App.get().Di().systemLinkHandler,
                App.get().Di().router
        ))
        attachWebView(webView)
        webView.setJsLifeCycleListener(this)
        refreshLayout.addView(webView)
        refreshLayoutLongTrigger(refreshLayout)
        webView.addJavascriptInterface(this, "IThemeView")
        webView.addJavascriptInterface(jsInterface, JS_INTERFACE)
        webView.webViewClient = webViewClient
        webView.webChromeClient = chromeClient
        registerForContextMenu(webView)
        fab.setOnClickListener {
            if (webView.direction == ExtendedWebView.DIRECTION_DOWN) {
                webView.pageDown(true)
            } else if (webView.direction == ExtendedWebView.DIRECTION_UP) {
                webView.pageUp(true)
            }
        }
        webView.setOnDirectionListener { direction ->
            if (direction == ExtendedWebView.DIRECTION_DOWN) {
                fab.setImageDrawable(App.getVecDrawable(fab.context, R.drawable.ic_arrow_down))
            } else if (direction == ExtendedWebView.DIRECTION_UP) {
                fab.setImageDrawable(App.getVecDrawable(fab.context, R.drawable.ic_arrow_up))
            }
        }
        topScroller = WebViewTopScroller(webView, appBarLayout)

        setFontSize(mainPreferencesHolder.getWebViewFontSize())

        //Кастомизация менюхи при выделении текста
        webView.setActionModeListener(object : ExtendedWebView.OnStartActionModeListener {
            override fun onCreate(actionMode: ActionMode, callback: ActionMode.Callback) {
                val menu = actionMode.menu
                val items = ArrayList<MenuItem>()
                for (i in 0 until menu.size()) {
                    items.add(menu.getItem(i))
                }
                menu.clear()

                menu.add(0, R.id.action_mode_item_copy, 0, R.string.copy)
                        .setIcon(App.getVecDrawable(context, R.drawable.ic_toolbar_content_copy))
                        .setShowAsActionFlags(MenuItem.SHOW_AS_ACTION_ALWAYS)

                if (!authHolder.get().isAuth() || presenter.canQuote()) {
                    menu.add(0, R.id.action_mode_item_quote, 0, R.string.quote)
                            .setIcon(App.getVecDrawable(context, R.drawable.ic_toolbar_quote_post))
                            .setShowAsActionFlags(MenuItem.SHOW_AS_ACTION_ALWAYS)
                }

                menu.add(0, R.id.action_mode_item_select_all, 0, R.string.all_text)
                        .setIcon(App.getVecDrawable(context, R.drawable.ic_toolbar_select_all))
                        .setShowAsActionFlags(MenuItem.SHOW_AS_ACTION_ALWAYS)

                menu.add(0, R.id.action_mode_item_share, 0, R.string.share)
                        .setIcon(App.getVecDrawable(context, R.drawable.ic_toolbar_share))
                        .setShowAsActionFlags(MenuItem.SHOW_AS_ACTION_ALWAYS)

                for (item in items) {
                    Log.e("ExtendedWebView", "fillItem " + item.itemId + " : " + item.title + " : " + item.titleCondensed + " : " + item.intent + " : " + item.menuInfo)
                    if (item.intent != null) {
                        menu.add(item.groupId, item.itemId, item.order, item.title)
                                .setIntent(item.intent)
                                .setNumericShortcut(item.numericShortcut).alphabeticShortcut = item.alphabeticShortcut
                    }
                }
            }

            override fun onClick(actionMode: ActionMode, item: MenuItem): Boolean {
                Log.e("ExtendedWebView", "onClick " + item.itemId)
                var result = false
                when (item.itemId) {
                    R.id.action_mode_item_copy -> {
                        webView.evalJs("copySelectedText()")
                        result = true
                    }
                    R.id.action_mode_item_quote -> {
                        webView.evalJs("selectionToQuote()")
                        result = true
                    }
                    R.id.action_mode_item_select_all -> {
                        webView.evalJs("selectAllPostText()")
                        result = true
                    }
                    R.id.action_mode_item_share -> {
                        webView.evalJs("shareSelectedText()")
                        result = true
                    }
                }
                return result
            }
        })
        presenter.attachView(this)
        presenter.start()
    }

    /**
     * Вызывается из [forpdateam.ru.forpda.ui.navigation.TabNavigator] после снятия верхнего фрагмента
     * (другая тема по ссылке). Только такой путь даёт стабильное восстановление — как pull-to-refresh.
     */
    fun onRestoredAfterChildFragmentRemoved() {
        view?.post {
            if (::webView.isInitialized) {
                presenter.reload()
            }
        }
    }

    override fun toggleScrollTop() {
        if (!::topScroller.isInitialized) return
        topScroller.toggleScrollTop()
    }

    override fun findNext(next: Boolean) {
        if (!::webView.isInitialized) return
        webView.findNext(next)
    }

    override fun findText(text: String) {
        if (!::webView.isInitialized) return
        webView.findAllAsync(text)
    }

    override fun setStyleType(type: String) {
        if (!::webView.isInitialized) return
        webView.evalJs("changeStyleType(\"$type\")")
    }

    override fun updateView(page: ThemePage) {
        super.updateView(page)
        if (!::webView.isInitialized) return
        webView.loadDataWithBaseURL("https://4pda.to/forum/", page.html ?: "", "text/html", "utf-8", null)
        webView.updatePaddingBottom()
        if (pendingUnreadAfterFirstPage) {
            pendingUnreadAfterFirstPage = false
            val tu = presenter.themeUrl.lowercase()
            if (!tu.contains("view=getnewpost")) {
                view?.post {
                    if (isAdded) presenter.loadNewPosts(fromTabSwitch = true)
                }
            }
        }
    }

    override fun updateShowAvatarState(isShow: Boolean) {
        if (!::webView.isInitialized) return
        webView.evalJs("updateShowAvatarState($isShow)")
    }

    override fun updateTypeAvatarState(isCircle: Boolean) {
        if (!::webView.isInitialized) return
        webView.evalJs("updateTypeAvatarState($isCircle)")
    }

    override fun updateScrollButtonState(isEnabled: Boolean) {
        if (isEnabled) {
            fab.visibility = View.VISIBLE
        } else {
            fab.visibility = View.GONE
        }
    }

    override fun setFontSize(size: Int) {
        if (!::webView.isInitialized) return
        webView.setRelativeFontSize(size)
    }

    override fun updateHistoryLastHtml() {
        // Эта операция формирует ОЧЕНЬ большой HTML и может фризить UI на некоторых устройствах.
        // Оставляем только scrollY (его достаточно для возврата назад), а HTML не сохраняем.
        if (!::webView.isInitialized) return
        presenter.updateHistoryLastHtml("", webView.scrollY)
    }

    @JavascriptInterface
    fun callbackUpdateHistoryHtml(@Suppress("UNUSED_PARAMETER") value: String) {
        // no-op (см. updateHistoryLastHtml)
    }

    private fun scheduleJumpToUnreadAfterTabSwitch() {
        val runner = Runnable {
            if (!isAdded) return@Runnable
            if (presenter.isPageLoaded()) {
                pendingUnreadAfterFirstPage = false
                presenter.loadNewPosts(fromTabSwitch = true)
            } else {
                pendingUnreadAfterFirstPage = true
            }
        }
        val v = view
        if (v != null) v.post(runner) else Handler(Looper.getMainLooper()).post(runner)
    }

    override fun onDestroyView() {
        pendingUnreadAfterFirstPage = false
        unregisterForContextMenu(webView)
        webView.removeJavascriptInterface("IThemeView")
        webView.removeJavascriptInterface(JS_INTERFACE)
        webView.setJsLifeCycleListener(null)
        presenter.detachView()
        webView.endWork()
        super.onDestroyView()
    }

    override fun onDomContentComplete(actions: ArrayList<String>) {
        Log.d(LOG_TAG, "DOMContentLoaded")
        if (BuildConfig.DEBUG) {
            Log.d(
                    LOG_TAG,
                    "domContent trace=${presenter.getThemeLoadTraceId()} elem=${presenter.currentPage?.anchor} url=${presenter.currentPage?.url} loadAction=${presenter.loadAction}"
            )
        }
        actions.add("setLoadAction(" + presenter.loadAction + ");")
        //Log.e("WebConsole", "" + currentPage.getScrollY() + " : " + App.get().getDensity() + " : " + ((int) (currentPage.getScrollY() / App.get().getDensity())));
        actions.add("setLoadScrollY(" + (presenter.getPageScrollY() / webView.resources.displayMetrics.density).toInt() + ");")
    }

    override fun onPageComplete(actions: ArrayList<String>) {
        presenter.loadAction = ThemeViewModel.ActionState.NORMAL
        val themeDiag = BuildConfig.DEBUG
        if (themeDiag) {
            Log.d(
                    LOG_TAG,
                    "pageLoad trace=${presenter.getThemeLoadTraceId()} elem=${presenter.currentPage?.anchor} retriesMs=SCROLL_ANCHOR_RETRY_DELAYS_MS"
            )
        }
        actions.add("setLoadAction(" + ThemeViewModel.ActionState.NORMAL + ");")
        actions.add("window.__themeScrollAnchorDiag=" + themeDiag + ";")
        // Повторы после load (высота страницы меняется после картинок).
        actions.add("if (typeof PageInfo !== 'undefined' && PageInfo.elemToScroll && PageInfo.elemToScroll.length && typeof scrollToElementWithRetries === 'function') { scrollToElementWithRetries(PageInfo.elemToScroll); } else if (typeof PageInfo !== 'undefined' && PageInfo.elemToScroll && PageInfo.elemToScroll.length) { scrollToElement(PageInfo.elemToScroll); }")
    }

    override fun deletePostUi(post: IBaseForumPost) {
        webView.evalJs("deletePost(" + post.id + ");")
    }

    override fun openAnchorDialog(post: IBaseForumPost, anchorName: String) {
        dialogsHelper.openAnchorDialog(presenter, post, anchorName)
    }

    override fun openSpoilerLinkDialog(post: IBaseForumPost, spoilNumber: String) {
        dialogsHelper.openSpoilerLinkDialog(presenter, post, spoilNumber)
    }

    private inner class ThemeWebViewClient : CustomWebViewClient() {
        private val p = Pattern.compile("\\.(jpg|png|gif|bmp)")
        private val m = p.matcher("")

        override fun handleUri(uri: Uri): Boolean {
            presenter.handleNewUrl(uri)
            return true
        }

        override fun onLoadResource(view: WebView, url: String) {
            super.onLoadResource(view, url)
            if (presenter.loadAction === ThemeViewModel.ActionState.NORMAL) {
                if (!url.contains("forum/uploads") && !url.contains("android_asset") && !url.contains("style_images") && m.reset(url).find()) {
                    webView.evalJs("onProgressChanged()")
                }
            }
        }
    }

    private inner class ThemeChromeClient : CustomWebChromeClient() {
        override fun onProgressChanged(view: WebView, progress: Int) {
            if (presenter.loadAction === ThemeViewModel.ActionState.NORMAL) {
                webView.evalJs("onProgressChanged()")
            }
        }
    }

    companion object {
        private val LOG_TAG = ThemeFragmentWeb::class.java.simpleName
        /** Имя объекта в JS (шаблоны, theme.js); реализация — [ThemeWebCallbacks] через [ThemeJsInterface]. */
        const val JS_INTERFACE = "IThemePresenter"
    }

}
