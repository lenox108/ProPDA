@file:JvmName("MaterialAlertDialogHelper")

package forpdateam.ru.forpda.ui.views.dialog

import androidx.appcompat.app.AlertDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import forpdateam.ru.forpda.R

/**
 * Тема alertDialogTheme не всегда задаёт контраст кнопок в тёмной теме — красим явно.
 * [android.util.TypedValue.data] для ссылки на цвет — не ARGB; через [android.content.res.TypedArray] цвет резолвится верно.
 */
fun MaterialAlertDialogBuilder.showWithStyledButtons(): AlertDialog {
    val dialog = create()
    dialog.setOnShowListener {
        val c = resolveLinkColor(dialog.context)
        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(c)
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(c)
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL)?.setTextColor(c)
    }
    dialog.show()
    return dialog
}

private fun resolveLinkColor(context: android.content.Context): Int {
    val a = context.obtainStyledAttributes(intArrayOf(R.attr.link_color))
    val c = a.getColor(0, 0xff2177af.toInt())
    a.recycle()
    return c
}
