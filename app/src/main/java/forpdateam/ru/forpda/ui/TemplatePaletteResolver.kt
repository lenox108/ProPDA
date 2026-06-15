package forpdateam.ru.forpda.ui

import forpdateam.ru.forpda.common.DayNightHelper
import forpdateam.ru.forpda.common.Preferences
import forpdateam.ru.forpda.model.preferences.MainPreferencesHolder

/**
 * Pure palette-resolution helpers extracted from [TemplateManager].
 *
 * Each method is a one-liner over [MainPreferencesHolder] + [DayNightHelper]
 * + [UiThemeStyles.effectivePalette]; together they encode the question
 * "is the active reading palette X?".
 *
 * No state of its own. Safe to construct per TemplateManager instance.
 */
class TemplatePaletteResolver(
        private val mainPreferencesHolder: MainPreferencesHolder,
        private val dayNightHelper: DayNightHelper,
) {
    fun isSepiaReading(): Boolean =
            UiThemeStyles.effectivePalette(mainPreferencesHolder.getUiPalette()) ==
                    Preferences.Main.UiPalette.SEPIA_READING

    fun isSepiaBlue(): Boolean =
            UiThemeStyles.effectivePalette(mainPreferencesHolder.getUiPalette()) ==
                    Preferences.Main.UiPalette.SEPIA_BLUE

    fun isMinimalReader(): Boolean =
            UiThemeStyles.effectivePalette(mainPreferencesHolder.getUiPalette()) ==
                    Preferences.Main.UiPalette.MINIMAL_READER

    fun isAmoled(): Boolean {
        if (!dayNightHelper.isNight()) return false
        return when (mainPreferencesHolder.getThemeMode()) {
            Preferences.Main.ThemeMode.AMOLED,
            Preferences.Main.ThemeMode.SYSTEM_AMOLED -> true
            else -> false
        }
    }
}
