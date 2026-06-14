package forpdateam.ru.forpda.ui.views.messagepanel

import android.content.Context
import android.util.AttributeSet
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import forpdateam.ru.forpda.R

/**
 * Created by radiationx on 08.01.17.
 */
class AutoFitRecyclerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : RecyclerView(context, attrs, defStyle) {
    internal val manager: GridLayoutManager = GridLayoutManager(context, 1)
    private var columnWidth: Int = -1
    private var isLinear: Boolean = false

    init {
        layoutManager = manager
        if (columnWidth < 0) {
            columnWidth = context.resources.getDimensionPixelSize(R.dimen.dp48)
        }
    }

    fun setColumnWidth(columnWidth: Int) {
        this.columnWidth = columnWidth
        invalidate()
    }

    fun setFakeLinear(linear: Boolean) {
        isLinear = linear
        invalidate()
    }

    fun getManager(): GridLayoutManager = manager

    override fun onMeasure(widthSpec: Int, heightSpec: Int) {
        super.onMeasure(widthSpec, heightSpec)
        if (isLinear || columnWidth <= 0) {
            manager.spanCount = 1
        } else {
            val spanCount = Math.max(1, measuredWidth / columnWidth)
            manager.spanCount = spanCount
        }
    }
}
