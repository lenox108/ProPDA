package forpdateam.ru.forpda.presentation.theme

import forpdateam.ru.forpda.common.Preferences as AppPreferences
import forpdateam.ru.forpda.entity.remote.theme.ThemePage
import forpdateam.ru.forpda.presentation.theme.TopicUnreadOpenPolicy.TopicOpenSessionKind

/**
 * Coordinates post-open enrichment after [markPrimaryOpenComplete].
 * Enrich jobs must not run during primary open and cannot affect first-paint navigation.
 */
internal class ThemePostOpenEnrichmentController(
        private val callbacks: Callbacks,
) {
    interface Callbacks {
        fun scheduleFavoriteSync(page: ThemePage, traceId: String)
        fun scheduleMetadataEnrichment(page: ThemePage, traceId: String)
        fun scheduleHatMetadata(page: ThemePage)
        fun scheduleHybridPrefetch(page: ThemePage, traceId: String)
        fun prefetchEditor(page: ThemePage)
    }

    private var primaryOpenCompleteTraceId: String? = null
    private var postOpenEnrichStartedTraceId: String? = null

    fun reset() {
        primaryOpenCompleteTraceId = null
        postOpenEnrichStartedTraceId = null
    }

    fun markPrimaryOpenComplete(traceId: String) {
        if (traceId.isBlank()) return
        primaryOpenCompleteTraceId = traceId
    }

    fun isPrimaryOpenComplete(activeTraceId: String): Boolean =
            activeTraceId.isNotBlank() && primaryOpenCompleteTraceId == activeTraceId

    fun isPostOpenEnrichStarted(activeTraceId: String): Boolean =
            activeTraceId.isNotBlank() && postOpenEnrichStartedTraceId == activeTraceId

    fun startPostOpenEnrichment(
            page: ThemePage,
            traceId: String,
            sessionKind: TopicOpenSessionKind?,
            loadAction: ThemeLoadAction,
            scrollMode: AppPreferences.Main.TopicScrollMode,
    ) {
        if (!ThemePostOpenEnrichmentPolicy.shouldStartEnrichment(primaryOpenCompleteTraceId, traceId)) {
            return
        }
        if (postOpenEnrichStartedTraceId == traceId) return
        postOpenEnrichStartedTraceId = traceId
        val tasks = ThemePostOpenEnrichmentPolicy.enrichmentTasks(
                sessionKind = sessionKind,
                loadAction = loadAction,
                scrollMode = scrollMode,
                page = page,
        )
        if (tasks.favoriteSync) callbacks.scheduleFavoriteSync(page, traceId)
        if (tasks.metadata) callbacks.scheduleMetadataEnrichment(page, traceId)
        if (tasks.hatMetadata) callbacks.scheduleHatMetadata(page)
        if (tasks.hybridPrefetch) callbacks.scheduleHybridPrefetch(page, traceId)
        if (tasks.editorPrefetch) callbacks.prefetchEditor(page)
    }
}
