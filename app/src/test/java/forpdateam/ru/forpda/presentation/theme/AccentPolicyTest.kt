package forpdateam.ru.forpda.presentation.theme

import forpdateam.ru.forpda.common.Preferences.Main.AccentPalette
import forpdateam.ru.forpda.common.Preferences.Main.UiPalette
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AccentPolicyTest {

    @Test
    fun `neutral never applies`() {
        assertFalse(AccentPolicy.shouldApply(AccentPalette.NEUTRAL, UiPalette.SYSTEM, false, 26))
        assertFalse(AccentPolicy.shouldApply(AccentPalette.NEUTRAL, UiPalette.SYSTEM, false, 34))
    }

    @Test
    fun `accent applies on SYSTEM palette when material you off`() {
        assertTrue(AccentPolicy.shouldApply(AccentPalette.BLUE, UiPalette.SYSTEM, false, 26))
        assertTrue(AccentPolicy.shouldApply(AccentPalette.GREEN, UiPalette.SYSTEM, false, 34))
    }

    @Test
    fun `reading palettes suppress accent`() {
        assertFalse(AccentPolicy.shouldApply(AccentPalette.BLUE, UiPalette.SEPIA_READING, false, 34))
        assertFalse(AccentPolicy.shouldApply(AccentPalette.BLUE, UiPalette.MINIMAL_READER, false, 34))
    }

    @Test
    fun `effective material you (api31+ enabled) wins over accent`() {
        assertFalse(AccentPolicy.shouldApply(AccentPalette.BLUE, UiPalette.SYSTEM, true, 31))
        assertFalse(AccentPolicy.shouldApply(AccentPalette.BLUE, UiPalette.SYSTEM, true, 34))
    }

    @Test
    fun `material you enabled but api under 31 is ineffective - accent still applies`() {
        assertTrue(AccentPolicy.shouldApply(AccentPalette.BLUE, UiPalette.SYSTEM, true, 26))
        assertTrue(AccentPolicy.shouldApply(AccentPalette.BLUE, UiPalette.SYSTEM, true, 30))
    }
}
