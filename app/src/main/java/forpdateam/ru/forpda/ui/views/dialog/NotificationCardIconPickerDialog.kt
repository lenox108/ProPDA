package forpdateam.ru.forpda.ui.views.dialog

import android.content.Context
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import forpdateam.ru.forpda.R
import forpdateam.ru.forpda.common.appicon.AppIcons

/**
 * Независимый выбор левого слота карточки уведомления.
 *
 * Android предоставляет только два корректных варианта: статическую иконку
 * пакета или тот же small icon, который уже используется в статус-баре.
 */
object NotificationCardIconPickerDialog {

    fun show(context: Context, current: String, onPick: (String) -> Unit) {
        val values = arrayOf(
                AppIcons.NOTIFICATION_CARD_ICON_APP,
                AppIcons.NOTIFICATION_CARD_ICON_STATUS,
        )
        val titles = arrayOf(
                context.getString(R.string.notification_card_icon_app),
                context.getString(R.string.notification_card_icon_status),
        )
        val checked = values.indexOf(current).coerceAtLeast(0)

        MaterialAlertDialogBuilder(context)
                .setTitle(R.string.pref_title_notification_card_icon)
                .setMessage(R.string.notification_card_icon_hint)
                .setSingleChoiceItems(titles, checked) { dialog, which ->
                    dialog.dismiss()
                    onPick(values[which])
                }
                .setNegativeButton(android.R.string.cancel, null)
                .showWithStyledButtons(compact = false)
    }
}
