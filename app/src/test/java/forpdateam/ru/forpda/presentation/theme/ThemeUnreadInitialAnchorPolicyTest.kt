package forpdateam.ru.forpda.presentation.theme

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeUnreadInitialAnchorPolicyTest {

    @Test
    fun deferHybridTopAutoload_whileUnreadAnchorPending_evenFarFromTop() {
        assertTrue(
                ThemeUnreadInitialAnchorPolicy.shouldDeferHybridTopAutoload(
                        unreadAnchorPending = true,
                        userScrolled = false,
                        nowMs = 0L,
                        initialTopSuppressedUntilMs = 0L,
                        scrollTop = 8000,
                        threshold = 800,
                        isUnderfilledInitialPage = false,
                )
        )
    }

    @Test
    fun deferHybridTopAutoload_whenNearTopDuringInitialSuppressWindow() {
        assertTrue(
                ThemeUnreadInitialAnchorPolicy.shouldDeferHybridTopAutoload(
                        unreadAnchorPending = false,
                        userScrolled = false,
                        nowMs = 1000L,
                        initialTopSuppressedUntilMs = 2000L,
                        scrollTop = 0,
                        threshold = 800,
                        isUnderfilledInitialPage = false,
                )
        )
    }

    @Test
    fun allowHybridTopAutoload_afterUserScrollDespitePendingUnreadFlag() {
        assertFalse(
                ThemeUnreadInitialAnchorPolicy.shouldDeferHybridTopAutoload(
                        unreadAnchorPending = true,
                        userScrolled = true,
                        nowMs = 0L,
                        initialTopSuppressedUntilMs = 5000L,
                        scrollTop = 0,
                        threshold = 800,
                        isUnderfilledInitialPage = false,
                )
        )
    }

    @Test
    fun allowHybridTopAutoload_whenSuppressWindowExpiredAndAnchorSettled() {
        assertFalse(
                ThemeUnreadInitialAnchorPolicy.shouldDeferHybridTopAutoload(
                        unreadAnchorPending = false,
                        userScrolled = false,
                        nowMs = 5000L,
                        initialTopSuppressedUntilMs = 2000L,
                        scrollTop = 5000,
                        threshold = 800,
                        isUnderfilledInitialPage = false,
                )
        )
    }

    @Test
    fun deferHybridTopAutoload_log1121483_fullLastPage_beforeInitialAnchorArms() {
        // Log 11_06: bootstrap+1450 fired requestInitialTop because 1400ms suppress expired before
        // INITIAL_ANCHOR armed unreadInitialAnchorPending; 3200ms window must block top autoload.
        assertTrue(
                ThemeUnreadInitialAnchorPolicy.shouldDeferHybridTopAutoload(
                        unreadAnchorPending = false,
                        userScrolled = false,
                        nowMs = 1500L,
                        initialTopSuppressedUntilMs = 3200L,
                        scrollTop = 0,
                        threshold = 800,
                        isUnderfilledInitialPage = false,
                )
        )
    }

    @Test
    fun deferHybridTopAutoload_onUnderfilledLastPage_whenInitialAnchorTargetWithinSuppressWindow() {
        // Log 11_06: underfilled last page bypassed 1400ms suppress; anchor target needs 3200ms guard.
        assertTrue(
                ThemeUnreadInitialAnchorPolicy.shouldDeferHybridTopAutoload(
                        unreadAnchorPending = false,
                        userScrolled = false,
                        nowMs = 1500L,
                        initialTopSuppressedUntilMs = 3200L,
                        scrollTop = 0,
                        threshold = 800,
                        isUnderfilledInitialPage = true,
                        hasInitialAnchorTarget = true,
                )
        )
    }

    @Test
    fun allowHybridTopAutoloadDespiteEndScrollPending_onUnderfilledLastPage() {
        assertTrue(
                ThemeUnreadInitialAnchorPolicy.shouldAllowTopAutoloadDespiteEndScrollPending(
                        isUnderfilledInitialPage = true
                )
        )
        assertFalse(
                ThemeUnreadInitialAnchorPolicy.shouldAllowTopAutoloadDespiteEndScrollPending(
                        isUnderfilledInitialPage = false
                )
        )
    }
}
