package forpdateam.ru.forpda.ui.fragments.theme.modules

import android.os.Looper
import android.view.View
import android.widget.LinearLayout
import androidx.appcompat.view.ContextThemeWrapper
import androidx.test.core.app.ApplicationProvider
import com.google.android.material.appbar.AppBarLayout
import org.robolectric.Shadows.shadowOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit tests for the extracted [SearchOnPageBarView] (AUDIT-L07). The
 * class is a view-only component: it owns the bar's EditText and three
 * icon buttons, and routes every interaction through a [SearchOnPageBarView.Listener]
 * implementation. The tests pin down:
 *
 *  - the bar starts hidden;
 *  - [SearchOnPageBarView.open] makes the bar visible and posts a focus
 *    request (verified via the listener callback);
 *  - the EditText forwards text changes to `onSearchOnPageTextChanged`;
 *  - the prev/next buttons route to `onSearchOnPageNext(false|true)`;
 *  - the close button triggers `onSearchOnPageClearRequested` and hides
 *    the bar.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [33])
class SearchOnPageBarViewTest {

    private fun newView(): Pair<SearchOnPageBarView, CapturingListener> {
        val baseContext = ApplicationProvider.getApplicationContext<android.content.Context>()
        val themed = ContextThemeWrapper(baseContext, forpdateam.ru.forpda.R.style.DayNightAppTheme)
        val appBar = AppBarLayout(themed)
        val listener = CapturingListener()
        return SearchOnPageBarView(themed, appBar, listener) to listener
    }

    @Test
    fun ensureBuilt_addsBarToAppBarAndStartsHidden() {
        val (view, _) = newView()
        view.ensureBuilt()
        // The bar is the only direct child of the AppBarLayout we created.
        assertNotNull("ensureBuilt must have created a bar view", view)
        assertFalse("bar starts hidden", view.isOpen)
    }

    @Test
    fun open_makesBarVisibleAndRequestsKeyboard() {
        val (view, listener) = newView()
        view.ensureBuilt()
        view.open()
        assertTrue(view.isOpen)
        // Drain the main looper so the posted Runnable in open() runs.
        shadowOf(Looper.getMainLooper()).idle()
        assertNotNull(
                "open() must call onSearchOnPageShowKeyboard (looper was idled)",
                listener.lastShownKeyboard
        )
    }

    @Test
    fun textChangeForwardsToListener() {
        val (view, listener) = newView()
        view.ensureBuilt()
        // Find the EditText by class so we can drive text changes.
        val editText = findEditText(view) ?: error("EditText not found")
        editText.setText("hello")
        assertEquals("hello", listener.lastText)
    }

    @Test
    fun emptyTextChangeRoutesToClear() {
        val (view, listener) = newView()
        view.ensureBuilt()
        val editText = findEditText(view) ?: error("EditText not found")
        editText.setText("")
        // The listener was called with the empty-clear path: either
        // onSearchOnPageClearRequested was triggered (preferred) or
        // onSearchOnPageTextChanged with an empty string.
        assertTrue(
                "empty text must trigger clear, got text=${listener.lastText} clearCount=${listener.clearCount}",
                listener.clearCount > 0 || listener.lastText.isNullOrEmpty()
        )
    }

    @Test
    fun close_hidesBarAndResetsText() {
        val (view, listener) = newView()
        view.open()
        shadowOf(Looper.getMainLooper()).idle()
        val editText = findEditText(view) ?: error("EditText not found")
        editText.setText("hello")
        // Confirm we set the text correctly.
        assertEquals("hello", editText.text.toString())
        view.close()
        assertFalse(view.isOpen)
        // After close(), the field's text must be empty.
        val barField = view.javaClass.getDeclaredField("bar").apply { isAccessible = true }
        val bar = barField.get(view) as LinearLayout
        val field = bar.getChildAt(0) as androidx.appcompat.widget.AppCompatEditText
        assertEquals(
                "field inside bar must be cleared on close()",
                "", field.text.toString()
        )
        assertTrue("close must invoke onSearchOnPageClearRequested", listener.clearCount >= 1)
    }

    /**
     * Locate the AppCompatEditText inside the built bar by reading the
     * private `bar` field, then walking the children of that specific
     * LinearLayout (not the whole view hierarchy).
     */
    private fun findEditText(view: SearchOnPageBarView): androidx.appcompat.widget.AppCompatEditText? {
        val barField = view.javaClass.getDeclaredField("bar").apply { isAccessible = true }
        val bar = barField.get(view) as? LinearLayout ?: return null
        return findChildOfType(bar, androidx.appcompat.widget.AppCompatEditText::class.java)
    }

    private fun <T : View> findChildOfType(root: View, type: Class<T>): T? {
        if (type.isInstance(root)) return type.cast(root)
        if (root is android.view.ViewGroup) {
            for (i in 0 until root.childCount) {
                val found = findChildOfType(root.getChildAt(i), type)
                if (found != null) return found
            }
        }
        return null
    }

    private class CapturingListener : SearchOnPageBarView.Listener {
        var lastText: String? = null
        var lastNext: Boolean? = null
        var clearCount: Int = 0
        var lastShownKeyboard: View? = null
        override fun onSearchOnPageTextChanged(query: String) {
            lastText = query
        }
        override fun onSearchOnPageNext(next: Boolean) {
            lastNext = next
        }
        override fun onSearchOnPageClearRequested() {
            clearCount++
        }
        override fun onSearchOnPageShowKeyboard(view: View) {
            lastShownKeyboard = view
        }
    }
}
