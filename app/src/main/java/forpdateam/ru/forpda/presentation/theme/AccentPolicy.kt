package forpdateam.ru.forpda.presentation.theme

import forpdateam.ru.forpda.common.Preferences as AppPreferences

/**
 * Чистая логика решения, накладывать ли выбранную акцент-палитру («смена цвета»).
 * Вынесено из [forpdateam.ru.forpda.ui.AccentApplier] ради юнит-тестов без Android.
 *
 * Правила:
 * - `NEUTRAL` — текущий монохромный бренд, оверлей не нужен.
 * - Акцент применяется ТОЛЬКО к системной палитре (`SYSTEM`); палитры чтения
 *   (`SEPIA_*`, `MINIMAL_READER`) самодостаточны и имеют приоритет.
 * - Material You (обои) имеет приоритет над фиксированным акцентом, но ТОЛЬКО
 *   когда реально доступен (API 31+ и тумблер включён). На API < 31 «включённый»
 *   Material You неэффективен → выбранный акцент применяется как фолбэк.
 */
object AccentPolicy {

    fun shouldApply(
            accent: AppPreferences.Main.AccentPalette,
            palette: AppPreferences.Main.UiPalette,
            materialYouEnabled: Boolean,
            sdkInt: Int
    ): Boolean {
        if (accent == AppPreferences.Main.AccentPalette.NEUTRAL) return false
        if (palette != AppPreferences.Main.UiPalette.SYSTEM) return false
        val materialYouEffective = materialYouEnabled && sdkInt >= 31
        if (materialYouEffective) return false
        return true
    }
}
