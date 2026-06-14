package forpdateam.ru.forpda.ui.views

import forpdateam.ru.forpda.common.getVecDrawable
import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.view.ViewCompat
import com.google.android.material.floatingactionbutton.FloatingActionButton
import forpdateam.ru.forpda.R
import forpdateam.ru.forpda.ui.dp24
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Behavior для FloatingActionButton, показывающий/скрывающий кнопку при скролле.
 *
 * Улучшения:
 * - Coroutines MainScope вместо Handler
 * - Упрощенная работа с анимациями
 * - dp24 вместо App.px24
 */
class FabOnScroll : FloatingActionButton.Behavior {

    interface OnScrollListener {
        fun onFabShown()
        fun onFabHidden()
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var hideJob: Job? = null
    private val interpolator = AccelerateDecelerateInterpolator()
    private var scrollListener: OnScrollListener? = null
    private var isMenuOpen = false
    private var currentFabDirection: Boolean? = null

    @JvmOverloads
    constructor(context: Context, attrs: AttributeSet? = null) : super(context, attrs)

    fun setOnScrollListener(listener: OnScrollListener?) {
        this.scrollListener = listener
    }

    fun setMenuOpen(isOpen: Boolean) {
        this.isMenuOpen = isOpen
    }

    fun dispose() {
        hideJob?.cancel()
        scope.cancel()
    }

    private fun setFabInteractive(child: FloatingActionButton, isInteractive: Boolean) {
        child.isClickable = isInteractive
        child.isEnabled = isInteractive
        child.isFocusable = isInteractive
    }

    override fun onStartNestedScroll(
        coordinatorLayout: CoordinatorLayout,
        child: FloatingActionButton,
        directTargetChild: View,
        target: View,
        nestedScrollAxes: Int,
        type: Int
    ): Boolean {
        return (nestedScrollAxes and ViewCompat.SCROLL_AXIS_VERTICAL) != 0
    }

    override fun onNestedPreScroll(
        coordinatorLayout: CoordinatorLayout,
        child: FloatingActionButton,
        target: View,
        dx: Int,
        dy: Int,
        consumed: IntArray,
        type: Int
    ) {
        super.onNestedPreScroll(coordinatorLayout, child, target, dx, dy, consumed, type)
        if (dy != 0) {
            showFab(child, dy > 0)
        }
    }

    override fun onNestedScroll(
        coordinatorLayout: CoordinatorLayout,
        child: FloatingActionButton,
        target: View,
        dxConsumed: Int,
        dyConsumed: Int,
        dxUnconsumed: Int,
        dyUnconsumed: Int,
        type: Int,
        consumed: IntArray
    ) {
        super.onNestedScroll(coordinatorLayout, child, target, dxConsumed, dyConsumed, dxUnconsumed, dyUnconsumed, type, consumed)
        if (dyUnconsumed != 0) {
            showFab(child, dyUnconsumed > 0)
        }
    }

    fun onUserScroll(child: FloatingActionButton, dy: Int) {
        if (dy == 0 || child.visibility != View.VISIBLE) return
        showFab(child, dy > 0)
        scheduleHide(child)
    }

    private fun showFab(child: FloatingActionButton, isScrollingDown: Boolean) {
        if (isMenuOpen) return
        if (child.visibility != View.VISIBLE) return
        hideJob?.cancel()
        setFabInteractive(child, true)

        // Guard: skip animation churn if already visible with same direction
        if (child.alpha > 0f && currentFabDirection == isScrollingDown) {
            return
        }
        currentFabDirection = isScrollingDown

        if (child.alpha == 0.0f) {
            child.setImageDrawable(child.context.getVecDrawable(if (isScrollingDown) R.drawable.ic_arrow_down else R.drawable.ic_arrow_up))
            child.clearAnimation()
            child.animate()
                .scaleX(1.0f)
                .scaleY(1.0f)
                .alpha(1.0f)
                .setInterpolator(interpolator)
                .setListener(object : android.animation.AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: android.animation.Animator) {
                        scrollListener?.onFabShown()
                    }
                })
                .start()
        }
    }

    override fun onStopNestedScroll(
        coordinatorLayout: CoordinatorLayout,
        child: FloatingActionButton,
        target: View,
        type: Int
    ) {
        super.onStopNestedScroll(coordinatorLayout, child, target, type)
        scheduleHide(child)
    }

    fun scheduleHide(child: FloatingActionButton) {
        if (isMenuOpen) return
        hideJob?.cancel()
        hideJob = scope.launch {
            delay(3000)
            if (isMenuOpen || !child.isAttachedToWindow) return@launch
            child.clearAnimation()
            setFabInteractive(child, false)
            child.animate()
                .scaleX(0.0f)
                .scaleY(0.0f)
                .alpha(0.0f)
                .setInterpolator(interpolator)
                .setListener(object : android.animation.AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: android.animation.Animator) {
                        scrollListener?.onFabHidden()
                        setFabInteractive(child, false)
                    }
                })
                .start()
        }
    }

    fun resetHideTimer(child: FloatingActionButton) {
        scheduleHide(child)
    }
}
