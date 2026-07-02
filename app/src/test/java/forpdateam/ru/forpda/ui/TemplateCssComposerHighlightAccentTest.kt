package forpdateam.ru.forpda.ui

import forpdateam.ru.forpda.common.DayNightHelper
import forpdateam.ru.forpda.common.Preferences
import forpdateam.ru.forpda.model.preferences.MainPreferencesHolder
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the contract that the topic "where I stopped" highlight's accent
 * (`--ppda-accent`, see `.post_container.ppda_highlight_post` in the shipped
 * `*_themes.css`) is defined for EVERY active palette/mode.
 *
 * Why this matters: the highlight's `box-shadow` and `background` resolve the
 * accent via `var(--ppda-accent, var(--surface-accent, <terminal fallback>))`.
 * Historically `--ppda-accent` was only set on the system path, while sepia /
 * sepia-blue / minimal / amoled were expected to provide the accent via
 * `--surface-accent`. That left the cascade brittle: any future regression in
 * the override composers would silently drop the highlight colour all the way
 * to the terminal fallback. Worse, `--surface-accent` is intentionally absent
 * on the system path, so the highlight colour was effectively hard-coded blue
 * in system mode and palette-specific everywhere else — inconsistent and a
 * maintenance trap.
 *
 * The fix unconditionally sets `--ppda-accent` in
 * `TemplateCssComposer.getThemeLayoutSafetyCss()`, picking a palette-aware
 * colour for each branch. These tests pin that invariant for every palette and
 * for both light and dark night modes; if any branch forgets to emit
 * `--ppda-accent`, the on-device highlight loses its colour in that palette.
 */
class TemplateCssComposerHighlightAccentTest {

    private data class PaletteMode(
            val name: String,
            val sepiaReading: Boolean,
            val sepiaBlue: Boolean,
            val minimalReader: Boolean,
            val amoled: Boolean,
            val night: Boolean,
    )

    private val allPaletteModes: List<PaletteMode> = buildList {
        for (night in listOf(false, true)) {
            add(PaletteMode("system_${if (night) "dark" else "light"}",
                    sepiaReading = false, sepiaBlue = false,
                    minimalReader = false, amoled = false, night = night))
            add(PaletteMode("sepia_reading_${if (night) "dark" else "light"}",
                    sepiaReading = true, sepiaBlue = false,
                    minimalReader = false, amoled = false, night = night))
            add(PaletteMode("sepia_blue_${if (night) "dark" else "light"}",
                    sepiaReading = false, sepiaBlue = true,
                    minimalReader = false, amoled = false, night = night))
            add(PaletteMode("minimal_reader_${if (night) "dark" else "light"}",
                    sepiaReading = false, sepiaBlue = false,
                    minimalReader = true, amoled = false, night = night))
            // AMOLED only fires at night, but pin the light-mode pass-through
            // too (where isAmoled()==false) to guard against a regression that
            // would accidentally activate AMOLED in the day.
            add(PaletteMode("amoled_${if (night) "dark" else "light"}",
                    sepiaReading = false, sepiaBlue = false,
                    minimalReader = false, amoled = night, night = night))
        }
    }

    private fun compose(mode: PaletteMode): String {
        val dayNightHelper = mockk<DayNightHelper>()
        val paletteResolver = mockk<TemplatePaletteResolver>()
        every { dayNightHelper.isNight() } returns mode.night
        every { paletteResolver.isSepiaReading() } returns mode.sepiaReading
        every { paletteResolver.isSepiaBlue() } returns mode.sepiaBlue
        every { paletteResolver.isMinimalReader() } returns mode.minimalReader
        every { paletteResolver.isAmoled() } returns mode.amoled
        every { paletteResolver.activePalette() } returns Preferences.Main.UiPalette.SYSTEM
        val mainPreferencesHolder = mockk<MainPreferencesHolder>(relaxed = true)
        return TemplateCssComposer(mockk<android.content.Context>(relaxed = true), mainPreferencesHolder, dayNightHelper, paletteResolver).compose()
    }

    // G2: с выбранным курируемым акцентом контент форума следует за ним —
    // --ppda-accent должен стать цветом акцента, а НЕ статическим системным
    // дефолтом (#2177AF light / #78B8E6 dark).
    @Test
    fun compose_ppdaAccentFollowsSelectedCuratedAccent() {
        val dayNightHelper = mockk<DayNightHelper>()
        val paletteResolver = mockk<TemplatePaletteResolver>()
        every { dayNightHelper.isNight() } returns false
        every { paletteResolver.isSepiaReading() } returns false
        every { paletteResolver.isSepiaBlue() } returns false
        every { paletteResolver.isMinimalReader() } returns false
        every { paletteResolver.isAmoled() } returns false
        every { paletteResolver.activePalette() } returns Preferences.Main.UiPalette.SYSTEM
        val holder = mockk<MainPreferencesHolder>(relaxed = true)
        every { holder.getUseMaterialYou() } returns false
        every { holder.getAccentPalette() } returns
                forpdateam.ru.forpda.common.Preferences.Main.AccentPalette.RED

        val css = TemplateCssComposer(
                mockk<android.content.Context>(relaxed = true), holder, dayNightHelper, paletteResolver
        ).compose()

        val match = Regex("--ppda-accent\\s*:\\s*(#[0-9A-Fa-f]{6})\\s*;").find(css)
        assertTrue("--ppda-accent must be present and hex. CSS:\n$css", match != null)
        val hex = match!!.groupValues[1].uppercase()
        assertTrue(
                "--ppda-accent should follow the selected RED accent, not the static system default. Got $hex",
                hex != "#2177AF" && hex != "#78B8E6",
        )
    }

    // H1/Expressive: стиль акцента меняет тон акцента контста форума
    // (TonalSpot vs Vibrant vs Expressive — три разных схемы M3 из одного seed).
    @Test
    fun compose_ppdaAccentDiffersBetweenAccentStyles() {
        fun accentHexFor(style: forpdateam.ru.forpda.common.Preferences.Main.AccentStyle): String {
            val dayNightHelper = mockk<DayNightHelper>()
            val paletteResolver = mockk<TemplatePaletteResolver>()
            every { dayNightHelper.isNight() } returns false
            every { paletteResolver.isSepiaReading() } returns false
            every { paletteResolver.isSepiaBlue() } returns false
            every { paletteResolver.isMinimalReader() } returns false
            every { paletteResolver.isAmoled() } returns false
            every { paletteResolver.activePalette() } returns Preferences.Main.UiPalette.SYSTEM
            val holder = mockk<MainPreferencesHolder>(relaxed = true)
            every { holder.getUseMaterialYou() } returns false
            every { holder.getAccentPalette() } returns
                    forpdateam.ru.forpda.common.Preferences.Main.AccentPalette.RED
            every { holder.getAccentStyle() } returns style
            val css = TemplateCssComposer(
                    mockk<android.content.Context>(relaxed = true), holder, dayNightHelper, paletteResolver
            ).compose()
            return Regex("--ppda-accent\\s*:\\s*(#[0-9A-Fa-f]{6})\\s*;").find(css)!!.groupValues[1].uppercase()
        }
        val tonal = accentHexFor(forpdateam.ru.forpda.common.Preferences.Main.AccentStyle.TONAL)
        val vibrant = accentHexFor(forpdateam.ru.forpda.common.Preferences.Main.AccentStyle.VIBRANT)
        val expressive = accentHexFor(forpdateam.ru.forpda.common.Preferences.Main.AccentStyle.EXPRESSIVE)
        assertTrue("Vibrant accent must differ from muted (TonalSpot)", tonal != vibrant)
        assertTrue("Expressive accent must differ from TonalSpot", tonal != expressive)
        assertTrue("Expressive accent must differ from Vibrant", vibrant != expressive)
    }

    @Test
    fun compose_definesPpdaAccentInEveryPaletteAndMode() {
        for (mode in allPaletteModes) {
            val css = compose(mode)
            assertTrue(
                    "Palette/mode '${mode.name}' must define --ppda-accent in the composed CSS, " +
                            "otherwise the topic highlight ring has no colour (regression " +
                            "\"highlight works only in system palette\"). Composed CSS:\n$css",
                    Regex("--ppda-accent\\s*:").containsMatchIn(css),
            )
        }
    }

    @Test
    fun compose_ppdaAccentValueIsAValidHexColourInEveryPaletteAndMode() {
        // Reject empty / `none` / `transparent` values, which would silently
        // invalidate the whole `box-shadow` (a `none` colour is a valid CSS
        // value that disables the shadow entirely) and the `background` tint.
        val validHex = Regex("--ppda-accent\\s*:\\s*#[0-9A-Fa-f]{6}\\s*;")
        for (mode in allPaletteModes) {
            val css = compose(mode)
            assertTrue(
                    "Palette/mode '${mode.name}' must define --ppda-accent as a 6-digit #RRGGBB hex " +
                            "(transparent/none/empty would disable the box-shadow or invalidate " +
                            "the color-mix background). Composed CSS:\n$css",
                    validHex.containsMatchIn(css),
            )
        }
    }
}
