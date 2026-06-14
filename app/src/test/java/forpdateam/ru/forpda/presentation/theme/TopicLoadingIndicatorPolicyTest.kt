package forpdateam.ru.forpda.presentation.theme

import forpdateam.ru.forpda.presentation.theme.TopicLoadingIndicatorPolicy.Indicator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class TopicLoadingIndicatorPolicyTest {

    @Test
    fun `not refreshing shows no indicator`() {
        assertEquals(Indicator.NONE, TopicLoadingIndicatorPolicy.resolve(isRefreshing = false, isPageLoaded = false))
        assertEquals(Indicator.NONE, TopicLoadingIndicatorPolicy.resolve(isRefreshing = false, isPageLoaded = true))
    }

    @Test
    fun `initial open uses content indicator`() {
        assertEquals(Indicator.CONTENT, TopicLoadingIndicatorPolicy.resolve(isRefreshing = true, isPageLoaded = false))
    }

    @Test
    fun `refresh of loaded topic uses swipe refresh`() {
        assertEquals(Indicator.SWIPE_REFRESH, TopicLoadingIndicatorPolicy.resolve(isRefreshing = true, isPageLoaded = true))
    }

    @Test
    fun `toolbar progress is never shown for topics`() {
        // Single-source contract: toolbar progress must never duplicate the content / swipe indicator.
        assertFalse(TopicLoadingIndicatorPolicy.showsToolbarProgress(isRefreshing = true, isPageLoaded = false))
        assertFalse(TopicLoadingIndicatorPolicy.showsToolbarProgress(isRefreshing = true, isPageLoaded = true))
        assertFalse(TopicLoadingIndicatorPolicy.showsToolbarProgress(isRefreshing = false, isPageLoaded = false))
        assertFalse(TopicLoadingIndicatorPolicy.showsToolbarProgress(isRefreshing = false, isPageLoaded = true))
    }

    @Test
    fun `resolve never selects a duplicate-prone indicator combination`() {
        // Exactly one indicator per state; CONTENT and SWIPE_REFRESH are mutually exclusive by phase.
        assertEquals(Indicator.CONTENT, TopicLoadingIndicatorPolicy.resolve(true, false))
        assertEquals(Indicator.SWIPE_REFRESH, TopicLoadingIndicatorPolicy.resolve(true, true))
    }
}
