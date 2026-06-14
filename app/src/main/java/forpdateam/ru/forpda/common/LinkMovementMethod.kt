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
import android.widget.TextView

/**
 * Created by radiationx on 19.09.16.
 */
class LinkMovementMethod(private val listener: ClickListener?) : ScrollingMovementMethod() {

    interface ClickListener {
        fun onClick(url: String): Boolean
    }

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
        val action = event.action
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_DOWN) {
            var x = event.x.toInt()
            var y = event.y.toInt()
            x -= widget.totalPaddingLeft
            y -= widget.totalPaddingTop
            x += widget.scrollX
            y += widget.scrollY
            val layout = widget.layout
            val line = layout.getLineForVertical(y)
            val off = layout.getOffsetForHorizontal(line, x.toFloat())
            val link = buffer.getSpans(off, off, ClickableSpan::class.java)
            if (link.isNotEmpty()) {
                if (action == MotionEvent.ACTION_UP) {
                    val url = (link[0] as URLSpan).url
                    if (listener?.onClick(url) != true) {
                        link[0].onClick(widget)
                    }
                } else {
                    Selection.setSelection(buffer, buffer.getSpanStart(link[0]), buffer.getSpanEnd(link[0]))
                }
                return true
            } else {
                Selection.removeSelection(buffer)
            }
        }
        return super.onTouchEvent(widget, buffer, event)
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
