package forpdateam.ru.forpda.common.webview

import forpdateam.ru.forpda.common.webview.WebViewRenderSession.Owner

/**
 * Small shared controller for the generic WebView render lifecycle. It owns the active
 * [WebViewRenderSession] and the dispatch state, delegating all decision logic to
 * [WebViewLoadDispatchPolicy] (it does NOT duplicate that logic).
 *
 * Phase 3 of the WebView stabilization task. This controller only handles generic render
 * lifecycle state (request → dispatch → DOM/page confirm → cleanup). It MUST NOT contain
 * feature-specific behavior (scroll restore, highlight, bridge wiring, HTML composition).
 *
 * It is introduced as an additional guard/diagnostic layer; integrating pipelines keep their
 * existing per-feature render-generation systems during migration.
 *
 * Threading: not thread-safe. Intended to be used from the WebView's UI thread, like the
 * existing per-feature generation fields.
 */
class WebViewRenderController {

    private var renderGeneration: Int = 0
    private var activeSession: WebViewRenderSession? = null

    private var pendingTargetId: Int = -1
    private var pendingContentHash: Int = 0
    private var loadDispatched: Boolean = false
    private var domConfirmedGeneration: Int = 0
    private var pageConfirmedGeneration: Int = 0
    private var lastDomConfirmedTargetId: Int = -1
    private var lastRequestedTargetId: Int = -1

    fun activeSession(): WebViewRenderSession? = activeSession

    fun currentGeneration(): Int = renderGeneration

    /**
     * Start a new render request. Mints a fresh session with an incremented generation and
     * records it as pending. Does NOT itself dispatch the load — call [markLoadDispatched]
     * once [android.webkit.WebView.loadDataWithBaseURL] actually runs.
     */
    fun beginRender(
            owner: Owner,
            targetId: Int,
            contentHash: Int,
            bridgeToken: String? = null,
            createdAt: Long = 0L,
    ): WebViewRenderSession {
        renderGeneration++
        pendingTargetId = targetId
        pendingContentHash = contentHash
        loadDispatched = false
        lastRequestedTargetId = targetId
        val session = WebViewRenderSession.create(
                owner = owner,
                targetId = targetId,
                contentHash = contentHash,
                renderGeneration = renderGeneration,
                bridgeToken = bridgeToken,
                createdAt = createdAt,
        )
        activeSession = session
        return session
    }

    /**
     * Delegates to [WebViewLoadDispatchPolicy.shouldSkipInflightDuplicate]. Call this with the
     * CANDIDATE target/content BEFORE [beginRender], so it compares against what is already
     * pending/in-flight rather than the not-yet-created session.
     */
    fun shouldSkipDuplicate(targetId: Int, contentHash: Int, force: Boolean): Boolean =
            WebViewLoadDispatchPolicy.shouldSkipInflightDuplicate(
                    force = force,
                    targetId = targetId,
                    contentHash = contentHash,
                    snapshot = snapshot(),
            )

    /** Convenience overload using an existing session's target/content. */
    fun shouldSkipDuplicate(session: WebViewRenderSession, force: Boolean): Boolean =
            shouldSkipDuplicate(session.targetId, session.contentHash, force)

    /** Delegates to [WebViewLoadDispatchPolicy.shouldForceEnsureRender] for the active target. */
    fun shouldForceEnsureRender(targetId: Int): Boolean =
            WebViewLoadDispatchPolicy.shouldForceEnsureRender(targetId, snapshot())

    /** Delegates to [WebViewLoadDispatchPolicy.shouldDeferLoadUntilLayout]. */
    fun shouldDeferUntilLayout(webViewWidth: Int, webViewHeight: Int): Boolean =
            WebViewLoadDispatchPolicy.shouldDeferLoadUntilLayout(webViewWidth, webViewHeight)

    /** Record that the actual load was dispatched for [session] (if it is still current). */
    fun markLoadDispatched(session: WebViewRenderSession) {
        if (!isCurrent(session)) return
        loadDispatched = true
    }

    /** Record DOM-ready confirmation for [session] (if it is still current). */
    fun markDomConfirmed(session: WebViewRenderSession) {
        if (!isCurrent(session)) return
        domConfirmedGeneration = renderGeneration
        lastDomConfirmedTargetId = session.targetId
    }

    /** Record page-finished confirmation for [session] (if it is still current). */
    fun markPageConfirmed(session: WebViewRenderSession) {
        if (!isCurrent(session)) return
        pageConfirmedGeneration = renderGeneration
    }

    /** True if [session] is the active one (same identity and not superseded). */
    fun isCurrent(session: WebViewRenderSession): Boolean {
        val active = activeSession ?: return false
        return session == active
    }

    /** True if a callback tied to [session] should be ignored because a newer render took over. */
    fun isStaleCallback(session: WebViewRenderSession): Boolean =
            session.isStaleComparedTo(activeSession)

    /** Invalidate the active session and reset dispatch state (call on destroy / new screen). */
    fun cleanup() {
        activeSession = null
        pendingTargetId = -1
        pendingContentHash = 0
        loadDispatched = false
        domConfirmedGeneration = 0
        pageConfirmedGeneration = 0
        lastDomConfirmedTargetId = -1
        lastRequestedTargetId = -1
    }

    private fun snapshot(): WebViewLoadDispatchPolicy.Snapshot =
            WebViewLoadDispatchPolicy.Snapshot(
                    pendingTargetId = pendingTargetId,
                    pendingContentHash = pendingContentHash,
                    loadDispatched = loadDispatched,
                    requestGeneration = renderGeneration,
                    domConfirmedGeneration = domConfirmedGeneration,
                    lastDomConfirmedTargetId = lastDomConfirmedTargetId,
                    lastRequestedTargetId = lastRequestedTargetId,
            )
}
