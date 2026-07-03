package forpdateam.ru.forpda.model.data.remote.api.news

import org.junit.Assert.assertEquals
import org.junit.Test

class NewsCommentEditTextTest {

    @Test
    fun `strips trailing otredaktirovan marker from edit text`() {
        // Точный воспроизведённый кейс: форма правки уже-редактированного коммента.
        assertEquals("Lenox30,", stripNewsCommentEditedMarker("Lenox30, (отредактирован)"))
    }

    @Test
    fun `strips otredaktirovan marker with trailing details`() {
        assertEquals(
                "мой текст",
                stripNewsCommentEditedMarker("мой текст (отредактировано 03.07.26, 10:20)")
        )
    }

    @Test
    fun `strips english edited by marker`() {
        assertEquals("my comment", stripNewsCommentEditedMarker("my comment (message edited by admin)"))
    }

    @Test
    fun `keeps clean text untouched`() {
        assertEquals("обычный коммент без маркера", stripNewsCommentEditedMarker("обычный коммент без маркера"))
    }

    @Test
    fun `keeps ordinary parentheses untouched`() {
        assertEquals("текст (в скобках пояснение)", stripNewsCommentEditedMarker("текст (в скобках пояснение)"))
    }
}
