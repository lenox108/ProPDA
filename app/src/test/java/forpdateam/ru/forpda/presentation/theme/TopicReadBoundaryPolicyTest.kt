package forpdateam.ru.forpda.presentation.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TopicReadBoundaryPolicyTest {

    @Test
    fun noBoundary_noOverride() {
        assertNull(TopicReadBoundaryPolicy.resumeAnchorPostId(0, 500, 900))
    }

    @Test
    fun serverBelowBoundary_resumeAtBoundary() {
        // Юзер видел до 100, сервер увёл на 500 (walk-down) → резюм на 100, чтобы не пропустить 101..499.
        assertEquals(100, TopicReadBoundaryPolicy.resumeAnchorPostId(100, 500, 900))
    }

    @Test
    fun serverAtBoundary_noOverride() {
        assertNull(TopicReadBoundaryPolicy.resumeAnchorPostId(100, 100, 200))
    }

    @Test
    fun serverBeforeBoundary_noOverride_trustServer() {
        // Сервер указывает раньше границы (есть непрочитанное до неё) — доверяем серверу.
        assertNull(TopicReadBoundaryPolicy.resumeAnchorPostId(100, 80, 200))
    }

    @Test
    fun ambiguousAllRead_lastLoadedBelowBoundary_resumeAtBoundary() {
        // Сервер думает «всё прочитано» (anchor null), сел бы на низ 900; юзер видел до 100 → резюм на 100.
        assertEquals(100, TopicReadBoundaryPolicy.resumeAnchorPostId(100, null, 900))
    }

    @Test
    fun ambiguousAllRead_lastLoadedAtOrBelowBoundary_noOverride() {
        // Низ загруженного окна не новее границы — переопределять нечего.
        assertNull(TopicReadBoundaryPolicy.resumeAnchorPostId(100, null, 100))
        assertNull(TopicReadBoundaryPolicy.resumeAnchorPostId(100, null, null))
    }
}
