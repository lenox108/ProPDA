package forpdateam.ru.forpda.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Rect
import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import kotlin.math.roundToInt

/**
 * Created by radiationx on 30.12.17.
 *
 * Высота клавиатуры: max(оценка по layout, IME inset). На Android 15+ с enableEdgeToEdge()
 * окно часто не «сжимается» как раньше, и layout-оценка даёт kh≈0 — тогда берём реальный IME.
 *
 * На API 24–25 Type.ime() часто 0, а measurer и container оба match_parent — layoutKh≈0.
 * Тогда [legacyKeyboardHeightPx] даёт высоту по видимой области окна (классический fallback).
 */
class DimensionHelper(
        private val measurer: View,
        private val container: View,
        private val listener: DimensionsListener,
        private val defaultStatusBarHeight: Int = 0,
        private val defaultKeyboardHeight: Int = 0
) {

    private val dimension = Dimensions()

    private var lastSb = 0
    private var lastNb = 0
    private var lastCh = 0
    private var lastKh = 0
    private var lastImeForNotify = 0

    /** Insets с decor/content root — источник правды при edge-to-edge. */
    private var lastImeBottom = 0
    private var lastImeVisible = false
    private var lastStatusTop = 0
    private var lastNavBottom = 0
    private var notifyScheduled = false

    init {
        dimension.also {
            it.statusBar = defaultStatusBarHeight
            it.savedKeyboardHeight = defaultKeyboardHeight
            listener.onDimensionsChange(it)
        }
        ViewCompat.setOnApplyWindowInsetsListener(container) { _, insets ->
            lastImeVisible = insets.isVisible(WindowInsetsCompat.Type.ime())
            // Некоторые OEM/версии Android могут отдавать "залипший" IME inset/visibility после скрытия IME.
            // Сама по себе isVisible(Type.ime()) тоже может "залипать", поэтому окончательное решение делаем ниже,
            // учитывая видимую область окна (visible frame).
            lastImeBottom = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
            lastStatusTop = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            lastNavBottom = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            scheduleApplyMergedKeyboardAndNotify()
            insets
        }
        measurer.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            scheduleApplyMergedKeyboardAndNotify()
        }
        container.post { ViewCompat.requestApplyInsets(container) }
    }

    /** Дебаунсим частые insets/layout события в один проход UI. */
    private fun scheduleApplyMergedKeyboardAndNotify() {
        if (notifyScheduled) return
        notifyScheduled = true
        container.post {
            notifyScheduled = false
            applyMergedKeyboardAndNotify()
        }
    }

    private fun applyMergedKeyboardAndNotify() {
        val v = measurer
        // При edge-to-edge и разных OEM view.top может быть 0 — используем WindowInsets.
        dimension.statusBar = maxOf(defaultStatusBarHeight, lastStatusTop)
        dimension.navigationBar = maxOf(0, lastNavBottom)
        dimension.contentHeight = v.height
        val layoutKh = maxOf(0, container.height - dimension.contentHeight - dimension.statusBar - dimension.navigationBar)
        val visibleFrameKb = visibleFrameKeyboardHeightPx()

        // Unified IME resolution:
        // - prefer IME inset when it looks real and IME is visible
        // - but always decay to 0 when visible frame says "no keyboard"
        val imeCandidate = if (lastImeVisible) lastImeBottom else 0
        val resolvedImeBottom = if (visibleFrameKb == 0) {
            // When keyboard is really hidden, visible frame delta is below threshold.
            0
        } else {
            maxOf(imeCandidate, visibleFrameKb)
        }

        // If a previously real IME signal has decayed to hidden, ignore a stale
        // adjustResize/layout delta. Otherwise MessagePanelHost keeps the old IME margin and
        // leaves a blank keyboard-sized strip after back/gesture dismisses the keyboard.
        val resolvedLayoutKh = if (resolvedImeBottom == 0 && visibleFrameKb == 0 && lastImeForNotify > 0) {
            0
        } else {
            layoutKh
        }
        val mergedKh = maxOf(resolvedLayoutKh, resolvedImeBottom, visibleFrameKb)
        dimension.keyboardHeight = mergedKh
        dimension.imeInsetBottom = resolvedImeBottom
        if (!dimension.isFakeKeyboardShow && mergedKh > 100) {
            dimension.savedKeyboardHeight = mergedKh
        }
        dimension.also {
            if (it.statusBar != lastSb
                    || it.navigationBar != lastNb
                    || it.contentHeight != lastCh
                    || it.keyboardHeight != lastKh
                    || it.imeInsetBottom != lastImeForNotify) {

                lastSb = it.statusBar
                lastNb = it.navigationBar
                lastCh = it.contentHeight
                lastKh = it.keyboardHeight
                lastImeForNotify = it.imeInsetBottom
                listener.onDimensionsChange(it)
            }
        }
    }

    /**
     * Высота клавиатуры по "видимой области" окна (classic visible display frame delta).
     *
     * Используется как:
     * - fallback для старых/кривых insets
     * - "decay signal" против залипания IME inset/visibility: если delta < threshold, считаем IME скрытой.
     */
    private fun visibleFrameKeyboardHeightPx(): Int {
        val activity = container.context.unwrapToActivity() ?: return 0
        val decor = activity.window.decorView
        val rect = Rect()
        decor.getWindowVisibleDisplayFrame(rect)
        val rawDelta = decor.rootView.height - rect.bottom
        // In edge-to-edge with 3-button navigation, the visible frame ends above the
        // navigation bar even when IME is hidden. Treat only the area above nav as keyboard.
        val delta = maxOf(0, rawDelta - lastNavBottom)
        val minKb = (48f * container.resources.displayMetrics.density).roundToInt().coerceAtLeast(1)
        return if (delta >= minKb) delta else 0
    }

    class Dimensions {
        var statusBar = 0
        var navigationBar = 0
        var contentHeight = 0
        var keyboardHeight = 0
        /** Нижний inset IME с WindowInsets — надёжный признак при компактной клавиатуре / OEM. */
        var imeInsetBottom = 0
        var savedKeyboardHeight = 0
        var isFakeKeyboardShow = false

        /**
         * Раньше порог 100px давал ложное «клавиатура закрыта» при kh 50–99px — тогда MainActivity
         * оставлял padding под нижнее меню и между IME и панелью ответа появлялся лишний зазор ~48dp.
         * Порог 32dp: компактные/плавающие IME и часть сторонних клавиатур дают kh 50–64.
         */
        fun isKeyboardShow(): Boolean = imeInsetBottom > 0 || keyboardHeight > 32

        override fun toString(): String {
            return "Dimensions: to=$statusBar, bo=$navigationBar, ch=$contentHeight, kh=$keyboardHeight, ime=$imeInsetBottom, skh=$savedKeyboardHeight, ifks=$isFakeKeyboardShow, iks=${isKeyboardShow()}"
        }
    }

    interface DimensionsListener {
        fun onDimensionsChange(dimensions: Dimensions)
    }
}

private tailrec fun Context.unwrapToActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.unwrapToActivity()
    else -> null
}
