package forpdateam.ru.forpda.common

import android.content.Context
import android.webkit.WebSettings
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Проверяет доступность WebView на устройстве.
 */
@Singleton
class WebViewChecker @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val webViewFound = AtomicReference<Boolean?>(null)

    /**
     * Проверяет, доступен ли WebView.
     * Кэширует результат для повторных вызовов.
     */
    fun isWebViewFound(): Boolean {
        return webViewFound.get() ?: try {
            WebSettings.getDefaultUserAgent(context)
            webViewFound.set(true)
            true
        } catch (e: Exception) {
            webViewFound.set(false)
            false
        }
    }

    /**
     * Сбрасывает кэш. Полезно при тестировании.
     */
    fun reset() {
        webViewFound.set(null)
    }
}
