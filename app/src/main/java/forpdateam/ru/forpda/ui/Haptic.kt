package forpdateam.ru.forpda.ui

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View

/**
 * Единая точка тактильного отклика по проекту. Тонкие, «M3-expressive» вибро-
 * события на ключевые действия. Всё через [View.performHapticFeedback], поэтому
 * автоматически уважает системную настройку тактильного отклика пользователя
 * (без FLAG_IGNORE_GLOBAL_SETTING) — если у него haptics выключены, ничего не
 * произойдёт.
 *
 * Константы CONFIRM/REJECT появились в API 30 — для более старых версий даём
 * разумный fallback, чтобы отклик был на всех поддерживаемых устройствах (26+).
 */
object Haptic {

    /** Лёгкий «тик» выбора: смена вкладки, тумблер, выбор в списке. */
    fun tick(view: View) {
        view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
    }

    /** Долгое нажатие: вход в мультивыбор, контекстное меню. */
    fun longPress(view: View) {
        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
    }

    /** Подтверждение действия: отправка сообщения/комментария, успех. */
    fun confirm(view: View) {
        val constant = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            HapticFeedbackConstants.CONFIRM
        } else {
            HapticFeedbackConstants.KEYBOARD_TAP
        }
        view.performHapticFeedback(constant)
    }

    /** Отказ/ошибка: недоступное действие, неуспех. */
    fun reject(view: View) {
        val constant = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            HapticFeedbackConstants.REJECT
        } else {
            HapticFeedbackConstants.LONG_PRESS
        }
        view.performHapticFeedback(constant)
    }
}
