package forpdateam.ru.forpda.ui

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.preference.PreferenceManager
import forpdateam.ru.forpda.common.Preferences

/**
 * Единая точка тактильного отклика по проекту. Тонкие, «M3-expressive» вибро-
 * события на ключевые действия (смена вкладки, отправка и т.д.).
 *
 * Два уровня гейта:
 *  1. Настройка приложения [Preferences.Main.HAPTIC_FEEDBACK_ENABLE] (тумблер в
 *     «Внешнем виде», по умолчанию вкл.) — читается синхронно из default
 *     SharedPreferences по контексту view; выключение мгновенно глушит весь отклик.
 *  2. Системная настройка тактильного отклика — уважается автоматически
 *     ([View.performHapticFeedback] без FLAG_IGNORE_GLOBAL_SETTING).
 *
 * Константы CONFIRM/REJECT появились в API 30 — для более старых версий fallback.
 */
object Haptic {

    /** Лёгкий «тик» выбора: смена вкладки, тумблер, выбор в списке. */
    fun tick(view: View) {
        if (!isEnabled(view)) return
        view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
    }

    /** Долгое нажатие: вход в мультивыбор, контекстное меню. */
    fun longPress(view: View) {
        if (!isEnabled(view)) return
        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
    }

    /** Подтверждение действия: отправка сообщения/комментария, успех. */
    fun confirm(view: View) {
        if (!isEnabled(view)) return
        val constant = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            HapticFeedbackConstants.CONFIRM
        } else {
            HapticFeedbackConstants.KEYBOARD_TAP
        }
        view.performHapticFeedback(constant)
    }

    /** Отказ/ошибка: недоступное действие, неуспех. */
    fun reject(view: View) {
        if (!isEnabled(view)) return
        val constant = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            HapticFeedbackConstants.REJECT
        } else {
            HapticFeedbackConstants.LONG_PRESS
        }
        view.performHapticFeedback(constant)
    }

    /**
     * Одиночный отклик на view, у которой автоматический haptic отключён
     * ([View.setHapticFeedbackEnabled] == false) — например, чтобы погасить дублирующий
     * авто-buzz долгого нажатия. Обходит флаг view через FLAG_IGNORE_VIEW_SETTING, но по-прежнему
     * уважает настройку приложения [Preferences.Main.HAPTIC_FEEDBACK_ENABLE] и системную настройку.
     */
    fun perform(view: View, constant: Int) {
        if (!isEnabled(view)) return
        view.performHapticFeedback(constant, HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING)
    }

    private fun isEnabled(view: View): Boolean =
            PreferenceManager.getDefaultSharedPreferences(view.context)
                    .getBoolean(Preferences.Main.HAPTIC_FEEDBACK_ENABLE, true)
}
