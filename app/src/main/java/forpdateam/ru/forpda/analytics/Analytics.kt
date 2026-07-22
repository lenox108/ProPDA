package forpdateam.ru.forpda.analytics

/**
 * Flavor-agnostic аналитический фасад.
 *
 * Реальный репортер (AppMetrica) поставляется ТОЛЬКО во флейворе `store` через
 * [forpdateam.ru.forpda.analytics.FlavorAnalytics]. Во всех остальных каналах
 * (stable/parallel) линкуется no-op, а сама библиотека AppMetrica туда не попадает
 * (зависимость объявлена как `storeImplementation`). Поэтому код в `main` не должен
 * ссылаться на `io.appmetrica.*` напрямую — только на этот фасад.
 */
interface AnalyticsReporter {
    fun reportError(message: String, throwable: Throwable?)
}

object Analytics {
    @Volatile
    private var reporter: AnalyticsReporter? = null

    /** Вызывается один раз при старте из flavor-специфичного FlavorAnalytics. */
    fun setReporter(reporter: AnalyticsReporter?) {
        this.reporter = reporter
    }

    /** No-op, если репортер не установлен (не-store флейворы). */
    fun reportError(message: String, throwable: Throwable? = null) {
        reporter?.reportError(message, throwable)
    }
}
