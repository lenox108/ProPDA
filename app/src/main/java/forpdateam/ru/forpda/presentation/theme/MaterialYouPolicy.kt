package forpdateam.ru.forpda.presentation.theme

import forpdateam.ru.forpda.common.Preferences as AppPreferences

/**
 * Чистая логика решения, применять ли динамические цвета (Material You) к
 * нативному UI.
 *
 * Вынесено отдельно от [forpdateam.ru.forpda.ui.MaterialYouApplier], чтобы
 * условие было юнит-тестируемым без Android-зависимостей (DataStore/Activity).
 *
 * Правило: динамические цвета включаются только если тумблер «Цвета системы
 * (Material You)» включён И выбрана системная палитра (SYSTEM). Палитры чтения
 * (SEPIA_*, MINIMAL_READER) имеют приоритет для контента, поэтому динамические
 * цвета к ним не применяются.
 *
 * Степень применения зависит от скина (см. [resolveMode]):
 * - SYSTEM в светлой/тёмной теме → SURFACE: акцент + динамический базовый фон
 *   окна (`colorSurface`/`colorOnSurface`) через сам оверлей, ПЛЮС (после
 *   consumer-side миграции Этапа C `concurrent-dreaming-wren` — см. план)
 *   page background/карточки/списки/разделители/часть текста, чьи XML/код-
 *   потребители перенаправлены на M3-роли напрямую — те тоже трекают обои.
 *   Не перекрашивается то, что ПИНИТСЯ статикой внутри оверлея (защита от
 *   TypedArray-краша — см. KDoc [ThemeOverlay.ForPDA.MaterialYouSurface]), и
 *   атрибуты без M3-зеркала (см. план).
 * - AMOLED → ACCENT_ONLY: красим только акцент, но НЕ поднимаем поверхности с
 *   чистого чёрного (иначе теряется смысл OLED-экономии).
 * - всё остальное → NONE.
 */
object MaterialYouPolicy {

    /** Насколько глубоко накладывать динамику. */
    enum class Mode {
        /** Не накладывать ничего. */
        NONE,
        /** Только акцент (FAB, переключатели, ссылки) — для AMOLED. */
        ACCENT_ONLY,
        /** Акцент + поверхности + типографика + хром — для SYSTEM light/dark. */
        SURFACE,
    }

    /**
     * @param enabled значение тумблера Material You.
     * @param palette текущая палитра интерфейса.
     * @return true, если поверх темы нужно накладывать DynamicColors.
     */
    fun shouldApplyDynamicColors(
            enabled: Boolean,
            palette: AppPreferences.Main.UiPalette
    ): Boolean {
        if (!enabled) return false
        return palette == AppPreferences.Main.UiPalette.SYSTEM
    }

    /**
     * Чистое решение, какой именно оверлей накладывать.
     *
     * @param enabled значение тумблера Material You.
     * @param palette текущая палитра интерфейса.
     * @param themeMode выбранный режим темы (для определения AMOLED-скина).
     * @param isNight тёмный ли сейчас режим (для SYSTEM_AMOLED, который AMOLED
     *        только ночью).
     */
    fun resolveMode(
            enabled: Boolean,
            palette: AppPreferences.Main.UiPalette,
            themeMode: AppPreferences.Main.ThemeMode,
            isNight: Boolean
    ): Mode {
        if (!shouldApplyDynamicColors(enabled, palette)) return Mode.NONE
        return if (isAmoledSkin(themeMode, isNight)) Mode.ACCENT_ONLY else Mode.SURFACE
    }

    private fun isAmoledSkin(
            themeMode: AppPreferences.Main.ThemeMode,
            isNight: Boolean
    ): Boolean = when (themeMode) {
        AppPreferences.Main.ThemeMode.AMOLED -> true
        AppPreferences.Main.ThemeMode.SYSTEM_AMOLED -> isNight
        else -> false
    }
}
