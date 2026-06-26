package forpdateam.ru.forpda.presentation.theme

import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadPositionSaveGateTest {

    @After
    fun tearDown() {
        ReadPositionSaveGate.resetForTests()
    }

    @Test
    fun suppressesDuringHighlightWindow() {
        ReadPositionSaveGate.onTopicOpenStarted(nowMs = 1000L)
        assertTrue(ReadPositionSaveGate.shouldSuppressSave(hasBlockingScrollPending = false, nowMs = 1500L))
        assertFalse(ReadPositionSaveGate.shouldSuppressSave(hasBlockingScrollPending = false, nowMs = 5000L))
    }

    @Test
    fun suppressesDuringBlockingScroll() {
        ReadPositionSaveGate.onHighlightFadeoutCompleted(renderGenerationId = 1, nowMs = 5000L)
        assertTrue(ReadPositionSaveGate.shouldSuppressSave(hasBlockingScrollPending = true, nowMs = 6000L))
    }

    @Test
    fun fadeoutClearsHighlightWindow() {
        ReadPositionSaveGate.onHighlightArmed(renderGenerationId = 3, nowMs = 1000L)
        ReadPositionSaveGate.onHighlightFadeoutCompleted(renderGenerationId = 3, nowMs = 3500L)
        assertFalse(ReadPositionSaveGate.shouldSuppressSave(hasBlockingScrollPending = false, nowMs = 3600L))
    }
}
