package forpdateam.ru.forpda.presentation.theme

import forpdateam.ru.forpda.common.Preferences as AppPreferences
import forpdateam.ru.forpda.entity.remote.theme.ThemePage
import forpdateam.ru.forpda.presentation.theme.TopicUnreadOpenPolicy.TopicOpenSessionKind

/**
 * Gates post-open enrichment so primary HTML paint stays deterministic.
 *
 * Primary open: URL → network → parse → minimal HTML → scroll/reveal.
 * Post-open enrich: metadata, hat, hybrid neighbors, editor prefetch, favorites — must not
 * mutate navigation/read-state fields guarded by [ThemeDeferredMetadataEnrichmentPolicy].
 */
object ThemePostOpenEnrichmentPolicy {

    data class EnrichmentTasks(
            val favoriteSync: Boolean,
            val metadata: Boolean,
            val hatMetadata: Boolean,
            val hybridPrefetch: Boolean,
            val editorPrefetch: Boolean,
    )

    fun shouldStartEnrichment(primaryOpenCompleteTraceId: String?, activeTraceId: String): Boolean =
            activeTraceId.isNotBlank() && primaryOpenCompleteTraceId == activeTraceId

    fun enrichmentTasks(
            sessionKind: TopicOpenSessionKind?,
            loadAction: ThemeLoadAction,
            scrollMode: AppPreferences.Main.TopicScrollMode,
            page: ThemePage,
    ): EnrichmentTasks {
        val normalOpen = loadAction == ThemeLoadAction.Normal
        val hybridMode = scrollMode == AppPreferences.Main.TopicScrollMode.HYBRID
        return EnrichmentTasks(
                favoriteSync = page.id > 0,
                metadata = page.id > 0 && page.posts.isNotEmpty(),
                hatMetadata = page.id > 0,
                hybridPrefetch = normalOpen &&
                        hybridMode &&
                        !TopicUnreadOpenPolicy.shouldSuppressHybridPreload(sessionKind) &&
                        page.pagination.current == 1 &&
                        page.pagination.all > 1,
                editorPrefetch = page.posts.isNotEmpty(),
        )
    }

    /** WebView reveal must wait until primary HTML for the active trace was emitted. */
    fun shouldDeferRevealUntilPrimaryOpenComplete(primaryOpenComplete: Boolean): Boolean =
            !primaryOpenComplete
}
