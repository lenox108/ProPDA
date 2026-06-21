package forpdateam.ru.forpda.common.webview

import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class WebViewJsBatchMetricsTest {

    private lateinit var metrics: WebViewJsBatchMetrics

    @Before
    fun setUp() {
        metrics = WebViewJsBatchMetrics()
    }

    @Test
    fun `initial state is all zeros`() {
        assertEquals(0L, metrics.commandCount)
        assertEquals(0L, metrics.flushCount)
        assertEquals(0L, metrics.flushedCommandTotal)
        assertEquals(0L, metrics.droppedByClearCount)
        assertEquals(0L, metrics.ignoredAfterDestroyCount)
        assertEquals(0L, metrics.forcedImmediateCount)
        assertEquals(0L, metrics.pendingCommandCount)
        assertEquals(0.0, metrics.averageBatchSize, 0.0001)
    }

    @Test
    fun `enqueue increments command and pending counts`() {
        repeat(3) { metrics.onCommandEnqueued() }
        assertEquals(3L, metrics.commandCount)
        assertEquals(3L, metrics.pendingCommandCount)
    }

    @Test
    fun `flush records batch and clears pending`() {
        repeat(4) { metrics.onCommandEnqueued() }
        metrics.onFlush(4)
        assertEquals(1L, metrics.flushCount)
        assertEquals(4L, metrics.flushedCommandTotal)
        assertEquals(0L, metrics.pendingCommandCount)
    }

    @Test
    fun `empty flush is ignored`() {
        metrics.onFlush(0)
        assertEquals(0L, metrics.flushCount)
    }

    @Test
    fun `average batch size is mean of flushed commands per flush`() {
        metrics.onCommandEnqueued(); metrics.onCommandEnqueued()
        metrics.onFlush(2)
        repeat(4) { metrics.onCommandEnqueued() }
        metrics.onFlush(4)
        // (2 + 4) / 2 flushes = 3.0
        assertEquals(3.0, metrics.averageBatchSize, 0.0001)
    }

    @Test
    fun `clear drops pending commands and counts them`() {
        repeat(5) { metrics.onCommandEnqueued() }
        metrics.onClear(5)
        assertEquals(5L, metrics.droppedByClearCount)
        assertEquals(0L, metrics.pendingCommandCount)
    }

    @Test
    fun `pending never goes negative`() {
        metrics.onCommandEnqueued()
        metrics.onClear(10)
        assertEquals(0L, metrics.pendingCommandCount)
    }

    @Test
    fun `ignored after destroy and forced immediate counters`() {
        metrics.onIgnoredAfterDestroy()
        metrics.onIgnoredAfterDestroy()
        metrics.onForcedImmediate()
        assertEquals(2L, metrics.ignoredAfterDestroyCount)
        assertEquals(1L, metrics.forcedImmediateCount)
    }

    @Test
    fun `snapshot exposes all counters`() {
        metrics.onCommandEnqueued()
        metrics.onFlush(1)
        metrics.onIgnoredAfterDestroy()
        val snap = metrics.snapshot()
        assertEquals(1L, snap["jsCommandCount"])
        assertEquals(1L, snap["jsFlushCount"])
        assertEquals(1L, snap["jsIgnoredAfterDestroy"])
        assertEquals("1.00", snap["jsAvgBatchSize"])
    }
}
