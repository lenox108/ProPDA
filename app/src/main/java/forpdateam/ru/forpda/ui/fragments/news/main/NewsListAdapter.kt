package forpdateam.ru.forpda.ui.fragments.news.main
import forpdateam.ru.forpda.BuildConfig
import timber.log.Timber

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.updateLayoutParams
import androidx.recyclerview.widget.RecyclerView
import forpdateam.ru.forpda.R
import forpdateam.ru.forpda.common.ForPdaCoil
import forpdateam.ru.forpda.ui.ListPlateSegment
import forpdateam.ru.forpda.ui.applyListRowPlate
import forpdateam.ru.forpda.ui.currentUiDensityValues
import forpdateam.ru.forpda.ui.setTextSizePx
import forpdateam.ru.forpda.databinding.ItemNewsBinding
import forpdateam.ru.forpda.databinding.NewsItemLoadMoreBinding
import forpdateam.ru.forpda.entity.remote.news.NewsItem
import forpdateam.ru.forpda.ui.views.adapters.BaseAdapter
import forpdateam.ru.forpda.ui.views.adapters.BaseViewHolder

@Suppress("UNCHECKED_CAST")
class NewsListAdapter : BaseAdapter<NewsItem, BaseViewHolder<NewsItem>>() {

    companion object {
        private const val FULL_LAYOUT = 1
        private const val LOAD_MORE_LAYOUT = 2
    }

    private var showBtn = false
    private var mItemClickListener: ItemClickListener? = null
    private var mItemDisplayListener: ((NewsItem) -> Unit)? = null

    /**
     * Колонок в сетке (1 = обычный список). При >1 горизонтальные отступы плашек
     * отдаются [NewsGridSpacingDecoration], иначе колонки получают двойной зазор.
     */
    var gridSpanCount: Int = 1

    fun setOnClickListener(onClickListener: ItemClickListener) {
        this.mItemClickListener = onClickListener
    }

    fun setOnItemDisplayListener(listener: (NewsItem) -> Unit) {
        mItemDisplayListener = listener
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BaseViewHolder<NewsItem> {
        return when (viewType) {
            FULL_LAYOUT -> FullHolder(
                ItemNewsBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            )
            LOAD_MORE_LAYOUT -> LoadMoreHolder(
                NewsItemLoadMoreBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            )
            else -> throw IllegalArgumentException("Unknown view type: $viewType")
        }
    }

    override fun onBindViewHolder(holder: BaseViewHolder<NewsItem>, position: Int) {
        when (getItemViewType(position)) {
            FULL_LAYOUT -> {
                val item = getItem(position)
                (holder as? FullHolder)?.bind(item, position)
                mItemDisplayListener?.invoke(item)
            }
            LOAD_MORE_LAYOUT -> (holder as? LoadMoreHolder)?.bind(position)
        }
    }

    override fun getItemCount(): Int = super.getItemCount() + 1

    override fun getItemViewType(position: Int): Int {
        return if (position == getItemCount() - 1) {
            LOAD_MORE_LAYOUT
        } else {
            FULL_LAYOUT
        }
    }

    fun insertMore(list: List<NewsItem>) {
        val lastSize = items.size
        items.addAll(list)
        notifyItemRangeInserted(lastSize, list.size)
        showBtn = true
    }

    fun setItemsDirect(items: Collection<NewsItem>) {
        this.items.clear()
        this.items.addAll(items)
        notifyDataSetChanged()
    }

    fun updateItems(items: List<NewsItem>) {
        for (item in items) {
            val index = this.items.indexOf(item)
            if (index != -1) {
                this.items[index] = item
            }
        }
        notifyDataSetChanged()
    }

    private inner class FullHolder(
        private val binding: ItemNewsBinding
    ) : BaseViewHolder<NewsItem>(binding.root) {

        init {
            binding.root.setOnClickListener {
                val position = bindingAdapterPosition
                if (position == RecyclerView.NO_POSITION) return@setOnClickListener
                mItemClickListener?.onItemClick(
                    binding.newsFullItemCover,
                    getItem(position),
                    position
                )
            }
            binding.root.setOnLongClickListener {
                val position = bindingAdapterPosition
                if (position == RecyclerView.NO_POSITION) return@setOnLongClickListener false
                mItemClickListener?.onLongItemClick(
                    it,
                    getItem(position),
                    position
                ) ?: false
            }
            binding.newsFullItemUsername.setOnClickListener {
                val position = bindingAdapterPosition
                if (position == RecyclerView.NO_POSITION) return@setOnClickListener
                mItemClickListener?.onNickClick(
                    binding.newsFullItemUsername,
                    getItem(position),
                    position
                )
            }
        }

        override fun bind(news: NewsItem, position: Int) {
            val res = binding.root.resources
            val inset = res.getDimensionPixelSize(R.dimen.list_plate_horizontal_inset)
            val gap = res.getDimensionPixelSize(R.dimen.list_plate_group_gap_vertical)
            binding.root.applyListRowPlate(
                    ListPlateSegment.SINGLE,
                    if (gridSpanCount > 1) 0 else inset,
                    gapBeforeGroupPx = if (position < gridSpanCount) gap else 0,
                    gapAfterGroupPx = gap,
                    ensureSelectableForeground = false,
            )
            val density = binding.root.context.currentUiDensityValues()
            val container = binding.root.getChildAt(0) as? ViewGroup
            container?.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                marginStart = density.itemHorizontalPaddingPx
                marginEnd = density.itemHorizontalPaddingPx
                topMargin = density.cardVerticalPaddingPx
                bottomMargin = density.cardVerticalPaddingPx
            }
            binding.newsFullItemTitle.setTextSizePx(
                    binding.root.resources.getDimension(R.dimen.list_news_title_text_size)
            )
            binding.newsFullItemDescription.setTextSizePx(density.subtitleTextSizePx)
            binding.newsFullItemUsername.setTextSizePx(density.metadataTextSizePx)
            binding.newsFullItemCategory.setTextSizePx(density.metadataTextSizePx)
            binding.newsFullItemCommentsCount.setTextSizePx(density.metadataTextSizePx)
            binding.newsFullItemDate.setTextSizePx(density.metadataTextSizePx)
            binding.newsFullItemUsername.text = news.author
            binding.newsFullItemTitle.text = news.title
            binding.newsFullItemDescription.text = news.description
            binding.newsFullItemCommentsCount.text = news.commentsCount.toString()
            binding.newsFullItemDate.text = news.date
            ForPdaCoil.loadInto(binding.newsFullItemCover, news.imgUrl)
            if (news.avatar == null) {
                ForPdaCoil.loadInto(binding.articleAvatar, "assets://av.png")
            } else {
                ForPdaCoil.loadInto(binding.articleAvatar, news.avatar)
            }
        }
    }

    private inner class LoadMoreHolder(
        private val binding: NewsItemLoadMoreBinding
    ) : BaseViewHolder<NewsItem>(binding.root) {

        init {
            binding.nlLmBtn.setOnClickListener {
                if (BuildConfig.DEBUG) Timber.d("NewsListAdapter: btn click")
            }
        }

        override fun bind(position: Int) {
            val res = binding.root.resources
            val inset = res.getDimensionPixelSize(R.dimen.list_plate_horizontal_inset)
            val gap = res.getDimensionPixelSize(R.dimen.list_plate_group_gap_vertical)
            binding.root.applyListRowPlate(
                    ListPlateSegment.SINGLE,
                    if (gridSpanCount > 1) 0 else inset,
                    gapBeforeGroupPx = if (position == 0) gap else 0,
                    gapAfterGroupPx = gap,
                    ensureSelectableForeground = false,
            )
            if (showBtn && binding.nlLmContainer.visibility == View.VISIBLE) {
                binding.nlLmContainer.visibility = View.GONE
                binding.nlLmBtn.visibility = View.VISIBLE
                binding.nlLmBtn.setOnClickListener {
                    if (BuildConfig.DEBUG) Timber.d("NewsListAdapter: news click")
                    binding.nlLmBtn.visibility = View.GONE
                    showBtn = false
                    binding.nlLmContainer.visibility = View.VISIBLE
                    mItemClickListener?.onLoadMoreClick()
                }
            } else {
                binding.nlLmBtn.setOnClickListener {
                    binding.nlLmBtn.visibility = View.GONE
                    binding.nlLmContainer.visibility = View.VISIBLE
                    mItemClickListener?.onLoadMoreClick()
                    if (BuildConfig.DEBUG) Timber.d("NewsListAdapter: load more click")
                }
            }
        }
    }

    interface ItemClickListener {
        fun onLongItemClick(view: View, item: NewsItem, position: Int): Boolean
        fun onItemClick(view: View, item: NewsItem, position: Int)
        fun onNickClick(view: View, item: NewsItem, position: Int)
        fun onLoadMoreClick()
    }
}
