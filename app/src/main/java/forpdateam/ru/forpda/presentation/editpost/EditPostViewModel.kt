package forpdateam.ru.forpda.presentation.editpost

import forpdateam.ru.forpda.presentation.BaseViewModel
import android.os.Handler
import android.os.Looper
import timber.log.Timber
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import forpdateam.ru.forpda.common.dedupeAttachmentsById
import forpdateam.ru.forpda.common.mergeAttachmentIdsFromPostText
import forpdateam.ru.forpda.common.mergeEditPostMessage
import forpdateam.ru.forpda.entity.app.EditPostSyncData
import forpdateam.ru.forpda.entity.remote.editpost.AttachmentItem
import forpdateam.ru.forpda.entity.remote.editpost.EditPostForm
import forpdateam.ru.forpda.entity.remote.theme.ThemePage
import forpdateam.ru.forpda.entity.app.profile.IUserHolder
import forpdateam.ru.forpda.model.AuthHolder
import forpdateam.ru.forpda.model.data.remote.api.RequestFile
import forpdateam.ru.forpda.model.repository.draft.PostDraftRepository
import forpdateam.ru.forpda.model.repository.faviorites.FavoritesRepository
import forpdateam.ru.forpda.model.repository.posteditor.PostEditorRepository
import forpdateam.ru.forpda.presentation.IErrorHandler
import forpdateam.ru.forpda.presentation.Screen
import forpdateam.ru.forpda.presentation.TabRouter
import forpdateam.ru.forpda.presentation.theme.ThemeTemplate

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.concurrent.TimeoutException

private const val EDIT_POST_DIAG = "ForPDA.EditPost"

private const val EDIT_LOAD_SAFETY_MS = 72_000L

private const val DRAFT_SAVE_DEBOUNCE_MS = 600L

private class EditLoadState {
    var form: EditPostForm? = null
    var formDone = false
    var attachments: List<AttachmentItem>? = null
    var attachDone = false
}

@HiltViewModel
class EditPostViewModel @Inject constructor(
        private val editorRepository: PostEditorRepository,
        private val themeTemplate: ThemeTemplate,
        private val router: TabRouter,
        private val postDraftRepository: PostDraftRepository,
        private val favoritesRepository: FavoritesRepository,
        private val authHolder: AuthHolder,
        private val userHolder: IUserHolder,
        private val errorHandler: IErrorHandler
) : BaseViewModel() {

    private var editLoadSafetyJob: Job? = null

    private var editLoadJob: Job? = null

    private var subscriptionsStarted = false

    private val postForm = EditPostForm()

    /** Серверные id вложений на момент первого успешного merge после открытия редактора (TYPE_EDIT_POST). */
    private var editSessionBaselineAttachmentIds: Set<Int>? = null

    private var uploadJob: Job? = null

    /** In-flight отправка поста. Пока Job активен — повторные клики «Отправить» игнорируются (защита от дубля поста). */
    private var sendJob: Job? = null

    /** Дебаунс-сохранение персистентного черновика (только TYPE_NEW_POST). */
    private var draftSaveJob: Job? = null

    private var messageFromView: (() -> String)? = null

    private val _uiEvents = MutableSharedFlow<EditPostUiEvent>()
    val uiEvents: SharedFlow<EditPostUiEvent> = _uiEvents.asSharedFlow()

    /**
     * SharedFlow без replay молча роняет событие, если подписчиков ещё нет.
     * [start] вызывается из onViewCreated, а collect стартует только с onStart
     * (repeatOnLifecycle(STARTED)); scope работает на Main.immediate, поэтому первый эмит
     * выполнялся синхронно и терялся. Симптом: вложения, переданные из компактной панели темы
     * в полноэкранный редактор, не доезжали до панелей (текст выживал отдельным путём —
     * pendingInitialMessage, вложения приходят только из ShowForm).
     */
    private suspend fun emitEvent(event: EditPostUiEvent) {
        if (_uiEvents.subscriptionCount.value == 0) {
            _uiEvents.subscriptionCount.first { it > 0 }
        }
        _uiEvents.emit(event)
    }

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
                Timber.e("TYPE_EDIT_POST has invalid post id, skip load")
                scope.launch { emitEvent(EditPostUiEvent.ShowForm(postForm)) }
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
                Timber.d("warm snapshot postId=${postForm.postId} attachReady=${snap.attachments != null}")
            } else {
                scope.launch { emitEvent(EditPostUiEvent.ShowEditLoadPlaceholder) }
            }
            loadForm(keepWarmPrefill = snap != null)
        } else {
            scope.launch {
                // Восстановление персистентного черновика: только если из темы не пришёл явный текст
                // (цитата/ответ). Ключ по topicId — «недописанный ответ» переживает перезапуск приложения.
                if (postForm.message.isEmpty()) {
                    draftKey()?.let { key ->
                        runCatching { postDraftRepository.load(key) }.getOrNull()?.let { saved ->
                            postForm.message = saved
                        }
                    }
                }
                emitEvent(EditPostUiEvent.ShowForm(postForm))
            }
        }
    }

    private fun draftKey(): String? =
        if (postForm.type == EditPostForm.TYPE_NEW_POST && postForm.topicId > 0) {
            PostDraftRepository.topicKey(postForm.topicId)
        } else {
            null
        }

    /** Дебаунс-сохранение черновика (вызывается из View на каждое изменение текста). */
    fun persistDraft(message: String) {
        val key = draftKey() ?: return
        draftSaveJob?.cancel()
        draftSaveJob = scope.launch {
            kotlinx.coroutines.delay(DRAFT_SAVE_DEBOUNCE_MS)
            runCatching { postDraftRepository.save(key, message.trim(), System.currentTimeMillis()) }
        }
    }

    /** Убрать персистентный черновик (после успешной отправки / осознанного выхода / синка в тему). */
    fun clearPersistedDraft() {
        val key = draftKey() ?: return
        draftSaveJob?.cancel()
        scope.launch { runCatching { postDraftRepository.clear(key) } }
    }

    override fun onCleared() {
        cancelEditLoadSafetyTimeout()
        editLoadJob?.cancel()
        uploadJob?.cancel()
        messageFromView = null
        super.onCleared()
    }

    private fun scheduleEditLoadSafetyTimeout() {
        cancelEditLoadSafetyTimeout()
        editLoadSafetyJob = scope.launch {
            kotlinx.coroutines.delay(EDIT_LOAD_SAFETY_MS)
            if (!editLoadState.formDone) {
                Timber.e(
                        "edit load safety timeout ${EDIT_LOAD_SAFETY_MS}ms"
                )
                errorHandler.handle(TimeoutException("Загрузка формы редактирования"))
                postForm.message = ""
                scope.launch { emitEvent(EditPostUiEvent.ShowForm(postForm)) }
            }
        }
    }

    private fun cancelEditLoadSafetyTimeout() {
        editLoadSafetyJob?.cancel()
        editLoadSafetyJob = null
    }

    fun sendMessage(message: String, attachments: List<AttachmentItem>) {
        // Защита от повторной отправки: на медленной сети пользователь не видит реакции и жмёт
        // «Отправить» повторно — без гарда каждый клик уходил отдельным sendPost и IPB создавал дубль.
        if (sendJob?.isActive == true) {
            Timber.d("sendMessage ignored: send already in progress")
            return
        }
        postForm.message = message
        postForm.attachments.clear()
        for (item in attachments) {
            postForm.addAttachment(item)
        }
        sendJob = scope.launch {
            scope.launch { emitEvent(EditPostUiEvent.SetSendProgress(true)) }
            val sentAtMillis = System.currentTimeMillis()
            runCatching {
                val page = editorRepository.sendPost(postForm)
                themeTemplate.mapEntity(page)
            }.onSuccess { mapped ->
                if (postForm.type == EditPostForm.TYPE_NEW_POST && authHolder.get().isAuth()) {
                    runCatching {
                        favoritesRepository.syncSubmittedTopicLastPost(
                                topicId = postForm.topicId,
                                currentUserId = authHolder.get().userId,
                                currentUserNick = userHolder.user?.nick,
                                sentAtMillis = sentAtMillis,
                                page = mapped
                        )
                    }.onFailure { errorHandler.handle(it) }
                }
                editorRepository.invalidateEditCache(postForm.postId)
                // После редактирования иногда теряются якорь/HTML скролла; явно привязываемся к postId.
                if (postForm.type == EditPostForm.TYPE_EDIT_POST && postForm.postId > 0) {
                    val tid = mapped.id.takeIf { it > 0 } ?: postForm.topicId
                    if (tid > 0) {
                        mapped.url =
                                "https://4pda.to/forum/index.php?showtopic=$tid&view=findpost&p=${postForm.postId}"
                    }
                    mapped.anchors.clear()
                    mapped.addAnchor("entry${postForm.postId}")
                    mapped.anchorPostId = postForm.postId.toString()
                    themeTemplate.mapEntity(mapped)
                }
                clearPersistedDraft()
                scope.launch { emitEvent(EditPostUiEvent.OnPostSend(mapped, postForm)) }
            }.onFailure {
                // Ошибка отправки — снимаем прогресс и разблокируем кнопку для повторной попытки.
                // (При успехе экран закрывается через OnPostSend, отдельно прогресс гасить не нужно.)
                scope.launch { emitEvent(EditPostUiEvent.SetSendProgress(false)) }
                errorHandler.handle(it)
            }
        }
    }

    fun loadForm(keepWarmPrefill: Boolean = false) {
        editLoadJob?.cancel()
        if (!keepWarmPrefill) {
            editLoadState = EditLoadState()
        }
        scheduleEditLoadSafetyTimeout()
        editLoadJob = scope.launch {
            try {
                val form = editorRepository.loadForm(postForm.postId)
                cancelEditLoadSafetyTimeout()
                try {
                    editLoadState.form = form
                    editLoadState.formDone = true
                    // Если форма уже принесла вложения — пропускаем отдельный attach-init запрос
                    // (согласно EditPostApi.loadForm, attach init часто пустой, а форма даёт список
                    // существующих вложений). Экономит HTTP-запрос на медленном сервере 4pda.
                    if (form.errorCode == EditPostForm.ERROR_NONE && form.attachments.isNotEmpty()) {
                        editLoadState.attachments = emptyList()
                        editLoadState.attachDone = true
                    }
                    applyEditLoadMerge()
                    if (form.errorCode == EditPostForm.ERROR_NONE && !editLoadState.attachDone) {
                        loadEditAttachmentsIfNeeded()
                    }
                } catch (e: Throwable) {
                    Timber.e(e, "applyEditLoadMerge failed")
                    errorHandler.handle(e)
                    postForm.message = ""
                    scope.launch { emitEvent(EditPostUiEvent.ShowForm(postForm)) }
                }
            } catch (e: Throwable) {
                cancelEditLoadSafetyTimeout()
                Timber.e(e, "loadForm error")
                errorHandler.handle(e)
                postForm.message = ""
                scope.launch { emitEvent(EditPostUiEvent.ShowForm(postForm)) }
            }
        }
    }

    private suspend fun loadEditAttachmentsIfNeeded() {
        if (editLoadState.attachDone) return
        try {
            val list = editorRepository.loadEditAttachments(postForm.postId)
            editLoadState.attachments = list
            editLoadState.attachDone = true
            applyEditLoadMerge()
        } catch (_: Throwable) {
            editLoadState.attachments = emptyList()
            editLoadState.attachDone = true
            applyEditLoadMerge()
        }
    }

    private fun applyEditLoadMerge() {
        val state = editLoadState
        val form = state.form
        if (!state.formDone || form == null) return

        if (form.errorCode != EditPostForm.ERROR_NONE) {
            postForm.errorCode = form.errorCode
            scope.launch { emitEvent(EditPostUiEvent.ShowForm(postForm)) }
            return
        }

        val serverAttachments = if (state.attachDone) state.attachments.orEmpty() else emptyList()

        postForm.errorCode = EditPostForm.ERROR_NONE
        val localDraft = messageFromView?.invoke()?.takeIf { it.isNotBlank() } ?: postForm.message
        val mergedMessage = mergeEditPostMessage(form.message, localDraft)
        if (state.attachDone) {
            Timber.d(
                    "loadForm ok postId=%d serverLen=%d prefilledLen=%d mergedLen=%d attachCount=%d mergedPreview=%s",
                    postForm.postId,
                    form.message.length,
                    localDraft.length,
                    mergedMessage.length,
                    serverAttachments.size,
                    mergedMessage.replace("\r\n", "\n").replace("\n", "↵").take(200)
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
        if (postForm.type == EditPostForm.TYPE_EDIT_POST && editSessionBaselineAttachmentIds == null) {
            editSessionBaselineAttachmentIds =
                    postForm.attachments.mapNotNull { it.id.takeIf { id -> id > 0 } }.toSet()
        }
        scope.launch { emitEvent(EditPostUiEvent.ShowForm(postForm)) }
    }

    fun uploadFiles(files: List<RequestFile>, pending: List<AttachmentItem>) {
        uploadJob?.cancel()
        uploadJob = scope.launch {
            runCatching { editorRepository.uploadFiles(postForm.postId, files, pending) }
                    .onSuccess { merged ->
                        for (item in merged) {
                            if (item.id > 0 && postForm.attachments.none { existing -> existing.id == item.id }) {
                                postForm.attachments.add(item)
                            }
                        }
                        scope.launch { emitEvent(EditPostUiEvent.OnUploadFiles(merged)) }
                    }
                    .onFailure { errorHandler.handle(it) }
        }
    }

    fun cancelPendingUploads() {
        uploadJob?.cancel()
        uploadJob = null
    }

    private fun transientUploadedForDiscard(uiSnapshot: List<AttachmentItem>): List<AttachmentItem> =
            when (postForm.type) {
                EditPostForm.TYPE_EDIT_POST -> {
                    val baseline = editSessionBaselineAttachmentIds ?: emptySet()
                    uiSnapshot.filter { it.id > 0 && it.id !in baseline }
                }
                else -> uiSnapshot.filter { it.id > 0 }
            }

    suspend fun discardTransientUploads(uiSnapshot: List<AttachmentItem>) {
        cancelPendingUploads()
        val orphans = transientUploadedForDiscard(uiSnapshot)
        if (orphans.isEmpty()) return
        runCatching { editorRepository.deleteFiles(postForm.postId, orphans) }
    }

    /** Вызывается при уничтожении без сохранения — очередь ViewModel уже отменена, удаление с сервера best-effort. */
    fun scheduleDiscardTransientUploads(uiSnapshot: List<AttachmentItem>) {
        cancelPendingUploads()
        val orphans = transientUploadedForDiscard(uiSnapshot)
        if (orphans.isEmpty()) return
        val postId = postForm.postId
        scope.launch(Dispatchers.IO) {
            runCatching { editorRepository.deleteFiles(postId, orphans) }
                    .onFailure { errorHandler.handle(it) }
        }
    }

    fun deleteFiles(items: List<AttachmentItem>) {
        scope.launch {
            runCatching { editorRepository.deleteFiles(postForm.postId, items) }
                    .onSuccess { scope.launch { emitEvent(EditPostUiEvent.OnDeleteFiles(it)) } }
                    .onFailure { errorHandler.handle(it) }
            scope.launch { emitEvent(EditPostUiEvent.OnAttachmentDeleteProgressFinished) }
        }
    }

    fun onSendClick() {
        if (postForm.type == EditPostForm.TYPE_EDIT_POST) {
            scope.launch { emitEvent(EditPostUiEvent.ShowReasonDialog(postForm)) }
        } else {
            scope.launch { emitEvent(EditPostUiEvent.SendMessage) }
        }
    }

    fun onReasonEdit(reason: String) {
        postForm.editReason = reason
        scope.launch { emitEvent(EditPostUiEvent.SendMessage) }
    }

    fun exit() {
        // Осознанный выход (в т.ч. discard непустого черновика) — убираем персистентную копию.
        // Синк обратно в тему (exitWithSync) НЕ чистит: текст остаётся жить в панели темы,
        // а БД служит бэкапом на случай последующего аварийного завершения.
        clearPersistedDraft()
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

sealed class EditPostUiEvent {
    data class ShowForm(val form: EditPostForm) : EditPostUiEvent()
    data class ShowEditLoadingDraft(val form: EditPostForm) : EditPostUiEvent()
    object ShowEditLoadPlaceholder : EditPostUiEvent()
    data class OnPostSend(val page: ThemePage, val form: EditPostForm) : EditPostUiEvent()
    data class OnUploadFiles(val files: List<AttachmentItem>) : EditPostUiEvent()
    data class OnDeleteFiles(val items: List<AttachmentItem>) : EditPostUiEvent()
    object OnAttachmentDeleteProgressFinished : EditPostUiEvent()
    data class ShowReasonDialog(val form: EditPostForm) : EditPostUiEvent()
    object SendMessage : EditPostUiEvent()
    data class SetSendProgress(val active: Boolean) : EditPostUiEvent()
}
