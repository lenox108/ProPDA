package forpdateam.ru.forpda.ui.views.tooltip

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.PopupWindow
import android.widget.TextView
import forpdateam.ru.forpda.R

/**
 * Simple tooltip to replace SimpleTooltip library
 */
class CustomTooltip(
    private val context: Context,
    private val anchorView: View,
    private val text: CharSequence,
    private val gravity: Int = Gravity.BOTTOM
) {
    private var popupWindow: PopupWindow? = null

    val isShowing: Boolean
        get() = popupWindow?.isShowing == true

    fun show() {
        val contentView = LayoutInflater.from(context).inflate(R.layout.custom_tooltip, null)
        val textView = contentView.findViewById<TextView>(R.id.tooltip_text)
        textView.text = text

        popupWindow = PopupWindow(
            contentView,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT
        ).apply {
            isFocusable = true
            isOutsideTouchable = true
            setBackgroundDrawable(ColorDrawable(Color.BLACK))
        }

        popupWindow?.showAsDropDown(anchorView, 0, -anchorView.height, gravity)
    }

    fun dismiss() {
        popupWindow?.dismiss()
    }
}
