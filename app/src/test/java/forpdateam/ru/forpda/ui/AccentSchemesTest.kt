package forpdateam.ru.forpda.ui

import com.google.android.material.color.utilities.Hct
import com.google.android.material.color.utilities.MaterialDynamicColors
import forpdateam.ru.forpda.common.Preferences.Main.AccentStyle
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * Контракт грида ПОДПИСАННЫХ акцентов: какой бы стиль ни был выбран, primary обязан
 * остаться в тоне своего seed'а — иначе «Синий» красит зелёным (так и было: сырой
 * SchemeExpressive крутит primary на +240°, blue → #2E6A3A).
 */
class AccentSchemesTest {

    private val seeds = mapOf(
            "blue" to 0xFF0B57D0.toInt(),
            "red" to 0xFFD32F2F.toInt(),
            "green" to 0xFF2E7D32.toInt(),
            "amber" to 0xFFFF8F00.toInt(),
            "teal" to 0xFF00796B.toInt(),
            "purple" to 0xFF9C27B0.toInt(),
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

    private fun hueDistance(a: Double, b: Double): Double {
        val d = abs(a - b) % 360.0
        return if (d > 180.0) 360.0 - d else d
    }

    private companion object {
        /** Тональные схемы слегка гуляют по тону при сведении хромы — но не на другой цвет. */
        const val MAX_HUE_DRIFT = 25.0
    }
}
