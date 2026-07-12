package forpdateam.ru.forpda.ui.fragments.other

import androidx.appcompat.content.res.AppCompatResources
import android.content.res.ColorStateList
import androidx.recyclerview.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.Animation
import android.view.animation.RotateAnimation
import androidx.core.view.ViewCompat
import androidx.core.view.updateLayoutParams
import com.hannesdorfmann.adapterdelegates4.AdapterDelegate
import forpdateam.ru.forpda.R
import forpdateam.ru.forpda.common.getColorFromAttr
import forpdateam.ru.forpda.databinding.ItemOtherMenuTileBinding
import forpdateam.ru.forpda.ui.getDimensionFromAttr
import forpdateam.ru.forpda.ui.dp4
import forpdateam.ru.forpda.ui.views.drawers.adapters.DrawerMenuItem
import forpdateam.ru.forpda.ui.views.drawers.adapters.ListItem
import forpdateam.ru.forpda.ui.views.drawers.adapters.MenuListItem

class MenuItemDelegate(
        private val clickListener: (DrawerMenuItem) -> Unit,
        private val longClickListener: () -> Boolean,
        private val isEditModeProvider: () -> Boolean
) : AdapterDelegate<MutableList<ListItem>>() {

    override fun isForViewType(items: MutableList<ListItem>, position: Int): Boolean
            = items[position] is MenuListItem

    override fun onBindViewHolder(items: MutableList<ListItem>, position: Int, holder: RecyclerView.ViewHolder, payloads: MutableList<Any>) {
        val item = items[position] as MenuListItem
        val vh = holder as ViewHolder
        if (payloads.any { it is OtherAdapter.Payload.MenuCountChanged }) {
            vh.bindCountOnly(item)
        } else {
            vh.bind(item)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup): RecyclerView.ViewHolder {
        val binding = ItemOtherMenuTileBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding, clickListener, longClickListener, isEditModeProvider)
    }

    class ViewHolder(
            private val binding: ItemOtherMenuTileBinding,
            private val clickListener: (DrawerMenuItem) -> Unit,
            private val longClickListener: () -> Boolean,
            private val isEditModeProvider: () -> Boolean
    ) : RecyclerView.ViewHolder(binding.root) {

        private lateinit var currentItem: DrawerMenuItem

        init {
            binding.root.setOnClickListener { clickListener(currentItem) }
            binding.root.setOnLongClickListener { longClickListener() }
        }

        fun getItem() = currentItem

        fun bind(item: MenuListItem) {
            currentItem = item.menuItem
            binding.otherMenuIcon.setImageDrawable(AppCompatResources.getDrawable(binding.root.context, item.menuItem.icon))
            applyTint()
            bindCounterAndTitle(item.menuItem)
            applyFixedGridLayoutParams()
            applyEditMode(isEditModeProvider())
        }

        fun bindCountOnly(item: MenuListItem) {
            currentItem = item.menuItem
            bindCounterAndTitle(item.menuItem)
            applyFixedGridLayoutParams()
            applyEditMode(isEditModeProvider())
        }

        private fun applyFixedGridLayoutParams() {
            val edge = binding.root.dp4
            // Horizontal spacing (16dp outer edges + 8dp inter-tile gaps) is owned by
            // MenuTileSpacingDecoration so the outer columns line up with the profile card,
            // section headers and the list plates (all at content_padding_horizontal).
            binding.root.updateLayoutParams<RecyclerView.LayoutParams> {
                width = ViewGroup.LayoutParams.MATCH_PARENT
                height = binding.root.resources.getDimensionPixelSize(R.dimen.other_menu_tile_height)
                leftMargin = 0
                rightMargin = 0
                topMargin = edge
                bottomMargin = edge
            }
        }

        private fun bindCounterAndTitle(item: DrawerMenuItem) {
            val ctx = binding.root.context
            val count = item.appItem.count
            val title = ctx.getString(item.title)
            if (count > 0) {
                binding.otherMenuCounter.text = count.toString()
                binding.otherMenuCounter.visibility = View.VISIBLE
                binding.root.contentDescription = ctx.getString(R.string.bottom_nav_item_with_count, title, count)
            } else {
                binding.otherMenuCounter.visibility = View.GONE
                binding.root.contentDescription = title
            }
            binding.otherMenuTitle.contentDescription = null
            binding.otherMenuTitle.text = title
        }

        private fun applyTint() {
            val ctx = binding.root.context
            binding.otherMenuIcon.imageTintList = ColorStateList.valueOf(ctx.getColorFromAttr(R.attr.menu_tile_icon))
        }

        private fun applyEditMode(isEditMode: Boolean) {
            binding.root.isSelected = isEditMode
            ViewCompat.setStateDescription(
                    binding.root,
                    if (isEditMode) binding.root.context.getString(R.string.other_menu_edit_mode_state) else null
            )
            binding.root.cardElevation = 0f
            binding.root.strokeWidth = if (isEditMode) {
                binding.root.resources.getDimensionPixelSize(R.dimen.dp2)
            } else {
                binding.root.context.getDimensionFromAttr(R.attr.list_plate_stroke_width).toInt()
            }
            binding.root.strokeColor = binding.root.context.getColorFromAttr(
                    if (isEditMode) R.attr.colorAccent else R.attr.list_plate_stroke_color
            )
            if (isEditMode) {
                if (binding.root.animation == null) {
                    binding.root.startAnimation(editModeAnimation())
                }
            } else {
                binding.root.clearAnimation()
                binding.root.rotation = 0f
            }
        }

        private fun editModeAnimation(): Animation =
                RotateAnimation(
                        -0.8f,
                        0.8f,
                        Animation.RELATIVE_TO_SELF,
                        0.5f,
                        Animation.RELATIVE_TO_SELF,
                        0.5f
                ).apply {
                    duration = 120L
                    repeatMode = Animation.REVERSE
                    repeatCount = Animation.INFINITE
                }
    }
}
