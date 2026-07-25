package forpdateam.ru.forpda.ui.views.messagepanel.attachments

import android.content.Context
import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.Menu
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.widget.PopupMenu
import androidx.core.content.ContextCompat
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.tabs.TabLayout
import forpdateam.ru.forpda.R
import forpdateam.ru.forpda.common.getColorFromAttr
import forpdateam.ru.forpda.common.removeAttachmentReferencesFromBody
import forpdateam.ru.forpda.databinding.MessagePanelAttachmentsBinding
import forpdateam.ru.forpda.entity.remote.editpost.AttachmentItem
import forpdateam.ru.forpda.entity.remote.editpost.EditPostForm
import forpdateam.ru.forpda.model.data.remote.api.RequestFile
import forpdateam.ru.forpda.ui.views.messagepanel.AutoFitRecyclerView
import forpdateam.ru.forpda.ui.views.messagepanel.MessagePanel
import java.util.ArrayList
import timber.log.Timber

/**
 * Created by radiationx on 09.01.17.
 */

class AttachmentsPopup(context: Context, private val messagePanel: MessagePanel) {
    private val context: Context = context
    private val dialog: BottomSheetDialog
    private val binding = MessagePanelAttachmentsBinding.inflate(LayoutInflater.from(context), null, false)
    private val recyclerView: AutoFitRecyclerView = binding.autoFitRecyclerView
    private val adapter = AttachmentAdapter()

    private val noAttachments: TextView = binding.noAttachmentsText
    private val emptyAttachments: TextView = binding.emptyAttachmentsText
    private val browseControls: View = binding.browseControls
    private val textControls: ViewGroup = binding.textControls
    private val selectedCount: TextView = binding.selectedCount
    private val addFile: ImageButton = binding.addFile
    private val deleteFile: ImageButton = binding.deleteFile
    private val retryFailed: ImageButton = binding.retryFailed
    private val clearFailed: ImageButton = binding.clearFailed
    private val addToSpoiler: Button = binding.addToSpoiler
    private val addToText: Button = binding.addToText
    private val progressOverlay: FrameLayout = binding.progressOverlay
    private val selectorTabs: TabLayout = binding.selectorTabLayout
    private val reverseOrder: MaterialButton = binding.selectorReverse

    private var enabledTextControls = true
    private var isLinear = true
    private var isReverse = false


    private val attachments = mutableListOf<AttachmentItem>()
    private val selected = mutableListOf<AttachmentItem>()


    private var insertAttachmentListener: OnInsertAttachmentListener? = null
    private var retryUploadListener: OnRetryUploadListener? = null
    private var deleteSelectedListener: (() -> Unit)? = null
    private var attachmentsChangedListener: ((List<AttachmentItem>) -> Unit)? = null

    /** Для retry: сопоставляем loading item -> исходный файл. */
    private val fileByItem = LinkedHashMap<AttachmentItem, RequestFile>()

    fun getAttachments(): List<AttachmentItem> = attachments.toList()

    fun getSelected(): List<AttachmentItem> = selected.toList()

    init {
        dialog = BottomSheetDialog(context)
        dialog.window?.let { window ->
            val lp = window.attributes
            lp.dimAmount = 1.0f
            window.attributes = lp
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        }
        //dialog.setPeekHeight(App.getKeyboardHeight());
        //dialog.getWindow().getDecorView().setFitsSystemWindows(true);

        recyclerView.setColumnWidth(
            recyclerView.context.resources.getDimensionPixelSize(R.dimen.attachment_grid_column_width)
        )
        adapter.updateIsLinear(isLinear)
        adapter.updateReverse(isReverse)
        recyclerView.setFakeLinear(isLinear)
        recyclerView.adapter = adapter

        dialog.setContentView(binding.root)
        setupViewControls()

        /*addFile.setItemClickListener(v -> {
            uploadFiles();
        });*/
        //deleteFile.setItemClickListener(v -> adapter.deleteSelected());
        adapter.setReloadOnClickListener(object : AttachmentAdapter.OnReloadClickListener {
            override fun onReloadClick(item: AttachmentItem) {
                val file = fileByItem[item]
                if (file != null) {
                    // Сбрасываем состояние и перезапускаем загрузку одного файла.
                    item.loadState = AttachmentItem.STATE_LOADING
                    item.setError(false)
                    adapter.updateItem(item)
                    retryUploadListener?.onRetry(listOf(file), listOf(item))
                }
            }
        })

        adapter.setOnItemClickListener(object : AttachmentAdapter.OnItemClickListener {
            override fun onItemClick(item: AttachmentItem) {
                item.toggle()
                if (item.selected) {
                    if (!selected.contains(item)) {
                        selected.add(item)
                    }
                } else {
                    selected.remove(item)
                }
                onSelectedChange()
                adapter.updateItem(item)
            }
        })
        adapter.setOnItemActionListener(object : AttachmentAdapter.OnItemActionListener {
            override fun onInsert(item: AttachmentItem, toSpoiler: Boolean) {
                if (item.loadState == AttachmentItem.STATE_LOADED) {
                    insertAttachment(listOf(item), toSpoiler)
                }
            }

            override fun onDelete(item: AttachmentItem) {
                selectOnly(item)
                deleteSelectedListener?.invoke()
            }
        })
        onDataChange(0)

        addToText.setOnClickListener { insertAttachment(selected, false) }
        addToSpoiler.setOnClickListener { insertAttachment(selected, true) }
        retryFailed.setOnClickListener { retryAllFailed() }
        clearFailed.setOnClickListener { clearAllFailed() }
        deleteFile.setOnClickListener { showSelectedActions() }

        messagePanel.addAttachmentsOnClickListener {
            if (binding.root.parent != null && binding.root.parent is ViewGroup) {
                (binding.root.parent as ViewGroup).removeView(binding.root)
            }
            dialog.setContentView(binding.root)
            dialog.show()
        }

        /*if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            Window window = dialog.getWindow();
            if (window != null) {
                window.addFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
                }
            }
        }*/
    }

    private fun setupViewControls() {
        val selectedIcon = context.getColorFromAttr(
            com.google.android.material.R.attr.colorOnSurface
        )
        val normalIcon = context.getColorFromAttr(R.attr.icon_base)
        selectorTabs.tabIconTint = ColorStateList(
            arrayOf(
                intArrayOf(android.R.attr.state_selected),
                intArrayOf()
            ),
            intArrayOf(selectedIcon, normalIcon)
        )

        val gridTab = selectorTabs.newTab()
            .setIcon(ContextCompat.getDrawable(context, R.drawable.ic_grid))
            .setContentDescription(R.string.attachments_grid_view)
        val listTab = selectorTabs.newTab()
            .setIcon(ContextCompat.getDrawable(context, R.drawable.ic_view_list))
            .setContentDescription(R.string.attachments_list_view)
        selectorTabs.addTab(gridTab)
        selectorTabs.addTab(listTab, true)
        selectorTabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabReselected(tab: TabLayout.Tab?) = Unit
            override fun onTabUnselected(tab: TabLayout.Tab?) = Unit

            override fun onTabSelected(tab: TabLayout.Tab?) {
                isLinear = tab == listTab
                recyclerView.setFakeLinear(isLinear)
                adapter.updateIsLinear(isLinear)
            }
        })

        reverseOrder.setOnClickListener {
            isReverse = !isReverse
            adapter.updateReverse(isReverse)
            adapter.clear()
            adapter.add(attachments)
            reverseOrder.isSelected = isReverse
            reverseOrder.setText(
                if (isReverse) {
                    R.string.attachments_newest_first
                } else {
                    R.string.attachments_oldest_first
                }
            )
        }
    }

    private fun showSelectedActions() {
        if (selected.isEmpty()) return
        PopupMenu(context, deleteFile).apply {
            menu.add(
                Menu.NONE,
                R.id.delete_file,
                Menu.NONE,
                R.string.delete_selected_attachments
            )
            setOnMenuItemClickListener { item ->
                if (item.itemId == R.id.delete_file) {
                    deleteSelectedListener?.invoke()
                    true
                } else {
                    false
                }
            }
            show()
        }
    }

    private fun selectOnly(item: AttachmentItem) {
        for (selectedItem in ArrayList(selected)) {
            if (selectedItem.selected) selectedItem.toggle()
            adapter.updateItem(selectedItem)
        }
        selected.clear()
        if (!item.selected) item.toggle()
        selected.add(item)
        adapter.updateItem(item)
        onSelectedChange()
    }

    fun setEnabledTextControls(enabled: Boolean) {
        enabledTextControls = enabled
        adapter.updateTextActionsEnabled(enabled)
        onSelectedChange()
    }

    fun insertAttachment(items: List<AttachmentItem>, toSpoiler: Boolean) {
        val text = StringBuilder()
        if (toSpoiler)
            text.append("[spoiler]")
        for (item in items) {
            insertAttachmentListener?.let {
                text.append(it.onInsert(item))
            } ?: text.append("[attachment=").append(item.id).append(":").append(item.name).append("]")
        }
        if (toSpoiler)
            text.append("[/spoiler]")
        messagePanel.insertText(text.toString())
        unSelectItems()
        dialog.cancel()
    }

    fun unSelectItems() {
        for (item in selected) {
            if (item.selected) item.toggle()
            adapter.updateItem(item)
        }
        selected.clear()
        onSelectedChange()
    }

    fun containNotLoaded(): Boolean {
        for (item in selected) {
            if (item.loadState != AttachmentItem.STATE_LOADED)
                return true
        }
        return false
    }


    fun deleteSelected() {
        for (item in selected) {
            if (item.status == AttachmentItem.STATUS_REMOVED) {
                attachments.remove(item)
                adapter.removeItem(item)
                updateDataCounter()
            }
        }
        unSelectItems()
    }


    private fun onDataChange(count: Int) {
        messagePanel.updateAttachmentsCounter(count)
        emptyAttachments.visibility = if (count == 0) View.VISIBLE else View.GONE
        recyclerView.visibility = if (count == 0) View.GONE else View.VISIBLE
        if (selected.isEmpty()) {
            browseControls.visibility = if (count > 0) View.VISIBLE else View.GONE
            noAttachments.text = if (count > 0) {
                context.getString(R.string.attachments_count, count)
            } else {
                context.getString(R.string.no_attachments)
            }
        }
    }

    private fun updateDataCounter() {
        onDataChange(attachments.size)
        updateRetryVisibility()
        attachmentsChangedListener?.invoke(attachments.toList())
    }

    private fun onSelectedChange() {
        val hasSelection = selected.isNotEmpty()
        noAttachments.text = if (hasSelection) {
            context.getString(R.string.attachments_selected, selected.size)
        } else if (attachments.isNotEmpty()) {
            context.getString(R.string.attachments_count, attachments.size)
        } else {
            context.getString(R.string.no_attachments)
        }
        selectedCount.text = context.getString(R.string.attachments_selected, selected.size)
        addFile.visibility = if (hasSelection) View.GONE else View.VISIBLE
        browseControls.visibility = if (!hasSelection && attachments.isNotEmpty()) {
            View.VISIBLE
        } else {
            View.GONE
        }
        updateRetryVisibility()
        textControls.visibility = if (hasSelection) View.VISIBLE else View.GONE
        addToSpoiler.visibility = if (enabledTextControls) View.VISIBLE else View.GONE
        addToText.visibility = if (enabledTextControls) View.VISIBLE else View.GONE
        tryLockControls(true)
    }

    private fun updateRetryVisibility() {
        val hasFailed = attachments.any { it.loadState == AttachmentItem.STATE_NOT_LOADED || it.isError }
        val shouldShow = hasFailed && selected.isEmpty()
        retryFailed.visibility = if (shouldShow) View.VISIBLE else View.GONE
        clearFailed.visibility = if (shouldShow) View.VISIBLE else View.GONE
    }

    private fun retryAllFailed() {
        val retryItems = attachments.filter { it.loadState == AttachmentItem.STATE_NOT_LOADED || it.isError }
            .mapNotNull { item ->
                val file = fileByItem[item] ?: return@mapNotNull null
                item.loadState = AttachmentItem.STATE_LOADING
                item.setError(false)
                adapter.updateItem(item)
                file to item
            }
        if (retryItems.isEmpty()) return
        val files = retryItems.map { it.first }
        val pending = retryItems.map { it.second }
        retryUploadListener?.onRetry(files, pending)
        updateRetryVisibility()
    }

    private fun clearAllFailed() {
        if (selected.isNotEmpty()) return
        val failedItems = attachments.filter { it.loadState == AttachmentItem.STATE_NOT_LOADED || it.isError }
        if (failedItems.isEmpty()) return
        for (item in failedItems) {
            fileByItem.remove(item)
            attachments.remove(item)
            adapter.removeItem(item)
        }
        updateDataCounter()
        onSelectedChange()
    }

    private fun tryLockControls(enable: Boolean) {
        if (textControls.visibility == View.VISIBLE) {
            val canInsert = enable && enabledTextControls && !containNotLoaded()
            addToSpoiler.isEnabled = canInsert
            addToText.isEnabled = canInsert
            deleteFile.isEnabled = enable
        }
    }


    fun setAddOnClickListener(listener: () -> Unit) {
        addFile.setOnClickListener { listener.invoke() }
    }

    fun setDeleteOnClickListener(listener: () -> Unit) {
        deleteSelectedListener = listener
    }

    fun setOnAttachmentsChangedListener(listener: ((List<AttachmentItem>) -> Unit)?) {
        attachmentsChangedListener = listener
    }

    fun onLoadAttachments(form: EditPostForm) {
        clearAttachments()
        attachments.addAll(form.attachments)
        adapter.add(form.attachments)
        updateDataCounter()
    }

    fun preUploadFiles(files: List<RequestFile>): List<AttachmentItem> {
        Timber.d("preUploadFiles $files")
        val loadingItems = ArrayList<AttachmentItem>()
        for (file in files) {
            val item = AttachmentItem(file.fileName)
            item.setProgressListener { _ ->

            }
            fileByItem[item] = file
            Timber.d("Add loading item $item")
            attachments.add(item)
            adapter.add(item)
            loadingItems.add(item)
        }
        updateDataCounter()
        return loadingItems
    }

    /** Opens the attachment sheet so loading thumbnails/spinner are visible during upload. */
    fun revealDuringUploadPreview() {
        if (!dialog.isShowing) dialog.show()
    }

    fun isShowing(): Boolean = dialog.isShowing

    fun dismiss(): Boolean {
        if (!dialog.isShowing) return false
        dialog.dismiss()
        return true
    }

    fun onUploadFiles(items: List<AttachmentItem>) {
        Timber.d("onUploadFiles $items")
        for (item in items) {
            Timber.d("Loading item $item")
            if (item.loadState == AttachmentItem.STATE_NOT_LOADED) {
                // Оставляем элемент, чтобы можно было нажать retry.
                item.setError(true)
                adapter.updateItem(item)
            } else {
                // Успешно — можно убрать файл из retry-map.
                if (item.loadState == AttachmentItem.STATE_LOADED) {
                    fileByItem.remove(item)
                }
                adapter.updateItem(item)
            }
        }
        updateDataCounter()
        onSelectedChange()
    }

    fun preDeleteFiles() {
        //block ui
        progressOverlay.visibility = View.VISIBLE
        tryLockControls(false)
    }

    fun endDeleteProgress() {
        progressOverlay.visibility = View.GONE
        tryLockControls(true)
    }

    fun setAttachments(items: List<AttachmentItem>) {
        // Копия до clear: иначе при вызове из setAttachmentsToPanels(getAttachments())
        // это тот же mutableList — clearAttachments() опустошает источник и список становится пустым.
        val snapshot = ArrayList(items)
        clearAttachments()
        attachments.addAll(snapshot)
        adapter.add(snapshot)
        updateDataCounter()
    }

    fun clearAttachments() {
        attachments.clear()
        selected.clear()
        fileByItem.clear()
        adapter.clear()
        updateDataCounter()
        onSelectedChange()
    }


    fun onDeleteFiles(deletedItems: List<AttachmentItem>) {
        Timber.d("onDeleteFiles $deletedItems")
        endDeleteProgress()
        val oldSelection = messagePanel.selectionRange
        val updatedMessage = removeAttachmentReferencesFromBody(
            messagePanel.message,
            deletedItems.map { it.id },
        )
        if (updatedMessage != messagePanel.message) {
            messagePanel.setText(updatedMessage)
            val length = updatedMessage.length
            messagePanel.messageField.setSelection(
                oldSelection[0].coerceIn(0, length),
                oldSelection[1].coerceIn(0, length),
            )
        }
        // Снимок: deletedItems может быть тем же самым списком, что и [selected]
        // (getSelected() отдаёт живой список, а deleteFiles возвращает его же обратно).
        // Тогда selected.remove(item) в цикле мутирует итерируемую коллекцию → ConcurrentModificationException
        // и падение приложения при удалении вложения.
        for (item in ArrayList(deletedItems)) {
            Timber.d("Delete file $item")
            if (item.status == AttachmentItem.STATUS_REMOVED) {
                attachments.remove(item)
                adapter.removeItem(item)
                selected.remove(item)
            }
        }
        updateDataCounter()
        onSelectedChange()
        unSelectItems()
    }

    fun setInsertAttachmentListener(insertAttachmentListener: OnInsertAttachmentListener) {
        this.insertAttachmentListener = insertAttachmentListener
    }

    fun setRetryUploadListener(listener: OnRetryUploadListener) {
        this.retryUploadListener = listener
    }

    interface OnRetryUploadListener {
        fun onRetry(files: List<RequestFile>, pending: List<AttachmentItem>)
    }

    interface OnInsertAttachmentListener {
        fun onInsert(item: AttachmentItem): String
    }

    companion object {
        private val LOG_TAG = AttachmentsPopup::class.java.simpleName
    }
}
