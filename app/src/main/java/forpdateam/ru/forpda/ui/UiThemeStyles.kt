package forpdateam.ru.forpda.ui

import android.content.res.Configuration
import androidx.annotation.StyleRes
import forpdateam.ru.forpda.R
import forpdateam.ru.forpda.common.DayNightHelper
import forpdateam.ru.forpda.common.Preferences

object UiThemeStyles {

    private fun useAmoledAppSkin(
            themeMode: Preferences.Main.ThemeMode,
            configuration: Configuration
    ): Boolean = when (themeMode) {
        Preferences.Main.ThemeMode.AMOLED -> true
        Preferences.Main.ThemeMode.SYSTEM_AMOLED -> DayNightHelper.isUiModeNight(configuration)
        else -> false
    }

    /**
     * Kept as the single normalization point for the active palette. Historically it collapsed the
     * now-removed CLASSIC_4PDA into SYSTEM; today it is an identity, but callers keep routing through
     * it so any future palette aliasing has one home.
     */
    fun effectivePalette(
            palette: Preferences.Main.UiPalette
    ): Preferences.Main.UiPalette = palette

    /** Стили reading-палитры: обычный/AMOLED × NoActionBar/PreferenceScreen. */
    private data class PaletteThemes(
            @StyleRes val dayNoActionBar: Int,
            @StyleRes val amoledNoActionBar: Int,
            @StyleRes val dayPreference: Int,
            @StyleRes val amoledPreference: Int,
    )

    private val paletteThemes: Map<Preferences.Main.UiPalette, PaletteThemes> = mapOf(
            Preferences.Main.UiPalette.SEPIA_READING to PaletteThemes(
                    R.style.DayNightAppTheme_SepiaReading_NoActionBar, R.style.AmoledAppTheme_SepiaReading_NoActionBar,
                    R.style.DayNightPreferenceTheme_SepiaReading, R.style.DayNightPreferenceTheme_AmoledSepiaReading),
            Preferences.Main.UiPalette.SEPIA_BLUE to PaletteThemes(
                    R.style.DayNightAppTheme_SepiaBlue_NoActionBar, R.style.AmoledAppTheme_SepiaBlue_NoActionBar,
                    R.style.DayNightPreferenceTheme_SepiaBlue, R.style.DayNightPreferenceTheme_AmoledSepiaBlue),
            Preferences.Main.UiPalette.MINIMAL_READER to PaletteThemes(
                    R.style.DayNightAppTheme_MinimalReader_NoActionBar, R.style.AmoledAppTheme_MinimalReader_NoActionBar,
                    R.style.DayNightPreferenceTheme_MinimalReader, R.style.DayNightPreferenceTheme_AmoledMinimalReader),
            Preferences.Main.UiPalette.GREEN_CARE to PaletteThemes(
                    R.style.DayNightAppTheme_GreenCare_NoActionBar, R.style.AmoledAppTheme_GreenCare_NoActionBar,
                    R.style.DayNightPreferenceTheme_GreenCare, R.style.DayNightPreferenceTheme_AmoledGreenCare),
            Preferences.Main.UiPalette.NORD to PaletteThemes(
                    R.style.DayNightAppTheme_Nord_NoActionBar, R.style.AmoledAppTheme_Nord_NoActionBar,
                    R.style.DayNightPreferenceTheme_Nord, R.style.DayNightPreferenceTheme_AmoledNord),
            Preferences.Main.UiPalette.SOLARIZED to PaletteThemes(
                    R.style.DayNightAppTheme_Solarized_NoActionBar, R.style.AmoledAppTheme_Solarized_NoActionBar,
                    R.style.DayNightPreferenceTheme_Solarized, R.style.DayNightPreferenceTheme_AmoledSolarized),
            Preferences.Main.UiPalette.GRUVBOX to PaletteThemes(
                    R.style.DayNightAppTheme_Gruvbox_NoActionBar, R.style.AmoledAppTheme_Gruvbox_NoActionBar,
                    R.style.DayNightPreferenceTheme_Gruvbox, R.style.DayNightPreferenceTheme_AmoledGruvbox),
            Preferences.Main.UiPalette.ROSE_PINE to PaletteThemes(
                    R.style.DayNightAppTheme_RosePine_NoActionBar, R.style.AmoledAppTheme_RosePine_NoActionBar,
                    R.style.DayNightPreferenceTheme_RosePine, R.style.DayNightPreferenceTheme_AmoledRosePine),
            Preferences.Main.UiPalette.DRACULA to PaletteThemes(
                    R.style.DayNightAppTheme_Dracula_NoActionBar, R.style.AmoledAppTheme_Dracula_NoActionBar,
                    R.style.DayNightPreferenceTheme_Dracula, R.style.DayNightPreferenceTheme_AmoledDracula),
    )

    @StyleRes
    fun mainNoActionBar(
            palette: Preferences.Main.UiPalette,
            themeMode: Preferences.Main.ThemeMode = Preferences.Main.ThemeMode.SYSTEM,
            configuration: Configuration
    ): Int {
        val amoled = useAmoledAppSkin(themeMode, configuration)
        val themes = paletteThemes[effectivePalette(palette)]
        return when {
            themes != null -> if (amoled) themes.amoledNoActionBar else themes.dayNoActionBar
            amoled -> R.style.AmoledAppTheme_NoActionBar
            else -> R.style.DayNightAppTheme_NoActionBar
        }
    }

    @StyleRes
    fun settingsPreferenceScreen(
            palette: Preferences.Main.UiPalette,
            themeMode: Preferences.Main.ThemeMode = Preferences.Main.ThemeMode.SYSTEM,
            configuration: Configuration
    ): Int {
        val amoled = useAmoledAppSkin(themeMode, configuration)
        val themes = paletteThemes[effectivePalette(palette)]
        return when {
            themes != null -> if (amoled) themes.amoledPreference else themes.dayPreference
            amoled -> R.style.AmoledAppTheme_NoActionBar
            else -> R.style.DayNightPreferenceTheme
        }
    }
}
