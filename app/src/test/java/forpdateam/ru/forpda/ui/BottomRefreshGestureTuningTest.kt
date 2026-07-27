package forpdateam.ru.forpda.ui

import forpdateam.ru.forpda.ui.fragments.theme.modules.BottomRefreshGestureTuning
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Пороги жеста «обновление свайпом снизу»: чистая математика, поэтому проверяется юнит-тестом.
 * Основное требование к нему после жалобы с ~6.9″ — порог всегда достижим из точки касания и не
 * заставляет уводить палец выше ~⅓ пути к верху.
 */
class BottomRefreshGestureTuningTest {

    private val density = 3f // ~xxhdpi, типичный крупный телефон
    private val slop = 24 // px, ViewConfiguration на такой плотности

    private fun capture() = BottomRefreshGestureTuning.captureDistancePx(density, slop)

    private fun trigger(downYdp: Float) =
            BottomRefreshGestureTuning.triggerDistancePx(density, downYdp * density, capture())

    @Test
    fun `capture distance is the 32dp floor when slop is small`() {
        assertEquals(96f, capture(), 0.01f) // 32dp * 3
    }

    @Test
    fun `trigger is capped at 128dp no matter how much room there is`() {
        assertEquals(128f * density, trigger(800f), 0.01f)
        assertEquals(128f * density, trigger(400f), 0.01f)
    }

    @Test
    fun `trigger shrinks with the available travel and never exceeds it`() {
        for (downYdp in listOf(60f, 120f, 200f, 300f, 500f, 900f)) {
            val t = trigger(downYdp)
            assertTrue("порог $t недостижим с высоты $downYdp dp", t <= downYdp * density)
            assertTrue("порог $t меньше зоны захвата", t > capture())
        }
    }

    @Test
    fun `trigger floor keeps a real pull after capture even at the top edge`() {
        val t = trigger(50f)
        assertTrue(t >= capture() + 24f * density)
    }

    @Test
    fun `progress starts at the capture point and saturates at the threshold`() {
        val c = capture()
        val t = trigger(600f)
        assertEquals(0f, BottomRefreshGestureTuning.progress(c, c, t), 0.001f)
        assertEquals(0.5f, BottomRefreshGestureTuning.progress(c + (t - c) / 2f, c, t), 0.001f)
        assertEquals(1f, BottomRefreshGestureTuning.progress(t, c, t), 0.001f)
        assertEquals(1f, BottomRefreshGestureTuning.progress(t * 4f, c, t), 0.001f)
        assertEquals(0f, BottomRefreshGestureTuning.progress(-10f, c, t), 0.001f)
    }

    @Test
    fun `full travel is roughly half of the previous fixed 230dp`() {
        assertTrue(trigger(800f) < 230f * density / 1.7f)
    }
}
