package forpdateam.ru.forpda.client.interceptors

import forpdateam.ru.forpda.client.FourPdaRequestGovernor
import forpdateam.ru.forpda.client.RequestPriority
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Пропускает исходящие запросы к 4pda через общий бюджет [FourPdaRequestGovernor] и сообщает ему
 * о полученных 429.
 *
 * Ставится СЕТЕВЫМ интерцептором: ответы, отданные из HTTP-кэша OkHttp, бюджет не тратят — платим
 * только за реальные обращения к серверу.
 *
 * Картинки и статика (их на экране много и они ходят на CDN) регулятор не проходят: ограничивать
 * нужно HTML/XHR, за которые 4pda и включает анти-флуд.
 */
class RequestGovernorInterceptor : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (!isGoverned(request)) return chain.proceed(request)

        val priority = request.tag(RequestPriority::class.java) ?: RequestPriority.USER
        FourPdaRequestGovernor.acquire(priority)
        val response = chain.proceed(request)
        if (response.code == FourPdaRequestGovernor.HTTP_TOO_MANY_REQUESTS) {
            FourPdaRequestGovernor.onResponse(
                    code = response.code,
                    retryAfterSeconds = response.header("Retry-After")?.toLongOrNull()
            )
        }
        return response
    }

    private fun isGoverned(request: okhttp3.Request): Boolean {
        if (!request.url.host.contains("4pda", ignoreCase = true)) return false
        if (request.header("Accept")?.startsWith("image/", ignoreCase = true) == true) return false
        return !isStaticAsset(request.url.encodedPath)
    }

    private fun isStaticAsset(path: String): Boolean {
        val lower = path.lowercase()
        return STATIC_SUFFIXES.any { lower.endsWith(it) }
    }

    private companion object {
        val STATIC_SUFFIXES = listOf(
                ".jpg", ".jpeg", ".png", ".webp", ".gif", ".bmp", ".ico", ".svg",
                ".css", ".js", ".mjs", ".woff", ".woff2", ".ttf", ".otf", ".eot",
                ".mp4", ".webm", ".zip", ".apk"
        )
    }
}
