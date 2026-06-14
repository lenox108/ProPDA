package forpdateam.ru.forpda.presentation.theme

import forpdateam.ru.forpda.entity.remote.theme.ThemePage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeDeferredMetadataEnrichmentPolicyTest {

    @Test
    fun navigationSnapshot_ignoresPostListMutations() {
        val page = ThemePage().apply {
            id = 903891
            url = "https://4pda.to/forum/index.php?showtopic=903891&view=getnewpost"
            anchorPostId = "55"
            addAnchor("entry55")
            hasUnreadTarget = true
        }
        val before = ThemeDeferredMetadataEnrichmentPolicy.navigationSnapshot(page)
        page.posts.add(
                forpdateam.ru.forpda.entity.remote.theme.ThemePost().apply {
                    id = 55
                    postRating = "+1"
                    userPostCount = 100
                }
        )
        val after = ThemeDeferredMetadataEnrichmentPolicy.navigationSnapshot(page)
        assertEquals(before, after)
    }

    @Test
    fun deferredMetadataDoesNotAffectAnchorOrScroll() {
        val page = ThemePage().apply {
            id = 1106099
            url = "https://4pda.to/forum/index.php?showtopic=1106099&view=getnewpost#entry123"
            anchorPostId = "123"
            addAnchor("entry123")
            hasUnreadTarget = false
            wasNearBottom = true
            refreshRestoreId = "restore-1"
        }
        val before = ThemeDeferredMetadataEnrichmentPolicy.navigationSnapshot(page)
        page.posts.add(
                forpdateam.ru.forpda.entity.remote.theme.ThemePost().apply {
                    id = 123
                    userPostCount = 42
                    postRating = "+5"
                }
        )
        val after = ThemeDeferredMetadataEnrichmentPolicy.navigationSnapshot(page)
        assertTrue(ThemeDeferredMetadataEnrichmentPolicy.navigationUnchanged(before, after))
        assertEquals(1500L, ThemeDeferredMetadataEnrichmentPolicy.DELAY_MS)
    }
}
