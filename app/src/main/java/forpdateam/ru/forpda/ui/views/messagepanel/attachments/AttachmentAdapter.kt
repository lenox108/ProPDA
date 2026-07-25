package forpdateam.ru.forpda.ui.views.messagepanel.attachments

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.Menu
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.RadioButton
import android.widget.TextView
import androidx.appcompat.widget.PopupMenu
import androidx.core.graphics.ColorUtils
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.progressindicator.CircularProgressIndicator
import forpdateam.ru.forpda.R
import forpdateam.ru.forpda.common.ForPdaCoil
import forpdateam.ru.forpda.common.getColorFromAttr
import forpdateam.ru.forpda.common.getVecDrawable
import forpdateam.ru.forpda.databinding.MessagePanelAttachmentItemBinding
import forpdateam.ru.forpda.databinding.MessagePanelAttachmentItemHorizontalBinding
import forpdateam.ru.forpda.entity.remote.editpost.AttachmentItem
import forpdateam.ru.forpda.model.data.remote.IWebClient

/**
 * Adapter for attachments that keeps list/grid presentation independent from
 * selection and attachment actions.
 */
class AttachmentAdapter(
    private val rowIdProvider: (AttachmentItem) -> Long,
) : ListAdapter<AttachmentAdapter.Row, RecyclerView.ViewHolder>(ROW_DIFF) {
    private var itemClickListener: OnItemClickListener? = null
    private var reloadOnClickListener: OnReloadClickListener? = null
    private var itemActionListener: OnItemActionListener? = null
    private var isLinear = true
    private var isReverse = false
    private var textActionsEnabled = true
    private var sourceItems: List<AttachmentItem> = emptyList()

    data class Row(
        val id: Long,
        val item: AttachmentItem,
        val signature: List<Any?>,
    )

    companion object {
        private const val TYPE_ITEM = 1
        private const val TYPE_ITEM_HORIZONTAL = 2
        private const val ACTION_SPOILER = 1
        private const val ACTION_DELETE = 2

        private val ROW_DIFF = object : DiffUtil.ItemCallback<Row>() {
            override fun areItemsTheSame(oldItem: Row, newItem: Row): Boolean =
                oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: Row, newItem: Row): Boolean =
                oldItem.signature == newItem.signature
        }
    }

    init {
        setHasStableIds(true)
    }

    fun updateIsLinear(isLinear: Boolean) {
        if (this.isLinear == isLinear) return
        this.isLinear = isLinear
        notifyDataSetChanged()
    }

    fun updateReverse(isReverse: Boolean) {
        if (this.isReverse == isReverse) return
        this.isReverse = isReverse
        submitItems(sourceItems)
    }

    fun updateTextActionsEnabled(enabled: Boolean) {
        if (textActionsEnabled == enabled) return
        textActionsEnabled = enabled
        notifyItemRangeChanged(0, itemCount)
    }

    fun updateItem(item: AttachmentItem) {
        if (item in sourceItems) submitItems(sourceItems)
    }

    fun submitItems(newItems: List<AttachmentItem>) {
        sourceItems = newItems.toList()
        val ordered = if (isReverse) sourceItems.asReversed() else sourceItems
        submitList(ordered.map(::toRow))
    }

    override fun getItemViewType(position: Int): Int =
        if (isLinear) TYPE_ITEM_HORIZONTAL else TYPE_ITEM

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            TYPE_ITEM -> {
                val binding = MessagePanelAttachmentItemBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                )
                ViewHolder(binding)
            }
            TYPE_ITEM_HORIZONTAL -> {
                val binding = MessagePanelAttachmentItemHorizontalBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                )
                ViewHolder(binding)
            }
            else -> error("Unknown attachment view type: $viewType")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = getItem(position).item
        (holder as ViewHolder).bind(item)
    }

    override fun getItemId(position: Int): Long = getItem(position).id

    override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
        (holder as? ViewHolder)?.unbind()
        super.onViewRecycled(holder)
    }

    private fun toRow(item: AttachmentItem): Row = Row(
        id = rowIdProvider(item),
        item = item,
        signature = listOf(
            item.id,
            item.name,
            item.extension,
            item.weight,
            item.typeFile,
            item.loadState,
            item.status,
            item.imageUrl,
            item.url,
            item.width,
            item.height,
            item.md5,
            item.progress,
            item.isError,
            item.errorText,
            item.selected,
            textActionsEnabled,
            isLinear,
        ),
    )

    fun setOnItemClickListener(listener: OnItemClickListener) {
        itemClickListener = listener
    }

    fun setReloadOnClickListener(listener: OnReloadClickListener) {
        reloadOnClickListener = listener
    }

    fun setOnItemActionListener(listener: OnItemActionListener) {
        itemActionListener = listener
    }

    interface OnItemClickListener {
        fun onItemClick(item: AttachmentItem)
    }

    interface OnReloadClickListener {
        fun onReloadClick(item: AttachmentItem)
    }

    interface OnItemActionListener {
        fun onInsert(item: AttachmentItem, toSpoiler: Boolean)
        fun onDelete(item: AttachmentItem)
    }

    inner class ViewHolder(private val bindingBase: Any) : RecyclerView.ViewHolder(
        if (bindingBase is MessagePanelAttachmentItemBinding) {
            bindingBase.root
        } else {
            (bindingBase as MessagePanelAttachmentItemHorizontalBinding).root
        }
    ), View.OnClickListener {
        private lateinit var imageView: ImageView
        private lateinit var radioButton: RadioButton
        private lateinit var overlay: View
        private lateinit var progressBar: CircularProgressIndicator
        private lateinit var progressValue: TextView
        private lateinit var reload: ImageButton
        private lateinit var name: TextView
        private lateinit var attributes: TextView
        private lateinit var errorText: TextView
        private lateinit var description: View
        private lateinit var insert: MaterialButton
        private lateinit var moreActions: ImageButton
        private var boundItem: AttachmentItem? = null
        private val progressListener = IWebClient.ProgressListener { percent ->
            itemView.post { updateProgress(percent) }
        }

        init {
            itemView.setOnClickListener(this)
            when (bindingBase) {
                is MessagePanelAttachmentItemBinding -> {
                    imageView = bindingBase.drawerItemIcon
                    radioButton = bindingBase.radioButton
                    overlay = bindingBase.overlayAndText
                    progressBar = bindingBase.progressBar
                    progressValue = bindingBase.progressValue
                    reload = bindingBase.reload
                    name = bindingBase.fileName
                    attributes = bindingBase.fileAttributes
                    errorText = bindingBase.errorText
                    description = bindingBase.fileDescription
                    insert = bindingBase.insertAttachment
                    moreActions = bindingBase.moreActions
                }
                is MessagePanelAttachmentItemHorizontalBinding -> {
                    imageView = bindingBase.drawerItemIcon
                    radioButton = bindingBase.radioButton
                    overlay = bindingBase.overlayAndText
                    progressBar = bindingBase.progressBar
                    progressValue = bindingBase.progressValue
                    reload = bindingBase.reload
                    name = bindingBase.fileName
                    attributes = bindingBase.fileAttributes
                    errorText = bindingBase.errorText
                    description = bindingBase.fileDescription
                    insert = bindingBase.insertAttachment
                    moreActions = bindingBase.moreActions
                }
            }

            reload.setOnClickListener {
                currentItem()?.let { item -> reloadOnClickListener?.onReloadClick(item) }
            }
            insert.setOnClickListener {
                currentItem()?.let { item -> itemActionListener?.onInsert(item, false) }
            }
            moreActions.setOnClickListener { anchor ->
                currentItem()?.let { item -> showActions(anchor, item) }
            }
        }

        @SuppressLint("SetTextI18n")
        fun bind(item: AttachmentItem) {
            boundItem?.progressListener = null
            boundItem = item

            description.visibility = View.VISIBLE
            name.visibility = View.VISIBLE
            name.text = item.name.orEmpty()
            imageView.contentDescription = item.name
            moreActions.visibility = View.VISIBLE

            when (item.loadState) {
                AttachmentItem.STATE_LOADING -> {
                    attributes.visibility = View.GONE
                    errorText.visibility = View.GONE
                    progressBar.visibility = View.VISIBLE
                    progressValue.visibility = View.VISIBLE
                    reload.visibility = View.GONE
                    imageView.visibility = View.INVISIBLE
                    insert.visibility = View.GONE
                    updateProgress(item.progress.coerceAtLeast(0))
                    item.progressListener = progressListener
                }
                AttachmentItem.STATE_NOT_LOADED -> {
                    attributes.visibility = View.GONE
                    errorText.visibility = View.VISIBLE
                    errorText.text = item.errorText
                        ?: itemView.context.getString(R.string.attachment_upload_error)
                    progressBar.visibility = View.GONE
                    progressValue.visibility = View.GONE
                    reload.visibility = View.VISIBLE
                    imageView.visibility = View.INVISIBLE
                    insert.visibility = View.GONE
                }
                AttachmentItem.STATE_LOADED -> {
                    attributes.visibility = View.VISIBLE
                    attributes.text = buildAttributes(item)
                    errorText.visibility = View.GONE
                    progressBar.visibility = View.GONE
                    progressValue.visibility = View.GONE
                    reload.visibility = View.GONE
                    imageView.visibility = View.VISIBLE
                    imageView.alpha = 1f
                    imageView.imageTintList = null
                    imageView.clearColorFilter()
                    insert.visibility = if (textActionsEnabled) View.VISIBLE else View.GONE

                    val previewUrl = item.imageUrl?.takeIf { it.isNotBlank() }
                        ?: item.url?.takeIf { it.isNotBlank() }
                    if (item.typeFile == AttachmentItem.TYPE_IMAGE && previewUrl != null) {
                        ForPdaCoil.loadInto(imageView, previewUrl)
                    } else {
                        imageView.setImageDrawable(
                            itemView.context.getVecDrawable(R.drawable.ic_insert_drive_file_gray_24dp)
                        )
                    }
                }
            }
            updateChecked(item)
        }

        private fun buildAttributes(item: AttachmentItem): String {
            return listOfNotNull(
                item.extension?.takeIf { it.isNotBlank() }?.uppercase(),
                item.weight?.takeIf { it.isNotBlank() },
                if (item.width > 0 && item.height > 0) "${item.width}×${item.height}" else null
            ).joinToString(" · ")
        }

        private fun currentItem(): AttachmentItem? {
            val position = bindingAdapterPosition
            if (position == RecyclerView.NO_POSITION) return null
            return currentList.getOrNull(position)?.item
        }

        private fun showActions(anchor: View, item: AttachmentItem) {
            PopupMenu(anchor.context, anchor).apply {
                if (textActionsEnabled && item.loadState == AttachmentItem.STATE_LOADED) {
                    menu.add(Menu.NONE, ACTION_SPOILER, Menu.NONE, R.string.add_in_spoiler)
                }
                menu.add(Menu.NONE, ACTION_DELETE, Menu.NONE, R.string.delete)
                setOnMenuItemClickListener { menuItem ->
                    when (menuItem.itemId) {
                        ACTION_SPOILER -> {
                            itemActionListener?.onInsert(item, true)
                            true
                        }
                        ACTION_DELETE -> {
                            itemActionListener?.onDelete(item)
                            true
                        }
                        else -> false
                    }
                }
                show()
            }
        }

        @SuppressLint("SetTextI18n")
        private fun updateProgress(progress: Int) {
            if (progressBar.isIndeterminate) {
                progressBar.isIndeterminate = false
            }
            progressBar.progress = progress
            progressValue.text = "$progress%"
        }

        override fun onClick(v: View) {
            currentItem()?.let { item -> itemClickListener?.onItemClick(item) }
        }

        private fun updateChecked(item: AttachmentItem) {
            radioButton.isChecked = item.selected
            val colorAttr = if (item.loadState == AttachmentItem.STATE_NOT_LOADED) {
                androidx.appcompat.R.attr.colorError
            } else {
                com.google.android.material.R.attr.colorOnSurface
            }
            val alpha = when {
                item.loadState == AttachmentItem.STATE_NOT_LOADED && item.selected -> 96
                item.loadState == AttachmentItem.STATE_NOT_LOADED -> 48
                else -> 40
            }
            overlay.setBackgroundColor(
                ColorUtils.setAlphaComponent(itemView.context.getColorFromAttr(colorAttr), alpha)
            )
            overlay.visibility = if (
                item.selected || item.loadState == AttachmentItem.STATE_NOT_LOADED
            ) {
                View.VISIBLE
            } else {
                View.GONE
            }
        }

        fun unbind() {
            val item = boundItem
            if (item?.progressListener === progressListener) {
                item.progressListener = null
            }
            boundItem = null
        }
    }
}
