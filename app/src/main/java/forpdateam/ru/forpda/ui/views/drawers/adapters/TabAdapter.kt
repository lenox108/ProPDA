package forpdateam.ru.forpda.ui.views.drawers.adapters

import timber.log.Timber
import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import forpdateam.ru.forpda.BuildConfig
import forpdateam.ru.forpda.R
import forpdateam.ru.forpda.common.getColorFromAttr
import forpdateam.ru.forpda.ui.applyListRowPlate
import forpdateam.ru.forpda.ui.fragments.TabFragment
import forpdateam.ru.forpda.ui.listPlateSegment
import forpdateam.ru.forpda.ui.views.adapters.BaseAdapter
import forpdateam.ru.forpda.ui.views.adapters.BaseViewHolder
import forpdateam.ru.forpda.databinding.DrawerTabItemBinding

/**
 * Адаптер для вкладок в drawer.
 */
class TabAdapter : BaseAdapter<TabFragment, TabAdapter.TabHolder>() {

    private var itemClickListener: OnItemClickListener<TabFragment>? = null
    private var closeClickListener: OnItemClickListener<TabFragment>? = null
    private var currentFragmentTag: String? = null

    fun setItemClickListener(listener: OnItemClickListener<TabFragment>?) {
        this.itemClickListener = listener
    }

    fun setCloseClickListener(listener: OnItemClickListener<TabFragment>?) {
        this.closeClickListener = listener
    }

    fun submitTabs(tabs: Collection<TabFragment>, currentTag: String?) {
        currentFragmentTag = currentTag
        items.clear()
        items.addAll(tabs)
        notifyDataSetChanged()
    }

    fun removeAt(index: Int) {
        items.removeAt(index)
        notifyItemRemoved(index)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TabHolder {
        val binding = DrawerTabItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return TabHolder(binding)
    }

    override fun onBindViewHolder(holder: TabHolder, position: Int) {
        holder.bind(getItem(position), position)
    }

    inner class TabHolder(private val binding: DrawerTabItemBinding) : BaseViewHolder<TabFragment>(binding.root), View.OnClickListener {
        private var currentItem: TabFragment? = null

        init {
            binding.root.setOnClickListener(this)
            binding.drawerItemClose.setOnClickListener {
                currentItem?.let { item ->
                    closeClickListener?.onItemClick(item)
                }
            }
        }

        override fun bind(item: TabFragment, section: Int) {
            currentItem = item
            val isActive = item.tag?.equals(currentFragmentTag) == true

            if (BuildConfig.DEBUG) {
                Timber.d("bind $item active=$isActive pos=$section")
            }

            val res = binding.root.resources
            val inset = res.getDimensionPixelSize(R.dimen.list_plate_horizontal_inset)
            val count = itemCount
            val segment = listPlateSegment(section > 0, section < count - 1)
            binding.root.applyListRowPlate(
                    segment,
                    inset,
                    gapBeforeGroupPx = 0,
                    gapAfterGroupPx = 0,
                    ensureSelectableForeground = true,
            )

            val ctx = binding.root.context
            val onSurface = ctx.getColorFromAttr(com.google.android.material.R.attr.colorOnSurface)
            val accent = ctx.getColorFromAttr(com.google.android.material.R.attr.colorSecondary)

            binding.drawerItemTitle.apply {
                text = item.getTabTitle()
                setTextColor(if (isActive) accent else onSurface)
                typeface = Typeface.DEFAULT_BOLD
            }
        }

        override fun onClick(view: View) {
            currentItem?.let { item ->
                itemClickListener?.onItemClick(item)
            }
        }
    }
}
