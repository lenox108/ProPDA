package forpdateam.ru.forpda.analytics

/**
 * Аналитический фасад.
 *
 * Внешней аналитики в сборке нет: [FlavorAnalytics] репортер не устанавливает, поэтому
 * все вызовы [Analytics.reportError] — no-op. Фасад оставлен как единственная точка,
 * через которую код в `main` сообщает об ошибках — если репортер когда-нибудь появится,
 * подключать его нужно здесь, а не прямыми вызовами SDK по коду.
 */
interface AnalyticsReporter {
    fun reportError(message: String, throwable: Throwable?)
}

object Analytics {
    @Volatile
    private var reporter: AnalyticsReporter? = null

    /** Вызывается один раз при старте из [FlavorAnalytics]. */
    fun setReporter(reporter: AnalyticsReporter?) {
        this.reporter = reporter
    }

    /** No-op, если репортер не установлен (текущая сборка — всегда). */
    fun reportError(message: String, throwable: Throwable? = null) {
        reporter?.reportError(message, throwable)
    }
}
