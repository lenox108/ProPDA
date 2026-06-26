package forpdateam.ru.forpda.notifications

import forpdateam.ru.forpda.entity.remote.events.NotificationEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationEventNotifyIdTest {

    @Test
    fun notifyId_differsForDifferentSourceIds() {
        val a = NotificationEvent(NotificationEvent.Type.NEW, NotificationEvent.Source.THEME).apply {
            sourceId = 100
            messageId = 1
        }
        val b = NotificationEvent(NotificationEvent.Type.NEW, NotificationEvent.Source.THEME).apply {
            sourceId = 104
            messageId = 1
        }
        assertNotEquals(a.notifyId(), b.notifyId())
    }

    @Test
    fun notifyId_differsForNewAndMentionSameTopic() {
        val topicId = 42
        val newEvent = NotificationEvent(NotificationEvent.Type.NEW, NotificationEvent.Source.THEME).apply {
            sourceId = topicId
            messageId = 10
        }
        val mention = NotificationEvent(NotificationEvent.Type.MENTION, NotificationEvent.Source.THEME).apply {
            sourceId = topicId
            messageId = 10
        }
        assertNotEquals(newEvent.notifyId(), mention.notifyId())
    }

    @Test
    fun notifyId_stableForSameEvent() {
        val event = NotificationEvent(NotificationEvent.Type.MENTION, NotificationEvent.Source.SITE).apply {
            sourceId = 99
            messageId = 7
            userId = 3
        }
        assertEquals(event.notifyId(), event.notifyId())
    }

    @Test
    fun notifyId_alwaysNonNegative() {
        val event = NotificationEvent(NotificationEvent.Type.NEW, NotificationEvent.Source.QMS).apply {
            sourceId = 1
            userId = 2
        }
        assertTrue(event.notifyId() >= 0)
    }
}
