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

    // --- шкала «по всей теме» ---

    private fun scale(total: Int, first: Int, last: Int) =
            TopicFastScroller.TopicScale(totalPages = total, firstLoadedPage = first, lastLoadedPage = last)

    @Test
    fun topicScale_isPointlessWhenTheWholeTopicIsLoaded() {
        assertFalse(scale(total = 3, first = 1, last = 3).isUsable)
        assertFalse(scale(total = 1, first = 1, last = 1).isUsable)
        assertTrue(scale(total = 10491, first = 10485, last = 10486).isUsable)
    }

    @Test
    fun topicFraction_putsTheThumbWhereTheLoadedWindowIs() {
        // Загружены страницы 5001–5002 из 10000: верх окна — ровно середина темы.
        val s = scale(total = 10000, first = 5001, last = 5002)
        assertEquals(0.5f, geometry.topicFraction(s, loaded = 0f), 0.0001f)
        assertEquals(0.5002f, geometry.topicFraction(s, loaded = 1f), 0.0001f)
    }

    @Test
    fun topicFraction_reachesBothEndsOfTheTrack() {
        assertEquals(0f, geometry.topicFraction(scale(100, 1, 1), loaded = 0f), 0.0001f)
        assertEquals(1f, geometry.topicFraction(scale(100, 100, 100), loaded = 1f), 0.0001f)
    }

    @Test
    fun withinLoadedFraction_isTheInverseOfTopicFraction() {
        val s = scale(total = 10000, first = 5001, last = 5004)
        for (loaded in listOf(0f, 0.25f, 0.5f, 1f)) {
            assertEquals(loaded, geometry.withinLoadedFraction(s, geometry.topicFraction(s, loaded)), 0.001f)
        }
    }

    @Test
    fun pageForFraction_coversTheWholeTopicWithoutOverflow() {
        assertEquals(1, geometry.pageForFraction(0f, totalPages = 10491))
        assertEquals(5246, geometry.pageForFraction(0.5f, totalPages = 10491))
        assertEquals(10491, geometry.pageForFraction(1f, totalPages = 10491))
    }

    @Test
    fun scrubSpeed_slowsDownAsTheFingerLeavesTheEdge() {
        // У края — обычная скорость; отвёл палец — подстройка (иначе пиксель пути = несколько страниц).
        assertEquals(1f, geometry.scrubSpeed(10f), 0.0001f)
        assertEquals(1f, geometry.scrubSpeed(55f), 0.0001f)
        assertTrue(geometry.scrubSpeed(80f) < 1f)
        assertTrue(geometry.scrubSpeed(150f) < geometry.scrubSpeed(80f))
        assertTrue(geometry.scrubSpeed(300f) < geometry.scrubSpeed(150f))
        assertTrue(geometry.scrubSpeed(300f) > 0f)
    }
}
