package forpdateam.ru.forpda.ui.views

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.recyclerview.widget.RecyclerView
import forpdateam.ru.forpda.R
import forpdateam.ru.forpda.common.getColorFromAttr
import forpdateam.ru.forpda.ui.dp8
import forpdateam.ru.forpda.ui.getDimensionFromAttr

internal class SmartNavPageAdapter(
    private var currentPage: Int,
    private var totalPages: Int,
    private val onPageClick: (Int) -> Unit,
    private val context: Context,
) : RecyclerView.Adapter<SmartNavPageViewHolder>() {

    fun update(newCurrentPage: Int, newTotalPages: Int) {
        val oldCurrent = currentPage
        val oldTotal = totalPages
        currentPage = newCurrentPage
        totalPages = newTotalPages

        when {
            oldTotal != newTotalPages -> notifyDataSetChanged()
            oldCurrent != newCurrentPage -> {
                if (oldCurrent in 1..oldTotal) {
                    notifyItemChanged(oldCurrent - 1)
                }
                if (newCurrentPage in 1..newTotalPages) {
                    notifyItemChanged(newCurrentPage - 1)
                }
            }
        }
    }

    override fun getItemCount(): Int = totalPages

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SmartNavPageViewHolder {
        val view = LayoutInflater.from(context).inflate(R.layout.smart_nav_page_item, parent, false)
        return SmartNavPageViewHolder(view, onPageClick, context)
    }

    override fun onBindViewHolder(holder: SmartNavPageViewHolder, position: Int) {
        val pageNumber = position + 1
        holder.bind(pageNumber, pageNumber == currentPage)
    }
}

internal class SmartNavPageViewHolder(
    itemView: View,
    private val onPageClick: (Int) -> Unit,
    private val context: Context,
) : RecyclerView.ViewHolder(itemView) {

    private val rowLayout: LinearLayout = itemView.findViewById(R.id.smart_nav_page_row)
    private val numberView: TextView = itemView.findViewById(R.id.smart_nav_page_number)
    private val currentLabel: TextView = itemView.findViewById(R.id.smart_nav_page_current_label)
    private val currentPageBg: GradientDrawable by lazy {
        GradientDrawable().apply {
            cornerRadius = context.dp8.toFloat()
        }
    }
    private var currentBgColor: Int? = null
    private var currentStrokeWidth: Int? = null
    private var currentStrokeColor: Int? = null

    init {
        itemView.setOnClickListener {
            val page = bindingAdapterPosition + 1
            if (page > 0) {
                onPageClick(page)
            }
        }
    }

    fun bind(pageNumber: Int, isCurrent: Boolean) {
        numberView.text = pageNumber.toString()
        currentLabel.visibility = if (isCurrent) View.VISIBLE else View.GONE
        itemView.contentDescription = context.getString(R.string.smart_nav_page_desc, pageNumber)
        ViewCompat.setStateDescription(
                itemView,
                if (isCurrent) context.getString(R.string.smart_nav_page_current_state) else null
        )

        if (isCurrent) {
            val bgColor = context.getColorFromAttr(R.attr.background_for_lists)
            val strokeWidth = context.getDimensionFromAttr(R.attr.list_plate_stroke_width).toInt()
            val strokeColor = context.getColorFromAttr(R.attr.list_plate_stroke_color)
            if (currentBgColor != bgColor || currentStrokeWidth != strokeWidth || currentStrokeColor != strokeColor) {
                currentBgColor = bgColor
                currentStrokeWidth = strokeWidth
                currentStrokeColor = strokeColor
                currentPageBg.setColor(bgColor)
                if (strokeWidth > 0) {
                    currentPageBg.setStroke(strokeWidth, strokeColor)
                } else {
                    currentPageBg.setStroke(0, 0)
                }
            }
            rowLayout.background = currentPageBg
            numberView.setTextColor(context.getColorFromAttr(R.attr.default_text_color))
        } else {
            rowLayout.background = null
            numberView.setTextColor(context.getColorFromAttr(R.attr.default_text_color))
        }
    }
}
