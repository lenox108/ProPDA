package forpdateam.ru.forpda.ui.views.dialog

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.content.res.AppCompatResources
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import forpdateam.ru.forpda.R
import forpdateam.ru.forpda.common.appicon.AppIconVariant
import forpdateam.ru.forpda.common.appicon.AppIcons
import forpdateam.ru.forpda.common.getColorFromAttr
import kotlin.math.roundToInt

/**
 * Выбор иконки запуска: список карточек «значок + название + пояснение»,
 * применение по тапу.
 *
 * Превью — те же adaptive-ресурсы, что уходят в лаунчер, поэтому день/ночь и
 * системная маска (круг/сквиркл) выглядят ровно как на рабочем столе.
 */
object AppIconPickerDialog {

    fun show(context: Context, current: AppIconVariant, onPick: (AppIconVariant) -> Unit) {
        val dp = context.resources.displayMetrics.density
        fun px(v: Int) = (v * dp).roundToInt()

        val outline = context.getColorFromAttr(com.google.android.material.R.attr.colorOutline)
        val selectedRing = context.getColorFromAttr(androidx.appcompat.R.attr.colorPrimary)
        val onSurface = context.getColorFromAttr(com.google.android.material.R.attr.colorOnSurface)
        val onSurfaceVar = context.getColorFromAttr(com.google.android.material.R.attr.colorOnSurfaceVariant)
        val surface = context.getColorFromAttr(com.google.android.material.R.attr.colorSurfaceContainerLow)

        lateinit var dialog: androidx.appcompat.app.AlertDialog

        val list = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(px(16), px(4), px(16), px(8))
        }

        AppIcons.variants.forEach { variant ->
            val selected = variant.id == current.id
            val title = context.getString(variant.titleRes)

            val preview = ImageView(context).apply {
                setImageDrawable(AppCompatResources.getDrawable(context, variant.iconRes))
                scaleType = ImageView.ScaleType.FIT_CENTER
                contentDescription = title
            }
            val texts = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                addView(TextView(context).apply {
                    text = title
                    setTextColor(onSurface)
                    textSize = 15f
                    setTypeface(typeface, Typeface.BOLD)
                })
                variant.subtitleRes?.let { subtitle ->
                    addView(TextView(context).apply {
                        setText(subtitle)
                        setTextColor(onSurfaceVar)
                        textSize = 12f
                        setPadding(0, px(2), 0, 0)
                    })
                }
            }
            val card = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(px(14), px(14), px(14), px(14))
                background = GradientDrawable().apply {
                    cornerRadius = px(16).toFloat()
                    setColor(surface)
                    setStroke(if (selected) px(3) else px(1), if (selected) selectedRing else outline)
                }
                addView(preview, LinearLayout.LayoutParams(px(56), px(56)))
                addView(texts, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                        .apply { marginStart = px(14) })
                isClickable = true
                setOnClickListener { dialog.dismiss(); onPick(variant) }
            }
            list.addView(card, LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = px(6); bottomMargin = px(6)
            })
        }

        dialog = MaterialAlertDialogBuilder(context)
                .setTitle(R.string.pref_title_app_icon)
                .setView(ScrollView(context).apply { addView(list) })
                .setNegativeButton(android.R.string.cancel, null)
                .showWithStyledButtons(compact = false)
    }
}
