package forpdateam.ru.forpda.ui.fragments.news.details

/**
 * Escalation policy for the article first-open blank-render recovery.
 *
 * A blank body must never be left on screen: each detected blank escalates the recovery so the
 * article reliably renders or surfaces an error instead of silently staying blank.
 *
 *  - 1st blank  -> [Decision.RERENDER_CACHED]: re-render the already-loaded HTML into the (now
 *    laid-out) WebView. The common first-open race (load against a not-yet-measured view) is fixed
 *    by a single forced re-render.
 *  - 2nd blank  -> [Decision.REFETCH]: the in-memory HTML keeps painting blank, so force a network
 *    refetch (bypassing cache) in case the mapped HTML itself is the problem.
 *  - beyond max -> [Decision.GIVE_UP]: show the error placeholder with a manual retry action.
 */
internal object BlankRenderRetryPolicy {

    enum class Decision {
        RERENDER_CACHED,
        REFETCH,
        GIVE_UP
    }

    fun decide(retryCount: Int, maxRetries: Int): Decision = when {
        retryCount > maxRetries -> Decision.GIVE_UP
        retryCount > 1 -> Decision.REFETCH
        else -> Decision.RERENDER_CACHED
    }
}
