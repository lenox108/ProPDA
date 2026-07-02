package forpdateam.ru.forpda.notifications

import forpdateam.ru.forpda.entity.remote.events.NotificationEvent
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Пинит чистую логику группировки уведомлений: одинаковый ключ для однотипных
 * событий (→ Android свернёт их в один бандл) и разный для разных типов. Ключи
 * — часть UX-контракта бандлов, менять осознанно.
 */
class NotificationActionsTest {

    private fun event(type: NotificationEvent.Type, source: NotificationEvent.Source) =
            NotificationEvent(type = type, source = source)

    @Test
    fun groupKey_separatesByType() {
        val qms = NotificationActions.groupKeyFor(event(NotificationEvent.Type.NEW, NotificationEvent.Source.QMS))
        val fav = NotificationActions.groupKeyFor(event(NotificationEvent.Type.NEW, NotificationEvent.Source.THEME))
        val site = NotificationActions.groupKeyFor(event(NotificationEvent.Type.NEW, NotificationEvent.Source.SITE))
        val mention = NotificationActions.groupKeyFor(event(NotificationEvent.Type.MENTION, NotificationEvent.Source.THEME))

        // Все четыре ключа различны — типы не смешиваются в одном бандле.
        assertEquals(4, setOf(qms, fav, site, mention).size)
    }

    @Test
    fun groupKey_sameForSameType() {
        val a = NotificationActions.groupKeyFor(event(NotificationEvent.Type.NEW, NotificationEvent.Source.QMS))
        val b = NotificationActions.groupKeyFor(event(NotificationEvent.Type.NEW, NotificationEvent.Source.QMS))
        assertEquals(a, b)
    }

    @Test
    fun groupKey_mentionWinsOverSource() {
        // Упоминание группируется как mention независимо от источника (тема/сайт).
        val mentionTheme = NotificationActions.groupKeyFor(event(NotificationEvent.Type.MENTION, NotificationEvent.Source.THEME))
        val mentionSite = NotificationActions.groupKeyFor(event(NotificationEvent.Type.MENTION, NotificationEvent.Source.SITE))
        assertEquals(mentionTheme, mentionSite)
    }
}
