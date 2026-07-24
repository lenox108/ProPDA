package forpdateam.ru.forpda.ui.fragments.theme.nativerender

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.text.Selection
import android.text.Spannable
import android.text.Spanned
import android.text.style.ClickableSpan
import android.util.AttributeSet
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.widget.PopupMenu
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
internal open class TopicSelectableTextView @JvmOverloads constructor(
        context: Context,
        attrs: AttributeSet? = null,
) : TextView(context, attrs) {

    private val longPressSlop = ViewConfiguration.get(context).scaledTouchSlop * 2
    private var reliableSelectionEnabled = false
    private var touchActive = false
    private var movedPastSlop = false
    private var gestureStartedOnLink = false
    private var manualActionMode: ActionMode? = null
    private var downX = 0f
    private var downY = 0f

    private val manualSelectionFallback = Runnable {
        if (!reliableSelectionEnabled || movedPastSlop || gestureStartedOnLink ||
                hasNonEmptySelection()) {
            return@Runnable
        }
        startManualSelection()
    }

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
        invokePlatformLongClick()
        // OEM TextView implementations can accept performLongClick() yet never start their Editor
        // ActionMode. Check once more after smart-selection had time to respond; if it is still
        // empty, use the app-owned selection menu instead.
        removeCallbacks(manualSelectionFallback)
        postDelayed(manualSelectionFallback, MANUAL_SELECTION_FALLBACK_DELAY_MS)
    }

    protected open fun invokePlatformLongClick(): Boolean = performLongClick()

    fun enableReliableSelection() {
        reliableSelectionEnabled = true
        isLongClickable = true
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (reliableSelectionEnabled) {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    if (manualActionMode == null && hasNonEmptySelection()) {
                        (text as? Spannable)?.let(Selection::removeSelection)
                    }
                    touchActive = true
                    movedPastSlop = false
                    downX = event.x
                    downY = event.y
                    gestureStartedOnLink = touchesClickableSpan(event)
                    isLongClickable = true
                    parent?.requestDisallowInterceptTouchEvent(true)
                    removeCallbacks(selectionFallback)
                    removeCallbacks(manualSelectionFallback)
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
                        removeCallbacks(manualSelectionFallback)
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
        removeCallbacks(manualSelectionFallback)
        manualActionMode?.finish()
        manualActionMode = null
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

    /**
     * Last-resort selection that does not depend on TextView.Editor. It highlights the word under
     * the original touch point and starts an ordinary floating ActionMode with app-owned actions.
     */
    private fun startManualSelection() {
        val buffer = text as? Spannable ?: return
        val offset = textOffsetForPosition(downX, downY) ?: return
        val range = wordRangeAt(buffer, offset) ?: return
        requestFocus()
        Selection.setSelection(buffer, range.first, range.last + 1)
        parent?.requestDisallowInterceptTouchEvent(true)

        val quoteCallback = customSelectionActionModeCallback
        val callback = object : ActionMode.Callback {
            override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
                menu.add(0, android.R.id.copy, 0, context.getString(android.R.string.copy))
                        .setShowAsActionFlags(MenuItem.SHOW_AS_ACTION_ALWAYS)
                menu.add(
                        0,
                        android.R.id.selectAll,
                        1,
                        context.getString(android.R.string.selectAll),
                ).setShowAsActionFlags(MenuItem.SHOW_AS_ACTION_IF_ROOM)
                quoteCallback?.onCreateActionMode(mode, menu)
                return true
            }

            override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean =
                    quoteCallback?.onPrepareActionMode(mode, menu) ?: false

            override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
                return when (item.itemId) {
                    android.R.id.copy -> {
                        onTextContextMenuItem(android.R.id.copy)
                        mode.finish()
                        true
                    }

                    android.R.id.selectAll -> {
                        Selection.setSelection(buffer, 0, buffer.length)
                        mode.invalidate()
                        true
                    }

                    else -> quoteCallback?.onActionItemClicked(mode, item) ?: false
                }
            }

            override fun onDestroyActionMode(mode: ActionMode) {
                quoteCallback?.onDestroyActionMode(mode)
                if (manualActionMode === mode) manualActionMode = null
                parent?.requestDisallowInterceptTouchEvent(false)
            }
        }

        manualActionMode = startActionMode(callback, ActionMode.TYPE_FLOATING)
        if (manualActionMode == null) {
            showManualPopup()
        }
    }

    private fun showManualPopup() {
        val popup = PopupMenu(context, this)
        popup.menu.add(0, android.R.id.copy, 0, context.getString(android.R.string.copy))
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                android.R.id.copy -> onTextContextMenuItem(android.R.id.copy)
                else -> false
            }
        }
        popup.setOnDismissListener {
            parent?.requestDisallowInterceptTouchEvent(false)
        }
        // A detached/broken-window OEM can reject both ActionMode and PopupMenu; keep the selected
        // range in that exceptional case instead of silently clearing the user's long press.
        try {
            popup.show()
        } catch (_: RuntimeException) { /* Keep selection visible; there is no safe menu window. */ }
    }

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

    private fun textOffsetForPosition(xPosition: Float, yPosition: Float): Int? {
        val textLayout = layout ?: return null
        if (textLayout.lineCount == 0 || textLayout.height == 0) return null
        val rawX = xPosition - totalPaddingLeft + scrollX
        val rawY = yPosition - totalPaddingTop + scrollY
        val y = rawY.coerceIn(0f, (textLayout.height - 1).toFloat())
        val line = textLayout.getLineForVertical(y.toInt())
        val lineLeft = textLayout.getLineLeft(line)
        val lineRight = textLayout.getLineRight(line)
        val x = rawX.coerceIn(min(lineLeft, lineRight), max(lineLeft, lineRight))
        return textLayout.getOffsetForHorizontal(line, x)
    }

    private fun wordRangeAt(value: CharSequence, rawOffset: Int): IntRange? {
        if (value.isEmpty()) return null
        var offset = rawOffset.coerceIn(0, value.length - 1)
        if (!value[offset].isWordCharacter()) {
            val next = (offset until value.length).firstOrNull { value[it].isWordCharacter() }
            val previous = (offset downTo 0).firstOrNull { value[it].isWordCharacter() }
            offset = next ?: previous ?: return null
        }
        var start = offset
        var end = offset + 1
        while (start > 0 && value[start - 1].isWordCharacter()) start--
        while (end < value.length && value[end].isWordCharacter()) end++
        return start until end
    }

    private fun Char.isWordCharacter(): Boolean = isLetterOrDigit() || this == '_' || this == '-'

    private companion object {
        const val LONG_PRESS_FALLBACK_EARLY_MS = 80
        const val MANUAL_SELECTION_FALLBACK_DELAY_MS = 180L
    }
}
