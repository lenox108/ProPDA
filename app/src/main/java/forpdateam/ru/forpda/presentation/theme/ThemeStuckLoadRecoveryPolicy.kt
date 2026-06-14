package forpdateam.ru.forpda.presentation.theme

/**
 * Recovers theme UI when [SharedFlow] updateView was missed (fragment below STARTED) or
 * [loadThemeJob] finished without emitting while the spinner stayed visible.
 */
internal object ThemeStuckLoadRecoveryPolicy {

    fun shouldReEmitLoadedPage(pageHtml: String?): Boolean = !pageHtml.isNullOrBlank()

    fun shouldClearOrphanedRefreshing(
            isRefreshing: Boolean,
            loadJobActive: Boolean,
            pageHtml: String?,
    ): Boolean = isRefreshing && !loadJobActive && pageHtml.isNullOrBlank()
}
