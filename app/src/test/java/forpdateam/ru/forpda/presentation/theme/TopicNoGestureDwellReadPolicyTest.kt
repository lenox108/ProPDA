package forpdateam.ru.forpda.presentation.theme

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [TopicNoGestureDwellReadPolicy] — сессия без жеста считается дочиткой только когда unread-гейт
 * mark-read всё ещё взведён, весь остаток темы был целиком виден и dwell превысил порог.
 */
class TopicNoGestureDwellReadPolicyTest {

    private fun decide(
            suppressActive: Boolean = true,
            hadGesture: Boolean = false,
            dwellMs: Long = 5_000L,
            hasNextPage: Boolean = false,
            lastVisible: Boolean = true,
    ) = TopicNoGestureDwellReadPolicy.shouldTreatVisibleTailAsRead(
            suppressEndMarkReadActive = suppressActive,
            hadUserGesture = hadGesture,
            dwellMs = dwellMs,
            hasNextPage = hasNextPage,
            lastItemFullyVisible = lastVisible,
    )

    @Test
    fun `unread-посадка без жеста, хвост виден, dwell достаточен - дочитка`() {
        assertTrue(decide())
    }

    @Test
    fun `короткий dwell (открыл и тут же закрыл) - не дочитка`() {
        assertFalse(decide(dwellMs = TopicNoGestureDwellReadPolicy.MIN_DWELL_MS - 1))
    }

    @Test
    fun `dwell ровно на пороге - дочитка`() {
        assertTrue(decide(dwellMs = TopicNoGestureDwellReadPolicy.MIN_DWELL_MS))
    }

    @Test
    fun `был жест - политика молчит, границу пишет обычный путь`() {
        assertFalse(decide(hadGesture = true))
    }

    @Test
    fun `гейт mark-read не взведён (не unread-посадка или уже снят) - молчим`() {
        assertFalse(decide(suppressActive = false))
    }

    @Test
    fun `ниже есть незагруженные страницы - вьюпорт не весь остаток темы`() {
        assertFalse(decide(hasNextPage = true))
    }

    @Test
    fun `последний элемент не был целиком виден - не дочитка`() {
        assertFalse(decide(lastVisible = false))
    }
}
