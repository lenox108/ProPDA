package forpdateam.ru.forpda.diagnostic

import android.util.Log
import forpdateam.ru.forpda.BuildConfig

/**
 * QMS dialog open diagnostics.
 *
 * [openError] is emitted in release builds for WebView/render failures (no message bodies).
 * Other helpers stay DEBUG-only via [FpdaDebugLog].
 *
 * Privacy: no cookies, tokens, passwords, or message bodies.
 */
object QmsOpenLog {

    const val TAG = FpdaDebugLog.TAG_QMS_OPEN

    fun openStart(
            traceId: String,
            dialogId: Int,
            userId: Int,
            requestId: Int,
            phase: String = "open_start",
            sourceScreen: String? = null,
            url: String? = null
    ) {
        if (!BuildConfig.DEBUG) return
        val parts = buildList {
            add("event=open_start")
            add("traceId=$traceId")
            add("dialogId=$dialogId")
            add("userId=$userId")
            add("requestId=$requestId")
            add("phase=$phase")
            sourceScreen?.let { add("sourceScreen=$it") }
            FpdaDebugLog.sanitizeUrl(url)?.let { add("url=$it") }
        }
        Log.v(TAG, parts.joinToString(separator = " "))
    }

    fun openError(
            traceId: String,
            dialogId: Int,
            userId: Int,
            requestId: Int,
            phase: String,
            errorReason: String? = null,
            kind: String? = null,
            httpStatus: Int? = null,
            messagesCount: Int? = null,
            detail: String? = null,
            url: String? = null
    ) {
        if (!BuildConfig.DEBUG && !Log.isLoggable(TAG, Log.WARN)) return
        val parts = buildList {
            add("event=open_error")
            add("traceId=$traceId")
            add("dialogId=$dialogId")
            add("userId=$userId")
            add("requestId=$requestId")
            add("phase=$phase")
            errorReason?.let { add("errorReason=$it") }
            kind?.let { add("kind=$it") }
            httpStatus?.let { add("httpStatus=$it") }
            messagesCount?.let { add("messagesCount=$it") }
            FpdaDebugLog.sanitizeUrl(url)?.let { add("url=$it") }
            detail?.take(160)?.let { add("detail=$it") }
        }
        Log.w(TAG, parts.joinToString(separator = " "))
    }
}
