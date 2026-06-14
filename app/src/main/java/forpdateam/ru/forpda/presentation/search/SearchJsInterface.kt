package forpdateam.ru.forpda.presentation.search

import android.webkit.JavascriptInterface
import forpdateam.ru.forpda.presentation.theme.ThemeWebCallbacks
import forpdateam.ru.forpda.ui.fragments.BaseJsInterface

/**
 * Restricted JS interface for search results.
 * Only exposes safe, read-only / navigation methods from [ThemeWebCallbacks].
 * Destructive actions (deletePost, editPost, votePost, submitPoll, etc.) are intentionally omitted
 * to mitigate XSS risk from user-generated content in search results.
 */
class SearchJsInterface(
    private val callbacks: ThemeWebCallbacks
) : BaseJsInterface() {

    @JavascriptInterface
    fun showUserMenu(postId: String) = runInUiThread(Runnable {
        postId.toIntOrNull()?.let { callbacks.onUserMenuClick(it) }
    })

    @JavascriptInterface
    fun showReputationMenu(postId: String) = runInUiThread(Runnable {
        postId.toIntOrNull()?.let { callbacks.onReputationMenuClick(it) }
    })

    @JavascriptInterface
    fun showPostMenu(postId: String) = runInUiThread(Runnable {
        postId.toIntOrNull()?.let { callbacks.onPostMenuClick(it) }
    })

    @JavascriptInterface
    fun onPostContentToggle(postId: String, expanded: String) = runInUiThread(Runnable {
        postId.toIntOrNull()?.let { callbacks.onPostContentToggle(it, expanded.toBoolean()) }
    })

    @JavascriptInterface
    fun toast(text: String) = runInUiThread(Runnable { callbacks.toast(text) })

    @JavascriptInterface
    fun log(text: String) = runInUiThread(Runnable { callbacks.log(text) })

    @JavascriptInterface
    fun copySelectedText(text: String) = runInUiThread(Runnable { callbacks.copyText(text) })

    @JavascriptInterface
    fun shareSelectedText(text: String) = runInUiThread(Runnable { callbacks.shareText(text) })
}
