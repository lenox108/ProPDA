package forpdateam.ru.forpda.ui

import forpdateam.ru.forpda.common.DayNightHelper
import forpdateam.ru.forpda.common.Preferences
import forpdateam.ru.forpda.model.preferences.MainPreferencesHolder
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Test

class TemplateCssComposerCacheTest {

    private fun composer(
            dayNightHelper: DayNightHelper,
            paletteResolver: TemplatePaletteResolver,
    ): TemplateCssComposer {
        val mainPreferencesHolder = mockk<MainPreferencesHolder>(relaxed = true)
        return TemplateCssComposer(mockk<android.content.Context>(relaxed = true), mainPreferencesHolder, dayNightHelper, paletteResolver)
    }

    private fun lightNoPalette(): Pair<DayNightHelper, TemplatePaletteResolver> {
        val dayNightHelper = mockk<DayNightHelper>()
        val paletteResolver = mockk<TemplatePaletteResolver>()
        every { dayNightHelper.isNight() } returns false
        every { paletteResolver.isSepiaReading() } returns false
        every { paletteResolver.isSepiaBlue() } returns false
        every { paletteResolver.isMinimalReader() } returns false
        every { paletteResolver.isAmoled() } returns false
        every { paletteResolver.activePalette() } returns Preferences.Main.UiPalette.SYSTEM
        return dayNightHelper to paletteResolver
    }

    @Test
    fun compose_reusesCachedStringForSameConfiguration() {
        val (dayNightHelper, paletteResolver) = lightNoPalette()
        val composer = composer(dayNightHelper, paletteResolver)
        val first = composer.compose()
        val second = composer.compose()

        assertSame(first, second)
        assertEquals(first.hashCode(), composer.composeHash())
    }

    @Test
    fun compose_recomputesWhenNightModeChanges() {
        var night = false
        val dayNightHelper = mockk<DayNightHelper>()
        val paletteResolver = mockk<TemplatePaletteResolver>()
        every { paletteResolver.isSepiaReading() } returns true
        every { paletteResolver.isSepiaBlue() } returns false
        every { paletteResolver.isMinimalReader() } returns false
        every { paletteResolver.isAmoled() } returns false
        every { paletteResolver.activePalette() } returns Preferences.Main.UiPalette.SYSTEM
        every { dayNightHelper.isNight() } answers { night }

        val composer = composer(dayNightHelper, paletteResolver)
        val light = composer.compose()
        night = true
        val dark = composer.compose()

        assertNotEquals(light, dark)
    }

    @Test
    fun compose_recomputesWhenPaletteChanges() {
        var sepiaReading = false
        val dayNightHelper = mockk<DayNightHelper>()
        val paletteResolver = mockk<TemplatePaletteResolver>()
        every { dayNightHelper.isNight() } returns false
        every { paletteResolver.isSepiaBlue() } returns false
        every { paletteResolver.isMinimalReader() } returns false
        every { paletteResolver.isAmoled() } returns false
        every { paletteResolver.activePalette() } returns Preferences.Main.UiPalette.SYSTEM
        every { paletteResolver.isSepiaReading() } answers { sepiaReading }

        val composer = composer(dayNightHelper, paletteResolver)
        val plain = composer.compose()
        sepiaReading = true
        val sepia = composer.compose()

        assertNotEquals(plain, sepia)
    }
}
