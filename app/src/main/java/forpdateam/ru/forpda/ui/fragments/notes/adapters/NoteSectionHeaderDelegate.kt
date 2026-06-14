package forpdateam.ru.forpda.ui.fragments.notes.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.hannesdorfmann.adapterdelegates4.AdapterDelegate
import forpdateam.ru.forpda.databinding.ItemNoteSectionHeaderBinding
import forpdateam.ru.forpda.ui.views.drawers.adapters.ListItem
import forpdateam.ru.forpda.ui.views.drawers.adapters.NoteSectionHeaderListItem

class NoteSectionHeaderDelegate : AdapterDelegate<MutableList<ListItem>>() {
    override fun isForViewType(items: MutableList<ListItem>, position: Int): Boolean =
            items[position] is NoteSectionHeaderListItem

    override fun onCreateViewHolder(parent: ViewGroup): RecyclerView.ViewHolder {
        val binding = ItemNoteSectionHeaderBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(items: MutableList<ListItem>, position: Int, holder: RecyclerView.ViewHolder, payloads: MutableList<Any>) {
        val item = items[position] as NoteSectionHeaderListItem
        (holder as ViewHolder).bind(item.title)
    }

    private class ViewHolder(
            private val binding: ItemNoteSectionHeaderBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(title: String) {
            binding.itemTitle.text = title
        }
    }
}
