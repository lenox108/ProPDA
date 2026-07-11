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

    @Test
    fun boundaryOffPage_ignoresFirstUnseenException_resumesToBoundary() {
        // Баг «5 непрочитанных, якорь на последнем». Юзер остановился на 95 в середине стр. N; сервер
        // пометил всю стр. N прочитанной (walk-down), новые посты ушли на стр. N+1. Открытие: сервер
        // сел на первый пост стр. N+1 (200); окно = стр. N+1, границы (95) в нём НЕТ. firstUnseen окна
        // == 200 == серверный якорь, поэтому старый эксепшен «доверяем серверу» проглотил бы непрочитанный
        // хвост стр. N (96..100). Граница off-page → эксепшен не применяем → резюм на 95.
        assertEquals(
                95,
                TopicReadBoundaryPolicy.resumeAnchorPostId(
                        boundaryPostId = 95,
                        serverAnchorPostId = 200,
                        lastLoadedPostId = 260,
                        firstUnseenPostId = 200,
                        boundaryPostOnPage = false,
                )
        )
    }

    @Test
    fun boundaryOnPage_firstUnseenException_stillTrustsServer() {
        // Ответ на свой пост в ТОМ ЖЕ окне: граница (100) и свежий ответ (500) на одной странице,
        // firstUnseen == 500 == серверный якорь. Граница on-page → эксепшен применяется, резюм не нужен
        // (иначе ответ обрезался бы снизу под прочитанным постом — регрессия read-boundary-reply-to-own).
        assertNull(
                TopicReadBoundaryPolicy.resumeAnchorPostId(
                        boundaryPostId = 100,
                        serverAnchorPostId = 500,
                        lastLoadedPostId = 500,
                        firstUnseenPostId = 500,
                        boundaryPostOnPage = true,
                )
        )
    }

    @Test
    fun boundaryOnPage_defaultsTrue_preservesLegacyBehavior() {
        // Дефолт boundaryPostOnPage=true сохраняет прежнее поведение для вызовов без нового аргумента.
        assertNull(TopicReadBoundaryPolicy.resumeAnchorPostId(100, 500, 500, firstUnseenPostId = 500))
    }
}
