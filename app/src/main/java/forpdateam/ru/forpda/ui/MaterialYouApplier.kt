package forpdateam.ru.forpda.ui

import android.app.Activity
import android.os.Build
import android.util.TypedValue
import com.google.android.material.color.DynamicColors
import com.google.android.material.color.DynamicColorsOptions
import forpdateam.ru.forpda.BuildConfig
import forpdateam.ru.forpda.R
import forpdateam.ru.forpda.common.DayNightHelper
import forpdateam.ru.forpda.common.Preferences
import forpdateam.ru.forpda.model.datastore.MainDataStore
import forpdateam.ru.forpda.presentation.theme.MaterialYouPolicy
import timber.log.Timber

/**
 * Применение Material You (Dynamic Color) к нативному UI на уровне КАЖДОЙ
 * активити.
 *
 * Почему НЕ через [DynamicColors.applyToActivitiesIfAvailable] на уровне
 * Application: активити приложения ([forpdateam.ru.forpda.ui.activities.MainActivity],
 * [forpdateam.ru.forpda.ui.activities.SettingsActivity] и др.) в своём
 * `onCreate()` вызывают `setTheme(UiThemeStyles.*)`. Вызов `Activity.setTheme()`
 * пересоздаёт тему «с нуля» и стирает любой оверлей, наложенный раньше (в т.ч.
 * динамические цвета, которые библиотека Material накладывает в
 * `onActivityPreCreated` ДО `onCreate`). Поэтому глобальная регистрация не
 * выживала, и тумблер Material You визуально ничего не менял.
 *
 * Решение: накладывать динамические цвета вручную ПОСЛЕ `setTheme(...)` и до
 * `setContentView(...)` — тогда оверлей ложится поверх уже выбранной темы и
 * доживает до инфляции вью.
 *
 * Поверх динамических цветов дополнительно накладывается оверлей, который
 * перенаправляет захардкоженные атрибуты темы на динамические роли Material 3
 * (иначе DynamicColors красит только стандартные M3-роли, а весь «хром»
 * приложения через собственные атрибуты остаётся прежним):
 * - [R.style.ThemeOverlay_ForPDA_MaterialYouAccent] — только акцент
 *   (`colorAccent`, `link_color`, `colorControlActivated` → `colorPrimary`).
 *   Используется для AMOLED, чтобы не поднимать поверхности с чистого чёрного.
 * - [R.style.ThemeOverlay_ForPDA_MaterialYouSurface] — акцент + поверхности +
 *   типографика + хром (фоны, карточки, списки, тулбары, навбар, текст,
 *   разделители → `colorSurface*`/`colorOnSurface*`/`colorOutlineVariant`).
 *   Используется для SYSTEM в светлой/тёмной теме.
 *
 * Выбор оверлея делает [MaterialYouPolicy.resolveMode]. Палитры чтения
 * (Sepia/Minimal) динамику не получают вовсе.
 *
 * §4.1 of REFACTOR_PLAN.md.
 */
object MaterialYouApplier {

    /**
     * Должен вызываться из `Activity.onCreate()` сразу после `setTheme(...)`.
     */
    fun applyIfEnabled(activity: Activity) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            if (BuildConfig.DEBUG) Timber.tag(LOG_TAG).d("skip: sdk<31 (sdk=%d)", Build.VERSION.SDK_INT)
            return
        }
        val dataStore = MainDataStore(activity)
        val enabled = runCatching { dataStore.getUseMaterialYouImmediate() }.getOrDefault(false)
        val palette = runCatching { dataStore.getUiPaletteImmediate() }
                .getOrDefault(Preferences.Main.UiPalette.SYSTEM)
        val themeMode = runCatching { dataStore.getThemeModeImmediate() }
                .getOrDefault(Preferences.Main.ThemeMode.SYSTEM)
        val isNight = DayNightHelper.isUiModeNight(activity.resources.configuration)
        val mode = MaterialYouPolicy.resolveMode(enabled, palette, themeMode, isNight)
        if (BuildConfig.DEBUG) {
            Timber.tag(LOG_TAG).d(
                    "precondition: enabled=%s palette=%s themeMode=%s night=%s sdk=%d -> mode=%s",
                    enabled, palette, themeMode, isNight, Build.VERSION.SDK_INT, mode
            )
        }
        if (mode == MaterialYouPolicy.Mode.NONE) return

        // SURFACE: полный оверлей (акцент + поверхности + типографика + хром) для
        // SYSTEM в светлой/тёмной теме. ACCENT_ONLY: только акцент для AMOLED —
        // чтобы не поднимать поверхности с чистого чёрного.
        val overlay = when (mode) {
            MaterialYouPolicy.Mode.SURFACE -> R.style.ThemeOverlay_ForPDA_MaterialYouSurface
            else -> R.style.ThemeOverlay_ForPDA_MaterialYouAccent
        }
        val options = DynamicColorsOptions.Builder()
                .setOnAppliedCallback {
                    activity.theme.applyStyle(overlay, true)
                    if (BuildConfig.DEBUG) logResolvedColors(activity)
                }
                .build()
        DynamicColors.applyToActivityIfAvailable(activity, options)
    }

    private fun logResolvedColors(activity: Activity) {
        val tv = TypedValue()
        val primary = if (activity.theme.resolveAttribute(
                        com.google.android.material.R.attr.colorPrimary, tv, true)) tv.data else 0
        val accent = if (activity.theme.resolveAttribute(
                        androidx.appcompat.R.attr.colorAccent, tv, true)) tv.data else 0
        Timber.tag(LOG_TAG).d(
                "applied to %s: colorPrimary=#%08X colorAccent=#%08X",
                activity.javaClass.simpleName, primary, accent
        )
    }

    private const val LOG_TAG = "MaterialYou"
}
