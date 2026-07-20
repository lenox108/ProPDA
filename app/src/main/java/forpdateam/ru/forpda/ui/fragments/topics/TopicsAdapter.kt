package forpdateam.ru.forpda.ui.fragments.topics

import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.View
import timber.log.Timber
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import forpdateam.ru.forpda.R
import forpdateam.ru.forpda.common.getColorFromAttr
import forpdateam.ru.forpda.common.Utils
import forpdateam.ru.forpda.databinding.TopicItemAnnounceBinding
import forpdateam.ru.forpda.databinding.TopicItemBinding
import forpdateam.ru.forpda.databinding.TopicItemPaginationFooterBinding
import forpdateam.ru.forpda.databinding.TopicItemSectionBinding
import forpdateam.ru.forpda.entity.remote.topics.TopicItem
import forpdateam.ru.forpda.ui.applyUiDensityPadding
import forpdateam.ru.forpda.ui.applyListRowPlate
import forpdateam.ru.forpda.ui.currentUiDensityValues
import forpdateam.ru.forpda.ui.listPlateSegment
import forpdateam.ru.forpda.ui.setTextSizePx
import forpdateam.ru.forpda.ui.views.adapters.BaseSectionedAdapter
import forpdateam.ru.forpda.ui.views.adapters.BaseSectionedViewHolder

class TopicsAdapter : BaseSectionedAdapter<TopicItem, BaseSectionedViewHolder<TopicItem>>() {

    companion object {
        private const val VIEW_TYPE_ANNOUNCE = 0
        private const val VIEW_TYPE_PAGINATION_FOOTER = 1
        private const val VIEW_TYPE_EMPTY_FOOTER = 2
    }

    private var titleColorNew: Int = 0
    private var titleColor: Int = 0
    var paginationFooterBinder: ((ViewGroup) -> Unit)? = null

    init {
        setHasStableIds(true)
        shouldShowFooters(true)
    }

    /**
     * [com.afollestad.sectionedrecyclerview.SectionedRecyclerViewAdapter] по умолчанию делает
     * `getItemId(section, relative) = super.getItemId(relative)` — **без секции**.
     * При [setHasStableIds] это даёт одинаковые id у строк с одинаковым индексом в разных секциях
     * (закреплённые / объявления / форум / темы) → RecyclerView переиспользует holder с чужой
     * моделью: дубли заголовков и открытие «не той» темы по клику.
     */
    override fun getItemId(section: Int, relativePosition: Int): Long {
        val item = getItem(section, relativePosition)
        val topicId = item.id.toLong() and 0xffffffffL
        val sec = section.toLong() and 0xffffL
        val rel = relativePosition.toLong() and 0xffffL
        return (sec shl 48) or (rel shl 32) or topicId
    }

    fun setOnItemClickListener(listener: BaseSectionedAdapter.OnItemClickListener<TopicItem>) {
        itemClickListener = listener
    }

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)
        titleColor = recyclerView.context.getColorFromAttr(com.google.android.material.R.attr.colorOnSurfaceVariant)
        titleColorNew = recyclerView.context.getColorFromAttr(com.google.android.material.R.attr.colorOnSurface)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BaseSectionedViewHolder<TopicItem> {
        return when (viewType) {
            VIEW_TYPE_HEADER -> HeaderHolder(
                TopicItemSectionBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            )
            VIEW_TYPE_ITEM -> ItemHolder(
                TopicItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            )
            VIEW_TYPE_ANNOUNCE -> AnnounceHolder(
                TopicItemAnnounceBinding.inflate(LayoutInflater.from(parent.context), parent, false)
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

    override fun getItemViewType(section: Int, relativePosition: Int, absolutePosition: Int): Int {
        val item = getItem(section, relativePosition)
        return if (item?.isAnnounce == true || item?.isForum == true) {
            VIEW_TYPE_ANNOUNCE
        } else {
            super.getItemViewType(section, relativePosition, absolutePosition)
        }
    }

    override fun onBindHeaderViewHolder(holder: BaseSectionedViewHolder<TopicItem>, section: Int, expanded: Boolean) {
        holder.bind(section)
    }

    override fun onBindFooterViewHolder(holder: BaseSectionedViewHolder<TopicItem>, section: Int) {
        holder.bind(section)
    }

    override fun onBindViewHolder(holder: BaseSectionedViewHolder<TopicItem>, section: Int, relativePosition: Int, absolutePosition: Int) {
        val item = getItem(section, relativePosition) ?: return
        val viewType = getItemViewType(section, relativePosition, absolutePosition)
        when (viewType) {
            VIEW_TYPE_ANNOUNCE -> (holder as? AnnounceHolder)?.bind(item, section, relativePosition, absolutePosition)
            else -> (holder as? ItemHolder)?.bind(item, section, relativePosition, absolutePosition)
        }
    }

    private inner class HeaderHolder(
        private val binding: TopicItemSectionBinding
    ) : BaseSectionedViewHolder<TopicItem>(binding.root) {

        override fun bind(section: Int) {
            val density = binding.root.context.currentUiDensityValues()
            binding.topicItemTopDivider?.visibility = if (section == 0) View.GONE else View.VISIBLE
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
    ) : BaseSectionedViewHolder<TopicItem>(binding.root) {

        override fun bind(section: Int) {
            if (binding.topicPaginationFooterContainer.childCount == 0) {
                paginationFooterBinder?.invoke(binding.topicPaginationFooterContainer)
            }
        }
    }

    private inner class EmptyFooterHolder(view: View) : BaseSectionedViewHolder<TopicItem>(view) {
        init {
            view.layoutParams = RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    0
            )
        }
    }

    private inner class AnnounceHolder(
        private val binding: TopicItemAnnounceBinding
    ) : BaseSectionedViewHolder<TopicItem>(binding.root), View.OnClickListener, View.OnLongClickListener {

        init {
            binding.root.setOnClickListener(this)
            binding.root.setOnLongClickListener(this)
        }

        override fun bind(item: TopicItem, section: Int, relativePosition: Int, absolutePosition: Int) {
            val res = binding.root.resources
            val inset = res.getDimensionPixelSize(R.dimen.list_plate_horizontal_inset)
            val gap = res.getDimensionPixelSize(R.dimen.list_plate_group_gap_vertical)
            val count = getItemCount(section)
            val segment = listPlateSegment(relativePosition > 0, relativePosition < count - 1)
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

            binding.topicItemTitle.text = item.title
        }

        override fun onClick(view: View) {
            itemClickListener?.let { listener ->
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    getItem(position)?.let { item ->
                        listener.onItemClick(item)
                    }
                }
            }
        }

        override fun onLongClick(view: View): Boolean {
            return itemClickListener?.let { listener ->
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    getItem(position)?.let { item ->
                        listener.onItemLongClick(item)
                        true
                    } ?: false
                } else false
            } ?: false
        }
    }

    private inner class ItemHolder(
        private val binding: TopicItemBinding
    ) : BaseSectionedViewHolder<TopicItem>(binding.root), View.OnClickListener, View.OnLongClickListener {

        init {
            binding.root.setOnClickListener(this)
            binding.root.setOnLongClickListener(this)
        }

        override fun bind(item: TopicItem, section: Int, relativePosition: Int, absolutePosition: Int) {
            val res = binding.root.resources
            val inset = res.getDimensionPixelSize(R.dimen.list_plate_horizontal_inset)
            val gap = res.getDimensionPixelSize(R.dimen.list_plate_group_gap_vertical)
            val count = getItemCount(section)
            val segment = listPlateSegment(relativePosition > 0, relativePosition < count - 1)
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

            binding.topicItemTitle.text = item.title
            binding.topicItemTitle.typeface = if (item.isNew) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            binding.topicItemTitle.setTextColor(if (item.isNew) titleColorNew else titleColor)

            // Show description if present
            if (item.desc.isNullOrBlank()) {
                binding.topicItemDesc.visibility = View.GONE
            } else {
                binding.topicItemDesc.text = item.desc
                binding.topicItemDesc.visibility = View.VISIBLE
            }

            binding.topicItemLockIcon.visibility = if (item.isClosed) View.VISIBLE else View.GONE
            binding.topicItemPollIcon.visibility = if (item.isPoll) View.VISIBLE else View.GONE
            val metadataText = TopicListMetadataFormatter.format(item.lastUserNick, item.authorNick, item.pages)
            binding.topicItemLastNick.text = metadataText

            val dateText = Utils.formatForumDisplayDateTime(item.date, "topics.lastPost").orEmpty()
            binding.topicItemDate.text = dateText

            val statusText = buildString {
                if (item.isNew) append(binding.root.context.getString(R.string.topic_unread_status))
                if (item.isClosed) append(binding.root.context.getString(R.string.topic_closed_status))
                if (item.isPoll) append(binding.root.context.getString(R.string.topic_poll_status))
                if (metadataText.isNotBlank()) append(metadataText).append(". ")
            }
            // Форматирование contentDescription не должно ронять раскладку списка:
            // это лишь accessibility-текст. У пользователя прилетал
            // UnknownFormatConversionException 'U' (String.format в Resources.getString)
            // при биндинге строки тем — падал весь onLayout. Деградируем до заголовка.
            binding.root.contentDescription = runCatching {
                binding.root.context.getString(
                        R.string.topic_row_desc,
                        item.title.orEmpty(),
                        statusText,
                        item.desc?.takeIf { it.isNotBlank() }?.let { "$it. " }.orEmpty(),
                        item.lastUserNick.orEmpty(),
                        dateText
                )
            }.getOrElse { e ->
                // Диагностика: источник UnknownFormatConversionException 'U' статически не
                // найден (ресурс topic_row_desc чист, форум-контент идёт аргументами).
                // Логируем сырые поля — если повторится на логируемой сборке, увидим виновника.
                Timber.w(e, "topic_row_desc format failed; title=%s status=%s desc=%s nick=%s date=%s",
                        item.title, statusText, item.desc, item.lastUserNick, dateText)
                item.title.orEmpty()
            }
        }

        override fun onClick(view: View) {
            itemClickListener?.let { listener ->
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    getItem(position)?.let { item ->
                        listener.onItemClick(item)
                    }
                }
            }
        }

        override fun onLongClick(view: View): Boolean {
            return itemClickListener?.let { listener ->
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    getItem(position)?.let { item ->
                        listener.onItemLongClick(item)
                        true
                    } ?: false
                } else false
            } ?: false
        }
    }
}
