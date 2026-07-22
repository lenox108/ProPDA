package forpdateam.ru.forpda.analytics

import android.app.Application
import forpdateam.ru.forpda.BuildConfig
import io.appmetrica.analytics.AppMetrica
import io.appmetrica.analytics.AppMetricaConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * store канал (публикация в Google Play): AppMetrica включена.
 *
 * Здесь и только здесь `io.appmetrica.*` присутствует на classpath. Вся тяжёлая
 * инициализация (сетевой I/O, чтение конфига) уходит в фон, чтобы не блокировать
 * cold start; установка UncaughtExceptionHandler — синхронно, чтобы ловить ранние падения.
 */
object FlavorAnalytics {
    fun setup(app: Application, scope: CoroutineScope) {
        Analytics.setReporter(object : AnalyticsReporter {
            override fun reportError(message: String, throwable: Throwable?) {
                runCatching {
                    AppMetrica.reportError(message, throwable ?: Throwable(message))
                }
            }
        })

        // Цепляем репортер необработанных исключений поверх уже установленного
        // (CrashReporter). Прежний handler в цепочке не теряется.
        val defaultUncaught = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, ex ->
            try {
                ex?.let { AppMetrica.reportError("uncaught:${thread.name}:${it.message}", it) }
            } catch (_: Throwable) {
                // ignore
            }
            defaultUncaught?.uncaughtException(thread, ex)
        }

        scope.launch(Dispatchers.IO) {
            try {
                val config = AppMetricaConfig.newConfigBuilder(BuildConfig.APPMETRICA_API_KEY).build()
                AppMetrica.activate(app, config)
                AppMetrica.enableActivityAutoTracking(app)
            } catch (t: Throwable) {
                Timber.w(t, "AppMetrica async init failed")
            }
        }
    }
}
