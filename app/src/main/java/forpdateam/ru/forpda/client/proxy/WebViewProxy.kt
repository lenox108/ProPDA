package forpdateam.ru.forpda.client.proxy

import android.content.Context
import androidx.webkit.ProxyController
import androidx.webkit.WebViewFeature
import dagger.hilt.android.EntryPointAccessors
import forpdateam.ru.forpda.di.AppEntryPoint
import timber.log.Timber
import java.util.concurrent.Executor
import androidx.webkit.ProxyConfig as WebViewProxyConfig

/**
 * Прокси для WebView (новости, встроенные фрагменты страниц, проверка Cloudflare).
 *
 * WebView умеет только ОДИН маршрут на весь процесс (`ProxyController`), выбирать его по конкретной
 * теме нельзя — поэтому:
 *  - в режиме [ProxyMode.ALL] override включается всегда;
 *  - в режиме [ProxyMode.ONLY_BLOCKED_TOPICS] — только по явному запросу ([applyForced]), и это
 *    экран проверки Cloudflare: челлендж выдаётся на IP, с которого пришёл запрос, поэтому решать
 *    его надо тем же маршрутом, иначе полученный cf_clearance не подойдёт.
 *
 * Логин/пароль WebView не поддерживает — прокси с авторизацией будет работать только для
 * HTTP-запросов приложения (OkHttp), о чём сказано в настройках.
 */
object WebViewProxy {

    private val mainExecutor = Executor { it.run() }

    @Volatile
    private var appliedRule: String? = null

    /** Обычный путь: включить override, если пользователь выбрал «весь трафик через прокси». */
    fun applyIfNeeded(context: Context) {
        val settings = settings(context) ?: return
        val config = settings.config()
        if (config == null || settings.mode != ProxyMode.ALL) {
            clear()
            return
        }
        apply(config)
    }

    /**
     * Прокси даже в режиме «только заблокированные темы» — для экрана проверки Cloudflare.
     *
     * Прокси с авторизацией пропускаем: у `ProxyController` нет API для логина и пароля, страница
     * просто не загрузилась бы. Лучше решить проверку напрямую (её результат всё равно нужен и для
     * прямого маршрута, которым идёт остальной трафик), чем показать пустой экран.
     */
    fun applyForced(context: Context) {
        val config = settings(context)?.config() ?: return
        if (config.hasCredentials) {
            Timber.tag(LOG_TAG).i("WebView proxy skipped: credentials are not supported by ProxyController")
            return
        }
        apply(config)
    }

    private fun apply(config: ProxyConfig) {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE)) return
        val rule = config.toWebViewRule()
        if (rule == appliedRule) return
        runCatching {
            ProxyController.getInstance().setProxyOverride(
                    WebViewProxyConfig.Builder().addProxyRule(rule).build(),
                    mainExecutor,
                    { Timber.tag(LOG_TAG).i("WebView proxy applied: %s", rule) },
            )
            appliedRule = rule
        }.onFailure { Timber.tag(LOG_TAG).w(it, "WebView proxy override failed") }
    }

    fun clear() {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE)) return
        if (appliedRule == null) return
        runCatching {
            ProxyController.getInstance().clearProxyOverride(mainExecutor) {
                Timber.tag(LOG_TAG).i("WebView proxy cleared")
            }
            appliedRule = null
        }.onFailure { Timber.tag(LOG_TAG).w(it, "WebView proxy clear failed") }
    }

    private fun settings(context: Context): ProxySettings? = runCatching {
        EntryPointAccessors
                .fromApplication(context.applicationContext, AppEntryPoint::class.java)
                .proxySettings()
    }.getOrNull()

    private const val LOG_TAG = "ProxyRoute"
}
