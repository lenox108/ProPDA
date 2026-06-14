package forpdateam.ru.forpda.presentation.theme

import android.webkit.JavascriptInterface
import forpdateam.ru.forpda.BuildConfig
import forpdateam.ru.forpda.diagnostic.StateRaceTrace
import forpdateam.ru.forpda.common.webview.UrlDecision
import forpdateam.ru.forpda.common.ArticleLinkResolver
import forpdateam.ru.forpda.common.webview.UrlPolicy
import forpdateam.ru.forpda.ui.fragments.BaseJsInterface
import timber.log.Timber

/**
 * Created by radiationx on 17.03.18.
 */
class ThemeJsInterface(
        private val callbacks: ThemeWebCallbacks,
        private val renderGuard: ThemeRenderGuard
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
    fun selectPageInput() = runInUiThread(Runnable { callbacks.onSelectPageInputClick() })

    @JavascriptInterface
    fun searchPage(st: String) = runInUiThread(Runnable {
        st.toIntOrNull()?.let { callbacks.onSearchPageClick(it) }
    })

    @JavascriptInterface
    fun infiniteScroll(direction: String) = runInUiThread(Runnable { callbacks.onInfiniteScrollRequest(direction) })

    @JavascriptInterface
    fun infiniteRetry(direction: String) = runInUiThread(Runnable { callbacks.onInfiniteRetry(direction) })

    @JavascriptInterface
    fun visiblePageChanged(pageNumber: String) = runInUiThread(Runnable {
        pageNumber.toIntOrNull()?.let { callbacks.onVisiblePageChanged(it) }
    })

    @JavascriptInterface
    fun showUserMenu(postId: String) = runInUiThread(Runnable { postId.toIntOrNull()?.let { callbacks.onUserMenuClick(it) } })

    @JavascriptInterface
    fun showReputationMenu(postId: String) = runInUiThread(Runnable { postId.toIntOrNull()?.let { callbacks.onReputationMenuClick(it) } })

    /**
     * Quick +/- reputation from WebView post actions.
     * Opens native "change reputation" dialog (with optional message).
     */
    @JavascriptInterface
    fun showChangeReputation(postId: String, type: Boolean, renderToken: String?) =
        runProtected("showChangeReputation", renderToken) {
            postId.toIntOrNull()?.let { callbacks.onChangeReputationClick(it, type) }
        }

    @JavascriptInterface
    fun showPostMenu(postId: String) = runInUiThread(Runnable { postId.toIntOrNull()?.let { callbacks.onPostMenuClick(it) } })

    @JavascriptInterface
    fun reportPost(postId: String, renderToken: String?) =
        runProtected("reportPost", renderToken) {
            postId.toIntOrNull()?.let { callbacks.onReportPostClick(it) }
        }

    @JavascriptInterface
    fun reply(postId: String, renderToken: String?) =
        runProtected("reply", renderToken) {
            postId.toIntOrNull()?.let { callbacks.onReplyPostClick(it) }
        }

    @JavascriptInterface
    fun quotePost(text: String, postId: String, renderToken: String?) =
        runProtected("quotePost", renderToken) {
            postId.toIntOrNull()?.let { callbacks.onQuotePostClick(it, text) }
        }

    @JavascriptInterface
    fun quotePostWithDate(text: String, postId: String, displayedDate: String, renderToken: String?) =
            runProtected("quotePostWithDate", renderToken) {
                postId.toIntOrNull()?.let { callbacks.onQuotePostClick(it, text, displayedDate) }
            }

    @JavascriptInterface
    fun quoteFullPost(postId: String, renderToken: String?) =
        runProtected("quoteFullPost", renderToken) {
            postId.toIntOrNull()?.let { callbacks.onQuoteFullPostClick(it) }
        }

    @JavascriptInterface
    fun quoteFullPostWithDate(postId: String, displayedDate: String, renderToken: String?) =
            runProtected("quoteFullPostWithDate", renderToken) {
                postId.toIntOrNull()?.let { callbacks.onQuoteFullPostClick(it, displayedDate) }
            }

    @JavascriptInterface
    fun deletePost(postId: String, renderToken: String?) =
        runProtected("deletePost", renderToken) {
            postId.toIntOrNull()?.let { callbacks.onDeletePostClick(it) }
        }

    @JavascriptInterface
    fun editPost(postId: String, renderToken: String?) =
        runProtected("editPost", renderToken) {
            postId.toIntOrNull()?.let { callbacks.onEditPostClick(it) }
        }

    @JavascriptInterface
    fun votePost(postId: String, type: Boolean, renderToken: String?) =
        runProtected("votePost", renderToken) {
            postId.toIntOrNull()?.let { callbacks.onVotePostClick(it, type) }
        }

    @JavascriptInterface
    fun submitPoll(action: String, method: String, encodedForm: String, renderToken: String?) =
            runProtected("submitPoll", renderToken) {
                callbacks.onPollSubmit(action, method, encodedForm)
            }

    @JavascriptInterface
    fun setHistoryBody(index: String, body: String) = runInUiThread(Runnable { index.toIntOrNull()?.let { callbacks.setHistoryBody(it, body) } })

    @JavascriptInterface
    fun copySelectedText(text: String) = runInUiThread(Runnable { callbacks.copyText(text) })

    @JavascriptInterface
    fun toast(text: String) = runInUiThread(Runnable { callbacks.toast(text) })

    @JavascriptInterface
    fun log(text: String) = runInUiThread(Runnable { callbacks.log(text) })

    @JavascriptInterface
    fun showPollResults(url: String?) = runInUiThread(Runnable {
        if (url.isNullOrBlank()) {
            callbacks.onPollResultsClick(url)
            return@Runnable
        }
        val normalizedUrl = normalizeOpenLinkUrl(url)
        when (val decision = UrlPolicy.classify(normalizedUrl)) {
            UrlDecision.Blocked -> Timber.w("Blocked unsafe Theme poll results URL")
            is UrlDecision.Internal -> callbacks.onPollResultsClick(decision.normalizedUrl)
            is UrlDecision.External -> callbacks.onPollResultsClick(decision.normalizedUrl)
        }
    })

    @JavascriptInterface
    fun showPoll() = runInUiThread(Runnable { callbacks.onPollClick() })

    @JavascriptInterface
    fun copySpoilerLink(postId: String, spoilNumber: String) = runInUiThread(Runnable { postId.toIntOrNull()?.let { callbacks.onSpoilerCopyLinkClick(it, spoilNumber) } })

    @JavascriptInterface
    fun setPollOpen(bValue: String) = runInUiThread(Runnable { callbacks.onPollHeaderClick(bValue.toBoolean()) })

    @JavascriptInterface
    fun setHatOpen(bValue: String) = runInUiThread(Runnable { callbacks.onHatHeaderClick(bValue.toBoolean()) })

    @JavascriptInterface
    fun requestHatOverlayInjection() = runInUiThread(Runnable { callbacks.onHatOverlayInjectionRequested() })

    @JavascriptInterface
    fun setInlineHatOpen(topicId: String, bValue: String) =
            setInlineHatOpen(topicId, bValue, "true")

    @JavascriptInterface
    fun setInlineHatOpen(topicId: String, bValue: String, persistPreference: String) = runInUiThread(Runnable {
        topicId.toIntOrNull()?.let {
            callbacks.onInlineHatHeaderClick(
                    it,
                    bValue.toBoolean(),
                    persistPreference.toBoolean(),
            )
        }
    })

    @JavascriptInterface
    fun shareSelectedText(text: String) = runInUiThread(Runnable { callbacks.shareText(text) })

    @JavascriptInterface
    fun openLink(url: String) = runInUiThread(Runnable {
        if (BuildConfig.DEBUG) Timber.d("ThemeJsInterface.openLink called")
        val normalizedUrl = normalizeOpenLinkUrl(url)
        when (val decision = UrlPolicy.classify(normalizedUrl)) {
            UrlDecision.Blocked -> Timber.w("Blocked unsafe Theme openLink URL")
            is UrlDecision.Internal -> callbacks.onOpenLink(decision.normalizedUrl)
            is UrlDecision.External -> callbacks.onOpenLink(decision.normalizedUrl)
        }
    })

    @JavascriptInterface
    fun onScrollCommandComplete(commandId: String, success: Boolean, reason: String?) =
            runInUiThread(Runnable { callbacks.onScrollCommandComplete(commandId, success, reason.orEmpty()) })

    @JavascriptInterface
    fun rememberLinkSourceAnchor(payload: String) {
        // Keep this synchronous: shouldOverrideUrlLoading may run before a posted UI callback.
        callbacks.onLinkSourceAnchorCaptured(payload)
    }

    @JavascriptInterface
    fun anchorDialog(postId: String, name: String) = runInUiThread(Runnable { postId.toIntOrNull()?.let { callbacks.onAnchorClick(it, name) } })

    private fun normalizeOpenLinkUrl(url: String): String {
        val value = url.replace("&amp;", "&").replace("\"", "").trim()
        return ArticleLinkResolver.resolveForNavigation(value) ?: value
    }

    private fun runProtected(action: String, renderToken: String?, actionBlock: () -> Unit) {
        if (!renderGuard.isValid(renderToken)) {
            if (BuildConfig.DEBUG) {
                StateRaceTrace.log(
                        domain = "theme_webview_bridge",
                        event = "stale_render_token_ignored",
                        reason = action
                )
            }
            Timber.w("Blocked Theme JS bridge action with invalid render token: %s", action)
            return
        }
        runInUiThread(Runnable { actionBlock() })
    }

}