package forpdateam.ru.forpda.ui.fragments.theme.modules

import com.google.android.material.appbar.AppBarLayout
import forpdateam.ru.forpda.ui.fragments.TabTopScroller
import forpdateam.ru.forpda.ui.views.ExtendedWebView
import org.json.JSONObject

/**
 * Handles scroll-related WebView operations
 */
class ThemeScrollHandler(
    private val webView: ExtendedWebView,
    private val appBarLayout: AppBarLayout,
    @Suppress("UNUSED_PARAMETER") contentView: android.view.View
) : TabTopScroller, ThemeUiModule {
    private var savedTopScrollY = 0
    private var scrolledToTop = false
    private var externalScrollListener: ExtendedWebView.OnScrollListener? = null
    private var headerScrollListener: ExtendedWebView.OnScrollListener? = null

    fun setExternalScrollListener(listener: ExtendedWebView.OnScrollListener?) {
        externalScrollListener = listener
    }

    fun setHeaderScrollListener(listener: ExtendedWebView.OnScrollListener?) {
        headerScrollListener = listener
    }

    override fun init() {
        webView.setOnScrollListener(object : ExtendedWebView.OnScrollListener {
            override fun onScrollChange(scrollX: Int, scrollY: Int, oldScrollX: Int, oldScrollY: Int) {
                if (scrolledToTop && scrollY > 0) {
                    resetState()
                }
                headerScrollListener?.onScrollChange(scrollX, scrollY, oldScrollX, oldScrollY)
                externalScrollListener?.onScrollChange(scrollX, scrollY, oldScrollX, oldScrollY)
            }
        })
    }

    fun scrollToAnchor(anchor: String?) {
        if (anchor.isNullOrBlank()) return
        webView.evaluateJavascript("scrollToElement(" + JSONObject.quote(anchor) + ")", null)
        resetState()
    }

    override fun toggleScrollTop() {
        if (savedTopScrollY > 0) {
            val scrollY = savedTopScrollY
            savedTopScrollY = 0
            scrolledToTop = false
            webView.scrollTo(webView.scrollX, scrollY)
        } else {
            appBarLayout.setExpanded(true, true)
            savedTopScrollY = webView.scrollY
            scrolledToTop = true
            webView.scrollTo(webView.scrollX, 0)
        }
    }

    fun pageDown() {
        webView.pageDown(true)
    }

    fun pageUp() {
        webView.pageUp(true)
    }

    fun getScrollY(): Int {
        return webView.scrollY
    }

    fun resetState() {
        savedTopScrollY = 0
        scrolledToTop = false
    }

    fun cleanup() {
        webView.setOnScrollListener(null)
        headerScrollListener = null
        externalScrollListener = null
    }

    override fun dispose() = cleanup()
}
