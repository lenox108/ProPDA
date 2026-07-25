package forpdateam.ru.forpda.ui

import android.app.Activity
import android.os.Bundle
import android.util.TypedValue
import forpdateam.ru.forpda.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = android.app.Application::class)
class FlatUiTest {

    class ThemedActivity : Activity() {
        override fun onCreate(savedInstanceState: Bundle?) {
            setTheme(R.style.DayNightAppTheme)
            super.onCreate(savedInstanceState)
        }
    }

    @Test
    fun `enabled preference removes common decorative dimensions`() {
        val activity = Robolectric.buildActivity(ThemedActivity::class.java).setup().get()
        activity.getSharedPreferences("topic_mirror", Activity.MODE_PRIVATE)
                .edit()
                .putBoolean("flat_posts", true)
                .commit()

        assertTrue(FlatUi.applyThemeOverlay(activity))
        assertEquals(0f, activity.resolveDimension(R.attr.list_plate_stroke_width), 0f)
        assertEquals(0f, activity.resolveDimension(R.attr.main_toolbar_stroke_width), 0f)
        assertEquals(0f, activity.resolveDimension(R.attr.content_card_elevation), 0f)
    }

    @Test
    fun `style policy keeps functional size outside flat mode`() {
        assertEquals(3f, FlatUiStylePolicy.decorativeSize(flat = false, normal = 3f), 0f)
        assertEquals(0f, FlatUiStylePolicy.decorativeSize(flat = true, normal = 3f), 0f)
        assertEquals(3, FlatUiStylePolicy.decorativeSize(flat = false, normal = 3))
        assertEquals(0, FlatUiStylePolicy.decorativeSize(flat = true, normal = 3))
    }

    private fun Activity.resolveDimension(attr: Int): Float {
        val value = TypedValue()
        assertTrue(theme.resolveAttribute(attr, value, true))
        return value.getDimension(resources.displayMetrics)
    }
}
