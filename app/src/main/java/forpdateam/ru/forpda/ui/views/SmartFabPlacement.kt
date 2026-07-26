package forpdateam.ru.forpda.ui.views

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.content.Context
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.view.ViewCompat
import com.google.android.material.floatingactionbutton.FloatingActionButton
import forpdateam.ru.forpda.R
import forpdateam.ru.forpda.common.getColorFromAttr
import forpdateam.ru.forpda.ui.Haptic
import forpdateam.ru.forpda.ui.dp8
import forpdateam.ru.forpda.ui.dp12
import forpdateam.ru.forpda.ui.dp16
import kotlin.math.roundToInt

/**
 * Свободное размещение «умной кнопки» темы (FAB) по экрану.
 *
 * Вход в перенос — ТОЛЬКО через пункт «Переместить кнопку» в smart-nav меню ([startMoveMode]): жестом
 * кнопку не сдвинуть, поэтому короткий тап (перелистнуть страницу) и удержание (меню) остаются ровно
 * такими, как были, и случайно утащить кнопку невозможно.
 *
 * Геометрия: во время перетаскивания двигаем [View.setTranslationX]/[View.setTranslationY] (без relayout),
 * на отпускании примагничиваемся к ближнему боковому краю и коммитим результат в margins
 * [CoordinatorLayout.LayoutParams], обнуляя translation. Позиция наружу отдаётся ДОЛЯМИ допустимой
 * области (0f — левый/верхний край, 1f — правый/нижний), а не пикселями: так она переживает поворот,
 * разделённый экран, смену insets и открытие панели ответа (координатор при этом ужимается).
 *
 * Границы считаются на лету: сверху — низ AppBar, снизу — резерв вызывающего (панель пагинации
 * в CLASSIC), по бокам — 16dp. Кнопка физически не может оказаться под тулбаром или за краем.
 */
class SmartFabPlacement(
        private val fab: FloatingActionButton,
        private val parent: ViewGroup,
        /** Низ AppBar (px) относительно [parent] — выше кнопку не пускаем. */
        private val topReservePx: () -> Int,
        /** Нижний резерв (px): панель пагинации в CLASSIC и т.п. */
        private val bottomReservePx: () -> Int,
        /** Отступ снизу для позиции ПО УМОЛЧАНИЮ (px), когда пользователь ничего не переносил. */
        private val defaultBottomOffsetPx: () -> Int,
        private val onPositionCommitted: (xFraction: Float, yFraction: Float) -> Unit,
        private val onPositionReset: () -> Unit,
        private val onMoveModeChanged: (active: Boolean) -> Unit,
) {

    private val context: Context get() = fab.context

    /** `null` — позиция по умолчанию (нижний правый угол). */
    private var xFraction: Float? = null
    private var yFraction: Float? = null

    private var moveOverlay: FrameLayout? = null
    private var hintView: View? = null
    private var dragging = false
    private var downRawX = 0f
    private var downRawY = 0f
    private var baseLeft = 0
    private var baseTop = 0
    private val dragRect = Rect()
    private var disposed = false
    private var savedCompatElevation: Float? = null

    /** Размеры координатора, под которые позиция уже разложена; `0` — ещё ни разу. */
    private var appliedWidth = 0
    private var appliedHeight = 0

    /**
     * Перекладка после смены размеров координатора (поворот, IME, панель ответа, insets) — и первая
     * раскладка вообще: [setStoredPosition] зовётся до layout, когда размеров ещё нет.
     */
    private val layoutListener = View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
        if (appliedWidth != parent.width || appliedHeight != parent.height) apply()
    }

    init {
        parent.addOnLayoutChangeListener(layoutListener)
    }

    val isMoveModeActive: Boolean get() = moveOverlay != null

    /** Сохранённая позиция из настроек; `null` — по умолчанию. */
    fun setStoredPosition(position: Pair<Float, Float>?) {
        xFraction = position?.first?.coerceIn(0f, 1f)
        yFraction = position?.second?.coerceIn(0f, 1f)
        apply()
    }

    /**
     * Ставит кнопку в текущую позицию. Безопасно вызывать до layout — тогда выйдет вхолостую,
     * а [layoutListener] повторит, когда координатор получит размеры.
     */
    fun apply() {
        if (disposed || dragging) return
        val rect = allowedRect() ?: return
        val lp = fab.layoutParams as? CoordinatorLayout.LayoutParams ?: return
        val x = xFraction
        val y = yFraction
        val left: Int
        val top: Int
        if (x == null || y == null) {
            left = rect.right
            top = (parent.height - defaultBottomOffsetPx() - fab.height).coerceIn(rect.top, rect.bottom)
        } else {
            left = rect.left + (rect.width() * x).roundToInt()
            top = rect.top + (rect.height() * y).roundToInt()
        }
        applyAbsolute(lp, left, top)
        appliedWidth = parent.width
        appliedHeight = parent.height
    }

    /** Вход в режим переноса: затемняем фон, подсказка сверху, кнопка едет за пальцем. */
    fun startMoveMode() {
        if (disposed || moveOverlay != null || !parent.isAttachedToWindow) return
        val overlay = buildOverlay()
        moveOverlay = overlay
        parent.addView(overlay, ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        // Оверлей ОСТАЁТСЯ на нулевой elevation: и рисование, и диспатч касаний в ViewGroup идут по Z,
        // а поднимать затемнение выше кнопки нельзя — она погаснет вместе с контентом и потеряет
        // касания. Добавлен последним среди Z=0, поэтому лежит над списком, но под кнопкой.
        // Кнопку дополнительно поднимаем через compatElevation (View.setElevation Material FAB
        // затирает своим StateListAnimator).
        savedCompatElevation = fab.compatElevation
        fab.compatElevation = MOVE_MODE_ELEVATION_DP * context.resources.displayMetrics.density
        fab.setOnTouchListener(moveTouchListener)
        fab.animate().cancel()
        fab.animate().scaleX(MOVE_MODE_SCALE).scaleY(MOVE_MODE_SCALE).setDuration(160).start()
        Haptic.longPress(fab)
        onMoveModeChanged(true)
        // Кнопка могла стоять ровно там, где встаёт подсказка — тогда притухаем её сразу, не дожидаясь
        // первого перетаскивания. Через post: до layout у подсказки нет размеров.
        overlay.post { fadeHint(dragging = false) }
    }

    fun stopMoveMode() {
        val overlay = moveOverlay ?: return
        moveOverlay = null
        hintView = null
        dragging = false
        fab.setOnTouchListener(null)
        savedCompatElevation?.let { fab.compatElevation = it }
        savedCompatElevation = null
        fab.animate().cancel()
        fab.animate().scaleX(1f).scaleY(1f).setDuration(160).start()
        overlay.animate().cancel()
        overlay.animate()
                .alpha(0f)
                .setDuration(120)
                .setListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        (overlay.parent as? ViewGroup)?.removeView(overlay)
                    }
                })
                .start()
        onMoveModeChanged(false)
    }

    fun dispose() {
        disposed = true
        parent.removeOnLayoutChangeListener(layoutListener)
        moveOverlay?.let { (it.parent as? ViewGroup)?.removeView(it) }
        moveOverlay = null
        hintView = null
        fab.setOnTouchListener(null)
        savedCompatElevation?.let { fab.compatElevation = it }
        savedCompatElevation = null
        fab.animate().cancel()
        fab.scaleX = 1f
        fab.scaleY = 1f
        fab.translationX = 0f
        fab.translationY = 0f
    }

    // ------------------------------------------------------------------------------------------
    // Геометрия
    // ------------------------------------------------------------------------------------------

    /**
     * Допустимая область как диапазон ПОЗИЦИЙ левого-верхнего угла кнопки: [Rect.left]/[Rect.top] —
     * минимум, [Rect.right]/[Rect.bottom] — максимум (а не края прямоугольника кнопки).
     */
    private fun allowedRect(): Rect? {
        val parentW = parent.width
        val parentH = parent.height
        val fabW = fab.width
        val fabH = fab.height
        if (parentW <= 0 || parentH <= 0 || fabW <= 0 || fabH <= 0) return null
        val side = context.dp16
        val left = side
        val right = parentW - side - fabW
        val top = topReservePx().coerceAtLeast(0) + context.dp8
        val bottom = parentH - bottomReservePx().coerceAtLeast(0) - context.dp8 - fabH
        if (right < left || bottom < top) return null
        return Rect(left, top, right, bottom)
    }

    /** Ставит margins только если они реально изменились — иначе [setLayoutParams] зациклит layout. */
    private fun applyAbsolute(lp: CoordinatorLayout.LayoutParams, left: Int, top: Int) {
        val gravity = Gravity.TOP or Gravity.LEFT
        val unchanged = lp.gravity == gravity && lp.leftMargin == left && lp.topMargin == top &&
                lp.rightMargin == 0 && lp.bottomMargin == 0
        if (unchanged) return
        lp.gravity = gravity
        lp.leftMargin = left
        lp.topMargin = top
        lp.rightMargin = 0
        lp.bottomMargin = 0
        fab.layoutParams = lp
    }

    // ------------------------------------------------------------------------------------------
    // Перетаскивание
    // ------------------------------------------------------------------------------------------

    /**
     * Один и тот же слушатель висит и на кнопке, и на затемняющем оверлее: кому именно достанется
     * касание, решает Z-порядок внутри координатора, а полагаться на него не хочется. Поэтому цель
     * определяем сами — по попаданию в прямоугольник кнопки; промах мимо неё = «Готово» (но только
     * если это тап, а не протяжка, иначе режим закрывался бы на каждом неудачном движении).
     */
    private val moveTouchListener = View.OnTouchListener { _, event ->
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downRawX = event.rawX
                downRawY = event.rawY
                val rect = allowedRect()
                val lp = fab.layoutParams as? CoordinatorLayout.LayoutParams
                if (rect != null && lp != null && isInsideFab(event.rawX, event.rawY)) {
                    dragRect.set(rect)
                    // Берём РЕАЛЬНОЕ положение кнопки в координаторе, а не margins: до первого
                    // [apply] она ещё живёт на XML-параметрах (gravity BOTTOM|END + одинаковые
                    // отступы), и margins там — не координаты. Заодно нормализуем LP в абсолютные,
                    // визуально это тот же самый пиксель.
                    baseLeft = fab.left
                    baseTop = fab.top
                    applyAbsolute(lp, baseLeft, baseTop)
                    dragging = true
                    fab.animate().cancel()
                    parent.requestDisallowInterceptTouchEvent(true)
                    Haptic.tick(fab)
                    fadeHint(dragging = true)
                }
                true
            }
            MotionEvent.ACTION_MOVE -> {
                if (!dragging) return@OnTouchListener true
                val left = (baseLeft + (event.rawX - downRawX)).roundToInt()
                        .coerceIn(dragRect.left, dragRect.right)
                val top = (baseTop + (event.rawY - downRawY)).roundToInt()
                        .coerceIn(dragRect.top, dragRect.bottom)
                fab.translationX = (left - baseLeft).toFloat()
                fab.translationY = (top - baseTop).toFloat()
                true
            }
            MotionEvent.ACTION_UP -> {
                if (dragging) {
                    dragging = false
                    settleToNearestEdge() // подсказку вернём в commit(), когда позиция уже финальная
                } else if (isTap(event)) {
                    stopMoveMode()
                }
                true
            }
            MotionEvent.ACTION_CANCEL -> {
                if (dragging) {
                    dragging = false
                    settleToNearestEdge() // подсказку вернём в commit(), когда позиция уже финальная
                }
                true
            }
            else -> false
        }
    }

    /**
     * Плашка-подсказка занимает верх экрана — то есть кусок области, куда кнопку можно перетащить.
     * На время перетаскивания гасим её полностью, а после отпускания возвращаем только если кнопка
     * встала не под ней: иначе кнопка оказалась бы «под уведомлением» и вытащить её обратно было бы
     * нечем. Плюс кнопка всегда рисуется поверх плашки и первой получает касание (см. compatElevation).
     */
    private fun fadeHint(dragging: Boolean, fabLeft: Int = fab.left, fabTop: Int = fab.top) {
        val hint = hintView ?: return
        // Оверлей растянут на весь координатор, так что hint.left/top уже в его системе координат —
        // сравниваем напрямую с целевой позицией кнопки (её margins применятся только следующим layout).
        val overlaps = Rect.intersects(
                Rect(hint.left, hint.top, hint.right, hint.bottom),
                Rect(fabLeft, fabTop, fabLeft + fab.width, fabTop + fab.height))
        val target = when {
            dragging -> 0f
            overlaps -> HINT_DIMMED_ALPHA
            else -> 1f
        }
        hint.animate().cancel()
        hint.animate().alpha(target).setDuration(150).start()
    }

    /** Попадание в кнопку с запасом: во время переноса она увеличена, да и палец не пиксель. */
    private fun isInsideFab(rawX: Float, rawY: Float): Boolean {
        val loc = IntArray(2)
        fab.getLocationOnScreen(loc)
        val slop = context.dp8
        return rawX >= loc[0] - slop && rawX <= loc[0] + fab.width + slop &&
                rawY >= loc[1] - slop && rawY <= loc[1] + fab.height + slop
    }

    private fun isTap(event: MotionEvent): Boolean {
        val slop = android.view.ViewConfiguration.get(context).scaledTouchSlop
        return kotlin.math.abs(event.rawX - downRawX) <= slop &&
                kotlin.math.abs(event.rawY - downRawY) <= slop
    }

    /** Отпустили: по X примагничиваемся к ближнему краю, по Y остаёмся где бросили. */
    private fun settleToNearestEdge() {
        val rect = dragRect
        val left = baseLeft + fab.translationX.roundToInt()
        val top = (baseTop + fab.translationY.roundToInt()).coerceIn(rect.top, rect.bottom)
        val centerX = left + fab.width / 2f
        val snappedLeft = if (centerX < parent.width / 2f) rect.left else rect.right
        animateTo(snappedLeft, top, rect, reset = false)
    }

    /** «Сбросить» в подсказке: возврат в позицию по умолчанию с той же пружинкой. */
    private fun resetToDefault() {
        val rect = allowedRect() ?: return
        val lp = fab.layoutParams as? CoordinatorLayout.LayoutParams ?: return
        baseLeft = fab.left
        baseTop = fab.top
        applyAbsolute(lp, baseLeft, baseTop)
        val left = rect.right
        val top = (parent.height - defaultBottomOffsetPx() - fab.height).coerceIn(rect.top, rect.bottom)
        animateTo(left, top, rect, reset = true)
    }

    private fun animateTo(left: Int, top: Int, rect: Rect, reset: Boolean) {
        fab.animate().cancel()
        fab.animate()
                .translationX((left - baseLeft).toFloat())
                .translationY((top - baseTop).toFloat())
                .setDuration(220)
                .setInterpolator(OvershootInterpolator(0.9f))
                .setListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        if (disposed) return
                        commit(left, top, rect, reset)
                    }
                })
                .start()
    }

    private fun commit(left: Int, top: Int, rect: Rect, reset: Boolean) {
        val lp = fab.layoutParams as? CoordinatorLayout.LayoutParams ?: return
        fab.translationX = 0f
        fab.translationY = 0f
        applyAbsolute(lp, left, top)
        fadeHint(dragging = false, fabLeft = left, fabTop = top)
        if (reset) {
            xFraction = null
            yFraction = null
            onPositionReset()
        } else {
            val x = fraction(left - rect.left, rect.width())
            val y = fraction(top - rect.top, rect.height())
            xFraction = x
            yFraction = y
            onPositionCommitted(x, y)
        }
    }

    private fun fraction(offset: Int, span: Int): Float =
            if (span <= 0) 0f else (offset.toFloat() / span).coerceIn(0f, 1f)

    // ------------------------------------------------------------------------------------------
    // Оверлей режима переноса
    // ------------------------------------------------------------------------------------------

    private fun buildOverlay(): FrameLayout = FrameLayout(context).apply {
        setBackgroundColor(SCRIM_COLOR)
        isClickable = true
        isFocusable = true
        // Тап мимо кнопки = «Готово» (позиция уже закоммичена на отпускании, терять нечего) — разбирается
        // в [moveTouchListener], а не в OnClickListener: у полноэкранной вьюхи «клик» срабатывает и на
        // протяжке, пока палец не вышел за её границы, и режим закрывался бы прямо во время переноса.
        setOnTouchListener(moveTouchListener)
        alpha = 0f
        animate().alpha(1f).setDuration(150).start()
        addView(buildHint().also { hintView = it }, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            topMargin = topReservePx().coerceAtLeast(0) + context.dp16
            marginStart = context.dp16
            marginEnd = context.dp16
        })
    }

    private fun buildHint(): View {
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(context.getColorFromAttr(com.google.android.material.R.attr.colorSurfaceVariant))
                cornerRadius = context.dp16.toFloat()
            }
            ViewCompat.setElevation(this, context.dp8.toFloat())
            setPadding(context.dp16, context.dp12, context.dp16, context.dp8)
        }
        root.addView(TextView(context).apply {
            text = context.getString(R.string.smart_fab_move_hint)
            setTextColor(context.getColorFromAttr(com.google.android.material.R.attr.colorOnSurface))
            textSize = 13f
        })
        root.addView(LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            addView(hintButton(R.string.smart_fab_move_reset) { resetToDefault() })
            addView(hintButton(R.string.smart_fab_move_done) { stopMoveMode() })
        }, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = context.dp8
        })
        return root
    }

    private fun hintButton(textRes: Int, onClick: () -> Unit): TextView = TextView(context).apply {
        setText(textRes)
        setTextColor(context.getColorFromAttr(R.attr.colorAccent))
        textSize = 13f
        isAllCaps = true
        setPadding(context.dp12, context.dp8, context.dp12, context.dp8)
        setBackgroundResource(
                context.resolveAttrResId(android.R.attr.selectableItemBackgroundBorderless))
        setOnClickListener {
            Haptic.tick(it)
            onClick()
        }
    }

    private fun Context.resolveAttrResId(attr: Int): Int {
        val typedValue = android.util.TypedValue()
        theme.resolveAttribute(attr, typedValue, true)
        return typedValue.resourceId
    }

    private companion object {
        /** Кнопка на время переноса поднимается над затемнением. */
        private const val MOVE_MODE_ELEVATION_DP = 24f
        private const val MOVE_MODE_SCALE = 1.15f
        private const val SCRIM_COLOR = 0x99000000.toInt()
        /** Кнопку бросили поверх подсказки — притухаем её, чтобы кнопка читалась и осталась хватаемой. */
        private const val HINT_DIMMED_ALPHA = 0.25f
    }
}
