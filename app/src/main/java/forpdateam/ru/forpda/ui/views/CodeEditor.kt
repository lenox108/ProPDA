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

        setSyntaxColors()
        setUpdateDelay(500)
        customInsertionActionModeCallback = insertionActionModeCallback
        updateHighlighting()
    }

    private fun smartUpdateHighlighting() {
        cancelUpdate()
        if (!modified) return
        updateHandler.postDelayed(updateRunnable, updateDelay.toLong())
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
            clearSpans(e)

            if (e.isEmpty()) return e

            val visibleStart: Int
            val visibleEnd: Int
            if (scrollView == null || e.length <= FULL_BB_HIGHLIGHT_MAX_CHARS) {
                visibleStart = 0
                visibleEnd = e.length
            } else {
                val scrollY = scrollView!!.scrollY
                val scrollViewHeight = scrollView!!.height
                visibleStart = getOffsetForPosition(0f, scrollY.toFloat())
                visibleEnd = getOffsetForPosition(0f, (scrollY + scrollViewHeight).toFloat())
            }

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

    companion object {
        private const val FULL_BB_HIGHLIGHT_MAX_CHARS = 16384

        private fun clearSpans(e: Editable) {
            e.getSpans(0, e.length, ForegroundColorSpan::class.java).forEach { span ->
                e.removeSpan(span)
            }
            e.getSpans(0, e.length, BackgroundColorSpan::class.java).forEach { span ->
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
