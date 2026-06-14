package forpdateam.ru.forpda.ui.views.drawers.adapters

import android.graphics.PorterDuff
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.widget.TextViewCompat
import androidx.recyclerview.widget.RecyclerView
import com.hannesdorfmann.adapterdelegates4.AdapterDelegate
import forpdateam.ru.forpda.R
import forpdateam.ru.forpda.common.getColorFromAttr
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
                val title = context.getString(item.title)
                contentDescription = if (item.appItem.count > 0) {
                    context.getString(R.string.bottom_nav_item_with_count, title, item.appItem.count)
                } else {
                    title
                }
                isSelected = selected
                ViewCompat.setStateDescription(
                        this,
                        context.getString(if (selected) R.string.bottom_nav_state_selected else R.string.bottom_nav_state_not_selected)
                )
                binding.itemBottomMenuIcon.setImageDrawable(ContextCompat.getDrawable(context, item.icon))

                binding.tabActiveBackground.visibility = if (selected) View.VISIBLE else View.GONE
                val inactiveColor = context.getColorFromAttr(R.attr.second_text_color)
                val iconColor = if (selected) {
                    context.getColorFromAttr(R.attr.default_text_color)
                } else {
                    inactiveColor
                }
                binding.itemBottomMenuIcon.setColorFilter(iconColor, PorterDuff.Mode.SRC_ATOP)

                // Обновляем счётчик
                binding.itemBottomMenuCounter.visibility = if (item.appItem.count > 0) {
                    TextViewCompat.setAutoSizeTextTypeWithDefaults(binding.itemBottomMenuCounter, TextViewCompat.AUTO_SIZE_TEXT_TYPE_NONE)
                    binding.itemBottomMenuCounter.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.0f)
                    binding.itemBottomMenuCounter.text = item.appItem.count.toString()
                    binding.itemBottomMenuCounter.contentDescription = null
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
