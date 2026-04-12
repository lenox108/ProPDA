package forpdateam.ru.forpda.ui.fragments.notes

import android.content.Context
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import com.google.android.material.bottomsheet.BottomSheetDialog
import forpdateam.ru.forpda.App
import forpdateam.ru.forpda.R
import forpdateam.ru.forpda.entity.app.notes.NoteItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Created by radiationx on 06.09.17.
 */
class NotesAddPopup(
        context: Context?,
        item: NoteItem?
) {

    private val ctx: Context = context ?: throw IllegalArgumentException("context is null")

    private val dialog = BottomSheetDialog(ctx)
    private val title: TextView
    private val addButton: ImageButton
    private val titleField: EditText
    private val linkField: EditText
    private val contentField: EditText
    private val editingMode = item != null
    private val notesRepository = App.get().Di().notesRepository
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    init {
        @Suppress("DEPRECATION")
        val resizeMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        dialog.setOnShowListener {
            dialog.window?.setSoftInputMode(resizeMode)
        }
        dialog.setOnDismissListener {
            scope.cancel()
        }
        val view = View.inflate(ctx, R.layout.notes_popup, null)
        title = view.findViewById(R.id.popup_title)
        addButton = view.findViewById(R.id.add_button)
        titleField = view.findViewById(R.id.title_field)
        linkField = view.findViewById(R.id.link_field)
        contentField = view.findViewById(R.id.content_field)

        if (editingMode) {
            val existing = requireNotNull(item)
            title.setText(R.string.note_edit)
            titleField.setText(existing.title)
            linkField.setText(existing.link)
            contentField.setText(existing.content)
            addButton.setImageDrawable(App.getVecDrawable(ctx, R.drawable.ic_toolbar_done))
        } else {
            title.setText(R.string.note_create)
        }

        addButton.setOnClickListener {
            val t = titleField.text.toString().trim()
            val link = linkField.text.toString().trim()
            val content = contentField.text.toString().trim()

            if (t.isEmpty()) {
                Toast.makeText(ctx, R.string.note_enter_title, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val result: NoteItem = item ?: NoteItem().apply { setId(System.currentTimeMillis()) }
            result.setTitle(t)
            result.setLink(link)
            result.setContent(content)
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

        dialog.setContentView(view)
        dialog.show()
    }

    private fun errorHandlerToast(context: Context, e: Throwable) {
        e.printStackTrace()
        Toast.makeText(context, e.message ?: e.toString(), Toast.LENGTH_SHORT).show()
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
        fun showAddNoteDialog(context: Context?, title: String, link: String) {
            NotesAddPopup(context, null)
                    .setTitle(title)
                    .setLink(link)
        }

        @JvmStatic
        fun showAddNoteDialog(context: Context?, title: String, link: String, content: String) {
            NotesAddPopup(context, null)
                    .setTitle(title)
                    .setLink(link)
                    .setContent(content)
        }
    }
}
