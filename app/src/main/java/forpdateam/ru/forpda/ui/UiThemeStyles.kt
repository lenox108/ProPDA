package forpdateam.ru.forpda.ui

import androidx.annotation.StyleRes
import forpdateam.ru.forpda.R
import forpdateam.ru.forpda.common.Preferences

object UiThemeStyles {

    @StyleRes
    fun mainNoActionBar(palette: Preferences.Main.UiPalette): Int = when (palette) {
        Preferences.Main.UiPalette.CLASSIC_4PDA -> R.style.DayNightAppTheme_Classic4pda_NoActionBar
        Preferences.Main.UiPalette.SYSTEM -> R.style.DayNightAppTheme_NoActionBar
    }

    @StyleRes
    fun settingsPreferenceScreen(palette: Preferences.Main.UiPalette): Int = when (palette) {
        Preferences.Main.UiPalette.CLASSIC_4PDA -> R.style.DayNightPreferenceTheme_Classic4pda
        Preferences.Main.UiPalette.SYSTEM -> R.style.DayNightPreferenceTheme
    }
}
