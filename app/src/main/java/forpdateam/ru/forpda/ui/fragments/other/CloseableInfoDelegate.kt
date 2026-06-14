package forpdateam.ru.forpda.ui.fragments.other

import androidx.recyclerview.widget.RecyclerView
import android.view.LayoutInflater
import android.view.ViewGroup
import com.hannesdorfmann.adapterdelegates4.AdapterDelegate
import forpdateam.ru.forpda.R
import forpdateam.ru.forpda.databinding.ItemCloseableInfoBinding
import forpdateam.ru.forpda.entity.app.CloseableInfo
import forpdateam.ru.forpda.model.CloseableInfoHolder
import forpdateam.ru.forpda.ui.views.drawers.adapters.CloseableInfoListItem
import forpdateam.ru.forpda.ui.views.drawers.adapters.ListItem

class CloseableInfoDelegate(
        private val clickListener: (CloseableInfo) -> Unit
) : AdapterDelegate<MutableList<ListItem>>() {

    override fun isForViewType(items: MutableList<ListItem>, position: Int): Boolean = items[position] is CloseableInfoListItem

    override fun onBindViewHolder(items: MutableList<ListItem>, position: Int, holder: RecyclerView.ViewHolder, payloads: MutableList<Any>) {
        val item = items[position] as CloseableInfoListItem
        (holder as ViewHolder).bind(item.item, items, position)
    }

    override fun onCreateViewHolder(parent: ViewGroup): RecyclerView.ViewHolder {
        val binding = ItemCloseableInfoBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding, clickListener)
    }

    class ViewHolder(
            private val binding: ItemCloseableInfoBinding,
            val closeClickListener: (CloseableInfo) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        private lateinit var currentItem: CloseableInfo

        init {
            binding.infoItemClose.setOnClickListener { closeClickListener.invoke(currentItem) }
        }

        fun bind(item: CloseableInfo, items: MutableList<ListItem>, position: Int) {
            currentItem = item
            binding.infoItemTitle.setText(getStringRes(item))
            binding.root.applyOtherMenuPlateMarginsAndBackground(
                    items,
                    position,
                    { it is CloseableInfoListItem },
                    binding.infoItemRowDivider,
            )
        }

        private fun getStringRes(item: CloseableInfo): Int = when (item.id) {
            CloseableInfoHolder.item_other_menu_drag -> R.string.closeable_info_other_menu_drag
            CloseableInfoHolder.item_notes_sync -> R.string.closeable_info_notes_sync
            else -> R.string.undefined
        }
    }
}
