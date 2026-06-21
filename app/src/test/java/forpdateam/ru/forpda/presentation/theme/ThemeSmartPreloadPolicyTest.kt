package forpdateam.ru.forpda.presentation.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeSmartPreloadPolicyTest {

    private fun input(
            featureEnabled: Boolean = true,
            slowModeEnabled: Boolean = false,
            currentTopicId: Int = 100,
            currentPage: Int = 2,
            totalPages: Int = 10,
            scrollFraction: Float = 0.8f,
            isRefreshing: Boolean = false,
            isTopicOpening: Boolean = false,
            isPreloadInFlight: Boolean = false,
            nextPageAlreadyAvailable: Boolean = false,
            consecutiveFailures: Int = 0,
            threshold: Float = ThemeSmartPreloadPolicy.DEFAULT_PRELOAD_THRESHOLD,
    ) = ThemeSmartPreloadPolicy.Input(
            featureEnabled = featureEnabled,
            slowModeEnabled = slowModeEnabled,
            currentTopicId = currentTopicId,
            currentPage = currentPage,
            totalPages = totalPages,
            scrollFraction = scrollFraction,
            isRefreshing = isRefreshing,
            isTopicOpening = isTopicOpening,
            isPreloadInFlight = isPreloadInFlight,
            nextPageAlreadyAvailable = nextPageAlreadyAvailable,
            consecutiveFailures = consecutiveFailures,
            threshold = threshold,
    )

    @Test
    fun preloadStartsAtThreshold() {
        val d = ThemeSmartPreloadPolicy.decide(input(scrollFraction = 0.8f))
        assertEquals(ThemeSmartPreloadPolicy.Decision.START, d)
        assertTrue(ThemeSmartPreloadPolicy.shouldStartPreload(input(scrollFraction = 0.75f)))
    }

    @Test
    fun preloadDoesNotStartBelowThreshold() {
        val d = ThemeSmartPreloadPolicy.decide(input(scrollFraction = 0.5f))
        assertEquals(ThemeSmartPreloadPolicy.Decision.BELOW_THRESHOLD, d)
        assertFalse(ThemeSmartPreloadPolicy.shouldStartPreload(input(scrollFraction = 0.74f)))
    }

    @Test
    fun preloadDisabledByKillSwitch() {
        assertEquals(
                ThemeSmartPreloadPolicy.Decision.DISABLED_BY_KILL_SWITCH,
                ThemeSmartPreloadPolicy.decide(input(featureEnabled = false)),
        )
    }

    @Test
    fun preloadDisabledInSlowMode() {
        assertEquals(
                ThemeSmartPreloadPolicy.Decision.DISABLED_BY_SLOW_MODE,
                ThemeSmartPreloadPolicy.decide(input(slowModeEnabled = true)),
        )
    }

    @Test
    fun preloadBlockedDuringRefresh() {
        assertEquals(
                ThemeSmartPreloadPolicy.Decision.BLOCKED_REFRESHING,
                ThemeSmartPreloadPolicy.decide(input(isRefreshing = true)),
        )
    }

    @Test
    fun preloadBlockedWhileTopicOpening() {
        assertEquals(
                ThemeSmartPreloadPolicy.Decision.BLOCKED_TOPIC_OPENING,
                ThemeSmartPreloadPolicy.decide(input(isTopicOpening = true)),
        )
    }

    @Test
    fun preloadBlockedWhenAlreadyInFlight() {
        assertEquals(
                ThemeSmartPreloadPolicy.Decision.BLOCKED_IN_FLIGHT,
                ThemeSmartPreloadPolicy.decide(input(isPreloadInFlight = true)),
        )
    }

    @Test
    fun preloadBlockedWhenNoNextPage() {
        // Current page is the last page.
        assertEquals(
                ThemeSmartPreloadPolicy.Decision.ALREADY_LAST_PAGE,
                ThemeSmartPreloadPolicy.decide(input(currentPage = 10, totalPages = 10)),
        )
        assertNull(ThemeSmartPreloadPolicy.nextPageToPreload(input(currentPage = 10, totalPages = 10)))
    }

    @Test
    fun preloadBlockedAfterRepeatedFailures() {
        assertEquals(
                ThemeSmartPreloadPolicy.Decision.BLOCKED_REPEATED_FAILURES,
                ThemeSmartPreloadPolicy.decide(
                        input(consecutiveFailures = ThemeSmartPreloadPolicy.MAX_CONSECUTIVE_FAILURES),
                ),
        )
    }

    @Test
    fun preloadSkippedWhenNextPageAlreadyAvailable() {
        assertEquals(
                ThemeSmartPreloadPolicy.Decision.NEXT_PAGE_ALREADY_AVAILABLE,
                ThemeSmartPreloadPolicy.decide(input(nextPageAlreadyAvailable = true)),
        )
    }

    @Test
    fun preloadBlockedForInvalidTopic() {
        assertEquals(
                ThemeSmartPreloadPolicy.Decision.NO_VALID_TOPIC,
                ThemeSmartPreloadPolicy.decide(input(currentTopicId = 0)),
        )
    }

    @Test
    fun nextPageIsExactlyOneAhead() {
        assertEquals(3, ThemeSmartPreloadPolicy.nextPageToPreload(input(currentPage = 2, totalPages = 10)))
        assertEquals(10, ThemeSmartPreloadPolicy.nextPageToPreload(input(currentPage = 9, totalPages = 10)))
    }

    @Test
    fun staleTopicPreloadResultIsIgnored() {
        // Result for the topic we requested while still on it: usable.
        assertTrue(
                ThemeSmartPreloadPolicy.isPreloadResultUsable(
                        requestedTopicId = 100,
                        requestedPage = 3,
                        resultTopicId = 100,
                        resultPage = 3,
                        currentTopicId = 100,
                ),
        )
        // User switched topics before the result arrived: not usable.
        assertFalse(
                ThemeSmartPreloadPolicy.isPreloadResultUsable(
                        requestedTopicId = 100,
                        requestedPage = 3,
                        resultTopicId = 100,
                        resultPage = 3,
                        currentTopicId = 200,
                ),
        )
        // Server returned a different page than requested: not usable.
        assertFalse(
                ThemeSmartPreloadPolicy.isPreloadResultUsable(
                        requestedTopicId = 100,
                        requestedPage = 3,
                        resultTopicId = 100,
                        resultPage = 4,
                        currentTopicId = 100,
                ),
        )
        // Result topic mismatched the requested topic: not usable.
        assertFalse(
                ThemeSmartPreloadPolicy.isPreloadResultUsable(
                        requestedTopicId = 100,
                        requestedPage = 3,
                        resultTopicId = 999,
                        resultPage = 3,
                        currentTopicId = 100,
                ),
        )
    }

    @Test
    fun killSwitchTakesPriorityOverEveryOtherBlock() {
        // Even with many blocks set, kill switch is reported first (cheapest hard stop).
        val d = ThemeSmartPreloadPolicy.decide(
                input(
                        featureEnabled = false,
                        slowModeEnabled = true,
                        isRefreshing = true,
                        currentPage = 10,
                        totalPages = 10,
                ),
        )
        assertEquals(ThemeSmartPreloadPolicy.Decision.DISABLED_BY_KILL_SWITCH, d)
    }
}
