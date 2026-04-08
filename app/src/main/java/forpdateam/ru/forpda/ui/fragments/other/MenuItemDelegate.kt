package forpdateam.ru.forpda.ui.fragments.other

import androidx.appcompat.content.res.AppCompatResources
import androidx.recyclerview.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.hannesdorfmann.adapterdelegates3.AdapterDelegate
import forpdateam.ru.forpda.R
import forpdateam.ru.forpda.databinding.ItemOtherMenuBinding
import forpdateam.ru.forpda.ui.views.drawers.adapters.DrawerMenuItem
import forpdateam.ru.forpda.ui.views.drawers.adapters.ListItem
import forpdateam.ru.forpda.ui.views.drawers.adapters.MenuListItem

class MenuItemDelegate(
        private val clickListener: (DrawerMenuItem) -> Unit
) : AdapterDelegate<MutableList<ListItem>>() {

    override fun isForViewType(items: MutableList<ListItem>, position: Int): Boolean
            = items[position] is MenuListItem

    override fun onBindViewHolder(items: MutableList<ListItem>, position: Int, holder: RecyclerView.ViewHolder, payloads: MutableList<Any>) {
        val item = items[position] as MenuListItem
        (holder as ViewHolder).bind(item.menuItem)
    }

    override fun onCreateViewHolder(parent: ViewGroup): RecyclerView.ViewHolder {
        val binding = ItemOtherMenuBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding, clickListener)
    }

    class ViewHolder(
            private val binding: ItemOtherMenuBinding,
            val clickListener: (DrawerMenuItem) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        private lateinit var currentItem: DrawerMenuItem

        init {
            binding.root.setOnClickListener { clickListener(currentItem) }
        }

        fun getItem() = currentItem

        fun bind(item: DrawerMenuItem) {
            this.currentItem = item
            binding.otherMenuTitle.setText(item.title)
            binding.otherMenuIcon.setImageDrawable(AppCompatResources.getDrawable(binding.root.context, item.icon))
            binding.otherMenuCounter.text = item.appItem.count.toString()
            binding.otherMenuCounter.visibility = if (item.appItem.count > 0) View.VISIBLE else View.GONE
        }
    }
}
