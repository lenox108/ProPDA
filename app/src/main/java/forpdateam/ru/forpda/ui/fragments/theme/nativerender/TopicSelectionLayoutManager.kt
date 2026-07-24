package forpdateam.ru.forpda.ui.fragments.theme.nativerender

import android.content.Context
import android.graphics.Rect
import android.view.View
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

/**
 * Prevents RecyclerView's focus machinery from scrolling a selectable post TextView when Android
 * starts or adjusts its selection ActionMode.
 *
 * A quote is nested several containers deep inside a variable-height post. The framework focuses
 * its TextView on long-press and immediately calls both focus-scroll hooks; the ordinary
 * LinearLayoutManager can move/re-layout the row before selection handles are established, making
 * the long-press look like it did nothing. Finger scrolling and programmatic topic navigation are
 * unaffected because only requests originating from a currently selectable TextView are consumed.
 */
internal class TopicSelectionLayoutManager(context: Context) : LinearLayoutManager(context) {

    override fun onRequestChildFocus(
            parent: RecyclerView,
            state: RecyclerView.State,
            child: View,
            focused: View?,
    ): Boolean {
        if (focused.isSelectableText()) return true
        return super.onRequestChildFocus(parent, state, child, focused)
    }

    override fun requestChildRectangleOnScreen(
            parent: RecyclerView,
            child: View,
            rect: Rect,
            immediate: Boolean,
            focusedChildVisible: Boolean,
    ): Boolean {
        if (child.findFocus().isSelectableText()) return false
        return super.requestChildRectangleOnScreen(
                parent,
                child,
                rect,
                immediate,
                focusedChildVisible,
        )
    }

    private fun View?.isSelectableText(): Boolean =
            this is TextView && isTextSelectable
}
