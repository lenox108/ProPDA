package forpdateam.ru.forpda.ui.fragments.theme.modules

import android.webkit.WebView

/**
 * Handles pagination/navigation-related WebView operations
 */
class ThemePaginationHandler(
    private val webView: WebView
) {

    fun loadUrl(url: String) {
        webView.loadUrl(url)
    }

    fun loadDataWithBaseURL(baseUrl: String, data: String, mimeType: String, encoding: String, historyUrl: String?) {
        webView.loadDataWithBaseURL(baseUrl, data, mimeType, encoding, historyUrl)
    }

    fun findNext(next: Boolean) {
        webView.findNext(next)
    }

    fun findAllAsync(text: String) {
        webView.findAllAsync(text)
    }

    fun reload() {
        webView.reload()
    }
}
