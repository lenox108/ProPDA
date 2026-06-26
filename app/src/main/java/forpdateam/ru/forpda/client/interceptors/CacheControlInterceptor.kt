package forpdateam.ru.forpda.client.interceptors

import okhttp3.Interceptor
import okhttp3.Response

/**
 * Network-side interceptor that assigns a sensible `Cache-Control: max-age=N` to
 * responses for public assets (CSS, JS, fonts) when the server did not set one.
 *
 * Why this exists:
 * - The 4pda backend often returns `Cache-Control: no-cache` or omits it for static
 *   assets. OkHttp's cache respects server headers strictly, so an OMIT means
 *   the response is cached but immediately revalidated on the next request.
 * - For theme CSS/JS, this causes repeated network round-trips for assets that
 *   are versioned by URL and effectively immutable within a release.
 *
 * Rules:
 * 1. Only network-side, only GETs, only for paths ending in a known asset suffix.
 * 2. Never overrides an explicit `Cache-Control` from the server.
 * 3. Never applies to requests that carry an `Authorization` header or the auth_key
 *    query parameter — those are personalised and must not be cached.
 *
 * The default max-age is conservative (5 minutes). Bump via [assetMaxAgeSeconds]
 * only if a measured profile shows the headroom is needed.
 */
class CacheControlInterceptor(
    private val assetMaxAgeSeconds: Int = 300,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (request.method != "GET") return chain.proceed(request)
        if (!isAssetPath(request.url.encodedPath)) return chain.proceed(request)
        if (isPersonalisedRequest(request)) return chain.proceed(request)

        val response = chain.proceed(request)
        if (response.header(CACHE_CONTROL) != null) return response
        if (response.header(EXPIRES) != null) return response

        val maxAge = "max-age=$assetMaxAgeSeconds"
        return response.newBuilder()
            .removeHeader("Pragma")
            .header(CACHE_CONTROL, maxAge)
            .build()
    }

    private fun isAssetPath(path: String): Boolean {
        val lower = path.lowercase()
        return lower.endsWith(".css") ||
            lower.endsWith(".js") ||
            lower.endsWith(".mjs") ||
            lower.endsWith(".woff") ||
            lower.endsWith(".woff2") ||
            lower.endsWith(".ttf") ||
            lower.endsWith(".otf") ||
            lower.endsWith(".eot") ||
            lower.endsWith(".svg") ||
            lower.endsWith(".ico")
    }

    private fun isPersonalisedRequest(request: okhttp3.Request): Boolean {
        if (request.header("Authorization") != null) return true
        // Forum auth_key query param: 4pda uses ?auth_key=… for personalised theme pages.
        if (request.url.queryParameter("auth_key") != null) return true
        // 4pda's "logged in" cookie is `member_id`; if it is present the request
        // may return user-specific content (theme CSS variables, per-user JS, etc.)
        // and must not be cached.
        val cookie = request.header("Cookie")
        if (cookie != null && cookie.contains(AUTH_COOKIE_MARKER)) return true
        return false
    }

    companion object {
        private const val CACHE_CONTROL = "Cache-Control"
        private const val EXPIRES = "Expires"
        private const val AUTH_COOKIE_MARKER = "member_id="
    }
}
