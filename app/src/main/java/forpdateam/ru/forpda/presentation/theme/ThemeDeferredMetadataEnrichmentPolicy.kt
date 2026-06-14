package forpdateam.ru.forpda.presentation.theme

import forpdateam.ru.forpda.entity.remote.theme.ThemePage

/**
 * Defers desktop/profile metadata merge so it does not compete with first WebView render.
 */
object ThemeDeferredMetadataEnrichmentPolicy {

    const val DELAY_MS = 1500L

    /** Navigation/scroll fields that deferred enrichment must never mutate. */
    fun navigationSnapshot(page: ThemePage): NavigationSnapshot =
            NavigationSnapshot(
                    url = page.url,
                    anchorPostId = page.anchorPostId,
                    anchor = page.anchor,
                    hasUnreadTarget = page.hasUnreadTarget,
                    wasNearBottom = page.wasNearBottom,
                    refreshRestoreId = page.refreshRestoreId,
            )

    data class NavigationSnapshot(
            val url: String?,
            val anchorPostId: String?,
            val anchor: String?,
            val hasUnreadTarget: Boolean,
            val wasNearBottom: Boolean,
            val refreshRestoreId: String?,
    )

    fun navigationUnchanged(before: NavigationSnapshot, after: NavigationSnapshot): Boolean =
            before == after
}
