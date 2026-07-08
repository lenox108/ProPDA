package forpdateam.ru.forpda.ui.fragments.notes

import forpdateam.ru.forpda.common.getVecDrawable
import forpdateam.ru.forpda.databinding.NotesPopupBinding
import forpdateam.ru.forpda.model.repository.note.NotesRepository
import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.AutoCompleteTextView
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import forpdateam.ru.forpda.common.showSnackbar
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import forpdateam.ru.forpda.R
import forpdateam.ru.forpda.entity.app.notes.NoteFolder
import forpdateam.ru.forpda.entity.app.notes.NoteItem
import forpdateam.ru.forpda.ui.views.dialog.showWithStyledButtons
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Created by radiationx on 06.09.17.
 */
class NotesAddPopup(
        context: Context?,
        item: NoteItem?,
        private val notesRepository: NotesRepository
) {
    private val ctx: Context = context ?: throw IllegalArgumentException("context is null")

    private val dialog = BottomSheetDialog(ctx)
    private val binding = NotesPopupBinding.inflate(ctx.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater)
    private val title: TextView = binding.popupTitle
    private val addButton: ImageButton = binding.addButton
    private val titleField: EditText = binding.titleField
    private val linkField: EditText = binding.linkField
    private val contentField: EditText = binding.contentField
    private val editingMode = item != null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    init {
        // The popup background is ?attr/colorPrimary. The default caret tint (colorControlActivated)
        // — and even ?attr/colorAccent on the low-saturation «reading» palettes — resolves close to
        // that background, so the caret is present but invisible (user: «курсора вообще нет»). Tint
        // the caret to the field's own text color, which is always contrast-checked against the bg.
        listOf(titleField, linkField, contentField).forEach { applyReadableCursor(it) }

        val resizeMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        dialog.setOnShowListener {
            dialog.window?.setSoftInputMode(resizeMode)
        }
        dialog.setOnDismissListener {
            scope.cancel()
        }

        if (editingMode) {
            val existing = requireNotNull(item)
            title.setText(R.string.note_edit)
            titleField.setText(existing.title)
            linkField.setText(existing.link)
            contentField.setText(existing.content)
            addButton.setImageDrawable(ctx.getVecDrawable(R.drawable.ic_toolbar_done))
        } else {
            title.setText(R.string.note_create)
        }

        addButton.setOnClickListener {
            val t = titleField.text.toString().trim()
            val link = linkField.text.toString().trim()
            val content = contentField.text.toString().trim()

            if (t.isEmpty()) {
                addButton.showSnackbar(R.string.note_enter_title)
                return@setOnClickListener
            }

            val result: NoteItem = item ?: NoteItem(id = System.currentTimeMillis())
            result.title = t
            result.link = link
            result.content = content
            scope.launch {
                runCatching {
                    if (editingMode) notesRepository.updateNote(result)
                    else notesRepository.addNote(result)
                }.onSuccess {
                    dialog.dismiss()
                }.onFailure { e ->
                    errorHandlerToast(ctx, e)
                }
            }
        }

        dialog.setContentView(binding.root)
        dialog.show()
    }

    private fun errorHandlerToast(context: Context, e: Throwable) {
        Timber.e(e, "Notes add error")
        binding.root.showSnackbar(e.message ?: e.toString())
    }

    fun setTitle(titleText: String): NotesAddPopup {
        titleField.setText(titleText)
        return this
    }

    fun setLink(link: String): NotesAddPopup {
        linkField.setText(link)
        return this
    }

    fun setContent(content: String): NotesAddPopup {
        contentField.setText(content)
        return this
    }

    companion object {
        @JvmStatic
        fun showAddNoteDialog(context: Context?, title: String, link: String, notesRepository: NotesRepository) {
            NotesAddPopup(context, null, notesRepository)
                    .setTitle(title)
                    .setLink(link)
        }

        @JvmStatic
        fun showAddNoteDialog(context: Context?, title: String, link: String, content: String, notesRepository: NotesRepository) {
            NotesAddPopup(context, null, notesRepository)
                    .setTitle(title)
                    .setLink(link)
                    .setContent(content)
        }

        @JvmStatic
        fun showCreateBookmarkDialog(context: Context?, title: String, link: String, notesRepository: NotesRepository) {
            val ctx = context ?: return
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
            val titleField = TextInputEditText(ctx).apply {
                applyBookmarkInputStyle(ctx)
                setText(title)
                setSingleLine(false)
                maxLines = 3
            }
            val titleLayout = TextInputLayout(ctx).apply {
                hint = ctx.getString(R.string.title)
                addView(titleField)
            }
            val contentField = TextInputEditText(ctx).apply {
                applyBookmarkInputStyle(ctx)
                inputType = InputType.TYPE_CLASS_TEXT or
                        InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or
                        InputType.TYPE_TEXT_FLAG_MULTI_LINE
                maxLines = 6
                setSingleLine(false)
            }
            val contentLayout = TextInputLayout(ctx).apply {
                hint = ctx.getString(R.string.note_content)
                addView(contentField)
            }
            val folderLayout = TextInputLayout(ctx).apply {
                hint = ctx.getString(R.string.note_folder)
                isFocusable = false
            }
            val folderField = AutoCompleteTextView(ctx).apply {
                inputType = InputType.TYPE_NULL
                isFocusable = false
                setText(ctx.getString(R.string.note_without_folder))
            }
            folderLayout.addView(folderField)

            val content = android.widget.LinearLayout(ctx).apply {
                orientation = android.widget.LinearLayout.VERTICAL
                val padding = ctx.resources.getDimensionPixelSize(R.dimen.content_padding_horizontal)
                setPadding(padding, padding / 2, padding, 0)
                addView(titleLayout)
                addView(contentLayout, android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = ctx.resources.getDimensionPixelSize(R.dimen.dp8)
                })
                addView(folderLayout, android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = ctx.resources.getDimensionPixelSize(R.dimen.dp8)
                })
            }

            var folders: List<NoteFolder> = emptyList()
            var selectedFolderId: Long? = null

            fun updateFolderText() {
                val selectedName = folders.firstOrNull { it.id == selectedFolderId }?.name
                folderField.setText(selectedName ?: ctx.getString(R.string.note_without_folder))
            }

            fun showFolderChooser() {
                val names = listOf(ctx.getString(R.string.note_without_folder)) + folders.map { it.name }
                val checkedIndex = selectedFolderId?.let { id -> folders.indexOfFirst { it.id == id } + 1 }
                    ?.takeIf { it > 0 } ?: 0
                MaterialAlertDialogBuilder(ctx)
                    .setTitle(R.string.note_folder)
                    .setSingleChoiceItems(names.toTypedArray(), checkedIndex) { dialog, which ->
                        selectedFolderId = if (which == 0) null else folders[which - 1].id
                        updateFolderText()
                        dialog.dismiss()
                    }
                    .setNegativeButton(R.string.cancel, null)
                    .showWithStyledButtons()
            }

            fun showCreateFolderDialog() {
                val input = TextInputEditText(ctx).apply {
                    applyBookmarkInputStyle(ctx)
                    inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                    setSingleLine()
                }
                val inputLayout = TextInputLayout(ctx).apply {
                    hint = ctx.getString(R.string.note_folder_name)
                    val padding = ctx.resources.getDimensionPixelSize(R.dimen.content_padding_horizontal)
                    setPadding(padding, padding / 2, padding, 0)
                    addView(input)
                }
                MaterialAlertDialogBuilder(ctx)
                    .setTitle(R.string.note_create_folder)
                    .setView(inputLayout)
                    .setPositiveButton(R.string.add) { _, _ ->
                        val name = input.text?.toString()?.trim().orEmpty()
                        if (name.isBlank()) {
                            Toast.makeText(ctx, R.string.note_folder_name_empty, Toast.LENGTH_SHORT).show()
                            return@setPositiveButton
                        }
                        scope.launch {
                            runCatching { notesRepository.createFolder(name) }
                                .onSuccess { folder ->
                                    folders = folders + folder
                                    selectedFolderId = folder.id
                                    updateFolderText()
                                }
                                .onFailure { e ->
                                    Timber.e(e, "Notes folder create error")
                                    Toast.makeText(ctx, e.message ?: e.toString(), Toast.LENGTH_SHORT).show()
                                }
                        }
                    }
                    .setNegativeButton(R.string.cancel, null)
                    .showWithStyledButtons()
            }

            folderField.setOnClickListener { showFolderChooser() }
            folderLayout.setEndIconMode(TextInputLayout.END_ICON_DROPDOWN_MENU)
            folderLayout.setEndIconOnClickListener { showFolderChooser() }

            val dialogBuilder = MaterialAlertDialogBuilder(ctx)
                .setTitle(R.string.create_note)
                .setView(content)
                .setNeutralButton(R.string.note_create_folder, null)
                .setPositiveButton(R.string.save, null)
                .setNegativeButton(R.string.cancel, null)

            val dialog = dialogBuilder.showWithStyledButtons()
            dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_NEUTRAL)
                .setOnClickListener { showCreateFolderDialog() }
            dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener {
                    val noteTitle = titleField.text?.toString()?.trim().orEmpty()
                    val noteContent = contentField.text?.toString()?.trim().orEmpty()
                    if (noteTitle.isBlank()) {
                        titleField.error = ctx.getString(R.string.note_enter_title)
                        return@setOnClickListener
                    }
                    scope.launch {
                        runCatching {
                            notesRepository.addNote(
                                NoteItem(
                                    id = System.currentTimeMillis(),
                                    title = noteTitle,
                                    link = link,
                                    content = noteContent,
                                    folderId = selectedFolderId
                                )
                            )
                        }.onSuccess {
                            dialog.dismiss()
                        }.onFailure { e ->
                            Timber.e(e, "Notes bookmark create error")
                            Toast.makeText(ctx, e.message ?: e.toString(), Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            dialog.setOnDismissListener { scope.cancel() }

            scope.launch {
                runCatching { withContext(Dispatchers.IO) { notesRepository.loadFolders() } }
                    .onSuccess {
                        folders = it
                        updateFolderText()
                    }
                    .onFailure { e ->
                        Timber.e(e, "Notes folders load error")
                        Toast.makeText(ctx, e.message ?: e.toString(), Toast.LENGTH_SHORT).show()
                    }
            }
        }

        private fun TextInputEditText.applyBookmarkInputStyle(context: Context) {
            applyReadableCursor(this)
        }

        /**
         * Forces a caret that is guaranteed to be visible: a 2dp bar tinted with the field's own
         * text color. The default caret tint (colorControlActivated), and ?attr/colorAccent on the
         * muted «reading» palettes, can resolve close to the ?attr/colorPrimary dialog background and
         * disappear. The text color is the one color we know contrasts with that background — it's
         * the same color the user is already reading in the field.
         */
        private fun applyReadableCursor(field: EditText) {
            field.isCursorVisible = true
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val widthPx = (2f * field.resources.displayMetrics.density).toInt().coerceAtLeast(2)
                field.textCursorDrawable = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    setSize(widthPx, 0)
                    setColor(field.currentTextColor)
                }
            }
        }
    }
}
