package forpdateam.ru.forpda.ui.views

import android.content.Context
import android.content.res.TypedArray
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView

/**
 * Created by radiationx on 22.09.16.
 */
class DividerItemDecoration : RecyclerView.ItemDecoration {
    private val mDivider: Drawable?

    constructor(context: Context) {
        val styledAttributes = context.obtainStyledAttributes(ATTRS)
        mDivider = styledAttributes.getDrawable(0)
        styledAttributes.recycle()
    }

    constructor(context: Context, resId: Int) {
        mDivider = ContextCompat.getDrawable(context, resId)
    }

    override fun onDraw(c: Canvas, parent: RecyclerView, state: RecyclerView.State) {
        val left = parent.paddingLeft
        val right = parent.width - parent.paddingRight
        val childCount = parent.childCount
        for (i in 0 until childCount - 1) {
            val child = parent.getChildAt(i)
            if (parent.getChildAdapterPosition(child) == RecyclerView.NO_POSITION) continue
            val params = child.layoutParams as RecyclerView.LayoutParams
            val top = child.bottom + params.bottomMargin
            val bottom = top + (mDivider?.intrinsicHeight ?: 0)
            mDivider?.setBounds(left, top, right, bottom)
            mDivider?.draw(c)
        }
    }

    companion object {
        private val ATTRS = intArrayOf(android.R.attr.listDivider)
    }
}
