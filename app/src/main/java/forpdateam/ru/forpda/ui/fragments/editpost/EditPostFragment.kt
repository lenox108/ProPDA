package forpdateam.ru.forpda.ui.fragments.editpost
import timber.log.Timber

import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.text.Editable
import android.os.SystemClock
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.os.BundleCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import forpdateam.ru.forpda.common.showSnackbar
import forpdateam.ru.forpda.common.simple.SimpleTextWatcher

import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.doOnPreDraw
import androidx.fragment.app.viewModels

import forpdateam.ru.forpda.R
import forpdateam.ru.forpda.common.FilePickHelper
import forpdateam.ru.forpda.entity.remote.editpost.AttachmentItem
import forpdateam.ru.forpda.entity.remote.editpost.EditPostForm
import forpdateam.ru.forpda.entity.remote.theme.ThemePage
import forpdateam.ru.forpda.model.data.remote.api.RequestFile
import forpdateam.ru.forpda.common.normalizeEditPostBodyForEditor
import forpdateam.ru.forpda.common.normalizeEditPostBodyFromDomHtml
import forpdateam.ru.forpda.presentation.editpost.EditPostUiEvent
import forpdateam.ru.forpda.presentation.editpost.EditPostViewModel
import forpdateam.ru.forpda.ui.fragments.TabFragment
import forpdateam.ru.forpda.ui.views.CodeEditor
import forpdateam.ru.forpda.ui.views.dialog.showWithStyledButtons
import forpdateam.ru.forpda.ui.views.messagepanel.MessagePanel
import forpdateam.ru.forpda.ui.views.messagepanel.advanced.AdvancedPopup
import forpdateam.ru.forpda.ui.views.messagepanel.attachments.AttachmentsPopup
import dagger.hilt.android.AndroidEntryPoint
import forpdateam.ru.forpda.model.preferences.MainPreferencesHolder
import forpdateam.ru.forpda.model.preferences.OtherPreferencesHolder
import javax.inject.Inject
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch

/**
 * Created by radiationx on 14.01.17.
 */

@AndroidEntryPoint
class EditPostFragment : TabFragment() {

    @Inject lateinit var mainPreferencesHolder: MainPreferencesHolder
    @Inject lateinit var otherPreferencesHolder: OtherPreferencesHolder

    private val pickFileLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@registerForActivityResult
        val data = result.data ?: return@registerForActivityResult
        uploadFiles(FilePickHelper.onActivityResult(requireContext(), data))
    }

    private var formType = 0

    private lateinit var fullPanel: MessagePanel
    private lateinit var compactPanel: MessagePanel
    private var isCompactMode = false
    private val uploadQueue: ArrayDeque<Pair<List<RequestFile>, List<AttachmentItem>>> = ArrayDeque()
    private var uploadInProgress = false
    private var pollPopup: EditPollPopup? = null
    private var activeUploadPopup: AttachmentsPopup? = null
    private var activeDeletePopup: AttachmentsPopup? = null
    private var fullscreenImeRequestId = 0
    private var fullscreenPreviousSoftInputMode: Int? = null

    /** false пока пользователь не ушёл осознанно (сохранение, синхронизация, явный discard). */
    private var exitedCleanly = false

    /** Последний снимок вложений для best-effort discard после уничтожения без сохранения. */
    private var attachmentsSnapshotCache: List<AttachmentItem> = emptyList()
    /** IME bottom on [fullPanel] insets; used to detect hide (e.g. back gesture) vs never-shown. */
    private var lastFullPanelImeInsetBottom = 0
    /** After compact→full sync; IME retry uses this instead of forcing caret to EOF. */
    private var pendingFullscreenSelection: IntArray? = null
    /**
     * Зеркало текста из обоих [CodeEditor] (afterTextChanged). На части прошивок при compact→full
     * буфер compact может кратковременно читаться пустым после фокуса/IME на полном поле — копирование
     * только из [compactPanel.messageField] теряет черновик; зеркало остаётся источником правды.
     */
    private var draftContentMirror: String = ""
    /** После первой успешной [showForm] для TYPE_EDIT_POST — для сравнения «грязности». */
    private var editBaselineEstablished = false
    private var baselineEditMessage: String = ""
    private var baselineAttachmentSignature: String = ""
    private var initialSelectionRange: IntArray? = null
    private var initialBodyFromArgs: String = ""
    private var pendingInitialMessage: String? = null
    private var clearEditorTextDialog: AlertDialog? = null
    // Однократный авто-фокус и показ IME при первом отображении формы
    // (фрагмент сразу открывается полноэкранным; без этого клавиатура не появлялась).
    // Сохраняется в instance state, чтобы при пересоздании процесса не «угонять»
    // фокус повторно после восстановления состояния пользователем.
    private var autoFocusedOnOpen = false

    private val presenter: EditPostViewModel by viewModels()

    override fun topBarSurfaceColorAttr(): Int = R.attr.main_toolbar_accent_surface

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState != null) {
            autoFocusedOnOpen = savedInstanceState.getBoolean(STATE_AUTO_FOCUSED_ON_OPEN, false)
        }
        arguments?.apply {
            val postForm = EditPostForm()
            postForm.type = getInt(EditPostForm.ARG_TYPE)
            formType = postForm.type
            BundleCompat.getParcelableArrayList(this, ARG_ATTACHMENTS, AttachmentItem::class.java)?.also {
                postForm.attachments.addAll(it)
            }
            var initialMessage = getString(ARG_MESSAGE, "")
            getString(ARG_INITIAL_BODY, null)?.takeIf { it.isNotBlank() }?.let { raw ->
                initialMessage = if (raw.contains('<') && raw.contains('>')) {
                    normalizeEditPostBodyFromDomHtml(raw)
                } else {
                    normalizeEditPostBodyForEditor(raw)
                }
            }
            postForm.message = initialMessage
            initialBodyFromArgs = initialMessage
            pendingInitialMessage = initialMessage.takeIf {
                postForm.type == EditPostForm.TYPE_NEW_POST && it.isNotEmpty()
            }
            val selectionStart = getInt(ARG_SELECTION_START, -1)
            val selectionEnd = getInt(ARG_SELECTION_END, -1)
            if (selectionStart >= 0 && selectionEnd >= 0) {
                initialSelectionRange = intArrayOf(selectionStart, selectionEnd)
                pendingFullscreenSelection = initialSelectionRange
            }
            Timber.d(
                "fragmentArgs len=${postForm.message.length}" +
                    " stage=onCreate" +
                    " attachments=${postForm.attachments.size}" +
                    " selection=${initialSelectionRange?.getOrNull(0)}..${initialSelectionRange?.getOrNull(1)}"
            )
            postForm.forumId = getInt(ARG_FORUM_ID)
            postForm.topicId = getInt(ARG_TOPIC_ID)
            postForm.postId = getInt(ARG_POST_ID)
            postForm.st = getInt(ARG_ST)
            presenter.initPostForm(postForm)
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        super.onCreateView(inflater, container, savedInstanceState)
        // Полный редактор — в fragment_content (ниже AppBar).
        fullPanel = MessagePanel(requireContext(), fragmentContainer, fragmentContent, true, mainPreferencesHolder, dimensionsProvider, otherPreferencesHolder)
        // Компактный редактор — внизу (message_panel_host).
        compactPanel = MessagePanel(requireContext(), fragmentContainer, messagePanelHost, false, mainPreferencesHolder, dimensionsProvider, otherPreferencesHolder).also {
            it.visibility = View.GONE
        }
        presenter.attachMessageSource {
            val raw = currentPanel()?.messageField?.text?.toString() ?: ""
            if (raw.isNotEmpty()) raw else draftContentMirror
        }
        return viewFragment
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Timber.d("onViewCreated.enter" +
                " formType=$formType" +
                " isNewPost=${formType == EditPostForm.TYPE_NEW_POST}" +
                " autoFocusedOnOpen=$autoFocusedOnOpen" +
                " savedInstanceState=${savedInstanceState != null}"
        )
        setupPanel(fullPanel)
        setupPanel(compactPanel)
        installDraftMirrorWatchers()
        Timber.d(
            "fragmentArgs len=${initialBodyFromArgs.length}" +
                " stage=onViewCreated" +
                " pending=${pendingInitialMessage?.length ?: 0}"
        )
        pendingInitialMessage?.let { message ->
            setDraftToPanels(message)
            applyInitialSelectionToPanels(message.length)
            pendingInitialMessage = null
        }
        arguments?.apply {
            val title = getString(ARG_THEME_NAME, "")
            setTitle("${getString(if (formType == EditPostForm.TYPE_NEW_POST) R.string.editpost_title_answer else R.string.editpost_title_edit)} $title")
        }

        fullPanel.messageField?.hint = null
        compactPanel.messageField?.hint = null

        observeViewModel()
        presenter.start()

        // TYPE_NEW_POST («Ответ …») — форма приходит из ViewModel асинхронно и в коротко-живущем
        // фрагменте ShowForm может не успеть прийти до onDestroy. Поэтому дублируем авто-фокус
        // прямо после setup'а панелей: triggerAutoFocusOnFirstOpenIfNeeded() сам охраняется
        // флагом autoFocusedOnOpen, так что повторного «угона» фокуса не будет.
        Timber.d("onViewCreated.triggerAutoFocus pre formType=$formType")
        triggerAutoFocusOnFirstOpenIfNeeded()

        // Фикс перекрытия клавиатурой на OEM устройствах (Oplus/Realme/Oppo и др.),
        // где setDecorFitsSystemWindows(true) не вычитает IME из окна — fragmentsContainer
        // остаётся полной высоты, fullPanel уходит под клавиатуру.
        // Считаем реальное перекрытие в координатах окна и компенсируем через paddingBottom
        // у внутреннего LinearLayout CardView. Если decor уже сжал окно — перекрытия нет
        // и padding = 0, без двойного смещения.
        ViewCompat.setOnApplyWindowInsetsListener(fullPanel) { _, insets ->
            applyFullPanelImeFix(insets)
            if (!isCompactMode && fullPanel.visibility == View.VISIBLE) {
                val bottom = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
                if (lastFullPanelImeInsetBottom > 0 && bottom == 0) {
                    fullPanel.cancelDeferredKeyboardShowRequests()
                    if (fullscreenPreviousSoftInputMode != null) {
                        restoreFullscreenImeWindowMode()
                    }
                }
                lastFullPanelImeInsetBottom = bottom
            } else if (isCompactMode) {
                lastFullPanelImeInsetBottom = 0
            }
            insets
        }
        // Пересчитываем overlap при изменении размеров панели (после layout, при смене видимости
        // progress spinner, при изменении сочетания insets и пр.). Защита от цикла —
        // setPadding вызывается только при реальном изменении значения.
        fullPanel.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            applyFullPanelImeFix()
        }
        // Панель BBCode/смайлов (advanced_popup_host) перекрывает низ редактора, но не даёт IME-инсета,
        // а размеры самого fullPanel при её показе не меняются — слой выше не дёрнет layout listener.
        // Поэтому пересчитываем отступ явно по событиям показа/скрытия панели (+ после её анимации).
        fullPanel.setAdvancedPanelStateListener(object : AdvancedPopup.StateListener {
            override fun onShow() {
                applyFullPanelImeFix()
                fullPanel.post { applyFullPanelImeFix() }
                fullPanel.postDelayed({ applyFullPanelImeFix() }, 220L)
            }

            override fun onHide() {
                applyFullPanelImeFix()
                fullPanel.post { applyFullPanelImeFix() }
                fullPanel.postDelayed({ applyFullPanelImeFix() }, 220L)
            }
        })
        // Полный редактор в fragment_content — не держим IME margin у пустого message_panel_host.
        syncMessagePanelImeWithDimensions()
    }

    override fun shouldApplyMessagePanelImeInsets(): Boolean = isCompactMode

    private fun applyFullPanelImeFix(insetsArg: WindowInsetsCompat? = null) {
        if (!isAdded || !::fullPanel.isInitialized) return
        val activity = activity ?: return
        val insets = insetsArg ?: ViewCompat.getRootWindowInsets(fullPanel) ?: return
        val ime = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
        val inner = (fullPanel as? ViewGroup)?.getChildAt(0) ?: return
        // Перекрытие клавиатурой (если она поднята).
        val imeOverlap = if (ime > 0) {
            val loc = IntArray(2)
            fullPanel.getLocationInWindow(loc)
            val panelBottomInWindow = loc[1] + fullPanel.height
            val windowHeight = activity.window.decorView.height
            val visibleBottom = windowHeight - ime
            (panelBottomInWindow - visibleBottom).coerceAtLeast(0)
        } else {
            0
        }
        // Перекрытие встроенной панелью BBCode/смайлов. advanced_popup_host и fragment_content
        // (где живёт fullPanel) выровнены по одному нижнему краю, поэтому overlap == высоте панели.
        // IME и панель одновременно не показываются (при открытии панели клавиатура скрывается),
        // поэтому берём максимум, а не сумму.
        val advancedOverlap = fullPanel.fullFormAdvancedReservedHeight
        val overlap = maxOf(imeOverlap, advancedOverlap)
        if (inner.paddingBottom != overlap) {
            inner.setPadding(inner.paddingLeft, inner.paddingTop, inner.paddingRight, overlap)
        }
    }

    override fun onDestroyView() {
        restoreFullscreenImeWindowMode()
        clearEditorTextDialog?.dismiss()
        clearEditorTextDialog = null
        presenter.attachMessageSource(null)
        super.onDestroyView()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(STATE_AUTO_FOCUSED_ON_OPEN, autoFocusedOnOpen)
    }

    override fun onResumeOrShow() {
        super.onResumeOrShow()
        Timber.d("onResumeOrShow.enter" +
                " formType=$formType" +
                " autoFocusedOnOpen=$autoFocusedOnOpen" +
                " isCompactMode=$isCompactMode" +
                " fullPanelInit=${::fullPanel.isInitialized}"
        )
        if (::fullPanel.isInitialized) {
            fullPanel.onResume()
        }
        if (::compactPanel.isInitialized) {
            compactPanel.onResume()
        }
        // Гарантированный путь авто-фокуса для TYPE_NEW_POST: ShowForm может не успеть прийти
        // (форма грузится асинхронно), но onResumeOrShow точно срабатывает по логам устройства.
        // Сам метод охраняется autoFocusedOnOpen, поэтому при повторных show/hide IME не угоняем.
        Timber.d("onResumeOrShow.triggerAutoFocus pre formType=$formType")
        triggerAutoFocusOnFirstOpenIfNeeded()
    }

    override fun onPauseOrHide() {
        super.onPauseOrHide()
        restoreFullscreenImeWindowMode()
        if (::fullPanel.isInitialized) {
            fullPanel.onPause()
        }
        if (::compactPanel.isInitialized) {
            compactPanel.onPause()
        }
    }

    override fun onDestroy() {
        if (!exitedCleanly) {
            uploadQueue.clear()
            uploadInProgress = false
            presenter.cancelPendingUploads()
            presenter.scheduleDiscardTransientUploads(attachmentsSnapshotCache)
        }
        super.onDestroy()
        if (::fullPanel.isInitialized) {
            fullPanel.onDestroy()
        }
        if (::compactPanel.isInitialized) {
            compactPanel.onDestroy()
        }
    }

    override fun hideKeyboard() {
        super.hideKeyboard()
        if (::fullPanel.isInitialized) {
            fullPanel.hidePopupWindows()
        }
        if (::compactPanel.isInitialized) {
            compactPanel.hidePopupWindows()
        }
    }

    override fun onBackPressed(): Boolean {
        super.onBackPressed()
        if (currentPanel()?.onBackPressed() == true)
            return true

        if (formType == EditPostForm.TYPE_EDIT_POST) {
            if (!isEditDraftDirty()) return false
            showEditUnsavedChangesDialog()
            return true
        }

        //Синхронизация с полем в фрагменте темы
        if (formType == EditPostForm.TYPE_NEW_POST) {
            showSyncDialog()
            return true
        }
        return false
    }

    // Экран написания/редактирования поста практически всегда перехватывает
    // «назад» (панель BB-кодов, подтверждение потери черновика) и открывается
    // над списком/темой — одиночной корневой вкладкой не бывает. Консервативно
    // true: не показываем «домой»-анимацию отсюда и не рискуем draft-логикой.
    override fun hasBackHandling(): Boolean = true

    private fun tryPickFile() {
        // ACTION_GET_CONTENT / Open Document не требует WRITE_EXTERNAL_STORAGE; на API 33+ это разрешение не выдаётся.
        FilePickHelper.showAttachChooser(requireContext()) { intent -> pickFileLauncher.launch(intent) }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    presenter.uiEvents.collect { event ->
                        handleUiEvent(event)
                    }
                }
            }
        }
    }

    private fun handleUiEvent(event: EditPostUiEvent) {
        Timber.d("handleUiEvent ${event.javaClass.simpleName} formType=$formType")
        when (event) {
            is EditPostUiEvent.ShowForm -> showForm(event.form)
            is EditPostUiEvent.ShowEditLoadingDraft -> showEditLoadingDraft(event.form)
            is EditPostUiEvent.ShowEditLoadPlaceholder -> showEditLoadPlaceholder()
            is EditPostUiEvent.OnPostSend -> onPostSend(event.page, event.form)
            is EditPostUiEvent.OnUploadFiles -> onUploadFiles(event.files)
            is EditPostUiEvent.OnDeleteFiles -> onDeleteFiles(event.items)
            is EditPostUiEvent.OnAttachmentDeleteProgressFinished -> onAttachmentDeleteProgressFinished()
            is EditPostUiEvent.ShowReasonDialog -> showReasonDialog(event.form)
            is EditPostUiEvent.SendMessage -> sendMessage()
        }
    }

    private fun showEditLoadPlaceholder() {
        hideKeyboard()
        currentPanel()?.messageField?.clearFocus()
        setDraftToPanels("")
        (currentPanel()?.messageField as? CodeEditor)?.updateHighlighting()
        fullPanel.editPollButton?.visibility = View.GONE
        fullPanel.formProgress?.visibility = View.VISIBLE
        compactPanel.formProgress?.visibility = View.VISIBLE
        setAttachmentsToPanels(emptyList())
        currentPanel()?.messageField?.visibility = View.INVISIBLE
    }

    private fun showEditLoadingDraft(form: EditPostForm) {
        hideKeyboard()
        currentPanel()?.messageField?.clearFocus()
        setDraftToPanels(form.message)
        (currentPanel()?.messageField as? CodeEditor)?.updateHighlighting()
        fullPanel.editPollButton?.visibility = View.GONE
        setAttachmentsToPanels(
                mergeEditorAttachments(form.attachments, currentAttachmentsPopup()?.getAttachments().orEmpty())
        )
        currentPanel()?.messageField?.visibility = View.VISIBLE
    }

    private fun showForm(form: EditPostForm) {
        Timber.d("showForm.enter" +
                " formType=$formType" +
                " errorCode=${form.errorCode}" +
                " messageLen=${form.message.length}" +
                " attachments=${form.attachments.size}" +
                " hasPoll=${form.poll != null}" +
                " autoFocusedOnOpen=$autoFocusedOnOpen"
        )
        currentPanel()?.messageField?.visibility = View.VISIBLE
        // TYPE_EDIT_POST заполняется только после загрузки серверной textarea, чтобы не показывать
        // частичный DOM-текст до полной формы редактирования.
        fullPanel.formProgress?.visibility = View.GONE
        compactPanel.formProgress?.visibility = View.GONE

        if (form.errorCode != EditPostForm.ERROR_NONE) {
            showSnackbar(R.string.editpost_error_edit)
            exitedCleanly = true
            presenter.exit()
            return
        }

        val poll = form.poll
        if (poll != null) {
            pollPopup = EditPollPopup(requireContext())
            pollPopup?.setPoll(poll)
            if (::fullPanel.isInitialized) {
                fullPanel.editPollButton?.visibility = View.VISIBLE
                fullPanel.formProgress?.visibility = View.GONE
            }
        } else {
            fullPanel.editPollButton?.visibility = View.GONE
        }

        val messageForPanels = resolveMessageForShowForm(form)
        setAttachmentsToPanels(
                mergeEditorAttachments(form.attachments, currentAttachmentsPopup()?.getAttachments().orEmpty())
        )
        setDraftToPanels(messageForPanels)
        applyInitialSelectionToPanels(messageForPanels.length)
        (currentPanel()?.messageField as? CodeEditor)?.updateHighlighting()

        // Однократный авто-фокус + IME при первом открытии полноэкранного редактора
        // (типичный кейс — «Ответ …» из темы). Используем тот же надёжный путь,
        // что и при разворачивании компактной панели в полноэкранную.
        triggerAutoFocusOnFirstOpenIfNeeded()

        currentPanel()?.messageField?.let { field ->
            field.post {
                if (isAdded && field.isAttachedToWindow) {
                    // IME анимируется ~250 ms; после окончания insets стабильны и overlap точен.
                    applyFullPanelImeFix()
                }
            }
            field.postDelayed({ applyFullPanelImeFix() }, 350)
        }

        captureEditBaselineFromUiOnce()
    }

    private fun resolveMessageForShowForm(form: EditPostForm): String {
        if (formType != EditPostForm.TYPE_NEW_POST || form.message.isNotEmpty()) {
            if (form.message.isNotEmpty()) pendingInitialMessage = null
            return form.message
        }
        val currentDraft = currentDraftForPreservation()
        val preserved = currentDraft.ifEmpty { pendingInitialMessage.orEmpty() }
        return if (preserved.isNotEmpty()) {
            Timber.d(
                "showForm.preserveInitial emptyForm=true" +
                    " currentLen=${currentDraft.length}" +
                    " pendingLen=${pendingInitialMessage?.length ?: 0}" +
                    " preservedLen=${preserved.length}"
            )
            pendingInitialMessage = null
            preserved
        } else {
            ""
        }
    }

    private fun currentDraftForPreservation(): String {
        val panelText = currentPanel()?.messageField?.text?.toString().orEmpty()
        return panelText.ifEmpty { draftContentMirror }
    }

    /**
     * При первом отображении формы (TYPE_NEW_POST «Ответ …», либо TYPE_EDIT_POST после загрузки
     * черновика) — поднимаем IME на полноэкранном редакторе теми же помощниками, что и при
     * переключении из компактного режима в полноэкранный. Дальше уже не вмешиваемся, чтобы
     * не «угонять» фокус при последующих эмитах ShowForm.
     */
    private fun triggerAutoFocusOnFirstOpenIfNeeded() {
        Timber.d("autoFocus.enter" +
                " autoFocusedOnOpen=$autoFocusedOnOpen" +
                " isAdded=$isAdded" +
                " isCompactMode=$isCompactMode" +
                " fullPanelInit=${::fullPanel.isInitialized}" +
                " fullPanel.vis=${if (::fullPanel.isInitialized) fullPanel.visibility else -1}"
        )
        if (autoFocusedOnOpen) {
            Timber.d("autoFocus.skip alreadyDone")
            return
        }
        if (!isAdded || isCompactMode) {
            Timber.d("autoFocus.skip notAddedOrCompact")
            return
        }
        if (!::fullPanel.isInitialized) {
            Timber.d("autoFocus.skip fullPanelNotInit")
            return
        }
        if (fullPanel.visibility != View.VISIBLE) {
            Timber.d("autoFocus.skip fullPanelNotVisible vis=${fullPanel.visibility}")
            return
        }
        autoFocusedOnOpen = true
        Timber.d("autoFocus.proceed -> focusFullEditorAndShowKeyboard()")
        focusFullEditorAndShowKeyboard()
        // Курсор в конец текста — гарантия видимости каретки сразу после показа IME.
        val field = fullPanel.messageField
        field.post {
            if (!isAdded || !field.isAttachedToWindow) {
                Timber.d("autoFocus.postSetSelection skip notAttached")
                return@post
            }
            val length = field.text?.length ?: 0
            if (length >= 0) {
                field.setSelection(length)
            }
            Timber.d("autoFocus.postSetSelection" +
                    " length=$length" +
                    " field.isFocused=${field.isFocused}" +
                    " field.hasWindowFocus=${field.hasWindowFocus()}"
            )
        }
    }

    override fun setRefreshing(isRefreshing: Boolean) {
        super.setRefreshing(isRefreshing)
        // formProgress управляется через setFormLoading() в showEditLoadPlaceholder/showEditLoadingDraft/showForm.
        currentPanel()?.messageField?.visibility = View.VISIBLE
    }

    private fun setSendRefreshing(isRefreshing: Boolean) {
        currentPanel()?.setProgressState(isRefreshing)
    }

    private fun sendMessage() {
        val panel = currentPanel()
        panel?.let { presenter.sendMessage(it.message, it.attachments) }
    }

    private fun onPostSend(page: ThemePage, form: EditPostForm) {
        exitedCleanly = true
        presenter.exitWithPage(page)
    }


    fun uploadFiles(files: List<RequestFile>) {
        val popup = currentAttachmentsPopup() ?: return
        val pending = popup.preUploadFiles(files)
        popup.revealDuringUploadPreview()
        enqueueUpload(files, pending)
    }

    private fun enqueueUpload(files: List<RequestFile>, pending: List<AttachmentItem>) {
        uploadQueue.addLast(files to pending)
        pumpUploadQueue()
    }

    private fun pumpUploadQueue() {
        if (uploadInProgress) return
        val next = uploadQueue.firstOrNull() ?: return
        uploadInProgress = true
        activeUploadPopup = currentAttachmentsPopup()
        presenter.uploadFiles(next.first, next.second)
    }

    private fun removeFiles() {
        val popup = currentAttachmentsPopup() ?: return
        activeDeletePopup = popup
        popup.preDeleteFiles()
        val selectedFiles = popup.getSelected()
        presenter.deleteFiles(selectedFiles)
    }

    private fun onUploadFiles(items: List<AttachmentItem>) {
        (activeUploadPopup ?: currentAttachmentsPopup())?.onUploadFiles(items)
        uploadInProgress = false
        if (uploadQueue.isNotEmpty()) uploadQueue.removeFirst()
        pumpUploadQueue()
        // синхронизируем вложения на вторую панель
        currentAttachmentsPopup()?.let { setAttachmentsToPanels(it.getAttachments()) }
    }

    private fun onDeleteFiles(items: List<AttachmentItem>) {
        (activeDeletePopup ?: currentAttachmentsPopup())?.onDeleteFiles(items)
        currentAttachmentsPopup()?.let { setAttachmentsToPanels(it.getAttachments()) }
    }

    private fun onAttachmentDeleteProgressFinished() {
        (activeDeletePopup ?: currentAttachmentsPopup())?.endDeleteProgress()
    }

    private fun currentPanel(): MessagePanel? {
        if (!::fullPanel.isInitialized && !::compactPanel.isInitialized) return null
        return if (isCompactMode) {
            if (::compactPanel.isInitialized) compactPanel else fullPanel
        } else {
            if (::fullPanel.isInitialized) fullPanel else compactPanel
        }
    }

    private fun currentAttachmentsPopup(): AttachmentsPopup? = currentPanel()?.attachmentsPopup

    private fun setupPanel(panel: MessagePanel) {
        panel.addSendOnClickListener { presenter.onSendClick() }
        panel.setClearMessageClickListener { requestClearEditorText() }
        panel.attachmentsPopup?.setAddOnClickListener { tryPickFile() }
        panel.attachmentsPopup?.setDeleteOnClickListener { removeFiles() }
        panel.attachmentsPopup?.setRetryUploadListener(object : AttachmentsPopup.OnRetryUploadListener {
            override fun onRetry(files: List<RequestFile>, pending: List<AttachmentItem>) {
                enqueueUpload(files, pending)
            }
        })
        panel.fullButton?.also { btn ->
            if (formType == EditPostForm.TYPE_EDIT_POST) {
                // Как раньше: в режиме редактирования поста кнопка “полноэкранно” не нужна.
                btn.visibility = View.GONE
                btn.setOnClickListener(null)
            } else {
                btn.visibility = View.VISIBLE
                btn.setOnClickListener { clickedView ->
                    if (panel === compactPanel) {
                        val activity = activity
                        val currentFocus = activity?.currentFocus
                        Timber.d("fullButton.click compact=true" +
                                " btn=${clickedView.javaClass.simpleName}@${Integer.toHexString(System.identityHashCode(clickedView))}" +
                                " btn.attached=${clickedView.isAttachedToWindow}" +
                                " btn.windowFocus=${clickedView.hasWindowFocus()}" +
                                " currentFocus=${currentFocus?.javaClass?.simpleName}" +
                                " currentFocusId=${currentFocus?.id?.let { Integer.toHexString(it) }}" +
                                " fullPanel.visibility=${fullPanel.visibility}" +
                                " compactPanel.visibility=${compactPanel.visibility}" +
                                " isCompactMode=$isCompactMode" +
                                " keyboardLikelyOpen=${dimensionsProvider.getDimensions().isKeyboardShow()}"
                        )
                        // Android 12+: IMM.showSoftInput работает только в окне пользовательского жеста.
                        // Если показывать IME после смены layout (через post / doOnPreDraw), сервис IME
                        // считает запрос «фоновым» и игнорирует его (наблюдается на OnePlus + Fleksy).
                        // Поэтому поднимаем клавиатуру синхронно прямо в обработчике клика, до того как
                        // toggleCompactMode() поменяет видимость панелей. Пост-layout ретраи остаются
                        // как fallback на случай, если сервис всё-таки отложит показ.
                        showFullscreenImeSynchronouslyOnClick()
                        toggleCompactMode()
                    } else {
                        val selectionRange = panel.selectionRange
                        exitedCleanly = true
                        presenter.exitWithSync(panel.message, selectionRange, panel.attachments)
                    }
                }
            }
        }
        if (panel === compactPanel && formType == EditPostForm.TYPE_NEW_POST) {
            panel.hideButton?.apply {
                visibility = View.VISIBLE
                setOnClickListener { toggleCompactMode() }
            }
        }
        // poll доступен только там, где реально есть кнопка (в compact её нет).
        panel.editPollButton?.setOnClickListener { pollPopup?.show() }
    }

    /**
     * Синхронизирует черновик между двумя независимыми [MessagePanel] / [CodeEditor].
     * При раскрытии компакта сохраняем выделение для IME-ретраев ([pendingFullscreenSelection]).
     */
    /** Явный перенос черновика compact → full (два независимых [CodeEditor]). */
    private fun syncDraftCompactToFull() {
        if (!::compactPanel.isInitialized || !::fullPanel.isInitialized) return
        val fieldOnly = compactPanel.messageField.text?.toString() ?: ""
        val resolved = compactDraftForSync()
        Timber.d(
            "syncCompactToFull fieldLen=${fieldOnly.length} mirrorLen=${draftContentMirror.length} resolvedLen=${resolved.length}"
        )
        copyMessageEditorState(compactPanel, fullPanel, resolved)
    }

    private fun copyMessageEditorState(from: MessagePanel, to: MessagePanel, messageTextOverride: String? = null) {
        val fieldText = from.messageField.text?.toString() ?: ""
        val text = messageTextOverride ?: fieldText
        Timber.d(
            "copyState fromCompact=${from === compactPanel} toFull=${to === fullPanel} fieldLen=${fieldText.length} appliedLen=${text.length} hasOverride=${messageTextOverride != null}"
        )
        to.setText(text)
        val len = text.length
        var selStart = from.messageField.selectionStart
        var selEnd = from.messageField.selectionEnd
        if (selStart < 0 || selStart > len) selStart = len
        if (selEnd < 0 || selEnd > len) selEnd = len
        if (selEnd < selStart) {
            val t = selStart
            selStart = selEnd
            selEnd = t
        }
        if (from === compactPanel && to === fullPanel) {
            pendingFullscreenSelection = intArrayOf(selStart, selEnd)
        } else if (from === fullPanel && to === compactPanel) {
            pendingFullscreenSelection = null
        }
        val fs = selStart
        val fe = selEnd
        to.messageField.post {
            if (!isAdded || !to.messageField.isAttachedToWindow) return@post
            val n = to.messageField.text?.length ?: 0
            var s = fs.coerceIn(0, n)
            var e = fe.coerceIn(0, n)
            if (e < s) e = s
            to.messageField.setSelection(s, e)
        }
    }

    private fun applyPendingFullscreenSelection(field: CodeEditor, length: Int) {
        val pending = pendingFullscreenSelection
        if (pending != null && pending.size == 2) {
            var s = pending[0].coerceIn(0, length)
            var e = pending[1].coerceIn(0, length)
            if (e < s) e = s
            field.setSelection(s, e)
        } else if (length >= 0) {
            field.setSelection(length)
        }
    }

    private fun toggleCompactMode() {
        val from = currentPanel() ?: return
        val to = if (isCompactMode) fullPanel else compactPanel
        val keyboardLikelyOpen = dimensionsProvider.getDimensions().isKeyboardShow()
        val expandingToFull = isCompactMode
        Timber.d("toggle.enter" +
                " expandingToFull=$expandingToFull" +
                " isCompactMode=$isCompactMode" +
                " keyboardLikelyOpen=$keyboardLikelyOpen" +
                " fullPanel.vis=${fullPanel.visibility}" +
                " compactPanel.vis=${compactPanel.visibility}" +
                " messageField.isFocused=${fullPanel.messageField.isFocused}" +
                " windowToken=${fullPanel.windowToken != null}" +
                " attached=${fullPanel.isAttachedToWindow}"
        )
        // Перенос текста, каретки/выделения и вложений (два независимых CodeEditor).
        // compact→full всегда из compactPanel: не полагаемся на currentPanel() + IME/focus
        // могут опустошить «from» между двумя копированиями в одном клике.
        if (expandingToFull) {
            syncDraftCompactToFull()
        } else {
            copyMessageEditorState(from, to)
        }
        to.attachmentsPopup?.setAttachments(from.attachmentsPopup?.getAttachments() ?: emptyList())

        if (expandingToFull) {
            // Компактный BBCode теперь часть layout; закрываем его до смены visibility,
            // чтобы message_panel_host не оставался с высотой панели форматирования.
            compactPanel.hidePopupWindows()
            compactPanel.messageField.clearFocus()
            Timber.d("toggle.afterCompactCleanup" +
                    " compactPanel.messageField.isFocused=${compactPanel.messageField.isFocused}"
            )
        } else {
            restoreFullscreenImeWindowMode()
            fullPanel.hideImeFromEditor()
            Timber.d("toggle.afterCollapseCleanup softInput restored, ime hidden from full editor")
        }
        isCompactMode = !isCompactMode
        fullPanel.visibility = if (isCompactMode) View.GONE else View.VISIBLE
        compactPanel.visibility = if (isCompactMode) View.VISIBLE else View.GONE
        Timber.d("toggle.afterVisibilityChange" +
                " isCompactMode=$isCompactMode" +
                " fullPanel.vis=${fullPanel.visibility}" +
                " compactPanel.vis=${compactPanel.visibility}" +
                " fullPanel.messageField.isFocused=${fullPanel.messageField.isFocused}" +
                " fullPanel.windowToken=${fullPanel.windowToken != null}" +
                " fullPanel.attached=${fullPanel.isAttachedToWindow}" +
                " fullPanel.hasWindowFocus=${fullPanel.hasWindowFocus()}"
        )
        // IME/restartInput после sync в showFullscreenImeSynchronouslyOnClick() иногда сбрасывает
        // полноэкранное поле — сверяем с зеркалом/компактом и при необходимости переносим снова.
        if (!isCompactMode) {
            val desired = compactDraftForSync()
            if (fullPanel.message != desired) {
                Timber.d(
                    "toggle.reconcile fullLen=${fullPanel.message.length} desiredLen=${desired.length}"
                )
                syncDraftCompactToFull()
            }
            fullPanel.messageField.post {
                if (!isAdded || isCompactMode) return@post
                val d = compactDraftForSync()
                if (fullPanel.message != d) {
                    Timber.d(
                        "toggle.postReconcile fullLen=${fullPanel.message.length} desiredLen=${d.length}"
                    )
                    syncDraftCompactToFull()
                }
            }
        }
        syncMessagePanelImeWithDimensions()
        ViewCompat.requestApplyInsets(fragmentContainer)

        if (!isCompactMode) {
            Timber.d("toggle.expandPath -> focusFullEditorAndShowKeyboard()")
            focusFullEditorAndShowKeyboard()
        } else {
            val field = compactPanel.messageField
            val reqFocus = field.requestFocus()
            Timber.d("toggle.collapsePath" +
                    " requestFocus=$reqFocus" +
                    " field.isFocused=${field.isFocused}" +
                    " keyboardLikelyOpen=$keyboardLikelyOpen"
            )
            if (keyboardLikelyOpen) {
                field.post {
                    if (!isAdded || !field.isAttachedToWindow || !isCompactMode) return@post
                    showKeyboard(field)
                }
            }
        }
    }

    private fun focusFullEditorAndShowKeyboard() {
        val requestId = ++fullscreenImeRequestId
        Timber.d("focusFull.enter" +
                " requestId=$requestId" +
                " isAdded=$isAdded" +
                " isCompactMode=$isCompactMode" +
                " fullPanelInit=${::fullPanel.isInitialized}" +
                " keyboardShow=${dimensionsProvider.getDimensions().isKeyboardShow()}"
        )
        if (!isAdded || isCompactMode || !::fullPanel.isInitialized) {
            Timber.w("focusFull.abort precondition failed requestId=$requestId")
            return
        }
        prepareFullscreenImeWindowMode()
        fullPanel.post {
            if (!isFullscreenImeRequestActive(requestId)) {
                Timber.d("focusFull.post inactive requestId=$requestId")
                return@post
            }
            if (fullPanel.isAttachedToWindow) {
                fullPanel.doOnPreDraw {
                    requestFullscreenImeAfterExpand(requestId, 0)
                }
            } else {
                requestFullscreenImeAfterExpand(requestId, 0)
            }
        }
    }

    private fun requestFullscreenImeAfterExpand(requestId: Int, attempt: Int) {
        if (!isFullscreenImeRequestActive(requestId)) {
            Timber.d("retry.skip inactive requestId=$requestId attempt=$attempt")
            return
        }
        val attemptStartedAt = SystemClock.uptimeMillis()
        val field = fullPanel.messageField
        val readyForIme = fullPanel.visibility == View.VISIBLE &&
                field.visibility == View.VISIBLE &&
                fullPanel.isAttachedToWindow &&
                field.isAttachedToWindow &&
                fullPanel.width > 0 &&
                fullPanel.height > 0 &&
                field.width > 0 &&
                field.height > 0 &&
                (field.hasWindowFocus() || fullPanel.hasWindowFocus() || view?.hasWindowFocus() == true)

        val keyboardShowBefore = dimensionsProvider.getDimensions().isKeyboardShow()
        Timber.d("retry.enter" +
                " attempt=$attempt" +
                " requestId=$requestId" +
                " readyForIme=$readyForIme" +
                " keyboardShowBefore=$keyboardShowBefore" +
                " fullPanel.vis=${fullPanel.visibility}" +
                " field.vis=${field.visibility}" +
                " fullPanel.attached=${fullPanel.isAttachedToWindow}" +
                " field.attached=${field.isAttachedToWindow}" +
                " fullPanel.size=${fullPanel.width}x${fullPanel.height}" +
                " field.size=${field.width}x${field.height}" +
                " field.hasWindowFocus=${field.hasWindowFocus()}" +
                " field.isFocused=${field.isFocused}"
        )

        if (readyForIme) {
            focusFullscreenEditorField(field)
            val length = field.text?.length ?: 0
            applyPendingFullscreenSelection(field, length)
            fullPanel.showKeyboard()
            val showKeyboardResult = runCatching { showKeyboard(field) }
            val useSyntheticTap = attempt >= FULLSCREEN_IME_SYNTHETIC_TAP_ATTEMPT
            showFullscreenIme(field, useSyntheticTap)
            applyPendingFullscreenSelection(field, length)
            applyFullPanelImeFix()
            Timber.d("retry.afterShow" +
                    " attempt=$attempt" +
                    " syntheticTap=$useSyntheticTap" +
                    " field.isFocused=${field.isFocused}" +
                    " field.hasWindowFocus=${field.hasWindowFocus()}" +
                    " showKeyboardOk=${showKeyboardResult.isSuccess}" +
                    " keyboardShowAfter=${dimensionsProvider.getDimensions().isKeyboardShow()}" +
                    " elapsed=${SystemClock.uptimeMillis() - attemptStartedAt}"
            )
        }

        val keyboardShowNow = dimensionsProvider.getDimensions().isKeyboardShow()
        if (keyboardShowNow || attempt >= FULLSCREEN_IME_MAX_ATTEMPTS) {
            Timber.d("retry.terminate" +
                    " attempt=$attempt" +
                    " keyboardShowNow=$keyboardShowNow" +
                    " maxAttempts=$FULLSCREEN_IME_MAX_ATTEMPTS"
            )
            pendingFullscreenSelection = null
            restoreFullscreenImeWindowMode()
            return
        }

        val delay = FULLSCREEN_IME_RETRY_DELAYS_MS.getOrElse(attempt) { FULLSCREEN_IME_RETRY_DELAYS_MS.last() }
        Timber.d("retry.scheduleNext attempt=${attempt + 1} delay=$delay")
        fullPanel.postDelayed({
            if (!isFullscreenImeRequestActive(requestId)) return@postDelayed
            if (fullPanel.isAttachedToWindow) {
                fullPanel.doOnPreDraw {
                    requestFullscreenImeAfterExpand(requestId, attempt + 1)
                }
            } else {
                requestFullscreenImeAfterExpand(requestId, attempt + 1)
            }
        }, delay)
    }

    private fun isFullscreenImeRequestActive(requestId: Int): Boolean {
        return isAdded && !isCompactMode && ::fullPanel.isInitialized && requestId == fullscreenImeRequestId
    }

    private fun prepareFullscreenImeWindowMode() {
        val window = activity?.window ?: return
        if (fullscreenPreviousSoftInputMode == null) {
            fullscreenPreviousSoftInputMode = window.attributes.softInputMode
        }
        val adjustMode = window.attributes.softInputMode and WindowManager.LayoutParams.SOFT_INPUT_MASK_ADJUST
        window.setSoftInputMode(adjustMode or WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
    }

    private fun restoreFullscreenImeWindowMode() {
        val previousMode = fullscreenPreviousSoftInputMode ?: return
        fullscreenPreviousSoftInputMode = null
        activity?.window?.setSoftInputMode(previousMode)
    }

    private fun focusFullscreenEditorField(field: CodeEditor) {
        if (compactPanel.messageField.hasFocus()) {
            compactPanel.messageField.clearFocus()
        }
        if (!field.isFocusableInTouchMode) {
            field.isFocusableInTouchMode = true
        }
        if (!field.requestFocusFromTouch()) {
            field.requestFocus()
        }
    }

    private fun showFullscreenIme(field: CodeEditor, useSyntheticTap: Boolean) {
        if (useSyntheticTap) {
            dispatchSyntheticEditorTap(field)
            focusFullscreenEditorField(field)
        }
        ViewCompat.getWindowInsetsController(field)?.show(WindowInsetsCompat.Type.ime())
        val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.restartInput(field)
        imm?.showSoftInput(field, InputMethodManager.SHOW_IMPLICIT)
        imm?.showSoftInput(field, InputMethodManager.SHOW_FORCED)
    }

    private fun dispatchSyntheticEditorTap(field: CodeEditor) {
        if (!field.isAttachedToWindow) return
        // На шаге Strategy B нужно «протолкнуть» событие касания в поле редактора даже если
        // оно ещё/уже не отрисовано (visibility=GONE → width/height=0). Используем
        // fallback-координаты: точные координаты не важны — важен сам факт диспатча
        // ACTION_DOWN/UP внутри окна пользовательского жеста.
        val downTime = SystemClock.uptimeMillis()
        val w = field.width.takeIf { it > 0 } ?: 1
        val h = field.height.takeIf { it > 0 } ?: 1
        val x = (w / 2f).coerceAtLeast(1f)
        val y = (h / 2f).coerceAtLeast(1f)
        val down = MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, x, y, 0)
        val up = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), MotionEvent.ACTION_UP, x, y, 0)
        try {
            field.dispatchTouchEvent(down)
            field.dispatchTouchEvent(up)
        } finally {
            down.recycle()
            up.recycle()
        }
    }

    /**
     * Strategy A (синхронный показ IME внутри клика по compact fullButton).
     *
     * На Android 12+ InputMethodManagerService.showSoftInput требует, чтобы вызов происходил
     * в том же фрейме обработки пользовательского ввода: иначе системa считает приложение
     * «фоновым» и отклоняет запрос показа IME. Когда мы переключали layout (toggleCompactMode)
     * и пытались поднять клавиатуру в post() / doOnPreDraw(), окно жеста уже было закрыто —
     * на OnePlus + Fleksy клавиатура не появлялась.
     *
     * Здесь мы делаем ВСЕ шаги синхронно прямо в обработчике клика:
     *  1. Поднимаем softInputMode у окна → STATE_ALWAYS_VISIBLE (с сохранением прежнего).
     *  2. Strategy B: диспатчим синтетические ACTION_DOWN/UP в fullPanel.messageField, чтобы
     *     поле «увидело» касание и framework пометил его как пользовательски-инициированный
     *     контекст ввода.
     *  3. Делаем поле фокусируемым в touch-mode и пытаемся отдать ему фокус заранее
     *     (ещё до того, как toggleCompactMode сделает fullPanel видимым).
     *  4. Запрашиваем IME через WindowInsetsControllerCompat (на уровне окна) и через
     *     InputMethodManager.showSoftInput. Оба вызова — без post() — сразу из click-листенера.
     *
     * Существующие пост-layout ретраи (focusFullEditorAndShowKeyboard / requestFullscreenImeAfterExpand)
     * сохраняются как fallback, если сервис всё-таки отложит показ.
     */
    private fun showFullscreenImeSynchronouslyOnClick() {
        Timber.d("syncIme.start" +
                " isAdded=$isAdded" +
                " fullPanelInit=${::fullPanel.isInitialized}" +
                " isCompactMode=$isCompactMode" +
                " activity=${activity != null}"
        )
        if (!isAdded || !::fullPanel.isInitialized) {
            Timber.w("syncIme.abort no fragment / panel")
            return
        }
        // Снимок до фокуса/IME: на части прошивок restartInput/showSoftInput по full
        // обнуляет буфер, если сессия ввода ещё «цеплялась» за compact.
        val draftSnapshot = if (isCompactMode && ::compactPanel.isInitialized) {
            compactDraftForSync()
        } else {
            ""
        }
        // Должно быть до любого фокуса/IME на полном поле: иначе пустое full + restartInput
        // может «перебить» черновик или гоняться с toggleCompactMode().
        if (isCompactMode && ::compactPanel.isInitialized) {
            syncDraftCompactToFull()
        }
        val window = activity?.window ?: run {
            Timber.w("syncIme.abort no window")
            return
        }
        // 1. softInputMode → ALWAYS_VISIBLE (с сохранением прежнего режима для последующего restore).
        prepareFullscreenImeWindowMode()
        Timber.d("syncIme.afterPrepareSoftInput" +
                " softInputMode=0x${Integer.toHexString(window.attributes.softInputMode)}" +
                " previousSaved=${fullscreenPreviousSoftInputMode?.let { "0x${Integer.toHexString(it)}" }}"
        )
        val field = fullPanel.messageField
        if (!field.isFocusableInTouchMode) {
            field.isFocusableInTouchMode = true
        }
        if (compactPanel.messageField.hasFocus()) {
            compactPanel.messageField.clearFocus()
        }
        // 2. Делаем fullPanel видимым СИНХРОННО — иначе View.requestFocus() возвращает false для
        //    view с visibility != VISIBLE, и imm.showSoftInput не получит served view.
        //    toggleCompactMode() далее выставит ту же видимость и спрячет compactPanel в том же
        //    UI-пассе, поэтому визуального наложения пользователь не увидит.
        if (fullPanel.visibility != View.VISIBLE) {
            fullPanel.visibility = View.VISIBLE
        }
        Timber.d("syncIme.afterMakeFullPanelVisible" +
                " fullPanel.visibility=${fullPanel.visibility}" +
                " w=${fullPanel.width}" +
                " h=${fullPanel.height}" +
                " isLaidOut=${fullPanel.isLaidOut}" +
                " isAttached=${fullPanel.isAttachedToWindow}" +
                " field.visibility=${field.visibility}" +
                " field.w=${field.width}" +
                " field.h=${field.height}" +
                " field.isLaidOut=${field.isLaidOut}" +
                " field.attached=${field.isAttachedToWindow}"
        )
        // 3. Strategy B — синтетический ACTION_DOWN/UP по полю до showSoftInput, чтобы
        //    framework пометил контекст ввода как пользовательский.
        dispatchSyntheticEditorTap(field)
        Timber.d("syncIme.afterSyntheticTap" +
                " field.isFocused=${field.isFocused}" +
                " field.hasWindowFocus=${field.hasWindowFocus()}"
        )
        // 4. Берём фокус в touch-mode (как при обычном тапе по EditText).
        val rffTouch = field.requestFocusFromTouch()
        val rffPlain = if (!rffTouch) field.requestFocus() else true
        Timber.d("syncIme.afterRequestFocus" +
                " requestFocusFromTouch=$rffTouch" +
                " requestFocus=$rffPlain" +
                " field.isFocused=${field.isFocused}" +
                " activity.currentFocus=${activity?.currentFocus?.javaClass?.simpleName}"
        )
        // 5. Window-level показ IME + InputMethodManager.showSoftInput — оба синхронно,
        //    в том же фрейме что и клик, чтобы Android 12+ принял запрос как user-gesture.
        try {
            WindowCompat.getInsetsController(window, fullPanel)
                .show(WindowInsetsCompat.Type.ime())
            Timber.d("syncIme.afterWindowInsetsControllerShow ok=true")
        } catch (t: Throwable) {
            Timber.e(t, "syncIme.afterWindowInsetsControllerShow ex=${t.javaClass.simpleName}: ${t.message}")
        }
        val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        val immResult = imm?.showSoftInput(field, InputMethodManager.SHOW_IMPLICIT)
        Timber.d("syncIme.afterImmShowSoftInput" +
                " imm=${imm != null}" +
                " result=$immResult" +
                " field.isFocused=${field.isFocused}" +
                " field.hasWindowFocus=${field.hasWindowFocus()}" +
                " field.windowToken=${field.windowToken != null}" +
                " imm.isActive(field)=${imm?.isActive(field)}"
        )
        if (draftSnapshot.isNotEmpty() && fullPanel.message != draftSnapshot) {
            Timber.w(
                "syncIme.draftMismatch restore len=${draftSnapshot.length} fullLen=${fullPanel.message.length}"
            )
            fullPanel.setText(draftSnapshot)
            val len = draftSnapshot.length
            val pending = pendingFullscreenSelection
            if (pending != null && pending.size == 2) {
                var s = pending[0].coerceIn(0, len)
                var e = pending[1].coerceIn(0, len)
                if (e < s) e = s
                field.post {
                    if (!isAdded || !field.isAttachedToWindow) return@post
                    val n = field.text?.length ?: 0
                    field.setSelection(s.coerceIn(0, n), e.coerceIn(0, n))
                }
            }
        }
    }

    private fun setDraftToPanels(text: String) {
        draftContentMirror = text
        fullPanel.setText(text)
        compactPanel.setText(text)
        Timber.d(
            "destFull len=${fullPanel.message.length}" +
                " destCompact len=${compactPanel.message.length}" +
                " sourceLen=${text.length}"
        )
    }

    private fun applyInitialSelectionToPanels(length: Int) {
        val pending = initialSelectionRange ?: return
        var start = pending[0].coerceIn(0, length)
        var end = pending[1].coerceIn(0, length)
        if (end < start) end = start
        fullPanel.messageField.setSelection(start, end)
        compactPanel.messageField.setSelection(start, end)
        pendingFullscreenSelection = intArrayOf(start, end)
    }

    private fun installDraftMirrorWatchers() {
        val watcher = object : SimpleTextWatcher() {
            override fun afterTextChanged(s: Editable) {
                draftContentMirror = s.toString()
            }
        }
        fullPanel.messageField.addTextChangedListener(watcher)
        compactPanel.messageField.addTextChangedListener(watcher)
    }

    /** Текст компактного поля для переноса в full; при «пустом» чтении буфера — [draftContentMirror]. */
    private fun compactDraftForSync(): String {
        if (!::compactPanel.isInitialized) return draftContentMirror
        val t = compactPanel.messageField.text?.toString() ?: ""
        return if (t.isNotEmpty()) t else draftContentMirror
    }

    private fun requestClearEditorText() {
        val panel = currentPanel() ?: return
        if (!isAdded || view == null || panel.messageField.visibility != View.VISIBLE || panel.isInputBlocked()) return
        if (panel.messageField.text?.isBlank() != false) return
        if (clearEditorTextDialog?.isShowing == true) return

        clearEditorTextDialog = MaterialAlertDialogBuilder(requireContext())
                .setMessage(R.string.editor_clear_text_confirm_message)
                .setPositiveButton(R.string.editor_clear_text_confirm_positive) { _, _ ->
                    clearEditorTextConfirmed()
                }
                .setNegativeButton(R.string.editor_clear_text_confirm_negative, null)
                .showWithStyledButtons()
                .also { dialog ->
                    dialog.setOnDismissListener {
                        if (clearEditorTextDialog === dialog) {
                            clearEditorTextDialog = null
                        }
                    }
                }
    }

    private fun clearEditorTextConfirmed() {
        if (!isAdded || view == null) return
        // Confirmation is only for the user's clear button; programmatic resets keep using setText/clearMessage directly.
        draftContentMirror = ""
        if (::fullPanel.isInitialized) fullPanel.clearMessage()
        if (::compactPanel.isInitialized) compactPanel.clearMessage()
    }

    private fun setAttachmentsToPanels(items: List<AttachmentItem>) {
        fullPanel.attachmentsPopup?.setAttachments(items)
        compactPanel.attachmentsPopup?.setAttachments(items)
        refreshAttachmentsSnapshotCache()
    }

    private fun refreshAttachmentsSnapshotCache() {
        if (!::fullPanel.isInitialized && !::compactPanel.isInitialized) return
        attachmentsSnapshotCache = allAttachmentsSnapshot()
    }

    private fun allAttachmentsSnapshot(): List<AttachmentItem> =
            mergeEditorAttachments(
                    if (::fullPanel.isInitialized) fullPanel.attachmentsPopup?.getAttachments().orEmpty() else emptyList(),
                    if (::compactPanel.isInitialized) compactPanel.attachmentsPopup?.getAttachments().orEmpty() else emptyList()
            )

    /**
     * Merge списков вложений: не терять session-upload и loading-элементы UI при повторном [EditPostUiEvent.ShowForm]
     * (где [EditPostForm.attachments] ещё без только что загруженных файлов).
     */
    private fun currentMessageNormalized(): String {
        val panelText = currentPanel()?.messageField?.text?.toString().orEmpty()
        val resolved = panelText.ifEmpty { draftContentMirror }
        return resolved.trimEnd()
    }

    /** Активные вложения поста + число локальных (ещё без id) для сравнения с baseline. */
    private fun attachmentsContentSignature(items: List<AttachmentItem>): String {
        val activeServerIds = items.asSequence()
                .filter { it.id > 0 && it.status != AttachmentItem.STATUS_REMOVED }
                .map { it.id }
                .sorted()
                .joinToString(",")
        val localPending = items.count { it.id <= 0 }
        return "${activeServerIds}#$localPending"
    }

    private fun isEditDraftDirty(): Boolean {
        if (formType != EditPostForm.TYPE_EDIT_POST) return false
        if (!editBaselineEstablished) {
            // Пока форма не зафиксирована после загрузки — не закрываем без запроса (нет эталона).
            return true
        }
        return currentMessageNormalized() != baselineEditMessage ||
                attachmentsContentSignature(allAttachmentsSnapshot()) != baselineAttachmentSignature
    }

    private fun captureEditBaselineFromUiOnce() {
        if (formType != EditPostForm.TYPE_EDIT_POST || editBaselineEstablished) return
        editBaselineEstablished = true
        baselineEditMessage = currentMessageNormalized()
        baselineAttachmentSignature = attachmentsContentSignature(allAttachmentsSnapshot())
    }

    private fun mergeEditorAttachments(primary: List<AttachmentItem>, secondary: List<AttachmentItem>): List<AttachmentItem> {
        val seenIds = mutableSetOf<Int>()
        val seenTransientPtr = mutableSetOf<Int>()
        val out = ArrayList<AttachmentItem>()
        fun consider(item: AttachmentItem) {
            if (item.id > 0) {
                if (!seenIds.add(item.id)) return
            } else {
                val ptr = System.identityHashCode(item)
                if (!seenTransientPtr.add(ptr)) return
            }
            out.add(item)
        }
        for (item in primary) consider(item)
        for (item in secondary) {
            when {
                item.id > 0 && primary.none { it.id == item.id } -> consider(item)
                item.id <= 0 -> consider(item)
            }
        }
        return out
    }

    private fun showReasonDialog(form: EditPostForm) {
        val view = View.inflate(requireContext(), R.layout.edit_post_reason, null)
        val editText = view.findViewById<EditText>(R.id.edit_post_reason_field) ?: throw IllegalStateException("editText not found")
        editText.setText(form.editReason)

        MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.editpost_reason)
                .setView(view)
                .setPositiveButton(R.string.send) { _, _ ->
                    presenter.onReasonEdit(editText.text.toString())
                }
                .setNegativeButton(R.string.cancel, null)
                .showWithStyledButtons()
    }

    private fun showEditUnsavedChangesDialog() {
        MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.editpost_unsaved_title)
                .setPositiveButton(R.string.save) { _, _ ->
                    presenter.onSendClick()
                }
                .setNegativeButton(R.string.editpost_discard) { _, _ ->
                    lifecycleScope.launch {
                        runCatching {
                            presenter.discardTransientUploads(allAttachmentsSnapshot())
                        }
                        uploadQueue.clear()
                        uploadInProgress = false
                        exitedCleanly = true
                        presenter.exit()
                    }
        }
                .setNeutralButton(R.string.cancel, null)
                .showWithStyledButtons()
    }

    private fun showSyncDialog() {
        MaterialAlertDialogBuilder(requireContext())
                .setMessage(R.string.editpost_sync)
                .setPositiveButton(R.string.ok) { _, _ ->
                    val panel = currentPanel() ?: return@setPositiveButton
                    val selectionRange = panel.selectionRange
                    exitedCleanly = true
                    presenter.exitWithSync(
                            panel.message,
                            selectionRange,
                            panel.attachments
                    )
                }
                .setNegativeButton(R.string.no) { _, _ ->
                    lifecycleScope.launch {
                        runCatching {
                            presenter.discardTransientUploads(allAttachmentsSnapshot())
                        }
                        uploadQueue.clear()
                        uploadInProgress = false
                        exitedCleanly = true
                        presenter.exit()
                    }
                }
                .showWithStyledButtons()
    }

    companion object {
        // Диагностические логи для отладки IME в полноэкранном режиме редактора.
        // Фильтрация на устройстве:
        //   adb logcat -s EditPostIme MessagePanelIme InputMethodManager *:E
        private const val TAG = "EditPostIme"
        private const val DRAFT_SYNC_TAG = "EditPostDraftSync"
        const val ARG_THEME_NAME = "theme_name"
        const val ARG_ATTACHMENTS = "attachments"
        const val ARG_MESSAGE = "message"
        const val ARG_FORUM_ID = "forumId"
        const val ARG_TOPIC_ID = "topicId"
        const val ARG_POST_ID = "postId"
        const val ARG_ST = "st"
        const val ARG_INITIAL_BODY = "initial_body"
        const val ARG_SELECTION_START = "selection_start"
        const val ARG_SELECTION_END = "selection_end"
        private const val FULLSCREEN_IME_MAX_ATTEMPTS = 5
        private const val FULLSCREEN_IME_SYNTHETIC_TAP_ATTEMPT = 2
        private val FULLSCREEN_IME_RETRY_DELAYS_MS = longArrayOf(80L, 120L, 180L, 220L, 200L)
        private const val STATE_AUTO_FOCUSED_ON_OPEN = "edit_post_auto_focused_on_open"

        fun fillArguments(
            args: Bundle,
            postId: Int,
            topicId: Int,
            forumId: Int,
            st: Int,
            themeName: String?,
            initialBodyHtml: String?
        ): Bundle {
            if (themeName != null)
                args.putString(ARG_THEME_NAME, themeName)
            args.putInt(EditPostForm.ARG_TYPE, EditPostForm.TYPE_EDIT_POST)
            args.putInt(ARG_FORUM_ID, forumId)
            args.putInt(ARG_TOPIC_ID, topicId)
            args.putInt(ARG_POST_ID, postId)
            args.putInt(ARG_ST, st)
            if (!initialBodyHtml.isNullOrBlank())
                args.putString(ARG_INITIAL_BODY, initialBodyHtml)
            return args
        }

        fun fillArguments(
            args: Bundle,
            form: EditPostForm,
            themeName: String?,
            selectionStart: Int? = null,
            selectionEnd: Int? = null
        ): Bundle {
            if (themeName != null)
                args.putString(ARG_THEME_NAME, themeName)
            // Honour the form's own type: handing an edit off to the fullscreen editor must keep
            // TYPE_EDIT_POST (+ postId), otherwise the submit goes out as a NEW post and IPB creates a
            // duplicate instead of editing (баг «из полноэкранного редактора появляется дубль»).
            args.putInt(EditPostForm.ARG_TYPE, form.type)
            args.putParcelableArrayList(ARG_ATTACHMENTS, form.attachments)
            args.putString(ARG_MESSAGE, form.message)
            if (selectionStart != null && selectionEnd != null) {
                args.putInt(ARG_SELECTION_START, selectionStart)
                args.putInt(ARG_SELECTION_END, selectionEnd)
            }
            args.putInt(ARG_FORUM_ID, form.forumId)
            args.putInt(ARG_TOPIC_ID, form.topicId)
            args.putInt(ARG_POST_ID, form.postId)
            args.putInt(ARG_ST, form.st)
            return args
        }
    }

}
