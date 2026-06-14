package forpdateam.ru.forpda.ui.fragments.qms.adapters

import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.updateLayoutParams
import forpdateam.ru.forpda.R
import forpdateam.ru.forpda.databinding.QmsThemeItemBinding
import forpdateam.ru.forpda.entity.remote.qms.QmsTheme
import forpdateam.ru.forpda.ui.applyListRowPlate
import forpdateam.ru.forpda.ui.currentUiDensityValues
import forpdateam.ru.forpda.ui.listPlateSegment
import forpdateam.ru.forpda.ui.setTextSizePx
import forpdateam.ru.forpda.ui.updateHeightPx
import forpdateam.ru.forpda.ui.views.adapters.BaseAdapter
import forpdateam.ru.forpda.ui.views.adapters.BaseViewHolder

class QmsThemesAdapter : BaseAdapter<QmsTheme, QmsThemesAdapter.ThemeHolder>() {

    private var itemClickListener: BaseAdapter.OnItemClickListener<QmsTheme>? = null

    fun setOnItemClickListener(listener: BaseAdapter.OnItemClickListener<QmsTheme>) {
        this.itemClickListener = listener
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ThemeHolder {
        val binding = QmsThemeItemBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ThemeHolder(binding)
    }

    override fun onBindViewHolder(holder: ThemeHolder, position: Int) {
        holder.bind(getItem(position), position)
    }

    inner class ThemeHolder(
        private val binding: QmsThemeItemBinding
    ) : BaseViewHolder<QmsTheme>(binding.root), View.OnClickListener, View.OnLongClickListener {

        init {
            binding.root.setOnClickListener(this)
            binding.root.setOnLongClickListener(this)
        }

        override fun bind(item: QmsTheme, position: Int) {
            val res = binding.root.resources
            val inset = res.getDimensionPixelSize(R.dimen.list_plate_horizontal_inset)
            val gap = res.getDimensionPixelSize(R.dimen.list_plate_group_gap_vertical)
            val last = itemCount - 1
            val segment = listPlateSegment(position > 0, position < last)
            binding.root.applyListRowPlate(
                    segment,
                    inset,
                    if (position == 0) gap else 0,
                    if (position == last) gap else 0,
                    ensureSelectableForeground = false,
            )
            val density = binding.root.context.currentUiDensityValues()
            binding.root.updateHeightPx(density.qmsThemeRowHeightPx)
            binding.qmsThemeName.setTextSizePx(density.titleTextSizePx)
            binding.qmsThemeName.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                marginStart = density.itemHorizontalPaddingPx
            }
            binding.qmsThemeCount.setTextSizePx(density.metadataTextSizePx)
            binding.qmsThemeCount.setPaddingRelative(density.dividerSpacingPx, 0, density.dividerSpacingPx, 0)

            binding.qmsThemeName.text = item.name
            binding.qmsThemeName.typeface = if (item.countNew > 0) {
                Typeface.DEFAULT_BOLD
            } else {
                Typeface.DEFAULT
            }
            if (item.countNew == 0) {
                binding.qmsThemeCount.visibility = View.GONE
            } else {
                binding.qmsThemeCount.text = item.countNew.toString()
                binding.qmsThemeCount.visibility = View.VISIBLE
            }
        }

        override fun onClick(view: View) {
            val pos = bindingAdapterPosition
            if (pos == androidx.recyclerview.widget.RecyclerView.NO_POSITION) return
            itemClickListener?.onItemClick(getItem(pos))
        }

        override fun onLongClick(view: View): Boolean {
            val pos = bindingAdapterPosition
            if (pos == androidx.recyclerview.widget.RecyclerView.NO_POSITION) return false
            return itemClickListener?.let {
                it.onItemLongClick(getItem(pos))
                true
            } ?: false
        }
    }
}
