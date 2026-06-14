package forpdateam.ru.forpda.presentation.qms.chat

import java.util.concurrent.ConcurrentHashMap

/**
 * Per-trace open latency markers for [FpdaDebugLog.TAG_QMS_OPEN] (no message bodies).
 */
object QmsOpenTiming {

    private val openStartMs = ConcurrentHashMap<String, Long>()

    fun markOpenStart(traceId: String) {
        openStartMs[traceId] = QmsOpenTrace.nowMs()
    }

    fun logCacheShown(traceId: String, dialogId: Int, userId: Int, requestId: Int, messagesCount: Int) {
        logElapsed(traceId, dialogId, userId, requestId, "cache_shown", messagesCount = messagesCount)
    }

    fun logNetworkDone(traceId: String, dialogId: Int, userId: Int, requestId: Int, messagesCount: Int?) {
        logElapsed(traceId, dialogId, userId, requestId, "network_done", messagesCount = messagesCount)
    }

    fun logRenderVisible(traceId: String, dialogId: Int, userId: Int, requestId: Int, containerCount: Int) {
        logElapsed(
                traceId,
                dialogId,
                userId,
                requestId,
                "render_visible",
                messagesCount = containerCount
        )
        clear(traceId)
    }

    private fun logElapsed(
            traceId: String,
            dialogId: Int,
            userId: Int,
            requestId: Int,
            phase: String,
            messagesCount: Int? = null
    ) {
        val start = openStartMs[traceId] ?: return
        val elapsed = QmsOpenTrace.nowMs() - start
        val extraKey = when (phase) {
            "cache_shown" -> "cacheShownMs"
            "network_done" -> "networkDoneMs"
            "render_visible" -> "renderVisibleMs"
            else -> "elapsedMs"
        }
        QmsOpenTrace.logOpen(
                traceId = traceId,
                dialogId = dialogId,
                userId = userId,
                requestId = requestId,
                phase = phase,
                messagesCount = messagesCount,
                totalOpenDurationMs = elapsed,
                extra = mapOf(extraKey to elapsed)
        )
    }

    fun clear(traceId: String) {
        openStartMs.remove(traceId)
    }
}
