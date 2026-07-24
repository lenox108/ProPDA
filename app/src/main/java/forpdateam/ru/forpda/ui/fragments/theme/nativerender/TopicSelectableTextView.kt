package forpdateam.ru.forpda.ui.fragments.theme.nativerender

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.text.Spanned
import android.text.style.ClickableSpan
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.widget.TextView
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Selectable topic text with an app-owned fallback for OEM TextView long-press handling.
 *
 * Some Android builds cancel TextView's pending long press when a RecyclerView/refresh parent sees
 * tiny finger drift. Keep ancestors out of the gesture until it either becomes a real scroll or the
 * selection starts. Invoke TextView's editor long-click shortly before the platform timeout: on
 * affected ROMs waiting until after that timeout is too late because the user has already felt the
 * long-press interval elapse and lifted the finger, cancelling the delayed fallback.
 */
internal class TopicSelectableTextView @JvmOverloads constructor(
        context: Context,
        attrs: AttributeSet? = null,
) : TextView(context, attrs) {

    private val longPressSlop = ViewConfiguration.get(context).scaledTouchSlop * 2
    private var reliableSelectionEnabled = false
    private var touchActive = false
    private var movedPastSlop = false
    private var gestureStartedOnLink = false
    private var downX = 0f
    private var downY = 0f

    private val selectionFallback = Runnable {
        if (!reliableSelectionEnabled || !touchActive || movedPastSlop ||
                gestureStartedOnLink || hasNonEmptySelection()) {
            return@Runnable
        }
        parent?.requestDisallowInterceptTouchEvent(true)
        // TextView.performLongClick() enters Editor.performLongClick(), using the touch offset that
        // super.onTouchEvent received on DOWN. Calling it directly also works on ROMs that cleared
        // View.isLongClickable after installing a movement method.
        isLongClickable = true
        // We are replacing View's pending long-click for this gesture. Cancel it first so stock
        // Android does not invoke performLongClick() a second time at the normal timeout.
        cancelLongPress()
        performLongClick()
    }

    fun enableReliableSelection() {
        reliableSelectionEnabled = true
        isLongClickable = true
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (reliableSelectionEnabled) {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    touchActive = true
                    movedPastSlop = false
                    downX = event.x
                    downY = event.y
                    gestureStartedOnLink = touchesClickableSpan(event)
                    isLongClickable = true
                    parent?.requestDisallowInterceptTouchEvent(true)
                    removeCallbacks(selectionFallback)
                    postDelayed(
                            selectionFallback,
                            (ViewConfiguration.getLongPressTimeout() -
                                    LONG_PRESS_FALLBACK_EARLY_MS).coerceAtLeast(1).toLong(),
                    )
                }

                MotionEvent.ACTION_MOVE -> {
                    if (!movedPastSlop &&
                            (abs(event.x - downX) > longPressSlop ||
                                    abs(event.y - downY) > longPressSlop)) {
                        movedPastSlop = true
                        removeCallbacks(selectionFallback)
                        // This is an intentional scroll, not a long press. Hand ownership back to
                        // RecyclerView/SwipeRefreshLayout without consuming the TextView event.
                        parent?.requestDisallowInterceptTouchEvent(false)
                    }
                }

                MotionEvent.ACTION_UP ->
                    finishTouch(cancelSelectionOwnership = !hasNonEmptySelection())
                MotionEvent.ACTION_CANCEL -> finishTouch(cancelSelectionOwnership = true)
            }
        }
        return super.onTouchEvent(event)
    }

    /**
     * Copy through ClipboardManager before delegating to the platform editor. The normal TextView
     * implementation still closes its ActionMode and performs accessibility feedback, while this
     * direct write covers OEM callbacks that display «Копировать» but fail to update the clipboard.
     */
    override fun onTextContextMenuItem(id: Int): Boolean {
        val selectedText = if (id == android.R.id.copy) {
            val start = selectionStart
            val end = selectionEnd
            if (start >= 0 && end >= 0 && start != end) {
                text.subSequence(min(start, end), max(start, end)).toString()
            } else null
        } else null
        val platformHandled = try {
            super.onTextContextMenuItem(id)
        } catch (_: RuntimeException) {
            false
        }
        var copied = false
        if (selectedText != null) {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val platformCopied = try {
                clipboard.primaryClip
                        ?.takeIf { it.itemCount > 0 }
                        ?.getItemAt(0)
                        ?.coerceToText(context)
                        ?.toString() == selectedText
            } catch (_: RuntimeException) {
                false
            }
            if (!platformCopied) {
                clipboard.setPrimaryClip(ClipData.newPlainText(null, selectedText))
            }
            copied = true
        }
        return copied || platformHandled
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(selectionFallback)
        touchActive = false
        parent?.requestDisallowInterceptTouchEvent(false)
        super.onDetachedFromWindow()
    }

    private fun finishTouch(cancelSelectionOwnership: Boolean) {
        touchActive = false
        removeCallbacks(selectionFallback)
        if (cancelSelectionOwnership) {
            parent?.requestDisallowInterceptTouchEvent(false)
        }
    }

    private fun hasNonEmptySelection(): Boolean =
            selectionStart >= 0 && selectionEnd >= 0 && selectionStart != selectionEnd

    private fun touchesClickableSpan(event: MotionEvent): Boolean {
        val spanned = text as? Spanned ?: return false
        val textLayout = layout ?: return false
        val x = event.x - totalPaddingLeft + scrollX
        val y = event.y - totalPaddingTop + scrollY
        if (y < 0 || y > textLayout.height) return false
        val line = textLayout.getLineForVertical(y.toInt())
        if (x < textLayout.getLineLeft(line) || x > textLayout.getLineRight(line)) return false
        val offset = textLayout.getOffsetForHorizontal(line, x)
        return spanned.getSpans(offset, offset, ClickableSpan::class.java).isNotEmpty()
    }

    private companion object {
        const val LONG_PRESS_FALLBACK_EARLY_MS = 80
    }
}
