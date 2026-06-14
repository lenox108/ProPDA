package forpdateam.ru.forpda.presentation.articles.detail

import android.os.SystemClock
import forpdateam.ru.forpda.diagnostic.FpdaDebugLog

/**
 * Debug-only structured log for tracing article open pipeline.
 */
object ArticleOpenTrace {

    fun nowMs(): Long = SystemClock.elapsedRealtime()

    fun log(
            articleId: Int,
            requestId: Int,
            generation: Int,
            phase: String,
            url: String? = null,
            htmlLen: Int? = null,
            mappedHtmlLen: Int? = null,
            elapsedMs: Long? = null,
            reason: String? = null,
            extra: Map<String, Any?> = emptyMap()
    ) {
        val fields = linkedMapOf<String, Any?>(
                "articleId" to articleId,
                "requestId" to requestId,
                "generation" to generation,
                "phase" to phase,
                "url" to FpdaDebugLog.sanitizeUrl(url),
                "htmlLen" to htmlLen,
                "mappedHtmlLen" to mappedHtmlLen,
                "elapsedMs" to elapsedMs,
                "reason" to reason
        )
        fields.putAll(extra)
        val tag = when {
            phase.startsWith("webview_render") ||
                    phase.startsWith("render_") ||
                    phase.startsWith("webview_") -> FpdaDebugLog.TAG_ARTICLE_RENDER
            phase.startsWith("parse_") -> FpdaDebugLog.TAG_ARTICLE_PARSE
            else -> FpdaDebugLog.TAG_ARTICLE_OPEN
        }
        if (phase == "rejected_unrenderable" || phase == "EMPTY_SUCCESS_REJECTED") {
            FpdaDebugLog.warn(tag, phase, fields)
        } else {
            FpdaDebugLog.log(tag, phase, fields)
        }
    }

    fun emptySuccessRejected(
            articleId: Int,
            requestId: Int,
            generation: Int,
            htmlLen: Int? = null,
            mappedHtmlLen: Int? = null,
            reason: String,
            extra: Map<String, Any?> = emptyMap()
    ) {
        log(
                articleId = articleId,
                requestId = requestId,
                generation = generation,
                phase = "EMPTY_SUCCESS_REJECTED",
                htmlLen = htmlLen,
                mappedHtmlLen = mappedHtmlLen,
                reason = reason,
                extra = extra
        )
    }
}
