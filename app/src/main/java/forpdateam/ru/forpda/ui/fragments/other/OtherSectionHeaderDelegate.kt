package forpdateam.ru.forpda.ui.fragments.other

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.hannesdorfmann.adapterdelegates4.AdapterDelegate
import forpdateam.ru.forpda.R
import forpdateam.ru.forpda.databinding.ItemOtherSectionHeaderBinding
import forpdateam.ru.forpda.ui.views.drawers.adapters.ListItem
import forpdateam.ru.forpda.ui.views.drawers.adapters.OtherMenuSection
import forpdateam.ru.forpda.ui.views.drawers.adapters.OtherMenuSectionListItem

class OtherSectionHeaderDelegate : AdapterDelegate<MutableList<ListItem>>() {

    override fun isForViewType(items: MutableList<ListItem>, position: Int): Boolean =
            items[position] is OtherMenuSectionListItem

    override fun onCreateViewHolder(parent: ViewGroup): RecyclerView.ViewHolder {
        val binding = ItemOtherSectionHeaderBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(items: MutableList<ListItem>, position: Int, holder: RecyclerView.ViewHolder, payloads: MutableList<Any>) {
        val item = items[position] as OtherMenuSectionListItem
        (holder as ViewHolder).bind(item.section)
    }

    private class ViewHolder(
            private val binding: ItemOtherSectionHeaderBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(section: OtherMenuSection) {
            binding.otherSectionTitle.setText(
                    when (section) {
                        OtherMenuSection.QUICK -> R.string.other_menu_section_quick
                        OtherMenuSection.PERSONAL -> R.string.other_menu_section_personal
                        OtherMenuSection.TOOLS -> R.string.other_menu_section_tools
                        OtherMenuSection.LEGACY -> R.string.undefined
                    }
            )
        }
    }
}
