package forpdateam.ru.forpda.ui.views.messagepanel.colorpicker

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.GridView

/**
 * Simple color palette view to replace Spectrum library
 */
class ColorPaletteView(context: Context) : GridView(context) {
    private var colors: IntArray? = null
    private var listener: OnColorSelectedListener? = null

    interface OnColorSelectedListener {
        fun onColorSelected(color: Int)
    }

    init {
        init()
    }

    private fun init() {
        numColumns = 5
        verticalSpacing = 8
        horizontalSpacing = 8
        stretchMode = STRETCH_COLUMN_WIDTH
    }

    fun setColors(colors: IntArray) {
        this.colors = colors
        adapter = ColorAdapter()
    }

    fun setOnColorSelectedListener(listener: OnColorSelectedListener?) {
        this.listener = listener
    }

    private inner class ColorAdapter : BaseAdapter() {
        override fun getCount(): Int = colors?.size ?: 0

        override fun getItem(position: Int): Any? = colors?.get(position)

        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val colorView: View
            if (convertView == null) {
                colorView = View(context)
                val size = (48 * context.resources.displayMetrics.density).toInt()
                colorView.layoutParams = LayoutParams(size, size)
            } else {
                colorView = convertView
            }
            colors?.let { colorView.setBackgroundColor(it[position]) }
            colorView.setOnClickListener {
                colors?.let { listener?.onColorSelected(it[position]) }
            }
            return colorView
        }
    }
}
