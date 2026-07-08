package forpdateam.ru.forpda.ui.fragments.notes

import android.content.Context
import android.text.InputType
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.PopupMenu
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import forpdateam.ru.forpda.R
import forpdateam.ru.forpda.entity.app.notes.NoteFolder
import forpdateam.ru.forpda.entity.app.notes.NoteItem
import forpdateam.ru.forpda.entity.app.notes.NoteSortMode
import forpdateam.ru.forpda.presentation.notes.NotesViewModel
import forpdateam.ru.forpda.ui.views.dialog.showWithStyledButtons

/**
 * Все диалоги/попапы экрана заметок, вынесенные из god-фрагмента [NotesFragment]
 * (декомпозиция §god-fragments). Чистый leaf-слой: только строит диалоги, дёргает
 * [NotesViewModel] и колбэк [clearSelection]; собственного состояния почти нет
 * (кроме анти-дабл-шоу [folderManagementDialog], перенесённого сюда 1:1).
 *
 * Поведение byte-identical оригиналу — методы перенесены без изменения логики,
 * динамические данные (папки/режим сортировки/заметка/выбранные id) приходят
 * параметрами вместо чтения полей фрагмента.
 */
class NotesDialogs(
        private val context: Context,
        private val viewModel: NotesViewModel,
        private val clearSelection: () -> Unit,
) {

    private var folderManagementDialog: AlertDialog? = null

    fun showFolderSelectorPopup(anchor: View, folders: List<NoteFolder>) {
        clearSelection()
        PopupMenu(context, anchor).apply {
            menu.add(R.string.note_all_folders).setOnMenuItemClickListener {
                viewModel.selectAllFolders()
                true
            }
            menu.add(R.string.note_without_folder).setOnMenuItemClickListener {
                viewModel.selectFolder(null)
                true
            }
            folders.forEach { folder ->
                menu.add(folder.name).setOnMenuItemClickListener {
                    viewModel.selectFolder(folder.id)
                    true
                }
            }
        }.show()
    }

    fun showSortDialog(currentMode: NoteSortMode) {
        clearSelection()
        val modes = arrayOf(
                NoteSortMode.CREATED_DESC,
                NoteSortMode.UPDATED_DESC,
                NoteSortMode.TITLE_ASC,
                NoteSortMode.MANUAL
        )
        val titles = arrayOf(
                context.getString(R.string.note_sort_created),
                context.getString(R.string.note_sort_updated),
                context.getString(R.string.note_sort_title),
                context.getString(R.string.note_sort_manual)
        )
        val checkedIndex = modes.indexOf(currentMode).takeIf { it >= 0 } ?: 0
        MaterialAlertDialogBuilder(context)
                .setTitle(R.string.sorting_title)
                .setSingleChoiceItems(titles, checkedIndex) { dialog, which ->
                    viewModel.setSortMode(modes[which])
                    dialog.dismiss()
                }
                .setNegativeButton(R.string.cancel, null)
                .showWithStyledButtons()
    }

    fun showCreateFolderDialog(onCreated: ((NoteFolder) -> Unit)? = null) {
        val input = TextInputEditText(context).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            setSingleLine()
        }
        val inputLayout = TextInputLayout(context).apply {
            hint = context.getString(R.string.note_folder_name)
            val padding = resources.getDimensionPixelSize(R.dimen.content_padding_horizontal)
            setPadding(padding, padding / 2, padding, 0)
            addView(input)
        }
        val dialog = MaterialAlertDialogBuilder(context)
                .setTitle(R.string.note_create_folder)
                .setView(inputLayout)
                .setPositiveButton(R.string.add, null)
                .setNegativeButton(R.string.cancel, null)
                .showWithStyledButtons()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val name = input.text?.toString()?.trim().orEmpty()
            if (name.isBlank()) {
                inputLayout.error = context.getString(R.string.note_folder_name_empty)
                return@setOnClickListener
            }
            inputLayout.error = null
            viewModel.createFolder(name, onCreated)
            dialog.dismiss()
        }
    }

    fun showMoveToFolderDialog(note: NoteItem, folders: List<NoteFolder>) {
        val titles = listOf(context.getString(R.string.note_without_folder)) + folders.map { it.name }
        val checkedIndex = note.folderId?.let { id -> folders.indexOfFirst { it.id == id } + 1 }
                ?.takeIf { it > 0 } ?: 0
        MaterialAlertDialogBuilder(context)
                .setTitle(R.string.note_move_to_folder)
                .setSingleChoiceItems(titles.toTypedArray(), checkedIndex) { dialog, which ->
                    viewModel.moveNoteToFolder(note.id, if (which == 0) null else folders[which - 1].id)
                    dialog.dismiss()
                }
                .setNeutralButton(R.string.note_create_folder) { _, _ ->
                    showCreateFolderDialog { folder -> viewModel.moveNoteToFolder(note.id, folder.id) }
                }
                .setNegativeButton(R.string.cancel, null)
                .showWithStyledButtons()
    }

    fun showMoveSelectedToFolderDialog(ids: List<Long>, folders: List<NoteFolder>) {
        if (ids.isEmpty()) return
        val titles = listOf(context.getString(R.string.note_without_folder)) + folders.map { it.name }
        MaterialAlertDialogBuilder(context)
                .setTitle(R.string.note_move_to_folder)
                .setItems(titles.toTypedArray()) { dialog, which ->
                    viewModel.moveNotesToFolder(ids, if (which == 0) null else folders[which - 1].id)
                    clearSelection()
                    dialog.dismiss()
                }
                .setNeutralButton(R.string.note_create_folder) { _, _ ->
                    showCreateFolderDialog { folder ->
                        viewModel.moveNotesToFolder(ids, folder.id)
                        clearSelection()
                    }
                }
                .setNegativeButton(R.string.cancel, null)
                .showWithStyledButtons()
    }

    fun confirmDeleteSelectedNotes(ids: List<Long>) {
        if (ids.isEmpty()) return
        MaterialAlertDialogBuilder(context)
                .setTitle(R.string.note_delete_selected_title)
                .setMessage(context.getString(R.string.note_delete_selected_message, ids.size))
                .setPositiveButton(R.string.delete) { _, _ ->
                    viewModel.deleteNotes(ids)
                    clearSelection()
                }
                .setNegativeButton(R.string.cancel, null)
                .showWithStyledButtons()
    }

    fun showFolderManagementDialog(folders: List<NoteFolder>) {
        if (folderManagementDialog?.isShowing == true) return
        if (folders.isEmpty()) {
            Toast.makeText(context, R.string.funny_notes_nodata_title, Toast.LENGTH_SHORT).show()
            return
        }
        folderManagementDialog = MaterialAlertDialogBuilder(context)
                .setTitle(R.string.note_manage_folders)
                .setItems(folders.map { it.name }.toTypedArray()) { _, which ->
                    showFolderActionsDialog(folders[which])
                }
                .setNegativeButton(R.string.cancel, null)
                .showWithStyledButtons()
                .also { dialog ->
                    dialog.setOnDismissListener { folderManagementDialog = null }
                }
    }

    private fun showFolderActionsDialog(folder: NoteFolder) {
        val actions = arrayOf(
                context.getString(R.string.note_rename_folder),
                context.getString(R.string.note_delete_folder),
        )
        MaterialAlertDialogBuilder(context)
                .setTitle(folder.name)
                .setItems(actions) { _, which ->
                    when (which) {
                        0 -> showRenameFolderDialog(folder)
                        1 -> confirmDeleteFolder(folder)
                    }
                }
                .setNegativeButton(R.string.cancel, null)
                .showWithStyledButtons()
    }

    fun showRenameFolderDialog(folder: NoteFolder) {
        val input = TextInputEditText(context).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            setSingleLine()
            setText(folder.name)
            selectAll()
        }
        val inputLayout = TextInputLayout(context).apply {
            hint = context.getString(R.string.note_folder_name)
            val padding = resources.getDimensionPixelSize(R.dimen.content_padding_horizontal)
            setPadding(padding, padding / 2, padding, 0)
            addView(input)
        }
        val dialog = MaterialAlertDialogBuilder(context)
                .setTitle(R.string.note_rename_folder)
                .setView(inputLayout)
                .setPositiveButton(R.string.save, null)
                .setNegativeButton(R.string.cancel, null)
                .showWithStyledButtons()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val name = input.text?.toString()?.trim().orEmpty()
            if (name.isBlank()) {
                inputLayout.error = context.getString(R.string.note_folder_name_empty)
                return@setOnClickListener
            }
            inputLayout.error = null
            viewModel.renameFolder(folder.id, name)
            dialog.dismiss()
        }
    }

    fun confirmDeleteFolder(folder: NoteFolder) {
        MaterialAlertDialogBuilder(context)
                .setTitle(R.string.note_delete_folder)
                .setMessage(context.getString(R.string.note_delete_folder_message, folder.name))
                .setPositiveButton(R.string.delete) { _, _ -> viewModel.deleteFolder(folder.id) }
                .setNegativeButton(R.string.cancel, null)
                .showWithStyledButtons()
    }
}
