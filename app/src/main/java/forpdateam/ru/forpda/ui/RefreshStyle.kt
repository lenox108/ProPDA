package forpdateam.ru.forpda.ui

import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.R as MaterialR
import forpdateam.ru.forpda.common.getColorFromAttr

/**
 * Единый M3-стиль индикатора pull-to-refresh по проекту: «пак» на нейтральной
 * поверхности (colorSurfaceVariant), стрелка — акцент/вторичный. Раньше три
 * экрана конфигурировали SwipeRefreshLayout вручную и расходились (в частности,
 * лента комментариев красила фон круга в colorPrimary — тяжёлый цветной пак).
 * Теперь один источник правды, консистентно и трекает палитру/Material You.
 */
fun SwipeRefreshLayout.applyM3RefreshStyle() {
    setProgressBackgroundColorSchemeColor(
            context.getColorFromAttr(MaterialR.attr.colorSurfaceVariant)
    )
    setColorSchemeColors(
            context.getColorFromAttr(androidx.appcompat.R.attr.colorPrimary),
            context.getColorFromAttr(MaterialR.attr.colorSecondary),
    )
}
