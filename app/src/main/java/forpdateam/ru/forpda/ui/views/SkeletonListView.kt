package forpdateam.ru.forpda.ui.views

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import forpdateam.ru.forpda.common.getColorFromAttr

/**
 * Лёгкий скелетон-плейсхолдер списка с shimmer — БЕЗ внешней зависимости.
 * Рисует фейковые строки (плашки на colorSurfaceVariant) и гоняет по ним
 * диагональную световую полосу (SRC_ATOP поверх плашек), пока грузятся данные.
 *
 * Показывается на первой загрузке вместо пустого списка/спиннера; следует за
 * палитрой/Material You (базовый тон и подсветка берутся из темы контекста).
 * Аниматор живёт только пока view прикреплена — не течёт.
 */
class SkeletonListView @JvmOverloads constructor(
        context: Context,
        attrs: AttributeSet? = null,
) : View(context, attrs) {

    enum class Style { LIST, CARD }

    var style: Style = Style.LIST

    private val density = resources.displayMetrics.density
    private fun dp(v: Float) = v * density

    private val basePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColorFromAttr(com.google.android.material.R.attr.colorSurfaceVariant)
    }
    private val shimmerPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val highlightColor = 0x66FFFFFF // полупрозрачный «блик» — читается и на светлой, и на тёмной плашке
    private val radius = dp(8f)
    private val rect = RectF()

    private var shimmerProgress = 0f
    private var animator: ValueAnimator? = null

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (animator == null) {
            animator = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 1150L
                repeatCount = ValueAnimator.INFINITE
                interpolator = LinearInterpolator()
                addUpdateListener {
                    shimmerProgress = it.animatedValue as Float
                    invalidate()
                }
                start()
            }
        }
    }

    override fun onDetachedFromWindow() {
        animator?.cancel()
        animator = null
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val layer = canvas.saveLayer(0f, 0f, w, h, null)

        when (style) {
            Style.LIST -> drawListRows(canvas, w)
            Style.CARD -> drawCards(canvas, w)
        }

        // Shimmer-полоса поверх плашек.
        val band = w * 0.55f
        val center = shimmerProgress * (w + band) - band / 2f
        shimmerPaint.shader = LinearGradient(
                center - band, 0f, center + band, 0f,
                intArrayOf(0x00FFFFFF, highlightColor, 0x00FFFFFF),
                floatArrayOf(0f, 0.5f, 1f),
                Shader.TileMode.CLAMP
        )
        shimmerPaint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP)
        canvas.drawRect(0f, 0f, w, h, shimmerPaint)
        shimmerPaint.xfermode = null

        canvas.restoreToCount(layer)
    }

    private fun bar(canvas: Canvas, left: Float, top: Float, width: Float, height: Float) {
        rect.set(left, top, left + width, top + height)
        canvas.drawRoundRect(rect, radius, radius, basePaint)
    }

    private fun drawListRows(canvas: Canvas, w: Float) {
        val padH = dp(16f)
        val rowHeight = dp(76f)
        val titleH = dp(18f)
        val subH = dp(14f)
        var y = dp(12f)
        while (y < height) {
            // заголовок
            bar(canvas, padH, y, w * 0.55f, titleH)
            // подзаголовок
            bar(canvas, padH, y + titleH + dp(12f), w * 0.32f, subH)
            // «дата» справа
            bar(canvas, w - padH - w * 0.2f, y + titleH + dp(12f), w * 0.2f, subH)
            y += rowHeight
        }
    }

    private fun drawCards(canvas: Canvas, w: Float) {
        val padH = dp(12f)
        val imageH = dp(180f)
        val titleH = dp(20f)
        val cardGap = dp(20f)
        var y = dp(12f)
        while (y < height) {
            // изображение
            bar(canvas, padH, y, w - padH * 2, imageH)
            // две строки заголовка
            bar(canvas, padH, y + imageH + dp(14f), w * 0.8f, titleH)
            bar(canvas, padH, y + imageH + dp(14f) + titleH + dp(10f), w * 0.5f, titleH)
            y += imageH + titleH * 2 + cardGap * 2
        }
    }
}
