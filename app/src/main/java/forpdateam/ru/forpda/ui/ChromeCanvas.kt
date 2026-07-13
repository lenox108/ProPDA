package forpdateam.ru.forpda.ui

import android.content.Context
import android.util.TypedValue
import androidx.annotation.AttrRes
import androidx.annotation.ColorInt
import androidx.core.graphics.ColorUtils
import forpdateam.ru.forpda.R
import forpdateam.ru.forpda.common.getColorFromAttr

/**
 * Единый источник цвета «полотна» приложения — плоского блока хрома, на котором
 * лежит контент: статус-бар, верхняя шапка главных разделов, фон страниц/списков,
 * нижний таббар, ряды пагинации, подложка панели ответа.
 *
 * Зачем. Под Material You роль `colorSurfaceContainerLowest`, на которой полотно
 * держалось раньше, — это M3-тон 100 (чистый белый) / DarkFloor-пин #121212:
 * нулевая цветность, полотно вообще не реагировало на обои («светлый или тёмный
 * по теме, а не по обоям» — жалоба). Канонические M3-поверхности (surfaceContainer
 * и т.п.) несут настолько слабый подтон, что на глаз неотличимы от белого.
 * Поэтому тон полотна вычисляется здесь В РАНТАЙМЕ: база (Lowest) подмешивается
 * с динамическим `colorPrimaryContainer` (пастельный wallpaper-контейнер) —
 * получается видимый, но спокойный тон обоев, как фоны системных приложений
 * Android. Карточки контента остаются на `content_card_surface` (светлый нейтрал)
 * и «всплывают» над тонированным полотном.
 *
 * Гейт — атрибут-флаг [R.attr.chrome_canvas_dynamic], который ставит ТОЛЬКО
 * `ThemeOverlay.ForPDA.MaterialYouSurface` (Material You, палитра SYSTEM,
 * светлая/тёмная). Everywhere else (15 статических палитр, AMOLED, MY off,
 * API < 31) флаг не разрешается → [chromeCanvasColor] возвращает ровно
 * `fallbackAttr`, т.е. в точности прежний цвет — статика не может измениться
 * by construction. AMOLED под MY идёт по ACCENT_ONLY-пути без Surface-оверлея
 * и сохраняет чистый #000000 (смысл режима — минимум свечения).
 *
 * Почему флаг в теме, а не чтение префов: тема — единственный источник правды о
 * том, какие оверлеи РЕАЛЬНО наложились на активити (см. MaterialYouApplier);
 * чтение префов здесь могло бы разойтись с фактически применённой темой
 * (гонки при смене настроек до recreate).
 */
object ChromeCanvas {

    /**
     * Доля примеси `colorPrimaryContainer` в базу полотна. Светлая: база — белый
     * (Lowest тон 100), контейнер — пастель тона 90 → итог заметный светлый
     * пастельный тон обоев. Тёмная: база — #121212 (DarkFloor), контейнер — тон 30
     * → тёмное полотно с оттенком; долю держим ниже, чтобы полотно оставалось
     * ТЕМНЕЕ карточек (#242424) и «тёмная» не светлела.
     */
    const val LIGHT_BLEND = 0.42f
    const val DARK_BLEND = 0.30f

    fun isDynamic(context: Context): Boolean {
        val tv = TypedValue()
        return context.theme.resolveAttribute(R.attr.chrome_canvas_dynamic, tv, true) &&
                tv.type >= TypedValue.TYPE_FIRST_INT && tv.type <= TypedValue.TYPE_LAST_INT &&
                tv.data != 0
    }

    @ColorInt
    fun color(context: Context, @AttrRes fallbackAttr: Int): Int {
        if (!isDynamic(context)) {
            if (forpdateam.ru.forpda.BuildConfig.DEBUG) {
                timber.log.Timber.tag("ChromeCanvas").d("static path: fallback attr used")
            }
            return context.getColorFromAttr(fallbackAttr)
        }
        val base = context.getColorFromAttr(
                com.google.android.material.R.attr.colorSurfaceContainerLowest)
        val tint = context.getColorFromAttr(
                com.google.android.material.R.attr.colorPrimaryContainer)
        val fraction = if (ColorUtils.calculateLuminance(base) > 0.5) LIGHT_BLEND else DARK_BLEND
        val result = ColorUtils.blendARGB(base, tint, fraction)
        if (forpdateam.ru.forpda.BuildConfig.DEBUG) {
            timber.log.Timber.tag("ChromeCanvas").d(
                    "dynamic: base=#%08X tint=#%08X k=%.2f -> #%08X", base, tint, fraction, result)
        }
        return result
    }
}

/**
 * Цвет полотна: под Material You (SYSTEM light/dark) — динамический тон обоев,
 * иначе — в точности `?attr/fallbackAttr` (прежнее поведение потребителя).
 * `fallbackAttr` каждого потребителя = атрибут, который он читал ДО миграции на
 * ChromeCanvas, поэтому вне Material You это чистый no-op.
 */
@ColorInt
fun Context.chromeCanvasColor(@AttrRes fallbackAttr: Int): Int =
        ChromeCanvas.color(this, fallbackAttr)
