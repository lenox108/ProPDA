package forpdateam.ru.forpda.common.appicon

import android.content.Context
import androidx.core.graphics.drawable.IconCompat
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import forpdateam.ru.forpda.R
import forpdateam.ru.forpda.common.Preferences
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class NotificationIconSelectionTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val preferences by lazy { PreferenceManager.getDefaultSharedPreferences(context) }

    @Before
    @After
    fun clearIconPreferences() {
        preferences.edit()
                .remove(Preferences.Main.APP_ICON)
                .remove(Preferences.Main.NOTIFICATION_ICON)
                .commit()
    }

    @Test
    fun `event mode keeps notification-specific resource`() {
        val icon = AppIcons.notificationSmallIcon(context, R.drawable.ic_notify_qms)

        assertEquals(IconCompat.TYPE_RESOURCE, icon.type)
        assertEquals(R.drawable.ic_notify_qms, icon.resId)
    }

    @Test
    fun `specific launcher variant becomes bitmap silhouette`() {
        preferences.edit()
                .putString(Preferences.Main.NOTIFICATION_ICON, "pixel_4")
                .commit()

        val icon = AppIcons.notificationSmallIcon(context, R.drawable.ic_notify_qms)

        assertEquals(IconCompat.TYPE_BITMAP, icon.type)
    }

    @Test
    fun `match app mode follows independently selected launcher icon`() {
        preferences.edit()
                .putString(Preferences.Main.APP_ICON, "droid_4")
                .putString(
                        Preferences.Main.NOTIFICATION_ICON,
                        AppIcons.NOTIFICATION_ICON_APP,
                )
                .commit()

        val icon = AppIcons.notificationSmallIcon(context, R.drawable.ic_notify_qms)

        assertEquals(IconCompat.TYPE_BITMAP, icon.type)
    }

    @Test
    fun `removed variant safely falls back to event resource`() {
        preferences.edit()
                .putString(Preferences.Main.NOTIFICATION_ICON, "removed_icon")
                .commit()

        val icon = AppIcons.notificationSmallIcon(context, R.drawable.ic_notify_mention)

        assertEquals(IconCompat.TYPE_RESOURCE, icon.type)
        assertEquals(R.drawable.ic_notify_mention, icon.resId)
    }
}
