package forpdateam.ru.forpda.presentation.articles.detail

import android.os.SystemClock
import forpdateam.ru.forpda.diagnostic.FpdaDebugLog
import forpdateam.ru.forpda.model.data.remote.api.news.ArticleHtmlValidator

/**
 * Per-open trace with timings for debug logcat (FPDA_ARTICLE_OPEN / PARSE / CACHE / WEBVIEW).
 */
class ArticleOpenSession(
        val traceId: String = FpdaDebugLog.newTraceId(),
        val articleId: Int,
        val requestedUrl: String?,
        val sourceScreen: String = "news_list",
        val requestId: Int,
        val generation: Int
) {
    private val startedAtMs: Long = SystemClock.elapsedRealtime()

    var cacheHit: Boolean = false
    var cacheValid: Boolean = false
    var cacheRejectedReason: String? = null

    var networkStartMs: Long = 0L
    var networkEndMs: Long = 0L
    var httpStatus: Int? = null
    var responseSizeBytes: Int? = null
    var responseLooksLikeArticle: Boolean? = null
    var responseLooksLikeLogin: Boolean? = null
    var responseLooksLikeErrorPage: Boolean? = null

    var parseStartMs: Long = 0L
    var parseEndMs: Long = 0L
    var sanitizerDurationMs: Long? = null
    var articleRootFound: Boolean? = null
    var articleBlocksCount: Int? = null
    var hasTitle: Boolean? = null
    var hasHeroImage: Boolean? = null
    var hasLeadParagraph: Boolean? = null
    var imageBlocksCount: Int? = null
    var videoBlocksCount: Int? = null
    var commentsParsedEagerly: Boolean = false
    /** True only if comment load started before first article content became visible. */
    var commentsLoadedBeforeFirstRender: Boolean = false
    var commentsCount: Int? = null
    var relatedParsedEagerly: Boolean = false
    var parserSelector: String? = null

    var templateBuildDurationMs: Long? = null
    var generatedHtmlSizeBytes: Int? = null

    var webViewLoadStartMs: Long? = null
    var webViewFinishMs: Long? = null
    var webViewError: String? = null
    var renderGenerationId: Int? = null
    var jsReady: Boolean? = null
    var firstContentVisibleMs: Long? = null
    var finalUiState: String? = null

    var deferredExtrasStartMs: Long = 0L
    var deferredExtrasEndMs: Long = 0L
    var deferredExtrasFullReload: Boolean? = null
    var deferredExtrasPatchApplied: Boolean = false
    var webViewBlankProbeTextLen: Int? = null
    var webViewBlankRetryCount: Int = 0

    fun markNetworkStart() {
        networkStartMs = elapsed()
    }

    fun markNetworkEnd(
            status: Int?,
            bodyBytes: Int,
            rawHtml: String
    ) {
        networkEndMs = elapsed()
        httpStatus = status
        responseSizeBytes = bodyBytes
        val kind = ArticleHtmlValidator.classifyRawHtml(rawHtml)
        responseLooksLikeArticle = kind == ArticleHtmlValidator.PageKind.ARTICLE
        responseLooksLikeLogin = kind == ArticleHtmlValidator.PageKind.LOGIN
        responseLooksLikeErrorPage = kind == ArticleHtmlValidator.PageKind.ERROR ||
                kind == ArticleHtmlValidator.PageKind.CAPTCHA
    }

    fun markParseStart() {
        parseStartMs = elapsed()
    }

    fun markParseEnd(
            selector: String?,
            metrics: ArticleHtmlValidator.BodyMetrics,
            commentsEager: Boolean,
            relatedEager: Boolean,
            commentsCountHint: Int
    ) {
        parseEndMs = elapsed()
        parserSelector = selector
        articleRootFound = metrics.articleRootFound
        articleBlocksCount = metrics.articleBlocksCount
        hasTitle = metrics.hasTitle
        hasHeroImage = metrics.hasHeroImage
        hasLeadParagraph = metrics.hasLeadParagraph
        imageBlocksCount = metrics.imageBlocksCount
        videoBlocksCount = metrics.videoBlocksCount
        commentsParsedEagerly = commentsEager
        relatedParsedEagerly = relatedEager
        commentsCount = commentsCountHint
    }

    fun markTemplateDone(durationMs: Long, htmlBytes: Int) {
        templateBuildDurationMs = durationMs
        generatedHtmlSizeBytes = htmlBytes
    }

    fun markWebViewLoadStart(renderGenerationId: Int) {
        this.renderGenerationId = renderGenerationId
        webViewLoadStartMs = elapsed()
    }

    fun markWebViewFinished(source: String, jsReady: Boolean = true) {
        webViewFinishMs = elapsed()
        this.jsReady = jsReady
        if (firstContentVisibleMs == null) {
            firstContentVisibleMs = webViewFinishMs
        }
        ArticleOpenTrace.log(
                articleId = articleId,
                requestId = requestId,
                generation = generation,
                phase = "webview_$source",
                extra = sessionFields() + mapOf("renderGenerationId" to renderGenerationId)
        )
    }

    fun markWebViewError(reason: String) {
        webViewError = reason
    }

    fun markDeferredExtrasStart() {
        deferredExtrasStartMs = elapsed()
    }

    fun markDeferredExtrasComplete(fullReload: Boolean, patchApplied: Boolean = false) {
        deferredExtrasEndMs = elapsed()
        deferredExtrasFullReload = fullReload
        deferredExtrasPatchApplied = patchApplied
    }

    fun markWebViewBlankProbe(textLen: Int, retryCount: Int) {
        webViewBlankProbeTextLen = textLen
        webViewBlankRetryCount = retryCount
    }

    fun markFinalUiState(state: String) {
        finalUiState = state
        emitSummary()
    }

    fun emitPhase(phase: String, reason: String? = null, extra: Map<String, Any?> = emptyMap()) {
        ArticleOpenTrace.log(
                articleId = articleId,
                requestId = requestId,
                generation = generation,
                phase = phase,
                url = requestedUrl,
                reason = reason,
                extra = sessionFields() + extra + mapOf(
                        "traceId" to traceId,
                        "sourceScreen" to sourceScreen
                )
        )
    }

    private fun emitSummary() {
        val fields = sessionFields() + mapOf(
                "traceId" to traceId,
                "sourceScreen" to sourceScreen,
                "requestedUrl" to FpdaDebugLog.sanitizeUrl(requestedUrl),
                "cacheHit" to cacheHit,
                "cacheValid" to cacheValid,
                "cacheRejectedReason" to cacheRejectedReason,
                "networkStartMs" to networkStartMs,
                "networkEndMs" to networkEndMs,
                "networkDurationMs" to durationOrNull(networkStartMs, networkEndMs),
                "httpStatus" to httpStatus,
                "responseSizeBytes" to responseSizeBytes,
                "responseLooksLikeArticle" to responseLooksLikeArticle,
                "responseLooksLikeLogin" to responseLooksLikeLogin,
                "responseLooksLikeErrorPage" to responseLooksLikeErrorPage,
                "parseStartMs" to parseStartMs,
                "parseEndMs" to parseEndMs,
                "parseDurationMs" to durationOrNull(parseStartMs, parseEndMs),
                "sanitizerDurationMs" to sanitizerDurationMs,
                "articleRootFound" to articleRootFound,
                "articleBlocksCount" to articleBlocksCount,
                "hasTitle" to hasTitle,
                "hasHeroImage" to hasHeroImage,
                "hasLeadParagraph" to hasLeadParagraph,
                "imageBlocksCount" to imageBlocksCount,
                "videoBlocksCount" to videoBlocksCount,
                "commentsParsedEagerly" to commentsParsedEagerly,
                "commentsLoadedBeforeFirstRender" to commentsLoadedBeforeFirstRender,
                "commentsCount" to commentsCount,
                "relatedParsedEagerly" to relatedParsedEagerly,
                "templateBuildDurationMs" to templateBuildDurationMs,
                "generatedHtmlSizeBytes" to generatedHtmlSizeBytes,
                "webViewLoadStartMs" to webViewLoadStartMs,
                "webViewFinishMs" to webViewFinishMs,
                "webViewError" to webViewError,
                "jsReady" to jsReady,
                "firstContentVisibleMs" to firstContentVisibleMs,
                "finalUiState" to finalUiState,
                "totalOpenDurationMs" to elapsed(),
                "parserSelector" to parserSelector,
                "deferredExtrasStartMs" to deferredExtrasStartMs.takeIf { it > 0L },
                "deferredExtrasEndMs" to deferredExtrasEndMs.takeIf { it > 0L },
                "deferredExtrasDurationMs" to durationOrNull(deferredExtrasStartMs, deferredExtrasEndMs),
                "deferredExtrasFullReload" to deferredExtrasFullReload,
                "deferredExtrasPatchApplied" to deferredExtrasPatchApplied,
                "webViewBlankProbeTextLen" to webViewBlankProbeTextLen,
                "webViewBlankRetryCount" to webViewBlankRetryCount
        )
        FpdaDebugLog.log(FpdaDebugLog.TAG_ARTICLE_OPEN, "summary", fields)
    }

    private fun sessionFields(): Map<String, Any?> = mapOf(
            "renderGenerationId" to renderGenerationId,
            "mappedHtmlLen" to generatedHtmlSizeBytes
    )

    private fun elapsed(): Long = SystemClock.elapsedRealtime() - startedAtMs

    private fun durationOrNull(start: Long, end: Long): Long? {
        if (start <= 0L || end <= 0L || end < start) return null
        return end - start
    }
}
