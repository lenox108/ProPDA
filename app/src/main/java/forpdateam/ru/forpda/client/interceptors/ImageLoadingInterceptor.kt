package forpdateam.ru.forpda.client.interceptors

import forpdateam.ru.forpda.BuildConfig
import forpdateam.ru.forpda.client.OkHttpResponseException
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.Response
import okhttp3.internal.http2.StreamResetException
import timber.log.Timber
import java.io.IOException
import java.util.Locale

/**
 * Adds browser-like metadata for 4PDA image hosts used by Coil/ImageViewer.
 *
 * Signed CDN URLs must stay untouched: this interceptor only adds headers and logs sanitized metadata.
 */
class ImageLoadingInterceptor(
    private val hasCookiesForRequest: (HttpUrl) -> Boolean
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val isImageRequest = isFourPdaImageRequest(original.url)
        val request = if (isImageRequest && original.header("Referer").isNullOrBlank()) {
            original.newBuilder()
                .header("Referer", DEFAULT_REFERER)
                .build()
        } else {
            original
        }

        if (BuildConfig.DEBUG && isImageRequest) {
            Timber.tag(TAG).d(
                "imageLoadStart host=%s path=%s cookiesPresent=%s refererPresent=%s",
                original.url.host,
                original.url.encodedPath,
                hasCookiesForRequest(original.url),
                !request.header("Referer").isNullOrBlank()
            )
        }

        return try {
            val response = chain.proceed(request)
            if (BuildConfig.DEBUG && isImageRequest) {
                val finalUrl = response.request.url
                Timber.tag(TAG).d(
                    "imageLoadResponse host=%s path=%s redirectHost=%s redirectPath=%s code=%d cookiesPresent=%s refererPresent=%s",
                    original.url.host,
                    original.url.encodedPath,
                    if (finalUrl != original.url) finalUrl.host else "",
                    if (finalUrl != original.url) finalUrl.encodedPath else "",
                    response.code,
                    hasCookiesForRequest(finalUrl),
                    !response.request.header("Referer").isNullOrBlank()
                )
            }
            // Retry once on 504/503 for image hosts: OkHttp's "only-if-cached" cache miss
            // returns 504 (and some CDNs return 503 on transient edge failures) without
            // ever hitting the network. A retry without blocking sleep is enough to recover
            // most image loads before the UI gives up. Logged so we can correlate with
            // coil_image_load_failed diagnostics.
            if (isImageRequest && response.code in 503..504 && !response.request.url.queryParameterNames.contains(RETRY_FLAG)) {
                response.closeQuietly()
                if (BuildConfig.DEBUG) {
                    Timber.tag(TAG).d(
                        "image_retry status=%d attempt=2 url=%s",
                        response.code,
                        sanitizeUrlForLog(original.url)
                    )
                }
                val retried = request.newBuilder()
                    .url(appendRetryFlag(request.url))
                    .build()
                return chain.proceed(retried)
            }
            response
        } catch (e: IOException) {
            if (BuildConfig.DEBUG && isImageRequest) {
                val httpCode = (e as? OkHttpResponseException)?.code
                Timber.tag(TAG).w(
                    "imageLoadFailure host=%s path=%s type=%s code=%s detail=%s cookiesPresent=%s refererPresent=%s",
                    original.url.host,
                    original.url.encodedPath,
                    e::class.java.simpleName,
                    httpCode?.toString().orEmpty(),
                    describeFailure(e),
                    hasCookiesForRequest(original.url),
                    !request.header("Referer").isNullOrBlank()
                )
            }
            throw e
        }
    }

    /** Цепочка cause + errorCode для HTTP/2 StreamResetException — см. ImageViewerAdapter. */
    private fun describeFailure(throwable: Throwable): String {
        val sb = StringBuilder()
        var current: Throwable? = throwable
        var depth = 0
        while (current != null && depth < 6) {
            if (depth > 0) sb.append(" <- ")
            sb.append(current::class.java.simpleName)
            current.message?.takeIf { it.isNotBlank() }?.let { sb.append('(').append(it.take(140)).append(')') }
            (current as? StreamResetException)?.errorCode?.name?.let { sb.append("[errorCode=").append(it).append(']') }
            current = current.cause
            depth++
        }
        return sb.toString()
    }

    private fun Response.closeQuietly() {
        try {
            close()
        } catch (_: Throwable) {
        }
    }

    private fun appendRetryFlag(url: HttpUrl): HttpUrl =
            url.newBuilder().setQueryParameter(RETRY_FLAG, "1").build()

    private fun sanitizeUrlForLog(url: HttpUrl): String {
        val raw = url.toString()
        return if (raw.length <= 200) raw else raw.substring(0, 200) + "…"
    }

    companion object {
        private const val TAG = "ImageViewer"
        private const val DEFAULT_REFERER = "https://4pda.to/forum/"
        private const val RETRY_FLAG = "forpda_retry"

        fun isFourPdaImageRequest(url: HttpUrl): Boolean {
            val host = url.host.lowercase(Locale.ROOT)
            val path = url.encodedPath.lowercase(Locale.ROOT)
            val isImagePath = IMAGE_EXTENSIONS.any { path.endsWith(it) }
            if (!isImagePath) return false

            return host == "4pda.to" && (
                path.startsWith("/s/") ||
                    path.startsWith("/forum/dl/post/") ||
                    path.startsWith("/wp-content/uploads/")
                ) ||
                host == "s.4pda.to" ||
                host == "4pda.ws" ||
                host.endsWith(".4pda.ws")
        }

        private val IMAGE_EXTENSIONS = listOf(".jpg", ".jpeg", ".png", ".gif", ".bmp", ".webp")
    }
}
