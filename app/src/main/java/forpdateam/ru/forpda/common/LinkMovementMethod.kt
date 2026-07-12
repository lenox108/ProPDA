package forpdateam.ru.forpda.common

import android.text.Layout
import android.text.NoCopySpan
import android.text.Selection
import android.text.Spannable
import android.text.method.ScrollingMovementMethod
import android.text.style.ClickableSpan
import android.text.style.URLSpan
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.TextView
import kotlin.math.abs

/**
 * Created by radiationx on 19.09.16.
 */
class LinkMovementMethod(private val listener: ClickListener?) : ScrollingMovementMethod() {

    interface ClickListener {
        fun onClick(url: String): Boolean

        /** Long-press over a link. Return `true` if handled (suppresses the following tap). Default
         *  no-op so callers that only care about taps don't need to implement it. */
        fun onLongClick(url: String): Boolean = false
    }

    // Long-press tracking: one gesture at a time, all on the main thread, so plain fields are safe.
    private var longPressRunnable: Runnable? = null
    private var longPressFired = false
    private var downX = 0f
    private var downY = 0f

    companion object {
        private val FROM_BELOW = NoCopySpan.Concrete()
        private const val CLICK = 1
        private const val UP = 2
        private const val DOWN = 3
    }

    override fun canSelectArbitrarily(): Boolean = true

    override fun handleMovementKey(widget: TextView, buffer: Spannable, keyCode: Int,
        movementMetaState: Int, event: KeyEvent): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                if (KeyEvent.metaStateHasNoModifiers(movementMetaState)) {
                    if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0 && action(CLICK, widget, buffer)) {
                        return true
                    }
                }
            }
        }
        return super.handleMovementKey(widget, buffer, keyCode, movementMetaState, event)
    }

    override fun up(widget: TextView, buffer: Spannable): Boolean =
        action(UP, widget, buffer) || super.up(widget, buffer)

    override fun down(widget: TextView, buffer: Spannable): Boolean =
        action(DOWN, widget, buffer) || super.down(widget, buffer)

    override fun left(widget: TextView, buffer: Spannable): Boolean =
        action(UP, widget, buffer) || super.left(widget, buffer)

    override fun right(widget: TextView, buffer: Spannable): Boolean =
        action(DOWN, widget, buffer) || super.right(widget, buffer)

    private fun action(what: Int, widget: TextView, buffer: Spannable): Boolean {
        val layout = widget.layout
        val padding = widget.totalPaddingTop + widget.totalPaddingBottom
        val areatop = widget.scrollY
        val areabot = areatop + widget.height - padding
        val linetop = layout.getLineForVertical(areatop)
        val linebot = layout.getLineForVertical(areabot)
        val first = layout.getLineStart(linetop)
        val last = layout.getLineEnd(linebot)
        val candidates = buffer.getSpans(first, last, ClickableSpan::class.java)

        val a = Selection.getSelectionStart(buffer)
        val b = Selection.getSelectionEnd(buffer)
        var selStart = minOf(a, b)
        var selEnd = maxOf(a, b)

        if (selStart < 0) {
            if (buffer.getSpanStart(FROM_BELOW) >= 0) {
                selStart = buffer.length; selEnd = buffer.length
            }
        }
        if (selStart > last) { selStart = Int.MAX_VALUE; selEnd = Int.MAX_VALUE }
        if (selEnd < first) { selStart = -1; selEnd = -1 }

        when (what) {
            CLICK -> {
                if (selStart == selEnd) return false
                val link = buffer.getSpans(selStart, selEnd, ClickableSpan::class.java)
                if (link.size != 1) return false
                link[0].onClick(widget)
            }
            UP -> {
                var beststart = -1; var bestend = -1
                for (candidate in candidates) {
                    val end = buffer.getSpanEnd(candidate)
                    if (end < selEnd || selStart == selEnd) {
                        if (end > bestend) {
                            beststart = buffer.getSpanStart(candidate); bestend = end
                        }
                    }
                }
                if (beststart >= 0) { Selection.setSelection(buffer, bestend, beststart); return true }
            }
            DOWN -> {
                var beststart = Int.MAX_VALUE; var bestend = Int.MAX_VALUE
                for (candidate in candidates) {
                    val start = buffer.getSpanStart(candidate)
                    if (start > selStart || selStart == selEnd) {
                        if (start < beststart) {
                            beststart = start; bestend = buffer.getSpanEnd(candidate)
                        }
                    }
                }
                if (bestend < Int.MAX_VALUE) { Selection.setSelection(buffer, beststart, bestend); return true }
            }
        }
        return false
    }

    override fun onTouchEvent(widget: TextView, buffer: Spannable, event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                longPressFired = false
                val link = linkAt(widget, buffer, event)
                if (link != null) {
                    Selection.setSelection(buffer, buffer.getSpanStart(link), buffer.getSpanEnd(link))
                    downX = event.x; downY = event.y
                    scheduleLongPress(widget, buffer, (link as URLSpan).url)
                    return true
                } else {
                    Selection.removeSelection(buffer)
                }
            }
            MotionEvent.ACTION_MOVE -> {
                // A real drag (RecyclerView scroll) must not fire a long-press: cancel once past slop.
                if (longPressRunnable != null) {
                    val slop = ViewConfiguration.get(widget.context).scaledTouchSlop
                    if (abs(event.x - downX) > slop || abs(event.y - downY) > slop) {
                        cancelLongPress(widget)
                    }
                }
            }
            MotionEvent.ACTION_UP -> {
                cancelLongPress(widget)
                if (longPressFired) {
                    // The long-press already handled this gesture — swallow the trailing tap.
                    longPressFired = false
                    Selection.removeSelection(buffer)
                    return true
                }
                val link = linkAt(widget, buffer, event)
                if (link != null) {
                    val url = (link as URLSpan).url
                    if (listener?.onClick(url) != true) {
                        link.onClick(widget)
                    }
                    return true
                } else {
                    Selection.removeSelection(buffer)
                }
            }
            MotionEvent.ACTION_CANCEL -> {
                cancelLongPress(widget)
                longPressFired = false
                Selection.removeSelection(buffer)
            }
        }
        return super.onTouchEvent(widget, buffer, event)
    }

    /** The [ClickableSpan] under the touch point, or `null` if the tap missed every link. */
    private fun linkAt(widget: TextView, buffer: Spannable, event: MotionEvent): ClickableSpan? {
        val layout = widget.layout ?: return null
        var x = event.x.toInt()
        var y = event.y.toInt()
        x -= widget.totalPaddingLeft
        y -= widget.totalPaddingTop
        x += widget.scrollX
        y += widget.scrollY
        val line = layout.getLineForVertical(y)
        val off = layout.getOffsetForHorizontal(line, x.toFloat())
        return buffer.getSpans(off, off, ClickableSpan::class.java).firstOrNull()
    }

    private fun scheduleLongPress(widget: TextView, buffer: Spannable, url: String) {
        cancelLongPress(widget)
        val r = Runnable {
            longPressFired = true
            Selection.removeSelection(buffer)
            listener?.onLongClick(url)
        }
        longPressRunnable = r
        widget.postDelayed(r, ViewConfiguration.getLongPressTimeout().toLong())
    }

    private fun cancelLongPress(widget: TextView) {
        longPressRunnable?.let { widget.removeCallbacks(it) }
        longPressRunnable = null
    }

    override fun initialize(widget: TextView, text: Spannable) {
        Selection.removeSelection(text)
        text.removeSpan(FROM_BELOW)
    }

    override fun onTakeFocus(view: TextView, text: Spannable, dir: Int) {
        Selection.removeSelection(text)
        if (dir and View.FOCUS_BACKWARD != 0) {
            text.setSpan(FROM_BELOW, 0, 0, Spannable.SPAN_POINT_POINT)
        } else {
            text.removeSpan(FROM_BELOW)
        }
    }
}
