package forpdateam.ru.forpda.ui.fragments.favorites

import android.content.Context
import android.text.InputType
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import forpdateam.ru.forpda.R
import forpdateam.ru.forpda.entity.app.favorites.FavFolder
import forpdateam.ru.forpda.entity.remote.favorites.FavItem
import forpdateam.ru.forpda.model.data.remote.api.favorites.FavoritesApi
import forpdateam.ru.forpda.presentation.favorites.FavoritesViewModel
import forpdateam.ru.forpda.ui.views.dialog.showWithStyledButtons
import java.util.Arrays

/**
 * Простые диалоги избранного, вынесенные из god-фрагмента [FavoritesFragment]
 * (декомпозиция §god-fragments): подтверждение «прочитать всё», выбор подписки,
 * подтверждение удаления выбранных. Leaf-слой: строит диалог и дёргает
 * [FavoritesViewModel] + колбэки. Поведение byte-identical оригиналу.
 *
 * Диалог item-меню (DynamicDialogMenu) и прогресс «прочитать всё» НЕ вынесены —
 * они завязаны на фрагмент как listener / lifecycle; здесь только чистые leaf-ы.
 */
class FavoritesDialogs(
        private val context: Context,
        private val presenter: FavoritesViewModel,
        private val showSnackbar: (Int) -> Unit,
) {

    fun openMarkAllFavoritesReadConfirmDialog() {
        val count = presenter.getMarkAllFavoritesReadCount()
        if (count <= 0) {
            showSnackbar(R.string.fav_mark_all_read_nothing)
            return
        }
        MaterialAlertDialogBuilder(context)
                .setTitle(R.string.fav_mark_all_read_title)
                .setMessage(context.getString(R.string.fav_mark_all_read_confirm, count))
                .setPositiveButton(R.string.fav_mark_all_read_button) { _, _ ->
                    presenter.markAllFavoritesRead()
                }
                .setNegativeButton(R.string.cancel, null)
                .showWithStyledButtons()
    }

    fun showSubscribeDialog(item: FavItem) {
        val subTypeIndex = Arrays.asList(*FavoritesApi.SUB_TYPES).indexOf(item.subType)
        MaterialAlertDialogBuilder(context)
                .setTitle(R.string.favorites_subscribe_email)
                .setSingleChoiceItems(FavoritesFragment.getSubNames(context), subTypeIndex) { dialog, which ->
                    presenter.changeFav(FavoritesApi.ACTION_EDIT_SUB_TYPE, FavoritesApi.SUB_TYPES[which], item.favId)
                    dialog.dismiss()
                }
                .showWithStyledButtons()
    }

    fun confirmDeleteSelected(items: List<FavItem>, onConfirmed: () -> Unit) {
        if (items.isEmpty()) return
        MaterialAlertDialogBuilder(context)
                .setTitle(R.string.fav_selection_delete_title)
                .setMessage(context.getString(R.string.fav_selection_delete_confirm, items.size))
                .setPositiveButton(R.string.delete) { _, _ ->
                    presenter.deleteFavorites(items)
                    onConfirmed()
                }
                .setNegativeButton(R.string.cancel, null)
                .showWithStyledButtons()
    }

    // --- Папки (локальная группировка избранного) ---

    fun showCreateFolderDialog(onCreated: ((FavFolder) -> Unit)? = null) {
        showFolderNameDialog(
                titleRes = R.string.fav_folder_create,
                positiveRes = R.string.add,
                initialName = null
        ) { name -> presenter.createFolder(name, onCreated) }
    }

    fun showRenameFolderDialog(folder: FavFolder) {
        showFolderNameDialog(
                titleRes = R.string.fav_folder_rename,
                positiveRes = R.string.save,
                initialName = folder.name
        ) { name -> presenter.renameFolder(folder.id, name) }
    }

    fun confirmDeleteFolder(folder: FavFolder) {
        MaterialAlertDialogBuilder(context)
                .setTitle(R.string.fav_folder_delete)
                .setMessage(context.getString(R.string.fav_folder_delete_message, folder.name))
                .setPositiveButton(R.string.delete) { _, _ -> presenter.deleteFolder(folder.id) }
                .setNegativeButton(R.string.cancel, null)
                .showWithStyledButtons()
    }

    /**
     * Перемещение выбранных тем в папку. Текущая папка отмечена галочкой только когда
     * все выбранные лежат в одной — иначе диалог просто предлагает, куда переложить.
     */
    fun showMoveToFolderDialog(
            items: List<FavItem>,
            folders: List<FavFolder>,
            onMoved: () -> Unit
    ) {
        if (items.isEmpty()) return
        val titles = (listOf(context.getString(R.string.fav_folder_none)) + folders.map { it.name })
                .toTypedArray()
        val currentFolders = items.map { presenter.folderOf(it) }.distinct()
        // Именно size == 1, а не singleOrNull(): у «Без папки» единственное значение — null,
        // и singleOrNull() не отличить от «выбраны темы из разных папок».
        val checkedIndex = if (currentFolders.size == 1) {
            val folderId = currentFolders.first()
            if (folderId == null) 0 else folders.indexOfFirst { it.id == folderId } + 1
        } else {
            -1
        }
        MaterialAlertDialogBuilder(context)
                .setTitle(R.string.fav_move_to_folder)
                .setSingleChoiceItems(titles, checkedIndex) { dialog, which ->
                    presenter.moveFavoritesToFolder(items, if (which == 0) null else folders[which - 1].id)
                    onMoved()
                    dialog.dismiss()
                }
                .setNeutralButton(R.string.fav_folder_create) { _, _ ->
                    showCreateFolderDialog { folder ->
                        presenter.moveFavoritesToFolder(items, folder.id)
                        onMoved()
                    }
                }
                .setNegativeButton(R.string.cancel, null)
                .showWithStyledButtons()
    }

    private fun showFolderNameDialog(
            @StringRes titleRes: Int,
            @StringRes positiveRes: Int,
            initialName: String?,
            onSubmit: (String) -> Unit
    ) {
        val input = TextInputEditText(context).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            setSingleLine()
            initialName?.let {
                setText(it)
                selectAll()
            }
        }
        val inputLayout = TextInputLayout(context).apply {
            hint = context.getString(R.string.fav_folder_name)
            val padding = resources.getDimensionPixelSize(R.dimen.content_padding_horizontal)
            setPadding(padding, padding / 2, padding, 0)
            addView(input)
        }
        val dialog = MaterialAlertDialogBuilder(context)
                .setTitle(titleRes)
                .setView(inputLayout)
                .setPositiveButton(positiveRes, null)
                .setNegativeButton(R.string.cancel, null)
                .showWithStyledButtons()
        // Кнопку перехватываем вручную: иначе диалог закрывается даже на пустом имени.
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val name = input.text?.toString()?.trim().orEmpty()
            if (name.isBlank()) {
                inputLayout.error = context.getString(R.string.fav_folder_name_empty)
                return@setOnClickListener
            }
            inputLayout.error = null
            onSubmit(name)
            dialog.dismiss()
        }
    }
}
