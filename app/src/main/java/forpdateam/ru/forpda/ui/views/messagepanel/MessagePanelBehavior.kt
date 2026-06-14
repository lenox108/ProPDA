package forpdateam.ru.forpda.ui.views.messagepanel

import android.view.View
import androidx.cardview.widget.CardView
import androidx.coordinatorlayout.widget.CoordinatorLayout
import com.google.android.material.appbar.AppBarLayout
import forpdateam.ru.forpda.R

/**
 * Created by radiationx on 07.01.17.
 */
class MessagePanelBehavior : CoordinatorLayout.Behavior<CardView> {
    private var canScrolling: Boolean = true

    constructor() : super()

    fun setCanScrolling(canScrolling: Boolean) {
        this.canScrolling = canScrolling
    }

    override fun onStartNestedScroll(
        coordinatorLayout: CoordinatorLayout,
        child: CardView,
        directTargetChild: View,
        target: View,
        nestedScrollAxes: Int
    ): Boolean {
        if (!canScrolling) child.translationY = 0f
        return canScrolling
    }

    override fun layoutDependsOn(parent: CoordinatorLayout, child: CardView, dependency: View): Boolean {
        return dependency is AppBarLayout
    }

    override fun onDependentViewChanged(parent: CoordinatorLayout, child: CardView, dependency: View): Boolean {
        if (!canScrolling) return false
        val depH = dependency.measuredHeight
        if (depH <= 0) {
            child.translationY = 0f
            return false
        }
        var percent = 1.0f - (-dependency.top.toFloat() / depH.toFloat())
        if (percent < 0f) percent = 0f
        else if (percent > 1f) percent = 1f
        val scrolled = ((child.measuredHeight + 2 * child.context.resources.getDimensionPixelSize(R.dimen.dp8)) * percent).toInt()
        child.translationY = scrolled.toFloat()
        return true
    }
}
