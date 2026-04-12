package forpdateam.ru.forpda.presentation.theme

import android.webkit.JavascriptInterface
import forpdateam.ru.forpda.ui.fragments.BaseJsInterface

/**
 * Created by radiationx on 17.03.18.
 */
class ThemeJsInterface(
        private val callbacks: ThemeWebCallbacks
) : BaseJsInterface() {

    @JavascriptInterface
    fun firstPage() = runInUiThread(Runnable { callbacks.onFirstPageClick() })

    @JavascriptInterface
    fun prevPage() = runInUiThread(Runnable { callbacks.onPrevPageClick() })

    @JavascriptInterface
    fun nextPage() = runInUiThread(Runnable { callbacks.onNextPageClick() })

    @JavascriptInterface
    fun lastPage() = runInUiThread(Runnable { callbacks.onLastPageClick() })

    @JavascriptInterface
    fun selectPage() = runInUiThread(Runnable { callbacks.onSelectPageClick() })

    @JavascriptInterface
    fun showUserMenu(postId: String) = runInUiThread(Runnable { callbacks.onUserMenuClick(postId.toInt()) })

    @JavascriptInterface
    fun showReputationMenu(postId: String) = runInUiThread(Runnable { callbacks.onReputationMenuClick(postId.toInt()) })

    @JavascriptInterface
    fun showPostMenu(postId: String) = runInUiThread(Runnable { callbacks.onPostMenuClick(postId.toInt()) })

    @JavascriptInterface
    fun reportPost(postId: String) = runInUiThread(Runnable { callbacks.onReportPostClick(postId.toInt()) })

    @JavascriptInterface
    fun reply(postId: String) = runInUiThread(Runnable { callbacks.onReplyPostClick(postId.toInt()) })

    @JavascriptInterface
    fun quotePost(text: String, postId: String) = runInUiThread(Runnable { callbacks.onQuotePostClick(postId.toInt(), text) })

    @JavascriptInterface
    fun quoteFullPost(postId: String) = runInUiThread(Runnable { callbacks.onQuoteFullPostClick(postId.toInt()) })

    @JavascriptInterface
    fun deletePost(postId: String) = runInUiThread(Runnable { callbacks.onDeletePostClick(postId.toInt()) })

    @JavascriptInterface
    fun editPost(postId: String) = runInUiThread(Runnable { callbacks.onEditPostClick(postId.toInt()) })

    @JavascriptInterface
    fun votePost(postId: String, type: Boolean) = runInUiThread(Runnable { callbacks.onVotePostClick(postId.toInt(), type) })

    @JavascriptInterface
    fun setHistoryBody(index: String, body: String) = runInUiThread(Runnable { callbacks.setHistoryBody(index.toInt(), body) })

    @JavascriptInterface
    fun copySelectedText(text: String) = runInUiThread(Runnable { callbacks.copyText(text) })

    @JavascriptInterface
    fun toast(text: String) = runInUiThread(Runnable { callbacks.toast(text) })

    @JavascriptInterface
    fun log(text: String) = runInUiThread(Runnable { callbacks.log(text) })

    @JavascriptInterface
    fun showPollResults() = runInUiThread(Runnable { callbacks.onPollResultsClick() })

    @JavascriptInterface
    fun showPoll() = runInUiThread(Runnable { callbacks.onPollClick() })

    @JavascriptInterface
    fun copySpoilerLink(postId: String, spoilNumber: String) = runInUiThread(Runnable { callbacks.onSpoilerCopyLinkClick(postId.toInt(), spoilNumber) })

    @JavascriptInterface
    fun setPollOpen(bValue: String) = runInUiThread(Runnable { callbacks.onPollHeaderClick(bValue.toBoolean()) })

    @JavascriptInterface
    fun setHatOpen(bValue: String) = runInUiThread(Runnable { callbacks.onHatHeaderClick(bValue.toBoolean()) })

    @JavascriptInterface
    fun shareSelectedText(text: String) = runInUiThread(Runnable { callbacks.shareText(text) })

    @JavascriptInterface
    fun anchorDialog(postId: String, name: String) = runInUiThread(Runnable { callbacks.onAnchorClick(postId.toInt(), name) })

}