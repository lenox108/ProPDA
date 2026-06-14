package forpdateam.ru.forpda.ui.fragments.news.details

import forpdateam.ru.forpda.common.webview.WebViewLoadDispatchPolicy

/**
 * Article-specific adapter over [WebViewLoadDispatchPolicy].
 *
 * First-open blank screens happened when [renderArticle] posted [loadAction] before the WebView
 * was ready or laid out — later calls were skipped as duplicate inflight renders until reopen.
 */
internal object ArticleRenderInflightPolicy {

    data class Snapshot(
            val inflightArticleId: Int = -1,
            val inflightHtmlHash: Int = 0,
            val renderLoadDispatched: Boolean = false,
            val articleRequestId: Int = 0,
            val domContentLoadedRequestId: Int = 0,
            val lastDomConfirmedArticleId: Int = -1,
            val lastRequestedArticleId: Int = -1,
    )

    fun shouldSkipInflightDuplicate(
            force: Boolean,
            articleId: Int,
            htmlHash: Int,
            snapshot: Snapshot
    ): Boolean = WebViewLoadDispatchPolicy.shouldSkipInflightDuplicate(
            force = force,
            targetId = articleId,
            contentHash = htmlHash,
            snapshot = snapshot.toShared()
    )

    fun shouldForceEnsureRender(articleId: Int, snapshot: Snapshot): Boolean =
            WebViewLoadDispatchPolicy.shouldForceEnsureRender(
                    targetId = articleId,
                    snapshot = snapshot.toShared()
            )

    private fun Snapshot.toShared(): WebViewLoadDispatchPolicy.Snapshot =
            WebViewLoadDispatchPolicy.Snapshot(
                    pendingTargetId = inflightArticleId,
                    pendingContentHash = inflightHtmlHash,
                    loadDispatched = renderLoadDispatched,
                    requestGeneration = articleRequestId,
                    domConfirmedGeneration = domContentLoadedRequestId,
                    lastDomConfirmedTargetId = lastDomConfirmedArticleId,
                    lastRequestedTargetId = lastRequestedArticleId,
            )
}
