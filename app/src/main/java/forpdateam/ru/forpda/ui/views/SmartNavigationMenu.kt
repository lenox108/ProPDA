package forpdateam.ru.forpda.ui.views

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.graphics.Rect
import android.util.TypedValue
import android.view.*
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.core.view.ViewCompat
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import forpdateam.ru.forpda.R
import forpdateam.ru.forpda.common.getColorFromAttr
import forpdateam.ru.forpda.ui.dp12
import forpdateam.ru.forpda.ui.dp16
import forpdateam.ru.forpda.ui.dp48
import kotlinx.coroutines.*

/**
 * Smart navigation menu that appears on long-press of the quick-scroll FAB.
 * Floating panel near the button with scrollable page list and quick actions.
 */
class SmartNavigationMenu(
    private val context: Context,
    private val anchorView: View,
    private val parent: ViewGroup,
) {

    interface Listener {
        fun onGoToPage(page: Int)
        fun onGoToStart()
        fun onGoToEnd()
        fun onGoToUnread()
        fun onDismiss()
    }

    private var overlay: FrameLayout? = null
    private var menuView: View? = null
    private var listener: Listener? = null
    private var keyboardLayoutListener: ViewTreeObserver.OnGlobalLayoutListener? = null

    private var cachedMenuView: View? = null
    private var pageAdapter: SmartNavPageAdapter? = null
    private var unreadActionView: View? = null

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var dismissJob: Job? = null

    private var currentPage: Int = 1
    private var totalPages: Int = 1
    private var hasUnread: Boolean = false
    private var isDismissing = false
    private var disposed = false

    fun setListener(listener: Listener) {
        this.listener = listener
    }

    fun show(currentPage: Int, totalPages: Int, hasUnread: Boolean = false) {
        if (disposed) return
        this.currentPage = currentPage.coerceIn(1, totalPages.coerceAtLeast(1))
        this.totalPages = totalPages.coerceAtLeast(1)
        this.hasUnread = hasUnread
        this.isDismissing = false
        this.disposed = false

        if (overlay != null || !parent.isAttachedToWindow) return

        val overlay = createOverlay()
        this.overlay = overlay
        parent.addView(overlay, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)

        val menuView = cachedMenuView ?: buildMenuView().also { cachedMenuView = it }
        (menuView.parent as? ViewGroup)?.removeView(menuView)
        this.menuView = menuView
        bindMenuData(menuView, this.currentPage, this.totalPages, this.hasUnread)
        overlay.addView(menuView)

        SmartNavMenuPositioner.position(
            context, anchorView, parent, menuView,
            PAGE_LIST_VISIBLE_ROWS, MIN_PAGE_LIST_VISIBLE_ROWS
        )
        animateMenuShow(menuView)
    }

    fun dismiss() {
        if (disposed) return
        dismissJob?.cancel()
        if (isDismissing || overlay == null) return
        val mv = menuView ?: return
        isDismissing = true
        if (!mv.isAttachedToWindow) {
            removeOverlay(animated = false)
            return
        }
        mv.animate().cancel()
        mv.animate()
            .alpha(0f)
            .scaleX(0.9f)
            .scaleY(0.9f)
            .setDuration(160)
            .setListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    removeOverlay(animated = false)
                }
            })
            .start()
    }

    fun isShowing(): Boolean = overlay != null && !isDismissing

    fun dispose() {
        disposed = true
        dismissJob?.cancel()
        menuView?.animate()?.cancel()
        overlay?.animate()?.cancel()
        removeOverlay(animated = false)
        scope.cancel()
        cachedMenuView = null
        pageAdapter = null
        unreadActionView = null
    }

    private fun removeOverlay(animated: Boolean = true) {
        keyboardLayoutListener?.let { listener ->
            if (parent.viewTreeObserver.isAlive) {
                parent.viewTreeObserver.removeOnGlobalLayoutListener(listener)
            }
        }
        keyboardLayoutListener = null
        val ov = overlay ?: return
        if (animated && ov.isAttachedToWindow) {
            ov.animate().cancel()
            ov.animate()
                .alpha(0f)
                .setDuration(120)
                .setListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        if (ov.parent != null) {
                            parent.removeView(ov)
                        }
                    }
                })
                .start()
        } else {
            if (ov.parent != null) {
                parent.removeView(ov)
            }
        }
        overlay = null
        menuView = null
        isDismissing = false
        listener?.onDismiss()
    }

    private fun createOverlay(): FrameLayout {
        return FrameLayout(context).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            setOnClickListener { dismiss() }
            alpha = 0f
            animate().alpha(1f).setDuration(120).start()
        }
    }

    @SuppressLint("InflateParams")
    private fun buildMenuView(): View {
        val inflater = LayoutInflater.from(context)
        val view = inflater.inflate(R.layout.smart_navigation_menu, null, false)

        view.isClickable = true
        view.isFocusable = true

        val fixedWidth = (context.resources.displayMetrics.density * 196).toInt()
        view.layoutParams = ViewGroup.LayoutParams(fixedWidth, ViewGroup.LayoutParams.WRAP_CONTENT)

        val bgDrawable = GradientDrawable().apply {
            setColor(context.getColorFromAttr(R.attr.background_for_cards))
            cornerRadius = context.dp16.toFloat()
            val strokeWidth = context.getDimensionFromAttr(R.attr.list_plate_stroke_width)
            if (strokeWidth > 0) {
                setStroke(strokeWidth, context.getColorFromAttr(R.attr.list_plate_stroke_color))
            }
        }
        view.background = bgDrawable
        ViewCompat.setElevation(view, context.dp16.toFloat())

        val pageList = view.findViewById<RecyclerView>(R.id.smart_nav_page_list)
        val itemHeight = context.dp48
        pageList.layoutParams = pageList.layoutParams.apply {
            height = itemHeight * PAGE_LIST_VISIBLE_ROWS
        }
        pageList.layoutManager = LinearLayoutManager(context)

        val adapter = SmartNavPageAdapter(1, 1, { page ->
            if (page in 1..totalPages) {
                listener?.onGoToPage(page)
                dismiss()
            }
        }, context)
        this.pageAdapter = adapter
        pageList.adapter = adapter

        setupAction(view, R.id.smart_nav_action_start, R.string.smart_nav_start_topic, R.drawable.ic_smart_nav_topic_start) {
            listener?.onGoToStart()
            dismiss()
        }
        setupAction(view, R.id.smart_nav_action_unread, R.string.smart_nav_unread, R.drawable.ic_smart_nav_unread) {
            listener?.onGoToUnread()
            dismiss()
        }
        setupAction(view, R.id.smart_nav_action_end, R.string.smart_nav_end_topic, R.drawable.ic_smart_nav_topic_end) {
            listener?.onGoToEnd()
            dismiss()
        }
        setupAction(view, R.id.smart_nav_action_enter, R.string.smart_nav_enter_page, R.drawable.ic_smart_nav_page_number) {
            showPageInput(view)
        }

        unreadActionView = view.findViewById(R.id.smart_nav_action_unread)
        setupPageInput(view)

        return view
    }

    private fun bindMenuData(view: View, currentPage: Int, totalPages: Int, hasUnread: Boolean) {
        pageAdapter?.update(currentPage, totalPages)
        val actualTotal = totalPages

        val pageList = view.findViewById<RecyclerView>(R.id.smart_nav_page_list)
        val itemHeight = context.dp48
        val scrollPosition = (currentPage - 1).coerceIn(0, actualTotal - 1)
        pageList.post {
            (pageList.layoutManager as? LinearLayoutManager)?.scrollToPositionWithOffset(
                scrollPosition,
                itemHeight * (PAGE_LIST_VISIBLE_ROWS / 2)
            )
        }

        unreadActionView?.visibility = if (hasUnread) View.VISIBLE else View.GONE

        // Reset input state from previous usage
        val actions = view.findViewById<View>(R.id.smart_nav_actions)
        val divider = view.findViewById<View>(R.id.smart_nav_divider)
        val inputContainer = view.findViewById<View>(R.id.smart_nav_input_container)
        val editText = view.findViewById<EditText>(R.id.smart_nav_page_input)
        actions?.visibility = View.VISIBLE
        divider?.visibility = View.VISIBLE
        inputContainer?.visibility = View.GONE
        editText?.text?.clear()
        editText?.error = null

        view.alpha = 0f
        view.scaleX = 0.9f
        view.scaleY = 0.9f
    }

    private fun setupAction(parentView: View, actionId: Int, titleRes: Int, iconRes: Int, onClick: () -> Unit) {
        val actionView = parentView.findViewById<View>(actionId)
        val icon = actionView.findViewById<ImageView>(R.id.smart_nav_action_icon)
        val title = actionView.findViewById<TextView>(R.id.smart_nav_action_title)
        icon.setImageResource(iconRes)
        title.text = context.getString(titleRes)
        actionView.contentDescription = title.text
        actionView.setOnClickListener { onClick() }
    }

    private fun showPageInput(parentView: View) {
        val inputContainer = parentView.findViewById<View>(R.id.smart_nav_input_container)
        val actions = parentView.findViewById<View>(R.id.smart_nav_actions)
        val divider = parentView.findViewById<View>(R.id.smart_nav_divider)

        actions.visibility = View.GONE
        divider.visibility = View.GONE
        inputContainer.visibility = View.VISIBLE
        keepInputAboveKeyboard(parentView)

        val editText = parentView.findViewById<EditText>(R.id.smart_nav_page_input)
        editText.requestFocus()
        editText.post {
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(editText, InputMethodManager.SHOW_IMPLICIT)
            ViewCompat.requestApplyInsets(parent)
        }
    }

    private fun keepInputAboveKeyboard(parentView: View) {
        if (keyboardLayoutListener == null) {
            keyboardLayoutListener = ViewTreeObserver.OnGlobalLayoutListener {
                val visibleFrame = Rect()
                parent.getWindowVisibleDisplayFrame(visibleFrame)
                val parentLocation = IntArray(2)
                parent.getLocationInWindow(parentLocation)
                val keyboardTopInParent = visibleFrame.bottom - parentLocation[1]
                moveMenuAboveKeyboard(parentView, keyboardTopInParent)
            }
            parent.viewTreeObserver.addOnGlobalLayoutListener(keyboardLayoutListener)
        }
        parentView.post {
            parentView.findViewById<EditText>(R.id.smart_nav_page_input)
                ?.requestRectangleOnScreen(Rect(0, 0, parentView.width, parentView.height), true)
        }
    }

    private fun moveMenuAboveKeyboard(menu: View, keyboardTop: Int) {
        val params = menu.layoutParams as? FrameLayout.LayoutParams ?: return
        val menuBottom = params.topMargin + menu.height
        val desiredBottom = keyboardTop - context.dp12
        if (menuBottom <= desiredBottom) return
        params.topMargin = (params.topMargin - (menuBottom - desiredBottom)).coerceAtLeast(context.dp16)
        menu.layoutParams = params
    }

    private fun setupPageInput(parentView: View) {
        val editText = parentView.findViewById<EditText>(R.id.smart_nav_page_input)
        val goButton = parentView.findViewById<View>(R.id.smart_nav_page_input_go)

        editText.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_GO) {
                submitPageInput(editText)
                true
            } else false
        }

        goButton.setOnClickListener {
            submitPageInput(editText)
        }

        editText.doAfterTextChanged {
            it?.toString()?.toIntOrNull()?.let { num ->
                if (num in 1..totalPages) {
                    editText.error = null
                }
            }
        }
    }

    private fun submitPageInput(editText: EditText) {
        val input = editText.text.toString().toIntOrNull()
        if (input == null || input < 1 || input > totalPages) {
            editText.error = context.getString(R.string.smart_nav_page_input_invalid, 1, totalPages)
            return
        }
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(editText.windowToken, 0)
        listener?.onGoToPage(input)
        dismiss()
    }

    private fun animateMenuShow(view: View) {
        view.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(180)
            .setListener(null)
            .start()
    }

    private companion object {
        private const val PAGE_LIST_VISIBLE_ROWS = 5
        private const val MIN_PAGE_LIST_VISIBLE_ROWS = 3

        private fun Context.getDimensionFromAttr(attr: Int): Int {
            val typedValue = TypedValue()
            if (!theme.resolveAttribute(attr, typedValue, true)) return 0
            return when (typedValue.type) {
                TypedValue.TYPE_DIMENSION -> typedValue.getDimension(resources.displayMetrics).toInt()
                in TypedValue.TYPE_FIRST_INT..TypedValue.TYPE_LAST_INT -> typedValue.data
                else -> 0
            }
        }
    }

}
