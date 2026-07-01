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
        val style = runCatching { dataStore.getAccentStyleImmediate() }
                .getOrDefault(Preferences.Main.AccentStyle.TONAL)
        val overlay = overlayFor(effective, style) ?: return
        activity.theme.applyStyle(overlay, true)
        if (BuildConfig.DEBUG) {
            Timber.tag(LOG_TAG).d("applied accent=%s (effective=%s style=%s) to %s",
                    accent, effective, style, activity.javaClass.simpleName)
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
     * [style] выбирает набор: приглушённый (TonalSpot), сочный (Vibrant) или
     * экспрессивный (Expressive).
     */
    internal fun overlayFor(
            accent: Preferences.Main.AccentPalette,
            style: Preferences.Main.AccentStyle = Preferences.Main.AccentStyle.TONAL,
    ): Int? {
        fun pick(tonal: Int, vibrant: Int, expressive: Int): Int = when (style) {
            Preferences.Main.AccentStyle.TONAL -> tonal
            Preferences.Main.AccentStyle.VIBRANT -> vibrant
            Preferences.Main.AccentStyle.EXPRESSIVE -> expressive
        }
        return when (accent) {
            Preferences.Main.AccentPalette.NEUTRAL -> null
            Preferences.Main.AccentPalette.CUSTOM -> null
            Preferences.Main.AccentPalette.BLUE -> pick(R.style.ThemeOverlay_ForPDA_Accent_Blue, R.style.ThemeOverlay_ForPDA_Accent_Blue_Vibrant, R.style.ThemeOverlay_ForPDA_Accent_Blue_Expressive)
            Preferences.Main.AccentPalette.INDIGO -> pick(R.style.ThemeOverlay_ForPDA_Accent_Indigo, R.style.ThemeOverlay_ForPDA_Accent_Indigo_Vibrant, R.style.ThemeOverlay_ForPDA_Accent_Indigo_Expressive)
            Preferences.Main.AccentPalette.VIOLET -> pick(R.style.ThemeOverlay_ForPDA_Accent_Violet, R.style.ThemeOverlay_ForPDA_Accent_Violet_Vibrant, R.style.ThemeOverlay_ForPDA_Accent_Violet_Expressive)
            Preferences.Main.AccentPalette.PURPLE -> pick(R.style.ThemeOverlay_ForPDA_Accent_Purple, R.style.ThemeOverlay_ForPDA_Accent_Purple_Vibrant, R.style.ThemeOverlay_ForPDA_Accent_Purple_Expressive)
            Preferences.Main.AccentPalette.PINK -> pick(R.style.ThemeOverlay_ForPDA_Accent_Pink, R.style.ThemeOverlay_ForPDA_Accent_Pink_Vibrant, R.style.ThemeOverlay_ForPDA_Accent_Pink_Expressive)
            Preferences.Main.AccentPalette.RED -> pick(R.style.ThemeOverlay_ForPDA_Accent_Red, R.style.ThemeOverlay_ForPDA_Accent_Red_Vibrant, R.style.ThemeOverlay_ForPDA_Accent_Red_Expressive)
            Preferences.Main.AccentPalette.DEEPORANGE -> pick(R.style.ThemeOverlay_ForPDA_Accent_DeepOrange, R.style.ThemeOverlay_ForPDA_Accent_DeepOrange_Vibrant, R.style.ThemeOverlay_ForPDA_Accent_DeepOrange_Expressive)
            Preferences.Main.AccentPalette.ORANGE -> pick(R.style.ThemeOverlay_ForPDA_Accent_Orange, R.style.ThemeOverlay_ForPDA_Accent_Orange_Vibrant, R.style.ThemeOverlay_ForPDA_Accent_Orange_Expressive)
            Preferences.Main.AccentPalette.AMBER -> pick(R.style.ThemeOverlay_ForPDA_Accent_Amber, R.style.ThemeOverlay_ForPDA_Accent_Amber_Vibrant, R.style.ThemeOverlay_ForPDA_Accent_Amber_Expressive)
            Preferences.Main.AccentPalette.GREEN -> pick(R.style.ThemeOverlay_ForPDA_Accent_Green, R.style.ThemeOverlay_ForPDA_Accent_Green_Vibrant, R.style.ThemeOverlay_ForPDA_Accent_Green_Expressive)
            Preferences.Main.AccentPalette.TEAL -> pick(R.style.ThemeOverlay_ForPDA_Accent_Teal, R.style.ThemeOverlay_ForPDA_Accent_Teal_Vibrant, R.style.ThemeOverlay_ForPDA_Accent_Teal_Expressive)
            Preferences.Main.AccentPalette.CYAN -> pick(R.style.ThemeOverlay_ForPDA_Accent_Cyan, R.style.ThemeOverlay_ForPDA_Accent_Cyan_Vibrant, R.style.ThemeOverlay_ForPDA_Accent_Cyan_Expressive)
        }
    }

    private const val LOG_TAG = "AccentApplier"
}
