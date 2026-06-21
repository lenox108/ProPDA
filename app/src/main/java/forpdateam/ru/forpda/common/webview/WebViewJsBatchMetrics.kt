package forpdateam.ru.forpda.common.webview

import java.util.concurrent.atomic.AtomicLong

/**
 * DEBUG-oriented metrics for the [ExtendedWebView] JS batching system (Phase 5).
 *
 * Pure JVM, thread-safe via atomics, no Android dependency — so it is unit-testable.
 * The owning WebView records events here; reporting is gated behind BuildConfig.DEBUG
 * at the call sites (this class itself never logs).
 *
 * Tracked counters:
 *  - [commandCount]       : individual JS commands enqueued via evalJs(script)
 *  - [flushCount]         : number of batch flushes that actually ran non-empty script
 *  - [flushedCommandTotal]: total commands carried by all flushes (for average batch size)
 *  - [droppedByClearCount]: commands discarded by clearQueuedJs()
 *  - [ignoredAfterDestroy]: enqueue attempts rejected because the WebView was destroyed
 *  - [forcedImmediateCount]: commands sent via the explicit immediate path
 */
class WebViewJsBatchMetrics {

    private val commandCounter = AtomicLong(0)
    private val flushCounter = AtomicLong(0)
    private val flushedCommandCounter = AtomicLong(0)
    private val droppedByClearCounter = AtomicLong(0)
    private val ignoredAfterDestroyCounter = AtomicLong(0)
    private val forcedImmediateCounter = AtomicLong(0)
    private val pendingCommands = AtomicLong(0)

    val commandCount: Long get() = commandCounter.get()
    val flushCount: Long get() = flushCounter.get()
    val flushedCommandTotal: Long get() = flushedCommandCounter.get()
    val droppedByClearCount: Long get() = droppedByClearCounter.get()
    val ignoredAfterDestroyCount: Long get() = ignoredAfterDestroyCounter.get()
    val forcedImmediateCount: Long get() = forcedImmediateCounter.get()

    /** Average number of commands per non-empty flush; 0.0 if nothing flushed yet. */
    val averageBatchSize: Double
        get() {
            val flushes = flushCounter.get()
            if (flushes == 0L) return 0.0
            return flushedCommandCounter.get().toDouble() / flushes.toDouble()
        }

    /** Number of commands currently buffered (enqueued but not yet flushed or cleared). */
    val pendingCommandCount: Long get() = pendingCommands.get()

    fun onCommandEnqueued() {
        commandCounter.incrementAndGet()
        pendingCommands.incrementAndGet()
    }

    /** Record a flush carrying [commandsInBatch] commands. Empty flushes are ignored. */
    fun onFlush(commandsInBatch: Long) {
        if (commandsInBatch <= 0) return
        flushCounter.incrementAndGet()
        flushedCommandCounter.addAndGet(commandsInBatch)
        decrementPendingBy(commandsInBatch)
    }

    /** Record that [cleared] buffered commands were dropped by clearQueuedJs(). */
    fun onClear(cleared: Long) {
        if (cleared <= 0) return
        droppedByClearCounter.addAndGet(cleared)
        decrementPendingBy(cleared)
    }

    fun onIgnoredAfterDestroy() {
        ignoredAfterDestroyCounter.incrementAndGet()
    }

    fun onForcedImmediate() {
        forcedImmediateCounter.incrementAndGet()
    }

    fun snapshot(): Map<String, Any?> = mapOf(
            "jsCommandCount" to commandCount,
            "jsFlushCount" to flushCount,
            "jsAvgBatchSize" to String.format(java.util.Locale.US, "%.2f", averageBatchSize),
            "jsDroppedByClear" to droppedByClearCount,
            "jsIgnoredAfterDestroy" to ignoredAfterDestroyCount,
            "jsForcedImmediate" to forcedImmediateCount,
    )

    private fun decrementPendingBy(amount: Long) {
        while (true) {
            val current = pendingCommands.get()
            val next = (current - amount).coerceAtLeast(0L)
            if (pendingCommands.compareAndSet(current, next)) return
        }
    }
}
