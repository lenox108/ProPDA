package forpdateam.ru.forpda.ui

import android.app.Activity
import android.os.Build
import forpdateam.ru.forpda.BuildConfig
import forpdateam.ru.forpda.R
import forpdateam.ru.forpda.common.Preferences
import forpdateam.ru.forpda.model.datastore.MainDataStore
import forpdateam.ru.forpda.presentation.theme.AccentPolicy
import timber.log.Timber

/**
 * Накладывает выбранную курируемую акцент-палитру («смена цвета») на нативный UI.
 *
 * Работает на ВСЕХ API (26+): в отличие от Material You (wallpaper-based, только
 * API 31+), акцент — это заранее сгенерированные `@color/accent_*` ресурсы
 * (см. `AccentPaletteGenerator` + `colors_accents.xml`), наложенные простым
 * `theme.applyStyle(ThemeOverlay.ForPDA.Accent.*)`. Перекрываются только акцентные
 * M3-роли (primary/secondary/tertiary + контейнеры) — поверхности/фон остаются
 * нейтральными. Поскольку хром после Этапа C читает эти M3-роли, акцент виден на
 * кнопках/FAB/ссылках/чипах/переключателях/выделении.
 *
 * Должен вызываться из `Activity.onCreate()` ПОСЛЕ `setTheme(...)` и (важно)
 * ПОСЛЕ [MaterialYouApplier.applyIfEnabled] — они взаимоисключающи по
 * [AccentPolicy] (Material You имеет приоритет, когда реально доступен), поэтому
 * порядок не создаёт конфликта, но так безопаснее.
 */
object AccentApplier {

    fun applyIfEnabled(activity: Activity) {
        val dataStore = MainDataStore(activity)
        val accent = runCatching { dataStore.getAccentPaletteImmediate() }
                .getOrDefault(Preferences.Main.AccentPalette.NEUTRAL)
        val palette = runCatching { dataStore.getUiPaletteImmediate() }
                .getOrDefault(Preferences.Main.UiPalette.SYSTEM)
        val materialYouEnabled = runCatching { dataStore.getUseMaterialYouImmediate() }
                .getOrDefault(false)

        if (!AccentPolicy.shouldApply(accent, palette, materialYouEnabled, Build.VERSION.SDK_INT)) {
            return
        }
        val overlay = overlayFor(accent) ?: return
        activity.theme.applyStyle(overlay, true)
        if (BuildConfig.DEBUG) {
            Timber.tag(LOG_TAG).d("applied accent=%s to %s", accent, activity.javaClass.simpleName)
        }
    }

    /** Стиль-оверлей для палитры (см. `styles_accents.xml`). NEUTRAL → null. */
    internal fun overlayFor(accent: Preferences.Main.AccentPalette): Int? = when (accent) {
        Preferences.Main.AccentPalette.NEUTRAL -> null
        Preferences.Main.AccentPalette.BLUE -> R.style.ThemeOverlay_ForPDA_Accent_Blue
        Preferences.Main.AccentPalette.INDIGO -> R.style.ThemeOverlay_ForPDA_Accent_Indigo
        Preferences.Main.AccentPalette.VIOLET -> R.style.ThemeOverlay_ForPDA_Accent_Violet
        Preferences.Main.AccentPalette.PURPLE -> R.style.ThemeOverlay_ForPDA_Accent_Purple
        Preferences.Main.AccentPalette.PINK -> R.style.ThemeOverlay_ForPDA_Accent_Pink
        Preferences.Main.AccentPalette.RED -> R.style.ThemeOverlay_ForPDA_Accent_Red
        Preferences.Main.AccentPalette.DEEPORANGE -> R.style.ThemeOverlay_ForPDA_Accent_DeepOrange
        Preferences.Main.AccentPalette.ORANGE -> R.style.ThemeOverlay_ForPDA_Accent_Orange
        Preferences.Main.AccentPalette.AMBER -> R.style.ThemeOverlay_ForPDA_Accent_Amber
        Preferences.Main.AccentPalette.GREEN -> R.style.ThemeOverlay_ForPDA_Accent_Green
        Preferences.Main.AccentPalette.TEAL -> R.style.ThemeOverlay_ForPDA_Accent_Teal
        Preferences.Main.AccentPalette.CYAN -> R.style.ThemeOverlay_ForPDA_Accent_Cyan
    }

    private const val LOG_TAG = "AccentApplier"
}
