package forpdateam.ru.forpda.ui.views.drawers.adapters

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import forpdateam.ru.forpda.R
import kotlin.math.abs
import kotlin.math.min

/**
 * Жесты списка открытых вкладок: свайп вбок — закрыть, перетаскивание вверх/вниз — свой порядок.
 *
 * Перетаскивание доступно только в режиме сортировки и стартует с касания ручки
 * ([ItemTouchHelper.startDrag] из [TabAdapter.Listener.onTabDragStart]), а не по долгому нажатию:
 * на удержании нижний лист успевает увести вертикальный жест себе и вместо строки уезжает шторка.
 *
 * Свайп рисует подложку по форме плашки строки (тот же радиус, что у [forpdateam.ru.forpda.ui.applyListRowPlate]),
 * значок закрытия у открывшегося края и подтапливает саму строку — чтобы жест читался ещё до порога.
 */
abstract class TabTouchCallback(
        context: Context,
        backgroundColor: Int,
) : ItemTouchHelper.SimpleCallback(
        ItemTouchHelper.UP or ItemTouchHelper.DOWN,
        ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT
) {

    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = backgroundColor }
    private val cornerRadius = context.resources.getDimension(R.dimen.list_plate_corner_radius)
    private val iconInset = context.resources.getDimensionPixelSize(R.dimen.dp16)
    private val icon = ContextCompat.getDrawable(context, R.drawable.ic_clear)?.mutate()?.apply {
        val onBackground = if (ColorUtils.calculateLuminance(backgroundColor) > 0.5) Color.BLACK else Color.WHITE
        setTint(onBackground)
    }
    private val bounds = RectF()
    private var moved = false

    /** @return true, если перестановка принята адаптером. */
    abstract fun onRowMoved(from: Int, to: Int): Boolean

    /** Перетаскивание завершено — можно зафиксировать порядок в модели. */
    abstract fun onRowMoveFinished()

    override fun isLongPressDragEnabled(): Boolean = false

    override fun onMove(
            recyclerView: RecyclerView,
            viewHolder: RecyclerView.ViewHolder,
            target: RecyclerView.ViewHolder,
    ): Boolean {
        val from = viewHolder.bindingAdapterPosition
        val to = target.bindingAdapterPosition
        if (from == RecyclerView.NO_POSITION || to == RecyclerView.NO_POSITION) return false
        if (!onRowMoved(from, to)) return false
        moved = true
        return true
    }

    override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
        super.clearView(recyclerView, viewHolder)
        viewHolder.itemView.alpha = 1f
        if (moved) {
            moved = false
            onRowMoveFinished()
        }
    }

    override fun onChildDraw(
            c: Canvas,
            recyclerView: RecyclerView,
            viewHolder: RecyclerView.ViewHolder,
            dX: Float,
            dY: Float,
            actionState: Int,
            isCurrentlyActive: Boolean,
    ) {
        if (actionState == ItemTouchHelper.ACTION_STATE_SWIPE && dX != 0f) {
            val itemView = viewHolder.itemView
            val width = (itemView.right - itemView.left).toFloat()
            val progress = min(abs(dX) / (width / 2f), 1f)

            backgroundPaint.alpha = (110 + 145 * progress).toInt()
            if (dX > 0) {
                bounds.set(itemView.left.toFloat(), itemView.top.toFloat(), itemView.left + dX, itemView.bottom.toFloat())
            } else {
                bounds.set(itemView.right + dX, itemView.top.toFloat(), itemView.right.toFloat(), itemView.bottom.toFloat())
            }
            c.drawRoundRect(bounds, cornerRadius, cornerRadius, backgroundPaint)

            icon?.also { drawable ->
                val size = drawable.intrinsicHeight.coerceAtLeast(1)
                val top = itemView.top + (itemView.height - size) / 2
                val left = if (dX > 0) {
                    itemView.left + iconInset
                } else {
                    itemView.right - iconInset - size
                }
                // Значок появляется только когда под ним уже есть подложка.
                if (abs(dX) > iconInset + size) {
                    drawable.alpha = (255 * progress).toInt()
                    drawable.setBounds(left, top, left + size, top + size)
                    drawable.draw(c)
                }
            }

            itemView.alpha = 1f - 0.4f * min(abs(dX) / width, 1f)
        }

        super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
    }
}
