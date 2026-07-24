package forpdateam.ru.forpda.ui.views.dialog

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.appcompat.content.res.AppCompatResources
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import forpdateam.ru.forpda.R
import forpdateam.ru.forpda.common.appicon.AppIcons
import forpdateam.ru.forpda.common.getColorFromAttr
import kotlin.math.roundToInt

/**
 * Выбор значка уведомлений в статус-баре — независимо от иконки приложения:
 * у приложения может стоять одна, в шторке — любая другая.
 *
 * Пункты: «По типу события» (штатные глифы), «Как иконка приложения»
 * (следует за выбором иконки) и все варианты из [AppIcons.variants].
 *
 * Превью вариантов — ЦВЕТНЫЕ adaptive-иконки, как в пикере иконки приложения.
 * Раньше показывались monochrome-силуэты «как в статус-баре», но у половины
 * вариантов силуэт — одна и та же жирная «4», и список читался как десяток
 * одинаковых строк. Цветное превью позволяет узнать вариант; о том, что
 * система перекрасит значок в один цвет, сказано в подписи диалога.
 */
object NotificationIconPickerDialog {

    fun show(context: Context, current: String, onPick: (String) -> Unit) {
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

        fun addCard(
                value: String,
                @DrawableRes previewRes: Int,
                @StringRes titleRes: Int,
                @StringRes subtitleRes: Int?,
                tinted: Boolean = true,
        ) {
            val selected = value == current
            val title = context.getString(titleRes)
            val preview = ImageView(context).apply {
                setImageDrawable(AppCompatResources.getDrawable(context, previewRes))
                scaleType = ImageView.ScaleType.FIT_CENTER
                // Силуэты белые на прозрачном — на светлой теме без тонировки невидимы.
                // Цветные adaptive-иконки красить нельзя: смысл превью как раз в цвете.
                if (tinted) imageTintList = ColorStateList.valueOf(onSurface)
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
                subtitleRes?.let { subtitle ->
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
                addView(preview, LinearLayout.LayoutParams(px(40), px(40)))
                addView(texts, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                        .apply { marginStart = px(14) })
                isClickable = true
                setOnClickListener { dialog.dismiss(); onPick(value) }
            }
            list.addView(card, LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = px(6); bottomMargin = px(6)
            })
        }

        addCard(AppIcons.NOTIFICATION_ICON_EVENT, R.drawable.ic_notify_favorites,
                R.string.notification_icon_event, R.string.notification_icon_event_desc)
        addCard(AppIcons.NOTIFICATION_ICON_APP,
                forpdateam.ru.forpda.common.appicon.AppIconManager.selected(context).iconRes,
                R.string.notification_icon_app, R.string.notification_icon_app_desc,
                tinted = false)
        AppIcons.variants.forEach { variant ->
            addCard(variant.id, variant.iconRes, variant.titleRes, subtitleRes = null,
                    tinted = false)
        }

        val hint = TextView(context).apply {
            setText(R.string.notification_icon_hint)
            setTextColor(onSurfaceVar)
            textSize = 12f
            setPadding(px(16), px(4), px(16), px(12))
        }
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(hint)
            addView(list)
        }

        dialog = MaterialAlertDialogBuilder(context)
                .setTitle(R.string.pref_title_notification_icon)
                .setView(ScrollView(context).apply { addView(content) })
                .setNegativeButton(android.R.string.cancel, null)
                .showWithStyledButtons(compact = false)
    }
}
