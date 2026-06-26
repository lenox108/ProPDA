package forpdateam.ru.forpda.ui.fragments.favorites

import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import forpdateam.ru.forpda.BuildConfig
import forpdateam.ru.forpda.diagnostic.FavoritesUnreadTrace
import forpdateam.ru.forpda.R
import forpdateam.ru.forpda.common.Utils
import forpdateam.ru.forpda.common.getColorFromAttr
import forpdateam.ru.forpda.databinding.TopicItemBinding
import forpdateam.ru.forpda.databinding.TopicItemPaginationFooterBinding
import forpdateam.ru.forpda.databinding.TopicItemSectionBinding
import forpdateam.ru.forpda.entity.remote.favorites.FavItem
import forpdateam.ru.forpda.entity.remote.favorites.FavoriteReadState
import forpdateam.ru.forpda.model.data.remote.api.favorites.Sorting
import forpdateam.ru.forpda.ui.applyUiDensityPadding
import forpdateam.ru.forpda.ui.applyListRowPlate
import forpdateam.ru.forpda.ui.currentUiDensityValues
import forpdateam.ru.forpda.ui.fragments.topics.TopicListMetadataFormatter
import forpdateam.ru.forpda.ui.listPlateSegment
import forpdateam.ru.forpda.ui.setTextSizePx
import forpdateam.ru.forpda.ui.views.adapters.BaseSectionedAdapter
import forpdateam.ru.forpda.ui.views.adapters.BaseSectionedViewHolder
import timber.log.Timber

class FavoritesAdapter : BaseSectionedAdapter<FavItem, BaseSectionedViewHolder<FavItem>>() {

    companion object {
        private const val VIEW_TYPE_PAGINATION_FOOTER = 1
        private const val VIEW_TYPE_EMPTY_FOOTER = 2
        // Дебаунс для сеттеров prefs (sorting, unread-top, showDot, showUnreadIndicators).
        // Без него каждый чих пользователя в настройках пересоздаёт snapshot и заново
        // сортирует весь список, что на 100+ избранных видно как jank и жрёт батарею.
        private const val PREFS_REBIND_DEBOUNCE_MS = 100L

        internal fun itemIdentity(item: FavItem): Long {
            val type = if (item.isForum) 1L else 0L
            val primaryId = when {
                item.isForum && item.forumId > 0 -> item.forumId
                !item.isForum && item.topicId > 0 -> item.topicId
                item.favId > 0 -> item.favId
                else -> 0
            }.toLong() and 0x7fffffffL
            val favId = item.favId.toLong() and 0x7fffffffL
            return (type shl 62) or (primaryId shl 31) or favId
        }

        internal fun itemContentKey(item: FavItem): FavItemContentKey = FavItemContentKey(
                favId = item.favId,
                topicId = item.topicId,
                forumId = item.forumId,
                topicTitle = item.topicTitle,
                authorUserNick = item.authorUserNick,
                lastUserNick = item.lastUserNick,
                date = item.date,
                displayDateOverride = item.displayDateOverride,
                isPin = item.isPin,
                isForum = item.isForum,
                isNew = item.isNew,
                readState = item.readState,
                isPoll = item.isPoll,
                isClosed = item.isClosed,
                unreadPostCount = item.unreadPostCount,
                pages = item.pages
        )
    }

    internal data class FavItemContentKey(
            val favId: Int,
            val topicId: Int,
            val forumId: Int,
            val topicTitle: String?,
            val authorUserNick: String?,
            val lastUserNick: String?,
            val date: String?,
            val displayDateOverride: String?,
            val isPin: Boolean,
            val isForum: Boolean,
            val isNew: Boolean,
            val readState: FavoriteReadState,
            val isPoll: Boolean,
            val isClosed: Boolean,
            val unreadPostCount: Int,
            val pages: Int
    )

    private data class SectionShapeKey(
            val title: String,
            val itemIds: List<Long>
    )

    private var showDot = false
    private var showUnreadIndicators = true
    private var unreadTop = false
    private var sorting: Sorting = Sorting()
    private var titleColorNew: Int = 0
    private var titleColor: Int = 0
    private var titleUnread: String = ""
    private var titlePinned: String = ""
    private var titleTopics: String = ""
    private var currentItems: List<FavItem>? = null
    private var lastSectionShapeKeys: List<SectionShapeKey> = emptyList()
    var paginationFooterBinder: ((ViewGroup) -> Unit)? = null
    private var itemDisplayListener: ((FavItem) -> Unit)? = null
    private var itemTouchDownListener: ((FavItem) -> Unit)? = null

    init {
        setHasStableIds(true)
        shouldShowFooters(true)
    }

    override fun getItemId(section: Int, relativePosition: Int): Long {
        val item = getItem(section, relativePosition)
        val sectionKey = section.toLong() and 0xffffL
        val itemKey = itemIdentity(item) and 0x0000ffffffffffffL
        return (sectionKey shl 48) or itemKey
    }

    override fun areSectionItemsTheSame(oldItem: FavItem, newItem: FavItem): Boolean {
        return itemIdentity(oldItem) == itemIdentity(newItem)
    }

    override fun areSectionContentsTheSame(oldItem: FavItem, newItem: FavItem): Boolean {
        return itemContentKey(oldItem) == itemContentKey(newItem)
    }

    fun bindItems(newItems: List<FavItem>) {
        val snapshotItems = newItems.map(::FavItem)
        if (BuildConfig.DEBUG) {
            snapshotItems.forEach { item ->
                if (!item.isForum) {
                    Timber.d(
                            "[FavUiBind] item topicId=%d favId=%d pin=%s hasLast=%s shown=%s unread=%s/%d",
                            item.topicId,
                            item.favId,
                            item.isPin,
                            !item.lastUserNick.isNullOrBlank(),
                            displayDate(item),
                            item.isNew,
                            item.unreadPostCount
                    )
                }
            }
        }
        currentItems = snapshotItems
        val itemsUnread = mutableListOf<FavItem>()
        val pinned = mutableListOf<FavItem>()
        val otherItems = mutableListOf<FavItem>()
        val splitUnread = unreadTop && sorting.key != Sorting.Companion.Key.LAST_POST

        for (item in snapshotItems) {
            if (item.isPin) {
                pinned.add(item)
            } else {
                if (splitUnread && item.isUnreadForDisplay() && !item.isForum) {
                    itemsUnread.add(item)
                } else {
                    otherItems.add(item)
                }
            }
        }

        val newSections = mutableListOf<android.util.Pair<String, List<FavItem>>>()
        if (pinned.isNotEmpty()) {
            newSections.add(android.util.Pair(titlePinned, pinned))
        }
        if (itemsUnread.isNotEmpty()) {
            newSections.add(android.util.Pair(titleUnread, itemsUnread))
        }
        if (otherItems.isNotEmpty()) {
            newSections.add(android.util.Pair(titleTopics, otherItems))
        }
        val sectionShapeKeys = newSections.map { section ->
            SectionShapeKey(section.first, section.second.map(::itemIdentity))
        }
        val forceFullRebind = lastSectionShapeKeys.isNotEmpty() && lastSectionShapeKeys != sectionShapeKeys
        lastSectionShapeKeys = sectionShapeKeys
        submitSections(newSections)
        if (forceFullRebind) {
            notifyDataSetChanged()
        }
    }

    private val prefsRebindHandler = Handler(Looper.getMainLooper())
    private val prefsRebindRunnable = Runnable {
        currentItems?.let { bindItems(it) }
    }

    fun setShowDot(showDot: Boolean) {
        this.showDot = showDot
        schedulePrefsRebind()
    }

    fun setShowUnreadIndicators(show: Boolean) {
        showUnreadIndicators = show
        schedulePrefsRebind()
    }

    fun setUnreadTop(unreadTop: Boolean) {
        this.unreadTop = unreadTop
        schedulePrefsRebind()
    }

    fun setSorting(sorting: Sorting) {
        this.sorting = Sorting(sorting.key, sorting.order)
        schedulePrefsRebind()
    }

    private fun schedulePrefsRebind() {
        prefsRebindHandler.removeCallbacks(prefsRebindRunnable)
        prefsRebindHandler.postDelayed(prefsRebindRunnable, PREFS_REBIND_DEBOUNCE_MS)
    }

    /** Cancels the debounced prefs-rebind callback so it cannot fire after the host view is gone. */
    fun release() {
        prefsRebindHandler.removeCallbacks(prefsRebindRunnable)
    }

    fun setOnItemClickListener(listener: BaseSectionedAdapter.OnItemClickListener<FavItem>) {
        itemClickListener = listener
    }

    fun setOnItemDisplayListener(listener: (FavItem) -> Unit) {
        itemDisplayListener = listener
    }

    fun setOnItemTouchDownListener(listener: (FavItem) -> Unit) {
        itemTouchDownListener = listener
    }

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)
        val context = recyclerView.context
        titleColor = context.getColorFromAttr(R.attr.second_text_color)
        titleColorNew = context.getColorFromAttr(R.attr.default_text_color)
        titleUnread = context.getString(R.string.fav_unreaded)
        titlePinned = context.getString(R.string.fav_pinned)
        titleTopics = context.getString(R.string.fav_themes)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BaseSectionedViewHolder<FavItem> {
        return when (viewType) {
            VIEW_TYPE_HEADER -> HeaderHolder(
                TopicItemSectionBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            )
            VIEW_TYPE_ITEM -> ItemHolder(
                TopicItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            )
            VIEW_TYPE_PAGINATION_FOOTER -> PaginationFooterHolder(
                TopicItemPaginationFooterBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            )
            VIEW_TYPE_EMPTY_FOOTER -> EmptyFooterHolder(View(parent.context))
            else -> throw IllegalArgumentException("Unknown view type: $viewType")
        }
    }

    override fun getFooterViewType(section: Int): Int {
        return if (section == sectionCount - 1) VIEW_TYPE_PAGINATION_FOOTER else VIEW_TYPE_EMPTY_FOOTER
    }

    override fun onBindHeaderViewHolder(vh: BaseSectionedViewHolder<FavItem>, i: Int, b: Boolean) {
        vh.bind(i)
    }

    override fun onBindFooterViewHolder(vh: BaseSectionedViewHolder<FavItem>, i: Int) {
        vh.bind(i)
    }

    override fun onBindViewHolder(vh: BaseSectionedViewHolder<FavItem>, i: Int, i1: Int, i2: Int) {
        val item = getItem(i, i1)
        (vh as? ItemHolder)?.bind(item, i, i1, i2)
    }

    private inner class HeaderHolder(
        private val binding: TopicItemSectionBinding
    ) : BaseSectionedViewHolder<FavItem>(binding.root) {

        override fun bind(section: Int) {
            val density = binding.root.context.currentUiDensityValues()
            binding.topicItemTopDivider.visibility = if (section == 0) View.GONE else View.VISIBLE
            binding.topicItemTitle.setPaddingRelative(
                    density.itemHorizontalPaddingPx,
                    density.sectionHeaderPaddingTopPx,
                    density.itemHorizontalPaddingPx,
                    density.sectionHeaderPaddingBottomPx
            )
            binding.topicItemTitle.text = sections[section].first
        }
    }

    private inner class PaginationFooterHolder(
        private val binding: TopicItemPaginationFooterBinding
    ) : BaseSectionedViewHolder<FavItem>(binding.root) {

        override fun bind(section: Int) {
            if (binding.topicPaginationFooterContainer.childCount == 0) {
                paginationFooterBinder?.invoke(binding.topicPaginationFooterContainer)
            }
        }
    }

    private inner class EmptyFooterHolder(view: View) : BaseSectionedViewHolder<FavItem>(view) {
        init {
            view.layoutParams = RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    0
            )
        }
    }

    private inner class ItemHolder(
        private val binding: TopicItemBinding
    ) : BaseSectionedViewHolder<FavItem>(binding.root), View.OnClickListener, View.OnLongClickListener {

        private var boundItem: FavItem? = null

        init {
            binding.root.setOnClickListener(this)
            binding.root.setOnLongClickListener(this)
            binding.root.setOnTouchListener { _, event ->
                if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                    boundItem?.let { itemTouchDownListener?.invoke(it) }
                }
                false
            }
        }

        override fun bind(item: FavItem, section: Int, relativePosition: Int, absolutePosition: Int) {
            boundItem = FavItem(item)
            val res = binding.root.resources
            val inset = res.getDimensionPixelSize(R.dimen.list_plate_horizontal_inset)
            val gap = res.getDimensionPixelSize(R.dimen.list_plate_group_gap_vertical)
            val count = getItemCount(section)
            val segment = listPlateSegment(relativePosition > 0, relativePosition < count - 1)
            if (BuildConfig.DEBUG) {
                Timber.d(
                        "[FavUiBind] plate section=%d rel=%d abs=%d count=%d segment=%s topicId=%d favId=%d pin=%s",
                        section,
                        relativePosition,
                        absolutePosition,
                        count,
                        segment,
                        item.topicId,
                        item.favId,
                        item.isPin
                )
            }
            binding.root.applyListRowPlate(
                    segment,
                    inset,
                    if (relativePosition == 0) gap else 0,
                    if (relativePosition == count - 1) gap else 0,
                    ensureSelectableForeground = false,
            )
            val density = binding.root.context.currentUiDensityValues()
            binding.root.applyUiDensityPadding(density)
            binding.topicItemTitle.setTextSizePx(density.titleTextSizePx)
            binding.topicItemDesc.setTextSizePx(density.subtitleTextSizePx)
            binding.topicItemLastNick.setTextSizePx(density.metadataTextSizePx)
            binding.topicItemDate.setTextSizePx(density.metadataTextSizePx)

            binding.topicItemTitle.text = item.topicTitle
            val isUnread = item.isUnreadForDisplay()
            binding.topicItemTitle.setTypeface(
                    Typeface.DEFAULT,
                    if (isUnread) Typeface.BOLD else Typeface.NORMAL,
            )
            binding.topicItemTitle.setTextColor(if (isUnread) titleColorNew else titleColor)
            when {
                !showDot || !showUnreadIndicators || !isUnread -> binding.topicItemUnreadDot.visibility = View.GONE
                item.unreadPostCount > 1 -> {
                    binding.topicItemUnreadDot.visibility = View.VISIBLE
                    binding.topicItemUnreadDot.text = item.unreadPostCount.toString()
                }
                else -> {
                    binding.topicItemUnreadDot.visibility = View.VISIBLE
                    binding.topicItemUnreadDot.text = ""
                }
            }
            FavoritesUnreadTrace.uiBound(item, showUnreadIndicators, showDot)
            binding.topicItemForumIcon.visibility = if (item.isForum) View.VISIBLE else View.GONE

            if (item.isForum) {
                binding.topicItemLockIcon.visibility = View.GONE
                binding.topicItemPollIcon.visibility = View.GONE
            } else {
                binding.topicItemLockIcon.visibility = if (item.isClosed) View.VISIBLE else View.GONE
                binding.topicItemPollIcon.visibility = if (item.isPoll) View.VISIBLE else View.GONE
            }

            val metadataNick = if (item.isForum) {
                item.lastUserNick
            } else {
                item.lastUserNick?.takeIf { it.isNotBlank() } ?: item.authorUserNick
            }
            val pages = if (item.isForum) 0 else item.pages
            val metadataText = TopicListMetadataFormatter.format(metadataNick, pages)
            binding.topicItemLastNick.text = metadataText
            binding.topicItemDate.text = displayDate(item)
            if (binding.topicItemDesc.visibility == View.VISIBLE) {
                binding.topicItemDesc.visibility = View.GONE
            }
            binding.root.contentDescription = buildString {
                append(item.topicTitle.orEmpty())
                if (metadataText.isNotBlank()) append(". ").append(metadataText)
            }
            itemDisplayListener?.invoke(item)
        }

        override fun onClick(view: View) {
            itemClickListener?.let { listener ->
                boundItem?.let { listener.onItemClick(it) }
            }
        }

        override fun onLongClick(view: View): Boolean {
            return itemClickListener?.let { listener ->
                boundItem?.let {
                    listener.onItemLongClick(it)
                    true
                } ?: false
            } ?: false
        }
    }

    private fun displayDate(item: FavItem): String {
        return Utils.formatFavoritesDisplayDateTime(item.displayDateOverride ?: item.date).orEmpty()
    }
}
