package forpdateam.ru.forpda.ui.fragments.theme.modules

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import kotlin.math.min
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import androidx.appcompat.widget.AppCompatButton
import androidx.appcompat.widget.AppCompatCheckBox
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.floatingactionbutton.FloatingActionButton
import forpdateam.ru.forpda.R
import forpdateam.ru.forpda.common.getColorFromAttr
import forpdateam.ru.forpda.common.getVecDrawable
import forpdateam.ru.forpda.entity.remote.others.pagination.Pagination
import forpdateam.ru.forpda.model.preferences.OtherPreferencesHolder
import forpdateam.ru.forpda.ui.BottomNavWindowInset
import forpdateam.ru.forpda.ui.dp12
import forpdateam.ru.forpda.ui.dp16
import forpdateam.ru.forpda.ui.dp24
import forpdateam.ru.forpda.ui.dp32
import forpdateam.ru.forpda.ui.dp8
import forpdateam.ru.forpda.ui.views.ExtendedWebView
import forpdateam.ru.forpda.ui.views.FabOnScroll
import forpdateam.ru.forpda.ui.views.SmartNavigationMenu
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Encapsulates FAB quick-scroll, smart navigation menu, and scroll-related interactions.
 * Keeps ThemeFragmentWeb free from FAB/menu wiring details.
 */
class ThemeFabCoordinator(
    private val context: Context,
    private val fab: FloatingActionButton,
    private val coordinatorLayout: CoordinatorLayout,
    private val fabBehavior: FabOnScroll?,
    private val webView: ExtendedWebView,
    private val scrollHandler: ThemeScrollHandler,
    private val lifecycleScope: CoroutineScope,
    private val otherPreferencesHolder: OtherPreferencesHolder,
    private val onLoadPage: (Int) -> Unit,
    private val onLoadLastPageAndScrollToBottom: () -> Unit,
    private val onLoadNewPosts: () -> Unit,
    private val onGetPagination: () -> Pagination?,
    private val onGetVisibleCurrentPage: () -> Int,
    private val onHasUnread: () -> Boolean = { false },
    private val onUserScroll: (Int) -> Unit = {}
) : ThemeUiModule {
    private var smartNavMenu: SmartNavigationMenu? = null
    private var smartNavHintPopup: PopupWindow? = null
    private var smartNavHintSuppressedForSession = false
    private var smartNavHintCheckInProgress = false
    private var smartNavHintDoNotShowAgain = false
    private var currentFabDirection = ExtendedWebView.DIRECTION_DOWN
    private var disposed = false
    private val smartNavHintRunnables = mutableListOf<Runnable>()

    override fun init() {
        disposed = false
        fabBehavior?.setOnScrollListener(object : FabOnScroll.OnScrollListener {
            override fun onFabShown() {
                if (fab.isEnabled) {
                    scheduleSmartNavHintCheck()
                } else {
                    fab.hide()
                }
            }

            override fun onFabHidden() = Unit
        })

        installFabCompactTouchTarget()

        fab.setOnClickListener {
            if (!fab.isEnabled) return@setOnClickListener
            if (currentFabDirection == ExtendedWebView.DIRECTION_DOWN) {
                webView.pageDown(true)
            } else if (currentFabDirection == ExtendedWebView.DIRECTION_UP) {
                webView.pageUp(true)
            }
            fabBehavior?.resetHideTimer(fab)
        }

        smartNavMenu = SmartNavigationMenu(context, fab, coordinatorLayout).apply {
            setListener(object : SmartNavigationMenu.Listener {
                override fun onGoToPage(page: Int) {
                    onGetPagination()?.let { pagination ->
                        onLoadPage(pagination.getPage(page - if (pagination.isForum) 1 else 0))
                    }
                }
                override fun onGoToStart() {
                    onGetPagination()?.let { pagination ->
                        onLoadPage(if (pagination.isForum) 0 else 1)
                    }
                }
                override fun onGoToEnd() {
                    onLoadLastPageAndScrollToBottom()
                }
                override fun onGoToUnread() {
                    onLoadNewPosts()
                }
                override fun onDismiss() {
                    fabBehavior?.setMenuOpen(false)
                    fabBehavior?.resetHideTimer(fab)
                }
            })
        }

        fab.setOnLongClickListener {
            if (!fab.isEnabled) return@setOnLongClickListener true
            val page = onGetPagination()
            if (page != null) {
                it.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                fabBehavior?.setMenuOpen(true)
                smartNavMenu?.show(onGetVisibleCurrentPage(), page.all, hasUnread = onHasUnread())
            }
            true
        }

        scrollHandler.setExternalScrollListener(object : ExtendedWebView.OnScrollListener {
            override fun onScrollChange(scrollX: Int, scrollY: Int, oldScrollX: Int, oldScrollY: Int) {
                if (webView.isUserScrollActive()) {
                    val dy = scrollY - oldScrollY
                    fabBehavior?.onUserScroll(fab, dy)
                    onUserScroll(dy)
                }
                if (smartNavMenu?.isShowing() == true) {
                    smartNavMenu?.dismiss()
                }
            }
        })

        scheduleSmartNavHintCheck()
    }

    /**
     * FAB рисуется поверх WebView: при скрытой (alpha≈0) или по краям mini-FAB не перехватывать
     * тапы по ⋮ в шапке последнего поста и другим элементам под кнопкой.
     */
    private fun installFabCompactTouchTarget() {
        fab.setOnTouchListener { view, event ->
            if (!view.isEnabled || view.alpha < 0.05f) {
                return@setOnTouchListener false
            }
            if (event.action == MotionEvent.ACTION_DOWN) {
                val radius = min(view.width, view.height) * 0.42f
                val dx = event.x - view.width / 2f
                val dy = event.y - view.height / 2f
                if (dx * dx + dy * dy > radius * radius) {
                    return@setOnTouchListener false
                }
            }
            false
        }
    }

    fun onPageUpdated() {
        scheduleSmartNavHintCheck()
    }

    fun onFabVisibilityUpdated() {
        scheduleSmartNavHintCheck()
    }

    fun onBackPressed(): Boolean {
        if (smartNavMenu?.isShowing() == true) {
            smartNavMenu?.dismiss()
            return true
        }
        if (smartNavHintPopup?.isShowing == true) {
            smartNavHintPopup?.dismiss()
            return true
        }
        return false
    }

    private fun scheduleSmartNavHintCheck() {
        if (disposed) return
        if (smartNavHintSuppressedForSession || smartNavHintCheckInProgress) return
        smartNavHintCheckInProgress = true
        val delays = longArrayOf(0L, 180L, 450L, 900L)
        delays.forEach { delayMillis ->
            var runnable: Runnable? = null
            runnable = Runnable {
                runnable?.let { smartNavHintRunnables.remove(it) }
                if (disposed || smartNavHintSuppressedForSession) return@Runnable
                maybeShowSmartNavHint()
            }
            smartNavHintRunnables.add(runnable)
            fab.postDelayed(runnable, delayMillis)
        }
        var resetRunnable: Runnable? = null
        resetRunnable = Runnable {
            resetRunnable?.let { smartNavHintRunnables.remove(it) }
            smartNavHintCheckInProgress = false
        }
        smartNavHintRunnables.add(resetRunnable)
        fab.postDelayed(resetRunnable, delays.last() + 50L)
    }

    private fun maybeShowSmartNavHint() {
        if (disposed) return
        if (!canShowSmartNavHint()) return

        lifecycleScope.launch {
            if (disposed) return@launch
            if (!canShowSmartNavHint() || otherPreferencesHolder.getSmartNavLongPressHintDisabled()) return@launch

            smartNavHintSuppressedForSession = true
            showSmartNavHintPopup()
        }
    }

    private fun canShowSmartNavHint(ignoreSessionSuppression: Boolean = false): Boolean {
        return (ignoreSessionSuppression || !smartNavHintSuppressedForSession) &&
                smartNavHintPopup?.isShowing != true &&
                smartNavMenu?.isShowing() != true &&
                onGetPagination() != null &&
                coordinatorLayout.isAttachedToWindow &&
                coordinatorLayout.width > 0 &&
                coordinatorLayout.height > 0 &&
                fab.isAttachedToWindow &&
                fab.visibility == View.VISIBLE &&
                fab.width > 0 &&
                fab.height > 0 &&
                fab.isEnabled &&
                fab.alpha > 0f
    }

    private fun showSmartNavHintPopup() {
        if (smartNavHintPopup?.isShowing == true || !coordinatorLayout.isAttachedToWindow) return

        smartNavHintDoNotShowAgain = false
        val panel = buildSmartNavHintPanel()
        val popup = PopupWindow(panel, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, true).apply {
            isOutsideTouchable = true
            elevation = context.dp24.toFloat()
            setBackgroundDrawable(GradientDrawable().apply { setColor(android.graphics.Color.TRANSPARENT) })
            setOnDismissListener {
                if (smartNavHintDoNotShowAgain) {
                    lifecycleScope.launch {
                        otherPreferencesHolder.setSmartNavLongPressHintDisabled(true)
                    }
                }
                if (smartNavHintPopup === this) smartNavHintPopup = null
            }
        }
        smartNavHintPopup = popup

        if (!canShowSmartNavHint(ignoreSessionSuppression = true)) {
            popup.dismiss()
            return
        }
        showSmartNavHintPopupAtFab(popup, panel)
        panel.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(180).start()
    }

    private fun buildSmartNavHintPanel(): View {
        val panelWidth = (context.resources.displayMetrics.density * 280).toInt()
        val panel = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            isClickable = true
            isFocusable = true
            alpha = 0f
            scaleX = 0.94f
            scaleY = 0.94f
            setPadding(context.dp16, context.dp12, context.dp16, context.dp8)
            layoutParams = ViewGroup.LayoutParams(panelWidth, ViewGroup.LayoutParams.WRAP_CONTENT)
            background = GradientDrawable().apply {
                setColor(context.getColorFromAttr(R.attr.background_for_cards))
                cornerRadius = context.dp16.toFloat()
                val strokeWidth = context.getDimensionFromAttr(R.attr.list_plate_stroke_width)
                if (strokeWidth > 0) {
                    setStroke(strokeWidth, context.getColorFromAttr(R.attr.list_plate_stroke_color))
                }
            }
        }
        ViewCompat.setElevation(panel, context.dp16.toFloat())

        panel.addView(TextView(context).apply {
            text = context.getString(R.string.smart_nav_hint)
            setTextColor(context.getColorFromAttr(R.attr.default_text_color))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        })

        val checkbox = AppCompatCheckBox(context).apply {
            text = context.getString(R.string.smart_nav_hint_never_show)
            setTextColor(context.getColorFromAttr(R.attr.default_text_color))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            buttonTintList = android.content.res.ColorStateList.valueOf(context.getColorFromAttr(R.attr.link_color))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = context.dp8
            }
            setOnCheckedChangeListener { _, isChecked ->
                smartNavHintDoNotShowAgain = isChecked
            }
        }
        panel.addView(checkbox)

        panel.addView(AppCompatButton(context).apply {
            text = context.getString(R.string.ok)
            setTextColor(context.getColorFromAttr(R.attr.link_color))
            minHeight = context.dp24
            minimumHeight = context.dp24
            background = null
            setOnClickListener { dismissSmartNavHint(checkbox.isChecked) }
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = context.dp8
                gravity = android.view.Gravity.END
            }
        })

        return panel
    }

    private fun showSmartNavHintPopupAtFab(popup: PopupWindow, panel: View) {
        val anchorLoc = IntArray(2)
        fab.getLocationInWindow(anchorLoc)

        val parentWidth = coordinatorLayout.width.takeIf { it > 0 } ?: coordinatorLayout.rootView.width
        val parentHeight = coordinatorLayout.height.takeIf { it > 0 } ?: coordinatorLayout.rootView.height
        val widthSpec = View.MeasureSpec.makeMeasureSpec(parentWidth - context.dp16 * 2, View.MeasureSpec.AT_MOST)
        val heightSpec = View.MeasureSpec.makeMeasureSpec(parentHeight, View.MeasureSpec.AT_MOST)
        panel.measure(widthSpec, heightSpec)

        val panelWidth = panel.measuredWidth
        val panelHeight = panel.measuredHeight
        val anchorCenterX = anchorLoc[0] + fab.width / 2
        val anchorTop = anchorLoc[1]
        val bottomLimit = (parentHeight - panelHeight - transientBottomInsetPx() - context.dp16)
                .coerceAtLeast(context.dp16)

        val targetX = (anchorCenterX - panelWidth / 2).coerceIn(context.dp16, (parentWidth - panelWidth - context.dp16).coerceAtLeast(context.dp16))
        val targetY = (anchorTop - panelHeight - context.dp8).let { aboveFab ->
            if (aboveFab >= context.dp16) {
                aboveFab.coerceAtMost(bottomLimit)
            } else {
                (anchorTop + fab.height + context.dp8).coerceAtMost(bottomLimit)
            }
        }

        popup.showAtLocation(coordinatorLayout, Gravity.NO_GRAVITY, targetX, targetY)
    }

    private fun transientBottomInsetPx(): Int {
        val rootInsets = ViewCompat.getRootWindowInsets(coordinatorLayout)
        val navigationBottom = BottomNavWindowInset.navigationBarsBottomPx(rootInsets)
        val imeBottom = rootInsets
                ?.takeIf { it.isVisible(WindowInsetsCompat.Type.ime()) }
                ?.getInsets(WindowInsetsCompat.Type.ime())
                ?.bottom
                ?: 0
        return maxOf(navigationBottom, imeBottom)
    }

    private fun dismissSmartNavHint(doNotShowAgain: Boolean = false) {
        smartNavHintDoNotShowAgain = smartNavHintDoNotShowAgain || doNotShowAgain
        smartNavHintPopup?.dismiss()
    }

    fun onDirectionChanged(direction: Int) {
        currentFabDirection = direction
        if (direction == ExtendedWebView.DIRECTION_DOWN) {
            fab.setImageDrawable(fab.context.getVecDrawable(R.drawable.ic_arrow_down))
        } else if (direction == ExtendedWebView.DIRECTION_UP) {
            fab.setImageDrawable(fab.context.getVecDrawable(R.drawable.ic_arrow_up))
        }
    }

    override fun dispose() {
        disposed = true
        smartNavHintRunnables.forEach { fab.removeCallbacks(it) }
        smartNavHintRunnables.clear()
        smartNavHintPopup?.dismiss()
        smartNavHintPopup = null
        smartNavMenu?.dispose()
        smartNavMenu = null
        fabBehavior?.setOnScrollListener(null)
        fabBehavior?.dispose()
        fab.setOnClickListener(null)
        fab.setOnLongClickListener(null)
        fab.setOnTouchListener(null)
        scrollHandler.setExternalScrollListener(null)
    }

    private companion object {
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
