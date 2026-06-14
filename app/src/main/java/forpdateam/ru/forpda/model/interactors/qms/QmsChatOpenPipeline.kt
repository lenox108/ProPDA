package forpdateam.ru.forpda.model.interactors.qms

import forpdateam.ru.forpda.client.GoogleCaptchaException
import forpdateam.ru.forpda.client.OkHttpResponseException
import forpdateam.ru.forpda.client.OnlyShowException
import forpdateam.ru.forpda.entity.remote.qms.QmsChatModel
import forpdateam.ru.forpda.model.data.remote.api.NetworkResponse
import forpdateam.ru.forpda.model.data.remote.api.qms.QmsApi
import forpdateam.ru.forpda.model.data.remote.api.qms.QmsHtmlValidator
import forpdateam.ru.forpda.diagnostic.FpdaDebugLog
import forpdateam.ru.forpda.BuildConfig
import forpdateam.ru.forpda.diagnostic.QmsOpenLog
import forpdateam.ru.forpda.model.data.remote.api.qms.QmsResponseBody
import forpdateam.ru.forpda.presentation.qms.chat.QmsLoadErrorKind
import forpdateam.ru.forpda.presentation.qms.chat.QmsOpenTrace
import forpdateam.ru.forpda.presentation.qms.chat.toOpenErrorReason
import android.os.NetworkOnMainThreadException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import java.io.IOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeoutException

sealed class QmsChatLoadOutcome {
    data class Content(
            val chat: QmsChatModel,
            val fromCache: Boolean,
            val pageKind: QmsHtmlValidator.PageKind
    ) : QmsChatLoadOutcome()

    data class Empty(
            val chat: QmsChatModel,
            val fromCache: Boolean
    ) : QmsChatLoadOutcome()

    data class Failure(
            val kind: QmsLoadErrorKind,
            val message: String,
            val canRetry: Boolean = true
    ) : QmsChatLoadOutcome()
}

class QmsChatOpenPipeline(
        private val qmsApi: QmsApi
) {

    suspend fun loadChat(
            userId: Int,
            themeId: Int,
            traceId: String,
            requestId: Int,
            bypassCache: Boolean,
            sourceScreen: String = "qms_chat"
    ): QmsChatLoadOutcome {
        val openStart = QmsOpenTrace.nowMs()
        val url = chatUrl(userId, themeId)
        QmsOpenTrace.logOpen(
                traceId = traceId,
                dialogId = themeId,
                userId = userId,
                requestId = requestId,
                phase = "open_start",
                requestedUrl = url,
                sourceScreen = sourceScreen
        )

        if (!bypassCache) {
            val cached = QmsChatMemoryCache.get(userId, themeId)
            if (cached != null) {
                val cacheValid = cached.messageCount > 0 ||
                        cached.pageKind == QmsHtmlValidator.PageKind.QMS_EMPTY_THREAD
                QmsOpenTrace.logOpen(
                        traceId = traceId,
                        dialogId = themeId,
                        userId = userId,
                        requestId = requestId,
                        phase = "cache_hit",
                        cacheHit = true,
                        cacheValid = cacheValid,
                        messagesCount = cached.messageCount,
                        finalUiState = if (cacheValid) "Content(cached)" else "InvalidCache"
                )
                if (cacheValid) {
                    return if (cached.messageCount > 0) {
                        QmsChatLoadOutcome.Content(cached.chat, fromCache = true, pageKind = cached.pageKind)
                    } else {
                        QmsChatLoadOutcome.Empty(cached.chat, fromCache = true)
                    }
                }
                QmsChatMemoryCache.invalidate(userId, themeId)
                QmsOpenTrace.logOpen(
                        traceId = traceId,
                        dialogId = themeId,
                        userId = userId,
                        requestId = requestId,
                        phase = "cache_invalidated",
                        cacheHit = true,
                        cacheValid = false,
                        messagesCount = cached.messageCount
                )
            }
            QmsOpenTrace.logOpen(
                    traceId = traceId,
                    dialogId = themeId,
                    userId = userId,
                    requestId = requestId,
                    phase = "cache_miss",
                    cacheHit = false
            )
        } else {
            QmsChatMemoryCache.invalidate(userId, themeId)
            QmsOpenTrace.logOpen(
                    traceId = traceId,
                    dialogId = themeId,
                    userId = userId,
                    requestId = requestId,
                    phase = "cache_bypass",
                    cacheHit = false
            )
        }

        val networkStart = QmsOpenTrace.nowMs()
        val fetch = try {
            fetchWithRetry(userId, themeId, traceId, requestId, url, networkStart)
        } catch (e: Throwable) {
            if (isRequestCancelled(e)) throw e
            logNetworkFailure(traceId, themeId, userId, requestId, url, networkStart, e, httpStatus = null)
            val classified = classifyFetchFailure(e)
            return finishFailure(
                    traceId, themeId, userId, requestId, openStart,
                    classified.kind,
                    classified.message,
                    canRetry = classified.canRetry,
                    requestedUrl = url
            )
        }
        val response = fetch.response
        val networkEnd = QmsOpenTrace.nowMs()
        val body = QmsResponseBody.normalize(response.body)
        if (body !== response.body) {
            QmsOpenTrace.logOpen(
                    traceId = traceId,
                    dialogId = themeId,
                    userId = userId,
                    requestId = requestId,
                    phase = "json_unwrapped",
                    responseSizeBytes = body.length,
                    extra = mapOf("rawSizeBytes" to response.body.length)
            )
        }
        val flags = QmsOpenTrace.classifyFlags(body, response.code)
        QmsOpenTrace.logOpen(
                traceId = traceId,
                dialogId = themeId,
                userId = userId,
                requestId = requestId,
                phase = if (fetch.retried) "network_retry_ok" else "network_ok",
                requestedUrl = url,
                networkStartMs = networkStart,
                networkEndMs = networkEnd,
                httpStatus = response.code,
                redirectUrl = response.redirect,
                finalUrl = response.redirect ?: url,
                responseSizeBytes = response.body.length,
                responseLooksLikeQms = flags["responseLooksLikeQms"],
                responseLooksLikeLogin = flags["responseLooksLikeLogin"],
                responseLooksLikeCaptcha = flags["responseLooksLikeCaptcha"],
                responseLooksLikeErrorPage = flags["responseLooksLikeErrorPage"],
                extra = mapOf("autoRetried" to fetch.retried)
        )

        if (response.code !in 200..299) {
            return finishFailure(
                    traceId, themeId, userId, requestId, openStart,
                    QmsLoadErrorKind.SERVER,
                    "HTTP ${response.code}",
                    canRetry = response.code >= 500 || response.code == 429,
                    httpStatus = response.code
            )
        }

        val pageKind = QmsHtmlValidator.classify(response.code, body)
        val signals = QmsHtmlValidator.measureThread(body)
        when (pageKind) {
            QmsHtmlValidator.PageKind.LOGIN ->
                    return finishFailure(
                            traceId, themeId, userId, requestId, openStart,
                            QmsLoadErrorKind.SESSION,
                            "session_expired",
                            canRetry = false,
                            httpStatus = response.code
                    )
            QmsHtmlValidator.PageKind.CAPTCHA ->
                    return finishFailure(
                            traceId, themeId, userId, requestId, openStart,
                            QmsLoadErrorKind.CAPTCHA,
                            "captcha_required",
                            canRetry = false,
                            httpStatus = response.code
                    )
            QmsHtmlValidator.PageKind.ERROR,
            QmsHtmlValidator.PageKind.UNKNOWN -> {
                val pageDetail = when {
                    pageKind == QmsHtmlValidator.PageKind.ERROR -> "unexpected_page:ERROR"
                    QmsResponseBody.looksLikeJsonEnvelope(response.body) ->
                            "json_envelope_unparsed"
                    else -> "unexpected_page:UNKNOWN"
                }
                return finishFailure(
                        traceId, themeId, userId, requestId, openStart,
                        if (pageKind == QmsHtmlValidator.PageKind.ERROR) {
                            QmsLoadErrorKind.SERVER
                        } else {
                            QmsLoadErrorKind.PARSER
                        },
                        pageDetail,
                        canRetry = true,
                        parserRootFound = signals.parserRootFound,
                        httpStatus = response.code,
                        messagesCount = 0
                )
            }
            QmsHtmlValidator.PageKind.QMS_THREAD,
            QmsHtmlValidator.PageKind.QMS_EMPTY_THREAD -> Unit
        }

        val parseStart = QmsOpenTrace.nowMs()
        val parsed = try {
            if (userId == 0) {
                qmsApi.parseFetchedSystemChat(body, themeId)
            } else {
                qmsApi.parseFetchedChat(body)
            }
        } catch (e: Throwable) {
            val parseEnd = QmsOpenTrace.nowMs()
            QmsOpenTrace.logOpen(
                    traceId = traceId,
                    dialogId = themeId,
                    userId = userId,
                    requestId = requestId,
                    phase = "parse_error",
                    parseStartMs = parseStart,
                    parseEndMs = parseEnd,
                    parserErrorClass = e::class.java.simpleName,
                    parserErrorMessage = e.message?.take(120),
                    parserRootFound = signals.parserRootFound,
                    hasEditorForm = signals.hasEditorForm,
                    hasPagination = signals.hasPagination
            )
            return finishFailure(
                    traceId, themeId, userId, requestId, openStart,
                    QmsLoadErrorKind.PARSER,
                    e.message ?: "parse_failed",
                    canRetry = true,
                    parserRootFound = signals.parserRootFound,
                    httpStatus = response.code
            )
        }
        val parseEnd = QmsOpenTrace.nowMs()
        QmsOpenTrace.logOpen(
                traceId = traceId,
                dialogId = themeId,
                userId = userId,
                requestId = requestId,
                phase = "parse_ok",
                parseStartMs = parseStart,
                parseEndMs = parseEnd,
                messagesCount = parsed.messages.size,
                parserRootFound = signals.parserRootFound,
                hasEditorForm = signals.hasEditorForm,
                hasPagination = signals.hasPagination
        )

        if (pageKind == QmsHtmlValidator.PageKind.QMS_THREAD && parsed.messages.isEmpty()) {
            val mismatchDetail = when {
                signals.containerMessageMarkers > 0 -> "selector_mismatch_container"
                signals.messageMarkers > 0 -> "selector_mismatch_legacy"
                else -> "selector_mismatch"
            }
            QmsOpenTrace.logOpen(
                    traceId = traceId,
                    dialogId = themeId,
                    userId = userId,
                    requestId = requestId,
                    phase = "parse_rejected_empty",
                    messagesCount = 0,
                    parserRootFound = signals.parserRootFound,
                    hasEditorForm = signals.hasEditorForm,
                    hasPagination = signals.hasPagination,
                    parserErrorMessage = mismatchDetail,
                    extra = mapOf(
                            "containerMessageMarkers" to signals.containerMessageMarkers,
                            "legacyMessageMarkers" to signals.messageMarkers
                    )
            )
            return finishFailure(
                    traceId, themeId, userId, requestId, openStart,
                    QmsLoadErrorKind.PARSER,
                    "parser_empty_thread:$mismatchDetail",
                    canRetry = true,
                    parserRootFound = signals.parserRootFound,
                    httpStatus = response.code,
                    messagesCount = 0
            )
        }

        QmsChatMemoryCache.put(userId, themeId, parsed, pageKind)
        val totalMs = QmsOpenTrace.nowMs() - openStart
        return if (parsed.messages.isEmpty()) {
            QmsOpenTrace.logOpen(
                    traceId = traceId,
                    dialogId = themeId,
                    userId = userId,
                    requestId = requestId,
                    phase = "open_empty",
                    messagesCount = 0,
                    finalUiState = "Empty",
                    totalOpenDurationMs = totalMs
            )
            QmsChatLoadOutcome.Empty(parsed, fromCache = false)
        } else {
            QmsOpenTrace.logOpen(
                    traceId = traceId,
                    dialogId = themeId,
                    userId = userId,
                    requestId = requestId,
                    phase = "open_content",
                    messagesCount = parsed.messages.size,
                    finalUiState = "Content",
                    totalOpenDurationMs = totalMs
            )
            QmsChatLoadOutcome.Content(parsed, fromCache = false, pageKind = pageKind)
        }
    }

    private data class FetchResult(val response: NetworkResponse, val retried: Boolean)

    private suspend fun fetchWithRetry(
            userId: Int,
            themeId: Int,
            traceId: String,
            requestId: Int,
            url: String,
            networkStart: Long
    ): FetchResult {
        if (userId == 0) {
            QmsOpenLog.openStart(
                    traceId = traceId,
                    dialogId = themeId,
                    userId = userId,
                    requestId = requestId,
                    phase = "open_start_system_alerts_parallel",
                    sourceScreen = "system_alerts_parallel",
                    url = url
            )
        }
        try {
            val response = qmsApi.fetchChat(userId, themeId)
            if (shouldRetryResponse(response.code)) {
                logNetworkRetry(traceId, themeId, userId, requestId, url, networkStart, response.code, null)
                delay(400L)
                return FetchResult(qmsApi.fetchChat(userId, themeId), retried = true)
            }
            return FetchResult(response, retried = false)
        } catch (first: Throwable) {
            if (isRequestCancelled(first)) throw first
            val retryStatus = retryableHttpStatus(first)
            if (!isTransientNetwork(first) && retryStatus == null) throw first
            logNetworkRetry(traceId, themeId, userId, requestId, url, networkStart, retryStatus, first)
            delay(400L)
            val response = qmsApi.fetchChat(userId, themeId)
            return FetchResult(response, retried = true)
        }
    }

    private fun logNetworkRetry(
            traceId: String,
            themeId: Int,
            userId: Int,
            requestId: Int,
            url: String,
            networkStart: Long,
            httpStatus: Int?,
            error: Throwable?
    ) {
        QmsOpenTrace.logOpen(
                traceId = traceId,
                dialogId = themeId,
                userId = userId,
                requestId = requestId,
                phase = "network_retry",
                requestedUrl = url,
                networkStartMs = networkStart,
                networkEndMs = QmsOpenTrace.nowMs(),
                httpStatus = httpStatus,
                parserErrorClass = error?.let { it::class.java.simpleName },
                parserErrorMessage = error?.message?.take(200)
        )
    }

    private fun finishFailure(
            traceId: String,
            dialogId: Int,
            userId: Int,
            requestId: Int,
            openStart: Long,
            kind: QmsLoadErrorKind,
            message: String,
            canRetry: Boolean,
            parserRootFound: Boolean? = null,
            requestedUrl: String? = null,
            httpStatus: Int? = null,
            messagesCount: Int? = null
    ): QmsChatLoadOutcome.Failure {
        val errorReason = kind.toOpenErrorReason(message)
        val userMessageKey = userMessageKeyFor(kind)
        val url = requestedUrl ?: chatUrl(userId, dialogId)
        val userDetail = failureDetailForUser(errorReason.name, httpStatus, messagesCount, message)
        QmsOpenTrace.logOpen(
                traceId = traceId,
                dialogId = dialogId,
                userId = userId,
                requestId = requestId,
                phase = "open_error",
                requestedUrl = url,
                httpStatus = httpStatus,
                messagesCount = messagesCount,
                parserErrorMessage = message,
                parserRootFound = parserRootFound,
                errorMappedToUserMessage = userMessageKey,
                errorReason = errorReason.name,
                finalUiState = "Error($kind)",
                totalOpenDurationMs = QmsOpenTrace.nowMs() - openStart
        )
        QmsOpenLog.openError(
                traceId = traceId,
                dialogId = dialogId,
                userId = userId,
                requestId = requestId,
                phase = "open_error",
                errorReason = errorReason.name,
                kind = kind.name,
                httpStatus = httpStatus,
                messagesCount = messagesCount,
                detail = message,
                url = url
        )
        return QmsChatLoadOutcome.Failure(kind, userDetail, canRetry)
    }

    private fun failureDetailForUser(
            errorReason: String,
            httpStatus: Int?,
            messagesCount: Int?,
            message: String
    ): String {
        if (!BuildConfig.DEBUG) return ""
        val parts = buildList {
            add(errorReason)
            httpStatus?.let { add("http=$it") }
            messagesCount?.let { add("msgs=$it") }
            val tail = message.trim().take(48)
            if (tail.isNotEmpty() && !tail.equals(errorReason, ignoreCase = true)) {
                add(tail)
            }
        }
        return parts.joinToString(separator = " · ")
    }

    private fun userMessageKeyFor(kind: QmsLoadErrorKind): String = when (kind) {
        QmsLoadErrorKind.NETWORK -> "no_network"
        QmsLoadErrorKind.SESSION -> "qms_error_session"
        QmsLoadErrorKind.CAPTCHA -> "qms_error_captcha"
        QmsLoadErrorKind.SERVER -> "qms_error_server"
        QmsLoadErrorKind.PARSER -> "qms_error_parser"
        QmsLoadErrorKind.UNKNOWN -> "qms_error_unknown"
    }

    private data class ClassifiedFetchFailure(
            val kind: QmsLoadErrorKind,
            val message: String,
            val canRetry: Boolean
    )

    private fun classifyFetchFailure(error: Throwable): ClassifiedFetchFailure {
        if (isRequestCancelled(error)) {
            throw error
        }
        val root = unwrapCause(error)
        return when (root) {
            is GoogleCaptchaException ->
                    ClassifiedFetchFailure(QmsLoadErrorKind.CAPTCHA, "captcha_required", canRetry = false)
            is OkHttpResponseException -> {
                val kind = when (root.code) {
                    401, 403 -> QmsLoadErrorKind.SESSION
                    else -> QmsLoadErrorKind.SERVER
                }
                ClassifiedFetchFailure(
                        kind,
                        "HTTP ${root.code}",
                        canRetry = root.code >= 500 || root.code == 429 || root.code == 408
                )
            }
            is OnlyShowException ->
                    ClassifiedFetchFailure(
                            QmsLoadErrorKind.SESSION,
                            root.message ?: "forum_error",
                            canRetry = false
                    )
            else -> if (isNetworkOnMainThread(root)) {
                    ClassifiedFetchFailure(
                            QmsLoadErrorKind.NETWORK,
                            "network_on_main_thread",
                            canRetry = true
                    )
            } else
            {
                if (isConnectivityFailure(root)) {
                    ClassifiedFetchFailure(
                            QmsLoadErrorKind.NETWORK,
                            root.message ?: "network_error",
                            canRetry = true
                    )
                } else {
                    val detail = root.message.orEmpty()
                    ClassifiedFetchFailure(
                            QmsLoadErrorKind.UNKNOWN,
                            detail.ifBlank { "unknown:${root::class.java.simpleName}" },
                            canRetry = !detail.contains("canceled", ignoreCase = true)
                    )
                }
            }
        }
    }

    private fun isRequestCancelled(error: Throwable): Boolean {
        if (error is CancellationException) return true
        if (error is IOException && error.message.orEmpty().contains("canceled", ignoreCase = true)) {
            return true
        }
        return error.cause?.let { isRequestCancelled(it) } == true
    }

    private fun unwrapCause(error: Throwable): Throwable {
        var current = error
        while (current.cause != null && current.cause !== current) {
            current = current.cause!!
        }
        return current
    }

    private fun isNetworkOnMainThread(t: Throwable): Boolean {
        if (t is NetworkOnMainThreadException) return true
        val name = t::class.java.name
        return name == "android.os.NetworkOnMainThreadException" ||
                name.endsWith(".NetworkOnMainThreadException")
    }

    private fun isConnectivityFailure(t: Throwable): Boolean {
        if (t is OkHttpResponseException || t is GoogleCaptchaException || t is OnlyShowException) {
            return false
        }
        if (t is SocketTimeoutException || t is TimeoutException) return true
        if (t is UnknownHostException || t is ConnectException || t is NoRouteToHostException) {
            return true
        }
        if (t is IOException) {
            val msg = t.message.orEmpty().lowercase()
            return msg.contains("unable to resolve host") ||
                    msg.contains("failed to connect") ||
                    msg.contains("network is unreachable") ||
                    msg.contains("connection refused") ||
                    msg.contains("timeout") ||
                    msg.contains("timed out") ||
                    msg.contains("ehostunreach") ||
                    msg.contains("enetunreach")
        }
        return t.cause?.let { isConnectivityFailure(it) } == true
    }

    private fun isTransientNetwork(t: Throwable): Boolean = isConnectivityFailure(t)

    private fun retryableHttpStatus(error: Throwable): Int? {
        val root = unwrapCause(error)
        return (root as? OkHttpResponseException)?.code?.takeIf(::shouldRetryResponse)
    }

    private fun shouldRetryResponse(code: Int): Boolean =
            code == 429 || code >= 500

    private fun logNetworkFailure(
            traceId: String,
            dialogId: Int,
            userId: Int,
            requestId: Int,
            url: String,
            networkStart: Long,
            error: Throwable,
            httpStatus: Int?
    ) {
        QmsOpenTrace.logOpen(
                traceId = traceId,
                dialogId = dialogId,
                userId = userId,
                requestId = requestId,
                phase = "network_error",
                requestedUrl = url,
                networkStartMs = networkStart,
                networkEndMs = QmsOpenTrace.nowMs(),
                httpStatus = httpStatus,
                parserErrorClass = error::class.java.simpleName,
                parserErrorMessage = error.message?.take(200),
                extra = mapOf(
                        "errorCause" to error.cause?.let { it::class.java.simpleName },
                        "transient" to isTransientNetwork(error)
                )
        )
    }

    private fun chatUrl(userId: Int, themeId: Int) =
            "https://4pda.to/forum/index.php?act=qms&mid=$userId&t=$themeId"
}
