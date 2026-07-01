package forpdateam.ru.forpda.presentation.theme

import forpdateam.ru.forpda.common.Preferences.Main.AccentPalette
import forpdateam.ru.forpda.common.Preferences.Main.UiPalette
import forpdateam.ru.forpda.presentation.theme.AccentPolicy.Mode
import org.junit.Assert.assertEquals
import org.junit.Test

class AccentPolicyTest {

    @Test
    fun `neutral never applies`() {
        assertEquals(Mode.NONE, AccentPolicy.resolveMode(false, UiPalette.SYSTEM, AccentPalette.NEUTRAL, 26))
        assertEquals(Mode.NONE, AccentPolicy.resolveMode(false, UiPalette.SYSTEM, AccentPalette.NEUTRAL, 34))
    }

    @Test
    fun `curated accent on SYSTEM when material you off`() {
        assertEquals(Mode.CURATED, AccentPolicy.resolveMode(false, UiPalette.SYSTEM, AccentPalette.BLUE, 26))
        assertEquals(Mode.CURATED, AccentPolicy.resolveMode(false, UiPalette.SYSTEM, AccentPalette.GREEN, 34))
    }

    @Test
    fun `reading palettes suppress accent`() {
        assertEquals(Mode.NONE, AccentPolicy.resolveMode(false, UiPalette.SEPIA_READING, AccentPalette.BLUE, 34))
        assertEquals(Mode.NONE, AccentPolicy.resolveMode(true, UiPalette.MINIMAL_READER, AccentPalette.CUSTOM, 34))
    }

    @Test
    fun `effective material you (api31+ enabled) wins over accent`() {
        assertEquals(Mode.WALLPAPER, AccentPolicy.resolveMode(true, UiPalette.SYSTEM, AccentPalette.BLUE, 31))
        assertEquals(Mode.WALLPAPER, AccentPolicy.resolveMode(true, UiPalette.SYSTEM, AccentPalette.CUSTOM, 34))
    }

    @Test
    fun `material you enabled but api under 31 is ineffective - accent still applies`() {
        assertEquals(Mode.CURATED, AccentPolicy.resolveMode(true, UiPalette.SYSTEM, AccentPalette.BLUE, 26))
        assertEquals(Mode.CURATED, AccentPolicy.resolveMode(true, UiPalette.SYSTEM, AccentPalette.GREEN, 30))
    }

    @Test
    fun `custom seed is dynamic on api31+ and snaps to curated below`() {
        assertEquals(Mode.CUSTOM_SEED, AccentPolicy.resolveMode(false, UiPalette.SYSTEM, AccentPalette.CUSTOM, 31))
        assertEquals(Mode.CUSTOM_SEED, AccentPolicy.resolveMode(false, UiPalette.SYSTEM, AccentPalette.CUSTOM, 34))
        // API < 31 → snap to nearest curated (CURATED mode, applier picks nearest).
        assertEquals(Mode.CURATED, AccentPolicy.resolveMode(false, UiPalette.SYSTEM, AccentPalette.CUSTOM, 26))
        assertEquals(Mode.CURATED, AccentPolicy.resolveMode(false, UiPalette.SYSTEM, AccentPalette.CUSTOM, 30))
    }
}
