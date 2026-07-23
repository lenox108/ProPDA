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
 * Зачем. В СВЕТЛОЙ теме роль `colorSurfaceContainerLowest`, на которой полотно
 * держалось раньше, — это M3-тон 100 (чистый белый): нулевая цветность, полотно
 * не реагировало на обои («светлый или тёмный по теме, а не по обоям» —
 * жалоба); канонические M3-поверхности (surfaceContainer и т.п.) несут
 * настолько слабый подтон, что на глаз неотличимы от белого. Поэтому светлый
 * тон полотна вычисляется В РАНТАЙМЕ: база (Lowest) подмешивается с
 * динамическим `colorPrimaryContainer` (пастельный wallpaper-контейнер) —
 * получается видимый, но спокойный тон обоев, как фоны системных приложений
 * Android. Карточки контента остаются на `content_card_surface` (светлый
 * нейтрал) и «всплывают» над тонированным полотном.
 *
 * В ТЁМНОЙ теме полотно = база КАК ЕСТЬ (см. [DARK_BLEND] = 0): floor-база
 * уже несёт оттенок обоев (lStar-пин поверх системного слота, 93aacd5), и
 * полотно обязано оставаться ТЕМНЕЕ карточек — примесь контейнера это ломала.
 *
 * Гейт — атрибут-флаг [R.attr.chrome_canvas_dynamic], который ставят
 * `ThemeOverlay.ForPDA.MaterialYouSurface` (Material You, палитра SYSTEM,
 * светлая/тёмная) И `ThemeOverlay.ForPDA.MaterialYouAmoled` (AMOLED под MY).
 * Everywhere else (15 статических палитр, MY off, API < 31) флаг не разрешается
 * → [chromeCanvasColor] возвращает ровно `fallbackAttr`, т.е. в точности прежний
 * цвет — статика не может измениться by construction. Под AMOLED база (Lowest)
 * с 22.07.2026 тоже ДИНАМИЧЕСКАЯ: MaterialYouAmoled больше не пинит surface-роли
 * на чёрный, поэтому полотно (как и в тёмной) берётся КАК ЕСТЬ, [DARK_BLEND] = 0
 * — оттенок обоев уже внутри самой базы.
 *
 * Почему флаг в теме, а не чтение префов: тема — единственный источник правды о
 * том, какие оверлеи РЕАЛЬНО наложились на активити (см. MaterialYouApplier);
 * чтение префов здесь могло бы разойтись с фактически применённой темой
 * (гонки при смене настроек до recreate).
 */
object ChromeCanvas {

    /**
     * Доля примеси `colorPrimaryContainer` в базу полотна. Светлая: база — белый
     * (Lowest тон 100, нулевая цветность), контейнер — пастель тона 90 → итог
     * заметный светлый пастельный тон обоев.
     *
     * Тёмная: 0 — база уже несёт оттенок обоев сама (DarkFloor с 93aacd5 пинит
     * lStar-селекторами поверх системного слота, L* ~6), и любая примесь
     * контейнера (тон 30) только ОСВЕТЛЯЕТ полотно. С прежним k=0.30 полотно
     * выходило на L* ~16 — СВЕТЛЕЕ карточек floor'а (L* ~14), иерархия
     * переворачивалась (жалоба «фон светлее, а должен быть темнее» — скрины QMS:
     * раньше фон был почти чёрным тёплым, пузыри светлее него).
     */
    const val LIGHT_BLEND = 0.42f
    const val DARK_BLEND = 0f

    fun isDynamic(context: Context): Boolean = readBoolFlag(context, R.attr.chrome_canvas_dynamic)

    private fun readBoolFlag(context: Context, attr: Int): Boolean {
        val tv = TypedValue()
        return context.theme.resolveAttribute(attr, tv, true) &&
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
