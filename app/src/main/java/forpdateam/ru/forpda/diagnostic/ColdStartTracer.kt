package forpdateam.ru.forpda.diagnostic

import android.os.SystemClock
import timber.log.Timber

/**
 * Lightweight in-process cold-start tracer.
 *
 * Why this exists:
 * - StrictMode is enabled only in debug builds (see [forpdateam.ru.forpda.App.setupStrictMode]).
 *   We have no release-side signal for "how long did cold start take on real devices".
 * - Sending data to AppMetrica requires explicit consent and is gated on the `store` flavor.
 *   We want a privacy-free, always-on fallback that does **not** leave the process.
 *
 * Usage:
 * 1. Call [mark] from the earliest possible hook (e.g. the very first line of
 *    `Application.attachBaseContext` or `Application.onCreate`) with a phase name.
 * 2. Call [snapshot] later (e.g. in `MainActivity.onResume`) to log the full trace.
 * 3. Call [reset] only between cold starts; do not call it from `onResume`.
 *
 * This class is intentionally a singleton with no synchronization beyond a
 * volatile read of the mark list. The clock source is [SystemClock.elapsedRealtime],
 * which is monotonic and survives deep sleep.
 */
object ColdStartTracer {

    private const val MAX_MARKS = 16
    private const val LOG_TAG = "ColdStart"

    private data class Mark(val name: String, val elapsedRealtimeMs: Long)

    @Volatile
    private var processStartElapsedMs: Long = -1L

    @Volatile
    private var marks: List<Mark> = emptyList()

    /**
     * Indirection over [SystemClock.elapsedRealtime] so unit tests can swap a
     * non-zero monotonic clock. Robolectric / `unitTests.returnDefaultValues`
     * make the real clock return `0L`, which collides with the
     * "anchor not yet set" sentinel below; routing through this var lets
     * tests advance the clock without poking at internals.
     */
    internal var clock: () -> Long = { SystemClock.elapsedRealtime() }

    /**
     * Records the process-start anchor. Call exactly once, as early as possible
     * in the process lifecycle (best-effort: from `Application.attachBaseContext`).
     *
     * Note: we do not skip when the clock reports `0L` — that is a legal
     * monotonic value (the first millisecond of uptime). A sentinel of
     * `-1L` (or any negative value) is used to mean "not yet set" so the
     * guard no longer collides with a real `0L` reading.
     */
    fun markProcessStart() {
        val now = clock()
        if (processStartElapsedMs < 0L) {
            processStartElapsedMs = now
        }
    }

    /**
     * Records a named checkpoint relative to [markProcessStart].
     * Silently drops marks past [MAX_MARKS] to bound memory.
     */
    fun mark(name: String) {
        if (processStartElapsedMs < 0L) {
            // markProcessStart() was not called; do not invent a baseline.
            return
        }
        val now = clock()
        synchronized(this) {
            if (marks.size >= MAX_MARKS) return
            marks = marks + Mark(name, now)
        }
    }

    /**
     * Returns a human-readable summary suitable for logcat. Never returns null.
     */
    fun snapshot(): String {
        val start = processStartElapsedMs
        if (start < 0L) return "$LOG_TAG: no process-start anchor recorded"
        val copy = synchronized(this) { marks }
        if (copy.isEmpty()) {
            val total = clock() - start
            return "$LOG_TAG: total=${total}ms (no marks)"
        }
        val sb = StringBuilder(LOG_TAG).append(": total=")
            .append(clock() - start).append("ms [")
        copy.forEachIndexed { i, m ->
            if (i > 0) sb.append(", ")
            sb.append(m.name).append('=').append(m.elapsedRealtimeMs - start).append("ms")
        }
        return sb.append(']').toString()
    }

    /**
     * Emits the current snapshot via [Timber]. Safe to call from any thread.
     */
    fun logSnapshot() {
        Timber.tag(LOG_TAG).i(snapshot())
    }

    /**
     * Clears marks between cold starts. Does **not** reset the process-start
     * anchor in production. Tests that need a clean slate can pass
     * `resetAnchor = true` (e.g. via [setUp] / [org.junit.Before]).
     */
    @JvmOverloads
    fun reset(resetAnchor: Boolean = false) {
        synchronized(this) {
            marks = emptyList()
            if (resetAnchor) {
                processStartElapsedMs = -1L
            }
        }
    }
}
