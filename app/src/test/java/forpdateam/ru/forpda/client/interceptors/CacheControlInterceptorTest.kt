package forpdateam.ru.forpda.client.interceptors

import io.mockk.every
import io.mockk.mockk
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CacheControlInterceptorTest {

    private val maxAge = "max-age=300"
    private val interceptor = CacheControlInterceptor(assetMaxAgeSeconds = 300)

    @Test
    fun `adds max-age to css response without server Cache-Control`() {
        val response = interceptor.underTest(
            path = "/forum/style.css",
            existingCacheControl = null,
            existingExpires = null,
        )

        assertEquals(maxAge, response.header("Cache-Control"))
    }

    @Test
    fun `adds max-age to js and font paths`() {
        for (path in listOf("/a.js", "/a.mjs", "/font.woff", "/font.woff2", "/font.ttf", "/x.svg", "/favicon.ico")) {
            val response = interceptor.underTest(path = path, existingCacheControl = null, existingExpires = null)
            assertEquals(
                "expected max-age for $path",
                maxAge,
                response.header("Cache-Control")
            )
        }
    }

    @Test
    fun `does NOT add max-age to html or unknown paths`() {
        for (path in listOf("/forum/", "/index.php?showtopic=1", "/forum/file.php?id=42")) {
            val response = interceptor.underTest(path = path, existingCacheControl = null, existingExpires = null)
            assertNull(
                "must not set Cache-Control for $path",
                response.header("Cache-Control")
            )
        }
    }

    @Test
    fun `preserves server Cache-Control when already present`() {
        val serverHeader = "no-store, max-age=0"
        val response = interceptor.underTest(
            path = "/style.css",
            existingCacheControl = serverHeader,
            existingExpires = null,
        )

        assertEquals(serverHeader, response.header("Cache-Control"))
    }

    @Test
    fun `preserves server Expires when present even without Cache-Control`() {
        val expires = "Wed, 21 Oct 2099 07:28:00 GMT"
        val response = interceptor.underTest(
            path = "/style.css",
            existingCacheControl = null,
            existingExpires = expires,
        )

        // If server sent Expires, do not override; OkHttp will use it.
        assertNull(response.header("Cache-Control"))
    }

    @Test
    fun `does NOT add max-age to personalised requests with auth_key`() {
        val response = interceptor.underTest(
            path = "/forum/style.css",
            existingCacheControl = null,
            existingExpires = null,
            queryParams = mapOf("auth_key" to "secret")
        )

        assertNull(
            "must not cache personalised responses",
            response.header("Cache-Control")
        )
    }

    @Test
    fun `does NOT add max-age to requests with Authorization header`() {
        val response = interceptor.underTest(
            path = "/forum/style.css",
            existingCacheControl = null,
            existingExpires = null,
            extraHeaders = mapOf("Authorization" to "Bearer x")
        )

        assertNull(response.header("Cache-Control"))
    }

    // --- helpers ---

    /**
     * Builds a chain that returns a response with the given headers, then runs
     * the interceptor and returns the **response returned by the interceptor**
     * (which is either the upstream response, or a rebuilt one with new headers).
     */
    private fun CacheControlInterceptor.underTest(
        path: String,
        existingCacheControl: String?,
        existingExpires: String?,
        queryParams: Map<String, String> = emptyMap(),
        extraHeaders: Map<String, String> = emptyMap(),
    ): Response {
        val urlBuilder = "https://4pda.to$path".toHttpUrlOrThrow().newBuilder()
        for ((k, v) in queryParams) urlBuilder.setQueryParameter(k, v)
        val requestBuilder = Request.Builder().url(urlBuilder.build())
        for ((k, v) in extraHeaders) requestBuilder.header(k, v)
        val request = requestBuilder.build()

        val responseBuilder = Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
        if (existingCacheControl != null) responseBuilder.header("Cache-Control", existingCacheControl)
        if (existingExpires != null) responseBuilder.header("Expires", existingExpires)
        val upstreamResponse = responseBuilder.build()

        val chain = mockk<Interceptor.Chain>()
        every { chain.request() } returns request
        every { chain.proceed(any()) } returns upstreamResponse

        return intercept(chain)
    }

    private fun String.toHttpUrlOrThrow(): okhttp3.HttpUrl =
        this.toHttpUrlOrNull() ?: error("invalid url: $this")
}
