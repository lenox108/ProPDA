package forpdateam.ru.forpda.ui.fragments.other

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.hannesdorfmann.adapterdelegates4.AdapterDelegate
import forpdateam.ru.forpda.databinding.ItemOtherMenuDropZoneBinding
import forpdateam.ru.forpda.ui.views.drawers.adapters.ListItem
import forpdateam.ru.forpda.ui.views.drawers.adapters.OtherMenuDropZoneListItem

class OtherMenuDropZoneDelegate : AdapterDelegate<MutableList<ListItem>>() {

    override fun isForViewType(items: MutableList<ListItem>, position: Int): Boolean =
            items[position] is OtherMenuDropZoneListItem

    override fun onCreateViewHolder(parent: ViewGroup): RecyclerView.ViewHolder {
        val binding = ItemOtherMenuDropZoneBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(
            items: MutableList<ListItem>,
            position: Int,
            holder: RecyclerView.ViewHolder,
            payloads: MutableList<Any>
    ) = Unit

    private class ViewHolder(binding: ItemOtherMenuDropZoneBinding) : RecyclerView.ViewHolder(binding.root)
}
