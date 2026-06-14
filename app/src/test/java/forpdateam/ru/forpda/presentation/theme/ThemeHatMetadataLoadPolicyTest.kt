package forpdateam.ru.forpda.presentation.theme

import forpdateam.ru.forpda.entity.remote.others.pagination.Pagination
import forpdateam.ru.forpda.entity.remote.theme.ThemePage
import forpdateam.ru.forpda.entity.remote.theme.ThemePost
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeHatMetadataLoadPolicyTest {

    private fun page(current: Int = 2, topicId: Int = 42): ThemePage = ThemePage().apply {
        id = topicId
        pagination = Pagination().apply {
            this.current = current
            all = 5
        }
    }

    private fun hatWithRating(id: Int = 1): ThemePost = ThemePost().apply {
        this.id = id
        postRating = "5"
    }

    @Test
    fun `does not block render for non-first page`() {
        assertFalse(ThemeHatMetadataLoadPolicy.shouldPreloadHatMetadataBeforeRender(page()))
    }

    @Test
    fun `defers network hat load when metadata missing on non-first page`() {
        assertTrue(
                ThemeHatMetadataLoadPolicy.shouldScheduleDeferredHatMetadataLoad(
                        page = page(),
                        cachedHat = null,
                        hatMetadataJobActive = false
                )
        )
    }

    @Test
    fun `skips deferred network load when cached hat already has metadata`() {
        assertFalse(
                ThemeHatMetadataLoadPolicy.shouldScheduleDeferredHatMetadataLoad(
                        page = page(),
                        cachedHat = hatWithRating(),
                        hatMetadataJobActive = false
                )
        )
        assertTrue(
                ThemeHatMetadataLoadPolicy.shouldEnrichHatFromCache(
                        page = page(),
                        cachedHat = hatWithRating()
                )
        )
    }

    @Test
    fun `skips deferred load on first page`() {
        assertFalse(
                ThemeHatMetadataLoadPolicy.shouldScheduleDeferredHatMetadataLoad(
                        page = page(current = 1),
                        cachedHat = null,
                        hatMetadataJobActive = false
                )
        )
    }

    @Test
    fun `skips deferred load while hat job active`() {
        assertFalse(
                ThemeHatMetadataLoadPolicy.shouldScheduleDeferredHatMetadataLoad(
                        page = page(),
                        cachedHat = null,
                        hatMetadataJobActive = true
                )
        )
    }

    @Test
    fun `does not emit view update after metadata-only enrichment`() {
        assertFalse(
                ThemeHatMetadataLoadPolicy.shouldEmitViewUpdateAfterDeferredHatMetadataLoad(
                        userHatOpenOverride = null
                )
        )
    }

    @Test
    fun `emits view update only when hat explicitly open`() {
        assertTrue(
                ThemeHatMetadataLoadPolicy.shouldEmitViewUpdateAfterDeferredHatMetadataLoad(
                        userHatOpenOverride = true
                )
        )
        assertFalse(
                ThemeHatMetadataLoadPolicy.shouldEmitViewUpdateAfterDeferredHatMetadataLoad(
                        userHatOpenOverride = null
                )
        )
    }

    @Test
    fun `refreshes toolbar after metadata cache without webview reload`() {
        assertTrue(
                ThemeHatMetadataLoadPolicy.shouldRefreshToolbarAfterDeferredHatMetadataLoad(
                        userHatOpenOverride = null
                )
        )
        assertFalse(
                ThemeHatMetadataLoadPolicy.shouldRefreshToolbarAfterDeferredHatMetadataLoad(
                        userHatOpenOverride = true
                )
        )
    }
}
