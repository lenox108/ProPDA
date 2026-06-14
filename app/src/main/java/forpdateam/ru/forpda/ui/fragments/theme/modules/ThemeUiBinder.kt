package forpdateam.ru.forpda.ui.fragments.theme.modules

import android.view.View
import forpdateam.ru.forpda.ui.views.ExtendedWebView
import forpdateam.ru.forpda.ui.views.messagepanel.MessagePanel

/**
 * Контроллер для связывания UI-элементов в ThemeFragmentWeb.
 * Отвечает за управление видимостью FAB, отступами WebView и синхронизацию с MessagePanel.
 */
class ThemeUiBinder(
        private val webView: ExtendedWebView,
        private val messagePanel: MessagePanel,
        private val messagePanelHost: View,
        private val onMessagePanelHeightChanged: (() -> Unit)? = null
) {

    /**
     * Настраивает слушатель изменения высоты MessagePanel для синхронизации отступов WebView.
     */
    fun setupMessagePanelHeightListener() {
        messagePanel.heightChangeListener = MessagePanel.HeightChangeListener {
            // Явно setPaddingBottom(ExtendedWebView) — не View-KTX paddingBottom (он задаёт padding вьюхи, не JS).
            // messagePanelHost участвует в RelativeLayout и уже уменьшает высоту WebView сверху панели.
            // Дополнительный DOM spacer на высоту панели создавал пустой блок перед редактором.
            webView.setPaddingBottom(0)
            webView.flushQueuedJs()
            onMessagePanelHeightChanged?.invoke()
        }
    }

    /**
     * Синхронизирует отступ WebView с текущей высотой MessagePanel.
     * Должен вызываться после show/hide панели, если setHeightChangeListener ещё не установлен.
     */
    fun syncWebViewPaddingWithMessagePanel() {
        webView.post {
            webView.setPaddingBottom(0)
            webView.flushQueuedJs()
        }
    }

    /**
     * Обновляет состояние кнопки скролла (FAB).
     */
    fun updateScrollButtonState(isEnabled: Boolean, fab: View) {
        fab.visibility = View.VISIBLE
        val isVisibleOnScreen = isEnabled && fab.alpha > 0f && fab.scaleX > 0f && fab.scaleY > 0f
        fab.isEnabled = isVisibleOnScreen
        fab.isClickable = isVisibleOnScreen
        fab.isFocusable = isVisibleOnScreen
    }

    /**
     * Сбрасывает отступы messagePanelHost при уходе из темы.
     * Fix: предотвращает фантомную белую область.
     */
    fun resetMessagePanelHostPadding() {
        messagePanelHost.setPadding(0, 0, 0, 0)
        (messagePanelHost.layoutParams as? android.widget.RelativeLayout.LayoutParams)?.also { lp ->
            if (lp.bottomMargin != 0) {
                lp.bottomMargin = 0
                messagePanelHost.layoutParams = lp
            }
        }
    }

    /**
     * Вызывает onResume для MessagePanel.
     */
    fun onResumeMessagePanel() {
        messagePanel.onResume()
    }
}
