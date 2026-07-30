package forpdateam.ru.forpda.ui.fragments.theme.nativerender

import android.animation.ValueAnimator
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import forpdateam.ru.forpda.common.getColorFromAttr
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Перетаскиваемый ползунок прокрутки для нативной темы (вариант «B» из макетов: толстая пилюля +
 * широкая зона захвата + плашка с позицией).
 *
 * Зачем: штатный `android:scrollbars="vertical"` рисует полоску в 2–4dp и НЕ перетаскивается вовсе —
 * это только индикатор. Юзер: «её очень трудно зацепить пальцем, чтобы быстро скроллить». Штатный
 * `RecyclerView.setFastScrollEnabled` тоже не годится: он ловит касание строго по ширине бегунка,
 * то есть ровно ту же проблему и оставляет.
 *
 * Устройство — [RecyclerView.ItemDecoration] + [RecyclerView.OnItemTouchListener], без единой новой
 * View: разметка темы ([fragment_base_list][forpdateam.ru.forpda.R.layout.fragment_base_list]) общая
 * с десятком других экранов, и оборачивать её ради одного экрана нельзя.
 *
 * Ключевые решения:
 * - Зона захвата — [HIT_WIDTH_DP] по правому краю (сама пилюля рисуется в [THUMB_WIDTH_DP]), плюс
 *   вертикальный допуск [GRAB_SLOP_DP]. За пределами допуска касание НЕ перехватывается и уходит
 *   в список: тап у правого края не должен телепортировать тему.
 * - Прокрутка — ПРИРАЩЕНИЯМИ ([RecyclerView.scrollBy] на дельту пальца), как в платформенном
 *   `FastScroller`. Абсолютный прыжок по доле был бы неверен: при бесконечной подгрузке диапазон
 *   растёт прямо под пальцем, а посты сильно разной высоты (см. [NativePostItem]).
 * - Дорожка отражает ЗАГРУЖЕННЫЙ кусок темы, а не все её страницы: подгрузка соседних страниц идёт
 *   тем же путём, что и при обычном скролле (наш [RecyclerView.scrollBy] шлёт `onScrolled`).
 * - Полоса захвата вычитается из системного жеста «назад» ([View.setSystemGestureExclusionRects],
 *   API 29+) — иначе на краю экрана перетаскивание конкурировало бы с уходом назад. Исключаем не всю
 *   высоту, а окрестность бегунка: система суммарно отдаёт не более 200dp на край.
 */
class TopicFastScroller private constructor(
        private val rv: RecyclerView,
        /** Левый край вместо правого — настройка «под руку» ([Preferences.Main.TopicFastScroll]). */
        private val onLeft: Boolean,
        /** Адаптерная позиция верхнего видимого элемента → текст плашки («Стр. 42 · 14:30»), либо null. */
        private val labelProvider: (Int) -> String?,
        /** Палец лёг на бегунок — фрагмент помечает это как пользовательский жест (как `DRAGGING`). */
        private val onDragStart: () -> Unit,
        /** Палец отпущен — фрагмент доигрывает то же, что и на `SCROLL_STATE_IDLE`. */
        private val onDragEnd: () -> Unit,
) : RecyclerView.ItemDecoration(), RecyclerView.OnItemTouchListener {

    private val density = rv.resources.displayMetrics.density
    private fun dp(v: Float) = v * density

    private val trackWidth = dp(TRACK_WIDTH_DP)
    private val thumbWidth = dp(THUMB_WIDTH_DP)
    private val thumbWidthDragging = dp(THUMB_WIDTH_DRAGGING_DP)
    private val minThumbHeight = dp(MIN_THUMB_HEIGHT_DP)
    private val edgeMargin = dp(EDGE_MARGIN_DP)
    private val verticalMargin = dp(VERTICAL_MARGIN_DP)
    private val hitWidth = dp(HIT_WIDTH_DP)
    private val grabSlop = dp(GRAB_SLOP_DP)

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val thumbPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bubblePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bubbleTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = dp(BUBBLE_TEXT_SP)
        typeface = android.graphics.Typeface.DEFAULT_BOLD
    }

    private var visibility = 0f
    private var visibilityAnimator: ValueAnimator? = null
    private var dragging = false
    private var lastDragY = 0f
    /** Нажатие пришлось на зону бегунка — ждём, станет ли оно вертикальной протяжкой. */
    private var grabCandidate = false
    private var downX = 0f
    private var downY = 0f
    private val touchSlop = android.view.ViewConfiguration.get(rv.context).scaledTouchSlop
    /** Дробный остаток дельты: при медленном перетаскивании округление до пикселя иначе съедало ход. */
    private var scrollRemainder = 0f

    private var thumbTop = 0f
    private var thumbHeight = 0f
    private val rect = RectF()
    private val exclusion = Rect()
    private var exclusionApplied: Rect? = null

    private val hideRunnable = Runnable { if (!dragging) animateVisibility(false) }

    private val scrollListener = object : RecyclerView.OnScrollListener() {
        override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
            if (dy != 0) show()
        }
    }

    init {
        applyColors()
    }

    private fun applyColors() {
        val ctx = rv.context
        val accent = ctx.getColorFromAttr(forpdateam.ru.forpda.R.attr.smart_nav_fab_background)
        thumbPaint.color = accent
        bubblePaint.color = accent
        bubbleTextPaint.color = ctx.getColorFromAttr(forpdateam.ru.forpda.R.attr.smart_nav_fab_icon)
        trackPaint.color = ctx.getColorFromAttr(com.google.android.material.R.attr.colorOutlineVariant)
    }

    /** Показать ползунок и перевзвести автоскрытие (вызывается на каждом кадре прокрутки). */
    fun show() {
        if (visibility < 1f) animateVisibility(true)
        rv.removeCallbacks(hideRunnable)
        if (!dragging) rv.postDelayed(hideRunnable, AUTO_HIDE_MS)
    }

    private fun animateVisibility(visible: Boolean) {
        val target = if (visible) 1f else 0f
        if (visibility == target && visibilityAnimator == null) return
        visibilityAnimator?.cancel()
        visibilityAnimator = ValueAnimator.ofFloat(visibility, target).apply {
            duration = if (visible) FADE_IN_MS else FADE_OUT_MS
            addUpdateListener {
                visibility = it.animatedValue as Float
                rv.invalidate()
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    visibilityAnimator = null
                    if (!visible) clearGestureExclusion()
                }
            })
            start()
        }
    }

    // region drawing

    override fun onDrawOver(c: Canvas, parent: RecyclerView, state: RecyclerView.State) {
        val range = parent.computeVerticalScrollRange()
        val extent = parent.computeVerticalScrollExtent()
        val trackTop = verticalMargin
        val trackHeight = parent.height - verticalMargin * 2
        if (!Geometry.isScrollerWorthShowing(range, extent) || trackHeight <= minThumbHeight) {
            thumbHeight = 0f
            if (visibility != 0f || dragging) {
                dragging = false
                visibilityAnimator?.cancel()
                visibilityAnimator = null
                visibility = 0f
                clearGestureExclusion()
            }
            return
        }
        // Геометрию считаем ВСЕГДА, даже пока ползунок погашен: за неё цепляется [canGrab], а тянуть
        // юзер может и по невидимому бегунку — иначе снова «не успел зацепить».
        thumbHeight = Geometry.thumbHeight(trackHeight, extent, range, minThumbHeight)
        thumbTop = trackTop + Geometry.thumbOffset(
                trackHeight - thumbHeight, parent.computeVerticalScrollOffset(), range, extent)
        if (visibility <= 0f) return

        val alpha = (255 * visibility).roundToInt().coerceIn(0, 255)
        val width = if (dragging) thumbWidthDragging else thumbWidth
        // Внешний край полосы: у своей стороны экрана (настройка «под руку»).
        val outer = if (onLeft) edgeMargin else parent.width - edgeMargin

        trackPaint.alpha = (alpha * TRACK_ALPHA).roundToInt().coerceIn(0, 255)
        setBarRect(outer, trackWidth, trackTop, trackHeight)
        c.drawRoundRect(rect, trackWidth / 2f, trackWidth / 2f, trackPaint)

        thumbPaint.alpha = alpha
        setBarRect(outer, width, thumbTop, thumbHeight)
        c.drawRoundRect(rect, width / 2f, width / 2f, thumbPaint)

        if (dragging) drawBubble(c, parent, if (onLeft) outer + width else outer - width)
        updateGestureExclusion(parent)
    }

    /** Прямоугольник полосы шириной [barWidth], прижатый к [outer] — внешнему краю своей стороны. */
    private fun setBarRect(outer: Float, barWidth: Float, top: Float, height: Float) {
        if (onLeft) rect.set(outer, top, outer + barWidth, top + height)
        else rect.set(outer - barWidth, top, outer, top + height)
    }

    private fun drawBubble(c: Canvas, parent: RecyclerView, thumbInnerEdge: Float) {
        val label = currentLabel(parent) ?: return
        val padH = dp(BUBBLE_PAD_H_DP)
        val height = dp(BUBBLE_HEIGHT_DP)
        val bubbleWidth = bubbleTextPaint.measureText(label) + padH * 2
        val gap = dp(BUBBLE_GAP_DP)
        // Плашка всегда УХОДИТ ОТ края внутрь экрана — иначе на левой стороне она уезжала бы за него.
        val left = if (onLeft) thumbInnerEdge + gap else thumbInnerEdge - gap - bubbleWidth
        val center = (thumbTop + thumbHeight / 2f)
                .coerceIn(verticalMargin + height / 2f, parent.height - verticalMargin - height / 2f)
        rect.set(left, center - height / 2f, left + bubbleWidth, center + height / 2f)
        c.drawRoundRect(rect, height / 2f, height / 2f, bubblePaint)
        val fm = bubbleTextPaint.fontMetrics
        c.drawText(label, left + padH, center - (fm.ascent + fm.descent) / 2f, bubbleTextPaint)
    }

    private fun currentLabel(parent: RecyclerView): String? {
        val lm = parent.layoutManager as? LinearLayoutManager ?: return null
        val pos = lm.findFirstVisibleItemPosition()
        if (pos == RecyclerView.NO_POSITION) return null
        return labelProvider(pos)?.takeIf { it.isNotBlank() }
    }

    // endregion

    // region touch

    /**
     * Захватываем жест НЕ на нажатии, а на первом заметном ВЕРТИКАЛЬНОМ движении из зоны бегунка.
     *
     * На нажатии нельзя: полоса захвата шире кнопки «⋮» в шапке поста (она сама сидит в ~35dp от
     * правого края), и перехваченный ACTION_DOWN съедал бы тап по меню поста, когда бегунок случайно
     * оказался на той же высоте. Тап теперь уходит в список как раньше, а полосу забирает только
     * протяжка. Горизонтальные жесты не трогаем вовсе — их ждёт свайп смены страницы.
     */
    override fun onInterceptTouchEvent(rv: RecyclerView, e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                grabCandidate = canGrab(e.x, e.y)
                downX = e.x
                downY = e.y
            }
            MotionEvent.ACTION_MOVE -> {
                if (dragging) return true
                if (!grabCandidate) return false
                val dy = e.y - downY
                if (abs(dy) < touchSlop || abs(dy) <= abs(e.x - downX)) return false
                startDrag(e.y)
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> grabCandidate = false
        }
        return false
    }

    override fun onTouchEvent(rv: RecyclerView, e: MotionEvent) {
        if (!dragging) return
        when (e.actionMasked) {
            MotionEvent.ACTION_MOVE -> dragBy(e.y)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> endDrag()
        }
    }

    override fun onRequestDisallowInterceptTouchEvent(disallowIntercept: Boolean) = Unit

    /**
     * Палец лёг в правую полосу на окрестность бегунка. Видимость НЕ требуется: ползунок гаснет через
     * секунду после прокрутки, и требование «сначала разбуди» возвращало бы ту же беду, что и с
     * системной полоской — не успеть зацепить. Геометрия бегунка от прозрачности не зависит.
     */
    private fun canGrab(x: Float, y: Float): Boolean {
        if (thumbHeight <= 0f) return false
        val inStrip = if (onLeft) x <= hitWidth else x >= rv.width - hitWidth
        if (!inStrip) return false
        return y >= thumbTop - grabSlop && y <= thumbTop + thumbHeight + grabSlop
    }

    private fun startDrag(y: Float) {
        dragging = true
        lastDragY = y
        scrollRemainder = 0f
        rv.stopScroll()
        rv.parent?.requestDisallowInterceptTouchEvent(true)
        rv.removeCallbacks(hideRunnable)
        rv.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        show()
        onDragStart()
        rv.invalidate()
    }

    private fun dragBy(y: Float) {
        val range = rv.computeVerticalScrollRange()
        val extent = rv.computeVerticalScrollExtent()
        val trackHeight = rv.height - verticalMargin * 2
        val delta = Geometry.scrollDelta(
                y - lastDragY, trackHeight - thumbHeight, range, extent) + scrollRemainder
        lastDragY = y
        val whole = delta.toInt()
        scrollRemainder = delta - whole
        if (whole != 0) rv.scrollBy(0, whole)
        rv.invalidate()
    }

    private fun endDrag() {
        if (!dragging) return
        dragging = false
        grabCandidate = false
        rv.parent?.requestDisallowInterceptTouchEvent(false)
        show()
        onDragEnd()
        rv.invalidate()
    }

    // endregion

    /**
     * Вычитаем окрестность бегунка из системного жеста «назад»: без этого протяжка от правого края
     * уводила бы из темы. Исключаем ровно область захвата (система отдаёт не более 200dp на край),
     * и только пока ползунок виден.
     */
    private fun updateGestureExclusion(parent: RecyclerView) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        exclusion.set(
                if (onLeft) 0 else (parent.width - hitWidth).roundToInt(),
                (thumbTop - grabSlop).roundToInt().coerceAtLeast(0),
                if (onLeft) hitWidth.roundToInt() else parent.width,
                (thumbTop + thumbHeight + grabSlop).roundToInt().coerceAtMost(parent.height))
        if (exclusionApplied == exclusion) return
        exclusionApplied = Rect(exclusion)
        parent.systemGestureExclusionRects = listOf(Rect(exclusion))
    }

    private fun clearGestureExclusion() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        if (exclusionApplied == null) return
        exclusionApplied = null
        rv.systemGestureExclusionRects = emptyList()
    }

    /** Снять ползунок со списка: смена стороны/выключение в настройках, либо уход вью с экрана. */
    fun detach() {
        rv.removeCallbacks(hideRunnable)
        visibilityAnimator?.cancel()
        dragging = false
        clearGestureExclusion()
        rv.removeItemDecoration(this)
        rv.removeOnItemTouchListener(this)
        rv.removeOnScrollListener(scrollListener)
        rv.isVerticalScrollBarEnabled = true // вернуть системную полоску
        rv.invalidate()
    }

    /** Чистая геометрия ползунка — без Android-зависимостей, покрыта [TopicFastScrollerGeometryTest]. */
    internal object Geometry {

        /** Короткая тема (меньше [MIN_RANGE_RATIO] экранов) ползунка не заслуживает — только шум. */
        fun isScrollerWorthShowing(range: Int, extent: Int): Boolean =
                extent > 0 && range > 0 && range >= extent * MIN_RANGE_RATIO

        fun thumbHeight(trackHeight: Float, extent: Int, range: Int, minHeight: Float): Float {
            if (range <= 0) return minHeight
            val proportional = trackHeight * extent / range
            return max(minHeight, proportional).coerceAtMost(trackHeight)
        }

        /** Смещение верха бегунка внутри дорожки (0..[travel]) по текущей прокрутке. */
        fun thumbOffset(travel: Float, offset: Int, range: Int, extent: Int): Float {
            val scrollable = (range - extent).toFloat()
            if (scrollable <= 0f || travel <= 0f) return 0f
            return (travel * (offset / scrollable)).coerceIn(0f, travel)
        }

        /**
         * Сколько пикселей контента приходится на [fingerDelta] пикселей пути пальца по дорожке.
         * Приращениями, а не абсолютной долей: при бесконечной подгрузке [range] растёт под пальцем.
         */
        fun scrollDelta(fingerDelta: Float, travel: Float, range: Int, extent: Int): Float {
            val scrollable = (range - extent).toFloat()
            if (travel <= 0f || scrollable <= 0f) return 0f
            return fingerDelta / travel * scrollable
        }
    }

    companion object {
        private const val TRACK_WIDTH_DP = 4f
        private const val THUMB_WIDTH_DP = 9f
        private const val THUMB_WIDTH_DRAGGING_DP = 12f
        private const val MIN_THUMB_HEIGHT_DP = 52f
        private const val EDGE_MARGIN_DP = 4f
        private const val VERTICAL_MARGIN_DP = 8f
        /** Ширина невидимой зоны захвата — рекомендованный минимум касания в M3. */
        private const val HIT_WIDTH_DP = 44f
        private const val GRAB_SLOP_DP = 16f
        private const val BUBBLE_TEXT_SP = 13f
        private const val BUBBLE_HEIGHT_DP = 30f
        private const val BUBBLE_PAD_H_DP = 12f
        private const val BUBBLE_GAP_DP = 8f
        private const val TRACK_ALPHA = 0.5f
        private const val AUTO_HIDE_MS = 1300L
        private const val FADE_IN_MS = 130L
        private const val FADE_OUT_MS = 250L
        /** Ниже этого отношения «весь контент / экран» ползунок не показываем. */
        internal const val MIN_RANGE_RATIO = 2.5f

        /**
         * Вешает ползунок на [rv] (декорация + перехватчик касаний + слушатель прокрутки) и отключает
         * штатную полоску, чтобы они не рисовались друг поверх друга.
         */
        fun attach(
                rv: RecyclerView,
                onLeft: Boolean,
                labelProvider: (Int) -> String?,
                onDragStart: () -> Unit,
                onDragEnd: () -> Unit,
        ): TopicFastScroller {
            val scroller = TopicFastScroller(rv, onLeft, labelProvider, onDragStart, onDragEnd)
            rv.isVerticalScrollBarEnabled = false
            rv.addItemDecoration(scroller)
            rv.addOnItemTouchListener(scroller)
            rv.addOnScrollListener(scroller.scrollListener)
            rv.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(v: View) = Unit
                override fun onViewDetachedFromWindow(v: View) {
                    scroller.detach()
                    rv.removeOnAttachStateChangeListener(this)
                }
            })
            return scroller
        }
    }
}
