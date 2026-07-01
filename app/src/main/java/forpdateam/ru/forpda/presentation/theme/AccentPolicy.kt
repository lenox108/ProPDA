package forpdateam.ru.forpda.presentation.theme

import forpdateam.ru.forpda.common.Preferences as AppPreferences

/**
 * Единый источник правды для решения, ЧЕМ красить акцент нативного UI —
 * обоями (Material You), произвольным seed-цветом пользователя, курируемой
 * палитрой, или ничем. Чистая логика (без Android) ради юнит-тестов.
 *
 * Оба аппликатора сверяются с этим решением:
 * - [forpdateam.ru.forpda.ui.MaterialYouApplier] реализует WALLPAPER и CUSTOM_SEED
 *   (оба через DynamicColors — обои vs `setContentBasedSource(seed)`).
 * - [forpdateam.ru.forpda.ui.AccentApplier] реализует CURATED (наложение
 *   ThemeOverlay.ForPDA.Accent.*; для CUSTOM на API < 31 — ближайшая палитра).
 *
 * Приоритет: палитры чтения (Sepia/Minimal) самодостаточны → NONE; иначе
 * Material You (обои) если реально доступен (API 31+); иначе custom seed; иначе
 * курируемая палитра; иначе нейтраль.
 */
object AccentPolicy {

    enum class Mode {
        /** Ничего не накладывать (нейтраль / палитра чтения). */
        NONE,
        /** DynamicColors из обоев (Material You). */
        WALLPAPER,
        /** DynamicColors из произвольного seed-цвета (API 31+). */
        CUSTOM_SEED,
        /** Статический ThemeOverlay.ForPDA.Accent.* (курируемая палитра или снап custom на <31). */
        CURATED,
    }

    fun resolveMode(
            materialYouEnabled: Boolean,
            palette: AppPreferences.Main.UiPalette,
            accent: AppPreferences.Main.AccentPalette,
            sdkInt: Int
    ): Mode {
        if (palette != AppPreferences.Main.UiPalette.SYSTEM) return Mode.NONE
        val materialYouEffective = materialYouEnabled && sdkInt >= 31
        if (materialYouEffective) return Mode.WALLPAPER
        return when (accent) {
            AppPreferences.Main.AccentPalette.NEUTRAL -> Mode.NONE
            // Произвольный seed: динамика на API 31+, иначе снап к ближайшей палитре.
            AppPreferences.Main.AccentPalette.CUSTOM ->
                if (sdkInt >= 31) Mode.CUSTOM_SEED else Mode.CURATED
            else -> Mode.CURATED
        }
    }
}
