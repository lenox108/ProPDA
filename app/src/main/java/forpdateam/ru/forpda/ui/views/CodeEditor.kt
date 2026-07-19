package forpdateam.ru.forpda.ui.views
import forpdateam.ru.forpda.BuildConfig
import timber.log.Timber

import android.content.Context
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.Spannable
import android.text.TextWatcher
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.util.AttributeSet
import android.view.ActionMode
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.widget.ScrollView
import androidx.core.view.ViewCompat
import androidx.appcompat.widget.AppCompatEditText
import forpdateam.ru.forpda.common.Html
import java.util.regex.Matcher
import java.util.regex.Pattern

/*
* ORIGINAL: https://github.com/markusfisch/CodeEditor/blob/master/app/src/main/java/de/markusfisch/android/CodeEditor/widget/CodeEditor.java
* */
class CodeEditor @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : AppCompatEditText(context, attrs) {
    private object ForumCodes {
        val ELEMENT: Pattern = Pattern.compile("(\\[(?:/)?((?:attachment|background|nomergetime|mergetime|snapback|numlist|spoiler|offtop|center|color|right|quote|code|font|hide|left|list|size|img|sub|sup|cur|url|b|i|u|s|\\*)))=?\\s?([^\\]\\[]+?)?(\\])", Pattern.CASE_INSENSITIVE)
        val ATTRIBUTE: Pattern = Pattern.compile("(name|date|post)?=?([\\s\\S]+?)\\s?(?=(?:name|date|post)=|\\z)", Pattern.CASE_INSENSITIVE)
    }

    private val updateHandler = Handler(Looper.getMainLooper())
    private val updateRunnable = Runnable {
        val e = text ?: return@Runnable
        highlightWithoutChange(e)
    }

    private var updateDelay: Int = 500
    private var modified: Boolean = true
    private var colorTag: Int = 0
    private var colorAttrName: Int = 0
    private var colorAttrValue: Int = 0
    private var scrollView: ScrollView? = null

    private var scrollerTask: Runnable? = null
    private var initialPosition: Int = 0
    private val newCheck: Int = 100
    private var insertionActionModeActive: Boolean = false

    // --- Undo/redo ---
    private data class EditOp(val start: Int, val before: CharSequence, val after: CharSequence)
    private val undoStack = ArrayDeque<EditOp>()
    private val redoStack = ArrayDeque<EditOp>()
    /** Пока true — правки от undo()/redo() не пишутся обратно в стек. */
    private var undoRedoInProgress = false
    private var pendingUndoStart = 0
    private var pendingUndoBefore: CharSequence = ""
    /** Последняя правка была посимвольным набором — для склейки подряд идущих вставок в одну операцию. */
    private var lastEditWasTyping = false
    /** Колбэк для UI (кнопки undo/redo): вызывается при любом изменении доступности истории. */
    var onUndoStateChanged: (() -> Unit)? = null

    fun canUndo(): Boolean = undoStack.isNotEmpty()
    fun canRedo(): Boolean = redoStack.isNotEmpty()

    /** Сбросить историю (после программной загрузки черновика — чтобы undo не стирал весь текст). */
    fun clearUndoHistory() {
        undoStack.clear()
        redoStack.clear()
        lastEditWasTyping = false
        onUndoStateChanged?.invoke()
    }

    private val insertionActionModeCallback = object : ActionMode.Callback {
        override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
            insertionActionModeActive = true
            return true
        }

        override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean = false

        override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean = false

        override fun onDestroyActionMode(mode: ActionMode) {
            insertionActionModeActive = false
            post { restartCursorBlink() }
        }
    }

    fun attachToScrollView(sv: ScrollView) {
        scrollView = sv

        sv.addOnLayoutChangeListener { v, _, _, _, _, _, _, _, oldBottom ->
            if (v.height != oldBottom - v.top) {
                smartUpdateHighlighting()
            }
        }

        sv.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_UP) {
                initialPosition = sv.scrollY
                scrollerTask?.let { sv.postDelayed(it, newCheck.toLong()) }
            }
            false
        }

        scrollerTask = Runnable {
            val newPosition = sv.scrollY
            if (initialPosition - newPosition == 0) {
                smartUpdateHighlighting()
            } else {
                initialPosition = sv.scrollY
                scrollerTask?.let { sv.postDelayed(it, newCheck.toLong()) }
            }
        }
    }

    fun setUpdateDelay(ms: Int) {
        updateDelay = ms
    }

    fun updateHighlighting() {
        highlightWithoutChange(text ?: return)
    }

    private fun recordEdit(start: Int, before: CharSequence, after: CharSequence) {
        if (before.isEmpty() && after.isEmpty()) return
        redoStack.clear()
        val last = undoStack.lastOrNull()
        // Склейка: подряд идущий набор одиночных символов (не перевод строки) — одна операция undo,
        // иначе undo откатывал бы по одной букве.
        val isTyping = before.isEmpty() && after.length == 1 && after[0] != '\n'
        if (isTyping && lastEditWasTyping && last != null &&
            last.before.isEmpty() && last.start + last.after.length == start
        ) {
            undoStack[undoStack.lastIndex] = last.copy(after = last.after.toString() + after)
        } else {
            if (undoStack.size >= MAX_UNDO_OPS) undoStack.removeFirst()
            undoStack.addLast(EditOp(start, before, after))
        }
        lastEditWasTyping = isTyping
        onUndoStateChanged?.invoke()
    }

    fun undo() {
        val op = undoStack.removeLastOrNull() ?: return
        applyUndoRedo(op.start, op.after.length, op.before)
        redoStack.addLast(op)
        lastEditWasTyping = false
        onUndoStateChanged?.invoke()
        updateHighlighting()
    }

    fun redo() {
        val op = redoStack.removeLastOrNull() ?: return
        applyUndoRedo(op.start, op.before.length, op.after)
        undoStack.addLast(op)
        lastEditWasTyping = false
        onUndoStateChanged?.invoke()
        updateHighlighting()
    }

    /** Заменяет [replaceLen] символов начиная с [start] на [replacement], не записывая правку в историю. */
    private fun applyUndoRedo(start: Int, replaceLen: Int, replacement: CharSequence) {
        val e = text ?: return
        val s = start.coerceIn(0, e.length)
        val end = (start + replaceLen).coerceIn(s, e.length)
        undoRedoInProgress = true
        try {
            e.replace(s, end, replacement)
            setSelection((s + replacement.length).coerceIn(0, text?.length ?: 0))
        } catch (_: Throwable) {
        } finally {
            undoRedoInProgress = false
        }
    }

    fun restartCursorBlink() {
        if (!isAttachedToWindow) return
        if (!isFocused) return
        if (!hasWindowFocus()) return
        try {
            // Toggle isCursorVisible to force internal blink runnable recreation on some OEM builds.
            // (Editor.makeBlink() is only triggered on visibility change / focus / selection events;
            // toggling isCursorVisible reliably re-arms it.)
            isCursorVisible = false
            isCursorVisible = true
        } catch (_: Throwable) {
        }
        ViewCompat.postInvalidateOnAnimation(this)
        refreshDrawableState()
    }

    private fun init() {
        addTextChangedListener(object : TextWatcher {
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
            override fun afterTextChanged(e: Editable) {
                smartUpdateHighlighting()
            }
        })

        // Отдельный watcher для истории undo/redo: захватывает заменённый фрагмент (before) и
        // вставленный (after). Правки от самих undo()/redo() пропускаются (undoRedoInProgress).
        addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {
                if (undoRedoInProgress) return
                pendingUndoStart = start
                pendingUndoBefore = if (count > 0) s.subSequence(start, start + count).toString() else ""
            }

            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
                if (undoRedoInProgress) return
                val after = if (count > 0) s.subSequence(start, start + count).toString() else ""
                recordEdit(pendingUndoStart, pendingUndoBefore, after)
            }

            override fun afterTextChanged(e: Editable) {}
        })

        setSyntaxColors()
        setUpdateDelay(500)
        customInsertionActionModeCallback = insertionActionModeCallback
        updateHighlighting()
    }

    private fun smartUpdateHighlighting() {
        cancelUpdate()
        if (!modified) return
        // Адаптивный дебаунс: на коротком тексте подсветка почти мгновенная, на длинном — реже,
        // чтобы регекс-проход не дёргался на каждый символ. updateDelay остаётся потолком.
        val len = text?.length ?: 0
        val delay = when {
            len < 2000 -> 120L
            len < 8000 -> 300L
            else -> updateDelay.toLong()
        }
        updateHandler.postDelayed(updateRunnable, delay)
    }

    private fun setSyntaxColors() {
        colorTag = Color.parseColor("#446FBD")
        colorAttrName = Color.parseColor("#6D8600")
        colorAttrValue = Color.parseColor("#e88501")
    }

    private fun cancelUpdate() {
        updateHandler.removeCallbacks(updateRunnable)
        scrollerTask?.let { task -> scrollView?.removeCallbacks(task) }
    }

    private fun highlightWithoutChange(e: Editable) {
        modified = false
        highlight(e)
        modified = true
    }

    private fun highlight(e: Editable): Editable {
        val time = System.currentTimeMillis()
        try {
            if (e.isEmpty()) {
                clearSpans(e, 0, 0)
                return e
            }

            var visibleStart: Int
            var visibleEnd: Int
            if (scrollView == null || e.length <= FULL_BB_HIGHLIGHT_MAX_CHARS) {
                visibleStart = 0
                visibleEnd = e.length
            } else {
                val scrollY = scrollView!!.scrollY
                val scrollViewHeight = scrollView!!.height
                visibleStart = getOffsetForPosition(0f, scrollY.toFloat())
                visibleEnd = getOffsetForPosition(0f, (scrollY + scrollViewHeight).toFloat())
                // Расширяем окно до границ строк: иначе BBCode-тег на кромке окна красится наполовину.
                layout?.let { l ->
                    visibleStart = l.getLineStart(l.getLineForOffset(visibleStart.coerceIn(0, e.length)))
                    visibleEnd = l.getLineEnd(l.getLineForOffset(visibleEnd.coerceIn(0, e.length)))
                }
                visibleStart = visibleStart.coerceIn(0, e.length)
                visibleEnd = visibleEnd.coerceIn(visibleStart, e.length)
            }

            // Снимаем спаны ТОЛЬКО в перекрашиваемом окне: при частичной подсветке (длинный текст)
            // полный clearSpans гасил offscreen-подсветку, и она исчезала после первой правки,
            // возвращаясь лишь после остановки скролла. Для короткого текста окно = весь текст.
            clearSpans(e, visibleStart, visibleEnd)

            val hlText = e.subSequence(visibleStart, visibleEnd)

            var attributes: Matcher? = null
            val m = ForumCodes.ELEMENT.matcher(hlText)
            while (m.find()) {
                val attrsSrc = m.group(3)
                if (attrsSrc != null) {
                    val tagName = m.group(2)
                    val eg3s = m.start(3)
                    val eg3e = m.end(3)
                    var color = colorAttrValue

                    if (tagName.equals("quote", ignoreCase = true)) {
                        attributes = if (attributes == null) {
                            ForumCodes.ATTRIBUTE.matcher(attrsSrc)
                        } else {
                            attributes.reset(attrsSrc)
                        }

                        while (attributes.find()) {
                            val attrName = attributes.group(1)
                            if (attrName != null) {
                                val ag1s = attributes.start(1)
                                val ag1e = attributes.end(1)
                                e.setSpan(
                                    ForegroundColorSpan(colorAttrName),
                                    visibleStart + eg3s + ag1s,
                                    visibleStart + eg3s + ag1e,
                                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                                )
                            }

                            val attrValue = attributes.group(2)
                            if (attrValue != null) {
                                val ag2s = attributes.start(2)
                                val ag2e = attributes.end(2)
                                e.setSpan(
                                    ForegroundColorSpan(color),
                                    visibleStart + eg3s + ag2s,
                                    visibleStart + eg3s + ag2e,
                                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                                )
                            }
                        }
                    } else {
                        if (tagName.equals("color", ignoreCase = true) || tagName.equals("background", ignoreCase = true)) {
                            try {
                                if (attrsSrc[0] != '#') {
                                    val htmlColor = Html.getColorMap()[attrsSrc]
                                    if (htmlColor != null) {
                                        color = htmlColor
                                    }
                                } else {
                                    color = Color.parseColor(attrsSrc)
                                }
                            } catch (_: Exception) {
                            }
                        }
                        e.setSpan(
                            ForegroundColorSpan(color),
                            visibleStart + eg3s,
                            visibleStart + eg3e,
                            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                        )
                    }
                }

                val eg1s = m.start(1)
                val eg1e = m.end(1)
                e.setSpan(
                    ForegroundColorSpan(colorTag),
                    visibleStart + eg1s,
                    visibleStart + eg1e,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )

                val eg4s = m.start(4)
                val eg4e = m.end(4)
                e.setSpan(
                    ForegroundColorSpan(colorTag),
                    visibleStart + eg4s,
                    visibleStart + eg4e,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
        } catch (_: IllegalStateException) {
        }

        if (BuildConfig.DEBUG) Timber.d("CodeEditor init time: ${System.currentTimeMillis() - time}ms")
        return e
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        // Аппаратная/BT-клавиатура: Ctrl+Z — undo, Ctrl+Y или Ctrl+Shift+Z — redo.
        if (event.isCtrlPressed) {
            when (keyCode) {
                KeyEvent.KEYCODE_Z -> {
                    if (event.isShiftPressed) redo() else undo()
                    return true
                }
                KeyEvent.KEYCODE_Y -> {
                    redo()
                    return true
                }
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    companion object {
        private const val FULL_BB_HIGHLIGHT_MAX_CHARS = 16384
        private const val MAX_UNDO_OPS = 200

        private fun clearSpans(e: Editable, start: Int, end: Int) {
            val s = start.coerceIn(0, e.length)
            val en = end.coerceIn(s, e.length)
            e.getSpans(s, en, ForegroundColorSpan::class.java).forEach { span ->
                e.removeSpan(span)
            }
            e.getSpans(s, en, BackgroundColorSpan::class.java).forEach { span ->
                e.removeSpan(span)
            }
        }
    }

    init {
        init()
    }

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        if (hasWindowFocus) {
            post { restartCursorBlink() }
        }
    }

    override fun onFocusChanged(focused: Boolean, direction: Int, previouslyFocusedRect: android.graphics.Rect?) {
        super.onFocusChanged(focused, direction, previouslyFocusedRect)
        if (focused) {
            post { restartCursorBlink() }
        }
    }

    /**
     * Caret movement (taps inside text, selection drag, programmatic setSelection) often leaves
     * Editor's blink runnable in a stale state on certain OEM/Android builds — caret disappears or
     * stops blinking. Re-arm the blink in the next frame whenever selection changes while focused.
     */
    override fun onSelectionChanged(selStart: Int, selEnd: Int) {
        super.onSelectionChanged(selStart, selEnd)
        if (!isAttachedToWindow) return
        if (!isFocused || !hasWindowFocus()) return
        if (insertionActionModeActive) return
        // Avoid recursion via setSelection() inside restartCursorBlink: schedule on next frame.
        ViewCompat.postOnAnimation(this) {
            if (isAttachedToWindow && isFocused && hasWindowFocus() && !insertionActionModeActive) {
                try {
                    isCursorVisible = false
                    isCursorVisible = true
                } catch (_: Throwable) {
                }
                ViewCompat.postInvalidateOnAnimation(this)
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val handled = super.onTouchEvent(event)
        if (event.actionMasked == MotionEvent.ACTION_UP && isFocused && hasWindowFocus() && !insertionActionModeActive) {
            post { restartCursorBlink() }
        }
        return handled
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        cancelUpdate()
    }
}
