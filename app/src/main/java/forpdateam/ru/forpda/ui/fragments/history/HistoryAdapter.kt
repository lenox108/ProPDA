package forpdateam.ru.forpda.ui.fragments.history

import android.view.LayoutInflater
import android.view.ViewGroup
import forpdateam.ru.forpda.R
import forpdateam.ru.forpda.databinding.ItemHistoryBinding
import forpdateam.ru.forpda.entity.app.history.HistoryItem
import forpdateam.ru.forpda.ui.ListPlateSegment
import forpdateam.ru.forpda.ui.applyListRowPlate
import forpdateam.ru.forpda.ui.views.adapters.BaseAdapter
import forpdateam.ru.forpda.ui.views.adapters.BaseViewHolder

class HistoryAdapter : BaseAdapter<HistoryItem, HistoryAdapter.HistoryHolder>() {

    private var itemClickListener: BaseAdapter.OnItemClickListener<HistoryItem>? = null

    fun setItemClickListener(listener: BaseAdapter.OnItemClickListener<HistoryItem>) {
        this.itemClickListener = listener
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HistoryHolder {
        val binding = ItemHistoryBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return HistoryHolder(binding)
    }

    override fun onBindViewHolder(holder: HistoryHolder, position: Int) {
        holder.bind(getItem(position), position)
    }

    inner class HistoryHolder(
        private val binding: ItemHistoryBinding
    ) : BaseViewHolder<HistoryItem>(binding.root) {

        init {
            binding.root.setOnClickListener {
                itemClickListener?.onItemClick(getItem(layoutPosition))
            }
            binding.root.setOnLongClickListener {
                itemClickListener?.let { listener ->
                    listener.onItemLongClick(getItem(layoutPosition))
                    true
                } ?: false
            }
        }

        override fun bind(item: HistoryItem, section: Int) {
            val res = binding.root.resources
            val inset = res.getDimensionPixelSize(R.dimen.list_plate_horizontal_inset)
            val gap = res.getDimensionPixelSize(R.dimen.list_plate_group_gap_vertical)
            binding.root.applyListRowPlate(
                    ListPlateSegment.SINGLE,
                    inset,
                    gapBeforeGroupPx = if (section == 0) gap else 0,
                    gapAfterGroupPx = gap,
                    ensureSelectableForeground = true,
            )
            binding.itemTitle.text = item.title
            binding.itemDate.text = item.date
        }
    }
}
