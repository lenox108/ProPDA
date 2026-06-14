package forpdateam.ru.forpda.ui.views.messagepanel.advanced

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Color
import android.os.SystemClock
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.LinearLayout
import androidx.core.view.WindowCompat
import androidx.viewpager.widget.PagerAdapter
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import forpdateam.ru.forpda.R
import forpdateam.ru.forpda.ui.DimensionHelper
import forpdateam.ru.forpda.ui.DimensionsProvider
import forpdateam.ru.forpda.common.getVecDrawable
import forpdateam.ru.forpda.ui.views.messagepanel.MessagePanel
import forpdateam.ru.forpda.databinding.MessagePanelAdvancedBinding
import android.view.LayoutInflater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * BBCode и смайлы.
 * <p>
 * Полная форма (редактор поста): {@link BottomSheetDialog} — отдельное окно, не участвует в борьбе
 * с IME внутри CardView (избегаем «панель уехала, клавиатура внизу»).
 * Компактная форма (ответ в тему): обычный view внутри message_panel_host под MessagePanel.
 * Так высота хоста меняется синхронно с layout, без PopupWindow.dismiss()/padding гонок.
 */
class AdvancedPopup(
    context: Context,
    panel: MessagePanel,
    fullForm: Boolean,
    private val dimensionsProvider: DimensionsProvider
) {
    private var formatSheet: BottomSheetDialog? = null
    private var bottomSheetView: View? = null
    private var sheetPeekHeight: Int = 0
    private val fullFormEditor: Boolean = fullForm
    private val fragmentContainer: ViewGroup = panel.fragmentContainer
    private var isShowingKeyboard = false
    private var showKeyboardAfterSheetDismiss = false
    private var stateListener: StateListener? = null
    private val messagePanel: MessagePanel = panel
    private val context: Context = context
    private var compactAdvancedView: View? = null
    private var compactHost: LinearLayout? = null
    private var popupGeneration = 0
    private var compactInputState = CompactInputState.NONE
    private var compactOpenedAt = 0L
    private var compactOpenHeight = 0
    private var compactOpenRetryScheduled = false

    private var inActivityHost: ViewGroup? = null
    private var inActivityAdvancedView: View? = null

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    init {
        val binding = MessagePanelAdvancedBinding.inflate(LayoutInflater.from(context), null, false)
        val viewPager = binding.pager

        val viewList = ArrayList<BasePanelItem>()
        viewList.add(CodesPanelItem(context, messagePanel, messagePanel.otherPreferencesHolder))
        viewList.add(SmilesPanelItem(context, messagePanel))
        viewPager.adapter = MyPagerAdapter(viewList)

        binding.tabLayout.setupWithViewPager(viewPager)

        if (fullFormEditor) {
            binding.sheetKeyboardButton.visibility = View.VISIBLE
            binding.sheetKeyboardButton.setOnClickListener {
                switchToKeyboard()
            }
        }

        binding.deleteButton.setOnClickListener {
            val messageField = messagePanel.messageField
            val selectionStart = messageField?.selectionStart ?: 0
            val selectionEnd = messageField?.selectionEnd ?: 0
            var s = selectionStart
            var e = selectionEnd
            if (e < s && e != -1) {
                val c = s
                s = e
                e = c
            }
            if (s != -1 && s != e) {
                messageField?.text?.delete(s, e)
                return@setOnClickListener
            }
            if (s > 0) {
                messageField?.text?.delete(s - 1, s)
            }
        }

        if (fullFormEditor) {
            // Fullscreen editor must not open a separate Window; render the panel inside the activity.
            inActivityHost = fragmentContainer.findViewById(R.id.advanced_popup_host)
            if (inActivityHost != null) {
                inActivityAdvancedView = binding.root
                binding.root.visibility = View.GONE
                inActivityHost?.visibility = View.GONE
                inActivityHost?.removeAllViews()
                inActivityHost?.addView(
                    binding.root,
                    ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                )
                formatSheet = null
            } else {
                // Fallback: legacy behavior if host is missing for some reason.
                formatSheet = BottomSheetDialog(context)
                formatSheet?.setContentView(binding.root)
            }
            // В fullscreen-редакторе тап по полю ввода (вне области bottom sheet) не должен
            // "случайно" закрывать панель BBCode/смайлов — иначе состояние ввода ломается и
            // панель может перестать открываться до пересоздания экрана.
            formatSheet?.setCanceledOnTouchOutside(false)
            formatSheet?.setOnDismissListener {
                onFullFormHidden()
            }
            val w = formatSheet?.window
            if (w != null) {
                WindowCompat.setDecorFitsSystemWindows(w, true)
                // SOFT_INPUT_ADJUST_NOTHING — окно не ресайзится под IME. Так как мы принудительно
                // скрываем IME при открытии popup и при обнаружении его появления, это исключает
                // «фантомное серое поле» (bottom sheet застывает в позиции для уменьшенного окна,
                // а после скрытия IME окно расширяется и под sheet остаётся пустая зона).
                w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING)
                // Убираем затемнение активности: пользователь должен видеть поле ввода над
                // BBCode-панелью, как при открытой клавиатуре (а не «чёрный экран» со скрима).
                w.setDimAmount(0f)
            }
            // Если IME всё-таки появится поверх диалога (например, фокус на поле в активити под
            // диалогом) — отслеживаем и сразу скрываем, чтобы не было «следа» от клавиатуры.
            formatSheet?.setOnShowListener {
                val decor = formatSheet?.window?.decorView ?: return@setOnShowListener
                androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(decor) { v, insets ->
                    val imeVisible = insets.isVisible(androidx.core.view.WindowInsetsCompat.Type.ime())
                    if (imeVisible) {
                        // Keep focus/cursor in editor; just suppress IME under the sheet.
                        messagePanel.hideImeFromEditor(clearFocus = false)
                    }
                    insets
                }
            }
            bottomSheetView = formatSheet?.findViewById(com.google.android.material.R.id.design_bottom_sheet)
            if (bottomSheetView != null) {
                // Высота контейнера — по содержимому, чтобы сеть не растягивалась во весь экран
                // (иначе в STATE_EXPANDED занимает всё и под BBCode видна пустая зона).
                bottomSheetView?.layoutParams?.height = ViewGroup.LayoutParams.WRAP_CONTENT
                val behavior = BottomSheetBehavior.from(bottomSheetView!!)
                val dm = context.resources.displayMetrics
                val minKb = context.resources.getDimensionPixelSize(R.dimen.default_keyboard_height)
                // ~половина экрана, как у встроенной панели; не меньше минимальной «клавиатурной» высоты
                val peek = maxOf(minKb, (dm.heightPixels * 0.48f).toInt())
                sheetPeekHeight = peek
                behavior.peekHeight = peek
                // fitToContents: expanded-состояние = высота контента (а не во весь экран).
                behavior.isFitToContents = true
                behavior.skipCollapsed = false
                // Для fullscreen-редактора важно, чтобы панель не «расползалась» во весь экран —
                // иначе окно снова перекроет поле ввода и заблокирует тачи.
                behavior.isDraggable = false
                // Стартуем в COLLAPSED (peek), чтобы поле ввода над панелью оставалось видимым.
                behavior.state = BottomSheetBehavior.STATE_COLLAPSED
            }

            // Критично настроить окно ДО первого layout/анимации, иначе при первом show()
            // bottom sheet рисуется как fullscreen и «прыгает» вниз после onShow/post.
            configureFullFormWindow()
        } else {
            formatSheet = null
            attachCompactAdvancedView(binding.root)
        }

        messagePanel.addAdvancedOnClickListener {
            if (isAdvancedPopupShowing()) {
                switchToKeyboard()
            } else {
                showPopup()
            }
        }

        scope.launch {
            dimensionsProvider.dimensionsFlow.collect { dimensions ->
                messagePanel.post {
                    updateDimens(dimensions)
                }
            }
        }
    }

    private fun isAdvancedPopupShowing(): Boolean {
        if (formatSheet != null) {
            return formatSheet?.isShowing == true
        }
        if (fullFormEditor && inActivityHost != null) {
            return isInActivityFullFormShowing()
        }
        return compactInputState == CompactInputState.BBCODE_OPENING ||
            compactInputState == CompactInputState.BBCODE
    }

    private fun attachCompactAdvancedView(advancedView: View) {
        val parent = messagePanel.parent as? ViewGroup ?: return
        val index = parent.indexOfChild(messagePanel).coerceAtLeast(0)
        val originalLayoutParams = messagePanel.layoutParams
        val host = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            clipChildren = false
            clipToPadding = false
            layoutParams = originalLayoutParams
        }

        parent.removeView(messagePanel)
        messagePanel.layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        host.addView(messagePanel)

        advancedView.visibility = View.GONE
        host.addView(
            advancedView,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                effectiveKeyboardPanelHeight()
            )
        )
        parent.addView(host, index)

        compactHost = host
        compactAdvancedView = advancedView
    }

    private fun effectiveKeyboardPanelHeight(
        dimensions: DimensionHelper.Dimensions = dimensionsProvider.getDimensions()
    ): Int {
        val minH = context.resources.getDimensionPixelSize(R.dimen.default_keyboard_height)
        val maxH = maxOf(minH, (context.resources.displayMetrics.heightPixels * 0.55f).toInt())
        val realKh = when {
            dimensions.imeInsetBottom > 0 -> dimensions.imeInsetBottom
            dimensions.savedKeyboardHeight > 0 -> dimensions.savedKeyboardHeight
            !dimensions.isFakeKeyboardShow && dimensions.keyboardHeight > 0 -> dimensions.keyboardHeight
            else -> 0
        }
        return maxOf(realKh, minH).coerceAtMost(maxH)
    }

    private fun updateDimens(dimensions: DimensionHelper.Dimensions) {
        if (fullFormEditor) {
            return
        }
        if (compactInputState == CompactInputState.BBCODE_OPENING) {
            if (dimensions.isKeyboardShow()) {
                hideCompactAdvancedViewOnly()
                messagePanel.hideImeFromEditor(clearFocus = false)
                messagePanel.setCanScrolling(false)
                scheduleCompactAdvancedOpen(popupGeneration, COMPACT_OPEN_RETRY_DELAY_MS)
                return
            }
            completeCompactAdvancedOpen(dimensions)
            messagePanel.setCanScrolling(false)
            return
        }
        if (compactInputState == CompactInputState.BBCODE) {
            clearCompactHostImeSpacing()
            if (dimensions.imeInsetBottom > 0) {
                messagePanel.hideImeFromEditor(clearFocus = false)
                messagePanel.setCanScrolling(false)
                if (SystemClock.uptimeMillis() - compactOpenedAt < COMPACT_OPEN_IME_GRACE_MS) {
                    return
                }
                hideCompactAdvancedViewOnly()
                clearCompactFakeKeyboardState()
                return
            }
            showCompactAdvancedView(dimensions)
            messagePanel.setCanScrolling(false)
            return
        }
        if (compactInputState == CompactInputState.IME_REQUESTED && dimensions.imeInsetBottom > 0) {
            compactInputState = CompactInputState.NONE
            messagePanel.setCanScrolling(false)
            return
        }
        isShowingKeyboard = dimensions.isKeyboardShow()
        messagePanel.setCanScrolling(!(isShowingKeyboard || isAdvancedPopupShowing()))
    }

    private fun hidePopup() {
        popupGeneration++
        if (formatSheet != null) {
            if (formatSheet?.isShowing == true) {
                formatSheet?.dismiss()
            }
            messagePanel.setImeSuppressed(false)
            return
        }
        if (fullFormEditor && inActivityHost != null) {
            if (isInActivityFullFormShowing()) {
                hideInActivityFullFormPanel()
            }
            messagePanel.setImeSuppressed(false)
            return
        }

        compactInputState = CompactInputState.NONE
        compactOpenHeight = 0
        compactOpenRetryScheduled = false
        messagePanel.advancedButton?.setImageDrawable(context.getVecDrawable(R.drawable.ic_add))

        hideCompactAdvancedViewOnly()
        clearCompactFakeKeyboardState()
        messagePanel.setImeSuppressed(false)

        stateListener?.onHide()
        messagePanel.setCanScrolling(true)
    }

    /** Пока compact BBCode встроен в host, IME-отступы host должны быть отключены. */
    fun isCompactBbcodeLayoutHoldActive(): Boolean =
        compactInputState == CompactInputState.BBCODE_OPENING ||
            compactInputState == CompactInputState.BBCODE

    private fun switchToKeyboard() {
        if (fullFormEditor) {
            hideSheetAndShowKeyboard()
            return
        }
        hideCompactPopupForKeyboard()
        messagePanel.setImeSuppressed(false)
        messagePanel.showKeyboard()
    }

    private fun hideCompactPopupForKeyboard() {
        popupGeneration++
        compactInputState = CompactInputState.IME_REQUESTED
        compactOpenHeight = 0
        compactOpenRetryScheduled = false
        hideCompactAdvancedViewOnly()

        messagePanel.advancedButton?.setImageDrawable(context.getVecDrawable(R.drawable.ic_add))
        clearCompactFakeKeyboardState()
        messagePanel.setImeSuppressed(false)

        stateListener?.onHide()
        messagePanel.setCanScrolling(true)
    }

    private fun hideSheetAndShowKeyboard() {
        val sheet = formatSheet
        if (sheet == null) {
            if (fullFormEditor && inActivityHost != null) {
                messagePanel.messageField.requestFocus()
                messagePanel.setImeSuppressed(false)
                showKeyboardAfterSheetDismiss = true
                hideInActivityFullFormPanel()
                return
            }
            hidePopup()
            messagePanel.post { messagePanel.showKeyboard() }
            return
        }

        messagePanel.messageField.requestFocus()
        messagePanel.setImeSuppressed(false)
        if (sheet.isShowing) {
            showKeyboardAfterSheetDismiss = true
            sheet.dismiss()
        } else {
            messagePanel.post {
                messagePanel.showKeyboard()
            }
        }
    }

    private fun showPopup() {
        if (!fullFormEditor && !messagePanel.isShown) {
            return
        }
        val generation = ++popupGeneration
        // Panel open: keep focus/selection, but IME must appear only via explicit keyboard button.
        messagePanel.setImeSuppressed(true)
        // При открытии BBCode/смайлов клавиатура должна автоматически скрываться, иначе остаётся
        // «фантомное» серое поле от IME, на котором висел peek bottom sheet.
        if (dimensionsProvider.getDimensions().isKeyboardShow()) {
            messagePanel.hideImeFromEditor(clearFocus = false)
        }
        val localDimensions = dimensionsProvider.getDimensions()

        messagePanel.advancedButton?.setImageDrawable(context.getVecDrawable(R.drawable.ic_keyboard))

        if (fullFormEditor && inActivityHost != null) {
            val localDimensions = dimensionsProvider.getDimensions()
            if (!localDimensions.isFakeKeyboardShow) {
                localDimensions.isFakeKeyboardShow = true
                dimensionsProvider.update(localDimensions)
            }
            showInActivityFullFormPanel(localDimensions)
            stateListener?.onShow()
            messagePanel.setCanScrolling(false)
            return
        }

        if (formatSheet != null) {
            if (!localDimensions.isFakeKeyboardShow) {
                localDimensions.isFakeKeyboardShow = true
                dimensionsProvider.update(localDimensions)
            }
            if (formatSheet?.isShowing != true) {
                formatSheet?.show()
                // BottomSheetDialog при show() может вернуть окну MATCH_PARENT по высоте.
                // Повторная настройка убирает прозрачный decor со всего экрана, чтобы long-press
                // и selection handles в редакторе получали события напрямую.
                configureFullFormWindow()
                messagePanel.restoreEditorFocusForSuppressedIme()
                messagePanel.post {
                    configureFullFormWindow()
                    messagePanel.restoreEditorFocusForSuppressedIme()
                }
            }
            stateListener?.onShow()
            messagePanel.setCanScrolling(false)
            return
        }

        compactInputState = CompactInputState.BBCODE_OPENING
        compactOpenHeight = 0
        compactOpenRetryScheduled = false
        clearCompactFakeKeyboardState()
        if (localDimensions.isKeyboardShow()) {
            hideCompactAdvancedViewOnly()
            scheduleCompactAdvancedOpen(generation, COMPACT_OPEN_INITIAL_DELAY_MS)
        } else if (generation == popupGeneration) {
            clearCompactHostImeSpacing()
            completeCompactAdvancedOpen(localDimensions)
        }

        stateListener?.onShow()

        messagePanel.setCanScrolling(false)
    }

    private fun configureFullFormWindow() {
        if (!fullFormEditor) {
            return
        }
        if (inActivityHost != null) {
            // In-activity panel: no Window to configure.
            return
        }
        val h = sheetPeekHeight.takeIf { it > 0 } ?: ViewGroup.LayoutParams.WRAP_CONTENT
        formatSheet?.window?.let { w ->
            WindowCompat.setDecorFitsSystemWindows(w, true)
            w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING)
            w.setDimAmount(0f)
            w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, h)
            w.setGravity(Gravity.BOTTOM)
            // Keep window sized to the sheet height so touches above go to the editor.
            // Also keep dialog focusable so the sheet itself can receive touch/scroll reliably.
            w.addFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL)
            w.clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
            inheritSystemBarAppearanceFromActivity(w)
        }
        bottomSheetView?.let { sheet ->
            if (sheet.layoutParams.height != ViewGroup.LayoutParams.WRAP_CONTENT) {
                sheet.layoutParams = sheet.layoutParams.apply {
                    height = ViewGroup.LayoutParams.WRAP_CONTENT
                }
            }
            sheet.requestLayout()
        }
    }

    /**
     * BottomSheetDialog opens its own [Window]; by default it does NOT inherit
     * `isAppearanceLightStatusBars` / `isAppearanceLightNavigationBars` from the activity,
     * so on light theme the activity's dark status-bar icons (time/battery) flip to white
     * once the sheet shows, then snap back to dark when the IME reappears (sheet's window loses
     * focus). Force the dialog window to mirror activity appearance and use transparent system
     * bar backgrounds so no color tint of the activity bars is repainted by the dialog.
     */
    private fun inheritSystemBarAppearanceFromActivity(dialogWindow: Window) {
        val activity = context.unwrapToActivity() ?: return
        val activityWindow = activity.window
        val activityCtrl = WindowCompat.getInsetsController(activityWindow, activityWindow.decorView)
        val dialogCtrl = WindowCompat.getInsetsController(dialogWindow, dialogWindow.decorView)
        dialogCtrl.isAppearanceLightStatusBars = activityCtrl.isAppearanceLightStatusBars
        dialogCtrl.isAppearanceLightNavigationBars = activityCtrl.isAppearanceLightNavigationBars
        // BottomSheetDialog draws its own status bar background — make it transparent so the
        // activity's status bar pixels remain (icons keep the activity's contrast).
        try {
            dialogWindow.statusBarColor = Color.TRANSPARENT
            dialogWindow.navigationBarColor = Color.TRANSPARENT
        } catch (_: Throwable) {
        }
    }

    private tailrec fun Context.unwrapToActivity(): Activity? = when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.unwrapToActivity()
        else -> null
    }

    private fun onFullFormHidden() {
        val d = dimensionsProvider.getDimensions()
        if (d.isFakeKeyboardShow) {
            d.isFakeKeyboardShow = false
            dimensionsProvider.update(d)
        }
        messagePanel.advancedButton?.setImageDrawable(context.getVecDrawable(R.drawable.ic_add))
        // Любое закрытие панели (back / programmatic / system) должно возвращать режим ввода.
        messagePanel.setImeSuppressed(false)
        // Восстановить курсор/мигание (после возврата фокуса/перекрытия).
        messagePanel.forceEditorCursorRefresh()
        if (fragmentContainer.paddingBottom != 0) {
            fragmentContainer.setPadding(
                fragmentContainer.paddingLeft,
                fragmentContainer.paddingTop,
                fragmentContainer.paddingRight,
                0
            )
        }
        stateListener?.onHide()
        messagePanel.setCanScrolling(true)
        if (showKeyboardAfterSheetDismiss) {
            showKeyboardAfterSheetDismiss = false
            messagePanel.postDelayed({
                messagePanel.showKeyboard()
                messagePanel.forceEditorCursorRefresh()
            }, 120L)
        }
    }

    private fun isInActivityFullFormShowing(): Boolean {
        if (!fullFormEditor) return false
        val v = inActivityAdvancedView ?: return false
        return v.visibility == View.VISIBLE
    }

    private fun fullFormPanelHeight(dimensions: DimensionHelper.Dimensions = dimensionsProvider.getDimensions()): Int {
        val dm = context.resources.displayMetrics
        val minKb = context.resources.getDimensionPixelSize(R.dimen.default_keyboard_height)
        val maxH = maxOf(minKb, (dm.heightPixels * 0.55f).toInt())
        val realKh = when {
            dimensions.imeInsetBottom > 0 -> dimensions.imeInsetBottom
            dimensions.savedKeyboardHeight > 0 -> dimensions.savedKeyboardHeight
            !dimensions.isFakeKeyboardShow && dimensions.keyboardHeight > 0 -> dimensions.keyboardHeight
            else -> 0
        }
        return maxOf(realKh, minKb).coerceAtMost(maxH)
    }

    private fun showInActivityFullFormPanel(dimensions: DimensionHelper.Dimensions = dimensionsProvider.getDimensions()) {
        val host = inActivityHost ?: return
        val view = inActivityAdvancedView ?: return
        val h = fullFormPanelHeight(dimensions)
        view.layoutParams = view.layoutParams?.apply {
            height = h
        } ?: ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, h)
        if (host.visibility != View.VISIBLE) host.visibility = View.VISIBLE
        if (view.visibility != View.VISIBLE) view.visibility = View.VISIBLE
        host.translationY = h.toFloat()
        host.animate().cancel()
        host.animate().translationY(0f).setDuration(180L).start()
        messagePanel.restoreEditorFocusForSuppressedIme()
    }

    private fun hideInActivityFullFormPanel() {
        val host = inActivityHost ?: return
        val view = inActivityAdvancedView ?: return
        if (view.visibility != View.VISIBLE) {
            host.visibility = View.GONE
            return
        }
        val h = (view.layoutParams?.height ?: host.height).takeIf { it > 0 } ?: fullFormPanelHeight()
        host.animate().cancel()
        host.animate()
            .translationY(h.toFloat())
            .setDuration(160L)
            .withEndAction {
                view.visibility = View.GONE
                host.visibility = View.GONE
                host.translationY = 0f
                onFullFormHidden()
            }
            .start()
    }

    private fun showCompactAdvancedView(dimensions: DimensionHelper.Dimensions = dimensionsProvider.getDimensions()) {
        compactAdvancedView?.let { view ->
            val targetHeight = compactOpenHeight.takeIf { it > 0 }
                ?: effectiveKeyboardPanelHeight(dimensions).also { compactOpenHeight = it }
            val alreadyVisible = view.visibility == View.VISIBLE
            val currentHeight = view.layoutParams?.height
            if (alreadyVisible && currentHeight == targetHeight) {
                clearCompactHostImeSpacing()
                return
            }
            view.layoutParams = view.layoutParams.apply {
                height = targetHeight
            }
            if (!alreadyVisible) {
                view.visibility = View.VISIBLE
            }
        }
        clearCompactHostImeSpacing()
        compactHost?.requestLayout()
    }

    private fun completeCompactAdvancedOpen(
        dimensions: DimensionHelper.Dimensions = dimensionsProvider.getDimensions()
    ) {
        if (compactInputState != CompactInputState.BBCODE_OPENING &&
            compactInputState != CompactInputState.BBCODE
        ) {
            return
        }
        compactInputState = CompactInputState.BBCODE
        compactOpenRetryScheduled = false
        compactOpenedAt = SystemClock.uptimeMillis()
        clearCompactHostImeSpacing()
        showCompactAdvancedView(dimensions)
    }

    private fun hideCompactAdvancedViewOnly() {
        compactAdvancedView?.let { view ->
            if (view.visibility != View.GONE) {
                view.visibility = View.GONE
            }
        }
        compactHost?.requestLayout()
    }

    private fun scheduleCompactAdvancedOpen(generation: Int, delayMillis: Long) {
        if (compactOpenRetryScheduled) {
            return
        }
        compactOpenRetryScheduled = true
        messagePanel.postDelayed({
            compactOpenRetryScheduled = false
            if (generation != popupGeneration || compactInputState != CompactInputState.BBCODE_OPENING) {
                return@postDelayed
            }
            val dimensions = dimensionsProvider.getDimensions()
            if (dimensions.isKeyboardShow()) {
                messagePanel.hideImeFromEditor(clearFocus = false)
                scheduleCompactAdvancedOpen(generation, COMPACT_OPEN_RETRY_DELAY_MS)
                return@postDelayed
            }
            completeCompactAdvancedOpen(dimensions)
        }, delayMillis)
    }

    private fun clearCompactFakeKeyboardState() {
        val localDimensions = dimensionsProvider.getDimensions()
        if (localDimensions.isFakeKeyboardShow) {
            localDimensions.isFakeKeyboardShow = false
            dimensionsProvider.update(localDimensions)
        }
    }

    private fun clearCompactHostImeSpacing() {
        val hostParent = compactHost?.parent as? View ?: return
        if (hostParent.paddingBottom != 0) {
            hostParent.setPadding(
                hostParent.paddingLeft,
                hostParent.paddingTop,
                hostParent.paddingRight,
                0
            )
        }
        (hostParent.layoutParams as? ViewGroup.MarginLayoutParams)?.also { lp ->
            if (lp.bottomMargin != 0) {
                lp.bottomMargin = 0
                hostParent.layoutParams = lp
            }
        }
    }

    fun onBackPressed(): Boolean {
        if (!isAdvancedPopupShowing()) {
            return false
        }
        if (fullFormEditor && inActivityHost != null && isInActivityFullFormShowing()) {
            hideInActivityFullFormPanel()
            return true
        }
        hidePopup()
        return true
    }

    fun onResume() {
    }

    fun onPause() {
        hidePopup()
    }

    fun onDestroy() {
        hidePopup()
        scope.cancel()
        // Cancel all pending postDelayed/post Runnables (keyboard retry, compact open retry, etc.)
        messagePanel.handler?.removeCallbacksAndMessages(null)
    }

    fun hidePopupWindows() {
        hidePopup()
    }

    fun setStateListener(stateListener: StateListener?) {
        this.stateListener = stateListener
    }

    interface StateListener {
        fun onShow()
        fun onHide()
    }

    private enum class CompactInputState {
        NONE,
        BBCODE_OPENING,
        BBCODE,
        IME_REQUESTED
    }

    private companion object {
        private const val COMPACT_OPEN_INITIAL_DELAY_MS = 180L
        private const val COMPACT_OPEN_RETRY_DELAY_MS = 80L
        private const val COMPACT_OPEN_IME_GRACE_MS = 500L
    }

    private class MyPagerAdapter(private val pages: List<BasePanelItem>) : PagerAdapter() {

        override fun instantiateItem(container: ViewGroup, position: Int): Any {
            val v = pages[position]
            container.addView(v, 0)
            return v
        }

        override fun destroyItem(container: ViewGroup, position: Int, `object`: Any) {
            container.removeView(`object` as View)
        }

        override fun getCount(): Int {
            return pages.size
        }

        override fun isViewFromObject(view: View, `object`: Any): Boolean {
            return view == `object`
        }

        override fun getPageTitle(position: Int): CharSequence {
            return pages[position].title
        }
    }
}
