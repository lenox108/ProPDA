package forpdateam.ru.forpda.ui.fragments.theme

import forpdateam.ru.forpda.entity.remote.theme.ThemePage
import forpdateam.ru.forpda.entity.remote.theme.ThemePost
import forpdateam.ru.forpda.presentation.theme.TopicHatAvailability
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeToolbarMenuPolicyTest {

    @Test
    fun `resolve shows refresh only when topic has no poll`() {
        val state = ThemeToolbarMenuPolicy.resolve(
                pageLoaded = true,
                hasPoll = false,
                hasTopicHat = false,
                hatToolbarEnabled = true,
        )
        assertTrue(state.showCompactRefresh)
        assertFalse(state.showCompactPoll)
        assertFalse(state.showCompactHat)
        assertFalse(state.showOverflowRefresh)
        assertFalse(state.showOverflowPoll)
    }

    @Test
    fun `resolve shows poll and overflow refresh when topic has poll only`() {
        val state = ThemeToolbarMenuPolicy.resolve(
                pageLoaded = true,
                hasPoll = true,
                hasTopicHat = false,
                hatToolbarEnabled = true,
        )
        assertFalse(state.showCompactRefresh)
        assertTrue(state.showCompactPoll)
        assertFalse(state.showCompactHat)
        assertTrue(state.showOverflowRefresh)
        assertFalse(state.showOverflowPoll)
    }

    @Test
    fun `resolve shows poll and hat together when topic has both`() {
        val state = ThemeToolbarMenuPolicy.resolve(
                pageLoaded = true,
                hasPoll = true,
                hasTopicHat = true,
                hatToolbarEnabled = true,
        )
        assertFalse(state.showCompactRefresh)
        assertTrue(state.showCompactPoll)
        assertTrue(state.showCompactHat)
        assertTrue(state.showOverflowRefresh)
        assertFalse(state.showOverflowPoll)
    }

    @Test
    fun `resolve shows refresh and hat when topic has hat without poll`() {
        val state = ThemeToolbarMenuPolicy.resolve(
                pageLoaded = true,
                hasPoll = false,
                hasTopicHat = true,
                hatToolbarEnabled = true,
        )
        assertTrue(state.showCompactRefresh)
        assertFalse(state.showCompactPoll)
        assertTrue(state.showCompactHat)
        assertFalse(state.showOverflowRefresh)
        assertFalse(state.showOverflowPoll)
    }

    @Test
    fun `resolve hides hat compact button when hat toolbar disabled`() {
        val state = ThemeToolbarMenuPolicy.resolve(
                pageLoaded = true,
                hasPoll = false,
                hasTopicHat = true,
                hatToolbarEnabled = false,
        )
        assertFalse(state.showCompactHat)
    }

    @Test
    fun `resolve disables all actions when page is not loaded`() {
        assertEquals(
                ThemeToolbarMenuPolicy.ToolbarMenuState.DISABLED,
                ThemeToolbarMenuPolicy.resolve(
                        pageLoaded = false,
                        hasPoll = true,
                        hasTopicHat = true,
                        hatToolbarEnabled = true,
                ),
        )
    }

    @Test
    fun `topic 824716 deep page with cached hat keeps hat button while poll uses first slot`() {
        val page = ThemePage().apply {
            id = 824716
            pagination.current = 168
        }
        val cachedHat = ThemePost().apply { id = 61638975 }
        val hasTopicHat = TopicHatAvailability.hasTopicHat(
                page = page,
                cachedHat = cachedHat,
                cachedHatTopicId = 824716,
                firstPageHatPostId = 61638975,
        )
        val state = ThemeToolbarMenuPolicy.resolve(
                pageLoaded = true,
                hasPoll = true,
                hasTopicHat = hasTopicHat,
                hatToolbarEnabled = true,
        )
        assertTrue(hasTopicHat)
        assertTrue(state.showCompactPoll)
        assertTrue(state.showCompactHat)
        assertFalse(state.showCompactRefresh)
        assertTrue(state.showOverflowRefresh)
    }

    @Test
    fun `compact poll shown whenever topic has poll`() {
        assertTrue(ThemeToolbarMenuPolicy.showCompactPollButton(hasPoll = true))
        assertFalse(ThemeToolbarMenuPolicy.showCompactPollButton(hasPoll = false))
    }

    @Test
    fun `compact hat shown when enabled`() {
        assertTrue(ThemeToolbarMenuPolicy.showCompactHatButton(hasTopicHat = true, hatToolbarEnabled = true))
        assertFalse(ThemeToolbarMenuPolicy.showCompactHatButton(hasTopicHat = true, hatToolbarEnabled = false))
    }
}
