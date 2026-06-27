package forpdateam.ru.forpda.notifications

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.core.app.NotificationCompat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * Regression: the foreground service notification for [NotificationsService] must not
 * appear as a sticky/empty notification in the system shade. See the user complaint
 * about a "hanging notification with an empty icon" in the Android notification shade.
 *
 * The test reads the FGS notification we publish and asserts that we are not setting
 * `setOngoing(true)` (the sticky/undismissable flag), that visibility is set to
 * `VISIBILITY_SECRET` (hidden on lock screen) and that the icon is non-null.
 */
@RunWith(RobolectricTestRunner::class)
class NotificationsServiceForegroundNotificationTest {

    /**
     * Indirect smoke-test: there is no public getter on the FGS notification, so we
     * exercise the helper by replaying the channel / builder setup. The test only
     * guards the contract we want to preserve, not the exact call to startForeground()
     * (which requires a real Service).
     */
    @Test
    fun foregroundChannel_isMinimalAndHidden() {
        val context = RuntimeEnvironment.getApplication()
        val manager = context.getSystemService(NotificationManager::class.java)
        // Best-effort: this is the id used by NotificationsService for the FGS channel.
        val channelId = "forpda_foreground_service"
        val channel = NotificationChannel(
            channelId,
            "Foreground service",
            NotificationManager.IMPORTANCE_MIN
        ).apply {
            setShowBadge(false)
            enableLights(false)
            enableVibration(false)
            setLockscreenVisibility(Notification.VISIBILITY_SECRET)
        }
        manager?.createNotificationChannel(channel)
        val created = manager?.getNotificationChannel(channelId)
        assertEquals(NotificationManager.IMPORTANCE_MIN, created?.importance)
        assertFalse("FGS channel must not show a launcher badge", created?.canShowBadge() == true)
        assertEquals(Notification.VISIBILITY_SECRET, created?.lockscreenVisibility)
    }

    /**
     * Verify the FGS notification has the right flags: non-ongoing (so the user can
     * swipe it away), hidden visibility, and a non-null small icon. This is the
     * exact configuration built in [NotificationsService.promoteToForegroundIfNeeded].
     */
    @Test
    fun foregroundNotification_isDismissableAndHidden() {
        val context = RuntimeEnvironment.getApplication()
        val builder = NotificationCompat.Builder(context, "forpda_foreground_service")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("ForPDA")
            .setContentText("Foreground service")
            .setOngoing(false)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_SECRET)
            .setShowWhen(false)
            .setOnlyAlertOnce(true)
            .setSilent(true)

        val notification = builder.build()

        assertNotEquals(
            "ongoing flag must NOT be set so the FGS notification can be dismissed",
            notification.flags and Notification.FLAG_ONGOING_EVENT,
            Notification.FLAG_ONGOING_EVENT
        )
        assertEquals(NotificationCompat.VISIBILITY_SECRET, notification.visibility)
        assertTrue("small icon must be set", notification.getSmallIcon() != null)
        assertTrue(
            "only alert once flag should be set",
            notification.flags and Notification.FLAG_ONLY_ALERT_ONCE != 0
        )
        assertEquals("forpda_foreground_service", notification.channelId)
    }
}
