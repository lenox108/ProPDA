package forpdateam.ru.forpda.ui.views.messagepanel.attachments

import android.content.Context
import android.content.res.ColorStateList
import android.net.Uri
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

class AttachmentsPopup(
    context: Context,
    private val messagePanel: MessagePanel,
    private val stateStore: AttachmentStateStore = AttachmentStateStore(),
) {
    private val context: Context = context
    private val dialog: BottomSheetDialog
    private val binding = MessagePanelAttachmentsBinding.inflate(LayoutInflater.from(context), null, false)
    private val recyclerView: AutoFitRecyclerView = binding.autoFitRecyclerView
    private val adapter = AttachmentAdapter(stateStore::rowId)

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


    private var insertAttachmentListener: OnInsertAttachmentListener? = null
    private var retryUploadListener: OnRetryUploadListener? = null
    private var deleteSelectedListener: (() -> Unit)? = null
    private var attachmentsChangedListener: ((List<AttachmentItem>) -> Unit)? = null

    private val attachmentItems: List<AttachmentItem>
        get() = stateStore.items()
    private val selectedItems: List<AttachmentItem>
        get() = stateStore.selectedItems()

    private val stateListener: (AttachmentStateStore.Change) -> Unit = { change ->
        adapter.submitItems(attachmentItems)
        renderState()
        if (change.contentChanged) {
            attachmentsChangedListener?.invoke(attachmentItems)
        }
    }

    fun getAttachments(): List<AttachmentItem> = attachmentItems

    fun getSelected(): List<AttachmentItem> = selectedItems

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
                val file = stateStore.sourceFor(item)
                if (file != null) {
                    // Сбрасываем состояние и перезапускаем загрузку одного файла.
                    item.loadState = AttachmentItem.STATE_LOADING
                    item.setError(false)
                    stateStore.itemChanged(item)
                    retryUploadListener?.onRetry(listOf(file), listOf(item))
                }
            }
        })

        adapter.setOnItemClickListener(object : AttachmentAdapter.OnItemClickListener {
            override fun onItemClick(item: AttachmentItem) {
                stateStore.toggleSelection(item)
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
        addToText.setOnClickListener { insertAttachment(selectedItems, false) }
        addToSpoiler.setOnClickListener { insertAttachment(selectedItems, true) }
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
        stateStore.addListener(stateListener)

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
        if (selectedItems.isEmpty()) return
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
        stateStore.selectOnly(item)
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
        stateStore.clearSelection()
    }

    fun containNotLoaded(): Boolean {
        for (item in selectedItems) {
            if (item.loadState != AttachmentItem.STATE_LOADED)
                return true
        }
        return false
    }


    fun deleteSelected() {
        stateStore.removeAll(selectedItems.filter { it.status == AttachmentItem.STATUS_REMOVED })
        unSelectItems()
    }


    private fun onDataChange(count: Int) {
        messagePanel.updateAttachmentsCounter(count)
        emptyAttachments.visibility = if (count == 0) View.VISIBLE else View.GONE
        recyclerView.visibility = if (count == 0) View.GONE else View.VISIBLE
        if (selectedItems.isEmpty()) {
            browseControls.visibility = if (count > 0) View.VISIBLE else View.GONE
            noAttachments.text = if (count > 0) {
                context.getString(R.string.attachments_count, count)
            } else {
                context.getString(R.string.no_attachments)
            }
        }
    }

    private fun renderState() {
        onDataChange(attachmentItems.size)
        updateRetryVisibility()
        onSelectedChange()
    }

    private fun onSelectedChange() {
        val hasSelection = selectedItems.isNotEmpty()
        noAttachments.text = if (hasSelection) {
            context.getString(R.string.attachments_selected, selectedItems.size)
        } else if (attachmentItems.isNotEmpty()) {
            context.getString(R.string.attachments_count, attachmentItems.size)
        } else {
            context.getString(R.string.no_attachments)
        }
        selectedCount.text = context.getString(R.string.attachments_selected, selectedItems.size)
        addFile.visibility = if (hasSelection) View.GONE else View.VISIBLE
        browseControls.visibility = if (!hasSelection && attachmentItems.isNotEmpty()) {
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
        val hasFailed = attachmentItems.any {
            it.loadState == AttachmentItem.STATE_NOT_LOADED || it.isError
        }
        val shouldShow = hasFailed && selectedItems.isEmpty()
        retryFailed.visibility = if (shouldShow) View.VISIBLE else View.GONE
        clearFailed.visibility = if (shouldShow) View.VISIBLE else View.GONE
    }

    private fun retryAllFailed() {
        val retryItems = attachmentItems.filter {
            it.loadState == AttachmentItem.STATE_NOT_LOADED || it.isError
        }
            .mapNotNull { item ->
                val file = stateStore.sourceFor(item) ?: return@mapNotNull null
                item.loadState = AttachmentItem.STATE_LOADING
                item.setError(false)
                stateStore.itemChanged(item)
                file to item
            }
        if (retryItems.isEmpty()) return
        val files = retryItems.map { it.first }
        val pending = retryItems.map { it.second }
        retryUploadListener?.onRetry(files, pending)
        updateRetryVisibility()
    }

    private fun clearAllFailed() {
        if (selectedItems.isNotEmpty()) return
        val failedItems = attachmentItems.filter {
            it.loadState == AttachmentItem.STATE_NOT_LOADED || it.isError
        }
        if (failedItems.isEmpty()) return
        stateStore.removeAll(failedItems)
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
        setAttachments(form.attachments)
    }

    fun preUploadFiles(files: List<RequestFile>): List<AttachmentItem> {
        Timber.d("preUploadFiles $files")
        val loadingItems = ArrayList<AttachmentItem>()
        for (file in files) {
            val item = AttachmentItem(file.fileName)
            item.sourceUri = file.sourceUri
            item.sourceMimeType = file.mimeType
            item.sourceFileSize = file.fileSize
            item.setProgressListener { _ ->

            }
            Timber.d("Add loading item $item")
            stateStore.add(item, file)
            loadingItems.add(item)
        }
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
                stateStore.itemChanged(item)
            } else {
                // Успешно — можно убрать файл из retry-map.
                if (item.loadState == AttachmentItem.STATE_LOADED) {
                    stateStore.removeSource(item)
                }
                stateStore.itemChanged(item)
            }
        }
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
        stateStore.replace(snapshot)
        snapshot.forEach { item ->
            if (stateStore.sourceFor(item) == null) {
                restoreRequestFile(item)?.let { stateStore.setSource(item, it) }
            }
        }
    }

    fun clearAttachments() {
        stateStore.clear()
    }


    fun onDeleteFiles(deletedItems: List<AttachmentItem>) {
        Timber.d("onDeleteFiles $deletedItems")
        endDeleteProgress()
        val oldSelection = messagePanel.selectionRange
        val originalMessage = messagePanel.message
        val updatedMessage = removeAttachmentReferencesFromBody(
            originalMessage,
            deletedItems.map { it.id },
        )
        if (updatedMessage != originalMessage) {
            messagePanel.replaceTextRange(
                0,
                originalMessage.length,
                updatedMessage,
                mapSelectionIndex(originalMessage, updatedMessage, oldSelection[0]),
                mapSelectionIndex(originalMessage, updatedMessage, oldSelection[1]),
            )
        }
        val removed = ArrayList<AttachmentItem>()
        for (item in ArrayList(deletedItems)) {
            Timber.d("Delete file $item")
            if (item.status == AttachmentItem.STATUS_REMOVED) {
                removed += item
            }
        }
        stateStore.removeAll(removed)
        unSelectItems()
    }

    private fun mapSelectionIndex(before: String, after: String, index: Int): Int {
        val safeIndex = index.coerceIn(0, before.length)
        val prefix = before.commonPrefixWith(after).length
        val maxSuffix = minOf(before.length - prefix, after.length - prefix)
        var suffix = 0
        while (
            suffix < maxSuffix &&
            before[before.lastIndex - suffix] == after[after.lastIndex - suffix]
        ) {
            suffix++
        }
        val beforeChangedEnd = before.length - suffix
        val afterChangedEnd = after.length - suffix
        return when {
            safeIndex <= prefix -> safeIndex
            safeIndex >= beforeChangedEnd ->
                (afterChangedEnd + safeIndex - beforeChangedEnd).coerceIn(0, after.length)
            else -> prefix
        }
    }

    private fun restoreRequestFile(item: AttachmentItem): RequestFile? {
        val source = item.sourceUri?.takeIf(String::isNotBlank) ?: return null
        val uri = runCatching { Uri.parse(source) }.getOrNull() ?: return null
        val streamProvider = {
            context.contentResolver.openInputStream(uri)
                ?: error("Unable to reopen attachment source: $uri")
        }
        val firstStream = runCatching(streamProvider).getOrNull() ?: return null
        return RequestFile(
            fileName = item.name.orEmpty(),
            mimeType = item.sourceMimeType.orEmpty(),
            fileStream = firstStream,
            fileSize = item.sourceFileSize,
            streamProvider = streamProvider,
            sourceUri = source,
        )
    }

    fun dispose() {
        stateStore.removeListener(stateListener)
        dismiss()
        getAttachments().forEach { item ->
            item.progressListener = null
        }
        recyclerView.adapter = null
        insertAttachmentListener = null
        retryUploadListener = null
        deleteSelectedListener = null
        attachmentsChangedListener = null
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
