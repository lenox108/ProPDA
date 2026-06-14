package forpdateam.ru.forpda.presentation.search

import forpdateam.ru.forpda.common.webview.WebViewLoadDispatchPolicy
import forpdateam.ru.forpda.ui.fragments.news.details.BlankRenderRetryPolicy

/**
 * WebView render guards for in-topic / forum post search results.
 *
 * First-open blank screens happen when [android.webkit.WebView.loadDataWithBaseURL] runs while the
 * view is still 0×0 (WebView is added to [androidx.swiperefreshlayout.widget.SwipeRefreshLayout]
 * only after the first HTML payload arrives).
 */
object SearchWebRenderPolicy {

    const val MAX_BLANK_RENDER_RETRIES = 2
    const val SEARCH_BASE_URL = "https://4pda.to/forum/"

    fun shouldDeferHtmlLoad(webViewWidth: Int, webViewHeight: Int): Boolean =
            WebViewLoadDispatchPolicy.shouldDeferLoadUntilLayout(webViewWidth, webViewHeight)

    /**
     * Same HTML queued twice (StateFlow + [SearchUiEvent.ShowData]) while a load is already
     * in flight for this generation — skip the second [loadDataWithBaseURL].
     */
    fun shouldSkipInflightDuplicate(
            force: Boolean,
            renderGeneration: Int,
            htmlHash: Int,
            loadDispatched: Boolean,
            domConfirmedGeneration: Int,
            pendingHtmlHash: Int,
    ): Boolean = WebViewLoadDispatchPolicy.shouldSkipInflightDuplicate(
            force = force,
            targetId = renderGeneration,
            contentHash = htmlHash,
            snapshot = WebViewLoadDispatchPolicy.Snapshot(
                    pendingTargetId = renderGeneration,
                    pendingContentHash = pendingHtmlHash,
                    loadDispatched = loadDispatched,
                    requestGeneration = renderGeneration,
                    domConfirmedGeneration = domConfirmedGeneration,
            ),
    )

    /** Same HTML payload already queued for the current generation (update pending only). */
    fun isDuplicateQueuedHtml(htmlHash: Int, pendingHtmlHash: Int, renderGeneration: Int): Boolean =
            htmlHash == pendingHtmlHash && renderGeneration > 0

    fun isBodyVisible(contentHeight: Int, domPostCount: Int): Boolean =
            contentHeight > 0 || domPostCount > 0

    enum class BlankRecovery {
        RERENDER_CACHED,
        REFETCH,
        GIVE_UP,
    }

    fun blankRecoveryDecision(retryCount: Int): BlankRecovery = when (
            BlankRenderRetryPolicy.decide(retryCount, MAX_BLANK_RENDER_RETRIES)
    ) {
        BlankRenderRetryPolicy.Decision.RERENDER_CACHED -> BlankRecovery.RERENDER_CACHED
        BlankRenderRetryPolicy.Decision.REFETCH -> BlankRecovery.REFETCH
        BlankRenderRetryPolicy.Decision.GIVE_UP -> BlankRecovery.GIVE_UP
    }
}
