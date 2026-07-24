package forpdateam.ru.forpda.common

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.StringRes
import com.google.android.material.color.MaterialColors
import forpdateam.ru.forpda.R
import forpdateam.ru.forpda.common.appicon.AppIconManager
import kotlin.math.roundToInt

/**
 * Внутриприложенная замена системного текстового [Toast].
 *
 * Android 12+ сам добавляет к обычному Toast статичную `applicationInfo.icon`,
 * поэтому она не следует за переключаемым launcher alias. Эта реализация рисует
 * плашку сама и берёт значок из [AppIconManager.selected].
 */
object AppToast {

    const val LENGTH_SHORT: Int = Toast.LENGTH_SHORT
    const val LENGTH_LONG: Int = Toast.LENGTH_LONG

    fun makeText(context: Context, text: CharSequence?, duration: Int): Message =
            Message(context, text ?: "", duration)

    fun makeText(context: Context, @StringRes textRes: Int, duration: Int): Message =
            Message(context, context.getText(textRes), duration)

    class Message internal constructor(
            private val context: Context,
            private val text: CharSequence,
            private val duration: Int,
    ) {
        fun show() {
            if (Looper.myLooper() == Looper.getMainLooper()) {
                createFrameworkToast().show()
            } else {
                Handler(Looper.getMainLooper()).post { createFrameworkToast().show() }
            }
        }

        @Suppress("DEPRECATION")
        internal fun createFrameworkToast(): Toast {
            val density = context.resources.displayMetrics.density
            fun dp(value: Int) = (value * density).roundToInt()

            val surface = MaterialColors.getColor(
                    context,
                    com.google.android.material.R.attr.colorSurfaceContainerHigh,
                    Color.rgb(48, 48, 48),
            )
            val onSurface = MaterialColors.getColor(
                    context,
                    com.google.android.material.R.attr.colorOnSurface,
                    Color.WHITE,
            )
            val iconRes = selectedIconRes(context)

            val icon = ImageView(context).apply {
                setImageResource(iconRes)
                scaleType = ImageView.ScaleType.FIT_CENTER
                contentDescription = context.getString(R.string.app_name)
                tag = iconRes
            }
            val label = TextView(context).apply {
                this.text = this@Message.text
                setTextColor(onSurface)
                textSize = 15f
                maxLines = 4
                maxWidth = context.resources.displayMetrics.widthPixels - dp(112)
            }
            val content = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(16), dp(12), dp(20), dp(12))
                elevation = dp(6).toFloat()
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dp(24).toFloat()
                    setColor(surface)
                }
                addView(icon, LinearLayout.LayoutParams(dp(32), dp(32)))
                addView(
                        label,
                        LinearLayout.LayoutParams(
                                ViewGroup.LayoutParams.WRAP_CONTENT,
                                ViewGroup.LayoutParams.WRAP_CONTENT,
                        ).apply { marginStart = dp(12) },
                )
            }

            return Toast(context.applicationContext).apply {
                this.duration = this@Message.duration
                setGravity(Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL, 0, dp(72))
                view = content
            }
        }
    }

    internal fun selectedIconRes(context: Context): Int =
            AppIconManager.selected(context).iconRes
}
