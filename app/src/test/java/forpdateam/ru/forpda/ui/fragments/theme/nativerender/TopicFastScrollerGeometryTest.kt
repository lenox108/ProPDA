package forpdateam.ru.forpda.ui.fragments.theme.nativerender

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Геометрия перетаскиваемого ползунка темы ([TopicFastScroller.Geometry]). */
class TopicFastScrollerGeometryTest {

    private val geometry = TopicFastScroller.Geometry

    @Test
    fun shortTopic_getsNoScroller() {
        // Полтора экрана контента: ползунок был бы шумом, обычной прокрутки хватает.
        assertFalse(geometry.isScrollerWorthShowing(range = 3000, extent = 2000))
    }

    @Test
    fun longTopic_getsScroller() {
        assertTrue(geometry.isScrollerWorthShowing(range = 40000, extent = 2000))
    }

    @Test
    fun emptyList_getsNoScroller() {
        assertFalse(geometry.isScrollerWorthShowing(range = 0, extent = 0))
    }

    @Test
    fun thumbHeight_isProportionalToVisibleShare() {
        assertEquals(
                200f,
                geometry.thumbHeight(trackHeight = 1000f, extent = 2000, range = 10000, minHeight = 100f),
                0.01f)
    }

    @Test
    fun thumbHeight_neverDropsBelowTheGrabbableMinimum() {
        // Тема в сотню экранов: пропорциональный бегунок был бы в 10px — ровно то, на что жаловался юзер.
        assertEquals(
                100f,
                geometry.thumbHeight(trackHeight = 1000f, extent = 2000, range = 200000, minHeight = 100f),
                0.01f)
    }

    @Test
    fun thumbHeight_neverExceedsTheTrack() {
        assertEquals(
                1000f,
                geometry.thumbHeight(trackHeight = 1000f, extent = 9000, range = 9000, minHeight = 100f),
                0.01f)
    }

    @Test
    fun thumbOffset_spansTheWholeTravel() {
        val travel = 800f
        assertEquals(0f, geometry.thumbOffset(travel, offset = 0, range = 10000, extent = 2000), 0.01f)
        assertEquals(400f, geometry.thumbOffset(travel, offset = 4000, range = 10000, extent = 2000), 0.01f)
        assertEquals(800f, geometry.thumbOffset(travel, offset = 8000, range = 10000, extent = 2000), 0.01f)
    }

    @Test
    fun thumbOffset_clampsPastTheEnd() {
        // Пере-прокрутка/подрастание постов под пальцем не должны выкидывать бегунок за дорожку.
        assertEquals(
                800f,
                geometry.thumbOffset(travel = 800f, offset = 99999, range = 10000, extent = 2000),
                0.01f)
    }

    @Test
    fun dragAcrossTheWholeTrack_scrollsTheWholeContent() {
        assertEquals(
                8000f,
                geometry.scrollDelta(fingerDelta = 800f, travel = 800f, range = 10000, extent = 2000),
                0.01f)
    }

    @Test
    fun dragIsIncremental_soPagesLoadedUnderTheFingerJustStretchTheScale() {
        // Тот же ход пальца после подгрузки страницы (range вырос) даёт больший ход контента — и НЕ
        // телепортирует список, как сделал бы пересчёт абсолютной доли.
        val before = geometry.scrollDelta(fingerDelta = 80f, travel = 800f, range = 10000, extent = 2000)
        val after = geometry.scrollDelta(fingerDelta = 80f, travel = 800f, range = 20000, extent = 2000)
        assertEquals(800f, before, 0.01f)
        assertEquals(1800f, after, 0.01f)
    }

    @Test
    fun nothingToScroll_yieldsNoDelta() {
        assertEquals(
                0f,
                geometry.scrollDelta(fingerDelta = 500f, travel = 800f, range = 2000, extent = 2000),
                0.01f)
    }
}
