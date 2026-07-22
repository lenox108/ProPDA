package forpdateam.ru.forpda.ui

import android.app.Activity
import androidx.core.graphics.ColorUtils
import forpdateam.ru.forpda.R
import forpdateam.ru.forpda.common.getColorFromAttr
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Контракт [ChromeCanvas] — единого источника цвета «полотна» хрома
 * (статус-бар / шапка / фон страниц / нижний таббар).
 *
 * 1. БЕЗ Material You-оверлея (все статические палитры, MY off) флаг
 *    `chrome_canvas_dynamic` не разрешается → [ChromeCanvas.color] возвращает
 *    В ТОЧНОСТИ цвет fallback-атрибута. Это гарантия «статика не изменилась»
 *    by construction — регрессия здесь означала бы перекраску всех 15 палитр.
 *
 * 2. С наложенным [R.style.ThemeOverlay_ForPDA_MaterialYouSurface] (путь
 *    SYSTEM light/dark из MaterialYouApplier) флаг = true → полотно =
 *    blendARGB(colorSurfaceContainerLowest, colorPrimaryContainer, k) —
 *    детерминированная формула тонирования обоями.
 *
 * 3. С наложенным [R.style.ThemeOverlay_ForPDA_MaterialYouAmoled] (путь AMOLED)
 *    флаги dynamic И amoled = true → полотно тонируется примесью
 *    [ChromeCanvas.AMOLED_BLEND] поверх чёрной базы и ОТЛИЧАЕТСЯ от чёрного
 *    (в AMOLED раньше из обоев менялся только акцент — жалоба).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
class ChromeCanvasTest {

    private fun themedActivity(): Activity =
            Robolectric.buildActivity(Activity::class.java).get().apply {
                setTheme(R.style.DayNightAppTheme)
            }

    @Test
    fun `static theme - flag off and color equals fallback attr exactly`() {
        val activity = themedActivity()
        assertFalse(
                "chrome_canvas_dynamic must NOT resolve on base DayNightAppTheme",
                ChromeCanvas.isDynamic(activity),
        )
        val fallback = activity.getColorFromAttr(R.attr.main_toolbar_accent_surface)
        assertEquals(
                "without MY overlay the canvas must be exactly the fallback attr value",
                fallback,
                ChromeCanvas.color(activity, R.attr.main_toolbar_accent_surface),
        )
        val listsFallback = activity.getColorFromAttr(R.attr.background_for_lists)
        assertEquals(
                listsFallback,
                ChromeCanvas.color(activity, R.attr.background_for_lists),
        )
    }

    @Test
    fun `MaterialYouSurface overlay - flag on and color is the documented blend`() {
        val activity = themedActivity()
        activity.theme.applyStyle(R.style.ThemeOverlay_ForPDA_MaterialYouSurface, true)
        assertTrue(
                "MaterialYouSurface overlay must set chrome_canvas_dynamic=true",
                ChromeCanvas.isDynamic(activity),
        )
        val base = activity.getColorFromAttr(
                com.google.android.material.R.attr.colorSurfaceContainerLowest)
        val tint = activity.getColorFromAttr(
                com.google.android.material.R.attr.colorPrimaryContainer)
        val k = if (ColorUtils.calculateLuminance(base) > 0.5) {
            ChromeCanvas.LIGHT_BLEND
        } else {
            ChromeCanvas.DARK_BLEND
        }
        val expected = ColorUtils.blendARGB(base, tint, k)
        val actual = ChromeCanvas.color(activity, R.attr.main_toolbar_accent_surface)
        assertEquals("canvas must follow the documented blend formula", expected, actual)
        if (k > 0f) {
            // В тёмной теме k=0 (полотно = база как есть, база уже тонирована
            // floor-селекторами) — расхождение с базой требуем только там, где
            // бленд вообще включён.
            assertNotEquals(
                    "under MY the canvas must actually differ from the untinted base",
                    base,
                    actual,
            )
        }
    }

    @Test
    fun `MaterialYouAmoled overlay - canvas is tinted above black, not pure black`() {
        val activity = themedActivity()
        activity.theme.applyStyle(R.style.ThemeOverlay_ForPDA_MaterialYouAmoled, true)
        assertTrue(
                "MaterialYouAmoled overlay must set chrome_canvas_dynamic=true",
                ChromeCanvas.isDynamic(activity),
        )
        assertTrue(
                "MaterialYouAmoled overlay must set chrome_canvas_amoled=true",
                ChromeCanvas.isAmoledCanvas(activity),
        )
        val base = activity.getColorFromAttr(
                com.google.android.material.R.attr.colorSurfaceContainerLowest)
        val tint = activity.getColorFromAttr(
                com.google.android.material.R.attr.colorPrimaryContainer)
        val expected = ColorUtils.blendARGB(base, tint, ChromeCanvas.AMOLED_BLEND)
        val actual = ChromeCanvas.color(activity, R.attr.main_toolbar_accent_surface)
        assertEquals(
                "AMOLED canvas must use the AMOLED_BLEND fraction over the black base",
                expected,
                actual,
        )
        // Смысл фикса: хром больше НЕ равен чистой чёрной базе (если контейнер
        // обоев вообще отличается от базы — на реальных обоях так и есть).
        if (tint != base) {
            assertNotEquals(
                    "AMOLED canvas must differ from the pure-black base once tinted",
                    base,
                    actual,
            )
        }
    }
}
