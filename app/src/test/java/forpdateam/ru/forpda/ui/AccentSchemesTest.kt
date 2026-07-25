package forpdateam.ru.forpda.ui

import com.google.android.material.color.utilities.Hct
import com.google.android.material.color.utilities.MaterialDynamicColors
import forpdateam.ru.forpda.common.Preferences.Main.AccentStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * Контракт грида ПОДПИСАННЫХ акцентов: какой бы стиль ни был выбран, primary обязан
 * остаться в тоне своего seed'а. Для видимого режима «Однотонный» это относится
 * ко всем трём accent-семействам: primary, secondary и tertiary.
 */
class AccentSchemesTest {

    private val seeds = mapOf(
            "blue" to 0xFF0B57D0.toInt(),
            "indigo" to 0xFF4355B9.toInt(),
            "violet" to 0xFF7B4FCF.toInt(),
            "purple" to 0xFF9C27B0.toInt(),
            "pink" to 0xFFC2185B.toInt(),
            "red" to 0xFFD32F2F.toInt(),
            "deeporange" to 0xFFE64A19.toInt(),
            "orange" to 0xFFEF6C00.toInt(),
            "amber" to 0xFFFF8F00.toInt(),
            "green" to 0xFF2E7D32.toInt(),
            "teal" to 0xFF00796B.toInt(),
            "cyan" to 0xFF0097A7.toInt(),
    )

    @Test
    fun `primary keeps the seed hue in every accent style`() {
        val mdc = MaterialDynamicColors()
        for ((name, seed) in seeds) {
            val seedHue = Hct.fromInt(seed).hue
            for (style in AccentStyle.values()) {
                for (isDark in listOf(false, true)) {
                    val primary = mdc.primary().getArgb(AccentSchemes.scheme(seed, style, isDark))
                    val delta = hueDistance(seedHue, Hct.fromInt(primary).hue)
                    assertTrue(
                            "$name/$style/dark=$isDark: primary ушёл на ${delta.toInt()}° от seed'а",
                            delta <= MAX_HUE_DRIFT)
                }
            }
        }
    }

    @Test
    fun `monochromatic manual style keeps every accent role in the named hue`() {
        val mdc = MaterialDynamicColors()
        for ((name, seed) in seeds) {
            val seedHue = Hct.fromInt(seed).hue
            for (isDark in listOf(false, true)) {
                val scheme = AccentSchemes.scheme(seed, AccentStyle.EXPRESSIVE, isDark)
                val roles = mapOf(
                        "primary" to mdc.primary().getArgb(scheme),
                        "primaryContainer" to mdc.primaryContainer().getArgb(scheme),
                        "secondary" to mdc.secondary().getArgb(scheme),
                        "secondaryContainer" to mdc.secondaryContainer().getArgb(scheme),
                        "tertiary" to mdc.tertiary().getArgb(scheme),
                        "tertiaryContainer" to mdc.tertiaryContainer().getArgb(scheme),
                )
                for ((role, color) in roles) {
                    val delta = hueDistance(seedHue, Hct.fromInt(color).hue)
                    assertTrue(
                            "$name/$role/dark=$isDark: роль ушла на ${delta.toInt()}° от seed'а",
                            delta <= MAX_MONOCHROMATIC_HUE_DRIFT,
                    )
                }
            }
        }
    }

    @Test
    fun `blue monochromatic secondary is no longer the legacy pink`() {
        val blue = seeds.getValue("blue")
        val scheme = AccentSchemes.scheme(blue, AccentStyle.EXPRESSIVE, isDark = true)
        val secondary = MaterialDynamicColors().secondary().getArgb(scheme)

        assertTrue(
                "secondary синего акцента должен оставаться рядом с hue синего seed",
                hueDistance(Hct.fromInt(blue).hue, Hct.fromInt(secondary).hue) <=
                        MAX_MONOCHROMATIC_HUE_DRIFT,
        )
        assertTrue(
                "secondary больше не должен совпадать со старым розовым #F0B8C7",
                secondary != 0xFFF0B8C7.toInt(),
        )
        assertEquals(0xFFBBC6EA.toInt(), secondary)
    }

    private fun hueDistance(a: Double, b: Double): Double {
        val d = abs(a - b) % 360.0
        return if (d > 180.0) 360.0 - d else d
    }

    private companion object {
        /** Тональные схемы слегка гуляют по тону при сведении хромы — но не на другой цвет. */
        const val MAX_HUE_DRIFT = 25.0
        const val MAX_MONOCHROMATIC_HUE_DRIFT = 12.0
    }
}
