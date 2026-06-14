package forpdateam.ru.forpda.presentation.theme

/**
 * Clears the topic loading indicator only for the [loadThemeJob] that still owns [themeLoadTraceId].
 * Cancelled jobs must not clear a newer in-flight load's spinner (log: e951dd07 spinner until 3cdb2906).
 */
internal object ThemeLoadRefreshingPolicy {

    fun shouldClearRefreshingOnJobEnd(jobTraceId: String, currentTraceId: String): Boolean =
            jobTraceId.isNotBlank() && jobTraceId == currentTraceId
}
