package forpdateam.ru.forpda.common.webview

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SlowWebViewModePolicyTest {

    @Test
    fun `disabled returns normal config`() {
        val config = SlowWebViewModePolicy.configFor(enabled = false)
        assertFalse(config.enabled)
        assertEquals(16L, config.jsBatchDelayMs)
        assertTrue(config.allowSmartPreload)
        assertTrue(config.allowAggressiveHighlightReapply)
        assertTrue(config.allowSpeculativeRender)
        assertEquals(1.0, config.domProbeIntervalScale, 0.0001)
    }

    @Test
    fun `enabled returns slow config with reduced aggressiveness`() {
        val config = SlowWebViewModePolicy.configFor(enabled = true)
        assertTrue(config.enabled)
        assertFalse(config.allowSmartPreload)
        assertFalse(config.allowAggressiveHighlightReapply)
        assertFalse(config.allowSpeculativeRender)
    }

    @Test
    fun `slow mode increases js batch delay`() {
        assertTrue(
                SlowWebViewModePolicy.SLOW.jsBatchDelayMs > SlowWebViewModePolicy.NORMAL.jsBatchDelayMs
        )
    }

    @Test
    fun `slow mode reduces scroll restore retries`() {
        assertTrue(
                SlowWebViewModePolicy.SLOW.maxScrollRestoreRetries < SlowWebViewModePolicy.NORMAL.maxScrollRestoreRetries
        )
    }

    @Test
    fun `slow mode increases dom probe interval scale`() {
        assertTrue(
                SlowWebViewModePolicy.SLOW.domProbeIntervalScale > SlowWebViewModePolicy.NORMAL.domProbeIntervalScale
        )
    }

    @Test
    fun `smart preload allowed only when slow mode off`() {
        assertTrue(SlowWebViewModePolicy.isSmartPreloadAllowed(enabled = false))
        assertFalse(SlowWebViewModePolicy.isSmartPreloadAllowed(enabled = true))
    }

    @Test
    fun `slow mode never zeroes out scroll restore retries (does not remove functionality)`() {
        assertTrue(SlowWebViewModePolicy.SLOW.maxScrollRestoreRetries > 0)
    }
}
