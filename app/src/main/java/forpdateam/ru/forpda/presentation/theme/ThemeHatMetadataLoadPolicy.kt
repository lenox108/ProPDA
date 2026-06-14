package forpdateam.ru.forpda.presentation.theme

import forpdateam.ru.forpda.entity.remote.theme.ThemePage
import forpdateam.ru.forpda.entity.remote.theme.ThemePost

/**
 * Decides when topic hat (page-1) metadata should block first paint vs load after render.
 */
internal object ThemeHatMetadataLoadPolicy {

    fun shouldPreloadHatMetadataBeforeRender(page: ThemePage): Boolean = false

    fun shouldScheduleDeferredHatMetadataLoad(
            page: ThemePage,
            cachedHat: ThemePost?,
            hatMetadataJobActive: Boolean,
            visiblePageNumber: Int = page.pagination.current,
    ): Boolean {
        if (page.id <= 0 || visiblePageNumber <= 1) return false
        if (hatMetadataJobActive) return false
        if (hasPostRatingMetadata(page.topicHatPost)) return false
        if (hasPostRatingMetadata(cachedHat)) return false
        return true
    }

    fun shouldEnrichHatFromCache(
            page: ThemePage,
            cachedHat: ThemePost?,
            visiblePageNumber: Int = page.pagination.current,
    ): Boolean {
        if (page.id <= 0 || visiblePageNumber <= 1) return false
        if (!hasPostRatingMetadata(cachedHat)) return false
        return !hasPostRatingMetadata(page.topicHatPost)
    }

    /**
     * Deferred page-1 hat metadata must not trigger a full WebView reload after the topic
     * has painted — that resets scroll to the top and flashes the hat block before anchor scroll.
     * Re-render only when the user explicitly opened the hat overlay; correcting the in-memory
     * posts list (duplicate hat strip) must stay DOM-only to avoid a second scroll jerk on open.
     */
    fun shouldEmitViewUpdateAfterDeferredHatMetadataLoad(
            userHatOpenOverride: Boolean?,
    ): Boolean = userHatOpenOverride == true

    /** Toolbar must learn about cached hat without reloading WebView on ordinary opens. */
    fun shouldRefreshToolbarAfterDeferredHatMetadataLoad(
            userHatOpenOverride: Boolean?,
    ): Boolean = userHatOpenOverride != true

    fun hasPostRatingMetadata(post: ThemePost?): Boolean =
            post != null && (!post.postRating.isNullOrBlank() || post.canPlusPostRating || post.canMinusPostRating)
}
