package forpdateam.ru.forpda.ui.views.messagepanel.attachments

import android.content.Context
import com.google.android.material.bottomsheet.BottomSheetDialog
import androidx.recyclerview.widget.GridLayoutManager
import timber.log.Timber
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.RelativeLayout
import android.widget.TextView

import java.util.ArrayList

import forpdateam.ru.forpda.R
import forpdateam.ru.forpda.common.removeAttachmentReferencesFromBody
import forpdateam.ru.forpda.ui.dp48
import forpdateam.ru.forpda.entity.remote.editpost.AttachmentItem
import forpdateam.ru.forpda.entity.remote.editpost.EditPostForm
import forpdateam.ru.forpda.model.data.remote.api.RequestFile
import forpdateam.ru.forpda.ui.views.messagepanel.AutoFitRecyclerView
import forpdateam.ru.forpda.ui.views.messagepanel.MessagePanel
import forpdateam.ru.forpda.databinding.MessagePanelAttachmentsBinding
import android.view.LayoutInflater

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
    private val textControls: RelativeLayout = binding.textControls
    private val addFile: ImageButton = binding.addFile
    private val deleteFile: ImageButton = binding.deleteFile
    private val retryFailed: ImageButton = binding.retryFailed
    private val clearFailed: ImageButton = binding.clearFailed
    private val addToSpoiler: Button = binding.addToSpoiler
    private val addToText: Button = binding.addToText
    private val progressOverlay: FrameLayout = binding.progressOverlay

    private var enabledTextControls = true
    private var isLinear = true
    private var isReverse = false


    private val attachments = mutableListOf<AttachmentItem>()
    private val selected = mutableListOf<AttachmentItem>()


    private var insertAttachmentListener: OnInsertAttachmentListener? = null
    private var retryUploadListener: OnRetryUploadListener? = null

    /** Для retry: сопоставляем loading item -> исходный файл. */
    private val fileByItem = LinkedHashMap<AttachmentItem, RequestFile>()

    fun getAttachments(): List<AttachmentItem> = attachments

    fun getSelected(): List<AttachmentItem> = selected

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

        recyclerView.manager.spanSizeLookup = object : androidx.recyclerview.widget.GridLayoutManager.SpanSizeLookup() {
            override fun getSpanSize(i: Int): Int {
                return if (isLinear) {
                    1
                } else if (i == 0) {
                    recyclerView.manager.spanCount
                } else {
                    1
                }
            }
        }

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
        adapter.setSelectorListener(object : AttachmentAdapter.SelectorListener {
            override fun onViewTypeChanged(isLinear: Boolean) {
                this@AttachmentsPopup.isLinear = isLinear
                recyclerView.setFakeLinear(isLinear)
                adapter.updateIsLinear(isLinear)
            }

            override fun onReverseClick() {
                isReverse = !isReverse
                adapter.updateReverse(isReverse)
                adapter.clear()
                adapter.add(attachments)
            }
        })
        onDataChange(0)

        addToText.setOnClickListener { insertAttachment(selected, false) }
        addToSpoiler.setOnClickListener { insertAttachment(selected, true) }
        retryFailed.setOnClickListener { retryAllFailed() }
        clearFailed.setOnClickListener { clearAllFailed() }

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

    fun setEnabledTextControls(enabled: Boolean) {
        enabledTextControls = enabled
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
        if (count > 0) {
            noAttachments.text = String.format(context.getString(R.string.attachments_count), count)
            //dialog.setPeekHeight(App.getKeyboardHeight());
        } else {
            noAttachments.setText(R.string.no_attachments)
            //dialog.setPeekHeight(dp48);
        }
    }

    private fun updateDataCounter() {
        onDataChange(attachments.size)
        updateRetryVisibility()
    }

    private fun onSelectedChange() {
        val firstGroup = if (selected.size > 0) View.GONE else View.VISIBLE
        val secondGroup = if (selected.size > 0) View.VISIBLE else View.GONE

        if (!enabledTextControls) {
            noAttachments.visibility = View.VISIBLE
        } else if (noAttachments.visibility != firstGroup) {
            noAttachments.visibility = firstGroup
        }
        if (addFile.visibility != firstGroup)
            addFile.visibility = firstGroup
        updateRetryVisibility()
        if (!enabledTextControls) {
            textControls.visibility = View.GONE
        } else if (textControls.visibility != secondGroup) {
            textControls.visibility = secondGroup
        }
        if (deleteFile.visibility != secondGroup)
            deleteFile.visibility = secondGroup

        tryLockControls(!containNotLoaded())
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
            addToSpoiler.isEnabled = enable
            addToText.isEnabled = enable
            deleteFile.isEnabled = enable
        }
    }


    fun setAddOnClickListener(listener: () -> Unit) {
        addFile.setOnClickListener { listener.invoke() }
    }

    fun setDeleteOnClickListener(listener: () -> Unit) {
        deleteFile.setOnClickListener { listener.invoke() }
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
        // Снимок: deletedItems может быть тем же самым списком, что и [selected]
        // (getSelected() отдаёт живой список, а deleteFiles возвращает его же обратно).
        // Тогда selected.remove(item) в цикле мутирует итерируемую коллекцию → ConcurrentModificationException
        // и падение приложения при удалении вложения.
        for (item in ArrayList(deletedItems)) {
            Timber.d("Delete file $item")
            if (item.id > 0) {
                // Снять ВСЕ формы ссылки на вложение (BBCode/img/url/markdown/голый URL), иначе 4PDA
                // при сохранении заново отрисует картинку по оставшейся разметке — вложение «не удаляется».
                messagePanel.setText(
                        removeAttachmentReferencesFromBody(messagePanel.message, item.id)
                )
            }
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
