package forpdateam.ru.forpda.ui.fragments.favorites

import android.content.Context
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import forpdateam.ru.forpda.R
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
}
