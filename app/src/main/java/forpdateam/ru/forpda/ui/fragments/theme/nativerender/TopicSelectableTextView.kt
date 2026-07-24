package forpdateam.ru.forpda.ui.fragments.theme.nativerender

import android.content.Context
import android.util.AttributeSet
import android.widget.TextView

/**
 * Text used inside a native quote that is measured before it is attached to the activity window.
 *
 * Android can create the text Layout during that detached measurement and disable Editor's
 * selection controller because the temporary root is not an application window. Some OEM builds
 * reuse the Layout after attachment without preparing the controller again. Re-applying the
 * existing movement method after attachment makes TextView rebuild only that platform controller.
 */
internal class TopicSelectableTextView @JvmOverloads constructor(
        context: Context,
        attrs: AttributeSet? = null,
) : TextView(context, attrs) {

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        recoverSelectionController()
    }

    private fun recoverSelectionController() {
        // setMovementMethod() invokes Editor.prepareCursorControllers() only when the value changes.
        // Preserve SelectableLinkMovementMethod when the quoted text also contains a link.
        val selectionMovementMethod = movementMethod ?: return
        movementMethod = null
        movementMethod = selectionMovementMethod
    }
}
