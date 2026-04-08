package forpdateam.ru.forpda.ui.fragments.editpost

import android.app.Activity
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.os.BundleCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast

import moxy.presenter.InjectPresenter
import moxy.presenter.ProvidePresenter

import forpdateam.ru.forpda.App
import forpdateam.ru.forpda.R
import forpdateam.ru.forpda.common.FilePickHelper
import forpdateam.ru.forpda.entity.remote.editpost.AttachmentItem
import forpdateam.ru.forpda.entity.remote.editpost.EditPostForm
import forpdateam.ru.forpda.entity.remote.theme.ThemePage
import forpdateam.ru.forpda.model.data.remote.api.RequestFile
import forpdateam.ru.forpda.common.normalizeEditPostBodyForEditor
import forpdateam.ru.forpda.common.normalizeEditPostBodyFromDomHtml
import forpdateam.ru.forpda.presentation.editpost.EditPostPresenter
import forpdateam.ru.forpda.presentation.editpost.EditPostView
import forpdateam.ru.forpda.ui.fragments.TabFragment
import forpdateam.ru.forpda.ui.views.CodeEditor
import forpdateam.ru.forpda.ui.views.dialog.showWithStyledButtons
import forpdateam.ru.forpda.ui.views.messagepanel.MessagePanel
import forpdateam.ru.forpda.ui.views.messagepanel.attachments.AttachmentsPopup

/**
 * Created by radiationx on 14.01.17.
 */

class EditPostFragment : TabFragment(), EditPostView {

    private val pickFileLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@registerForActivityResult
        val data = result.data ?: return@registerForActivityResult
        uploadFiles(FilePickHelper.onActivityResult(context, data))
    }

    private var formType = 0

    private lateinit var messagePanel: MessagePanel
    private lateinit var attachmentsPopup: AttachmentsPopup
    private var pollPopup: EditPollPopup? = null

    @InjectPresenter
    lateinit var presenter: EditPostPresenter

    @ProvidePresenter
    fun providePresenter(): EditPostPresenter = EditPostPresenter(
            App.get().Di().editPostRepository,
            App.get().Di().themeTemplate,
            App.get().Di().router,
            App.get().Di().errorHandler
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.apply {
            val postForm = EditPostForm()
            postForm.type = getInt(EditPostForm.ARG_TYPE)
            formType = postForm.type
            BundleCompat.getParcelableArrayList(this, ARG_ATTACHMENTS, AttachmentItem::class.java)?.also {
                postForm.attachments.addAll(it)
            }
            postForm.message = getString(ARG_MESSAGE, "")
            getString(ARG_INITIAL_BODY, null)?.takeIf { it.isNotBlank() }?.let { raw ->
                postForm.message = if (raw.contains('<') && raw.contains('>')) {
                    normalizeEditPostBodyFromDomHtml(raw)
                } else {
                    normalizeEditPostBodyForEditor(raw)
                }
            }
            postForm.forumId = getInt(ARG_FORUM_ID)
            postForm.topicId = getInt(ARG_TOPIC_ID)
            postForm.postId = getInt(ARG_POST_ID)
            postForm.st = getInt(ARG_ST)
            presenter.initPostForm(postForm)
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        super.onCreateView(inflater, container, savedInstanceState)
        messagePanel = MessagePanel(context, fragmentContainer, fragmentContent, true)
        attachmentsPopup = messagePanel.attachmentsPopup
        return viewFragment
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        messagePanel.addSendOnClickListener { presenter.onSendClick() }
        attachmentsPopup.setAddOnClickListener { tryPickFile() }
        attachmentsPopup.setDeleteOnClickListener { removeFiles() }
        arguments?.apply {
            val title = getString(ARG_THEME_NAME, "")
            setTitle("${App.get().getString(if (formType == EditPostForm.TYPE_NEW_POST) R.string.editpost_title_answer else R.string.editpost_title_edit)} $title")
        }

        messagePanel.messageField.hint = null

        messagePanel.editPollButton.setOnClickListener {
            pollPopup?.show()
        }
    }

    override fun onResumeOrShow() {
        super.onResumeOrShow()
        messagePanel.onResume()
    }

    override fun onPauseOrHide() {
        super.onPauseOrHide()
        messagePanel.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
        messagePanel.onDestroy()
    }

    override fun hideKeyboard() {
        super.hideKeyboard()
        messagePanel.hidePopupWindows()
    }

    override fun onBackPressed(): Boolean {
        super.onBackPressed()
        if (messagePanel.onBackPressed())
            return true

        if (showExitDialog()) {
            return true
        }

        //Синхронизация с полем в фрагменте темы
        if (formType == EditPostForm.TYPE_NEW_POST) {
            showSyncDialog()
            return true
        }
        return false
    }

    private fun tryPickFile() {
        // ACTION_GET_CONTENT / Open Document не требует WRITE_EXTERNAL_STORAGE; на API 33+ это разрешение не выдаётся.
        pickFileLauncher.launch(FilePickHelper.pickFile(false))
    }

    override fun showForm(form: EditPostForm) {
        messagePanel.formProgress.visibility = View.GONE
        messagePanel.messageField.visibility = View.VISIBLE

        if (form.errorCode != EditPostForm.ERROR_NONE) {
            Toast.makeText(context, R.string.editpost_error_edit, Toast.LENGTH_SHORT).show()
            presenter.exit()
            return
        }

        if (form.poll != null) {
            pollPopup = EditPollPopup(context)
            pollPopup?.setPoll(form.poll)
            messagePanel.editPollButton.visibility = View.VISIBLE
        } else {
            messagePanel.editPollButton.visibility = View.GONE
        }

        attachmentsPopup.onLoadAttachments(form)
        // insertText() использует Editable.insert(selectionStart, …); при selectionStart == -1
        // (поле без фокуса) текст не появляется — для загрузки формы задаём текст целиком.
        messagePanel.setText(form.message)
        (messagePanel.messageField as? CodeEditor)?.updateHighlighting()
        messagePanel.messageField.requestFocus()
        showKeyboard(messagePanel.messageField)
        messagePanel.post { messagePanel.formProgress.visibility = View.GONE }
    }

    override fun setRefreshing(isRefreshing: Boolean) {
        messagePanel.formProgress.visibility = if (isRefreshing) View.VISIBLE else View.GONE
        // Поле не скрываем: иначе при сбое/долгой загрузке остаётся только кружок без текста.
        messagePanel.messageField.visibility = View.VISIBLE
    }

    override fun setSendRefreshing(isRefreshing: Boolean) {
        messagePanel.setProgressState(isRefreshing)
    }

    override fun sendMessage() {
        presenter.sendMessage(messagePanel.message, messagePanel.attachments)
    }

    override fun onPostSend(page: ThemePage, form: EditPostForm) {
        presenter.exitWithPage(page)
    }


    fun uploadFiles(files: List<RequestFile>) {
        val pending = attachmentsPopup.preUploadFiles(files)
        presenter.uploadFiles(files, pending)
    }

    private fun removeFiles() {
        attachmentsPopup.preDeleteFiles()
        val selectedFiles = attachmentsPopup.getSelected()
        presenter.deleteFiles(selectedFiles)
    }

    override fun onUploadFiles(items: List<AttachmentItem>) {
        attachmentsPopup.onUploadFiles(items)
    }

    override fun onDeleteFiles(items: List<AttachmentItem>) {
        attachmentsPopup.onDeleteFiles(items)
    }


    override fun showReasonDialog(form: EditPostForm) {
        val view = View.inflate(context, R.layout.edit_post_reason, null)
        val editText = view.findViewById<View>(R.id.edit_post_reason_field) as EditText
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

    private fun showExitDialog(): Boolean {
        if (formType == EditPostForm.TYPE_EDIT_POST) {
            MaterialAlertDialogBuilder(requireContext())
                    .setMessage(R.string.editpost_lose_changes)
                    .setPositiveButton(R.string.yes) { _, _ ->
                        presenter.exit()
                    }
                    .setNegativeButton(R.string.no, null)
                    .showWithStyledButtons()
            return true
        }
        return false
    }

    private fun showSyncDialog() {
        MaterialAlertDialogBuilder(requireContext())
                .setMessage(R.string.editpost_sync)
                .setPositiveButton(R.string.ok) { _, _ ->
                    val selectionRange = messagePanel.selectionRange
                    presenter.exitWithSync(
                            messagePanel.message,
                            selectionRange,
                            messagePanel.attachments
                    )
                }
                .setNegativeButton(R.string.no) { _, _ ->
                    if (!showExitDialog()) {
                        presenter.exit()
                    }
                }
                .showWithStyledButtons()
    }

    companion object {
        const val ARG_THEME_NAME = "theme_name"
        const val ARG_ATTACHMENTS = "attachments"
        const val ARG_MESSAGE = "message"
        const val ARG_FORUM_ID = "forumId"
        const val ARG_TOPIC_ID = "topicId"
        const val ARG_POST_ID = "postId"
        const val ARG_ST = "st"
        const val ARG_INITIAL_BODY = "initial_body"

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

        fun fillArguments(args: Bundle, form: EditPostForm, themeName: String?): Bundle {
            if (themeName != null)
                args.putString(ARG_THEME_NAME, themeName)
            args.putInt(EditPostForm.ARG_TYPE, EditPostForm.TYPE_NEW_POST)
            args.putParcelableArrayList(ARG_ATTACHMENTS, form.attachments)
            args.putString(ARG_MESSAGE, form.message)
            args.putInt(ARG_FORUM_ID, form.forumId)
            args.putInt(ARG_TOPIC_ID, form.topicId)
            args.putInt(ARG_POST_ID, form.postId)
            args.putInt(ARG_ST, form.st)
            return args
        }
    }

}
