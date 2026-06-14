package forpdateam.ru.forpda.client.interceptors

import okhttp3.Interceptor
import okhttp3.Response
import timber.log.Timber
import java.util.Locale
import forpdateam.ru.forpda.BuildConfig
import forpdateam.ru.forpda.client.OkHttpResponseException
import forpdateam.ru.forpda.client.GoogleCaptchaException
import okhttp3.HttpUrl

/**
 * Interceptor for handling HTTP errors and special cases like Cloudflare
 */
class ErrorInterceptor : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val response = chain.proceed(chain.request())
        
        if (!response.isSuccessful) {
            Timber.w("HTTP %d %s", response.code, response.message)
            
            when (response.code) {
                // 404 для форума часто возвращает HTML-заглушку «тема перенесена/не найдена»,
                // которую умеют разбирать верхние уровни (ThemeApi relocation fallback).
                // Важно: не читать body тут, иначе downstream уже не увидит HTML.
                404 -> return response
                403 -> {
                    val content = response.body?.string() ?: ""
                    if (looksLikeCloudflareCaptchaChallenge(content)) {
                        throw GoogleCaptchaException(content)
                    }
                    throw response.toHttpException()
                }
                else -> throw response.toHttpException()
            }
        }
        
        return response
    }

    private fun HttpUrl.sanitizedForError(): String {
        return newBuilder().query(null).fragment(null).build().toString()
    }

    private fun Response.toHttpException(): OkHttpResponseException {
        val retryAfterSeconds = header("Retry-After")?.toLongOrNull()
        if (code == 429 && retryAfterSeconds != null) {
            Timber.w("HTTP 429 Retry-After=%d url=%s", retryAfterSeconds, request.url.sanitizedForError())
        }
        return OkHttpResponseException(
            code = code,
            name = message,
            url = request.url.sanitizedForError(),
            retryAfterSeconds = retryAfterSeconds
        )
    }

    /**
     * Только явные признаки страницы проверки Cloudflare / chk_captcha. 
     * Остальные 403 — обычная ошибка доступа.
     */
    private fun looksLikeCloudflareCaptchaChallenge(html: String?): Boolean {
        if (html == null || html.length < 32) {
            return false
        }
        
        val lower = html.lowercase(Locale.ROOT)
        
        return when {
            lower.contains("chk_captcha") || lower.contains("cdn-cgi/l/chk_captcha") -> true
            lower.contains("cf-browser-verification") || lower.contains("cf-chl-") -> true
            lower.contains("challenge-platform") && lower.contains("cloudflare") -> true
            lower.contains("just a moment") && lower.contains("cloudflare") -> true
            lower.contains("turnstile") || lower.contains("cf-turnstile") -> true
            lower.contains("проверка безопасности") && lower.contains("cloudflare") -> true
            else -> false
        }
    }
}
