package forpdateam.ru.forpda.ui

import forpdateam.ru.forpda.common.DayNightHelper
import forpdateam.ru.forpda.common.Preferences
import forpdateam.ru.forpda.model.preferences.MainPreferencesHolder
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins that AMOLED is a "palette-on-pure-black" MODE, not a palette of its own:
 * when a reading palette (Sepia / SepiaBlue / Minimal / new Nord…Dracula) is
 * active AND the theme mode is AMOLED, the GENERIC amoled layer
 * ([TemplateCssComposer.getAmoledOverrideCss]) must NOT run — the palette's own
 * AMOLED variant paints everything (black background + its own text/accent).
 *
 * Regression this guards: the generic layer used to run AFTER the palette in
 * `compose()`, and its `:root { --surface-* }` overrode the palette's variables,
 * so e.g. news-comment text in Sepia+AMOLED rendered generic white (#ffffff)
 * instead of the warm sepia cream (#F2E6D3). Reply/menu/link chips read
 * `--ppda-accent`, comment body reads `--surface-text-primary`; both must come
 * from the palette, not the muted generic amoled block.
 */
class TemplateCssComposerAmoledPaletteTest {

    /** Unique fingerprints of the generic [getAmoledOverrideCss] block. */
    private val genericAmoledSurfaceAccent = "--surface-accent: #9E9E9E;"
    private val genericAmoledPostSeparator = "box-shadow: 0 0 0 1px #1a1a1a"

    private fun compose(
            sepiaReading: Boolean = false,
            sepiaBlue: Boolean = false,
            minimalReader: Boolean = false,
            newPalette: Preferences.Main.UiPalette? = null,
            amoled: Boolean = true,
            night: Boolean = true,
    ): String {
        val dayNightHelper = mockk<DayNightHelper>()
        val paletteResolver = mockk<TemplatePaletteResolver>()
        every { dayNightHelper.isNight() } returns night
        every { paletteResolver.isSepiaReading() } returns sepiaReading
        every { paletteResolver.isSepiaBlue() } returns sepiaBlue
        every { paletteResolver.isMinimalReader() } returns minimalReader
        every { paletteResolver.isAmoled() } returns amoled
        every { paletteResolver.activePalette() } returns
                (newPalette ?: Preferences.Main.UiPalette.SYSTEM)
        val holder = mockk<MainPreferencesHolder>(relaxed = true)
        return TemplateCssComposer(
                mockk<android.content.Context>(relaxed = true), holder, dayNightHelper, paletteResolver
        ).compose()
    }

    @Test
    fun systemPalette_amoled_stillEmitsGenericAmoledLayer() {
        // Control: with the plain (system) palette AMOLED, the generic layer IS
        // the palette, so it must still be present.
        val css = compose()
        assertTrue(
                "System+AMOLED must still emit the generic amoled layer. CSS:\n$css",
                css.contains(genericAmoledSurfaceAccent) && css.contains(genericAmoledPostSeparator),
        )
    }

    @Test
    fun sepiaReading_amoled_paletteOwnsSurfaces_noGenericLeak() {
        val css = compose(sepiaReading = true)
        assertFalse(
                "Sepia+AMOLED must NOT leak the generic amoled --surface-accent (#9E9E9E), " +
                        "it would override the sepia accent. CSS:\n$css",
                css.contains(genericAmoledSurfaceAccent),
        )
        assertFalse(
                "Sepia+AMOLED must NOT leak the generic amoled #1a1a1a post separator. CSS:\n$css",
                css.contains(genericAmoledPostSeparator),
        )
        assertTrue(
                "Sepia+AMOLED comment body must resolve to the sepia AMOLED cream text " +
                        "(--surface-text-primary: #F2E6D3), not generic white. CSS:\n$css",
                css.contains("--surface-text-primary: #F2E6D3"),
        )
    }

    @Test
    fun sepiaBlue_and_minimal_amoled_noGenericLeak() {
        assertFalse(compose(sepiaBlue = true).contains(genericAmoledSurfaceAccent))
        assertFalse(compose(minimalReader = true).contains(genericAmoledSurfaceAccent))
    }

    @Test
    fun newReadingPalette_amoled_noGenericLeak() {
        // Nord is one of the new reading palettes; isNewReadingPalette() keys off
        // activePalette(), so pass it through.
        val css = compose(newPalette = Preferences.Main.UiPalette.NORD)
        assertFalse(
                "Nord+AMOLED must NOT leak the generic amoled --surface-accent (#9E9E9E). CSS:\n$css",
                css.contains(genericAmoledSurfaceAccent),
        )
    }
}
