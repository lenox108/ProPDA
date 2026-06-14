package forpdateam.ru.forpda.common.webview

import android.net.Uri
import android.os.Message
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebView
import forpdateam.ru.forpda.BuildConfig
import forpdateam.ru.forpda.common.ArticleLinkResolver
import timber.log.Timber

/**
 * Created by radiationx on 12.09.17.
 */
open class CustomWebChromeClient : WebChromeClient() {

    companion object {
        private const val CONSOLE_TAG = "WebConsole"
    }

    override fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean {
        var message = "\"${consoleMessage.message()}\""
        var source = consoleMessage.sourceId()
        if (source != null) {
            val cut = source.lastIndexOf('/')
            if (cut != -1) source = source.substring(cut + 1)
            message += ", [$source]"
        }
        message += ", (${consoleMessage.lineNumber()})"

        if (BuildConfig.DEBUG) {
            when (consoleMessage.messageLevel()) {
                ConsoleMessage.MessageLevel.DEBUG -> Timber.d("$CONSOLE_TAG: $message")
                ConsoleMessage.MessageLevel.ERROR -> Timber.e("$CONSOLE_TAG: $message")
                ConsoleMessage.MessageLevel.WARNING -> Timber.w("$CONSOLE_TAG: $message")
                ConsoleMessage.MessageLevel.LOG, ConsoleMessage.MessageLevel.TIP -> Timber.i("$CONSOLE_TAG: $message")
                else -> Timber.d("$CONSOLE_TAG: $message")
            }
        }
        return true
    }

    override fun onCreateWindow(
        view: WebView,
        isDialog: Boolean,
        isUserGesture: Boolean,
        resultMsg: Message
    ): Boolean {
        val href = view.hitTestResult.extra?.trim().orEmpty()
        val resolved = ArticleLinkResolver.resolveForNavigation(href)
        if (!resolved.isNullOrBlank()) {
            val client = view.webViewClient
            if (client is CustomWebViewClient && client.handleUri(view, Uri.parse(resolved))) {
                return false
            }
        }
        return false
    }
}
