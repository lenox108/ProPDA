package forpdateam.ru.forpda.ui.fragments.theme.nativerender

import android.graphics.Color
import androidx.core.graphics.ColorUtils

/**
 * Adapts the saturated web colours supplied by the forum to the app theme.
 *
 * The hue remains intact so forum groups are still distinguishable. Saturation and lightness are
 * constrained to calmer, theme-appropriate ranges; in particular, the bright green used for
 * «Начинающий» becomes a muted olive instead of looking neon.
 */
internal object GroupColorHarmonizer {
    fun parse(raw: String?, night: Boolean): Int? {
        val value = raw
                ?.trim()
                ?.takeIf { it.isNotBlank() && !it.equals("black", ignoreCase = true) }
                ?: return null
        return try {
            harmonize(Color.parseColor(value), night)
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    fun harmonize(color: Int, night: Boolean): Int {
        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(color, hsl)
        hsl[1] = hsl[1].coerceAtMost(MAX_SATURATION)
        hsl[2] = if (night) {
            hsl[2].coerceIn(NIGHT_MIN_LIGHTNESS, NIGHT_MAX_LIGHTNESS)
        } else {
            hsl[2].coerceIn(DAY_MIN_LIGHTNESS, DAY_MAX_LIGHTNESS)
        }
        return ColorUtils.HSLToColor(hsl)
    }

    private const val MAX_SATURATION = 0.40f
    private const val DAY_MIN_LIGHTNESS = 0.38f
    private const val DAY_MAX_LIGHTNESS = 0.48f
    private const val NIGHT_MIN_LIGHTNESS = 0.62f
    private const val NIGHT_MAX_LIGHTNESS = 0.72f
}
