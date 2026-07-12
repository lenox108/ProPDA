package forpdateam.ru.forpda.ui.fragments.other

import android.graphics.Rect
import android.view.View
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlin.math.roundToInt

/**
 * Горизонтальные отступы сетки плиток меню: [outerPx] по краям экрана, [gapPx] между
 * плитками, равная ширина колонок. Плитки бывают по 2 и по 3 в ряд (spanSize 6/4 при
 * spanCount=12), поэтому left/right считаются по позиции колонки — так внешние края
 * встают ровно на [outerPx], совпадая с карточкой профиля, заголовками секций и
 * плашками списков (все на content_padding_horizontal).
 *
 * Полноширинные строки (spanSize == spanCount) сохраняют свои собственные margin —
 * им отступы не добавляются.
 */
class MenuTileSpacingDecoration(
        private val spanCount: Int,
        private val outerPx: Int,
        private val gapPx: Int,
) : RecyclerView.ItemDecoration() {

    override fun getItemOffsets(
            outRect: Rect,
            view: View,
            parent: RecyclerView,
            state: RecyclerView.State,
    ) {
        val lp = view.layoutParams as? GridLayoutManager.LayoutParams ?: return
        val spanSize = lp.spanSize
        if (spanSize <= 0 || spanSize >= spanCount) {
            // Full-width row (профиль, заголовок, плашка, выход) — не трогаем.
            outRect.left = 0
            outRect.right = 0
            return
        }
        val columns = spanCount / spanSize
        val colIndex = lp.spanIndex / spanSize
        // Равная ширина колонок: каждая резервирует одинаковый суммарный отступ total,
        // при этом left_0 == right_{last} == outer и интервал между плитками == gap.
        val total = (2.0 * outerPx + gapPx.toDouble() * (columns - 1)) / columns
        val left = outerPx + colIndex * (gapPx - total)
        outRect.left = left.roundToInt()
        outRect.right = (total - left).roundToInt()
    }
}
