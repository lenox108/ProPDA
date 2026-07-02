package forpdateam.ru.forpda.ui.views.messagepanel
import timber.log.Timber

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.view.LayoutInflater
import android.graphics.Typeface
import android.text.method.LinkMovementMethod
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewConfiguration
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.view.MotionEvent
import androidx.cardview.widget.CardView
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import forpdateam.ru.forpda.R
import forpdateam.ru.forpda.BuildConfig
import forpdateam.ru.forpda.common.Html
import forpdateam.ru.forpda.common.getColorFromAttr
import forpdateam.ru.forpda.databinding.MessagePanelFullBinding
import forpdateam.ru.forpda.databinding.MessagePanelQuickBinding
import forpdateam.ru.forpda.common.simple.SimpleTextWatcher
import forpdateam.ru.forpda.entity.remote.editpost.AttachmentItem
import forpdateam.ru.forpda.model.preferences.MainPreferencesHolder
import forpdateam.ru.forpda.model.preferences.OtherPreferencesHolder
import forpdateam.ru.forpda.ui.DimensionsProvider
import forpdateam.ru.forpda.ui.AppFontMode
import forpdateam.ru.forpda.ui.FontController
import forpdateam.ru.forpda.ui.views.CodeEditor
import forpdateam.ru.forpda.ui.views.messagepanel.advanced.AdvancedPopup
import forpdateam.ru.forpda.ui.views.messagepanel.attachments.AttachmentsPopup
import forpdateam.ru.forpda.common.bbcode.BbcodePreviewRenderer
import forpdateam.ru.forpda.common.bbcode.BbcodeWrap
import forpdateam.ru.forpda.ui.views.dialog.showWithStyledButtons
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

@SuppressLint("ViewConstructor")
class MessagePanel(
    context: Context,
    val fragmentContainer: ViewGroup,
    targetContainer: ViewGroup,
    private val fullForm: Boolean,
    private val mainPreferencesHolder: MainPreferencesHolder,
    private val dimensionsProvider: DimensionsProvider,
    val otherPreferencesHolder: OtherPreferencesHolder
) : CardView(context) {
    
    private var fullBinding: MessagePanelFullBinding? = null
    private var quickBinding: MessagePanelQuickBinding? = null

    // Common fields - initialized in init()
    lateinit var advancedButton: ImageButton
    private lateinit var attachmentsButton: ImageButton
    lateinit var sendButton: ImageButton
    lateinit var fullButton: ImageButton
    var hideButton: ImageButton? = null
    var editPollButton: ImageButton? = null
    private lateinit var previewButton: ImageButton
    private lateinit var clearMessageButton: ImageButton
    private lateinit var attachmentsCounter: TextView

    private val advancedListeners = mutableListOf<View.OnClickListener>()
    private val attachmentsListeners = mutableListOf<View.OnClickListener>()
    private val sendListeners = mutableListOf<View.OnClickListener>()
    private var clearMessageClickListener: (() -> Unit)? = null
    
    lateinit var messageField: CodeEditor
    private val panelBehavior = MessagePanelBehavior()
    private var advancedPopup: AdvancedPopup? = null
    var attachmentsPopup: AttachmentsPopup? = null
    
    private lateinit var sendProgress: ProgressBar
    var formProgress: ProgressBar? = null
    private lateinit var messageWrapper: ScrollView
    
    var lastHeight = 0
    var heightChangeListener: HeightChangeListener? = null

    /**
     * Базовый нижний margin хоста [message_panel_host] под нижним таббаром приложения.
     * Нужен фрагментам, которые рисуют контент под таббаром ([shouldDrawBehindBottomNav]) — там
     * fragments_container не резервирует место под таббар, поэтому хост сам поднимается на эту высоту.
     * Используется [AdvancedPopup] при открытии компактной панели BBCode/смайлов, чтобы её нижние
     * ряды не уходили под таббар. По умолчанию 0 (полноэкранный редактор и т.п.).
     */
    var hostBaseBottomMarginProvider: (() -> Int)? = null
    
    private var params: ViewGroup.LayoutParams? = null
    private var isMonospace = true

    /**
     * When true, editor must keep focus/selection but never show IME automatically.
     * IME is allowed only via explicit "keyboard" button.
     */
    private var imeSuppressed: Boolean = false
    
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    /**
     * Invalidates delayed [requestKeyboard] work from [showKeyboard] after IME is dismissed
     * (system back / gesture) so postDelayed(120/300/600) does not re-show the keyboard.
     */
    private var keyboardShowGeneration: Int = 0
    private var editorTouchGeneration: Int = 0

    // Saved references for deterministic cleanup in onDestroy()
    private var layoutChangeListener: View.OnLayoutChangeListener? = null
    private var textWatcher: SimpleTextWatcher? = null
    private var touchListener: View.OnTouchListener? = null
    private var focusChangeListener: View.OnFocusChangeListener? = null

    init {
        isMonospace = mainPreferencesHolder.getEditorMonospace()
        init(targetContainer)
        val tc = targetContainer.childCount
        if (tc == 0) {
            targetContainer.addView(this)
        } else {
            // Вставить перед последним (FAB в coordinator); в пустом message_panel_host — просто add.
            targetContainer.addView(this, tc - 1)
        }
        onCreatePanel()
    }
    
    private fun init(panelParent: ViewGroup) {
        val inflater = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        if (fullForm) {
            fullBinding = MessagePanelFullBinding.inflate(inflater, this, true)
            val fb = requireNotNull(fullBinding) { "MessagePanelFullBinding.inflate returned null" }
            advancedButton = fb.buttonAdvancedInput
            attachmentsButton = fb.buttonAttachments
            sendButton = fb.buttonSend
            fullButton = fb.buttonFull
            hideButton = null
            editPollButton = fb.buttonEdtPoll
            previewButton = fb.buttonPreview
            clearMessageButton = fb.buttonClearMessage
            attachmentsCounter = fb.attachmentCounter
            messageField = fb.messageField
            sendProgress = fb.sendProgress
            formProgress = fb.formLoadProgress
            messageWrapper = fb.messageWrapper
        } else {
            quickBinding = MessagePanelQuickBinding.inflate(inflater, this, true)
            val qb = requireNotNull(quickBinding) { "MessagePanelQuickBinding.inflate returned null" }
            advancedButton = qb.buttonAdvancedInput
            attachmentsButton = qb.buttonAttachments
            sendButton = qb.buttonSend
            fullButton = qb.buttonFull
            hideButton = qb.buttonHide
            editPollButton = null
            previewButton = qb.buttonPreview
            clearMessageButton = qb.buttonClearMessage
            attachmentsCounter = qb.attachmentCounter
            messageField = qb.messageField
            sendProgress = qb.sendProgress
            formProgress = null
            messageWrapper = qb.messageWrapper
        }
        isClickable = true
        
        messageField?.attachToScrollView(messageWrapper)
        messageWrapper?.isEnabled = true
        messageWrapper?.isVerticalFadingEdgeEnabled = true
        messageWrapper?.setFadingEdgeLength(context.resources.getDimensionPixelSize(R.dimen.dp8))
        
        val h = if (fullForm) ViewGroup.LayoutParams.MATCH_PARENT else ViewGroup.LayoutParams.WRAP_CONTENT
        params = if (panelParent is CoordinatorLayout) {
            val clp = CoordinatorLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, h)
            //clp.setBehavior(panelBehavior)
            clp.gravity = Gravity.BOTTOM
            if (!fullForm) {
                clp.setMargins(context.resources.getDimensionPixelSize(R.dimen.dp8), context.resources.getDimensionPixelSize(R.dimen.dp8), context.resources.getDimensionPixelSize(R.dimen.dp8), 0)
            }
            clp
        } else {
            val flp = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, h)
            flp.gravity = Gravity.BOTTOM
            if (!fullForm) {
                flp.setMargins(context.resources.getDimensionPixelSize(R.dimen.dp8), context.resources.getDimensionPixelSize(R.dimen.dp8), context.resources.getDimensionPixelSize(R.dimen.dp8), 0)
            }
            flp
        }
        layoutParams = params
        radius = 0f
        preventCornerOverlap = false
        setCardBackgroundColor(if (fullForm) context.getColorFromAttr(com.google.android.material.R.attr.colorSurface) else Color.TRANSPARENT)
        if (!fullForm) {
            setCardElevation(0f)
            setMaxCardElevation(0f)
            useCompatPadding = false
        }
        clipChildren = true
        clipToPadding = true
        
        //На случай, когда добавляются несколько слушателей
        advancedButton?.setOnClickListener { v ->
            advancedListeners.forEach { it.onClick(v) }
        }
        attachmentsButton?.setOnClickListener { v ->
            attachmentsListeners.forEach { it.onClick(v) }
        }
        sendButton?.setOnClickListener { v ->
            forpdateam.ru.forpda.ui.Haptic.confirm(v)
            sendListeners.forEach { it.onClick(v) }
        }
        previewButton?.setOnClickListener { showLocalPreview() }
        clearMessageButton?.setOnClickListener { clearMessageClickListener?.invoke() ?: clearMessage() }
        
        lastHeight = height + context.resources.getDimensionPixelSize(R.dimen.dp16)
        layoutChangeListener = View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            if (heightChangeListener == null) return@OnLayoutChangeListener
            val newHeight = height + context.resources.getDimensionPixelSize(R.dimen.dp16)
            if (newHeight != lastHeight) {
                lastHeight = newHeight
                heightChangeListener?.onChangedHeight(newHeight)
            }
        }
        addOnLayoutChangeListener(requireNotNull(layoutChangeListener))

        textWatcher = object : SimpleTextWatcher() {
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
                if (s.isNotEmpty()) {
                    if (sendButton?.colorFilter == null) {
                        sendButton?.setColorFilter(context.getColorFromAttr(R.attr.colorAccent))
                    }
                } else {
                    if (sendButton?.colorFilter != null) {
                        sendButton?.clearColorFilter()
                    }
                }
            }
        }
        messageField?.addTextChangedListener(requireNotNull(textWatcher))
        applyEditorTypeface()

        // Keep cursor/selection on tap/long-press; suppress IME without consuming touch events.
        touchListener = View.OnTouchListener { v, event ->
            if (imeSuppressed) {
                logSuppressedEditorTouch(event)
                // Don't hide IME on ACTION_DOWN: it breaks long-press selection on some OEM builds.
                // Just ensure focus so selection/cursor can be updated by the EditText itself.
                if (event.actionMasked == MotionEvent.ACTION_DOWN && !v.isFocused) {
                    v.requestFocus()
                }
                // If IME appears anyway, hide it *after* the EditText processes the gesture.
                if (event.actionMasked == MotionEvent.ACTION_UP) {
                    v.post { hideImeFromEditor(clearFocus = false) }
                }
            }
            false
        }
        messageField.setOnTouchListener(requireNotNull(touchListener))
        focusChangeListener = View.OnFocusChangeListener { _, hasFocus ->
            if (hasFocus && imeSuppressed) {
                hideImeFromEditor(clearFocus = false)
            }
        }
        messageField.onFocusChangeListener = requireNotNull(focusChangeListener)
        
        scope.launch {
            mainPreferencesHolder.observeEditorMonospaceFlow().collect { value ->
                isMonospace = value
                applyEditorTypeface()
            }
        }
        scope.launch {
            mainPreferencesHolder.observeAppFontModeFlow().collect {
                applyEditorTypeface()
            }
        }
        // IME не обрабатываем здесь: enableEdgeToEdge + adjustResize + ручной padding давали двойной отступ
        // («панель уехала вверх, клавиатура внизу»). Отступы — в MainActivity (decor fits) + updateDimens.
    }

    private fun applyEditorTypeface() {
        if (!::messageField.isInitialized) return
        val field = messageField
        field.typeface = if (isMonospace) {
            Typeface.MONOSPACE
        } else when (FontController.getCurrentFontMode(mainPreferencesHolder)) {
            AppFontMode.SYSTEM -> Typeface.DEFAULT
            AppFontMode.ROBOTO -> ResourcesCompat.getFont(context, R.font.forpda_roboto) ?: Typeface.DEFAULT
            AppFontMode.INTER -> ResourcesCompat.getFont(context, R.font.forpda_inter) ?: Typeface.DEFAULT
            AppFontMode.SOURCE_SANS_3 -> ResourcesCompat.getFont(context, R.font.forpda_source_sans_3) ?: Typeface.DEFAULT
            AppFontMode.OPEN_SANS -> ResourcesCompat.getFont(context, R.font.forpda_open_sans) ?: Typeface.DEFAULT
        }
    }

    fun setImeSuppressed(suppressed: Boolean) {
        if (imeSuppressed == suppressed) return
        imeSuppressed = suppressed
        setShowSoftInputOnFocusCompat(messageField, !suppressed)
        if (suppressed) {
            hideImeFromEditor(clearFocus = false)
        }
    }

    fun restoreEditorFocusForSuppressedIme() {
        if (!imeSuppressed) {
            return
        }
        messageField.requestFocus()
        messageField.isCursorVisible = true
        messageField.restartCursorBlink()
        hideImeFromEditor(clearFocus = false)
    }

    fun forceEditorCursorRefresh() {
        messageField.requestFocus()
        messageField.isCursorVisible = true
        messageField.post {
            messageField.restartCursorBlink()
        }
    }

    private fun logSuppressedEditorTouch(event: MotionEvent) {
        if (!BuildConfig.DEBUG || !fullForm) {
            return
        }
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val generation = ++editorTouchGeneration
                val x = event.x.toInt()
                val y = event.y.toInt()
                Timber.d(EDITOR_TOUCH_TAG, editorTouchLogMessage("editor.touch.down", x, y))
                messageField.postDelayed({
                    if (generation == editorTouchGeneration) {
                        Timber.d(
                            EDITOR_TOUCH_TAG,
                            editorTouchLogMessage("editor.touch.longPressProbe", x, y)
                        )
                    }
                }, ViewConfiguration.getLongPressTimeout().toLong() + 50L)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                editorTouchGeneration++
                Timber.d(
                    EDITOR_TOUCH_TAG,
                    editorTouchLogMessage(
                        if (event.actionMasked == MotionEvent.ACTION_UP) {
                            "editor.touch.up"
                        } else {
                            "editor.touch.cancel"
                        },
                        event.x.toInt(),
                        event.y.toInt()
                    )
                )
            }
        }
    }

    private fun editorTouchLogMessage(prefix: String, x: Int, y: Int): String =
        prefix +
            " x=$x" +
            " y=$y" +
            " field.isFocused=${messageField.isFocused}" +
            " field.hasWindowFocus=${messageField.hasWindowFocus()}" +
            " field.windowToken=${messageField.windowToken != null}" +
            " selection=${messageField.selectionStart}:${messageField.selectionEnd}" +
            " imeSuppressed=$imeSuppressed"

    private fun setShowSoftInputOnFocusCompat(editText: EditText, show: Boolean) {
        try {
            editText.showSoftInputOnFocus = show
            return
        } catch (_: Throwable) {
        }
        // Reflection fallback (some builds / older APIs)
        try {
            EditText::class.java.getMethod("setShowSoftInputOnFocus", Boolean::class.javaPrimitiveType)
                .invoke(editText, show)
        } catch (_: Throwable) {
        }
    }
    
    fun disableBehavior() {
        if (params is CoordinatorLayout.LayoutParams) {
            (params as CoordinatorLayout.LayoutParams).behavior = null
            layoutParams = params
        }
    }
    
    fun enableBehavior() {
        if (params is CoordinatorLayout.LayoutParams) {
            (params as CoordinatorLayout.LayoutParams).behavior = panelBehavior
            layoutParams = params
        }
    }
    
    fun setProgressState(state: Boolean) {
        sendProgress?.visibility = if (state) View.VISIBLE else View.GONE
        sendButton?.visibility = if (state) View.GONE else View.VISIBLE
    }

    fun isInputBlocked(): Boolean =
        !messageField.isEnabled ||
            sendProgress.visibility == View.VISIBLE ||
            formProgress?.visibility == View.VISIBLE
    
    fun show() {
        translationY = 0f
    }
    
    fun setText(text: String?) {
        messageField?.setText(text)
    }
    
    fun insertText(text: String): Boolean = insertText(text, null)
    
    val selectionRange: IntArray
        get() {
            var selectionStart = messageField?.selectionStart ?: 0
            var selectionEnd = messageField?.selectionEnd ?: 0
            if (selectionEnd < selectionStart && selectionEnd != -1) {
                val c = selectionStart
                selectionStart = selectionEnd
                selectionEnd = c
            }
            val len = messageField?.text?.length ?: 0
            // Без фокуса getSelectionStart() == -1 — insert() падает или не вставляет BBCode
            if (selectionStart < 0 || selectionStart > len) {
                selectionStart = len
            }
            if (selectionEnd < 0) {
                selectionEnd = selectionStart
            }
            if (selectionEnd > len) {
                selectionEnd = len
            }
            return intArrayOf(selectionStart, selectionEnd)
        }
    
    fun insertText(startText: String, endText: String?): Boolean = insertText(startText, endText, true)
    
    fun insertText(startText: String, endText: String?, selectionStart: Int, selectionEnd: Int): Boolean =
        insertText(startText, endText, selectionStart, selectionEnd, true)
    
    fun insertText(startText: String, endText: String?, selectionInside: Boolean): Boolean {
        val selectionRange = selectionRange
        val s = selectionRange[0]
        val e = selectionRange[1]
        return insertText(startText, endText, s, e, selectionInside)
    }
    
    fun insertText(startText: String, endText: String?, selectionStart: Int, selectionEnd: Int, selectionInside: Boolean): Boolean {
        show()
        val field = messageField
        val editable = field.text ?: return false
        val currentText = editable.toString()

        val result = BbcodeWrap.wrap(
            text = currentText,
            selectionStart = selectionStart,
            selectionEnd = selectionEnd,
            open = startText,
            close = endText.orEmpty(),
            keepSelection = endText != null,
            placeCursorInsideIfEmpty = selectionInside
        )

        field.requestFocus()
        editable.replace(0, editable.length, result.text)
        field.setSelection(result.selectionStart, result.selectionEnd)

        return endText != null && selectionStart != selectionEnd
    }
    
    fun getSelectedText(): String {
        val selectionRange = selectionRange
        val s = selectionRange[0]
        val e = selectionRange[1]
        val t = messageField?.text?.toString() ?: ""
        val start = if (s < 0) 0 else s
        val end = if (e > t.length) t.length else e
        return if (start > end) "" else t.substring(start, end)
    }
    
    fun deleteSelected() {
        val selectionRange = selectionRange
        val s = selectionRange[0]
        val e = selectionRange[1]
        val len = messageField?.text?.length ?: 0
        if (s >= 0 && e <= len && s < e) {
            messageField?.text?.delete(s, e)
        }
    }
    
    fun updateAttachmentsCounter(count: Int) {
        attachmentsCounter?.text = count.toString()
        attachmentsCounter?.visibility = if (count > 0) View.VISIBLE else View.GONE
    }
    
    val message: String
        get() = messageField?.text?.toString() ?: ""
    
    val attachments: List<AttachmentItem>
        get() = attachmentsPopup?.getAttachments() ?: emptyList()
    
    fun clearMessage() {
        messageField?.setText("")
    }
    
    fun clearAttachments() {
        attachmentsPopup?.clearAttachments()
    }
    
    private fun onCreatePanel() {
        attachmentsPopup = AttachmentsPopup(context, this)
        advancedPopup = AdvancedPopup(context, this, fullForm, dimensionsProvider)
    }
    
    fun addAdvancedOnClickListener(listener: View.OnClickListener) {
        advancedListeners.add(listener)
    }
    
    fun addAttachmentsOnClickListener(listener: View.OnClickListener) {
        attachmentsListeners.add(listener)
    }
    
    fun addSendOnClickListener(listener: View.OnClickListener) {
        sendListeners.add(listener)
    }

    fun setClearMessageClickListener(listener: (() -> Unit)?) {
        clearMessageClickListener = listener
    }

    private fun showLocalPreview() {
        hidePopupWindows()
        hideImeFromEditor(clearFocus = false)

        val rawMessage = message
        val previewText = if (rawMessage.isBlank()) {
            context.getString(R.string.msg_panel_preview_empty)
        } else {
            Html.fromHtml(BbcodePreviewRenderer.renderToHtml(rawMessage), Html.FROM_HTML_MODE_LEGACY)
        }

        val previewView = TextView(context).apply {
            setTextColor(context.getColorFromAttr(com.google.android.material.R.attr.colorOnSurface))
            setLinkTextColor(context.getColorFromAttr(com.google.android.material.R.attr.colorSecondary))
            textSize = 16f
            setLineSpacing(0f, 1.15f)
            setPadding(
                resources.getDimensionPixelSize(R.dimen.content_padding_horizontal),
                resources.getDimensionPixelSize(R.dimen.content_padding_vertical),
                resources.getDimensionPixelSize(R.dimen.content_padding_horizontal),
                resources.getDimensionPixelSize(R.dimen.content_padding_vertical)
            )
            text = previewText
            movementMethod = LinkMovementMethod.getInstance()
        }

        val previewContainer = ScrollView(context).apply {
            setBackgroundColor(context.getColorFromAttr(com.google.android.material.R.attr.colorSurface))
            addView(previewView, ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ))
        }

        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.msg_panel_preview)
            .setMessage(R.string.msg_panel_preview_note)
            .setView(previewContainer)
            .setNegativeButton(R.string.edit) { _, _ ->
                messageField.post { showKeyboard() }
            }
            .setPositiveButton(R.string.send_message) { _, _ ->
                sendListeners.forEach { it.onClick(this) }
            }
            .showWithStyledButtons()
    }
    
    fun getAttachmentsButton(): ImageButton? = attachmentsButton
    
    fun showKeyboard() {
        val gen = ++keyboardShowGeneration
        Timber.d(
            IME_TAG,
            "panel.showKeyboard.enter" +
                " fullForm=$fullForm" +
                " gen=$gen" +
                " panel.vis=$visibility" +
                " panel.attached=$isAttachedToWindow" +
                " panel.hasWindowFocus=${hasWindowFocus()}" +
                " messageField.attached=${messageField.isAttachedToWindow}" +
                " messageField.isFocused=${messageField.isFocused}" +
                " messageField.windowToken=${messageField.windowToken != null}"
        )
        show()
        requestKeyboard("immediate")
        post {
            if (gen != keyboardShowGeneration) return@post
            requestKeyboard("post0")
            messageField.post {
                if (gen == keyboardShowGeneration) {
                    requestKeyboard("messageField.post")
                }
            }
            messageField.postDelayed({
                if (gen == keyboardShowGeneration) {
                    requestKeyboard("delay120")
                }
            }, 120L)
            messageField.postDelayed({
                if (gen == keyboardShowGeneration) {
                    requestKeyboard("delay300")
                }
            }, 300L)
            messageField.postDelayed({
                if (gen == keyboardShowGeneration) {
                    requestKeyboard("delay600")
                }
            }, 600L)
        }
    }

    fun cancelDeferredKeyboardShowRequests() {
        keyboardShowGeneration++
    }

    private fun requestKeyboard(stage: String = "?") {
        if (!isAttachedToWindow || !messageField.isAttachedToWindow) {
            Timber.d(
                IME_TAG,
                "panel.requestKeyboard.skip" +
                    " stage=$stage" +
                    " panel.attached=$isAttachedToWindow" +
                    " field.attached=${messageField.isAttachedToWindow}"
            )
            return
        }
        val reqFocus = messageField.requestFocus()
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.restartInput(messageField)
        val immResult = imm?.showSoftInput(messageField, InputMethodManager.SHOW_IMPLICIT)
        var insetsOk = false
        var insetsErr: String? = null
        try {
            ViewCompat.getWindowInsetsController(messageField)?.show(WindowInsetsCompat.Type.ime())
            insetsOk = true
        } catch (t: Throwable) {
            insetsErr = "${t.javaClass.simpleName}: ${t.message}"
        }
        Timber.d(
            IME_TAG,
            "panel.requestKeyboard" +
                " stage=$stage" +
                " requestFocus=$reqFocus" +
                " field.isFocused=${messageField.isFocused}" +
                " field.hasWindowFocus=${messageField.hasWindowFocus()}" +
                " imm=${imm != null}" +
                " showSoftInputResult=$immResult" +
                " imm.isActive(field)=${imm?.isActive(messageField)}" +
                " insetsCtrlOk=$insetsOk" +
                (insetsErr?.let { " insetsCtrlErr=$it" } ?: "")
        )
    }
    
    /** Скрыть IME по полю редактора; вызывать до {@code setVisibility(GONE)} у панели, иначе токен окна может быть недействителен. */
    fun hideImeFromEditor(clearFocus: Boolean = true) {
        cancelDeferredKeyboardShowRequests()
        if (clearFocus) {
            messageField?.clearFocus()
        }
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.hideSoftInputFromWindow(messageField?.windowToken, 0)
    }
    
    fun setCanScrolling(canScrolling: Boolean) {
        panelBehavior.setCanScrolling(canScrolling)
    }
    
    fun onBackPressed(): Boolean = advancedPopup?.onBackPressed() ?: false
    
    fun onResume() {
        advancedPopup?.onResume()
    }
    
    fun onDestroy() {
        advancedPopup?.onDestroy()
        scope.cancel()
        // Cancel all pending postDelayed (keyboard show retries, editor touch probes, etc.)
        messageField.handler?.removeCallbacksAndMessages(null)
        // Remove listeners to prevent leaks
        layoutChangeListener?.let { removeOnLayoutChangeListener(it) }
        textWatcher?.let { messageField.removeTextChangedListener(it) }
        messageField.setOnTouchListener(null)
        messageField.onFocusChangeListener = null
        fullBinding = null
        quickBinding = null
    }
    
    fun onPause() {
        advancedPopup?.onPause()
    }
    
    fun hidePopupWindows() {
        advancedPopup?.hidePopupWindows()
    }

    /** Слушатель показа/скрытия панели BBCode/смайлов (для подгонки отступов полноэкранного редактора). */
    fun setAdvancedPanelStateListener(listener: AdvancedPopup.StateListener?) {
        advancedPopup?.setStateListener(listener)
    }

    /** См. [AdvancedPopup.fullFormReservedHeight]. */
    val fullFormAdvancedReservedHeight: Int
        get() = advancedPopup?.fullFormReservedHeight ?: 0

    /** См. [AdvancedPopup.isCompactBbcodeLayoutHoldActive]. */
    fun isCompactBbcodeLayoutHoldActive(): Boolean =
        !fullForm && (advancedPopup?.isCompactBbcodeLayoutHoldActive() == true)
    
    fun interface HeightChangeListener {
        fun onChangedHeight(newHeight: Int)
    }

    private companion object {
        // Диагностический тег для отладки IME (фильтрация: adb logcat -s MessagePanelIme).
        private const val IME_TAG = "MessagePanelIme"
        private const val EDITOR_TOUCH_TAG = "MessagePanelTouch"
    }
}
