package forpdateam.ru.forpda.ui.views.control

import android.view.View
import androidx.coordinatorlayout.widget.CoordinatorLayout

interface ICustomBottomSheetBehavior<V : View> {
    fun setNestedScrollingChildRefList(nestedScrollingChildRefList: List<View>)
    fun onLayoutChild(parent: CoordinatorLayout, child: V, layoutDirection: Int): Boolean
}
