package forpdateam.ru.forpda.ui.fragments

import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.RelativeLayout
import forpdateam.ru.forpda.ui.DimensionHelper
import forpdateam.ru.forpda.ui.views.messagepanel.MessagePanel

/**
 * Контроллер для управления MessagePanel в TabFragment.
 * Отвечает за настройку IME insets и отступов для messagePanelHost.
 */
class MessagePanelHelper(
        private val messagePanelHost: FrameLayout
) {

    /**
     * Обновляет отступы messagePanelHost на основе IME insets.
     * На части устройств/OEM adjustResize не "поднимает" нижние вьюхи, IME просто накладывается.
     * Поэтому поднимаем хост MessagePanel (theme/QMS) через bottomMargin = IME inset.
     * Если IME inset отсутствует, но keyboardHeight уже рассчитан — используем его.
     */
    fun updateImeInsets(dimensions: DimensionHelper.Dimensions, baseBottomMargin: Int = 0) {
        val imeBottom = if (dimensions.isFakeKeyboardShow || hasCompactBbcodeHold(messagePanelHost)) {
            0
        } else {
            maxOf(0, maxOf(dimensions.imeInsetBottom, dimensions.keyboardHeight))
        }
        val bottomMargin = if (imeBottom > 0) {
            imeBottom
        } else {
            baseBottomMargin
        }
        (messagePanelHost.layoutParams as? RelativeLayout.LayoutParams)?.also { lp ->
            if (lp.bottomMargin != bottomMargin) {
                lp.bottomMargin = bottomMargin
                messagePanelHost.layoutParams = lp
            }
        }
    }

    private fun hasCompactBbcodeHold(view: View): Boolean {
        if (view is MessagePanel && view.isCompactBbcodeLayoutHoldActive()) {
            return true
        }
        if (view !is ViewGroup) {
            return false
        }
        for (i in 0 until view.childCount) {
            if (hasCompactBbcodeHold(view.getChildAt(i))) {
                return true
            }
        }
        return false
    }

    /**
     * Сбрасывает отступы messagePanelHost.
     */
    fun resetImeInsets() {
        (messagePanelHost.layoutParams as? RelativeLayout.LayoutParams)?.also { lp ->
            if (lp.bottomMargin != 0) {
                lp.bottomMargin = 0
                messagePanelHost.layoutParams = lp
            }
        }
    }
}
