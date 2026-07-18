package forpdateam.ru.forpda.ui.fragments.history

import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.View
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

    /** Настройка «Индикатор новых сообщений» (lists.topic.show_dot) — гейт точки/счётчика. */
    private var showDot = false

    fun setItemClickListener(listener: BaseAdapter.OnItemClickListener<HistoryItem>) {
        this.itemClickListener = listener
    }

    fun setShowDot(value: Boolean) {
        if (showDot == value) return
        showDot = value
        notifyDataSetChanged()
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
                // layoutPosition = -1 (NO_POSITION) при удалении/анимации item →
                // getItem(-1) роняет IndexOutOfBoundsException. Гейтим по границам.
                val position = layoutPosition
                if (position in 0 until getItemCount()) {
                    itemClickListener?.onItemClick(getItem(position))
                }
            }
            binding.root.setOnLongClickListener {
                val position = layoutPosition
                if (position < 0 || position >= getItemCount()) {
                    false
                } else itemClickListener?.let { listener ->
                    listener.onItemLongClick(getItem(position))
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
            // Индикатор новых сообщений (паритет с Избранным): жирный заголовок при непрочитанном,
            // точка/счётчик гейтятся настройкой showDot. Заголовок обязательно переустанавливаем в
            // обеих ветках — ViewHolder переиспользуется.
            binding.itemTitle.setTypeface(
                    Typeface.DEFAULT,
                    if (item.isUnread) Typeface.BOLD else Typeface.NORMAL,
            )
            if (!showDot || !item.isUnread) {
                binding.historyUnreadDotFrame.visibility = View.GONE
            } else {
                binding.historyUnreadDotFrame.visibility = View.VISIBLE
                binding.historyUnreadDot.text = if (item.unreadCount > 1) item.unreadCount.toString() else ""
            }
        }
    }
}
