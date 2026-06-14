package forpdateam.ru.forpda.presentation.qms.chat

import android.os.SystemClock
import forpdateam.ru.forpda.diagnostic.FpdaDebugLog
import forpdateam.ru.forpda.model.data.remote.api.qms.QmsHtmlValidator

object QmsOpenTrace {

    fun nowMs(): Long = SystemClock.elapsedRealtime()

    fun logOpen(
            traceId: String,
            dialogId: Int,
            userId: Int,
            requestId: Int,
            phase: String,
            requestedUrl: String? = null,
            sourceScreen: String? = null,
            cacheHit: Boolean? = null,
            cacheValid: Boolean? = null,
            networkStartMs: Long? = null,
            networkEndMs: Long? = null,
            httpStatus: Int? = null,
            redirectUrl: String? = null,
            finalUrl: String? = null,
            responseSizeBytes: Int? = null,
            responseLooksLikeQms: Boolean? = null,
            responseLooksLikeLogin: Boolean? = null,
            responseLooksLikeCaptcha: Boolean? = null,
            responseLooksLikeErrorPage: Boolean? = null,
            parseStartMs: Long? = null,
            parseEndMs: Long? = null,
            messagesCount: Int? = null,
            hasEditorForm: Boolean? = null,
            hasPagination: Boolean? = null,
            parserRootFound: Boolean? = null,
            parserErrorClass: String? = null,
            parserErrorMessage: String? = null,
            errorMappedToUserMessage: String? = null,
            errorReason: String? = null,
            finalUiState: String? = null,
            totalOpenDurationMs: Long? = null,
            extra: Map<String, Any?> = emptyMap()
    ) {
        val networkDurationMs = if (networkStartMs != null && networkEndMs != null) {
            networkEndMs - networkStartMs
        } else {
            null
        }
        val parseDurationMs = if (parseStartMs != null && parseEndMs != null) {
            parseEndMs - parseStartMs
        } else {
            null
        }
        val tag = when {
            phase.startsWith("network_") || phase.startsWith("http_") -> FpdaDebugLog.TAG_QMS_NETWORK
            phase.startsWith("parse_") -> FpdaDebugLog.TAG_QMS_PARSE
            phase.startsWith("cache_") -> FpdaDebugLog.TAG_QMS_CACHE
            else -> FpdaDebugLog.TAG_QMS_OPEN
        }
        val fields = linkedMapOf<String, Any?>(
                "traceId" to traceId,
                "dialogId" to dialogId,
                "userId" to userId,
                "requestId" to requestId,
                "phase" to phase,
                "requestedUrlSanitized" to FpdaDebugLog.sanitizeUrl(requestedUrl),
                "sourceScreen" to sourceScreen,
                "cacheHit" to cacheHit,
                "cacheValid" to cacheValid,
                "networkStartMs" to networkStartMs,
                "networkEndMs" to networkEndMs,
                "networkDurationMs" to networkDurationMs,
                "httpStatus" to httpStatus,
                "redirectUrlSanitized" to FpdaDebugLog.sanitizeUrl(redirectUrl),
                "finalUrlSanitized" to FpdaDebugLog.sanitizeUrl(finalUrl ?: redirectUrl),
                "responseSizeBytes" to responseSizeBytes,
                "responseLooksLikeQms" to responseLooksLikeQms,
                "responseLooksLikeLogin" to responseLooksLikeLogin,
                "responseLooksLikeCaptcha" to responseLooksLikeCaptcha,
                "responseLooksLikeErrorPage" to responseLooksLikeErrorPage,
                "parseStartMs" to parseStartMs,
                "parseEndMs" to parseEndMs,
                "parseDurationMs" to parseDurationMs,
                "messagesCount" to messagesCount,
                "hasEditorForm" to hasEditorForm,
                "hasPagination" to hasPagination,
                "parserRootFound" to parserRootFound,
                "parserErrorClass" to parserErrorClass,
                "parserErrorMessage" to parserErrorMessage,
                "errorMappedToUserMessage" to errorMappedToUserMessage,
                "errorReason" to errorReason,
                "finalUiState" to finalUiState,
                "totalOpenDurationMs" to totalOpenDurationMs
        )
        fields.putAll(extra)
        if (phase.contains("rejected", ignoreCase = true) ||
                phase.contains("error", ignoreCase = true) ||
                phase == "stale_ignored"
        ) {
            FpdaDebugLog.warn(tag, phase, fields)
        } else {
            FpdaDebugLog.log(tag, phase, fields)
        }
    }

    fun logStateTransition(
            traceId: String,
            dialogId: Int,
            userId: Int,
            requestId: Int,
            previousState: String,
            nextState: String,
            reason: String,
            staleResultIgnored: Boolean = false
    ) {
        FpdaDebugLog.log(
                FpdaDebugLog.TAG_QMS_STATE,
                "transition",
                mapOf(
                        "traceId" to traceId,
                        "dialogId" to dialogId,
                        "userId" to userId,
                        "requestId" to requestId,
                        "previousState" to previousState,
                        "nextState" to nextState,
                        "reason" to reason,
                        "staleResultIgnored" to staleResultIgnored
                )
        )
        if (staleResultIgnored) {
            FpdaDebugLog.log(
                    FpdaDebugLog.TAG_STATE_RACE,
                    "stale_ignored",
                    mapOf(
                            "domain" to "qms_thread",
                            "traceId" to traceId,
                            "requestId" to requestId,
                            "dialogId" to dialogId,
                            "reason" to reason
                    )
            )
        }
    }

    fun classifyFlags(html: String, httpCode: Int): Map<String, Boolean> {
        val kind = QmsHtmlValidator.classify(httpCode, html)
        return mapOf(
                "responseLooksLikeQms" to (kind == QmsHtmlValidator.PageKind.QMS_THREAD ||
                        kind == QmsHtmlValidator.PageKind.QMS_EMPTY_THREAD),
                "responseLooksLikeLogin" to (kind == QmsHtmlValidator.PageKind.LOGIN),
                "responseLooksLikeCaptcha" to (kind == QmsHtmlValidator.PageKind.CAPTCHA),
                "responseLooksLikeErrorPage" to (kind == QmsHtmlValidator.PageKind.ERROR)
        )
    }
}
