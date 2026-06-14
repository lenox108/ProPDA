package forpdateam.ru.forpda.ui.fragments.theme.modules

import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import forpdateam.ru.forpda.R
import forpdateam.ru.forpda.common.ClipboardHelper
import forpdateam.ru.forpda.common.getColorFromAttr
import forpdateam.ru.forpda.common.webview.DialogsHelper
import forpdateam.ru.forpda.presentation.ILinkHandler
import forpdateam.ru.forpda.presentation.ISystemLinkHandler
import forpdateam.ru.forpda.presentation.TabRouter
import forpdateam.ru.forpda.ui.views.ExtendedWebView

class ThemeWebViewHost(
        private val config: Config
) : ThemeUiModule {

    private var attachedToRefreshLayout = false

    override fun init() {
        val webView = config.webView
        webView.setBackgroundColor(webView.context.getColorFromAttr(R.attr.background_for_lists))
        webView.overScrollMode = View.OVER_SCROLL_NEVER
        webView.isHorizontalScrollBarEnabled = false
        webView.isVerticalScrollBarEnabled = false
        webView.setDialogsHelper(
                DialogsHelper(
                        webView.context,
                        config.linkHandler,
                        config.systemLinkHandler,
                        config.router,
                        config.clipboardHelper
                )
        )
        webView.setJsLifeCycleListener(config.jsLifeCycleListener)
        webView.setPadding(0, 0, 0, 0)
        ViewCompat.setElevation(webView, 0f)
        ViewCompat.setTranslationZ(webView, 0f)
        ensureWebViewAttached()
    }

    override fun dispose() {
        attachedToRefreshLayout = false
        config.webView.setJsLifeCycleListener(null)
        config.webView.setDialogsHelper(null)
        (config.webView.parent as? ViewGroup)?.removeView(config.webView)
    }

    fun ensureWebViewAttached(): Boolean {
        val webView = config.webView
        val parent = webView.parent
        if (parent === config.refreshLayout) {
            webView.visibility = View.VISIBLE
            config.refreshLayout.visibility = View.VISIBLE
            val changed = !attachedToRefreshLayout
            attachedToRefreshLayout = true
            return changed
        }
        if (parent == null && config.refreshLayout.indexOfChild(webView) >= 0) {
            webView.visibility = View.VISIBLE
            config.refreshLayout.visibility = View.VISIBLE
            attachedToRefreshLayout = true
            return false
        }
        if (parent is ViewGroup) {
            parent.removeView(webView)
        }
        var attached = false
        if (webView.parent == null) {
            config.refreshLayout.addView(
                    webView,
                    ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                    )
            )
            attached = true
        }
        webView.visibility = View.VISIBLE
        config.refreshLayout.visibility = View.VISIBLE
        attachedToRefreshLayout = true
        return attached || parent != null
    }

    data class Config(
            val webView: ExtendedWebView,
            val refreshLayout: ViewGroup,
            val linkHandler: ILinkHandler,
            val systemLinkHandler: ISystemLinkHandler,
            val router: TabRouter,
            val clipboardHelper: ClipboardHelper,
            val jsLifeCycleListener: ExtendedWebView.JsLifeCycleListener
    )
}
