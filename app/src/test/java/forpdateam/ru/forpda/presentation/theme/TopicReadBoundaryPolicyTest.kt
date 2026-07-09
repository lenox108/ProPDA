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

    @Test
    fun serverAtFirstUnseen_noOverride_trustServer() {
        // Свежий ответ (500) — ПЕРВЫЙ не-виденный пост сразу за границей (100), сервер сел ровно на него.
        // Ничего не пропущено → не резюмим на границу (иначе 500 обрезался бы снизу под прочитанным 100).
        // Это кейс «ответ на свой пост»: юзер видел свой пост (100), пришёл ответ (500) — открыть надо ответ.
        assertNull(TopicReadBoundaryPolicy.resumeAnchorPostId(100, 500, 500, firstUnseenPostId = 500))
    }

    @Test
    fun serverBelowFirstUnseen_walkDown_resumeAtBoundary() {
        // Реальный walk-down: между границей (100) и серверным таргетом (500) есть не-виденные посты —
        // первый из них 200. Сервер проскочил 200..499 → резюмим на границу, чтобы не пропустить их.
        assertEquals(100, TopicReadBoundaryPolicy.resumeAnchorPostId(100, 500, 900, firstUnseenPostId = 200))
    }

    @Test
    fun firstUnseenNull_fallsBackToBoundaryComparison() {
        // firstUnseen неизвестен → прежнее поведение (резюм на границу при server > boundary).
        assertEquals(100, TopicReadBoundaryPolicy.resumeAnchorPostId(100, 500, 900, firstUnseenPostId = null))
    }
}
