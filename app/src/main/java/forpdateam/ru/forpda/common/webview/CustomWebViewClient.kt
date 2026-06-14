package forpdateam.ru.forpda.common.webview

import android.annotation.TargetApi
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.net.http.SslError
import android.os.Build
import android.os.SystemClock
import android.util.Base64
import android.util.LruCache
import forpdateam.ru.forpda.BuildConfig
import timber.log.Timber
import android.webkit.SslErrorHandler
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import forpdateam.ru.forpda.common.ForPdaCoil
import forpdateam.ru.forpda.common.SiteUrls
import forpdateam.ru.forpda.model.repository.avatar.AvatarRepository
import forpdateam.ru.forpda.presentation.ILinkHandler
import forpdateam.ru.forpda.presentation.ISystemLinkHandler
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.net.URLDecoder
import java.util.regex.Pattern
/**
 * Created by radiationx on 12.09.17.
 */
open class CustomWebViewClient(
    private val avatarRepository: AvatarRepository? = null,
    private val linkHandler: ILinkHandler? = null,
    private val systemLinkHandler: ISystemLinkHandler? = null
) : WebViewClient() {

    companion object {
        private const val LOG_TAG = "CustomWebViewClient"
        private const val TYPE_NICK = "nick"
        private const val TYPE_URL = "url"
        private const val AVATAR_RESPONSE_CACHE_BYTES = 2 * 1024 * 1024
        private const val AVATAR_RESPONSE_MAX_ENTRY_BYTES = 256 * 1024
        private const val AVATAR_INTERCEPT_SLOW_LOG_MS = 16L
        private data class AvatarCachedResponse(val mimeType: String, val bytes: ByteArray) {
            override fun equals(other: Any?): Boolean {
                if (this === other) return true
                if (other !is AvatarCachedResponse) return false
                return mimeType == other.mimeType && bytes.contentEquals(other.bytes)
            }

            override fun hashCode(): Int {
                var result = mimeType.hashCode()
                result = 31 * result + bytes.contentHashCode()
                return result
            }
        }
        private val avatarResponseCache = object : LruCache<String, AvatarCachedResponse>(AVATAR_RESPONSE_CACHE_BYTES) {
            override fun sizeOf(key: String, value: AvatarCachedResponse): Int = value.bytes.size
        }
        private val DOWNLOAD_PATTERN: Pattern = Pattern.compile(
            ".*\\.(apk|zip|rar|7z|tar|gz|bz2|pdf|doc|docx|xls|xlsx|ppt|pptx|txt|csv|mp3|mp4|avi|mkv|mov|wmv|flv|wav|ogg|exe|dmg|iso|img|torrent|bin|patch)(\\?.*)?\$",
            Pattern.CASE_INSENSITIVE
        )
        private val P4PDA_DOWNLOAD_PATTERN: Pattern = Pattern.compile(
            "https?://.*4pda\\.to/.*(?:dl/|download|attach|upload)[^\\.]*(?:\\.(?!jpg|jpeg|png|gif|bmp|webp)[a-z0-9]+)?\$",
            Pattern.CASE_INSENSITIVE
        )
    }

    private val cachePattern: Pattern = Pattern.compile("app_cache:avatars\\?(url|nick)=([\\s\\S]*)")

    private fun initDependencies(context: Context) {
        // Dependencies now provided via constructor; no lazy init needed
    }

    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
        return super.shouldInterceptRequest(view, request)
    }

    override fun shouldInterceptRequest(view: WebView, url: String): WebResourceResponse? {
        initDependencies(view.context)
        val matcher = cachePattern.matcher(url)
        if (matcher.find()) {
            try {
                val startedAt = SystemClock.elapsedRealtime()
                val type = matcher.group(1).orEmpty()
                var value = matcher.group(2).orEmpty()
                value = URLDecoder.decode(value, "UTF-8")

                val resolveStartedAt = SystemClock.elapsedRealtime()
                val avatarUrl = when (type) {
                    TYPE_NICK -> avatarRepository?.getAvatarForWebViewInterceptSync(value)
                    TYPE_URL -> value
                    else -> null
                }
                val resolvedAt = SystemClock.elapsedRealtime()

                val resolvedAvatarUrl = avatarUrl ?: run {
                    logAvatarInterceptTiming(
                        type = type,
                        resolveMs = resolvedAt - resolveStartedAt,
                        loadMs = 0,
                        encodeMs = 0,
                        totalMs = resolvedAt - startedAt,
                        bytes = 0,
                        cacheHit = false,
                        skipped = true,
                        source = "unresolved"
                    )
                    return super.shouldInterceptRequest(view, url)
                }
                avatarResponseCache.get(resolvedAvatarUrl)?.let { cached ->
                    logAvatarInterceptTiming(
                        type = type,
                        resolveMs = resolvedAt - resolveStartedAt,
                        loadMs = 0,
                        encodeMs = 0,
                        totalMs = SystemClock.elapsedRealtime() - startedAt,
                        bytes = cached.bytes.size,
                        cacheHit = true,
                        skipped = false,
                        source = "responseCache"
                    )
                    return avatarResponse(cached.bytes, cached.mimeType)
                }
                val cachedImageBytes = ForPdaCoil.loadCachedImageBytesSync(view.context, resolvedAvatarUrl)
                val loadedAt = SystemClock.elapsedRealtime()
                if (cachedImageBytes != null) {
                    logAvatarInterceptTiming(
                        type = type,
                        resolveMs = resolvedAt - resolveStartedAt,
                        loadMs = loadedAt - resolvedAt,
                        encodeMs = 0,
                        totalMs = loadedAt - startedAt,
                        bytes = cachedImageBytes.bytes.size,
                        cacheHit = false,
                        skipped = false,
                        source = "diskBytes"
                    )
                    if (cachedImageBytes.bytes.size <= AVATAR_RESPONSE_MAX_ENTRY_BYTES) {
                        avatarResponseCache.put(
                            resolvedAvatarUrl,
                            AvatarCachedResponse(cachedImageBytes.mimeType, cachedImageBytes.bytes)
                        )
                    }
                    return avatarResponse(cachedImageBytes.bytes, cachedImageBytes.mimeType)
                }
                val bitmap = ForPdaCoil.loadBitmapSync(view.context, resolvedAvatarUrl, allowNetwork = false)
                val bitmapLoadedAt = SystemClock.elapsedRealtime()
                val avatarBytes = convertToPngBytes(bitmap)
                val encodedAt = SystemClock.elapsedRealtime()
                logAvatarInterceptTiming(
                    type = type,
                    resolveMs = resolvedAt - resolveStartedAt,
                    loadMs = bitmapLoadedAt - loadedAt,
                    encodeMs = encodedAt - bitmapLoadedAt,
                    totalMs = encodedAt - startedAt,
                    bytes = avatarBytes?.size ?: 0,
                    cacheHit = false,
                    skipped = avatarBytes == null,
                    source = "bitmapEncode"
                )
                if (avatarBytes == null) {
                    return super.shouldInterceptRequest(view, url)
                }
                if (avatarBytes.size <= AVATAR_RESPONSE_MAX_ENTRY_BYTES) {
                    avatarResponseCache.put(
                        resolvedAvatarUrl,
                        AvatarCachedResponse("image/png", avatarBytes)
                    )
                }
                return avatarResponse(avatarBytes, "image/png")
            } catch (e: Exception) {
                Timber.e(e, "Avatar intercept error")
                return super.shouldInterceptRequest(view, url)
            }
        }
        return super.shouldInterceptRequest(view, url)
    }

    private fun avatarResponse(bytes: ByteArray, mimeType: String): WebResourceResponse {
        return WebResourceResponse(
            mimeType,
            null,
            ByteArrayInputStream(bytes)
        )
    }

    private fun logAvatarInterceptTiming(
        type: String,
        resolveMs: Long,
        loadMs: Long,
        encodeMs: Long,
        totalMs: Long,
        bytes: Int,
        cacheHit: Boolean,
        skipped: Boolean,
        source: String
    ) {
        if (totalMs < AVATAR_INTERCEPT_SLOW_LOG_MS && encodeMs == 0L) return
        if (!BuildConfig.DEBUG) return
        Timber.d(
            "$LOG_TAG avatarIntercept type=%s source=%s resolveMs=%d loadMs=%d encodeMs=%d totalMs=%d bytes=%d cacheHit=%s skipped=%s",
            type,
            source,
            resolveMs,
            loadMs,
            encodeMs,
            totalMs,
            bytes,
            cacheHit,
            skipped
        )
    }

    fun convert(base64Str: String): Bitmap {
        val decodedBytes = Base64.decode(
            base64Str.substring(base64Str.indexOf(",") + 1),
            Base64.DEFAULT
        )
        return BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
    }

    fun convert(bitmap: Bitmap?): String? {
        return convertToPngBytes(bitmap)?.let { Base64.encodeToString(it, Base64.NO_WRAP) }
    }

    fun convertToPngBytes(bitmap: Bitmap?): ByteArray? {
        if (bitmap == null) return null
        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
        return outputStream.toByteArray()
    }

    override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
        initDependencies(view.context)
        return handleUri(view, Uri.parse(url))
    }

    @TargetApi(Build.VERSION_CODES.N)
    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
        initDependencies(view.context)
        return handleUri(view, request.url)
    }

    open fun handleUri(view: WebView, uri: Uri): Boolean {
        return when (val decision = UrlPolicy.classify(uri.toString())) {
            UrlDecision.Blocked -> {
                Timber.w("Blocked unsafe WebView URL")
                true
            }
            is UrlDecision.External -> {
                systemLinkHandler?.handle(decision.normalizedUrl)
                true
            }
            is UrlDecision.Internal -> {
                val safeUri = Uri.parse(decision.normalizedUrl)
                if (isDownloadableFile(safeUri)) {
                    downloadFile(view.context, safeUri)
                    true
                } else {
                    false
                }
            }
        }
    }

    protected fun shouldOpenExternally(uri: Uri): Boolean {
        return UrlPolicy.classify(uri.toString()) is UrlDecision.External
    }

    private fun isDownloadableFile(uri: Uri): Boolean {
        val url = uri.toString()
        if (cachePattern.matcher(url).find()) return false
        if (!SiteUrls.isSiteUri(uri)) return false
        val lowerUrl = url.lowercase()
        if (lowerUrl.endsWith(".jpg") || lowerUrl.endsWith(".jpeg") ||
            lowerUrl.endsWith(".png") || lowerUrl.endsWith(".gif") ||
            lowerUrl.endsWith(".bmp") || lowerUrl.endsWith(".webp")) return false
        val hasExtension = DOWNLOAD_PATTERN.matcher(url).matches()
        val is4pdaDownload = P4PDA_DOWNLOAD_PATTERN.matcher(url).matches()
        return hasExtension || is4pdaDownload
    }

    private fun downloadFile(context: Context, uri: Uri) {
        systemLinkHandler!!.handleDownload(uri.toString(), null, context)
    }

    override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
        super.onReceivedSslError(view, handler, error)
    }

    @TargetApi(Build.VERSION_CODES.M)
    override fun onReceivedError(
            view: WebView,
            request: WebResourceRequest,
            error: WebResourceError
    ) {
        if (request.isForMainFrame) {
            onMainFrameLoadError(
                    view,
                    request,
                    error.errorCode,
                    error.description?.toString()
            )
        }
        super.onReceivedError(view, request, error)
    }

    @Suppress("DEPRECATION")
    override fun onReceivedError(
            view: WebView,
            errorCode: Int,
            description: String?,
            failingUrl: String?
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            onMainFrameLoadError(view, null, errorCode, description)
        }
        super.onReceivedError(view, errorCode, description, failingUrl)
    }

    @TargetApi(Build.VERSION_CODES.M)
    override fun onReceivedHttpError(
            view: WebView,
            request: WebResourceRequest,
            errorResponse: WebResourceResponse
    ) {
        if (request.isForMainFrame) {
            onMainFrameHttpError(view, request, errorResponse.statusCode)
        }
        super.onReceivedHttpError(view, request, errorResponse)
    }

    /** Main document failed to load (network/DNS/SSL, etc.). Subresources are ignored. */
    protected open fun onMainFrameLoadError(
            view: WebView,
            request: WebResourceRequest?,
            errorCode: Int,
            description: String?
    ) {
    }

    /** HTTP 4xx/5xx on the main document. Subresources are ignored. */
    protected open fun onMainFrameHttpError(
            view: WebView,
            request: WebResourceRequest?,
            statusCode: Int
    ) {
    }
}
