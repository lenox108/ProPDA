package forpdateam.ru.forpda.ui.fragments.devdb.brands

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import forpdateam.ru.forpda.R
import forpdateam.ru.forpda.databinding.BrandsItemBinding
import forpdateam.ru.forpda.databinding.BrandsItemSectionBinding
import forpdateam.ru.forpda.entity.remote.devdb.Brands
import forpdateam.ru.forpda.ui.applyListRowPlate
import forpdateam.ru.forpda.ui.listPlateSegment
import forpdateam.ru.forpda.ui.views.adapters.BaseSectionedAdapter
import forpdateam.ru.forpda.ui.views.adapters.BaseSectionedViewHolder

class BrandsAdapter : BaseSectionedAdapter<Brands.Item, BaseSectionedViewHolder<Brands.Item>>() {

    fun setOnItemClickListener(listener: BaseSectionedAdapter.OnItemClickListener<Brands.Item>) {
        itemClickListener = listener
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BaseSectionedViewHolder<Brands.Item> {
        return when (viewType) {
            VIEW_TYPE_HEADER -> {
                val binding = BrandsItemSectionBinding.inflate(
                    LayoutInflater.from(parent.context), parent, false
                )
                HeaderHolder(binding)
            }
            VIEW_TYPE_ITEM -> {
                val binding = BrandsItemBinding.inflate(
                    LayoutInflater.from(parent.context), parent, false
                )
                ItemHolder(binding)
            }
            else -> throw IllegalArgumentException("Unknown view type: $viewType")
        }
    }

    override fun onBindHeaderViewHolder(holder: BaseSectionedViewHolder<Brands.Item>, section: Int, expanded: Boolean) {
        (holder as? HeaderHolder)?.bindHeader(section)
    }

    override fun onBindViewHolder(
        holder: BaseSectionedViewHolder<Brands.Item>,
        section: Int,
        relativePosition: Int,
        absolutePosition: Int
    ) {
        val item = getItem(section, relativePosition)
        (holder as? ItemHolder)?.bind(item, section, relativePosition)
    }

    private inner class HeaderHolder(
        private val binding: BrandsItemSectionBinding
    ) : BaseSectionedViewHolder<Brands.Item>(binding.root) {

        fun bindHeader(section: Int) {
            binding.itemTopDivider.visibility = if (section == 0) View.GONE else View.VISIBLE
            binding.itemTitle.text = sections[section].first
        }
    }

    private inner class ItemHolder(
        private val binding: BrandsItemBinding
    ) : BaseSectionedViewHolder<Brands.Item>(binding.root), View.OnClickListener, View.OnLongClickListener {

        init {
            binding.root.setOnClickListener(this)
            binding.root.setOnLongClickListener(this)
        }

        fun bind(item: Brands.Item, section: Int, relativePosition: Int) {
            binding.itemTitle.text = item.title
            binding.itemCount.text = item.count.toString()

            val res = binding.root.resources
            val inset = res.getDimensionPixelSize(R.dimen.list_plate_horizontal_inset)
            val gap = res.getDimensionPixelSize(R.dimen.list_plate_group_gap_vertical)
            val countInSection = sections[section].second.size
            val segment = listPlateSegment(
                    relativePosition > 0,
                    relativePosition < countInSection - 1
            )
            val gapBefore = if (section == 0 && relativePosition == 0) gap else 0
            binding.root.applyListRowPlate(
                    segment,
                    inset,
                    gapBeforeGroupPx = gapBefore,
                    gapAfterGroupPx = gap,
                    ensureSelectableForeground = true
            )
        }

        override fun onClick(view: View) {
            itemClickListener?.let { listener ->
                getItem(layoutPosition)?.let { item ->
                    listener.onItemClick(item)
                }
            }
        }

        override fun onLongClick(view: View): Boolean {
            return itemClickListener?.let { listener ->
                getItem(layoutPosition)?.let { item ->
                    listener.onItemLongClick(item)
                }
                true
            } ?: false
        }
    }
}
