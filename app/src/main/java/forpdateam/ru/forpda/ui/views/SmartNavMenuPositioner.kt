package forpdateam.ru.forpda.ui.views

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.core.view.ViewCompat
import androidx.recyclerview.widget.RecyclerView
import forpdateam.ru.forpda.R
import forpdateam.ru.forpda.ui.BottomNavWindowInset
import forpdateam.ru.forpda.ui.dp12
import forpdateam.ru.forpda.ui.dp16
import forpdateam.ru.forpda.ui.dp40
import forpdateam.ru.forpda.ui.dp8

internal object SmartNavMenuPositioner {

    fun position(
        context: Context,
        anchorView: View,
        parent: ViewGroup,
        menuView: View,
        pageListVisibleRows: Int,
        minPageListVisibleRows: Int,
    ) {
        val anchorLoc = IntArray(2)
        anchorView.getLocationInWindow(anchorLoc)
        val anchorX = anchorLoc[0]
        val anchorY = anchorLoc[1]
        val anchorH = anchorView.height

        val parentLoc = IntArray(2)
        parent.getLocationInWindow(parentLoc)

        val parentHeight = parent.height.takeIf { it > 0 } ?: parent.rootView.height
        val bottomReserved = bottomChromeHeightPx(context, parent) + context.dp12
        val topReserved = context.dp16
        val maxMenuHeight = (parentHeight - topReserved - bottomReserved).coerceAtLeast(context.dp40 * 3)

        // Shrink page list if menu exceeds available height (single-pass measure)
        val pageList = menuView.findViewById<RecyclerView>(R.id.smart_nav_page_list)
        val itemHeight = context.dp40
        val minListHeight = itemHeight * minPageListVisibleRows
        val maxListHeight = itemHeight * pageListVisibleRows

        pageList.layoutParams = pageList.layoutParams.apply {
            height = maxListHeight
        }
        menuView.measure(
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
            View.MeasureSpec.makeMeasureSpec(maxMenuHeight, View.MeasureSpec.AT_MOST)
        )

        val overflow = menuView.measuredHeight - maxMenuHeight
        if (overflow > 0) {
            pageList.layoutParams = pageList.layoutParams.apply {
                height = (maxListHeight - overflow).coerceAtLeast(minListHeight)
            }
            menuView.measure(
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                View.MeasureSpec.makeMeasureSpec(maxMenuHeight, View.MeasureSpec.AT_MOST)
            )
        }

        val menuW = menuView.measuredWidth
        val menuH = menuView.measuredHeight

        val maxTop = (parentHeight - bottomReserved - menuH).coerceAtLeast(topReserved)
        // Position to the left of FAB, vertically centered with FAB, then keep it above bottom chrome.
        val targetX = (anchorX - parentLoc[0] - menuW - context.dp8).coerceAtLeast(context.dp16)
        val targetY = (anchorY - parentLoc[1] + anchorH / 2 - menuH / 2).coerceIn(topReserved, maxTop)

        val params = FrameLayout.LayoutParams(menuW, menuH)
        params.marginStart = targetX
        params.topMargin = targetY
        menuView.layoutParams = params
    }

    private fun bottomChromeHeightPx(context: Context, parent: ViewGroup): Int {
        val bottomBarHeight = context.resources.getDimensionPixelSize(R.dimen.bottom_nav_tab_bar_height)
        val navInset = BottomNavWindowInset.navigationBarsBottomPx(ViewCompat.getRootWindowInsets(parent))
        return bottomBarHeight + navInset
    }
}
