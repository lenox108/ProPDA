package forpdateam.ru.forpda.ui.fragments.theme.modules

import android.app.Activity
import android.view.View
import android.widget.FrameLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper
import org.robolectric.shadows.ShadowSystemClock
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ThemeToolbarScrollControllerTest {

    private lateinit var appBarLayout: View
    private lateinit var linkedShadow: View
    private lateinit var controller: ThemeToolbarScrollController

    @Before
    fun setUp() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val context = RuntimeEnvironment.getApplication()
        val root = FrameLayout(context).apply {
            layoutParams = FrameLayout.LayoutParams(1080, 2400)
        }
        appBarLayout = View(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    168,
            )
        }
        linkedShadow = View(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    8,
            )
        }
        root.addView(appBarLayout)
        root.addView(linkedShadow)
        activity.setContentView(root)
        root.measure(
                View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(2400, View.MeasureSpec.EXACTLY),
        )
        root.layout(0, 0, 1080, 2400)

        controller = ThemeToolbarScrollController(
                appBarLayout = appBarLayout,
                linkedTranslationViews = listOf(linkedShadow),
                shouldStayVisible = { false },
        )
        controller.bind()
        controller.setEnabled(true)
    }

    @Test
    fun onScroll_downPastThreshold_hidesToolbarAndLinkedChrome() {
        ShadowSystemClock.advanceBy(300, TimeUnit.MILLISECONDS)
        controller.onScroll(scrollY = 200, oldScrollY = 0)
        ShadowLooper.idleMainLooper()

        assertEquals(
                ThemeToolbarScrollController.State.HIDDEN,
                controller.state,
        )
    }

    @Test
    fun onScroll_userScrollDownHidesToolbarWithoutThresholdDelay() {
        controller.onScroll(scrollY = 1, oldScrollY = 0, userScroll = true)
        ShadowLooper.idleMainLooper()

        assertEquals(
                ThemeToolbarScrollController.State.HIDDEN,
                controller.state,
        )
    }

    @Test
    fun onScroll_upPastThreshold_showsToolbar() {
        ShadowSystemClock.advanceBy(300, TimeUnit.MILLISECONDS)
        controller.hide(force = true)
        ShadowLooper.idleMainLooper()

        ShadowSystemClock.advanceBy(300, TimeUnit.MILLISECONDS)
        controller.onScroll(scrollY = 150, oldScrollY = 200)
        ShadowLooper.idleMainLooper()

        assertEquals(
                ThemeToolbarScrollController.State.VISIBLE,
                controller.state,
        )
        assertEquals(0f, appBarLayout.translationY, 0.01f)
        assertEquals(0f, linkedShadow.translationY, 0.01f)
    }

    @Test
    fun onScroll_whenShouldStayVisible_keepsToolbarVisible() {
        controller = ThemeToolbarScrollController(
                appBarLayout = appBarLayout,
                linkedTranslationViews = listOf(linkedShadow),
                shouldStayVisible = { true },
        )
        controller.bind()
        controller.setEnabled(true)

        controller.onScroll(scrollY = 400, oldScrollY = 0)

        assertEquals(
                ThemeToolbarScrollController.State.VISIBLE,
                controller.state,
        )
        assertEquals(0f, appBarLayout.translationY, 0.01f)
    }

    @Test
    fun setEnabled_false_resetsVisibleState() {
        ShadowSystemClock.advanceBy(300, TimeUnit.MILLISECONDS)
        controller.onScroll(scrollY = 200, oldScrollY = 0)
        ShadowLooper.idleMainLooper()
        controller.setEnabled(false)

        assertEquals(
                ThemeToolbarScrollController.State.VISIBLE,
                controller.state,
        )
        assertEquals(0f, appBarLayout.translationY, 0.01f)
    }
}
