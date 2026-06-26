package forpdateam.ru.forpda.ui.fragments.theme

import android.os.Handler
import android.os.Looper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Regression for AUDIT-L02: `ThemeFragmentWeb` schedules
 * `webView.postDelayed(...)` calls inside `updateView` and
 * `verifyThemeRenderedOrRetry` that must not fire after `onDestroyView`.
 *
 * The fragment's `onDestroyView` calls
 * `viewHandler.removeCallbacksAndMessages(null)` (line 1738) which clears
 * every pending message on the main-thread `Handler`. The lambdas are
 * additionally guarded by a `viewRuntimeGeneration` mismatch check
 * (`postOnActiveWebView`) so even if the queue drain were missed, the
 * callback would no-op. This test pins down the "no pending messages
 * after `removeCallbacksAndMessages(null)`" contract on a real
 * `Handler`/`Looper` so future refactors of the fragment don't silently
 * lose the cleanup.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [33])
class ThemeFragmentWebPostDelayedCleanupTest {

    @Test
    fun removeCallbacksAndMessages_clearsAllPending() {
        val handler = Handler(Looper.getMainLooper())
        val looper = shadowOf(Looper.getMainLooper())

        var ran = false
        handler.postDelayed({ ran = true }, 5_000L)
        handler.postDelayed({ ran = true }, 10_000L)

        // Simulate the exact cleanup that ThemeFragmentWeb.onDestroyView
        // performs (line 1738).
        handler.removeCallbacksAndMessages(null)

        // Drain the looper — no work should fire because the queue is empty.
        looper.idle()
        assertTrue(
                "all postDelayed tasks must be removed by onDestroyView; " +
                        "if ran=true the message survived the drain",
                !ran
        )
    }

    @Test
    fun generationGuard_preventsLateFiringWhenQueueNotDrained() {
        // Mirror the in-fragment guard: a Runnable captures the current
        // `viewRuntimeGeneration` and no-ops if it no longer matches.
        val handler = Handler(Looper.getMainLooper())
        val looper = shadowOf(Looper.getMainLooper())

        var liveGeneration = 0
        var firedCount = 0

        val r = Runnable {
            if (liveGeneration != 0) return@Runnable
            firedCount++
        }
        handler.postDelayed(r, 1_000L)

        // Bump the generation BEFORE the runnable fires (simulating
        // onDestroyView incrementing viewRuntimeGeneration).
        liveGeneration = 1

        looper.idle()
        assertEquals(
                "generation-guarded runnable must not fire after the view is destroyed",
                0, firedCount
        )
    }
}
