package forpdateam.ru.forpda.ui.fragments.theme.modules

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.appcompat.widget.AppCompatEditText
import androidx.appcompat.widget.AppCompatImageButton
import androidx.appcompat.widget.TooltipCompat
import com.google.android.material.appbar.AppBarLayout
import forpdateam.ru.forpda.R
import forpdateam.ru.forpda.common.getColorFromAttr
import forpdateam.ru.forpda.common.getVecDrawable
import forpdateam.ru.forpda.common.simple.SimpleTextWatcher

/**
 * "Поиск на странице" toolbar bar — extracted from [forpdateam.ru.forpda.ui.fragments.theme.ThemeFragment]
 * as part of AUDIT-L07. This class is the **view** of the search-on-page feature
 * (the EditText + prev/next/close buttons). The *state machine* is already
 * in [forpdateam.ru.forpda.presentation.theme.FindOnPageState]; the *bridge*
 * to the actual content (WebView / native) stays in `ThemeFragment` /
 * `ThemeFragmentWeb` via the [Listener] callbacks.
 *
 * The bar is built once and attached to the [AppBarLayout]; it is hidden
 * by default and shown via [open]. Tapping close (or the host's back) calls
 * [Listener.onSearchOnPageClearRequested], which is responsible for hiding
 * the bar via [close].
 */
class SearchOnPageBarView(
        private val context: Context,
        private val appBarLayout: AppBarLayout,
        private val listener: Listener
) {

    interface Listener {
        fun onSearchOnPageTextChanged(query: String)
        fun onSearchOnPageNext(next: Boolean)
        fun onSearchOnPageClearRequested()
        /** Request that the host show the soft keyboard for [view]. */
        fun onSearchOnPageShowKeyboard(view: View)
    }

    private var bar: LinearLayout? = null
    private var field: AppCompatEditText? = null
    private var _isOpen: Boolean = false

    val isOpen: Boolean
        get() = _isOpen

    fun ensureBuilt() {
        if (bar != null) return

        val itemSize = context.resources.getDimensionPixelSize(R.dimen.dp40)
        val horizontalPadding = context.resources.getDimensionPixelSize(R.dimen.content_padding_horizontal)
        val verticalPadding = context.resources.getDimensionPixelSize(R.dimen.dp4)
        val outValue = TypedValue()
        context.theme.resolveAttribute(android.R.attr.actionBarItemBackground, outValue, true)

        val barView = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            visibility = View.GONE
            background = android.graphics.drawable.ColorDrawable(
                    context.getColorFromAttr(R.attr.chrome_plane_background)
            )
            setPadding(horizontalPadding, verticalPadding, horizontalPadding, verticalPadding)
            layoutParams = AppBarLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        val fieldView = AppCompatEditText(context).apply {
            hint = context.getString(R.string.search_in_page)
            isSingleLine = true
            imeOptions = EditorInfo.IME_ACTION_SEARCH or EditorInfo.IME_FLAG_NO_FULLSCREEN
            inputType = android.text.InputType.TYPE_CLASS_TEXT
            setTextColor(context.getColorFromAttr(com.google.android.material.R.attr.colorOnSurface))
            setHintTextColor(context.getColorFromAttr(com.google.android.material.R.attr.colorOnSurfaceVariant))
            background = android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT)
            minHeight = itemSize
            setPadding(0, 0, context.resources.getDimensionPixelSize(R.dimen.dp8), 0)
            layoutParams = LinearLayout.LayoutParams(0, itemSize, 1f)
            addTextChangedListener(object : SimpleTextWatcher() {
                override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
                    val q = s.toString().trim()
                    if (q.isEmpty()) {
                        listener.onSearchOnPageClearRequested()
                    } else {
                        listener.onSearchOnPageTextChanged(q)
                    }
                }
            })
        }

        fun iconButton(iconRes: Int, description: CharSequence?, onClick: () -> Unit): AppCompatImageButton {
            return AppCompatImageButton(context).apply {
                setImageDrawable(context.getVecDrawable(iconRes))
                setBackgroundResource(outValue.resourceId)
                contentDescription = description
                TooltipCompat.setTooltipText(this, description)
                scaleType = ImageView.ScaleType.CENTER
                layoutParams = LinearLayout.LayoutParams(itemSize, itemSize)
                setOnClickListener { onClick() }
            }
        }

        barView.addView(fieldView)
        barView.addView(iconButton(R.drawable.ic_toolbar_search_prev, context.getString(R.string.search_in_page)) {
            listener.onSearchOnPageNext(false)
        })
        barView.addView(iconButton(R.drawable.ic_toolbar_search_next, context.getString(R.string.search_in_page)) {
            listener.onSearchOnPageNext(true)
        })
        barView.addView(iconButton(R.drawable.ic_close, context.getString(R.string.close)) {
            listener.onSearchOnPageClearRequested()
        })

        appBarLayout.addView(barView)
        bar = barView
        field = fieldView
    }

    fun open() {
        ensureBuilt()
        bar?.visibility = View.VISIBLE
        _isOpen = true
        // Request focus + show keyboard on the next main-loop turn so the
        // host has a chance to finish the layout pass. We post via a
        // Handler bound to the main looper (rather than `view.post`) so
        // the work also runs when the field is not yet attached to a
        // window — e.g. inside a Robolectric test.
        val target = field
        if (target != null) {
            Handler(Looper.getMainLooper()).post {
                target.requestFocus()
                listener.onSearchOnPageShowKeyboard(target)
            }
        }
    }

    fun close() {
        if (!_isOpen) return
        // Mark closed FIRST. A typical host hides the bar in response to
        // onSearchOnPageClearRequested(), so close() can re-enter — directly,
        // or via the text-watcher that setText("") fires below. Flipping the
        // guard before any callback makes that re-entry a no-op instead of
        // infinite recursion (the hazard that kept this bar unwired). See
        // SearchOnPageBarViewTest.close_isReentrancySafe*.
        _isOpen = false
        field?.setText("")
        field?.clearFocus()
        listener.onSearchOnPageClearRequested()
        bar?.visibility = View.GONE
    }
}
