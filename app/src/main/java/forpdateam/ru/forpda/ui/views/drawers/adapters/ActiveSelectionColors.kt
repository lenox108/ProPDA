package forpdateam.ru.forpda.ui.views.drawers.adapters

import android.content.Context
import androidx.core.graphics.ColorUtils
import forpdateam.ru.forpda.common.getColorFromAttr

/**
 * Цвета активного пункта навигации.
 *
 * Активное состояние должно сохранять оттенок выбранного акцента. Secondary-роли
 * для expressive-палитр могут иметь намеренно сдвинутый hue (например, розовый у
 * синего акцента), поэтому для навигации используем только primary-роли.
 */
internal object ActiveSelectionColors {
    const val ROW_TINT_ALPHA = 0x33 // 20%

    fun indicator(context: Context): Int =
            context.getColorFromAttr(com.google.android.material.R.attr.colorPrimaryContainer)

    fun onIndicator(context: Context): Int =
            context.getColorFromAttr(com.google.android.material.R.attr.colorOnPrimaryContainer)

    fun accent(context: Context): Int =
            context.getColorFromAttr(androidx.appcompat.R.attr.colorPrimary)

    fun rowBackground(context: Context): Int =
            ColorUtils.setAlphaComponent(accent(context), ROW_TINT_ALPHA)
}
