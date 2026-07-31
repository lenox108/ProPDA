package forpdateam.ru.forpda.ui.views.drawers.adapters

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.Drawable
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import forpdateam.ru.forpda.R
import kotlin.math.abs
import kotlin.math.min

/**
 * Жесты списка открытых вкладок: свайп вправо — закрыть, свайп влево — закрепить/открепить,
 * перетаскивание вверх/вниз — свой порядок.
 *
 * Перетаскивание доступно только в режиме сортировки и стартует с касания ручки
 * ([ItemTouchHelper.startDrag] из [TabAdapter.Listener.onTabDragStart]), а не по долгому нажатию:
 * на удержании нижний лист успевает увести вертикальный жест себе и вместо строки уезжает шторка.
 *
 * Подложка рисуется по форме плашки строки (тот же радиус, что у
 * [forpdateam.ru.forpda.ui.applyListRowPlate]), со значком у открывшегося края и подтапливанием
 * самой строки — чтобы направление жеста читалось ещё до порога.
 */
abstract class TabTouchCallback(
        context: Context,
        closeColor: Int,
        pinColor: Int,
) : ItemTouchHelper.SimpleCallback(
        ItemTouchHelper.UP or ItemTouchHelper.DOWN,
        ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT
) {

    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val cornerRadius = context.resources.getDimension(R.dimen.list_plate_corner_radius)
    private val iconInset = context.resources.getDimensionPixelSize(R.dimen.dp16)
    private val closeBackground = closeColor
    private val pinBackground = pinColor
    private val closeIcon = context.tintedIcon(R.drawable.ic_clear, closeColor)
    private val pinIcon = context.tintedIcon(R.drawable.ic_pin, pinColor)
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
        viewHolder.itemView.translationX = 0f
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
            val swipingToClose = dX > 0

            backgroundPaint.color = if (swipingToClose) closeBackground else pinBackground
            backgroundPaint.alpha = (110 + 145 * progress).toInt()
            if (swipingToClose) {
                bounds.set(itemView.left.toFloat(), itemView.top.toFloat(), itemView.left + dX, itemView.bottom.toFloat())
            } else {
                bounds.set(itemView.right + dX, itemView.top.toFloat(), itemView.right.toFloat(), itemView.bottom.toFloat())
            }
            c.drawRoundRect(bounds, cornerRadius, cornerRadius, backgroundPaint)

            val drawable = if (swipingToClose) closeIcon else pinIcon
            drawable?.also {
                val size = it.intrinsicHeight.coerceAtLeast(1)
                val top = itemView.top + (itemView.height - size) / 2
                val left = if (swipingToClose) itemView.left + iconInset else itemView.right - iconInset - size
                // Значок появляется только когда под ним уже есть подложка.
                if (abs(dX) > iconInset + size) {
                    it.alpha = (255 * progress).toInt()
                    it.setBounds(left, top, left + size, top + size)
                    it.draw(c)
                }
            }

            itemView.alpha = 1f - 0.4f * min(abs(dX) / width, 1f)
        }

        super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
    }

    /** Значок красим в контрастный к своей подложке цвет, иначе он тонет в ней на светлых палитрах. */
    private fun Context.tintedIcon(iconRes: Int, backgroundColor: Int): Drawable? =
            ContextCompat.getDrawable(this, iconRes)?.mutate()?.apply {
                setTint(if (ColorUtils.calculateLuminance(backgroundColor) > 0.5) Color.BLACK else Color.WHITE)
            }
}
