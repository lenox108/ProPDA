package forpdateam.ru.forpda.ui.views

import android.content.Context
import android.view.Gravity
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
        // Кнопку можно поднять к верхнему краю ([SmartFabPlacement]), а меню центрируется по ней —
        // поэтому потолок не просто 16dp от координатора, а низ AppBar, иначе меню лезет под тулбар.
        val appBarBottom = parent.findViewById<View>(R.id.appbar_layout)?.bottom ?: 0
        val topReserved = maxOf(context.dp16, appBarBottom + context.dp8)
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
        // Кнопку можно утащить к любому краю ([SmartFabPlacement]), поэтому сторона выбирается по её
        // центру: у правого края — меню слева от кнопки (как было), у левого — справа, иначе оно
        // упиралось бы в клампе в левый край и наезжало на саму кнопку.
        val anchorLeftInParent = anchorX - parentLoc[0]
        val parentWidth = parent.width.takeIf { it > 0 } ?: parent.rootView.width
        val anchorCenterX = anchorLeftInParent + anchorView.width / 2
        val placeLeftOfAnchor = anchorCenterX >= parentWidth / 2
        val maxStart = (parentWidth - menuW - context.dp16).coerceAtLeast(context.dp16)
        val rawX = if (placeLeftOfAnchor) anchorLeftInParent - menuW - context.dp8
        else anchorLeftInParent + anchorView.width + context.dp8
        val targetX = rawX.coerceIn(context.dp16, maxStart)
        val targetY = (anchorY - parentLoc[1] + anchorH / 2 - menuH / 2).coerceIn(topReserved, maxTop)

        // Абсолютная гравитация + leftMargin: сторону меню мы уже посчитали сами, а marginStart в RTL
        // отмерялся бы от противоположного края.
        val params = FrameLayout.LayoutParams(menuW, menuH, Gravity.TOP or Gravity.LEFT)
        params.leftMargin = targetX
        params.topMargin = targetY
        menuView.layoutParams = params
    }

    private fun bottomChromeHeightPx(context: Context, parent: ViewGroup): Int {
        val bottomBarHeight = context.resources.getDimensionPixelSize(R.dimen.bottom_nav_tab_bar_height)
        val navInset = BottomNavWindowInset.navigationBarsBottomPx(ViewCompat.getRootWindowInsets(parent))
        return bottomBarHeight + navInset
    }
}
