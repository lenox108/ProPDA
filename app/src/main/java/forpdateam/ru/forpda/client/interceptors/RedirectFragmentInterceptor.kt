package forpdateam.ru.forpda.client.interceptors

import okhttp3.Interceptor
import okhttp3.Response
import java.util.concurrent.atomic.AtomicReference

/**
 * Сетевой перехватчик, который сохраняет фрагмент (#entry…) из заголовка Location
 * при HTTP-редиректах. OkHttp при парсинге URL отбрасывает фрагмент,
 * поэтому [okhttp3.HttpUrl] в [Response.request].url его не содержит.
 */
class RedirectFragmentInterceptor : Interceptor {

    class State {
        val lastFragment: AtomicReference<String?> = AtomicReference(null)
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val response = chain.proceed(chain.request())
        if (response.isRedirect) {
            val location = response.header("Location")
            if (location != null) {
                val hashIdx = location.indexOf('#')
                if (hashIdx >= 0) {
                    val fragment = location.substring(hashIdx + 1)
                    chain.request().tag(State::class.java)?.lastFragment?.set(fragment)
                }
            }
        }
        return response
    }
}
