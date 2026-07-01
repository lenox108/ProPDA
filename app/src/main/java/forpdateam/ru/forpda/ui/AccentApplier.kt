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

        // Только CURATED достаётся этому аппликатору (WALLPAPER/CUSTOM_SEED —
        // MaterialYouApplier, NONE — ничего). См. AccentPolicy.
        if (AccentPolicy.resolveMode(materialYouEnabled, palette, accent, Build.VERSION.SDK_INT)
                != AccentPolicy.Mode.CURATED) {
            return
        }
        // CUSTOM на API < 31: снап произвольного seed к ближайшей курируемой
        // палитре (нативную инъекцию произвольного цвета ниже 31 сделать нельзя).
        val effective = if (accent == Preferences.Main.AccentPalette.CUSTOM) {
            val seed = runCatching { dataStore.getAccentCustomColorImmediate() }.getOrDefault(0xFF1976D2.toInt())
            nearestCuratedPalette(seed)
        } else {
            accent
        }
        val vibrant = runCatching { dataStore.getAccentVibrantImmediate() }.getOrDefault(false)
        val overlay = overlayFor(effective, vibrant) ?: return
        activity.theme.applyStyle(overlay, true)
        if (BuildConfig.DEBUG) {
            Timber.tag(LOG_TAG).d("applied accent=%s (effective=%s vibrant=%s) to %s",
                    accent, effective, vibrant, activity.javaClass.simpleName)
        }
    }

    /** Seed-цвета курируемых палитр — синхронно с AccentPaletteGenerator. */
    private val curatedSeeds: List<Pair<Preferences.Main.AccentPalette, Int>> = listOf(
            Preferences.Main.AccentPalette.BLUE to 0xFF0B57D0.toInt(),
            Preferences.Main.AccentPalette.INDIGO to 0xFF4355B9.toInt(),
            Preferences.Main.AccentPalette.VIOLET to 0xFF7B4FCF.toInt(),
            Preferences.Main.AccentPalette.PURPLE to 0xFF9C27B0.toInt(),
            Preferences.Main.AccentPalette.PINK to 0xFFC2185B.toInt(),
            Preferences.Main.AccentPalette.RED to 0xFFD32F2F.toInt(),
            Preferences.Main.AccentPalette.DEEPORANGE to 0xFFE64A19.toInt(),
            Preferences.Main.AccentPalette.ORANGE to 0xFFEF6C00.toInt(),
            Preferences.Main.AccentPalette.AMBER to 0xFFFF8F00.toInt(),
            Preferences.Main.AccentPalette.GREEN to 0xFF2E7D32.toInt(),
            Preferences.Main.AccentPalette.TEAL to 0xFF00796B.toInt(),
            Preferences.Main.AccentPalette.CYAN to 0xFF0097A7.toInt(),
    )

    /** Ближайшая курируемая палитра к произвольному seed по HCT-hue (для API < 31). */
    internal fun nearestCuratedPalette(seed: Int): Preferences.Main.AccentPalette {
        val seedHue = com.google.android.material.color.utilities.Hct.fromInt(seed).hue
        return curatedSeeds.minByOrNull { hueDistance(seedHue, com.google.android.material.color.utilities.Hct.fromInt(it.second).hue) }
                ?.first ?: Preferences.Main.AccentPalette.BLUE
    }

    private fun hueDistance(a: Double, b: Double): Double {
        val d = Math.abs(a - b) % 360.0
        return if (d > 180.0) 360.0 - d else d
    }

    /**
     * Стиль-оверлей для палитры (см. `styles_accents.xml`). NEUTRAL/CUSTOM → null.
     * [vibrant] выбирает сочный вариант (Vibrant) вместо приглушённого (TonalSpot).
     */
    internal fun overlayFor(accent: Preferences.Main.AccentPalette, vibrant: Boolean = false): Int? = when (accent) {
        Preferences.Main.AccentPalette.NEUTRAL -> null
        Preferences.Main.AccentPalette.CUSTOM -> null
        Preferences.Main.AccentPalette.BLUE -> if (vibrant) R.style.ThemeOverlay_ForPDA_Accent_Blue_Vibrant else R.style.ThemeOverlay_ForPDA_Accent_Blue
        Preferences.Main.AccentPalette.INDIGO -> if (vibrant) R.style.ThemeOverlay_ForPDA_Accent_Indigo_Vibrant else R.style.ThemeOverlay_ForPDA_Accent_Indigo
        Preferences.Main.AccentPalette.VIOLET -> if (vibrant) R.style.ThemeOverlay_ForPDA_Accent_Violet_Vibrant else R.style.ThemeOverlay_ForPDA_Accent_Violet
        Preferences.Main.AccentPalette.PURPLE -> if (vibrant) R.style.ThemeOverlay_ForPDA_Accent_Purple_Vibrant else R.style.ThemeOverlay_ForPDA_Accent_Purple
        Preferences.Main.AccentPalette.PINK -> if (vibrant) R.style.ThemeOverlay_ForPDA_Accent_Pink_Vibrant else R.style.ThemeOverlay_ForPDA_Accent_Pink
        Preferences.Main.AccentPalette.RED -> if (vibrant) R.style.ThemeOverlay_ForPDA_Accent_Red_Vibrant else R.style.ThemeOverlay_ForPDA_Accent_Red
        Preferences.Main.AccentPalette.DEEPORANGE -> if (vibrant) R.style.ThemeOverlay_ForPDA_Accent_DeepOrange_Vibrant else R.style.ThemeOverlay_ForPDA_Accent_DeepOrange
        Preferences.Main.AccentPalette.ORANGE -> if (vibrant) R.style.ThemeOverlay_ForPDA_Accent_Orange_Vibrant else R.style.ThemeOverlay_ForPDA_Accent_Orange
        Preferences.Main.AccentPalette.AMBER -> if (vibrant) R.style.ThemeOverlay_ForPDA_Accent_Amber_Vibrant else R.style.ThemeOverlay_ForPDA_Accent_Amber
        Preferences.Main.AccentPalette.GREEN -> if (vibrant) R.style.ThemeOverlay_ForPDA_Accent_Green_Vibrant else R.style.ThemeOverlay_ForPDA_Accent_Green
        Preferences.Main.AccentPalette.TEAL -> if (vibrant) R.style.ThemeOverlay_ForPDA_Accent_Teal_Vibrant else R.style.ThemeOverlay_ForPDA_Accent_Teal
        Preferences.Main.AccentPalette.CYAN -> if (vibrant) R.style.ThemeOverlay_ForPDA_Accent_Cyan_Vibrant else R.style.ThemeOverlay_ForPDA_Accent_Cyan
    }

    private const val LOG_TAG = "AccentApplier"
}
