package forpdateam.ru.forpda.ui.fragments.news.details

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.widget.AppCompatImageButton
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.bottomsheet.BottomSheetDragHandleView
import forpdateam.ru.forpda.common.getColorFromAttr
import com.google.android.material.button.MaterialButton
import dagger.hilt.android.AndroidEntryPoint
import forpdateam.ru.forpda.R
import forpdateam.ru.forpda.common.Utils
import forpdateam.ru.forpda.model.AuthHolder
import forpdateam.ru.forpda.presentation.IErrorHandler
import forpdateam.ru.forpda.presentation.ILinkHandler
import forpdateam.ru.forpda.presentation.TabRouter
import forpdateam.ru.forpda.presentation.articles.detail.comments.ArticleCommentUiEvent
import forpdateam.ru.forpda.presentation.articles.detail.comments.ArticleCommentViewModel
import kotlinx.coroutines.launch
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import javax.inject.Inject

@AndroidEntryPoint
class NewsCommentComposeBottomSheet : BottomSheetDialogFragment() {
    @Inject lateinit var authHolder: AuthHolder
    @Inject lateinit var router: TabRouter
    @Inject lateinit var linkHandler: ILinkHandler
    @Inject lateinit var errorHandler: IErrorHandler

    private lateinit var messageField: EditText
    private lateinit var buttonHide: AppCompatImageButton
    private lateinit var buttonSend: MaterialButton
    private lateinit var sendProgress: com.google.android.material.progressindicator.CircularProgressIndicator
    private lateinit var dragHandle: BottomSheetDragHandleView
    private lateinit var title: TextView

    private val presenter: ArticleCommentViewModel by viewModels(
            ownerProducer = { requireParentFragment() },
    ) {
        ArticleCommentViewModel.Factory(hostFragment().provideChildInteractor(), router, linkHandler, authHolder, errorHandler)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val view = inflater.inflate(R.layout.bottom_sheet_news_comment_compose, container, false)
        messageField = view.findViewById(R.id.message_field)
        buttonHide = view.findViewById(R.id.button_hide)
        buttonSend = view.findViewById(R.id.button_send)
        sendProgress = view.findViewById(R.id.send_progress)
        dragHandle = view.findViewById(R.id.drag_handle)
        title = view.findViewById(R.id.title)

        val draft = savedInstanceState?.getString(STATE_DRAFT).orEmpty()
        if (draft.isNotBlank()) {
            messageField.setText(draft)
            messageField.setSelection(messageField.text.length)
        }

        buttonHide.setOnClickListener { dismissAllowingStateLoss() }
        buttonSend.setOnClickListener { send() }
        buttonSend.isEnabled = messageField.text?.isNotBlank() == true
        messageField.addTextChangedListener(SimpleTextWatcherAdapter { text ->
            buttonSend.isEnabled = text.isNotBlank()
        })
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        applyComposePanelTheme(view)
        if (!authHolder.get().isAuth()) {
            Utils.showNeedAuthDialog(requireContext(), router)
            dismissAllowingStateLoss()
            return
        }

        presenter.start()
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    presenter.sendRefreshing.collect { refreshing ->
                        sendProgress.visibility = if (refreshing) View.VISIBLE else View.GONE
                        buttonSend.isEnabled = !refreshing && messageField.text?.isNotBlank() == true
                    }
                }
                launch {
                    presenter.uiEvents.collect { event ->
                        if (event is ArticleCommentUiEvent.OnReplyComment) {
                            hostFragment().showInlineComments()
                            dismissAllowingStateLoss()
                        }
                    }
                }
            }
        }

        // Focus + keyboard
        messageField.requestFocus()
        val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(messageField, InputMethodManager.SHOW_IMPLICIT)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(STATE_DRAFT, messageField.text?.toString().orEmpty())
        super.onSaveInstanceState(outState)
    }

    override fun onDestroyView() {
        val imm = context?.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.hideSoftInputFromWindow(messageField.windowToken, 0)
        super.onDestroyView()
    }

    private fun send() {
        val text = messageField.text?.toString().orEmpty()
        if (text.isBlank()) return
        presenter.replyComment(0, text)
    }

    /**
     * BottomSheetDialog инфлейтится со СВОЕЙ темой окна (`bottomSheetDialogTheme` →
     * DayNightAppTheme.BottomSheetDialog). Это полная тема, она ложится поверх темы активити с
     * force=true и пинит colorSurface = @color/light_colorPrimary — исторически «светлая подложка».
     * Под Material You `colorPrimary` это ДИНАМИЧЕСКИЙ акцент обоев, поэтому панель заливалась
     * сплошным цветным слэбом (коричневым на скрине пользователя), а текст/кнопки на нём тонули.
     *
     * Поэтому все цвета берём из темы АКТИВИТИ (там уже применён DynamicColors + оверлеи палитры)
     * и по M3-ролям, а не по `colorPrimary`: поверхность панели = colorSurface (та же, что у
     * MessagePanel в теме и QMS), поле ввода — контейнер чуть выше по лестнице, акценты — colorAccent.
     */
    private fun applyComposePanelTheme(view: View) {
        val themed = requireActivity()
        val panelColor = themed.getColorFromAttr(com.google.android.material.R.attr.colorSurface)
        val fieldColor = themed.getColorFromAttr(com.google.android.material.R.attr.colorSurfaceContainerHigh)
        val textColor = themed.getColorFromAttr(com.google.android.material.R.attr.colorOnSurface)
        val hintColor = themed.getColorFromAttr(com.google.android.material.R.attr.colorOnSurfaceVariant)
        val accentColor = themed.getColorFromAttr(R.attr.colorAccent)

        // Фон рисует САМ лист (MaterialShapeDrawable со скруглённым верхом из
        // ShapeAppearance.ForPDA.BottomSheet). Красим его тинтом, а не setBackgroundColor:
        // подмена фона плоским цветом срезала бы скругления. Контент при этом прозрачный.
        val sheet = (dialog as? BottomSheetDialog)
                ?.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
        val sheetBackground = sheet?.background
        if (sheetBackground != null) {
            sheetBackground.mutate().setTintList(ColorStateList.valueOf(panelColor))
            view.background = null
        } else {
            sheet?.setBackgroundColor(panelColor)
            view.setBackgroundColor(panelColor)
        }

        dragHandle.imageTintList = ColorStateList.valueOf(hintColor)
        title.setTextColor(textColor)
        buttonHide.imageTintList = ColorStateList.valueOf(hintColor)

        // Контейнер поля ввода. Заливка + обводка: в AMOLED-палитрах роли colorSurface и
        // colorSurfaceContainerHigh схлопываются в один тон, и одна заливка была бы неотличима
        // от панели — границу поля держит обводка.
        messageField.background = GradientDrawable().apply {
            cornerRadius = resources.getDimension(R.dimen.dp12)
            setColor(fieldColor)
            setStroke(
                    resources.getDimensionPixelSize(R.dimen.divider_thin).coerceAtLeast(1),
                    themed.getColorFromAttr(com.google.android.material.R.attr.colorOutlineVariant),
            )
        }
        messageField.setTextColor(textColor)
        messageField.setHintTextColor(hintColor)
        buttonSend.setTextColor(accentColor)
        buttonSend.iconTint = ColorStateList.valueOf(accentColor)
        sendProgress.setIndicatorColor(accentColor)
    }

    private fun hostFragment(): NewsDetailsFragment {
        return (parentFragment as? NewsDetailsFragment)
            ?: throw IllegalStateException("NewsCommentComposeBottomSheet must be shown from NewsDetailsFragment")
    }

    companion object {
        private const val STATE_DRAFT = "STATE_DRAFT"
        const val TAG = "NewsCommentComposeBottomSheet"
    }
}

private class SimpleTextWatcherAdapter(
    private val after: (String) -> Unit
) : android.text.TextWatcher {
    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
    override fun afterTextChanged(s: android.text.Editable?) {
        after(s?.toString().orEmpty())
    }
}

