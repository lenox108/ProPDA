package forpdateam.ru.forpda.presentation.theme

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TopicReadExitPolicyTest {

    private val threshold = TopicReadExitPolicy.LAST_PAGE_MARK_READ_RATIO_THRESHOLD

    @Test
    fun marksReadWhenWasNearBottomRegardlessOfRatio() {
        // wasNearBottom=true bypasses the scroll ratio gate entirely (legacy 0.995+ cases
        // and any post-fix threshold). This is the cheap fast-path: if the WebView already
        // reported a near-bottom settle, mark read.
        assertTrue(TopicReadExitPolicy.shouldMarkReadOnLastPageExit(wasNearBottom = true, scrollRatio = null))
        assertTrue(TopicReadExitPolicy.shouldMarkReadOnLastPageExit(wasNearBottom = true, scrollRatio = 0.0))
        assertTrue(TopicReadExitPolicy.shouldMarkReadOnLastPageExit(wasNearBottom = true, scrollRatio = 0.5))
    }

    @Test
    fun marksReadOnLastPageAtOrAboveThreshold() {
        // log752 / 1122662: after the fix the threshold was lowered from 0.995 to ~0.93.
        // At the new threshold the policy must still mark the topic read; the existing
        // log 752 ratios (0.975..0.979) must continue to be honored.
        val aboveThreshold = (threshold + 0.001).coerceAtMost(0.999)
        val log752High = 0.979
        val log752Low = 0.9759

        assertTrue(
                "scrollRatio=$log752High must be >= threshold=$threshold",
                TopicReadExitPolicy.shouldMarkReadOnLastPageExit(wasNearBottom = false, scrollRatio = log752High)
        )
        assertTrue(
                "scrollRatio=$log752Low must be >= threshold=$threshold",
                TopicReadExitPolicy.shouldMarkReadOnLastPageExit(wasNearBottom = false, scrollRatio = log752Low)
        )
        assertTrue(
                "scrollRatio=$aboveThreshold (just above threshold) must mark read",
                TopicReadExitPolicy.shouldMarkReadOnLastPageExit(wasNearBottom = false, scrollRatio = aboveThreshold)
        )
        // Identical-ratio: boundary check on the threshold itself.
        assertTrue(
                "scrollRatio=threshold must be treated as 'at threshold' and mark read",
                TopicReadExitPolicy.shouldMarkReadOnLastPageExit(wasNearBottom = false, scrollRatio = threshold)
        )
    }

    @Test
    fun skipsMarkReadWhenUserDidNotReachLastPageContent() {
        // Just below threshold (e.g. 0.92 if the fix lowered threshold to 0.93) must NOT mark.
        val justBelow = (threshold - 0.01).coerceAtLeast(0.0)
        val zeroRatio = 0.0

        assertFalse(
                "scrollRatio=$justBelow (just below threshold=$threshold) must not mark read",
                TopicReadExitPolicy.shouldMarkReadOnLastPageExit(wasNearBottom = false, scrollRatio = justBelow)
        )
        assertFalse(
                "scrollRatio=$zeroRatio must not mark read",
                TopicReadExitPolicy.shouldMarkReadOnLastPageExit(wasNearBottom = false, scrollRatio = zeroRatio)
        )
        // No data at all → no mark read.
        assertFalse(
                "null wasNearBottom and null scrollRatio must not mark read",
                TopicReadExitPolicy.shouldMarkReadOnLastPageExit(wasNearBottom = null, scrollRatio = null)
        )
        // No wasNearBottom but ratio below threshold → no mark read.
        assertFalse(
                "null wasNearBottom with low ratio must not mark read",
                TopicReadExitPolicy.shouldMarkReadOnLastPageExit(wasNearBottom = null, scrollRatio = 0.5)
        )
    }

    @Test
    fun thresholdConstantIsBounded() {
        // Sanity check that the threshold constant is within a sensible range. The fix
        // lowered it from 0.995 to ~0.93 — we just guard against a typo that puts it
        // outside [0.5, 1.0].
        assertTrue(
                "LAST_PAGE_MARK_READ_RATIO_THRESHOLD must be within [0.5, 1.0]",
                threshold in 0.5..1.0
        )
    }
}
