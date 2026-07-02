package forpdateam.ru.forpda.ui.compose.theme

import android.app.Activity
import android.os.Bundle
import android.util.TypedValue
import androidx.core.content.ContextCompat
import com.google.android.material.color.MaterialColors
import forpdateam.ru.forpda.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Диагностика жалобы: на читающей палитре (Sepia) фон Compose-экранов (QMS
 * Contacts/Favorites/News — все берут `colorScheme.background`) виден «белым»,
 * не совпадая с тёплыми карточками/тулбаром палитры.
 *
 * [ForpdaTheme] строит `background` из `?android:attr/colorBackground`, которое
 * в теме — ССЫЛКА `?attr/colorSurface` (styles.xml). Тест проверяет, что эта
 * ссылка реально резолвится в конкретный sepia-цвет через тот же путь, что
 * использует продакшн ([MaterialColors.getColor]), а не падает на нейтральный
 * baseline-фолбэк.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = android.app.Application::class)
class ForpdaThemeBackgroundTest {

    class SepiaActivity : Activity() {
        override fun onCreate(savedInstanceState: Bundle?) {
            setTheme(R.style.DayNightAppTheme_SepiaReading_NoActionBar)
            super.onCreate(savedInstanceState)
        }
    }

    private val ctx: android.content.Context by lazy {
        Robolectric.buildActivity(SepiaActivity::class.java).setup().get()
    }

    @Test
    fun `android colorBackground resolves to a concrete color int on Sepia`() {
        val tv = TypedValue()
        val resolved = ctx.theme.resolveAttribute(android.R.attr.colorBackground, tv, true)
        assertTrue("android:colorBackground must resolve", resolved)
        assertNotEquals(
                "android:colorBackground must NOT stay TYPE_ATTRIBUTE",
                TypedValue.TYPE_ATTRIBUTE, tv.type
        )
    }

    @Test
    fun `android colorBackground mis-resolves to M3 baseline while colorSurface is correct — why ForpdaTheme uses colorSurface`() {
        val sepiaSurface = ContextCompat.getColor(ctx, R.color.sepia_card_background)
        val sentinelFallback = 0xFF00FF00.toInt()
        val viaColorBackground =
                MaterialColors.getColor(ctx, android.R.attr.colorBackground, sentinelFallback)
        val viaColorSurface = MaterialColors.getColor(
                ctx, com.google.android.material.R.attr.colorSurface, sentinelFallback)

        // Конкретная роль colorSurface резолвится в переопределение палитры — верно.
        assertEquals("colorSurface должен резолвиться в sepia_card_background",
                sepiaSurface, viaColorSurface)
        // А android:colorBackground (= ?attr/colorSurface ССЫЛКА) подхватывает базовый
        // M3-colorSurface, НЕ палитру. Это и есть баг, из-за которого ForpdaTheme НЕ
        // читает android:colorBackground. Регресс-гейт: если Android/Material однажды
        // начнут резолвить ссылку правильно — можно упростить, но сегодня это не так.
        assertNotEquals(
                "android:colorBackground НЕ должен совпасть с sepia (ссылка резолвится в M3-базу)",
                sepiaSurface, viaColorBackground)
    }

    @Test
    fun `forpdaColorScheme background matches Sepia surface`() {
        val sepiaSurface = androidx.compose.ui.graphics.Color(
                ContextCompat.getColor(ctx, R.color.sepia_card_background))
        val scheme = forpdaColorSchemeFromContext(ctx, isDark = false)
        assertEquals("scheme.surface должен быть sepia", sepiaSurface, scheme.surface)
        assertEquals("scheme.background должен быть sepia (не нейтральный фолбэк)",
                sepiaSurface, scheme.background)
    }
}
