package forpdateam.ru.forpda.ui.fragments.theme.modules

import android.content.Context
import forpdateam.ru.forpda.R
import forpdateam.ru.forpda.common.getColorFromAttr
import forpdateam.ru.forpda.ui.views.ExtendedWebView

/**
 * Handles style/appearance-related WebView operations
 */
class ThemeStyleHandler(
    private val webView: ExtendedWebView,
    private val context: Context
) : ThemeUiModule {

    override fun init() {
        webView.setBackgroundColor(context.getColorFromAttr(R.attr.background_for_lists))
    }

    fun setPaddingBottom(padding: Int) {
        webView.setPaddingBottom(padding)
    }

    fun setPadding(left: Int, top: Int, right: Int, bottom: Int) {
        webView.setPadding(left, top, right, bottom)
    }

    fun setRelativeFontSize(size: Int) {
        webView.setRelativeFontSize(size)
    }

    fun updatePaddingBottom() {
        webView.updatePaddingBottom()
    }

    override fun dispose() {}
}
