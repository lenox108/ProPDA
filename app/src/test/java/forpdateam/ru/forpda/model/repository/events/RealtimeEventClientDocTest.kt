package forpdateam.ru.forpda.model.repository.events

import forpdateam.ru.forpda.entity.remote.events.NotificationEvent
import forpdateam.ru.forpda.model.data.remote.api.events.NotificationEventsApi
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Кадр события app-протокола → текстовый вид легаси-канала → штатный парсер.
 * Форма кадра снята живьём с сервера 4PDA (30.07.2026) и сверена с диспетчером офиц. клиента:
 * `doc[0] == 30309 ("ev") && doc[1] == 0`, тело `["<тип><id>", код, messageId]`.
 */
class RealtimeEventClientDocTest {

    // Разбор не ходит в сеть — webClient нужен только конструктору.
    private val api = NotificationEventsApi(mockk(relaxed = true))

    private fun parse(doc: List<Any?>): NotificationEvent? =
            RealtimeEventClient.eventDocToLegacyText(doc)?.let { api.parseWebSocketEvent(it) }

    @Test
    fun `новое сообщение qms разбирается как QMS NEW`() {
        // Реальный кадр: [30309, 0, q9432633, 1, 126482833]
        val event = parse(listOf(30309, 0, "q9432633", 1, 126482833))!!
        assertEquals(NotificationEvent.Source.QMS, event.source)
        assertEquals(NotificationEvent.Type.NEW, event.type)
        assertEquals(9432633, event.sourceId)
        assertEquals(126482833, event.messageId)
    }

    @Test
    fun `прочтение диалога разбирается как READ`() {
        val event = parse(listOf(30309, 0, "q9432633", 2, 126482833))!!
        assertEquals(NotificationEvent.Type.READ, event.type)
    }

    @Test
    fun `новый пост в теме избранного разбирается как THEME NEW`() {
        val event = parse(listOf(30309, 0, "t1121483", 1, 1785439441))!!
        assertEquals(NotificationEvent.Source.THEME, event.source)
        assertEquals(NotificationEvent.Type.NEW, event.type)
        assertEquals(1121483, event.sourceId)
    }

    @Test
    fun `обычный ответ на запрос событием не считается`() {
        // Ответ на `ea`: [rid, 0] — не событие, иначе подписка сама себя «доставила» бы.
        assertNull(RealtimeEventClient.eventDocToLegacyText(listOf(3, 0)))
        // Ответ другого запроса той же длины, но с чужим опкодом в поле 0.
        assertNull(RealtimeEventClient.eventDocToLegacyText(listOf(7, 0, "x1", 1, 2)))
        // Ненулевой статус — ошибка, а не событие.
        assertNull(RealtimeEventClient.eventDocToLegacyText(listOf(30309, 2, "q1", 1, 2)))
    }
}
