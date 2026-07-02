package forpdateam.ru.forpda.ui.fragments.news.details

import android.content.Context
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ProgressBar
import androidx.appcompat.widget.AppCompatImageButton
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
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
     * BottomSheetDialog uses its own window theme; sync the sheet chrome with the active app palette
     * (same surface as [article_comments] write panel — ?attr/colorPrimary).
     */
    private fun applyComposePanelTheme(view: View) {
        val themed = requireActivity()
        val panelColor = themed.getColorFromAttr(R.attr.colorPrimary)
        val textColor = themed.getColorFromAttr(com.google.android.material.R.attr.colorOnSurface)
        val hintColor = themed.getColorFromAttr(com.google.android.material.R.attr.colorOnSurfaceVariant)
        val linkColor = themed.getColorFromAttr(com.google.android.material.R.attr.colorSecondary)

        view.setBackgroundColor(panelColor)
        (dialog as? BottomSheetDialog)
                ?.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
                ?.setBackgroundColor(panelColor)

        messageField.setTextColor(textColor)
        messageField.setHintTextColor(hintColor)
        buttonSend.setTextColor(linkColor)
        buttonSend.iconTint = ColorStateList.valueOf(linkColor)
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

