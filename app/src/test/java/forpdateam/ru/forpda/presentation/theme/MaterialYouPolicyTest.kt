package forpdateam.ru.forpda.presentation.theme

import forpdateam.ru.forpda.common.Preferences as AppPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MaterialYouPolicyTest {

    private val system = AppPreferences.Main.UiPalette.SYSTEM
    private val light = AppPreferences.Main.ThemeMode.LIGHT
    private val dark = AppPreferences.Main.ThemeMode.DARK
    private val amoled = AppPreferences.Main.ThemeMode.AMOLED
    private val systemAmoled = AppPreferences.Main.ThemeMode.SYSTEM_AMOLED

    @Test
    fun disabled_neverApplies_regardlessOfPalette() {
        AppPreferences.Main.UiPalette.values().forEach { palette ->
            assertFalse(
                    "palette=$palette",
                    MaterialYouPolicy.shouldApplyDynamicColors(enabled = false, palette = palette)
            )
        }
    }

    @Test
    fun enabled_appliesForSystemOnly() {
        assertTrue(
                MaterialYouPolicy.shouldApplyDynamicColors(
                        enabled = true,
                        palette = AppPreferences.Main.UiPalette.SYSTEM
                )
        )
    }

    @Test
    fun enabled_doesNotApplyForReadingPalettes() {
        listOf(
                AppPreferences.Main.UiPalette.SEPIA_READING,
                AppPreferences.Main.UiPalette.SEPIA_BLUE,
                AppPreferences.Main.UiPalette.MINIMAL_READER
        ).forEach { palette ->
            assertFalse(
                    "palette=$palette",
                    MaterialYouPolicy.shouldApplyDynamicColors(enabled = true, palette = palette)
            )
        }
    }

    // --- resolveMode ---

    @Test
    fun resolveMode_none_whenDisabled() {
        assertEquals(
                MaterialYouPolicy.Mode.NONE,
                MaterialYouPolicy.resolveMode(false, system, light, isNight = false)
        )
    }

    @Test
    fun resolveMode_none_forReadingPalettes() {
        listOf(
                AppPreferences.Main.UiPalette.SEPIA_READING,
                AppPreferences.Main.UiPalette.SEPIA_BLUE,
                AppPreferences.Main.UiPalette.MINIMAL_READER
        ).forEach { palette ->
            assertEquals(
                    "palette=$palette",
                    MaterialYouPolicy.Mode.NONE,
                    MaterialYouPolicy.resolveMode(true, palette, dark, isNight = true)
            )
        }
    }

    @Test
    fun resolveMode_surface_forSystemLightAndDark() {
        assertEquals(
                MaterialYouPolicy.Mode.SURFACE,
                MaterialYouPolicy.resolveMode(true, system, light, isNight = false)
        )
        assertEquals(
                MaterialYouPolicy.Mode.SURFACE,
                MaterialYouPolicy.resolveMode(true, system, dark, isNight = true)
        )
    }

    @Test
    fun resolveMode_accentOnly_forAmoled() {
        assertEquals(
                MaterialYouPolicy.Mode.ACCENT_ONLY,
                MaterialYouPolicy.resolveMode(true, system, amoled, isNight = true)
        )
    }

    @Test
    fun resolveMode_systemAmoled_accentOnlyAtNight_surfaceByDay() {
        assertEquals(
                "SYSTEM_AMOLED at night uses the pure-black AMOLED skin",
                MaterialYouPolicy.Mode.ACCENT_ONLY,
                MaterialYouPolicy.resolveMode(true, system, systemAmoled, isNight = true)
        )
        assertEquals(
                "SYSTEM_AMOLED by day falls back to the normal light skin",
                MaterialYouPolicy.Mode.SURFACE,
                MaterialYouPolicy.resolveMode(true, system, systemAmoled, isNight = false)
        )
    }
}
