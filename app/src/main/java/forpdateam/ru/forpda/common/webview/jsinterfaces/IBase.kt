package forpdateam.ru.forpda.common.webview.jsinterfaces

import android.webkit.JavascriptInterface

/**
 * Created by radiationx on 28.05.17.
 */
interface IBase {
    companion object {
        const val JS_BASE_INTERFACE = "IBase"
    }

    @JavascriptInterface
    fun playClickEffect()

    @JavascriptInterface
    fun domContentLoaded()

    @JavascriptInterface
    fun onPageLoaded()
}
