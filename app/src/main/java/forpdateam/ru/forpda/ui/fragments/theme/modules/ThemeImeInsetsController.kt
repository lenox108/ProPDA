package forpdateam.ru.forpda.ui.fragments.theme.modules

import android.graphics.Rect
import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import forpdateam.ru.forpda.BuildConfig
import forpdateam.ru.forpda.common.getColorFromAttr
import forpdateam.ru.forpda.ui.views.ExtendedWebView
import forpdateam.ru.forpda.ui.views.messagepanel.MessagePanel
import timber.log.Timber
import kotlin.math.roundToInt

/**
 * Encapsulates IME inset handling for the theme screen.
 * Prevents "blank strip" bugs after keyboard dismissal on OEM builds.
 */
class ThemeImeInsetsController(
    private val webView: ExtendedWebView,
) {

    fun applyImeInsets(
        coordinatorLayout: View,
        messagePanel: MessagePanel,
        refreshLayout: View
    ) {
        ViewCompat.setOnApplyWindowInsetsListener(coordinatorLayout) { v, insets ->
            applyResolvedImeInsets(v, insets, messagePanel, refreshLayout)
            insets
        }
    }

    /**
     * Some OEM builds can "stick" IME insets/visibility after IME is dismissed
     * (especially after SearchView / find-in-page).
     * If listener is not re-fired, the host padding remains >0 and produces a blank space.
     */
    fun forceReapplyImeInsets(
        coordinatorLayout: View,
        messagePanel: MessagePanel,
        messagePanelHost: View,
        refreshLayout: View
    ) {
        ViewCompat.requestApplyInsets(coordinatorLayout)
        coordinatorLayout.post {
            val rootInsets = ViewCompat.getRootWindowInsets(coordinatorLayout)
            if (rootInsets != null) {
                applyResolvedImeInsets(
                    coordinatorLayout = coordinatorLayout,
                    insets = rootInsets,
                    messagePanel = messagePanel,
                    refreshLayout = refreshLayout
                )
            } else {
                messagePanelHost.setPadding(0, 0, 0, 0)
                coordinatorLayout.requestLayout()
                refreshLayout.requestLayout()
            }
        }
    }

    /**
     * Full "hard" layout reset after keyboard/search dismiss.
     */
    fun forceFullLayoutReset(
        coordinatorLayout: View,
        messagePanel: MessagePanel,
        messagePanelHost: View,
        refreshLayout: View
    ) {
        ViewCompat.getWindowInsetsController(webView)?.hide(WindowInsetsCompat.Type.ime())
        coordinatorLayout.setPadding(
            coordinatorLayout.paddingLeft,
            coordinatorLayout.paddingTop,
            coordinatorLayout.paddingRight,
            0
        )
        messagePanelHost.setPadding(0, 0, 0, 0)
        (messagePanelHost.layoutParams as? android.widget.RelativeLayout.LayoutParams)?.also { lp ->
            if (lp.bottomMargin != 0) {
                lp.bottomMargin = 0
                messagePanelHost.layoutParams = lp
            }
        }

        ensureMatchParentHeight(refreshLayout)
        ensureMatchParentHeight(webView)

        try {
            webView.setBackgroundColor(android.graphics.Color.TRANSPARENT)
        } catch (_: Throwable) {
        }
        val underlayColor = webView.context.getColorFromAttr(forpdateam.ru.forpda.R.attr.background_for_lists)
        try {
            refreshLayout.setBackgroundColor(underlayColor)
        } catch (_: Throwable) {
        }

        ViewCompat.requestApplyInsets(coordinatorLayout)
        coordinatorLayout.post {
            coordinatorLayout.requestLayout()
            refreshLayout.requestLayout()
            messagePanel.requestLayout()
            webView.requestLayout()
            webView.setPaddingBottom(0)
            webView.flushQueuedJs()
            val rootInsets = ViewCompat.getRootWindowInsets(coordinatorLayout)
            if (rootInsets != null) {
                applyResolvedImeInsets(
                    coordinatorLayout = coordinatorLayout,
                    insets = rootInsets,
                    messagePanel = messagePanel,
                    refreshLayout = refreshLayout
                )
            }
        }
    }

    fun resetImeInsets(
        coordinatorLayout: View,
        messagePanelHost: View
    ) {
        ViewCompat.getWindowInsetsController(webView)?.hide(WindowInsetsCompat.Type.ime())
        webView.requestLayout()
        coordinatorLayout.requestLayout()
        coordinatorLayout.setPadding(
            coordinatorLayout.paddingLeft,
            coordinatorLayout.paddingTop,
            coordinatorLayout.paddingRight,
            0
        )
        messagePanelHost.setPadding(0, 0, 0, 0)
        (messagePanelHost.layoutParams as? android.widget.RelativeLayout.LayoutParams)?.also { lp ->
            if (lp.bottomMargin != 0) {
                lp.bottomMargin = 0
                messagePanelHost.layoutParams = lp
            }
        }
    }

    private fun applyResolvedImeInsets(
        coordinatorLayout: View,
        insets: WindowInsetsCompat,
        messagePanel: MessagePanel,
        refreshLayout: View
    ) {
        val imeBottomRaw = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
        val imeVisibleRaw = insets.isVisible(WindowInsetsCompat.Type.ime())
        val visibleFrameKb = visibleFrameKeyboardHeightPx(coordinatorLayout)

        val resolvedIme = if (!imeVisibleRaw) {
            0
        } else {
            maxOf(imeBottomRaw, visibleFrameKb)
        }

        if (BuildConfig.DEBUG) {
            Timber.d(
                "applyResolvedImeInsets: imeRaw=$imeBottomRaw visRaw=$imeVisibleRaw vfKb=$visibleFrameKb resolved=$resolvedIme " +
                        "coordPb=${coordinatorLayout.paddingBottom}"
            )
        }

        coordinatorLayout.post {
            coordinatorLayout.requestLayout()
            messagePanel.requestLayout()
            refreshLayout.requestLayout()
            webView.setPaddingBottom(0)
            webView.flushQueuedJs()
        }
    }

    private fun visibleFrameKeyboardHeightPx(view: View): Int {
        val visibleFrame = Rect()
        view.getWindowVisibleDisplayFrame(visibleFrame)
        val rootView = view.rootView
        val displayHeight = rootView.height
        return (displayHeight - visibleFrame.bottom).coerceAtLeast(0)
    }

    private fun ensureMatchParentHeight(view: View) {
        val lp = view.layoutParams ?: return
        if (lp.height != ViewGroup.LayoutParams.MATCH_PARENT) {
            lp.height = ViewGroup.LayoutParams.MATCH_PARENT
            view.layoutParams = lp
        }
        if (lp.width != ViewGroup.LayoutParams.MATCH_PARENT) {
            lp.width = ViewGroup.LayoutParams.MATCH_PARENT
            view.layoutParams = lp
        }
    }
}
