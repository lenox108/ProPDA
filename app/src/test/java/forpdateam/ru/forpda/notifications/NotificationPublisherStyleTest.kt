package forpdateam.ru.forpda.notifications

import android.graphics.Bitmap
import forpdateam.ru.forpda.entity.remote.events.NotificationEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Smoke test for the per-event pieces of [NotificationPublisher].
 * Runs under Robolectric so the [android.content.Context] is real.
 */
@RunWith(RobolectricTestRunner::class)
class NotificationPublisherStyleTest {

    @Test
    fun avatar_isKeptOnlyForQmsNotifications() {
        val avatar = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        val qms = NotificationEvent(
            NotificationEvent.Type.NEW,
            NotificationEvent.Source.QMS,
        )
        val theme = NotificationEvent(
            NotificationEvent.Type.NEW,
            NotificationEvent.Source.THEME,
        )

        assertSame(avatar, NotificationPublisher.avatarFor(qms, avatar))
        assertNull(NotificationPublisher.avatarFor(theme, avatar))
    }

    @Test
    fun groupKey_matchesTheChannelOfTheEvent() {
        val cases = mapOf(
            NotificationGroups.QMS to NotificationsService.CHANNEL_QMS_ID,
            NotificationGroups.FAV to NotificationsService.CHANNEL_FAV_ID,
            NotificationGroups.MENTION to NotificationsService.CHANNEL_MENTION_ID,
            NotificationGroups.SITE to NotificationsService.CHANNEL_SITE_ID,
        )
        for ((group, channel) in cases) {
            assertEquals(channel, NotificationGroups.channelIdFor(group))
        }
    }

    @Test
    fun summaryIds_areDistinctAndNegative() {
        val ids = NotificationGroups.SUMMARY_IDS
        assertEquals(ids.size, ids.toSet().size)
        // Пространство event.notifyId() неотрицательно — сводка не должна в него попадать.
        assertEquals(emptyList<Int>(), ids.filter { it >= 0 })
    }
}
