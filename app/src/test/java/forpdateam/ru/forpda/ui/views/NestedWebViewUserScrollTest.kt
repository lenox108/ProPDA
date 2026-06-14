package forpdateam.ru.forpda.ui.views

import android.view.MotionEvent
import androidx.fragment.app.FragmentActivity
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class NestedWebViewUserScrollTest {

    private lateinit var webView: ExtendedWebView

    @Before
    fun setUp() {
        val activity = Robolectric.buildActivity(FragmentActivity::class.java).setup().get()
        webView = ExtendedWebView(activity)
        activity.setContentView(webView)
    }

    @Test
    fun markUserTouchForScroll_marksUserScrollActive() {
        assertFalse(webView.isUserScrollActive())

        webView.markUserTouchForScroll()

        assertTrue(webView.isUserScrollActive())
    }

    @Test
    fun onTouchEvent_down_marksUserScrollActive() {
        assertFalse(webView.isUserScrollActive())

        webView.onTouchEvent(
                MotionEvent.obtain(0L, 0L, MotionEvent.ACTION_DOWN, 100f, 100f, 0),
        )

        assertTrue(webView.isUserScrollActive())
    }

    @Test
    fun onScrollChanged_withoutTouch_marksUserScrollActive() {
        assertFalse(webView.isUserScrollActive())

        webView.scrollTo(0, 120)

        assertTrue(webView.isUserScrollActive())
    }
}
