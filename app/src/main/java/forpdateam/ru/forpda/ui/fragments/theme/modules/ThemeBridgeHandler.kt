package forpdateam.ru.forpda.ui.fragments.theme.modules

import android.annotation.SuppressLint
import forpdateam.ru.forpda.common.extractPostBodyHtml
import forpdateam.ru.forpda.entity.remote.IBaseForumPost
import forpdateam.ru.forpda.presentation.theme.ThemeJsInterface
import forpdateam.ru.forpda.ui.views.ExtendedWebView

/**
 * Single owner of Theme WebView JS bridge registration/removal.
 *
 * The controller configures the trusted security profile first; this handler then attaches
 * both the base lifecycle bridge and theme-specific interfaces, and removes them together.
 */
class ThemeBridgeHandler(
    private val webView: ExtendedWebView,
    private val jsInterface: ThemeJsInterface
) {

    companion object {
        const val JS_INTERFACE = "IThemePresenter"
    }

    private val jsApi = ThemeJsApi(webView)
    private var isRegistered = false

    @SuppressLint("JavascriptInterface")
    fun init() {
        if (isRegistered) return
        webView.enableBaseBridge()
        webView.addJavascriptInterface(jsInterface, JS_INTERFACE)
        isRegistered = true
    }

    fun cleanup() {
        if (!isRegistered) {
            jsInterface.cancel()
            return
        }
        webView.removeBaseBridge()
        webView.removeJavascriptInterface(JS_INTERFACE)
        jsInterface.cancel()
        isRegistered = false
    }

    fun copySelectedText() = jsApi.copySelectedText()

    fun selectionToQuote() = jsApi.selectionToQuote()

    fun selectAllPostText() = jsApi.selectAllPostText()

    fun shareSelectedText() = jsApi.shareSelectedText()

    fun changeStyleType(type: String) = jsApi.changeStyleType(type)

    fun updateShowAvatarState(isShow: Boolean) = jsApi.updateShowAvatarState(isShow)

    fun updateTypeAvatarState(isCircle: Boolean) = jsApi.updateTypeAvatarState(isCircle)

    fun deletePost(postId: Int) = jsApi.deletePost(postId)

    fun onProgressChanged() = jsApi.onProgressChanged()

    fun extractPostBodyHtml(postId: Int, callback: (String?) -> Unit) {
        webView.extractPostBodyHtml(postId, callback)
    }
}
