package forpdateam.ru.forpda.client.interceptors

import android.os.Looper
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/**
 * Контракт прогресса загрузки картинок: тело оборачивается счётчиком ТОЛЬКО для URL,
 * на который подписан индикатор, события идут по мере чтения и заканчиваются 100%.
 */
@RunWith(RobolectricTestRunner::class)
class ImageProgressInterceptorTest {

    private val url = "https://s.4pda.to/forum/uploads/pic.jpg"
    private val interceptor = ImageProgressInterceptor()

    @Test
    fun `reports byte progress and finishes at full content length`() {
        val events = mutableListOf<Pair<Long, Long>>()
        val listener = ImageDownloadProgress.Listener { bytesRead, contentLength ->
            events += bytesRead to contentLength
        }
        ImageDownloadProgress.register(url, listener)
        try {
            val payload = ByteArray(64 * 1024) { it.toByte() }
            val response = interceptor.intercept(chainFor(payload, contentLengthKnown = true))
            response.body!!.bytes()
            shadowOf(Looper.getMainLooper()).idle()

            assertTrue("должно прийти несколько событий, а не одно", events.size > 1)
            events.forEach { (bytesRead, contentLength) ->
                assertEquals(payload.size.toLong(), contentLength)
                assertTrue(bytesRead in 1..payload.size.toLong())
            }
            assertEquals(payload.size.toLong(), events.last().first)
        } finally {
            ImageDownloadProgress.unregister(url, listener)
        }
    }

    @Test
    fun `unknown content length is resolved on the last event`() {
        val events = mutableListOf<Pair<Long, Long>>()
        val listener = ImageDownloadProgress.Listener { bytesRead, contentLength ->
            events += bytesRead to contentLength
        }
        ImageDownloadProgress.register(url, listener)
        try {
            val payload = ByteArray(32 * 1024)
            val response = interceptor.intercept(chainFor(payload, contentLengthKnown = false))
            response.body!!.bytes()
            shadowOf(Looper.getMainLooper()).idle()

            assertTrue(events.isNotEmpty())
            assertTrue("до конца потока размер неизвестен", events.dropLast(1).all { it.second < 0 })
            assertEquals(payload.size.toLong() to payload.size.toLong(), events.last())
        } finally {
            ImageDownloadProgress.unregister(url, listener)
        }
    }

    @Test
    fun `body is left untouched when nobody listens`() {
        val original = ByteArray(1024).toResponseBody(IMAGE_JPEG)
        val response = interceptor.intercept(chainFor(original))

        assertSame(original, response.body)
    }

    @Test
    fun `unregister stops delivering to a stale listener`() {
        val events = mutableListOf<Long>()
        val listener = ImageDownloadProgress.Listener { bytesRead, _ -> events += bytesRead }
        ImageDownloadProgress.register(url, listener)
        val response = interceptor.intercept(chainFor(ByteArray(64 * 1024), contentLengthKnown = true))
        assertNotNull(response.body)

        // Свайп на соседнюю страницу: подписка снята ДО того, как main-очередь разобрала события.
        response.body!!.bytes()
        ImageDownloadProgress.unregister(url, listener)
        shadowOf(Looper.getMainLooper()).idle()

        assertTrue(events.isEmpty())
    }

    private fun chainFor(payload: ByteArray, contentLengthKnown: Boolean): Interceptor.Chain {
        val body = payload.toResponseBody(IMAGE_JPEG)
        return chainFor(if (contentLengthKnown) body else UnknownLengthBody(body))
    }

    private fun chainFor(body: okhttp3.ResponseBody): Interceptor.Chain {
        val request = Request.Builder().url(url).build()
        val response = Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_2)
            .code(200)
            .message("OK")
            .body(body)
            .build()
        return TestChain(request, response)
    }

    /** Ответ без Content-Length (chunked CDN): длина известна только по факту дочитывания. */
    private class UnknownLengthBody(private val original: okhttp3.ResponseBody) : okhttp3.ResponseBody() {
        override fun contentType() = original.contentType()
        override fun contentLength(): Long = -1L
        override fun source() = original.source()
    }

    private class TestChain(
        private val request: Request,
        private val response: Response,
    ) : Interceptor.Chain {
        override fun request(): Request = request
        override fun proceed(request: Request): Response = response
        override fun connection() = null
        override fun call() = throw UnsupportedOperationException()
        override fun connectTimeoutMillis() = 0
        override fun withConnectTimeout(timeout: Int, unit: java.util.concurrent.TimeUnit) = this
        override fun readTimeoutMillis() = 0
        override fun withReadTimeout(timeout: Int, unit: java.util.concurrent.TimeUnit) = this
        override fun writeTimeoutMillis() = 0
        override fun withWriteTimeout(timeout: Int, unit: java.util.concurrent.TimeUnit) = this
    }

    private companion object {
        val IMAGE_JPEG = "image/jpeg".toMediaType()
    }
}
