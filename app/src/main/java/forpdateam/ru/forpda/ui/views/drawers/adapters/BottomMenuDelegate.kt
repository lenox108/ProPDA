package forpdateam.ru.forpda.ui.views.drawers.adapters

import android.graphics.PorterDuff
import androidx.core.content.ContextCompat
import androidx.core.widget.TextViewCompat
import androidx.recyclerview.widget.RecyclerView
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.hannesdorfmann.adapterdelegates3.AdapterDelegate
import forpdateam.ru.forpda.App
import forpdateam.ru.forpda.R
import forpdateam.ru.forpda.databinding.ItemBottomTabBinding

class BottomMenuDelegate(private val clickListener: Listener) : AdapterDelegate<MutableList<ListItem>>() {

    override fun isForViewType(items: MutableList<ListItem>, position: Int): Boolean = items[position] is BottomTabListItem

    override fun onBindViewHolder(items: MutableList<ListItem>, position: Int, holder: RecyclerView.ViewHolder, payloads: MutableList<Any>) {
        val item = items[position] as BottomTabListItem
        (holder as ViewHolder).bind(item.item, item.selected)
    }

    override fun onCreateViewHolder(parent: ViewGroup): RecyclerView.ViewHolder {
        val binding = ItemBottomTabBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    private inner class ViewHolder(private val binding: ItemBottomTabBinding) : RecyclerView.ViewHolder(binding.root) {

        private lateinit var currentItem: DrawerMenuItem

        init {
            binding.root.setOnClickListener { clickListener.onTabClick(currentItem) }
        }

        fun bind(item: DrawerMenuItem, selected: Boolean) {
            this.currentItem = item
            binding.root.apply {
                contentDescription = context.getString(item.title)
                binding.itemBottomMenuIcon.setImageDrawable(ContextCompat.getDrawable(context, item.icon))

                val colorRes = if (selected) App.getColorFromAttr(context, R.attr.colorAccent) else App.getColorFromAttr(context, R.attr.icon_base)
                binding.itemBottomMenuIcon.setColorFilter(
                        colorRes,
                        PorterDuff.Mode.SRC_ATOP
                )

                binding.itemBottomMenuCounter.visibility = if (item.appItem.count > 0) {
                    TextViewCompat.setAutoSizeTextTypeWithDefaults(binding.itemBottomMenuCounter, TextViewCompat.AUTO_SIZE_TEXT_TYPE_NONE)
                    binding.itemBottomMenuCounter.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.0f)
                    binding.itemBottomMenuCounter.text = item.appItem.count.toString()
                    post { TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(binding.itemBottomMenuCounter, 3, 10, 1, TypedValue.COMPLEX_UNIT_SP) }
                    View.VISIBLE
                } else View.GONE
            }
        }
    }

    interface Listener {
        fun onTabClick(menu: DrawerMenuItem)
    }
}
