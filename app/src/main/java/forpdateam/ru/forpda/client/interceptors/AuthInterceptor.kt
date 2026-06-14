package forpdateam.ru.forpda.client.interceptors
import forpdateam.ru.forpda.BuildConfig

import okhttp3.Interceptor
import okhttp3.Response
import timber.log.Timber
import forpdateam.ru.forpda.common.PrivateHeaders

/**
 * Interceptor for adding authentication headers to requests
 */
class AuthInterceptor : Interceptor {

    companion object {
        /** Актуальный мобильный Chrome на Android — ближе к WebView и к типичному браузеру пользователя. */
        private const val USER_AGENT = "Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        
        val requestBuilder = originalRequest.newBuilder()
            .header("Accept-Language", "ru-RU,ru;q=0.8,en-US;q=0.6,en;q=0.4")
            .header("User-Agent", originalRequest.header("User-Agent") ?: USER_AGENT)

        // Log headers in debug mode
        if (forpdateam.ru.forpda.BuildConfig.DEBUG) {
            Timber.d("Request url ${originalRequest.url}")
        }

        return chain.proceed(requestBuilder.build())
    }
}
