package forpdateam.ru.forpda.ui.views.drawers.adapters

import android.graphics.PorterDuff
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.widget.TextViewCompat
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import androidx.recyclerview.widget.RecyclerView
import com.hannesdorfmann.adapterdelegates4.AdapterDelegate
import forpdateam.ru.forpda.R
import forpdateam.ru.forpda.common.getColorFromAttr
import forpdateam.ru.forpda.databinding.ItemBottomTabBinding

class BottomMenuDelegate(private val clickListener: Listener) : AdapterDelegate<MutableList<ListItem>>() {

    override fun isForViewType(items: MutableList<ListItem>, position: Int): Boolean = items[position] is BottomTabListItem

    override fun onBindViewHolder(items: MutableList<ListItem>, position: Int, holder: RecyclerView.ViewHolder, payloads: MutableList<Any>) {
        val item = items[position] as BottomTabListItem
        val animate = payloads.contains(BottomMenuAdapter.PAYLOAD_ANIMATE_SELECT)
        (holder as ViewHolder).bind(item.item, item.selected, animate)
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

        fun bind(item: DrawerMenuItem, selected: Boolean, animate: Boolean = false) {
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

                binding.tabActiveBackground.apply {
                    visibility = if (selected) View.VISIBLE else View.GONE
                    animate().cancel()
                    if (selected && animate) {
                        // M3 NavigationBar: индикатор «раскрывается» по горизонтали + лёгкий fade.
                        scaleX = 0.35f
                        alpha = 0f
                        animate()
                                .scaleX(1f)
                                .alpha(1f)
                                .setDuration(220L)
                                .setInterpolator(FastOutSlowInInterpolator())
                                .withEndAction {
                                    scaleX = 1f
                                    alpha = 1f
                                }
                                .start()
                    } else {
                        scaleX = 1f
                        alpha = 1f
                    }
                }
                if (selected && animate) {
                    // Небольшой «поп» иконки в такт раскрытию таблетки.
                    binding.itemBottomMenuIcon.apply {
                        animate().cancel()
                        scaleX = 0.8f
                        scaleY = 0.8f
                        animate()
                                .scaleX(1f)
                                .scaleY(1f)
                                .setDuration(220L)
                                .setInterpolator(FastOutSlowInInterpolator())
                                .withEndAction {
                                    scaleX = 1f
                                    scaleY = 1f
                                }
                                .start()
                    }
                } else {
                    binding.itemBottomMenuIcon.apply {
                        animate().cancel()
                        scaleX = 1f
                        scaleY = 1f
                    }
                }
                val inactiveColor = context.getColorFromAttr(com.google.android.material.R.attr.colorOnSurfaceVariant)
                // M3 NavigationBar: активная иконка лежит на «таблетке» colorSecondaryContainer,
                // поэтому её тон — colorOnSecondaryContainer (не colorOnSurface).
                val iconColor = if (selected) {
                    context.getColorFromAttr(com.google.android.material.R.attr.colorOnSecondaryContainer)
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
