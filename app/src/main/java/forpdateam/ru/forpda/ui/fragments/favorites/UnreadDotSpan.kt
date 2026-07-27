package forpdateam.ru.forpda.ui.fragments.favorites

import android.graphics.Canvas
import android.graphics.Paint
import android.text.style.ReplacementSpan

/**
 * Точка «в папке есть непрочитанное» перед названием на чипе папки.
 *
 * Своя отрисовка, а не `chipIcon`: у M3 filter-чипа слот иконки занимает галочка выбранного
 * состояния, и точка там не видна. Заодно спан даёт то, чего у иконки нет — [scale] и [alpha],
 * которые снаружи гоняет аниматор (плавное появление + короткий пульс на новое сообщение).
 *
 * Цвет приходит готовым числом из `?attr/colorAccent`, чтобы точка следовала теме, палитре и
 * Material You (см. FavoritesFragment.addFolderChip).
 */
class UnreadDotSpan(
        private val color: Int,
        private val radiusPx: Float,
        private val gapPx: Float,
) : ReplacementSpan() {

    /** 1f — обычный размер; аниматор кратковременно раздувает точку до ~1.6f. */
    var scale: Float = 1f

    /** 0f..1f — используется при появлении точки. */
    var alpha: Float = 1f

    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = this@UnreadDotSpan.color }

    /**
     * Ширина в тексте берётся по НЕмасштабированной точке: иначе на пульсе чип дёргался бы
     * по ширине и утаскивал за собой соседние чипы.
     */
    override fun getSize(
            paint: Paint,
            text: CharSequence?,
            start: Int,
            end: Int,
            fm: Paint.FontMetricsInt?,
    ): Int = (radiusPx * 2 + gapPx).toInt()

    override fun draw(
            canvas: Canvas,
            text: CharSequence?,
            start: Int,
            end: Int,
            x: Float,
            top: Int,
            y: Int,
            bottom: Int,
            paint: Paint,
    ) {
        val cx = x + radiusPx
        // Центрируем по x-height текста, а не по строке целиком: со строкой точка визуально
        // проваливается вниз из-за нижних выносных элементов шрифта.
        val metrics = paint.fontMetrics
        val cy = y + (metrics.ascent + metrics.descent) / 2f
        dotPaint.alpha = (255 * alpha.coerceIn(0f, 1f)).toInt()
        canvas.drawCircle(cx, cy, radiusPx * scale, dotPaint)
    }
}
