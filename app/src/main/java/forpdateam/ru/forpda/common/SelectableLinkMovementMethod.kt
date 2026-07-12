package forpdateam.ru.forpda.common

import android.text.Selection
import android.text.Spannable
import android.text.method.ArrowKeyMovementMethod
import android.text.style.ClickableSpan
import android.text.style.URLSpan
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.widget.TextView
import kotlin.math.abs

/**
 * A movement method that keeps a [TextView] BOTH text-selectable AND link-clickable.
 *
 * The stock [android.text.method.LinkMovementMethod] (and our custom [LinkMovementMethod]) extends
 * [android.text.method.ScrollingMovementMethod], which has NO text-selection support — so a post
 * block containing a link (e.g. a clickable @mention nick) could not be selected/copied at all
 * (long-press did nothing). This extends [ArrowKeyMovementMethod] instead, so it inherits the full
 * selection machinery (long-press → selection ActionMode, drag → extend selection), and layers link
 * tap / long-press handling on top.
 *
 * Gesture ownership: when a touch STARTS on a link we own the whole gesture — a clean tap routes
 * through [listener].onClick, a long-press fires [listener].onLongClick (the link-actions menu) and
 * the editor never starts a text selection from the link. When a touch starts on plain text we defer
 * to super, so normal text selection works. A drag past touch-slop cancels the pending link
 * long-press so RecyclerView scrolling isn't mistaken for a press (mirrors [LinkMovementMethod]).
 */
class SelectableLinkMovementMethod(private val listener: LinkMovementMethod.ClickListener?) :
        ArrowKeyMovementMethod() {

    // One gesture at a time, all on the main thread, so plain fields are safe.
    private var longPressRunnable: Runnable? = null
    private var longPressFired = false
    private var gestureOnLink = false
    private var restoreLongClickable = false
    private var downX = 0f
    private var downY = 0f

    override fun canSelectArbitrarily(): Boolean = true

    override fun onTouchEvent(widget: TextView, buffer: Spannable, event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                longPressFired = false
                val link = linkAt(widget, buffer, event)
                gestureOnLink = link != null
                if (link != null) {
                    downX = event.x; downY = event.y
                    // Suppress the editor's own long-press-to-select for this gesture so a link
                    // long-press shows ONLY our link-actions menu, not a stray text selection of the
                    // link word. Our menu runs off [scheduleLongPress], independent of isLongClickable.
                    if (widget.isLongClickable) {
                        widget.isLongClickable = false
                        restoreLongClickable = true
                    }
                    scheduleLongPress(widget, (link as URLSpan).url)
                    // Own the gesture: no selection should start from a tap on the link itself.
                    return true
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (gestureOnLink) {
                    // A real drag (RecyclerView scroll) must not fire the link long-press.
                    if (longPressRunnable != null) {
                        val slop = ViewConfiguration.get(widget.context).scaledTouchSlop
                        if (abs(event.x - downX) > slop || abs(event.y - downY) > slop) {
                            cancelLongPress(widget)
                        }
                    }
                    return true
                }
            }
            MotionEvent.ACTION_UP -> {
                if (gestureOnLink) {
                    cancelLongPress(widget)
                    restoreLongClickable(widget)
                    gestureOnLink = false
                    if (longPressFired) {
                        // The long-press already handled this gesture — swallow the trailing tap.
                        longPressFired = false
                        return true
                    }
                    val link = linkAt(widget, buffer, event)
                    if (link != null) {
                        val url = (link as URLSpan).url
                        if (listener?.onClick(url) != true) link.onClick(widget)
                    }
                    return true
                }
            }
            MotionEvent.ACTION_CANCEL -> {
                cancelLongPress(widget)
                restoreLongClickable(widget)
                longPressFired = false
                if (gestureOnLink) {
                    gestureOnLink = false
                    return true
                }
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

    private fun scheduleLongPress(widget: TextView, url: String) {
        cancelLongPress(widget)
        val r = Runnable {
            longPressFired = true
            // The menu can grab focus before the TextView sees ACTION_UP, so restore here too
            // (idempotent with the UP/CANCEL restore).
            restoreLongClickable(widget)
            listener?.onLongClick(url)
        }
        longPressRunnable = r
        widget.postDelayed(r, ViewConfiguration.getLongPressTimeout().toLong())
    }

    private fun cancelLongPress(widget: TextView) {
        longPressRunnable?.let { widget.removeCallbacks(it) }
        longPressRunnable = null
    }

    /** Restore the selectable TextView's long-click (disabled while a link gesture is in flight). */
    private fun restoreLongClickable(widget: TextView) {
        if (restoreLongClickable) {
            widget.isLongClickable = true
            restoreLongClickable = false
        }
    }

    override fun initialize(widget: TextView, text: Spannable) {
        Selection.removeSelection(text)
    }
}
