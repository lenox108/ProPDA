package forpdateam.ru.forpda.presentation.editpost

import android.util.Log
import moxy.InjectViewState
import forpdateam.ru.forpda.common.mvp.BasePresenter
import forpdateam.ru.forpda.entity.app.EditPostSyncData
import forpdateam.ru.forpda.entity.remote.editpost.AttachmentItem
import forpdateam.ru.forpda.entity.remote.editpost.EditPostForm
import forpdateam.ru.forpda.entity.remote.theme.ThemePage
import forpdateam.ru.forpda.model.data.remote.api.RequestFile
import forpdateam.ru.forpda.model.repository.posteditor.PostEditorRepository
import forpdateam.ru.forpda.presentation.IErrorHandler
import forpdateam.ru.forpda.presentation.Screen
import forpdateam.ru.forpda.common.mergeEditPostMessage
import forpdateam.ru.forpda.presentation.TabRouter
import forpdateam.ru.forpda.presentation.theme.ThemeTemplate

private const val EDIT_POST_DIAG = "ForPDA.EditPost"

/**
 * Created by radiationx on 11.11.17.
 */

@InjectViewState
class EditPostPresenter(
        private val editorRepository: PostEditorRepository,
        private val themeTemplate: ThemeTemplate,
        private val router: TabRouter,
        private val errorHandler: IErrorHandler
) : BasePresenter<EditPostView>() {

    private val postForm = EditPostForm()

    fun initPostForm(newPostForm: EditPostForm) {
        postForm.apply {
            postForm.type = newPostForm.type
            postForm.attachments.addAll(newPostForm.attachments)
            postForm.message = newPostForm.message
            postForm.forumId = newPostForm.forumId
            postForm.topicId = newPostForm.topicId
            postForm.postId = newPostForm.postId
            postForm.st = newPostForm.st
        }
    }

    override fun onFirstViewAttach() {
        super.onFirstViewAttach()
        if (postForm.type == EditPostForm.TYPE_EDIT_POST) {
            // Сразу показываем поле (предзаполнение из бандла); ответ loadForm() обновит текст с сервера.
            viewState.showForm(postForm)
            viewState.setRefreshing(true)
            loadForm()
        } else {
            viewState.showForm(postForm)
        }
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
                    viewState.onPostSend(it, postForm)
                }, {
                    errorHandler.handle(it)
                })
                .untilDestroy()
    }

    fun loadForm() {
        editorRepository
                .loadForm(postForm.postId)
                .doFinally { viewState.setRefreshing(false) }
                .subscribe({ form ->
                    viewState.setRefreshing(false)
                    val mergedMessage = mergeEditPostMessage(form.message, postForm.message)
                    Log.d(
                            EDIT_POST_DIAG,
                            "presenter loadForm ok postId=${postForm.postId} serverLen=${form.message.length} " +
                                    "prefilledLen=${postForm.message.length} mergedLen=${mergedMessage.length} " +
                                    "mergedPreview=${mergedMessage.replace("\r\n", "\n").replace("\n", "↵").take(200)}"
                    )
                    postForm.message = mergedMessage
                    postForm.editReason = form.editReason.trim()
                            .takeIf { r -> r.isNotBlank() && r != "default_edit_reason" }
                            .orEmpty()
                    postForm.attachments.addAll(form.attachments)
                    form.poll?.let {
                        postForm.poll = it
                    }
                    viewState.showForm(form.apply { message = mergedMessage })

                    editorRepository.loadEditAttachments(postForm.postId)
                            .subscribe({ serverAttachments ->
                                if (serverAttachments.isNotEmpty()) {
                                    postForm.attachments.addAll(serverAttachments)
                                    viewState.showForm(form.apply {
                                        message = mergedMessage
                                        attachments.clear()
                                        attachments.addAll(serverAttachments)
                                    })
                                }
                            }, { e ->
                                Log.e(EDIT_POST_DIAG, "loadEditAttachments failed postId=${postForm.postId}", e)
                            })
                            .untilDestroy()
                }, { e ->
                    Log.e(EDIT_POST_DIAG, "presenter loadForm error postId=${postForm.postId}", e)
                    errorHandler.handle(e)
                    viewState.showForm(postForm)
                })
                .untilDestroy()
    }

    fun uploadFiles(files: List<RequestFile>, pending: List<AttachmentItem>) {
        editorRepository
                .uploadFiles(postForm.postId, files, pending)
                .subscribe({
                    viewState.onUploadFiles(it)
                }, {
                    errorHandler.handle(it)
                })
                .untilDestroy()
    }

    fun deleteFiles(items: List<AttachmentItem>) {
        editorRepository
                .deleteFiles(postForm.postId, items)
                .subscribe({
                    viewState.onDeleteFiles(it)
                }, {
                    errorHandler.handle(it)
                })
                .untilDestroy()
    }

    fun onSendClick() {
        if (postForm.type == EditPostForm.TYPE_EDIT_POST) {
            viewState.showReasonDialog(postForm)
        } else {
            viewState.sendMessage()
        }
    }

    fun onReasonEdit(reason: String) {
        postForm.editReason = reason
        viewState.sendMessage()
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
}
