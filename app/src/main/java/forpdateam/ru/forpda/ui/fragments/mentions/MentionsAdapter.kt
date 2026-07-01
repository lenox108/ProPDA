package forpdateam.ru.forpda.ui.fragments.mentions

import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import forpdateam.ru.forpda.R
import forpdateam.ru.forpda.common.Utils
import forpdateam.ru.forpda.common.getColorFromAttr
import forpdateam.ru.forpda.databinding.TopicItemBinding
import forpdateam.ru.forpda.databinding.TopicItemPaginationFooterBinding
import forpdateam.ru.forpda.entity.remote.mentions.MentionItem
import forpdateam.ru.forpda.ui.applyUiDensityPadding
import forpdateam.ru.forpda.ui.applyListRowPlate
import forpdateam.ru.forpda.ui.currentUiDensityValues
import forpdateam.ru.forpda.ui.listPlateSegment
import forpdateam.ru.forpda.ui.setTextSizePx
import forpdateam.ru.forpda.ui.views.adapters.BaseAdapter
import forpdateam.ru.forpda.ui.views.adapters.BaseViewHolder

class MentionsAdapter : BaseAdapter<MentionItem, MentionsAdapter.MentionHolder>() {

    companion object {
        private const val VIEW_TYPE_ITEM = 0
        private const val VIEW_TYPE_PAGINATION_FOOTER = 1
    }

    private var titleColorNew: Int = 0
    private var titleColor: Int = 0
    private var itemClickListener: BaseAdapter.OnItemClickListener<MentionItem>? = null
    var paginationFooterBinder: ((ViewGroup) -> Unit)? = null

    fun setOnItemClickListener(listener: BaseAdapter.OnItemClickListener<MentionItem>) {
        this.itemClickListener = listener
    }

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)
        titleColor = recyclerView.context.getColorFromAttr(com.google.android.material.R.attr.colorOnSurfaceVariant)
        titleColorNew = recyclerView.context.getColorFromAttr(com.google.android.material.R.attr.colorOnSurface)
    }

    override fun getItemViewType(position: Int): Int {
        return if (position == items.size) VIEW_TYPE_PAGINATION_FOOTER else VIEW_TYPE_ITEM
    }

    override fun getItemCount(): Int = items.size + 1

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MentionHolder {
        return when (viewType) {
            VIEW_TYPE_PAGINATION_FOOTER -> PaginationFooterHolder(
                TopicItemPaginationFooterBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            )
            else -> MentionItemHolder(
                TopicItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            )
        }
    }

    override fun onBindViewHolder(holder: MentionHolder, position: Int) {
        if (position < items.size) {
            holder.bind(getItem(position), position)
        } else if (holder is PaginationFooterHolder) {
            holder.bindFooter()
        }
    }

    abstract inner class MentionHolder(view: View) : BaseViewHolder<MentionItem>(view)

    inner class PaginationFooterHolder(
        private val binding: TopicItemPaginationFooterBinding
    ) : MentionHolder(binding.root) {

        fun bindFooter() {
            if (binding.topicPaginationFooterContainer.childCount == 0) {
                paginationFooterBinder?.invoke(binding.topicPaginationFooterContainer)
            }
        }

        override fun bind(item: MentionItem, position: Int) = Unit
    }

    inner class MentionItemHolder(
        private val binding: TopicItemBinding
    ) : MentionHolder(binding.root), View.OnClickListener, View.OnLongClickListener {

        init {
            binding.root.setOnClickListener(this)
            binding.root.setOnLongClickListener(this)
        }

        override fun bind(item: MentionItem, position: Int) {
            val res = binding.root.resources
            val inset = res.getDimensionPixelSize(R.dimen.list_plate_horizontal_inset)
            val gap = res.getDimensionPixelSize(R.dimen.list_plate_group_gap_vertical)
            val last = items.size - 1
            val segment = listPlateSegment(position > 0, position < last)
            binding.root.applyListRowPlate(
                    segment,
                    inset,
                    if (position == 0) gap else 0,
                    if (position == last) gap else 0,
                    ensureSelectableForeground = false,
            )
            val density = binding.root.context.currentUiDensityValues()
            binding.root.applyUiDensityPadding(density)
            binding.topicItemTitle.setTextSizePx(density.titleTextSizePx)
            binding.topicItemDesc.setTextSizePx(density.subtitleTextSizePx)
            binding.topicItemLastNick.setTextSizePx(density.metadataTextSizePx)
            binding.topicItemDate.setTextSizePx(density.metadataTextSizePx)

            binding.topicItemTitle.text = item.title
            binding.topicItemTitle.typeface = if (item.state == MentionItem.STATE_UNREAD) {
                Typeface.DEFAULT_BOLD
            } else {
                Typeface.DEFAULT
            }
            binding.topicItemTitle.setTextColor(
                if (item.state == MentionItem.STATE_UNREAD) titleColorNew else titleColor
            )
            binding.topicItemLastNick.text = item.nick
            binding.topicItemDate.text = Utils.formatForumDisplayDateTime(item.date, "mentions.item").orEmpty()
            if (binding.topicItemDesc.visibility == View.VISIBLE) {
                binding.topicItemDesc.visibility = View.GONE
            }
        }

        override fun onClick(view: View) {
            itemClickListener?.onItemClick(getItem(layoutPosition))
        }

        override fun onLongClick(view: View): Boolean {
            return itemClickListener?.let {
                it.onItemLongClick(getItem(layoutPosition))
                true
            } ?: false
        }
    }
}
