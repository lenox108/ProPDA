package forpdateam.ru.forpda.notifications

import forpdateam.ru.forpda.entity.remote.events.NotificationEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationEventNotifyIdTest {

    private fun event(
            type: NotificationEvent.Type,
            source: NotificationEvent.Source,
            sourceId: Int
    ) = NotificationEvent(type, source).apply { this.sourceId = sourceId }

    @Test
    fun notifyId_differsForAdjacentSourceIds() {
        // Старая формула делила sourceId на 4, поэтому темы 100..103 получали один ID
        // и перетирали уведомления друг друга.
        val ids = (100..103).map {
            event(NotificationEvent.Type.NEW, NotificationEvent.Source.THEME, it).notifyId()
        }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun notifyId_differsAcrossSourcesWithSameSourceId() {
        val theme = event(NotificationEvent.Type.NEW, NotificationEvent.Source.THEME, 1000)
        val qms = event(NotificationEvent.Type.NEW, NotificationEvent.Source.QMS, 1000)
        val site = event(NotificationEvent.Type.NEW, NotificationEvent.Source.SITE, 1000)

        assertEquals(3, setOf(theme.notifyId(), qms.notifyId(), site.notifyId()).size)
    }

    @Test
    fun notifyId_differsForEveryTypeOfSameTopic() {
        val ids = NotificationEvent.Type.entries.map {
            event(it, NotificationEvent.Source.THEME, 42).notifyId()
        }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun notifyId_differsForNewAndMentionSameTopic() {
        val topicId = 42
        val newEvent = event(NotificationEvent.Type.NEW, NotificationEvent.Source.THEME, topicId)
        val mention = event(NotificationEvent.Type.MENTION, NotificationEvent.Source.THEME, topicId)
        assertNotEquals(newEvent.notifyId(), mention.notifyId())
    }

    @Test
    fun notifyId_typeOverrideMatchesEventOfThatType() {
        val read = event(NotificationEvent.Type.READ, NotificationEvent.Source.THEME, 777)
        val new = event(NotificationEvent.Type.NEW, NotificationEvent.Source.THEME, 777)
        // checkOldEvent() ищет прежнее NEW-уведомление по READ-событию — эти ID обязаны совпасть.
        assertEquals(new.notifyId(), read.notifyId(NotificationEvent.Type.NEW))
    }

    @Test
    fun notifyId_stableForSameEvent() {
        val e = event(NotificationEvent.Type.MENTION, NotificationEvent.Source.SITE, 99)
        assertEquals(e.notifyId(), e.notifyId())
    }

    @Test
    fun notifyId_alwaysNonNegative() {
        // Отрицательные ID зарезервированы под stacked/foreground-уведомления.
        val ids = listOf(0, 1, 4242, 1_200_000, 0x07FFFFFF).flatMap { sourceId ->
            NotificationEvent.Source.entries.map { source ->
                event(NotificationEvent.Type.NEW, source, sourceId).notifyId()
            }
        }
        assertTrue(ids.all { it >= 0 })
    }

    @Test
    fun notifyId_systemQmsThreadWithZeroSourceIdDoesNotCollideWithTheme() {
        // «Сообщения 4PDA» приходят с sourceId == 0 — раньше это давало коллизию с темой 0..3.
        val systemQms = event(NotificationEvent.Type.NEW, NotificationEvent.Source.QMS, 0)
        val theme = event(NotificationEvent.Type.NEW, NotificationEvent.Source.THEME, 0)
        assertNotEquals(systemQms.notifyId(), theme.notifyId())
    }
}
