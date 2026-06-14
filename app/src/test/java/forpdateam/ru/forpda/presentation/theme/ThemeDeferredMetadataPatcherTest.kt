package forpdateam.ru.forpda.presentation.theme

import forpdateam.ru.forpda.entity.remote.theme.ThemePost
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeDeferredMetadataPatcherTest {

    @Test
    fun uiEvents_emitsUserPostCountPatchWhenCountAppears() {
        val post = ThemePost().apply {
            id = 100
            userPostCount = 19342
        }
        val before = mapOf(100 to ThemeDeferredMetadataPatcher.PostMetadataUiState(
                userPostCount = null,
                postRating = null,
                canPlusPostRating = false,
                canMinusPostRating = false,
        ))

        val events = ThemeDeferredMetadataPatcher.uiEvents(before, listOf(post))

        assertEquals(1, events.size)
        val patch = events.single() as ThemeUiEvent.PatchUserPostCountUi
        assertEquals(100, patch.postId)
        assertEquals(19342, patch.userPostCount)
    }

    @Test
    fun uiEvents_emitsRatingPatchWhenControlsAppear() {
        val post = ThemePost().apply {
            id = 200
            postRating = "5"
            canPlusPostRating = true
            canMinusPostRating = true
        }
        val before = mapOf(200 to ThemeDeferredMetadataPatcher.PostMetadataUiState(
                userPostCount = null,
                postRating = null,
                canPlusPostRating = false,
                canMinusPostRating = false,
        ))

        val events = ThemeDeferredMetadataPatcher.uiEvents(before, listOf(post))

        assertEquals(1, events.size)
        val patch = events.single() as ThemeUiEvent.PatchPostRatingUi
        assertEquals(200, patch.postId)
        assertEquals("5", patch.ratingText)
        assertTrue(patch.canPlus)
        assertTrue(patch.canMinus)
    }

    @Test
    fun uiEvents_emptyWhenMetadataUnchanged() {
        val post = ThemePost().apply {
            id = 300
            userPostCount = 42
            postRating = "1"
            canPlusPostRating = true
            canMinusPostRating = false
        }
        val before = mapOf(300 to ThemeDeferredMetadataPatcher.snapshot(post))

        val events = ThemeDeferredMetadataPatcher.uiEvents(before, listOf(post))

        assertTrue(events.isEmpty())
    }
}
