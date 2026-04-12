package forpdateam.ru.forpda.ui.fragments.qms.chat

import android.webkit.JavascriptInterface
import forpdateam.ru.forpda.presentation.qms.chat.QmsChatWebCallbacks
import forpdateam.ru.forpda.ui.fragments.BaseJsInterface

class QmsChatJsInterface(
        private val callbacks: QmsChatWebCallbacks
) : BaseJsInterface() {

    @JavascriptInterface
    fun loadMoreMessages() = runInUiThread(Runnable { callbacks.loadMoreMessages() })

}