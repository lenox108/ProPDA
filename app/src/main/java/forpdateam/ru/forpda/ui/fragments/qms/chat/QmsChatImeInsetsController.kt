package forpdateam.ru.forpda.ui.fragments.qms.chat

import android.graphics.Rect
import android.os.Build
import android.view.View
import android.widget.RelativeLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import forpdateam.ru.forpda.ui.DimensionsProvider
import forpdateam.ru.forpda.ui.views.messagepanel.MessagePanel
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Owns the IME inset logic for the QMS chat screen. Extracted from
 * `QmsChatFragment` (god-class §1.1). All Android view dependencies
 * are passed in via constructor lambdas/parameters so this class
 * stays unit-testable.
 */
class QmsChatImeInsetsController(
        private val messagePanelHost: View,
        private val messagePanel: MessagePanel,
        private val dimensionsProvider: DimensionsProvider,
        private val densityPx: Float,
        private val viewReadyProvider: () -> Boolean,
        private val visibleFrameProvider: () -> Rect,
) {

    private var lastWindowInsets: WindowInsetsCompat? = null

    /** Bind the inset listener to the given root view. */
    fun attachTo(root: View) {
        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            lastWindowInsets = insets
            applyMessagePanelImeInsets(insets)
            insets
        }
    }

    /** Drop the listener and clear the cached insets (e.g. on view destroy). */
    fun detach(root: View) {
        ViewCompat.setOnApplyWindowInsetsListener(root, null)
        lastWindowInsets = null
    }

    /** Public re-application entry point (e.g. when message panel becomes visible). */
    fun reapply() {
        applyMessagePanelImeInsets(lastWindowInsets)
    }

    fun applyMessagePanelImeInsets(insets: WindowInsetsCompat?) {
        if (insets != null) {
            lastWindowInsets = insets
        }
        if (!viewReadyProvider()) return
        val bottomMargin = if (!messagePanel.isCompactBbcodeLayoutHoldActive()) {
            resolveImeBottom(insets)
        } else {
            0
        }
        // NEVER re-assign layoutParams unconditionally here: `View.setLayoutParams` always calls
        // requestLayout(), and this method is also driven by an OnGlobalLayoutListener — so an
        // unconditional assignment made every layout pass schedule the next one, i.e. the chat screen
        // ran a full measure/layout traversal on EVERY frame for as long as it was open. That storm is
        // what made text selection lag and the selection handles / floating toolbar jitter (the editor
        // repositions them on each layout). Mutate + assign only when the margin actually changes.
        val lp = messagePanelHost.layoutParams as? RelativeLayout.LayoutParams ?: return
        if (lp.bottomMargin != bottomMargin) {
            lp.bottomMargin = bottomMargin
            messagePanelHost.layoutParams = lp
        }
    }

    fun resolveImeBottom(insets: WindowInsetsCompat?): Int {
        val imeBottom = insets?.getInsets(WindowInsetsCompat.Type.ime())?.bottom ?: 0
        val imeVisible = insets?.isVisible(WindowInsetsCompat.Type.ime()) == true
        val dimensions = dimensionsProvider.getDimensions()
        val dimensionBottom = if (dimensions.isKeyboardShow()) {
            max(imeBottom, max(dimensions.imeInsetBottom, dimensions.keyboardHeight))
        } else {
            0
        }
        if (imeVisible && imeBottom > 0) {
            // DimensionHelper cross-checks visible display frame and decays stale IME insets.
            return max(imeBottom, dimensionBottom)
        }

        // API 24/25 often reports no Type.ime() inset under edge-to-edge/fullscreen flags.
        // Use the same visible display frame signal as DimensionHelper, but only as a legacy
        // fallback so Android 11+ keeps the platform IME inset as the source of truth.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return 0
        }

        return max(dimensionBottom, visibleFrameKeyboardHeightPx())
    }

    private fun visibleFrameKeyboardHeightPx(): Int {
        if (!viewReadyProvider()) return 0
        val rect = visibleFrameProvider()
        val rootView = messagePanelHost.rootView
        val rawDelta = (rootView.height - rect.bottom).coerceAtLeast(0)
        val navBottom = dimensionsProvider.getDimensions().navigationBar
        val delta = (rawDelta - navBottom).coerceAtLeast(0)
        val minKeyboard = (48f * densityPx).roundToInt().coerceAtLeast(1)
        return if (delta >= minKeyboard) delta else 0
    }
}
