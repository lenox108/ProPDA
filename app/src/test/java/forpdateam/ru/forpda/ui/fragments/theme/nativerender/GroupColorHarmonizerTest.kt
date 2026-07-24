package forpdateam.ru.forpda.ui.fragments.theme.nativerender

import android.graphics.Color
import androidx.core.graphics.ColorUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class GroupColorHarmonizerTest {

    @Test
    fun `beginner green becomes muted while preserving its hue`() {
        val source = Color.parseColor("#99CC00")
        val sourceHsl = source.toHsl()

        val dayHsl = GroupColorHarmonizer.harmonize(source, night = false).toHsl()
        val nightHsl = GroupColorHarmonizer.harmonize(source, night = true).toHsl()

        assertEquals(sourceHsl[0], dayHsl[0], 0.6f)
        assertEquals(sourceHsl[0], nightHsl[0], 0.6f)
        // HSL -> 8-bit RGB -> HSL quantization can add just under one percentage point.
        assertTrue(dayHsl[1] <= 0.41f)
        assertTrue(nightHsl[1] <= 0.41f)
        assertTrue(dayHsl[2] in 0.379f..0.481f)
        assertTrue(nightHsl[2] in 0.619f..0.721f)
    }

    @Test
    fun `default and malformed server colors fall back to the theme color`() {
        assertNull(GroupColorHarmonizer.parse(null, night = false))
        assertNull(GroupColorHarmonizer.parse(" ", night = false))
        assertNull(GroupColorHarmonizer.parse("black", night = false))
        assertNull(GroupColorHarmonizer.parse("not-a-color", night = false))
    }

    private fun Int.toHsl() = FloatArray(3).also { ColorUtils.colorToHSL(this, it) }
}
