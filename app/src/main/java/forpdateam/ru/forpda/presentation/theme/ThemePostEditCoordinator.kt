package forpdateam.ru.forpda.presentation.theme

import forpdateam.ru.forpda.common.normalizeEditPostBodyForEditor
import forpdateam.ru.forpda.common.normalizeEditPostBodyFromDomHtml
import forpdateam.ru.forpda.entity.app.EditPostSyncData
import forpdateam.ru.forpda.entity.remote.editpost.AttachmentItem
import forpdateam.ru.forpda.entity.remote.editpost.EditPostForm
import forpdateam.ru.forpda.entity.remote.theme.ThemePage
import forpdateam.ru.forpda.model.data.remote.api.RequestFile
import forpdateam.ru.forpda.model.interactors.theme.ThemeEditorUseCase
import forpdateam.ru.forpda.presentation.Screen
import com.github.terrakok.cicerone.ResultListenerHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Координатор для управления редактированием постов в теме.
 * Отвечает за открытие формы редактирования, отправку сообщений, загрузку/удаление файлов.
 */
class ThemePostEditCoordinator(
    private val scope: CoroutineScope,
    private val editorUseCase: ThemeEditorUseCase,
    private val uiEvents: MutableSharedFlow<ThemeUiEvent>,
    private val router: forpdateam.ru.forpda.presentation.TabRouter,
    private val getCurrentPage: () -> ThemePage?,
    private val getThemeLoadTraceId: () -> String,
    private val setThemeLoadTraceId: (String) -> Unit,
    private val getSetMessageRefreshing: () -> kotlinx.coroutines.flow.MutableStateFlow<Boolean>,
    private val onPostSubmitSuccess: suspend (EditPostForm, Long, ThemePage) -> Unit,
    private val onApplyPostedThemePage: (ThemePage, Boolean, Int?) -> Unit,
    private val onOpenEditPost: (Int) -> Unit
) {

    companion object {
        private const val EDIT_POST_DRAFT_SYNC_TAG = "EDIT_POST_DRAFT_SYNC"
    }

    private var themeSyncResultHandler: ResultListenerHandler? = null
    private var themePageResultHandler: ResultListenerHandler? = null

    /**
     * Создает форму редактирования поста на основе текущей страницы.
     */
    private fun createEditPostForm(message: String, attachments: MutableList<AttachmentItem>): EditPostForm? =
        getCurrentPage()?.let {
            val form = EditPostForm()
            form.forumId = it.forumId
            form.topicId = it.id
            form.st = it.st
            form.message = message
            form.attachments.addAll(attachments)
            form
        }

    /**
     * Открывает форму редактирования для нового сообщения.
     */
    fun openEditPostForm(
        message: String,
        attachments: MutableList<AttachmentItem>,
        selectionRange: IntArray? = null
    ) {
        Timber.d(
            EDIT_POST_DRAFT_SYNC_TAG,
            "vmOpen len=${message.length}" +
                " attachments=${attachments.size}" +
                " selection=${selectionRange?.getOrNull(0)}..${selectionRange?.getOrNull(1)}"
        )
        getCurrentPage()?.let { page ->
            createEditPostForm(message, attachments)?.let {
                router.navigateTo(Screen.EditPost().apply {
                    editPostForm = it
                    themeName = page.title
                    initialSelectionStart = selectionRange?.getOrNull(0)
                    initialSelectionEnd = selectionRange?.getOrNull(1)
                })
                themeSyncResultHandler?.dispose()
                themeSyncResultHandler = router.setResultListener(Screen.Theme.CODE_RESULT_SYNC) {
                    (it as? EditPostSyncData?)?.let { sync ->
                        if (sync.topicId == page.id) {
                            uiEvents.tryEmit(ThemeUiEvent.SyncEditPost(sync))
                        }
                    }
                }
                themePageResultHandler?.dispose()
                themePageResultHandler = router.setResultListener(Screen.Theme.CODE_RESULT_PAGE) {
                    (it as? ThemePage?)?.let { themePage ->
                        onApplyPostedThemePage(themePage, true, null)
                    }
                }
            }
        }
    }

    /**
     * Открывает форму редактирования для существующего поста.
     */
    fun openEditPostForm(postId: Int) {
        openEditPostForm(postId, null)
    }

    /**
     * Открывает форму редактирования для существующего поста с указанием HTML тела.
     * [domBodyHtml] — только если явно передан (например из WebView); HTML из модели темы не подставляем —
     * это разметка поста без BBCode, из‑за неё в редакторе «пустой» текст без [b]/[code] до ответа сервера.
     */
    fun openEditPostForm(postId: Int, domBodyHtml: String?) {
        getCurrentPage()?.let { page ->
            if (postId > 0) {
                onOpenEditPost(postId)
            }
            editorUseCase.kickWarmNetworkLoad(postId)
            val merged = domBodyHtml?.takeIf { it.isNotBlank() }?.trim()
            router.navigateTo(Screen.EditPost().apply {
                this.postId = postId
                topicId = page.id
                forumId = page.forumId
                st = page.st
                themeName = page.title
                initialBodyHtml = merged
            })
            themePageResultHandler?.dispose()
            themePageResultHandler = router.setResultListener(Screen.Theme.CODE_RESULT_PAGE) { result ->
                (result as? ThemePage?)?.let { page ->
                    val scrollPostId = page.anchorPostId?.toIntOrNull()
                            ?: page.anchor?.removePrefix("entry")?.toIntOrNull()
                            ?: postId.takeIf { it > 0 }
                    onApplyPostedThemePage(page, false, scrollPostId)
                }
            }
        }
    }

    /**
     * Отправляет новое сообщение в тему.
     */
    fun sendMessage(message: String, attachments: MutableList<AttachmentItem>) {
        createEditPostForm(message, attachments)?.let { form ->
            setThemeLoadTraceId(java.util.UUID.randomUUID().toString().replace("-", "").take(8))
            val sentAtMillis = System.currentTimeMillis()
            getSetMessageRefreshing().value = true
            scope.launch {
                when (val result = editorUseCase.sendPost(form, getThemeLoadTraceId())) {
                    is ThemeEditorUseCase.SendResult.Success -> {
                        onPostSubmitSuccess(form, sentAtMillis, result.page)
                        val scrollPostId = result.page.anchorPostId?.toIntOrNull()
                                ?: result.page.anchor?.removePrefix("entry")?.toIntOrNull()
                                ?: result.page.posts.lastOrNull()?.id?.takeIf { it > 0 }
                        onApplyPostedThemePage(result.page, true, scrollPostId)
                    }
                    is ThemeEditorUseCase.SendResult.Error -> { /* handled in UseCase */ }
                }
                getSetMessageRefreshing().value = false
            }
        }
    }

    /**
     * Загружает файлы для редактирования поста.
     */
    fun uploadFiles(files: List<RequestFile>, pending: List<AttachmentItem>) {
        scope.launch {
            when (val result = editorUseCase.uploadFiles(0, files, pending)) {
                is ThemeEditorUseCase.UploadResult.Success -> uiEvents.emit(ThemeUiEvent.OnUploadFiles(result.items))
                is ThemeEditorUseCase.UploadResult.Error -> { /* handled in UseCase */ }
            }
        }
    }

    /**
     * Удаляет файлы из редактирования поста.
     */
    fun deleteFiles(items: List<AttachmentItem>) {
        scope.launch {
            when (val result = editorUseCase.deleteFiles(0, items)) {
                is ThemeEditorUseCase.DeleteResult.Success -> uiEvents.emit(ThemeUiEvent.OnDeleteFiles(result.items))
                is ThemeEditorUseCase.DeleteResult.Error -> { /* handled in UseCase */ }
            }
        }
    }

    /**
     * Запускает предварительную загрузку данных для редактирования поста.
     */
    fun kickWarmNetworkLoad(postId: Int) {
        editorUseCase.kickWarmNetworkLoad(postId)
    }

    /**
     * Нормализует тело поста для редактора.
     */
    fun normalizeEditPostBody(trimmed: String): String {
        return if (trimmed.contains("[")) {
            normalizeEditPostBodyForEditor(trimmed)
        } else {
            normalizeEditPostBodyFromDomHtml(trimmed)
        }
    }

    /**
     * Очищает обработчики результатов.
     */
    fun dispose() {
        themeSyncResultHandler?.dispose()
        themePageResultHandler?.dispose()
        themeSyncResultHandler = null
        themePageResultHandler = null
    }
}
