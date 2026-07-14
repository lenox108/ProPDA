package forpdateam.ru.forpda.ui.fragments.other

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import androidx.core.view.updateLayoutParams
import com.hannesdorfmann.adapterdelegates4.AdapterDelegate
import forpdateam.ru.forpda.R
import forpdateam.ru.forpda.databinding.ItemOtherMenuAddTileBinding
import forpdateam.ru.forpda.ui.dp4
import forpdateam.ru.forpda.ui.views.drawers.adapters.ListItem
import forpdateam.ru.forpda.ui.views.drawers.adapters.OtherMenuAddTileListItem
import forpdateam.ru.forpda.ui.views.drawers.adapters.OtherMenuSection

class OtherMenuAddTileDelegate(
        private val clickListener: (OtherMenuSection) -> Unit
) : AdapterDelegate<MutableList<ListItem>>() {

    override fun isForViewType(items: MutableList<ListItem>, position: Int): Boolean =
            items[position] is OtherMenuAddTileListItem

    override fun onCreateViewHolder(parent: ViewGroup): RecyclerView.ViewHolder {
        val binding = ItemOtherMenuAddTileBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding, clickListener)
    }

    override fun onBindViewHolder(
            items: MutableList<ListItem>,
            position: Int,
            holder: RecyclerView.ViewHolder,
            payloads: MutableList<Any>
    ) {
        (holder as ViewHolder).bind(items[position] as OtherMenuAddTileListItem)
    }

    private class ViewHolder(
            private val binding: ItemOtherMenuAddTileBinding,
            clickListener: (OtherMenuSection) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        private var section: OtherMenuSection = OtherMenuSection.QUICK

        init {
            binding.root.setOnClickListener { clickListener(section) }
        }

        fun bind(item: OtherMenuAddTileListItem) {
            section = item.section
            // Те же вертикальные отступы, что у обычных плиток (горизонтальные даёт декорация).
            binding.root.updateLayoutParams<RecyclerView.LayoutParams> {
                height = binding.root.resources.getDimensionPixelSize(R.dimen.other_menu_tile_height)
                topMargin = binding.root.dp4
                bottomMargin = binding.root.dp4
            }
        }
    }
}
