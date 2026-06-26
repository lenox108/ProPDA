package forpdateam.ru.forpda.presentation.theme

/**
 * R-02: gesture-scoped "navigation already dispatched for this user gesture" guard.
 *
 * A single tap can reach navigation through three entry points (WebViewClient.handleUri,
 * WebViewClient.onPageStarted and the JS bridge openLink). The previous protection was a
 * brittle string `lastHandledUrl` de-dup that failed when the three paths saw slightly
 * different URL shapes (resolved vs. raw, with/without `#entry`), letting one tap push history
 * twice or double-load.
 *
 * This guard tracks a monotonic gesture token instead. The first dispatch for a gesture wins;
 * subsequent dispatches for the SAME gesture are suppressed regardless of URL string. A new
 * user gesture (fresh source-anchor capture) or a page settle (fresh loadData) opens a new
 * gesture window, so legitimate distinct navigations are never blocked.
 */
class NavigationGestureDispatchGuard {

    private var currentGestureToken: Long = 0L
    private var dispatchedGestureToken: Long = -1L

    /**
     * Opens a new gesture window. Called on a fresh user gesture (source-anchor capture)
     * and on page settle (loadData). Returns the new token for diagnostics.
     */
    fun beginGesture(): Long {
        currentGestureToken += 1
        return currentGestureToken
    }

    /**
     * Atomically claims the current gesture for navigation dispatch.
     * Returns true exactly once per gesture window; false for repeat dispatches.
     */
    fun tryClaimDispatch(): Boolean {
        if (dispatchedGestureToken == currentGestureToken) {
            return false
        }
        dispatchedGestureToken = currentGestureToken
        return true
    }

    /** True if the current gesture has already dispatched a navigation. */
    fun isCurrentGestureDispatched(): Boolean = dispatchedGestureToken == currentGestureToken

    /** Clears the dispatched mark on page settle so the next gesture starts fresh. */
    fun reset() {
        beginGesture()
    }
}
