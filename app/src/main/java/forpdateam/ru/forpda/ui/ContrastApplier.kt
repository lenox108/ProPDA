package forpdateam.ru.forpda.ui

import android.app.Activity
import com.google.android.material.color.ColorContrast
import com.google.android.material.color.ColorContrastOptions
import forpdateam.ru.forpda.BuildConfig
import forpdateam.ru.forpda.R
import timber.log.Timber

/**
 * Накладывает оверлеи усиленного контраста (a11y) по СИСТЕМНОЙ настройке
 * контраста пользователя (Settings → Accessibility → Contrast).
 *
 * Использует Material `ColorContrast` (доступно с Material 1.13): на Android 14+
 * (`isContrastAvailable()`) читает системный уровень контраста и накладывает
 * [R.style.ThemeOverlay_ForPDA_MediumContrast] или `...HighContrast` поверх уже
 * собранной темы. На API < 34 — no-op (системного контраста нет). Не требует
 * своей настройки: следует за системой, как и положено доступности.
 *
 * Оверлеи перекрывают только роли читаемости (onSurface/onSurfaceVariant/
 * outline*), поэтому складываются с любой палитрой, акцентом и Material You.
 *
 * Должен вызываться из `Activity.onCreate()` ПОСЛЕ `setTheme(...)` и остальных
 * аппликаторов ([MaterialYouApplier], [AccentApplier]) — контраст идёт последним
 * слоем поверх итоговой цветовой схемы.
 */
object ContrastApplier {

    fun applyIfAvailable(activity: Activity) {
        if (!ColorContrast.isContrastAvailable()) return
        val options = ColorContrastOptions.Builder()
                .setMediumContrastThemeOverlay(R.style.ThemeOverlay_ForPDA_MediumContrast)
                .setHighContrastThemeOverlay(R.style.ThemeOverlay_ForPDA_HighContrast)
                .build()
        ColorContrast.applyToActivityIfAvailable(activity, options)
        if (BuildConfig.DEBUG) {
            Timber.tag(LOG_TAG).d("applied system contrast overlay to %s", activity.javaClass.simpleName)
        }
    }

    private const val LOG_TAG = "ContrastApplier"
}
