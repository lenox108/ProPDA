package forpdateam.ru.forpda.presentation.editpost

import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import forpdateam.ru.forpda.common.mergeEditPostMessage
import forpdateam.ru.forpda.entity.app.EditPostSyncData
import forpdateam.ru.forpda.entity.remote.editpost.AttachmentItem
import forpdateam.ru.forpda.entity.remote.editpost.EditPostForm
import forpdateam.ru.forpda.entity.remote.theme.ThemePage
import forpdateam.ru.forpda.model.data.remote.api.RequestFile
import forpdateam.ru.forpda.model.repository.posteditor.PostEditorRepository
import forpdateam.ru.forpda.presentation.IErrorHandler
import forpdateam.ru.forpda.presentation.Screen
import forpdateam.ru.forpda.presentation.TabRouter
import forpdateam.ru.forpda.presentation.theme.ThemeTemplate
import io.reactivex.disposables.CompositeDisposable
import java.util.HashSet
import java.util.concurrent.TimeoutException

private const val EDIT_POST_DIAG = "ForPDA.EditPost"

private const val EDIT_LOAD_SAFETY_MS = 72_000L

private class EditLoadState {
    var form: EditPostForm? = null
    var formDone = false
    var attachments: List<AttachmentItem>? = null
    var attachDone = false
}

class EditPostViewModel(
        private val editorRepository: PostEditorRepository,
        private val themeTemplate: ThemeTemplate,
        private val router: TabRouter,
        private val errorHandler: IErrorHandler
) : ViewModel() {

    @Volatile
    private var editPostView: EditPostView? = null

    fun attachView(view: EditPostView) {
        editPostView = view
    }

    fun detachView() {
        editPostView = null
    }

    private val editLoadSafetyHandler = Handler(Looper.getMainLooper())
    private var editLoadSafetyRunnable: Runnable? = null

    private val editLoadDisposables = CompositeDisposable()
    private val rxSubscriptions = CompositeDisposable()

    private var subscriptionsStarted = false

    private val postForm = EditPostForm()

    private var messageFromView: (() -> String)? = null

    fun attachMessageSource(source: (() -> String)?) {
        messageFromView = source
    }

    private var editLoadState = EditLoadState()

    fun initPostForm(newPostForm: EditPostForm) {
        postForm.apply {
            type = newPostForm.type
            attachments.addAll(newPostForm.attachments)
            message = newPostForm.message
            forumId = newPostForm.forumId
            topicId = newPostForm.topicId
            postId = newPostForm.postId
            st = newPostForm.st
        }
    }

    fun start() {
        if (subscriptionsStarted) return
        subscriptionsStarted = true
        if (postForm.type == EditPostForm.TYPE_EDIT_POST) {
            if (postForm.postId <= 0) {
                Log.e(EDIT_POST_DIAG, "TYPE_EDIT_POST but postId=${postForm.postId}, skip load")
                editPostView?.showForm(postForm)
                return
            }
            val snap = editorRepository.snapshotWarmEdit(postForm.postId)
            if (snap != null) {
                editLoadState.form = snap.form
                editLoadState.formDone = true
                if (snap.attachments != null) {
                    editLoadState.attachments = snap.attachments.toMutableList()
                    editLoadState.attachDone = true
                }
                applyEditLoadMerge()
                Log.d(EDIT_POST_DIAG, "warm snapshot postId=${postForm.postId} attachReady=${snap.attachments != null}")
            } else {
                if (postForm.message.isNotBlank() || postForm.attachments.isNotEmpty()) {
                    editPostView?.showEditLoadingDraft(postForm)
                } else {
                    editPostView?.showEditLoadPlaceholder()
                }
            }
            loadForm(keepWarmPrefill = snap != null)
        } else {
            editPostView?.showForm(postForm)
        }
    }

    override fun onCleared() {
        cancelEditLoadSafetyTimeout()
        editLoadDisposables.dispose()
        messageFromView = null
        rxSubscriptions.clear()
        super.onCleared()
    }

    private fun scheduleEditLoadSafetyTimeout() {
        cancelEditLoadSafetyTimeout()
        editLoadSafetyRunnable = Runnable {
            editLoadSafetyRunnable = null
            if (!editLoadState.formDone) {
                Log.e(
                        EDIT_POST_DIAG,
                        "edit load safety timeout ${EDIT_LOAD_SAFETY_MS}ms postId=${postForm.postId}"
                )
                errorHandler.handle(TimeoutException("Загрузка формы редактирования"))
                editPostView?.showForm(postForm)
            }
        }
        editLoadSafetyHandler.postDelayed(editLoadSafetyRunnable!!, EDIT_LOAD_SAFETY_MS)
    }

    private fun cancelEditLoadSafetyTimeout() {
        editLoadSafetyRunnable?.let { editLoadSafetyHandler.removeCallbacks(it) }
        editLoadSafetyRunnable = null
    }

    fun sendMessage(message: String, attachments: List<AttachmentItem>) {
        postForm.message = message
        postForm.attachments.clear()
        for (item in attachments) {
            postForm.addAttachment(item)
        }
        editorRepository
                .sendPost(postForm)
                .map { themeTemplate.mapEntity(it) }
                .subscribe({
                    editorRepository.invalidateEditCache(postForm.postId)
                    editPostView?.onPostSend(it, postForm)
                }, {
                    errorHandler.handle(it)
                })
                .also { rxSubscriptions.add(it) }
    }

    fun loadForm(keepWarmPrefill: Boolean = false) {
        editLoadDisposables.clear()
        if (!keepWarmPrefill) {
            editLoadState = EditLoadState()
        }
        scheduleEditLoadSafetyTimeout()
        editorRepository.loadForm(postForm.postId)
                .doFinally {
                    cancelEditLoadSafetyTimeout()
                }
                .subscribe({ form ->
                    try {
                        editLoadState.form = form
                        editLoadState.formDone = true
                        applyEditLoadMerge()
                        if (form.errorCode == EditPostForm.ERROR_NONE) {
                            subscribeLoadEditAttachmentsIfNeeded()
                        }
                    } catch (e: Throwable) {
                        Log.e(EDIT_POST_DIAG, "applyEditLoadMerge postId=${postForm.postId}", e)
                        errorHandler.handle(e)
                        editPostView?.showForm(postForm)
                    }
                }, { e ->
                    Log.e(EDIT_POST_DIAG, "loadForm error postId=${postForm.postId}", e)
                    errorHandler.handle(e)
                    editPostView?.showForm(postForm)
                })
                .also { editLoadDisposables.add(it) }
    }

    private fun subscribeLoadEditAttachmentsIfNeeded() {
        if (editLoadState.attachDone) return
        editorRepository.loadEditAttachments(postForm.postId)
                .subscribe({ list ->
                    editLoadState.attachments = list
                    editLoadState.attachDone = true
                    applyEditLoadMerge()
                }, {
                    editLoadState.attachments = emptyList()
                    editLoadState.attachDone = true
                    applyEditLoadMerge()
                })
                .also { editLoadDisposables.add(it) }
    }

    private fun applyEditLoadMerge() {
        val state = editLoadState
        val form = state.form
        if (!state.formDone || form == null) return

        if (form.errorCode != EditPostForm.ERROR_NONE) {
            postForm.errorCode = form.errorCode
            editPostView?.showForm(postForm)
            return
        }

        val serverAttachments = if (state.attachDone) state.attachments.orEmpty() else emptyList()

        postForm.errorCode = EditPostForm.ERROR_NONE
        val localDraft = messageFromView?.invoke() ?: postForm.message
        val mergedMessage = mergeEditPostMessage(form.message, localDraft)
        if (state.attachDone) {
            Log.d(
                    EDIT_POST_DIAG,
                    "loadForm ok postId=${postForm.postId} serverLen=${form.message.length} " +
                            "prefilledLen=${localDraft.length} mergedLen=${mergedMessage.length} " +
                            "attachCount=${serverAttachments.size} " +
                            "mergedPreview=${mergedMessage.replace("\r\n", "\n").replace("\n", "↵").take(200)}"
            )
        }
        postForm.message = mergedMessage
        postForm.editReason = form.editReason.trim()
                .takeIf { r -> r.isNotBlank() && r != "default_edit_reason" }
                .orEmpty()
        form.poll?.let { postForm.poll = it }
        postForm.attachments.clear()
        postForm.attachments.addAll(form.attachments)
        postForm.attachments.addAll(serverAttachments)
        mergeAttachmentIdsFromPostText(postForm)
        dedupeAttachmentsById(postForm)
        editPostView?.showForm(postForm)
    }

    private fun mergeAttachmentIdsFromPostText(form: EditPostForm) {
        val have = form.attachments.map { it.id }.toMutableSet()
        fun add(id: Int, nameHint: String?) {
            if (id <= 0 || id in have) return
            have.add(id)
            val item = AttachmentItem()
            item.setId(id)
            val name = nameHint?.trim()?.takeIf { it.isNotBlank() && it.length < 260 }
                    ?: "attachment_$id"
            item.setName(name)
            item.setLoadState(AttachmentItem.STATE_LOADED)
            item.setStatus(AttachmentItem.STATUS_READY)
            form.attachments.add(item)
        }
        val msg = form.message
        Regex("""(?i)\[attachment\s*=\s*(\d+)\s*:([^\]]+?)]""").findAll(msg).forEach { m ->
            add(m.groupValues[1].toIntOrNull() ?: return@forEach, m.groupValues.getOrNull(2))
        }
        Regex("""(?i)\[attachment\s*=\s*"(\d+)\s*:([^"]+?)"]""").findAll(msg).forEach { m ->
            add(m.groupValues[1].toIntOrNull() ?: return@forEach, m.groupValues.getOrNull(2))
        }
        Regex("""(?i)\[([^\]]{0,400})]\(\s*https?://4pda\.to/forum/dl/post/(\d+)/[^)\s]+""").findAll(msg).forEach { m ->
            add(m.groupValues[2].toIntOrNull() ?: return@forEach, m.groupValues.getOrNull(1))
        }
        Regex("""(?i)\[url=https?://4pda\.to/forum/dl/post/(\d+)/[^\]]+]([^\[]*)\[/url]""").findAll(msg).forEach { m ->
            add(m.groupValues[1].toIntOrNull() ?: return@forEach, m.groupValues.getOrNull(2))
        }
        Regex("""(?i)https?://4pda\.to/forum/dl/post/(\d+)/[^\s\]\)>"']+""").findAll(msg).forEach { m ->
            val id = m.groupValues[1].toIntOrNull() ?: return@forEach
            val tail = m.value.substringAfterLast('/').trim()
            add(id, tail.takeIf { it.contains('.') })
        }
    }

    private fun dedupeAttachmentsById(form: EditPostForm) {
        val seen = HashSet<Int>()
        val it = form.attachments.iterator()
        while (it.hasNext()) {
            val id = it.next().id
            if (id != 0 && !seen.add(id)) it.remove()
        }
    }

    fun uploadFiles(files: List<RequestFile>, pending: List<AttachmentItem>) {
        editorRepository
                .uploadFiles(postForm.postId, files, pending)
                .subscribe({
                    editPostView?.onUploadFiles(it)
                }, {
                    errorHandler.handle(it)
                })
                .also { rxSubscriptions.add(it) }
    }

    fun deleteFiles(items: List<AttachmentItem>) {
        editorRepository
                .deleteFiles(postForm.postId, items)
                .doFinally { editPostView?.onAttachmentDeleteProgressFinished() }
                .subscribe({
                    editPostView?.onDeleteFiles(it)
                }, {
                    errorHandler.handle(it)
                })
                .also { rxSubscriptions.add(it) }
    }

    fun onSendClick() {
        if (postForm.type == EditPostForm.TYPE_EDIT_POST) {
            editPostView?.showReasonDialog(postForm)
        } else {
            editPostView?.sendMessage()
        }
    }

    fun onReasonEdit(reason: String) {
        postForm.editReason = reason
        editPostView?.sendMessage()
    }

    fun exit() {
        router.exit()
    }

    fun exitWithSync(message: String, intArray: IntArray, attachments: List<AttachmentItem>) {
        router.exitWithResult(Screen.Theme.CODE_RESULT_SYNC, EditPostSyncData().also {
            it.topicId = postForm.topicId
            it.message = message
            it.selectionStart = intArray[0]
            it.selectionEnd = intArray[1]
            it.attachments = attachments
        })
    }

    fun exitWithPage(page: ThemePage) {
        router.exitWithResult(Screen.Theme.CODE_RESULT_PAGE, page)
    }

    class Factory(
            private val editorRepository: PostEditorRepository,
            private val themeTemplate: ThemeTemplate,
            private val router: TabRouter,
            private val errorHandler: IErrorHandler
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass != EditPostViewModel::class.java) throw IllegalArgumentException("Unknown ViewModel class")
            return EditPostViewModel(editorRepository, themeTemplate, router, errorHandler) as T
        }
    }
}
