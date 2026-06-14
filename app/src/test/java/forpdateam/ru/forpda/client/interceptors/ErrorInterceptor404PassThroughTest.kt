package forpdateam.ru.forpda.client.interceptors

import io.mockk.every
import io.mockk.mockk
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Test

class ErrorInterceptor404PassThroughTest {

    @Test
    fun `404 response body is not swallowed`() {
        val html = "<html><body>Тема не найдена 404 <a href=\"/forum/index.php?showtopic=999\">go</a></body></html>"
        val request = Request.Builder()
            .url("https://4pda.to/forum/index.php?showtopic=111")
            .build()

        val upstream = Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(404)
            .message("Not Found")
            .body(html.toResponseBody("text/html; charset=utf-8".toMediaType()))
            .build()

        val chain = mockk<Interceptor.Chain>()
        every { chain.request() } returns request
        every { chain.proceed(request) } returns upstream

        val out = ErrorInterceptor().intercept(chain)

        assertEquals(404, out.code)
        assertEquals(html, out.body?.string())
    }
}

