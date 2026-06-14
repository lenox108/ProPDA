package forpdateam.ru.forpda.ui.fragments.other

import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.ItemTouchHelper
import forpdateam.ru.forpda.ui.views.drawers.adapters.MenuListItem
import forpdateam.ru.forpda.ui.views.drawers.adapters.OtherMenuSectionListItem

/**
 * Created by radiationx on 26.05.17.
 */

class OtherItemDragCallback(
        private val otherAdapter: OtherAdapter,
        private val listener: ItemTouchHelperListener
) : ItemTouchHelper.Callback() {

    private var isDragging = false

    override fun isLongPressDragEnabled(): Boolean {
        return false
    }

    override fun getMovementFlags(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder): Int {
        val dragFlags = if (isDraggableViewHolder(viewHolder)) {
            ItemTouchHelper.UP or ItemTouchHelper.DOWN or ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT
        } else {
            ItemTouchHelper.ACTION_STATE_IDLE
        }
        val swipeFlags = ItemTouchHelper.ACTION_STATE_IDLE
        return ItemTouchHelper.Callback.makeMovementFlags(dragFlags, swipeFlags)
    }

    override fun canDropOver(recyclerView: RecyclerView, current: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean {
        return isDraggableViewHolder(current) && isDropTargetViewHolder(target)
    }

    override fun onMove(
            recyclerView: RecyclerView,
            viewHolder: RecyclerView.ViewHolder,
            target: RecyclerView.ViewHolder
    ): Boolean {
        if (isDraggableViewHolder(viewHolder) && isDropTargetViewHolder(target)) {
            return otherAdapter.canMoveItem(viewHolder.bindingAdapterPosition, target.bindingAdapterPosition).also { canMove ->
                if (canMove) listener.onItemMove(viewHolder.bindingAdapterPosition, target.bindingAdapterPosition)
            }
        }
        return false
    }


    override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
    }

    override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
        super.onSelectedChanged(viewHolder, actionState)
        if (actionState == ItemTouchHelper.ACTION_STATE_DRAG && viewHolder != null && isDraggableViewHolder(viewHolder)) {
            isDragging = true
            listener.onDragStart()
            viewHolder.itemView.animate()
                    .alpha(0.9f)
                    .setDuration(120L)
                    .start()
            viewHolder.itemView.elevation = viewHolder.itemView.resources.getDimension(forpdateam.ru.forpda.R.dimen.dp8)
        }
    }

    override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
        super.clearView(recyclerView, viewHolder)
        if (!isDragging) return
        isDragging = false
        viewHolder.itemView.animate()
                .alpha(1f)
                .setDuration(120L)
                .start()
        viewHolder.itemView.elevation = 0f
        listener.onDragEnd()
    }

    private fun isDraggableViewHolder(viewHolder: RecyclerView.ViewHolder): Boolean =
            otherAdapter.isMenuEditMode() && otherAdapter.isDraggableItem(viewHolder.bindingAdapterPosition)

    private fun isDropTargetViewHolder(viewHolder: RecyclerView.ViewHolder): Boolean {
        val item = otherAdapter.items?.getOrNull(viewHolder.bindingAdapterPosition)
        return item is MenuListItem || item is OtherMenuSectionListItem
    }

    interface ItemTouchHelperListener {
        fun onDragStart()
        fun onItemMove(fromPosition: Int, toPosition: Int)
        fun onDragEnd()
    }
}
