package forpdateam.ru.forpda.presentation.theme

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeToolbarScrollPolicyTest {

    @Test
    fun shouldReactToScrollChange_byDefault() {
        assertTrue(ThemeToolbarScrollPolicy.shouldReactToScrollChange())
    }

    @Test
    fun shouldReactToScrollChange_suppressedDuringProgrammaticScroll() {
        assertFalse(
                ThemeToolbarScrollPolicy.shouldReactToScrollChange(
                        programmaticScrollSuppressed = true,
                )
        )
    }

    @Test
    fun isProgrammaticScrollSuppressed_trueWhileTimedWindowActive() {
        assertTrue(
                ThemeToolbarScrollPolicy.isProgrammaticScrollSuppressed(
                        nowUptimeMs = 12_000L,
                        suppressUntilMs = 15_000L,
                )
        )
    }

    @Test
    fun isProgrammaticScrollSuppressed_falseAfterTimedWindowExpires() {
        assertFalse(
                ThemeToolbarScrollPolicy.isProgrammaticScrollSuppressed(
                        nowUptimeMs = 16_000L,
                        suppressUntilMs = 15_000L,
                )
        )
    }

    @Test
    fun shouldReactToScrollChange_reactsWithoutUserScrollSignal() {
        // Oplus and similar OEM WebViews change scrollY without touch/inertia callbacks.
        assertTrue(
                ThemeToolbarScrollPolicy.shouldReactToScrollChange(
                        programmaticScrollSuppressed = false,
                )
        )
    }

    @Test
    fun resolveProgrammaticScrollSuppressed_stickyExpiresWithTimedWindow() {
        val resolution = ThemeToolbarScrollPolicy.resolveProgrammaticScrollSuppressed(
                nowUptimeMs = 20_000L,
                suppressUntilMs = 15_000L,
                stickySuppressActive = true,
        )

        assertFalse(resolution.suppressed)
        assertTrue(resolution.clearSticky)
    }

    @Test
    fun resolveProgrammaticScrollSuppressed_stickyActiveBeforeExpiry() {
        val resolution = ThemeToolbarScrollPolicy.resolveProgrammaticScrollSuppressed(
                nowUptimeMs = 12_000L,
                suppressUntilMs = 15_000L,
                stickySuppressActive = true,
        )

        assertTrue(resolution.suppressed)
        assertFalse(resolution.clearSticky)
    }

    @Test
    fun resolveProgrammaticScrollSuppressed_timedOnlyWithoutSticky() {
        val resolution = ThemeToolbarScrollPolicy.resolveProgrammaticScrollSuppressed(
                nowUptimeMs = 12_000L,
                suppressUntilMs = 15_000L,
                stickySuppressActive = false,
        )

        assertTrue(resolution.suppressed)
        assertFalse(resolution.clearSticky)
    }

    @Test
    fun isProgrammaticScrollSuppressed_falseWhenUntilUnset() {
        assertFalse(
                ThemeToolbarScrollPolicy.isProgrammaticScrollSuppressed(
                        nowUptimeMs = 12_000L,
                        suppressUntilMs = 0L,
                )
        )
    }

    @Test
    fun resolveProgrammaticScrollSuppressed_stickyActiveWithoutTimedWindow() {
        val resolution = ThemeToolbarScrollPolicy.resolveProgrammaticScrollSuppressed(
                nowUptimeMs = 12_000L,
                suppressUntilMs = 0L,
                stickySuppressActive = true,
        )

        assertTrue(resolution.suppressed)
        assertFalse(resolution.clearSticky)
    }

    @Test
    fun resolveProgrammaticScrollSuppressed_notSuppressedWhenBothInactive() {
        val resolution = ThemeToolbarScrollPolicy.resolveProgrammaticScrollSuppressed(
                nowUptimeMs = 12_000L,
                suppressUntilMs = 0L,
                stickySuppressActive = false,
        )

        assertFalse(resolution.suppressed)
        assertFalse(resolution.clearSticky)
    }
}
