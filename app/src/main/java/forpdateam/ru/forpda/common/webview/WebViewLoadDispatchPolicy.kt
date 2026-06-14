package forpdateam.ru.forpda.common.webview

/**
 * Shared guards for trusted-HTML WebView pipelines (news article body, QMS shell, etc.).
 *
 * First-open blank screens happen when native code marks a render "in flight" and later calls
 * skip duplicate work — even though [android.webkit.WebView.loadDataWithBaseURL] never ran or the
 * document never confirmed paint (WebView not ready, 0×0 layout, stale bridge).
 */
object WebViewLoadDispatchPolicy {

    data class Snapshot(
            val pendingTargetId: Int = -1,
            val pendingContentHash: Int = 0,
            val loadDispatched: Boolean = false,
            val requestGeneration: Int = 0,
            val domConfirmedGeneration: Int = 0,
            val lastDomConfirmedTargetId: Int = -1,
            /** Last target id passed to [markRenderRequested]; survives inflight reset on timeout. */
            val lastRequestedTargetId: Int = -1,
    )

    /**
     * Skip only when the same payload is actively loading (dispatched, not yet DOM-confirmed).
     */
    fun shouldSkipInflightDuplicate(
            force: Boolean,
            targetId: Int,
            contentHash: Int,
            snapshot: Snapshot
    ): Boolean {
        if (force) return false
        if (targetId != snapshot.pendingTargetId || contentHash != snapshot.pendingContentHash) {
            return false
        }
        if (!snapshot.loadDispatched) return false
        return snapshot.domConfirmedGeneration != snapshot.requestGeneration
    }

    /**
     * HTML/data is available but the WebView never confirmed paint — caller should force a render.
     */
    fun shouldForceEnsureRender(targetId: Int, snapshot: Snapshot): Boolean {
        if (targetId <= 0) return false
        if (snapshot.lastDomConfirmedTargetId == targetId &&
                snapshot.domConfirmedGeneration == snapshot.requestGeneration &&
                snapshot.domConfirmedGeneration > 0
        ) {
            return false
        }
        val pendingForTarget = targetId == snapshot.pendingTargetId ||
                (snapshot.pendingTargetId <= 0 && targetId == snapshot.lastRequestedTargetId)
        if (!pendingForTarget) return false
        if (!snapshot.loadDispatched) return true
        return snapshot.domConfirmedGeneration != snapshot.requestGeneration
    }

    /** Do not call [android.webkit.WebView.loadDataWithBaseURL] while the view is still 0×0. */
    fun shouldDeferLoadUntilLayout(webViewWidth: Int, webViewHeight: Int): Boolean =
            webViewWidth <= 0 || webViewHeight <= 0
}
