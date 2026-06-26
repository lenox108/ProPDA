package forpdateam.ru.forpda.notifications

import androidx.core.app.NotificationCompat
import forpdateam.ru.forpda.entity.remote.events.NotificationEvent
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * Smoke test for the style-selector used by [NotificationPublisher.publishStacked].
 * Runs under Robolectric so the [android.content.Context] is real.
 *
 * See AUDIT-L12.
 */
@RunWith(RobolectricTestRunner::class)
class NotificationPublisherStyleTest {

    @Test
    fun stackedStyle_picksInboxForFourOrMoreEvents() {
        val context = RuntimeEnvironment.getApplication()
        val events = (1..4).map {
            NotificationEvent(
                type = NotificationEvent.Type.NEW,
                source = NotificationEvent.Source.THEME,
                sourceId = it,
                sourceTitle = "Topic $it",
            )
        }
        val style = NotificationPublisher.stackedStyle(
            context = context,
            events = events,
            title = "4 new",
            summary = "favorites",
        )
        assertTrue(
            "expected InboxStyle for 4+ events, got ${style::class.java.simpleName}",
            style is NotificationCompat.InboxStyle
        )
    }

    @Test
    fun stackedStyle_picksBigTextForSmallStacks() {
        val context = RuntimeEnvironment.getApplication()
        val events = (1..3).map {
            NotificationEvent(
                type = NotificationEvent.Type.NEW,
                source = NotificationEvent.Source.THEME,
                sourceId = it,
                sourceTitle = "Topic $it",
            )
        }
        val style = NotificationPublisher.stackedStyle(
            context = context,
            events = events,
            title = "3 new",
            summary = "favorites",
        )
        assertTrue(
            "expected BigTextStyle for < 4 events, got ${style::class.java.simpleName}",
            style is NotificationCompat.BigTextStyle
        )
    }
}
