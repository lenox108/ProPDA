package forpdateam.ru.forpda.downloads

import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class DownloadNotificationsTest {

    @Test
    fun `download notifications do not duplicate app icon as large icon`() {
        val context = RuntimeEnvironment.getApplication()

        assertNull(DownloadNotifications.baseBuilder(context).build().getLargeIcon())
        assertNull(DownloadNotifications.completedBuilder(context).build().getLargeIcon())
    }
}
