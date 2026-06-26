package forpdateam.ru.forpda.presentation.theme

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * R-02 — One user gesture can dispatch navigation through three entry points
 * (handleUri, onPageStarted, openLink). The gesture-scoped guard must let exactly one dispatch
 * win per gesture and suppress the rest, while still allowing legitimate distinct navigations.
 */
class NavigationGestureDispatchGuardTest {

    @Test
    fun firstDispatchWins_secondSameGestureSuppressed() {
        val guard = NavigationGestureDispatchGuard()
        guard.beginGesture()
        // handleUri claims the gesture.
        assertTrue(guard.tryClaimDispatch())
        // onPageStarted for the SAME tap must be suppressed.
        assertFalse(guard.tryClaimDispatch())
        assertTrue(guard.isCurrentGestureDispatched())
    }

    @Test
    fun newGestureAllowsAnotherDispatch() {
        val guard = NavigationGestureDispatchGuard()
        guard.beginGesture()
        assertTrue(guard.tryClaimDispatch())
        assertFalse(guard.tryClaimDispatch())
        // A new user tap opens a fresh gesture window.
        guard.beginGesture()
        assertFalse(guard.isCurrentGestureDispatched())
        assertTrue(guard.tryClaimDispatch())
        assertFalse(guard.tryClaimDispatch())
    }

    @Test
    fun resetOnPageSettle_allowsNextDistinctNavigation() {
        val guard = NavigationGestureDispatchGuard()
        guard.beginGesture()
        assertTrue(guard.tryClaimDispatch())
        // A fresh loadData settles the previous gesture.
        guard.reset()
        assertFalse(guard.isCurrentGestureDispatched())
        assertTrue(guard.tryClaimDispatch())
    }

    @Test
    fun firstDispatchWithoutExplicitBeginGesture_isAllowed() {
        // Programmatic navigation that was not preceded by a source-anchor capture must still
        // dispatch once (initial token state).
        val guard = NavigationGestureDispatchGuard()
        assertFalse(guard.isCurrentGestureDispatched())
        assertTrue(guard.tryClaimDispatch())
        assertFalse(guard.tryClaimDispatch())
    }

    @Test
    fun tripleEntry_singleGesture_onlyOneClaim() {
        // Simulates handleUri + onPageStarted + openLink all firing for one tap.
        val guard = NavigationGestureDispatchGuard()
        guard.beginGesture()
        var dispatches = 0
        repeat(3) { if (guard.tryClaimDispatch()) dispatches++ }
        org.junit.Assert.assertEquals(1, dispatches)
    }
}
