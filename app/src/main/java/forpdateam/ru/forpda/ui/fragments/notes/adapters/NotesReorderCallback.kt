package forpdateam.ru.forpda.ui.fragments.notes.adapters

import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import forpdateam.ru.forpda.ui.views.drawers.adapters.NoteListItem

/**
 * Drag-and-drop перестановка заметок для режима ручной сортировки
 * ([forpdateam.ru.forpda.entity.app.notes.NoteSortMode.MANUAL]).
 *
 * Тащить можно только заметки ([NoteListItem]) и только внутри их собственной папки —
 * заголовки секций, папки и заметки других папок не являются валидной целью
 * ([onMove]/[canDropOver] это отсекают). Во время перетаскивания список меняется лишь
 * визуально ([NotesAdapter.moveItem]); фактический порядок пишется в БД один раз на drop
 * ([clearView] → [onReordered]).
 *
 * Долгий тап стартует drag вручную из [NoteAdapterDelegate] (см. [isLongPressDragEnabled]),
 * чтобы не конфликтовать с режимом выделения на не-ручных сортировках.
 */
class NotesReorderCallback(
        private val adapter: NotesAdapter,
        private val onReordered: (List<Long>) -> Unit
) : ItemTouchHelper.Callback() {

    override fun isLongPressDragEnabled(): Boolean = false

    override fun isItemViewSwipeEnabled(): Boolean = false

    override fun getMovementFlags(
            recyclerView: RecyclerView,
            viewHolder: RecyclerView.ViewHolder
    ): Int {
        val dragFlags = if (adapter.itemAt(viewHolder.bindingAdapterPosition) is NoteListItem) {
            ItemTouchHelper.UP or ItemTouchHelper.DOWN
        } else {
            0
        }
        return makeMovementFlags(dragFlags, 0)
    }

    override fun onMove(
            recyclerView: RecyclerView,
            viewHolder: RecyclerView.ViewHolder,
            target: RecyclerView.ViewHolder
    ): Boolean {
        val from = viewHolder.bindingAdapterPosition
        val to = target.bindingAdapterPosition
        if (from == RecyclerView.NO_POSITION || to == RecyclerView.NO_POSITION) return false
        if (!sameFolder(from, to)) return false
        adapter.moveItem(from, to)
        return true
    }

    override fun canDropOver(
            recyclerView: RecyclerView,
            current: RecyclerView.ViewHolder,
            target: RecyclerView.ViewHolder
    ): Boolean = sameFolder(current.bindingAdapterPosition, target.bindingAdapterPosition)

    override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) = Unit

    override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
        super.clearView(recyclerView, viewHolder)
        onReordered(adapter.noteIdsInSameFolder(viewHolder.bindingAdapterPosition))
    }

    private fun sameFolder(posA: Int, posB: Int): Boolean {
        val a = adapter.itemAt(posA) as? NoteListItem ?: return false
        val b = adapter.itemAt(posB) as? NoteListItem ?: return false
        return a.item.folderId == b.item.folderId
    }
}
