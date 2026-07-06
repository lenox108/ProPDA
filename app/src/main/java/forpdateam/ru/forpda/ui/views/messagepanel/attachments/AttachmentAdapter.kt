package forpdateam.ru.forpda.ui.views.messagepanel.attachments

import forpdateam.ru.forpda.common.getVecDrawable
import android.annotation.SuppressLint
import android.content.res.ColorStateList
import android.graphics.Color
import com.google.android.material.tabs.TabLayout
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import timber.log.Timber
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.RadioButton

import android.widget.TextView
import com.google.android.material.progressindicator.CircularProgressIndicator

import forpdateam.ru.forpda.common.ForPdaCoil
import forpdateam.ru.forpda.databinding.MessagePanelAttachmentsSelectorBinding
import forpdateam.ru.forpda.databinding.MessagePanelAttachmentItemBinding
import forpdateam.ru.forpda.databinding.MessagePanelAttachmentItemHorizontalBinding

import java.util.ArrayList
import forpdateam.ru.forpda.R
import forpdateam.ru.forpda.common.getColorFromAttr
import forpdateam.ru.forpda.entity.remote.editpost.AttachmentItem
import forpdateam.ru.forpda.model.data.remote.IWebClient
import forpdateam.ru.forpda.ui.views.drawers.adapters.AttachmentListItem
import forpdateam.ru.forpda.ui.views.drawers.adapters.AttachmentSelectorListItem
import forpdateam.ru.forpda.ui.views.drawers.adapters.ListItem


/**
 * Created by radiationx on 09.01.17.
 */

class AttachmentAdapter : androidx.recyclerview.widget.RecyclerView.Adapter<androidx.recyclerview.widget.RecyclerView.ViewHolder>() {
    private val items = ArrayList<ListItem>()
    //private val selected = ArrayList<AttachmentItem>()
    private var itemClickListener: AttachmentAdapter.OnItemClickListener? = null
    private var reloadOnClickListener: OnReloadClickListener? = null
    private var selectorListener: SelectorListener? = null
    private var isLinear = false
    private var isReverse = false

    companion object {
        private const val TYPE_SELECTOR = 1
        private const val TYPE_ITEM = 2
        private const val TYPE_ITEM_HORIZONTAL = 3
    }

    init {
        clear()
    }

    fun updateIsLinear(isLinear: Boolean) {
        this.isLinear = isLinear
        val index = items.indexOfFirst { it is AttachmentSelectorListItem }
        if (index != -1) {
            (items[index] as AttachmentSelectorListItem).isLinear = isLinear
            notifyItemChanged(index)
        }
    }

    fun updateReverse(isReverse: Boolean) {
        this.isReverse = isReverse
        val index = items.indexOfFirst { it is AttachmentSelectorListItem }
        if (index != -1) {
            (items[index] as AttachmentSelectorListItem).isReverse = isReverse
            notifyItemChanged(index)
        }
    }

    fun updateItem(item: AttachmentItem) {
        val index = items.indexOfFirst { (it as? AttachmentListItem)?.item == item }
        if (index != -1) {
            notifyItemChanged(index)
        }
    }

    fun add(newItems: List<AttachmentItem>) {
        val finalItems = if (isReverse) {
            newItems.asReversed()
        } else {
            newItems
        }
        val insertIndex = if (isReverse) {
            1
        } else {
            this.items.size
        }
        this.items.addAll(insertIndex, finalItems.map { AttachmentListItem(it) })
        notifyItemRangeInserted(insertIndex, finalItems.size)
    }

    fun add(item: AttachmentItem) {
        add(listOf(item))
    }

    fun clear() {
        val oldSize = items.size
        items.clear()
        items.add(AttachmentSelectorListItem(isLinear, isReverse))
        if (oldSize > 1) {
            notifyItemRangeRemoved(1, oldSize - 1)
        }
        notifyItemChanged(0)
    }

    fun removeItem(item: AttachmentItem) {
        val index = items.indexOfFirst { (it as? AttachmentListItem)?.item == item }
        if (index != -1) {
            items.removeAt(index)
            notifyItemRemoved(index)
        }
    }

    override fun getItemViewType(position: Int): Int {
        val item = items[position]
        return when (item) {
            is AttachmentListItem -> if (isLinear) TYPE_ITEM_HORIZONTAL else TYPE_ITEM
            is AttachmentSelectorListItem -> TYPE_SELECTOR
            else -> -1
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): androidx.recyclerview.widget.RecyclerView.ViewHolder {
        return when (viewType) {
            TYPE_ITEM -> {
                val binding = MessagePanelAttachmentItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
                ViewHolder(binding)
            }
            TYPE_ITEM_HORIZONTAL -> {
                val binding = MessagePanelAttachmentItemHorizontalBinding.inflate(LayoutInflater.from(parent.context), parent, false)
                ViewHolder(binding)
            }
            TYPE_SELECTOR -> {
                val binding = MessagePanelAttachmentsSelectorBinding.inflate(LayoutInflater.from(parent.context), parent, false)
                SelectorHolder(binding)
            }
            else -> throw NullPointerException()
        }
    }

    override fun onBindViewHolder(holder: androidx.recyclerview.widget.RecyclerView.ViewHolder, position: Int) {
        val item = items[position]
        val viewType = getItemViewType(position)
        when (viewType) {
            TYPE_ITEM, TYPE_ITEM_HORIZONTAL -> {
                (holder as ViewHolder).bind((item as AttachmentListItem).item)
            }
            TYPE_SELECTOR -> {
                val selectorItem = (item as AttachmentSelectorListItem)
                (holder as SelectorHolder).bind(selectorItem.isLinear, selectorItem.isReverse)
            }
        }
    }

    override fun getItemCount(): Int {
        return items.size
    }

    fun setOnItemClickListener(mItemClickListener: AttachmentAdapter.OnItemClickListener) {
        this.itemClickListener = mItemClickListener
    }

    fun setReloadOnClickListener(reloadOnClickListener: OnReloadClickListener) {
        this.reloadOnClickListener = reloadOnClickListener
    }

    fun setSelectorListener(selectorListener: SelectorListener) {
        this.selectorListener = selectorListener
    }

    interface OnItemClickListener {
        fun onItemClick(item: AttachmentItem)
    }

    interface OnReloadClickListener {
        fun onReloadClick(item: AttachmentItem)
    }

    interface SelectorListener {
        fun onViewTypeChanged(isLinear: Boolean)
        fun onReverseClick()
    }

    inner class SelectorHolder(private val binding: MessagePanelAttachmentsSelectorBinding) : androidx.recyclerview.widget.RecyclerView.ViewHolder(binding.root) {
        private var tabLayout: TabLayout = binding.selectorTabLayout
        private var reverseBtn: ImageView = binding.selectorReverse
        private var gridTab: TabLayout.Tab
        private var listTab: TabLayout.Tab
        private var listener: TabLayout.OnTabSelectedListener

        init {
            val selectedIcon = tabLayout.context.getColorFromAttr(com.google.android.material.R.attr.colorOnSurface)
            val normalIcon = tabLayout.context.getColorFromAttr(R.attr.icon_base)
            val iconTint = ColorStateList(
                arrayOf(
                    intArrayOf(android.R.attr.state_selected),
                    intArrayOf()
                ),
                intArrayOf(selectedIcon, normalIcon)
            )
            tabLayout.tabIconTint = iconTint
            reverseBtn.imageTintList = ColorStateList.valueOf(normalIcon)

            gridTab = tabLayout.newTab().setIcon(ContextCompat.getDrawable(tabLayout.context, R.drawable.ic_grid)).also {
                tabLayout.addTab(it)
            }
            listTab = tabLayout.newTab().setIcon(ContextCompat.getDrawable(tabLayout.context, R.drawable.ic_view_list)).also {
                tabLayout.addTab(it)
            }
            listener = object : TabLayout.OnTabSelectedListener {
                override fun onTabReselected(p0: TabLayout.Tab?) {}
                override fun onTabUnselected(p0: TabLayout.Tab?) {}

                override fun onTabSelected(p0: TabLayout.Tab?) {
                    selectorListener?.onViewTypeChanged(p0 == listTab)
                }
            }
            reverseBtn.setOnClickListener { selectorListener?.onReverseClick() }
        }

        fun bind(isLinear: Boolean, _isReverse: Boolean) {
            tabLayout.removeOnTabSelectedListener(listener)
            if (!isLinear) {
                gridTab.select()
            } else {
                listTab.select()
            }
            reverseBtn.isSelected = _isReverse
            reverseBtn.imageTintList = ColorStateList.valueOf(
                reverseBtn.context.getColorFromAttr(
                    if (_isReverse) com.google.android.material.R.attr.colorOnSurface else R.attr.icon_base
                )
            )
            tabLayout.addOnTabSelectedListener(listener)
        }
    }

    inner class ViewHolder(private val bindingBase: Any) : androidx.recyclerview.widget.RecyclerView.ViewHolder(
        if (bindingBase is MessagePanelAttachmentItemBinding) bindingBase.root else (bindingBase as MessagePanelAttachmentItemHorizontalBinding).root
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
        private var progressListener = IWebClient.ProgressListener { percent ->
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
                }
            }

            reload.setOnClickListener {
                // bindingAdapterPosition + getOrNull + as?: при сдвиге позиций
                // (удалили вложение) layoutPosition попадал на селектор-элемент →
                // ClassCastException; NO_POSITION(-1) → IndexOutOfBounds.
                val item = (items.getOrNull(bindingAdapterPosition) as? AttachmentListItem)?.item
                        ?: return@setOnClickListener
                reloadOnClickListener?.onReloadClick(item)
            }
        }

        @SuppressLint("SetTextI18n")
        fun bind(item: AttachmentItem) {
            // На случай реюза viewholder: убираем слушатель у прошлой модели
            (items.getOrNull(layoutPosition) as? AttachmentListItem)?.item?.progressListener = null
            when (item.loadState) {
                AttachmentItem.STATE_LOADING -> {
                    description.visibility = View.GONE
                    progressBar.visibility = View.VISIBLE
                    progressValue.visibility = View.VISIBLE
                    reload.visibility = View.GONE
                    errorText.visibility = View.GONE
                    imageView.visibility = View.GONE
                    updateProgress(item.progress)
                    item.progressListener = progressListener
                }
                AttachmentItem.STATE_NOT_LOADED -> {
                    description.visibility = View.GONE
                    progressBar.visibility = View.GONE
                    progressValue.visibility = View.GONE
                    reload.visibility = View.VISIBLE
                    errorText.visibility = View.VISIBLE
                    errorText.text = item.errorText ?: "Ошибка загрузки. Нажмите повторить."
                    imageView.visibility = View.GONE
                }
                AttachmentItem.STATE_LOADED -> {
                    description.visibility = View.VISIBLE
                    name.text = item.name
                    attributes.text = "${item.extension}, ${item.weight}"
                    errorText.visibility = View.GONE
                    progressBar.visibility = View.GONE
                    progressValue.visibility = View.GONE
                    reload.visibility = View.GONE
                    imageView.visibility = View.VISIBLE
                    imageView.alpha = 1f
                    imageView.imageTintList = null
                    imageView.clearColorFilter()
                    if (item.typeFile == AttachmentItem.TYPE_IMAGE) {
                        ForPdaCoil.loadInto(imageView, item.imageUrl)
                    } else {
                        imageView.setImageDrawable(itemView.context.getVecDrawable(R.drawable.ic_insert_drive_file_gray_24dp))
                    }
                }
            }
            updateChecked(item)
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
            // См. reload-клик выше: селектор-элемент / NO_POSITION по индексу.
            val item = (items.getOrNull(bindingAdapterPosition) as? AttachmentListItem)?.item ?: return
            itemClickListener?.onItemClick(item)
        }

        private fun updateChecked(item: AttachmentItem) {
            radioButton.isChecked = item.selected
            if (item.loadState == AttachmentItem.STATE_NOT_LOADED) {
                overlay.visibility = View.VISIBLE
                overlay.setBackgroundColor(Color.argb(if (item.selected) 96 else 48, 255, 0, 0))
            } else {
                overlay.setBackgroundColor(Color.argb(48, 0, 0, 0))
                overlay.visibility = if (item.selected) View.VISIBLE else View.GONE
            }
        }
    }
}
