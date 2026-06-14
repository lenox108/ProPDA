package forpdateam.ru.forpda.presentation.theme

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeTabUnreadJumpPolicyTest {

    @Test
    fun skipsUnreadJumpForExplicitFindpostOpen() {
        assertFalse(
                ThemeTabUnreadJumpPolicy.shouldScheduleUnreadJumpOnTabFocus(
                        reloadUnreadOnTabFocus = true,
                        openedViaFindPostLink = true,
                        loadInFlight = false,
                        renderSettled = true,
                        pendingPostedPageScroll = false,
                )
        )
    }

    @Test
    fun skipsUnreadJumpWhileLoadOrRenderUnsettled() {
        assertFalse(
                ThemeTabUnreadJumpPolicy.shouldScheduleUnreadJumpOnTabFocus(
                        reloadUnreadOnTabFocus = true,
                        openedViaFindPostLink = false,
                        loadInFlight = true,
                        renderSettled = true,
                        pendingPostedPageScroll = false,
                )
        )
        assertFalse(
                ThemeTabUnreadJumpPolicy.shouldScheduleUnreadJumpOnTabFocus(
                        reloadUnreadOnTabFocus = true,
                        openedViaFindPostLink = false,
                        loadInFlight = false,
                        renderSettled = false,
                        pendingPostedPageScroll = false,
                )
        )
    }

    @Test
    fun skipsUnreadJumpDuringPostedPageScroll() {
        assertFalse(
                ThemeTabUnreadJumpPolicy.shouldScheduleUnreadJumpOnTabFocus(
                        reloadUnreadOnTabFocus = true,
                        openedViaFindPostLink = false,
                        loadInFlight = false,
                        renderSettled = true,
                        pendingPostedPageScroll = true,
                )
        )
    }

    @Test
    fun allowsUnreadJumpWhenTopicSettled() {
        assertTrue(
                ThemeTabUnreadJumpPolicy.shouldScheduleUnreadJumpOnTabFocus(
                        reloadUnreadOnTabFocus = true,
                        openedViaFindPostLink = false,
                        loadInFlight = false,
                        renderSettled = true,
                        pendingPostedPageScroll = false,
                )
        )
    }
}
