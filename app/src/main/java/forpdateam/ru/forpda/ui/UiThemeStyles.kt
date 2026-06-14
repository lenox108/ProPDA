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

    fun effectivePalette(
            palette: Preferences.Main.UiPalette
    ): Preferences.Main.UiPalette = when {
        palette == Preferences.Main.UiPalette.CLASSIC_4PDA -> Preferences.Main.UiPalette.SYSTEM
        else -> palette
    }

    @StyleRes
    fun mainNoActionBar(
            palette: Preferences.Main.UiPalette,
            themeMode: Preferences.Main.ThemeMode = Preferences.Main.ThemeMode.SYSTEM,
            configuration: Configuration
    ): Int = when {
        useAmoledAppSkin(themeMode, configuration) &&
                effectivePalette(palette) == Preferences.Main.UiPalette.SEPIA_READING ->
            R.style.AmoledAppTheme_SepiaReading_NoActionBar
        useAmoledAppSkin(themeMode, configuration) &&
                effectivePalette(palette) == Preferences.Main.UiPalette.SEPIA_BLUE ->
            R.style.AmoledAppTheme_SepiaBlue_NoActionBar
        useAmoledAppSkin(themeMode, configuration) &&
                effectivePalette(palette) == Preferences.Main.UiPalette.MINIMAL_READER ->
            R.style.AmoledAppTheme_MinimalReader_NoActionBar
        useAmoledAppSkin(themeMode, configuration) -> R.style.AmoledAppTheme_NoActionBar
        effectivePalette(palette) == Preferences.Main.UiPalette.SEPIA_READING -> R.style.DayNightAppTheme_SepiaReading_NoActionBar
        effectivePalette(palette) == Preferences.Main.UiPalette.SEPIA_BLUE -> R.style.DayNightAppTheme_SepiaBlue_NoActionBar
        effectivePalette(palette) == Preferences.Main.UiPalette.MINIMAL_READER -> R.style.DayNightAppTheme_MinimalReader_NoActionBar
        else -> R.style.DayNightAppTheme_NoActionBar
    }

    @StyleRes
    fun settingsPreferenceScreen(
            palette: Preferences.Main.UiPalette,
            themeMode: Preferences.Main.ThemeMode = Preferences.Main.ThemeMode.SYSTEM,
            configuration: Configuration
    ): Int = when {
        useAmoledAppSkin(themeMode, configuration) &&
                effectivePalette(palette) == Preferences.Main.UiPalette.SEPIA_READING ->
            R.style.DayNightPreferenceTheme_AmoledSepiaReading
        useAmoledAppSkin(themeMode, configuration) &&
                effectivePalette(palette) == Preferences.Main.UiPalette.SEPIA_BLUE ->
            R.style.DayNightPreferenceTheme_AmoledSepiaBlue
        useAmoledAppSkin(themeMode, configuration) &&
                effectivePalette(palette) == Preferences.Main.UiPalette.MINIMAL_READER ->
            R.style.DayNightPreferenceTheme_AmoledMinimalReader
        useAmoledAppSkin(themeMode, configuration) -> R.style.AmoledAppTheme_NoActionBar
        effectivePalette(palette) == Preferences.Main.UiPalette.SEPIA_READING -> R.style.DayNightPreferenceTheme_SepiaReading
        effectivePalette(palette) == Preferences.Main.UiPalette.SEPIA_BLUE -> R.style.DayNightPreferenceTheme_SepiaBlue
        effectivePalette(palette) == Preferences.Main.UiPalette.MINIMAL_READER -> R.style.DayNightPreferenceTheme_MinimalReader
        else -> R.style.DayNightPreferenceTheme
    }
}
