package forpdateam.ru.forpda.ui.fragments.theme.nativerender

import android.content.Context
import android.text.Selection
import android.text.Spannable
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
class TopicTextSelectionStateTest {

    private val context: Context =
            ApplicationProvider.getApplicationContext()

    @Test
    fun `non-empty selection blocks page swipe`() {
        val text = TextView(context).apply {
            setText("Текст цитаты", TextView.BufferType.SPANNABLE)
            setTextIsSelectable(true)
        }
        Selection.setSelection(text.text as Spannable, 0, 5)

        assertTrue(TopicTextSelectionState.isActive(text))
    }

    @Test
    fun `focused selectable text without a range does not block normal page swipe`() {
        val text = TextView(context).apply {
            setText("Текст цитаты", TextView.BufferType.SPANNABLE)
            setTextIsSelectable(true)
        }
        Selection.setSelection(text.text as Spannable, 3)

        assertFalse(TopicTextSelectionState.isActive(text))
    }

    @Test
    fun `non-text focus does not block page swipe`() {
        assertFalse(TopicTextSelectionState.isActive(android.view.View(context)))
        assertFalse(TopicTextSelectionState.isActive(null))
    }
}
