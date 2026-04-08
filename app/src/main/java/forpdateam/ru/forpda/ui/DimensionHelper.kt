package forpdateam.ru.forpda.ui
import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

/**
 * Created by radiationx on 30.12.17.
 *
 * Высота клавиатуры: max(оценка по layout, IME inset). На Android 15+ с enableEdgeToEdge()
 * окно часто не «сжимается» как раньше, и layout-оценка даёт kh≈0 — тогда берём реальный IME.
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

    /** Insets с decor/content root — источник правды при edge-to-edge. */
    private var lastImeBottom = 0
    private var lastStatusTop = 0
    private var lastNavBottom = 0

    init {
        dimension.also {
            it.statusBar = defaultStatusBarHeight
            it.savedKeyboardHeight = defaultKeyboardHeight
            listener.onDimensionsChange(it)
        }
        ViewCompat.setOnApplyWindowInsetsListener(container) { _, insets ->
            lastImeBottom = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
            lastStatusTop = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            lastNavBottom = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            applyMergedKeyboardAndNotify()
            insets
        }
        measurer.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            applyMergedKeyboardAndNotify()
        }
        container.post { ViewCompat.requestApplyInsets(container) }
    }

    private fun applyMergedKeyboardAndNotify() {
        val v = measurer
        // При edge-to-edge и разных OEM view.top может быть 0 — используем WindowInsets.
        dimension.statusBar = maxOf(defaultStatusBarHeight, lastStatusTop)
        dimension.navigationBar = maxOf(0, lastNavBottom)
        dimension.contentHeight = v.height
        val layoutKh = container.height - dimension.contentHeight - dimension.statusBar - dimension.navigationBar
        val mergedKh = maxOf(maxOf(0, layoutKh), lastImeBottom)
        dimension.keyboardHeight = mergedKh
        if (mergedKh > 100) {
            dimension.savedKeyboardHeight = mergedKh
        }
        dimension.also {
            if (it.statusBar != lastSb
                    || it.navigationBar != lastNb
                    || it.contentHeight != lastCh
                    || it.keyboardHeight != lastKh) {

                lastSb = it.statusBar
                lastNb = it.navigationBar
                lastCh = it.contentHeight
                lastKh = it.keyboardHeight
                listener.onDimensionsChange(it)
            }
        }
    }

    class Dimensions {
        var statusBar = 0
        var navigationBar = 0
        var contentHeight = 0
        var keyboardHeight = 0
        var savedKeyboardHeight = 0
        var isFakeKeyboardShow = false

        fun isKeyboardShow(): Boolean = keyboardHeight > 100

        override fun toString(): String {
            return "Dimensions: to=$statusBar, bo=$navigationBar, ch=$contentHeight, kh=$keyboardHeight, skh=$savedKeyboardHeight, ifks=$isFakeKeyboardShow, iks=${isKeyboardShow()}"
        }
    }

    interface DimensionsListener {
        fun onDimensionsChange(dimensions: Dimensions)
    }
}
