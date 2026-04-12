package forpdateam.ru.forpda.ui.fragments.other

import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.hannesdorfmann.adapterdelegates3.AdapterDelegate
import forpdateam.ru.forpda.R
import forpdateam.ru.forpda.databinding.ItemOtherMenuBinding
import forpdateam.ru.forpda.model.interactors.other.MenuRepository
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
            binding.otherMenuIcon.setImageDrawable(AppCompatResources.getDrawable(binding.root.context, item.icon))
            val ctx = binding.root.context
            val count = item.appItem.count
            if (item.appItem.id == MenuRepository.item_mentions && count > 0) {
                val title = ctx.getString(item.title)
                val full = "$title ($count)"
                val ss = SpannableString(full)
                val red = ContextCompat.getColor(ctx, R.color.md_red_400)
                ss.setSpan(ForegroundColorSpan(red), title.length, full.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                binding.otherMenuTitle.text = ss
                binding.otherMenuTitle.contentDescription = full
                binding.otherMenuCounter.visibility = View.GONE
            } else {
                binding.otherMenuTitle.setText(item.title)
                binding.otherMenuTitle.contentDescription = ctx.getString(item.title)
                binding.otherMenuCounter.text = count.toString()
                binding.otherMenuCounter.visibility = if (count > 0) View.VISIBLE else View.GONE
            }
        }
    }
}
