package forpdateam.ru.forpda.common

import android.content.Context
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import forpdateam.ru.forpda.R
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class AppToastTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val preferences = PreferenceManager.getDefaultSharedPreferences(context)

    @After
    fun clearSelection() {
        preferences.edit().remove(Preferences.Main.APP_ICON).commit()
    }

    @Test
    fun `popup uses icon selected in app settings`() {
        preferences.edit()
                .putString(Preferences.Main.APP_ICON, "droid_4")
                .commit()

        @Suppress("DEPRECATION")
        val root = AppToast.makeText(context, "Готово", AppToast.LENGTH_SHORT)
                .createFrameworkToast()
                .view as LinearLayout
        val icon = root.getChildAt(0) as ImageView

        assertEquals(R.mipmap.ic_launcher_droid_4, AppToast.selectedIconRes(context))
        assertEquals(R.mipmap.ic_launcher_droid_4, icon.tag)
    }
}
