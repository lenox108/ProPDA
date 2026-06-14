package forpdateam.ru.forpda.ui.views.control

import android.view.MotionEvent
import android.view.View
import androidx.coordinatorlayout.widget.CoordinatorLayout

/**
 * Created by fedor on 21.03.2017.
 */
class BottomSheetBehaviorRecyclerManager<T : View>(
    private val mParent: CoordinatorLayout,
    private val mBehavior: ICustomBottomSheetBehavior<T>,
    private val mBottomSheetView: T
) {
    private var mViews: MutableList<View> = ArrayList()
    private lateinit var mTouchEventListener: View.OnTouchListener

    init {
        initTouchCallback()
    }

    fun addControl(recyclerView: View) {
        mViews.add(recyclerView)
        mBehavior.setNestedScrollingChildRefList(mViews)
    }

    fun create() {
        if (mViews.isEmpty()) return
        for (view in mViews) {
            view.setOnTouchListener(mTouchEventListener)
        }
    }

    private fun initTouchCallback() {
        mTouchEventListener = View.OnTouchListener { _, _ -> false }
    }
}
