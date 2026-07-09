package forpdateam.ru.forpda.ui.fragments.search

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.updateLayoutParams
import androidx.recyclerview.widget.RecyclerView
import forpdateam.ru.forpda.common.ForPdaCoil
import forpdateam.ru.forpda.common.Utils
import forpdateam.ru.forpda.common.letterAvatarDrawable
import forpdateam.ru.forpda.databinding.NewsItemBinding
import forpdateam.ru.forpda.databinding.SearchItemBinding
import forpdateam.ru.forpda.databinding.SearchPostItemBinding
import forpdateam.ru.forpda.entity.remote.search.SearchItem
import forpdateam.ru.forpda.ui.applyUiDensityPadding
import forpdateam.ru.forpda.ui.currentUiDensityValues
import forpdateam.ru.forpda.ui.setTextSizePx
import forpdateam.ru.forpda.ui.views.adapters.BaseAdapter
import forpdateam.ru.forpda.ui.views.adapters.BaseViewHolder

class SearchAdapter : BaseAdapter<SearchItem, BaseViewHolder<SearchItem>>() {

    companion object {
        private const val TOPIC_LAYOUT = 1
        private const val NEWS_LAYOUT = 2
        private const val POST_LAYOUT = 3
    }

    private var itemClickListener: BaseAdapter.OnItemClickListener<SearchItem>? = null

    /**
     * When true, results are «поиск по сообщениям форума» — each item is a full forum post whose [body] is
     * rich HTML, rendered NATIVELY by [bodyRenderer] (replaces the legacy WebView; Фаза 7). Set by the
     * fragment from the search settings (RESULT_POSTS + RESOURCE_FORUM).
     */
    var postMode: Boolean = false

    /** Native post-body renderer (supplied by the fragment with a link handler + image-viewer callback). */
    var bodyRenderer: SearchPostBodyRenderer? = null

    fun setOnItemClickListener(listener: BaseAdapter.OnItemClickListener<SearchItem>) {
        this.itemClickListener = listener
    }

    override fun getItemViewType(position: Int): Int {
        if (postMode) return POST_LAYOUT
        val item = getItem(position)
        return if (item.imageUrl != null) NEWS_LAYOUT else TOPIC_LAYOUT
    }

    // [SearchItem] instances are re-created on every search/page, and [BaseForumPost] has no `equals`, so the
    // default reference check made DiffUtil treat every reload as a full replace (O(n·m) diff, no row reuse,
    // no animation). Identify by the stable (topicId, id) pair — topic hits carry topicId, post/news hits carry
    // id — so re-searching the same query or re-emitting cached results reuses rows instead of rebinding all.
    override fun areItemsSame(oldItem: SearchItem, newItem: SearchItem): Boolean =
            oldItem.topicId == newItem.topicId && oldItem.id == newItem.id

    override fun areContentsSame(oldItem: SearchItem, newItem: SearchItem): Boolean =
            oldItem.title == newItem.title &&
                    oldItem.desc == newItem.desc &&
                    oldItem.body == newItem.body &&
                    oldItem.nick == newItem.nick &&
                    oldItem.date == newItem.date &&
                    oldItem.avatar == newItem.avatar &&
                    oldItem.imageUrl == newItem.imageUrl &&
                    oldItem.isOnline == newItem.isOnline

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BaseViewHolder<SearchItem> {
        return when (viewType) {
            TOPIC_LAYOUT -> SearchHolder(
                SearchItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            )
            NEWS_LAYOUT -> FullHolder(
                NewsItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            )
            POST_LAYOUT -> PostHolder(
                SearchPostItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            )
            else -> throw IllegalArgumentException("Unknown view type: $viewType")
        }
    }

    override fun onBindViewHolder(holder: BaseViewHolder<SearchItem>, position: Int) {
        holder.bind(getItem(position), position)
    }

    private inner class SearchHolder(
        private val binding: SearchItemBinding
    ) : BaseViewHolder<SearchItem>(binding.root), View.OnClickListener, View.OnLongClickListener {

        init {
            binding.root.setOnClickListener(this)
            binding.root.setOnLongClickListener(this)
        }

        override fun bind(item: SearchItem, position: Int) {
            val density = binding.root.context.currentUiDensityValues()
            (binding.root.getChildAt(0) as? View)?.applyUiDensityPadding(density)
            binding.searchItemTitle.setTextSizePx(density.titleTextSizePx)
            binding.searchItemContent.setTextSizePx(density.subtitleTextSizePx)
            binding.searchItemLastNick.setTextSizePx(density.metadataTextSizePx)
            binding.searchItemDate.setTextSizePx(density.metadataTextSizePx)
            binding.searchItemTitle.text = item.title
            binding.searchItemLastNick.text = item.nick
            binding.searchItemDate.text = Utils.formatForumDisplayDateTime(item.date, "search.list").orEmpty()
            
            val contentText = item.body?.takeIf { it.isNotEmpty() }
                ?: item.desc?.takeIf { it.isNotEmpty() }
            
            if (contentText != null) {
                binding.searchItemContent.text = contentText
                binding.searchItemContent.visibility = View.VISIBLE
            } else {
                binding.searchItemContent.visibility = View.GONE
            }
        }

        override fun onClick(view: View) {
            val position = bindingAdapterPosition
            if (position != RecyclerView.NO_POSITION) {
                itemClickListener?.onItemClick(getItem(position))
            }
        }

        override fun onLongClick(view: View): Boolean {
            val position = bindingAdapterPosition
            if (position == RecyclerView.NO_POSITION) return false
            return itemClickListener?.let {
                it.onItemLongClick(getItem(position))
                true
            } ?: false
        }
    }

    private inner class FullHolder(
        private val binding: NewsItemBinding
    ) : BaseViewHolder<SearchItem>(binding.root), View.OnClickListener, View.OnLongClickListener {

        init {
            binding.root.setOnClickListener(this)
            binding.root.setOnLongClickListener(this)
            binding.articleAvatar.visibility = View.GONE
            binding.newsFullItemCommentsCount.visibility = View.GONE
        }

        override fun bind(item: SearchItem, position: Int) {
            val density = binding.root.context.currentUiDensityValues()
            val container = binding.root.getChildAt(0) as? ViewGroup
            container?.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                marginStart = density.itemHorizontalPaddingPx
                marginEnd = density.itemHorizontalPaddingPx
                topMargin = density.cardVerticalPaddingPx
                bottomMargin = density.cardVerticalPaddingPx
            }
            binding.newsFullItemTitle.setTextSizePx(density.titleTextSizePx)
            binding.newsFullItemDescription.setTextSizePx(density.subtitleTextSizePx)
            binding.newsFullItemUsername.setTextSizePx(density.metadataTextSizePx)
            binding.newsFullItemCategory.setTextSizePx(density.metadataTextSizePx)
            binding.newsFullItemDate.setTextSizePx(density.metadataTextSizePx)
            binding.newsFullItemUsername.text = item.nick
            binding.newsFullItemTitle.text = item.title
            binding.newsFullItemDescription.text = item.body
            binding.newsFullItemDate.text = item.date
            ForPdaCoil.loadInto(binding.newsFullItemCover, item.imageUrl)
        }

        override fun onClick(view: View) {
            val position = bindingAdapterPosition
            if (position != RecyclerView.NO_POSITION) {
                itemClickListener?.onItemClick(getItem(position))
            }
        }

        override fun onLongClick(view: View): Boolean {
            val position = bindingAdapterPosition
            if (position == RecyclerView.NO_POSITION) return false
            return itemClickListener?.let {
                it.onItemLongClick(getItem(position))
                true
            } ?: false
        }
    }

    /** Forum «поиск по сообщениям» result: full post rendered natively via [bodyRenderer] (no WebView). */
    private inner class PostHolder(
        private val binding: SearchPostItemBinding
    ) : BaseViewHolder<SearchItem>(binding.root), View.OnClickListener, View.OnLongClickListener {

        init {
            binding.root.setOnClickListener(this)
            binding.root.setOnLongClickListener(this)
        }

        override fun bind(item: SearchItem, position: Int) {
            binding.searchPostTopicTitle.text = item.title
            binding.searchPostTopicTitle.visibility = if (item.title.isNullOrBlank()) View.GONE else View.VISIBLE
            binding.searchPostNick.text = item.nick
            binding.searchPostDate.text =
                Utils.formatForumDisplayDateTime(item.date, "search.post").orEmpty().ifEmpty { item.date.orEmpty() }
            // loadAvatar (not loadInto) so a recycled post card never flashes the PREVIOUS author's face:
            // it resets to a letter fallback synchronously and cancels any in-flight load before enqueuing
            // (parity with the topic adapter — see [[native-avatar-recycling-wrong-face]]).
            ForPdaCoil.loadAvatar(
                binding.searchPostAvatar,
                item.avatar,
                letterAvatarDrawable(binding.searchPostAvatar.context, item.nick)
            )
            bodyRenderer?.renderInto(binding.searchPostBody, item.body)
                ?: run { binding.searchPostBody.removeAllViews() }
        }

        override fun onClick(view: View) {
            val position = bindingAdapterPosition
            if (position != RecyclerView.NO_POSITION) {
                itemClickListener?.onItemClick(getItem(position))
            }
        }

        override fun onLongClick(view: View): Boolean {
            val position = bindingAdapterPosition
            if (position == RecyclerView.NO_POSITION) return false
            return itemClickListener?.let {
                it.onItemLongClick(getItem(position))
                true
            } ?: false
        }
    }
}
