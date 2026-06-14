package forpdateam.ru.forpda.ui.fragments.qms.chat

import android.webkit.JavascriptInterface
import forpdateam.ru.forpda.presentation.qms.chat.QmsChatWebCallbacks
import forpdateam.ru.forpda.ui.fragments.BaseJsInterface

class QmsChatJsInterface(
    private val callbacks: QmsChatWebCallbacks
) : BaseJsInterface() {

    // QMS exposes only pagination to JS; message sending and other mutations stay in native UI.
    @JavascriptInterface
    fun loadMoreMessages() = runInUiThread(Runnable { callbacks.loadMoreMessages() })

    @JavascriptInterface
    fun openLink(url: String) = runInUiThread(Runnable { callbacks.openLink(url) })
}