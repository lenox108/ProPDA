package forpdateam.ru.forpda.presentation.theme

import android.os.SystemClock

/**
 * Suppresses [ThemeReadPositionRepository] writes while a topic open is
 * arming / showing the transient post highlight or while a blocking programmatic
 * scroll is in flight. IntersectionObserver reports the topmost visible post
 * during scroll restore and would overwrite `lastViewedPostId` with a post
 * the user has not actually read yet (log: alternating 143903679 / 143903696
 * during open).
 */
object ReadPositionSaveGate {

    private const val HIGHLIGHT_WINDOW_MS = 3_000L

    @Volatile
    private var suppressUntilUptimeMs: Long = 0L

    @Volatile
    private var armedHighlightGeneration: Int = 0

    fun onTopicOpenStarted(nowMs: Long = SystemClock.uptimeMillis()) {
        suppressUntilUptimeMs = nowMs + HIGHLIGHT_WINDOW_MS
        armedHighlightGeneration = 0
    }

    fun onHighlightArmed(
            renderGenerationId: Int,
            nowMs: Long = SystemClock.uptimeMillis(),
    ) {
        if (renderGenerationId > 0) {
            armedHighlightGeneration = renderGenerationId
        }
        suppressUntilUptimeMs = nowMs + HIGHLIGHT_WINDOW_MS
    }

    fun onHighlightFadeoutCompleted(
            renderGenerationId: Int,
            nowMs: Long = SystemClock.uptimeMillis(),
    ) {
        if (renderGenerationId <= 0) return
        if (renderGenerationId >= armedHighlightGeneration) {
            suppressUntilUptimeMs = 0L
        }
    }

    fun shouldSuppressSave(
            hasBlockingScrollPending: Boolean,
            nowMs: Long = SystemClock.uptimeMillis(),
    ): Boolean {
        if (hasBlockingScrollPending) return true
        return nowMs < suppressUntilUptimeMs
    }

    /** Test-only reset. */
    internal fun resetForTests() {
        suppressUntilUptimeMs = 0L
        armedHighlightGeneration = 0
    }
}
